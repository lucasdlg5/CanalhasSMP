package com.canalhas.cummins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Gerencia a presença física do morcego "Juninho" (Morcego Guardião do Jodevil).
 * - Lore: O fiel morcego guardião da noite de Jodevil, dotado de ecolocalização mística
 *   e sentidos aguçados para proteger o seu tutor nas sombras e espantar criaturas perigosas.
 * - Comportamento de Voo: Voa com a inteligência espacial do Senhor Cummins e do Pipira,
 *   orbitando estritamente pelos FLANCOS ou pela RETAGUARDA (NUNCA fica na frente da visão do jogador!).
 * - Proteção & Espantar Mobs: Emite pulsos ultrassônicos (Sonic Screech) que repelem, desaceleram
 *   e espantam Phantoms, Aranhas, Creepers e outros monstros que ousem ameaçar o jogador.
 * - Interação Discreta: Não spamma o chat global! Responde a carinho, alimentação de frutas (bagas/maçã)
 *   e interações via ActionBar individual.
 * - Imortalidade: 100% invulnerável, persistente, noclip (sem colisão física incômoda) e nunca sufoca.
 */
public class JuninhoBody {
    public static final String JUNINHO_TAG = "juninho_bat_body";
    public static final String OWNER_NAME = "jodevil";

    private final CumminsMod mod;
    private MinecraftServer server;
    private UUID batUuid;
    private String lastFocusPlayerName = OWNER_NAME;
    private boolean activeInSession = false;
    private boolean scaleApplied = false;
    private int tickCounter = 0;
    private long lastGiftAt = System.currentTimeMillis();
    private boolean lastFocusSneaking = false;
    private final List<Long> sneakHistory = new ArrayList<>();
    private long lastTbagAt = 0;
    private long lastPetTime = 0;
    private long lastScreechAt = 0;
    private long lastSonarPingAt = 0;
    private Vec3d hoverTarget = null;
    private long lastWanderAt = 0;
    private long lastStepAsideAt = 0;
    private long petPauseUntil = 0;
    private boolean manuallyDismissed = false;
    private final Map<UUID, Long> lastBatMilkGiftPerPlayer = new HashMap<>();
    private final Map<UUID, Boolean> drinkingBatMilk = new HashMap<>();
    private final Map<UUID, Long> batMilkBuffedUntil = new HashMap<>();
    private final Random random = new Random();

    public JuninhoBody(CumminsMod mod) {
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
        this.lastSonarPingAt = 0;
        this.lastBatMilkGiftPerPlayer.clear();
        this.drinkingBatMilk.clear();
        this.batMilkBuffedUntil.clear();
        String savedUuid = mod.context().juninhoUuid();
        if (savedUuid != null && !savedUuid.isBlank()) {
            try {
                this.batUuid = UUID.fromString(savedUuid);
            } catch (Exception ignored) {}
        }
    }

    public void stop() {
        this.lastBatMilkGiftPerPlayer.clear();
        this.drinkingBatMilk.clear();
        this.batMilkBuffedUntil.clear();
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
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(OWNER_NAME);
        if (owner != null && owner.isAlive()) return owner;

        List<ServerPlayerEntity> list = server.getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    public BatEntity getBat() {
        if (server == null) return null;
        BatEntity primary = null;
        if (batUuid != null) {
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(batUuid);
                if (e instanceof BatEntity bat && bat.isAlive()) {
                    primary = bat;
                    break;
                }
            }
        }
        for (ServerWorld world : server.getWorlds()) {
            List<BatEntity> toDiscard = new ArrayList<>();
            for (Entity e : world.iterateEntities()) {
                if (e instanceof BatEntity bat && bat.isAlive() && bat.getCommandTags().contains(JUNINHO_TAG)) {
                    if (primary == null) {
                        primary = bat;
                        this.batUuid = bat.getUuid();
                        mod.context().setJuninhoUuid(batUuid.toString());
                        mod.context().save();
                    } else if (!bat.getUuid().equals(primary.getUuid())) {
                        toDiscard.add(bat);
                    }
                }
            }
            for (BatEntity dup : toDiscard) {
                if (dup.hasVehicle()) dup.stopRiding();
                dup.discard();
            }
        }
        return primary;
    }

    /**
     * Invocado quando Jodevil entra no servidor, quando alguém chama o Juninho no chat,
     * ou via comando.
     */
    public BatEntity summonOrTeleportTo(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        this.activeInSession = true;
        this.manuallyDismissed = false;
        this.lastFocusPlayerName = player.getGameProfile().getName();

        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(JUNINHO_TAG);
        }

        BatEntity bat = getBat();
        if (bat == null) {
            bat = spawnNear(player);
        } else {
            teleportToPlayer(bat, player);
            applyAttributes(bat, player);
        }
        return bat;
    }

    public void dismiss() {
        this.manuallyDismissed = true;
        this.activeInSession = false;
        despawnIfPresent();
    }

    public void despawnIfPresent() {
        this.activeInSession = false;
        this.scaleApplied = false;
        BatEntity bat = getBat();
        if (bat != null) {
            if (bat.hasVehicle()) bat.stopRiding();
            if (bat.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SCULK_SOUL, bat.getX(), bat.getY() + 0.3, bat.getZ(), 12, 0.25, 0.2, 0.25, 0.05);
                sw.spawnParticles(ParticleTypes.POOF, bat.getX(), bat.getY() + 0.3, bat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
            bat.discard();
        }
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (player.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME)) {
            if (server != null && server.getPlayerManager().getCurrentPlayerCount() <= 1) {
                despawnIfPresent();
            }
        }
    }

    public void onTick() {
        if (server == null) return;
        tickCounter++;
        checkBatMilkDrinking();

        List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
        if (onlinePlayers.isEmpty()) {
            if (activeInSession) {
                despawnIfPresent();
            }
            return;
        }

        // Se Jodevil estiver online, garante que Juninho esteja ativo acompanhando ele
        boolean jodevilOnline = false;
        for (ServerPlayerEntity sp : onlinePlayers) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME)) {
                jodevilOnline = true;
                if (!activeInSession && !manuallyDismissed) {
                    activeInSession = true;
                    lastFocusPlayerName = sp.getGameProfile().getName();
                }
                break;
            }
        }

        if (!activeInSession && (!jodevilOnline || manuallyDismissed)) {
            if (tickCounter % 40 == 0) {
                BatEntity stray = getBat();
                if (stray != null) {
                    if (stray.hasVehicle()) stray.stopRiding();
                    stray.discard();
                }
            }
            return;
        }

        BatEntity bat = getBat();
        if (bat == null) {
            ServerPlayerEntity focus = getFocusPlayer();
            if (focus != null) {
                bat = spawnNear(focus);
            }
            if (bat == null) return;
        }

        // Garantias de vida, voo e imortalidade absoluta
        bat.setNoGravity(true);
        bat.setAir(300);
        bat.setInvulnerable(true);
        bat.setRoosting(false); // Mantém voando alegremente

        ServerPlayerEntity focus = getFocusPlayer();
        if (focus == null) return;

        // Detecção de T-bag / Shift spam do jogador
        boolean isSneaking = focus.isSneaking();
        if (isSneaking && !lastFocusSneaking) {
            long nowSneak = System.currentTimeMillis();
            if (bat.squaredDistanceTo(focus) <= 6.0 * 6.0) {
                sneakHistory.add(nowSneak);
                sneakHistory.removeIf(t -> nowSneak - t > 2000L);
                if (sneakHistory.size() >= 3 && nowSneak - lastTbagAt > 15_000L) {
                    lastTbagAt = nowSneak;
                    sneakHistory.clear();
                    onTbagDetected(bat, focus);
                }
            }
        }
        lastFocusSneaking = isSneaking;

        // 1. Proteção de monstros e pulso sônico afugentador
        if (tickCounter % 20 == 0) {
            handleMonsterDeterrence(bat, focus);
            checkSonarDarknessScan(bat, focus);
        }

        // 2. Lógica de Voo 3D Inteligente (flancos e retaguarda, nunca na frente)
        handleBatFlight(bat, focus);
    }

    /**
     * Sistema de Proteção e Afugentamento de Monstros:
     * Juninho usa sua ecolocalização e emite um grito ultrassônico (Sonic Screech)
     * quando monstros ou Phantoms se aproximam de Jodevil/jogadores aliados!
     */
    private void handleMonsterDeterrence(BatEntity bat, ServerPlayerEntity player) {
        if (!(bat.getWorld() instanceof ServerWorld sw)) return;
        long now = System.currentTimeMillis();
        if (now - lastScreechAt < 3500L) return;

        Box searchBox = player.getBoundingBox().expand(8.0, 5.0, 8.0);
        List<LivingEntity> threats = sw.getEntitiesByClass(
                LivingEntity.class, searchBox,
                e -> e.isAlive() && (e instanceof net.minecraft.entity.mob.Monster)
        );

        if (threats.isEmpty()) return;

        // Pega a ameaça mais próxima do jogador
        LivingEntity closestThreat = null;
        double closestDistSq = Double.MAX_VALUE;
        for (LivingEntity threat : threats) {
            double dSq = threat.squaredDistanceTo(player);
            if (dSq < closestDistSq) {
                closestDistSq = dSq;
                closestThreat = threat;
            }
        }

        if (closestThreat == null) return;

        lastScreechAt = now;

        // Voo rasante na direção da criatura
        double dx = closestThreat.getX() - bat.getX();
        double dy = (closestThreat.getY() + 0.5) - bat.getY();
        double dz = closestThreat.getZ() - bat.getZ();
        double dist = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));

        bat.setVelocity((dx / dist) * 0.42, (dy / dist) * 0.42, (dz / dist) * 0.42);
        bat.velocityDirty = true;

        // Efeito sonoro ultrassônico sutil e partículas místicas de ecolocalização
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, bat.getX(), bat.getY() + 0.2, bat.getZ(), 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.SCULK_SOUL, closestThreat.getX(), closestThreat.getY() + 0.5, closestThreat.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, bat.getX(), bat.getY() + 0.2, bat.getZ(), 12, 0.25, 0.25, 0.25, 0.05);

        sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.NEUTRAL, 1.1f, 1.8f);
        sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.NEUTRAL, 0.35f, 1.85f);

        // Repulsão e desaceleração do monstro
        closestThreat.takeKnockback(1.0D, -(dx / dist), -(dz / dist));
        closestThreat.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1, false, true, true));
        closestThreat.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1, false, true, true));
        closestThreat.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, true, true));

        // Se for Phantom (inimigo natural das alturas), o Juninho causa dano mágico afugentando de vez
        if (closestThreat instanceof net.minecraft.entity.mob.PhantomEntity) {
            closestThreat.damage(player.getDamageSources().magic(), 4.0f);
        }

        // Limpa o alvo da criatura para que pare de focar o player
        if (closestThreat instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setTarget(null);
        }

        // Mensagem discreta na Action Bar avisando o tutor da proteção (zero chat spam!)
        String threatName = closestThreat.getName().getString();
        player.sendMessage(Text.literal("§8<Juninho>§r §d*SCREEECH!* 🦇🔊 §7(Juninho emitiu um pulso sônico e espantou o " + threatName + "!)"), true);
    }

    private void checkSonarDarknessScan(BatEntity bat, ServerPlayerEntity player) {
        if (bat == null || player == null || !bat.isAlive()) return;
        long now = System.currentTimeMillis();
        if (now - lastSonarPingAt < 18_000L) return; // Cooldown 18s

        if (!(player.getWorld() instanceof ServerWorld world)) return;
        Box box = player.getBoundingBox().expand(14.0, 6.0, 14.0);
        List<net.minecraft.entity.mob.HostileEntity> darkMonsters = world.getEntitiesByClass(
                net.minecraft.entity.mob.HostileEntity.class,
                box,
                m -> m.isAlive() && !m.isSpectator() && world.getLightLevel(LightType.BLOCK, m.getBlockPos()) <= 4
        );

        if (!darkMonsters.isEmpty()) {
            lastSonarPingAt = now;
            for (net.minecraft.entity.mob.HostileEntity m : darkMonsters) {
                m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0, false, false, true));
            }
            world.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.BLOCK_SCULK_SHRIEKER_SHRIEK, SoundCategory.NEUTRAL, 0.3F, 1.9F);
            world.spawnParticles(ParticleTypes.SONIC_BOOM, bat.getX(), bat.getY() + 0.2, bat.getZ(), 1, 0, 0, 0, 0);
            player.sendMessage(Text.literal("🦇 §d[Ecolocalização do Juninho] §5Pulso sônico revelou " + darkMonsters.size() + " presença(s) na escuridão!"), true);
        }
    }

    /**
     * Voo 3D Inteligente e Acolhedor:
     * - Orbita bem pertinho do jogador (1.2 a 1.9 blocos), na altura ideal para carinho e interação (peito/ombro).
     * - Voo calmo, suave e sem disparadas repentinas.
     * - Ao receber carinho, PARA TOTALMENTE no ar, fixa o olhar no tutor e emite corações por 12 segundos sem fugir!
     */
    private void handleBatFlight(BatEntity bat, ServerPlayerEntity player) {
        if (!(bat.getWorld() instanceof ServerWorld sw)) return;

        if (bat.getWorld() != player.getWorld() || bat.squaredDistanceTo(player) > 32.0 * 32.0) {
            teleportToPlayer(bat, player);
            return;
        }

        double distSq = bat.squaredDistanceTo(player);
        long now = System.currentTimeMillis();

        boolean inPetAffection = now < petPauseUntil;
        if (distSq > 8.0 * 8.0) {
            petPauseUntil = 0; // se o jogador se afastar mais de 8 blocos, retoma o acompanhamento normal
            inPetAffection = false;
        }

        if (inPetAffection) {
            // Em estado de carinho: para quieto pairando no lugar exato, de frente para o jogador e NÃO se move!
            bat.setVelocity(0, Math.sin(tickCounter * 0.05) * 0.005, 0);
            bat.velocityDirty = true;
            bat.getLookControl().lookAt(player, 60.0f, 60.0f);
            if (tickCounter % 20 == 0) {
                sw.spawnParticles(ParticleTypes.HEART, bat.getX(), bat.getY() + 0.35, bat.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
            }
            return; // Interrompe o voo aqui para ficar parado recebendo carinho
        }

        // Não foge do jogador quando ele se aproxima para acariciar (< 1.1 blocos)
        boolean tooClose = distSq < 0.9 * 0.9;
        boolean inFront = isInFrontOfPlayer(bat, player) && distSq < 1.8 * 1.8;

        if (tooClose || inFront) {
            if (now - lastStepAsideAt > 2000L || hoverTarget == null) {
                lastStepAsideAt = now;
                lastWanderAt = now;
                hoverTarget = pickHoverSpotAroundPlayer(bat, player, true);
            }
        } else if (distSq > 4.5 * 4.5) {
            if (now - lastWanderAt > 1200L || hoverTarget == null || hoverTarget.squaredDistanceTo(player.getPos()) > 5.0 * 5.0) {
                lastWanderAt = now;
                hoverTarget = pickHoverSpotAroundPlayer(bat, player, false);
            }
        } else {
            // Fica calmo e estável perto do jogador por intervalos mais longos (6 a 9 segundos)
            if (now - lastWanderAt > 7500L || hoverTarget == null) {
                lastWanderAt = now;
                hoverTarget = pickHoverSpotAroundPlayer(bat, player, false);
            }
        }

        if (hoverTarget == null) {
            hoverTarget = pickHoverSpotAroundPlayer(bat, player, false);
        }

        double bobbing = Math.sin(tickCounter * 0.06) * 0.025;
        double targetX = hoverTarget.x;
        double targetY = hoverTarget.y + bobbing;
        double targetZ = hoverTarget.z;

        double dx = targetX - bat.getX();
        double dy = targetY - bat.getY();
        double dz = targetZ - bat.getZ();
        double distToTarget = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (distToTarget > 0.14) {
            double speed;
            if (distSq > 10.0 * 10.0) {
                speed = 0.22D; // aproximação rápida se ficou para trás
            } else if (distSq > 4.5 * 4.5) {
                speed = 0.13D; // acompanhamento médio
            } else {
                speed = 0.075D; // voo lento, suave e calmo pertinho do tutor
            }

            double desiredVx = (dx / distToTarget) * speed;
            double desiredVy = (dy / distToTarget) * speed;
            double desiredVz = (dz / distToTarget) * speed;

            Vec3d cur = bat.getVelocity();
            double blend = 0.25D; // amortecimento orgânico de voo
            double vx = cur.x * (1.0D - blend) + desiredVx * blend;
            double vy = cur.y * (1.0D - blend) + desiredVy * blend;
            double vz = cur.z * (1.0D - blend) + desiredVz * blend;

            bat.setVelocity(vx, vy, vz);
            bat.velocityDirty = true;

            if (vx * vx + vz * vz > 0.003) {
                float swimYaw = (float) (Math.toDegrees(Math.atan2(vz, vx)) - 90.0);
                bat.setYaw(swimYaw);
                bat.setBodyYaw(swimYaw);
                bat.setHeadYaw(swimYaw);
            } else {
                bat.getLookControl().lookAt(player, 30.0f, 30.0f);
            }

            if (tickCounter % 16 == 0) {
                sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, bat.getX(), bat.getY() + 0.1, bat.getZ(), 1, 0.04, 0.04, 0.04, 0.01);
            }
        } else {
            Vec3d cur = bat.getVelocity();
            bat.setVelocity(cur.x * 0.5, Math.sin(tickCounter * 0.06) * 0.008, cur.z * 0.5);
            bat.velocityDirty = true;
            bat.getLookControl().lookAt(player, 30.0f, 30.0f);

            if (tickCounter % 40 == 0) {
                sw.spawnParticles(ParticleTypes.WITCH, bat.getX(), bat.getY() + 0.1, bat.getZ(), 1, 0.03, 0.03, 0.03, 0.01);
            }
        }
    }

    private boolean isInFrontOfPlayer(BatEntity bat, ServerPlayerEntity player) {
        double lookX = -Math.sin(Math.toRadians(player.getYaw()));
        double lookZ = Math.cos(Math.toRadians(player.getYaw()));
        double dx = bat.getX() - player.getX();
        double dz = bat.getZ() - player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001) return true;
        double dot = (lookX * dx + lookZ * dz) / dist;
        return dot > 0.45; // cone frontal restrito
    }

    private Vec3d pickHoverSpotAroundPlayer(BatEntity bat, ServerPlayerEntity player, boolean stepAside) {
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

        // Raio confortável e sempre ao alcance do clique do jogador (1.3 a 1.9 blocos)
        double radius = stepAside ? (1.7 + random.nextDouble() * 0.3) : (1.3 + random.nextDouble() * 0.5);
        double rad = Math.toRadians(angleDeg);
        double targetX = player.getX() - Math.sin(rad) * radius;
        double targetZ = player.getZ() + Math.cos(rad) * radius;

        // Altura amigável na linha de visão/peito do tutor (Y + 0.60 a 0.85) para carinho fácil
        double targetY = player.getY() + 0.65 + (random.nextDouble() * 0.20);
        if (player.getWorld() instanceof ServerWorld sw) {
            BlockPos checkPos = new BlockPos((int) Math.floor(targetX), (int) Math.floor(player.getY()), (int) Math.floor(targetZ));
            BlockPos ground = PipiraBody.findSafeGroundNear(checkPos, sw);
            targetY = Math.max(ground.getY() + 0.65, targetY);
        }

        return new Vec3d(targetX, targetY, targetZ);
    }

    public BatEntity spawnNear(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        ServerWorld world = player.getServerWorld();

        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw + 180.0);
        int bx = (int) Math.floor(player.getX() - Math.sin(rad) * 2.6);
        int bz = (int) Math.floor(player.getZ() + Math.cos(rad) * 2.6);
        BlockPos behind = new BlockPos(bx, player.getBlockY(), bz);
        BlockPos safe = PipiraBody.findSafeGroundNear(behind, world);

        BatEntity bat = EntityType.BAT.create(world);
        if (bat == null) return null;

        bat.refreshPositionAndAngles(safe.getX() + 0.5, safe.getY() + 0.35, safe.getZ() + 0.5, player.getYaw(), 0);
        bat.setVelocity(Vec3d.ZERO);
        applyAttributes(bat, player);

        this.hoverTarget = new Vec3d(safe.getX() + 0.5, safe.getY() + 0.35, safe.getZ() + 0.5);
        this.lastWanderAt = System.currentTimeMillis();

        world.spawnEntity(bat);
        this.batUuid = bat.getUuid();
        mod.context().setJuninhoUuid(batUuid.toString());
        mod.context().save();

        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, bat.getX(), bat.getY() + 0.3, bat.getZ(), 20, 0.25, 0.2, 0.25, 0.05);
        world.spawnParticles(ParticleTypes.SCULK_SOUL, bat.getX(), bat.getY() + 0.3, bat.getZ(), 10, 0.2, 0.2, 0.2, 0.04);
        world.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.NEUTRAL, 1.0f, 1.6f);
        return bat;
    }

    public void teleportToPlayer(BatEntity bat, ServerPlayerEntity player) {
        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(JUNINHO_TAG);
        }
        if (bat.hasVehicle()) {
            bat.stopRiding();
        }
        ServerWorld targetWorld = player.getServerWorld();

        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw + 180.0);
        int bx = (int) Math.floor(player.getX() - Math.sin(rad) * 2.6);
        int bz = (int) Math.floor(player.getZ() + Math.cos(rad) * 2.6);
        BlockPos behind = new BlockPos(bx, player.getBlockY(), bz);
        BlockPos safePos = PipiraBody.findSafeGroundNear(behind, targetWorld);

        double x = safePos.getX() + 0.5;
        double y = safePos.getY() + 0.8;
        double z = safePos.getZ() + 0.5;

        if (bat.getWorld() != targetWorld) {
            bat.teleport(targetWorld, x, y, z, java.util.Set.of(), player.getYaw(), 0);
        } else {
            bat.refreshPositionAndAngles(x, y, z, player.getYaw(), 0);
        }
        bat.setVelocity(Vec3d.ZERO);
        bat.fallDistance = 0;
        bat.setNoGravity(true);
        bat.setAir(300);

        this.hoverTarget = new Vec3d(x, y, z);
        this.lastWanderAt = System.currentTimeMillis();

        targetWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y + 0.2, z, 15, 0.25, 0.2, 0.25, 0.05);
        targetWorld.playSound(null, x, y, z, SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.NEUTRAL, 0.9f, 1.7f);
    }

    public void emoteFlutter() {
        BatEntity bat = getBat();
        if (bat == null || !bat.isAlive() || !(bat.getWorld() instanceof ServerWorld sw)) return;
        bat.setVelocity(0, 0.35, 0);
        bat.velocityDirty = true;
        sw.spawnParticles(ParticleTypes.SCULK_SOUL, bat.getX(), bat.getY() + 0.2, bat.getZ(), 10, 0.2, 0.15, 0.2, 0.05);
        sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, bat.getX(), bat.getY() + 0.2, bat.getZ(), 8, 0.2, 0.15, 0.2, 0.04);
        sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.NEUTRAL, 0.8f, 1.8f);
    }

    public void onTbagDetected(BatEntity bat, ServerPlayerEntity player) {
        bat.setVelocity(0, 0.42, 0);
        bat.velocityDirty = true;

        if (bat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, bat.getX(), bat.getY() + 0.3, bat.getZ(), 20, 0.3, 0.25, 0.3, 0.08);
            sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, bat.getX(), bat.getY() + 0.3, bat.getZ(), 15, 0.3, 0.25, 0.3, 0.06);
            sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.NEUTRAL, 1.0f, 1.9f);
        }

        // Mensagem discreta na Action Bar exclusivamente para quem interagiu
        player.sendMessage(Text.literal("§8<Juninho>§r §5*FLIP SÔNICO!* 🦇✨ §7(Juninho deu um mortal aéreo de cabeça pra baixo respondendo à sua dança!)"), true);
    }

    /**
     * Interação com Juninho (botão direito de QUALQUER jogador):
     * - Pode ser alimentado com frutinhas (Sweet Berries, Glow Berries, Maçã).
     * - Pode receber carinho carinhoso.
     * - Tem chance de soltar um "Mimo das Sombras" a cada 30 minutos.
     * - TODAS as respostas vão para a Action Bar individual do jogador (zero chat flood!).
     */
    public void onPet(BatEntity bat, ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastPetTime < 500L) {
            return;
        }
        lastPetTime = now;

        this.petPauseUntil = now + 12_000L;
        this.hoverTarget = bat.getPos();
        bat.setVelocity(0, 0, 0);
        bat.velocityDirty = true;
        bat.getLookControl().lookAt(player, 60.0f, 60.0f);
        setFocusPlayer(player.getGameProfile().getName());
        applyAttributes(bat, player);

        ItemStack held = player.getMainHandStack();
        boolean isBerry = held.isOf(Items.SWEET_BERRIES) || held.isOf(Items.GLOW_BERRIES) || held.isOf(Items.APPLE);

        if (bat.getWorld() instanceof ServerWorld sw) {
            long lastMilk = lastBatMilkGiftPerPlayer.getOrDefault(player.getUuid(), 0L);
            boolean milkCooldownReady = (now - lastMilk) > 30_000L;

            if (isBerry) {
                if (!player.isCreative()) {
                    held.decrement(1);
                }
                sw.spawnParticles(ParticleTypes.HEART, bat.getX(), bat.getY() + 0.35, bat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, bat.getX(), bat.getY() + 0.3, bat.getZ(), 6, 0.2, 0.2, 0.2, 0.04);
                sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.9f, 1.4f);
                bat.setVelocity(0, 0.22, 0);
                bat.velocityDirty = true;

                if (milkCooldownReady && random.nextDouble() < 0.35) {
                    lastBatMilkGiftPerPlayer.put(player.getUuid(), now);
                    giveBatMilk(sw, bat, player, true);
                } else {
                    player.sendMessage(Text.literal("§8<Juninho>§r §a*crunch crunch!* 🍇🦇 §7(Juninho devorou a frutinha feliz e bateu asinhas no seu ombro!)"), true);
                }
            } else {
                sw.spawnParticles(ParticleTypes.HEART, bat.getX(), bat.getY() + 0.35, bat.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, bat.getX(), bat.getY() + 0.2, bat.getZ(), 6, 0.15, 0.15, 0.15, 0.03);
                sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_BAT_AMBIENT, SoundCategory.NEUTRAL, 0.8f, 1.7f);

                // Ao acariciar, tem chance de presentear com Bat Leite
                if (milkCooldownReady && random.nextDouble() < 0.25) {
                    lastBatMilkGiftPerPlayer.put(player.getUuid(), now);
                    giveBatMilk(sw, bat, player, false);
                } else {
                    player.sendMessage(Text.literal("§8<Juninho>§r §d*chirp chirp!* 🦇💖 §7(Juninho deu uma cabeçadinha carinhosa na sua mão e soltou um piadinho)"), true);
                }
            }
        }

        // Mimo das sombras: cooldown de 30 minutos e 15% de chance de soltar um tesouro da noite
        if (System.currentTimeMillis() - lastGiftAt > 1_800_000L && random.nextDouble() < 0.15) {
            deliverShadowGift(bat, player);
        }
    }

    private void giveBatMilk(ServerWorld sw, BatEntity bat, ServerPlayerEntity player, boolean wasFed) {
        ItemStack batMilk = createBatMilkItem();
        if (!player.getInventory().insertStack(batMilk)) {
            player.dropItem(batMilk, false);
        }

        sw.spawnParticles(ParticleTypes.HEART, bat.getX(), bat.getY() + 0.35, bat.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, bat.getX(), bat.getY() + 0.3, bat.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.spawnParticles(ParticleTypes.SCULK_SOUL, bat.getX(), bat.getY() + 0.2, bat.getZ(), 8, 0.2, 0.2, 0.2, 0.04);
        sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 0.9f, 1.3f);

        String reason = wasFed ? "agradecido pelo lanche" : "feliz com o carinho";
        player.sendMessage(Text.literal("§8<Juninho>§r §d*chirp chirp!* 🦇🥛 §7(Juninho ficou todo " + reason + " e te presenteou com um frasco de §f§lBat Leite§7!)"), true);
    }

    public static ItemStack createBatMilkItem() {
        ItemStack stack = new ItemStack(Items.POTION);
        stack.setCustomName(Text.literal("§f§lBat Leite§r").styled(s -> s.withItalic(false)));

        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList lore = new NbtList();
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§7Uma fórmula láctea mística produzida pelo morcego Juninho.").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§eAo beber, concede TODOS os buffs positivos do jogo por 15 segundos!").styled(s -> s.withItalic(false)))));
        lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("§5§o\"Nutritivo, místico e 100% bat-aprovado pela turma de Jodevil.\"").styled(s -> s.withItalic(true)))));
        display.put("Lore", lore);

        // Cor branca de leite puro (0xFFFFFF = 16777215)
        stack.getOrCreateNbt().putInt("CustomPotionColor", 0xFFFFFF);
        stack.getOrCreateNbt().putBoolean("BatLeite", true);
        stack.getOrCreateNbt().putBoolean("BatLeiteSanitizedV3", true);

        List<StatusEffectInstance> positiveBuffs = getCleanPositiveBuffs();
        PotionUtil.setCustomPotionEffects(stack, positiveBuffs);
        PotionUtil.setPotion(stack, Potions.WATER);

        return stack;
    }

    public static List<StatusEffectInstance> getCleanPositiveBuffs() {
        List<StatusEffectInstance> positiveBuffs = new ArrayList<>();
        for (StatusEffect effect : Registries.STATUS_EFFECT) {
            int amplifier = 1; // Nível II por padrão para buffs fortes
            if (effect == StatusEffects.HERO_OF_THE_VILLAGE) {
                amplifier = 4; // Nível V para descontos massivos
            } else if (effect == StatusEffects.STRENGTH
                    || effect == StatusEffects.HASTE
                    || effect == StatusEffects.LUCK) {
                amplifier = 2; // Nível III
            } else if (effect == StatusEffects.SPEED) {
                amplifier = 0; // Nível I (Speed I) - velocidade agradável sem perder controle da física
            } else if (effect == StatusEffects.JUMP_BOOST) {
                amplifier = 0; // Nível I (Jump Boost I) - pulo leve sem bater violentamente em tetos
            } else if (effect == StatusEffects.NIGHT_VISION
                    || effect == StatusEffects.WATER_BREATHING
                    || effect == StatusEffects.FIRE_RESISTANCE
                    || effect == StatusEffects.INVISIBILITY
                    || effect == StatusEffects.SLOW_FALLING
                    || effect == StatusEffects.DOLPHINS_GRACE
                    || effect == StatusEffects.CONDUIT_POWER) {
                amplifier = 0; // Nível I para efeitos booleanos
            }

            if (!isPurelyPositiveEffect(effect, amplifier)) {
                continue;
            }

            if (effect.isInstant()) {
                // Permite apenas efeitos instantâneos comprovadamente seguros e benéficos de cura/saturação
                if (effect == StatusEffects.INSTANT_HEALTH || effect == StatusEffects.SATURATION) {
                    positiveBuffs.add(new StatusEffectInstance(effect, 1, 1));
                }
            } else {
                positiveBuffs.add(new StatusEffectInstance(effect, 300, amplifier, false, true, true));
            }
        }
        return positiveBuffs;
    }

    public static boolean isPurelyPositiveEffect(StatusEffect effect, int amplifier) {
        if (effect == null) return false;
        if (effect.getCategory() != StatusEffectCategory.BENEFICIAL) return false;

        Identifier id = Registries.STATUS_EFFECT.getId(effect);
        if (id == null) return false;

        String idStr = id.toString().toLowerCase();
        String namespace = id.getNamespace().toLowerCase();
        String path = id.getPath().toLowerCase();

        // 1. Blacklist cirúrgica de efeitos problemáticos conhecidos por explosão, recuo involuntário ou conflitos
        if (idStr.equals("archon:shadow") || idStr.contains("shadow")) {
            return false;
        }

        // Bewitchment: o efeito wednesday dá dano no tick empurrando violentamente o player. Restringe bewitchment aos puramente utilitários
        if (namespace.equals("bewitchment")) {
            if (path.contains("wednesday") || (!path.equals("gills") && !path.equals("purity") && !path.equals("invigorating"))) {
                return false;
            }
        }

        // Palavras-chave no nome do efeito que causam dano, impulso violento, física descontrolada ou recuo
        if (path.contains("wednesday") || path.contains("momentum") || path.contains("bounce") || path.contains("bouncer")
                || path.contains("leap") || path.contains("slam") || path.contains("thrust") || path.contains("propel")
                || path.contains("propulsion") || path.contains("fling") || path.contains("launch") || path.contains("dash")
                || path.contains("slide") || path.contains("push") || path.contains("pull") || path.contains("recoil")
                || path.contains("knockback") || path.contains("repel") || path.contains("repulsion") || path.contains("wind")
                || path.contains("gale") || path.contains("squall") || path.contains("quake") || path.contains("shake")
                || path.contains("shockwave") || path.contains("burst") || path.contains("blast") || path.contains("explode")
                || path.contains("explosion") || path.contains("detonate") || path.contains("detonation") || path.contains("volatile")
                || path.contains("combustion") || path.contains("overload") || path.contains("havoc") || path.contains("eruption")
                || path.contains("concussion") || path.contains("earthquake") || path.contains("tremor") || path.contains("stormveil")
                || path.contains("chaos") || path.contains("bleed") || path.contains("hemorrhage") || path.contains("stagger")
                || path.contains("stun") || path.contains("gravity") || path.contains("vortex") || path.contains("singularity")
                || path.contains("implosion") || path.contains("unstable") || path.contains("paralyze") || path.contains("freeze")
                || path.contains("frozen") || path.contains("root") || path.contains("bind") || path.contains("petrify")
                || path.contains("warp") || path.contains("teleport") || path.contains("levitation") || path.contains("curse")
                || path.contains("hex") || path.contains("daze") || path.contains("vertigo") || path.contains("damage")
                || path.contains("harm") || path.contains("pain") || path.contains("dynamo") || path.contains("accelerate")) {
            return false;
        }

        // 2. FILTRAGEM CIRÚRGICA DE MODIFICADORES DE ATRIBUTOS
        // Se QUALQUER atributo tiver valor negativo (ex: -80% velocidade, -110% velocidade de ataque),
        // este efeito atua como um debuff no jogador e é terminantemente rejeitado!
        Map<EntityAttribute, EntityAttributeModifier> modifiers = effect.getAttributeModifiers();
        if (modifiers != null && !modifiers.isEmpty()) {
            for (Map.Entry<EntityAttribute, EntityAttributeModifier> entry : modifiers.entrySet()) {
                EntityAttributeModifier mod = entry.getValue();
                if (mod == null) continue;

                // Valor base do modificador negativo
                if (mod.getValue() < 0.0) {
                    return false;
                }

                // Valor ajustado com o amplifier do efeito
                try {
                    double adjusted = effect.adjustModifierAmount(amplifier, mod);
                    if (adjusted < 0.0) {
                        return false;
                    }
                } catch (Throwable ignored) {
                }
            }
        }

        return true;
    }

    public static void sanitizeBatMilkStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.hasNbt() || !stack.getNbt().getBoolean("BatLeite")) return;
        if (stack.getNbt().getBoolean("BatLeiteSanitizedV3")) return;

        List<StatusEffectInstance> cleanEffects = getCleanPositiveBuffs();
        PotionUtil.setCustomPotionEffects(stack, cleanEffects);
        stack.getOrCreateNbt().putBoolean("BatLeiteSanitizedV3", true);
    }

    public static void sanitizePlayerBatMilk(ServerPlayerEntity player) {
        if (player == null) return;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean("BatLeite")) {
                sanitizeBatMilkStack(stack);
            }
        }
    }

    public static void purgeNegativeEffectsFromPlayer(ServerPlayerEntity p) {
        if (p == null) return;
        List<StatusEffectInstance> active = new ArrayList<>(p.getStatusEffects());
        for (StatusEffectInstance inst : active) {
            StatusEffect eff = inst.getEffectType();
            if (!isPurelyPositiveEffect(eff, inst.getAmplifier())) {
                p.removeStatusEffect(eff);
            }
        }
    }

    private void checkBatMilkDrinking() {
        if (server == null) return;
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            sanitizePlayerBatMilk(p);

            boolean isDrinkingNow = p.isUsingItem()
                    && p.getActiveItem().hasNbt()
                    && p.getActiveItem().getNbt().getBoolean("BatLeite");

            if (isDrinkingNow) {
                sanitizeBatMilkStack(p.getActiveItem());
            }

            Boolean wasDrinking = drinkingBatMilk.get(p.getUuid());
            if (wasDrinking != null && wasDrinking && !isDrinkingNow) {
                // Se parou de beber e o tempo restante chegou ao fim (<= 2 ticks)
                if (p.getItemUseTimeLeft() <= 2) {
                    if (p.getWorld() instanceof ServerWorld sw) {
                        sw.spawnParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.4, 0.5, 0.4, 0.05);
                        sw.spawnParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1.0, p.getZ(), 15, 0.3, 0.5, 0.3, 0.04);
                        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.85f, 1.6f);
                        sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.PLAYERS, 0.9f, 1.7f);
                    }
                    p.sendMessage(Text.literal("§8[Bat Leite]§r §f§lGLUB GLUB! 🦇🥛 §aVocê bebeu o Bat Leite! Todos os buffs positivos ativados por 15s!"), true);

                    // Anula qualquer empurrão ou aceleração cinética residual no exato momento da ingestão
                    p.setVelocity(Vec3d.ZERO);
                    p.velocityModified = true;

                    // Purga imediatamente qualquer resíduo negativo ou efeito físico involuntário
                    purgeNegativeEffectsFromPlayer(p);
                    batMilkBuffedUntil.put(p.getUuid(), now + 16_000L);
                }
            }

            // Durante a vigência dos efeitos do Bat Leite, protege o jogador contra efeitos caóticos residuais
            if (now < batMilkBuffedUntil.getOrDefault(p.getUuid(), 0L)) {
                purgeNegativeEffectsFromPlayer(p);
            }

            if (isDrinkingNow) {
                drinkingBatMilk.put(p.getUuid(), true);
            } else {
                drinkingBatMilk.remove(p.getUuid());
            }
        }
    }

    private void deliverShadowGift(BatEntity bat, ServerPlayerEntity player) {
        if (!(bat.getWorld() instanceof ServerWorld sw)) return;
        lastGiftAt = System.currentTimeMillis();

        ItemStack gift;
        int roll = random.nextInt(6);
        switch (roll) {
            case 0 -> gift = new ItemStack(Items.ECHO_SHARD, 1);
            case 1 -> gift = new ItemStack(Items.AMETHYST_SHARD, 2);
            case 2 -> gift = new ItemStack(Items.GLOW_BERRIES, 4);
            case 3 -> gift = new ItemStack(Items.REDSTONE, 5);
            case 4 -> gift = createBatMilkItem();
            default -> gift = new ItemStack(Items.SWEET_BERRIES, 4);
        }

        ItemEntity drop = new ItemEntity(sw, bat.getX(), bat.getY() + 0.2, bat.getZ(), gift);
        drop.setVelocity((player.getX() - bat.getX()) * 0.15, 0.25, (player.getZ() - bat.getZ()) * 0.15);
        sw.spawnEntity(drop);

        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, bat.getX(), bat.getY() + 0.4, bat.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 1.0f, 1.1f);
    }

    private void applyAttributes(BatEntity bat, ServerPlayerEntity owner) {
        bat.setCustomName(Text.literal("§8Juninho §5(Morcego Guardião)§r"));
        bat.setCustomNameVisible(true);
        bat.setInvulnerable(true);
        bat.setPersistent();
        bat.setNoGravity(true);
        bat.setAir(300);
        bat.setRoosting(false);

        if (!bat.getCommandTags().contains(JUNINHO_TAG)) {
            bat.addCommandTag(JUNINHO_TAG);
        }

        if (mod.body() != null) {
            mod.body().disableEntityCollision(bat, "juninho_noclip", Formatting.DARK_PURPLE);
        }

        // Leve aumento de escala (1.2x) via Pehkui se disponível
        if (!scaleApplied && server != null) {
            try {
                server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(),
                        "scale set pehkui:base 1.2 " + bat.getUuidAsString()
                );
                scaleApplied = true;
            } catch (Exception ignored) {}
        }
    }
}
