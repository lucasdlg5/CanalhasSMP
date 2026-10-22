package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.network.packet.s2c.play.SetCameraEntityS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canalhas TV / Modo Espectador Contínuo 24/7:
 * - Suporta múltiplos modos de visão de câmera:
 *   1. FIRST_PERSON: 1ª Pessoa (Olhos do Jogador)
 *   2. DRONE: 3ª Pessoa Traseira / Drone de Perseguição
 *   3. FRONT: Câmera Frontal (Selfie / Entrevista)
 *   4. ORBIT: Órbita 360° Cinemática ao redor do jogador
 *   5. TOP_DOWN: Visão Aérea Tática / Satélite
 *   6. AUTO: Diretor Automático (mistura dinâmica de câmeras)
 */
public class CumminsTvManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final CumminsMod mod;
    private final Path configFile;
    private TvConfig config = new TvConfig();

    // Sessões ativas de TV
    private final Map<UUID, SpectatorSession> sessions = new ConcurrentHashMap<>();

    // Rastreamento de movimento dos jogadores para detecção de AFK
    private final Map<UUID, PlayerMovementTracker> movementTrackers = new ConcurrentHashMap<>();

    // Rastreamento dos jogadores atualmente spectados para o Easter Egg da música monitoring.mp3 (volume 1%)
    private final Set<UUID> spectatedPlayerUuids = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> spectatedTicks = new ConcurrentHashMap<>();

    public enum TvCameraMode {
        FIRST_PERSON("1ª Pessoa (Olhos)", "olhos", "🎥"),
        DRONE("Drone Traseiro", "drone", "🚁"),
        FRONT("Câmera Frontal", "frente", "🎤"),
        ORBIT("Órbita 360°", "orbita", "🔄"),
        TOP_DOWN("Visão Aérea", "topo", "🛰️"),
        AUTO("Diretor Auto", "auto", "🎬");

        public final String displayName;
        public final String commandAlias;
        public final String icon;

        TvCameraMode(String displayName, String commandAlias, String icon) {
            this.displayName = displayName;
            this.commandAlias = commandAlias;
            this.icon = icon;
        }

        public static TvCameraMode fromString(String input) {
            if (input == null) return FIRST_PERSON;
            String s = input.trim().toLowerCase(Locale.ROOT);
            for (TvCameraMode m : values()) {
                if (m.name().equalsIgnoreCase(s) || m.commandAlias.equalsIgnoreCase(s)) {
                    return m;
                }
            }
            if (s.contains("olho") || s.contains("1") || s.contains("fp") || s.contains("first")) return FIRST_PERSON;
            if (s.contains("drone") || s.contains("costa") || s.contains("2") || s.contains("tp") || s.contains("third")) return DRONE;
            if (s.contains("frente") || s.contains("selfie") || s.contains("3") || s.contains("front")) return FRONT;
            if (s.contains("orbit") || s.contains("360") || s.contains("4")) return ORBIT;
            if (s.contains("topo") || s.contains("satelite") || s.contains("cima") || s.contains("5") || s.contains("top")) return TOP_DOWN;
            if (s.contains("auto") || s.contains("diretor") || s.contains("6")) return AUTO;
            return FIRST_PERSON;
        }
    }

    public static class TvConfig {
        public String botNick = "CanalhasTV";
        public int focusDurationSeconds = 20;
        public boolean autoStartOnJoin = true;
        public boolean skipAfk = true;
        public int afkThresholdSeconds = 45;
        public boolean showActionBar = true;
        public String defaultMode = "FIRST_PERSON";
    }

    public static class SpectatorSession {
        public final UUID spectatorUuid;
        public final String spectatorName;
        public final GameMode originalGameMode;
        public final RegistryKey<World> originalDimension;
        public final Vec3d originalPos;
        public final float originalYaw;
        public final float originalPitch;

        public Entity currentTarget = null;
        public int ticksOnTarget = 0;
        public boolean isLocked = false;
        public boolean lastSneaking = false;
        public TvCameraMode cameraMode = TvCameraMode.FIRST_PERSON;
        public float orbitAngleDeg = 0f;
        public int dimensionTransitionCooldownTicks = 0;
        public int ticksSinceLastAttachPacket = 0;

        public SpectatorSession(ServerPlayerEntity p, TvCameraMode initialMode) {
            this.spectatorUuid = p.getUuid();
            this.spectatorName = p.getGameProfile().getName();
            this.originalGameMode = p.interactionManager.getGameMode();
            this.originalDimension = p.getServerWorld().getRegistryKey();
            this.originalPos = p.getPos();
            this.originalYaw = p.getYaw();
            this.originalPitch = p.getPitch();
            this.cameraMode = initialMode != null ? initialMode : TvCameraMode.FIRST_PERSON;
        }
    }

    private static class PlayerMovementTracker {
        public Vec3d lastPos = Vec3d.ZERO;
        public long lastMovedEpochMs = System.currentTimeMillis();
    }

    public CumminsTvManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.configFile = configDir.resolve("tv_config.json");
        load();
    }

    public synchronized void load() {
        if (!Files.exists(configFile)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            TvConfig c = GSON.fromJson(r, TvConfig.class);
            if (c != null) this.config = c;
        } catch (Exception e) {
            CumminsMod.LOG.warn("[CumminsTV] Falha ao ler tv_config.json: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer w = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[CumminsTV] Falha ao salvar tv_config.json: {}", e.getMessage());
        }
    }

    public TvConfig getConfig() {
        return config;
    }

    public boolean isTvSpectator(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public TvCameraMode getCameraMode(ServerPlayerEntity spectator) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        return session != null ? session.cameraMode : TvCameraMode.fromString(config.defaultMode);
    }

    public boolean setCameraMode(ServerPlayerEntity spectator, TvCameraMode mode) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        if (session == null || mode == null) return false;
        session.cameraMode = mode;
        if (session.currentTarget != null && !session.currentTarget.isRemoved()) {
            if (mode == TvCameraMode.FIRST_PERSON) {
                forceCameraAttach(spectator, session.currentTarget);
            } else {
                forceCameraDetach(spectator);
            }
        }
        spectator.sendMessage(Text.literal("§6📺 [Canalhas TV]§r Modo de visão alterado para: §e" + mode.icon + " " + mode.displayName), false);
        return true;
    }

    public TvCameraMode cycleNextMode(ServerPlayerEntity spectator) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        if (session == null) return TvCameraMode.FIRST_PERSON;
        TvCameraMode[] all = TvCameraMode.values();
        int nextIdx = (session.cameraMode.ordinal() + 1) % all.length;
        session.cameraMode = all[nextIdx];
        if (session.currentTarget != null && !session.currentTarget.isRemoved()) {
            if (session.cameraMode == TvCameraMode.FIRST_PERSON) {
                forceCameraAttach(spectator, session.currentTarget);
            } else {
                forceCameraDetach(spectator);
            }
        }
        spectator.sendMessage(Text.literal("§6📺 [Canalhas TV]§r Modo de visão: §e" + session.cameraMode.icon + " " + session.cameraMode.displayName), false);
        return session.cameraMode;
    }

    /**
     * Inicia o modo TV para o jogador especificado.
     */
    public boolean startTv(ServerPlayerEntity player) {
        if (player == null || player.isRemoved()) return false;

        UUID uuid = player.getUuid();
        TvCameraMode initialMode = TvCameraMode.fromString(config.defaultMode);
        SpectatorSession session = new SpectatorSession(player, initialMode);
        sessions.put(uuid, session);

        // Altera para modo espectador
        player.changeGameMode(GameMode.SPECTATOR);

        player.sendMessage(Text.literal("§6📺 [Canalhas TV]§a Modo transmissão ativado! Carrossel a cada " + config.focusDurationSeconds + "s."), false);
        player.sendMessage(Text.literal("§7Dica: Pressione §eShift (Agachar)§7 para pular de jogador imediatamente."), false);
        player.sendMessage(Text.literal("§7Modos de câmera: §e/tv modo <olhos|drone|frente|orbita|topo|auto>"), false);

        // Foca no primeiro jogador imediatamente
        cycleNext(player);
        return true;
    }

    /**
     * Encerra o modo TV e devolve o jogador à posição e modo de jogo anteriores.
     */
    public boolean stopTv(ServerPlayerEntity player) {
        if (player == null) return false;
        SpectatorSession session = sessions.remove(player.getUuid());
        if (session == null) return false;

        // Desanexa a câmera
        player.setCameraEntity(player);

        // Restaura dimensão e posição
        MinecraftServer server = player.getServer();
        if (server != null && session.originalDimension != null) {
            ServerWorld originalWorld = server.getWorld(session.originalDimension);
            if (originalWorld != null && session.originalPos != null) {
                player.teleport(originalWorld, session.originalPos.x, session.originalPos.y, session.originalPos.z,
                        session.originalYaw, session.originalPitch);
            }
        }

        // Restaura modo de jogo original
        GameMode restoreMode = session.originalGameMode != null && session.originalGameMode != GameMode.SPECTATOR
                ? session.originalGameMode : GameMode.SURVIVAL;
        player.changeGameMode(restoreMode);

        player.sendMessage(Text.literal("§6📺 [Canalhas TV]§c Transmissão encerrada. Bem-vindo de volta!"), false);
        return true;
    }

    public void cycleNext(ServerPlayerEntity spectator) {
        cycle(spectator, true);
    }

    public void cyclePrev(ServerPlayerEntity spectator) {
        cycle(spectator, false);
    }

    private void cycle(ServerPlayerEntity spectator, boolean forward) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        if (session == null) return;

        MinecraftServer server = spectator.getServer();
        if (server == null) return;

        List<Entity> candidates = buildCandidateList(server, spectator);
        if (candidates.isEmpty()) {
            spectator.sendMessage(Text.literal("§6📺 [Canalhas TV]§e Procurando operários ou mascotes na firma..."), true);
            return;
        }

        int currentIndex = -1;
        if (session.currentTarget != null) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).getUuid().equals(session.currentTarget.getUuid())) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int nextIndex;
        if (currentIndex == -1) {
            nextIndex = 0;
        } else if (forward) {
            nextIndex = (currentIndex + 1) % candidates.size();
        } else {
            nextIndex = (currentIndex - 1 + candidates.size()) % candidates.size();
        }

        Entity target = candidates.get(nextIndex);
        attachCameraTo(spectator, session, target);
    }

    public boolean lockTarget(ServerPlayerEntity spectator, ServerPlayerEntity target) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        if (session == null || target == null || target.equals(spectator)) return false;

        attachCameraTo(spectator, session, target);
        session.isLocked = true;
        spectator.sendMessage(Text.literal("§6📺 [Canalhas TV]§e Câmera travada em §f" + target.getGameProfile().getName() + "§e. Use §f/tv continuar§e para retomar o carrossel."), false);
        return true;
    }

    public boolean resumeCycle(ServerPlayerEntity spectator) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        if (session == null) return false;
        session.isLocked = false;
        session.ticksOnTarget = 0;
        spectator.sendMessage(Text.literal("§6📺 [Canalhas TV]§a Carrossel automático retomado!"), false);
        return true;
    }

    public void forceCameraAttach(ServerPlayerEntity spectator, Entity target) {
        if (spectator == null || target == null || target.isRemoved()) return;
        if (spectator.getWorld() != target.getWorld()) {
            spectator.teleport((ServerWorld) target.getWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
        }
        spectator.setCameraEntity(target);
        if (spectator.networkHandler != null) {
            spectator.networkHandler.sendPacket(new SetCameraEntityS2CPacket(target));
        }
    }

    public void forceCameraDetach(ServerPlayerEntity spectator) {
        if (spectator == null) return;
        spectator.setCameraEntity(spectator);
        if (spectator.networkHandler != null) {
            spectator.networkHandler.sendPacket(new SetCameraEntityS2CPacket(spectator));
        }
    }

    public boolean refocusCamera(ServerPlayerEntity spectator) {
        SpectatorSession session = sessions.get(spectator.getUuid());
        if (session == null || session.currentTarget == null) return false;
        attachCameraTo(spectator, session, session.currentTarget);
        spectator.sendMessage(Text.literal("§6📺 [Canalhas TV]§a Câmera re-sincronizada no jogador: §e" + session.currentTarget.getName().getString()), false);
        return true;
    }

    private void attachCameraTo(ServerPlayerEntity spectator, SpectatorSession session, Entity target) {
        if (spectator == null || target == null || target.isRemoved()) return;

        if (target.getWorld() instanceof ServerWorld targetWorld) {
            boolean diffWorld = spectator.getWorld() != targetWorld;
            double distSq = diffWorld ? Double.MAX_VALUE : spectator.squaredDistanceTo(target);
            // Só teleporta o avatar físico se estiver em outra dimensão OU a mais de 64 blocos de distância
            // para que os chunks sejam enviados antes do acoplamento
            if (diffWorld || distSq > 64.0 * 64.0) {
                spectator.teleport(targetWorld, target.getX(), target.getY() + 0.1, target.getZ(), target.getYaw(), target.getPitch());
                if (diffWorld) {
                    session.dimensionTransitionCooldownTicks = 40; // 2s de re-sincronização contínua pós-portal
                }
            }
        }

        session.currentTarget = target;
        session.ticksOnTarget = 0;
        session.orbitAngleDeg = target.getYaw();
        session.ticksSinceLastAttachPacket = 0;

        // Se estiver em 1ª pessoa, acopla nativamente aos olhos da entidade
        if (session.cameraMode == TvCameraMode.FIRST_PERSON) {
            forceCameraAttach(spectator, target);
        } else {
            forceCameraDetach(spectator);
        }
    }

    private List<Entity> buildCandidateList(MinecraftServer server, ServerPlayerEntity spectator) {
        List<Entity> playerCandidates = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == null || p.isRemoved() || p.equals(spectator)) continue;
            if (sessions.containsKey(p.getUuid())) continue;
            if (p.isSpectator()) continue;

            if (config.skipAfk) {
                PlayerMovementTracker tr = movementTrackers.get(p.getUuid());
                if (tr != null && (now - tr.lastMovedEpochMs) > (config.afkThresholdSeconds * 1000L)) {
                    continue;
                }
            }
            playerCandidates.add(p);
        }

        if (playerCandidates.isEmpty()) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p != null && !p.isRemoved() && !p.equals(spectator) && !sessions.containsKey(p.getUuid()) && !p.isSpectator()) {
                    playerCandidates.add(p);
                }
            }
        }

        if (playerCandidates.isEmpty()) {
            addPetIfAlive(playerCandidates, mod.body().getCat());
            addPetIfAlive(playerCandidates, mod.vitoria().getDog());
            addPetIfAlive(playerCandidates, mod.javidizineo().getCat());
            addPetIfAlive(playerCandidates, mod.jinqiuzineo().getCat());
            addPetIfAlive(playerCandidates, mod.pipira().getFish());
            addPetIfAlive(playerCandidates, mod.juninho().getBat());
        }

        return playerCandidates;
    }

    private void addPetIfAlive(List<Entity> list, Entity pet) {
        if (pet != null && !pet.isRemoved() && pet.isAlive()) {
            list.add(pet);
        }
    }

    public void onTick(MinecraftServer server) {
        if (server == null) return;

        if (server.getTicks() % 20 == 0) {
            updatePlayerMovement(server);
        }

        // Easter Egg: Gerencia o áudio de monitoramento (monitoring.mp3 a 1% de volume)
        updateMonitoringMusic(server);

        if (sessions.isEmpty()) return;

        Iterator<Map.Entry<UUID, SpectatorSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SpectatorSession> entry = it.next();
            SpectatorSession session = entry.getValue();
            ServerPlayerEntity spectator = server.getPlayerManager().getPlayer(session.spectatorUuid);

            if (spectator == null || spectator.isRemoved()) {
                it.remove();
                continue;
            }

            if (!spectator.isSpectator()) {
                spectator.changeGameMode(GameMode.SPECTATOR);
            }

            boolean isSneaking = spectator.isSneaking();
            if (isSneaking && !session.lastSneaking) {
                cycleNext(spectator);
                session.lastSneaking = true;
                continue;
            }
            session.lastSneaking = isSneaking;

            Entity target = session.currentTarget;

            if (target == null || target.isRemoved() || !target.isAlive()) {
                cycleNext(spectator);
                continue;
            }

            if (spectator.getWorld() != target.getWorld()) {
                attachCameraTo(spectator, session, target);
                continue;
            }

            session.ticksOnTarget++;

            int durationTicks = Math.max(5, config.focusDurationSeconds) * 20;
            if (!session.isLocked && session.ticksOnTarget >= durationTicks) {
                cycleNext(spectator);
                continue;
            }

            // Determina o modo de câmera efetivo deste tick
            TvCameraMode effectiveMode = session.cameraMode;
            if (effectiveMode == TvCameraMode.AUTO) {
                float progress = (float) session.ticksOnTarget / durationTicks;
                if (progress < 0.25f) {
                    effectiveMode = TvCameraMode.ORBIT; // Primeiros 25%: Visão 360° panorâmica
                } else if (progress < 0.75f) {
                    effectiveMode = TvCameraMode.FIRST_PERSON; // 50% intermediário: Olhos do player
                } else {
                    effectiveMode = TvCameraMode.DRONE; // Últimos 25%: Drone traseiro
                }
            }

            // Aplica a câmera do tick
            applyCameraPosition(spectator, session, target, effectiveMode);

            // Exibe a barra de ação informativa a cada 10 ticks (0.5s)
            if (config.showActionBar && server.getTicks() % 10 == 0) {
                renderActionBar(spectator, session, target, durationTicks, effectiveMode);
            }
        }
    }

    /**
     * Posiciona a câmera do espectador de acordo com o modo de visão selecionado.
     */
    private void applyCameraPosition(ServerPlayerEntity spectator, SpectatorSession session, Entity target, TvCameraMode mode) {
        if (target.getWorld() != spectator.getWorld()) {
            attachCameraTo(spectator, session, target);
            return;
        }

        session.ticksSinceLastAttachPacket++;

        if (mode == TvCameraMode.FIRST_PERSON) {
            // Modo 1ª Pessoa nativo:
            // O Minecraft sincroniza nativamente no ServerPlayerEntity.tick() o updatePositionAndAngles e chunkManager.
            // NUNCA chamar spectator.teleport() no mesmo mundo aqui, pois o teleport nativo reseta cameraEntity para 'this',
            // desanexando o espectador e travando a visão na posição anterior!

            // 1. Transição pós-troca de dimensão
            if (session.dimensionTransitionCooldownTicks > 0) {
                session.dimensionTransitionCooldownTicks--;
                if (session.dimensionTransitionCooldownTicks % 10 == 0) {
                    forceCameraAttach(spectator, target);
                }
                return;
            }

            // 2. Se a câmera desanexou (ex: renascimento do alvo ou desconexão do pacote), re-anexa:
            if (spectator.getCameraEntity() != target && !spectator.isSneaking()) {
                forceCameraAttach(spectator, target);
            }
            return;
        }

        // Modos livres / cinemáticos: desanexa da 1ª pessoa para mover o avatar espectador
        if (spectator.getCameraEntity() != null && !spectator.getCameraEntity().equals(spectator)) {
            forceCameraDetach(spectator);
        }

        double targetX = target.getX();
        double targetEyeY = target.getY() + target.getStandingEyeHeight();
        double targetCenterY = target.getY() + target.getHeight() * 0.5;
        double targetZ = target.getZ();
        float targetYaw = target.getYaw();

        double camX;
        double camY;
        double camZ;
        float lookYaw;
        float lookPitch;

        switch (mode) {
            case DRONE -> {
                // Drone Traseiro (3.8 blocos atrás, 1.4 blocos acima dos olhos)
                double rad = Math.toRadians(targetYaw);
                camX = targetX + Math.sin(rad) * 3.8;
                camY = targetEyeY + 1.4;
                camZ = targetZ - Math.cos(rad) * 3.8;
                lookYaw = targetYaw;
                lookPitch = 16.0f;
            }
            case FRONT -> {
                // Câmera Frontal (3.2 blocos à frente, na altura do rosto)
                double rad = Math.toRadians(targetYaw);
                camX = targetX - Math.sin(rad) * 3.2;
                camY = targetEyeY + 0.3;
                camZ = targetZ + Math.cos(rad) * 3.2;
                lookYaw = (targetYaw + 180.0f) % 360.0f;
                lookPitch = 6.0f;
            }
            case ORBIT -> {
                // Órbita 360° Cinemática (gira 1.2 graus por tick)
                session.orbitAngleDeg = (session.orbitAngleDeg + 1.2f) % 360.0f;
                double rad = Math.toRadians(session.orbitAngleDeg);
                double radius = 4.2;
                camX = targetX + Math.cos(rad) * radius;
                camY = targetEyeY + 0.9;
                camZ = targetZ + Math.sin(rad) * radius;

                // Aponta para o peito do jogador
                double dx = targetX - camX;
                double dy = targetCenterY - camY;
                double dz = targetZ - camZ;
                double distH = Math.sqrt(dx * dx + dz * dz);
                lookYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                lookPitch = (float) (-Math.toDegrees(Math.atan2(dy, distH)));
            }
            case TOP_DOWN -> {
                // Visão Aérea / Satélite (9.5 blocos acima olhando direto para baixo)
                camX = targetX;
                camY = targetEyeY + 9.5;
                camZ = targetZ;
                lookYaw = targetYaw;
                lookPitch = 88.0f;
            }
            default -> {
                return;
            }
        }

        // Teleporta suavemente o espectador para a posição da câmera
        spectator.teleport((ServerWorld) target.getWorld(), camX, camY, camZ, lookYaw, lookPitch);
    }

    private void renderActionBar(ServerPlayerEntity spectator, SpectatorSession session, Entity target, int durationTicks, TvCameraMode effectiveMode) {
        String name;
        String extraInfo;

        if (target instanceof ServerPlayerEntity tp) {
            name = tp.getGameProfile().getName();
            int hp = (int) Math.ceil(tp.getHealth());
            String dim = getDimensionDisplayName(tp.getWorld());
            extraInfo = "§7(§c❤ " + hp + "§7) §8[§b" + dim + "§8]";
        } else {
            name = target.getName().getString();
            extraInfo = "§8[§dMascote§8]";
        }

        int remainingSeconds = Math.max(0, (durationTicks - session.ticksOnTarget) / 20);
        String timing = session.isLocked ? "§c[TRAVADO]" : "§7Próximo: §6" + remainingSeconds + "s";
        String modeBadge = "§3[" + effectiveMode.icon + " " + effectiveMode.displayName + "]§r";

        Text bar = Text.literal("§6📺 CANALHAS TV §7| §fAo Vivo: §e" + name + " " + extraInfo + " §7• " + modeBadge + " §7• " + timing);
        spectator.sendMessage(bar, true);
    }

    private String getDimensionDisplayName(World world) {
        if (world == null) return "Overworld";
        String path = world.getRegistryKey().getValue().getPath().toLowerCase(Locale.ROOT);
        if (path.contains("nether")) return "Nether";
        if (path.contains("end")) return "The End";
        return "Overworld";
    }

    private void updatePlayerMovement(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p == null || p.isRemoved()) continue;
            PlayerMovementTracker tr = movementTrackers.computeIfAbsent(p.getUuid(), k -> new PlayerMovementTracker());
            Vec3d currentPos = p.getPos();
            if (tr.lastPos.squaredDistanceTo(currentPos) > 0.05) {
                tr.lastPos = currentPos;
                tr.lastMovedEpochMs = now;
            }
        }
    }

    public boolean isBot(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        // Jamais considere jodevil ou _iodevil como bot de transmissão
        if (trimmed.equalsIgnoreCase("jodevil") || trimmed.equalsIgnoreCase("_iodevil")) {
            return false;
        }
        if (trimmed.equalsIgnoreCase("CanalhasTV") || trimmed.equalsIgnoreCase("CanalhaTV")) {
            return true;
        }
        if (config.botNick != null && !config.botNick.isBlank()) {
            String configured = config.botNick.trim();
            if (!configured.equalsIgnoreCase("jodevil") && !configured.equalsIgnoreCase("_iodevil")) {
                return trimmed.equalsIgnoreCase(configured);
            }
        }
        return false;
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        String name = player.getGameProfile().getName();
        if (config.autoStartOnJoin && isBot(name)) {
            CumminsMod.LOG.info("[CumminsTV] Conta de transmissão '{}' conectou! Ativando modo TV automático...", name);
            player.getServer().execute(() -> startTv(player));
        } else if (name.equalsIgnoreCase("jodevil") || name.equalsIgnoreCase("_iodevil")) {
            // Se jodevil entrar e estiver em modo espectador pela sessão antiga indevida, encerra a TV e restaura Sobrevivência
            player.getServer().execute(() -> {
                if (sessions.containsKey(player.getUuid())) {
                    stopTv(player);
                } else if (player.isSpectator()) {
                    player.changeGameMode(GameMode.SURVIVAL);
                    player.sendMessage(Text.literal("§6📺 [Canalhas TV]§a Seu modo de jogo foi restaurado para Sobrevivência!"), false);
                }
            });
        }
    }

    public void onPlayerLeave(ServerPlayerEntity player) {
        if (player == null) return;
        SpectatorSession session = sessions.remove(player.getUuid());
        if (session != null && !isBot(player.getGameProfile().getName())) {
            GameMode restoreMode = session.originalGameMode != null && session.originalGameMode != GameMode.SPECTATOR
                    ? session.originalGameMode : GameMode.SURVIVAL;
            player.changeGameMode(restoreMode);
        }
        movementTrackers.remove(player.getUuid());
        spectatedPlayerUuids.remove(player.getUuid());
        spectatedTicks.remove(player.getUuid());
    }

    private void updateMonitoringMusic(MinecraftServer server) {
        Set<UUID> currentlySpectated = new HashSet<>();
        if (!sessions.isEmpty()) {
            for (SpectatorSession session : sessions.values()) {
                if (session.currentTarget instanceof ServerPlayerEntity targetPlayer && !targetPlayer.isRemoved() && targetPlayer.isAlive()) {
                    currentlySpectated.add(targetPlayer.getUuid());
                }
            }
        }

        // 1. Quem acabou de virar alvo de spectate: começa a ouvir monitoring.mp3 a 1% de volume (0.01f)
        for (UUID uuid : currentlySpectated) {
            if (!spectatedPlayerUuids.contains(uuid)) {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                if (p != null) {
                    startMonitoringMusic(p);
                }
                spectatedTicks.put(uuid, 0);
            } else {
                int ticks = spectatedTicks.merge(uuid, 1, Integer::sum);
                // Duração do áudio: 176 segundos (~3520 ticks). Re-toca a cada 175s se continuar spectado (looping contínuo)
                if (ticks >= 175 * 20) {
                    spectatedTicks.put(uuid, 0);
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                    if (p != null) {
                        startMonitoringMusic(p);
                    }
                }
            }
        }

        // 2. Quem deixou de ser spectado (câmera trocou para outro player/pet ou spectate encerrou):
        // Para a música imediatamente
        for (UUID uuid : spectatedPlayerUuids) {
            if (!currentlySpectated.contains(uuid)) {
                spectatedTicks.remove(uuid);
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(uuid);
                if (p != null) {
                    stopMonitoringMusic(p);
                }
            }
        }

        spectatedPlayerUuids.clear();
        spectatedPlayerUuids.addAll(currentlySpectated);
    }

    public void startMonitoringMusic(ServerPlayerEntity player) {
        if (player == null || player.networkHandler == null) return;
        try {
            stopMonitoringMusic(player);
            // Envia o pacote de som PlaySoundS2CPacket direcionado exclusivamente para o jogador
            // Categoria: RECORDS (Jukebox / Disco)
            // Volume: 0.01f (1% - sutil Easter Egg)
            // Pitch: 1.0f (velocidade normal)
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket(
                    net.minecraft.registry.entry.RegistryEntry.of(CumminsMod.MONITORING_MUSIC_EVENT),
                    net.minecraft.sound.SoundCategory.RECORDS,
                    player.getX(), player.getY(), player.getZ(),
                    1.0f,
                    1.0f,
                    player.getRandom().nextLong()
            ));
        } catch (Exception ex) {
            CumminsMod.LOG.warn("[CumminsTV] Falha ao iniciar música de monitoramento para {}: {}", player.getGameProfile().getName(), ex.getMessage());
        }
    }

    public void stopMonitoringMusic(ServerPlayerEntity player) {
        if (player == null || player.networkHandler == null) return;
        try {
            player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.StopSoundS2CPacket(
                    CumminsMod.MONITORING_MUSIC_ID,
                    net.minecraft.sound.SoundCategory.RECORDS
            ));
        } catch (Exception ex) {
            CumminsMod.LOG.warn("[CumminsTV] Falha ao parar música de monitoramento para {}: {}", player.getGameProfile().getName(), ex.getMessage());
        }
    }

    public void setBotNick(String nick) {
        String clean = nick != null ? nick.trim() : "CanalhasTV";
        if (clean.equalsIgnoreCase("jodevil") || clean.equalsIgnoreCase("_iodevil")) {
            clean = "CanalhasTV";
        }
        this.config.botNick = clean;
        save();
    }

    public void setFocusDurationSeconds(int seconds) {
        this.config.focusDurationSeconds = Math.max(5, seconds);
        save();
    }
}
