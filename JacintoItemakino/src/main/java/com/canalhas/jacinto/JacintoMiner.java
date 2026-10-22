package com.canalhas.jacinto;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.HuskEntity;
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
    private UUID requestingPlayerUuid = null;

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

    /**
     * Inicia a mineração passiva do item desejado
     */
    public boolean tryStartMining(ServerPlayerEntity player, String query) {
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
        this.targetDisplayName = resolveDisplayName(this.rawQuery);
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
            player.sendMessage(Text.literal("§fIniciando mineração passiva de: §6§l[" + targetDisplayName + "]§f!"), false);
            player.sendMessage(Text.literal("§7- Scanner geológico ativado (varredura contínua de até 32 blocos)."), false);
            player.sendMessage(Text.literal("§7- Sistema anti-despawn e 3x3 chunks forçados (trabalho contínuo mesmo longe dos players)."), false);
            player.sendMessage(Text.literal("§7- Use §e/minerar stop§7 a qualquer momento para eu encerrar o turno!"), false);
            player.sendMessage(Text.literal("§6=================================================="), false);
        }

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

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity != null && entity.isAlive()) {
            entity.getNavigation().stop();
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

        this.rawQuery = data.miningTarget != null ? data.miningTarget : "diamante";
        this.targetDisplayName = resolveDisplayName(this.rawQuery);
        this.isMining = true;
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

        LOG.info("[JacintoMiner] Retomando mineração passiva de [{}]...", targetDisplayName);
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

        // 3. Validação do Alvo Atual
        if (targetOrePos != null) {
            BlockState state = world.getBlockState(targetOrePos);
            if (!isMatchingOre(state, rawQuery)) {
                // Bloco já foi minerado ou sumiu
                targetOrePos = null;
                navigationTarget = null;
                digProgressTicks = 0;
            }
        }

        // 4. Se não tem alvo, ativa o scanner de 32 blocos
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
                    // Sem minérios no raio: avança no estilo túnel / prospeção
                    tickExploratoryMining(world, entity);
                }
            }
            return;
        }

        // 5. Com alvo definido: Aproximação e Mineração
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

        // 6. Dentro do alcance: Para e Executa a Escavação
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
            BlockState minedState = world.getBlockState(targetOrePos);

            // Quebra o bloco com drop baseado na ferramenta do Jacinto
            boolean broken = world.breakBlock(targetOrePos, true, entity);
            if (broken) {
                sessionBlocksMined++;
                mod.getData().totalBlocksMined++;
                mod.getData().save();

                // Efeito sonoro de conquista ao minerar
                world.playSound(null, targetOrePos, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        targetOrePos.getX() + 0.5, targetOrePos.getY() + 0.5, targetOrePos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.1);

                // Notificação de marco para o solicitante
                notifyMilestoneIfApplicable(entity, minedState);
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

        int minY = Math.max(world.getBottomY(), center.getY() - 16);
        int maxY = Math.min(world.getTopY(), center.getY() + 16);

        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int y = minY; y <= maxY; y += 2) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    if (ignoredOres.contains(pos)) continue;

                    BlockState state = world.getBlockState(pos);
                    if (isMatchingOre(state, rawQuery)) {
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
            safeBreakBlock(world, entity, downStep);
            safeBreakBlock(world, entity, downStep.up());
            entity.getNavigation().startMovingTo(downStep.getX() + 0.5, downStep.getY(), downStep.getZ() + 0.5, 1.0);
            return;
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
            if (stuckTicks >= 20) {
                stuckTicks = 0;
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
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        if (state.isOf(Blocks.BEDROCK)) return false;
        if (state.isOf(Blocks.LAVA) || state.isOf(Blocks.WATER)) return false;

        // Não quebre se houver lava imediatamente acima
        if (world.getBlockState(pos.up()).isOf(Blocks.LAVA)) return false;

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
     * Garante que o Jacinto esteja com uma picareta equipada
     */
    private void ensurePickaxeEquipped(HuskEntity entity) {
        if (entity == null) return;
        ItemStack held = entity.getMainHandStack();
        if (held.isEmpty() || !(held.getItem() instanceof MiningToolItem)) {
            // Restaura equipamento salvo se for picareta, senão concede Picareta de Diamante
            ItemStack saved = mod.getData().getSavedEquipment(EquipmentSlot.MAINHAND);
            if (!saved.isEmpty() && saved.getItem() instanceof MiningToolItem) {
                entity.equipStack(EquipmentSlot.MAINHAND, saved.copy());
            } else {
                ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
                entity.equipStack(EquipmentSlot.MAINHAND, pickaxe);
                mod.getData().saveEquipment(EquipmentSlot.MAINHAND, pickaxe);
            }
            entity.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.0f);
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
        return 40; // Carvão e minérios de superfície
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

        // Genérico: se o jogador pediu um minério específico de mod ou bloco com nome correspondente
        return blockPath.contains(q) && (blockPath.contains("ore") || blockPath.contains("debris") || blockPath.contains("raw"));
    }

    private String resolveDisplayName(String query) {
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
