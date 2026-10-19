package com.canalhas.cummins;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.SitGoal;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Gerencia a presença física da cachorrinha "Vitória" (Fox Paulistinha / Terrier Brasileiro tricolor:
 * preto, branco e marrom, porte médio para pequeno).
 * - Lore: Pet amado do ArzanLion (Lid) e querida por todos os jogadores do CanalhasSMP.
 * - Comportamento de Aparição: Só aparece no mundo quando alguém a chama pelo chat, usa /cummins vitoria,
 *   ou quando o jogador ArzanLion entra/está no servidor.
 * - 100% cosmética, imortal e pacífica, acompanhando o jogador com alegria.
 */
public class VitoriaBody {
    public static final String VITORIA_TAG = "vitoria_dog_body";
    public static final String OWNER_NAME = "ArzanLion";
    private static final String TRICOLOR_VARIANT = "moremobvariants:spotted";

    private final CumminsMod mod;
    private MinecraftServer server;
    private UUID dogUuid;
    private String lastFocusPlayerName = OWNER_NAME;
    private boolean activeInSession = false;
    private boolean scaleApplied = false;
    private int tickCounter = 0;
    private long lastWanderAt = 0;
    private long lastBarkAt = 0;
    private int zoomiesTicksRemaining = 0;
    private long lastGiftAt = System.currentTimeMillis();
    private boolean lastFocusSneaking = false;
    private final List<Long> sneakHistory = new ArrayList<>();
    private long lastTbagAt = 0;
    private long petPauseUntil = 0;
    private long lastStalkerAlertAt = 0;
    private boolean manuallyDismissed = false;
    private final Random random = new Random();

    public VitoriaBody(CumminsMod mod) {
        this.mod = mod;
    }

    public void start(MinecraftServer server) {
        this.server = server;
        this.activeInSession = false;
        this.scaleApplied = false;
        this.lastStalkerAlertAt = 0;
        String savedUuid = mod.context().vitoriaUuid();
        if (savedUuid != null && !savedUuid.isBlank()) {
            try {
                this.dogUuid = UUID.fromString(savedUuid);
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
        // Prioriza o dono ArzanLion se estiver online
        ServerPlayerEntity arzan = server.getPlayerManager().getPlayer(OWNER_NAME);
        if (arzan != null && arzan.isAlive()) {
            lastFocusPlayerName = arzan.getGameProfile().getName();
            return arzan;
        }
        List<ServerPlayerEntity> list = server.getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    public WolfEntity getDog() {
        if (server == null) return null;
        WolfEntity primary = null;
        if (dogUuid != null) {
            for (ServerWorld world : server.getWorlds()) {
                Entity e = world.getEntity(dogUuid);
                if (e instanceof WolfEntity dog && dog.isAlive()) {
                    primary = dog;
                    break;
                }
            }
        }
        List<WolfEntity> duplicates = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            for (Entity e : world.iterateEntities()) {
                if (e instanceof WolfEntity dog && dog.isAlive() && dog.getCommandTags().contains(VITORIA_TAG)) {
                    if (primary == null) {
                        primary = dog;
                        this.dogUuid = dog.getUuid();
                        mod.context().setVitoriaUuid(dogUuid.toString());
                        mod.context().save();
                    } else if (!dog.getUuid().equals(primary.getUuid())) {
                        duplicates.add(dog);
                    }
                }
            }
        }
        for (WolfEntity dup : duplicates) {
            if (dup.hasVehicle()) dup.stopRiding();
            dup.discard();
        }
        return primary;
    }

    /**
     * Chamado quando ArzanLion entra no servidor, quando alguém chama a Vitória no chat,
     * ou quando acionam /cummins vitoria.
     */
    public WolfEntity summonOrTeleportTo(ServerPlayerEntity player) {
        if (server == null || player == null) return null;
        this.activeInSession = true;
        this.manuallyDismissed = false;
        this.lastFocusPlayerName = player.getGameProfile().getName();

        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(VITORIA_TAG);
        }

        WolfEntity dog = getDog();
        if (dog == null) {
            dog = spawnNear(player);
        } else {
            teleportToPlayer(dog, player);
            applyAttributes(dog, player);
        }
        return dog;
    }

    public void dismiss() {
        this.manuallyDismissed = true;
        this.activeInSession = false;
        despawnIfPresent();
    }

    public void despawnIfPresent() {
        this.activeInSession = false;
        this.scaleApplied = false;
        WolfEntity dog = getDog();
        if (dog != null) {
            if (dog.hasVehicle()) dog.stopRiding();
            if (dog.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.POOF, dog.getX(), dog.getY() + 0.4, dog.getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            }
            dog.discard();
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

        // Se ArzanLion estiver online, garante que a Vitória esteja ativa automaticamente
        ServerPlayerEntity arzan = server.getPlayerManager().getPlayer(OWNER_NAME);
        if (arzan != null && !activeInSession && !manuallyDismissed) {
            activeInSession = true;
            lastFocusPlayerName = arzan.getGameProfile().getName();
        }

        // Se alguém estiver segurando a Vitória no colo (CarryOn), não spawna cópia e remove duplicata solta
        if (mod.body() != null && mod.body().isCarriedByAnyPlayer(VITORIA_TAG)) {
            if (tickCounter % 10 == 0) {
                WolfEntity stray = getDog();
                if (stray != null) {
                    if (stray.hasVehicle()) stray.stopRiding();
                    stray.discard();
                }
            }
            return;
        }

        // Se não foi chamada e o ArzanLion não está online, garante que ela fique recolhida
        if (!activeInSession) {
            if (tickCounter % 40 == 0) {
                WolfEntity stray = getDog();
                if (stray != null) {
                    if (stray.hasVehicle()) stray.stopRiding();
                    stray.discard();
                }
            }
            return;
        }

        WolfEntity dog = getDog();
        if (dog != null) {
            if (dog.isSitting()) dog.setSitting(false);
            if (dog.isInSittingPose()) dog.setInSittingPose(false);

            // REGRA ABSOLUTA: A Vitória NUNCA ataca jogadores ou criaturas pacíficas/aliadas, APENAS monstros (Monster)!
            net.minecraft.entity.LivingEntity currentTarget = dog.getTarget();
            if (currentTarget != null && (!(currentTarget instanceof net.minecraft.entity.mob.Monster) || currentTarget instanceof net.minecraft.entity.player.PlayerEntity)) {
                dog.setTarget(null);
                dog.setAttacking(false);
                dog.setAngryAt(null);
                dog.setAngerTime(0);
                dog.setAttacker(null);
            }
            if (dog.getAttacker() != null && !(dog.getAttacker() instanceof net.minecraft.entity.mob.Monster)) {
                dog.setAttacker(null);
            }
            if (dog.getAngryAt() != null && server.getPlayerManager().getPlayer(dog.getAngryAt()) != null) {
                dog.setAngryAt(null);
                dog.setAngerTime(0);
            }

            if (zoomiesTicksRemaining > 0) {
                runZoomiesTick(dog);
            }
        }

        if (tickCounter % 5 != 0) return;

        ServerPlayerEntity focus = getFocusPlayer();
        if (focus == null) return;

        // Detecção de Shift spam (T-bag de carinho)
        boolean isSneaking = focus.isSneaking();
        if (isSneaking && !lastFocusSneaking) {
            long nowSneak = System.currentTimeMillis();
            if (dog != null && dog.squaredDistanceTo(focus) <= 6.0 * 6.0) {
                sneakHistory.add(nowSneak);
                sneakHistory.removeIf(t -> nowSneak - t > 2000L);
                if (sneakHistory.size() >= 3 && nowSneak - lastTbagAt > 20_000L) {
                    lastTbagAt = nowSneak;
                    sneakHistory.clear();
                    onTbagDetected(dog, focus);
                }
            }
        }
        lastFocusSneaking = isSneaking;

        if (dog == null) {
            dog = spawnNear(focus);
            if (dog == null) return;
        }

        if (tickCounter % 40 == 0) {
            applyAttributes(dog, focus);
        }

        if (dog.getY() < -55) {
            teleportToPlayer(dog, focus);
            return;
        }

        boolean differentWorld = dog.getWorld() != focus.getWorld();
        double distSq = differentWorld ? Double.MAX_VALUE : dog.squaredDistanceTo(focus);
        double maxDist = mod.config().body.teleportDistanceBlocks;

        // Se estiver dentro de um barco/carrinho, não anda sozinha; mas se o player se afastar (>14 blocos),
        // desmonta antes do chunk do barco descarregar (evita duplicar ao recarregar o barco).
        if (dog.hasVehicle()) {
            if (differentWorld || distSq > 14.0 * 14.0) {
                dog.stopRiding();
                teleportToPlayer(dog, focus);
            }
            return;
        }

        long now = System.currentTimeMillis();

        if (differentWorld || distSq > maxDist * maxDist) {
            dog.setTarget(null);
            teleportToPlayer(dog, focus);
            return;
        }

        // Verifica se há um monstro atacando o jogador ou próximo para a Vitória defender
        net.minecraft.entity.LivingEntity target = dog.getTarget();
        if (target == null || !target.isAlive() || !(target instanceof net.minecraft.entity.mob.Monster)) {
            net.minecraft.entity.LivingEntity monsterToAttack = findNearbyMonsterToAttack(dog, focus);
            if (monsterToAttack != null) {
                dog.setTarget(monsterToAttack);
                target = monsterToAttack;
            }
        }

        // Se estiver lutando contra um monstro perto do jogador, deixa o combate fluir
        if (target instanceof net.minecraft.entity.mob.Monster && target.isAlive() && dog.squaredDistanceTo(target) <= 16.0 * 16.0) {
            return;
        }

        checkStalkerWarning(dog, focus);

        if (distSq > 8.0 * 8.0) {
            petPauseUntil = 0; // se o jogador se afastar mais de 8 blocos, retoma o acompanhamento normal
            dog.getLookControl().lookAt(focus, 30.0f, 30.0f);
            if (dog.getNavigation().isIdle() || distSq > 12.0 * 12.0) {
                wanderAroundPlayer(dog, focus, 0.55D);
            }
        } else {
            boolean inPetAffection = now < petPauseUntil;
            if (inPetAffection) {
                // Em estado de carinho: senta calminha, para 100% de andar e olha com afeto para o jogador!
                dog.setSitting(true);
                dog.setInSittingPose(true);
                dog.getNavigation().stop();
                dog.setVelocity(Vec3d.ZERO);
                dog.getLookControl().lookAt(focus, 60.0f, 60.0f);
                if (tickCounter % 20 == 0 && dog.getWorld() instanceof ServerWorld sw) {
                    sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.5, dog.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
                }
            } else {
                if (dog.isSitting()) {
                    dog.setSitting(false);
                    dog.setInSittingPose(false);
                }
                if (distSq < 1.0 * 1.0 && mod.body() != null) {
                    mod.body().stepAsideFromPlayer(dog, focus);
                } else if (distSq > 3.2 * 3.2 && dog.getNavigation().isIdle() && (now - lastWanderAt > 8000L)) {
                    lastWanderAt = now;
                    wanderAroundPlayer(dog, focus, 0.38D);
                }
            }
            dog.getLookControl().lookAt(focus, 50.0f, 50.0f);

            // Presentinho da Vitória ocasionalmente
            if (now - lastGiftAt > 650_000L && random.nextDouble() < 0.04) {
                deliverGift(dog, focus);
            }

            // Latido alegre ou respiração fofa ocasional
            if (now - lastBarkAt > 22_000L && random.nextDouble() < 0.18) {
                lastBarkAt = now;
                dog.playSound(random.nextBoolean() ? SoundEvents.ENTITY_WOLF_AMBIENT : SoundEvents.ENTITY_WOLF_PANT, 0.75f, 1.2f);
            }
        }
    }

    private net.minecraft.entity.LivingEntity findNearbyMonsterToAttack(WolfEntity dog, ServerPlayerEntity focus) {
        net.minecraft.entity.LivingEntity att = focus.getAttacker();
        if (att instanceof net.minecraft.entity.mob.Monster && att.isAlive() && !(att instanceof net.minecraft.entity.mob.CreeperEntity) && dog.squaredDistanceTo(att) <= 16.0 * 16.0) {
            return att;
        }
        net.minecraft.entity.LivingEntity atk = focus.getAttacking();
        if (atk instanceof net.minecraft.entity.mob.Monster && atk.isAlive() && !(atk instanceof net.minecraft.entity.mob.CreeperEntity) && dog.squaredDistanceTo(atk) <= 16.0 * 16.0) {
            return atk;
        }
        if (dog.getWorld() instanceof ServerWorld sw) {
            List<net.minecraft.entity.mob.HostileEntity> hostiles = sw.getEntitiesByClass(
                    net.minecraft.entity.mob.HostileEntity.class,
                    focus.getBoundingBox().expand(10.0, 5.0, 10.0),
                    e -> e != null && e.isAlive() && !(e instanceof net.minecraft.entity.mob.CreeperEntity)
            );
            for (net.minecraft.entity.mob.HostileEntity h : hostiles) {
                if (h.getTarget() instanceof net.minecraft.entity.player.PlayerEntity) {
                    return h;
                }
            }
        }
        return null;
    }

    public void triggerZoomies() {
        WolfEntity dog = getDog();
        if (dog == null || !dog.isAlive()) return;
        zoomiesTicksRemaining = 90;

        var speedAttr = dog.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.70D);
        }

        dog.playSound(SoundEvents.ENTITY_WOLF_AMBIENT, 1.1f, 1.4f);
        if (dog.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.5, dog.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
            sw.spawnParticles(ParticleTypes.POOF, dog.getX(), dog.getY() + 0.2, dog.getZ(), 8, 0.3, 0.2, 0.3, 0.08);
        }
        pickZoomiesTarget(dog);
    }

    private void runZoomiesTick(WolfEntity dog) {
        zoomiesTicksRemaining--;
        if (dog.getWorld() instanceof ServerWorld sw && tickCounter % 2 == 0) {
            sw.spawnParticles(ParticleTypes.POOF, dog.getX(), dog.getY() + 0.1, dog.getZ(), 2, 0.15, 0.1, 0.15, 0.02);
        }

        if (zoomiesTicksRemaining % 15 == 0) {
            if (dog.isOnGround()) {
                dog.setVelocity(dog.getVelocity().x * 1.25, 0.40, dog.getVelocity().z * 1.25);
                dog.velocityDirty = true;
            }
            pickZoomiesTarget(dog);
            dog.playSound(SoundEvents.ENTITY_WOLF_PANT, 0.8f, 1.4f);
        }

        if (zoomiesTicksRemaining <= 0) {
            var speedAttr = dog.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speedAttr != null) {
                speedAttr.setBaseValue(0.35D);
            }
            dog.playSound(SoundEvents.ENTITY_WOLF_WHINE, 0.8f, 1.3f);
            if (dog.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.6, dog.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    private void pickZoomiesTarget(WolfEntity dog) {
        ServerPlayerEntity focus = getFocusPlayer();
        Vec3d origin = focus != null ? focus.getPos() : dog.getPos();
        double angle = random.nextDouble() * 2 * Math.PI;
        double radius = 3.5 + random.nextDouble() * 5.5;
        int tx = (int) Math.floor(origin.getX() + Math.cos(angle) * radius);
        int tz = (int) Math.floor(origin.getZ() + Math.sin(angle) * radius);
        if (dog.getWorld() instanceof ServerWorld sw) {
            BlockPos target = findSafeGroundNear(new BlockPos(tx, (int) origin.getY(), tz), sw);
            dog.getNavigation().startMovingTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.75D);
        }
    }

    public void onTbagDetected(WolfEntity dog, ServerPlayerEntity player) {
        dog.setSitting(false);
        dog.setInSittingPose(false);
        dog.getLookControl().lookAt(player, 60.0f, 60.0f);
        dog.setVelocity(dog.getVelocity().x, 0.38, dog.getVelocity().z);
        dog.velocityDirty = true;

        if (dog.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.7, dog.getZ(), 8, 0.3, 0.3, 0.3, 0.08);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, dog.getX(), dog.getY() + 0.6, dog.getZ(), 6, 0.3, 0.3, 0.3, 0.08);
            dog.playSound(SoundEvents.ENTITY_WOLF_AMBIENT, 1.0f, 1.3f);
        }

        if (mod.config().vitoriaAiEnabled) {
            mod.brain().broadcastAsVitoria("au au! pula pula de alegria com você!! 🐾", player.getGameProfile().getName());
        }
    }

    public void deliverGift(WolfEntity dog, ServerPlayerEntity player) {
        if (dog == null || player == null || !(dog.getWorld() instanceof ServerWorld sw)) return;
        lastGiftAt = System.currentTimeMillis();

        ItemStack stack = new ItemStack(Items.BONE, 1);
        stack.setCustomName(Text.literal("Ossinho da Sorte da Vitória").formatted(Formatting.LIGHT_PURPLE));
        NbtCompound display = stack.getOrCreateSubNbt("display");
        NbtList loreList = new NbtList();
        loreList.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("Enterrado com muito amor pela Fox Paulistinha mais amada do servidor!").formatted(Formatting.GRAY, Formatting.ITALIC))));
        display.put("Lore", loreList);

        ItemEntity itemEntity = new ItemEntity(sw, dog.getX(), dog.getY() + 0.4, dog.getZ(), stack);
        Vec3d dir = player.getPos().subtract(dog.getPos()).normalize().multiply(0.25).add(0, 0.15, 0);
        itemEntity.setVelocity(dir);
        itemEntity.setPickupDelay(10);
        sw.spawnEntity(itemEntity);

        sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.7, dog.getZ(), 6, 0.25, 0.25, 0.25, 0.05);
        dog.playSound(SoundEvents.ENTITY_WOLF_AMBIENT, 1.0f, 1.25f);

        if (mod.config().vitoriaAiEnabled) {
            mod.brain().broadcastAsVitoria("au au! trouxe meu ossinho favorito pra você cuidar pra mim! 🦴🐾", player.getGameProfile().getName());
        }
    }

    private void wanderAroundPlayer(WolfEntity dog, ServerPlayerEntity focus, double speed) {
        double dx = dog.getX() - focus.getX();
        double dz = dog.getZ() - focus.getZ();
        double currentAngle = Math.atan2(dz, dx);
        double deltaAngle = (random.nextDouble() - 0.5) * (Math.PI * 0.70);
        double angle = currentAngle + deltaAngle;
        double radius = 1.4 + random.nextDouble() * 0.8; // 1.4 a 2.2 blocos (pertinho do tutor)
        int targetX = (int) Math.floor(focus.getX() + Math.cos(angle) * radius);
        int targetZ = (int) Math.floor(focus.getZ() + Math.sin(angle) * radius);
        BlockPos targetPos = new BlockPos(targetX, focus.getBlockY(), targetZ);

        if (dog.getWorld() instanceof ServerWorld sw) {
            BlockPos safePos = findSafeGroundNear(targetPos, sw);
            dog.getNavigation().startMovingTo(safePos.getX() + 0.5, safePos.getY(), safePos.getZ() + 0.5, speed);
        }
    }

    public WolfEntity spawnNear(ServerPlayerEntity player) {
        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(VITORIA_TAG);
        }
        WolfEntity existing = getDog();
        if (existing != null) {
            if (existing.hasVehicle()) existing.stopRiding();
            existing.discard();
        }
        ServerWorld world = player.getServerWorld();
        BlockPos pos = findSafeGroundAroundPlayer(player.getBlockPos(), world);
        WolfEntity dog = EntityType.WOLF.create(world);
        if (dog == null) return null;

        this.scaleApplied = false;
        dog.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYaw(), 0);
        world.spawnEntity(dog);
        applyAttributes(dog, player);

        this.dogUuid = dog.getUuid();
        mod.context().setVitoriaUuid(dogUuid.toString());
        mod.context().save();

        world.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.7, dog.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, dog.getX(), dog.getY() + 0.5, dog.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        dog.playSound(SoundEvents.ENTITY_WOLF_AMBIENT, 1.0f, 1.25f);

        CumminsMod.LOG.info("[Cummins] Cachorrinha Vitória (Fox Paulistinha) invocada perto de {}", player.getGameProfile().getName());
        return dog;
    }

    public void teleportToPlayer(WolfEntity dog, ServerPlayerEntity player) {
        if (mod.body() != null) {
            mod.body().releaseFromCarryOn(VITORIA_TAG);
        }
        if (dog.hasVehicle()) {
            dog.stopRiding();
        }
        ServerWorld targetWorld = player.getServerWorld();
        BlockPos safePos = findSafeGroundAroundPlayer(player.getBlockPos(), targetWorld);
        double x = safePos.getX() + 0.5;
        double y = safePos.getY();
        double z = safePos.getZ() + 0.5;

        if (dog.getWorld() != targetWorld) {
            dog.teleport(targetWorld, x, y, z, java.util.Set.of(), player.getYaw(), 0);
        } else {
            dog.refreshPositionAndAngles(x, y, z, player.getYaw(), 0);
        }
        dog.setVelocity(Vec3d.ZERO);
        dog.fallDistance = 0;
        dog.setSitting(false);
        dog.setInSittingPose(false);
        dog.setAngerTime(0);

        targetWorld.spawnParticles(ParticleTypes.HEART, x, y + 0.7, z, 5, 0.25, 0.25, 0.25, 0.05);
        dog.playSound(SoundEvents.ENTITY_WOLF_AMBIENT, 1.0f, 1.25f);
        syncTricolorVariant(dog);
    }

    public void emoteSpeaking() {
        WolfEntity dog = getDog();
        if (dog == null || !dog.isAlive()) return;
        dog.setSitting(false);
        dog.setInSittingPose(false);
        if (dog.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.6, dog.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
            dog.playSound(SoundEvents.ENTITY_WOLF_AMBIENT, 0.9f, 1.25f);
        }
    }

    public void onPet(WolfEntity dog, ServerPlayerEntity player) {
        this.petPauseUntil = System.currentTimeMillis() + 12_000L;
        dog.getNavigation().stop();
        dog.setSitting(false);
        dog.setInSittingPose(false);
        dog.getLookControl().lookAt(player, 60.0f, 60.0f);
        setFocusPlayer(player.getGameProfile().getName());
        applyAttributes(dog, player);

        if (dog.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.HEART, dog.getX(), dog.getY() + 0.6, dog.getZ(), 8, 0.25, 0.25, 0.25, 0.05);
            dog.playSound(SoundEvents.ENTITY_WOLF_WHINE, 0.9f, 1.35f);
            dog.playSound(SoundEvents.ENTITY_WOLF_PANT, 0.9f, 1.2f);
        }

        mod.brain().onVitoriaPet(player);
    }

    private void applyAttributes(WolfEntity dog, ServerPlayerEntity owner) {
        dog.setCustomName(Text.literal("Vitória").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        dog.setCustomNameVisible(true);
        dog.setInvulnerable(true);
        dog.setPersistent();
        dog.setTamed(true);
        // Usa UUID fictício fixo para que TameableEntity.getScoreboardTeam() retorne "vitoria_noclip" (CollisionRule.NEVER)
        // em vez de retornar a equipe do jogador online!
        dog.setOwnerUuid(CumminsBody.DUMMY_OWNER_UUID);
        dog.setSitting(false);
        dog.setInSittingPose(false);
        dog.setAngerTime(0);
        dog.setCollarColor(DyeColor.RED);
        if (!dog.getCommandTags().contains(VITORIA_TAG)) {
            dog.addCommandTag(VITORIA_TAG);
        }

        // Desativa colisão física (empurrão) no servidor e nos clientes via Scoreboard Team CollisionRule.NEVER
        if (mod.body() != null) {
            mod.body().disableEntityCollision(dog, "vitoria_noclip", Formatting.LIGHT_PURPLE);
        }

        // Velocidade ágil de Terrier Brasileiro / Fox Paulistinha
        var speedAttr = dog.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speedAttr != null && speedAttr.getBaseValue() < 0.35D) {
            speedAttr.setBaseValue(0.35D);
        }

        // Aplica pelagem Tricolor (Preto, Branco e Marrom) via MoreMobVariants
        syncTricolorVariant(dog);

        // Porte médio para pequeno (0.82x) via Pehkui
        if (!scaleApplied && server != null) {
            try {
                server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent(),
                        "scale set pehkui:base 0.82 " + dog.getUuidAsString()
                );
                scaleApplied = true;
            } catch (Exception ignored) {}
        }

        // Remove metas de sentar e FollowOwnerGoal (para não colar em cima do jogador parado)
        removeSitAndCombatGoals(dog);
    }

    public void syncTricolorVariant(WolfEntity dog) {
        try {
            NbtCompound nbt = new NbtCompound();
            dog.writeNbt(nbt);
            nbt.putString("VariantID", TRICOLOR_VARIANT);
            dog.readNbt(nbt);
        } catch (Exception ignored) {}
    }

    public void syncAppearanceForJoin() {
        WolfEntity dog = getDog();
        if (dog != null && dog.isAlive()) {
            syncTricolorVariant(dog);
        }
    }

    public String getSituationalContext() {
        if (!activeInSession) return null;
        WolfEntity dog = getDog();
        ServerPlayerEntity focus = getFocusPlayer();
        if (dog == null || focus == null) return null;
        String dim = dog.getWorld().getRegistryKey().getValue().toString();
        double dist = Math.sqrt(dog.squaredDistanceTo(focus));
        return String.format(Locale.ROOT,
                "CONTEXTO FÍSICO DA VITÓRIA: Você (a cachorrinha Vitória, Fox Paulistinha tricolor do ArzanLion) está fisicamente em [X=%d, Y=%d, Z=%d] na dimensão %s, acompanhando %s a %.1f blocos de distância.",
                dog.getBlockX(), dog.getBlockY(), dog.getBlockZ(), dim, focus.getGameProfile().getName(), dist);
    }

    private void removeSitAndCombatGoals(WolfEntity dog) {
        try {
            Class<?> mobClass = dog.getClass();
            while (mobClass != null && mobClass != Object.class) {
                for (java.lang.reflect.Field field : mobClass.getDeclaredFields()) {
                    if (net.minecraft.entity.ai.goal.GoalSelector.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        net.minecraft.entity.ai.goal.GoalSelector gs = (net.minecraft.entity.ai.goal.GoalSelector) field.get(dog);
                        if (gs != null) {
                            gs.clear(goal -> goal instanceof SitGoal || goal instanceof net.minecraft.entity.ai.goal.FollowOwnerGoal);
                        }
                    }
                }
                mobClass = mobClass.getSuperclass();
            }
        } catch (Exception ignored) {}
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
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = center.add(0, dy, 0);
            if (isWalkable(world, pos)) return pos;
        }
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

    private void checkStalkerWarning(WolfEntity dog, ServerPlayerEntity player) {
        if (dog == null || player == null || !dog.isAlive() || !player.isAlive()) return;
        long now = System.currentTimeMillis();
        if (now - lastStalkerAlertAt < 15_000L) return; // Cooldown de 15 segundos

        if (!(player.getWorld() instanceof ServerWorld world)) return;
        Box searchBox = player.getBoundingBox().expand(14.0, 6.0, 14.0);
        List<net.minecraft.entity.mob.HostileEntity> monsters = world.getEntitiesByClass(
                net.minecraft.entity.mob.HostileEntity.class,
                searchBox,
                m -> m.isAlive() && !m.isSpectator()
        );

        if (monsters.isEmpty()) return;

        Vec3d look = player.getRotationVec(1.0F).normalize();

        for (net.minecraft.entity.mob.HostileEntity m : monsters) {
            int light = world.getLightLevel(LightType.BLOCK, m.getBlockPos());
            if (light <= 4) {
                Vec3d toMob = m.getPos().subtract(player.getPos()).normalize();
                double dot = look.dotProduct(toMob);
                if (dot < 0.1) { // ponto cego (atrás ou flanco não visível)
                    lastStalkerAlertAt = now;
                    dog.getLookControl().lookAt(m, 40.0F, 40.0F);
                    world.playSound(null, dog.getX(), dog.getY(), dog.getZ(), SoundEvents.ENTITY_WOLF_GROWL, net.minecraft.sound.SoundCategory.NEUTRAL, 1.2F, 0.9F);
                    world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, dog.getX(), dog.getY() + 0.6, dog.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
                    player.sendMessage(Text.literal("🐾 §cVitória rosnou! §eAlgo perigoso espreita na escuridão atrás de você..."), true);
                    break;
                }
            }
        }
    }
}

