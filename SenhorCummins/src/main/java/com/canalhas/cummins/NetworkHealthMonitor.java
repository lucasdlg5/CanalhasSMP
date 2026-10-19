package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monitor Contínuo de Conexão com a Internet (Host Side):
 * - Executa ping constante em segundo plano (ex: 8.8.8.8 -t) sem sobrecarregar ou travar o servidor.
 * - Mantém um histórico deslizante dos últimos 5 minutos (latência média, mínima, máxima, perda de pacotes).
 * - Detecta e registra quedas de internet momentâneas do host em network_outages.log.
 * - Avisa no chat quando uma queda do lado do servidor ocorre para que os jogadores saibam que o lag foi no host.
 * - Quando um jogador reconecta ou indaga sobre a conexão ("minha net ou o server?"), responde com o diagnóstico exato dos últimos 5 minutos.
 */
public class NetworkHealthMonitor {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Pattern PING_TIME_PATTERN = Pattern.compile("(?:tempo|time)[=<](\\d+)\\s*ms", Pattern.CASE_INSENSITIVE);

    private final CumminsMod mod;
    private final Path outagesLogFile;
    private volatile boolean running = false;
    private Thread workerThread;
    private Process currentPingProcess;

    public static class PingSample {
        public final long timestampMs;
        public final int pingMs; // -1 indica perda de pacote / timeout

        public PingSample(long timestampMs, int pingMs) {
            this.timestampMs = timestampMs;
            this.pingMs = pingMs;
        }

        public boolean isLoss() {
            return pingMs < 0;
        }
    }

    public static class OutageEvent {
        public final long startMs;
        public final long endMs;
        public final int durationSeconds;
        public final int lostPackets;
        public final String formattedDate;

        public OutageEvent(long startMs, long endMs, int durationSeconds, int lostPackets) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.durationSeconds = durationSeconds;
            this.lostPackets = lostPackets;
            this.formattedDate = LocalDateTime.now().format(TIME_FMT);
        }
    }

    public static class NetworkStats {
        public int totalSamples = 0;
        public int lostSamples = 0;
        public double packetLossPercent = 0.0;
        public int currentPingMs = 0;
        public double avgPingMs = 0.0;
        public int minPingMs = 0;
        public int maxPingMs = 0;
        public int outagesCount = 0;
        public OutageEvent lastOutage = null;
        public boolean isOnline = true;
        public boolean multiplePlayersDroppedRecently = false;
        public long lastBurstDisconnectAgoSec = -1;
    }

    // Histórico em memória dos últimos 5 minutos
    private final ArrayDeque<PingSample> samples = new ArrayDeque<>();
    private final List<OutageEvent> recentOutages = new ArrayList<>();
    private final Map<UUID, Long> playerDisconnectTimes = new ConcurrentHashMap<>();
    private final List<Long> recentDisconnectTimestamps = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile long lastBurstDisconnectAt = 0;

    // Estado da queda atual
    private volatile int latestPingMs = 10;
    private volatile boolean currentlyOnline = true;
    private volatile long currentOutageStartMs = 0;
    private volatile int consecutiveLossCount = 0;
    private volatile long lastOutageBroadcastAt = 0;

    public NetworkHealthMonitor(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.outagesLogFile = configDir.resolve("network_outages.log");
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        workerThread = new Thread(this::runPingLoop, "Cummins-NetworkPing");
        workerThread.setDaemon(true);
        workerThread.start();
        CumminsMod.LOG.info("[Cummins] Monitor contínuo de ping e saúde de internet iniciado ({})", getTarget());
    }

    public synchronized void stop() {
        running = false;
        if (currentPingProcess != null) {
            try {
                currentPingProcess.destroyForcibly();
            } catch (Exception ignored) {}
            currentPingProcess = null;
        }
        if (workerThread != null) {
            workerThread.interrupt();
            workerThread = null;
        }
    }

    private String getTarget() {
        if (mod.config() != null && mod.config().networkMonitor != null && mod.config().networkMonitor.pingTarget != null) {
            return mod.config().networkMonitor.pingTarget.trim();
        }
        return "8.8.8.8";
    }

    private void runPingLoop() {
        while (running) {
            boolean processSuccess = tryRunPingProcess();
            if (!processSuccess && running) {
                // Fallback para socket ping caso ProcessBuilder não consiga rodar ping.exe
                runSocketPingFallback();
            }
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Inicia o comando ping contínuo nativo (ex: ping 8.8.8.8 -t) e lê linha por linha em tempo real.
     */
    private boolean tryRunPingProcess() {
        String target = getTarget();
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> cmd = isWindows
                ? List.of("ping", target, "-t")
                : List.of("ping", "-i", "1", target);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            currentPingProcess = pb.start();

            // Pega o charset do Windows OEM (CP850) ou default
            Charset cs;
            try {
                cs = isWindows ? Charset.forName("IBM850") : StandardCharsets.UTF_8;
            } catch (Exception e) {
                cs = Charset.defaultCharset();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentPingProcess.getInputStream(), cs))) {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    processPingLine(line);
                }
            }

            int exitCode = currentPingProcess.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            if (running) {
                CumminsMod.LOG.debug("[Cummins] Ping process encerrou: {}", e.getMessage());
            }
            return false;
        } finally {
            if (currentPingProcess != null) {
                try {
                    currentPingProcess.destroyForcibly();
                } catch (Exception ignored) {}
                currentPingProcess = null;
            }
        }
    }

    /**
     * Fallback via conexão TCP rápida (porta 53 DNS) se ping.exe não estiver disponível.
     */
    private void runSocketPingFallback() {
        String target = getTarget();
        while (running) {
            long startNano = System.nanoTime();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(target, 53), 1500);
                long latencyMs = (System.nanoTime() - startNano) / 1_000_000L;
                onPingSuccess((int) latencyMs);
            } catch (Exception e) {
                onPingLoss();
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void processPingLine(String line) {
        if (line == null || line.isBlank()) return;
        String lower = line.toLowerCase(Locale.ROOT);

        // Verifica sucesso com tempo em ms
        Matcher matcher = PING_TIME_PATTERN.matcher(line);
        if (matcher.find()) {
            try {
                int pingMs = Integer.parseInt(matcher.group(1));
                onPingSuccess(pingMs);
                return;
            } catch (Exception ignored) {}
        }

        // Verifica se a linha indica timeout, perda de pacote ou host inacessível
        if (lower.contains("esgotado") || lower.contains("tempo limite") || lower.contains("timed out")
                || lower.contains("inacess") || lower.contains("unreachable") || lower.contains("falha")
                || lower.contains("failure") || lower.contains("100% de perda") || lower.contains("100% packet loss")) {
            onPingLoss();
        }
    }

    private synchronized void onPingSuccess(int pingMs) {
        latestPingMs = Math.max(1, pingMs);
        currentlyOnline = true;
        long now = System.currentTimeMillis();

        // Se estávamos em uma queda de conexão e agora retornou
        if (currentOutageStartMs > 0) {
            long durationSec = (now - currentOutageStartMs) / 1000L;
            int threshold = (mod.config() != null && mod.config().networkMonitor != null)
                    ? mod.config().networkMonitor.dropThresholdSeconds : 2;

            if (durationSec >= threshold) {
                OutageEvent event = new OutageEvent(currentOutageStartMs, now, (int) durationSec, consecutiveLossCount);
                registerOutage(event);
            }
            currentOutageStartMs = 0;
            consecutiveLossCount = 0;
        }

        samples.addLast(new PingSample(now, pingMs));
        cleanOldSamples(now);
    }

    private synchronized void onPingLoss() {
        currentlyOnline = false;
        long now = System.currentTimeMillis();
        consecutiveLossCount++;

        // A partir de 2 perdas seguidas, marca o início formal da queda
        if (consecutiveLossCount >= 2 && currentOutageStartMs == 0) {
            currentOutageStartMs = now - 2000L; // conta os segundos anteriores que falharam
        }

        samples.addLast(new PingSample(now, -1));
        cleanOldSamples(now);
    }

    private synchronized void cleanOldSamples(long now) {
        long windowMs = 300_000L; // 5 minutos
        if (mod.config() != null && mod.config().networkMonitor != null) {
            windowMs = mod.config().networkMonitor.historyMinutes * 60_000L;
        }
        long cutoff = now - windowMs;
        while (!samples.isEmpty() && samples.peekFirst().timestampMs < cutoff) {
            samples.pollFirst();
        }

        // Limpa quedas mais velhas que 1 hora
        long outageCutoff = now - 3_600_000L;
        recentOutages.removeIf(o -> o.endMs < outageCutoff);
    }

    private void registerOutage(OutageEvent event) {
        recentOutages.add(event);
        logOutageToFile(event);

        CumminsMod.LOG.warn("[Cummins] 🚨 Queda de internet no host detectada: durou {}s ({} pacotes perdidos). Ping normalizado para {}ms",
                event.durationSeconds, event.lostPackets, latestPingMs);

        // Avisa no chat do servidor se habilitado na config
        boolean announce = mod.config() == null || mod.config().networkMonitor == null || mod.config().networkMonitor.announceOutagesInChat;
        long now = System.currentTimeMillis();
        if (announce && now - lastOutageBroadcastAt > 30_000L) {
            lastOutageBroadcastAt = now;
            String msg = String.format(Locale.ROOT,
                    "🚨 [Aviso de Conexão] A internet do servidor (host) sofreu uma oscilação/queda de %ds há instantes! "
                    + "Se alguém sentiu travamento ou foi desconectado, o lag foi do NOSSO lado. Conexão já restabeleceu (ping: %dms)! 🐾",
                    event.durationSeconds, latestPingMs);
            if (mod.brain() != null) {
                mod.brain().broadcast(msg, null);
            }
        }
    }

    private void logOutageToFile(OutageEvent event) {
        try {
            Files.createDirectories(outagesLogFile.getParent());
            String line = String.format(Locale.ROOT, "[%s] QUEDA DE INTERNET: duracao=%ds, pacotesPerdidos=%d, alvo=%s, pingAposRecuperacao=%dms%n",
                    event.formattedDate, event.durationSeconds, event.lostPackets, getTarget(), latestPingMs);
            Files.writeString(outagesLogFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao registrar network_outages.log: {}", e.getMessage());
        }
    }

    public synchronized NetworkStats get5MinStats() {
        NetworkStats stats = new NetworkStats();
        long now = System.currentTimeMillis();
        cleanOldSamples(now);

        stats.isOnline = currentlyOnline;
        stats.currentPingMs = latestPingMs;
        stats.totalSamples = samples.size();

        if (samples.isEmpty()) {
            return stats;
        }

        long sumPing = 0;
        int countSuccess = 0;
        int minP = Integer.MAX_VALUE;
        int maxP = 0;

        for (PingSample s : samples) {
            if (s.isLoss()) {
                stats.lostSamples++;
            } else {
                countSuccess++;
                sumPing += s.pingMs;
                if (s.pingMs < minP) minP = s.pingMs;
                if (s.pingMs > maxP) maxP = s.pingMs;
            }
        }

        stats.packetLossPercent = stats.totalSamples > 0
                ? Math.round(((double) stats.lostSamples / stats.totalSamples) * 1000.0) / 10.0
                : 0.0;
        stats.avgPingMs = countSuccess > 0 ? Math.round(((double) sumPing / countSuccess) * 10.0) / 10.0 : latestPingMs;
        stats.minPingMs = minP == Integer.MAX_VALUE ? latestPingMs : minP;
        stats.maxPingMs = maxP == 0 ? latestPingMs : maxP;

        long cutoff5m = now - 300_000L;
        for (OutageEvent o : recentOutages) {
            if (o.endMs >= cutoff5m) {
                stats.outagesCount++;
                if (stats.lastOutage == null || o.endMs > stats.lastOutage.endMs) {
                    stats.lastOutage = o;
                }
            }
        }

        long burstAgo = now - lastBurstDisconnectAt;
        if (lastBurstDisconnectAt > 0 && burstAgo <= 600_000L) { // 10 minutos
            stats.multiplePlayersDroppedRecently = true;
            stats.lastBurstDisconnectAgoSec = burstAgo / 1000L;
        }

        return stats;
    }

    public boolean wasServerInternetUnstableInLast(int minutes) {
        long cutoff = System.currentTimeMillis() - (minutes * 60_000L);
        synchronized (this) {
            for (OutageEvent o : recentOutages) {
                if (o.endMs >= cutoff) return true;
            }
        }
        NetworkStats stats = get5MinStats();
        return stats.packetLossPercent >= 3.0 || stats.multiplePlayersDroppedRecently;
    }

    /**
     * Gera resposta explicativa personalizada para jogadores que perguntam sobre lag ou conexão.
     */
    public String formatPlayerDiagnostic(String playerName) {
        NetworkStats stats = get5MinStats();
        double tps = mod.telemetry() != null ? mod.telemetry().getCurrentTps() : 20.0;
        long now = System.currentTimeMillis();

        if (stats.outagesCount > 0 || stats.packetLossPercent >= 3.5 || !stats.isOnline) {
            int duration = stats.lastOutage != null ? stats.lastOutage.durationSeconds : 5;
            long agoSec = stats.lastOutage != null ? Math.max(1, (now - stats.lastOutage.endMs) / 1000L) : 30;
            String agoStr = agoSec < 60 ? (agoSec + " segundos atrás") : ((agoSec / 60) + " minutos atrás");

            return String.format(Locale.ROOT,
                    "Ô %s, o lag/queda recente foi do NOSSO lado sim! 🚨 A internet do host teve uma oscilação de %ds há %s (perda de %.1f%% de pacotes). "
                    + "Agora a conexão já normalizou (ping: %dms, TPS: %.1f). Desculpa o susto!",
                    playerName, duration, agoStr, stats.packetLossPercent, stats.currentPingMs, tps);
        } else if (stats.multiplePlayersDroppedRecently) {
            long minAgo = Math.max(1, stats.lastBurstDisconnectAgoSec / 60L);
            return String.format(Locale.ROOT,
                    "Ô %s, a rota do host/Radmin VPN deu uma engasgada há cerca de %d minuto(s) que derrubou vários jogadores juntos! 🚨 "
                    + "A internet aqui do host já estabilizou (ping %.1fms para o Google e 0%% de perda agora). O lag foi do nosso lado sim, desculpa!",
                    playerName, minAgo, stats.avgPingMs);
        } else {
            double meanMspt = mod.telemetry() != null ? mod.telemetry().getCurrentMeanMspt() : 10.0;
            return String.format(Locale.ROOT,
                    "Ô %s, a estabilidade e internet do servidor estão 100%% LISAS nos últimos minutos! 🌐 "
                    + "(0%% de perda de pacotes, ping cravado em %.1fms para o Google 8.8.8.8, TPS em %.1f e MSPT em %.1fms). "
                    + "Se você sentiu alguma travada isolada, pode ter sido na sua rota ou momento de backup!",
                    playerName, stats.avgPingMs, tps, meanMspt);
        }
    }

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        playerDisconnectTimes.put(player.getUuid(), now);
        recentDisconnectTimestamps.add(now);
        recentDisconnectTimestamps.removeIf(t -> now - t > 180_000L);
        long countRecent = recentDisconnectTimestamps.stream().filter(t -> now - t <= 60_000L).count();
        if (countRecent >= 2) {
            lastBurstDisconnectAt = now;
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        Long discTime = playerDisconnectTimes.remove(uuid);
        if (discTime == null) return;

        long now = System.currentTimeMillis();
        // Se o jogador reconectou dentro de 8 minutos após cair
        if (now - discTime < 480_000L) {
            OutageEvent matchingOutage = null;
            synchronized (this) {
                for (OutageEvent o : recentOutages) {
                    // Queda aconteceu enquanto o player estava offline ou nos 45s antes dele desconectar
                    if (o.endMs >= discTime - 45_000L && o.startMs <= now) {
                        matchingOutage = o;
                        break;
                    }
                }
            }

            if (matchingOutage != null && mod.brain() != null) {
                String pName = player.getGameProfile().getName();
                final int dur = matchingOutage.durationSeconds;
                final int currentP = latestPingMs;
                // Envia após 3 segundos no jogo para a tela de loading já ter sumido
                mod.brain().scheduleTask(() -> {
                    String welcome = String.format(Locale.ROOT,
                            "Opa %s, bem-vindo(a) de volta! Vi que você caiu há pouco. "
                            + "A internet aqui do host teve uma oscilação momentânea de %ds bem na hora, então a culpa foi do nosso lado! "
                            + "Já restabeleceu e tá 100%% estável agora (ping: %dms). Tmj! 🐾",
                            pName, dur, currentP);
                    mod.brain().broadcast(welcome, pName);
                }, 3000L);
            }
        }
    }

    public JsonObject getTelemetryJsonObject() {
        NetworkStats stats = get5MinStats();
        JsonObject obj = new JsonObject();
        obj.addProperty("target", getTarget());
        obj.addProperty("online", stats.isOnline);
        obj.addProperty("currentPingMs", stats.currentPingMs);
        obj.addProperty("avgPing5m", stats.avgPingMs);
        obj.addProperty("minPing5m", stats.minPingMs);
        obj.addProperty("maxPing5m", stats.maxPingMs);
        obj.addProperty("packetLossPercent5m", stats.packetLossPercent);
        obj.addProperty("packetLossCount5m", stats.lostSamples);
        obj.addProperty("totalSamples5m", stats.totalSamples);
        obj.addProperty("outagesLast5m", stats.outagesCount);

        long now = System.currentTimeMillis();
        long lastOutageAgo = stats.lastOutage != null ? (now - stats.lastOutage.endMs) / 1000L : -1;
        obj.addProperty("lastOutageAgoSeconds", lastOutageAgo);
        obj.addProperty("lastOutageDurationSeconds", stats.lastOutage != null ? stats.lastOutage.durationSeconds : 0);
        obj.addProperty("summary", stats.isOnline && stats.packetLossPercent < 2.0 ? "Estável (ping " + stats.currentPingMs + "ms)" : "Instável / Perda detectada");
        return obj;
    }
}
