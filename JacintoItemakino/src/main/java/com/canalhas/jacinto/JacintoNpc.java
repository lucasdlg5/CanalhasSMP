package com.canalhas.jacinto;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Entidade e lógica de presença do Jacinto Itemakino Baú:
 * - Corpo: HuskEntity (não queima ao sol, equipa qualquer armadura e arma de player)
 * - Vida: 20 HP (10 corações, igual player normal)
 * - Nome: Jacinto Itemakino Baú
 * - Combate: Ataca monstros hostis ao redor enquanto em idle
 * - Morte: Não perde equipamentos e renasce em 5 segundos mandando mensagem engraçada
 */
public class JacintoNpc {
    public static final String JACINTO_TAG = "jacinto_itemakino_npc";
    public static final String NPC_NAME = "§e§lJacinto Itemakino Baú";
    private static final Logger LOG = LoggerFactory.getLogger("JacintoNpc");

    private final JacintoMod mod;
    private final Random random = new Random();

    private HuskEntity jacintoEntity = null;
    private int respawnTicksRemaining = 0;
    private int idleCombatCooldown = 0;
    private int idleWanderCooldown = 0;

    private static final String[] RESPAWN_PHRASES = {
            "§6[Jacinto Itemakino Baú] §eRapaz, fui de base rapidão... tropecei na quina do baú, mas o Jacinto já tá vivinho da silva de novo! 📦💀",
            "§6[Jacinto Itemakino Baú] §eBateram no Jacinto, mas esqueceram que vaso ruim não quebra! Já tô de pé pro trabalho!",
            "§6[Jacinto Itemakino Baú] §eMorri mas passo bem! 5 segundinhos de CLT remunerado e já voltei pra labuta da firma!",
            "§6[Jacinto Itemakino Baú] §eQuase que o Jacinto fica guardado no baú pra sempre, mas o plano de saúde da firma cobriu o respawn!",
            "§6[Jacinto Itemakino Baú] §eDerrubaram o peão, mas o Jacinto levanta mais rápido que entrega expressa! Quem é o próximo monstro?"
    };

    public JacintoNpc(JacintoMod mod) {
        this.mod = mod;
    }

    public HuskEntity getEntity() {
        if (jacintoEntity != null && (jacintoEntity.isRemoved() || !jacintoEntity.isAlive())) {
            jacintoEntity = null;
        }
        return jacintoEntity;
    }

    public boolean isAlive() {
        return getEntity() != null;
    }

    public void onServerTick(MinecraftServer server) {
        if (server == null) return;

        // Gerenciamento de Respawn (5 segundos = 100 ticks)
        if (respawnTicksRemaining > 0) {
            respawnTicksRemaining--;
            if (respawnTicksRemaining == 0) {
                spawnOrRespawn(server, true);
            }
            return;
        }

        HuskEntity entity = getEntity();
        if (entity == null) {
            // Se o Jacinto ainda não está no mundo e tem jogadores online, tenta invocá-lo na base
            if (server.getTicks() % 100 == 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
                spawnOrRespawn(server, false);
            }
            return;
        }

        // Comportamento quando está Ocioso (Idle): Defesa da base e patrulha
        if (!mod.getOrganizer().isBusy()) {
            tickIdleCombat(entity);
            tickIdleWander(entity);
        }
    }

    /**
     * Patrulha e ataca monstros hostis ao redor
     */
    private void tickIdleCombat(HuskEntity entity) {
        if (--idleCombatCooldown > 0) return;
        idleCombatCooldown = 15; // Checa a cada 0.75s

        // Nunca ataque se o alvo atual já for válido e vivo
        if (entity.getTarget() != null && entity.getTarget().isAlive()) {
            return;
        }

        Box searchBox = entity.getBoundingBox().expand(14.0, 5.0, 14.0);
        List<HostileEntity> monsters = entity.getWorld().getEntitiesByClass(HostileEntity.class, searchBox,
                m -> m.isAlive() && !m.getCommandTags().contains(JACINTO_TAG)
        );

        if (!monsters.isEmpty()) {
            // Foca no monstro mais próximo
            HostileEntity closest = null;
            double closestDistSq = Double.MAX_VALUE;
            for (HostileEntity m : monsters) {
                double dist = entity.squaredDistanceTo(m);
                if (dist < closestDistSq) {
                    closestDistSq = dist;
                    closest = m;
                }
            }
            if (closest != null) {
                entity.setTarget(closest);
                entity.getNavigation().startMovingTo(closest, 1.25);
            }
        }
    }

    /**
     * Passeia suavemente pela base quando não há perigo por perto
     */
    private void tickIdleWander(HuskEntity entity) {
        if (--idleWanderCooldown > 0) return;
        idleWanderCooldown = 100 + random.nextInt(100); // A cada 5-10s

        if (entity.getTarget() != null) return;

        double ox = (random.nextDouble() - 0.5) * 16.0;
        double oz = (random.nextDouble() - 0.5) * 16.0;
        entity.getNavigation().startMovingTo(entity.getX() + ox, entity.getY(), entity.getZ() + oz, 0.9);
    }

    /**
     * Spawna ou Respowna o Jacinto no mundo
     */
    public HuskEntity spawnOrRespawn(MinecraftServer server, boolean announceRespawn) {
        if (server == null) return null;

        JacintoData data = mod.getData();
        ServerWorld world = null;

        // 1. Tenta pegar a dimensão salva da base
        if (data.baseSet && data.baseDimension != null) {
            Identifier dimId = Identifier.tryParse(data.baseDimension);
            if (dimId != null) {
                world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
            }
        }

        // 2. Fallback: mundo Overworld do servidor ou jogador online
        if (world == null) {
            world = server.getOverworld();
        }

        Vec3d spawnPos;
        if (data.baseSet) {
            spawnPos = new Vec3d(data.baseX, data.baseY, data.baseZ);
        } else if (!server.getPlayerManager().getPlayerList().isEmpty()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            world = p.getServerWorld();
            spawnPos = p.getPos();
            data.setBase(spawnPos, world.getRegistryKey().getValue().toString());
        } else {
            spawnPos = Vec3d.ofBottomCenter(world.getSpawnPos());
        }

        // Remove entidade antiga se houver
        if (jacintoEntity != null && !jacintoEntity.isRemoved()) {
            jacintoEntity.discard();
        }

        HuskEntity husk = EntityType.HUSK.create(world);
        if (husk == null) return null;

        husk.refreshPositionAndAngles(spawnPos.x, spawnPos.y + 0.1, spawnPos.z, 0f, 0f);
        husk.addCommandTag(JACINTO_TAG);
        husk.setCustomName(Text.literal(NPC_NAME));
        husk.setCustomNameVisible(true);
        husk.setPersistent();
        husk.setSilent(true); // Silencioso para não ficar rosnando como zumbi
        husk.setCanPickUpLoot(false);

        // Atributos de Player Normal
        var maxHpAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(20.0);
        husk.setHealth(20.0f);

        var speedAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.28);

        var attackAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) attackAttr.setBaseValue(4.0);

        // Restaura armaduras e armas salvas
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack saved = data.getSavedEquipment(slot);
            if (!saved.isEmpty()) {
                husk.equipStack(slot, saved.copy());
                husk.setEquipmentDropChance(slot, 0.0f); // 0% de chance de dropar no chão
            }
        }

        world.spawnEntity(husk);
        this.jacintoEntity = husk;
        data.jacintoUuid = husk.getUuidAsString();
        data.save();

        // Partículas festivas de invocação
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, spawnPos.x, spawnPos.y + 1.0, spawnPos.z, 15, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);

        if (announceRespawn) {
            String phrase = RESPAWN_PHRASES[random.nextInt(RESPAWN_PHRASES.length)];
            server.getPlayerManager().broadcast(Text.literal(phrase), false);
        }

        return husk;
    }

    /**
     * Chamado quando o Jacinto sofre dano fatal
     */
    public void onDeath(HuskEntity entity) {
        if (entity == null) return;

        // Salva os equipamentos atuais na memória para garantir que nada seja perdido
        JacintoData data = mod.getData();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                data.saveEquipment(slot, stack.copy());
            }
        }

        // Limpa a mão e armadura da entidade antes da remoção para evitar qualquer drop no chão
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            entity.equipStack(slot, ItemStack.EMPTY);
        }

        this.jacintoEntity = null;
        this.respawnTicksRemaining = 100; // 5 segundos = 100 ticks

        MinecraftServer server = entity.getServer();
        if (server != null) {
            server.getPlayerManager().broadcast(
                    Text.literal("§c☠ [Jacinto Itemakino Baú] §7O Jacinto caiu em combate! Respawnando em 5 segundos na base..."),
                    false
            );
        }
    }

    /**
     * Interação do jogador ao clicar com o botão direito no Jacinto
     */
    public void handleInteract(PlayerEntity player, HuskEntity jacinto, Hand hand) {
        if (player.getWorld().isClient() || hand != Hand.MAIN_HAND) return;

        ItemStack held = player.getStackInHand(hand);
        JacintoData data = mod.getData();

        // 1. Shift + Clique com mão vazia: Exibe status e equipamentos
        if (player.isSneaking() && held.isEmpty()) {
            player.sendMessage(Text.literal("§6=== 📦 STATUS DO JACINTO ITEMAKINO BAÚ ==="), false);
            player.sendMessage(Text.literal("§fVida: §a" + String.format("%.0f", jacinto.getHealth()) + "/20 ❤"), false);
            player.sendMessage(Text.literal("§fArma: §e" + getStackDisplayName(jacinto.getMainHandStack())), false);
            player.sendMessage(Text.literal("§fEscudo/Mão Secundária: §e" + getStackDisplayName(jacinto.getOffHandStack())), false);
            player.sendMessage(Text.literal("§fArmadura: §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.HEAD)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.CHEST)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.LEGS)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.FEET))), false);
            player.sendMessage(Text.literal("§fTotal de Itens Organizados na Carreira: §a" + data.totalItemsOrganized + " itens"), false);
            player.sendMessage(Text.literal("§7(Dica: Clique com armaduras ou armas na mão dele para equipá-lo!)"), false);
            jacinto.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            return;
        }

        // 2. Jogador segurando armadura ou arma: Equipa no Jacinto
        EquipmentSlot targetSlot = getSlotForItem(held);
        if (targetSlot != null) {
            ItemStack oldEquip = jacinto.getEquippedStack(targetSlot).copy();
            ItemStack toEquip = held.split(1);

            jacinto.equipStack(targetSlot, toEquip);
            jacinto.setEquipmentDropChance(targetSlot, 0.0f);
            data.saveEquipment(targetSlot, toEquip);

            // Devolve o item antigo ao jogador ou dropa na frente
            if (!oldEquip.isEmpty()) {
                if (!player.getInventory().insertStack(oldEquip)) {
                    player.dropItem(oldEquip, false);
                }
            }

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);

            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER,
                    jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa, valeu pelo agrado, patrão §b" +
                    player.getGameProfile().getName() + "§f! Esse(a) §b" + toEquip.getName().getString() + "§f ficou tinindo em mim!"), false);
            return;
        }

        // 3. Clique comum sem item especial: Mensagem amigável de operário
        String[] idleQuotes = {
                "§e[Jacinto Itemakino Baú] §fÀs suas ordens, patrão! Coloque uma placa no baú e digite §b/organizar§f que eu cuido do resto!",
                "§e[Jacinto Itemakino Baú] §fAqui na firma o serviço é rápido. Se precisar parar no meio, só mandar um §b/organizar stop§f!",
                "§e[Jacinto Itemakino Baú] §fDeixa qualquer monstro chegar perto desse baú pra ver se eu não mostro meu serviço!",
                "§e[Jacinto Itemakino Baú] §fJacinto organizando, a firma prosperando! Só me dar as ordens."
        };
        player.sendMessage(Text.literal(idleQuotes[random.nextInt(idleQuotes.length)]), false);
        jacinto.playSound(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
    }

    private EquipmentSlot getSlotForItem(ItemStack stack) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getSlotType();
        }
        if (stack.getItem() instanceof ShieldItem) {
            return EquipmentSlot.OFFHAND;
        }
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof MiningToolItem) {
            return EquipmentSlot.MAINHAND;
        }
        return null;
    }

    private String getStackDisplayName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "§7[Vazio]";
        return stack.getName().getString();
    }
}
