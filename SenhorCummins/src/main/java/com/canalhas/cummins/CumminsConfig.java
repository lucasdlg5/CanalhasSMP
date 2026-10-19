package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do Senhor Cummins. Salva em config/senhor-cummins/config.json.
 * Campos novos são adicionados automaticamente ao arquivo ao carregar.
 */
public class CumminsConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public boolean enabled = true;
    public String botName = "Senhor Cummins da Silva";
    /** Inicia o 'spark profiler start' ao subir e 'spark profiler stop' ao dar stop no servidor. */
    public boolean autoSparkProfiler = true;
    /** Cor do nome no chat (nomes do Minecraft: gold, white, yellow, aqua, red...). */
    public String nameColor = "gold";
    public Api api = new Api();
    /** Palavras que fazem ele responder com certeza (comparação sem maiúsculas). */
    public List<String> triggerWords = new ArrayList<>(List.of(
            "cummins", "cumins", "cummings", "cumings", "cumming", "cuming", "cummin", "cumin", "cummis", "cumis", "cummim", "cumim",
            "seu cummins", "sr cummins", "senhor cummins", "tio cummins",
            "seu cummings", "sr cummings", "senhor cummings", "tio cummings",
            "seu cumming", "sr cumming", "senhor cumming", "tio cumming",
            "seu cumings", "sr cumings", "senhor cumings", "tio cumings",
            "seu cuming", "sr cuming", "senhor cuming", "tio cuming",
            "seu cummin", "sr cummin", "senhor cummin", "tio cummin"
    ));
    public Chances chances = new Chances();
    public Timing timing = new Timing();
    /** Contexto fixo sobre o servidor, entregue à LLM em todo prompt. */
    public String serverLore = "Servidor de Minecraft 1.20.1 chamado CanalhasSMP, modpack Prominence II: Hasturian Era "
            + "(RPG de fantasia sombria, chefões, dragões, magias, classes, Create, Tech Reborn, dimensões espaciais). "
            + "É um grupo de amigos brasileiros que joga junto.";
    /** Instruções extras de personalidade (opcional), ex: apelidos fixos de jogadores. */
    public String extraPersonality = "";
    /** Mensagens de sistema contendo esses textos são ignoradas no contexto (ruído). */
    public List<String> ignoreSystemMessagesContaining = new ArrayList<>(List.of("Textile Backup", "backup"));
    public Discord discord = new Discord();
    public Body body = new Body();
    public Teleport teleport = new Teleport();
    public NetworkMonitor networkMonitor = new NetworkMonitor();
    public TokenTracking tokenTracking = new TokenTracking();
    /** Se ligado, a cachorrinha Vitória também conversa no chat com a mesma inteligência do Cummins. */
    public boolean vitoriaAiEnabled = false;
    public boolean debugLog = false;

    public static class TokenTracking {
        public boolean enabled = true;
        /** Tipo de reset: "daily" (reseta à meia-noite), "weekly" (semanal), ou intervalo tipo "5h", "12h" */
        public String resetCycle = "daily";
        /** Limite máximo de tokens por ciclo (padrão: 1.000.000, teto gratuito diário do Gemini Flash) */
        public long tokenLimit = 1_000_000L;
    }

    public static class NetworkMonitor {
        public boolean enabled = true;
        public String pingTarget = "8.8.8.8";
        public int historyMinutes = 5;
        public int dropThresholdSeconds = 2;
        public boolean announceOutagesInChat = true;
    }

    public static class Teleport {
        public boolean enabled = true;
        public int cooldownMinutes = 60;
    }

    public static class Body {
        public boolean enabled = true;
        public String variant = "jellie";
        public String collarColor = "gold";
        public int teleportDistanceBlocks = 25;
        public int followDistanceBlocks = 5;
        public int checkIntervalSeconds = 3;
        public double proximityCommentChance = 0.08;
    }

    public static class Api {
        /** Qualquer endpoint compatível com OpenAI (Gemini, OpenAI, Groq, OpenRouter, Ollama...). */
        public String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
        public String apiKey = "";
        public String model = "gemini-3.5-flash";
        public List<String> fallbackModels = new ArrayList<>(List.of(
                "gemini-flash-lite-latest",
                "gemini-flash-latest",
                "gemini-pro-latest"
        ));
        public double temperature = 0.82;
        public int maxTokens = 400;
        public int memoryMaxTokens = 1200;
        public int timeoutSeconds = 40;
        /** Campos extras mesclados no corpo da requisição (ex: {"reasoning_effort":"none"} para Gemini 2.5 Flash). */
        public JsonObject extraBody = defaultExtraBody();

        private static JsonObject defaultExtraBody() {
            JsonObject o = new JsonObject();
            o.addProperty("reasoning_effort", "none");
            return o;
        }
    }

    public static class Chances {
        /** Quando citam o nome dele. */
        public double mention = 1.0;
        /** Quando o jogador está no meio de uma conversa com ele. */
        public double conversation = 0.42;
        /** Mensagem qualquer quando só tem 1 jogador online. */
        public double solo = 0.20;
        /** Mensagem qualquer com 2+ jogadores online (mínimo para não interromper conversa entre amigos). */
        public double group = 0.05;
        public double death = 0.70;
        public double join = 0.30;
        public double advancement = 0.25;
        public double idleSolo = 0.25;
        public double idleGroup = 0.15;
    }

    public static class Timing {
        public int minSecondsBetweenReplies = 10;
        public int conversationWindowSeconds = 45;
        public int idleMinutesSolo = 25;
        public int idleMinutesGroup = 45;
        public double typingBaseSeconds = 1.5;
        public double typingSecondsPerChar = 0.04;
        public double typingMaxSeconds = 7.0;
        public int contextSize = 18;
        public int memoryUpdateEveryEvents = 30;
        public int maxMemoryNotes = 40;
    }

    public static class Discord {
        public boolean enabled = false;
        /** Token do bot (Discord Developer Portal -> Bot -> Reset Token). */
        public String botToken = "";
        /** ID fixo do canal #geral do Discord (CanalhasSMP). */
        public static final String GERAL_CHANNEL_ID = "508475539665715228";
        /** IDs dos canais a serem lidos com frequência (canal #geral como padrão). */
        public List<String> channelIds = new ArrayList<>(List.of(GERAL_CHANNEL_ID));
        /** ID do canal #pérolas de onde ele sincroniza uma vez ao dia. */
        public String perolasChannelId = "988152602237730866";
        public int pollSeconds = 15;
        public int contextSize = 15;
        /** (Opcional) ID de um canal onde o Cummins também posta o que ele fala no jogo. Vazio = desligado. */
        public String relayChannelId = "";
    }

    public static CumminsConfig load(Path file) {
        CumminsConfig cfg = null;
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                cfg = GSON.fromJson(r, CumminsConfig.class);
            } catch (Exception e) {
                CumminsMod.LOG.error("[Cummins] config.json inválido, usando padrão: {}", e.getMessage());
            }
        }
        if (cfg == null) cfg = new CumminsConfig();
        if (cfg.api == null) cfg.api = new Api();
        if (cfg.api.maxTokens < 350) cfg.api.maxTokens = 450;
        if (cfg.api.extraBody == null) cfg.api.extraBody = new JsonObject();
        if (cfg.chances == null) cfg.chances = new Chances();
        if (cfg.chances.conversation > 0.60) cfg.chances.conversation = 0.42;
        if (cfg.timing == null) cfg.timing = new Timing();
        if (cfg.timing.contextSize > 22) cfg.timing.contextSize = 18;
        if (cfg.timing.conversationWindowSeconds > 50) cfg.timing.conversationWindowSeconds = 45;
        if (cfg.timing.minSecondsBetweenReplies < 10) cfg.timing.minSecondsBetweenReplies = 10;
        if (cfg.discord == null) cfg.discord = new Discord();
        if (cfg.discord.contextSize > 20) cfg.discord.contextSize = 15;
        if (cfg.discord.channelIds == null) cfg.discord.channelIds = new ArrayList<>();
        if (!cfg.discord.channelIds.contains(Discord.GERAL_CHANNEL_ID)) {
            cfg.discord.channelIds.add(0, Discord.GERAL_CHANNEL_ID);
        }
        if (cfg.discord.pollSeconds > 15 || cfg.discord.pollSeconds <= 0) {
            cfg.discord.pollSeconds = 15;
        }
        if (cfg.body == null) cfg.body = new Body();
        if (cfg.teleport == null) cfg.teleport = new Teleport();
        if (cfg.triggerWords == null) cfg.triggerWords = new ArrayList<>();
        List<String> defaultTriggers = List.of(
                "cummins", "cumins", "cummings", "cumings", "cumming", "cuming", "cummin", "cumin", "cummis", "cumis", "cummim", "cumim",
                "seu cummins", "sr cummins", "senhor cummins", "tio cummins",
                "seu cummings", "sr cummings", "senhor cummings", "tio cummings",
                "seu cumming", "sr cumming", "senhor cumming", "tio cumming",
                "seu cumings", "sr cumings", "senhor cumings", "tio cumings",
                "seu cuming", "sr cuming", "senhor cuming", "tio cuming",
                "seu cummin", "sr cummin", "senhor cummin", "tio cummin"
        );
        for (String dt : defaultTriggers) {
            if (!cfg.triggerWords.contains(dt)) {
                cfg.triggerWords.add(dt);
            }
        }
        if (cfg.ignoreSystemMessagesContaining == null) cfg.ignoreSystemMessagesContaining = new ArrayList<>();
        if (cfg.tokenTracking == null) cfg.tokenTracking = new TokenTracking();

        // Limpeza de regras duplicadas em extraPersonality mantendo notas de perfis/apelidos
        if (cfg.extraPersonality != null && !cfg.extraPersonality.isBlank()) {
            String lowerEp = cfg.extraPersonality.toLowerCase(java.util.Locale.ROOT);
            if (cfg.extraPersonality.length() > 350 || lowerEp.contains("professoral") || lowerEp.contains("parceirinho") || lowerEp.contains("tmj") || lowerEp.contains("diretrizes fundamentais")) {
                int idx = cfg.extraPersonality.indexOf("PERFIS E PIADAS INTERNAS");
                if (idx >= 0) {
                    cfg.extraPersonality = cfg.extraPersonality.substring(idx).trim();
                } else {
                    cfg.extraPersonality = "";
                }
            }
        }

        cfg.save(file);
        return cfg;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            CumminsMod.LOG.error("[Cummins] Falha ao salvar config: {}", e.getMessage());
        }
    }

    public boolean hasApiKey() {
        return api.apiKey != null && !api.apiKey.isBlank() && !api.apiKey.equals("COLE_SUA_CHAVE_AQUI");
    }
}
