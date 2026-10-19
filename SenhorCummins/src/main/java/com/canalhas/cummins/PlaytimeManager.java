package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de Tempo Jogado & Scoreboard / Mural de Horas:
 * - Mantém e atualiza o tempo total jogado de cada player no servidor em horas/minutos.
 * - Sincroniza e cria o objetivo de scoreboard vanilla ("horas_jogadas" / "Tempo Jogado")
 *   que exibe o ranking no Sidebar ou na PlayerList.
 * - Registra e exibe o Mural Interativo (Livro GUI com botão Fechar) com o Top Players
 *   e detalhes de cada um.
 * - Abre automaticamente 1x ao dia quando o player entra no servidor (após o carregamento do terreno).
 * - Pode ser consultado a qualquer momento via /horas ou /ranking.
 * - Atualiza o acumulado e o scoreboard automaticamente a cada 10 minutos (ou ao sair).
 */
public class PlaytimeManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final String SCOREBOARD_OBJECTIVE_NAME = "horas_jogadas";

    private final CumminsMod mod;
    private final Path playtimeFile;
    private final Path seenFile;

    public static class PlaytimeData {
        public Map<String, Long> totalSecondsByPlayer = new HashMap<>();
    }

    public static class SeenData {
        public Map<String, String> lastSeenDate = new HashMap<>();
    }

    private PlaytimeData data = new PlaytimeData();
    private SeenData seenData = new SeenData();

    // Rastreamento da sessão atual em tempo real: UUID -> timestamp em ms de quando entrou (ou do último checkpoint)
    private final Map<UUID, Long> sessionStartMs = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pendingJoinPopup = new ConcurrentHashMap<>();

    // Timer de atualização periódica de 10 em 10 minutos
    private long lastPeriodicUpdateMs = System.currentTimeMillis();
    private static final long PERIODIC_INTERVAL_MS = 10L * 60 * 1000L; // 10 minutos

    // Mapeamento de contas alternativas para a conta principal ativa
    private static final Map<String, String> CANONICAL_MAP = new HashMap<>();
    static {
        CANONICAL_MAP.put("asminnn", "Asminnnnnnnnnn");
        CANONICAL_MAP.put("lucasdlg", "Asminnnnnnnnnn");
        CANONICAL_MAP.put("asminnnnnnnnnn", "Asminnnnnnnnnn");
    }

    // Nomes secundários/alternativos que devem ser ocultados do Scoreboard e do Ranking
    private static final Set<String> HIDDEN_PLAYERS = new HashSet<>(Arrays.asList(
            "asminnn",
            "lucasdlg"
    ));

    public static String getCanonicalName(String name) {
        if (name == null) return null;
        String mapped = CANONICAL_MAP.get(name.toLowerCase(Locale.ROOT));
        return mapped != null ? mapped : name;
    }

    public static boolean isHidden(String name) {
        if (name == null) return false;
        return HIDDEN_PLAYERS.contains(name.toLowerCase(Locale.ROOT));
    }

    public PlaytimeManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.playtimeFile = configDir.resolve("playtime.json");
        this.seenFile = configDir.resolve("playtime_seen.json");
        load();
        loadSeen();
    }

    public synchronized void load() {
        try {
            if (Files.exists(playtimeFile)) {
                try (Reader r = Files.newBufferedReader(playtimeFile, StandardCharsets.UTF_8)) {
                    PlaytimeData d = GSON.fromJson(r, PlaytimeData.class);
                    if (d != null && d.totalSecondsByPlayer != null) {
                        this.data = d;
                        mergeHiddenAccounts();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar playtime.json: {}", e.getMessage());
        }
        if (this.data == null) this.data = new PlaytimeData();
        if (this.data.totalSecondsByPlayer == null) this.data.totalSecondsByPlayer = new HashMap<>();
        mergeHiddenAccounts();
    }

    /**
     * Mescla as horas de contas antigas/alternativas (ex: Asminnn e lucasdlg) na conta principal (Asminnnnnnnnnn)
     * e remove os nicks antigos do mapa para que não apareçam mais duplicados.
     */
    private synchronized void mergeHiddenAccounts() {
        if (data == null || data.totalSecondsByPlayer == null) return;
        long extraSeconds = 0;
        Iterator<Map.Entry<String, Long>> it = data.totalSecondsByPlayer.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (isHidden(entry.getKey())) {
                extraSeconds += entry.getValue();
                it.remove();
            }
        }
        if (extraSeconds > 0) {
            long existing = data.totalSecondsByPlayer.getOrDefault("Asminnnnnnnnnn", 0L);
            data.totalSecondsByPlayer.put("Asminnnnnnnnnn", existing + extraSeconds);
            save();
            CumminsMod.LOG.info("[Cummins] Mesclado {}s de contas secundárias na conta ativa Asminnnnnnnnnn! Total: {}s", extraSeconds, existing + extraSeconds);
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(playtimeFile.getParent());
            try (Writer w = Files.newBufferedWriter(playtimeFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this.data, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar playtime.json: {}", e.getMessage());
        }
    }

    private synchronized void loadSeen() {
        try {
            if (Files.exists(seenFile)) {
                try (Reader r = Files.newBufferedReader(seenFile, StandardCharsets.UTF_8)) {
                    SeenData sd = GSON.fromJson(r, SeenData.class);
                    if (sd != null && sd.lastSeenDate != null) {
                        this.seenData = sd;
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar playtime_seen.json: {}", e.getMessage());
        }
        if (this.seenData == null) this.seenData = new SeenData();
        if (this.seenData.lastSeenDate == null) this.seenData.lastSeenDate = new HashMap<>();
    }

    private synchronized void saveSeen() {
        try {
            Files.createDirectories(seenFile.getParent());
            try (Writer w = Files.newBufferedWriter(seenFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this.seenData, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar playtime_seen.json: {}", e.getMessage());
        }
    }

    public synchronized void resetSeenForAll() {
        if (this.seenData != null && this.seenData.lastSeenDate != null) {
            this.seenData.lastSeenDate.clear();
            saveSeen();
        }
    }

    /**
     * Chamado na inicialização do servidor para carregar stats, restaurar horas e configurar o scoreboard vanilla.
     */
    public void onServerStarted(MinecraftServer server) {
        load();
        initFromStatsAndLogsIfEmpty(server);
        updateScoreboard(server);
    }

    /**
     * Inicializa o mapa com as estatísticas do mundo caso o arquivo ainda não contenha dados.
     */
    private void initFromStatsAndLogsIfEmpty(MinecraftServer server) {
        if (data.totalSecondsByPlayer.isEmpty()) {
            Path worldDir = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT);
            Path statsDir = worldDir.resolve("stats");
            if (Files.exists(statsDir)) {
                try (var stream = Files.list(statsDir)) {
                    stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                            if (root.has("stats") && root.getAsJsonObject("stats").has("minecraft:custom")) {
                                JsonObject custom = root.getAsJsonObject("stats").getAsJsonObject("minecraft:custom");
                                long ticks = 0;
                                if (custom.has("minecraft:play_time")) {
                                    ticks = custom.get("minecraft:play_time").getAsLong();
                                } else if (custom.has("minecraft:total_world_time")) {
                                    ticks = custom.get("minecraft:total_world_time").getAsLong();
                                }
                                if (ticks > 0) {
                                    String fname = p.getFileName().toString().replace(".json", "");
                                    data.totalSecondsByPlayer.put(fname, ticks / 20L);
                                }
                            }
                        } catch (Exception ignored) {}
                    });
                } catch (Exception e) {
                    CumminsMod.LOG.warn("[Cummins] Erro ao carregar stats do mundo: {}", e.getMessage());
                }
            }
            save();
        }
    }

    /**
     * Chamado no evento JOIN do jogador.
     */
    public void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        sessionStartMs.put(uuid, System.currentTimeMillis());

        // Se o player ainda não tiver registro pelo nome canônico, inicializa
        String canonicalName = getCanonicalName(player.getGameProfile().getName());
        synchronized (this) {
            data.totalSecondsByPlayer.putIfAbsent(canonicalName, 0L);
        }

        // Verifica se é a primeira vez do player no dia para agendar a exibição do mural
        String today = LocalDate.now().toString();
        String playerKey = canonicalName.toLowerCase(Locale.ROOT);
        boolean alreadySeenToday;
        synchronized (this) {
            alreadySeenToday = today.equals(seenData.lastSeenDate.get(playerKey));
        }

        if (!alreadySeenToday) {
            // Agenda para abrir após 80 ticks (4 segundos) após o carregamento do terreno
            pendingJoinPopup.put(uuid, 80);
        }
    }

    /**
     * Chamado no evento DISCONNECT do jogador.
     */
    public void onPlayerLeave(ServerPlayerEntity player) {
        if (player == null) return;
        UUID uuid = player.getUuid();
        Long start = sessionStartMs.remove(uuid);
        if (start != null) {
            long sessionSeconds = (System.currentTimeMillis() - start) / 1000L;
            if (sessionSeconds > 0) {
                addPlaytime(player.getGameProfile().getName(), sessionSeconds);
            }
        }
    }

    /**
     * Adiciona segundos jogados e salva na conta canônica ativa.
     */
    public synchronized void addPlaytime(String playerName, long seconds) {
        if (playerName == null || seconds <= 0) return;
        String canonical = getCanonicalName(playerName);
        long current = data.totalSecondsByPlayer.getOrDefault(canonical, 0L);
        data.totalSecondsByPlayer.put(canonical, current + seconds);
        save();
    }

    /**
     * Obtém o tempo total jogado de um player em segundos (acumulado + sessão atual se online).
     */
    public synchronized long getTotalPlaytimeSeconds(String playerName, MinecraftServer server) {
        String canonical = getCanonicalName(playerName);
        long total = data.totalSecondsByPlayer.getOrDefault(canonical, 0L);
        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (getCanonicalName(p.getGameProfile().getName()).equalsIgnoreCase(canonical)) {
                    Long start = sessionStartMs.get(p.getUuid());
                    if (start != null) {
                        long currentSessionSec = (System.currentTimeMillis() - start) / 1000L;
                        if (currentSessionSec > 0) {
                            total += currentSessionSec;
                        }
                    }
                }
            }
        }
        return total;
    }

    /**
     * Chamado a cada ServerTick.
     * Cuida dos popups agendados e do checkpoint de 10 em 10 minutos.
     */
    public void onTick(MinecraftServer server) {
        if (server == null) return;

        // Processa popups agendados de login
        if (!pendingJoinPopup.isEmpty()) {
            Iterator<Map.Entry<UUID, Integer>> it = pendingJoinPopup.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                int ticksLeft = entry.getValue() - 1;
                if (ticksLeft <= 0) {
                    it.remove();
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(entry.getKey());
                    if (p != null && p.isAlive()) {
                        markSeenToday(p);
                        openPlaytimeMural(p, true);
                    }
                } else {
                    entry.setValue(ticksLeft);
                }
            }
        }

        // Checkpoint e atualização automática de 10 em 10 minutos
        long now = System.currentTimeMillis();
        if (now - lastPeriodicUpdateMs >= PERIODIC_INTERVAL_MS) {
            lastPeriodicUpdateMs = now;
            doPeriodicUpdate(server);
        }
    }

    /**
     * Atualiza as sessões ativas dos jogadores online, salva o JSON e atualiza o Scoreboard.
     */
    public void doPeriodicUpdate(MinecraftServer server) {
        if (server == null) return;
        long now = System.currentTimeMillis();

        synchronized (this) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                UUID uuid = p.getUuid();
                Long start = sessionStartMs.get(uuid);
                if (start != null) {
                    long elapsedSec = (now - start) / 1000L;
                    if (elapsedSec > 0) {
                        String canonical = getCanonicalName(p.getGameProfile().getName());
                        long cur = data.totalSecondsByPlayer.getOrDefault(canonical, 0L);
                        data.totalSecondsByPlayer.put(canonical, cur + elapsedSec);
                        sessionStartMs.put(uuid, now); // Reinicia o contador para os próximos 10 min
                    }
                } else {
                    sessionStartMs.put(uuid, now);
                }
            }
            mergeHiddenAccounts();
            save();
        }

        updateScoreboard(server);
        CumminsMod.LOG.info("[Cummins] Scoreboard e ranking de horas jogadas atualizados com sucesso!");
    }

    private synchronized void markSeenToday(ServerPlayerEntity player) {
        String key = getCanonicalName(player.getGameProfile().getName()).toLowerCase(Locale.ROOT);
        seenData.lastSeenDate.put(key, LocalDate.now().toString());
        saveSeen();
    }

    /**
     * Atualiza o Scoreboard vanilla no servidor.
     */
    public void updateScoreboard(MinecraftServer server) {
        if (server == null) return;
        ServerScoreboard sb = server.getScoreboard();

        ScoreboardObjective obj = sb.getNullableObjective(SCOREBOARD_OBJECTIVE_NAME);
        if (obj == null) {
            obj = sb.addObjective(
                    SCOREBOARD_OBJECTIVE_NAME,
                    ScoreboardCriterion.DUMMY,
                    Text.literal("⏱ Horas Jogadas").formatted(Formatting.GOLD, Formatting.BOLD),
                    ScoreboardCriterion.RenderType.INTEGER
            );
        }

        // Limpa pontuações de contas antigas/ocultas no scoreboard vanilla
        for (String hiddenName : Arrays.asList("Asminnn", "asminnn", "lucasdlg", "Lucasdlg")) {
            try {
                sb.resetPlayerScore(hiddenName, obj);
            } catch (Exception ignored) {}
        }

        // Atualiza a pontuação de cada jogador ativo/canônico com suas horas totais jogadas
        Map<String, Long> snapshot = getRankingSnapshot(server);
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            String pName = entry.getKey();
            int hours = (int) (entry.getValue() / 3600L);
            sb.getPlayerScore(pName, obj).setScore(hours);
        }
    }

    /**
     * Retorna a lista de jogadores ordenada por tempo jogado (decrescente), ocultando nicks secundários.
     */
    public synchronized Map<String, Long> getRankingSnapshot(MinecraftServer server) {
        Map<String, Long> map = new HashMap<>();
        // Adiciona dados persistidos mesclando nicks canônicos
        for (var entry : data.totalSecondsByPlayer.entrySet()) {
            String canonical = getCanonicalName(entry.getKey());
            if (!isHidden(canonical)) {
                map.put(canonical, map.getOrDefault(canonical, 0L) + entry.getValue());
            }
        }
        long now = System.currentTimeMillis();

        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                String canonical = getCanonicalName(p.getGameProfile().getName());
                Long start = sessionStartMs.get(p.getUuid());
                if (start != null) {
                    long currentSec = (now - start) / 1000L;
                    map.put(canonical, map.getOrDefault(canonical, 0L) + currentSec);
                }
            }
        }

        // Garante que nenhum nick oculto apareça no ranking
        map.entrySet().removeIf(e -> isHidden(e.getKey()));

        List<Map.Entry<String, Long>> list = new ArrayList<>(map.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        Map<String, Long> sorted = new LinkedHashMap<>();
        for (var e : list) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
    }

    /**
     * Abre a interface gráfica interativa do Mural de Horas Jogadas (Livro Virtual com botão Fechar).
     */
    public void openPlaytimeMural(ServerPlayerEntity player, boolean sendWelcomeChat) {
        if (player == null || player.networkHandler == null) return;
        MinecraftServer server = player.getServer();

        if (sendWelcomeChat) {
            MutableText chatMsg = Text.literal("§6§l[⏱ Mural do CanalhasSMP] §eRanking de horas atualizado! ")
                    .append(Text.literal("§a§l[Ver Mural de Horas]")
                            .setStyle(Style.EMPTY
                                    .withFormatting(Formatting.GREEN, Formatting.BOLD)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/horas"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§eClique para reabrir o Mural de Horas Jogadas a qualquer momento!")))));
            player.sendMessage(chatMsg, false);
        }

        ItemStack bookStack = buildRankingBookStack(player, server);
        int selectedSlot = player.getInventory().selectedSlot;
        ItemStack originalStack = player.getInventory().getStack(selectedSlot).copy();

        // Envia o livro virtual, abre a janela no cliente e restaura a mão original
        player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-2, 0, selectedSlot, bookStack));
        player.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(Hand.MAIN_HAND));
        player.networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(-2, 0, selectedSlot, originalStack));

        player.getServerWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_BOOK_PAGE_TURN,
                SoundCategory.PLAYERS,
                1.0f,
                1.1f
        );
    }

    /**
     * Constrói o Livro com o Ranking de Horas Jogadas formatado e paginado.
     */
    private ItemStack buildRankingBookStack(ServerPlayerEntity viewer, MinecraftServer server) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        nbt.put("title", NbtString.of("Ranking de Horas"));
        nbt.put("author", NbtString.of("Senhor Cummins"));
        nbt.put("resolved", NbtByte.of((byte) 1));

        NbtList pages = new NbtList();
        String todayBr = LocalDate.now().format(BR_DATE);
        String rawViewerName = viewer.getGameProfile().getName();
        String viewerName = getCanonicalName(rawViewerName);

        Map<String, Long> ranking = getRankingSnapshot(server);
        List<Map.Entry<String, Long>> sortedList = new ArrayList<>(ranking.entrySet());

        long viewerSec = ranking.getOrDefault(viewerName, 0L);
        int viewerRank = 1;
        for (int i = 0; i < sortedList.size(); i++) {
            if (sortedList.get(i).getKey().equalsIgnoreCase(viewerName)) {
                viewerRank = i + 1;
                break;
            }
        }

        long viewerH = viewerSec / 3600L;
        long viewerM = (viewerSec % 3600L) / 60L;

        // Página 1: Capa & Resumo Pessoal
        MutableText cover = Text.empty()
                .append(Text.literal("✦ CANALHAS SMP ✦\n").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("Mural de Horas Jogadas\n").formatted(Formatting.DARK_GREEN, Formatting.BOLD))
                .append(Text.literal("Data: " + todayBr + "\n\n").formatted(Formatting.GRAY))
                .append(Text.literal("Jogador: ").formatted(Formatting.BLACK))
                .append(Text.literal(viewerName + "\n").formatted(Formatting.DARK_BLUE, Formatting.BOLD))
                .append(Text.literal("Sua Posição: ").formatted(Formatting.BLACK))
                .append(Text.literal("#" + viewerRank + " no Ranking\n").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal("Seu Tempo: ").formatted(Formatting.BLACK))
                .append(Text.literal(viewerH + "h " + viewerM + "min\n\n").formatted(Formatting.DARK_GREEN, Formatting.BOLD))
                .append(Text.literal("➡ Passe as páginas para conferir o ranking dos maiores viciados do servidor!").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        pages.add(NbtString.of(Text.Serializer.toJson(cover)));

        // Páginas de Ranking (4 jogadores por página para caber confortavelmente)
        int perPage = 4;
        int totalPages = (int) Math.ceil((double) sortedList.size() / perPage);
        if (totalPages == 0) totalPages = 1;

        for (int p = 0; p < totalPages; p++) {
            int start = p * perPage;
            int end = Math.min(start + perPage, sortedList.size());

            String header = String.format("🏆 TOP JOGADORES (%d/%d)\n\n", p + 1, totalPages);
            MutableText page = Text.empty()
                    .append(Text.literal(header).formatted(Formatting.GOLD, Formatting.BOLD));

            for (int i = start; i < end; i++) {
                int pos = i + 1;
                var entry = sortedList.get(i);
                String pName = entry.getKey();
                long sec = entry.getValue();
                long h = sec / 3600L;
                long m = (sec % 3600L) / 60L;

                Formatting medalColor = (pos == 1) ? Formatting.GOLD
                        : (pos == 2) ? Formatting.GRAY
                        : (pos == 3) ? Formatting.GOLD // bronze/amarelo
                        : Formatting.DARK_BLUE;

                String medalIcon = (pos == 1) ? "🥇 " : (pos == 2) ? "🥈 " : (pos == 3) ? "🥉 " : "#" + pos + " ";

                boolean isViewer = pName.equalsIgnoreCase(viewerName) || pName.equalsIgnoreCase(rawViewerName);
                Formatting nameColor = isViewer ? Formatting.DARK_GREEN : Formatting.BLACK;

                page.append(Text.literal(medalIcon).formatted(medalColor, Formatting.BOLD));
                page.append(Text.literal(pName + (isViewer ? " (Você)" : "") + "\n").formatted(nameColor, Formatting.BOLD));
                page.append(Text.literal("   ⏱ " + h + "h " + m + "min jogados\n\n").formatted(Formatting.DARK_GRAY));
            }

            pages.add(NbtString.of(Text.Serializer.toJson(page)));
        }

        // Página de Informações do Sistema
        MutableText infoPage = Text.empty()
                .append(Text.literal("ℹ️ SOBRE O MURAL\n\n").formatted(Formatting.DARK_BLUE, Formatting.BOLD))
                .append(Text.literal("• Atualizado a cada 10 min automaticamente.\n\n").formatted(Formatting.BLACK))
                .append(Text.literal("• Reabra quando quiser digitando ").formatted(Formatting.BLACK))
                .append(Text.literal("/horas").formatted(Formatting.DARK_GREEN, Formatting.BOLD))
                .append(Text.literal(" ou ").formatted(Formatting.BLACK))
                .append(Text.literal("/ranking").formatted(Formatting.DARK_GREEN, Formatting.BOLD))
                .append(Text.literal(".\n\n").formatted(Formatting.BLACK))
                .append(Text.literal("• O ranking também pode ser fixado na tela via Scoreboard!\n\n").formatted(Formatting.BLACK))
                .append(Text.literal("Bom jogo e continue firme na firma! 🐾🐱").formatted(Formatting.GOLD, Formatting.BOLD));
        pages.add(NbtString.of(Text.Serializer.toJson(infoPage)));

        nbt.put("pages", pages);
        return book;
    }
}
