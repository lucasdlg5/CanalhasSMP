package com.canalhas.jacinto;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.*;

/**
 * Sistema de Mineração Passiva e Autônoma do Jacinto Itemakino:
 * - Scanner geológico que localiza veios de minério em raio de até 32 blocos
 * - Navegação inteligente pelo chão/caminho mais próximo antes de escavar
 * - Anti-Despawn e Manutenção de Chunks (Chunk Loading 3x3 forçado) mesmo a milhares de blocos de qualquer jogador
 * - Persistência ininterrupta através de mortes e reinicializações
 * - Retenção de ferramentas e suporte a encantamentos de Picareta
 */
public class JacintoMiner {
    private static final Logger LOG = LoggerFactory.getLogger("JacintoMiner");
    private final JacintoMod mod;

    private boolean isMining = false;
    private String rawQuery = "diamante";
    private String targetDisplayName = "Diamante";
    private Item targetItem = null;
    private UUID requestingPlayerUuid = null;

    // Baú de destino para encher e finalizar
    private boolean hasMiningChest = false;
    private BlockPos targetChestPos = null;
    private String targetChestDim = "minecraft:overworld";
    private int carriedItemCount = 0;
    private boolean isReturningToChest = false;

    private BlockPos targetOrePos = null;
    private BlockPos navigationTarget = null;
    private final Set<BlockPos> ignoredOres = new HashSet<>();
    private final Set<ChunkPos> forcedChunks = new HashSet<>();

    private Direction explorationDirection = Direction.NORTH;
    private int scanCooldown = 0;
    private int digProgressTicks = 0;
    private int sessionBlocksMined = 0;
    private int stuckTicks = 0;
    private Vec3d lastPosition = null;

    private ServerWorld world = null;

    public JacintoMiner(JacintoMod mod) {
        this.mod = mod;
    }

    public boolean isMining() {
        return isMining;
    }

    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    public int getSessionBlocksMined() {
        return sessionBlocksMined;
    }

    public Set<ChunkPos> getForcedChunks() {
        return forcedChunks;
    }

    public boolean hasMiningChest() {
        return hasMiningChest;
    }

    public BlockPos getTargetChestPos() {
        return targetChestPos;
    }

    public boolean isConstructionOrStoneTarget() {
        return JacintoClaimHelper.isConstructionOrStoneType(rawQuery, targetDisplayName);
    }

    /**
     * Inicia a mineração passiva com baú de destino para encher
     */
    public boolean startMiningWithChest(ServerPlayerEntity player, Item item, String displayName, String query, BlockPos chestPos) {
        if (chestPos == null) {
            if (player != null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, você precisa estar olhando diretamente para um baú (ou barril) para que eu possa iniciar a mineração e saber onde guardar os itens!"), false);
            }
            return false;
        }

        // Se estava organizando baús, interrompe a organização para focar na mineração
        if (mod.getOrganizer().isBusy()) {
            mod.getOrganizer().stopOrganizing(player);
        }

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity == null || !entity.isAlive()) {
            MinecraftServer server = player != null ? player.getServer() : null;
            entity = mod.getNpc().spawnOrRespawn(server, false);
        }

        this.rawQuery = query != null ? query.trim() : "andesito";
        this.targetItem = item != null ? item : JacintoOrganizer.matchItemName(this.rawQuery);
        this.targetDisplayName = displayName != null ? displayName : resolveDisplayName(this.rawQuery);

        // Validação da Picareta vs Nível do Bloco (Mining Tier Compatibility)
        ItemStack currentPick = mod.getData().getEffectivePickaxe();
        JacintoMiningCompatibility.CompatibilityResult comp = JacintoMiningCompatibility.check(
                currentPick, this.targetItem, this.rawQuery, this.targetDisplayName
        );

        if (!comp.canMine) {
            if (player != null) {
                player.sendMessage(Text.literal("§6=================================================="), false);
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, não consigo minerar §6§l[" + comp.targetName + "]§c no momento!"), false);
                player.sendMessage(Text.literal("§fMotivo: §e" + comp.explanation), false);
                player.sendMessage(Text.literal("§7Se eu tentar minerar com essa ferramenta, o bloco vai esfarelar e nada será dropado!"), false);
                player.sendMessage(Text.literal("§aDica: Me entregue uma picareta de §b" + comp.requiredTierName + "§a ou superior clicando em mim com o botão direito!"), false);
                player.sendMessage(Text.literal("§7(Digite §e/minerar compatibilidade§7 para consultar o guia de picaretas da firma)."), false);
                player.sendMessage(Text.literal("§6=================================================="), false);
            }
            if (entity != null) {
                entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            return false;
        }

        this.isMining = true;
        this.hasMiningChest = true;
        this.targetChestPos = chestPos;
        this.targetChestDim = player != null ? player.getServerWorld().getRegistryKey().getValue().toString() : "minecraft:overworld";
        this.carriedItemCount = 0;
        this.isReturningToChest = false;
        this.requestingPlayerUuid = player != null ? player.getUuid() : null;
        this.targetOrePos = null;
        this.navigationTarget = null;
        this.ignoredOres.clear();
        this.sessionBlocksMined = 0;
        this.digProgressTicks = 0;
        this.stuckTicks = 0;

        if (player != null) {
            this.world = player.getServerWorld();
            this.explorationDirection = Direction.fromRotation(player.getYaw());
            if (explorationDirection.getAxis() == Direction.Axis.Y) {
                explorationDirection = Direction.NORTH;
            }
        } else if (entity != null) {
            this.world = (ServerWorld) entity.getWorld();
        }

        ensurePickaxeEquipped(entity);

        // Salva estado permanente com o baú de destino
        Vec3d pos = entity != null ? entity.getPos() : (player != null ? player.getPos() : Vec3d.ZERO);
        String dim = world != null ? world.getRegistryKey().getValue().toString() : "minecraft:overworld";
        mod.getData().setMiningTask(this.rawQuery, pos, dim, targetChestPos, targetChestDim);

        // Confirmação ao jogador
        if (player != null) {
            player.sendMessage(Text.literal("§6=================================================="), false);
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §aOpa, patrão §b" + player.getName().getString() + "§a!"), false);
            player.sendMessage(Text.literal("§fIniciando mineração passiva de: §6§l[" + targetDisplayName + "]§f!"), false);
            player.sendMessage(Text.literal("§fDestino: Baú em §eX: " + targetChestPos.getX() + ", Y: " + targetChestPos.getY() + ", Z: " + targetChestPos.getZ()), false);
            player.sendMessage(Text.literal("§aTrabalharei sem parar até encher este baú! Ao ficar cheio, entrarei em modo de descanso (IDLE)."), false);

            if (isConstructionOrStoneTarget()) {
                player.sendMessage(Text.literal("§e🛡️ [Proteção de Construções & Claims Ativada]:"), false);
                player.sendMessage(Text.literal("§7- Material de construção detectado! Descerei para cavernas subterrâneas (abaixo de Y=40) para não tocar em castelos ou edificações."), false);
                player.sendMessage(Text.literal("§7- Respeito integral a Claims (Open Parties and Claims / FTB): nenhum bloco em chunk claimado será quebrado!"), false);
            } else {
                player.sendMessage(Text.literal("§7- Respeito integral a Claims: chunks protegidos por players/parties são 100% seguros."), false);
            }

            player.sendMessage(Text.literal("§7- Scanner geológico ativado (varredura de até 32 blocos)."), false);
            player.sendMessage(Text.literal("§7- Sistema anti-despawn e 3x3 chunks forçados mantidos."), false);
            player.sendMessage(Text.literal("§6✨ [Silhueta do Jacinto Ativada]: §fAcompanhe a silhueta dourada do Jacinto através das paredes e terreno enquanto ele minera!"), false);
            player.sendMessage(Text.literal("§7- Use §e/minerar stop§7 a qualquer momento se desejar interromper."), false);
            player.sendMessage(Text.literal("§6=================================================="), false);
        }

        // Ativa a silhueta luminosa (Glowing Outline)
        mod.getNpc().setGlowingSilhouette(true, player);

        if (entity != null) {
            entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
        }

        return true;
    }

    /**
     * Inicia a mineração passiva a partir do item contido em uma Moldura
     */
    public boolean tryStartMiningFromFrame(ServerPlayerEntity player, ItemStack frameStack) {
        if (frameStack == null || frameStack.isEmpty()) {
            if (player != null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cEssa moldura está vazia! Coloque o item ou minério que deseja minerar dentro dela."), false);
            }
            return false;
        }

        Item item = frameStack.getItem();
        String query = resolveQueryFromItem(item);
        String name = frameStack.getName().getString();

        return tryStartMining(player, query, name, true);
    }

    /**
     * Inicia a mineração passiva do item desejado via texto
     */
    public boolean tryStartMining(ServerPlayerEntity player, String query) {
        return tryStartMining(player, query, null, false);
    }

    /**
     * Inicia a mineração passiva com suporte a identificação via Moldura ou Comando Direto
     */
    public boolean tryStartMining(ServerPlayerEntity player, String query, String customDisplayName, boolean fromFrame) {
        if (query == null || query.isBlank()) {
            if (player != null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cInforme o que deseja que eu minere! Ex: §e/minerar diamante§c, §e/minerar ferro§c, §e/minerar ouro§c, etc."), false);
            }
            return false;
        }

        // Se estava organizando baús, interrompe a organização para focar na mineração
        if (mod.getOrganizer().isBusy()) {
            mod.getOrganizer().stopOrganizing(player);
        }

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity == null || !entity.isAlive()) {
            MinecraftServer server = player != null ? player.getServer() : null;
            entity = mod.getNpc().spawnOrRespawn(server, false);
        }

        this.rawQuery = query.trim();
        this.targetDisplayName = resolveDisplayName(this.rawQuery, customDisplayName);

        // Validação da Picareta vs Nível do Bloco (Mining Tier Compatibility)
        ItemStack currentPick = mod.getData().getEffectivePickaxe();
        JacintoMiningCompatibility.CompatibilityResult comp = JacintoMiningCompatibility.check(
                currentPick, this.targetItem, this.rawQuery, this.targetDisplayName
        );

        if (!comp.canMine) {
            if (player != null) {
                player.sendMessage(Text.literal("§6=================================================="), false);
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, não consigo minerar §6§l[" + comp.targetName + "]§c no momento!"), false);
                player.sendMessage(Text.literal("§fMotivo: §e" + comp.explanation), false);
                player.sendMessage(Text.literal("§7Se eu tentar minerar com essa ferramenta, o bloco vai esfarelar e nada será dropado!"), false);
                player.sendMessage(Text.literal("§aDica: Me entregue uma picareta de §b" + comp.requiredTierName + "§a ou superior clicando em mim com o botão direito!"), false);
                player.sendMessage(Text.literal("§7(Digite §e/minerar compatibilidade§7 para consultar o guia de picaretas da firma)."), false);
                player.sendMessage(Text.literal("§6=================================================="), false);
            }
            if (entity != null) {
                entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            }
            return false;
        }

        this.isMining = true;
        this.requestingPlayerUuid = player != null ? player.getUuid() : null;
        this.targetOrePos = null;
        this.navigationTarget = null;
        this.ignoredOres.clear();
        this.sessionBlocksMined = 0;
        this.digProgressTicks = 0;
        this.stuckTicks = 0;

        if (player != null) {
            this.world = player.getServerWorld();
            this.explorationDirection = Direction.fromRotation(player.getYaw());
            if (explorationDirection.getAxis() == Direction.Axis.Y) {
                explorationDirection = Direction.NORTH;
            }
        } else if (entity != null) {
            this.world = (ServerWorld) entity.getWorld();
        }

        // Garante que o Jacinto esteja equipado para a tarefa
        ensurePickaxeEquipped(entity);

        // Salva estado permanente
        Vec3d pos = entity != null ? entity.getPos() : (player != null ? player.getPos() : Vec3d.ZERO);
        String dim = world != null ? world.getRegistryKey().getValue().toString() : "minecraft:overworld";
        mod.getData().setMiningTask(this.rawQuery, pos, dim);

        // Mensagem de confirmação privada ao jogador
        if (player != null) {
            player.sendMessage(Text.literal("§6=================================================="), false);
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §aOpa, patrão §b" + player.getName().getString() + "§a!"), false);
            if (fromFrame) {
                player.sendMessage(Text.literal("§fIdentifiquei a referência na §6Moldura do Baú§f: §6§l[" + targetDisplayName + "]§f!"), false);
                player.sendMessage(Text.literal("§aIniciando mineração passiva imediata até nova ordem dos patrões! ⛏️"), false);
            } else {
                player.sendMessage(Text.literal("§fIniciando mineração passiva de: §6§l[" + targetDisplayName + "]§f!"), false);
            }

            if (isConstructionOrStoneTarget()) {
                player.sendMessage(Text.literal("§e🛡️ [Proteção de Construções & Claims Ativada]:"), false);
                player.sendMessage(Text.literal("§7- Material de construção detectado! Descerei para o subsolo (abaixo de Y=40) para não mexer em castelos ou edificações."), false);
                player.sendMessage(Text.literal("§7- Chunks com Claim (Open Parties / FTB) são 100% protegidos contra mineração."), false);
            } else {
                player.sendMessage(Text.literal("§7- Respeito integral a Claims: chunks protegidos por players/parties são 100% seguros."), false);
            }

            player.sendMessage(Text.literal("§7- Scanner geológico ativado (varredura contínua de até 32 blocos)."), false);
            player.sendMessage(Text.literal("§7- Sistema anti-despawn e 3x3 chunks forçados (trabalho contínuo mesmo longe dos players)."), false);
            player.sendMessage(Text.literal("§6✨ [Silhueta do Jacinto Ativada]: §fAcompanhe a silhueta dourada do Jacinto através das paredes e terreno enquanto ele minera!"), false);
            player.sendMessage(Text.literal("§7- Use §e/minerar stop§7 a qualquer momento para eu encerrar o turno!"), false);
            player.sendMessage(Text.literal("§6=================================================="), false);
        }

        // Ativa a silhueta luminosa (Glowing Outline)
        mod.getNpc().setGlowingSilhouette(true, player);

        if (entity != null) {
            entity.getWorld().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.2f);
        }

        return true;
    }

    /**
     * Interrompe a mineração passiva e libera os chunks forçados
     */
    public void stopMining(ServerPlayerEntity player) {
        if (!isMining) {
            if (player != null) {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §7Não estou minerando no momento, patrão! Use §e/minerar <item>§7 para me dar trabalho."), false);
            }
            return;
        }

        this.isMining = false;
        this.targetOrePos = null;
        this.navigationTarget = null;

        // Libera todos os chunks que estavam forçados para o Jacinto minerar
        releaseForcedChunks(this.world);

        mod.getData().setIdleTask();
        mod.getNpc().setGlowingSilhouette(false, null);

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity != null && entity.isAlive()) {
            entity.getNavigation().stop();
            // Reverte a mão principal para a Espada (padrão ou customizada) ao parar de minerar
            ItemStack sword = mod.getData().getEffectiveSword();
            entity.equipStack(EquipmentSlot.MAINHAND, sword);
            mod.getData().saveEquipment(EquipmentSlot.MAINHAND, sword);
        }

        String msg = "§e[Jacinto Itemakino Baú] §fParei a picareta, patrão! Total coletado nesta sessão: §a" +
                sessionBlocksMined + " blocos de minério§f. (Total na carreira: §e" + mod.getData().totalBlocksMined + "§f).";

        if (player != null) {
            player.sendMessage(Text.literal(msg), false);
        } else if (entity != null && entity.getServer() != null) {
            entity.getServer().getPlayerManager().broadcast(Text.literal(msg), false);
        }
    }

    /**
     * Retoma a mineração após reinicialização do servidor ou respawn
     */
    public void resumeMining() {
        JacintoData data = mod.getData();
        if (!"MINING".equalsIgnoreCase(data.currentTask)) return;

        this.rawQuery = data.miningTarget != null ? data.miningTarget : "andesito";
        this.targetItem = JacintoOrganizer.matchItemName(this.rawQuery);
        this.targetDisplayName = resolveDisplayName(this.rawQuery);
        this.isMining = true;
        this.hasMiningChest = data.hasMiningChest;
        if (data.hasMiningChest) {
            this.targetChestPos = new BlockPos(data.miningChestX, data.miningChestY, data.miningChestZ);
            this.targetChestDim = data.miningChestDimension;
            this.carriedItemCount = data.carriedMinedCount;
        }
        this.targetOrePos = null;
        this.navigationTarget = null;
        this.ignoredOres.clear();
        this.digProgressTicks = 0;
        this.stuckTicks = 0;

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity != null) {
            this.world = (ServerWorld) entity.getWorld();
            ensurePickaxeEquipped(entity);
        }

        LOG.info("[JacintoMiner] Retomando mineração passiva de [{}] com destino ao baú [{}]...", targetDisplayName, targetChestPos);
    }

    /**
     * Loop de execução chamado no ServerTickEvents.END_SERVER_TICK
     */
    public void onServerTick() {
        if (!isMining) return;

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity == null || !entity.isAlive()) {
            return;
        }

        this.world = (ServerWorld) entity.getWorld();

        // 1. Manutenção de Persistência e Chunks Forçados
        if (world.getServer().getTicks() % 20 == 0) {
            updateForcedChunks(world, entity);
            if (!entity.isPersistent()) {
                entity.setPersistent();
            }
            // Atualiza posição da mina na persistência
            mod.getData().miningX = entity.getX();
            mod.getData().miningY = entity.getY();
            mod.getData().miningZ = entity.getZ();
            mod.getData().miningDimension = world.getRegistryKey().getValue().toString();
        }

        // 2. Garante que está segurando uma picareta
        if (world.getServer().getTicks() % 100 == 0) {
            ensurePickaxeEquipped(entity);
        }

        // 3. Caso esteja retornando para descarregar no Baú de Destino
        if (isReturningToChest && hasMiningChest && targetChestPos != null) {
            ServerWorld targetWorld = world;
            if (targetChestDim != null && !targetChestDim.equals(world.getRegistryKey().getValue().toString())) {
                ServerWorld w = world.getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, new Identifier(targetChestDim)));
                if (w != null) targetWorld = w;
            }

            double distSq = entity.squaredDistanceTo(Vec3d.ofCenter(targetChestPos));
            if (distSq > 16.0) { // mais de 4 blocos de distância
                if (!entity.getNavigation().isFollowingPath() || world.getServer().getTicks() % 20 == 0) {
                    entity.getNavigation().startMovingTo(targetChestPos.getX() + 0.5, targetChestPos.getY(), targetChestPos.getZ() + 0.5, 1.25);
                }
                checkStuckAndClearObstacle(world, entity, targetChestPos);
                return;
            }

            // Chegou ao baú! Descarrega os itens:
            entity.getNavigation().stop();
            faceBlock(entity, targetChestPos);
            entity.swingHand(Hand.MAIN_HAND);
            world.playSound(null, targetChestPos, SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, 0.8f, 1.0f);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetChestPos.getX() + 0.5, targetChestPos.getY() + 0.8, targetChestPos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);

            Inventory inv = JacintoOrganizer.getInventoryAtPos(targetWorld, targetChestPos);
            Item deliverItem = targetItem != null ? targetItem : JacintoOrganizer.matchItemName(rawQuery);
            if (deliverItem == null) deliverItem = Items.ANDESITE;

            if (inv != null && carriedItemCount > 0) {
                ItemStack stack = new ItemStack(deliverItem, carriedItemCount);
                ItemStack rem = JacintoOrganizer.insertItemIntoInventory(inv, stack);
                carriedItemCount = rem.getCount();
                mod.getData().carriedMinedCount = carriedItemCount;
                mod.getData().save();
            }

            // Verifica se o baú está completamente CHEIO
            boolean full = (inv != null) && (carriedItemCount > 0 || JacintoOrganizer.isInventoryFullOfItem(inv, deliverItem));
            if (full) {
                finishMiningChestFull(entity, world);
                return;
            }

            // Não está cheio, descarregou o lote e retorna para a mina
            isReturningToChest = false;
            targetOrePos = null;
            navigationTarget = null;
            return;
        }

        // 4. Validação do Alvo Atual
        if (targetOrePos != null) {
            BlockState state = world.getBlockState(targetOrePos);
            if (!isTargetBlock(state, targetOrePos) || !canSafeBreak(world, targetOrePos)) {
                // Bloco já foi minerado, sumiu ou virou claim/proibido
                targetOrePos = null;
                navigationTarget = null;
                digProgressTicks = 0;
            }
        }

        // 5. Se não tem alvo, ativa o scanner de 32 blocos
        if (targetOrePos == null) {
            if (--scanCooldown <= 0) {
                scanCooldown = 25; // Escaneia a cada 1.25s
                targetOrePos = scanNearbyOres(world, entity.getBlockPos(), 32);

                if (targetOrePos != null) {
                    navigationTarget = findBestStandingPosition(world, targetOrePos, entity.getBlockPos());
                    if (navigationTarget != null) {
                        entity.getNavigation().startMovingTo(
                                navigationTarget.getX() + 0.5,
                                navigationTarget.getY(),
                                navigationTarget.getZ() + 0.5,
                                1.15
                        );
                    }
                } else {
                    // Sem minérios no raio: se tiver itens no bolso e baú configurado, vai descarregar!
                    if (hasMiningChest && carriedItemCount > 0) {
                        isReturningToChest = true;
                    } else {
                        // Sem minérios no raio: avança no estilo túnel / prospeção
                        tickExploratoryMining(world, entity);
                    }
                }
            }
            return;
        }

        // 6. Com alvo definido: Aproximação e Mineração
        double distSq = entity.squaredDistanceTo(Vec3d.ofCenter(targetOrePos));

        // Alcance de mineração de player: cerca de 4.2 blocos (distSq <= 18.0)
        if (distSq > 18.0) {
            // Ainda precisa andar até o bloco
            BlockPos dest = navigationTarget != null ? navigationTarget : targetOrePos;
            if (!entity.getNavigation().isFollowingPath() || world.getServer().getTicks() % 20 == 0) {
                entity.getNavigation().startMovingTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 1.15);
            }

            // Detector de enrosco / obstáculo
            checkStuckAndClearObstacle(world, entity, dest);
            return;
        }

        // 7. Dentro do alcance: Para e Executa a Escavação
        entity.getNavigation().stop();
        faceBlock(entity, targetOrePos);

        digProgressTicks++;

        // Efeitos visuais e sonoros de bater a picareta
        if (digProgressTicks % 4 == 0) {
            entity.swingHand(Hand.MAIN_HAND);
            BlockState state = world.getBlockState(targetOrePos);
            world.playSound(null, targetOrePos, state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.8f, 1.0f);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state),
                    targetOrePos.getX() + 0.5, targetOrePos.getY() + 0.5, targetOrePos.getZ() + 0.5,
                    5, 0.25, 0.25, 0.25, 0.05);
        }

        // Tempo de quebra realista (12 ticks = 0.6s)
        if (digProgressTicks >= 12) {
            digProgressTicks = 0;

            // Re-valida segurança contra Claims e Proteção de Construções antes de quebrar
            if (!canSafeBreak(world, targetOrePos)) {
                targetOrePos = null;
                navigationTarget = null;
                return;
            }

            BlockState minedState = world.getBlockState(targetOrePos);

            // Quebra o bloco com drop baseado na ferramenta do Jacinto
            boolean broken = world.breakBlock(targetOrePos, true, entity);
            if (broken) {
                sessionBlocksMined++;
                mod.getData().totalBlocksMined++;
                if (hasMiningChest) {
                    carriedItemCount++;
                    mod.getData().carriedMinedCount = carriedItemCount;
                }
                mod.getData().save();

                // Aplica desgaste e verifica quebra na ferramenta (ferramentas padrão infinitas nunca quebram)
                applyToolWear(entity, world);

                // Efeito sonoro de conquista ao minerar
                world.playSound(null, targetOrePos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        targetOrePos.getX() + 0.5, targetOrePos.getY() + 0.5, targetOrePos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.1);

                // Notificação de marco para o solicitante
                notifyMilestoneIfApplicable(entity, minedState);

                // Se encheu o lote de transporte (32 itens) e tem baú de destino, vai descarregar
                if (hasMiningChest && carriedItemCount >= 32) {
                    isReturningToChest = true;
                }
            }

            targetOrePos = null;
            navigationTarget = null;
        }
    }

    /**
     * Varredura geológica num raio especificado em torno do Jacinto
     */
    private BlockPos scanNearbyOres(ServerWorld world, BlockPos center, int radius) {
        BlockPos bestPos = null;
        double closestDistSq = Double.MAX_VALUE;

        boolean isStone = isConstructionOrStoneTarget();
        int minY = Math.max(world.getBottomY(), center.getY() - 16);
        int maxY = Math.min(world.getTopY(), center.getY() + 16);

        // Se o alvo for pedra/andesito, a busca NUNCA ultrapassa a camada Y=40 (proteção de construções)
        if (isStone && maxY > JacintoClaimHelper.SAFE_SUBTERRANEAN_MAX_Y) {
            maxY = JacintoClaimHelper.SAFE_SUBTERRANEAN_MAX_Y;
        }

        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int y = minY; y <= maxY; y += 2) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (ignoredOres.contains(pos)) continue;

                    // Ignora veios em chunks protegidos por Claims (OPAC / FTB)
                    if (JacintoClaimHelper.isPosClaimed(world, pos)) {
                        ignoredOres.add(pos);
                        continue;
                    }

                    // Ignora rochas/andesito em superfície
                    if (isStone && pos.getY() > JacintoClaimHelper.SAFE_SUBTERRANEAN_MAX_Y) {
                        ignoredOres.add(pos);
                        continue;
                    }

                    BlockState state = world.getBlockState(pos);
                    if (isTargetBlock(state, pos)) {
                        // Verifica se não está completamente imerso em lava
                        if (isSurroundedByLava(world, pos)) {
                            ignoredOres.add(pos);
                            continue;
                        }

                        double d = center.getSquaredDistance(pos);
                        if (d < closestDistSq) {
                            closestDistSq = d;
                            bestPos = pos;
                        }
                    }
                }
            }
        }

        return bestPos;
    }

    /**
     * Localiza a melhor posição de apoio adjacente ao minério (onde há chão sólido e ar para respirar)
     */
    private BlockPos findBestStandingPosition(ServerWorld world, BlockPos orePos, BlockPos entityPos) {
        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : directions) {
            BlockPos candidate = orePos.offset(dir);
            BlockState state = world.getBlockState(candidate);

            // Se for ar, é um ótimo candidato para caminhar
            if (state.isAir()) {
                BlockPos floor = candidate.down();
                if (world.getBlockState(floor).isOpaque() && !world.getBlockState(candidate.up()).isOpaque()) {
                    double dist = entityPos.getSquaredDistance(candidate);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }

        return best != null ? best : orePos;
    }

    /**
     * Mineração exploratória (quando não há veios expostos ou detectados imediatamente por perto)
     */
    private void tickExploratoryMining(ServerWorld world, HuskEntity entity) {
        BlockPos current = entity.getBlockPos();

        // 1. Ajuste de altura ideal de acordo com o minério desejado
        int optimalY = getOptimalMiningY(rawQuery);

        if (current.getY() > optimalY + 2) {
            // Cria escada/degrau seguro para baixo
            BlockPos downStep = current.offset(explorationDirection).down();
            if (canSafeBreak(world, downStep) && canSafeBreak(world, downStep.up())) {
                safeBreakBlock(world, entity, downStep);
                safeBreakBlock(world, entity, downStep.up());
                entity.getNavigation().startMovingTo(downStep.getX() + 0.5, downStep.getY(), downStep.getZ() + 0.5, 1.0);
                return;
            } else {
                explorationDirection = explorationDirection.rotateYClockwise();
                return;
            }
        }

        // 2. No nível ideal, avança minerando um túnel 1x2 na direção da exploração
        BlockPos frontFeet = current.offset(explorationDirection);
        BlockPos frontHead = frontFeet.up();

        if (canSafeBreak(world, frontFeet) && !world.getBlockState(frontFeet).isAir()) {
            safeBreakBlock(world, entity, frontFeet);
        }
        if (canSafeBreak(world, frontHead) && !world.getBlockState(frontHead).isAir()) {
            safeBreakBlock(world, entity, frontHead);
        }

        entity.getNavigation().startMovingTo(frontFeet.getX() + 0.5, frontFeet.getY(), frontFeet.getZ() + 0.5, 1.0);

        // Se encontrou obstáculo intransponível à frente, rotaciona a direção
        if (world.getBlockState(frontFeet).isOf(Blocks.BEDROCK) || world.getBlockState(frontFeet).isOf(Blocks.LAVA)) {
            explorationDirection = explorationDirection.rotateYClockwise();
        }
    }

    /**
     * Detecta se o Jacinto ficou preso em paredes ou blocos durante o trajeto e cava o caminho
     */
    private void checkStuckAndClearObstacle(ServerWorld world, HuskEntity entity, BlockPos target) {
        Vec3d pos = entity.getPos();
        if (lastPosition != null && lastPosition.squaredDistanceTo(pos) < 0.05) {
            stuckTicks++;

            // Se estiver retornando para o baú e ficar preso (especialmente em andar diferente)
            if (isReturningToChest && hasMiningChest && targetChestPos != null) {
                double dy = entity.getY() - targetChestPos.getY();
                double dx = entity.getX() - (targetChestPos.getX() + 0.5);
                double dz = entity.getZ() - (targetChestPos.getZ() + 0.5);
                boolean isWrongFloor = Math.abs(dy) >= 2.0 && (dx * dx + dz * dz) <= 6.0 * 6.0;

                if ((isWrongFloor && stuckTicks >= 80) || stuckTicks >= 120) {
                    BlockPos stand = JacintoOrganizer.findBestStandingPosAdjacent(world, targetChestPos);
                    entity.teleport(world, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Collections.emptySet(), entity.getYaw(), 0.0f);
                    faceBlock(entity, targetChestPos);
                    world.playSound(null, stand, SoundEvents.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.NEUTRAL, 0.8f, 1.2f);
                    world.spawnParticles(ParticleTypes.POOF, stand.getX() + 0.5, stand.getY() + 0.5, stand.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.05);
                    stuckTicks = 0;
                    lastPosition = entity.getPos();
                    return;
                }
            }

            if (stuckTicks % 20 == 0) {
                // Quebra bloco obstrutor na altura dos pés ou da cabeça em direção ao alvo
                Direction dirToTarget = Direction.fromRotation(entity.getYaw());
                BlockPos obsFeet = entity.getBlockPos().offset(dirToTarget);
                BlockPos obsHead = obsFeet.up();

                if (canSafeBreak(world, obsHead) && !world.getBlockState(obsHead).isAir()) {
                    safeBreakBlock(world, entity, obsHead);
                }
                if (canSafeBreak(world, obsFeet) && !world.getBlockState(obsFeet).isAir()) {
                    safeBreakBlock(world, entity, obsFeet);
                }
            }
        } else {
            stuckTicks = 0;
            lastPosition = pos;
        }
    }

    /**
     * Quebra segura de bloco (evita lava e bedrock)
     */
    private void safeBreakBlock(ServerWorld world, HuskEntity entity, BlockPos pos) {
        if (!canSafeBreak(world, pos)) return;
        entity.swingHand(Hand.MAIN_HAND);
        world.breakBlock(pos, true, entity);
    }

    private boolean canSafeBreak(ServerWorld world, BlockPos pos) {
        if (pos == null) return false;
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.isOf(Blocks.BEDROCK)) return false;
        if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.WATER)) return false;

        // Não quebre se houver lava imediatamente acima
        if (world.getBlockState(pos.up()).isOf(Blocks.LAVA)) return false;

        // 1. REGRA DE INTEGRAÇÃO COM MODS DE CLAIM (Open Parties and Claims / FTB Chunks)
        // Se o chunk estiver protegido/claimado por um player ou party, o Jacinto NUNCA quebra nada ali!
        if (JacintoClaimHelper.isPosClaimed(world, pos)) {
            return false;
        }

        // 2. Proteção do Baú de Destino e suporte adjacente
        if (hasMiningChest && targetChestPos != null && pos.isWithinDistance(targetChestPos, 2.5)) {
            return false;
        }

        // 3. Regra Subterrânea para Andesito e Rochas: NUNCA quebra blocos de pedra acima de Y=40
        if (isConstructionOrStoneTarget() && pos.getY() > JacintoClaimHelper.SAFE_SUBTERRANEAN_MAX_Y) {
            return false;
        }

        return state.getHardness(world, pos) >= 0;
    }

    private boolean isSurroundedByLava(ServerWorld world, BlockPos pos) {
        int lavaCount = 0;
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isOf(Blocks.LAVA)) {
                lavaCount++;
            }
        }
        return lavaCount >= 3;
    }

    /**
     * Mantém um grid de 3x3 Chunks Forçados em torno do Jacinto para que ele continue ativo
     * mesmo que nenhum jogador esteja no mapa ou perto dele
     */
    private void updateForcedChunks(ServerWorld world, HuskEntity entity) {
        ChunkPos center = entity.getChunkPos();
        Set<ChunkPos> desired = new HashSet<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                desired.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        // Libera chunks que ficaram distantes
        Iterator<ChunkPos> it = forcedChunks.iterator();
        while (it.hasNext()) {
            ChunkPos cp = it.next();
            if (!desired.contains(cp)) {
                world.setChunkForced(cp.x, cp.z, false);
                it.remove();
            }
        }

        // Força os novos chunks no raio de atuação
        for (ChunkPos cp : desired) {
            if (!forcedChunks.contains(cp)) {
                boolean success = world.setChunkForced(cp.x, cp.z, true);
                if (success) {
                    forcedChunks.add(cp);
                }
            }
        }
    }

    private void releaseForcedChunks(ServerWorld world) {
        if (world == null) return;
        for (ChunkPos cp : forcedChunks) {
            world.setChunkForced(cp.x, cp.z, false);
        }
        forcedChunks.clear();
    }

    /**
     * Garante que o Jacinto esteja com sua picareta equipada (customizada ou padrão infinita)
     */
    private void ensurePickaxeEquipped(HuskEntity entity) {
        if (entity == null) return;
        ItemStack held = entity.getMainHandStack();
        if (held.isEmpty() || !(held.getItem() instanceof MiningToolItem)) {
            ItemStack pickaxe = mod.getData().getEffectivePickaxe();
            entity.equipStack(EquipmentSlot.MAINHAND, pickaxe);
            mod.getData().saveEquipment(EquipmentSlot.MAINHAND, pickaxe);
            entity.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
        }
    }

    /**
     * Aplica desgaste na picareta do Jacinto. Se a picareta quebrar:
     * 1. Limpa a picareta customizada nos dados
     * 2. Reverte automaticamente para a Picareta de Madeira padrão (infinita)
     * 3. Se a picareta de madeira não tiver nível para continuar minerando o minério atual, avisa e pausa.
     */
    private void applyToolWear(HuskEntity entity, ServerWorld world) {
        if (entity == null || world == null) return;
        ItemStack heldTool = entity.getMainHandStack();
        if (heldTool.isEmpty() || !(heldTool.getItem() instanceof MiningToolItem)) return;

        // Ferramentas padrão de madeira com Unbreakable:true nunca desgastam
        if (JacintoData.isDefaultTool(heldTool) || !heldTool.isDamageable()) {
            return;
        }

        // Aplica dano à ferramenta dada pelo jogador
        boolean broke = heldTool.damage(1, world.getRandom(), null);
        if (broke || heldTool.getDamage() >= heldTool.getMaxDamage()) {
            // Efeitos de quebra
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.CRIT,
                    entity.getX(), entity.getY() + 1.0, entity.getZ(),
                    15, 0.2, 0.2, 0.2, 0.1);

            // Descarta a picareta customizada quebrada
            mod.getData().customPickaxeSnbt = "";
            mod.getData().save();

            // Reverte imediatamente para a Picareta de Madeira padrão (infinita)
            ItemStack defaultPick = mod.getData().getEffectivePickaxe();
            entity.equipStack(EquipmentSlot.MAINHAND, defaultPick);
            mod.getData().saveEquipment(EquipmentSlot.MAINHAND, defaultPick);

            String breakNotice = "§6==================================================\n" +
                    "§e[Jacinto Itemakino Baú] §c⚠️ Patrão! A picareta que você me deu quebrou de tanto trabalhar!\n" +
                    "§7Reverti imediatamente para a minha fiel §fPicareta de Madeira Padrão (Infinita)§7 da firma!\n" +
                    "§6==================================================";

            ServerPlayerEntity p = requestingPlayerUuid != null && world.getServer() != null
                    ? world.getServer().getPlayerManager().getPlayer(requestingPlayerUuid) : null;
            if (p != null) {
                p.sendMessage(Text.literal(breakNotice), false);
            } else if (world.getServer() != null) {
                world.getServer().getPlayerManager().broadcast(Text.literal(breakNotice), false);
            }

            // Checa se o minério atual é compatível com a picareta de madeira padrão
            JacintoMiningCompatibility.CompatibilityResult comp = JacintoMiningCompatibility.check(
                    defaultPick, this.targetItem, this.rawQuery, this.targetDisplayName
            );

            if (!comp.canMine) {
                String cantContinueMsg = "§6==================================================\n" +
                        "§e[Jacinto Itemakino Baú] §cMineração de §6[" + comp.targetName + "]§c suspensa!\n" +
                        "§fMotivo: " + comp.explanation + "\n" +
                        "§aMe entregue uma nova picareta de §b" + comp.requiredTierName + "§a ou superior (botão direito em mim) para continuar!\n" +
                        "§7(Consulte a tabela com §e/minerar compatibilidade§7).\n" +
                        "§6==================================================";
                if (p != null) {
                    p.sendMessage(Text.literal(cantContinueMsg), false);
                } else if (world.getServer() != null) {
                    world.getServer().getPlayerManager().broadcast(Text.literal(cantContinueMsg), false);
                }
                stopMining(null);
            }
        }
    }

    private void faceBlock(HuskEntity entity, BlockPos pos) {
        entity.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, Vec3d.ofCenter(pos));
    }

    private int getOptimalMiningY(String query) {
        String n = normalize(query);
        if (n.contains("diaman") || n.contains("redstone") || n.contains("lapis")) {
            return -58; // Camada mais rica de Diamante no Deepslate
        }
        if (n.contains("debris") || n.contains("netherite") || n.contains("detrito")) {
            return 14; // Altura ideal de Netherite
        }
        if (n.contains("ferro") || n.contains("ouro") || n.contains("cobre")) {
            return 16;
        }
        if (n.contains("esmeralda") || n.contains("emerald")) {
            return 100;
        }
        if (n.contains("quartz") || n.contains("quartzo")) {
            return 32;
        }
        if (n.contains("andesit") || n.contains("diorit") || n.contains("granit") || n.contains("pedra") || n.contains("cobble") || n.contains("stone") || n.contains("tufo") || n.contains("tuff")) {
            return 15; // Camada subterrânea profunda e segura (longe de castelos e edificações de base)
        }
        return 40; // Carvão e minérios de superfície
    }

    public boolean isTargetBlock(BlockState state) {
        return isTargetBlock(state, null);
    }

    public boolean isTargetBlock(BlockState state, BlockPos pos) {
        if (state == null || state.isAir()) return false;

        // Proteção de Construções: NUNCA considera blocos de pedra/construção acima de Y=40
        if (pos != null && isConstructionOrStoneTarget() && pos.getY() > JacintoClaimHelper.SAFE_SUBTERRANEAN_MAX_Y) {
            return false;
        }

        // Proteção de Claims: NUNCA considera blocos em chunks protegidos por Claims
        if (pos != null && world != null && JacintoClaimHelper.isPosClaimed(world, pos)) {
            return false;
        }

        Block block = state.getBlock();

        // 1. Se o targetItem for um BlockItem (ex: Andesito, Pedra, Pedregulho, Granito, Diorito, Tufo, etc.)
        if (targetItem instanceof BlockItem bi) {
            if (bi.getBlock() == block) return true;
        }

        // 2. Mapeamento de minérios quando targetItem for lingote/gema/bruto
        if (targetItem == Items.DIAMOND || targetItem == Items.DIAMOND_ORE || targetItem == Items.DEEPSLATE_DIAMOND_ORE) {
            return block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE;
        }
        if (targetItem == Items.RAW_IRON || targetItem == Items.IRON_INGOT || targetItem == Items.IRON_ORE || targetItem == Items.DEEPSLATE_IRON_ORE) {
            return block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE || block == Blocks.RAW_IRON_BLOCK;
        }
        if (targetItem == Items.RAW_GOLD || targetItem == Items.GOLD_INGOT || targetItem == Items.GOLD_ORE || targetItem == Items.DEEPSLATE_GOLD_ORE || targetItem == Items.NETHER_GOLD_ORE) {
            return block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE || block == Blocks.RAW_GOLD_BLOCK;
        }
        if (targetItem == Items.COAL || targetItem == Items.COAL_ORE || targetItem == Items.DEEPSLATE_COAL_ORE) {
            return block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE;
        }
        if (targetItem == Items.RAW_COPPER || targetItem == Items.COPPER_INGOT || targetItem == Items.COPPER_ORE || targetItem == Items.DEEPSLATE_COPPER_ORE) {
            return block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE || block == Blocks.RAW_COPPER_BLOCK;
        }
        if (targetItem == Items.REDSTONE || targetItem == Items.REDSTONE_ORE || targetItem == Items.DEEPSLATE_REDSTONE_ORE) {
            return block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE;
        }
        if (targetItem == Items.EMERALD || targetItem == Items.EMERALD_ORE || targetItem == Items.DEEPSLATE_EMERALD_ORE) {
            return block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE;
        }
        if (targetItem == Items.LAPIS_LAZULI || targetItem == Items.LAPIS_ORE || targetItem == Items.DEEPSLATE_LAPIS_ORE) {
            return block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE;
        }
        if (targetItem == Items.ANCIENT_DEBRIS || targetItem == Items.NETHERITE_INGOT || targetItem == Items.NETHERITE_SCRAP) {
            return block == Blocks.ANCIENT_DEBRIS;
        }
        if (targetItem == Items.QUARTZ || targetItem == Items.NETHER_QUARTZ_ORE) {
            return block == Blocks.NETHER_QUARTZ_ORE;
        }
        if (targetItem == Items.AMETHYST_SHARD || targetItem == Items.AMETHYST_CLUSTER || targetItem == Items.AMETHYST_BLOCK) {
            return block == Blocks.AMETHYST_CLUSTER || block == Blocks.AMETHYST_BLOCK;
        }

        // 3. Fallback inteligente via isMatchingOre
        return isMatchingOre(state, rawQuery);
    }

    private void finishMiningChestFull(HuskEntity entity, ServerWorld world) {
        this.isMining = false;
        this.hasMiningChest = false;
        this.isReturningToChest = false;
        this.targetOrePos = null;
        this.navigationTarget = null;
        releaseForcedChunks(world);
        mod.getData().setIdleTask();
        mod.getNpc().setGlowingSilhouette(false, null);
        if (entity != null) {
            entity.getNavigation().stop();
            // Reverte a mão principal para a Espada (padrão ou customizada) ao concluir a tarefa
            ItemStack sword = mod.getData().getEffectiveSword();
            entity.equipStack(EquipmentSlot.MAINHAND, sword);
            mod.getData().saveEquipment(EquipmentSlot.MAINHAND, sword);
        }

        if (targetChestPos != null) {
            world.playSound(null, targetChestPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 1.0f);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetChestPos.getX() + 0.5, targetChestPos.getY() + 1.0, targetChestPos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.1);
        }

        String msg = "§6==================================================\n" +
                "§e[Jacinto Itemakino Baú] §aMissão cumprida, patrão!\n" +
                "§fO baú designado para §6§l[" + targetDisplayName + "]§f foi completamente §a§lENCHIDO§f! 📦🎉\n" +
                "§7Entrando em modo de descanso (IDLE). Me chame com /organizar ou nova tarefa quando precisar!\n" +
                "§6==================================================";

        ServerPlayerEntity p = null;
        if (requestingPlayerUuid != null && world.getServer() != null) {
            p = world.getServer().getPlayerManager().getPlayer(requestingPlayerUuid);
        }
        if (p != null) {
            p.sendMessage(Text.literal(msg), false);
        } else if (world.getServer() != null) {
            world.getServer().getPlayerManager().broadcast(Text.literal(msg), false);
        }
    }

    /**
     * Mapeamento Inteligente: Verifica se um bloco corresponde ao minério solicitado
     */
    public static boolean isMatchingOre(BlockState state, String query) {
        if (state == null || state.isAir()) return false;
        Block block = state.getBlock();
        Identifier id = Registries.BLOCK.getId(block);
        String blockPath = id.getPath().toLowerCase(Locale.ROOT);
        String q = normalize(query);

        if (q.contains("diaman") || q.contains("diamond")) {
            return blockPath.contains("diamond_ore");
        }
        if (q.contains("ferro") || q.contains("iron")) {
            return blockPath.contains("iron_ore") || blockPath.contains("raw_iron");
        }
        if (q.contains("ouro") || q.contains("gold")) {
            return blockPath.contains("gold_ore") || blockPath.contains("nether_gold");
        }
        if (q.contains("carvao") || q.contains("coal")) {
            return blockPath.contains("coal_ore");
        }
        if (q.contains("cobre") || q.contains("copper")) {
            return blockPath.contains("copper_ore");
        }
        if (q.contains("redstone")) {
            return blockPath.contains("redstone_ore");
        }
        if (q.contains("esmeralda") || q.contains("emerald")) {
            return blockPath.contains("emerald_ore");
        }
        if (q.contains("lapis")) {
            return blockPath.contains("lapis_ore");
        }
        if (q.contains("netherite") || q.contains("debris") || q.contains("detrito")) {
            return blockPath.contains("ancient_debris");
        }
        if (q.contains("quartzo") || q.contains("quartz")) {
            return blockPath.contains("quartz_ore");
        }
        if (q.contains("amethyst") || q.contains("ametista")) {
            return blockPath.contains("amethyst");
        }

        // Genérico: se o jogador pediu um minério específico de mod ou bloco com nome correspondente
        if (blockPath.contains(q) && (blockPath.contains("ore") || blockPath.contains("debris") || blockPath.contains("raw"))) {
            return true;
        }

        // Caso tenha referenciado um bloco de construção/pedra específico (ex: obsidian, stone, deepslate, etc.)
        if (blockPath.equals(q) || blockPath.replace("_", " ").equals(q) || blockPath.contains(q)) {
            return true;
        }

        return false;
    }

    public static String resolveQueryFromItem(Item item) {
        if (item == null || item == Items.AIR) return "";
        if (item == Items.DIAMOND || item == Items.DIAMOND_ORE || item == Items.DEEPSLATE_DIAMOND_ORE || item == Items.DIAMOND_BLOCK) {
            return "diamante";
        }
        if (item == Items.RAW_IRON || item == Items.IRON_INGOT || item == Items.IRON_ORE || item == Items.DEEPSLATE_IRON_ORE || item == Items.RAW_IRON_BLOCK || item == Items.IRON_BLOCK) {
            return "ferro";
        }
        if (item == Items.RAW_GOLD || item == Items.GOLD_INGOT || item == Items.GOLD_ORE || item == Items.DEEPSLATE_GOLD_ORE || item == Items.NETHER_GOLD_ORE || item == Items.RAW_GOLD_BLOCK || item == Items.GOLD_BLOCK) {
            return "ouro";
        }
        if (item == Items.COAL || item == Items.COAL_ORE || item == Items.DEEPSLATE_COAL_ORE || item == Items.COAL_BLOCK) {
            return "carvao";
        }
        if (item == Items.RAW_COPPER || item == Items.COPPER_INGOT || item == Items.COPPER_ORE || item == Items.DEEPSLATE_COPPER_ORE || item == Items.RAW_COPPER_BLOCK || item == Items.COPPER_BLOCK) {
            return "cobre";
        }
        if (item == Items.REDSTONE || item == Items.REDSTONE_ORE || item == Items.DEEPSLATE_REDSTONE_ORE || item == Items.REDSTONE_BLOCK) {
            return "redstone";
        }
        if (item == Items.EMERALD || item == Items.EMERALD_ORE || item == Items.DEEPSLATE_EMERALD_ORE || item == Items.EMERALD_BLOCK) {
            return "esmeralda";
        }
        if (item == Items.LAPIS_LAZULI || item == Items.LAPIS_ORE || item == Items.DEEPSLATE_LAPIS_ORE || item == Items.LAPIS_BLOCK) {
            return "lapis";
        }
        if (item == Items.ANCIENT_DEBRIS || item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP || item == Items.NETHERITE_BLOCK) {
            return "debris";
        }
        if (item == Items.QUARTZ || item == Items.NETHER_QUARTZ_ORE || item == Items.QUARTZ_BLOCK) {
            return "quartz";
        }
        if (item == Items.AMETHYST_SHARD || item == Items.AMETHYST_CLUSTER || item == Items.AMETHYST_BLOCK) {
            return "amethyst";
        }
        return Registries.ITEM.getId(item).getPath().replace("_", " ");
    }

    private String resolveDisplayName(String query) {
        return resolveDisplayName(query, null);
    }

    private String resolveDisplayName(String query, String fallbackCustomName) {
        String q = normalize(query);
        if (q.contains("diaman") || q.contains("diamond")) return "Diamante";
        if (q.contains("ferro") || q.contains("iron")) return "Ferro";
        if (q.contains("ouro") || q.contains("gold")) return "Ouro";
        if (q.contains("carvao") || q.contains("coal")) return "Carvão";
        if (q.contains("cobre") || q.contains("copper")) return "Cobre";
        if (q.contains("redstone")) return "Redstone";
        if (q.contains("esmeralda") || q.contains("emerald")) return "Esmeralda";
        if (q.contains("lapis")) return "Lápis-Lazúli";
        if (q.contains("netherite") || q.contains("debris") || q.contains("detrito")) return "Detritos Ancestrais (Netherite)";
        if (q.contains("quartzo") || q.contains("quartz")) return "Quartzo";
        if (q.contains("amethyst") || q.contains("ametista")) return "Ametista";
        if (q.contains("andesit")) return "Andesito";
        if (q.contains("diorit")) return "Diorito";
        if (q.contains("granit")) return "Granito";
        if (fallbackCustomName != null && !fallbackCustomName.isBlank()) {
            return fallbackCustomName;
        }
        return query;
    }

    private void notifyMilestoneIfApplicable(HuskEntity entity, BlockState state) {
        if (sessionBlocksMined % 10 == 0 || sessionBlocksMined == 1) {
            String quote = "§e[Jacinto Itemakino Baú] §aMais um veio extraído com sucesso! (§e" + sessionBlocksMined + " " + targetDisplayName + "s minerados§a nesta sessão).";
            if (requestingPlayerUuid != null && entity.getServer() != null) {
                ServerPlayerEntity p = entity.getServer().getPlayerManager().getPlayer(requestingPlayerUuid);
                if (p != null) {
                    p.sendMessage(Text.literal(quote), false);
                    return;
                }
            }
            if (entity.getServer() != null && sessionBlocksMined % 20 == 0) {
                entity.getServer().getPlayerManager().broadcast(Text.literal(quote), false);
            }
        }
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}
