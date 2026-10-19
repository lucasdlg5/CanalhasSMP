package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Gerencia o registro histórico de relatos de lag, travamentos e lentidão enviados pelos jogadores via chat.
 * Salva os relatos tanto em formato JSON estruturado (lag_reports.json) quanto em log legível (lag_reports.log)
 * para fácil inspeção do Asminn e administradores.
 */
public class LagReportManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path reportsJsonFile;
    private final Path reportsLogFile;
    private final List<LagReport> recentReports = new ArrayList<>();

    public static class LagReport extends com.canalhas.cummins.LagReport {
        public LagReport() {
            super();
        }

        public LagReport(String playerName, String playerUuid, String rawMessage,
                         String dimension, double x, double y, double z,
                         double tps, double meanMspt, double maxMspt,
                         long ramUsedMb, double ramPercent,
                         int netPingMs, double netPacketLossPercent, boolean netOutageRecent) {
            super(playerName, playerUuid, rawMessage, dimension, x, y, z,
                    tps, meanMspt, maxMspt, ramUsedMb, ramPercent, netPingMs, netPacketLossPercent, netOutageRecent);
        }
    }

    public LagReportManager(Path storageDir) {
        this.reportsJsonFile = storageDir.resolve("lag_reports.json");
        this.reportsLogFile = storageDir.resolve("lag_reports.log");
        loadExistingReports();
    }

    private synchronized void loadExistingReports() {
        if (!Files.exists(reportsJsonFile)) return;
        try {
            String json = Files.readString(reportsJsonFile, StandardCharsets.UTF_8);
            JsonArray arr = GSON.fromJson(json, JsonArray.class);
            if (arr != null) {
                recentReports.clear();
                for (var el : arr) {
                    LagReport r = GSON.fromJson(el, LagReport.class);
                    if (r != null) recentReports.add(r);
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao carregar lag_reports.json: {}", e.getMessage());
        }
    }

    /**
     * Registra um novo relato de lag vindo de um jogador.
     */
    public synchronized LagReport recordReport(ServerPlayerEntity player, String rawMessage, ServerTelemetry telemetry, NetworkHealthMonitor network) {
        String name = player.getGameProfile().getName();
        String uuid = player.getUuidAsString();
        String dim = player.getServerWorld().getRegistryKey().getValue().toString();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        double tps = telemetry != null ? telemetry.getCurrentTps() : 20.0;
        double meanMspt = telemetry != null ? telemetry.getCurrentMeanMspt() : 10.0;
        double maxMspt = telemetry != null ? telemetry.getCurrentMaxMspt() : 20.0;
        long ramUsed = telemetry != null ? telemetry.getCurrentUsedRamMb() : 0;
        double ramPercent = telemetry != null ? telemetry.getCurrentRamPercent() : 0.0;

        int netPing = 0;
        double netLoss = 0.0;
        boolean netOutage = false;
        if (network != null) {
            var stats = network.get5MinStats();
            netPing = stats.currentPingMs;
            netLoss = stats.packetLossPercent;
            netOutage = stats.outagesCount > 0 || !stats.isOnline;
        }

        LagReport report = new LagReport(name, uuid, rawMessage, dim, x, y, z,
                tps, meanMspt, maxMspt, ramUsed, ramPercent, netPing, netLoss, netOutage);

        recentReports.add(report);
        if (recentReports.size() > 500) {
            recentReports.remove(0); // mantém histórico dos últimos 500 reports
        }

        saveReportsAsync(report);
        return report;
    }

    private void saveReportsAsync(LagReport newReport) {
        new Thread(() -> {
            try {
                // 1. Salva em lag_reports.json
                Files.createDirectories(reportsJsonFile.getParent());
                String json = GSON.toJson(recentReports);
                Files.writeString(reportsJsonFile, json, StandardCharsets.UTF_8);

                // 2. Anexa linha human-readable em lag_reports.log
                String logLine = String.format(Locale.ROOT,
                        "[%s] PLAYER: %s | POS: (%.1f, %.1f, %.1f, %s) | METRICAS: TPS=%.1f, MSPT=%.1fms (Max=%.1fms), RAM=%dMB (%.1f%%) | NET: Ping=%dms, Perda=%.1f%%%s | MENSAGEM: \"%s\"%n",
                        newReport.timestamp, newReport.playerName, newReport.x, newReport.y, newReport.z,
                        newReport.dimension, newReport.tps, newReport.meanMspt, newReport.maxMspt,
                        newReport.ramUsedMb, newReport.ramPercent, newReport.netPingMs, newReport.netPacketLossPercent,
                        (newReport.netOutageRecent ? " (QUEDA DETECTADA)" : ""), newReport.rawMessage);

                Files.writeString(reportsLogFile, logLine, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);

                CumminsMod.LOG.info("[Cummins-Report] Relato de lag de {} gravado com sucesso no histórico!", newReport.playerName);
            } catch (IOException e) {
                CumminsMod.LOG.error("[Cummins-Report] Erro ao gravar histórico de lag: {}", e.getMessage());
            }
        }, "LagReportWriter").start();
    }

    public synchronized List<LagReport> getRecentReports() {
        return Collections.unmodifiableList(new ArrayList<>(recentReports));
    }

    public synchronized int getReportCount() {
        return recentReports.size();
    }
}
