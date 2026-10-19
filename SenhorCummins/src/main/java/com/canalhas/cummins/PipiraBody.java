package com.canalhas.cummins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.SalmonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gerencia a presença física do peixe "Pipira" (Magikarp voador místico do Kikkawa).
 * - Lore: O lendário peixe voador do Kikkawa, pescado em águas nunca habitadas pelo ser humano,
 *   que aprendeu a levitar e planar pelos ares através do místico poder do Magikarp Splash!
 * - Voo Místico Suave: Voa e plana graciosamente pelo ar ao lado do ombro do Kikkawa com NoGravity=true,
 *   eliminando em definitivo o som de peixe se debatendo na terra (zero som de flop!).
 * - Comportamento de Aparição: Spawna quando o jogador Kikkawa loga no servidor,
 *   ou quando qualquer jogador o chama pelo nome "Pipira" / "Magikarp" no chat.
 * - Sem colisão com jogadores (CollisionRule.NEVER), imortal e 100% pacífico.
 */
public class PipiraBody {
    public static final String PIPIRA_TAG = "pipira_fish_body";
    public static final String OWNER_NAME = "kikkawa";

    private final CumminsMod mod;
    private MinecraftServer server;
    private UUID fishUuid;
    private String lastFocusPlayerName = OWNER_NAME;
    private boolean activeInSession = false;
    private boolean scaleApplied = false;
    private int tickCounter = 0;
    private long lastGiftAt = System.currentTimeMillis();
    private boolean lastFocusSneaking = false;
    private final List<Long> sneakHistory = new ArrayList<>();
    private long lastTbagAt = 0;
    private long lastPetTime = 0;
    private Vec3d hoverTarget = null;
    private long lastWanderAt = 0;
    private long lastStepAsideAt = 0;
    private long petPauseUntil = 0;
    private boolean manuallyDismissed = false;
    private final Random random = new Random();

    public PipiraBody(CumminsMod mod) {
        this.mod = mod;
    }

    public void start(MinecraftServer server) {
        this.server = server;
        this.activeInSession = false;
        this.scaleApplied = false;
        this.hoverTarget = null;
        this.lastWanderAt = 0;
        this.lastStepAsideAt = 0;
        this.petPauseUntil = 0;
        String savedUuid = mod.context().pipiraUuid();
        if (savedUuid != null && !savedUuid.isBlank()) {
            try {
                this.fishUuid = UUID.fromString(savedUuid);
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        this.server = null;
    }

    public boolean isActiveInSession() {
        return activeInSession;
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
        // Prioriza o dono Kikkawa se estiver online (case-insensitive)
        for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME) && sp.isAlive()) {
                lastFocusPlayerName = sp.getGameProfile().getName();
                return sp;
            }
        }
        List<ServerPlayerEntity> list = server.getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    public SalmonEntity getFish() {
        if (server == null) return null;
        SalmonEntity primary = null;
        if (fishUuid != null) {
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(fishUuid);
                if (e instanceof SalmonEntity fish && fish.isAlive()) {
                    primary = fish;
                    break;
                }
            }
        }
        List<SalmonEntity> duplicates = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e instanceof SalmonEntity fish && fish.isAlive() && fish.getCommandTags().contains(PIPIRA_TAG)) {
                    if (primary == null) {
                        primary = fish;
                        this.fishUuid = fish.getUuid();
                        mod.context().setPipiraUuid(fishUuid.toString());
                        mod.context().save();
                    } else if (!fish.getUuid().equals(primary.getUuid())) {
                        duplicates.add(fish);
                    }
                }
            }
        }
        for (SalmonEntity dup : duplicates) {
            if (dup.hasVehicle()) dup.stopRiding();
            dup.discard();
        }
        return primary;
    }

    /**
     * Chamado quando Kikkawa entra no servidor, quando alguém chama o Pipira no chat,
     * ou quando acionam /cummins pipira.
     */
    public SalmonEntity summonOrTeleportTo(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        this.activeInSession = true;
        this.manuallyDismissed = false;
        this.lastFocusPlayerName = player.getGameProfile().getName();

        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(PIPIRA_TAG);
        }

        SalmonEntity fish = getFish();
        if (fish == null) {
            fish = spawnNear(player);
        } else {
            teleportToPlayer(fish, player);
            applyAttributes(fish, player);
        }
        return fish;
    }

    public void dismiss() {
        this.manuallyDismissed = true;
        this.activeInSession = false;
        despawnIfPresent();
    }

    public void despawnIfPresent() {
        this.activeInSession = false;
        this.scaleApplied = false;
        SalmonEntity fish = getFish();
        if (fish != null) {
            if (fish.hasVehicle()) fish.stopRiding();
            if (fish.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.3, fish.getZ(), 15, 0.3, 0.2, 0.3, 0.1);
                sw.spawnParticles(ParticleTypes.POOF, fish.getX(), fish.getY() + 0.3, fish.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
            fish.discard();
        }
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (player.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME)) {
            // Se o dono deslogar e ninguém mais estiver online ou interagindo, recolhe Pipira
            if (server != null && server.getPlayerManager().getCurrentPlayerCount() <= 1) {
                despawnIfPresent();
            }
        }
    }

    public void onTick() {
        if (server == null) return;
        tickCounter++;

        List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
        if (onlinePlayers.isEmpty()) {
            if (activeInSession) {
                despawnIfPresent();
            }
            return;
        }

        // Se Kikkawa estiver online, garante que Pipira esteja ativo automaticamente
        boolean kikkawaOnline = false;
        for (ServerPlayerEntity sp : onlinePlayers) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME)) {
                kikkawaOnline = true;
                if (!activeInSession && !manuallyDismissed) {
                    activeInSession = true;
                    lastFocusPlayerName = sp.getGameProfile().getName();
                }
                break;
            }
        }

        // Se ninguém chamou e o Kikkawa não está online (ou foi dispensado), recolhe duplicatas ou o pet
        if (!activeInSession && (!kikkawaOnline || manuallyDismissed)) {
            if (tickCounter % 40 == 0) {
                SalmonEntity stray = getFish();
                if (stray != null) {
                    if (stray.hasVehicle()) stray.stopRiding();
                    stray.discard();
                }
            }
            return;
        }

        SalmonEntity fish = getFish();
        if (fish == null) {
            ServerPlayerEntity focus = getFocusPlayer();
            if (focus != null) {
                fish = spawnNear(focus);
            }
            if (fish == null) return;
        }

        // Manutenção de vida, gravidade zero e imortalidade: NUNCA bate no chão e NUNCA sufoca!
        fish.setNoGravity(true);
        fish.setAir(300);
        fish.setInvulnerable(true);

        ServerPlayerEntity focus = getFocusPlayer();
        if (focus == null) return;

        // Detecção de T-bag / Shift spam do jogador para o Splash Especial
        boolean isSneaking = focus.isSneaking();
        if (isSneaking && !lastFocusSneaking) {
            long nowSneak = System.currentTimeMillis();
            if (fish.squaredDistanceTo(focus) <= 6.0 * 6.0) {
                sneakHistory.add(nowSneak);
                sneakHistory.removeIf(t -> nowSneak - t > 2000L);
                if (sneakHistory.size() >= 3 && nowSneak - lastTbagAt > 15_000L) {
                    lastTbagAt = nowSneak;
                    sneakHistory.clear();
                    onTbagDetected(fish, focus);
                }
            }
        }
        lastFocusSneaking = isSneaking;

        // Lógica de Voo Místico Suave em 3D
        handleFishFlight(fish, focus);
    }

    /**
     * Movimentação de Voo 3D Suave e Flutuação Mística (Estilo Cummins Andando):
     * O peixe navega suavemente pelo ar acompanhando o jogador como um pet andando:
     * - NUNCA fica na frente do campo de visão do jogador (orbita flancos ou retaguarda).
     * - Se o jogador se aproxima (< 2.3 blocos) ou olha de perto, o Pipira dá espaço suavemente (step aside).
     * - Paira em altura amigável próxima ao chão/cintura (~0.4 a 0.65 blocos), sem tampar a tela.
     * - Movimento fluído com amortecimento e rotação natural na direção do nado aéreo.
     */
    private void handleFishFlight(SalmonEntity fish, ServerPlayerEntity player) {
        if (!(fish.getWorld() instanceof ServerWorld sw)) return;

        // Se estiver em outra dimensão ou a mais de 32 blocos, teleporta para perto
        if (fish.getWorld() != player.getWorld() || fish.squaredDistanceTo(player) > 32.0 * 32.0) {
            teleportToPlayer(fish, player);
            return;
        }

        double distSq = fish.squaredDistanceTo(player);
        long now = System.currentTimeMillis();

        boolean inPetAffection = now < petPauseUntil;
        if (distSq > 8.0 * 8.0) {
            petPauseUntil = 0; // se o jogador se afastar mais de 8 blocos, retoma o acompanhamento normal
            inPetAffection = false;
        }

        if (inPetAffection) {
            // Em estado de carinho: para 100% no lugar e olha amorosamente para o jogador
            fish.setVelocity(0, Math.sin(tickCounter * 0.05) * 0.005, 0);
            fish.velocityDirty = true;
            fish.getLookControl().lookAt(player, 60.0f, 60.0f);
            if (tickCounter % 20 == 0) {
                sw.spawnParticles(ParticleTypes.HEART, fish.getX(), fish.getY() + 0.35, fish.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
            }
            return;
        }

        // 1. Detecção de proximidade excessiva
        boolean tooClose = distSq < 0.9 * 0.9;
        boolean inFront = isInFrontOfPlayer(fish, player) && distSq < 1.8 * 1.8;

        if (tooClose || inFront) {
            if (now - lastStepAsideAt > 2000L || hoverTarget == null) {
                lastStepAsideAt = now;
                lastWanderAt = now;
                hoverTarget = pickHoverSpotAroundPlayer(fish, player, true);
            }
        } else if (distSq > 4.5 * 4.5) {
            if (now - lastWanderAt > 1200L || hoverTarget == null || hoverTarget.squaredDistanceTo(player.getPos()) > 5.0 * 5.0) {
                lastWanderAt = now;
                hoverTarget = pickHoverSpotAroundPlayer(fish, player, false);
            }
        } else {
            // Fica calmo e estável perto do jogador por intervalos mais longos (6 a 9 segundos)
            if (now - lastWanderAt > 7500L || hoverTarget == null) {
                lastWanderAt = now;
                hoverTarget = pickHoverSpotAroundPlayer(fish, player, false);
            }
        }

        if (hoverTarget == null) {
            hoverTarget = pickHoverSpotAroundPlayer(fish, player, false);
        }

        // Flutuação senoidal suave (bobbing)
        double bobbing = Math.sin(tickCounter * 0.06) * 0.03;
        double targetX = hoverTarget.x;
        double targetY = hoverTarget.y + bobbing;
        double targetZ = hoverTarget.z;

        double dx = targetX - fish.getX();
        double dy = targetY - fish.getY();
        double dz = targetZ - fish.getZ();
        double distToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distToTarget > 0.14) {
            // Velocidade dinâmica suave e desacelerada
            double speed;
            if (distSq > 10.0 * 10.0) {
                speed = 0.22D;
            } else if (distSq > 4.5 * 4.5) {
                speed = 0.13D;
            } else {
                speed = 0.075D; // Voo calmo pertinho do tutor
            }

            double desiredVx = (dx / distToTarget) * speed;
            double desiredVy = (dy / distToTarget) * speed;
            double desiredVz = (dz / distToTarget) * speed;

            // Interpolação/amortecimento suave de velocidade para nado orgânico no ar
            Vec3d cur = fish.getVelocity();
            double blend = 0.25D;
            double vx = cur.x * (1.0D - blend) + desiredVx * blend;
            double vy = cur.y * (1.0D - blend) + desiredVy * blend;
            double vz = cur.z * (1.0D - blend) + desiredVz * blend;

            fish.setVelocity(vx, vy, vz);
            fish.velocityDirty = true;

            // Ajusta suavemente a orientação do corpo do peixe para a direção do nado
            if (vx * vx + vz * vz > 0.003) {
                float swimYaw = (float) (Math.toDegrees(Math.atan2(vz, vx)) - 90.0);
                fish.setYaw(swimYaw);
                fish.setBodyYaw(swimYaw);
                fish.setHeadYaw(swimYaw);
            } else {
                fish.getLookControl().lookAt(player, 30.0f, 30.0f);
            }

            // Rastro místico aquático suave enquanto voa pelo ar
            if (tickCounter % 8 == 0) {
                sw.spawnParticles(ParticleTypes.BUBBLE, fish.getX(), fish.getY() + 0.12, fish.getZ(), 1, 0.05, 0.05, 0.05, 0.01);
            }
            if (tickCounter % 20 == 0) {
                sw.spawnParticles(ParticleTypes.GLOW, fish.getX(), fish.getY() + 0.15, fish.getZ(), 1, 0.04, 0.04, 0.04, 0.01);
            }
        } else {
            // Pairando no ponto com amortecimento e nado estático suave
            Vec3d cur = fish.getVelocity();
            fish.setVelocity(cur.x * 0.5, Math.sin(tickCounter * 0.06) * 0.008, cur.z * 0.5);
            fish.velocityDirty = true;

            // Olha amigavelmente na direção do jogador
            fish.getLookControl().lookAt(player, 30.0f, 30.0f);

            if (tickCounter % 35 == 0) {
                sw.spawnParticles(ParticleTypes.BUBBLE, fish.getX(), fish.getY() + 0.12, fish.getZ(), 1, 0.04, 0.04, 0.04, 0.01);
            }
        }
    }

    private boolean isInFrontOfPlayer(SalmonEntity fish, ServerPlayerEntity player) {
        double lookX = -Math.sin(Math.toRadians(player.getYaw()));
        double lookZ = Math.cos(Math.toRadians(player.getYaw()));
        double dx = fish.getX() - player.getX();
        double dz = fish.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return true;
        double dot = (lookX * dx + lookZ * dz) / dist;
        return dot > 0.45; // dentro do cone frontal
    }

    private Vec3d pickHoverSpotAroundPlayer(SalmonEntity fish, ServerPlayerEntity player, boolean stepAside) {
        float playerYaw = player.getYaw();
        int zone = random.nextInt(3);
        double angleDeg;
        if (zone == 0) {
            angleDeg = playerYaw + 65.0 + random.nextDouble() * 25.0; // flanco direito próximo
        } else if (zone == 1) {
            angleDeg = playerYaw - 65.0 - random.nextDouble() * 25.0; // flanco esquerdo próximo
        } else {
            angleDeg = playerYaw + 180.0 + (random.nextDouble() - 0.5) * 50.0; // retaguarda próxima
        }

        // Raio muito mais próximo (1.3 a 1.9 blocos) para facilitar carinho e cliques
        double radius = stepAside ? (1.7 + random.nextDouble() * 0.3) : (1.3 + random.nextDouble() * 0.5);
        double rad = Math.toRadians(angleDeg);
        double targetX = player.getX() - Math.sin(rad) * radius;
        double targetZ = player.getZ() + Math.cos(rad) * radius;

        // Altura suave próxima à cintura
        double targetY = player.getY() + 0.45 + (random.nextDouble() * 0.18);
        if (player.getWorld() instanceof ServerWorld sw) {
            BlockPos checkPos = new BlockPos((int) Math.floor(targetX), (int) Math.floor(player.getY()), (int) Math.floor(targetZ));
            BlockPos ground = findSafeGroundNear(checkPos, sw);
            targetY = Math.max(ground.getY() + 0.40, targetY);
        }

        return new Vec3d(targetX, targetY, targetZ);
    }

    public SalmonEntity spawnNear(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        ServerWorld world = player.getServerWorld();

        // Spawna atrás do jogador
        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw + 180.0);
        int bx = (int) Math.floor(player.getX() - Math.sin(rad) * 2.6);
        int bz = (int) Math.floor(player.getZ() + Math.cos(rad) * 2.6);
        BlockPos behind = new BlockPos(bx, player.getBlockY(), bz);
        BlockPos safe = findSafeGroundNear(behind, world);

        SalmonEntity fish = EntityType.SALMON.create(world);
        if (fish == null) return null;

        // Spawna flutuando suavemente ~0.45 blocos acima do chão
        fish.refreshPositionAndAngles(safe.getX() + 0.5, safe.getY() + 0.45, safe.getZ() + 0.5, player.getYaw(), 0);
        fish.setVelocity(Vec3d.ZERO);
        applyAttributes(fish, player);

        this.hoverTarget = new Vec3d(safe.getX() + 0.5, safe.getY() + 0.45, safe.getZ() + 0.5);
        this.lastWanderAt = System.currentTimeMillis();

        world.spawnEntity(fish);
        this.fishUuid = fish.getUuid();
        mod.context().setPipiraUuid(fishUuid.toString());
        mod.context().save();

        world.spawnParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.3, fish.getZ(), 20, 0.25, 0.15, 0.25, 0.08);
        world.spawnParticles(ParticleTypes.BUBBLE, fish.getX(), fish.getY() + 0.3, fish.getZ(), 10, 0.2, 0.15, 0.2, 0.04);
        world.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.NEUTRAL, 0.9f, 1.2f);
        return fish;
    }

    public void teleportToPlayer(SalmonEntity fish, ServerPlayerEntity player) {
        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(PIPIRA_TAG);
        }
        if (fish.hasVehicle()) {
            fish.stopRiding();
        }
        ServerWorld targetWorld = player.getServerWorld();

        // Teleporta para trás ou flanco do jogador (nunca na frente)
        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw + 180.0);
        int bx = (int) Math.floor(player.getX() - Math.sin(rad) * 2.6);
        int bz = (int) Math.floor(player.getZ() + Math.cos(rad) * 2.6);
        BlockPos behind = new BlockPos(bx, player.getBlockY(), bz);
        BlockPos safePos = findSafeGroundNear(behind, targetWorld);

        double x = safePos.getX() + 0.5;
        double y = safePos.getY() + 0.45;
        double z = safePos.getZ() + 0.5;

        if (fish.getWorld() != targetWorld) {
            fish.teleport(targetWorld, x, y, z, java.util.Set.of(), player.getYaw(), 0);
        } else {
            fish.refreshPositionAndAngles(x, y, z, player.getYaw(), 0);
        }
        fish.setVelocity(Vec3d.ZERO);
        fish.fallDistance = 0;
        fish.setNoGravity(true);
        fish.setAir(300);

        this.hoverTarget = new Vec3d(x, y, z);
        this.lastWanderAt = System.currentTimeMillis();

        targetWorld.spawnParticles(ParticleTypes.SPLASH, x, y + 0.2, z, 15, 0.25, 0.15, 0.25, 0.06);
        targetWorld.spawnParticles(ParticleTypes.BUBBLE, x, y + 0.2, z, 8, 0.2, 0.15, 0.2, 0.04);
        targetWorld.playSound(null, x, y, z, SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.NEUTRAL, 0.8f, 1.3f);
    }

    public void emoteSplash() {
        SalmonEntity fish = getFish();
        if (fish == null || !fish.isAlive() || !(fish.getWorld() instanceof ServerWorld sw)) return;
        fish.setVelocity(0, 0.35, 0);
        fish.velocityDirty = true;
        sw.spawnParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.2, fish.getZ(), 15, 0.25, 0.1, 0.25, 0.08);
        sw.spawnParticles(ParticleTypes.BUBBLE, fish.getX(), fish.getY() + 0.3, fish.getZ(), 8, 0.2, 0.1, 0.2, 0.04);
        sw.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.ENTITY_PLAYER_SPLASH, SoundCategory.NEUTRAL, 0.7f, 1.4f);
    }

    public void onTbagDetected(SalmonEntity fish, ServerPlayerEntity player) {
        fish.setVelocity(0, 0.45, 0);
        fish.velocityDirty = true;

        if (fish.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SPLASH, fish.getX(), fish.getY() + 0.3, fish.getZ(), 25, 0.35, 0.25, 0.35, 0.1);
            sw.spawnParticles(ParticleTypes.BUBBLE_POP, fish.getX(), fish.getY() + 0.4, fish.getZ(), 15, 0.25, 0.25, 0.25, 0.06);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, fish.getX(), fish.getY() + 0.5, fish.getZ(), 8, 0.25, 0.25, 0.25, 0.06);
            sw.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.NEUTRAL, 0.9f, 1.25f);
        }

        String ownerNick = player.getGameProfile().getName();
        if (server != null) {
            server.getPlayerManager().broadcast(Text.literal("§6<Pipira>§r §e*SPLASH SUPREMO!* 🐟💦 (Pipira fez um loop voador usando Splash com toda a sua energia! Não teve efeito em batalha, mas o " + ownerNick + " adorou!)"), false);
        }
    }

    public void onPet(SalmonEntity fish, ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastPetTime < 500L) {
            return;
        }
        lastPetTime = now;

        this.petPauseUntil = now + 12_000L;
        this.hoverTarget = fish.getPos();
        fish.setVelocity(0, 0, 0);
        fish.velocityDirty = true;
        fish.getLookControl().lookAt(player, 60.0f, 60.0f);
        setFocusPlayer(player.getGameProfile().getName());
        applyAttributes(fish, player);

        if (fish.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, fish.getX(), fish.getY() + 0.35, fish.getZ(), 7, 0.2, 0.2, 0.2, 0.05);
            sw.spawnParticles(ParticleTypes.BUBBLE, fish.getX(), fish.getY() + 0.2, fish.getZ(), 8, 0.15, 0.1, 0.15, 0.03);
            sw.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.NEUTRAL, 0.8f, 1.3f);
        }

        // Mimo do mar: cooldown de 30 minutos e 15% de chance de cuspir um tesouro marinho
        if (System.currentTimeMillis() - lastGiftAt > 1_800_000L && random.nextDouble() < 0.15) {
            deliverSeaGift(fish, player);
        }

        // Mensagem discreta na Action Bar apenas para quem fez carinho (zero spam no chat global!)
        player.sendMessage(Text.literal("§6<Pipira>§r §e*blub blub splash!* 🐟💖 §7(Pipira flutuou carinhosamente perto de você)"), true);
    }

    private void deliverSeaGift(SalmonEntity fish, ServerPlayerEntity player) {
        if (!(fish.getWorld() instanceof ServerWorld sw)) return;
        lastGiftAt = System.currentTimeMillis();

        ItemStack gift;
        int roll = random.nextInt(5);
        switch (roll) {
            case 0 -> gift = new ItemStack(Items.PRISMARINE_SHARD, 2);
            case 1 -> gift = new ItemStack(Items.NAUTILUS_SHELL, 1);
            case 2 -> gift = new ItemStack(Items.LILY_PAD, 4);
            case 3 -> gift = new ItemStack(Items.SEA_PICKLE, 2);
            default -> gift = new ItemStack(Items.COOKED_SALMON, 2);
        }

        ItemEntity drop = new ItemEntity(sw, fish.getX(), fish.getY() + 0.2, fish.getZ(), gift);
        drop.setVelocity((player.getX() - fish.getX()) * 0.15, 0.25, (player.getZ() - fish.getZ()) * 0.15);
        sw.spawnEntity(drop);

        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, fish.getX(), fish.getY() + 0.4, fish.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.playSound(null, fish.getX(), fish.getY(), fish.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 1.0f, 1.1f);
    }

    private void applyAttributes(SalmonEntity fish, ServerPlayerEntity owner) {
        fish.setCustomName(Text.literal("§6Pipira §c(Magikarp)§r"));
        fish.setCustomNameVisible(true);
        fish.setInvulnerable(true);
        fish.setPersistent();
        fish.setNoGravity(true);
        fish.setAir(300);

        if (!fish.getCommandTags().contains(PIPIRA_TAG)) {
            fish.addCommandTag(PIPIRA_TAG);
        }

        // Desativa colisão física (empurrão) com jogadores via Scoreboard Team CollisionRule.NEVER
        if (mod.body() != null) {
            mod.body().disableEntityCollision(fish, "pipira_noclip", Formatting.GOLD);
        }

        // Leve aumento de escala (1.15x) via Pehkui se disponível para dar porte de carpa Magikarp
        if (!scaleApplied && server != null) {
            try {
                server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(),
                        "scale set pehkui:base 1.15 " + fish.getUuidAsString()
                );
                scaleApplied = true;
            } catch (Exception ignored) {}
        }
    }

    public static BlockPos findSafeGroundNear(BlockPos center, ServerWorld world) {
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

    public static boolean isWalkable(ServerWorld world, BlockPos feet) {
        BlockPos floor = feet.down();
        BlockPos head = feet.up();
        var floorState = world.getBlockState(floor);
        var feetState = world.getBlockState(feet);
        var headState = world.getBlockState(head);

        // Chão sólido ou água
        boolean solidFloor = !floorState.getCollisionShape(world, floor).isEmpty() || !world.getFluidState(feet).isEmpty();
        boolean clearFeet = feetState.getCollisionShape(world, feet).isEmpty();
        boolean clearHead = headState.getCollisionShape(world, head).isEmpty();
        return solidFloor && clearFeet && clearHead;
    }
}
