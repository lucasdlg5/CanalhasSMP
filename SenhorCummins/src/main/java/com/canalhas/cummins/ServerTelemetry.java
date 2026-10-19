package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Coleta métricas de alta precisão do Minecraft Server estilo Spark:
 * TPS, MSPT (Min, Mediana, 95th Percentil, Max), Heap RAM, GC e Players.
 * Exporta em server_telemetry.json e via HTTP em 127.0.0.1:25588/telemetry.
 * Também oferece command bridge (/command e server_command.queue) para o Dashboard.
 */
public class ServerTelemetry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int BUFFER_SIZE = 100; // Últimos 100 ticks (5 segundos)

    private final CumminsMod mod;
    private final Path telemetryFile;
    private final long[] tickDurations = new long[BUFFER_SIZE];
    private int tickIndex = 0;
    private long tickStartNano = 0;
    private int tickCounter = 0;

    private final Map<String, Long> lastGcTimes = new HashMap<>();
    private final Map<String, Long> lastGcCounts = new HashMap<>();
    private String lastAlert = null;
    private long lastAlertTime = 0;

    private HttpServer httpServer;
    private volatile String latestJson = "{}";
    private volatile MinecraftServer currentServer;

    // Snapshot das métricas em cache para consultas da IA e comandos sem recalcular
    private volatile double currentTps = 20.0;
    private volatile double currentMeanMspt = 10.0;
    private volatile double currentP95Mspt = 15.0;
    private volatile double currentMaxMspt = 20.0;
    private volatile long currentUsedRamMb = 0;
    private volatile long currentMaxRamMb = 0;
    private volatile double currentRamPercent = 0.0;

    public double getCurrentTps() { return currentTps; }
    public double getCurrentMeanMspt() { return currentMeanMspt; }
    public double getCurrentP95Mspt() { return currentP95Mspt; }
    public double getCurrentMaxMspt() { return currentMaxMspt; }
    public long getCurrentUsedRamMb() { return currentUsedRamMb; }
    public long getCurrentMaxRamMb() { return currentMaxRamMb; }
    public double getCurrentRamPercent() { return currentRamPercent; }

    public String getPerformanceSummary() {
        String netInfo = "";
        if (mod != null && mod.networkMonitor() != null) {
            var stats = mod.networkMonitor().get5MinStats();
            netInfo = String.format(Locale.ROOT, " | Net Ping (%s): %dms (Perda: %.1f%%)",
                    mod.networkMonitor().get5MinStats().currentPingMs > 0 ? "8.8.8.8" : "host",
                    stats.currentPingMs, stats.packetLossPercent);
        }
        return String.format(Locale.ROOT, "TPS: %.1f | MSPT Médio: %.1fms (P95: %.1fms, Max: %.1fms) | RAM: %dMB/%dMB (%.1f%%)%s",
                currentTps, currentMeanMspt, currentP95Mspt, currentMaxMspt, currentUsedRamMb, currentMaxRamMb, currentRamPercent, netInfo);
    }

    public ServerTelemetry(CumminsMod mod) {
        this.mod = mod;
        this.telemetryFile = Path.of("server_telemetry.json");
    }

    public void start() {
        try {
            // Escuta em 0.0.0.0:25588 para atender tanto localhost (127.0.0.1) quanto a interface Hamachi (25.x.x.x) no app Android
            httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", 25588), 0);
            httpServer.createContext("/telemetry", exchange -> {
                byte[] bytes = latestJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            });

            // Bridge de comando HTTP: permite ao Dashboard enviar comandos mesmo com PID desacoplado
            httpServer.createContext("/command", exchange -> {
                String method = exchange.getRequestMethod();
                String cmd = null;
                if ("POST".equalsIgnoreCase(method)) {
                    try (InputStream is = exchange.getRequestBody()) {
                        cmd = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                    }
                } else if ("GET".equalsIgnoreCase(method)) {
                    String query = exchange.getRequestURI().getRawQuery();
                    if (query != null && query.startsWith("cmd=")) {
                        cmd = URLDecoder.decode(query.substring(4), StandardCharsets.UTF_8).trim();
                    }
                }

                if (cmd != null && !cmd.isBlank() && currentServer != null) {
                    final String toRun = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                    currentServer.execute(() -> {
                        try {
                            currentServer.getCommandManager().executeWithPrefix(currentServer.getCommandSource(), toRun);
                        } catch (Exception ex) {
                            CumminsMod.LOG.warn("[Cummins] Erro ao executar comando via bridge HTTP: {}", ex.getMessage());
                        }
                    });
                    byte[] resp = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                } else {
                    exchange.sendResponseHeaders(400, -1);
                }
            });

            httpServer.setExecutor(null);
            httpServer.start();
            CumminsMod.LOG.info("[Cummins] Telemetria e Command Bridge ativos em http://127.0.0.1:25588/");
        } catch (Exception e) {
            CumminsMod.LOG.info("[Cummins] Servidor HTTP de telemetria desativado (usando server_telemetry.json): {}", e.getMessage());
        }
    }

    public void stop() {
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            } catch (Exception ignored) {}
            httpServer = null;
        }
    }

    public void onTickStart() {
        tickStartNano = System.nanoTime();
    }

    public void onTickEnd(MinecraftServer server) {
        this.currentServer = server;
        long duration = System.nanoTime() - tickStartNano;
        tickDurations[tickIndex] = duration;
        tickIndex = (tickIndex + 1) % BUFFER_SIZE;

        // Processa fila de comandos por arquivo se existir
        Path queuePath = Path.of("server_command.queue");
        if (Files.exists(queuePath)) {
            try {
                List<String> commands = Files.readAllLines(queuePath, StandardCharsets.UTF_8);
                Files.deleteIfExists(queuePath);
                for (String c : commands) {
                    String trimmed = c.trim();
                    if (!trimmed.isEmpty()) {
                        final String toRun = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), toRun);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (++tickCounter % 20 == 0) { // Atualiza a cada 1 segundo (20 ticks)
            updateTelemetry(server);
        }
    }

    private void updateTelemetry(MinecraftServer server) {
        long[] copy = Arrays.copyOf(tickDurations, BUFFER_SIZE);
        Arrays.sort(copy);

        double minMs = copy[0] / 1_000_000.0;
        double medianMs = copy[BUFFER_SIZE / 2] / 1_000_000.0;
        double p95Ms = copy[(int) (BUFFER_SIZE * 0.95)] / 1_000_000.0;
        double maxMs = copy[BUFFER_SIZE - 1] / 1_000_000.0;

        long sum = 0;
        for (long d : copy) sum += d;
        double meanMs = (sum / (double) BUFFER_SIZE) / 1_000_000.0;

        double tps = Math.min(20.0, 1000.0 / Math.max(50.0, meanMs));

        // Memória JVM
        Runtime rt = Runtime.getRuntime();
        long totalMemory = rt.totalMemory();
        long freeMemory = rt.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long maxMemory = rt.maxMemory();

        long usedMb = usedMemory / (1024 * 1024);
        long totalMb = totalMemory / (1024 * 1024);
        long maxMb = maxMemory / (1024 * 1024);
        double ramPercent = Math.round(((double) usedMemory / totalMemory) * 1000.0) / 10.0;

        // Atualiza snapshot volátil para consultas em tempo real
        this.currentTps = Math.round(tps * 100.0) / 100.0;
        this.currentMeanMspt = Math.round(meanMs * 100.0) / 100.0;
        this.currentP95Mspt = Math.round(p95Ms * 100.0) / 100.0;
        this.currentMaxMspt = Math.round(maxMs * 100.0) / 100.0;
        this.currentUsedRamMb = usedMb;
        this.currentMaxRamMb = maxMb;
        this.currentRamPercent = ramPercent;

        JsonObject root = new JsonObject();
        root.addProperty("timestamp", System.currentTimeMillis());
        root.addProperty("pid", ProcessHandle.current().pid());
        root.addProperty("tps", this.currentTps);

        JsonObject mspt = new JsonObject();
        mspt.addProperty("min", Math.round(minMs * 100.0) / 100.0);
        mspt.addProperty("mean", this.currentMeanMspt);
        mspt.addProperty("median", Math.round(medianMs * 100.0) / 100.0);
        mspt.addProperty("p95", this.currentP95Mspt);
        mspt.addProperty("max", this.currentMaxMspt);
        root.add("mspt", mspt);

        JsonObject memory = new JsonObject();
        memory.addProperty("usedMb", usedMb);
        memory.addProperty("totalMb", totalMb);
        memory.addProperty("maxMb", maxMb);
        memory.addProperty("percent", ramPercent);
        root.add("memory", memory);

        // Garbage Collector
        JsonArray gcArray = new JsonArray();
        String currentAlert = null;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            String name = gc.getName();
            long count = gc.getCollectionCount();
            long timeMs = gc.getCollectionTime();

            long prevTime = lastGcTimes.getOrDefault(name, timeMs);
            long deltaTime = timeMs - prevTime;
            lastGcTimes.put(name, timeMs);

            long prevCount = lastGcCounts.getOrDefault(name, count);
            long deltaCount = count - prevCount;
            lastGcCounts.put(name, count);

            JsonObject gcObj = new JsonObject();
            gcObj.addProperty("name", name);
            gcObj.addProperty("count", count);
            gcObj.addProperty("timeMs", timeMs);
            gcObj.addProperty("deltaTimeMs", deltaTime);
            gcObj.addProperty("deltaCount", deltaCount);
            gcArray.add(gcObj);

            // Alerta se um ciclo de GC demorar mais que 100ms
            if (deltaTime > 100) {
                currentAlert = "Alerta GC: " + name + " pausou " + deltaTime + "ms (" + deltaCount + " ciclo(s))";
            } else if (name.toLowerCase(Locale.ROOT).contains("old") && deltaCount > 0) {
                currentAlert = "Alerta Full GC detectado: " + name + " (" + deltaTime + "ms)";
            }
        }
        root.add("gc", gcArray);

        if (currentAlert != null) {
            lastAlert = currentAlert;
            lastAlertTime = System.currentTimeMillis();
        } else if (System.currentTimeMillis() - lastAlertTime > 15000) {
            lastAlert = null; // expira alerta após 15 segundos
        }
        root.addProperty("alert", lastAlert);

        // Jogadores
        JsonArray players = new JsonArray();
        JsonArray playerDetails = new JsonArray();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String name = p.getGameProfile().getName();
            players.add(name);

            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", name);
            pObj.addProperty("uuid", p.getUuidAsString());
            pObj.addProperty("dimension", p.getServerWorld().getRegistryKey().getValue().toString());
            pObj.addProperty("x", Math.round(p.getX() * 10.0) / 10.0);
            pObj.addProperty("y", Math.round(p.getY() * 10.0) / 10.0);
            pObj.addProperty("z", Math.round(p.getZ() * 10.0) / 10.0);
            pObj.addProperty("yaw", Math.round(p.getYaw() * 10.0) / 10.0);
            pObj.addProperty("health", Math.round(p.getHealth() * 10.0) / 10.0);
            pObj.addProperty("maxHealth", Math.round(p.getMaxHealth() * 10.0) / 10.0);
            playerDetails.add(pObj);
        }
        root.add("players", players);
        root.add("playerDetails", playerDetails);
        root.addProperty("playerCount", players.size());

        // Senhor Cummins (Gato Físico)
        try {
            if (mod != null && mod.body() != null) {
                CatEntity cat = mod.body().getCat();
                if (cat != null && cat.isAlive()) {
                    JsonObject catObj = new JsonObject();
                    catObj.addProperty("name", "Senhor Cummins");
                    catObj.addProperty("dimension", cat.getWorld().getRegistryKey().getValue().toString());
                    catObj.addProperty("x", Math.round(cat.getX() * 10.0) / 10.0);
                    catObj.addProperty("y", Math.round(cat.getY() * 10.0) / 10.0);
                    catObj.addProperty("z", Math.round(cat.getZ() * 10.0) / 10.0);
                    catObj.addProperty("yaw", Math.round(cat.getYaw() * 10.0) / 10.0);
                    root.add("cummins", catObj);
                }
            }
        } catch (Exception ignored) {}

        // Vitória (Cachorrinha Fox Paulistinha do ArzanLion)
        try {
            if (mod != null && mod.vitoria() != null) {
                net.minecraft.entity.passive.WolfEntity dog = mod.vitoria().getDog();
                if (dog != null && dog.isAlive()) {
                    JsonObject dogObj = new JsonObject();
                    dogObj.addProperty("name", "Vitória");
                    dogObj.addProperty("dimension", dog.getWorld().getRegistryKey().getValue().toString());
                    dogObj.addProperty("x", Math.round(dog.getX() * 10.0) / 10.0);
                    dogObj.addProperty("y", Math.round(dog.getY() * 10.0) / 10.0);
                    dogObj.addProperty("z", Math.round(dog.getZ() * 10.0) / 10.0);
                    dogObj.addProperty("yaw", Math.round(dog.getYaw() * 10.0) / 10.0);
                    dogObj.addProperty("aiEnabled", mod.config().vitoriaAiEnabled);
                    root.add("vitoria", dogObj);
                }
            }
        } catch (Exception ignored) {}

        // Chat recente dos jogadores e pets (para a aba de Chat do App Android / Dashboard)
        try {
            if (mod != null && mod.context() != null) {
                JsonArray chatArr = new JsonArray();
                for (String msg : mod.context().gameSnapshot()) {
                    chatArr.add(msg);
                }
                root.add("chatMessages", chatArr);
            }
        } catch (Exception ignored) {}

        // Últimas linhas do console do servidor (logs/latest.log) para a aba de Console do App Android
        try {
            JsonArray logsArr = new JsonArray();
            for (String line : readTailLogLines(Path.of("logs", "latest.log"), 80)) {
                logsArr.add(line);
            }
            root.add("recentLogs", logsArr);
        } catch (Exception ignored) {}

        // Saúde da Conexão / Internet do Host (Ping contínuo)
        try {
            if (mod != null && mod.networkMonitor() != null) {
                root.add("network", mod.networkMonitor().getTelemetryJsonObject());
            }
        } catch (Exception ignored) {}

        // Cota e consumo de tokens LLM (para o Dashboard)
        try {
            if (mod != null && mod.tokenTracker() != null) {
                root.add("tokens", mod.tokenTracker().getTelemetryJsonObject());
            }
        } catch (Exception ignored) {}

        // Mural de Novidades (para o Dashboard e Mobile App)
        try {
            if (mod != null && mod.mural() != null && mod.mural().getData() != null) {
                root.add("mural", GSON.toJsonTree(mod.mural().getData()));
            }
        } catch (Exception ignored) {}

        latestJson = GSON.toJson(root);

        // Salva atomicamente no arquivo
        try {
            Path tmp = Path.of("server_telemetry.tmp");
            Files.writeString(tmp, latestJson, StandardCharsets.UTF_8);
            Files.move(tmp, telemetryFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ignored) {
            try {
                Files.writeString(telemetryFile, latestJson, StandardCharsets.UTF_8);
            } catch (Exception ignored2) {}
        }
    }

    private List<String> readTailLogLines(Path logPath, int maxLines) {
        if (!Files.exists(logPath)) return Collections.emptyList();
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(logPath.toFile(), "r")) {
            long len = raf.length();
            long readBytes = Math.min(len, 16384L); // Lê apenas os últimos 16 KB para performance instantânea
            raf.seek(len - readBytes);
            byte[] buf = new byte[(int) readBytes];
            raf.readFully(buf);
            String text = new String(buf, StandardCharsets.UTF_8);
            String[] lines = text.split("\\r?\\n");
            int start = Math.max(len > readBytes ? 1 : 0, lines.length - maxLines);
            List<String> out = new ArrayList<>(lines.length - start);
            for (int i = start; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    out.add(lines[i]);
                }
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
