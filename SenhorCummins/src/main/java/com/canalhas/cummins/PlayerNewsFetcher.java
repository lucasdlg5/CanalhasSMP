package com.canalhas.cummins;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pesquisa novidades e descobertas reais e recentes na web (via Google News RSS PT-BR)
 * personalizadas de acordo com os temas de interesse e conhecimento de cada jogador
 * registrados na base de dados (memoria.json / config.json).
 *
 * Exemplos:
 * - ArzanLion (Lid) -> McLaren, Fórmula 1, Warframe, carros
 * - kikkawa (Richard) -> Fusão nuclear, energia nuclear, aviação militar/caças, Segunda Guerra Mundial, FFXIV
 * - Asminnnnnnnnnn -> Deadlock (Valve), programação, energia solar / engenharia
 * - Jodevil (Jhon) -> Castlevania, cultura gótica/vampiros, RPGs sombrios
 * - Azarasquel -> Dislyte, RPGs e jogos
 */
public class PlayerNewsFetcher {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern ITEM_PATTERN = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?\\s*</title>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Random RANDOM = new Random();

    // Guarda manchetes já comentadas na sessão para nunca repetir a mesma notícia
    private static final Set<String> USED_HEADLINES = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static class PlayerTopicNews {
        public final String playerName;
        public final String nickname;
        public final String topicLabel;
        public final String headline;

        public PlayerTopicNews(String playerName, String nickname, String topicLabel, String headline) {
            this.playerName = playerName;
            this.nickname = nickname;
            this.topicLabel = topicLabel;
            this.headline = headline;
        }
    }

    private static class TopicQuery {
        final String topicLabel;
        final String searchQuery;
        final List<String> offlineFallbacks;

        TopicQuery(String topicLabel, String searchQuery, List<String> offlineFallbacks) {
            this.topicLabel = topicLabel;
            this.searchQuery = searchQuery;
            this.offlineFallbacks = offlineFallbacks;
        }
    }

    private static final Map<String, List<TopicQuery>> PLAYER_TOPICS = new HashMap<>();
    private static final Map<String, String> PLAYER_NICKNAMES = new HashMap<>();

    static {
        // ArzanLion (Lid)
        PLAYER_NICKNAMES.put("arzanlion", "Lid");
        PLAYER_TOPICS.put("arzanlion", List.of(
                new TopicQuery("a McLaren", "McLaren Formula 1 carros novidade", List.of(
                        "a McLaren anunciou atualizações aerodinâmicas pesadas no carro da F1 e um novo supercarro híbrido de pista",
                        "a McLaren bateu recorde de pit stop e revelou novidades no desenvolvimento do motor pra próxima temporada"
                )),
                new TopicQuery("o Warframe", "Warframe atualizacao novidade Digital Extremes", List.of(
                        "a Digital Extremes revelou uma expansão nova pro Warframe cheia de lore da Lotus e armas incarnon novas"
                )),
                new TopicQuery("a engenharia automotiva e a McLaren", "McLaren supercarro lancamento", List.of(
                        "a divisão automotiva da McLaren mostrou um novo protótipo de fibra de carbono ultraleve"
                ))
        ));

        // kikkawa (Richard)
        PLAYER_NICKNAMES.put("kikkawa", "Richard");
        PLAYER_TOPICS.put("kikkawa", List.of(
                new TopicQuery("a pesquisa de fusão nuclear", "fusao nuclear descoberta pesquisa reator", List.of(
                        "cientistas bateram um novo recorde de tempo mantendo plasma superaquecido a mais de 100 milhões de graus num reator tokamak de fusão nuclear",
                        "pesquisadores conseguiram um ganho líquido de energia inédito usando ímãs supercondutores de nova geração na fusão nuclear"
                )),
                new TopicQuery("aviação militar e caças históricos", "caca militar aviacao descoberta segunda guerra", List.of(
                        "restauraram um caça raríssimo da Segunda Guerra Mundial em condições de voo e descobriram arquivos inéditos de engenharia aérea",
                        "saíram novos testes dos caças de sexta geração com controle assistido e motores de ciclo adaptativo"
                )),
                new TopicQuery("o Final Fantasy XIV", "Final Fantasy XIV atualizacao novidade", List.of(
                        "o produtor Yoshi-P comentou sobre as novas raids e ajustes de classes que vão chegar no Final Fantasy XIV"
                ))
        ));

        // Asminnnnnnnnnn (Asminn)
        PLAYER_NICKNAMES.put("asminnnnnnnnnn", "Asminn");
        PLAYER_TOPICS.put("asminnnnnnnnnn", List.of(
                new TopicQuery("o Deadlock da Valve", "Deadlock Valve atualizacao heroi patch", List.of(
                        "a Valve soltou um patch gigante no Deadlock mudando o mapa, balanceando as lanes e ajustando os heróis novos"
                )),
                new TopicQuery("tecnologia de energia solar e engenharia", "energia solar descoberta eficiencia painel", List.of(
                        "pesquisadores criaram células solares de perovskita em tandem que bateram recorde mundial de eficiência energética"
                )),
                new TopicQuery("engenharia de software e IA", "inteligencia artificial programacao descoberta", List.of(
                        "saíram novos compiladores e arquiteturas de código aberto que aceleram processamento em tempo real"
                ))
        ));

        // Jodevil (Jhon / Vampirão)
        PLAYER_NICKNAMES.put("jodevil", "Jhon");
        PLAYER_TOPICS.put("jodevil", List.of(
                new TopicQuery("Castlevania e jogos de vampiro", "Castlevania novidade jogo serie", List.of(
                        "saíram novidades quentes sobre o universo de Castlevania e novos projetos góticos inspirados em Symphony of the Night"
                )),
                new TopicQuery("tecnologia de reatores e automação", "reator nuclear tecnologia descoberta", List.of(
                        "novos reatores modulares compactos estão sendo testados para gerar energia limpa contínua"
                ))
        ));

        // Aster (Agro_Vamp)
        PLAYER_NICKNAMES.put("aster", "Aster");
        PLAYER_NICKNAMES.put("agro_vamp", "Aster");
        PLAYER_TOPICS.put("aster", List.of(
                new TopicQuery("o Deadlock", "Deadlock Valve novidade atualizacao", List.of(
                        "a comunidade descobriu combos novos absurdos com os personagens recém-adicionados no Deadlock"
                ))
        ));

        // Azarasquel
        PLAYER_NICKNAMES.put("azarasquel", "Azarasquel");
        PLAYER_TOPICS.put("azarasquel", List.of(
                new TopicQuery("o Dislyte e jogos gacha", "Dislyte jogo atualizacao novidade", List.of(
                        "saíram eventos novos e balanceamento dos Espers favoritos da comunidade no Dislyte"
                )),
                new TopicQuery("hardware retrô e armazenamento", "tecnologia armazenamento descoberta", List.of(
                        "pesquisadores desenvolveram um disco óptico experimental que guarda petabytes de dados do tamanho de um disquete antigo"
                ))
        ));
    }

    /**
     * Busca uma novidade recente na web sobre um tema que o jogador domina/gosta,
     * combinando os perfis conhecidos + as anotações da base de dados (memoria.json).
     */
    public static PlayerTopicNews fetchNewsForPlayer(String playerName, List<String> memoryNotes) {
        return fetchNewsForPlayer(playerName, memoryNotes, null);
    }

    public static PlayerTopicNews fetchNewsForPlayer(String playerName, List<String> memoryNotes, PlayerRelationshipManager.Profile profile) {
        if (playerName == null || playerName.isBlank()) return null;
        String key = playerName.toLowerCase(Locale.ROOT);
        String nickname = PLAYER_NICKNAMES.getOrDefault(key, playerName);

        List<TopicQuery> candidates = new ArrayList<>();
        if (PLAYER_TOPICS.containsKey(key)) {
            candidates.addAll(PLAYER_TOPICS.get(key));
        }

        // Adiciona os gostos e interesses aprendidos em conversas individuais com o jogador!
        if (profile != null && profile.tastesAndInterests != null) {
            for (String interest : profile.tastesAndInterests) {
                if (interest == null || interest.isBlank()) continue;
                String clean = interest.trim();
                boolean already = candidates.stream().anyMatch(c -> c.topicLabel.toLowerCase(Locale.ROOT).contains(clean.toLowerCase(Locale.ROOT)));
                if (!already) {
                    candidates.add(new TopicQuery(clean, clean + " novidade descoberta", List.of(
                            "saíram novidades muito comentadas recentemente sobre " + clean
                    )));
                }
            }
        }

        // Também extrai temas dinamicamente das notas da base de dados (memoria.json) para esse jogador!
        if (memoryNotes != null) {
            for (String note : memoryNotes) {
                String lowerNote = note.toLowerCase(Locale.ROOT);
                if (lowerNote.contains(key) || lowerNote.contains(nickname.toLowerCase(Locale.ROOT))) {
                    extractDynamicTopicsFromNote(note, candidates);
                }
            }
        }

        // Se for um jogador novo ainda sem tópicos específicos, usa tópicos gerais de ciência/games/carros
        if (candidates.isEmpty()) {
            candidates.add(new TopicQuery("a McLaren na Fórmula 1", "McLaren Formula 1 novidade", List.of(
                    "a McLaren trouxe um pacote novo de atualizações aerodinâmicas que surpreendeu nos treinos da F1"
            )));
            candidates.add(new TopicQuery("a descoberta recente na fusão nuclear", "fusao nuclear descoberta pesquisa", List.of(
                    "cientistas anunciaram um avanço importante no confinamento magnético de fusão nuclear limpa"
            )));
        }

        TopicQuery chosen = candidates.get(RANDOM.nextInt(candidates.size()));

        // Tenta buscar uma notícia real e recente no Google News RSS em português
        String liveHeadline = fetchLiveGoogleNewsHeadline(chosen.searchQuery);
        if (liveHeadline == null || liveHeadline.isBlank()) {
            List<String> fb = chosen.offlineFallbacks;
            liveHeadline = fb.get(RANDOM.nextInt(fb.size()));
        }

        return new PlayerTopicNews(playerName, nickname, chosen.topicLabel, liveHeadline);
    }

    private static void extractDynamicTopicsFromNote(String note, List<TopicQuery> out) {
        String lower = note.toLowerCase(Locale.ROOT);
        if (lower.contains("mclaren") && out.stream().noneMatch(t -> t.topicLabel.toLowerCase().contains("mclaren"))) {
            out.add(new TopicQuery("a McLaren", "McLaren Formula 1 novidade", List.of(
                    "a McLaren revelou novidades técnicas importantes para as próximas corridas"
            )));
        }
        if ((lower.contains("nuclear") || lower.contains("usina")) && out.stream().noneMatch(t -> t.topicLabel.toLowerCase().contains("nuclear"))) {
            out.add(new TopicQuery("a descoberta na fusão nuclear", "fusao nuclear descoberta pesquisa", List.of(
                    "pesquisadores atingiram um novo marco de estabilidade de plasma na pesquisa de fusão nuclear"
            )));
        }
        if (lower.contains("warframe") && out.stream().noneMatch(t -> t.topicLabel.toLowerCase().contains("warframe"))) {
            out.add(new TopicQuery("o Warframe", "Warframe novidade atualizacao", List.of(
                    "saíram novidades quentes na última Devstream do Warframe"
            )));
        }
        if (lower.contains("deadlock") && out.stream().noneMatch(t -> t.topicLabel.toLowerCase().contains("deadlock"))) {
            out.add(new TopicQuery("o Deadlock", "Deadlock Valve atualizacao", List.of(
                    "o Deadlock recebeu uma atualização grande de mapa e novos ajustes de heróis"
            )));
        }
        if (lower.contains("botania") || lower.contains("rpg")) {
            out.add(new TopicQuery("o mundo dos RPGs de fantasia", "RPG fantasia jogos novidade", List.of(
                    "saíram anúncios novos de expansões grandes nos RPGs de fantasia deste ano"
            )));
        }
    }

    private static String fetchLiveGoogleNewsHeadline(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://news.google.com/rss/search?q=" + encoded + "&hl=pt-BR&gl=BR&ceid=BR:pt-419";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .GET()
                    .build();

            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() != 200 || res.body() == null) return null;

            Matcher itemMatcher = ITEM_PATTERN.matcher(res.body());
            List<String> headlines = new ArrayList<>();
            while (itemMatcher.find() && headlines.size() < 10) {
                String itemXml = itemMatcher.group(1);
                Matcher titleMatcher = TITLE_PATTERN.matcher(itemXml);
                if (titleMatcher.find()) {
                    String title = decodeHtmlEntities(titleMatcher.group(1).trim());
                    // Remove o sufixo " - Nome do Portal" no final do título do Google News
                    int dashIdx = title.lastIndexOf(" - ");
                    if (dashIdx > 15) {
                        title = title.substring(0, dashIdx).trim();
                    }
                    if (title.length() >= 18 && !USED_HEADLINES.contains(title)) {
                        headlines.add(title);
                    }
                }
            }

            if (!headlines.isEmpty()) {
                String picked = headlines.get(RANDOM.nextInt(Math.min(4, headlines.size())));
                USED_HEADLINES.add(picked);
                return picked;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String decodeHtmlEntities(String s) {
        if (s == null) return "";
        return s.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&nbsp;", " ");
    }
}
