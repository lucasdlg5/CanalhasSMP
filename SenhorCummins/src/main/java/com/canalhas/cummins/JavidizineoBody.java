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
 * Gerencia a presença física do mini leão "Javidizineo" de Azarasquel.
 * - Espécie: Leão em miniatura (porte felino/gato, pelagem alaranjada/dourada com porte de filhote de leão).
 * - Rugidos em Miniatura: Solta rugidinhos ferozes e meigos ("rawrrr!") tanto em momentos pacíficos
 *   quanto para defender seu tutor.
 * - Proteção Ativa: Ao detectar monstros ou perigos perto de Azarasquel, salta na criatura, ruge
 *   e a arremessa para longe, aplicando fraqueza e lentidão.
 * - Comportamento Estilo Cummins: Orbita o jogador, nunca fica na frente da visão (step aside),
 *   reage a carinho na Action Bar (zero spam no chat) e participa da dancinha do Shift.
 * - Imortalidade: 100% invulnerável, persistente, noclip (sem colisão física com players) e imune ao void.
 */
public class JavidizineoBody {
    public static final String JAVIDIZINEO_TAG = "javidizineo_lion_body";
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

    public JavidizineoBody(CumminsMod mod) {
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
        String savedUuid = mod.context().javidizineoUuid();
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
                if (e instanceof CatEntity cat && cat.isAlive() && cat.getCommandTags().contains(JAVIDIZINEO_TAG)) {
                    if (primary == null) {
                        primary = cat;
                        this.catUuid = cat.getUuid();
                        mod.context().setJavidizineoUuid(catUuid.toString());
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
            mod.body().releaseFromCarryOn(JAVIDIZINEO_TAG);
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
                sw.spawnParticles(ParticleTypes.FLAME, cat.getX(), cat.getY() + 0.3, cat.getZ(), 10, 0.2, 0.15, 0.2, 0.04);
                sw.spawnParticles(ParticleTypes.POOF, cat.getX(), cat.getY() + 0.3, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
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
            this.petPauseUntil = 0; // se o jogador se afastar mais de 8 blocos, retoma o acompanhamento normal
        }
        boolean inPetAffection = now < petPauseUntil;

        // 1. Proteção de Azarasquel contra monstros (pounce e mini-rugido afugentador)
        if (tickCounter % 15 == 0) {
            handleMonsterDefense(cat, focus, now);
        }

        if (inPetAffection) {
            // Em estado de carinho: senta calmo, para 100% de andar e olha com afeto para o jogador!
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

        // 2. Comportamento de caminhada e órbita amigável (calmo, desacelerado e pertinho do tutor)
        if (distSq > 10.0 * 10.0) {
            cat.getLookControl().lookAt(focus, 40.0f, 40.0f);
            wanderAroundPlayer(cat, focus, 0.55D);
        } else if (distSq > 3.2 * 3.2) {
            cat.getLookControl().lookAt(focus, 30.0f, 30.0f);
            if (cat.getNavigation().isIdle() || (now - lastWanderAt > 8000L)) {
                lastWanderAt = now;
                wanderAroundPlayer(cat, focus, 0.38D);
            }
        } else {
            // Player está bem perto (dist <= 3.2 blocos): PARA DE ANDAR para ficar ao lado e receber carinho facilmente!
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
     * Sistema de Proteção Ativa do Javidizineo:
     * O mini leão salta ferozmente na direção de qualquer monstro que se aproxime
     * de Azarasquel, soltando um potente rugido em miniatura que repele o inimigo!
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

        // Salto felino de ataque
        cat.setVelocity((dx / dist) * 0.45, 0.36, (dz / dist) * 0.45);
        cat.velocityDirty = true;
        cat.getLookControl().lookAt(target, 60.0f, 60.0f);

        // Som de mini-rugido feroz (combinação de rugido de ravager em tom agudo + chiado felino)
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.45f, 1.85f);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_HISS, SoundCategory.NEUTRAL, 0.7f, 1.2f);

        sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.25, 0.25, 0.25, 0.08);
        sw.spawnParticles(ParticleTypes.FLAME, cat.getX(), cat.getY() + 0.4, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.03);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);

        // Repulsão e enfraquecimento
        target.takeKnockback(1.0D, -(dx / dist), -(dz / dist));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1, false, true, true));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, true, true));

        if (target instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.setTarget(null);
        }

        String targetName = target.getName().getString();
        List<String> defenseMsgs = List.of(
                "§6<Javidizineo>§r §e*RUGIDO DE SHAMASH!* 🦁🔥 §7(\"Ajoelhe-se, " + targetName + "! ...Ei, pare de olhar pra cima, eu tô aqui embaixo!\")",
                "§6<Javidizineo>§r §e*ROAAARRR!* 🦁👑 §7(\"Sinta a fúria solar das minhas mordidinhas no tornozelo, " + targetName + "!\")",
                "§6<Javidizineo>§r §e*JULGAMENTO SOLAR!* 🦁✨ §7(\"Fique atrás de mim, humano! Minha hitbox é minúscula, mas meu orgulho é impenetrável!\")"
        );
        player.sendMessage(Text.literal(defenseMsgs.get(random.nextInt(defenseMsgs.size()))), true);
    }

    private void emitAmbientMiniRoar(CatEntity cat, ServerPlayerEntity player) {
        if (!(cat.getWorld() instanceof ServerWorld sw)) return;
        cat.setVelocity(0, 0.26, 0);
        cat.velocityDirty = true;

        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.35f, 1.95f);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.85f, 0.9f);

        sw.spawnParticles(ParticleTypes.CRIT, cat.getX(), cat.getY() + 0.5, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
        sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.6, cat.getZ(), 3, 0.2, 0.2, 0.2, 0.04);

        List<String> ambientMsgs = List.of(
                "§6<Javidizineo>§r §e*rawrrr!* 🦁☀️ §7(\"Pare de me encarar! Minha estatura foi comprimida, mas minha fúria ainda é do tamanho de um sol!\")",
                "§6<Javidizineo>§r §e*miau... digo, ROAR!* 🦁💢 §7(\"Por que tudo neste mundo tem quinas?! Até o sol é quadrado! Que heresia é essa?!\")",
                "§6<Javidizineo>§r §e*prrr-grrr!* 🦁🐾 §7(\"Não, eu não estava lambendo minha pata! Estava polindo minhas garras letais!\")"
        );
        player.sendMessage(Text.literal(ambientMsgs.get(random.nextInt(ambientMsgs.size()))), true);
    }

    private void wanderAroundPlayer(CatEntity cat, ServerPlayerEntity focus, double speed) {
        double dx = cat.getX() - focus.getX();
        double dz = cat.getZ() - focus.getZ();
        double currentAngle = (Math.abs(dx) < 0.01 && Math.abs(dz) < 0.01) ? random.nextDouble() * 2 * Math.PI : Math.atan2(dz, dx);
        double deltaAngle = (random.nextDouble() - 0.5) * (Math.PI * 0.70);
        double angle = currentAngle + deltaAngle;
        double radius = 1.3 + random.nextDouble() * 0.8; // 1.3 a 2.1 blocos (coladinho no tutor)

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
        mod.context().setJavidizineoUuid(catUuid.toString());
        mod.context().save();

        world.spawnParticles(ParticleTypes.FLAME, cat.getX(), cat.getY() + 0.3, cat.getZ(), 15, 0.25, 0.15, 0.25, 0.05);
        world.spawnParticles(ParticleTypes.CRIT, cat.getX(), cat.getY() + 0.5, cat.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
        world.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.4f, 1.9f);
        return cat;
    }

    public void teleportToPlayer(CatEntity cat, ServerPlayerEntity player) {
        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(JAVIDIZINEO_TAG);
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

        targetWorld.spawnParticles(ParticleTypes.FLAME, x, y + 0.2, z, 12, 0.25, 0.15, 0.25, 0.05);
        targetWorld.playSound(null, x, y, z, SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 1.0f);
    }

    public void onTbagDetected(CatEntity cat, ServerPlayerEntity player) {
        cat.setVelocity(0, 0.42, 0);
        cat.velocityDirty = true;

        if (cat.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.CRIT, cat.getX(), cat.getY() + 0.6, cat.getZ(), 15, 0.25, 0.25, 0.25, 0.06);
            sw.spawnParticles(ParticleTypes.FLAME, cat.getX(), cat.getY() + 0.3, cat.getZ(), 10, 0.2, 0.2, 0.2, 0.04);
            sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.5f, 1.9f);
        }

        player.sendMessage(Text.literal("§6<Javidizineo>§r §e*SALTO REAL DE SHAMASH!* 🦁👑 §7(\"Que ritual de agachamento é esse?! Está reverenciando o Sol?! Eu aceito!\")"), true);
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
        boolean isMeat = held.isOf(Items.BEEF) || held.isOf(Items.COOKED_BEEF) || held.isOf(Items.MUTTON)
                || held.isOf(Items.COOKED_MUTTON) || held.isOf(Items.PORKCHOP) || held.isOf(Items.BREAD);

        if (cat.getWorld() instanceof ServerWorld sw) {
            if (isMeat) {
                if (!player.isCreative()) held.decrement(1);
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.5, cat.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.4, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.04);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.9f, 1.2f);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.NEUTRAL, 0.35f, 2.0f);
                cat.setVelocity(0, 0.25, 0);
                cat.velocityDirty = true;

                List<String> meatMsgs = List.of(
                        "§6<Javidizineo>§r §e*nhac nhac rawr!* 🥩🦁 §7(\"Acha que pode comprar o Juiz do Deserto com carne?! ...Humpf, aceitarei apenas para manter minha energia vital.\")",
                        "§6<Javidizineo>§r §e*nhac-prrr!* 🥩🔥 §7(\"Uma oferenda digna de Shamash! Pode trazer mais quando quiser, carcereiro.\")"
                );
                player.sendMessage(Text.literal(meatMsgs.get(random.nextInt(meatMsgs.size()))), true);
            } else {
                sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.5, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
                sw.spawnParticles(ParticleTypes.CRIT, cat.getX(), cat.getY() + 0.4, cat.getZ(), 4, 0.15, 0.15, 0.15, 0.03);
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.8f, 1.1f);

                List<String> petMsgs = List.of(
                        "§6<Javidizineo>§r §e*prrr... DIGO, GRRR!* 🦁💖 §7(\"Tire essas mãos da minha juba sagrada! ...Não pare agora, coce atrás da orelha esquerda.\")",
                        "§6<Javidizineo>§r §e*prrr-rawr!* 🦁💢 §7(\"Eu sou uma fera ancestral de Grandis, não um gatinho doméstico! ...*ronrom*\")"
                );
                player.sendMessage(Text.literal(petMsgs.get(random.nextInt(petMsgs.size()))), true);
            }
        }

        if (System.currentTimeMillis() - lastGiftAt > 1_800_000L && random.nextDouble() < 0.15) {
            deliverLionGift(cat, player);
        }
    }

    private void deliverLionGift(CatEntity cat, ServerPlayerEntity player) {
        if (!(cat.getWorld() instanceof ServerWorld sw)) return;
        lastGiftAt = System.currentTimeMillis();

        ItemStack gift;
        int roll = random.nextInt(5);
        switch (roll) {
            case 0 -> gift = new ItemStack(Items.GOLD_INGOT, 2);
            case 1 -> gift = new ItemStack(Items.GOLDEN_CARROT, 2);
            case 2 -> gift = new ItemStack(Items.COOKED_BEEF, 4);
            case 3 -> gift = new ItemStack(Items.BONE, 4);
            default -> gift = new ItemStack(Items.BREAD, 3);
        }

        ItemEntity drop = new ItemEntity(sw, cat.getX(), cat.getY() + 0.2, cat.getZ(), gift);
        drop.setVelocity((player.getX() - cat.getX()) * 0.15, 0.25, (player.getZ() - cat.getZ()) * 0.15);
        sw.spawnEntity(drop);

        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.4, cat.getZ(), 10, 0.25, 0.25, 0.25, 0.05);
        sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.NEUTRAL, 1.0f, 1.1f);

        player.sendMessage(Text.literal("§6<Javidizineo>§r §e*TRIBUTO REAL!* 🦁🪙 §7(\"Pegue isto, humano. É um tributo do Sol, e definitivamente NÃO um presentinho de gato!\")"), true);
    }

    private void applyAttributes(CatEntity cat, ServerPlayerEntity owner) {
        cat.setCustomName(Text.literal("§6Javidizineo §e(Mini Leão)§r"));
        cat.setCustomNameVisible(true);
        cat.setInvulnerable(true);
        cat.setPersistent();
        cat.setCollarColor(DyeColor.ORANGE);

        // Pelagem alaranjada/dourada (Red Tabby) estilo mini leão de Shamash — diferente de Cummins (cinza Jellie)
        CatVariant variant = Registries.CAT_VARIANT.get(CatVariant.RED);
        if (variant == null) {
            variant = Registries.CAT_VARIANT.get(new Identifier("minecraft", "red"));
        }
        if (variant != null) {
            cat.setVariant(variant);
        }

        if (!cat.getCommandTags().contains(JAVIDIZINEO_TAG)) {
            cat.addCommandTag(JAVIDIZINEO_TAG);
        }

        if (mod.body() != null) {
            mod.body().disableEntityCollision(cat, "javidizineo_noclip", Formatting.GOLD);
            mod.body().removeUnwantedPetGoals(cat);
        }

        // Leve aumento de escala (1.08x) para dar porte de mini leãozinho robusto
        if (!scaleApplied && server != null) {
            try {
                server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(),
                        "scale set pehkui:base 1.08 " + cat.getUuidAsString()
                );
                scaleApplied = true;
            } catch (Exception ignored) {}
        }
    }
}
