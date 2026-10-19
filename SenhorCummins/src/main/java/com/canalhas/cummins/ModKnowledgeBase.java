package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Base de conhecimento local sobre os mods do CanalhasSMP / Prominence II.
 * Responde dúvidas comuns sobre Create, Zenith, Mochilas, Netherita, Artifacts, etc.
 * com ZERO consumo de tokens de API e resposta instantânea!
 * Armazena e carrega os dados em config/senhor-cummins/mod_knowledge.json.
 */
public class ModKnowledgeBase {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static class ModEntry {
        public String id;
        public String modName;
        public List<List<String>> matchRules = new ArrayList<>(); // Cada sublista é um conjunto de palavras AND que devem estar na pergunta
        public String answer;

        public ModEntry() {}

        public ModEntry(String id, String modName, List<List<String>> matchRules, String answer) {
            this.id = id;
            this.modName = modName;
            this.matchRules = matchRules;
            this.answer = answer;
        }
    }

    public static class KnowledgeData {
        public List<ModEntry> entries = new ArrayList<>();
    }

    private static KnowledgeData data = new KnowledgeData();

    public static void load(Path file) {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                KnowledgeData loaded = GSON.fromJson(r, KnowledgeData.class);
                if (loaded != null && loaded.entries != null && !loaded.entries.isEmpty()) {
                    data = loaded;
                    return;
                }
            } catch (Exception e) {
                CumminsMod.LOG.warn("[Cummins] Falha ao ler mod_knowledge.json: {}", e.getMessage());
            }
        }
        populateDefaults();
        save(file);
    }

    public static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao salvar mod_knowledge.json: {}", e.getMessage());
        }
    }

    /**
     * Busca na base local se há uma resposta exata para a pergunta do jogador.
     */
    public static String findAnswer(String question) {
        if (question == null || question.isBlank() || data == null || data.entries == null) return null;
        String normalized = normalize(question);

        for (ModEntry entry : data.entries) {
            for (List<String> ruleGroup : entry.matchRules) {
                boolean allMatch = true;
                for (String keyword : ruleGroup) {
                    if (!normalized.contains(normalize(keyword))) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    return entry.answer;
                }
            }
        }
        return null;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{M}", "");
        n = n.toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9\\s]", " ");
    }

    private static void populateDefaults() {
        data.entries = new ArrayList<>();

        // 1. Molde de Netherita
        data.entries.add(new ModEntry(
                "netherite_upgrade_template",
                "Minecraft 1.20+",
                List.of(
                        List.of("netherita", "molde"),
                        List.of("netherita", "template"),
                        List.of("upar", "diamante", "netherita"),
                        List.of("upgrade", "netherita"),
                        List.of("melhorar", "netherita"),
                        List.of("duplicar", "molde")
                ),
                "No 1.20 pra upar diamante pra netherita precisa do Molde de Aprimoramento (Smithing Template). Ele spawna nos baús de Bastiões dos Piglins no Nether (100% na sala do tesouro). Com 1 molde, duplique na bancada usando: 1 Molde + 7 Diamantes + 1 Netherrack!"
        ));

        // 2. Onde achar Debris Ancestrais
        data.entries.add(new ModEntry(
                "ancient_debris_location",
                "Minecraft",
                List.of(
                        List.of("onde", "debris"),
                        List.of("camada", "debris"),
                        List.of("achar", "debris"),
                        List.of("camada", "netherita"),
                        List.of("onde", "netherita")
                ),
                "Debris Ancestrais spawnam no Nether entre as camadas Y=8 e Y=22, sendo a camada Y=15 a mais recheada! A melhor tática é cavar retão na Y=15 e ir explodindo camas ou dinamites em linha reta."
        ));

        // 3. Mochilas (Backpack Upgrades)
        data.entries.add(new ModEntry(
                "backpack_upgrades",
                "Beans Backpacks / Traveler's",
                List.of(
                        List.of("mochila", "upgrade"),
                        List.of("upar", "mochila"),
                        List.of("evoluir", "mochila"),
                        List.of("mochila", "netherita"),
                        List.of("melhorar", "mochila")
                ),
                "A evolução das mochilas segue a ordem dos minérios: Couro -> Cobre -> Ferro -> Ouro -> Diamante -> Netherita. Coloque a mochila atual no centro da bancada ou mesa de ferraria rodeada pelas barras do próximo nível!"
        ));

        // 4. Create - Misturador Mecânico (Mechanical Mixer)
        data.entries.add(new ModEntry(
                "create_mechanical_mixer",
                "Create",
                List.of(
                        List.of("misturador"),
                        List.of("mixer"),
                        List.of("create", "misturar")
                ),
                "O Misturador Mecânico do Create precisa de uma Bacia (Basin) exatamente 1 bloco abaixo dele e rotação entrando por cima ou lados. Pra receitas avançadas (ligas, latão, poções), coloque um Blaze Burner aceso com carvão embaixo da bacia!"
        ));

        // 5. Create - Prensa Mecânica (Mechanical Press)
        data.entries.add(new ModEntry(
                "create_mechanical_press",
                "Create",
                List.of(
                        List.of("prensa"),
                        List.of("press", "create")
                ),
                "A Prensa Mecânica precisa de rotação entrando por cima ou pelas laterais e de um Depósito (Depot) ou Esteira logo abaixo dela. Basta jogar os lingotes ou canas no depósito que ela esmaga automaticamente!"
        ));

        // 6. Create - Overstressed / Sem Força / Parou de girar
        data.entries.add(new ModEntry(
                "create_overstressed",
                "Create",
                List.of(
                        List.of("overstressed"),
                        List.of("create", "parou"),
                        List.of("create", "nao gira"),
                        List.of("create", "não gira"),
                        List.of("create", "sem forca"),
                        List.of("create", "sem força"),
                        List.of("create", "estresse")
                ),
                "Overstressed significa que as máquinas estão consumindo mais estresse (SU) do que seus geradores produzem! Soluções: adicione mais Rodas d'Água / Moinhos de Vento, ou use engrenagens pra diminuir a velocidade da rede (menos RPM consome menos estresse)!"
        ));

        // 7. Create - Chave Inglesa (Wrench)
        data.entries.add(new ModEntry(
                "create_wrench",
                "Create",
                List.of(
                        List.of("wrench"),
                        List.of("chave", "create"),
                        List.of("chave inglesa")
                ),
                "A Chave Inglesa (Wrench) do Create é indispensável: botão direito nela gira o eixo dos blocos, e Shift + botão direito desmonta qualquer máquina ou engrenagem na hora sem quebrar nada!"
        ));

        // 8. Zenith - Altar de Encantamentos (Eterna, Quanta, Arcana)
        data.entries.add(new ModEntry(
                "zenith_enchanting",
                "Zenith / Apotheosis",
                List.of(
                        List.of("eterna"),
                        List.of("quanta"),
                        List.of("arcana"),
                        List.of("altar", "zenith"),
                        List.of("encantamento", "zenith"),
                        List.of("zenith", "estante")
                ),
                "No Zenith o altar usa 3 estatísticas: Eterna aumenta o nível máximo (até 100), Quanta aumenta a aleatoriedade dos encantamentos, e Arcana aumenta a chance de encantamentos raros! Use estantes marítimas, estantes do nether e velas pra equilibrar."
        ));

        // 9. Zenith - Reforging & Gemas
        data.entries.add(new ModEntry(
                "zenith_reforging",
                "Zenith / Apotheosis",
                List.of(
                        List.of("reforja"),
                        List.of("reforging"),
                        List.of("gem dust"),
                        List.of("gemas", "zenith"),
                        List.of("soquete")
                ),
                "Na Mesa de Reforja (Reforging Table), você usa Pó de Gema (triture gemas jogando bigorna em cima delas) e Materiais de Raridade pra adicionar afixos míticos e soquetes a armas e armaduras!"
        ));

        // 10. Artifacts - Onde encontrar
        data.entries.add(new ModEntry(
                "artifacts_location",
                "Artifacts",
                List.of(
                        List.of("onde", "artefatos"),
                        List.of("onde", "artifacts"),
                        List.of("como acho", "artefatos"),
                        List.of("mimic", "artefato"),
                        List.of("acampamento", "artefato")
                ),
                "Os itens do mod Artifacts são encontrados explorando cavernas profundas: dentro de baús em Acampamentos Subterrâneos (estruturas com fogueira) ou derrotando Mímicos (baús falsos com boca e dentes)!"
        ));

        // 11. Elevador de Água
        data.entries.add(new ModEntry(
                "water_elevator",
                "Minecraft",
                List.of(
                        List.of("elevador", "agua"),
                        List.of("elevador", "água")
                ),
                "Elevador de água que sobe: coloque Areia das Almas (Soul Sand) no chão e encha TODOS os blocos acima com água de fonte (coloque algas até o topo pra virar fonte). Pra descer, coloque Bloco de Magma no fundo!"
        ));

        // 12. Simply Swords & Mythic Metals
        data.entries.add(new ModEntry(
                "simply_swords_runes",
                "Simply Swords",
                List.of(
                        List.of("simply swords"),
                        List.of("runa", "espada"),
                        List.of("espadas", "runas")
                ),
                "No Simply Swords, você encontra Runas em baús de masmorras e vilas. Use a Mesa de Ferraria pra fundir uma runa numa espada de diamante ou netherita e forjar armas com habilidades ativas e efeitos especiais únicos!"
        ));

        // 13. Escala da Longeura Popular (PQP vs Casa do Caralho)
        data.entries.add(new ModEntry(
                "escala_longeura_popular",
                "Cultura & Geografia Brasileira",
                List.of(
                        List.of("casa do caralho", "pqp"),
                        List.of("casa do caralho", "puta que pariu"),
                        List.of("mais longe", "casa do caralho"),
                        List.of("mais longe", "pqp"),
                        List.of("diferenca", "casa do caralho"),
                        List.of("diferença", "casa do caralho")
                ),
                "Pela Escala da Longeura Popular: a Puta que Pariu (PQP) é longe mas aceitável (uns 40min a 1h30, tem civilização e retorno, você acaba indo). 'Onde Judas perdeu as botas' fica no meio do caminho. Já a Casa do Caralho é infinitamente mais longe: exige atravessar rios e montanhas no sol rachando, o celular perde sinal e se der zoom no mapa não há nada ao redor — você faz de tudo pra escapar! Ambas só perdem pro 'Cu do Mundo' e pro 'Logo ali' de um mineiro!"
        ));
    }
}
