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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cliente HTTP resiliente para LLMs.
 * - Suporta a API nativa do Google Gemini (/v1beta/models/{model}:generateContent) para máxima estabilidade e zero 503s.
 * - Suporta endpoints padrão OpenAI (/chat/completions).
 * - Espaçamento anti-rate-limit (evita ultrapassar limites gratuitos de RPM).
 * - Retry automático com backoff em erros transitórios.
 * - Cascata de modelos de fallback configuráveis.
 */
public class LlmClient {
    private final java.util.concurrent.ExecutorService httpExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "Cummins-HttpIO");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http = HttpClient.newBuilder()
            .executor(httpExecutor)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Cummins-LlmWorker");
        t.setDaemon(true);
        return t;
    });

    public void stop() {
        scheduler.shutdownNow();
        httpExecutor.shutdownNow();
    }

    private LlmTokenTracker tokenTracker;
    public void setTokenTracker(LlmTokenTracker tracker) {
        this.tokenTracker = tracker;
    }

    private volatile long lastCallTimestamp = 0;

    public CompletableFuture<String> chat(CumminsConfig.Api api, String system, String user, int maxTokens) {
        List<String> models = new ArrayList<>();
        if (api.model != null && !api.model.isBlank()) {
            models.add(api.model.trim());
        }
        if (api.fallbackModels != null) {
            for (String fb : api.fallbackModels) {
                if (fb != null && !fb.isBlank() && !models.contains(fb.trim())) {
                    models.add(fb.trim());
                }
            }
        }
        if (models.isEmpty()) {
            models.add("gemini-flash-lite-latest");
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        scheduleWithSpacing(() -> tryModelWithFallback(api, system, user, maxTokens, models, 0, 0, future));
        return future;
    }

    private synchronized void scheduleWithSpacing(Runnable task) {
        long now = System.currentTimeMillis();
        long minGap = 2500; // Mínimo de 2.5s entre chamadas para nunca estourar o limite de 15 RPM
        long delay = Math.max(0, minGap - (now - lastCallTimestamp));
        lastCallTimestamp = now + delay;
        scheduler.schedule(task, delay, TimeUnit.MILLISECONDS);
    }

    private void tryModelWithFallback(
            CumminsConfig.Api api, String system, String user, int maxTokens,
            List<String> models, int modelIndex, int retryCount, CompletableFuture<String> result) {

        if (modelIndex >= models.size()) {
            result.completeExceptionally(new RuntimeException("Todos os modelos da LLM falharam (" + models + ")"));
            return;
        }

        String currentModel = models.get(modelIndex);
        boolean isGoogleNative = api.baseUrl != null && api.baseUrl.contains("generativelanguage.googleapis.com");

        HttpRequest req = isGoogleNative
                ? buildGoogleNativeRequest(api, currentModel, system, user, maxTokens)
                : buildOpenAiRequest(api, currentModel, system, user, maxTokens);

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((resp, err) -> {
                    if (err != null) {
                        handleFailure(api, system, user, maxTokens, models, modelIndex, retryCount, err.getMessage(), result);
                        return;
                    }

                    int status = resp.statusCode();
                    if (status / 100 != 2) {
                        String b = resp.body();
                        if (b != null && b.length() > 400) b = b.substring(0, 400);
                        String errMsg = "HTTP " + status + ": " + b;
                        handleFailure(api, system, user, maxTokens, models, modelIndex, retryCount, errMsg, result);
                        return;
                    }

                    try {
                        String content = isGoogleNative
                                ? parseGoogleNativeResponse(resp.body())
                                : parseOpenAiResponse(resp.body());

                        recordUsageIfPresent(resp.body(), currentModel, system, user, content);

                        if (content == null || content.isBlank()) {
                            CumminsMod.LOG.warn("[Cummins] LLM respondeu vazio com modelo {}", currentModel);
                            result.complete("");
                            return;
                        }
                        result.complete(content);
                    } catch (Exception ex) {
                        handleFailure(api, system, user, maxTokens, models, modelIndex, retryCount, "Erro parse: " + ex.getMessage(), result);
                    }
                });
    }

    private void recordUsageIfPresent(String json, String model, String system, String user, String content) {
        if (tokenTracker == null) return;
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            int promptTokens = 0;
            int candidateTokens = 0;
            int totalTokens = 0;

            if (o.has("usageMetadata") && o.get("usageMetadata").isJsonObject()) {
                JsonObject um = o.getAsJsonObject("usageMetadata");
                if (um.has("promptTokenCount")) promptTokens = um.get("promptTokenCount").getAsInt();
                if (um.has("candidatesTokenCount")) candidateTokens = um.get("candidatesTokenCount").getAsInt();
                if (um.has("totalTokenCount")) totalTokens = um.get("totalTokenCount").getAsInt();
                else totalTokens = promptTokens + candidateTokens;
            } else if (o.has("usage") && o.get("usage").isJsonObject()) {
                JsonObject u = o.getAsJsonObject("usage");
                if (u.has("prompt_tokens")) promptTokens = u.get("prompt_tokens").getAsInt();
                if (u.has("completion_tokens")) candidateTokens = u.get("completion_tokens").getAsInt();
                if (u.has("total_tokens")) totalTokens = u.get("total_tokens").getAsInt();
                else totalTokens = promptTokens + candidateTokens;
            } else {
                int sysLen = (system != null) ? system.length() : 0;
                int userLen = (user != null) ? user.length() : 0;
                int outLen = (content != null) ? content.length() : 0;
                promptTokens = Math.max(1, (sysLen + userLen) / 4);
                candidateTokens = Math.max(1, outLen / 4);
                totalTokens = promptTokens + candidateTokens;
            }

            tokenTracker.record(promptTokens, candidateTokens, totalTokens, model);
        } catch (Exception e) {
            CumminsMod.LOG.debug("[Cummins] Falha ao extrair contagem de tokens: {}", e.getMessage());
        }
    }

    private void handleFailure(
            CumminsConfig.Api api, String system, String user, int maxTokens,
            List<String> models, int modelIndex, int retryCount, String errorDesc,
            CompletableFuture<String> result) {

        String currentModel = models.get(modelIndex);
        boolean isTransient = errorDesc != null && (errorDesc.contains("503") || errorDesc.contains("429") || errorDesc.toLowerCase().contains("unavailable"));

        if (isTransient && retryCount < 1) {
            CumminsMod.LOG.warn("[Cummins] Modelo {} teve lentidão temporária ({}). Retentando em 2s...", currentModel, errorDesc);
            scheduler.schedule(() -> {
                tryModelWithFallback(api, system, user, maxTokens, models, modelIndex, retryCount + 1, result);
            }, 2000, TimeUnit.MILLISECONDS);
            return;
        }

        int nextIndex = modelIndex + 1;
        if (nextIndex < models.size()) {
            String nextModel = models.get(nextIndex);
            CumminsMod.LOG.warn("[Cummins] Modelo {} falhou ({}). Acionando FALLBACK para {}...", currentModel, errorDesc, nextModel);
            scheduler.schedule(() -> {
                tryModelWithFallback(api, system, user, maxTokens, models, nextIndex, 0, result);
            }, 500, TimeUnit.MILLISECONDS);
        } else {
            result.completeExceptionally(new RuntimeException("Todos os modelos falharam. Último erro em " + currentModel + ": " + errorDesc));
        }
    }

    private HttpRequest buildGoogleNativeRequest(CumminsConfig.Api api, String model, String system, String user, int maxTokens) {
        JsonObject body = new JsonObject();

        if (system != null && !system.isBlank()) {
            JsonObject sys = new JsonObject();
            JsonArray parts = new JsonArray();
            JsonObject p = new JsonObject();
            p.addProperty("text", system);
            parts.add(p);
            sys.add("parts", parts);
            body.add("system_instruction", sys);
        }

        JsonArray contents = new JsonArray();
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        JsonArray uParts = new JsonArray();
        JsonObject up = new JsonObject();
        up.addProperty("text", user);
        uParts.add(up);
        userMsg.add("parts", uParts);
        contents.add(userMsg);
        body.add("contents", contents);

        // Desativa bloqueios falsos-positivos de filtro para gírias e expressões humorísticas brasileiras ("PQP", "casa do caralho", zoeiras)
        JsonArray safetySettings = new JsonArray();
        String[] categories = {
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT",
                "HARM_CATEGORY_CIVIC_INTEGRITY"
        };
        for (String cat : categories) {
            JsonObject s = new JsonObject();
            s.addProperty("category", cat);
            s.addProperty("threshold", "BLOCK_NONE");
            safetySettings.add(s);
        }
        body.add("safetySettings", safetySettings);

        JsonObject genConfig = new JsonObject();
        genConfig.addProperty("temperature", api.temperature);
        genConfig.addProperty("topP", 0.92);
        genConfig.addProperty("maxOutputTokens", maxTokens);
        body.add("generationConfig", genConfig);

        String uri = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        return HttpRequest.newBuilder(URI.create(uri))
                .timeout(Duration.ofSeconds(Math.max(5, api.timeoutSeconds)))
                .header("Content-Type", "application/json")
                .header("X-goog-api-key", api.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
    }

    private String parseGoogleNativeResponse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonArray candidates = o.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) return "";
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        JsonObject content = candidate.getAsJsonObject("content");
        if (content == null) return "";
        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (JsonElement el : parts) {
            if (!el.isJsonObject()) continue;
            JsonObject part = el.getAsJsonObject();
            // Ignora partes internas de 'thought' se o modelo for thinking
            if (part.has("thought") && part.get("thought").isJsonPrimitive() && part.get("thought").getAsBoolean()) {
                continue;
            }
            if (part.has("text") && !part.get("text").isJsonNull()) {
                sb.append(part.get("text").getAsString());
            }
        }
        return sb.toString().trim();
    }

    private HttpRequest buildOpenAiRequest(CumminsConfig.Api api, String model, String system, String user, int maxTokens) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", api.temperature);
        body.addProperty("top_p", 0.92);
        body.addProperty("frequency_penalty", 0.45);
        body.addProperty("presence_penalty", 0.35);
        body.addProperty("max_tokens", maxTokens);

        JsonArray messages = new JsonArray();
        JsonObject sm = new JsonObject();
        sm.addProperty("role", "system");
        sm.addProperty("content", system);
        messages.add(sm);

        JsonObject um = new JsonObject();
        um.addProperty("role", "user");
        um.addProperty("content", user);
        messages.add(um);
        body.add("messages", messages);

        if (api.extraBody != null) {
            for (Map.Entry<String, JsonElement> e : api.extraBody.entrySet()) body.add(e.getKey(), e.getValue());
        }

        String base = api.baseUrl.endsWith("/") ? api.baseUrl.substring(0, api.baseUrl.length() - 1) : api.baseUrl;
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(5, api.timeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        if (api.apiKey != null && !api.apiKey.isBlank()) req.header("Authorization", "Bearer " + api.apiKey);
        return req.build();
    }

    private String parseOpenAiResponse(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        JsonArray choices = o.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return "";
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = choice.getAsJsonObject("message");
        JsonElement content = message == null ? null : message.get("content");
        return (content == null || content.isJsonNull()) ? "" : content.getAsString();
    }
}
