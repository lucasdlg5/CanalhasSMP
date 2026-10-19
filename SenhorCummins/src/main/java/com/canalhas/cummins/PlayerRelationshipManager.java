package com.canalhas.cummins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gerencia a relação emocional dinâmica, percepção de vácuo ("sendo ignorado")
 * e a Base de Conhecimento Individual de cada jogador:
 * - Emoções dinâmicas: Sarcasmo, Carinho, Amor, Saudade, Irritação e Vácuo/Ignorado (com decaimento temporal natural).
 * - Gostos e Interesses (tastesAndInterests): jogos, carros, ciência, músicas, comidas, séries, hobbies que o player curte.
 * - Contexto Pessoal e de Jogo (personalContext): o que o player faz no servidor, classe/build, rotina, fatos contados em conversas.
 * - Estilo de Fala Adaptativo (preferredSpeechStyle): como o Cummins molda o jeito de conversar com esse player e no geral.
 */
public class PlayerRelationshipManager {

    private static final Random RANDOM = new Random();

    private static final List<Pattern> INTEREST_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:eu\\s+)?(?:gosto\\s+muito\\s+de|gosto\\s+de|curto\\s+muito|curto|amo|adoro|sou\\s+f[aã]\\s+de|meu\\s+jogo\\s+favorito\\s+[eé]|minha\\s+comida\\s+favorita\\s+[eé]|minha\\s+banda\\s+favorita\\s+[eé]|meu\\s+filme\\s+favorito\\s+[eé]|minha\\s+s[eé]rie\\s+favorita\\s+[eé])\\s+([^,.!?]{3,45})"),
            Pattern.compile("(?i)\\b(?:t[oô]\\s+ouvindo|estou\\s+ouvindo|t[oô]\\s+assistindo|t[oô]\\s+viciado\\s+em)\\s+([^,.!?]{3,45})")
    );

    private static final List<Pattern> CONTEXT_PATTERNS = List.of(
            Pattern.compile("(?i)\\b(?:eu\\s+)?(?:trabalho\\s+com|fa[cç]o\\s+faculdade\\s+de|estudo|t[oô]\\s+construindo|estou\\s+construindo|minha\\s+meta\\s+aqui\\s+[eé]|t[oô]\\s+montando)\\s+([^,.!?]{3,50})")
    );

    public static class Profile {
        public String playerName;
        public double sarcasm = 0.10;   // Baseline 0.10
        public double affection = 0.40; // Baseline 0.40 (naturalmente dócil e amigável)
        public double love = 0.15;      // Baseline 0.15
        public double saudade = 0.00;   // Aumenta com ausência
        public double irritation = 0.00;// Baseline 0.00

        // Percepção de Vácuo ("Sendo Ignorado")
        public double ignored = 0.00;   // Sentimento de estar sendo ignorado/no vácuo (0.0 a 1.0)
        public int consecutiveUnansweredPokes = 0; // Quantas vezes o Cummins falou diretamente com esse player sem receber resposta
        public long lastBotDirectedMessageAt = 0L;
        public boolean awaitingPlayerReply = false;
        public boolean vacuumReactionSent = false; // Garante reação espontânea de vácuo apenas 1x por sequência
        public int justBrokeVacuumStreak = 0;      // Guarda os vácuos recém-quebrados para a primeira resposta após o vácuo
        public double justBrokeIgnoredLevel = 0.0;

        public long lastInteractionAt = System.currentTimeMillis();
        public long lastSeenAt = System.currentTimeMillis();
        public long lastDiscoveryAskedAt = 0;

        public List<String> tastesAndInterests = new ArrayList<>();
        public List<String> personalContext = new ArrayList<>();
        public String preferredSpeechStyle = "";

        public Profile(String playerName) {
            this.playerName = playerName;
            seedDefaultsIfKnown(playerName);
        }

        private void seedDefaultsIfKnown(String name) {
            if (name == null) return;
            String key = name.toLowerCase(Locale.ROOT);
            if (key.equals("arzanlion")) {
                addUnique(tastesAndInterests, "McLaren e Fórmula 1");
                addUnique(tastesAndInterests, "Carros (Peugeot azul, Yaris)");
                addUnique(tastesAndInterests, "Warframe (Lotus)");
                addUnique(personalContext, "Apelido Lid, arqueiro mineiro querido da turma e dono da cachorrinha Vitória (Fox Paulistinha tricolor)");
                preferredSpeechStyle = "Carinhoso, entusiasmado, cheio de parceria automotiva e referências à Vitória e carros";
            } else if (key.equals("kikkawa")) {
                addUnique(tastesAndInterests, "Fusão nuclear e física/energia nuclear");
                addUnique(tastesAndInterests, "Aviação militar, caças e história da Segunda Guerra Mundial");
                addUnique(tastesAndInterests, "Final Fantasy XIV (FFXIV) e pescaria");
                addUnique(personalContext, "Apelido Richard (pipira's catcher), mestre pescador e engenheiro da usina nuclear da base");
                preferredSpeechStyle = "Parceiro, curioso sobre ciência/aviação/FFXIV e respeitoso com as engenhocas nucleares dele";
            } else if (key.equals("asminnnnnnnnnn")) {
                addUnique(tastesAndInterests, "Deadlock (Valve) e jogos competitivos");
                addUnique(tastesAndInterests, "Engenharia, automação com Create, energia solar e programação");
                addUnique(personalContext, "Apelido Asminn, host e engenheiro chefe da base no servidor");
                preferredSpeechStyle = "Descontraído, parceiro de engenharia/Create e bem-humorado";
            } else if (key.equals("jodevil")) {
                addUnique(tastesAndInterests, "Castlevania, cultura gótica e vampiros");
                addUnique(tastesAndInterests, "Tech Reborn e arqueiria");
                addUnique(personalContext, "Apelido Jhon / Vampirão do beco, fã de estética noturna e RPG sombrio");
                preferredSpeechStyle = "Amigável com referências góticas/vampirescas leves e muita parceria";
            } else if (key.equals("azarasquel")) {
                addUnique(tastesAndInterests, "Dislyte (Jinqiu) e jogos de RPG/gacha");
                addUnique(tastesAndInterests, "Hardware retrô e zoeira no chat");
                addUnique(personalContext, "Adora zoar o Lid e o Asminn e viver aventuras caóticas no mapa");
                preferredSpeechStyle = "Brincalhão, bem-humorado e entrando na zoeira saudável";
            } else if (key.equals("aster") || key.equals("agro_vamp")) {
                addUnique(tastesAndInterests, "Deadlock (personagem pintora/artista) e memes");
                addUnique(personalContext, "Participa ativamente das conversas e zoeiras do Discord");
                preferredSpeechStyle = "Leve, divertido e acolhedor";
            }
        }

        public void addInterest(String interest) {
            if (interest == null) return;
            String clean = interest.trim();
            if (clean.length() < 2 || clean.length() > 80) return;
            addUnique(tastesAndInterests, clean);
            while (tastesAndInterests.size() > 18) {
                tastesAndInterests.remove(0);
            }
        }

        public void addContextNote(String ctx) {
            if (ctx == null) return;
            String clean = ctx.trim();
            if (clean.length() < 3 || clean.length() > 110) return;
            addUnique(personalContext, clean);
            while (personalContext.size() > 15) {
                personalContext.remove(0);
            }
        }

        private static void addUnique(List<String> list, String item) {
            for (String existing : list) {
                if (existing.equalsIgnoreCase(item)) return;
            }
            list.add(item);
        }

        public void applyDecay(long now) {
            double hours = (now - lastInteractionAt) / (1000.0 * 3600.0);
            if (hours > 0.05) {
                double decay = Math.exp(-hours / 2.0); // meia-vida de 2 horas
                sarcasm = 0.10 + (sarcasm - 0.10) * decay;
                affection = 0.40 + (affection - 0.40) * decay;
                love = 0.15 + (love - 0.15) * decay;
                irritation = 0.00 + (irritation - 0.00) * decay;
                ignored = 0.00 + (ignored - 0.00) * decay;
            }

            // Saudade aumenta progressivamente se ficar horas sem ver o player
            double awayHours = (now - lastSeenAt) / (1000.0 * 3600.0);
            if (awayHours > 2.0) {
                saudade = Math.min(1.0, (awayHours - 2.0) / 10.0);
            }
        }

        public JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", playerName);
            o.addProperty("sarcasm", Math.round(sarcasm * 100.0) / 100.0);
            o.addProperty("affection", Math.round(affection * 100.0) / 100.0);
            o.addProperty("love", Math.round(love * 100.0) / 100.0);
            o.addProperty("saudade", Math.round(saudade * 100.0) / 100.0);
            o.addProperty("irritation", Math.round(irritation * 100.0) / 100.0);
            o.addProperty("ignored", Math.round(ignored * 100.0) / 100.0);
            o.addProperty("consecutiveUnansweredPokes", consecutiveUnansweredPokes);
            o.addProperty("lastBotDirectedMessageAt", lastBotDirectedMessageAt);
            o.addProperty("awaitingPlayerReply", awaitingPlayerReply);
            o.addProperty("lastInteractionAt", lastInteractionAt);
            o.addProperty("lastSeenAt", lastSeenAt);
            o.addProperty("lastDiscoveryAskedAt", lastDiscoveryAskedAt);

            JsonArray interestsArr = new JsonArray();
            for (String s : tastesAndInterests) interestsArr.add(s);
            o.add("tastesAndInterests", interestsArr);

            JsonArray ctxArr = new JsonArray();
            for (String s : personalContext) ctxArr.add(s);
            o.add("personalContext", ctxArr);

            if (preferredSpeechStyle != null && !preferredSpeechStyle.isBlank()) {
                o.addProperty("preferredSpeechStyle", preferredSpeechStyle);
            }
            return o;
        }

        public static Profile fromJson(JsonObject o) {
            String name = o.has("name") ? o.get("name").getAsString() : "Player";
            Profile p = new Profile(name);
            if (o.has("sarcasm")) p.sarcasm = o.get("sarcasm").getAsDouble();
            if (o.has("affection")) p.affection = o.get("affection").getAsDouble();
            if (o.has("love")) p.love = o.get("love").getAsDouble();
            if (o.has("saudade")) p.saudade = o.get("saudade").getAsDouble();
            if (o.has("irritation")) p.irritation = o.get("irritation").getAsDouble();
            if (o.has("ignored")) p.ignored = o.get("ignored").getAsDouble();
            if (o.has("consecutiveUnansweredPokes")) p.consecutiveUnansweredPokes = o.get("consecutiveUnansweredPokes").getAsInt();
            if (o.has("lastBotDirectedMessageAt")) p.lastBotDirectedMessageAt = o.get("lastBotDirectedMessageAt").getAsLong();
            if (o.has("awaitingPlayerReply")) p.awaitingPlayerReply = o.get("awaitingPlayerReply").getAsBoolean();
            if (o.has("lastInteractionAt")) p.lastInteractionAt = o.get("lastInteractionAt").getAsLong();
            if (o.has("lastSeenAt")) p.lastSeenAt = o.get("lastSeenAt").getAsLong();
            if (o.has("lastDiscoveryAskedAt")) p.lastDiscoveryAskedAt = o.get("lastDiscoveryAskedAt").getAsLong();

            if (o.has("tastesAndInterests") && o.get("tastesAndInterests").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("tastesAndInterests")) {
                    p.addInterest(el.getAsString());
                }
            }
            if (o.has("personalContext") && o.get("personalContext").isJsonArray()) {
                for (JsonElement el : o.getAsJsonArray("personalContext")) {
                    p.addContextNote(el.getAsString());
                }
            }
            if (o.has("preferredSpeechStyle") && !o.get("preferredSpeechStyle").isJsonNull()) {
                p.preferredSpeechStyle = o.get("preferredSpeechStyle").getAsString();
            }
            return p;
        }
    }

    private final Map<String, Profile> profiles = new HashMap<>();

    public synchronized Profile getProfile(String playerName) {
        if (playerName == null || playerName.isBlank()) return null;
        String key = playerName.toLowerCase(Locale.ROOT);
        Profile p = profiles.computeIfAbsent(key, k -> new Profile(playerName));
        p.applyDecay(System.currentTimeMillis());
        return p;
    }

    public synchronized List<Profile> getAllProfiles() {
        return new ArrayList<>(profiles.values());
    }

    /**
     * Chamado sempre que o Cummins enviar uma mensagem direcionada a um jogador específico (target != null).
     * Registra o momento da interação sem exigir obrigatoriedade de resposta dos jogadores.
     */
    public synchronized void recordBotDirectedMessage(String playerName) {
        if (playerName == null || playerName.isBlank() || playerName.equalsIgnoreCase("a galera")) return;
        Profile p = getProfile(playerName);
        if (p == null) return;
        long now = System.currentTimeMillis();
        p.lastBotDirectedMessageAt = now;
        p.lastInteractionAt = now;
        p.lastSeenAt = now;
    }

    /**
     * Chamado no onTick periodicamente para limpeza de timeouts pendentes sem gerar spam ou cobrança.
     */
    public synchronized List<Profile> checkIgnoredTimeouts(long now) {
        for (Profile p : profiles.values()) {
            if (p.awaitingPlayerReply && (now - p.lastBotDirectedMessageAt) > 180_000L) {
                p.awaitingPlayerReply = false;
            }
        }
        return List.of();
    }

    /**
     * Registra uma mensagem do jogador, atualiza o humor dinâmico (incluindo alívio de vácuo)
     * e extrai automaticamente gostos/interesses explícitos que o jogador mencionar no chat.
     * Retorna true se um novo gosto ou contexto foi aprendido diretamente da frase.
     */
    public synchronized boolean recordPlayerMessage(String playerName, String text) {
        Profile p = getProfile(playerName);
        if (p == null || text == null) return false;
        long now = System.currentTimeMillis();
        p.lastSeenAt = now;
        p.lastInteractionAt = now;
        p.saudade = Math.max(0.0, p.saudade - 0.5); // conversar ameniza a saudade

        // Se estava em vácuo antes de responder, preserva o registro para que a resposta imediata possa cobrar o vácuo com bom humor
        if (p.consecutiveUnansweredPokes > 0 || p.ignored >= 0.35) {
            p.justBrokeVacuumStreak = p.consecutiveUnansweredPokes;
            p.justBrokeIgnoredLevel = p.ignored;
        }

        // Jogador falou/respondeu: zera a espera e os vácuos seguidos e reduz ignored em 0.60
        p.awaitingPlayerReply = false;
        p.consecutiveUnansweredPokes = 0;
        p.vacuumReactionSent = false;
        p.ignored = Math.max(0.0, p.ignored - 0.60);

        String lower = text.toLowerCase(Locale.ROOT);

        // Detecção de Sarcasmo / Ironia
        if (containsAny(lower, "nossa que util", "grande ajuda", "nossa ajudou muito", "ah ta", "ah tá",
                "duvido", "aham", "aham senta la", "aham senta lá", "uau", "nossa que genio", "nossa que gênio",
                "kkk ata", "kkkk ata", "parabens nota 0", "parabéns nota 0", "meio obvio", "meio óbvio",
                "grande coisa", "aham sei", "ironico", "irônico", "deboche", "sarcasmo", "sarcástico", "ata")) {
            p.sarcasm = Math.min(1.0, p.sarcasm + 0.25);
        }

        // Detecção de Carinho / Amizade
        if (containsAny(lower, "fofo", "fofinho", "lindinho", "lindo", "te amo", "amo vc", "amo você",
                "amor", "gatinho", "obrigado", "obrigada", "valeu cummins", "obrigado cummins", "fofura",
                "querido", "meu mano", "tmj", "parceiro", "coração", "❤️", "s2", "🥰", "amigo", "parceirinho")) {
            p.affection = Math.min(1.0, p.affection + 0.20);
        }

        // Detecção de Amor / Romance cômico
        if (containsAny(lower, "te amo", "casa comigo", "lindo", "amor da minha vida", "meu bem", "gatinho lindo", "perfeito")) {
            p.love = Math.min(1.0, p.love + 0.25);
        }

        // Detecção de Irritação / Grosseria
        if (containsAny(lower, "chato", "cala a boca", "cala boca", "inutil", "inútil", "some daqui",
                "burro", "para de falar", "sai daqui", "gato idiota", "odeio", "insuportavel", "insuportável")) {
            p.irritation = Math.min(1.0, p.irritation + 0.30);
            p.affection = Math.max(0.0, p.affection - 0.20);
        }

        boolean learnedSomething = false;
        for (Pattern pat : INTEREST_PATTERNS) {
            Matcher m = pat.matcher(text);
            if (m.find()) {
                String extracted = m.group(1).trim();
                if (!extracted.toLowerCase(Locale.ROOT).contains("cummins")) {
                    p.addInterest(extracted);
                    learnedSomething = true;
                }
            }
        }
        for (Pattern pat : CONTEXT_PATTERNS) {
            Matcher m = pat.matcher(text);
            if (m.find()) {
                String extracted = m.group(0).trim();
                p.addContextNote(extracted);
                learnedSomething = true;
            }
        }
        return learnedSomething;
    }

    /**
     * Fazer carinho no gato cura imediatamente a carência de vácuo e sobe carinho/amor.
     */
    public synchronized void recordPet(String playerName) {
        Profile p = getProfile(playerName);
        if (p == null) return;
        long now = System.currentTimeMillis();
        p.lastSeenAt = now;
        p.lastInteractionAt = now;
        p.affection = Math.min(1.0, p.affection + 0.25);
        p.love = Math.min(1.0, p.love + 0.15);
        p.irritation = Math.max(0.0, p.irritation - 0.35);
        p.saudade = 0.0;
        p.ignored = 0.0;
        p.consecutiveUnansweredPokes = 0;
        p.awaitingPlayerReply = false;
        p.vacuumReactionSent = false;
        p.justBrokeVacuumStreak = 0;
        p.justBrokeIgnoredLevel = 0.0;
    }

    /**
     * Sorteia um tema de conversa individual amigável para o Cummins puxar papo com o jogador
     * e descobrir mais sobre os gostos, interesses e contexto dele.
     */
    public synchronized String pickDiscoveryPromptTopic(String playerName) {
        Profile p = getProfile(playerName);
        if (p != null) {
            p.lastDiscoveryAskedAt = System.currentTimeMillis();
        }
        String[] topics = new String[]{
                "qual estilo de música, banda ou cantor ele mais curte ouvir enquanto joga no servidor",
                "qual outro jogo (além do Minecraft) ou franquia marcou a vida dele ou ele anda jogando ultimamente",
                "qual é a comida, lanche ou bebida favorita dele pra acompanhar uma madrugada de jogatina",
                "qual é o maior projeto, construção ou mod que ele mais tá curtindo mexer aqui no CanalhasSMP",
                "qual série, filme, anime ou assunto nerd/curiosidade da vida real ele mais gosta de acompanhar",
                "se ele curte mais a parte de engenharia/automação (tipo Create e Tech Reborn), magia ou sair caçando chefão no mapa",
                "qual carro, esporte ou hobby da vida real ele acha mais dahora"
        };
        return topics[RANDOM.nextInt(topics.length)];
    }

    public synchronized String buildDynamicInstruction(String playerName) {
        Profile p = getProfile(playerName);
        if (p == null) return "";

        double effectiveIgnored = Math.max(p.ignored, p.justBrokeIgnoredLevel);
        int effectiveVacuums = Math.max(p.consecutiveUnansweredPokes, p.justBrokeVacuumStreak);
        // Consome o estado transitório de pós-vácuo após montar a instrução desta resposta
        p.justBrokeVacuumStreak = 0;
        p.justBrokeIgnoredLevel = 0.0;

        StringBuilder sb = new StringBuilder();
        sb.append("\nPERFIL INDIVIDUAL E BASE DE CONHECIMENTO DO JOGADOR ").append(p.playerName).append(":\n");
        sb.append(String.format(Locale.ROOT,
                "- Humor com ele agora -> Sarcasmo: %.0f%% | Carinho: %.0f%% | Amor: %.0f%% | Saudade: %.0f%% | Irritação: %.0f%% | Vácuo/Ignorado: %.0f%% (vácuos seguidos: %d)\n",
                p.sarcasm * 100, p.affection * 100, p.love * 100, p.saudade * 100, p.irritation * 100,
                effectiveIgnored * 100, effectiveVacuums));

        if (!p.tastesAndInterests.isEmpty()) {
            sb.append("- Gostos e Interesses conhecidos de ").append(p.playerName).append(": ")
              .append(String.join("; ", p.tastesAndInterests)).append("\n");
        }
        if (!p.personalContext.isEmpty()) {
            sb.append("- Contexto pessoal/jogo de ").append(p.playerName).append(": ")
              .append(String.join("; ", p.personalContext)).append("\n");
        }
        if (p.preferredSpeechStyle != null && !p.preferredSpeechStyle.isBlank()) {
            sb.append("- Tom de fala ideal com ").append(p.playerName).append(": ").append(p.preferredSpeechStyle).append("\n");
        }

        sb.append("COMO VOCÊ DEVE MOLDAR SUA FALA COM ELE AGORA (DE IGUAL PRA IGUAL, SEM AR DE SUPERIORIDADE):\n");
        if (p.sarcasm > 0.40) {
            sb.append("- O nível de sarcasmo com ele está ALTO: devolva as tiradas com ironia debochada e zoeira saudável na mesma moeda!\n");
        }
        if (p.affection > 0.60) {
            sb.append("- O nível de carinho com ele está MUITO ALTO: seja super carinhoso, doce, acolhedor e mostre que adora a companhia dele!\n");
        }
        if (p.love > 0.40) {
            sb.append("- O nível de amor/afeto com ele está elevado: solte uma cantadinha boba/cringe ou demonstre apego fofo!\n");
        }
        if (p.saudade > 0.50) {
            sb.append("- O nível de saudade está ALTO: comemore muito que ele apareceu no chat e diga que sentiu falta!\n");
        }
        if (p.irritation > 0.40) {
            sb.append("- Ele foi meio rude recentemente: responda fazendo birra fofa de gato orgulhoso, sem grosseria.\n");
        }
        sb.append("- Use os gostos, interesses e contexto dele acima com naturalidade quando tiver relação com o assunto!\n");

        return sb.toString();
    }

    /**
     * Constrói um resumo coletivo dos gostos e estilos dos jogadores atualmente online
     * para moldar a fala geral do Cummins no servidor.
     */
    public synchronized String buildCollectiveOnlineContext(List<String> onlineNames) {
        if (onlineNames == null || onlineNames.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("PERFIL DA TURMA ONLINE AGORA (molde sua linguagem, referências e assuntos para combinar com quem está no servidor):\n");
        for (String name : onlineNames) {
            Profile p = getProfile(name);
            if (p == null) continue;
            sb.append("- ").append(p.playerName).append(": ");
            if (!p.tastesAndInterests.isEmpty()) {
                sb.append("curte [").append(String.join(", ", p.tastesAndInterests)).append("]");
            } else {
                sb.append("ainda descobrindo os gostos dele (puxe papo amigável para conhecê-lo melhor!)");
            }
            if (!p.personalContext.isEmpty()) {
                sb.append(" | contexto: ").append(String.join("; ", p.personalContext));
            }
            if (p.preferredSpeechStyle != null && !p.preferredSpeechStyle.isBlank()) {
                sb.append(" | estilo: ").append(p.preferredSpeechStyle);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public synchronized JsonObject serialize() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Profile> entry : profiles.entrySet()) {
            root.add(entry.getKey(), entry.getValue().toJson());
        }
        return root;
    }

    public synchronized void deserialize(JsonObject root) {
        if (root == null) return;
        profiles.clear();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                Profile p = Profile.fromJson(entry.getValue().getAsJsonObject());
                profiles.put(entry.getKey().toLowerCase(Locale.ROOT), p);
            }
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}
