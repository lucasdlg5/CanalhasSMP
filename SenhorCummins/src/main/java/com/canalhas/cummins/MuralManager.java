package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtByte;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerencia o "Mural de Novidades" do servidor (arquivo MURAL_NOVIDADES.json).
 * Exibe automaticamente uma janela interativa (Book GUI com botão Fechar/Concluído)
 * quando o jogador entra pela primeira vez no dia, ou a qualquer momento via /mural.
 */
public class MuralManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Path muralFile;
    private final Path seenFile;
    private MuralData data = new MuralData();
    private Map<String, String> seenByPlayerDate = new HashMap<>();
    private final Map<UUID, Integer> pendingJoinPopup = new ConcurrentHashMap<>();

    public static class MuralData {
        public String versaoMural = "2026-10-17-v1";
        public String tituloServidor = "CanalhasSMP do Asmi";
        public String subtitulo = "Mural de Boas-Vindas & Novidades";
        public String mensagemBoasVindas = "Bem-vindo(a) de volta! Confira abaixo as novidades e melhorias recentes do nosso servidor:";
        public List<String> novidades = new ArrayList<>();
        public List<String> correcoes = new ArrayList<>();
        public List<String> comandosUteis = new ArrayList<>();
    }

    public static class SeenStore {
        public Map<String, String> lastSeen = new HashMap<>();
    }

    public MuralManager(Path configDir) {
        this.muralFile = FabricLoader.getInstance().getGameDir().resolve("MURAL_NOVIDADES.json");
        this.seenFile = configDir.resolve("mural_seen.json");
        loadOrCreateDefault();
        loadSeen();
    }

    public synchronized void loadOrCreateDefault() {
        try {
            if (Files.exists(muralFile)) {
                try (Reader r = Files.newBufferedReader(muralFile, StandardCharsets.UTF_8)) {
                    MuralData loaded = GSON.fromJson(r, MuralData.class);
                    if (loaded != null) {
                        this.data = loaded;
                        if (this.data.novidades == null) this.data.novidades = new ArrayList<>();
                        if (this.data.correcoes == null) this.data.correcoes = new ArrayList<>();
                        if (this.data.comandosUteis == null) this.data.comandosUteis = new ArrayList<>();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao ler MURAL_NOVIDADES.json: {}", e.getMessage());
        }

        // Cria o arquivo padrão com todas as implementações e correções feitas até agora
        MuralData d = new MuralData();
        d.versaoMural = "2026-10-18-v3";
        d.tituloServidor = "CanalhasSMP do Asmi";
        d.subtitulo = "Mural de Boas-Vindas & Novidades";
        d.mensagemBoasVindas = "Bem-vindo(a) ao CanalhasSMP do Asmi! Veja o que preparamos de novo para você hoje:";

        d.novidades.add("🌐 Teste de Ping Contínuo & Diagnóstico de Conexão (/conexao ou /ping): O Cummins testa a internet do host constantemente em segundo plano (8.8.8.8). Se ocorrer qualquer queda momentânea no servidor, ele avisa no chat que o lag foi do nosso lado! Se você reconectar ou perguntar 'minha net ou o server?', ele te responde com o diagnóstico exato dos últimos 5 minutos!");
        d.novidades.add("🧭 Guia Turístico / \"Leva até lá\" (/levar <lugar> ou /pontos): O Cummins conhece os pontos do servidor (Spawn, Forja, Peugeot 206) e guia você caminhando até o local! Se você ficar para trás, ele para, mia e te espera com partículas. Use /levar <lugar> ou diga no chat 'cummins me leva até o spawn'!");
        d.novidades.add("🎒 Mula de Carga / Pochete do Cummins (Shift + Botão Direito ou /pochete): Compartimento portátil de 9 slots no Senhor Cummins para guardar ferramentas de emergência, tochas, comida e minérios da firma!");
        d.novidades.add("📋 Contratos & Tarefas da Firma (/contrato e /entregar): A diretoria lança contratos diários de manutenção (cobre pro motor do Peugeot 206, carvão pra caldeira do Create, couro pros bancos da van). Entregue os recursos e receba diamantes e mimos da firma!");
        d.novidades.add("⚖️ Livro de Reclamações / RH do Servidor (/reclamar <alvo> <motivo>): Abra protocolos oficiais de ouvidoria contra outros jogadores. O RH do Cummins também alerta no chat em casos de mortes seguidas (segurança do trabalho) ou briga no PvP!");
        d.novidades.add("🦇 Caçador de Pragas Noturno: O Cummins agora dedetiza Silverfish no chão com patadas letais instantâneas e salta nos Phantoms nos céus com patadas e ondas sônicas, protegendo os trabalhadores noturnos!");
        d.novidades.add("🌙 Boletim de Fim de Turno no Discord: No fechamento do expediente diário, o Cummins publica no Discord o resumo da firma: Operário Destaque com mais horas jogadas, status de TPS da fábrica e a clássica mensagem de encerramento!");
        d.novidades.add("⏱️ Scoreboard & Mural de Horas (/horas ou /ranking): Veja o ranking dos maiores viciados do servidor! O mural abre 1x ao dia no login ou a qualquer momento pelo comando. Atualiza de 10 em 10 min e inclui todo o histórico do servidor!");
        d.novidades.add("🌀 Teleporte Místico do Senhor Cummins (/cumminstp): Peça no chat 'cummins me teleporte para perto do <jogador>' ou use /cumminstp <alvo>! Cooldown de 1 hora por player (consulte com /tpcooldown).");
        d.novidades.add("🐱 Senhor Cummins (Gato Mascote): Faz companhia pelo mundo, reage a carinho (botão direito) e dança do Shift repetido, dando mimos quando está feliz!");
        d.novidades.add("📚 Assistente de Mods: Pergunte no chat começando com 'Como faço', 'Como posso', 'O que', 'Por que' ou 'Onde' para o Cummins ensinar receitas e máquinas dos mods!");
        d.novidades.add("💬 Humor por Jogador & Cantadas: O Cummins lembra de como você trata ele (carinho, amor, saudade ou sarcasmo) e solta cantadas cômicas no chat.");
        d.novidades.add("🐶 Cachorrinha Vitória: A Fox Paulistinha tricolor do ArzanLion chegou! Ela aparece quando o ArzanLion entra ou quando chamam 'Vitória' no chat, protegendo contra monstros.");
        d.novidades.add("🔍 Scanner de Itens & Escala da Longeura: Pergunte 'Onde posso encontrar <item>?'! Se estiver perto, o Cummins senta na frente do baú/bloco. Se for longe, te guia e te dá a direção cardeal!");
        d.novidades.add("📰 Novidades da Vida Real por Jogador: O Cummins pesquisa notícias recentes dos temas favoritos de cada player (McLaren, fusão nuclear, aviação, Deadlock, etc.) para puxar papo!");
        d.novidades.add("⏰ Horário de Brasília & Saúde Gamer: O Cummins sabe a hora exata de Brasília, responde que horas são e lembra você de beber água e esticar as costas!");
        d.novidades.add("🧠 Conversa Ping-Pong & Memória: O Cummins aprende seus gostos e interage naturalmente no chat e no canal #geral do Discord!");
        d.novidades.add("💾 Backup Completo dos Mods & Memória: Os backups do servidor no Google Drive agora incluem a pasta 'mods_desenvolvidos' com todo o código-fonte, painel, JARs e memória da IA!");
        d.novidades.add("⚡ Servidor Otimizado: Mapa pré-gerado com Chunky e novo mod Noisium para exploração lisinha sem travadas.");

        d.correcoes.add("🔤 Nome do Cummins Flexível: O Cummins agora atende mesmo quando escrevem seu nome errado ou com preguiça (como 'cummings', 'cumings', 'cumin', etc.).");
        d.correcoes.add("🔇 Fim do Spam de Vácuo: Reduzida drasticamente a frequência de mensagens quando o Cummins não é respondido, mantendo o chat agradável.");
        d.correcoes.add("🎁 Presentes Rebalanceados: Frequência de presentes reajustada para momentos especiais, com frases e motivos mais variados e sem repetição excessiva.");
        d.correcoes.add("✂️ Respostas Completas: O Cummins não corta mais explicações grandes no meio; ele continua na mensagem seguinte.");
        d.correcoes.add("🗣️ Falas Mais Naturais: Reduzida a repetição de bordões no chat.");
        d.correcoes.add("🛡️ Vitória 100% Amigável: Configurada para nunca atacar outros jogadores, defendendo apenas contra monstros.");
        d.correcoes.add("👻 Sem Empurrão: Removida de vez a colisão física do Cummins e da Vitória + caminhada em órbita para não empurrarem jogadores.");
        d.correcoes.add("🛶 Sem Duplicação: Corrigido o bug que duplicava os pets ao segurá-los no colo (CarryOn) ou colocá-los em barcos.");

        d.comandosUteis.add("/conexao ou /ping -> Mostra o diagnóstico da internet do host nos últimos 5 min (ping, perda e quedas).");
        d.comandosUteis.add("Perguntar 'minha net ou o server?' no chat -> O Cummins responde se o lag recente foi do host ou do jogador.");
        d.comandosUteis.add("/contrato -> Consulta a tarefa/contrato da firma do dia.");
        d.comandosUteis.add("/entregar -> Entrega os itens do contrato e recebe as recompensas.");
        d.comandosUteis.add("/reclamar <alvo> <motivo> -> Abre um protocolo formal no RH contra um jogador.");
        d.comandosUteis.add("/levar <lugar> -> O Cummins te guia caminhando até o ponto turístico.");
        d.comandosUteis.add("/pontos -> Lista todos os pontos turísticos cadastrados.");
        d.comandosUteis.add("/pochete ou Shift+Botão Direito no gato -> Abre a pochete de 9 slots do Cummins.");
        d.comandosUteis.add("/mural -> Abre esta janela do Mural a qualquer momento.");
        d.comandosUteis.add("/horas ou /ranking -> Abre o Mural de Horas Jogadas e ranking do servidor.");
        d.comandosUteis.add("/cumminstp <jogador> -> Teleporta você para perto de um amigo (cooldown 1h).");
        d.comandosUteis.add("/tpcooldown -> Mostra quanto tempo falta para usar o teleporte novamente.");
        d.comandosUteis.add("Onde posso encontrar <item>? -> O Cummins escaneia baús/blocos e te guia até lá.");
        d.comandosUteis.add("Que horas são? -> Informa a hora de Brasília e há quanto tempo você está jogando.");
        d.comandosUteis.add("Cummins, alguma novidade? -> Puxa uma notícia recente do seu tema favorito.");
        d.comandosUteis.add("Chamar 'Cummins' ou 'Vitória' no chat -> Interage com os pets.");
        d.comandosUteis.add("Shift 3x perto do pet -> Faz carinho especial / ganha mimo.");

        this.data = d;
        saveMural();
    }

    public synchronized MuralData getData() {
        return this.data;
    }

    public synchronized void saveMural() {
        try {
            Files.createDirectories(muralFile.getParent());
            try (Writer w = Files.newBufferedWriter(muralFile, StandardCharsets.UTF_8)) {
                GSON.toJson(this.data, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar MURAL_NOVIDADES.json: {}", e.getMessage());
        }
    }

    private synchronized void loadSeen() {
        try {
            if (Files.exists(seenFile)) {
                try (Reader r = Files.newBufferedReader(seenFile, StandardCharsets.UTF_8)) {
                    SeenStore store = GSON.fromJson(r, SeenStore.class);
                    if (store != null && store.lastSeen != null) {
                        this.seenByPlayerDate = new HashMap<>(store.lastSeen);
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao ler mural_seen.json: {}", e.getMessage());
        }
    }

    private synchronized void saveSeen() {
        try {
            Files.createDirectories(seenFile.getParent());
            SeenStore store = new SeenStore();
            store.lastSeen = new HashMap<>(this.seenByPlayerDate);
            try (Writer w = Files.newBufferedWriter(seenFile, StandardCharsets.UTF_8)) {
                GSON.toJson(store, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar mural_seen.json: {}", e.getMessage());
        }
    }

    private String getTodayStamp() {
        return LocalDate.now().toString() + "|" + (data.versaoMural != null ? data.versaoMural : "v1");
    }

    /**
     * Chamado quando um jogador entra no servidor.
     * Se for a primeira vez dele no dia (ou se o mural tiver versão nova), agenda a abertura da janela do Mural.
     */
    public void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        loadOrCreateDefault();
        String key = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        String todayStamp = getTodayStamp();

        synchronized (this) {
            String last = seenByPlayerDate.get(key);
            if (todayStamp.equals(last)) {
                return; // Já viu o mural hoje nesta versão
            }
        }

        // Agenda para abrir após 70 ticks (3.5 segundos), garantindo que a tela "Baixando terreno" já fechou
        pendingJoinPopup.put(player.getUuid(), 70);
    }

    public void onTick(MinecraftServer server) {
        if (pendingJoinPopup.isEmpty() || server == null) return;

        Iterator<Map.Entry<UUID, Integer>> it = pendingJoinPopup.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int ticksLeft = entry.getValue() - 1;
            if (ticksLeft <= 0) {
                it.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
                if (player != null && player.isAlive()) {
                    markSeenToday(player);
                    openMuralForPlayer(player, true);
                }
            } else {
                entry.setValue(ticksLeft);
            }
        }
    }

    private synchronized void markSeenToday(ServerPlayerEntity player) {
        String key = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        seenByPlayerDate.put(key, getTodayStamp());
        saveSeen();
    }

    public synchronized void resetSeenForAll() {
        seenByPlayerDate.clear();
        saveSeen();
    }

    /**
     * Abre a janela interativa do Mural de Novidades na tela do jogador (com botão Fechar/Concluído).
     */
    public void openMuralForPlayer(ServerPlayerEntity player, boolean sendWelcomeChat) {
        if (player == null || player.networkHandler == null) return;
        loadOrCreateDefault();

        if (sendWelcomeChat) {
            MutableText msg = Text.literal("§6§l[📜 " + data.tituloServidor + "] §eBem-vindo(a), §f" + player.getGameProfile().getName() + "§e! ")
                    .append(Text.literal("§a§l[Abrir Mural de Novidades]")
                            .setStyle(Style.EMPTY
                                    .withFormatting(Formatting.GREEN, Formatting.BOLD)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mural"))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("§eClique para reabrir a janela do Mural de Novidades!")))));
            player.sendMessage(msg, false);
        }

        ItemStack bookStack = buildMuralBookStack(player.getGameProfile().getName());
        int selectedSlot = player.getInventory().selectedSlot;
        ItemStack originalStack = player.getInventory().getStack(selectedSlot).copy();

        // Envia o livro virtual no slot ativo do cliente, abre a janela nativa do livro (com botão Concluído/Fechar)
        // e restaura imediatamente o item real da mão do jogador sem alterar o inventário do servidor.
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

    private ItemStack buildMuralBookStack(String playerName) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();
        nbt.put("title", NbtString.of(data.tituloServidor));
        nbt.put("author", NbtString.of("Administração"));
        nbt.put("resolved", NbtByte.of((byte) 1));

        NbtList pages = new NbtList();
        String todayBr = LocalDate.now().format(BR_DATE);

        // Página 1: Capa & Boas-vindas
        MutableText cover = Text.empty()
                .append(Text.literal("✦ " + data.tituloServidor + " ✦\n").formatted(Formatting.GOLD, Formatting.BOLD))
                .append(Text.literal(data.subtitulo + "\n").formatted(Formatting.DARK_GREEN, Formatting.BOLD))
                .append(Text.literal("Data: " + todayBr + "\n\n").formatted(Formatting.GRAY))
                .append(Text.literal("Olá, ").formatted(Formatting.BLACK))
                .append(Text.literal(playerName + "!\n").formatted(Formatting.DARK_BLUE, Formatting.BOLD))
                .append(Text.literal(data.mensagemBoasVindas + "\n\n").formatted(Formatting.BLACK))
                .append(Text.literal("➡ Passe as páginas para ver as novidades e clique no botão abaixo para fechar.").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        pages.add(NbtString.of(Text.Serializer.toJson(cover)));

        // Páginas de Novidades (2 itens curtos por página para caber perfeitamente sem cortar)
        addSectionPages(pages, "✨ NOVIDADES", Formatting.DARK_GREEN, data.novidades);

        // Páginas de Correções (2 itens curtos por página)
        addSectionPages(pages, "🛠️ CORREÇÕES", Formatting.DARK_BLUE, data.correcoes);

        // Página Final: Dicas e Comandos Úteis
        if (data.comandosUteis != null && !data.comandosUteis.isEmpty()) {
            MutableText tipsPage = Text.empty()
                    .append(Text.literal("💡 DICAS & COMANDOS\n\n").formatted(Formatting.GOLD, Formatting.BOLD));
            for (String tip : data.comandosUteis) {
                tipsPage.append(Text.literal("• " + tip + "\n\n").formatted(Formatting.BLACK));
            }
            tipsPage.append(Text.literal("Bom jogo a todos! 🎮").formatted(Formatting.DARK_GREEN, Formatting.BOLD));
            pages.add(NbtString.of(Text.Serializer.toJson(tipsPage)));
        }

        nbt.put("pages", pages);
        return book;
    }

    private static void addSectionPages(NbtList pages, String headerTitle, Formatting headerColor, List<String> items) {
        if (items == null || items.isEmpty()) return;

        int itemsPerPage = 2;
        int totalPages = (int) Math.ceil((double) items.size() / itemsPerPage);

        for (int p = 0; p < totalPages; p++) {
            int start = p * itemsPerPage;
            int end = Math.min(start + itemsPerPage, items.size());

            String pageHeader = totalPages > 1
                    ? String.format("%s (%d/%d)\n\n", headerTitle, p + 1, totalPages)
                    : headerTitle + "\n\n";

            MutableText pageText = Text.empty()
                    .append(Text.literal(pageHeader).formatted(headerColor, Formatting.BOLD));

            for (int i = start; i < end; i++) {
                String item = items.get(i);
                pageText.append(Text.literal(item + "\n\n").formatted(Formatting.BLACK));
            }

            pages.add(NbtString.of(Text.Serializer.toJson(pageText)));
        }
    }
}
