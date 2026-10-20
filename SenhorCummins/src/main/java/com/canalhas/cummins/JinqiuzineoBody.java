package com.canalhas.cummins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.CatVariant;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Gerencia a presença física do mini tigre "Jinqiuzineo" (Jin Qiu de Dislyte / Ru Shou).
 * - Espécie: Tigre em miniatura (porte felino/gato, pelagem rajada/tabby com coleira roxa).
 * - Execução de Ru Shou: Ao detectar monstros perto de seu tutor, salta ferozmente aplicando
 *   Wither, Slowness, Glowing e arremessando o inimigo com knockback, além de conceder Força ao tutor.
 * - Comportamento Estilo Cummins: Orbita o jogador, nunca fica na frente da visão,
 *   reage a carinho e barbantes de forma 100% privada na Action Bar (zero spam global).
 * - Imortalidade: 100% invulnerável, persistente, noclip (sem colisão física) e imune ao void.
 */
public class JinqiuzineoBody {
    public static final String JINQIUZINEO_TAG = "jinqiuzineo_tiger_body";
    public static final String OWNER_NAME = "Azarasquel";

    private final CumminsMod mod;
    private MinecraftServer server;
    private UUID catUuid;
    private String lastFocusPlayerName = OWNER_NAME;
    private boolean activeInSession = false;
    private boolean scaleApplied = false;
    private int tickCounter = 0;
    private long lastGiftAt = System.currentTimeMillis();
    private boolean lastFocusSneaking = false;
    private final List<Long> sneakHistory = new ArrayList<>();
    private long lastTbagAt = 0;
    private long lastPetTime = 0;
    private long lastWanderAt = 0;
    private long lastRoarAt = 0;
    private long lastDefenseAt = 0;
    private long petPauseUntil = 0;
    private boolean manuallyDismissed = false;
    private final Random random = new Random();

    public JinqiuzineoBody(CumminsMod mod) {
        this.mod = mod;
    }

    public void start(MinecraftServer server) {
        this.server = server;
        this.activeInSession = false;
        this.scaleApplied = false;
        this.lastWanderAt = 0;
        this.lastRoarAt = 0;
        this.lastDefenseAt = 0;
        this.petPauseUntil = 0;
        String savedUuid = mod.context().jinqiuzineoUuid();
        if (savedUuid != null && !savedUuid.isBlank()) {
            try {
                this.catUuid = UUID.fromString(savedUuid);
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
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME)) return p;
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
        for (ServerWorld world : server.getWorlds()) {
            List<CatEntity> toDiscard = new ArrayList<>();
            for (Entity e : world.iterateEntities()) {
                if (e instanceof CatEntity cat && cat.isAlive() && cat.getCommandTags().contains(JINQIUZINEO_TAG)) {
                    if (primary == null) {
                        primary = cat;
                        this.catUuid = cat.getUuid();
                        mod.context().setJinqiuzineoUuid(catUuid.toString());
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

    public CatEntity summonOrTeleportTo(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        this.activeInSession = true;
        this.manuallyDismissed = false;
        this.lastFocusPlayerName = player.getGameProfile().getName();

        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(JINQIUZINEO_TAG);
        }

        CatEntity cat = getCat();
        if (cat == null) {
            cat = spawnNear(player);
        } else {
            teleportToPlayer(cat, player);
            applyAttributes(cat, player);
        }
        return cat;
    }

    public void dismiss() {
        this.manuallyDismissed = true;
        this.activeInSession = false;
        despawnIfPresent();
    }

    public void despawnIfPresent() {
        this.activeInSession = false;
        this.scaleApplied = false;
        CatEntity cat = getCat();
        if (cat != null) {
            if (cat.hasVehicle()) cat.stopRiding();
            if (cat.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.3, cat.getZ(), 10, 0.2, 0.15, 0.2, 0.04);
                sw.spawnParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.3, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
            cat.discard();
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

        List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
        if (onlinePlayers.isEmpty()) {
            if (activeInSession) {
                despawnIfPresent();
            }
            return;
        }

        boolean ownerOnline = false;
        for (ServerPlayerEntity sp : onlinePlayers) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(OWNER_NAME)) {
                ownerOnline = true;
                if (!activeInSession && !manuallyDismissed) {
                    activeInSession = true;
                    lastFocusPlayerName = sp.getGameProfile().getName();
                }
                break;
            }
        }

        if (!activeInSession && (!ownerOnline || manuallyDismissed)) {
            if (tickCounter % 40 == 0) {
                CatEntity stray = getCat();
                if (stray != null) {
                    if (stray.hasVehicle()) stray.stopRiding();
                    stray.discard();
                }
            }
            return;
        }

        CatEntity cat = getCat();
        if (cat == null) {
            ServerPlayerEntity focus = getFocusPlayer();
            if (focus != null) {
                cat = spawnNear(focus);
            }
            if (cat == null) return;
        }

        if (cat.getY() < -55) {
            ServerPlayerEntity focus = getFocusPlayer();
            if (focus != null) teleportToPlayer(cat, focus);
            return;
        }

        if (tickCounter % 40 == 0) {
            ServerPlayerEntity focus = getFocusPlayer();
            if (focus != null) applyAttributes(cat, focus);
        }

        ServerPlayerEntity focus = getFocusPlayer();
        if (focus == null) return;

        // Detecção de T-bag (Shift 3x)
        boolean isSneaking = focus.isSneaking();
        if (isSneaking && !lastFocusSneaking) {
            long nowSneak = System.currentTimeMillis();
            if (cat.squaredDistanceTo(focus) <= 6.0 * 6.0) {
                sneakHistory.add(nowSneak);
                sneakHistory.removeIf(t -> nowSneak - t > 2000L);
                if (sneakHistory.size() >= 3 && nowSneak - lastTbagAt > 15_000L) {
                    lastTbagAt = nowSneak;
                    sneakHistory.clear();
                    onTbagDetected(cat, focus);
                }
            }
        }
        lastFocusSneaking = isSneaking;

        boolean differentWorld = cat.getWorld() != focus.getWorld();
        double distSq = differentWorld ? Double.MAX_VALUE : cat.squaredDistanceTo(focus);

        if (differentWorld || distSq > 32.0 * 32.0) {
            teleportToPlayer(cat, focus);
            return;
        }

        long now = System.currentTimeMillis();

        if (distSq > 8.0 * 8.0) {
            this.petPauseUntil = 0;
        }
        boolean inPetAffection = now < petPauseUntil;

        // 1. Proteção de Ru Shou contra monstros
        if (tickCounter % 15 == 0) {
            handleMonsterDefense(cat, focus, now);
        }

        if (inPetAffection) {
            cat.setSitting(true);
            cat.setInSittingPose(true);
            cat.getNavigation().stop();
            cat.setVelocity(Vec3d.ZERO);
            cat.getLookControl().lookAt(focus, 60.0f, 60.0f);
            if (tickCounter % 20 == 0 && cat.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.5, cat.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
            }
            return;
        } else {
            if (cat.isSitting()) {
                cat.setSitting(false);
                cat.setInSittingPose(false);
            }
        }

        // 2. Comportamento de caminhada e órbita amigável
        if (distSq > 10.0 * 10.0) {
            cat.getLookControl().lookAt(focus, 40.0f, 40.0f);
            wanderAroundPlayer(cat, focus, 0.56D);
        } else if (distSq > 3.2 * 3.2) {
            cat.getLookControl().lookAt(focus, 30.0f, 30.0f);
            if (cat.getNavigation().isIdle() || (now - lastWanderAt > 8000L)) {
                lastWanderAt = now;
                wanderAroundPlayer(cat, focus, 0.38D);
            }
        } else {
            cat.getLookControl().lookAt(focus, 60.0f, 60.0f);
            if (!cat.getNavigation().isIdle()) {
                cat.getNavigation().stop();
            }

            // Rugidinho em miniatura ambiente espontâneo
            if (now - lastRoarAt > 35_000L && random.nextDouble() < 0.22) {
                lastRoarAt = now;
                emitAmbientMiniRoar(cat, focus);
            }
        }
    }

    /**
     * Sistema de Combate — "Execução de Ru Shou"
     */
    private void handleMonsterDefense(CatEntity cat, ServerPlayerEntity player, long now) {
        if (now - lastDefenseAt < 3000L || !(cat.getWorld() instanceof ServerWorld sw)) return;

        Box box = player.getBoundingBox().expand(7.5, 4.0, 7.5);
        List<LivingEntity> monsters = sw.getEntitiesByClass(
                LivingEntity.class, box,
                e -> e.isAlive() && (e instanceof net.minecraft.entity.mob.Monster)
        );

        if (monsters.isEmpty()) return;

        LivingEntity target = monsters.get(0);
        lastDefenseAt = now;
        lastRoarAt = now;

        double dx = target.getX() - cat.getX();
        double dz = target.getZ() - cat.getZ();
        double dist = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));

        // Salto do tigre de ataque
        cat.setVelocity((dx / dist) * 0.50, 0.38, (dz / dist) * 0.50);
        cat.velocityDirty = true;
        cat.getLookControl().lookAt(target, 60.0f, 60.0f);

        // Combinação de sons de ataque e rugido
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.NEUTRAL, 0.8f, 1.4f);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_HISS, SoundCategory.NEUTRAL, 0.8f, 0.7f);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.35f, 1.6f);

        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.4, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.03);
        sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.25, 0.25, 0.25, 0.08);
        sw.spawnParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.5, target.getZ(), 6, 0.2, 0.2, 0.2, 0.04);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);

        // Repulsão, Wither, Lentidão e Glowing (Marca do Condenado)
        target.takeKnockback(1.1D, -(dx / dist), -(dz / dist));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, true, true));

        if (target instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setTarget(null);
        }

        // Bônus de Força para o player protegido
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, false, true, true));

        String targetName = target.getName().getString();
        List<String> defenseMsgs = List.of(
                "§5<Jinqiuzineo>§r §d*CHICOTE DE RU SHOU!* 🐯⛓️ §7(\"Hora da disciplina, " + targetName + "! ...Ei, abaixa a cabeça pra eu alcançar sua cara!\")",
                "§5<Jinqiuzineo>§r §d*GARRA DA PUNIÇÃO!* 🐯💢 §7(\"Ninguém toca no meu carcereiro! Sinta o peso da lei nos seus tornozelos, " + targetName + "!\")",
                "§5<Jinqiuzineo>§r §d*INTIMIDAÇÃO LETAL!* 🐯🔥 §7(\"Julgado e condenado! ...Mesmo que minha caixa de colisão tenha meio metro!\")"
        );
        player.sendMessage(Text.literal(defenseMsgs.get(random.nextInt(defenseMsgs.size()))), true);
    }

    private void emitAmbientMiniRoar(CatEntity cat, ServerPlayerEntity player) {
        if (!(cat.getWorld() instanceof ServerWorld sw)) return;
        cat.setVelocity(0, 0.26, 0);
        cat.velocityDirty = true;

        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_HISS, SoundCategory.NEUTRAL, 0.6f, 0.8f);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.85f, 0.85f);

        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.4, cat.getZ(), 4, 0.15, 0.15, 0.15, 0.03);
        sw.spawnParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.5, cat.getZ(), 4, 0.15, 0.15, 0.15, 0.02);

        // Easter Egg: Interação Dupla com Javidizineo caso esteja a menos de 6 blocos
        if (mod.javidizineo() != null && mod.javidizineo().getCat() != null) {
            CatEntity javidCat = mod.javidizineo().getCat();
            if (javidCat.isAlive() && cat.squaredDistanceTo(javidCat) <= 6.0 * 6.0 && random.nextDouble() < 0.40) {
                player.sendMessage(Text.literal("§5<Jinqiuzineo>§r §d*encara o Javidizineo* 🐯⚡🦁 §7(\"Ei, Javid! Belo sininho na coleira laranja, majestade! Já aprendeu a usar a caixa de areia?\")"), true);
                return;
            }
        }

        List<String> ambientMsgs = List.of(
                "§5<Jinqiuzineo>§r §d*rawr-hiss!* 🐯🚬 §7(\"Daria metade do meu poder de Ru Shou por um charuto agora... mas só tem trigo quadrado neste lugar!\")",
                "§5<Jinqiuzineo>§r §d*grrr-miau!* 🐯❄️ §7(\"Olha bem pra minha pelagem branca, humano. Isso é marca do lendário Tigre Branco de Ru Shou, não de gatinho de vó!\")"
        );
        player.sendMessage(Text.literal(ambientMsgs.get(random.nextInt(ambientMsgs.size()))), true);
    }

    private void wanderAroundPlayer(CatEntity cat, ServerPlayerEntity focus, double speed) {
        double dx = cat.getX() - focus.getX();
        double dz = cat.getZ() - focus.getZ();
        double currentAngle = (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) ? random.nextDouble() * 2 * Math.PI : Math.atan2(dz, dx);
        double deltaAngle = (random.nextDouble() - 0.5) * (Math.PI * 0.70);
        double angle = currentAngle + deltaAngle;
        double radius = 1.3 + random.nextDouble() * 0.8;

        int targetX = (int) Math.floor(focus.getX() + Math.cos(angle) * radius);
        int targetZ = (int) Math.floor(focus.getZ() + Math.sin(angle) * radius);
        BlockPos targetPos = new BlockPos(targetX, focus.getBlockY(), targetZ);

        if (cat.getWorld() instanceof ServerWorld sw) {
            BlockPos safePos = PipiraBody.findSafeGroundNear(targetPos, sw);
            cat.getNavigation().startMovingTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, speed);
        }
    }

    public CatEntity spawnNear(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        ServerWorld world = player.getServerWorld();

        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw + 180.0);
        int bx = (int) Math.floor(player.getX() - Math.sin(rad) * 2.6);
        int bz = (int) Math.floor(player.getZ() + Math.cos(rad) * 2.6);
        BlockPos behind = new BlockPos(bx, player.getBlockY(), bz);
        BlockPos safe = PipiraBody.findSafeGroundNear(behind, world);

        CatEntity cat = EntityType.CAT.create(world);
        if (cat == null) return null;

        cat.refreshPositionAndAngles(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYaw(), 0);
        applyAttributes(cat, player);

        world.spawnEntity(cat);
        this.catUuid = cat.getUuid();
        mod.context().setJinqiuzineoUuid(catUuid.toString());
        mod.context().save();

        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.3, cat.getZ(), 15, 0.25, 0.15, 0.25, 0.05);
        world.spawnParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.4, cat.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        world.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.35f, 1.7f);
        return cat;
    }

    public void teleportToPlayer(CatEntity cat, ServerPlayerEntity player) {
        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(JINQIUZINEO_TAG);
        }
        if (cat.hasVehicle()) {
            cat.stopRiding();
        }
        ServerWorld targetWorld = player.getServerWorld();

        float yaw = player.getYaw();
        double rad = Math.toRadians(yaw + 180.0);
        int bx = (int) Math.floor(player.getX() - Math.sin(rad) * 2.6);
        int bz = (int) Math.floor(player.getZ() + Math.cos(rad) * 2.6);
        BlockPos behind = new BlockPos(bx, player.getBlockY(), bz);
        BlockPos safePos = PipiraBody.findSafeGroundNear(behind, targetWorld);

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

        targetWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y + 0.2, z, 12, 0.25, 0.15, 0.25, 0.05);
        targetWorld.playSound(null, x, y, z, SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 0.9f);
    }

    public void onTbagDetected(CatEntity cat, ServerPlayerEntity player) {
        cat.setVelocity(0, 0.42, 0);
        cat.velocityDirty = true;

        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.6, cat.getZ(), 15, 0.25, 0.25, 0.25, 0.06);
            sw.spawnParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.3, cat.getZ(), 10, 0.2, 0.2, 0.2, 0.04);
            sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.45f, 1.7f);
        }

        player.sendMessage(Text.literal("§5<Jinqiuzineo>§r §d*SALTO DO CARRASCO!* 🐯⚡ §7(\"Pagando flexão no pátio da prisão?! Continua assim, recruta!\")"), true);
    }

    public void onPet(CatEntity cat, ServerPlayerEntity player) {
        long now = System.currentTimeMillis();
        if (now - lastPetTime < 500L) {
            return;
        }
        lastPetTime = now;

        cat.getNavigation().stop();
        cat.getLookControl().lookAt(player, 60.0f, 60.0f);
        this.petPauseUntil = now + 12_000L;
        setFocusPlayer(player.getGameProfile().getName());
        applyAttributes(cat, player);

        ItemStack held = player.getMainHandStack();
        boolean isStringOrLead = held.isOf(Items.STRING) || held.isOf(Items.LEAD);
        boolean isMeatOrFish = held.isOf(Items.BEEF) || held.isOf(Items.COOKED_BEEF) || held.isOf(Items.SALMON)
                || held.isOf(Items.COOKED_SALMON) || held.isOf(Items.MUTTON) || held.isOf(Items.COOKED_MUTTON) || held.isOf(Items.PORKCHOP);

        if (cat.getWorld() instanceof ServerWorld sw) {
            if (isStringOrLead) {
                sw.spawnParticles(ParticleTypes.CRIT, cat.getX(), cat.getY() + 0.5, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.4, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.04);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.85f, 1.2f);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.NEUTRAL, 0.7f, 1.4f);
                cat.setVelocity(0, 0.28, 0);
                cat.velocityDirty = true;
                player.sendMessage(Text.literal("§5<Jinqiuzineo>§r §d*SWISH-MIAU!* 🧶🐯 §7(\"Finalmente um chicote novo! ...Não, eu NÃO estou brincando com o barbante, estou testando a tensão da corda!\")"), true);
            } else if (isMeatOrFish) {
                if (!player.isCreative()) held.decrement(1);
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.5, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.4, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.04);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.9f, 1.1f);
                cat.setVelocity(0, 0.22, 0);
                cat.velocityDirty = true;

                player.sendMessage(Text.literal("§5<Jinqiuzineo>§r §d*nhac-grrr!* 🥩🐯 §7(\"Tentando subornar o carcereiro de Aeterna com comida?! ...Humpf, passa mais um pedaço pra cá.\")"), true);
            } else {
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.5, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.4, cat.getZ(), 4, 0.15, 0.15, 0.15, 0.03);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 0.9f);

                player.sendMessage(Text.literal("§5<Jinqiuzineo>§r §d*grrr... prrr!* 🐯💜 §7(\"Tira a mão daí, tá bagunçando meu topete! ...Tá bom, coça atrás da orelha, mas se contar pro Javid eu te prendo!\")"), true);
            }
        }

        if (System.currentTimeMillis() - lastGiftAt > 1_800_000L && random.nextDouble() < 0.15) {
            deliverTigerGift(cat, player);
        }
    }

    private void deliverTigerGift(CatEntity cat, ServerPlayerEntity player) {
        if (!(cat.getWorld() instanceof ServerWorld sw)) return;
        lastGiftAt = System.currentTimeMillis();

        ItemStack gift;
        int roll = random.nextInt(5);
        switch (roll) {
            case 0 -> gift = new ItemStack(Items.STRING, 3);
            case 1 -> gift = new ItemStack(Items.GOLD_NUGGET, 5);
            case 2 -> gift = new ItemStack(Items.GUNPOWDER, 3);
            case 3 -> gift = new ItemStack(Items.COOKED_SALMON, 3);
            default -> gift = new ItemStack(Items.IRON_INGOT, 2);
        }

        ItemEntity drop = new ItemEntity(sw, cat.getX(), cat.getY() + 0.2, cat.getZ(), gift);
        drop.setVelocity((player.getX() - cat.getX()) * 0.15, 0.25, (player.getZ() - cat.getZ()) * 0.15);
        sw.spawnEntity(drop);

        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.4, cat.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 1.0f, 1.1f);

        player.sendMessage(Text.literal("§5<Jinqiuzineo>§r §d*CONTRABANDO CONFISCADO!* 🐯⛓️ §7(\"Confisquei isso dos monstros locais. Guarde no inventário e não faça perguntas.\")"), true);
    }

    private void applyAttributes(CatEntity cat, ServerPlayerEntity owner) {
        cat.setCustomName(Text.literal("§5Jinqiuzineo §f(Mini Tigre Branco)§r"));
        cat.setCustomNameVisible(true);
        cat.setInvulnerable(true);
        cat.setPersistent();
        cat.setCollarColor(DyeColor.PURPLE);

        // Pelagem de Tigre Branco (White Cat puro com heterocromia)
        CatVariant variant = Registries.CAT_VARIANT.get(CatVariant.WHITE);
        if (variant == null) {
            variant = Registries.CAT_VARIANT.get(new Identifier("minecraft", "white"));
        }
        if (variant != null) {
            cat.setVariant(variant);
        }

        if (!cat.getCommandTags().contains(JINQIUZINEO_TAG)) {
            cat.addCommandTag(JINQIUZINEO_TAG);
        }

        if (mod.body() != null) {
            mod.body().disableEntityCollision(cat, "jinqiuzineo_noclip", Formatting.DARK_PURPLE);
            mod.body().removeUnwantedPetGoals(cat);
        }

        // Leve aumento de escala (1.06x) para porte de mini tigre robusto
        if (!scaleApplied && server != null) {
            try {
                server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(),
                        "scale set pehkui:base 1.06 " + cat.getUuidAsString()
                );
                scaleApplied = true;
            } catch (Exception ignored) {}
        }
    }
}
