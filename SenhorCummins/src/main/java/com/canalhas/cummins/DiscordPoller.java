package com.canalhas.cummins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lê mensagens de canais do Discord via REST (sem gateway/websocket), usando um bot token.
 * As mensagens entram como contexto para o Cummins. Requer "Message Content Intent" ligado no bot.
 */
public class DiscordPoller {
    private static final String API = "https://discord.com/api/v10";

    private final CumminsMod mod;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Map<String, String> lastIds = new HashMap<>();
    private final Map<String, String> channelNames = new HashMap<>();
    private final List<String> perolasCache = new CopyOnWriteArrayList<>();
    private long lastPerolasPollAt = 0;
    private static final long PEROLAS_SYNC_INTERVAL_MS = 24L * 60 * 60 * 1000L; // Sincroniza 1 vez ao dia (24 horas)
    private ScheduledExecutorService scheduler;
    private volatile String status = "desligado";
    private boolean warnedEmptyContent = false;

    public DiscordPoller(CumminsMod mod) {
        this.mod = mod;
    }

    public String status() {
        String base = status;
        if (!perolasCache.isEmpty()) {
            return base + " (" + perolasCache.size() + " pérolas)";
        }
        return base;
    }

    public boolean hasPerolas() {
        return !perolasCache.isEmpty();
    }

    public String getRandomPerola() {
        if (perolasCache.isEmpty()) return null;
        int idx = (int) (Math.random() * perolasCache.size());
        return perolasCache.get(idx);
    }

    public List<String> getPerolas() {
        return Collections.unmodifiableList(perolasCache);
    }

    private CumminsConfig.Discord cfg() { return mod.config().discord; }

    private boolean configured() {
        return cfg().enabled && cfg().botToken != null && !cfg().botToken.isBlank();
    }

    public synchronized void start() {
        stop();
        if (!configured()) {
            status = "desligado";
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SenhorCummins-Discord");
            t.setDaemon(true);
            return t;
        });
        boolean hasChannels = !cfg().channelIds.isEmpty();
        boolean hasPerolasChannel = cfg().perolasChannelId != null && !cfg().perolasChannelId.isBlank();
        if (hasChannels || hasPerolasChannel) {
            int interval = Math.max(5, cfg().pollSeconds);
            scheduler.scheduleWithFixedDelay(this::pollAll, 2, interval, TimeUnit.SECONDS);
            // Sincroniza pérolas logo na inicialização (após 5s) e depois apenas 1x ao dia
            if (hasPerolasChannel) {
                scheduler.schedule(this::pollPerolas, 5, TimeUnit.SECONDS);
            }
            status = "lendo " + cfg().channelIds.size() + " canal(is)";
        } else {
            status = "sem canais configurados";
        }
        CumminsMod.LOG.info("[Cummins] Discord: {}", status);
    }

    public synchronized void stop() {
        if (scheduler != null) scheduler.shutdownNow();
        scheduler = null;
    }

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder(URI.create(API + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bot " + cfg().botToken)
                .header("User-Agent", "DiscordBot (https://github.com/canalhas/senhor-cummins, 1.0)");
    }

    private void pollAll() {
        for (String channelId : new ArrayList<>(cfg().channelIds)) {
            try {
                poll(channelId.trim());
            } catch (Exception e) {
                status = "erro: " + e.getMessage();
                CumminsMod.LOG.warn("[Cummins] Discord erro no canal {}: {}", channelId, e.getMessage());
            }
        }
        // Sincronização do canal #pérolas reduzida para UMA VEZ AO DIA (24 horas)
        long now = System.currentTimeMillis();
        boolean hasPerolasChannel = cfg().perolasChannelId != null && !cfg().perolasChannelId.isBlank();
        if (hasPerolasChannel && (now - lastPerolasPollAt >= PEROLAS_SYNC_INTERVAL_MS)) {
            pollPerolas();
        }
    }

    public void pollPerolas() {
        if (!configured()) return;
        lastPerolasPollAt = System.currentTimeMillis();
        String chId = cfg().perolasChannelId;
        if (chId == null || chId.isBlank()) return;
        try {
            HttpResponse<String> r = http.send(req("/channels/" + chId.trim() + "/messages?limit=100").GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
                List<String> list = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject m = el.getAsJsonObject();
                    String content = m.has("content") ? m.get("content").getAsString().trim() : "";
                    content = content.replaceAll("https?://\\S+", "").trim();
                    if (!content.isEmpty() && content.length() >= 3) {
                        list.add(content);
                    }
                }
                if (!list.isEmpty()) {
                    perolasCache.clear();
                    perolasCache.addAll(list);
                    CumminsMod.LOG.info("[Cummins] {} pérolas carregadas do canal #pérolas!", perolasCache.size());
                }
            } else {
                CumminsMod.LOG.warn("[Cummins] HTTP {} ao buscar pérolas no canal {}", r.statusCode(), chId);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao buscar pérolas: {}", e.getMessage());
        }
    }

    private void poll(String channelId) throws Exception {
        if (channelId.isEmpty()) return;
        if (!channelNames.containsKey(channelId)) {
            HttpResponse<String> r = http.send(req("/channels/" + channelId).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                channelNames.put(channelId, o.has("name") ? o.get("name").getAsString() : channelId);
            } else {
                throw new RuntimeException("HTTP " + r.statusCode() + " ao abrir canal (o bot está no servidor e tem permissão de ver o canal?)");
            }
        }

        String after = lastIds.get(channelId);
        String path = "/channels/" + channelId + "/messages?limit=" + (after == null ? 15 : 50) + (after == null ? "" : "&after=" + after);
        HttpResponse<String> r = http.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 429) return; // rate limit, tenta no próximo ciclo
        if (r.statusCode() != 200) throw new RuntimeException("HTTP " + r.statusCode() + ": " + r.body());

        JsonArray arr = JsonParser.parseString(r.body()).getAsJsonArray();
        if (arr.isEmpty()) return;
        // Discord retorna do mais novo pro mais antigo
        lastIds.put(channelId, arr.get(0).getAsJsonObject().get("id").getAsString());
        String chName = channelNames.getOrDefault(channelId, channelId);
        List<JsonObject> msgs = new ArrayList<>();
        for (JsonElement e : arr) msgs.add(0, e.getAsJsonObject());

        for (JsonObject m : msgs) {
            JsonObject author = m.getAsJsonObject("author");
            if (author == null || (author.has("bot") && author.get("bot").getAsBoolean())) continue;
            String name = author.has("global_name") && !author.get("global_name").isJsonNull()
                    ? author.get("global_name").getAsString()
                    : author.get("username").getAsString();
            String content = m.has("content") ? m.get("content").getAsString().trim() : "";
            if (content.isEmpty()) {
                boolean hasAttach = m.has("attachments") && !m.getAsJsonArray("attachments").isEmpty();
                if (hasAttach) content = "[mandou imagem/arquivo]";
                else {
                    if (!warnedEmptyContent) {
                        warnedEmptyContent = true;
                        CumminsMod.LOG.warn("[Cummins] Mensagens do Discord chegando vazias: ative 'Message Content Intent' no Developer Portal (Bot -> Privileged Gateway Intents).");
                    }
                    continue;
                }
            }
            if (content.length() > 300) content = content.substring(0, 300) + "...";
            mod.context().addDiscord("#" + chName + " " + name + ": " + content, cfg().contextSize);
        }
        status = "ok, lendo " + cfg().channelIds.size() + " canal(is)";
    }

    /** Posta uma fala do Cummins no canal de relay (se configurado). Não bloqueia. */
    public void relayToDiscord(String line) {
        if (!configured() || cfg().relayChannelId == null || cfg().relayChannelId.isBlank()) return;
        JsonObject body = new JsonObject();
        body.addProperty("content", "**" + mod.config().botName + ":** " + line);
        JsonObject allowed = new JsonObject();
        allowed.add("parse", new JsonArray()); // não pinga ninguém
        body.add("allowed_mentions", allowed);
        HttpRequest request = req("/channels/" + cfg().relayChannelId.trim() + "/messages")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((r, e) -> {
            if (e != null) CumminsMod.LOG.warn("[Cummins] Falha no relay pro Discord: {}", e.getMessage());
            else if (r.statusCode() / 100 != 2) CumminsMod.LOG.warn("[Cummins] Relay Discord HTTP {}: {}", r.statusCode(), r.body());
        });
    }

    public void sendToChannel(String channelId, String content) {
        if (!configured() || channelId == null || channelId.isBlank()) return;
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        JsonObject allowed = new JsonObject();
        allowed.add("parse", new JsonArray());
        body.add("allowed_mentions", allowed);
        HttpRequest request = req("/channels/" + channelId.trim() + "/messages")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((r, e) -> {
            if (e != null) CumminsMod.LOG.warn("[Cummins] Falha ao enviar pro Discord: {}", e.getMessage());
            else if (r.statusCode() / 100 != 2) CumminsMod.LOG.warn("[Cummins] Discord HTTP {}: {}", r.statusCode(), r.body());
        });
    }

    /**
     * Boletim de Fim de Turno no Discord:
     * Posta um resumo alegre e descontraído do dia no canal do Discord.
     */
    public void postShiftReport(net.minecraft.server.MinecraftServer server) {
        if (!configured()) return;
        String channelId = cfg().relayChannelId != null && !cfg().relayChannelId.isBlank()
                ? cfg().relayChannelId.trim()
                : (!cfg().channelIds.isEmpty() ? cfg().channelIds.get(0).trim() : null);
        if (channelId == null) return;

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        String topPlayer = "ArzanLion";
        if (mod.playtime() != null && server != null) {
            var ranking = mod.playtime().getRankingSnapshot(server);
            if (!ranking.isEmpty()) {
                topPlayer = ranking.keySet().iterator().next();
            }
        }

        String tpsStr = "20.0";
        if (mod.telemetry() != null) {
            tpsStr = String.format(java.util.Locale.US, "%.1f", mod.telemetry().getCurrentTps());
        }

        final String finalTopPlayer = topPlayer;
        final String finalTpsStr = tpsStr;

        if (mod.dailyHighlights() != null) {
            mod.dailyHighlights().generateHighlightsAsync(server).thenAccept(highlights -> {
                String report = "🌙 **[BOLETIM DE FIM DE TURNO • CANALHAS SMP]**\n"
                        + "📅 **Data:** " + today + " • **Horário:** " + time + " (Brasília)\n"
                        + "🏆 **Operário Destaque:** `" + finalTopPlayer + "` liderando com mais horas de suor na firma!\n"
                        + "⚡ **Status da Fábrica:** Servidor rodando suave a `" + finalTpsStr + " TPS`.\n\n"
                        + "✨ **Destaques do Dia dos Operários:**\n"
                        + highlights + "\n\n"
                        + "🐈 **Palavra do Tio Cummins:** *\"Portas da firma fechadas por hoje! Bom descanso a todos os sócios e colaboradores. Amanhã tem mais serviço na fábrica. Tmj!\"* 🐾💤";

                sendToChannel(channelId, report);
                CumminsMod.LOG.info("[Cummins] Boletim de fim de turno com destaques dos operários postado com sucesso no Discord!");
            });
        } else {
            String report = "🌙 **[BOLETIM DE FIM DE TURNO • CANALHAS SMP]**\n"
                    + "📅 **Data:** " + today + " • **Horário:** " + time + " (Brasília)\n"
                    + "🏆 **Operário Destaque:** `" + topPlayer + "` liderando com mais horas de suor na firma!\n"
                    + "⚡ **Status da Fábrica:** Servidor rodando suave a `" + tpsStr + " TPS`.\n\n"
                    + "🐈 **Palavra do Tio Cummins:** *\"Portas da firma fechadas por hoje! Bom descanso a todos os sócios e colaboradores. Amanhã tem mais serviço na fábrica. Tmj!\"* 🐾💤";

            sendToChannel(channelId, report);
            CumminsMod.LOG.info("[Cummins] Boletim de fim de turno postado com sucesso no Discord!");
        }
    }
}
