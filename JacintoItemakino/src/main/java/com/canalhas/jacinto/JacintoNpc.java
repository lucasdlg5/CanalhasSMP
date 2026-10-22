package com.canalhas.jacinto;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.MiningToolItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

    // Silhueta Luminosa (Glowing Outline) ativa durante tarefas delegadas
    private boolean glowingSilhouette = false;
    private UUID taskDelegatorUuid = null;

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

        // A cada 40 ticks (2 segundos), executa varredura para eliminar clones/órfãos
        if (server.getTicks() % 40 == 0) {
            cleanupDuplicateJacintos(server);
        }

        HuskEntity entity = getEntity();
        if (entity == null) {
            // Tenta encontrar um Jacinto já existente nos mundos antes de criar outro
            cleanupDuplicateJacintos(server);
            entity = getEntity();
        }

        if (entity == null) {
            // Se o Jacinto ainda não está no mundo e tem jogadores online, tenta invocá-lo na base
            if (server.getTicks() % 100 == 0 && !server.getPlayerManager().getPlayerList().isEmpty()) {
                spawnOrRespawn(server, false);
            }
            return;
        }

        // Garante que Jacinto NUNCA tenha jogadores ou entidades pacíficas como alvo e mantenha braços relaxados
        if (entity.getTarget() instanceof PlayerEntity || entity.getTarget() instanceof PassiveEntity) {
            entity.setTarget(null);
            entity.setAttacking(false);
        }
        if (entity.getTarget() == null && entity.isAttacking()) {
            entity.setAttacking(false);
        }

        // Sincroniza a silhueta luminosa (Glowing): Ativa enquanto executa tarefa delegada, desliga quando ocioso
        boolean shouldGlow = glowingSilhouette || mod.getOrganizer().isBusy() || mod.getMiner().isMining();
        if (entity.isGlowing() != shouldGlow) {
            entity.setGlowing(shouldGlow);
        }

        // Comportamento quando está Ocioso (Idle): Defesa da base e patrulha
        if (!mod.getOrganizer().isBusy() && !mod.getMiner().isMining()) {
            tickIdleCombat(entity);
            tickIdleWander(entity);
        }
    }

    /**
     * Varre todos os mundos do servidor e elimina cópias órfãs ou duplicadas do Jacinto.
     * Mantém apenas 1 Jacinto ativo e reconecta a referência se necessário.
     */
    public int cleanupDuplicateJacintos(MinecraftServer server) {
        if (server == null) return 0;
        int purged = 0;
        JacintoData data = mod.getData();

        HuskEntity primary = (jacintoEntity != null && !jacintoEntity.isRemoved() && jacintoEntity.isAlive()) ? jacintoEntity : null;
        UUID savedUuid = null;
        if (data.jacintoUuid != null && !data.jacintoUuid.isEmpty()) {
            try {
                savedUuid = UUID.fromString(data.jacintoUuid);
            } catch (Exception ignored) {}
        }

        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e instanceof HuskEntity husk) {
                    boolean isJacintoTag = husk.getCommandTags().contains(JACINTO_TAG);
                    boolean isJacintoName = husk.getCustomName() != null && husk.getCustomName().getString().contains("Jacinto");
                    if (isJacintoTag || isJacintoName) {
                        if (primary == null && (savedUuid == null || husk.getUuid().equals(savedUuid)) && husk.isAlive()) {
                            // Reconecta o Jacinto principal
                            primary = husk;
                            this.jacintoEntity = husk;
                            data.jacintoUuid = husk.getUuidAsString();
                            configureJacintoAI(husk);
                        } else if (husk == primary) {
                            // É a única entidade principal válida
                            continue;
                        } else {
                            // É uma duplicata/clone do Jacinto! Descarta imediatamente
                            husk.discard();
                            purged++;
                        }
                    }
                }
            }
        }

        if (primary != null && this.jacintoEntity == null) {
            this.jacintoEntity = primary;
        }

        return purged;
    }

    /**
     * Configura a IA e propriedades do Jacinto para agir como trabalhador amigável:
     * - Não ataca jogadores nem aldeões (arms down)
     * - Defende a base atacando monstros hostis
     * - Sem colisão com players (não bloqueia portas nem empurra ninguém)
     * - Não dropa XP ao morrer
     * - Usa uniforme de operário se não houver armadura
     */
    public void configureJacintoAI(HuskEntity husk) {
        if (husk == null) return;

        husk.addCommandTag(JACINTO_TAG);
        husk.setCustomName(Text.literal(NPC_NAME));
        husk.setCustomNameVisible(true);
        husk.setPersistent();
        husk.setSilent(true);
        husk.setBaby(false);
        husk.setCanPickUpLoot(false);
        husk.disableExperienceDropping();

        var maxHpAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(20.0);
        if (husk.getHealth() > 20.0f) husk.setHealth(20.0f);

        var speedAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.28);

        var attackAttr = husk.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attackAttr != null) attackAttr.setBaseValue(4.0);

        // Limpa TODOS os alvos e objetivos de zumbi vanilla (removendo ActiveTargetGoal<PlayerEntity>)
        husk.targetSelector.clear(goal -> true);
        husk.goalSelector.clear(goal -> true);

        // Registra IA amigável
        husk.goalSelector.add(0, new SwimGoal(husk));
        husk.goalSelector.add(2, new MeleeAttackGoal(husk, 1.25, false));
        husk.goalSelector.add(5, new WanderAroundFarGoal(husk, 0.9));
        husk.goalSelector.add(6, new LookAtEntityGoal(husk, PlayerEntity.class, 8.0f));
        husk.goalSelector.add(7, new LookAroundGoal(husk));

        // Foco de combate APENAS em monstros hostis verdadeiros
        husk.targetSelector.add(1, new ActiveTargetGoal<>(husk, HostileEntity.class, 10, true, false,
                target -> target != null && target.isAlive() && !target.getCommandTags().contains(JACINTO_TAG)));

        husk.setAttacking(false);

        // Adiciona ao scoreboard com colisão desligada e cor da equipe
        applyScoreboardTeam(husk);

        // Uniforme de trabalho
        ensureWorkerAttire(husk);

        // Silhueta luminosa caso esteja em tarefa ativa
        boolean shouldGlow = glowingSilhouette || mod.getOrganizer().isBusy() || mod.getMiner().isMining();
        husk.setGlowing(shouldGlow);
    }

    public void ensureWorkerAttire(HuskEntity husk) {
        if (husk == null) return;
        if (husk.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
            husk.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            husk.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0f);
        }
        if (husk.getEquippedStack(EquipmentSlot.CHEST).isEmpty()) {
            husk.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
            husk.setEquipmentDropChance(EquipmentSlot.CHEST, 0.0f);
        }
        if (husk.getEquippedStack(EquipmentSlot.LEGS).isEmpty()) {
            husk.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.LEATHER_LEGGINGS));
            husk.setEquipmentDropChance(EquipmentSlot.LEGS, 0.0f);
        }
        if (husk.getEquippedStack(EquipmentSlot.FEET).isEmpty()) {
            husk.equipStack(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
            husk.setEquipmentDropChance(EquipmentSlot.FEET, 0.0f);
        }
        if (husk.getMainHandStack().isEmpty()) {
            ItemStack mainTool = mod.getMiner().isMining() ? mod.getData().getEffectivePickaxe() : mod.getData().getEffectiveSword();
            husk.equipStack(EquipmentSlot.MAINHAND, mainTool.copy());
            husk.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }
    }

    public void applyScoreboardTeam(HuskEntity husk) {
        if (husk == null) return;
        MinecraftServer server = husk.getServer();
        if (server == null) return;
        try {
            ServerScoreboard scoreboard = server.getScoreboard();
            Team team = scoreboard.getTeam("jacinto_team");
            if (team == null) {
                team = scoreboard.addTeam("jacinto_team");
                team.setDisplayName(Text.literal("Equipe Jacinto"));
                team.setCollisionRule(AbstractTeam.CollisionRule.NEVER);
                team.setFriendlyFireAllowed(false);
                team.setColor(Formatting.GOLD);
            } else {
                team.setColor(Formatting.GOLD);
            }
            scoreboard.addPlayerToTeam(husk.getEntityName(), team);
        } catch (Exception e) {
            LOG.warn("Falha ao registrar equipe scoreboard para Jacinto: " + e.getMessage());
        }
    }

    /**
     * Ativa ou desativa a silhueta luminosa (Glowing Outline) do Jacinto.
     * Fica visível através de paredes para que o jogador acompanhe o trajeto até o baú ou mina.
     */
    public void setGlowingSilhouette(boolean active, ServerPlayerEntity player) {
        this.glowingSilhouette = active;
        this.taskDelegatorUuid = (active && player != null) ? player.getUuid() : null;
        HuskEntity entity = getEntity();
        if (entity != null && entity.isAlive()) {
            entity.setGlowing(active);
            if (active && entity.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.NEUTRAL, 0.7f, 1.6f);
                sw.spawnParticles(ParticleTypes.GLOW, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                        15, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    public boolean isGlowingSilhouette() {
        return glowingSilhouette;
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
        if ("MINING".equalsIgnoreCase(data.currentTask) && data.miningY != 0.0 && data.miningDimension != null) {
            Identifier dimId = Identifier.tryParse(data.miningDimension);
            if (dimId != null) {
                ServerWorld mineWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
                if (mineWorld != null) {
                    world = mineWorld;
                }
            }
            spawnPos = new Vec3d(data.miningX, data.miningY, data.miningZ);
        } else if (data.baseSet) {
            spawnPos = new Vec3d(data.baseX, data.baseY, data.baseZ);
        } else if (!server.getPlayerManager().getPlayerList().isEmpty()) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayerList().get(0);
            world = p.getServerWorld();
            spawnPos = p.getPos();
            data.setBase(spawnPos, world.getRegistryKey().getValue().toString());
        } else {
            spawnPos = Vec3d.ofBottomCenter(world.getSpawnPos());
        }

        // 3. Limpa duplicatas e verifica se já existe um Jacinto ativo
        cleanupDuplicateJacintos(server);
        if (this.jacintoEntity != null && this.jacintoEntity.isAlive()) {
            this.jacintoEntity.refreshPositionAndAngles(spawnPos.x, spawnPos.y + 0.1, spawnPos.z, 0f, 0f);
            configureJacintoAI(this.jacintoEntity);
            return this.jacintoEntity;
        }

        // Remove entidade antiga se houver
        if (jacintoEntity != null && !jacintoEntity.isRemoved()) {
            jacintoEntity.discard();
        }

        HuskEntity husk = EntityType.HUSK.create(world);
        if (husk == null) return null;

        husk.refreshPositionAndAngles(spawnPos.x, spawnPos.y + 0.1, spawnPos.z, 0f, 0f);

        // Restaura armaduras e armas salvas
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack saved = data.getSavedEquipment(slot);
            if (!saved.isEmpty()) {
                husk.equipStack(slot, saved.copy());
                husk.setEquipmentDropChance(slot, 0.0f); // 0% de chance de dropar no chão
            }
        }

        // Aplica IA, tags, sem colisão e uniforme de operário
        configureJacintoAI(husk);

        world.spawnEntity(husk);
        this.jacintoEntity = husk;
        data.jacintoUuid = husk.getUuidAsString();
        data.save();

        // Partículas festivas de invocação
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, spawnPos.x, spawnPos.y + 1.0, spawnPos.z, 15, 0.5, 0.5, 0.5, 0.1);
        world.playSound(null, spawnPos.x, spawnPos.y, spawnPos.z, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);

        if (announceRespawn) {
            if ("MINING".equalsIgnoreCase(data.currentTask)) {
                String phrase = "§6[Jacinto Itemakino Baú] §eMorri mas voltei com sede de minério! Retomando a picareta e a extração de [" + mod.getMiner().getTargetDisplayName() + "] imediatamente! ⛏️🔥";
                server.getPlayerManager().broadcast(Text.literal(phrase), false);
                mod.getMiner().resumeMining();
            } else {
                String phrase = RESPAWN_PHRASES[random.nextInt(RESPAWN_PHRASES.length)];
                server.getPlayerManager().broadcast(Text.literal(phrase), false);
            }
        } else if ("MINING".equalsIgnoreCase(data.currentTask)) {
            mod.getMiner().resumeMining();
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

        // 1. Shift + Clique com mão vazia: Exibe status, ferramentas e equipamentos
        if (player.isSneaking() && held.isEmpty()) {
            player.sendMessage(Text.literal("§6=== 📦 STATUS DO JACINTO ITEMAKINO BAÚ ==="), false);
            player.sendMessage(Text.literal("§fVida: §a" + String.format("%.0f", jacinto.getHealth()) + "/20 ❤"), false);
            player.sendMessage(Text.literal("§fTarefa Atual: §e" + (mod.getMiner().isMining() ? "Minerando " + mod.getMiner().getTargetDisplayName() : (mod.getOrganizer().isBusy() ? "Organizando Baús" : "Patrulhando / Ocioso"))), false);

            ItemStack effSword = data.getEffectiveSword();
            ItemStack effPick = data.getEffectivePickaxe();
            ItemStack effShovel = data.getEffectiveShovel();
            int pickTier = JacintoMiningCompatibility.getPickaxeMiningLevel(effPick);

            player.sendMessage(Text.literal("§f🗡️ Espada de Combate: §e" + getStackDisplayName(effSword) + (JacintoData.isDefaultTool(effSword) ? " §7(Padrão Infinita)" : "")), false);
            player.sendMessage(Text.literal("§f⛏️ Picareta de Mineração: §e" + getStackDisplayName(effPick) + " §b[Tier " + JacintoMiningCompatibility.getTierName(pickTier) + "]" + (JacintoData.isDefaultTool(effPick) ? " §7(Padrão Infinita)" : "")), false);
            player.sendMessage(Text.literal("§f🪵 Pá de Escavação: §e" + getStackDisplayName(effShovel) + (JacintoData.isDefaultTool(effShovel) ? " §7(Padrão Infinita)" : "")), false);
            player.sendMessage(Text.literal("§f🛡️ Escudo/Offhand: §e" + getStackDisplayName(jacinto.getOffHandStack())), false);
            player.sendMessage(Text.literal("§f🦺 Armadura: §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.HEAD)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.CHEST)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.LEGS)) + " §7| §b" +
                    getStackDisplayName(jacinto.getEquippedStack(EquipmentSlot.FEET))), false);
            player.sendMessage(Text.literal("§fTotal de Itens Organizados na Carreira: §a" + data.totalItemsOrganized + " itens"), false);
            player.sendMessage(Text.literal("§fTotal de Minérios Extraídos na Carreira: §b" + data.totalBlocksMined + " blocos"), false);
            player.sendMessage(Text.literal("§7(Dica: Clique com espadas, picaretas, pás ou armaduras para equipá-lo!)"), false);
            jacinto.playSound(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            return;
        }

        // 2. Jogador segurando Espada:
        if (held.getItem() instanceof SwordItem) {
            ItemStack oldSword = data.getSavedSword();
            ItemStack toEquip = held.split(1);
            data.saveSword(toEquip);

            if (!mod.getMiner().isMining()) {
                jacinto.equipStack(EquipmentSlot.MAINHAND, toEquip.copy());
            }

            if (!oldSword.isEmpty() && !JacintoData.isDefaultTool(oldSword)) {
                if (!player.getInventory().insertStack(oldSword)) {
                    player.dropItem(oldSword, false);
                }
            }

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa patrão §b" + player.getName().getString() +
                    "§f! Essa espada §b" + toEquip.getName().getString() + "§f vai defender a base com tudo! Quando quebrar, voltarei para a minha de madeira infinita."), false);
            return;
        }

        // 3. Jogador segurando Picareta:
        if (held.getItem() instanceof PickaxeItem) {
            ItemStack oldPick = data.getSavedPickaxe();
            ItemStack toEquip = held.split(1);
            data.savePickaxe(toEquip);

            if (mod.getMiner().isMining()) {
                jacinto.equipStack(EquipmentSlot.MAINHAND, toEquip.copy());
            }

            if (!oldPick.isEmpty() && !JacintoData.isDefaultTool(oldPick)) {
                if (!player.getInventory().insertStack(oldPick)) {
                    player.dropItem(oldPick, false);
                }
            }

            int tier = JacintoMiningCompatibility.getPickaxeMiningLevel(toEquip);
            String tierName = JacintoMiningCompatibility.getTierName(tier);

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fValeu patrão §b" + player.getName().getString() +
                    "§f! Equipei a §b" + toEquip.getName().getString() + " §7(Nível " + tierName + ")§f! Agora consigo minerar minérios desse nível. Ao quebrar, volto para a de madeira infinita."), false);
            return;
        }

        // 4. Jogador segurando Pá:
        if (held.getItem() instanceof ShovelItem) {
            ItemStack oldShovel = data.getSavedShovel();
            ItemStack toEquip = held.split(1);
            data.saveShovel(toEquip);

            if (!oldShovel.isEmpty() && !JacintoData.isDefaultTool(oldShovel)) {
                if (!player.getInventory().insertStack(oldShovel)) {
                    player.dropItem(oldShovel, false);
                }
            }

            jacinto.getWorld().playSound(null, jacinto.getX(), jacinto.getY(), jacinto.getZ(),
                    SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND, SoundCategory.PLAYERS, 1.0f, 1.0f);
            jacinto.getWorld().addParticle(ParticleTypes.HAPPY_VILLAGER, jacinto.getX(), jacinto.getY() + 1.2, jacinto.getZ(), 0, 0.1, 0);

            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fOpa, uma pá de qualidade! §b" + toEquip.getName().getString() +
                    "§f guardada para quando eu cavar terra e cascalho!"), false);
            return;
        }

        // 5. Jogador segurando Armadura ou Escudo: Equipa no Jacinto
        EquipmentSlot targetSlot = getSlotForItem(held);
        if (targetSlot != null) {
            ItemStack oldEquip = jacinto.getEquippedStack(targetSlot).copy();
            ItemStack toEquip = held.split(1);

            jacinto.equipStack(targetSlot, toEquip);
            jacinto.setEquipmentDropChance(targetSlot, 0.0f);
            data.saveEquipment(targetSlot, toEquip);

            // Devolve o item antigo ao jogador se não for ferramenta padrão
            if (!oldEquip.isEmpty() && !JacintoData.isDefaultTool(oldEquip)) {
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
