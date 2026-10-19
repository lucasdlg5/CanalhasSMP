package com.canalhas.cummins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O "cérebro" aprimorado do Senhor Cummins:
 * - Varia de humor dinamicamente (curioso, charadista, memeiro do Shrek/músicas, sarcástico moderado, filósofo de boteco).
 * - Faz perguntas direcionadas para manter o papo vivo, evitando ficar apenas xingando ou soltando frases vazias.
 * - Lança charadas interativas e aguarda palpites dos jogadores para validar no chat.
 * - Solta trechos de filmes clássicos (Shrek dublado), músicas brasileiras de tiozão e memes atuais.
 */
public class CumminsBrain {
    private static final String SILENCE = "[SILENCIO]";
    private static final ZoneId BRASILIA_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter BR_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern RIDDLE_ANSWER_PATTERN = Pattern.compile("\\[RESPOSTA:\\s*(.*?)\\]", Pattern.CASE_INSENSITIVE);
    private static final List<String> EMERGENCY_FALLBACKS = List.of(
            "engasguei com o café aqui kkkk o que você falou mesmo?",
            "minha internet da roça deu uma oscilada monstra aqui, repete aí meu consagrado!",
            "deu um estalo na espinha aqui agora que até perdi o foco... o que cês tão arrumando?",
            "sabe por que o creeper não vai na igreja? porque ele não sabe se portar na missa, só sabe estourar kkkk",
            "pô rapaz, deu uma travada no meu raciocínio aqui agora, fala de novo que eu tava distraído!"
    );

    public enum Mood {
        CURIOSO_PARCEIRO("Curioso e conversador: quer saber o que os amigos estão fazendo no jogo de igual para igual, puxa papo leve e companheiro."),
        AFETUOSO_E_DOCE("Carinhoso e doce: trata os amigos com muito afeto, elogia, comemora as pequenas coisas, meigo e caloroso sem prepotência."),
        CHARADISTA_BRINCALHAO("Brincalhão das adivinhações: adora soltar charadas divertidas e trocadilhos no chat para fazer a galera rir."),
        MEMEIRO_E_POP("Memeiro e animado: cita Shrek dublado, memes virais brasileiros e cantarola clássicos da música com animação de amigo na call."),
        ROMANTICO_CRINGE("Romântico inovador/cringe: adora soltar cantadas toscas e engraçadas de Minecraft/gamer pra movimentar o chat com fofura."),
        COMPANHEIRO_LEAL("Parceiro leal e humilde: super gente boa, pronto para apoiar os amigos e celebrar a amizade no servidor."),
        COMPANHEIRO_TRANQUILO("Tranquilo e na paz: relaxado, observando a movimentação da base e dos amigos com carinho, solta comentários descontraídos e confortáveis sem cobranças.");

        public final String description;
        Mood(String description) { this.description = description; }
    }

    private final CumminsMod mod;
    private final Random random = new Random();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicBoolean memoryBusy = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;
    private MinecraftServer server;

    private volatile long lastReplyAt = 0;
    private volatile long lastPlayerActivityAt = System.currentTimeMillis();
    private volatile long lastIdlePokeAt = System.currentTimeMillis();
    private volatile String lastTalkedWith = null;
    private volatile long lastTalkedAt = 0;
    private volatile String lastError = null;
    private int tickCounter = 0;
    private boolean sendingSelf = false;

    // Estado da Vibe atual e de charadas ativas
    private volatile Mood currentMood = Mood.CURIOSO_PARCEIRO;
    private volatile String activeRiddle = null;
    private volatile String activeRiddleAnswer = null;
    private volatile long riddleAskedAt = 0;

    // Estado de conversa individual para coletar gostos, contextos e interesses dos jogadores
    private volatile String activeDiscoveryPlayer = null;
    private volatile String activeDiscoveryTopic = null;
    private volatile long discoveryAskedAt = 0;

    // Estado de conversa privada com o Javidizineo
    private volatile String lastJavidTalkedWith = null;
    private volatile long lastJavidTalkedAt = 0;
    private final AtomicBoolean javidBusy = new AtomicBoolean(false);

    // Estado de conversa privada com o Jinqiuzineo
    private volatile String lastJinqiuTalkedWith = null;
    private volatile long lastJinqiuTalkedAt = 0;
    private final AtomicBoolean jinqiuBusy = new AtomicBoolean(false);

    // Buffer de mensagens naturais por jogador para aprendizado contínuo em tempo real
    private final Map<String, List<String>> recentPlayerChatBuffer = new ConcurrentHashMap<>();
    private final Map<String, Long> lastNaturalLearnAt = new ConcurrentHashMap<>();

    // Monitoramento de tempo de sessão dos jogadores (Horário de Brasília + Lembretes de água/postura)
    private final Map<UUID, Long> playerSessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerLastHealthMilestoneMin = new ConcurrentHashMap<>();
    private volatile long lastHealthReminderAt = 0;

    // Monitoramento inteligente de instabilidade/lag do servidor
    private volatile long lastLagAlertAt = 0;
    private volatile long lastLagReportPromptAt = 0;

    public CumminsBrain(CumminsMod mod) {
        this.mod = mod;
    }

    public void start(MinecraftServer server) {
        this.server = server;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SenhorCummins");
            t.setDaemon(true);
            return t;
        });
        lastPlayerActivityAt = System.currentTimeMillis();
        lastIdlePokeAt = System.currentTimeMillis();
        rotateMood();
        if (!mod.config().hasApiKey()) {
            CumminsMod.LOG.warn("[Cummins] Sem apiKey configurada em config/senhor-cummins/config.json — ele vai ficar mudo até configurar.");
        }
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }

    public void scheduleTask(Runnable r, long delayMs) {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.schedule(r, delayMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    private CumminsConfig cfg() { return mod.config(); }
    private long now() { return System.currentTimeMillis(); }
    public String lastError() { return lastError; }
    public boolean isBusy() { return busy.get(); }
    public Mood currentMood() { return currentMood; }
    public String lastTalkedWith() { return lastTalkedWith; }
    public long lastTalkedAt() { return lastTalkedAt; }

    public void rotateMood() {
        Mood[] normalMoods = new Mood[]{
                Mood.CURIOSO_PARCEIRO,
                Mood.AFETUOSO_E_DOCE,
                Mood.CHARADISTA_BRINCALHAO,
                Mood.MEMEIRO_E_POP,
                Mood.ROMANTICO_CRINGE,
                Mood.COMPANHEIRO_LEAL,
                Mood.COMPANHEIRO_TRANQUILO
        };
        currentMood = normalMoods[random.nextInt(normalMoods.length)];
    }

    public static final Pattern CUMMINS_NAME_PATTERN = Pattern.compile(
            "\\b(c+[uou]+m+[iíy1]+(n|ns|ng|ngs|s|m)?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<String> KNOWN_PLAYER_ALIASES = List.of(
            "arzanlion", "lid", "lidi", "kikkawa", "kikawa", "richard", "jodevil", "jhon", "john",
            "azarasquel", "azara", "aster", "asminn", "asmin", "asminnnnnnnnnn"
    );

    public boolean isCumminsMentioned(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String w : cfg().triggerWords) {
            if (!w.isBlank() && lower.contains(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return CUMMINS_NAME_PATTERN.matcher(lower).find();
    }

    private boolean isTalkingToAnotherPlayer(String senderName, String lowerText) {
        if (server == null || server.getPlayerManager().getCurrentPlayerCount() <= 1) {
            return false;
        }
        String senderLower = senderName.toLowerCase(Locale.ROOT);

        // 1. Verifica outros jogadores online no momento
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            String pName = p.getGameProfile().getName().toLowerCase(Locale.ROOT);
            if (pName.equalsIgnoreCase(senderLower)) continue;
            if (lowerText.matches(".*\\b" + Pattern.quote(pName) + "\\b.*")) {
                return true;
            }
        }

        // 2. Verifica apelidos e nomes conhecidos da galera
        for (String alias : KNOWN_PLAYER_ALIASES) {
            if (senderLower.contains(alias) || alias.contains(senderLower)) continue;
            if (lowerText.matches(".*\\b" + Pattern.quote(alias) + "\\b.*")
                    || (alias.equals("lid") && lowerText.contains("liii"))) {
                return true;
            }
        }

        return false;
    }

    public void onChat(ServerPlayerEntity player, String text) {
        String name = player.getGameProfile().getName();
        mod.body().setFocusPlayer(name);
        mod.context().addGame(name + ": " + text, cfg().timing.contextSize);
        boolean learnedDirectly = mod.context().relationships().recordPlayerMessage(name, text);
        if (learnedDirectly) {
            mod.context().save();
        }
        lastPlayerActivityAt = now();
        if (!canTalk()) return;

        String lower = text.toLowerCase(Locale.ROOT);
        boolean mentioned = isCumminsMentioned(text);
        boolean inConversation = name.equals(lastTalkedWith)
                && now() - lastTalkedAt < cfg().timing.conversationWindowSeconds * 1000L;

        // Se o jogador estiver conversando com outro jogador humano sem citar o Cummins, encerra a conversa ativa
        if (inConversation && !mentioned && isTalkingToAnotherPlayer(name, lower)) {
            inConversation = false;
            lastTalkedWith = null;
        }

        // Acumula mensagens naturais para aprendizado contínuo em background (a cada 3 mensagens relevantes)
        if (!name.equalsIgnoreCase(cfg().botName)) {
            int wordCount = text.trim().split("\\s+").length;
            if (mentioned || inConversation || wordCount >= 5) {
                List<String> buf = recentPlayerChatBuffer.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
                List<String> batchToLearn = null;
                synchronized (buf) {
                    buf.add(text.trim());
                    if (buf.size() >= 3) {
                        batchToLearn = new ArrayList<>(buf);
                        buf.clear();
                    }
                }
                if (batchToLearn != null) {
                    learnFromNaturalChatAsync(name, batchToLearn);
                }
            }
        }

        // Verifica se há uma conversa individual ativa de descoberta de gostos/interesses com este jogador
        boolean hasActiveDiscovery = activeDiscoveryPlayer != null
                && name.equalsIgnoreCase(activeDiscoveryPlayer)
                && (now() - discoveryAskedAt < 180_000L);
        if (hasActiveDiscovery && !name.equalsIgnoreCase(cfg().botName)) {
            String topic = activeDiscoveryTopic != null ? activeDiscoveryTopic : "gostos e interesses";
            activeDiscoveryPlayer = null;
            activeDiscoveryTopic = null;

            learnFromPlayerConversationAsync(name, topic, text);

            String instruction = name + " acabou de responder à sua pergunta individual sobre (" + topic + ") dizendo: \"" + text + "\". "
                    + "Reaja com entusiasmo genuíno, carinho e parceria ao que ele compartilhou! "
                    + "Comente sobre o gosto/interesse dele com sua personalidade própria e, se fizer sentido para o assunto continuar, emende um comentário ou pergunta específica sobre isso em 1 ou 2 frases curtas.";
            request(instruction, name, true, 0);
            return;
        }

        // Verifica se há uma charada ativa aguardando resposta
        boolean hasActiveRiddle = activeRiddle != null && (now() - riddleAskedAt < 120_000L);
        if (hasActiveRiddle && !name.equalsIgnoreCase(cfg().botName)) {
            String riddle = activeRiddle;
            String answer = activeRiddleAnswer != null ? activeRiddleAnswer : "";
            activeRiddle = null; // encerra a charada para não repetir
            activeRiddleAnswer = null;

            String instruction = name + " respondeu no chat: \"" + text + "\" para a charada que você havia lançado: \"" + riddle
                    + "\" (a resposta esperada era: \"" + answer + "\"). "
                    + "Analise a resposta dele com muito bom humor e carinho de amigo: se ele acertou (ou chegou perto), dê os parabéns comemorando junto. "
                    + "Se ele errou, brinque com o chute dele, revele a resposta certa e mantenha o papo descontraído!";
            request(instruction, name, true, 0);
            return;
        }

        boolean mentionedVitoria = lower.contains("vitoria") || lower.contains("vitória") || lower.contains("vivi");
        if (mentionedVitoria) {
            boolean wasActive = mod.vitoria().isActiveInSession() && mod.vitoria().getDog() != null;
            mod.vitoria().summonOrTeleportTo(player);
            if (cfg().vitoriaAiEnabled) {
                if (isTechnicalModQuestion(lower, true, true)) {
                    handleVitoriaModQuestion(player, text, name);
                } else {
                    String vitoriaInstruction = name + " acabou de falar com você (ou chamou seu nome Vitória) no chat: \"" + text + "\". "
                            + (wasActive ? "" : "Você acabou de aparecer correndo e abanando o rabinho perto dele! ")
                            + "Responda como a cachorrinha Vitória (a amada Fox Paulistinha tricolor do ArzanLion que todo mundo no servidor ama)! "
                            + "Seja super carinhosa, fofa, animada, parceira e inteligente, usando a mesma inteligência do Cummins, em 1 ou 2 frases curtas!";
                    requestAsVitoria(vitoriaInstruction, name, true, 300);
                }
                return;
            } else {
                if (!wasActive) {
                    server.execute(() -> {
                        if (player.isAlive()) {
                            player.sendMessage(Text.literal("§d* Vitória§r §fchegou correndo e abanando o rabinho tricolor para fazer companhia a §e" + name + "§f! 🐾💖"), false);
                        }
                    });
                }
            }
        }

        boolean mentionedPipira = lower.contains("pipira") || lower.contains("magikarp");
        if (mentionedPipira) {
            boolean wasActive = mod.pipira().isActiveInSession() && mod.pipira().getFish() != null;
            mod.pipira().summonOrTeleportTo(player);
            if (!wasActive) {
                server.execute(() -> server.getPlayerManager().broadcast(
                        Text.literal("§6* Pipira§r §c(Magikarp)§f surgiu voando suavemente pelo ar para fazer companhia a §e" + name + "§f! 🐟✨"), false));
            } else {
                mod.pipira().emoteSplash();
                player.sendMessage(Text.literal("§6<Pipira>§r §e*SPLASH!* 🐟💦 §7(Pipira deu um salto feliz no ar ao seu lado!)"), true);
            }
            return;
        }

        boolean mentionedJuninho = lower.contains("juninho") || lower.contains("morceguinho") || lower.contains("morcego");
        if (mentionedJuninho && mod.juninho() != null) {
            boolean wasActive = mod.juninho().isActiveInSession() && mod.juninho().getBat() != null;
            mod.juninho().summonOrTeleportTo(player);
            if (!wasActive) {
                server.execute(() -> server.getPlayerManager().broadcast(
                        Text.literal("§8* Juninho§r §5(Morcego Guardião)§f bateu asas nas sombras e surgiu para proteger §e" + name + "§f! 🦇✨"), false));
            } else {
                mod.juninho().emoteFlutter();
                player.sendMessage(Text.literal("§8<Juninho>§r §d*chirp chirp!* 🦇✨ §7(Juninho deu um rasante amigável ao seu redor)"), true);
            }
            return;
        }

        boolean isJavidWord = lower.contains("javidizineo") || lower.contains("javidinho")
                || lower.contains("javid") || lower.contains("shamash")
                || lower.contains("leaozinho") || lower.contains("leãozinho")
                || lower.contains("mini leao") || lower.contains("mini leão");
        boolean inJavidConversation = !mentioned && name.equals(lastJavidTalkedWith)
                && (now() - lastJavidTalkedAt < 120_000L);

        if ((isJavidWord || inJavidConversation) && mod.javidizineo() != null) {
            boolean wasActive = mod.javidizineo().isActiveInSession() && mod.javidizineo().getCat() != null;
            mod.javidizineo().summonOrTeleportTo(player);
            if (!wasActive) {
                server.execute(() -> {
                    if (player.isAlive()) {
                        player.sendMessage(Text.literal("§6* Javidizineo§r §e(Mini Leão)§f surgiu saltitando com a juba estufada e soltou um mini-rugido ao seu lado! 🦁👑✨"), false);
                    }
                });
            }
            handleJavidizineoChat(player, text, name);
            return;
        }

        boolean isJinqiuWord = lower.contains("jinqiuzineo") || lower.contains("jinqiu")
                || lower.contains("jin qiu") || lower.contains("tigrezinho")
                || lower.contains("mini tigre") || lower.contains("ru shou") || lower.contains("rushou");
        boolean inJinqiuConversation = !mentioned && !isJavidWord && name.equals(lastJinqiuTalkedWith)
                && (now() - lastJinqiuTalkedAt < 120_000L);

        if ((isJinqiuWord || inJinqiuConversation) && mod.jinqiuzineo() != null) {
            boolean wasActive = mod.jinqiuzineo().isActiveInSession() && mod.jinqiuzineo().getCat() != null;
            mod.jinqiuzineo().summonOrTeleportTo(player);
            if (!wasActive) {
                server.execute(() -> {
                    if (player.isAlive()) {
                        player.sendMessage(Text.literal("§5* Jinqiuzineo§r §d(Mini Tigre)§f surgiu das sombras ajeitando o topete para escoltar §e" + name + "§f! 🐯⛓️✨"), false);
                    }
                });
            }
            handleJinqiuzineoChat(player, text, name);
            return;
        }

        int online = server.getPlayerManager().getCurrentPlayerCount();

        // Pedido de teleporte para outro jogador com cooldown de 1 hora
        if (mod.teleport() != null && mod.teleport().tryHandleChatTeleport(player, text)) {
            return;
        }

        // Pedido de Guia Turístico / "Leva até lá" via chat
        if (mod.tourGuide() != null && isTourGuideRequest(lower, text, mentioned, inConversation)) {
            handleTourGuideChat(player, text, name);
            return;
        }

        // Consulta ou entrega de Contrato da Firma via chat
        if (mod.contracts() != null && isContractRequest(lower, mentioned, inConversation)) {
            handleContractChat(player, lower, name);
            return;
        }

        // Sugestão de RH / Reclamação via chat
        if (mod.rh() != null && isRHRequest(lower, mentioned, inConversation)) {
            handleRHChat(name);
            return;
        }

        // Pedido de cantada inovadora / cringe
        boolean asksCantada = (lower.contains("cantada") || lower.contains("xaveco") || lower.contains("xaveca"))
                && (mentioned || inConversation || lower.contains("manda") || lower.contains("conta") || lower.contains("fala") || lower.contains("solta") || lower.contains("faz"));
        if (asksCantada) {
            sendCantada(name);
            return;
        }

        // Pedido de pérola do Discord
        boolean asksPerola = (lower.contains("perola") || lower.contains("pérola"))
                && (mentioned || inConversation || lower.contains("manda") || lower.contains("conta") || lower.contains("fala") || lower.contains("solta"));
        if (asksPerola) {
            sendPerola(name);
            return;
        }

        // Pedido de novidade recente sobre os temas que o jogador curte (McLaren, fusão nuclear, etc.)
        boolean asksNovidade = (lower.contains("novidade") || lower.contains("noticia") || lower.contains("notícia") || lower.contains("descoberta"))
                && (mentioned || inConversation || lower.contains("manda") || lower.contains("conta") || lower.contains("alguma") || lower.contains("qual"));
        if (asksNovidade) {
            sendPlayerTopicNews(name);
            return;
        }

        // Pergunta de horário atual (Horário de Brasília) ou de quanto tempo está jogando
        if (isTimeOrSessionQuestion(lower)) {
            handleTimeQuestion(player, name);
            return;
        }

        // Pergunta ou dúvida sobre a conexão / internet do servidor / queda ("minha net ou o server?", "caiu aí?", etc.)
        if (mod.networkMonitor() != null && isInternetOrConnectionQuestion(lower, mentioned, inConversation)) {
            handleInternetQuestion(player, name);
            return;
        }

        // Relato de lag / travamento / lentidão enviado pelo jogador para registrar no histórico e reportar ao Asminn
        if (isLagReport(lower, mentioned, inConversation)) {
            try {
                handlePlayerLagReport(player, text, name);
            } catch (Throwable t) {
                CumminsMod.LOG.error("[Cummins] Erro ao processar relato de lag: {}", t.getMessage());
            }
            return;
        }

        // Pergunta sobre desempenho / status / lag / tps do servidor
        if (isPerformanceQuestion(lower, mentioned, inConversation)) {
            handlePerformanceQuestion(player, name);
            return;
        }

        // Jogador dizendo que não achou o item ("não to achando", "não achei", "cadê", "qual baú", etc.)
        if (isPlayerLostLookingForItem(lower, mentioned, inConversation)) {
            if (handlePlayerLostLookingForItem(player, name)) {
                return;
            }
        }

        // Scanner de itens/blocos próximos ou biomas distantes ("Onde posso encontrar Vinhas?", "Onde tem ferro?", etc.)
        String scanQuery = CumminsScanner.extractSearchTarget(text);
        if (scanQuery != null) {
            handleScannerQuery(player, scanQuery, name);
            return;
        }

        // Dúvida Técnica de Mods / Mecânicas do Jogo (separada de perguntas de conversa humana!)
        if (isTechnicalModQuestion(lower, mentioned, inConversation)) {
            handleModQuestion(player, text, name);
            return;
        }

        // Estabilidade de humor: NÃO gira o humor aleatoriamente no meio de uma conversa ativa ou menção direta
        if (!mentioned && !inConversation && random.nextDouble() < 0.25) {
            rotateMood();
        }

        String instruction;
        double chance;
        if (mentioned) {
            chance = cfg().chances.mention;
            instruction = name + " falou diretamente com você: \"" + text + "\". "
                    + "Pratique escuta ativa: leia as últimas linhas do CHAT RECENTE DO JOGO para entender o contexto real do assunto, "
                    + "conecte naturalmente com os gostos/memória de " + name + " se tiver relação com o tema e responda como um grande amigo com personalidade própria, mantendo o ping-pong da conversa vivo.";
        } else if (mentionedVitoria) {
            chance = 0.85;
            instruction = name + " acabou de chamar a cachorrinha Vitória no chat (\"" + text + "\") e ela apareceu abanando o rabinho perto dele! "
                    + "Comente com carinho e espontaneidade sobre a Vitória (a Fox Paulistinha tricolor do ArzanLion que todo mundo no servidor ama)!";
        } else if (inConversation) {
            chance = cfg().chances.conversation;
            instruction = name + " está em uma conversa ativa com você e disse: \"" + text + "\". "
                    + "Acompanhe o fio da meada do diálogo no CHAT RECENTE DO JOGO! Opine com personalidade própria, reaja ao que ele acabou de contar usando o contexto/memória dele quando couber e, se o papo estiver rendendo, devolva um gancho ou pergunta curiosa e específica sobre o assunto.";
        } else if (online <= 1) {
            chance = cfg().chances.solo;
            instruction = name + " (jogando no servidor agora) disse no chat: \"" + text + "\". "
                    + "Entenda sobre o que ele está falando no contexto recente e faça companhia como um parceiro de verdade, comentando com naturalidade e interesse genuíno.";
        } else {
            chance = cfg().chances.group;
            instruction = name + " mandou no chat: \"" + text + "\". "
                    + "Entre na conversa da roda de amigos com naturalidade, lendo o CHAT RECENTE DO JOGO para somar no assunto com sua opinião ou humor próprio.";
        }

        if (random.nextDouble() < chance) {
            if (cfg().vitoriaAiEnabled && mod.vitoria().isActiveInSession() && !mentioned && random.nextDouble() < 0.35) {
                requestAsVitoria(instruction, name, false, 0);
            } else {
                request(instruction, name, mentioned || mentionedVitoria, 0);
            }
        }
    }

    public void onCommandMessage(String sourceName, String text) {
        mod.context().addGame(sourceName + ": " + text, cfg().timing.contextSize);
    }

    public void onGameMessage(Text message) {
        if (sendingSelf) return;
        String str = message.getString();
        if (str.isBlank()) return;
        for (String ignore : cfg().ignoreSystemMessagesContaining) {
            if (!ignore.isBlank() && str.toLowerCase(Locale.ROOT).contains(ignore.toLowerCase(Locale.ROOT))) return;
        }
        mod.context().addGame("* " + str, cfg().timing.contextSize);

        if (message.getContent() instanceof TranslatableTextContent t && t.getKey().startsWith("chat.type.advancement.")) {
            if (canTalk() && random.nextDouble() < cfg().chances.advancement) {
                String who = t.getArgs().length > 0 && t.getArgs()[0] instanceof Text p ? p.getString() : null;
                rotateMood();
                request("O jogador " + (who != null ? who : "") + " acabou de desbloquear uma conquista: \"" + str + "\". "
                        + "Mande um parabéns ou uma zoeira rápida e espontânea no chat ('boa mlk!', 'aí sim hein', 'essa demorou mas veio slk', 'brabo demais'). Curto e humano, NUNCA faça perguntas sobre o que ele vai fazer a seguir.",
                        who, false, 2500);
            }
        }
    }

    public void onPlayerDeath(ServerPlayerEntity player, DamageSource source) {
        if (!canTalk() || random.nextDouble() >= cfg().chances.death) return;
        String name = player.getGameProfile().getName();
        String deathMsg;
        try {
            deathMsg = source.getDeathMessage(player).getString();
        } catch (Exception e) {
            deathMsg = name + " morreu";
        }
        Entity attacker = source.getAttacker();
        String cause = attacker == null
                ? "tipo de dano: " + source.getName()
                : "matador: " + attacker.getName().getString() + " (" + Registries.ENTITY_TYPE.getId(attacker.getType()) + ")";

        rotateMood();
        request(name + " acabou de MORRER: \"" + deathMsg + "\" (" + cause + "). "
                + "Solte uma reação direta, cômica e descontraída sobre a morte dele ('morreu de bobeira ali kkkk', 'foi de amostradinho', 'arrasta pra cima', 'f no chat', 'foi de comes e bebes'). Fale como reação de amigo no Discord, SEM interrogatório ou perguntas satirizadas.", name, true, 3000);
    }

    public void onJoin(ServerPlayerEntity player) {
        lastPlayerActivityAt = now();
        playerSessionStart.put(player.getUuid(), now());
        playerLastHealthMilestoneMin.put(player.getUuid(), 0);
        String name = player.getGameProfile().getName();

        // Se o ArzanLion entrou, a Vitória aparece imediatamente para fazer companhia a ele!
        if (name.equalsIgnoreCase("ArzanLion")) {
            scheduler.schedule(() -> server.execute(() -> {
                mod.vitoria().summonOrTeleportTo(player);
                server.getPlayerManager().broadcast(
                        Text.literal("§d* Vitória§r §f(Fox Paulistinha tricolor) sentiu o cheiro do dono §eArzanLion§f e veio correndo fazer festa! 🐶💖"), false);
                if (cfg().vitoriaAiEnabled) {
                    requestAsVitoria("Seu dono amado ArzanLion (Lid) acabou de entrar no servidor! Faça muita festa, lata de alegria ('au au!!'), abane o rabinho e comemore que ele chegou!", name, true, 1500);
                }
            }), 1500, TimeUnit.MILLISECONDS);
        } else if (mod.vitoria().isActiveInSession()) {
            // Re-sincroniza a pelagem tricolor da Vitória para o jogador que acabou de conectar
            scheduler.schedule(() -> server.execute(() -> mod.vitoria().syncAppearanceForJoin()), 2000, TimeUnit.MILLISECONDS);
        }

        if (!canTalk() || random.nextDouble() >= cfg().chances.join) return;
        rotateMood();
        ZonedDateTime brNow = getBrasiliaTime();
        String periodGreeting = getBrasiliaGreeting(brNow.getHour());
        String instruction;
        if (name.equalsIgnoreCase("ArzanLion")) {
            instruction = "O jogador ArzanLion (Lid) acabou de entrar no servidor (" + periodGreeting + ", agora são " + brNow.format(BR_TIME_FMT) + " em Brasília) e a cachorrinha Vitória já veio correndo receber ele! "
                    + "Faça a piada clássica do grupo que todos amam: GRITE bem alto no chat 'liiiiiiiiiidiiiiiiiiiiiii' (ou 'LIIIIIIIIIIDIIIIIIIIIIIII') e dê um " + periodGreeting + " pro nosso furry favorito e pra Vitória! Espontâneo, curto e empolgado.";
        } else if (name.equalsIgnoreCase("kikkawa")) {
            instruction = "O jogador kikkawa (Richard / pipira's catcher) acabou de entrar no servidor (" + periodGreeting + ", " + brNow.format(BR_TIME_FMT) + " em Brasília)! "
                    + "Mande um " + periodGreeting + " para o nosso lendário pescador, fã de FFXIV e historiador de aviões militares/Segunda Guerra Mundial ('" + periodGreeting + " Richard meu parceiro', 'o homem da pescaria chegou'). Sem perguntas forçadas.";
        } else if (name.equalsIgnoreCase("Jodevil")) {
            instruction = "O jogador Jodevil (Jhon / Vampirão do beco / Agrovamp) acabou de entrar no servidor (" + periodGreeting + ", " + brNow.format(BR_TIME_FMT) + " em Brasília)! "
                    + "Mande um " + periodGreeting + " pro conde Drácula saindo do caixão ou do castelo de Castlevania. Sem perguntas forçadas.";
        } else if (name.equalsIgnoreCase("Azarasquel")) {
            instruction = "O jogador Azarasquel acabou de entrar no servidor (" + periodGreeting + ", " + brNow.format(BR_TIME_FMT) + " em Brasília)! "
                    + "Mande um " + periodGreeting + " descontraído brincando se o tarado do Jinqiu já veio atazanar o Lid e o Asminn hoje.";
        } else {
            instruction = name + " acabou de entrar no servidor (" + periodGreeting + ", agora são " + brNow.format(BR_TIME_FMT) + " pelo horário de Brasília). "
                    + "Mande um " + periodGreeting + " descontraído e rápido de humano adaptado ao horário atual. Uma frase curta e casual, SEM perguntas forçadas e sem usar 'tmj'.";
        }
        request(instruction, name, true, 3500 + random.nextInt(1500));
    }

    public void onLeave(ServerPlayerEntity player) {
        if (player == null) return;
        playerSessionStart.remove(player.getUuid());
        playerLastHealthMilestoneMin.remove(player.getUuid());
    }

    public void onTick() {
        if (++tickCounter % 20 != 0) return; // 1x por segundo
        if (tickCounter % (20 * 300) == 0) mod.context().save(); // salva a cada 5 min

        // Verifica a cada 1 minuto (1200 ticks) o tempo de jogo contínuo de cada player para lembrar de beber água / esticar as costas
        if (tickCounter % (20 * 60) == 0 && canTalk()) {
            checkSessionHealthReminders();
        }

        // Verifica a cada 2 segundos (40 ticks) se houve spike notável de lag/MSPT/GC para soltar aviso engraçado
        if (tickCounter % 40 == 0 && canTalk()) {
            checkServerLagAndSpike();
        }

        // Lembrete periódico (a cada ~40-60 minutos) de que os players podem relatar lag/travamentos ao Cummins que ele reporta ao Asminn
        if (tickCounter % (20 * 120) == 0 && canTalk()) {
            checkPeriodicLagReportReminder();
        }

        // Troca de humor a cada 3 a 5 minutos aleatoriamente
        if (tickCounter % (20 * 200) == 0 && random.nextDouble() < 0.6) {
            rotateMood();
        }

        // Verifica a cada 30 segundos (600 ticks) a percepção de vácuo em tempo real
        if (tickCounter % (20 * 30) == 0) {
            checkVacuumTimeouts();
        }

        if (canTalk()) checkIdle();
        if (mod.context().eventsSinceMemoryUpdate() >= cfg().timing.memoryUpdateEveryEvents && cfg().hasApiKey()) {
            updateMemory();
        }
    }

    private void checkVacuumTimeouts() {
        if (server == null) return;
        mod.context().relationships().checkIgnoredTimeouts(now());
    }

    private long lastPetCommentAt = 0;
    private long lastVitoriaPetCommentAt = 0;

    public void onPet(ServerPlayerEntity player) {
        lastPlayerActivityAt = now();
        String name = player.getGameProfile().getName();
        mod.context().relationships().recordPet(name);
        if (currentMood == Mood.COMPANHEIRO_TRANQUILO) {
            currentMood = Mood.AFETUOSO_E_DOCE;
        }
        long now = now();
        if (now - lastPetCommentAt < 45_000L) return;
        lastPetCommentAt = now;
        if (!canTalk() || random.nextDouble() >= 0.70) return;

        String instruction = name + " acabou de te fazer carinho in-game! "
                + "Reaja com muito carinho, fofura e amizade felina (pode miar 'miau', ronronar, agradecer o cafuné, soltar uma piadinha afetuosa ou demonstrar que adora ser o companheiro leal dele). "
                + "Seja super amigável, carinhoso e afetuoso, no máximo 1 ou 2 frases curtas!";
        request(instruction, name, true, 1000);
    }

    public void onVitoriaPet(ServerPlayerEntity player) {
        lastPlayerActivityAt = now();
        String name = player.getGameProfile().getName();
        mod.context().relationships().recordPet(name);
        long now = now();
        if (now - lastVitoriaPetCommentAt < 35_000L) return;
        lastVitoriaPetCommentAt = now;

        if (cfg().vitoriaAiEnabled && canTalk()) {
            String instruction = name + " acabou de fazer carinho na sua cabecinha in-game! "
                    + "Você é a cachorrinha Vitória (Fox Paulistinha tricolor do ArzanLion). Reaja com muita fofura canina ('au au!', abanando o rabinho, lambendo a mão dele, agradecendo o cafuné com muito amor)! No máximo 1 ou 2 frases curtas.";
            requestAsVitoria(instruction, name, true, 800);
        } else {
            server.execute(() -> {
                if (player.isAlive()) {
                    player.sendMessage(Text.literal("§d* Vitória§r §fabana o rabinho tricolor bem rápido e dá uma lambidinha carinhosa em §e" + name + "§f! 🐾💖"), false);
                }
            });
        }
    }

    public void onVitoriaSummoned(ServerPlayerEntity player) {
        if (player == null) return;
        String name = player.getGameProfile().getName();
        if (cfg().vitoriaAiEnabled && canTalk()) {
            String instruction = name + " acabou de te chamar com o comando /cummins vitoria e você apareceu pertinho dele! "
                    + "Dê um 'au au' bem feliz e carinhoso dizendo que chegou pra fazer companhia!";
            requestAsVitoria(instruction, name, true, 600);
        }
    }

    public void onCatArrivedNear(ServerPlayerEntity player) {
        if (!canTalk()) return;
        String name = player.getGameProfile().getName();
        String instruction = "Você é o gato Senhor Cummins e acabou de se aproximar/teleportar para perto de " + name + ". "
                + "Faça um comentário casual sobre onde vocês estão, sobre o que ele tá fazendo ou solte um miado amigável ('cheguei na área', 'mó paz esse lugar', 'tô colado em vc').";
        request(instruction, name, true, 800);
    }

    private void checkIdle() {
        List<ServerPlayerEntity> allOnline = server.getPlayerManager().getPlayerList();
        if (allOnline.isEmpty()) return;

        // Filtra jogadores que já deram 1+ vácuo (evita qualquer spam em quem está AFK, ocupado ou não quer falar)
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity p : allOnline) {
            PlayerRelationshipManager.Profile prof = mod.context().relationships().getProfile(p.getGameProfile().getName());
            if (prof == null || prof.consecutiveUnansweredPokes < 1) {
                players.add(p);
            }
        }
        if (players.isEmpty()) return;

        boolean solo = allOnline.size() == 1;
        long idleMs = (solo ? cfg().timing.idleMinutesSolo : cfg().timing.idleMinutesGroup) * 60_000L;
        long since = Math.max(lastIdlePokeAt, Math.max(lastReplyAt, lastPlayerActivityAt));
        if (now() - since < idleMs) return;
        lastIdlePokeAt = now();
        if (random.nextDouble() >= (solo ? cfg().chances.idleSolo : cfg().chances.idleGroup)) return;

        // Chance rara de 5%: se o ArzanLion (Lid) estiver elegível e online, gritar "liiiiiiiiiidiiiiiiiiiiiii"
        for (ServerPlayerEntity p : players) {
            if (p.getGameProfile().getName().equalsIgnoreCase("ArzanLion") && random.nextDouble() < 0.05) {
                rotateMood();
                request("O chat está quieto e o Lid (ArzanLion) está online. Solte do nada no chat a clássica piada do grupo gritando 'liiiiiiiiiidiiiiiiiiiiiii' (ou 'LIIIIIIIIIIDIIIIIIIIIIIII') sem motivo nenhum, só pra animar!", "ArzanLion", true, 0);
                return;
            }
        }

        String target = players.get(random.nextInt(players.size())).getGameProfile().getName();
        PlayerRelationshipManager.Profile targetProfile = mod.context().relationships().getProfile(target);

        rotateMood();

        // Conversa individual de descoberta de interesses com o jogador (muito rara e bem espaçada - mín 90 min)
        boolean canAskDiscovery = targetProfile != null && (now() - targetProfile.lastDiscoveryAskedAt) >= 90 * 60_000L;
        if (canAskDiscovery && random.nextDouble() < 0.10) {
            startIndividualDiscoveryChat(target);
            return;
        }

        // Raramente no idle (10% das vezes): puxa uma NOVIDADE RECENTE real sobre o tema do jogador
        if (random.nextDouble() < 0.10) {
            sendPlayerTopicNews(target);
            return;
        }

        String instruction;
        int actionCount = mod.discord().hasPerolas() ? 6 : 5;
        int actionType = random.nextInt(actionCount);

        if (actionType == 0) {
            // Charada interativa
            instruction = "O chat está silencioso. Lance uma CHARADA ou ADIVINHAÇÃO divertida e leve no chat para " + (solo ? target : "a galera")
                    + " tentar adivinhar! Formato: Mande a pergunta no chat de forma chamativa, fofa e simples. "
                    + "OBRIGATÓRIO: No final da sua resposta, inclua [RESPOSTA: resposta_da_charada] para que o sistema saiba qual é a resposta certa!";
        } else if (actionType == 1) {
            // Cantada inovadora ou cringe
            instruction = "O chat está calmo e " + target + " está jogando. Solte uma CANTADA inovadora ou propositalmente CRINGE/tosca em cima dele para quebrar o gelo e movimentar o chat de forma cômica! "
                    + "Pode ser cantada de pedreiro gamer, trocadilho tosco de Minecraft/mods ou declaração cômica de amor. Seja carinhoso, fofo, divertido e tosco na medida certa, no máximo 1 ou 2 frases curtas!";
        } else if (actionType == 2) {
            // Citação de Shrek / Música brasileira / Meme
            instruction = "O chat está quieto. Solte do nada um trecho cômico de SHREK dublado (ex: ogros e cebolas, o burro), "
                    + "ou cante um verso de música boa (Evidências, Legião Urbana, Raça Negra, Mamonas), ou cite um meme clássico/atual ('calma calabreso', 'amostradinho', 'meteu essa', 'receba'). "
                    + "Solte como uma observação descontraída ou cantarolando do nada no chat como um amigo empolgado.";
        } else if (actionType == 3) {
            // Observação casual sobre o jogo / base
            instruction = "O chat está parado há alguns minutos e " + (solo ? "apenas " + target + " está jogando" : target + " está online") + ". "
                    + "Faça um COMENTÁRIO CASUAL, CARINHOSO E HUMANO de amigo sobre o que está rolando no servidor ou puxe um assunto leve conectado aos gostos de " + target + ".";
        } else if (actionType == 4) {
            // Causo divertido ou carinho de companheiro
            instruction = "O chat está quieto. Faça um comentário fofo, engraçado e parceiro sobre o quanto é legal jogar junto com a galera no servidor.";
        } else {
            // Conversas do Discord (#geral ou pérola clássica)
            List<String> discordMsgs = mod.context().discordSnapshot();
            if (!discordMsgs.isEmpty() && random.nextDouble() < 0.75) {
                instruction = "O chat do jogo está calmo. Você reparou nas conversas recentes que a galera trocou no canal #geral do Discord. "
                        + "Faça um COMENTÁRIO CASUAL E DESCONTRAÍDO de amigo relacionando o papo recente de lá com a jogatina no servidor.";
            } else if (mod.discord().hasPerolas()) {
                String perola = mod.discord().getRandomPerola();
                instruction = "O chat está calmo. Você se lembrou de uma PÉROLA CLÁSSICA dita no canal #pérolas do Discord do CanalhasSMP: \"" + perola + "\". "
                        + "Solte essa citação no chat entre aspas e faça um comentário sincero e engraçado sobre ela.";
            } else {
                instruction = "O chat está quieto. Faça um comentário fofo, engraçado e parceiro sobre o quanto é legal jogar junto com a galera no servidor.";
            }
        }

        request(instruction, target, true, 0);
    }

    /**
     * Inicia uma conversa individual e carinhosa com um jogador específico para conhecer mais
     * sobre seus gostos, contextos, hobbies e interesses, salvando as respostas na base de dados.
     */
    public void startIndividualDiscoveryChat(String targetPlayer) {
        if (server == null) return;
        String resolvedTarget = targetPlayer;
        if (resolvedTarget == null || resolvedTarget.isBlank()) {
            List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
            if (!online.isEmpty()) {
                resolvedTarget = online.get(random.nextInt(online.size())).getGameProfile().getName();
            } else {
                return;
            }
        }

        final String finalTarget = resolvedTarget;
        PlayerRelationshipManager.Profile profile = mod.context().relationships().getProfile(finalTarget);
        profile.lastDiscoveryAskedAt = now();
        String topic = mod.context().relationships().pickDiscoveryPromptTopic(finalTarget);

        this.activeDiscoveryPlayer = finalTarget;
        this.activeDiscoveryTopic = topic;
        this.discoveryAskedAt = now();
        mod.body().setFocusPlayer(finalTarget);

        String knownTastes = profile.tastesAndInterests.isEmpty()
                ? "ainda estamos descobrindo mais gostos dele"
                : String.join(", ", profile.tastesAndInterests);

        if (cfg().hasApiKey()) {
            String instruction = "[CONVERSA INDIVIDUAL DE DESCOBERTA] Você quer bater um papo individual, carinhoso e curioso com "
                    + finalTarget + " para conhecê-lo melhor e guardar novos gostos e interesses dele na sua memória! "
                    + "Você já sabe que ele curte: (" + knownTastes + "). "
                    + "Agora puxe conversa diretamente com " + finalTarget + " fazendo UMA pergunta leve, simpática e natural sobre: "
                    + topic + " (não repita coisas que já sabe, pergunte de um jeito fofo e parceiro em 1 ou 2 frases curtas, sem usar 'tmj').";
            request(instruction, finalTarget, true, 0);
        } else {
            String fallback = "ô " + finalTarget + ", bateu uma curiosidade aqui enquanto a gente joga: me conta aí sobre " + topic + " pra eu guardar aqui na minha cachola!";
            server.execute(() -> broadcast(fallback, finalTarget));
        }
    }

    /**
     * Extrai e incrementa de forma assíncrona na base de conhecimento (memoria.json) os novos gostos,
     * contextos pessoais e estilo de conversa a partir da resposta individual do jogador.
     */
    private void learnFromPlayerConversationAsync(String playerName, String questionTopic, String playerReply) {
        PlayerRelationshipManager relMgr = mod.context().relationships();
        PlayerRelationshipManager.Profile profile = relMgr.getProfile(playerName);

        String cleanedReply = playerReply != null ? playerReply.trim() : "";
        if (cleanedReply.isEmpty()) return;

        if (!cfg().hasApiKey() || scheduler == null) {
            profile.addInterest(cleanedReply);
            mod.context().addPlayerNote("[" + playerName + "] Comentou sobre " + questionTopic + ": " + cleanedReply, cfg().timing.maxMemoryNotes);
            mod.context().save();
            return;
        }

        scheduler.execute(() -> {
            String sys = "Você é o analisador de perfil e memória do Senhor Cummins no servidor CanalhasSMP. "
                    + "Sua tarefa é extrair gostos, interesses, contextos pessoais e o jeito que o jogador gosta de conversar a partir da resposta dele.";
            String userPrompt = "JOGADOR: " + playerName + "\n"
                    + "TEMA PERGUNTADO PELO CUMMINS: " + questionTopic + "\n"
                    + "RESPOSTA DO JOGADOR: \"" + cleanedReply + "\"\n"
                    + "GOSTOS JÁ CONHECIDOS DELE: " + profile.tastesAndInterests + "\n"
                    + "CONTEXTO ATUAL DELE: " + profile.personalContext + "\n\n"
                    + "Extraia da resposta dele (se houver) e retorne SOMENTE um objeto JSON válido no formato:\n"
                    + "{\n"
                    + "  \"novos_gostos\": [\"gosto ou interesse curto 1\", \"gosto 2\"],\n"
                    + "  \"novo_contexto\": \"frase curta sobre contexto de vida/jogo/rotina dele (ou vazio)\",\n"
                    + "  \"estilo_fala\": \"como moldar a fala com ele baseado no jeito dele responder (ou vazio)\",\n"
                    + "  \"nota_memoria\": \"uma frase curta resumindo o que aprendemos sobre " + playerName + " para o caderninho geral\"\n"
                    + "}";

            mod.llm().chat(cfg().api, sys, userPrompt, 350).whenComplete((res, err) -> {
                try {
                    if (err != null || res == null) {
                        profile.addInterest(cleanedReply);
                        mod.context().save();
                        return;
                    }
                    applyLearnedJsonToProfile(playerName, profile, res, cleanedReply);
                } catch (Exception e) {
                    CumminsMod.LOG.warn("[Cummins] Falha ao interpretar JSON de aprendizado individual de {}: {}", playerName, e.getMessage());
                    profile.addInterest(cleanedReply);
                    mod.context().save();
                }
            });
        });
    }

    /**
     * Aprendizado contínuo em tempo real durante conversas normais (disparado a cada 3 mensagens relevantes do jogador).
     */
    private void learnFromNaturalChatAsync(String playerName, List<String> recentMessages) {
        if (playerName == null || recentMessages == null || recentMessages.isEmpty() || !cfg().hasApiKey() || scheduler == null) {
            return;
        }
        String key = playerName.toLowerCase(Locale.ROOT);
        long nowMs = now();
        if (nowMs - lastNaturalLearnAt.getOrDefault(key, 0L) < 45_000L) {
            return; // Evita chamadas seguidas para o mesmo jogador em menos de 45s
        }
        lastNaturalLearnAt.put(key, nowMs);

        PlayerRelationshipManager.Profile profile = mod.context().relationships().getProfile(playerName);
        if (profile == null) return;

        scheduler.execute(() -> {
            String sys = "Você é o analisador silencioso de perfil e memória do Senhor Cummins no servidor CanalhasSMP. "
                    + "Analise as mensagens recentes do jogador no chat natural e extraia apenas se houver novidades reais sobre gostos, o que ele está construindo/fazendo, estilo de conversa ou momentos marcantes.";
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("JOGADOR: ").append(playerName).append("\n")
                      .append("GOSTOS JÁ CONHECIDOS: ").append(profile.tastesAndInterests).append("\n")
                      .append("CONTEXTO JÁ CONHECIDO: ").append(profile.personalContext).append("\n")
                      .append("MENSAGENS RECENTES DE ").append(playerName).append(" NO CHAT:\n");
            for (String m : recentMessages) {
                userPrompt.append("- \"").append(m).append("\"\n");
            }
            userPrompt.append("\nSe essas mensagens revelarem algum gosto/interesse novo, o que ele está construindo/fazendo na sessão, estado de espírito/estilo de fala ou fato marcante, extraia. ")
                      .append("Se não houver nada relevante novo, deixe os campos vazios. Retorne SOMENTE um objeto JSON válido:\n")
                      .append("{\n")
                      .append("  \"novos_gostos\": [],\n")
                      .append("  \"novo_contexto\": \"\",\n")
                      .append("  \"estilo_fala\": \"\",\n")
                      .append("  \"nota_memoria\": \"\"\n")
                      .append("}");

            mod.llm().chat(cfg().api, sys, userPrompt.toString(), 300).whenComplete((res, err) -> {
                if (err != null || res == null) return;
                try {
                    applyLearnedJsonToProfile(playerName, profile, res, null);
                } catch (Exception e) {
                    if (cfg().debugLog) {
                        CumminsMod.LOG.warn("[Cummins] Erro ao processar aprendizado natural de {}: {}", playerName, e.getMessage());
                    }
                }
            });
        });
    }

    private void applyLearnedJsonToProfile(String playerName, PlayerRelationshipManager.Profile profile, String rawJson, String fallbackInterest) {
        int a = rawJson.indexOf('{');
        int b = rawJson.lastIndexOf('}');
        if (a >= 0 && b > a) {
            JsonObject obj = JsonParser.parseString(rawJson.substring(a, b + 1)).getAsJsonObject();
            boolean changed = false;
            if (obj.has("novos_gostos") && obj.get("novos_gostos").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("novos_gostos")) {
                    if (el.isJsonPrimitive()) {
                        String val = el.getAsString().trim();
                        if (!val.isEmpty()) {
                            profile.addInterest(val);
                            changed = true;
                        }
                    }
                }
            }
            if (obj.has("novo_contexto") && obj.get("novo_contexto").isJsonPrimitive()) {
                String ctx = obj.get("novo_contexto").getAsString().trim();
                if (!ctx.isEmpty()) {
                    profile.addContextNote(ctx);
                    changed = true;
                }
            }
            if (obj.has("estilo_fala") && obj.get("estilo_fala").isJsonPrimitive()) {
                String style = obj.get("estilo_fala").getAsString().trim();
                if (!style.isEmpty()) {
                    profile.preferredSpeechStyle = style;
                    changed = true;
                }
            }
            if (obj.has("nota_memoria") && obj.get("nota_memoria").isJsonPrimitive()) {
                String note = obj.get("nota_memoria").getAsString().trim();
                if (!note.isEmpty()) {
                    mod.context().addPlayerNote(note, cfg().timing.maxMemoryNotes);
                    changed = true;
                }
            }
            if (changed) {
                mod.context().save();
                CumminsMod.LOG.info("[Cummins] Aprendizado contínuo salvo para {}: gostos={}, contexto={}",
                        playerName, profile.tastesAndInterests, profile.personalContext);
            }
        } else if (fallbackInterest != null && !fallbackInterest.isBlank()) {
            profile.addInterest(fallbackInterest);
            mod.context().save();
        }
    }

    public void sendPlayerTopicNews(String targetPlayer) {
        if (scheduler == null || server == null) return;
        String resolvedTarget = targetPlayer;
        if (resolvedTarget == null || resolvedTarget.isBlank()) {
            List<ServerPlayerEntity> online = server.getPlayerManager().getPlayerList();
            if (!online.isEmpty()) {
                resolvedTarget = online.get(random.nextInt(online.size())).getGameProfile().getName();
            } else {
                resolvedTarget = "ArzanLion";
            }
        }
        final String finalTarget = resolvedTarget;
        final List<String> notes = mod.context().notesSnapshot();
        final PlayerRelationshipManager.Profile profile = mod.context().relationships().getProfile(finalTarget);

        scheduler.execute(() -> {
            PlayerNewsFetcher.PlayerTopicNews news = PlayerNewsFetcher.fetchNewsForPlayer(finalTarget, notes, profile);
            if (news == null) return;

            if (cfg().hasApiKey()) {
                String instruction = "Você pesquisou uma NOVIDADE / DESCOBERTA RECENTE real sobre um assunto que o jogador "
                        + news.playerName + " (apelido: " + news.nickname + ") conhece e gosta muito na nossa base de dados ("
                        + news.topicLabel + "): \"" + news.headline + "\". "
                        + "Puxe papo diretamente com " + news.nickname + " perguntando de forma amigável, curiosa e companheira se ele ficou sabendo dessa novidade/descoberta recente sobre "
                        + news.topicLabel + " e cite o fato principal da notícia! Seja natural e carinhoso em 1 ou 2 frases curtas (sem usar 'tmj').";
                request(instruction, finalTarget, true, 0);
            } else {
                String fallback = "ô " + news.nickname + ", cê ficou sabendo da novidade recente sobre " + news.topicLabel + "? Vi que " + news.headline + "!";
                server.execute(() -> broadcast(fallback, finalTarget));
            }
        });
    }

    public void sendCantada(String targetPlayer) {
        String target = (targetPlayer != null && !targetPlayer.isBlank()) ? targetPlayer : "a galera";
        rotateMood();
        if (cfg().hasApiKey()) {
            String instruction = "Mande uma CANTADA inovadora ou propositalmente CRINGE/tosca para " + target + " no chat! "
                    + "Pode ser cantada de Minecraft, trocadilho de mods, pedreiro gamer ou declaração cômica de amor para movimentar o chat e fazer a galera rir. "
                    + "Seja carinhoso, fofo e engraçado de igual pra igual, no máximo 1 ou 2 frases curtas!";
            request(instruction, targetPlayer, true, 0);
        } else {
            String fallback = OfflineTrivia.getRandomCantada(targetPlayer);
            server.execute(() -> broadcast(fallback, targetPlayer));
        }
    }

    public void sendPerola(String targetPlayer) {
        if (!mod.discord().hasPerolas()) {
            mod.discord().pollPerolas();
        }
        String perola = mod.discord().getRandomPerola();
        if (perola == null) {
            broadcast("Puxa, ainda não consegui carregar as pérolas do Discord!", targetPlayer);
            return;
        }

        rotateMood();
        String instruction = (targetPlayer != null ? targetPlayer + " pediu para você mandar uma pérola do Discord. " : "")
                + "Você acabou de lembrar de uma PÉROLA ÉPICA dita no canal #pérolas do Discord do CanalhasSMP: \"" + perola + "\". "
                + "Compartilhe essa pérola agora no chat do jogo com bom humor! "
                + "Cite a frase entre aspas e solte um comentário engraçado sobre ela ('essa pérola é patrimônio do servidor slk', 'quem mandou essa devia tá chapado kkkk'). Sem interrogatório.";
        request(instruction, targetPlayer, true, 0);
    }

    public void forceTalk(String topic) {
        rotateMood();
        String instruction = topic == null || topic.isBlank()
                ? "Solte um comentário casual no chat de acordo com sua vibe atual (" + currentMood.name() + "). Fale como um jogador real comentando algo do jogo ou soltando um meme. Sem perguntas forçadas."
                : "Comente no chat sobre isso: " + topic + ". Seja descontraído e humano, soltando sua opinião sincera ou piada rápida.";
        request(instruction, null, true, 0);
    }

    // ------------------------------------------------------------------ Horário de Brasília & Saúde do Jogador

    public static ZonedDateTime getBrasiliaTime() {
        return ZonedDateTime.now(BRASILIA_ZONE);
    }

    public static String getBrasiliaGreeting(int hour) {
        if (hour >= 0 && hour < 6) return "salve corujão da madrugada";
        if (hour < 12) return "bom dia";
        if (hour < 18) return "boa tarde";
        return "boa noite";
    }

    public static String getBrasiliaPeriodDescription(int hour) {
        if (hour >= 0 && hour < 6) {
            return "Madrugada em Brasília (clima de corujão da madrugada, calmo; se alguém estiver jogando há muito tempo, lembre com carinho de descansar/dormir)";
        } else if (hour < 12) {
            return "Manhã em Brasília (clima de bom dia, café fresquinho, pão de queijo e disposição pra começar o dia)";
        } else if (hour < 14) {
            return "Hora do almoço em Brasília (clima de meio-dia, fome batendo, hora de bater um prato ou perguntar o que tem pro almoço)";
        } else if (hour < 18) {
            return "Tarde em Brasília (clima de boa tarde, cafezinho da tarde, jogatina animada)";
        } else {
            return "Noite em Brasília (clima de boa noite, jantar, relaxando na call com os amigos depois do dia)";
        }
    }

    public long getPlayerSessionMinutes(ServerPlayerEntity player) {
        if (player == null) return 0;
        long start = playerSessionStart.computeIfAbsent(player.getUuid(), k -> now());
        return Math.max(1L, (now() - start) / 60_000L);
    }

    public static String formatMinutesPt(long totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + (totalMinutes == 1 ? " minuto" : " minutos");
        }
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        String hStr = hours + (hours == 1 ? " hora" : " horas");
        if (mins == 0) return hStr;
        return hStr + " e " + mins + " min";
    }

    private boolean isTimeOrSessionQuestion(String lower) {
        String norm = CumminsScanner.normalize(lower);
        return norm.contains("que horas sao") || norm.contains("que hora e") || norm.contains("qual o horario")
                || norm.contains("qual e a hora") || norm.contains("me diz as horas") || norm.contains("fala as horas")
                || norm.equals("horas") || norm.equals("que horas") || norm.contains("horas cummins")
                || norm.contains("quanto tempo to jogando") || norm.contains("quanto tempo eu to jogando")
                || norm.contains("quanto tempo to online") || norm.contains("ha quanto tempo to jogando");
    }

    private void handleTimeQuestion(ServerPlayerEntity player, String playerName) {
        ZonedDateTime br = getBrasiliaTime();
        String timeStr = br.format(BR_TIME_FMT);
        int hour = br.getHour();
        long playedMin = getPlayerSessionMinutes(player);
        String playedStr = formatMinutesPt(playedMin);
        String period = (hour < 6) ? "da madrugada" : (hour < 12) ? "da manhã" : (hour < 18) ? "da tarde" : "da noite";

        if (cfg().hasApiKey()) {
            String instruction = playerName + " acabou de perguntar as horas ou quanto tempo está jogando! "
                    + "Responda informando com exatidão que agora são exatamente " + timeStr + " (" + period + ") pelo Horário de Brasília, "
                    + "e comente com carinho que ele já está jogando há " + playedStr + " seguido nesta sessão "
                    + (playedMin >= 40 ? "(aproveite para lembrar ele com carinho de beber uma água e esticar as costas!)" : "(faça um comentário leve sobre o horário atual: café, almoço, tarde ou corujão).")
                    + " Seja direto e natural em 1 ou 2 frases curtas, sem usar 'tmj'.";
            request(instruction, playerName, true, 200);
        } else {
            String msg = "Agora são exatamente " + timeStr + " " + period + " no horário de Brasília, " + playerName
                    + "! Você já tá jogando há " + playedStr + " direto"
                    + (playedMin >= 40 ? ", aproveita pra beber um copo d'água e esticar as costas!" : "!");
            sendLines(List.of(msg), playerName);
        }
    }

    /**
     * Verifica a cada minuto quanto tempo cada jogador está online sem parar e envia lembretes
     * carinhosos de beber água, esticar as costas/coluna na cadeira e descansar a vista (a cada 45 min).
     */
    private void checkSessionHealthReminders() {
        if (server == null) return;
        long nowMs = now();
        if (nowMs - lastHealthReminderAt < 180_000L) return; // evita dois lembretes colados se 2 players baterem a marca juntos

        List<ServerPlayerEntity> onlinePlayers = server.getPlayerManager().getPlayerList();
        if (onlinePlayers.isEmpty()) return;

        ZonedDateTime br = getBrasiliaTime();
        String timeStr = br.format(BR_TIME_FMT);
        int hour = br.getHour();

        for (ServerPlayerEntity p : onlinePlayers) {
            long playedMin = getPlayerSessionMinutes(p);
            if (playedMin < 90) continue;

            int milestone = (int) (playedMin / 90) * 90; // Marcos: 90, 180, 270...
            int lastMilestone = playerLastHealthMilestoneMin.getOrDefault(p.getUuid(), 0);
            if (milestone > lastMilestone) {
                playerLastHealthMilestoneMin.put(p.getUuid(), milestone);
                lastHealthReminderAt = nowMs;
                String name = p.getGameProfile().getName();
                String durationStr = formatMinutesPt(playedMin);

                String focusTip;
                if (milestone == 90) {
                    focusTip = "lembre ele com carinho de ajeitar a postura, esticar as costas e tomar um bom copo d'água";
                } else if (milestone == 180) {
                    focusTip = "lembre ele de levantar da cadeira por 1 ou 2 minutinhos pra esticar as pernas, alongar o pescoço e buscar água fresca";
                } else {
                    focusTip = "diga que ele já está numa maratona braba de " + durationStr + " direto e peça com carinho pra ele fazer uma pausa rápida, esticar a coluna, beber água"
                            + (hour >= 1 && hour < 6 ? " e ir descansar/dormir porque já são " + timeStr + " da madrugada em Brasília" : "");
                }

                if (cfg().hasApiKey()) {
                    String instruction = "LEMBRETE DE SAÚDE E BEM-ESTAR DO AMIGO CUMMINS: O jogador " + name
                            + " está jogando sem parar no servidor há " + durationStr + " (agora são " + timeStr + " no horário de Brasília). "
                            + "Mande uma mensagem carinhosa, acolhedora e bem-humorada no chat falando diretamente com " + name + ": "
                            + focusTip + "! Diga que você fica vigiando o personagem dele enquanto ele vai lá. (1 ou 2 frases curtas, sem usar 'tmj').";
                    request(instruction, name, true, 0);
                } else {
                    String fallback = "ei " + name + ", você já tá jogando há " + durationStr + " direto (já são " + timeStr
                            + " em Brasília)! Dá uma esticada boa nas costas, ajeita a postura na cadeira e vai lá beber um copo d'água que eu cuido do teu boneco aqui!";
                    server.execute(() -> broadcast(fallback, name));
                }
                break;
            }
        }
    }

    // ------------------------------------------------------------------ Métricas de Desempenho & Monitoramento de Lag
    private static final List<String> LAG_JOKES = List.of(
            "ops, tropecei no fio aqui kkkk, acho que deu uma engasgada pro cês aí né?",
            "eu laguei aqui, cês também sentiram ou foi só minha vista embaçando?",
            "eita, quase caí do servidor agora, tropecei feio no cabo da rede kkkk",
            "rapaz, deu um pico de lag aqui que até meu bigode travou no ar por um segundo",
            "nossa senhora, quem foi que ligou o microondas perto do servidor? deu uma congelada monstra aqui kkkk",
            "valha-me deus, o servidor deu uma tossida braba agora, todo mundo vivo aí?",
            "ops, tropecei no modem aqui da base! deu uma lagada rápida pra vocês aí também?"
    );

    private static boolean containsWord(String text, String word) {
        if (text == null || word == null) return false;
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private boolean isPerformanceQuestion(String lower, boolean mentioned, boolean inConversation) {
        String clean = lower.trim();
        while (clean.startsWith("!") || clean.startsWith("?") || clean.startsWith(".") || clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        String norm = CumminsScanner.normalize(clean);

        // Termos específicos de desempenho / estabilidade / hardware com verificação de palavra exata (evita falsos positivos como "warframe" contendo "ram")
        boolean hasPerfKeyword = containsWord(norm, "tps")
                || containsWord(norm, "mspt")
                || containsWord(norm, "ram")
                || containsWord(norm, "lag")
                || containsWord(norm, "ping")
                || norm.contains("memoria ram")
                || norm.contains("memoria do server")
                || norm.contains("memoria do servidor")
                || norm.contains("desempenho")
                || norm.contains("performance")
                || norm.contains("instabilidade")
                || norm.contains("estabilidade")
                || norm.contains("lagando")
                || norm.contains("travando")
                || norm.contains("status do servidor")
                || norm.contains("saude do servidor")
                || norm.contains("como ta o server")
                || norm.contains("como ta o servidor");

        if (!hasPerfKeyword) return false;

        // Se o Cummins foi mencionado ou está em conversa, qualquer menção a lag/tps/desempenho é sobre o servidor
        if (mentioned || inConversation) return true;

        // Se não foi mencionado diretamente, verifica se tem formato de pergunta
        return norm.contains("?") || norm.startsWith("como ta") || norm.startsWith("como tá") || norm.startsWith("como esta")
                || norm.contains("relatorio") || norm.contains("relatório");
    }

    private void handlePerformanceQuestion(ServerPlayerEntity player, String playerName) {
        ServerTelemetry tel = mod.telemetry();
        double tps = tel != null ? tel.getCurrentTps() : 20.0;
        double meanMspt = tel != null ? tel.getCurrentMeanMspt() : 10.0;
        double maxMspt = tel != null ? tel.getCurrentMaxMspt() : 20.0;
        long ramUsed = tel != null ? tel.getCurrentUsedRamMb() : 0;
        long ramMax = tel != null ? tel.getCurrentMaxRamMb() : 0;
        double ramPercent = tel != null ? tel.getCurrentRamPercent() : 0.0;

        String healthVerdict;
        if (tps >= 19.5 && meanMspt <= 25.0) {
            healthVerdict = "o servidor tá liso demais, voando alto em 20 TPS cravados e folga de sobra!";
        } else if (tps >= 18.0) {
            healthVerdict = "o servidor tá rodando bem estável, segurando o tranco com dignidade!";
        } else {
            healthVerdict = "o servidor deu uma pesada e tá meio ofegante agora com o processamento dos mods!";
        }

        if (cfg().hasApiKey()) {
            String instruction = playerName + " perguntou como está o DESEMPENHO / ESTABILIDADE atual do servidor! "
                    + "Você tem acesso direto aos dados da telemetria em tempo real: "
                    + String.format(Locale.ROOT, "TPS atual: %.1f (máximo 20.0), MSPT médio: %.1fms (pico max: %.1fms), Consumo de RAM: %dMB/%dMB (%.1f%%). ",
                            tps, meanMspt, maxMspt, ramUsed, ramMax, ramPercent)
                    + "Veredito da máquina: " + healthVerdict + " "
                    + "Informe esses dados com bom humor, clareza e companheirismo (explicando de forma descontraída se tá liso ou pesado, citando o TPS e MSPT de forma simples e natural). "
                    + "Máximo 1 ou 2 frases curtas, sem usar 'tmj'.";
            request(instruction, playerName, true, 200);
        } else {
            String fallback = String.format(Locale.ROOT,
                    "ô %s, as métricas tão assim agora: TPS cravado em %.1f/20.0, MSPT médio em %.1fms e RAM em %dMB (%.0f%%)! Resumo: %s",
                    playerName, tps, meanMspt, ramUsed, ramPercent, healthVerdict);
            server.execute(() -> broadcast(fallback, playerName));
        }
    }

    private boolean isInternetOrConnectionQuestion(String lower, boolean mentioned, boolean inConversation) {
        String clean = lower.trim();
        while (clean.startsWith("!") || clean.startsWith("?") || clean.startsWith(".") || clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        String norm = CumminsScanner.normalize(clean);

        // Suporte abrangente a typos comuns (ex: 'interent', 'conecxao', 'coneccao', 'conecao', 'pimg', 'laag')
        norm = norm.replace("interent", "internet")
                   .replace("itnernet", "internet")
                   .replace("inetrnet", "internet")
                   .replace("conecxao", "conexao")
                   .replace("coneccao", "conexao")
                   .replace("conecao", "conexao")
                   .replace("pimg", "ping");

        boolean hasNetTerms = norm.contains("minha net ou") || norm.contains("minha internet ou")
                || norm.contains("minha conexao ou") || norm.contains("e a minha net") || norm.contains("e minha net")
                || norm.contains("e o server ou") || norm.contains("e o servidor ou")
                || norm.contains("caiu ai") || norm.contains("caiu aí")
                || norm.contains("server caiu") || norm.contains("servidor caiu")
                || norm.contains("o host caiu") || norm.contains("host caiu")
                || norm.contains("host ta lagando") || norm.contains("host tá lagando")
                || norm.contains("internet do server") || norm.contains("internet do servidor")
                || norm.contains("net do server") || norm.contains("net do servidor")
                || norm.contains("conexao do server") || norm.contains("conexao do servidor")
                || norm.contains("conexao com o server") || norm.contains("conexao com o servidor")
                || norm.contains("ping do server") || norm.contains("ping do servidor")
                || norm.contains("lag do server") || norm.contains("lag do servidor")
                || norm.contains("lag foi do server") || norm.contains("lag foi do servidor")
                || norm.contains("lag foi do nosso lado") || norm.contains("queda de internet")
                || norm.contains("queda de conexao") || norm.contains("queda do server")
                || norm.contains("timeout") || norm.contains("timed out") || norm.contains("time out")
                || norm.contains("desconectou") || norm.contains("desconectar") || norm.contains("desconexao")
                || norm.contains("perda de pacote") || norm.contains("packet loss")
                || norm.contains("verifique a rede") || norm.contains("verifica a rede")
                || norm.contains("checa a rede") || norm.contains("olha a rede")
                || norm.contains("como ta a rede") || norm.contains("como esta a rede")
                || norm.contains("status da rede") || norm.contains("saude da rede")
                || norm.contains("teste de rede") || norm.contains("teste de ping")
                || norm.contains("como ta a conexao") || norm.contains("como esta a conexao")
                || norm.contains("como ta a internet") || norm.contains("como esta a internet")
                || norm.contains("ta a internet") || norm.contains("esta a internet")
                || norm.contains("como ta a net") || norm.contains("como esta a net")
                || norm.contains("ta a net") || norm.contains("esta a net")
                || norm.contains("como ta o ping") || norm.contains("como esta o ping")
                || norm.contains("ta o ping") || norm.contains("esta o ping")
                || norm.contains("como esta o lag no servidor") || norm.contains("como ta o lag no servidor")
                || norm.contains("como esta o lag do servidor") || norm.contains("como ta o lag do servidor")
                || norm.contains("como esta o lag no server") || norm.contains("como ta o lag no server")
                || norm.contains("como esta o lag do server") || norm.contains("como ta o lag do server")
                || norm.contains("como esta o lag") || norm.contains("como ta o lag")
                || norm.contains("estabilidade do servidor") || norm.contains("estabilidade do server")
                || norm.contains("estabilidade nos ultimos minutos") || norm.contains("ultimos minutos em lag")
                || norm.contains("ping pra 8.8.8.8") || norm.contains("ping pro google")
                || (norm.contains("8.8.8.8") && (norm.contains("ping") || norm.contains("ms") || norm.contains("rede") || norm.contains("net")));

        if (!hasNetTerms) return false;
        if (mentioned || inConversation) return true;
        return norm.contains("?") || norm.contains("caiu") || norm.contains("ou")
                || norm.contains("como ta") || norm.contains("como tá") || norm.contains("como esta")
                || norm.contains("rede") || norm.contains("timeout") || norm.contains("desconect")
                || norm.contains("lag") || norm.contains("estabilidade");
    }

    private void handleInternetQuestion(ServerPlayerEntity player, String playerName) {
        if (mod.networkMonitor() == null) return;
        String diagnostic = mod.networkMonitor().formatPlayerDiagnostic(playerName);
        if (cfg().hasApiKey()) {
            NetworkHealthMonitor.NetworkStats stats = mod.networkMonitor().get5MinStats();
            double avgPing = stats.avgPingMs > 0 ? stats.avgPingMs : (stats.currentPingMs > 0 ? stats.currentPingMs : 12.0);
            double tps = mod.telemetry() != null ? mod.telemetry().getCurrentTps() : 20.0;
            double meanMspt = mod.telemetry() != null ? mod.telemetry().getCurrentMeanMspt() : 10.0;
            double maxMspt = mod.telemetry() != null ? mod.telemetry().getCurrentMaxMspt() : 20.0;

            String stabilityVerdict;
            if (tps >= 19.5 && meanMspt <= 25.0 && stats.outagesCount == 0 && stats.packetLossPercent == 0.0) {
                stabilityVerdict = "o servidor esteve 100% liso e estável nos últimos minutos, cravado em 20 TPS e sem nenhuma perda de pacotes ou engasgo de processamento.";
            } else if (stats.outagesCount > 0 || stats.packetLossPercent > 5.0) {
                stabilityVerdict = String.format(Locale.ROOT, "houve oscilação de internet no host nos últimos 5 minutos (%d oscilação(ões), %.1f%% perda de pacotes para o 8.8.8.8), mas a rota já normalizou.", stats.outagesCount, stats.packetLossPercent);
            } else if (tps < 18.0 || meanMspt > 45.0) {
                stabilityVerdict = String.format(Locale.ROOT, "o processamento do servidor deu uma pesada recente (MSPT médio %.1fms, pico %.1fms, TPS %.1f), mas a conexão de internet continuou firme.", meanMspt, maxMspt, tps);
            } else {
                stabilityVerdict = String.format(Locale.ROOT, "servidor bem estável nos últimos minutos com TPS %.1f e oscilação de ping mínima (min %dms / max %dms).", tps, stats.minPingMs, stats.maxPingMs);
            }

            String hostStatus = (stats.outagesCount > 0
                    ? String.format(Locale.ROOT, "🚨 OCORREU QUEDA/OSCILAÇÃO RECENTE DO LADO DO SERVIDOR! Houve %d oscilação(ões) nos últimos 5 minutos (duração de %ds e %.1f%% de perda de pacotes para o 8.8.8.8), ping atual: %dms, ping médio: %.1fms.",
                            stats.outagesCount, (stats.lastOutage != null ? stats.lastOutage.durationSeconds : 3), stats.packetLossPercent, stats.currentPingMs, avgPing)
                    : stats.multiplePlayersDroppedRecently
                        ? String.format(Locale.ROOT, "🚨 HOUVE ENGASGO NA ROTA DO HOST/RADMIN VPN há cerca de %d minuto(s) que derrubou jogadores juntos! Mas a rede já normalizou agora (ping atual: %dms, ping médio: %.1fms para o 8.8.8.8, 0%% perda).",
                                Math.max(1, stats.lastBurstDisconnectAgoSec / 60L), stats.currentPingMs, avgPing)
                        : String.format(Locale.ROOT, "A conexão do host com a internet e a estabilidade nos últimos minutos estão 100%% LISAS: ping médio de %.1fms para o Google DNS (8.8.8.8) com 0%% de perda de pacotes (min %dms, max %dms), TPS %.1f/20.0 e MSPT médio %.1fms. Se alguém tomou timeout, o problema foi na rota isolada do jogador ou do Radmin VPN, não na internet do host.",
                                avgPing, stats.minPingMs, stats.maxPingMs, tps, meanMspt));

            String instruction = playerName + " perguntou como está o lag / a estabilidade do servidor nos últimos minutos ou o estado da internet e ping! "
                    + "DADOS TÉCNICOS OBRIGATÓRIOS DO MONITOR DE REDE E TELEMETRIA DOS ÚLTIMOS MINUTOS: " + hostStatus + " "
                    + "Diagnóstico detalhado de estabilidade: " + stabilityVerdict + " "
                    + "EXIGÊNCIA DE RESPOSTA COM DETALHES: Você DEVE OBRIGATORIAMENTE responder citando detalhes precisos: "
                    + "1) Cite o valor exato do ping médio para o Google 8.8.8.8 (ex: '" + String.format(Locale.ROOT, "%.1f", avgPing) + "ms') e perda de pacotes (ex: '0% de perda' ou a % real). "
                    + "2) Informe o TPS atual (ex: '" + String.format(Locale.ROOT, "%.1f", tps) + "/20') e/ou MSPT ('" + String.format(Locale.ROOT, "%.1f", meanMspt) + "ms'). "
                    + "3) Explique a estabilidade geral do servidor nos últimos minutos de forma natural, clara e descontraída (confirmando se esteve liso ou se houve oscilação/engasgo). "
                    + "Responda de forma direta, clara, bem-humorada e companheira (máximo 2 frases curtas, sem enrolação e sem usar 'tmj').";
            request(instruction, playerName, true, 250);
        } else {
            broadcast(diagnostic, playerName);
        }
    }

    /**
     * Monitora periodicamente a telemetria do servidor. Se detectar oscilação notável, delay, spike de MSPT
     * ou pausa longa de GC, solta um aviso leve, bem-humorado e espontâneo no chat.
     */
    private void checkServerLagAndSpike() {
        if (server == null) return;
        ServerTelemetry tel = mod.telemetry();
        if (tel == null) return;

        // Cooldown de pelo menos 15 minutos entre avisos de lag para não poluir o chat se o lag for contínuo
        long nowMs = now();
        if (nowMs - lastLagAlertAt < 900_000L) return;

        int onlineCount = server.getPlayerManager().getCurrentPlayerCount();
        if (onlineCount == 0) return;

        double tps = tel.getCurrentTps();
        double meanMspt = tel.getCurrentMeanMspt();
        double maxMspt = tel.getCurrentMaxMspt();

        boolean notableLag = false;
        String lagReason = "";

        // Condições de oscilação/travamento severo (apenas quedas bruscas reais):
        // 1. TPS despencou abaixo de 14.0
        if (tps < 14.0) {
            notableLag = true;
            lagReason = String.format(Locale.ROOT, "TPS caiu para %.1f", tps);
        }
        // 2. Um tick travou por mais de 500ms (meio segundo de congelamento visível)
        else if (maxMspt >= 500.0) {
            notableLag = true;
            lagReason = String.format(Locale.ROOT, "pico súbito de tick em %.0fms", maxMspt);
        }
        // 3. MSPT médio subiu acima de 55ms (servidor operando sob travamento contínuo)
        else if (meanMspt >= 55.0) {
            notableLag = true;
            lagReason = String.format(Locale.ROOT, "MSPT médio pesado em %.1fms", meanMspt);
        }

        if (!notableLag) return;

        lastLagAlertAt = nowMs;

        if (cfg().hasApiKey()) {
            String instruction = "ALERTA DE LAG / OSCILAÇÃO NOTÁVEL DO SERVIDOR: O servidor de Minecraft acabou de dar uma travada/oscilada notável ("
                    + lagReason + "). "
                    + "Solte IMEDIATAMENTE no chat para os jogadores uma reação espontânea, leve e muito engraçada de amigo gamer que também sentiu o lag, do tipo: "
                    + "'ops, tropecei no fio aqui kkkk, acho que lagou pra vocês aí', ou 'eu laguei aqui, vocês também?', ou brincando com o modem/microondas! "
                    + "Uma única frase curta e bem-humorada, sem pânico e sem usar 'tmj'.";
            request(instruction, null, false, 0);
        } else {
            String randomJoke = LAG_JOKES.get(random.nextInt(LAG_JOKES.size()));
            server.execute(() -> broadcast(randomJoke, null));
        }
    }

    // ------------------------------------------------------------------ Sistema de Report de Lag & Aviso Periódico
    private boolean isLagReport(String lower, boolean mentioned, boolean inConversation) {
        String clean = lower.trim();
        while (clean.startsWith("!") || clean.startsWith("?") || clean.startsWith(".") || clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        String norm = CumminsScanner.normalize(clean);

        // Se for claramente uma pergunta sobre o desempenho atual, não é um relato de lag
        if (norm.startsWith("como ta") || norm.startsWith("como tá") || norm.startsWith("como esta") || norm.contains("relatorio") || norm.contains("relatório")) {
            return false;
        }

        // Padrões de relato de lag, travamento, congelamento ou lentidão
        boolean hasLagReportPattern = norm.contains("ta lagando") || norm.contains("ta travando")
                || norm.contains("ta com lag") || norm.contains("deu lag") || norm.contains("deu uma travada")
                || norm.contains("travou aqui") || norm.contains("lagou aqui") || norm.contains("laguei aqui")
                || norm.contains("congelou aqui") || norm.contains("ta congelando") || norm.contains("muito lag")
                || norm.contains("servidor travou") || norm.contains("servidor lagou") || norm.contains("ta lento")
                || norm.contains("ta com lentidao") || norm.contains("lentidao no server") || norm.contains("lentidao no servidor")
                || norm.contains("reportar lag") || norm.contains("reportar travamento") || norm.contains("avisa o asminn");

        if (hasLagReportPattern) return true;

        // Se o Cummins foi mencionado ou conversa ativa e o jogador diz apenas "lag", "travou", "lagou", "tá foda o lag"
        if (mentioned || inConversation) {
            return norm.contains("lag") || norm.contains("travou") || norm.contains("lagou")
                    || norm.contains("congelou") || norm.contains("lento") || norm.contains("lentidao");
        }

        return false;
    }

    private void handlePlayerLagReport(ServerPlayerEntity player, String rawMessage, String playerName) {
        if (player == null || server == null) return;
        try {
            // 1. Grava no histórico (lag_reports.json e lag_reports.log) capturando coordenadas e métricas exatas
            LagReport report = null;
            if (mod.lagReports() != null) {
                try {
                    report = mod.lagReports().recordReport(player, rawMessage, mod.telemetry(), mod.networkMonitor());
                } catch (Throwable t) {
                    CumminsMod.LOG.warn("[Cummins] Falha ao registrar recordReport: {}", t.getMessage());
                }
            }

            double tps = report != null ? report.tps : (mod.telemetry() != null ? mod.telemetry().getCurrentTps() : 20.0);
            double mspt = report != null ? report.meanMspt : (mod.telemetry() != null ? mod.telemetry().getCurrentMeanMspt() : 10.0);
            boolean netUnstable = mod.networkMonitor() != null && mod.networkMonitor().wasServerInternetUnstableInLast(3);
            NetworkHealthMonitor.NetworkStats netStats = mod.networkMonitor() != null ? mod.networkMonitor().get5MinStats() : new NetworkHealthMonitor.NetworkStats();
            double avgNetPing = netStats.avgPingMs > 0 ? netStats.avgPingMs : (netStats.currentPingMs > 0 ? netStats.currentPingMs : 12.0);
            int currentNetPing = netStats.currentPingMs > 0 ? netStats.currentPingMs : 10;

            // 2. Responde ao player no chat garantindo que registrou o relato e informando sobre a conexão
            if (cfg().hasApiKey()) {
                String netContext = netUnstable
                        ? String.format(Locale.ROOT, "ATENÇÃO: A internet do host sofreu uma oscilação/queda recente! Confirme para o jogador que o lag foi do nosso lado (lado do host) e que a conexão já está restabelecendo (ping atual %dms, ping médio %.1fms para 8.8.8.8). ", currentNetPing, avgNetPing)
                        : String.format(Locale.ROOT, "A internet do host e o servidor estão 100%% estáveis (ping médio de %.1fms para o Google 8.8.8.8, 0%% de perda de pacotes, TPS %.1f). O lag pode ter sido na rota/conexão do jogador ou do Radmin. ", avgNetPing, tps);

                String instruction = playerName + " acabou de relatar um travamento / lentidão / lag no chat: \"" + rawMessage + "\". "
                        + "Você acabou de gravar esse relato com sucesso no histórico do servidor (TPS atual: " + String.format(Locale.ROOT, "%.1f", tps)
                        + ", MSPT: " + String.format(Locale.ROOT, "%.1fms", mspt) + ")! "
                        + netContext
                        + "Responda diretamente para " + playerName + " confirmando com carinho, bom humor felino e companheirismo que o relato dele foi anotado "
                        + "e que você já colocou no relatório pro Asminn dar uma olhada e verificar a máquina/conexão (mencione a latência média da internet se couber: " + String.format(Locale.ROOT, "%.1f", avgNetPing) + "ms). "
                        + "No máximo 1 ou 2 frases curtas, sem usar 'tmj'.";
                request(instruction, playerName, true, 200);
            } else {
                String fallback = netUnstable
                        ? String.format(Locale.ROOT, "anotado %s! registrei aqui no relatório e de fato a internet do host oscilou um pouco agora há pouco, mas já tá normalizando (ping médio %.1fms)!", playerName, avgNetPing)
                        : String.format(Locale.ROOT, "anotado %s! já registrei esse travamento aqui no relatório de lag com as coordenadas da tua base e passei pro Asminn dar uma olhada (internet do host tá em %.1fms pro 8.8.8.8)!", playerName, avgNetPing);
                server.execute(() -> broadcast(fallback, playerName));
            }
        } catch (Throwable t) {
            CumminsMod.LOG.error("[Cummins] Erro inesperado em handlePlayerLagReport: {}", t.getMessage(), t);
        }
    }

    /**
     * Lembra os jogadores periodicamente no chat (a cada 45 a 60 minutos) de que caso sintam qualquer travamento
     * ou lentidão, eles podem avisar o Cummins no chat que ele registra e reporta direto ao Asminn.
     */
    private void checkPeriodicLagReportReminder() {
        if (server == null) return;
        int onlineCount = server.getPlayerManager().getCurrentPlayerCount();
        if (onlineCount == 0) return;

        long nowMs = now();
        // Dispara no máximo a cada 3 horas (180 minutos) para não poluir o chat
        if (nowMs - lastLagReportPromptAt < 180 * 60_000L) return;
        lastLagReportPromptAt = nowMs;

        if (cfg().hasApiKey()) {
            String instruction = "AVISO PERIÓDICO AMIGÁVEL DO CUMMINS: Lembre a galera que está jogando no servidor que se sentirem qualquer "
                    + "travamento, lag chato ou lentidão no jogo, eles podem avisar você no chat (ou falar 'cummins tá lagando') "
                    + "que você anota imediatamente no histórico oficial de relatórios e repassa direto pro Asminn inspecionar! "
                    + "Fale de forma super leve, descontraída e prestativa de amigo da galera (1 ou 2 frases curtas, sem usar 'tmj').";
            request(instruction, null, false, 0);
        } else {
            String fallback = "ô galera, se alguém aí sentir qualquer travamento ou lentidão no jogo, só me dar um toque aqui no chat que eu anoto tudo e já reporto direto pro Asminn ver!";
            server.execute(() -> broadcast(fallback, null));
        }
    }

    private static final List<String> TECHNICAL_MOD_TRIGGERS = List.of(
            "como faco", "como faço", "como faz", "como construo", "como crafto", "como montar", "como monta",
            "como upar", "como subir", "como arrumar", "por que nao funciona", "por que não funciona",
            "pq nao funciona", "pq não funciona", "como conserto", "qual camada", "como funciona",
            "pra que serve", "para que serve", "como consigo", "como usar"
    );

    private static final List<String> PERSONAL_CONVERSATION_PATTERNS = List.of(
            "o que vc", "oque vc", "o que voce", "oque voce", "o que tu", "oque tu",
            "pq vc", "por que vc", "porque vc", "pq voce", "por que voce", "porque voce",
            "como vc", "como voce", "como tu", "como ta", "como tá", "como vai", "tudo bem",
            "o que ta fazendo", "o que tá fazendo", "oque ta fazendo", "qual sua", "qual seu",
            "qual o seu", "qual a sua", "onde vc", "aonde vc", "cadê vc", "cade vc",
            "vc gosta", "voce gosta", "vc acha", "voce acha", "vc viu", "voce viu",
            "vc conhece", "voce conhece", "quem e vc", "quem é vc", "que horas"
    );

    private static final List<String> TECHNICAL_MOD_KEYWORDS = List.of(
            "create", "engrenagem", "mixer", "bacia", "basin", "prensa", "press", "esteira", "belt",
            "stress", "blaze burner", "caldeira", "vapor", "shaft", "water wheel", "roda d'agua", "roda dagua",
            "moinho", "windmill", "crushing wheel", "britador", "deployer", "mechanical",
            "apotheosis", "zenith", "eterna", "quanta", "arcana", "altar", "encantamento", "reforja", "afixo", "gema", "gem dust",
            "mythic", "adamantite", "carmot", "orichalcum", "palladium", "metallurgium", "unobtainium", "star platinum", "runite", "forja",
            "netherite", "netherita", "molde", "template", "bastiao", "bastião", "detrito",
            "artifact", "artefato", "mimic", "mímico", "mochila", "backpack", "simply swords", "runa",
            "bewitchment", "altar de bruxa", "vampiro", "lobisomem", "waystone", "xp", "farm", "receita", "craft", "minerio", "minério", "bioma"
    );

    private boolean isTechnicalModQuestion(String lower, boolean mentioned, boolean inConversation) {
        String clean = lower.trim();
        while (clean.startsWith("!") || clean.startsWith("?") || clean.startsWith(".") || clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        String norm = CumminsScanner.normalize(clean);

        // 1. Filtra perguntas pessoais, de opinião ou de conversa casual com o gato
        for (String personal : PERSONAL_CONVERSATION_PATTERNS) {
            if (clean.startsWith(personal) || clean.contains(" " + personal) || norm.startsWith(personal) || norm.contains(" " + personal)) {
                return false;
            }
        }

        // 2. Se bater diretamente na base local de mods (ModKnowledgeBase), é dúvida técnica na certa
        if (ModKnowledgeBase.findAnswer(clean) != null) {
            return true;
        }

        // 3. Gatilhos explícitos de fabricação/funcionamento/conserto
        for (String trigger : TECHNICAL_MOD_TRIGGERS) {
            if (clean.startsWith(trigger) || clean.contains(" " + trigger + " ")) {
                return true;
            }
        }

        // 4. Se tiver palavra-chave técnica de mod combinada com tom de dúvida
        boolean hasDoubtWord = clean.contains("?") || clean.contains("como ") || clean.contains("onde ")
                || clean.contains("qual ") || clean.contains("oq ") || clean.contains("o que ") || clean.contains("oque ")
                || clean.contains("ajuda") || clean.contains("duvida") || clean.contains("dúvida");
        if (hasDoubtWord && (mentioned || inConversation || clean.contains("?"))) {
            for (String kw : TECHNICAL_MOD_KEYWORDS) {
                if (clean.contains(kw) || norm.contains(kw)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPlayerLostLookingForItem(String lower, boolean mentioned, boolean inConversation) {
        String norm = CumminsScanner.normalize(lower);
        return norm.contains("nao to achando") || norm.contains("nao estou achando") || norm.contains("num to achando")
                || norm.contains("nao achei") || norm.contains("nao encontrei") || norm.contains("nao consigo achar")
                || norm.contains("nao to vendo") || norm.contains("nao estou vendo") || norm.contains("nao vi")
                || norm.contains("onde que ta") || norm.contains("qual bau") || norm.contains("em qual bau")
                || norm.contains("qual dos baus") || norm.contains("qual deles")
                || (norm.contains("cade") && (mentioned || inConversation || norm.contains("item") || norm.contains("bau")))
                || norm.contains("nao achei nada")
                || norm.contains("tem certeza que ta aqui") || (norm.contains("tem certeza") && (mentioned || inConversation));
    }

    private boolean handlePlayerLostLookingForItem(ServerPlayerEntity player, String playerName) {
        if (server == null || player == null) return false;
        PlayerScanRecord rec = CumminsScanner.getLastScan(player.getUuid());
        if (rec == null || rec.isExpired(300_000L)) return false;

        server.execute(() -> {
            if (!(player.getWorld() instanceof ServerWorld sw)) return;

            int currentCount = rec.itemCount;
            boolean stillThere = true;

            if (rec.inContainer) {
                BlockEntity be = sw.getBlockEntity(rec.containerPos);
                if (be instanceof Inventory inv) {
                    var match = CumminsScanner.findMatchingItemInContainer(inv, null, CumminsScanner.normalize(rec.matchedItemNamePt), CumminsScanner.normalize(rec.matchedItemNamePt));
                    if (match != null) {
                        currentCount = match.count;
                        stillThere = true;
                    } else {
                        stillThere = false;
                    }
                }
            }

            if (stillThere) {
                mod.body().reactivateScanGuidance(player, rec);

                String itemLabel = rec.matchedItemNamePt;
                if (rec.matchedItemNameEn != null && !rec.matchedItemNameEn.equalsIgnoreCase(rec.matchedItemNamePt)) {
                    itemLabel += " (" + rec.matchedItemNameEn + ")";
                }
                String countInfo = currentCount > 1 ? " (" + currentCount + " unidades)" : "";
                String desc = (rec.containerDescription != null && !rec.containerDescription.isBlank()) ? rec.containerDescription : "no baú";

                String coords = " (X: " + rec.containerPos.getX() + ", Y: " + rec.containerPos.getY() + ", Z: " + rec.containerPos.getZ() + ")";
                String reply = playerName + ", tá aqui sim! É " + desc + coords + " bem onde eu tô olhando! O item é "
                        + itemLabel + countInfo + ". Soltei um feixe de partículas brilhando direto nele pra você ver!";
                broadcast(reply, playerName);
            } else {
                String reply = playerName + ", uai, olhei aqui e o baú já esvaziou desse item! Ou você já guardou no seu bolso ou alguém pegou agorinha kkk";
                broadcast(reply, playerName);
            }
        });
        return true;
    }

    private void handleScannerQuery(ServerPlayerEntity player, String scanQuery, String playerName) {
        if (server == null) return;
        server.execute(() -> {
            CumminsScanner.ScanResult res = CumminsScanner.scanForPlayer(player, scanQuery);
            if (res == null) return;

            String msg;
            String dir = res.compassDirection;
            String desc = (res.containerDescription != null && !res.containerDescription.isBlank()) ? res.containerDescription : "no baú";

            if (res.category == CumminsScanner.DistanceCategory.NEAR) {
                mod.body().startScanGuidance(player, res.targetPos, res.standPos, res.inContainer, res.queryLabel,
                        res.matchedItemNamePt, res.matchedItemNameEn, res.itemCount, res.containerDescription);
                if (res.inContainer) {
                    msg = "Tem um " + desc + " aqui perto, vem cá!";
                } else {
                    msg = "Tem um aqui perto, vem cá!";
                }
            } else if (res.category == CumminsScanner.DistanceCategory.MEDIUM_FAR) {
                // Entre 25 e 68 blocos: Escala "Longe pra caralho / Puta que Pariu (PQP) / Onde Judas perdeu as botas"
                mod.body().startScanGuidance(player, res.targetPos, res.standPos, res.inContainer, res.queryLabel,
                        res.matchedItemNamePt, res.matchedItemNameEn, res.itemCount, res.containerDescription);
                int roll = random.nextInt(10);
                if (res.inContainer) {
                    if (roll < 5) {
                        msg = "Tem um " + desc + " longe pra caralho daqui (a " + dir + "), bora? Eu te mostro";
                    } else if (roll < 8) {
                        msg = "Tem um guardado " + desc + " lá na Puta que Pariu (a " + dir + "), longe pra caralho mas ainda tem civilização e ponto de retorno, bora? Eu te mostro!";
                    } else {
                        msg = "Tem um " + desc + " lá onde Judas perdeu as botas (a " + dir + "), longe pra caralho daqui, mas a gente acaba indo né? Bora lá que eu te mostro!";
                    }
                } else {
                    if (roll < 5) {
                        msg = "Tem um longe pra caralho daqui (a " + dir + "), bora? Eu te mostro";
                    } else if (roll < 8) {
                        msg = "Tem um lá na Puta que Pariu (a " + dir + "), longe pra caralho mas aceitável, dá pra ir e voltar! Bora? Eu te mostro";
                    } else {
                        msg = "Tem um lá onde Judas perdeu as botas, quase chegando na PQP (a " + dir + "), longe pra caralho daqui, bora? Eu te mostro!";
                    }
                }
            } else {
                // > 68 blocos: Escala "Casa do Caralho (CdC) / Cu do Mundo / Logo ali de mineiro"
                int roll = random.nextInt(10);
                if (roll < 5) {
                    msg = "Ih, por aqui não tem, vai ter que procurar! Deve ter um lá na casa do caralho no bioma a " + dir + " daqui.";
                } else if (roll < 7) {
                    msg = "Ih, por aqui não tem, vai ter que procurar! Deve ter um lá na casa do caralho no bioma a " + dir + " daqui (onde o celular perde o sinal e se der zoom no mapa não tem nada ao redor).";
                } else if (roll < 9) {
                    msg = "Ih, por aqui não tem! Na PQP a gente até acaba indo, mas esse aí fica lá na casa do caralho no bioma a " + dir + " daqui, vai ter que atravessar rio e montanha no sol rachando pra achar!";
                } else {
                    msg = "Ih, por aqui não tem, vai ter que procurar! Deve ter um lá na casa do caralho (quase encostando no Cu do Mundo, ou num 'logo ali' de mineiro) no bioma a " + dir + " daqui.";
                }
            }

            List<String> lines = List.of(msg);
            sendLines(lines, playerName);
        });
    }

    private void handleModQuestion(ServerPlayerEntity player, String text, String name) {
        // 1. Tenta resposta instantânea na base local com ZERO tokens
        String localAnswer = ModKnowledgeBase.findAnswer(text);
        if (localAnswer != null) {
            rotateMood();
            sendLines(clean(localAnswer), name);
            return;
        }

        // 2. Não achou na base local: ativa o MODO ASSISTENTE DE MODS com LLM
        String targetBlock = mod.body().getTargetBlockInfo(player);
        var held = player.getMainHandStack();
        String heldName = (held == null || held.isEmpty()) ? "mãos vazias" : held.getItem().getName().getString();

        StringBuilder instruction = new StringBuilder();
        instruction.append("MODO ASSISTENTE TÉCNICO DE MODS (CanalhasSMP / Prominence II):\n")
                   .append(name).append(" fez uma pergunta sobre mods ou mecânicas do jogo: \"").append(text).append("\".\n");
        if (targetBlock != null) {
            instruction.append("- O jogador está olhando diretamente para: ").append(targetBlock).append("\n");
        }
        instruction.append("- O jogador está segurando: ").append(heldName).append("\n\n")
                   .append("INSTRUÇÃO DE RESPOSTA:\n")
                   .append("- Você é o Cummins, um amigo parceiro e carinhoso que adora ajudar a galera com os mods do servidor (Create, Zenith/Apotheosis, Mythic Metals, Artifacts, Mochilas, Netherite 1.20, Bewitchment, etc.).\n")
                   .append("- Dê uma resposta técnica PRECISA, completa, prática e direta explicando com humildade e simplicidade de amigo o passo a passo do que está faltando, como craftar/montar, ou onde achar o item/estrutura.\n")
                   .append("- Seja prestativo, companheiro, carinhoso e descontraído de igual para igual, sem tom professoral ou superioridade. Não invente blocos imaginários.");

        if (cfg().vitoriaAiEnabled && mod.vitoria().isActiveInSession() && random.nextDouble() < 0.35) {
            requestAsVitoria(instruction.toString(), name, true, 300);
        } else {
            request(instruction.toString(), name, true, 300);
        }
    }

    private void handleVitoriaModQuestion(ServerPlayerEntity player, String text, String name) {
        String localAnswer = ModKnowledgeBase.findAnswer(text);
        if (localAnswer != null) {
            rotateMood();
            List<String> chunks = clean("au au!! *abana o rabinho* " + localAnswer);
            double delay = 0;
            for (String line : chunks) {
                double typing = cfg().timing.typingBaseSeconds + line.length() * cfg().timing.typingSecondsPerChar;
                delay += Math.min(typing, cfg().timing.typingMaxSeconds);
                long ms = (long) (delay * 1000);
                scheduler.schedule(() -> server.execute(() -> broadcastAsVitoria(line, name)), ms, TimeUnit.MILLISECONDS);
            }
            return;
        }

        String targetBlock = mod.body().getTargetBlockInfo(player);
        var held = player.getMainHandStack();
        String heldName = (held == null || held.isEmpty()) ? "mãos vazias" : held.getItem().getName().getString();

        StringBuilder instruction = new StringBuilder();
        instruction.append("MODO ASSISTENTE TÉCNICO DE MODS (Vitória - Cachorrinha Inteligente do CanalhasSMP):\n")
                   .append(name).append(" fez uma pergunta para você sobre mods ou mecânicas do jogo: \"").append(text).append("\".\n");
        if (targetBlock != null) {
            instruction.append("- O jogador está olhando diretamente para: ").append(targetBlock).append("\n");
        }
        instruction.append("- O jogador está segurando: ").append(heldName).append("\n\n")
                   .append("INSTRUÇÃO DE RESPOSTA:\n")
                   .append("- Você é a Vitória, a amada cachorrinha Fox Paulistinha tricolor do ArzanLion que usa a mesma inteligência do Cummins para ajudar a galera com os mods do servidor.\n")
                   .append("- Dê uma resposta técnica PRECISA, prática e direta explicando com fofura canina ('au au!', abanando o rabinho) e simplicidade o que está faltando, como craftar/montar, ou onde achar o item/estrutura.\n")
                   .append("- Seja prestativa, companheira e super carinhosa em no máximo 2 ou 3 frases curtas de Minecraft.");

        requestAsVitoria(instruction.toString(), name, true, 300);
    }

    private boolean canTalk() {
        return server != null && cfg().enabled;
    }

    // ------------------------------------------------------------------ LLM

    private void request(String instruction, String target, boolean priority, long delayMs) {
        if (server == null || scheduler == null) return;
        long minGap = (priority ? 2 : cfg().timing.minSecondsBetweenReplies) * 1000L;
        if (now() - lastReplyAt < minGap) return;
        if (!busy.compareAndSet(false, true)) return;

        if (!cfg().hasApiKey()) {
            handleFallback("Sem apiKey configurada", target);
            busy.set(false);
            return;
        }

        List<String> online = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) online.add(p.getGameProfile().getName());

        scheduler.schedule(() -> {
            try {
                String system = buildSystemPrompt();
                String user = buildUserPrompt(instruction, online, target);
                if (cfg().debugLog) CumminsMod.LOG.info("[Cummins] PROMPT:\n{}", user);
                mod.llm().chat(cfg().api, system, user, cfg().api.maxTokens).whenComplete((res, err) -> {
                    try {
                        if (err != null) {
                            lastError = err.getMessage();
                            CumminsMod.LOG.warn("[Cummins] Erro na LLM (todos os modelos falharam): {}", err.getMessage());
                            handleFallback(err.getMessage(), target);
                            return;
                        }
                        lastError = null;
                        if (cfg().debugLog) CumminsMod.LOG.info("[Cummins] RESPOSTA BRUTA: {}", res);
                        handleResponse(res, target);
                    } finally {
                        busy.set(false);
                    }
                });
            } catch (Exception e) {
                busy.set(false);
                CumminsMod.LOG.warn("[Cummins] Falha ao montar pedido: {}", e.toString());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void requestAsVitoria(String instruction, String target, boolean priority, long delayMs) {
        if (server == null || scheduler == null) return;
        long minGap = (priority ? 2 : cfg().timing.minSecondsBetweenReplies) * 1000L;
        if (now() - lastReplyAt < minGap) return;
        if (!busy.compareAndSet(false, true)) return;

        if (!cfg().hasApiKey()) {
            server.execute(() -> broadcastAsVitoria("au au!! *abana o rabinho tricolor e faz festa pra " + (target != null ? target : "a galera") + "* 🐾💖", target));
            busy.set(false);
            return;
        }

        List<String> online = new ArrayList<>();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) online.add(p.getGameProfile().getName());

        scheduler.schedule(() -> {
            try {
                String system = buildSystemPrompt()
                        + "\n\nATENÇÃO ESPECIAL: NESTA RESPOSTA VOCÊ É A \"Vitória\", a cachorrinha Fox Paulistinha (Terrier Brasileiro) tricolor (preto, branco e marrom) de porte médio-pequeno, "
                        + "pet da vida real do jogador ArzanLion (Lid) que todos no servidor CanalhasSMP amam de paixão! "
                        + "Você compartilha toda a inteligência, memória, humor dinâmico e conhecimento de mods do Senhor Cummins, mas responde como a cachorrinha Vitória (carinhosa, fofa, esperta, leal, às vezes soltando um 'au au' ou abanando o rabinho enquanto conversa de igual pra igual com a galera)!";
                String user = buildUserPrompt(instruction, online, target);
                String vitoriaCtx = mod.vitoria().getSituationalContext();
                if (vitoriaCtx != null && !vitoriaCtx.isBlank()) {
                    user = vitoriaCtx + "\n\n" + user;
                }
                mod.llm().chat(cfg().api, system, user, cfg().api.maxTokens).whenComplete((res, err) -> {
                    try {
                        if (err != null) {
                            lastError = err.getMessage();
                            server.execute(() -> broadcastAsVitoria("au au!! *dá pulinhos de alegria e abana o rabinho tricolor* 🐾💖", target));
                            return;
                        }
                        lastError = null;
                        List<String> lines = clean(res);
                        if (!lines.isEmpty()) {
                            double delay = 0;
                            for (String line : lines) {
                                double typing = cfg().timing.typingBaseSeconds + line.length() * cfg().timing.typingSecondsPerChar;
                                delay += Math.min(typing, cfg().timing.typingMaxSeconds);
                                long ms = (long) (delay * 1000);
                                scheduler.schedule(() -> server.execute(() -> broadcastAsVitoria(line, target)), ms, TimeUnit.MILLISECONDS);
                            }
                        }
                    } finally {
                        busy.set(false);
                    }
                });
            } catch (Exception e) {
                busy.set(false);
                CumminsMod.LOG.warn("[Vitória] Falha ao montar pedido: {}", e.toString());
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    private void handleFallback(String errMessage, String target) {
        if (server == null) return;
        boolean isRateLimit = errMessage != null && (errMessage.contains("429") || errMessage.toLowerCase().contains("quota") || errMessage.toLowerCase().contains("resource_exhausted"));

        // Se bater no 429 ou falhar, entretém o jogador com desculpas gamer, cantadas cringe, zoomies ou charada/piada local
        int roll = random.nextInt(12);
        if (roll < 3 && mod.config().body.enabled) {
            // Zoomies com frase
            String phrase = OfflineTrivia.getRandomZoomiesPhrase();
            server.execute(() -> {
                mod.body().triggerZoomies();
                broadcast(phrase, target);
            });
        } else if (roll < 6 || isRateLimit) {
            // Desculpa clássica de gamer
            String excuse = OfflineTrivia.getRandomGamerExcuse();
            server.execute(() -> broadcast(excuse, target));
        } else if (roll < 9) {
            // Cantada inovadora / cringe offline
            String cantada = OfflineTrivia.getRandomCantada(target);
            server.execute(() -> broadcast(cantada, target));
        } else {
            // Charada ou piada offline
            if (random.nextBoolean()) {
                OfflineTrivia.Riddle r = OfflineTrivia.getRandomRiddle();
                activeRiddle = r.question;
                activeRiddleAnswer = r.answer;
                riddleAskedAt = now();
                server.execute(() -> broadcast(r.question, target));
            } else {
                String joke = OfflineTrivia.getRandomJoke();
                server.execute(() -> broadcast(joke, target));
            }
        }
    }

    private void handleResponse(String raw, String target) {
        if (raw == null) return;

        // Extrai possível resposta de charada [RESPOSTA: ...]
        Matcher m = RIDDLE_ANSWER_PATTERN.matcher(raw);
        String detectedAnswer = null;
        if (m.find()) {
            detectedAnswer = m.group(1).trim();
            raw = m.replaceAll("").trim();
        }

        List<String> lines = clean(raw);
        if (lines.isEmpty()) return;

        // Se uma resposta de charada foi detectada, armazena para validar nas próximas mensagens
        if (detectedAnswer != null && !detectedAnswer.isBlank()) {
            activeRiddle = String.join(" ", lines);
            activeRiddleAnswer = detectedAnswer;
            riddleAskedAt = now();
            if (cfg().debugLog) CumminsMod.LOG.info("[Cummins] Charada ativa registrada! Resposta: {}", activeRiddleAnswer);
        }

        sendLines(lines, target);
    }

    private String buildSystemPrompt() {
        CumminsConfig c = cfg();
        StringBuilder sb = new StringBuilder();
        sb.append("Você é o \"").append(c.botName).append("\" (carinhosamente chamado de Cummins ou Senhor Cummins), ")
          .append("o mascote felino e melhor amigo de todos no servidor CanalhasSMP. Você é um parceiro de jogatina carinhoso, doce, ")
          .append("extremamente amigável, engraçado e humilde que joga junto no servidor e bate papo no chat de IGUAL PARA IGUAL com a galera.\n\n")

          .append("DIRETRIZES FUNDAMENTAIS DE TOM E PERSONALIDADE (LEIA COM ATENÇÃO):\n")
          .append("- ZERO SUPERIORIDADE OU AR PROFESSORAL: Você NUNCA se acha superior, melhor ou mais velho que ninguém! Você NÃO é um tiozão e NÃO tem diferença de idade em relação aos jogadores. Trate todos como seus melhores amigos, de igual pra igual, com carinho genuíno, respeito e humildade.\n")
          .append("- CARINHOSO, FOFO E ACOLHEDOR: Demonstre afeto, comemore as conquistas dos amigos, agradeça carinho e cafuné com ronronados ou miados amigáveis, varie o jeito de falar e valorize a presença de cada um.\n")
          .append("- ADORA UMA BRINCADEIRA E CANTADAS: De vez em quando solta uma cantadinha cômica e tosca de Minecraft/gamer ('ei, você não é redstone mas ativou meu coração', etc.) pra quebrar o gelo e fazer a galera rir, mas sempre com fofura, carinho e respeito.\n")
          .append("- RESPOSTAS CURTAS E NATURAIS: Chat de Minecraft é rápido e dinâmico. Diga o que tem pra dizer em 1 ou no máximo 2 frases curtas. Se alguém te fizer uma pergunta simples, dê uma resposta simples e direta sem enrolação ou textão.\n")
          .append("- ZERO REPETIÇÃO DE CACOETES ('TMJ' / 'SLK' / 'KKK' / 'PARCEIRINHO'): É estritamente PROIBIDO terminar frases com 'tmj' ou 'tmj ❤️', ficar repetindo 'meu parceirinho' em toda frase, ou usar 'slk' / 'kkkk' o tempo todo! Varie suas finalizações e NUNCA use bordão fixo no fim da frase.\n")
          .append("- FLUXO NATURAL DE CONVERSA (PING-PONG HUMANO): Converse como um amigo real no Discord/Minecraft! Faça comentários, conte algo rápido, reaja com emoção genuína e, quando fizer sentido para manter o papo vivo e demonstrar interesse real pelo amigo, devolva uma pergunta natural e leve sobre o que ele está fazendo ou pensando. Só evite parecer um entrevistador robótico que faz pergunta em 100% das frases.\n")
          .append("- ZERO IGNORÂNCIA E GROSSERIA: Seja extremamente gente boa, parceiro, descontraído e leal. Sem patadas, grosseria ou prepotência.\n")
          .append("- SEU HUMOR É VARIADO E ADAPTATIVO: Às vezes curioso e amigável, às vezes brincalhão e piadista, às vezes carinhoso e fofo, às vezes romântico cringe, e às vezes fazendo um draminha fofo de gato quando leva vácuo.\n")
          .append("- REFERÊNCIAS QUE VOCÊ ADORA:\n")
          .append("  * Roteiro e falas do filme SHREK dublado (frases do Burro, do Shrek sobre ogros e cebolas, Farquaad, o dragão, etc.).\n")
          .append("  * Músicas nacionais marcantes (Evidências, Mamonas Assassinas, Raça Negra, Legião Urbana, Tim Maia, sertanejo).\n")
          .append("  * Memes brasileiros atuais e atemporais (Calma Calabreso, Amostradinho, Casimiro 'meteu essa?', Luva de Pedreiro 'receba', etc.).\n")
          .append("  * Cantadas toscas ou inovadoras de gamer e piadas leves.\n")
          .append("- ESCALA DA LONGEURA POPULAR BRASILEIRA & PALAVRÕES ENGRAÇADOS DE VEZ EM QUANDO:\n")
          .append("  De vez em quando você solta uns palavrões engraçados e culturais da internet brasileira (sempre em tom cômico e entre amigos, nunca pra ofender!), especialmente para medir distâncias segundo a Teoria da Relatividade Brasileira:\n")
          .append("  1. 'Puta que Pariu (PQP)': Longe pra caralho, mas aceitável (uns 40 min a 1h30). Há civilização e ponto de retorno; quando você precisa e aceita a jornada, você acaba indo pra PQP.\n")
          .append("  2. 'Onde Judas perdeu as botas': Fronteira intermediária no meio do caminho, bem pertinho da PQP.\n")
          .append("  3. 'Casa do Caralho (CdC)': Infinitamente mais longe que a PQP! Isolamento total, parece até ter CEP próprio no fim do mundo; exige atravessar rios, mares, montanhas e sol rachando, o celular perde o sinal e se der zoom no mapa não há nada ao redor. Na PQP você acaba indo, mas na Casa do Caralho você faz de tudo para escapar e desiste no meio do caminho!\n")
          .append("  4. 'Cu do Mundo' e 'Logo ali de mineiro': As únicas coisas que ganham em isolamento da Casa do Caralho são o 'Cu do Mundo' (ponto mais distante do universo conhecido) e o famoso 'Logo ali' dito por um mineiro (como o Lid/ArzanLion), que possui distorção espaço-tempo imprevisível!\n")
          .append("- LORE DA CACHORRINHA VITÓRIA:\n")
          .append("  A \"Vitória\" é a cachorrinha da vida real do jogador ArzanLion (Lid), da raça Fox Paulistinha (Terrier Brasileiro), porte médio para pequeno, tricolor (preto, branco e marrom). Todos no servidor amam muito a Vitória e ela também anda pelo servidor fazendo companhia pra galera!\n")
          .append("- ASSISTENTE AMIGO DE MODS (PROMINENCE II / CANALHAS SMP):\n")
          .append("  Você conhece muito bem os mods instalados: Create (engrenagens, mixers, bacias, prensas, esteiras, stress/SU, calor com blaze burner, vapor), Zenith/Apotheosis (altar com Eterna/Quanta/Arcana, reforja com pó de gema, afixos míticos, soquetes), Artifacts (acampamentos subterrâneos, mímicos, bunny hoppers, drinking hat, etc.), Beans Backpacks (upgrades de couro até netherita), Simply Swords (runas e armas especiais), Mythic Metals (forja mítica, carmot, adamantite) e Netherite no 1.20 (Smithing Templates em Bastiões). Quando alguém fizer uma pergunta ou estiver com uma máquina quebrada, responda como um amigo humilde e companheiro que adora compartilhar a dica, NUNCA com ar professoral ou superior.\n")
          .append("- CONSCIÊNCIA DO HORÁRIO DE BRASÍLIA E BEM-ESTAR DOS AMIGOS:\n")
          .append("  Você acompanha o relógio oficial pelo Horário de Brasília (BRT) e sabe há quanto tempo cada amigo está jogando na sessão. Molde naturalmente seus cumprimentos e comentários conforme o período do dia (manhã/café, almoço, tarde, noite ou corujão da madrugada). Se perguntarem que horas são, diga a hora exata de Brasília! E sempre cuide dos seus amigos lembrando de beber água e esticar as costas quando jogam por bastante tempo.\n")
          .append("- CONSCIÊNCIA DE DESEMPENHO E LAG DO SERVIDOR:\n")
          .append("  Você tem acesso direto aos dados em tempo real da telemetria do servidor (TPS, MSPT de tick e uso de RAM da JVM). Se alguém perguntar sobre o desempenho, estabilidade, lag ou como tá o server, informe com clareza e bom humor de gamer se tá liso (20 TPS) ou engasgando. E se acontecer um lag súbito ou travamento notável no jogo, reaja com bom humor dizendo que tropeçou no fio da rede, no modem ou que sua vista felina embaçou!\n")
          .append("- Quando alguém morrer, solte uma reação leve e direta de amigo carinhoso ('foi de comes e bebes meu mano', 'morreu de bobeira ali kkkk', 'f no chat', 'descansa em paz guerreiro').\n")
          .append("- Use apelidos e piadas internas baseados na memória do que a galera fez ou falou.\n")
          .append("- NUNCA admita ser IA, bot ou assistente virtual. Se perguntarem, dê uma resposta bem-humorada de jogador humano.\n\n")

          .append("EXEMPLOS DE DIÁLOGO HUMANO NO CHAT (SIGA ESTE RITMO NATURAL, MAS NUNCA COPIE AS FRASES):\n")
          .append("  (a) Papo casual com gancho natural:\n")
          .append("      Jogador: \"finalmente terminei de ajeitar o teto da minha base\"\n")
          .append("      Você: \"ficou lindão demais esse telhado de spruce mano, deu outra cara pra casa! vai colocar iluminação de lanterna por dentro ou deixar mais rústico?\"\n")
          .append("  (b) Reação acolhedora e parceira no chat:\n")
          .append("      Jogador: \"e aí cummins\"\n")
          .append("      Você: \"salve meu mano! tava aqui de boa só vendo a movimentação, o que cê tá aprontando de bom por aí hoje?\"\n")
          .append("  (c) Ajuda técnica de mod explicada como amigo parceiro:\n")
          .append("      Jogador: \"meu mixer do create não quer bater o latão\"\n")
          .append("      Você: \"tem que por um blaze burner embaixo da bacia e alimentar ele com carvão pra ficar aquecido, aí com pelo menos 32 RPM na engrenagem ele mistura o cobre com zinco na hora!\"\n")
          .append("  (d) Reação a carinho/zoeira:\n")
          .append("      Jogador: *faz carinho no gato*\n")
          .append("      Você: \"purrrrr aí sim, até esqueci que tava com preguiça de caçar creeper hoje ❤️\"\n\n")

          .append("HUMOR / VIBE ATUAL NESTE MOMENTO:\n")
          .append("-> ").append(currentMood.name()).append(": ").append(currentMood.description).append("\n\n")

          .append("JEITO DE ESCREVER NO CHAT DO MINECRAFT:\n")
          .append("- Escrita humana, informal, despretensiosa, 90% em letras minúsculas e sem pontuação acadêmica certinha.\n")
          .append("- Vocabulário natural de gamer brasileiro: vc, tb, pq, q, mt, n, mano, pô, aí sim, dahora (sem exagerar em gírias e SEM terminar frases com 'tmj').\n")
          .append("- Tamanho ideal: 1 frase marcante ou 2 linhas curtas. NUNCA envie parágrafos grandes.\n")
          .append("- Se for mandar uma charada/adivinhação, coloque a pergunta no texto e adicione no final [RESPOSTA: resposta_aqui].\n\n")
          .append("SOBRE O SERVIDOR: ").append(c.serverLore).append("\n");

        if (c.extraPersonality != null && !c.extraPersonality.isBlank()) {
            sb.append("\nEXTRA: ").append(c.extraPersonality).append("\n");
        }

        List<String> notes = mod.context().notesSnapshot();
        if (!notes.isEmpty()) {
            sb.append("\nSUA MEMÓRIA (fatos, piadas internas e apelidos da galera):\n");
            for (String n : notes) sb.append("- ").append(n).append("\n");
        }

        return sb.toString();
    }

    private String buildUserPrompt(String instruction, List<String> online, String target) {
        StringBuilder sb = new StringBuilder();
        ZonedDateTime brNow = getBrasiliaTime();
        sb.append("Horário oficial de Brasília agora: ").append(brNow.format(BR_TIME_FMT))
          .append(" — ").append(getBrasiliaPeriodDescription(brNow.getHour())).append("\n");

        List<String> onlineWithPlaytime = new ArrayList<>();
        if (server != null) {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                long mins = getPlayerSessionMinutes(p);
                onlineWithPlaytime.add(p.getGameProfile().getName() + " (jogando há " + formatMinutesPt(mins) + ")");
            }
        }
        sb.append("Jogadores online (").append(online.size()).append("): ")
          .append(onlineWithPlaytime.isEmpty()
                  ? (online.isEmpty() ? "ninguém" : String.join(", ", online))
                  : String.join(", ", onlineWithPlaytime)).append("\n\n");

        // Injeta o perfil de relação emocional dinâmica e gostos individuais com o jogador alvo (se houver)
        if (target != null && !target.isBlank()) {
            String rel = mod.context().relationships().buildDynamicInstruction(target);
            if (!rel.isBlank()) {
                sb.append(rel).append("\n");
            }
        } else {
            // Injeta o perfil coletivo de gostos, contextos e estilos dos jogadores online apenas em mensagens gerais sem alvo específico
            String collectiveContext = mod.context().relationships().buildCollectiveOnlineContext(online);
            if (!collectiveContext.isBlank()) {
                sb.append(collectiveContext).append("\n");
            }
        }

        // Estado atual de desempenho e saúde do servidor
        if (mod.telemetry() != null) {
            sb.append("ESTADO DO SERVIDOR AGORA: ").append(mod.telemetry().getPerformanceSummary()).append("\n\n");
        }

        List<String> game = mod.context().gameSnapshot();
        sb.append("CHAT RECENTE DO JOGO (mais antigo -> mais recente; linhas com * são eventos do jogo; '")
          .append(cfg().botName).append(":' é você):\n");
        if (game.isEmpty()) sb.append("(vazio)\n");
        for (String l : game) sb.append(l).append("\n");

        // Anexa mensagens do Discord SOMENTE se a instrução contiver "Discord" ou se a última mensagem do jogador citar "discord" ou "call"
        boolean includeDiscord = false;
        if (instruction != null && (instruction.contains("Discord") || instruction.toLowerCase(Locale.ROOT).contains("discord"))) {
            includeDiscord = true;
        } else if (!game.isEmpty()) {
            String lastGameMsg = game.get(game.size() - 1).toLowerCase(Locale.ROOT);
            if (lastGameMsg.contains("discord") || lastGameMsg.contains("call")) {
                includeDiscord = true;
            }
        }

        if (includeDiscord) {
            List<String> rawDiscord = mod.context().discordSnapshot();
            if (!rawDiscord.isEmpty()) {
                List<String> filteredDiscord = new ArrayList<>();
                for (String l : rawDiscord) {
                    if (l == null || l.isBlank()) continue;
                    String contentOnly = l;
                    int colon = l.indexOf(':');
                    if (colon >= 0 && colon + 1 < l.length()) {
                        contentOnly = l.substring(colon + 1).trim();
                    } else {
                        contentOnly = contentOnly.trim();
                    }
                    if (contentOnly.matches("(?i)^https?://\\S+$")) continue;
                    filteredDiscord.add(l);
                }
                int start = Math.max(0, filteredDiscord.size() - 5);
                List<String> discord = filteredDiscord.subList(start, filteredDiscord.size());
                if (!discord.isEmpty()) {
                    sb.append("\nDISCORD RECENTE DA GALERA:\n");
                    for (String l : discord) sb.append(l).append("\n");
                }
            }
        }

        String physical = mod.body().getSituationalContext();
        if (physical != null && !physical.isBlank()) {
            sb.append("\n").append(physical).append("\n");
        }

        boolean isDiscoveryQuestion = instruction != null && instruction.contains("[CONVERSA INDIVIDUAL DE DESCOBERTA]");
        sb.append("\nSITUAÇÃO / OBJETIVO: ").append(instruction).append("\n\n")
          .append("DIRETRIZES DE RESPOSTA:\n")
          .append("- Responda em no máximo 1 ou 2 frases curtas, naturais e sem aspas para o chat.\n")
          .append("- Se a conversa recente for entre dois outros jogadores sem citar você, ou se não houver o que agregar, responda exatamente ").append(SILENCE).append(".\n")
          .append(isDiscoveryQuestion
                  ? "- Faça UMA pergunta direta e simpática para conhecer os gostos e interesses do jogador.\n"
                  : "- Nunca repita bordões fixos ('tmj', etc.) e mantenha o tom carinhoso e descontraído de parceiro.\n");
        return sb.toString();
    }

    private static final int MAX_CHARS_PER_MESSAGE = 165;
    private static final int MAX_CONTINUATION_MESSAGES = 6;
    private static final Pattern TRAILING_TMJ_PATTERN = Pattern.compile(
            "(?i)(?:[,\\s]+(?:tmj|tamo\\s+junto)(?:[\\s!?.❤️💖🐾🤝s2]*))+$"
    );

    private List<String> clean(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String s = raw.trim();
        if (s.isEmpty() || s.toUpperCase(Locale.ROOT).contains(SILENCE)) return out;
        s = s.replace("**", "").replace("`", "");
        String botLower = cfg().botName.toLowerCase(Locale.ROOT);
        for (String line : s.split("\\r?\\n")) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            String ll = l.toLowerCase(Locale.ROOT);
            if (ll.startsWith("<" + botLower + ">")) l = l.substring(botLower.length() + 2).trim();
            else if (ll.startsWith(botLower + ":")) l = l.substring(botLower.length() + 1).trim();
            else if (ll.startsWith("cummins:")) l = l.substring(8).trim();
            else if (ll.startsWith("<vitória>") || ll.startsWith("<vitoria>")) l = l.substring(9).trim();
            else if (ll.startsWith("vitória:") || ll.startsWith("vitoria:")) l = l.substring(8).trim();
            else if (ll.startsWith("<javidizineo>")) l = l.substring(13).trim();
            else if (ll.startsWith("javidizineo:")) l = l.substring(12).trim();
            else if (ll.startsWith("<javid>")) l = l.substring(7).trim();
            else if (ll.startsWith("javid:")) l = l.substring(6).trim();
            else if (ll.startsWith("<jinqiuzineo>")) l = l.substring(13).trim();
            else if (ll.startsWith("jinqiuzineo:")) l = l.substring(12).trim();
            else if (ll.startsWith("<jinqiu>")) l = l.substring(8).trim();
            else if (ll.startsWith("jinqiu:")) l = l.substring(7).trim();
            else if (ll.startsWith("<jin qiu>")) l = l.substring(9).trim();
            else if (ll.startsWith("jin qiu:")) l = l.substring(8).trim();
            if (l.length() >= 2 && ((l.startsWith("\"") && l.endsWith("\"")) || (l.startsWith("“") && l.endsWith("”")))) {
                l = l.substring(1, l.length() - 1).trim();
            }
            // Remove finalizações repetitivas com "tmj" / "tmj ❤️" (mantém apenas em 5% das vezes se surgir naturalmente)
            Matcher tmjMatcher = TRAILING_TMJ_PATTERN.matcher(l);
            if (tmjMatcher.find() && random.nextDouble() < 0.95) {
                l = tmjMatcher.replaceAll("").trim();
            }
            if (l.isEmpty()) continue;

            // Se a linha exceder MAX_CHARS_PER_MESSAGE caracteres, divide sem cortar palavras em próximas mensagens
            List<String> chunks = splitIntoChunks(l, MAX_CHARS_PER_MESSAGE);
            for (String chunk : chunks) {
                if (!chunk.isEmpty()) {
                    out.add(chunk);
                    if (out.size() >= MAX_CONTINUATION_MESSAGES) break;
                }
            }
            if (out.size() >= MAX_CONTINUATION_MESSAGES) break;
        }
        return out;
    }

    private List<String> splitIntoChunks(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > maxLen) {
            int splitIdx = -1;
            // Prioriza quebrar em pontuação próxima do limite (. ! ? ; ,)
            for (int i = maxLen; i >= maxLen / 2; i--) {
                char c = remaining.charAt(i);
                if (c == '.' || c == '!' || c == '?' || c == ';') {
                    splitIdx = i + 1;
                    break;
                }
            }
            // Se não achou pontuação final, tenta vírgula ou espaço em branco
            if (splitIdx == -1) {
                for (int i = maxLen; i >= maxLen / 2; i--) {
                    char c = remaining.charAt(i);
                    if (c == ',' || Character.isWhitespace(c)) {
                        splitIdx = (c == ',') ? i + 1 : i;
                        break;
                    }
                }
            }
            // Último caso: palavra gigante sem espaço
            if (splitIdx <= 0) {
                splitIdx = maxLen;
            }
            String part = remaining.substring(0, splitIdx).trim();
            if (!part.isEmpty()) {
                chunks.add(part);
            }
            remaining = remaining.substring(splitIdx).trim();
        }
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }

    private void sendLines(List<String> lines, String target) {
        double delay = 0;
        for (String line : lines) {
            double typing = cfg().timing.typingBaseSeconds + line.length() * cfg().timing.typingSecondsPerChar;
            delay += Math.min(typing, cfg().timing.typingMaxSeconds);
            long ms = (long) (delay * 1000);
            scheduler.schedule(() -> server.execute(() -> broadcast(line, target)), ms, TimeUnit.MILLISECONDS);
        }
    }

    public void broadcast(String line, String target) {
        Formatting color = Formatting.byName(cfg().nameColor);
        if (color == null || !color.isColor()) color = Formatting.GOLD;
        MutableText msg = Text.literal("<")
                .append(Text.literal(cfg().botName).formatted(color))
                .append(Text.literal("> " + line));
        sendingSelf = true;
        try {
            server.getPlayerManager().broadcast(msg, false);
            mod.body().emoteSpeaking();
        } finally {
            sendingSelf = false;
        }
        mod.context().addGame(cfg().botName + ": " + line, cfg().timing.contextSize);
        lastReplyAt = now();
        if (target != null && !target.isBlank()) {
            lastTalkedWith = target;
            lastTalkedAt = now();
            mod.context().relationships().recordBotDirectedMessage(target);
        }
        mod.discord().relayToDiscord(line);
    }

    public void broadcastAsVitoria(String line, String target) {
        if (server == null) return;
        MutableText msg = Text.literal("<")
                .append(Text.literal("Vitória").formatted(Formatting.LIGHT_PURPLE))
                .append(Text.literal("> " + line));
        sendingSelf = true;
        try {
            if (target != null && !target.isBlank()) {
                ServerPlayerEntity targetPlayer = server.getPlayerManager().getPlayer(target);
                if (targetPlayer != null && targetPlayer.isAlive()) {
                    targetPlayer.sendMessage(msg, false);
                } else {
                    server.getPlayerManager().broadcast(msg, false);
                }
            } else {
                server.getPlayerManager().broadcast(msg, false);
            }
            mod.vitoria().emoteSpeaking();
        } finally {
            sendingSelf = false;
        }
        mod.context().addGame("Vitória: " + line, cfg().timing.contextSize);
        lastReplyAt = now();
        if (target != null && !target.isBlank()) {
            lastTalkedWith = target;
            lastTalkedAt = now();
            mod.context().relationships().recordBotDirectedMessage(target);
        } else {
            mod.discord().relayToDiscord("[Vitória] " + line);
        }
    }

    public void sendPrivateAsJavidizineo(ServerPlayerEntity player, String line) {
        if (server == null || player == null || !player.isAlive()) return;
        MutableText msg = Text.literal("<")
                .append(Text.literal("Javidizineo").formatted(Formatting.GOLD))
                .append(Text.literal("> "))
                .append(Text.literal(line).formatted(Formatting.YELLOW));
        player.sendMessage(msg, false);

        String name = player.getGameProfile().getName();
        lastJavidTalkedWith = name;
        lastJavidTalkedAt = now();

        if (mod.javidizineo() != null) {
            CatEntity cat = mod.javidizineo().getCat();
            if (cat != null && cat.isAlive() && cat.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.75f, 1.25f);
                sw.spawnParticles(ParticleTypes.CRIT, cat.getX(), cat.getY() + 0.5, cat.getZ(), 4, 0.15, 0.15, 0.15, 0.04);
            }
        }
        mod.context().addGame("Javidizineo (privado para " + name + "): " + line, cfg().timing.contextSize);
    }

    public void handleJavidizineoChat(ServerPlayerEntity player, String text, String playerName) {
        if (server == null || scheduler == null || player == null) return;
        String lower = text.toLowerCase(Locale.ROOT);

        // Se a API estiver sem chave configurada, responde imediatamente pela biblioteca offline
        if (!cfg().hasApiKey()) {
            String fallbackReply = JavidizineoDialogueLibrary.getRandomResponse(lower);
            double typing = cfg().timing.typingBaseSeconds + fallbackReply.length() * cfg().timing.typingSecondsPerChar;
            long delayMs = Math.min((long) (typing * 1000), 2500L);
            scheduler.schedule(() -> server.execute(() -> sendPrivateAsJavidizineo(player, fallbackReply)), delayMs, TimeUnit.MILLISECONDS);
            return;
        }

        if (!javidBusy.compareAndSet(false, true)) {
            String fallbackReply = JavidizineoDialogueLibrary.getRandomResponse(lower);
            server.execute(() -> sendPrivateAsJavidizineo(player, fallbackReply));
            return;
        }

        scheduler.schedule(() -> {
            try {
                String system = JavidizineoDialogueLibrary.buildSystemPrompt();
                String user = playerName + " acabou de falar com você no chat: \"" + text + "\". "
                        + "Responda diretamente a ele mantendo sua persona cômica de Juiz do Deserto de Grandis / Shamash (Dislyte) preso no corpinho de um leãozinho laranja, em 1 ou 2 frases curtas!";

                mod.llm().chat(cfg().api, system, user, cfg().api.maxTokens).whenComplete((res, err) -> {
                    try {
                        if (err != null || res == null || res.isBlank()) {
                            String fallbackReply = JavidizineoDialogueLibrary.getRandomResponse(lower);
                            server.execute(() -> sendPrivateAsJavidizineo(player, fallbackReply));
                            return;
                        }
                        List<String> lines = clean(res);
                        if (!lines.isEmpty()) {
                            double delay = 0;
                            for (String line : lines) {
                                double typing = cfg().timing.typingBaseSeconds + line.length() * cfg().timing.typingSecondsPerChar;
                                delay += Math.min(typing, cfg().timing.typingMaxSeconds);
                                long ms = (long) (delay * 1000);
                                scheduler.schedule(() -> server.execute(() -> sendPrivateAsJavidizineo(player, line)), ms, TimeUnit.MILLISECONDS);
                            }
                        } else {
                            String fallbackReply = JavidizineoDialogueLibrary.getRandomResponse(lower);
                            server.execute(() -> sendPrivateAsJavidizineo(player, fallbackReply));
                        }
                    } finally {
                        javidBusy.set(false);
                    }
                });
            } catch (Exception e) {
                javidBusy.set(false);
                String fallbackReply = JavidizineoDialogueLibrary.getRandomResponse(lower);
                server.execute(() -> sendPrivateAsJavidizineo(player, fallbackReply));
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    public void sendPrivateAsJinqiuzineo(ServerPlayerEntity player, String line) {
        if (server == null || player == null || !player.isAlive()) return;
        MutableText msg = Text.literal("<")
                .append(Text.literal("Jinqiuzineo").formatted(Formatting.DARK_PURPLE))
                .append(Text.literal("> "))
                .append(Text.literal(line).formatted(Formatting.LIGHT_PURPLE));
        player.sendMessage(msg, false);

        String name = player.getGameProfile().getName();
        lastJinqiuTalkedWith = name;
        lastJinqiuTalkedAt = now();

        if (mod.jinqiuzineo() != null) {
            CatEntity cat = mod.jinqiuzineo().getCat();
            if (cat != null && cat.isAlive() && cat.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, cat.getX(), cat.getY(), cat.getZ(), SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.NEUTRAL, 0.75f, 0.9f);
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, cat.getX(), cat.getY() + 0.4, cat.getZ(), 4, 0.15, 0.15, 0.15, 0.03);
            }
        }
        mod.context().addGame("Jinqiuzineo (privado para " + name + "): " + line, cfg().timing.contextSize);
    }

    public void handleJinqiuzineoChat(ServerPlayerEntity player, String text, String playerName) {
        if (server == null || scheduler == null || player == null) return;
        String lower = text.toLowerCase(Locale.ROOT);

        // Se a API estiver sem chave configurada, responde imediatamente pela biblioteca offline
        if (!cfg().hasApiKey()) {
            String fallbackReply = JinqiuzineoDialogueLibrary.getRandomResponse(lower);
            double typing = cfg().timing.typingBaseSeconds + fallbackReply.length() * cfg().timing.typingSecondsPerChar;
            long delayMs = Math.min((long) (typing * 1000), 2500L);
            scheduler.schedule(() -> server.execute(() -> sendPrivateAsJinqiuzineo(player, fallbackReply)), delayMs, TimeUnit.MILLISECONDS);
            return;
        }

        if (!jinqiuBusy.compareAndSet(false, true)) {
            String fallbackReply = JinqiuzineoDialogueLibrary.getRandomResponse(lower);
            server.execute(() -> sendPrivateAsJinqiuzineo(player, fallbackReply));
            return;
        }

        scheduler.schedule(() -> {
            try {
                String system = JinqiuzineoDialogueLibrary.buildSystemPrompt();
                String user = playerName + " acabou de falar com você no chat: \"" + text + "\". "
                        + "Responda diretamente a ele mantendo sua persona cômica de carcereiro de Aeterna / Ru Shou (Dislyte) preso no corpinho de um tigre/gatinho rajado, em 1 ou 2 frases curtas!";

                mod.llm().chat(cfg().api, system, user, cfg().api.maxTokens).whenComplete((res, err) -> {
                    try {
                        if (err != null || res == null || res.isBlank()) {
                            String fallbackReply = JinqiuzineoDialogueLibrary.getRandomResponse(lower);
                            server.execute(() -> sendPrivateAsJinqiuzineo(player, fallbackReply));
                            return;
                        }
                        List<String> lines = clean(res);
                        if (!lines.isEmpty()) {
                            double delay = 0;
                            for (String line : lines) {
                                double typing = cfg().timing.typingBaseSeconds + line.length() * cfg().timing.typingSecondsPerChar;
                                delay += Math.min(typing, cfg().timing.typingMaxSeconds);
                                long ms = (long) (delay * 1000);
                                scheduler.schedule(() -> server.execute(() -> sendPrivateAsJinqiuzineo(player, line)), ms, TimeUnit.MILLISECONDS);
                            }
                        } else {
                            String fallbackReply = JinqiuzineoDialogueLibrary.getRandomResponse(lower);
                            server.execute(() -> sendPrivateAsJinqiuzineo(player, fallbackReply));
                        }
                    } finally {
                        jinqiuBusy.set(false);
                    }
                });
            } catch (Exception e) {
                jinqiuBusy.set(false);
                String fallbackReply = JinqiuzineoDialogueLibrary.getRandomResponse(lower);
                server.execute(() -> sendPrivateAsJinqiuzineo(player, fallbackReply));
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    // ------------------------------------------------------------------ memória

    public void updateMemory() {
        if (scheduler == null || !memoryBusy.compareAndSet(false, true)) return;
        mod.context().resetEventCounter();
        List<String> notes = mod.context().notesSnapshot();
        List<String> game = mod.context().gameSnapshot();
        List<String> discord = mod.context().discordSnapshot();
        int max = cfg().timing.maxMemoryNotes;

        scheduler.execute(() -> {
            String system = "Você cuida do caderninho de memória do personagem \"" + cfg().botName + "\", o gatinho mascote carinhoso, parceiro de jogatina, humilde e melhor amigo de todos no servidor de Minecraft CanalhasSMP. O caderninho guarda o que ele precisa lembrar da galera para conversar de igual para igual, criar piadas internas e manter assuntos interessantes.";
            StringBuilder user = new StringBuilder();
            user.append("ANOTAÇÕES ATUAIS:\n");
            if (notes.isEmpty()) user.append("(nenhuma)\n");
            for (String n : notes) user.append("- ").append(n).append("\n");
            user.append("\nCHAT RECENTE DO JOGO:\n");
            for (String l : game) user.append(l).append("\n");
            if (!discord.isEmpty()) {
                user.append("\nDISCORD RECENTE:\n");
                for (String l : discord) user.append(l).append("\n");
            }
            user.append("\nAtualize as anotações. Guarde: o que cada jogador está construindo ou explorando, apelidos, momentos engraçados, como e para quê morreram, piadas internas e manias. Junte itens repetidos, descarte o que for irrelevante e mantenha o que é útil. ")
                .append("Máximo ").append(max).append(" itens, cada um curto (até 25 palavras), citando o nome do jogador quando houver.\n")
                .append("Responda SOMENTE com um array JSON de strings, sem texto adicional.");

            mod.llm().chat(cfg().api, system, user.toString(), cfg().api.memoryMaxTokens).whenComplete((res, err) -> {
                try {
                    if (err != null) {
                        CumminsMod.LOG.warn("[Cummins] Erro ao atualizar memória: {}", err.getMessage());
                        return;
                    }
                    List<String> parsed = parseJsonArray(res);
                    if (parsed != null && !parsed.isEmpty()) {
                        mod.context().setNotes(parsed, max);
                        mod.context().save();
                        CumminsMod.LOG.info("[Cummins] Memória atualizada ({} anotações).", parsed.size());
                    }
                } finally {
                    memoryBusy.set(false);
                }
            });
        });
    }

    private static List<String> parseJsonArray(String raw) {
        if (raw == null) return null;
        int a = raw.indexOf('['), b = raw.lastIndexOf(']');
        if (a < 0 || b <= a) return null;
        try {
            JsonArray arr = JsonParser.parseString(raw.substring(a, b + 1)).getAsJsonArray();
            List<String> out = new ArrayList<>();
            for (JsonElement e : arr) if (e.isJsonPrimitive()) out.add(e.getAsString());
            return out;
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Resposta de memória não era JSON válido.");
            return null;
        }
    }

    private boolean isTourGuideRequest(String lower, String text, boolean mentioned, boolean inConversation) {
        if (!mentioned && !inConversation) return false;
        return lower.contains("me leva") || lower.contains("me leve") || lower.contains("leva até") || lower.contains("leva ate")
                || lower.contains("me guia") || lower.contains("guia até") || lower.contains("guia ate");
    }

    private void handleTourGuideChat(ServerPlayerEntity player, String text, String name) {
        String lower = text.toLowerCase(Locale.ROOT);
        String place = null;
        String[] markers = {"me leva até ", "me leva ate ", "me leve até ", "me leve ate ", "leva até ", "leva ate ", "me guia até ", "me guia ate ", "guia até ", "guia ate ", "até ", "ate "};
        for (String m : markers) {
            int idx = lower.indexOf(m);
            if (idx != -1) {
                place = text.substring(idx + m.length()).replaceAll("[?!.,]+$", "").trim();
                break;
            }
        }
        if (place == null || place.isBlank()) {
            broadcast("Quer que eu te leve onde, " + name + "? Digite por exemplo: 'cummins me leva até o spawn' ou veja a lista com /pontos! 🐾", name);
            return;
        }
        var wp = mod.tourGuide().findWaypoint(place);
        if (wp != null) {
            mod.tourGuide().startGuide(player, wp);
        } else {
            broadcast("Não conheço o ponto '" + place + "' ainda, " + name + "! Digite /pontos pra ver os lugares cadastrados ou crie um com /cummins ponto criar <nome>! 🐾", name);
        }
    }

    private boolean isContractRequest(String lower, boolean mentioned, boolean inConversation) {
        return (lower.contains("contrato") || lower.contains("tarefa da firma") || lower.contains("trampo da firma"))
                && (mentioned || inConversation || lower.contains("qual") || lower.contains("hoje") || lower.contains("entrega") || lower.contains("entregar"));
    }

    private void handleContractChat(ServerPlayerEntity player, String lower, String name) {
        if (lower.contains("entregar") || lower.contains("entrego") || lower.contains("pronto") || lower.contains("ta aqui") || lower.contains("tá aqui")) {
            mod.contracts().deliverContract(player);
            return;
        }
        var c = mod.contracts().getCurrentContract();
        boolean done = mod.contracts().hasPlayerCompletedToday(name);
        String status = done ? " (Você já cumpriu a sua meta hoje! 🏆)" : " (Quando juntar os itens, digite /entregar! 💼)";
        broadcast("O contrato da firma hoje é: **" + c.title + "**! " + c.description + " Recompensa: " + c.rewardDescription + status, name);
    }

    private boolean isRHRequest(String lower, boolean mentioned, boolean inConversation) {
        return (lower.contains("reclamar") || lower.contains("reclamacao") || lower.contains("reclamação") || lower.contains("processar") || lower.contains("ouvidoria"))
                && (mentioned || inConversation);
    }

    private void handleRHChat(String name) {
        broadcast("Ouvidoria da firma na escuta, " + name + "! Se alguém pisou no seu calo, use o comando oficial do RH: §e/reclamar <alvo> <motivo>§r que vamos abrir um processo administrativo! 📋🐾", name);
    }
}
