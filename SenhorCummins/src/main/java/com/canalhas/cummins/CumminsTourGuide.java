package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.passive.CatEntity;
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

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Guia Turístico / "Leva até lá":
 * - Mantém waypoints conhecidos do servidor (pontos_turisticos.json).
 * - Permite aos jogadores salvarem novos pontos via /cummins ponto criar <nome> ou pelo chat.
 * - Quando solicitado (/cummins levar <nome> ou chat "cummins me leva até..."), o Cummins guia o jogador até o local,
 *   olhando para trás para checar se o jogador está acompanhando.
 */
public class CumminsTourGuide {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final CumminsMod mod;
    private final Path file;

    public static class Waypoint {
        public String name;
        public String dimension;
        public int x;
        public int y;
        public int z;
        public String creator;

        public Waypoint() {}

        public Waypoint(String name, String dimension, int x, int y, int z, String creator) {
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.creator = creator;
        }
    }

    public static class TourStore {
        public Map<String, Waypoint> waypoints = new LinkedHashMap<>();
    }

    private TourStore store = new TourStore();

    // Estado do Guia Ativo
    private Waypoint activeTarget = null;
    private UUID activeRequesterUuid = null;
    private long tourStartedAt = 0;
    private long lastWaitSoundAt = 0;

    public CumminsTourGuide(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.file = configDir.resolve("pontos_turisticos.json");
        load();
    }

    public synchronized void load() {
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    TourStore s = GSON.fromJson(r, TourStore.class);
                    if (s != null && s.waypoints != null) {
                        this.store = s;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar pontos_turisticos.json: {}", e.getMessage());
        }
        if (store == null) store = new TourStore();
        if (store.waypoints == null) store.waypoints = new LinkedHashMap<>();
        initDefaults();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(store, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar pontos_turisticos.json: {}", e.getMessage());
        }
    }

    private void initDefaults() {
        if (store.waypoints.isEmpty()) {
            store.waypoints.put("spawn", new Waypoint("Spawn", "minecraft:overworld", 0, 70, 0, "Servidor"));
            store.waypoints.put("forja", new Waypoint("Forja", "minecraft:overworld", 100, 70, 100, "Servidor"));
            store.waypoints.put("peugeot", new Waypoint("Peugeot", "minecraft:overworld", 50, 70, 50, "Servidor"));
            save();
        }
    }

    public synchronized boolean addWaypoint(String name, ServerPlayerEntity player) {
        if (name == null || name.isBlank() || player == null) return false;
        String key = name.trim().toLowerCase(Locale.ROOT);
        String dim = player.getWorld().getRegistryKey().getValue().toString();
        BlockPos pos = player.getBlockPos();
        Waypoint wp = new Waypoint(name.trim(), dim, pos.getX(), pos.getY(), pos.getZ(), player.getGameProfile().getName());
        store.waypoints.put(key, wp);
        save();
        return true;
    }

    public synchronized boolean removeWaypoint(String name) {
        if (name == null) return false;
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (store.waypoints.remove(key) != null) {
            save();
            return true;
        }
        return false;
    }

    public synchronized Waypoint findWaypoint(String query) {
        if (query == null || query.isBlank()) return null;
        String q = query.trim().toLowerCase(Locale.ROOT);

        // Busca exata
        if (store.waypoints.containsKey(q)) return store.waypoints.get(q);

        // Busca parcial
        for (Map.Entry<String, Waypoint> e : store.waypoints.entrySet()) {
            if (e.getKey().contains(q) || q.contains(e.getKey()) || e.getValue().name.toLowerCase(Locale.ROOT).contains(q)) {
                return e.getValue();
            }
        }
        return null;
    }

    public synchronized List<Waypoint> getAllWaypoints() {
        return new ArrayList<>(store.waypoints.values());
    }

    public boolean isGuiding() {
        return activeTarget != null && System.currentTimeMillis() - tourStartedAt < 300_000L; // 5 minutos máx
    }

    public void cancelTour() {
        this.activeTarget = null;
        this.activeRequesterUuid = null;
        this.tourStartedAt = 0;
    }

    public boolean startGuide(ServerPlayerEntity player, Waypoint target) {
        if (player == null || target == null) return false;
        String playerDim = player.getWorld().getRegistryKey().getValue().toString();
        if (!playerDim.equalsIgnoreCase(target.dimension)) {
            player.sendMessage(Text.literal("§6[Cummins]§r O ponto '§e" + target.name + "§r' fica na dimensão §c" + target.dimension + "§r! Entre lá primeiro que eu te levo."), false);
            return false;
        }

        CatEntity cat = mod.body().getCat();
        if (cat == null) {
            cat = mod.body().spawnNear(player);
        }

        this.activeTarget = target;
        this.activeRequesterUuid = player.getUuid();
        this.tourStartedAt = System.currentTimeMillis();

        mod.body().clearScanGuidance(cat);
        cat.setSitting(false);
        cat.setInSittingPose(false);

        String msg = "Bora lá! Segue o rabo do gato que eu te levo até " + target.name + "! 🐾";
        mod.brain().broadcast(msg, player.getGameProfile().getName());
        return true;
    }

    public void onTick(CatEntity cat, MinecraftServer server) {
        if (!isGuiding() || cat == null || server == null) return;

        ServerPlayerEntity player = server.getPlayerManager().getPlayer(activeRequesterUuid);
        if (player == null || !player.isAlive()) {
            cancelTour();
            return;
        }

        if (cat.getWorld() != player.getWorld()) {
            cancelTour();
            return;
        }

        ServerWorld sw = (ServerWorld) cat.getWorld();
        double targetX = activeTarget.x + 0.5;
        double targetY = activeTarget.y;
        double targetZ = activeTarget.z + 0.5;

        double distToTargetSq = cat.squaredDistanceTo(targetX, cat.getY(), targetZ);
        double playerDistToCatSq = cat.squaredDistanceTo(player);

        // Chegou ao destino (menos de 3.8 blocos)
        if (distToTargetSq <= 3.8 * 3.8) {
            cat.getNavigation().stop();
            cat.setSitting(true);
            cat.setInSittingPose(true);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.6, cat.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            sw.spawnParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7, cat.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
            sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 1.0f, 1.1f);
            sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURR, SoundCategory.NEUTRAL, 1.0f, 1.0f);

            String endMsg = "Chegamos em " + activeTarget.name + "! Tá entregue com segurança pela firma! 🐾✨";
            mod.brain().broadcast(endMsg, player.getGameProfile().getName());
            cancelTour();
            return;
        }

        // Se o player ficou muito para trás (> 11 blocos)
        if (playerDistToCatSq > 11.0 * 11.0) {
            cat.getNavigation().stop();
            cat.getLookControl().lookAt(player, 60.0f, 60.0f);

            long now = System.currentTimeMillis();
            if (now - lastWaitSoundAt > 3500L) {
                lastWaitSoundAt = now;
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.9f, 1.25f);
                // Trilha de partículas de guia
                Vec3d toTarget = new Vec3d(targetX - cat.getX(), 0, targetZ - cat.getZ()).normalize();
                for (double d = 0.8; d <= 3.0; d += 0.8) {
                    sw.spawnParticles(ParticleTypes.GLOW, cat.getX() + toTarget.x * d, cat.getY() + 0.4, cat.getZ() + toTarget.z * d, 1, 0, 0, 0, 0);
                }
            }
            return;
        }

        // Caminha ativamente em direção ao destino
        if (cat.age % 10 == 0) {
            cat.getNavigation().startMovingTo(targetX, targetY, targetZ, 1.35D);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, cat.getX(), cat.getY() + 0.2, cat.getZ(), 1, 0.1, 0.1, 0.1, 0.02);
        }
    }
}
