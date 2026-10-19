package com.canalhas.cummins;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.CatSitOnBlockGoal;
import net.minecraft.entity.ai.goal.GoToBedAndSleepGoal;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Gerencia a presença física do Senhor Cummins como um gato sábio, amigável e imortal no mundo do Minecraft.
 * Ele acompanha ativamente o jogador, anda ao redor explorando o perímetro ao invés de ficar apenas sentado,
 * se teleporta quando necessário e provê contexto visual/situacional à IA.
 */
public class CumminsBody {
    public static final String BODY_TAG = "senhor_cummins_body";

    private final CumminsMod mod;
    private MinecraftServer server;
    private UUID catUuid;
    private String lastFocusPlayerName;
    private int tickCounter = 0;
    private long lastProximityCommentAt = 0;
    private long lastWanderAt = 0;
    private long lastPurrAt = 0;
    private int zoomiesTicksRemaining = 0;
    private long lastGiftAt = System.currentTimeMillis();
    private long lastIdleGiftCheckAt = 0;
    private boolean lastFocusSneaking = false;
    private final List<Long> sneakHistory = new ArrayList<>();
    private long lastTbagAt = 0;
    private long petPauseUntil = 0;
    private final Random random = new Random();

    // Estado do Modo Scanner (guiar dentro de um raio perto do player até o ponto mais próximo possível e sentar olhando pra direção por até 2 minutos)
    private static final double MAX_GUIDE_RADIUS = 8.5;
    private static final double RESUME_GUIDE_RADIUS = 6.2;

    private BlockPos scanTargetPos = null;
    private BlockPos scanStandPos = null;
    private String scanQueryLabel = null;
    private String scanItemPt = null;
    private String scanItemEn = null;
    private int scanItemCount = 1;
    private String scanContainerDesc = null;
    private long scanStartedAt = 0;
    private long scanExpireAt = 0;
    private long scanSatDownAt = 0;
    private boolean scanWaitingSitting = false;
    private boolean scanInContainer = false;
    private boolean scanPausedWaitingForPlayer = false;
    private boolean scanDirectionAnnounced = false;
    private double bestCatDistToTargetSq = Double.MAX_VALUE;
    private long lastProgressTowardTargetAt = 0;

    public CumminsBody(CumminsMod mod) {
        this.mod = mod;
    }

    public boolean isInScanSitMode() {
        return scanTargetPos != null && System.currentTimeMillis() < scanExpireAt && scanWaitingSitting;
    }

    public void clearScanGuidance(CatEntity cat) {
        scanTargetPos = null;
        scanStandPos = null;
        scanQueryLabel = null;
        scanItemPt = null;
        scanItemEn = null;
        scanItemCount = 1;
        scanContainerDesc = null;
        scanStartedAt = 0;
        scanExpireAt = 0;
        scanSatDownAt = 0;
        scanWaitingSitting = false;
        scanInContainer = false;
        scanPausedWaitingForPlayer = false;
        scanDirectionAnnounced = false;
        bestCatDistToTargetSq = Double.MAX_VALUE;
        lastProgressTowardTargetAt = 0;
        if (cat != null) {
            cat.setSitting(false);
            cat.setInSittingPose(false);
            var followAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
            if (followAttr != null) followAttr.setBaseValue(16.0D);
            var speedAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(0.34D);
        }
    }

    public void startScanGuidance(ServerPlayerEntity player, BlockPos targetPos, BlockPos standPos, boolean inContainer) {
        startScanGuidance(player, targetPos, standPos, inContainer, null, null, null, 1, null);
    }

    public void startScanGuidance(ServerPlayerEntity player, BlockPos targetPos, BlockPos standPos, boolean inContainer, String queryLabel) {
        startScanGuidance(player, targetPos, standPos, inContainer, queryLabel, null, null, 1, null);
    }

    /**
     * Faz o gato guiar o jogador mantendo-se sempre dentro de um raio próximo ao player até chegar na frente do baú
     * ou no ponto mais próximo possível andando (ex: minério soterrado), onde senta olhando para a direção do item.
     */
    public void startScanGuidance(ServerPlayerEntity player, BlockPos targetPos, BlockPos standPos, boolean inContainer,
                                  String queryLabel, String itemPt, String itemEn, int itemCount, String containerDesc) {
        if (player == null || targetPos == null) return;
        setFocusPlayer(player.getGameProfile().getName());
        this.scanTargetPos = targetPos.toImmutable();
        this.scanStandPos = (standPos != null ? standPos : targetPos).toImmutable();
        this.scanQueryLabel = queryLabel;
        this.scanItemPt = itemPt;
        this.scanItemEn = itemEn;
        this.scanItemCount = Math.max(1, itemCount);
        this.scanContainerDesc = containerDesc;
        long now = System.currentTimeMillis();
        this.scanStartedAt = now;
        this.scanExpireAt = now + 120_000L; // Máximo 2 minutos
        this.scanSatDownAt = 0;
        this.scanWaitingSitting = false;
        this.scanInContainer = inContainer;
        this.scanPausedWaitingForPlayer = false;
        this.scanDirectionAnnounced = false;

        CatEntity cat = getCat();
        if (cat == null) {
            cat = spawnNear(player);
        }
        if (cat == null) return;

        // Se o gato estiver em outra dimensão ou longe do jogador no início do scan, traz para perto do jogador para começar a guiá-lo dali
        if (cat.getWorld() != player.getWorld() || cat.squaredDistanceTo(player) > 12.0 * 12.0) {
            teleportToPlayer(cat, player);
        }

        var followAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(64.0D);
        }
        var speedAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.38D);
        }

        this.bestCatDistToTargetSq = cat.squaredDistanceTo(
                this.scanTargetPos.getX() + 0.5,
                this.scanTargetPos.getY() + 0.5,
                this.scanTargetPos.getZ() + 0.5
        );
        this.lastProgressTowardTargetAt = now;

        cat.setSitting(false);
        cat.setInSittingPose(false);
        cat.setInSleepingPose(false);
        cat.playSound(SoundEvents.ENTITY_CAT_AMBIENT, 1.0f, 1.15f);
        moveTowardScanStandPos(cat, player);
    }

    /**
     * Reativa e estende a orientação quando o jogador diz que não achou o item.
     * O gato olha diretamente para o baú/container exato, senta e emite partículas brilhantes.
     */
    public void reactivateScanGuidance(ServerPlayerEntity player, PlayerScanRecord record) {
        if (player == null || record == null) return;
        setFocusPlayer(player.getGameProfile().getName());
        this.scanTargetPos = record.containerPos.toImmutable();
        this.scanStandPos = (record.standPos != null ? record.standPos : record.containerPos).toImmutable();
        this.scanQueryLabel = record.matchedItemNamePt;
        this.scanItemPt = record.matchedItemNamePt;
        this.scanItemEn = record.matchedItemNameEn;
        this.scanItemCount = Math.max(1, record.itemCount);
        this.scanContainerDesc = record.containerDescription;
        long now = System.currentTimeMillis();
        this.scanStartedAt = now;
        this.scanExpireAt = now + 120_000L; // +2 minutos
        this.scanSatDownAt = now;
        this.scanWaitingSitting = true;
        this.scanInContainer = record.inContainer;
        this.scanPausedWaitingForPlayer = false;
        this.scanDirectionAnnounced = true;

        CatEntity cat = getCat();
        if (cat == null) cat = spawnNear(player);
        if (cat == null) return;

        if (cat.getWorld() != player.getWorld() || cat.squaredDistanceTo(player) > 16.0 * 16.0) {
            teleportToPlayer(cat, player);
        }

        double tx = scanTargetPos.getX() + 0.5;
        double ty = scanTargetPos.getY() + 0.5;
        double tz = scanTargetPos.getZ() + 0.5;

        cat.getNavigation().stop();
        cat.setVelocity(Vec3d.ZERO);
        cat.setSitting(true);
        cat.setInSittingPose(true);
        faceAndLookAt(cat, tx, ty, tz);
        cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.2f);

        if (cat.getWorld() instanceof ServerWorld sw) {
            Vec3d catEye = new Vec3d(cat.getX(), cat.getY() + 0.45, cat.getZ());
            Vec3d toTarget = new Vec3d(tx, ty, tz).subtract(catEye);
            double dist = toTarget.length();
            if (dist > 0.2) {
                Vec3d norm = toTarget.normalize();
                for (double d = 0.3; d <= dist; d += 0.4) {
                    Vec3d pt = catEye.add(norm.multiply(d));
                    sw.spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 2, 0.05, 0.05, 0.05, 0.01);
                    sw.spawnParticles(ParticleTypes.WAX_ON, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.01);
                }
            }
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, tx, ty + 0.5, tz, 25, 0.4, 0.4, 0.4, 0.08);
            sw.spawnParticles(ParticleTypes.GLOW, tx, ty + 0.65, tz, 15, 0.3, 0.3, 0.3, 0.03);
            sw.spawnParticles(ParticleTypes.HEART, tx, ty + 0.9, tz, 6, 0.2, 0.2, 0.2, 0.05);
        }
    }

    /**
     * Navega em direção ao scanStandPos utilizando o MobNavigation nativo do Minecraft.
     * O pathfinder explora a malha de blocos do jogo para desviar de paredes, passar por portas
     * e subir escadas/lajes naturalmente. O controle de proximidade com o jogador é mantido no loop principal.
     */
    private void moveTowardScanStandPos(CatEntity cat, ServerPlayerEntity focus) {
        if (cat == null || scanStandPos == null || !(cat.getWorld() instanceof ServerWorld sw)) return;
        double tx = scanStandPos.getX() + 0.5;
        double ty = scanStandPos.getY();
        double tz = scanStandPos.getZ() + 0.5;

        // Se a navegação já está em andamento, não recalcula todo tick desnecessariamente
        if (!cat.getNavigation().isIdle() && tickCounter % 15 != 0) {
            return;
        }

        cat.getNavigation().startMovingTo(tx, ty, tz, 1.35D);
    }

    public void start(MinecraftServer server) {
        this.server = server;
        String savedUuid = mod.context().catUuid();
        if (savedUuid != null && !savedUuid.isBlank()) {
            try {
                this.catUuid = UUID.fromString(savedUuid);
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        this.server = null;
    }

    public void setFocusPlayer(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            this.lastFocusPlayerName = playerName;
        }
    }

    public ServerPlayerEntity getFocusPlayer() {
        if (server == null) return null;
        if (lastFocusPlayerName != null) {
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(lastFocusPlayerName);
            if (p != null && p.isAlive()) return p;
        }
        List<ServerPlayerEntity> list = server.getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    public CatEntity getCat() {
        if (server == null) return null;
        CatEntity primary = null;
        if (catUuid != null) {
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(catUuid);
                if (e instanceof CatEntity cat && cat.isAlive()) {
                    primary = cat;
                    break;
                }
            }
        }
        // Varre todos os mundos para achar o gato e eliminar imediatamente qualquer duplicata
        for (ServerWorld world : server.getWorlds()) {
            List<CatEntity> toDiscard = new ArrayList<>();
            for (Entity e : world.iterateEntities()) {
                if (e instanceof CatEntity cat && cat.isAlive() && cat.getCommandTags().contains(BODY_TAG)) {
                    if (primary == null) {
                        primary = cat;
                        this.catUuid = cat.getUuid();
                        mod.context().setCatUuid(catUuid.toString());
                        mod.context().save();
                    } else if (!cat.getUuid().equals(primary.getUuid())) {
                        toDiscard.add(cat);
                    }
                }
            }
            for (CatEntity dup : toDiscard) {
                if (dup.hasVehicle()) dup.stopRiding();
                dup.discard();
            }
        }
        return primary;
    }

    public void onTick() {
        if (server == null || !mod.config().body.enabled) return;
        tickCounter++;

        // Se algum jogador estiver segurando o Cummins na mão (CarryOn), não spawna nem teleporta!
        if (isCarriedByAnyPlayer(BODY_TAG)) {
            if (tickCounter % 10 == 0) {
                // Se já existe na mão do player e por acaso sobrou algum no chão, remove o do chão
                CatEntity stray = getCat();
                if (stray != null) {
                    if (stray.hasVehicle()) stray.stopRiding();
                    stray.discard();
                }
            }
            return;
        }

        CatEntity cat = getCat();
        if (cat != null) {
            if (isInScanSitMode()) {
                if (!cat.isSitting()) cat.setSitting(true);
                if (!cat.isInSittingPose()) cat.setInSittingPose(true);
            } else {
                // Garante todo tick que nenhuma pose de sentar ou dormir trave o gato fora do modo scanner
                if (cat.isSitting()) cat.setSitting(false);
                if (cat.isInSittingPose()) cat.setInSittingPose(false);
            }
            if (cat.isInSleepingPose()) cat.setInSleepingPose(false);

            if (zoomiesTicksRemaining > 0) {
                runZoomiesTick(cat);
            }
        }

        // Roda a lógica de navegação e decisões a cada 5 ticks (0.25s) para movimentação ágil e fluida
        if (tickCounter % 5 != 0) return;

        ServerPlayerEntity focus = getFocusPlayer();
        if (focus == null) return;

        // Detecção de T-bag / Shift spam do jogador
        boolean isSneaking = focus.isSneaking();
        if (isSneaking && !lastFocusSneaking) {
            long nowSneak = System.currentTimeMillis();
            if (cat != null && cat.squaredDistanceTo(focus) <= 6.0 * 6.0) {
                sneakHistory.add(nowSneak);
                sneakHistory.removeIf(t -> nowSneak - t > 2000L);
                if (sneakHistory.size() >= 3 && nowSneak - lastTbagAt > 20_000L) {
                    lastTbagAt = nowSneak;
                    sneakHistory.clear();
                    onTbagDetected(cat, focus);
                }
            }
        }
        lastFocusSneaking = isSneaking;

        if (cat == null) {
            cat = spawnNear(focus);
            if (cat == null) return;
        }

        // Aplica e reforça atributos/limpeza de IA a cada 40 ticks (2s)
        if (tickCounter % 40 == 0) {
            applyAttributes(cat, focus);
        }

        // Se caiu no void
        if (cat.getY() < -55) {
            teleportToPlayer(cat, focus);
            return;
        }

        boolean differentWorld = cat.getWorld() != focus.getWorld();
        double distSq = differentWorld ? Double.MAX_VALUE : cat.squaredDistanceTo(focus);
        double maxDist = mod.config().body.teleportDistanceBlocks;

        // Se o gato estiver dentro de um barco/carrinho:
        if (cat.hasVehicle()) {
            // Desmonta antes de o chunk do barco descarregar (ex: > 14 blocos) ou ao trocar de dimensão
            if (differentWorld || distSq > 14.0 * 14.0) {
                cat.stopRiding();
                teleportToPlayer(cat, focus);
            } else {
                cat.getLookControl().lookAt(focus, 30.0f, 30.0f);
            }
            return;
        }

        long now = System.currentTimeMillis();

        // MODO SCANNER ATIVO: Anda até a frente do baú/item e fica sentado por até 2 minutos até o player chegar perto
        if (scanTargetPos != null && scanStandPos != null) {
            if (differentWorld || now >= scanExpireAt) {
                clearScanGuidance(cat);
            } else {
                handleScanGuidanceTick(cat, focus, now);
                return;
            }
        }

        if (differentWorld || distSq > maxDist * maxDist) {
            boolean wasFar = distSq > (maxDist * 2) * (maxDist * 2);
            teleportToPlayer(cat, focus);

            if (wasFar && (now - lastProximityCommentAt > 180_000L) && Math.random() < mod.config().body.proximityCommentChance) {
                lastProximityCommentAt = now;
                mod.brain().onCatArrivedNear(focus);
            }
        } else if (distSq > 8.0 * 8.0) {
            petPauseUntil = 0; // se o jogador se afastar mais de 8 blocos, retoma o acompanhamento normal
            // Jogador se distanciou: caminha com calma para perto do jogador
            cat.getLookControl().lookAt(focus, 30.0f, 30.0f);
            if (cat.getNavigation().isIdle() || distSq > 12.0 * 12.0) {
                wanderAroundPlayer(cat, focus, 0.55D);
            }
        } else {
            boolean inPetAffection = now < petPauseUntil;
            if (inPetAffection) {
                // Em estado de carinho: senta calmo, para 100% de andar e olha com afeto para o jogador!
                cat.setSitting(true);
                cat.setInSittingPose(true);
                cat.getNavigation().stop();
                cat.setVelocity(Vec3d.ZERO);
                cat.getLookControl().lookAt(focus, 60.0f, 60.0f);
            } else {
                if (cat.isSitting() && !scanWaitingSitting) {
                    cat.setSitting(false);
                    cat.setInSittingPose(false);
                }
                // Se o jogador estiver muito colado (< 1.0 bloco) e andando, dá um passinho sutil de lado
                if (distSq < 1.0 * 1.0) {
                    stepAsideFromPlayer(cat, focus);
                } else if (distSq > 3.2 * 3.2 && cat.getNavigation().isIdle()) {
                    wanderAroundPlayer(cat, focus, 0.38D);
                }
            }
            cat.getLookControl().lookAt(focus, 50.0f, 50.0f);

            // Presentinho do Cummins: só avalia a cada 5 minutos se já passaram pelo menos 45 minutos desde o último presente
            if (now - lastIdleGiftCheckAt > 300_000L) {
                lastIdleGiftCheckAt = now;
                if (now - lastGiftAt > 2_700_000L && random.nextDouble() < 0.15) {
                    deliverGift(cat, focus);
                }
            }

            // Se terminou o percurso anterior ou passou o intervalo de passeio (a cada 2s a 3.5s)
            if (!inPetAffection && cat.getNavigation().isIdle() && (now - lastWanderAt > 2500L)) {
                lastWanderAt = now;
                wanderAroundPlayer(cat, focus, 1.0D);
            }

            // Ronronar ou miado carinhoso ocasional a cada ~25 segundos
            if (now - lastPurrAt > 25_000L && random.nextDouble() < 0.15) {
                lastPurrAt = now;
                cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 0.7f, 1.0f);
            }
        }
    }

    private void handleScanGuidanceTick(CatEntity cat, ServerPlayerEntity focus, long now) {
        double standX = scanStandPos.getX() + 0.5;
        double standY = scanStandPos.getY();
        double standZ = scanStandPos.getZ() + 0.5;
        double targetX = scanTargetPos.getX() + 0.5;
        double targetY = scanTargetPos.getY() + 0.5;
        double targetZ = scanTargetPos.getZ() + 0.5;

        double catDistToStandSq = cat.squaredDistanceTo(standX, standY, standZ);
        double catDistToTargetSq = cat.squaredDistanceTo(targetX, targetY, targetZ);
        double playerDistToTargetSq = focus.squaredDistanceTo(targetX, targetY, targetZ);
        double playerDistToCatSq = focus.squaredDistanceTo(cat);

        // Atualiza progresso real do gato rumo ao item
        if (catDistToTargetSq < bestCatDistToTargetSq - 0.35) {
            bestCatDistToTargetSq = catDistToTargetSq;
            lastProgressTowardTargetAt = now;
        }

        // Fase 1: O gato está guiando o jogador passo a passo
        if (!scanWaitingSitting) {
            boolean reachedExactSpot = catDistToStandSq <= 2.6 * 2.6 || catDistToTargetSq <= 2.8 * 2.8;

            // Se o jogador se distanciou muito (> 16 blocos na direção oposta), o gato volta para buscá-lo
            if (!reachedExactSpot) {
                if (playerDistToCatSq > 16.0 * 16.0 && cat.getWorld() instanceof ServerWorld sw) {
                    scanPausedWaitingForPlayer = false;
                    lastProgressTowardTargetAt = now;
                    cat.setSitting(false);
                    cat.setInSittingPose(false);
                    cat.getNavigation().startMovingTo(focus.getX(), focus.getY(), focus.getZ(), 1.35D);
                    return;
                }

                if (playerDistToCatSq > MAX_GUIDE_RADIUS * MAX_GUIDE_RADIUS) {
                    scanPausedWaitingForPlayer = true;
                } else if (scanPausedWaitingForPlayer && playerDistToCatSq <= RESUME_GUIDE_RADIUS * RESUME_GUIDE_RADIUS) {
                    scanPausedWaitingForPlayer = false;
                }

                if (scanPausedWaitingForPlayer) {
                    // Enquanto espera o jogador alcançar o raio, não conta como travamento de pathfinding!
                    lastProgressTowardTargetAt = now;
                    cat.getNavigation().stop();
                    cat.setSitting(false);
                    cat.setInSittingPose(false);
                    cat.getLookControl().lookAt(focus, 40.0f, 40.0f);
                    if (tickCounter % 20 == 0 && cat.getWorld() instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.5, cat.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
                        if (tickCounter % 60 == 0) {
                            cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 0.9f, 1.15f);
                        }
                    }
                    return;
                }
            }

            // Checagem de chegada ao destino
            double horizDx = standX - cat.getX();
            double horizDz = standZ - cat.getZ();
            double horizDistToStandSq = horizDx * horizDx + horizDz * horizDz;
            int dy = Math.abs(cat.getBlockY() - scanStandPos.getY());

            // Ponto mais próximo possível andando:
            // Só pode ser considerado "mais próximo" se estiver no mesmo nível (dy <= 2) e bem perto horizontalmente (<= 3.5 blocos)
            boolean isNearbyTarget = (catDistToStandSq <= 3.2 * 3.2)
                    || (horizDistToStandSq <= 3.5 * 3.5 && dy <= 2)
                    || (catDistToTargetSq <= 3.2 * 3.2);

            boolean reachedClosestWalkablePoint = !scanPausedWaitingForPlayer
                    && isNearbyTarget
                    && cat.getNavigation().isIdle()
                    && (now - lastProgressTowardTargetAt >= 2500L);

            // Bloqueio real (apenas se após 18s de tentativas contínuas a navegação não conseguiu avançar)
            boolean trulyBlockedFarAway = !scanPausedWaitingForPlayer
                    && (now - lastProgressTowardTargetAt >= 18_000L)
                    && (now - scanStartedAt >= 20_000L)
                    && cat.getNavigation().isIdle();

            if (reachedExactSpot || reachedClosestWalkablePoint || trulyBlockedFarAway) {
                sitAndPointTowardTarget(cat, focus, now, reachedExactSpot);
            } else {
                cat.setSitting(false);
                cat.setInSittingPose(false);
                cat.getLookControl().lookAt(targetX, targetY, targetZ, 30.0f, 30.0f);
                moveTowardScanStandPos(cat, focus);
                if (tickCounter % 10 == 0 && cat.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.5, cat.getZ(), 2, 0.15, 0.15, 0.15, 0.02);
                }
            }
            return;
        }

        // Fase 2: O gato já chegou no destino e está SENTADO olhando diretamente para o item/container
        cat.getNavigation().stop();
        cat.setSitting(true);
        cat.setInSittingPose(true);
        faceAndLookAt(cat, targetX, targetY, targetZ);

        // Efeito visual contínuo e farol de partículas apontando para o container/bloco
        if (tickCounter % 10 == 0 && cat.getWorld() instanceof ServerWorld sw) {
            // Farol radiante de partículas diretamente sobre o baú ou bloco alvo
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetX, targetY + 0.5, targetZ, 6, 0.25, 0.25, 0.25, 0.05);
            sw.spawnParticles(ParticleTypes.GLOW, targetX, targetY + 0.65, targetZ, 4, 0.2, 0.3, 0.2, 0.02);
            sw.spawnParticles(ParticleTypes.END_ROD, targetX, targetY + 0.85, targetZ, 2, 0.1, 0.2, 0.1, 0.01);

            // Feixe de partículas conectando os olhos do gato ao alvo
            Vec3d catEye = new Vec3d(cat.getX(), cat.getY() + 0.45, cat.getZ());
            Vec3d toTarget = new Vec3d(targetX, targetY, targetZ).subtract(catEye);
            double totalDist = toTarget.length();
            if (totalDist > 0.3) {
                Vec3d dirNorm = toTarget.normalize();
                for (double d = 0.5; d < totalDist; d += 0.6) {
                    Vec3d p = catEye.add(dirNorm.multiply(d));
                    sw.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
                    if (tickCounter % 20 == 0) {
                        sw.spawnParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.01);
                    }
                }
            }

            if (tickCounter % 40 == 0) {
                cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 0.9f, 1.15f);
            }
        }

        // Permanece sentado olhando na direção por pelo menos 45s após sentar (ou até o jogador encostar no próprio item/baú), até o limite de 2 min
        boolean playerTouchedTarget = (now - scanSatDownAt > 6000L) && (playerDistToTargetSq <= 2.2 * 2.2);
        boolean playerObservedSittingCat = (now - scanSatDownAt > 45_000L) && (playerDistToTargetSq <= 2.8 * 2.8 || playerDistToCatSq <= 2.5 * 2.5);
        if (playerTouchedTarget || playerObservedSittingCat) {
            if (cat.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7, cat.getZ(), 5, 0.25, 0.25, 0.25, 0.05);
            }
            cat.playSound(SoundEvents.ENTITY_CAT_PURR, 1.0f, 1.0f);
            clearScanGuidance(cat);
        }
    }

    private void sitAndPointTowardTarget(CatEntity cat, ServerPlayerEntity focus, long now, boolean reachedExactSpot) {
        double targetX = scanTargetPos.getX() + 0.5;
        double targetY = scanTargetPos.getY() + 0.5;
        double targetZ = scanTargetPos.getZ() + 0.5;

        scanWaitingSitting = true;
        scanSatDownAt = now;
        cat.getNavigation().stop();
        cat.setVelocity(Vec3d.ZERO);
        cat.setSitting(true);
        cat.setInSittingPose(true);
        faceAndLookAt(cat, targetX, targetY, targetZ);
        cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.1f);

        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, targetX, targetY + 0.5, targetZ, 12, 0.3, 0.3, 0.3, 0.05);
            sw.spawnParticles(ParticleTypes.GLOW, targetX, targetY + 0.65, targetZ, 8, 0.25, 0.3, 0.25, 0.03);
            sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7, cat.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
        }

        if (!scanDirectionAnnounced && focus != null) {
            scanDirectionAnnounced = true;
            String playerName = focus.getGameProfile().getName();
            String dirFromCat = CumminsScanner.getCompassDirectionPt(cat.getBlockPos(), scanTargetPos);
            int dy = scanTargetPos.getY() - cat.getBlockY();
            int distBlocks = (int) Math.max(1, Math.round(Math.sqrt(cat.squaredDistanceTo(targetX, targetY, targetZ))));

            String containerDesc = (scanContainerDesc != null && !scanContainerDesc.isBlank()) ? scanContainerDesc : "no baú";
            String itemPt = (scanItemPt != null && !scanItemPt.isBlank()) ? scanItemPt : scanQueryLabel;
            String itemEn = (scanItemEn != null && !scanItemEn.isBlank()) ? scanItemEn : "";
            String itemDisplay = (itemEn.isBlank() || itemEn.equalsIgnoreCase(itemPt)) ? itemPt : (itemPt + " (" + itemEn + ")");
            String countInfo = scanItemCount > 1 ? " (" + scanItemCount + " unidades)" : "";

            String verticalHint;
            if (dy <= -2) {
                verticalHint = "uns " + Math.abs(dy) + " blocos mais pra baixo";
            } else if (dy >= 2) {
                verticalHint = "uns " + dy + " blocos mais pra cima";
            } else {
                verticalHint = "nessa mesma altura";
            }

            String arrivalMsg;
            if (distBlocks <= 3.5) {
                if (scanInContainer) {
                    arrivalMsg = "Chegamos! Tá guardado " + containerDesc + " bem aqui onde tô olhando! É "
                            + itemDisplay + countInfo + " (botei partículas brilhando nele pra você ver)!";
                } else {
                    arrivalMsg = "Chegamos! Tá bem aqui onde tô olhando: " + itemDisplay + countInfo
                            + " (botei partículas brilhando nele pra você achar)!";
                }
            } else if (distBlocks <= 8) {
                arrivalMsg = "Cheguei bem pertinho! Tô sentado olhando direto pro " + containerDesc + ": tá pra lá (para o "
                        + dirFromCat + ", " + verticalHint + ", a uns " + distBlocks + " blocos na minha frente, com brilho flutuando)!";
            } else {
                arrivalMsg = "O caminho direto tá trancado por parede ou porta, mas o " + itemDisplay + countInfo
                        + " tá pra lá (para o " + dirFromCat + ", " + verticalHint + ", a uns " + distBlocks + " blocos na direção que eu tô olhando)!";
            }

            mod.brain().broadcast(arrivalMsg, playerName);
        }
    }

    private void faceAndLookAt(CatEntity cat, double targetX, double targetY, double targetZ) {
        double dx = targetX - cat.getX();
        double dy = targetY - (cat.getY() + cat.getStandingEyeHeight());
        double dz = targetZ - cat.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist > 0.001 || Math.abs(dy) > 0.001) {
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizDist)));
            cat.setYaw(yaw);
            cat.setBodyYaw(yaw);
            cat.setHeadYaw(yaw);
            cat.setPitch(pitch);
        }
        cat.getLookControl().lookAt(targetX, targetY, targetZ, 60.0f, 60.0f);
    }

    public void triggerZoomies() {
        CatEntity cat = getCat();
        if (cat == null || !cat.isAlive()) return;
        zoomiesTicksRemaining = 90; // ~4.5 segundos a 200 por hora

        var speedAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.75D);
        }

        cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.2f, 1.4f);
        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION, cat.getX(), cat.getY() + 0.5, cat.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
            sw.spawnParticles(ParticleTypes.POOF, cat.getX(), cat.getY() + 0.3, cat.getZ(), 10, 0.3, 0.2, 0.3, 0.1);
        }
        pickZoomiesTarget(cat);
    }

    private void runZoomiesTick(CatEntity cat) {
        zoomiesTicksRemaining--;
        if (cat.getWorld() instanceof ServerWorld sw) {
            if (tickCounter % 2 == 0) {
                sw.spawnParticles(ParticleTypes.POOF, cat.getX(), cat.getY() + 0.1, cat.getZ(), 2, 0.15, 0.1, 0.15, 0.02);
            }
        }

        if (zoomiesTicksRemaining % 15 == 0) {
            if (cat.isOnGround()) {
                cat.setVelocity(cat.getVelocity().x * 1.3, 0.42, cat.getVelocity().z * 1.3);
                cat.velocityDirty = true;
            }
            pickZoomiesTarget(cat);
            cat.playSound(SoundEvents.ENTITY_CAT_HURT, 0.3f, 1.8f);
        }

        if (zoomiesTicksRemaining <= 0) {
            var speedAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.34D);
            }
            cat.playSound(SoundEvents.ENTITY_CAT_PURR, 1.0f, 1.0f);
            if (cat.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.6, cat.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private void pickZoomiesTarget(CatEntity cat) {
        ServerPlayerEntity focus = getFocusPlayer();
        Vec3d origin = focus != null ? focus.getPos() : cat.getPos();
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = 4.0 + random.nextDouble() * 6.0;
        int tx = (int) Math.floor(origin.getX() + Math.cos(angle) * radius);
        int tz = (int) Math.floor(origin.getZ() + Math.sin(angle) * radius);
        if (cat.getWorld() instanceof ServerWorld sw) {
            BlockPos target = findSafeGroundNear(new BlockPos(tx, (int) origin.getY(), tz), sw);
            cat.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.8D);
        }
    }

    public void onTbagDetected(CatEntity cat, ServerPlayerEntity player) {
        cat.setSitting(false);
        cat.setInSittingPose(false);
        cat.getLookControl().lookAt(player, 60.0f, 60.0f);
        cat.setVelocity(cat.getVelocity().x, 0.38, cat.getVelocity().z);
        cat.velocityDirty = true;

        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7, cat.getZ(), 8, 0.3, 0.3, 0.3, 0.08);
            sw.spawnParticles(ParticleTypes.NOTE, cat.getX(), cat.getY() + 0.9, cat.getZ(), 5, 0.3, 0.3, 0.3, 0.1);
            cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.2f);
            cat.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.8f, 1.2f);
        }

        String phrase = OfflineTrivia.getRandomTbagPhrase();
        mod.brain().broadcast(phrase, player.getGameProfile().getName());
    }

    public static class GiftResult {
        public final ItemStack stack;
        public final String phrase;

        public GiftResult(ItemStack stack, String phrase) {
            this.stack = stack;
            this.phrase = phrase;
        }
    }

    public void deliverGift(CatEntity cat, ServerPlayerEntity player) {
        if (cat == null || player == null || !(cat.getWorld() instanceof ServerWorld sw)) return;
        lastGiftAt = System.currentTimeMillis();

        GiftResult result = createGiftWithReason(player);

        ItemEntity itemEntity = new ItemEntity(sw, cat.getX(), cat.getY() + 0.4, cat.getZ(), result.stack);
        Vec3d dir = player.getPos().subtract(cat.getPos()).normalize().multiply(0.25).add(0, 0.15, 0);
        itemEntity.setVelocity(dir);
        itemEntity.setPickupDelay(10);
        sw.spawnEntity(itemEntity);

        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.6, cat.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7, cat.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
        cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.1f);

        mod.brain().broadcast(result.phrase, player.getGameProfile().getName());
    }

    private GiftResult createGiftWithReason(ServerPlayerEntity player) {
        String playerName = player.getGameProfile().getName();
        String lowerName = playerName.toLowerCase(Locale.ROOT);

        ItemStack stack = null;
        List<String> phrases = null;

        // 1. Presentes hiper-personalizados para Kikkawa (Pescaria abissal lendária & Aviação militar)
        if (lowerName.contains("kikkawa")) {
            int roll = random.nextInt(100);
            if (roll < 45) {
                // Peixe lendário "Pipira pira"
                stack = new ItemStack(Items.TROPICAL_FISH, 1);
                stack.addEnchantment(Enchantments.LUCK_OF_THE_SEA, 3);
                setCustomNameAndLore(stack, "Pipira pira", Formatting.AQUA,
                        "Espécie mística e bioluminescente das profundezas abissais.",
                        "Pescado por Kikkawa em águas jamais habitadas por seres humanos,",
                        "utilizando apenas paciência lendária e um anzol cego sem nenhuma isca!",
                        "Carrega o frescor dos mares ancestrais e atrai maré mansa ao portador.");
                phrases = List.of(
                        "Ó {player}, olha o que eu descolei lá do fundo daquele lago místico! É o lendário Pipira pira que você pescou no anzol seco sem isca!",
                        "{player}, achei o teu Pipira pira nadando perto da margem! O peixe das águas onde nenhum ser humano jamais pisou, fisgado na pura fé!",
                        "Tava farejando o riacho e achei esse espécime raro pra você, {player}: o grande Pipira pira! Só você pra pescar esse bicho sem isca nenhuma kkkk"
                );
            } else if (roll < 80) {
                // Brinquedo de avião "Teco-Teco" das Guerras (Com utilidade: propulsão aérea Flight 3 para voo com Elytra!)
                stack = new ItemStack(Items.FIREWORK_ROCKET, 3);
                NbtCompound fireworksNbt = stack.getOrCreateSubNbt("Fireworks");
                fireworksNbt.putByte("Flight", (byte) 3);
                stack.addEnchantment(Enchantments.UNBREAKING, 1);
                setCustomNameAndLore(stack, "Avião Teco-Teco das Guerras (Caça W-42 Scout)", Formatting.GOLD,
                        "Réplica em miniatura do clássico biplano teco-teco de reconhecimento militar.",
                        "Equipado com micro-motor a pistão e propulsor de pólvora de alta queima.",
                        "Utilidade: Acione nos céus com sua Elytra para decolar voo veloz como um caça aéreo!",
                        "Feito com carinho para o ás da aviação Kikkawa patrulhar os céus.");
                phrases = List.of(
                        "{player}, achei esse brinquedo de avião teco-teco de guerra na oficina! Ele tem carga propulsora pra você rasgar as nuvens com a Elytra!",
                        "Ó {player}, o Tio Cummins trouxe o lendário caça Teco-Teco W-42 pra tua coleção de aviação! Bota pra voar alto nos céus da firma!",
                        "Toma aí, ás dos céus {player}! Um teco-teco militar com motorzinho a pistão pra dar rasante e fazer reconhecimento aéreo!"
                );
            } else {
                // Instrumento de Voo: Bússola Giroscópica Militar
                stack = new ItemStack(Items.COMPASS, 1);
                stack.addEnchantment(Enchantments.UNBREAKING, 1);
                setCustomNameAndLore(stack, "Bússola Giroscópica do Teco-Teco", Formatting.YELLOW,
                        "Instrumento de navegação clássico resgatado do painel de um caça da Segunda Guerra.",
                        "Aponta o rumo exato da base e da pista de pouso mesmo sob névoa espessa e tempestade.",
                        "Instrumento de voo indispensável para a esquadrilha do piloto Kikkawa.");
                phrases = List.of(
                        "{player}, descolei essa bússola giroscópica de bordo de um teco-teco da guerra! Pra você nunca perder a pista de pouso da base!",
                        "Instrumento de voo militar fresquinho pro meu aviador favorito! Toma aí {player}, bússola giroscópica de caça aéreo!"
                );
            }
        }

        // 2. Presentes hiper-personalizados para ArzanLion (Remetendo a Leão, Força e a cadelinha Vitória)
        else if (lowerName.contains("arzan") || lowerName.contains("lion")) {
            int roll = random.nextInt(100);
            if (roll < 60) {
                // Pão de Dente-de-Leão da Juba Dourada
                stack = new ItemStack(Items.BREAD, 4);
                stack.addEnchantment(Enchantments.UNBREAKING, 1);
                setCustomNameAndLore(stack, "Pão de Dente-de-Leão da Juba Dourada", Formatting.GOLD,
                        "Massa nobre fermentada com trigo selvagem e infusão de pétalas de dente-de-leão.",
                        "Assado na brasa dourada para despertar o vigor, a nobreza e a ferocidade de um leão.",
                        "Exala o aroma caloroso das savanas e o rugido que comanda o respeito de todas as feras.",
                        "Alimento real de ArzanLion, abençoado com o carinho e os latidos alegres da Vitória!");
                phrases = List.of(
                        "ArzanLion, meu bom leão! Assei essa fornada de Pão de Dente-de-Leão com as flores mais amarelas do campo! Forra esse bucho com a força da juba!",
                        "Ó {player}, a Vitória ficou até sentadinha na frente do forno esperando esse Pão de Dente-de-Leão sair! Pega aí, rango reforçado de rei das feras!",
                        "{player}, cheirinho bom de padaria selvagem! Pão quentinho de Dente-de-Leão pro nosso leão alfa do servidor!"
                );
            } else if (roll < 85) {
                // Banquete Carnívoro do Rei Leão
                stack = new ItemStack(Items.COOKED_BEEF, 4);
                stack.addEnchantment(Enchantments.UNBREAKING, 1);
                setCustomNameAndLore(stack, "Corte Nobre do Banquete do Leão", Formatting.RED,
                        "Carne nobre suculenta selada na lenha aromática de carvalho das savanas.",
                        "Proteína pura para rugir alto nas caçadas e defender a alcateia.",
                        "Até a cachorrinha Vitória abana o rabinho sem parar quando sente esse cheiro irresistível.");
                phrases = List.of(
                        "ArzanLion, leão de verdade precisa de carne forte! Toma essa peça grelhada caprichada que descolei pro rei da savana!",
                        "Rango de caçador pro meu parceiro {player}! Come aí pra manter a patrulha e a Vitória bem protegidas!"
                );
            } else {
                // Brasão Dourado do Leão Guardião
                stack = new ItemStack(Items.GOLD_INGOT, 3);
                stack.addEnchantment(Enchantments.LUCK_OF_THE_SEA, 1);
                setCustomNameAndLore(stack, "Brasão Dourado do Leão Guardião", Formatting.YELLOW,
                        "Barra de ouro maciço polido que resplandece como a pelagem de um leão ao entardecer.",
                        "Símbolo ancestral de liderança, coragem e honra da firma CanalhasSMP.");
                phrases = List.of(
                        "Toma aqui, {player}! Um ouro puro polido brilhando igual à juba de um leão ao sol. Presente digno da liderança da firma!"
                );
            }
        }

        // 3. Presentes hiper-personalizados para Asminn / Lucas (Oficina, Peugeot 206 e Infraestrutura de Rede)
        else if (lowerName.contains("asmin") || lowerName.contains("lucas")) {
            int roll = random.nextInt(100);
            if (roll < 45) {
                // Chave Mestra do Peugeot 206
                stack = new ItemStack(Items.IRON_INGOT, 2);
                stack.addEnchantment(Enchantments.EFFICIENCY, 3);
                setCustomNameAndLore(stack, "Chave Mestra de Regulagem do Peugeot 206", Formatting.AQUA,
                        "Forjada nas bancadas pesadas da fábrica de Create pelo próprio Asminn.",
                        "A ferramenta lendária usada para afinar carburadores, turbinas e engrenagens do 206.",
                        "Garante torque máximo e arrancada sem fumaça na subida da serra.");
                phrases = List.of(
                        "Ó {player}, achei tua chave mestra do Peugeot 206 caída perto da bancada! Guarda aí antes que o Richard invente de mexer no carburador kkk",
                        "{player}, ferramenta de calibragem fina pro nosso mecânico-chefe! Com essa chave o 206 canta pneu bonito!"
                );
            } else if (roll < 80) {
                // Fibras Ópticas do Ping 1ms
                stack = new ItemStack(Items.REDSTONE, 8);
                stack.addEnchantment(Enchantments.UNBREAKING, 3);
                setCustomNameAndLore(stack, "Fibras Ópticas do Ping 1ms (8.8.8.8)", Formatting.GREEN,
                        "Condutores quânticos purificados de pulso de luz ultrarrápido.",
                        "Testados contra oscilações e quedas na rota do DNS do Google.",
                        "Símbolo da internet 100% lisa, sem jitter e sem perda de pacotes.");
                phrases = List.of(
                        "{player}, descolei essa fiação de fibra óptica com latência zero pro 8.8.8.8! Pra manter a conexão da firma sempre no talo!",
                        "Toma aí, {player}! Fibras da internet do servidor, blindadas contra qualquer oscilação de upload!"
                );
            } else {
                // Célula Condutora da Central
                stack = new ItemStack(Items.COPPER_INGOT, 4);
                stack.addEnchantment(Enchantments.UNBREAKING, 2);
                setCustomNameAndLore(stack, "Célula Condutora da Central Elétrica", Formatting.GOLD,
                        "Barra maciça de cobre supercondutor para motores a vapor e alta amperagem.",
                        "Peça de primeira linha para expandir a indústria do CanalhasSMP.");
                phrases = List.of(
                        "{player}, achei esses condutores de cobre no almoxarifado! Perfeito pras tuas invenções industriais!"
                );
            }
        }

        // 4. Se não for nenhum dos players acima (ou fallback geral), usa as categorias da firma
        if (stack == null) {
            int categoryRoll = random.nextInt(100);
            if (categoryRoll < 25) {
                // MOTIVO 1: Oficina / Peças do Peugeot 206 / Fábrica
                int itemRoll = random.nextInt(4);
                if (itemRoll == 0) {
                    stack = new ItemStack(Items.IRON_NUGGET, 4);
                    setCustomNameAndLore(stack, "Porcas 13mm do Peugeot 206", Formatting.YELLOW,
                            "Caiu do parachoque na última arrancada.",
                            "Guarde bem antes que o motor desmonte.");
                } else if (itemRoll == 1) {
                    stack = new ItemStack(Items.COPPER_INGOT, 3);
                    setCustomNameAndLore(stack, "Barra de Cobre Condutor", Formatting.GOLD,
                            "Achado na oficina mecânica.",
                            "Excelente para fiação e circuitos do maquinário.");
                } else if (itemRoll == 2) {
                    stack = new ItemStack(Items.IRON_INGOT, 2);
                    setCustomNameAndLore(stack, "Chapa de Aço da Oficina", Formatting.GRAY,
                            "Estava dando sopa perto da bancada da fábrica.",
                            "Boa para reparos pesados e engrenagens.");
                } else {
                    stack = new ItemStack(Items.REDSTONE, 5);
                    setCustomNameAndLore(stack, "Fiação de Redstone Purificada", Formatting.RED,
                            "O Cummins brincou com o fio achando que era novelo.",
                            "Pronta para automatizar o maquinário.");
                }

                phrases = List.of(
                        "Ó {player}, achei essa peça solta perto da oficina. Vê se não caiu de alguma máquina sua!",
                        "Caiu isso aqui do parachoque do Peugeot 206 quando dei ré kkkk toma aí {player}, guarda na caixa de ferramentas!",
                        "{player}, tava inspecionando o chão da fábrica e descolei essa peça pra você não pisar descalço.",
                        "Tava farejando a bancada e achei isso dando sopa. Na firma nada se perde, tudo vira recurso, toma aí {player}!"
                );
            } else if (categoryRoll < 55) {
                // MOTIVO 2: Lanche e Sustento do Trabalhador
                int itemRoll = random.nextInt(5);
                if (itemRoll == 0) {
                    stack = new ItemStack(Items.COOKED_SALMON, 2);
                    setCustomNameAndLore(stack, "Salmão Grelhado na Brasa", Formatting.AQUA,
                            "Pescado fresquinho no riacho pelas garras do Cummins.",
                            "Assado no ponto certo para repor as energias.");
                } else if (itemRoll == 1) {
                    stack = new ItemStack(Items.GOLDEN_CARROT, 2);
                    setCustomNameAndLore(stack, "Cenoura Dourada Energética", Formatting.GOLD,
                            "Rango reforçado da firma.",
                            "Garante fôlego infinito para a maratona de mineração.");
                } else if (itemRoll == 2) {
                    stack = new ItemStack(Items.COOKIE, 5);
                    setCustomNameAndLore(stack, "Biscoito Crocante do Cummins", Formatting.YELLOW,
                            "Crocante e cheio de sabedoria felina.",
                            "Dizem que comer um desses dá sorte nos drops.");
                } else if (itemRoll == 3) {
                    stack = new ItemStack(Items.HONEY_BOTTLE, 1);
                    setCustomNameAndLore(stack, "Frasco de Mel Puro Silvestre", Formatting.GOLD,
                            "Colhido de uma colmeia na floresta.",
                            "Doce revigorante que cura qualquer veneno ou cansaço.");
                } else {
                    stack = new ItemStack(Items.PUMPKIN_PIE, 2);
                    setCustomNameAndLore(stack, "Torta de Abóbora da Firma", Formatting.GOLD,
                            "Sobremesa caprichada do refeitório.",
                            "Perfeita para forrar o bucho do trabalhador.");
                }

                phrases = List.of(
                        "{player}, você tá minerando e construindo há horas sem comer nada direito! Come isso aqui antes que desmaie, pô!",
                        "Pausa pro café da firma, {player}! Pega esse rango reforçado pra aguentar a maratona.",
                        "Fui na beira do rio dar uma pescada rápida e trouxe essa iguaria fresca pro meu trabalhador favorito!",
                        "Toma esse petisco caprichado, {player}! Saco vazio não para em pé e obra não anda sem sustento."
                );
            } else if (categoryRoll < 80) {
                // MOTIVO 3: Escavação / Garimpo Felino
                int itemRoll = random.nextInt(4);
                if (itemRoll == 0) {
                    stack = new ItemStack(Items.AMETHYST_SHARD, 2);
                    setCustomNameAndLore(stack, "Cristal de Ametista Reluzente", Formatting.LIGHT_PURPLE,
                            "Desenterrado das fendas rochosas com as próprias garras.",
                            "Brilha forte e emite um som cristalino suave.");
                } else if (itemRoll == 1) {
                    stack = new ItemStack(Items.LAPIS_LAZULI, 4);
                    setCustomNameAndLore(stack, "Lápis-Lazúli de Encantamento", Formatting.BLUE,
                            "Achado em um veio secreto da caverna.",
                            "Excelente para encantar boas ferramentas na bigorna.");
                } else if (itemRoll == 2) {
                    stack = new ItemStack(Items.RAW_GOLD, 2);
                    setCustomNameAndLore(stack, "Pepita Bruta do Garimpo", Formatting.GOLD,
                            "Desenterrada da terra escura.",
                            "Brilha quase tanto quanto os olhos do Tio Cummins à noite.");
                } else {
                    stack = new ItemStack(Items.EMERALD, 2);
                    setCustomNameAndLore(stack, "Moeda de Câmbio da Diretoria", Formatting.GREEN,
                            "Economizada das diárias da firma.",
                            "Perfeita para negociar com os aldeões sem levar prejuízo.");
                }

                phrases = List.of(
                        "Tava cavando com as unhas perto da caverna e olha o que eu desenterrei brilhando pra você, {player}!",
                        "Achei essa pedrinha reluzente entocada numa fenda escura. Lembrei na hora de você, meu consagrado!",
                        "Olha o que as garras afiadas do Tio Cummins acharam no fundo da terra! É todinho seu, {player}.",
                        "Tava brincando na cascalheira e achei esse brilho aqui. Guarda aí no inventário que dá sorte!"
                );
            } else if (categoryRoll < 95) {
                // MOTIVO 4: Amizade, Carinho e Companheirismo
                int itemRoll = random.nextInt(4);
                if (itemRoll == 0) {
                    stack = new ItemStack(Items.FEATHER, 3);
                    setCustomNameAndLore(stack, "Pena Macia da Sorte", Formatting.WHITE,
                            "Caçada de uma galinha fujona.",
                            "Guardada como amuleto felino de carinho e boa sorte.");
                } else if (itemRoll == 1) {
                    stack = new ItemStack(Items.SWEET_BERRIES, 6);
                    setCustomNameAndLore(stack, "Frutinhas Silvestres Adoçadas", Formatting.RED,
                            "Colhidas pelo caminho enquanto acompanhava seus passos.",
                            "Doces e refrescantes.");
                } else if (itemRoll == 2) {
                    stack = new ItemStack(Items.GLOW_BERRIES, 4);
                    setCustomNameAndLore(stack, "Bagas Luminosas da Caverna", Formatting.GOLD,
                            "Brilham no breu total das noites frias.",
                            "Para iluminar os passos do melhor parceiro da firma.");
                } else {
                    stack = new ItemStack(Items.NAME_TAG, 1);
                    setCustomNameAndLore(stack, "Plaqueta de Identificação da Firma", Formatting.YELLOW,
                            "Achada na gaveta de pertences do Cummins.",
                            "Para batizar com carinho uma máquina, pet ou mascote.");
                }

                phrases = List.of(
                        "Parceria é parceria, {player}! Você cuida de mim, me dá carinho e o Tio Cummins descola os mimos da firma!",
                        "O Tio Cummins reconhece quem é parceiro de verdade nesse servidor. Receba esse agrado da diretoria!",
                        "Você me deu tanto carinho hoje que meu coração felino amoleceu. Toma esse presente aqui, {player}!",
                        "Um agrado especial pro meu sócio mais dedicado desse mundo de blocos! Tamo junto nessa caminhada."
                );
            } else {
                // MOTIVO 5: Relíquia Rara / Emergência (5% de chance)
                boolean isDiamond = random.nextBoolean();
                if (isDiamond) {
                    stack = new ItemStack(Items.DIAMOND, 1);
                    setCustomNameAndLore(stack, "Diamante Bruto da Amizade Suprema", Formatting.AQUA,
                            "O maior tesouro guardado nas gavetas do Tio Cummins.",
                            "Reservado com honra apenas para sócios de fé.");
                } else {
                    stack = new ItemStack(Items.GOLDEN_APPLE, 1);
                    setCustomNameAndLore(stack, "Maçã Dourada Reserva da Firma", Formatting.LIGHT_PURPLE,
                            "Patrimônio sagrado guardado a sete chaves.",
                            "Para salvar sua pele nos momentos mais perigosos!");
                }

                phrases = List.of(
                        "Segura isso bem guardado na mochila, {player}! Se a barra pesar lá fora na escuridão, isso pode salvar sua pele!",
                        "Achei uma relíquia bruta escondida no cofre da firma! Só tô te passando porque nossa amizade não tem preço!",
                        "Presente de peso do Tio Cummins pra você não passar perrengue contra os monstros da noite, {player}!"
                );
            }
        }

        String chosenPhrase = phrases.get(random.nextInt(phrases.size())).replace("{player}", playerName);
        return new GiftResult(stack, chosenPhrase);
    }

    private void setCustomNameAndLore(ItemStack stack, String name, Formatting color, String... loreLines) {
        stack.setCustomName(Text.literal(name).formatted(color));
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        for (String line : loreLines) {
            loreList.add(NbtString.of(Text.Serializer.toJson(Text.literal(line).formatted(Formatting.GRAY, Formatting.ITALIC))));
        }
        display.put("Lore", loreList);
    }

    public void stepAsideFromPlayer(net.minecraft.entity.mob.MobEntity pet, ServerPlayerEntity focus) {
        double dx = pet.getX() - focus.getX();
        double dz = pet.getZ() - focus.getZ();
        double baseAngle = (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01)
                ? random.nextDouble() * 2 * Math.PI
                : Math.atan2(dz, dx);
        double offset = (random.nextBoolean() ? 1 : -1) * (0.25 + random.nextDouble() * 0.35);
        double angle = baseAngle + offset;
        double radius = 1.4 + random.nextDouble() * 0.6; // Passinho suave de lado (1.4 a 2.0 blocos)
        int targetX = (int) Math.floor(focus.getX() + Math.cos(angle) * radius);
        int targetZ = (int) Math.floor(focus.getZ() + Math.sin(angle) * radius);
        BlockPos targetPos = new BlockPos(targetX, focus.getBlockY(), targetZ);

        if (pet.getWorld() instanceof ServerWorld sw) {
            BlockPos safePos = findSafeGroundNear(targetPos, sw);
            pet.getNavigation().startMovingTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, 0.42D);
        }
    }

    private void wanderAroundPlayer(CatEntity cat, ServerPlayerEntity focus, double speed) {
        // Escolhe um ângulo próximo ao lado atual do gato para orbitar ao redor do jogador
        double dx = cat.getX() - focus.getX();
        double dz = cat.getZ() - focus.getZ();
        double currentAngle = Math.atan2(dz, dx);
        double deltaAngle = (random.nextDouble() - 0.5) * (Math.PI * 0.70);
        double angle = currentAngle + deltaAngle;
        double radius = 1.4 + random.nextDouble() * 0.8; // 1.4 a 2.2 blocos (pertinho do player)
        int targetX = (int) Math.floor(focus.getX() + Math.cos(angle) * radius);
        int targetZ = (int) Math.floor(focus.getZ() + Math.sin(angle) * radius);
        BlockPos targetPos = new BlockPos(targetX, focus.getBlockY(), targetZ);

        if (cat.getWorld() instanceof ServerWorld sw) {
            BlockPos safePos = findSafeGroundNear(targetPos, sw);
            cat.getNavigation().startMovingTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, speed);
        }
    }

    public CatEntity spawnNear(ServerPlayerEntity player) {
        releaseFromCarryOn(BODY_TAG);
        CatEntity existing = getCat();
        if (existing != null) {
            if (existing.hasVehicle()) existing.stopRiding();
            existing.discard();
        }

        ServerWorld world = player.getServerWorld();
        BlockPos pos = findSafeGroundAroundPlayer(player.getBlockPos(), world);
        CatEntity cat = EntityType.CAT.create(world);
        if (cat == null) return null;

        cat.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
        applyAttributes(cat, player);
        world.spawnEntity(cat);

        this.catUuid = cat.getUuid();
        mod.context().setCatUuid(catUuid.toString());
        mod.context().save();

        world.spawnParticles(ParticleTypes.PORTAL, cat.getX(), cat.getY() + 0.5, cat.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
        world.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.8, cat.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
        cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.0f);

        CumminsMod.LOG.info("[Cummins] Corpo felino invocado para Senhor Cummins perto de {}", player.getGameProfile().getName());
        return cat;
    }

    public void teleportToPlayer(CatEntity cat, ServerPlayerEntity player) {
        releaseFromCarryOn(BODY_TAG);
        if (cat.hasVehicle()) {
            cat.stopRiding();
        }
        ServerWorld targetWorld = player.getServerWorld();
        BlockPos safePos = findSafeGroundAroundPlayer(player.getBlockPos(), targetWorld);
        double x = safePos.getX() + 0.5;
        double y = safePos.getY();
        double z = safePos.getZ() + 0.5;

        if (cat.getWorld() != targetWorld) {
            cat.teleport(targetWorld, x, y, z, java.util.Set.of(), player.getYaw(), 0);
        } else {
            cat.refreshPositionAndAngles(x, y, z, player.getYaw(), 0);
        }
        cat.setVelocity(Vec3d.ZERO);
        cat.fallDistance = 0;
        cat.setSitting(false);
        cat.setInSittingPose(false);
        cat.setInSleepingPose(false);

        targetWorld.spawnParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 15, 0.3, 0.3, 0.3, 0.1);
        targetWorld.spawnParticles(ParticleTypes.HEART, x, y + 0.8, z, 3, 0.2, 0.2, 0.2, 0.05);
        cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.0f);
    }

    public boolean isCarriedByAnyPlayer(String tag) {
        if (server == null) return false;
        try {
            Class<?> managerClass = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            java.lang.reflect.Method getCarryData = managerClass.getMethod("getCarryData", net.minecraft.entity.player.PlayerEntity.class);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                Object carryData = getCarryData.invoke(null, p);
                if (carryData != null) {
                    boolean carrying = (Boolean) carryData.getClass().getMethod("isCarrying").invoke(carryData);
                    if (carrying) {
                        Object nbt = carryData.getClass().getMethod("getNbt").invoke(carryData);
                        if (nbt != null && nbt.toString().contains(tag)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void releaseFromCarryOn(String tag) {
        if (server == null) return;
        try {
            Class<?> managerClass = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            java.lang.reflect.Method getCarryData = managerClass.getMethod("getCarryData", net.minecraft.entity.player.PlayerEntity.class);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                Object carryData = getCarryData.invoke(null, p);
                if (carryData != null) {
                    boolean carrying = (Boolean) carryData.getClass().getMethod("isCarrying").invoke(carryData);
                    if (carrying) {
                        Object nbt = carryData.getClass().getMethod("getNbt").invoke(carryData);
                        if (nbt != null && nbt.toString().contains(tag)) {
                            carryData.getClass().getMethod("clear").invoke(carryData);
                            managerClass.getMethod("setCarryData", net.minecraft.entity.player.PlayerEntity.class, carryData.getClass())
                                    .invoke(null, p, carryData);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    public void emoteSpeaking() {
        CatEntity cat = getCat();
        if (cat == null || !cat.isAlive()) return;
        if (!isInScanSitMode()) {
            cat.setSitting(false);
            cat.setInSittingPose(false);
        }
        cat.setInSleepingPose(false);
        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.NOTE, cat.getX(), cat.getY() + 0.6, cat.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
            cat.playSound(SoundEvents.ENTITY_CAT_PURR, 0.8f, 1.0f);
        }
    }

    public void onPet(CatEntity cat, ServerPlayerEntity player) {
        clearScanGuidance(cat);
        this.petPauseUntil = System.currentTimeMillis() + 12_000L;
        cat.getNavigation().stop();
        cat.setSitting(false);
        cat.setInSittingPose(false);
        cat.setInSleepingPose(false);
        cat.getLookControl().lookAt(player, 60.0f, 60.0f);
        setFocusPlayer(player.getGameProfile().getName());
        applyAttributes(cat, player);
        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.6, cat.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
            cat.playSound(SoundEvents.ENTITY_CAT_PURREOW, 1.0f, 1.0f);
        }
        // Retribuição de carinho com presente da firma: cooldown de 35 minutos e 15% de chance
        if (System.currentTimeMillis() - lastGiftAt > 2_100_000L && random.nextDouble() < 0.15) {
            deliverGift(cat, player);
        }

        // Noite da Firma: Cummins serve café fresquinho para quem trabalha no turno da madrugada
        if (cat.getWorld().isNight() && mod.cafe() != null && random.nextDouble() < 0.25) {
            mod.cafe().serveCafe(player);
        }

        // 1% de chance de resetar o timer de teleporte ao receber carinho
        checkPetTeleportTimerReset(cat, player);

        mod.brain().onPet(player);
    }

    private void checkPetTeleportTimerReset(CatEntity cat, ServerPlayerEntity player) {
        String playerName = player.getGameProfile().getName();

        if (random.nextDouble() < 0.01) { // 1% de chance a cada carinho (sem limite de tempo)
            mod.teleport().resetCooldown(playerName);

            if (cat.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, cat.getX(), cat.getY() + 0.5, cat.getZ(), 35, 0.4, 0.4, 0.4, 0.1);
                sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, cat.getX(), cat.getY() + 0.7, cat.getZ(), 25, 0.3, 0.4, 0.3, 0.15);
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 1.0, cat.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 1.2f);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, net.minecraft.sound.SoundCategory.PLAYERS, 0.8f, 1.4f);
            }

            String msg = "§6[Cummins]§r *PURRRRR!* ✨💖 Ai que carinho gostoso, ô §e" + playerName 
                    + "§f! Minhas energias cósmicas se recarregaram completamente e me sinto renovado para usar o teleporte novamente! 🐾🌀 (Cooldown resetado!)";
            if (player.getServer() != null) {
                player.getServer().getPlayerManager().broadcast(Text.literal(msg), false);
            }
        }
    }

    public String getSituationalContext() {
        CatEntity cat = getCat();
        if (cat == null || !cat.isAlive()) return null;
        ServerPlayerEntity p = getFocusPlayer();
        if (p == null) return null;

        double dist = Math.sqrt(cat.squaredDistanceTo(p));
        String dimensionName = p.getWorld().getRegistryKey().getValue().getPath();
        String biomeName = p.getWorld().getBiome(p.getBlockPos()).getKey()
                .map(k -> k.getValue().getPath()).orElse("desconhecido");
        ItemStack held = p.getMainHandStack();
        String itemName = (held == null || held.isEmpty()) ? "mãos vazias" : held.getItem().getName().getString();
        int health = (int) p.getHealth();

        StringBuilder sb = new StringBuilder();
        sb.append("SEU CORPO FÍSICO ATUAL (GATO COMPANHEIRO E CONSELHEIRO):\n")
          .append("- Você é corporificado no jogo como um gato sábio, amigável e imortal chamado ").append(mod.config().botName).append(".\n")
          .append("- Você está ao lado do jogador: ").append(p.getGameProfile().getName())
          .append("- Ambiente atual: Dimensão ").append(dimensionName).append(" | Bioma ").append(biomeName)
          .append(" | Altitude Y=").append(p.getBlockY()).append("\n")
          .append("- Condição do jogador: ").append(health).append("/20 HP | Segurando: ").append(itemName).append("\n");
        String targetBlock = getTargetBlockInfo(p);
        if (targetBlock != null) {
            sb.append("- Olhando diretamente para o bloco: ").append(targetBlock).append("\n");
        }
        sb.append("- OBJETIVO DO DIÁLOGO: Seja um companheiro fiel, caloroso e atencioso. Comente sobre onde vocês estão (a caverna, o bioma, a base), repare no que ele está fazendo ou construindo, dê conselhos carinhosos de amigo, e mostre interesse genuíno pelo bem-estar dele!");
        return sb.toString();
    }

    public String getTargetBlockInfo(ServerPlayerEntity p) {
        if (p == null) return null;
        try {
            var hit = p.raycast(6.5D, 1.0F, false);
            if (hit != null && hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK && hit instanceof net.minecraft.util.hit.BlockHitResult bhr) {
                BlockPos pos = bhr.getBlockPos();
                var state = p.getWorld().getBlockState(pos);
                if (!state.isAir()) {
                    String blockName = state.getBlock().getName().getString();
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    return blockName + " [" + blockId + "] em X=" + pos.getX() + " Y=" + pos.getY() + " Z=" + pos.getZ();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static final UUID DUMMY_OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-00000000c0de");

    private void applyAttributes(CatEntity cat, ServerPlayerEntity owner) {
        Formatting color = Formatting.byName(mod.config().nameColor);
        if (color == null || !color.isColor()) color = Formatting.GOLD;
        cat.setCustomName(Text.literal(mod.config().botName).formatted(color));
        cat.setCustomNameVisible(true);
        cat.setInvulnerable(true);
        cat.setPersistent();
        cat.setTamed(true);
        // IMPORTANTE: Em TameableEntity, se getOwner() retornar um player online, getScoreboardTeam() ignora a equipe do pet
        // e retorna a equipe do player (que tem colisão ativada!). Usando um UUID fictício fixo, a coleira continua visível
        // (isTamed = true), mas getScoreboardTeam() usa a equipe "cummins_noclip" (CollisionRule.NEVER) no servidor e no cliente!
        cat.setOwnerUuid(DUMMY_OWNER_UUID);
        if (!isInScanSitMode()) {
            cat.setSitting(false);
            cat.setInSittingPose(false);
        }
        cat.setInSleepingPose(false);
        cat.setCollarColor(resolveCollar(mod.config().body.collarColor));
        CatVariant v = resolveVariant(mod.config().body.variant);
        if (v != null) cat.setVariant(v);
        if (!cat.getCommandTags().contains(BODY_TAG)) cat.addCommandTag(BODY_TAG);

        // Desativa colisão física (empurrão) no servidor e nos clientes via Scoreboard Team CollisionRule.NEVER
        disableEntityCollision(cat, "cummins_noclip", color);

        // Aumenta a velocidade base e alcance de navegação para movimentação ágil e sem engasgos
        var speedAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getBaseValue() < 0.34D) {
            speedAttr.setBaseValue(0.34D);
        }
        var followAttr = cat.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null && followAttr.getBaseValue() < 96.0D) {
            followAttr.setBaseValue(96.0D);
        }

        // Remove permanentemente qualquer Goal que force o pet a sentar, dormir ou colar em cima do dono
        removeUnwantedPetGoals(cat);
    }

    public void disableEntityCollision(Entity entity, String teamName, Formatting teamColor) {
        if (server == null || entity == null) return;
        try {
            net.minecraft.scoreboard.ServerScoreboard sb = server.getScoreboard();
            net.minecraft.scoreboard.Team team = sb.getTeam(teamName);
            if (team == null) {
                team = sb.addTeam(teamName);
            }
            if (team.getCollisionRule() != net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER) {
                team.setCollisionRule(net.minecraft.scoreboard.AbstractTeam.CollisionRule.NEVER);
            }
            if (teamColor != null && team.getColor() != teamColor) {
                team.setColor(teamColor);
            }
            String entry = entity.getEntityName();
            if (sb.getPlayerTeam(entry) != team) {
                sb.addPlayerToTeam(entry, team);
            }
        } catch (Exception ignored) {}
    }

    public void removeUnwantedPetGoals(net.minecraft.entity.mob.MobEntity mob) {
        try {
            Class<?> mobClass = mob.getClass();
            while (mobClass != null && mobClass != Object.class) {
                for (java.lang.reflect.Field field : mobClass.getDeclaredFields()) {
                    if (net.minecraft.entity.ai.goal.GoalSelector.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        net.minecraft.entity.ai.goal.GoalSelector gs = (net.minecraft.entity.ai.goal.GoalSelector) field.get(mob);
                        if (gs != null) {
                            gs.clear(goal -> {
                                String name = goal.getClass().getSimpleName().toLowerCase(Locale.ROOT);
                                return goal instanceof SitGoal
                                        || goal instanceof CatSitOnBlockGoal
                                        || goal instanceof GoToBedAndSleepGoal
                                        || goal instanceof net.minecraft.entity.ai.goal.FollowOwnerGoal
                                        || goal instanceof net.minecraft.entity.ai.goal.TrackOwnerAttackerGoal
                                        || goal instanceof net.minecraft.entity.ai.goal.AttackWithOwnerGoal
                                        || goal instanceof net.minecraft.entity.ai.goal.WanderAroundGoal
                                        || goal instanceof net.minecraft.entity.ai.goal.WanderAroundFarGoal
                                        || name.contains("sit")
                                        || name.contains("sleep")
                                        || name.contains("followowner")
                                        || name.contains("wander");
                            });
                        }
                    }
                }
                mobClass = mobClass.getSuperclass();
            }
        } catch (Exception ignored) {}
    }

    private CatVariant resolveVariant(String name) {
        Identifier id = new Identifier("minecraft", (name == null || name.isBlank()) ? "jellie" : name.toLowerCase(Locale.ROOT).trim());
        CatVariant v = Registries.CAT_VARIANT.get(id);
        return v != null ? v : Registries.CAT_VARIANT.get(new Identifier("minecraft", "jellie"));
    }

    private DyeColor resolveCollar(String color) {
        if (color == null || color.isBlank()) return DyeColor.YELLOW;
        try {
            return DyeColor.byName(color.toLowerCase(Locale.ROOT).trim(), DyeColor.YELLOW);
        } catch (Exception e) {
            return DyeColor.YELLOW;
        }
    }

    private BlockPos findSafeGroundAroundPlayer(BlockPos center, ServerWorld world) {
        // Prioriza distância de 2 a 3 blocos do jogador para não nascer/teleportar em cima dele
        for (int r = 2; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos target = center.add(dx, 0, dz);
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos pos = target.add(0, dy, 0);
                        if (isWalkable(world, pos)) return pos;
                    }
                }
            }
        }
        return findSafeGroundNear(center, world);
    }

    private BlockPos findSafeGroundNear(BlockPos center, ServerWorld world) {
        // Testa o próprio centro primeiro
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = center.add(0, dy, 0);
            if (isWalkable(world, pos)) return pos;
        }
        // Testa raio ao redor
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos target = center.add(dx, 0, dz);
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos pos = target.add(0, dy, 0);
                        if (isWalkable(world, pos)) return pos;
                    }
                }
            }
        }
        return center;
    }

    private boolean isWalkable(ServerWorld world, BlockPos feet) {
        BlockPos floor = feet.down();
        return !world.getBlockState(floor).getCollisionShape(world, floor).isEmpty()
                && world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                && world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty();
    }
}
