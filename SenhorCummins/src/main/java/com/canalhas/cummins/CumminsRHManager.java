package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Livro de Reclamações / RH do Servidor:
 * - Registra protocolos oficiais de ouvidoria/reclamação da firma (reclamacoes_rh.json).
 * - Monitora conflitos (PvP, mortes em sequência, discussões) e intervém com tom de funcionário de RH.
 * - Comandos: /reclamar <alvo> <motivo> e /cummins rh.
 */
public class CumminsRHManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final CumminsMod mod;
    private final Path file;

    public static class Ticket {
        public int id;
        public String date;
        public String author;
        public String target;
        public String reason;
        public String status = "ABERTO - EM ANÁLISE PELO RH";

        public Ticket() {}

        public Ticket(int id, String date, String author, String target, String reason) {
            this.id = id;
            this.date = date;
            this.author = author;
            this.target = target;
            this.reason = reason;
        }
    }

    public static class RHStore {
        public int nextId = 101;
        public List<Ticket> tickets = new ArrayList<>();
    }

    private RHStore store = new RHStore();

    // Rastreamento de mortes recentes para alertas de segurança do trabalho: UUID -> timestamps de mortes
    private final Map<UUID, List<Long>> recentDeaths = new ConcurrentHashMap<>();
    private long lastPvPWarningAt = 0;
    private long lastFrequentDeathWarningAt = 0;

    public CumminsRHManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.file = configDir.resolve("reclamacoes_rh.json");
        load();
    }

    public synchronized void load() {
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    RHStore s = GSON.fromJson(r, RHStore.class);
                    if (s != null && s.tickets != null) {
                        this.store = s;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar reclamacoes_rh.json: {}", e.getMessage());
        }
        if (store == null) store = new RHStore();
        if (store.tickets == null) store.tickets = new ArrayList<>();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(store, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar reclamacoes_rh.json: {}", e.getMessage());
        }
    }

    public synchronized Ticket openTicket(String author, String target, String reason) {
        int id = store.nextId++;
        String date = LocalDateTime.now().format(TIME_FORMAT);
        Ticket t = new Ticket(id, date, author, target, reason);
        store.tickets.add(t);
        save();

        String announce = String.format("📋 [RH da Firma - Protocolo #%04d] O colaborador %s abriu um chamado formal contra %s! Motivo: \"%s\". Favor resolver na base do diálogo ou haverá desconto na folha de pagamento! 🐾",
                id, author, target, reason);
        mod.brain().broadcast(announce, null);
        return t;
    }

    public synchronized List<Ticket> getRecentTickets(int max) {
        int start = Math.max(0, store.tickets.size() - max);
        List<Ticket> list = new ArrayList<>(store.tickets.subList(start, store.tickets.size()));
        Collections.reverse(list);
        return list;
    }

    /**
     * Chamado em mortes de jogadores para monitorar segurança do trabalho.
     */
    public void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        long now = System.currentTimeMillis();

        List<Long> list = recentDeaths.computeIfAbsent(uuid, k -> new ArrayList<>());
        synchronized (list) {
            list.removeIf(t -> now - t > 300_000L); // mantém mortes dos últimos 5 minutos
            list.add(now);

            if (list.size() >= 3 && now - lastFrequentDeathWarningAt > 120_000L) {
                lastFrequentDeathWarningAt = now;
                String name = player.getGameProfile().getName();
                String msg = "⚠️ [RH - Segurança do Trabalho] Colaborador " + name + " sofreu 3 acidentes em menos de 5 minutos! A CIPA da firma recomenda uso de capacete e 10 minutos de descanso na copa.";
                mod.brain().broadcast(msg, name);
            }
        }

        // Se a morte foi causada por outro jogador (PvP)
        if (source != null && source.getAttacker() instanceof ServerPlayerEntity killer) {
            if (!killer.equals(player) && now - lastPvPWarningAt > 60_000L) {
                lastPvPWarningAt = now;
                String kName = killer.getGameProfile().getName();
                String vName = player.getGameProfile().getName();
                String msg = "🚨 [RH da Firma] Alerta de agressão entre colaboradores no recinto! " + kName + " causou a baixa de " + vName + "! Isso vai constar no prontuário da diretoria!";
                mod.brain().broadcast(msg, null);
            }
        }
    }
}
