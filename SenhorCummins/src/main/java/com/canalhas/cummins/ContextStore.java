package com.canalhas.cummins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Guarda o contexto recente (chat do jogo + Discord) e a memória de longo prazo
 * (piadas internas / fatos). Persistido em config/senhor-cummins/memoria.json.
 */
public class ContextStore {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    private final Path file;
    private final Deque<String> game = new ArrayDeque<>();
    private final Deque<String> discord = new ArrayDeque<>();
    private final List<String> notes = new ArrayList<>();
    private final PlayerRelationshipManager relationships = new PlayerRelationshipManager();
    private final java.util.Map<String, Long> teleportCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private int eventsSinceMemoryUpdate = 0;
    private String catUuid = null;
    private String vitoriaUuid = null;
    private String pipiraUuid = null;
    private String juninhoUuid = null;
    private String javidizineoUuid = null;
    private String jinqiuzineoUuid = null;

    public ContextStore(Path file) {
        this.file = file;
    }

    public synchronized String catUuid() { return catUuid; }
    public synchronized void setCatUuid(String uuid) { this.catUuid = uuid; }
    public synchronized String vitoriaUuid() { return vitoriaUuid; }
    public synchronized void setVitoriaUuid(String uuid) { this.vitoriaUuid = uuid; }
    public synchronized String pipiraUuid() { return pipiraUuid; }
    public synchronized void setPipiraUuid(String uuid) { this.pipiraUuid = uuid; }
    public synchronized String juninhoUuid() { return juninhoUuid; }
    public synchronized void setJuninhoUuid(String uuid) { this.juninhoUuid = uuid; }
    public synchronized String javidizineoUuid() { return javidizineoUuid; }
    public synchronized void setJavidizineoUuid(String uuid) { this.javidizineoUuid = uuid; }
    public synchronized String jinqiuzineoUuid() { return jinqiuzineoUuid; }
    public synchronized void setJinqiuzineoUuid(String uuid) { this.jinqiuzineoUuid = uuid; }
    public synchronized PlayerRelationshipManager relationships() { return relationships; }

    public long getTeleportCooldown(String playerName) {
        if (playerName == null) return 0L;
        return teleportCooldowns.getOrDefault(playerName.toLowerCase(java.util.Locale.ROOT), 0L);
    }

    public void setTeleportCooldown(String playerName, long timestamp) {
        if (playerName == null) return;
        teleportCooldowns.put(playerName.toLowerCase(java.util.Locale.ROOT), timestamp);
    }

    public void resetTeleportCooldown(String playerName) {
        if (playerName == null) return;
        teleportCooldowns.remove(playerName.toLowerCase(java.util.Locale.ROOT));
    }

    public java.util.Map<String, Long> getAllTeleportCooldowns() {
        return new java.util.HashMap<>(teleportCooldowns);
    }

    private static String stamp(String line) {
        return "[" + LocalTime.now().format(HM) + "] " + line;
    }

    public synchronized void addGame(String line, int max) {
        game.addLast(stamp(line));
        while (game.size() > Math.max(5, max)) game.removeFirst();
        eventsSinceMemoryUpdate++;
    }

    public synchronized void addDiscord(String line, int max) {
        discord.addLast(stamp(line));
        while (discord.size() > Math.max(5, max)) discord.removeFirst();
    }

    public synchronized List<String> gameSnapshot() { return new ArrayList<>(game); }
    public synchronized List<String> discordSnapshot() { return new ArrayList<>(discord); }
    public synchronized List<String> notesSnapshot() { return new ArrayList<>(notes); }
    public synchronized int eventsSinceMemoryUpdate() { return eventsSinceMemoryUpdate; }
    public synchronized void resetEventCounter() { eventsSinceMemoryUpdate = 0; }

    public synchronized void setNotes(List<String> newNotes, int max) {
        notes.clear();
        for (String n : newNotes) {
            if (n == null) continue;
            String t = n.trim();
            if (!t.isEmpty()) notes.add(t);
            if (notes.size() >= max) break;
        }
    }

    public synchronized void addPlayerNote(String note, int max) {
        if (note == null) return;
        String t = note.trim();
        if (t.isEmpty()) return;
        for (String existing : notes) {
            if (existing.equalsIgnoreCase(t)) return;
        }
        notes.add(t);
        while (notes.size() > Math.max(10, max)) {
            notes.remove(0);
        }
    }

    public synchronized void clearAll() {
        notes.clear();
        game.clear();
        discord.clear();
        eventsSinceMemoryUpdate = 0;
    }

    public synchronized void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            if (o.has("notes")) for (JsonElement e : o.getAsJsonArray("notes")) notes.add(e.getAsString());
            if (o.has("recentGame")) for (JsonElement e : o.getAsJsonArray("recentGame")) game.addLast(e.getAsString());
            if (o.has("recentDiscord")) for (JsonElement e : o.getAsJsonArray("recentDiscord")) discord.addLast(e.getAsString());
            if (o.has("catUuid") && !o.get("catUuid").isJsonNull()) catUuid = o.get("catUuid").getAsString();
            if (o.has("vitoriaUuid") && !o.get("vitoriaUuid").isJsonNull()) vitoriaUuid = o.get("vitoriaUuid").getAsString();
            if (o.has("pipiraUuid") && !o.get("pipiraUuid").isJsonNull()) pipiraUuid = o.get("pipiraUuid").getAsString();
            if (o.has("juninhoUuid") && !o.get("juninhoUuid").isJsonNull()) juninhoUuid = o.get("juninhoUuid").getAsString();
            if (o.has("javidizineoUuid") && !o.get("javidizineoUuid").isJsonNull()) javidizineoUuid = o.get("javidizineoUuid").getAsString();
            if (o.has("jinqiuzineoUuid") && !o.get("jinqiuzineoUuid").isJsonNull()) jinqiuzineoUuid = o.get("jinqiuzineoUuid").getAsString();
            if (o.has("relationships") && o.get("relationships").isJsonObject()) relationships.deserialize(o.getAsJsonObject("relationships"));
            if (o.has("teleportCooldowns") && o.get("teleportCooldowns").isJsonObject()) {
                JsonObject tc = o.getAsJsonObject("teleportCooldowns");
                for (String key : tc.keySet()) {
                    try {
                        teleportCooldowns.put(key.toLowerCase(java.util.Locale.ROOT), tc.get(key).getAsLong());
                    } catch (Exception ignored) {}
                }
            }
            CumminsMod.LOG.info("[Cummins] Memória carregada: {} anotações, {} linhas de chat, {} cooldowns de tp.", notes.size(), game.size(), teleportCooldowns.size());
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Não consegui ler memoria.json: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JsonObject o = new JsonObject();
            JsonArray n = new JsonArray();
            notes.forEach(n::add);
            JsonArray g = new JsonArray();
            game.forEach(g::add);
            JsonArray d = new JsonArray();
            discord.forEach(d::add);
            o.add("notes", n);
            o.add("recentGame", g);
            o.add("recentDiscord", d);
            if (catUuid != null) o.addProperty("catUuid", catUuid);
            if (vitoriaUuid != null) o.addProperty("vitoriaUuid", vitoriaUuid);
            if (pipiraUuid != null) o.addProperty("pipiraUuid", pipiraUuid);
            if (juninhoUuid != null) o.addProperty("juninhoUuid", juninhoUuid);
            if (javidizineoUuid != null) o.addProperty("javidizineoUuid", javidizineoUuid);
            if (jinqiuzineoUuid != null) o.addProperty("jinqiuzineoUuid", jinqiuzineoUuid);
            o.add("relationships", relationships.serialize());
            JsonObject tc = new JsonObject();
            teleportCooldowns.forEach(tc::addProperty);
            o.add("teleportCooldowns", tc);
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                CumminsConfig.GSON.toJson(o, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao salvar memoria.json: {}", e.getMessage());
        }
    }
}
