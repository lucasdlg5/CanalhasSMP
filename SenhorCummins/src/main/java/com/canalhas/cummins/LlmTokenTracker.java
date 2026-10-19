package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.Locale;

/**
 * Rastreador persistente de consumo de tokens LLM (Gemini / OpenAI).
 * Suporta ciclos de renovação configuráveis:
 * - "daily" (reseta à meia-noite 00:00 local)
 * - "weekly" (reseta todo domingo à meia-noite)
 * - Intervalo personalizado em horas (ex: "5h", "12h")
 * 
 * Persiste os dados em config/senhor-cummins/token_usage.json e exporta
 * em tempo real para o server_telemetry.json lido pelo Dashboard.
 */
public class LlmTokenTracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final CumminsMod mod;
    private final Path dataFile;

    private String resetCycle = "daily";
    private long limit = 1_000_000L;

    private long cycleStartEpochMs = 0;
    private long nextResetEpochMs = 0;
    private long usedTokens = 0;
    private long promptTokens = 0;
    private long candidateTokens = 0;
    private int requestCount = 0;
    private String lastModel = "gemini-flash-lite-latest";
    private long lastUpdatedEpochMs = 0;
    private JsonArray recentCycles = new JsonArray();

    public LlmTokenTracker(CumminsMod mod, Path dir) {
        this.mod = mod;
        this.dataFile = dir.resolve("token_usage.json");
        load();
    }

    public synchronized void record(int pTokens, int cTokens, int total, String model) {
        checkCycleReset();

        int effectiveTotal = total > 0 ? total : (pTokens + cTokens);
        this.usedTokens += effectiveTotal;
        this.promptTokens += Math.max(0, pTokens);
        this.candidateTokens += Math.max(0, cTokens);
        this.requestCount++;
        if (model != null && !model.isBlank()) {
            this.lastModel = model;
        }
        this.lastUpdatedEpochMs = System.currentTimeMillis();

        save();
    }

    public synchronized void checkCycleReset() {
        long now = System.currentTimeMillis();
        syncConfig();

        if (cycleStartEpochMs <= 0 || nextResetEpochMs <= 0) {
            initNewCycle(now);
            save();
            return;
        }

        if (now >= nextResetEpochMs) {
            // Arquiva resumo do ciclo anterior no histórico
            archiveCurrentCycle();

            // Inicia novo ciclo limpo
            initNewCycle(now);
            CumminsMod.LOG.info("[Cummins] Ciclo de tokens renovado! Próximo reset agendado para: {}", 
                    Instant.ofEpochMilli(nextResetEpochMs).atZone(ZoneId.systemDefault()));
            save();
        }
    }

    private void syncConfig() {
        if (mod != null && mod.config() != null && mod.config().tokenTracking != null) {
            var cfg = mod.config().tokenTracking;
            if (cfg.tokenLimit > 0) {
                this.limit = cfg.tokenLimit;
            }
            if (cfg.resetCycle != null && !cfg.resetCycle.isBlank() && !cfg.resetCycle.equalsIgnoreCase(this.resetCycle)) {
                this.resetCycle = cfg.resetCycle.trim().toLowerCase(Locale.ROOT);
                // Recalcula o próximo reset para o novo tipo de ciclo
                this.nextResetEpochMs = computeNextReset(this.cycleStartEpochMs > 0 ? this.cycleStartEpochMs : System.currentTimeMillis(), this.resetCycle);
            }
        }
    }

    private void initNewCycle(long now) {
        this.cycleStartEpochMs = now;
        this.nextResetEpochMs = computeNextReset(now, this.resetCycle);
        this.usedTokens = 0;
        this.promptTokens = 0;
        this.candidateTokens = 0;
        this.requestCount = 0;
        this.lastUpdatedEpochMs = now;
    }

    private void archiveCurrentCycle() {
        JsonObject entry = new JsonObject();
        entry.addProperty("startEpochMs", this.cycleStartEpochMs);
        entry.addProperty("endEpochMs", this.nextResetEpochMs);
        entry.addProperty("cycleType", this.resetCycle);
        entry.addProperty("usedTokens", this.usedTokens);
        entry.addProperty("promptTokens", this.promptTokens);
        entry.addProperty("candidateTokens", this.candidateTokens);
        entry.addProperty("requestCount", this.requestCount);
        entry.addProperty("limit", this.limit);

        recentCycles.add(entry);
        while (recentCycles.size() > 14) { // Guarda os últimos 14 ciclos
            recentCycles.remove(0);
        }
    }

    private long computeNextReset(long baseEpochMs, String cycle) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime baseTime = Instant.ofEpochMilli(baseEpochMs).atZone(zone);

        if (cycle == null) cycle = "daily";
        String c = cycle.trim().toLowerCase(Locale.ROOT);

        // Caso intervalo em horas: "5h", "12h", "1h"
        if (c.endsWith("h")) {
            try {
                int hours = Integer.parseInt(c.substring(0, c.length() - 1).trim());
                if (hours > 0) {
                    long intervalMs = hours * 3600_000L;
                    long now = System.currentTimeMillis();
                    long target = baseEpochMs + intervalMs;
                    while (target <= now) {
                        target += intervalMs;
                    }
                    return target;
                }
            } catch (Exception ignored) {}
        }

        // Caso semanal: próximo domingo às 00:00:00
        if (c.equals("weekly") || c.equals("semanal") || c.equals("7d")) {
            ZonedDateTime nextSunday = baseTime.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                    .toLocalDate().atStartOfDay(zone);
            long target = nextSunday.toInstant().toEpochMilli();
            if (target <= System.currentTimeMillis()) {
                target += 7 * 24 * 3600_000L;
            }
            return target;
        }

        // Padrão: diário (próxima meia-noite 00:00:00 local)
        ZonedDateTime nextMidnight = baseTime.toLocalDate().plusDays(1).atStartOfDay(zone);
        long target = nextMidnight.toInstant().toEpochMilli();
        if (target <= System.currentTimeMillis()) {
            target = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        }
        return target;
    }

    public synchronized String getResetRemainingFormatted() {
        checkCycleReset();
        long diffMs = Math.max(0, nextResetEpochMs - System.currentTimeMillis());
        long totalMinutes = diffMs / 60_000L;
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;

        if (hours > 0) {
            return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
        } else {
            return String.format(Locale.ROOT, "%dm", minutes);
        }
    }

    public synchronized String formatCycleDescription() {
        String c = resetCycle.toLowerCase(Locale.ROOT);
        if (c.endsWith("h")) {
            return "A cada " + c;
        }
        if (c.equals("weekly") || c.equals("semanal")) {
            return "Semanal (Domingo 00:00)";
        }
        return "Diário (00:00)";
    }

    public synchronized JsonObject getTelemetryJsonObject() {
        checkCycleReset();

        JsonObject obj = new JsonObject();
        obj.addProperty("used", usedTokens);
        obj.addProperty("limit", limit);
        long rem = Math.max(0, limit - usedTokens);
        obj.addProperty("remaining", rem);

        double pctUsed = limit > 0 ? Math.min(100.0, Math.round(((double) usedTokens / limit * 100.0) * 10.0) / 10.0) : 0.0;
        double pctRem = Math.max(0.0, Math.round((100.0 - pctUsed) * 10.0) / 10.0);
        obj.addProperty("percentUsed", pctUsed);
        obj.addProperty("percentRemaining", pctRem);

        obj.addProperty("promptTokens", promptTokens);
        obj.addProperty("candidateTokens", candidateTokens);
        obj.addProperty("requestCount", requestCount);
        obj.addProperty("resetCycle", formatCycleDescription());
        obj.addProperty("cycleStartEpochMs", cycleStartEpochMs);
        obj.addProperty("nextResetEpochMs", nextResetEpochMs);
        obj.addProperty("resetRemainingFormatted", getResetRemainingFormatted());
        obj.addProperty("lastModel", lastModel != null ? lastModel : "gemini-flash-lite-latest");
        obj.addProperty("lastUpdatedEpochMs", lastUpdatedEpochMs);

        return obj;
    }

    private synchronized void load() {
        if (!Files.exists(dataFile)) {
            initNewCycle(System.currentTimeMillis());
            save();
            return;
        }

        try (Reader r = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
            if (obj.has("resetCycle")) this.resetCycle = obj.get("resetCycle").getAsString();
            if (obj.has("limit")) this.limit = obj.get("limit").getAsLong();
            if (obj.has("cycleStartEpochMs")) this.cycleStartEpochMs = obj.get("cycleStartEpochMs").getAsLong();
            if (obj.has("nextResetEpochMs")) this.nextResetEpochMs = obj.get("nextResetEpochMs").getAsLong();
            if (obj.has("usedTokens")) this.usedTokens = obj.get("usedTokens").getAsLong();
            if (obj.has("promptTokens")) this.promptTokens = obj.get("promptTokens").getAsLong();
            if (obj.has("candidateTokens")) this.candidateTokens = obj.get("candidateTokens").getAsLong();
            if (obj.has("requestCount")) this.requestCount = obj.get("requestCount").getAsInt();
            if (obj.has("lastModel")) this.lastModel = obj.get("lastModel").getAsString();
            if (obj.has("lastUpdatedEpochMs")) this.lastUpdatedEpochMs = obj.get("lastUpdatedEpochMs").getAsLong();
            if (obj.has("recentCycles") && obj.get("recentCycles").isJsonArray()) {
                this.recentCycles = obj.getAsJsonArray("recentCycles");
            }
        } catch (Exception e) {
            CumminsMod.LOG.error("[Cummins] Erro ao carregar token_usage.json: {}", e.getMessage());
            initNewCycle(System.currentTimeMillis());
        }

        checkCycleReset();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(dataFile.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("resetCycle", resetCycle);
            root.addProperty("limit", limit);
            root.addProperty("cycleStartEpochMs", cycleStartEpochMs);
            root.addProperty("nextResetEpochMs", nextResetEpochMs);
            root.addProperty("usedTokens", usedTokens);
            root.addProperty("promptTokens", promptTokens);
            root.addProperty("candidateTokens", candidateTokens);
            root.addProperty("requestCount", requestCount);
            root.addProperty("lastModel", lastModel);
            root.addProperty("lastUpdatedEpochMs", lastUpdatedEpochMs);
            root.addProperty("resetRemainingFormatted", getResetRemainingFormatted());

            double pctUsed = limit > 0 ? Math.min(100.0, Math.round(((double) usedTokens / limit * 100.0) * 10.0) / 10.0) : 0.0;
            double pctRem = Math.max(0.0, Math.round((100.0 - pctUsed) * 10.0) / 10.0);
            long rem = Math.max(0, limit - usedTokens);
            root.addProperty("remaining", rem);
            root.addProperty("percentUsed", pctUsed);
            root.addProperty("percentRemaining", pctRem);
            root.add("recentCycles", recentCycles);

            try (Writer w = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.error("[Cummins] Falha ao salvar token_usage.json: {}", e.getMessage());
        }
    }
}
