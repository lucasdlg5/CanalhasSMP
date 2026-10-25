package com.canalhas.jacinto;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * Biblioteca Épica de 27 Volumes (Capítulos I a XXVII) da Lore Vampírica de Conde Jodevil.
 * Cada um dos 27 slots do baú ao lado da cama de Jodevil guarda um livro completo (Tome of Vampiric Knowledge).
 * Escrita inspirada em "Entrevista com o Vampiro", "A Rainha dos Condenados" (Anne Rice) e "Drácula" (Bram Stoker),
 * sem qualquer referência a Minecraft, com começo, meio e fim, e paginação automática sem cortes.
 */
public final class JodevilLoreLibrary {

    private static final int MAX_CHARS_PER_LINE = 19;
    private static final int MAX_LINES_PER_PAGE = 12;

    private JodevilLoreLibrary() {}

    public record ChapterSpec(
            int number,
            String roman,
            String actTitle,
            String chapterTitle,
            String subtitle,
            String[] paragraphs
    ) {}

    /**
     * Gera a coleção completa de 27 livros (Capítulos I a XXVII) para preencher os 27 slots do baú.
     */
    public static List<ItemStack> createAll27ChapterBooks() {
        List<ChapterSpec> specs = buildAll27Chapters();
        List<ItemStack> books = new ArrayList<>(specs.size());
        for (ChapterSpec spec : specs) {
            books.add(createChapterBook(spec));
        }
        return books;
    }

    public static ItemStack createChapterBook(ChapterSpec spec) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();

        String shortTitle = "Tomo " + spec.roman() + " - " + spec.chapterTitle();
        if (shortTitle.length() > 32) {
            shortTitle = shortTitle.substring(0, 32);
        }
        nbt.putString("title", shortTitle);
        nbt.putString("author", "Conde Jodevil & Asminn");
        nbt.putInt("generation", 0);
        nbt.putBoolean("resolved", true);

        // Aparência visual do item "Tome of Vampiric Knowledge" (death_knights:blood_spell_book) via Transmog
        NbtCompound transmogTag = new NbtCompound();
        transmogTag.putString("id", "death_knights:blood_spell_book");
        transmogTag.putByte("Count", (byte) 1);
        nbt.put("transmog:transmogItem", transmogTag);

        // Nome e Lore no Tooltip do item
        NbtCompound display = nbt.getCompound("display");
        display.putString("Name", Text.Serializer.toJson(
                Text.literal("§4§lTome of Vampiric Knowledge — Cap. " + spec.roman() + ": " + spec.chapterTitle())));

        NbtList tooltipLore = new NbtList();
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§6" + spec.actTitle()).formatted(Formatting.GOLD))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7\"" + spec.subtitle() + "\"").formatted(Formatting.GRAY, Formatting.ITALIC))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(""))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§4🩸 Crônicas do Sangue Duvidoso (Volume " + spec.number() + " de 27)").formatted(Formatting.DARK_RED))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§8Clique com o botão direito para ler este capítulo.").formatted(Formatting.DARK_GRAY))));
        display.put("Lore", tooltipLore);
        nbt.put("display", display);

        List<String> pages = paginateChapter(spec);
        NbtList pagesList = new NbtList();
        for (String pageContent : pages) {
            String jsonPage = Text.Serializer.toJson(Text.literal(pageContent));
            pagesList.add(NbtString.of(jsonPage));
        }
        nbt.put("pages", pagesList);

        return book;
    }

    /**
     * Paginador automático que garante que TODA linha tenha <= 19 caracteres visíveis
     * e TODA página tenha <= 12 linhas (de 14 possíveis), impedindo qualquer corte no rodapé.
     */
    private static List<String> paginateChapter(ChapterSpec spec) {
        List<String> pages = new ArrayList<>();

        // Página 1: Folha de Rosto do Capítulo (máximo 11-12 linhas garantidas)
        List<String> coverLines = new ArrayList<>();
        coverLines.add("§4§lCAPÍTULO " + spec.roman() + "§r");
        coverLines.add("§8━━━━━━━━━━━━━━§r");
        for (String titleLine : wrapPlainText(spec.chapterTitle(), 17)) {
            coverLines.add("§4§l" + titleLine + "§r");
        }
        coverLines.add("");
        for (String subLine : wrapPlainText(spec.subtitle(), 18)) {
            if (coverLines.size() < MAX_LINES_PER_PAGE - 2) {
                coverLines.add("§8§o" + subLine + "§r");
            }
        }
        for (String actLine : wrapPlainText(spec.actTitle(), 18)) {
            if (coverLines.size() < MAX_LINES_PER_PAGE) {
                coverLines.add("§0" + actLine);
            }
        }
        pages.add(String.join("\n", coverLines));

        // Demais páginas: Prosa literária fluindo sem cortes
        List<String> allLines = new ArrayList<>();
        for (int i = 0; i < spec.paragraphs().length; i++) {
            String para = spec.paragraphs()[i];
            List<String> wrapped = wrapPlainText(para, MAX_CHARS_PER_LINE);
            allLines.addAll(wrapped);
            if (i < spec.paragraphs().length - 1) {
                allLines.add(""); // linha em branco entre parágrafos
            }
        }

        List<String> currentPageLines = new ArrayList<>();
        for (String line : allLines) {
            // Não inicia página nova com linha em branco
            if (currentPageLines.isEmpty() && line.isEmpty()) {
                continue;
            }
            currentPageLines.add(line);
            if (currentPageLines.size() >= MAX_LINES_PER_PAGE) {
                pages.add(formatPageLines(currentPageLines));
                currentPageLines.clear();
            }
        }
        if (!currentPageLines.isEmpty()) {
            // Remove eventual linha vazia no final da página
            while (!currentPageLines.isEmpty() && currentPageLines.get(currentPageLines.size() - 1).isEmpty()) {
                currentPageLines.remove(currentPageLines.size() - 1);
            }
            if (!currentPageLines.isEmpty()) {
                pages.add(formatPageLines(currentPageLines));
            }
        }

        return pages;
    }

    private static String formatPageLines(List<String> lines) {
        StringBuilder sb = new StringBuilder("§0");
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static List<String> wrapPlainText(String text, int maxCharsPerLine) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        int currentVisibleLen = 0;

        for (String word : words) {
            int wordVisibleLen = visibleLength(word);
            if (currentVisibleLen == 0) {
                current.append(word);
                currentVisibleLen = wordVisibleLen;
            } else if (currentVisibleLen + 1 + wordVisibleLen <= maxCharsPerLine) {
                current.append(" ").append(word);
                currentVisibleLen += 1 + wordVisibleLen;
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
                currentVisibleLen = wordVisibleLen;
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static int visibleLength(String s) {
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) {
                i++; // pula código de cor/formatação
            } else {
                len++;
            }
        }
        return len;
    }

    private static List<ChapterSpec> buildAll27Chapters() {
        List<ChapterSpec> list = new ArrayList<>(27);

        // =========================================================================
        // ATO I — O COMEÇO: A SEDE DE ETERNIDADE E O CREPÚSCULO MORTAL (Cap. I a IX)
        // =========================================================================

        list.add(new ChapterSpec(1, "I", "Ato I: O Começo",
                "O Sussurro da Vela",
                "Onde Conde Jodevil abre sua alma ao cronista Asminn na torre gótica.",
                new String[]{
                        "\"Vejo que trouxeste a pena, o pergaminho e o tinteiro, meu caro §9Asminn§0\", murmurou §4Jodevil§0, enquanto a chama trêmula do candelabro de prata desenhava sombras longas sobre sua face pálida.",
                        "Lá fora, o oceano noturno batia contra os rochedos da cidadela como um coração antigo e inconsolável. O vento de outubro trazia o perfume das folhas mortas e das rosas negras do jardim.",
                        "\"Tu me perguntas como deixei de ser um homem comum para tornar-me esta criatura que teme o meio-dia\", continuou ele, ajustando o colarinho de veludo carmesim com dedos frios e teatrais.",
                        "\"Esperas ouvir um conto de terror digno dos antigos lordes da Transilvânia ou dos salões dourados de Paris. Mas acomoda-te na poltrona, §9Asminn§0, e serve-te de vinho — do bom, não do que usaram em mim.\"",
                        "\"Pois a história da minha imortalidade começa com a mais sublime poesia romântica... e termina na mais absurda tragédia alquímica que estas terras já testemunharam.\""
                }));

        list.add(new ChapterSpec(2, "II", "Ato I: O Começo",
                "A Dor de Ser Mortal",
                "As memórias da juventude de Jodevil e seu horror ao passar do tempo.",
                new String[]{
                        "\"Quando eu ainda respirava o ar morno dos mortais\", suspirou §4Jodevil§0, caminhando até a janela em arco, \"cada batida do meu peito parecia um relógio apressado anunciando o fim da beleza.\"",
                        "\"Eu observava os camponeses e os guerreiros da província. Eles aceitavam o suor da lavoura, as rugas na testa e o esquecimento do túmulo com uma resignação que me causava calafrios.\"",
                        "\"Eu, porém, nascera com uma fome diferente. Eu amava a arquitetura das sombras, o silêncio solene das catedrais vazias e o brilho prateado da lua cheia sobre o mármore frio.\"",
                        "\"Por que deveria um espírito tão refinado, tão talhado para o mistério e para a paixão eterna, submeter-se à banalidade de envelhecer e morrer como um simples aldeão?\"",
                        "\"Foi então que jurei aos céus estrelados: eu não pertenceria ao dia. Eu conquistaria a eternidade da noite, custasse o que custasse à minha alma.\""
                }));

        list.add(new ChapterSpec(3, "III", "Ato I: O Começo",
                "A Rainha Antiga",
                "A descoberta dos pergaminhos proibidos da Mãe de Todos os Imortais.",
                new String[]{
                        "Nas profundezas das bibliotecas esquecidas da cidadela, sob camadas de poeira que nenhum erudito ousava tocar, §4Jodevil§0 encontrou os fragmentos de couro que mudariam seu destino.",
                        "Eram códices antigos que narravam a linhagem da primeira Rainha dos Condenados — aquela cujo sangue ancestral transformava homens em semideuses de alabastro e olhos de brasa.",
                        "\"Li cada linha com as mãos trêmulas de êxtase\", confessou §4Jodevil§0 a §9Asminn§0. \"As crônicas falavam de seres que caminhavam sobre o vento, cuja voz hipnotizava reis e cujo coração jamais conhecia a fraqueza.\"",
                        "\"Eles bebiam a própria essência da vida em taças de rubi e dançavam sob os séculos enquanto impérios inteiros desmoronavam em pó. Aquilo, §9Asminn§0, era arte! Aquilo era vocação!\"",
                        "\"Fechei o tomo naquela madrugada com lágrimas nos olhos. Eu já tinha a capa, já tinha o olhar melancólico e já dormia até tarde. Só me faltava o sangue imortal.\""
                }));

        list.add(new ChapterSpec(4, "IV", "Ato I: O Começo",
                "Serenata ao Luar",
                "Os ensaios noturnos de Jodevil sob os olhares de Kikkawa e Luwuana.",
                new String[]{
                        "Muito antes de receber qualquer maldição, §4Jodevil§0 decidiu que precisava agir como um lorde vampiro para atrair a atenção das forças ocultas do universo.",
                        "Todas as noites, vestia seu manto mais escuro e subia aos terraços de pedra para declamar poemas trágicos para a lua, treinando seu sorriso enigmático diante dos espelhos d'água.",
                        "Do balcão vizinho, §dLady Kikkawa§0 — acompanhada de seu místico peixe flutuante §bPipira§0 — e a nobre §dDama Luwuana§0 assistiam ao espetáculo tomando chá de ervas.",
                        "\"Ele está fingindo que conversa com os espíritos do abismo de novo?\", sussurrava §dLuwuana§0, segurando o riso atrás do leque de seda enquanto §4Jodevil§0 fazia poses dramáticas contra o vento.",
                        "\"Acho que ele prendeu a capa no candelabro\", respondia §dKikkawa§0. E de fato, ao tentar dar uma meia-volta majestosa, §4Jodevil§0 arrancou a cortina inteira, mas manteve a pose como se tudo fizesse parte do ritual."
                }));

        list.add(new ChapterSpec(5, "V", "Ato I: O Começo",
                "Em Busca do Beijo",
                "As peregrinações pelas matas escuras e o encontro com o morcego Juninho.",
                new String[]{
                        "Durante semanas, §4Jodevil§0 vagou sozinho pelas criptas abandonadas e florestas nebulosas da comarca, deixando o pescoço estrategicamente descoberto na esperança de ser mordido por um Nosferatu.",
                        "\"Ó criaturas da meia-noite!\", clamava ele de braços abertos no meio do cemitério antigo. \"Vinde a mim! Concedei-me o Dote Sombrio e fazei de mim vosso príncipe herdeiro!\"",
                        "Nenhuma criatura ancestral apareceu. Tudo o que saiu das árvores foi um enxame de pernilongos famintos e um pequeno morcego rechonchudo e simpático que pousou em seu ombro.",
                        "\"Ao menos tu me compreendes, criatura das trevas\", disse §4Jodevil§0 solenemente, batizando o pequeno morcego de §8Juninho§0 e alimentando-o com frutinhas silvestres.",
                        "Voltou para casa sem nenhuma mordida vampírica, mas com três picadas de mosquito no queixo e a certeza de que teria de recorrer à ciência proibida dos homens."
                }));

        list.add(new ChapterSpec(6, "VI", "Ato I: O Começo",
                "Diário de Asminn",
                "Registros epistolares de estranhos carregamentos chegando à cidadela.",
                new String[]{
                        "§8Trecho do Diário de Lorde Asminn — 10 de Outubro.§0 O outono trouxe névoas espessas às nossas muralhas, e com elas uma inquietação peculiar entre os moradores da fortaleza.",
                        "Hoje pela manhã, o mercador da estrada relatou que o autoproclamado Grão-Alquimista §6ArzanLion§0 comprou todo o estoque de vinho azedo de terceira categoria da taverna local.",
                        "Mais curioso ainda: §6ArzanLion§0 foi visto colhendo beterrabas passadas na horta comunitária e pedindo dentes de alho mofados sob o pretexto de realizar uma 'transmutação abissal de alta nobreza'.",
                        "Ao cair da tarde, encontrei §4Jodevil§0 nos corredores. Seus olhos brilhavam com uma febre romântica. Disse-me em segredo: '§9Asminn§0, em três noites deixarei de caminhar sob a lei dos mortais.'",
                        "Tentei adverti-lo de que a última poção de §6ArzanLion§0 havia apenas tingido as sobrancelhas de §2Reznozada§0 de azul, mas a vaidade imortal de §4Jodevil§0 já estava surda à razão."
                }));

        list.add(new ChapterSpec(7, "VII", "Ato I: O Começo",
                "O Pacto na Taverna",
                "As negociações secretas entre Jodevil e o ocultista ArzanLion.",
                new String[]{
                        "Foi no canto mais escuro da taverna, sob o olhar atento da fiel cadelinha §eVitória§0, que §4Jodevil§0 e §6ArzanLion§0 selaram o acordo que mudaria a história da corte.",
                        "\"Podes mesmo conceder-me a maldição dos antigos Condes da Noite, §6ArzanLion§0?\", perguntou §4Jodevil§0 em voz baixa, inclinando-se sobre a mesa manchada de cera.",
                        "\"Meu caro §4Jodevil§0\", respondeu §6ArzanLion§0 estufando o peito com absoluta confiança, \"eu domino os mistérios herméticos do Oriente e do Ocidente! Transformar-te em um lorde vampiro é tarefa simples para meu intelecto.\"",
                        "\"Terás a palidez aristocrática, a comunhão com as sombras e a eterna sensibilidade ao astro-rei, exatamente como nos romances antigos!\", garantiu o alquimista.",
                        "\"E quanto custará tamanho milagre profano?\", indagou o futuro conde. \"Três moedas de prata, um queijo curado e não contares a ninguém se o caldeirão fizer barulho de chocalho\", respondeu §6ArzanLion§0."
                }));

        list.add(new ChapterSpec(8, "VIII", "Ato I: O Começo",
                "O Sono de AsterZZZz",
                "Como o acólito adormecido arruinou o pergaminho sagrado dos Cárpatos.",
                new String[]{
                        "Toda grande tragédia gótica depende de um detalhe oculto do destino, e naquela véspera de Lua Sangrenta, o instrumento do caos atendia pelo nome de §5AsterZZZz§0.",
                        "Encarregado por §6ArzanLion§0 de vigiar o único pergaminho original contendo a pronúncia exata do feitiço vampírico em aramaico antigo, o sábio §5AsterZZZz§0 sentou-se junto à escrivaninha.",
                        "Passados exatos quatro minutos de vigília, §5AsterZZZz§0 tombou a cabeça diretamente sobre o pergaminho aberto e mergulhou em um sono tão profundo que nem os trovões o despertavam.",
                        "Durante seis horas, um fio constante e generoso de baba escorreu pelo canto de sua boca, dissolvendo a tinta secular das três estrofes mais importantes da conjuração.",
                        "Quando §6ArzanLion§0 finalmente o sacudiu, metade das palavras místicas havia virado um borrão violeta. \"Bah\", disse o alquimista limpando a folha na manga, \"eu improviso o resto em latim de taverna!\""
                }));

        list.add(new ChapterSpec(9, "IX", "Ato I: O Começo",
                "A Última Aurora",
                "O adeus poético de Jodevil ao sol antes de descer às catacumbas.",
                new String[]{
                        "No dia 14 de outubro, §4Jodevil§0 subiu à torre mais alta para contemplar aquele que julgava ser o último pôr do sol de sua existência mortal.",
                        "O céu ardia em tons de púrpura, sangue e ouro velho. O vento agitava seus cabelos enquanto ele recitava um adeus digno dos grandes poetas malditos da antiguidade.",
                        "\"Adeus, luz efêmera dos homens comuns!\", murmurou ele com a mão sobre o peito. \"Amanhã, quando tornares a brilhar sobre as colinas, eu já serei um soberano do véu noturno!\"",
                        "Mal sabia o infeliz romântico que o sol ouvira sua provocação — e que, graças à alquimia duvidosa que o aguardava no subsolo, aquele astro tornar-se-ia seu mais lento e sarcástico torturador.",
                        "Ao soar das doze badaladas do sino da meia-noite, §4Jodevil§0 envolveu-se na capa e desceu os degraus úmidos rumo à adega de §6ArzanLion§0."
                }));

        // =========================================================================
        // ATO II — O MEIO: O RITUAL DESASTROSO E AS PROVAÇÕES (Cap. X a XVIII)
        // =========================================================================

        list.add(new ChapterSpec(10, "X", "Ato II: O Meio",
                "O Caldeirão Torto",
                "A descida à adega de ArzanLion e a receita profana de beterraba e alho.",
                new String[]{
                        "\"Quando as portas de carvalho da adega se fecharam atrás de mim\", recordou §4Jodevil§0 com um arrepio de desgosto, \"esperei ver um santuário de obsidiana e cálices cravejados de rubis.\"",
                        "\"Em vez disso, §9Asminn§0, deparei-me com um círculo de giz desenhado no chão que mais parecia uma batata amassada, ladeado por quatro velas tortas pingando sebo sobre barris de picles.\"",
                        "No centro fumegava o caldeirão de §6ArzanLion§0. Na falta do autêntico Sangue Real dos Cárpatos, o alquimista despejara duas garrafas de vinho tinto azedo e um litro de caldo grosso de beterraba.",
                        "Para dar \"corpo místico\" à mistura, adicionara três cabeças de alho vencido, pimenta-do-reino, banha de porco derretida e uma folha de louro que encontrara no bolso do colete.",
                        "\"Tem certeza de que vampiros levam alho e beterraba na fórmula?\", perguntei desconfiado. \"Silêncio, neófito!\", bradou §6ArzanLion§0 mexendo a sopa com uma colher de pau. \"A alquimia moderna transcende tradições!\""
                }));

        list.add(new ChapterSpec(11, "XI", "Ato II: O Meio",
                "O Espirro Arcano",
                "O clímax do ritual em latim improvisado e a fumaça cor-de-rosa.",
                new String[]{
                        "A tempestade rugia lá fora quando §6ArzanLion§0 encheu uma caneca de estanho com aquele líquido espesso e violáceo e entregou-a solenemente às mãos de §4Jodevil§0.",
                        "\"Bebe até a última gota enquanto invoco os Senhores do Abismo!\", ordenou o mago. Fechando os olhos e pensando na majestade dos imortais, §4Jodevil§0 engoliu a beberagem de uma só vez.",
                        "§6ArzanLion§0 ergueu o pergaminho babado por §5AsterZZZz§0, estreitou os olhos para tentar ler os borrões e começou a gritar palavras inventadas na hora: \"§oSanguis Noctis... Beterrabus Eternum... Mortis...§0\"",
                        "Foi então que a pimenta-do-reino subiu do caldeirão direto para o nariz do alquimista. No exato instante da canalização suprema, §6ArzanLion§0 soltou um colossal: \"§oATCHIM!§0\"",
                        "O espirro lançou o pergaminho dentro do braseiro. Uma explosão ensurdecedora sacudiu a adega, enchendo o salão com uma nuvem de fumaça cor-de-rosa com cheiro de alho frito, e §4Jodevil§0 caiu desacordado."
                }));

        list.add(new ChapterSpec(12, "XII", "Ato II: O Meio",
                "Renascido pelo Avesso",
                "O despertar de Jodevil com palidez cadavérica e gosto de vinagre.",
                new String[]{
                        "Três horas antes do amanhecer, §4Jodevil§0 abriu os olhos no chão frio da adega. Seu coração batia num compasso lento, cavernoso e estranhamente oco.",
                        "Ergueu as mãos diante do rosto: sua pele de fato adquirira a alvura fria do mármore antigo, e suas pupilas dilatadas enxergavam cada grão de poeira na escuridão.",
                        "Contudo, em vez da força titânica de cem homens, sentia uma enxaqueca monumental, azia de beterraba e um gosto persistente de vinagre barato no céu da boca.",
                        "Ao seu lado, §6ArzanLion§0 abanava a fumaça rosa com o chapéu, sorrindo amarelo: \"Olha, meu caro §4Jodevil§0... considerando que o livro pegou fogo no meio, diria que foi um sucesso de oitenta por cento!\"",
                        "\"Oitenta por cento?!\", rugiu o novo Conde das Trevas, sentindo a voz ecoar com um timbre grave, porém falhando num soluço logo em seguida. \"O que fizeste ao meu sangue, charlatão?!\""
                }));

        list.add(new ChapterSpec(13, "XIII", "Ato II: O Meio",
                "O Voo das Roseiras",
                "Carta de Lady Kikkawa e Dama Luwuana sobre o primeiro teste de voo.",
                new String[]{
                        "§8Carta de Lady Kikkawa e Dama Luwuana aos arquivos da Cidadela.§0 \"Escrevemos este relato para que a posteridade jamais esqueça a cena que presenciamos às quatro horas daquela madrugada.\"",
                        "\"Vimos §4Conde Jodevil§0 emergir das escadarias subterrâneas envolto em sua capa preta, ainda tossindo fumaça cor-de-rosa, mas determinado a provar seus novos poderes sobrenaturais.\"",
                        "\"Subiu ao parapeito de pedra do jardim superior, abriu os braços contra a ventania e bradou a plenos pulmões: §o'Contemplai a metamorfose do Príncipe da Meia-Noite! Transforma-te, carcaça, em morcego!'§0\"",
                        "\"Saltou no ar agitando os braços com admirável convicção teatral. Infelizmente, a alquimia de §6ArzanLion§0 não incluía asas no pacote.\"",
                        "\"§4Jodevil§0 desabou como um saco de batatas diretamente sobre os arbustos de roseiras espinhosas, de onde saiu mancando, com uma rosa presa atrás da orelha, jurando que o vento estava forte demais para voar.\""
                }));

        list.add(new ChapterSpec(14, "XIV", "Ato II: O Meio",
                "A Prova da Prata",
                "O interrogatório inquisitorial conduzido por Azarasquel e Reznozada.",
                new String[]{
                        "Na noite seguinte, a notícia de que §4Jodevil§0 declarara-se formalmente um Vampiro Imortal chegou aos ouvidos dos guardiões da fortaleza: §2Azarasquel§0 e §2Reznozada§0.",
                        "Acompanhado de seu leal leãozinho dourado §6Javidizineo§0, §2Azarasquel§0 cercou o Conde no pátio central, enquanto §2Reznozada§0 trazia um balde de água benta e uma colher de prata.",
                        "\"Se és mesmo uma abominação nosferatu das trevas\", declarou §2Azarasquel§0 solenemente, \"esta prata sagrada há de queimar-te a carne!\" E encostou a colher de sobremesa na bochecha de §4Jodevil§0.",
                        "Não houve fumaça nem chamas profanas. A colher apenas deixou uma mancha vermelha que deu a §4Jodevil§0 uma crise de coceira alérgica no pescoço.",
                        "Quando jogaram a água benta, o Conde apenas espirrou e reclamou que haviam molhado seu veludo novo, fazendo §2Reznozada§0 gargalhar tão alto que acordou metade da guarnição."
                }));

        list.add(new ChapterSpec(15, "XV", "Ato II: O Meio",
                "As Apostas de Corvo",
                "A alvorada decisiva e a banca de apostas no telhado da taverna.",
                new String[]{
                        "Restava, porém, a prova suprema de todo imortal das trevas: o nascer do sol. E ninguém soube aproveitar melhor a ocasião do que o astuto §8Corvo§0.",
                        "Antes que os primeiros raios dourados tingissem o horizonte, §8Corvo§0 empoleirou-se no telhado da taverna com um livro de registros e uma bolsa tilintante de moedas.",
                        "\"Façam suas apostas, senhores da corte!\", anunciava §8Corvo§0. \"Três para um que §4Jodevil§0 explode em cinzas dramáticas ao primeiro raio! Dois para um que é tudo teatro e ele só pega um bronzeado!\"",
                        "Toda a cidadela reuniu-se à sombra das arcadas enquanto §4Jodevil§0, orgulhoso demais para recuar, deu um passo solene para fora do telhado quando o sol despontou sobre o mar.",
                        "O silêncio foi absoluto. O raio solar atingiu em cheio a fronte pálida do Conde."
                }));

        list.add(new ChapterSpec(16, "XVI", "Ato II: O Meio",
                "O Chiado do Sol",
                "A revelação da queimação lenta de 1 ponto por minuto e o risco de 1%.",
                new String[]{
                        "\"Ninguém ganhou a aposta de §8Corvo§0 naquela manhã, §9Asminn§0\", gemeu §4Jodevil§0 cobrindo o rosto com as mãos. \"Pois o feitiço torto de §6ArzanLion§0 reservava-me uma humilhação muito mais refinada!\"",
                        "\"Drácula e os antigos imortais de Anne Rice ardiam em fogueiras majestosas quando tocados pelo sol. Era uma morte rápida, trágica, digna de ópera!\"",
                        "\"Comigo não! Quando o sol me tocou, ouviu-se apenas um chiado baixo — §o'tsssss'§0 — exatamente como uma fatia de toucinho esquecida numa frigideira morna!\"",
                        "\"Descobri então a lei cruel da minha Maldição do Sangue Duvidoso: a cada minuto inteiro exposto ao sol aberto, o astro-rei drena lentamente uma gota da minha vitalidade.\"",
                        "\"Mas há um terror ainda pior: devido ao espirro de §6ArzanLion§0, a cada minuto que o sol me queima, há exatamente uma chance em cem de o feitiço entrar em colapso e reduzir-me a cinzas instantaneamente!\""
                }));

        list.add(new ChapterSpec(17, "XVII", "Ato II: O Meio",
                "O Servo Jacinto",
                "O encontro fatídico com o guardião dos cofres e a perda do Mjölnir.",
                new String[]{
                        "Humilhado pelo sol e caçoado pelos inquisidores, §4Conde Jodevil§0 decidiu na noite seguinte que imporia sua autoridade vampírica sobre o funcionário mais obstinado da cidadela: §6Jacinto§0.",
                        "§6Jacinto§0, o servo imortal de pele ressecada pelo deserto que organizava incansavelmente os cofres e armazéns do reino, caminhava entre as arcas de madeira com sua prancheta invisível.",
                        "\"Ouve-me bem, criatura subalterna!\", bradou §4Jodevil§0 fazendo sua capa esvoaçar e sacando sua arma mais lendária e preciosa — o martelo ancestral §bMjölnir§0, forjado com o poder das tempestades.",
                        "\"Diante de ti está o Senhor da Noite! Curva-te perante o meu poder!\", declarou o Conde, gesticulando com tanta ênfase teatral que, num descuido inexplicável, estendeu o martelo diretamente para as mãos do servo.",
                        "Instintivamente treinado para receber equipamentos para a firma, §6Jacinto§0 segurou o §bMjölnir§0, guardou-o no cinturão, bateu continência e disse: \"Muito obrigado pela doação, patrão! Já está registrado no patrimônio!\""
                }));

        list.add(new ChapterSpec(18, "XVIII", "Ato II: O Meio",
                "Chamas e Súplicas",
                "A fúria impotente de Jodevil tentando queimar Jacinto para reaver a arma.",
                new String[]{
                        "O sangue duvidoso de §4Jodevil§0 ferveu como nunca antes. \"Devolve-me o martelo imediatamente, servo insolente! Foi um gesto dramático, não uma doação!\"",
                        "Mas §6Jacinto§0 apenas ajeitou o capacete de couro e continuou organizando os baús. Furioso, §4Jodevil§0 tentou golpeá-lo, tentou empurrá-lo em poços de fogo e até conjurou labaredas aos pés do guardião.",
                        "Para desespero do Conde, §6Jacinto§0 caminhava entre as chamas sem que um único fio de sua túnica chamuscasse, olhando para §4Jodevil§0 com a serenidade implacável de um burocrata imortal.",
                        "\"Ei patrão\", dizia §6Jacinto§0 enquanto toda a corte se aglomerava para rir da cena, \"tacar fogo em mim não vai fazer o martelo cair! Se pedir com carinho e educação, quem sabe eu te devolvo!\"",
                        "E assim viu-se o temível Príncipe das Trevas seguindo um organizador de baús corredor abaixo, implorando: \"Por favorzinho, §6Jacinto§0, devolve o meu martelo...\""
                }));

        // =========================================================================
        // ATO III — O FIM: A REDENÇÃO NOTURNA E O REINADO IMORTAL (Cap. XIX a XXVII)
        // =========================================================================

        list.add(new ChapterSpec(19, "XIX", "Ato III: O Fim",
                "O Oráculo Felino",
                "A noite de melancolia na torre com o Senhor Cummins e o morcego Juninho.",
                new String[]{
                        "Naquela madrugada chuvosa, após recuperar seu orgulho (e depois de muito implorar no salão), §4Jodevil§0 recolheu-se ao topo de sua torre, sentindo o peso agridoce de sua condição.",
                        "Sentado junto à lareira apagada, perguntava-se se era apenas uma piada entre os vivos e os mortos. Foi quando um miado suave quebrou o silêncio da câmara.",
                        "O místico gato §6Senhor Cummins§0 saltou para o braço de sua poltrona, ronronando com aquela sabedoria cósmica que só os felinos imortais possuem, enquanto o morcego §8Juninho§0 pousava no candelabro trazendo um frasco de §fBat Leite§0.",
                        "Ao acariciar a cabeça do gato sob a luz prateada da lua, §4Jodevil§0 compreendeu algo profundo: mesmo que seu vampirismo tivesse nascido de um espirro e de caldo de beterraba, sua ligação com a noite era real.",
                        "\"Talvez\", sussurrou o Conde para §6Senhor Cummins§0, \"não seja a pureza do feitiço que faz um soberano da noite, mas a grandeza com que ele habita a própria escuridão.\""
                }));

        list.add(new ChapterSpec(20, "XX", "Ato III: O Fim",
                "Carta Sem Reembolso",
                "A resposta oficial de ArzanLion e o presente do chapéu de palha.",
                new String[]{
                        "§8Correspondência deixada sob a porta do quarto de Conde Jodevil por ArzanLion.§0 \"Prezado cliente e amigo §4Conde Jodevil§0, venho por meio desta responder às tuas trinta e sete reclamações formais.\"",
                        "\"Alegas que meu ritual não te concedeu a transformação em névoa nem o domínio sobre alcateias, e que em vez disso sofres de insolação vampírica de um ponto por minuto com risco de combustão.\"",
                        "\"Ora, meu caro! Na cláusula quarta do nosso acordo verbal na taverna, pediste expressamente para 'viver como um autêntico vampiro que teme a luz do sol'. E hoje não há na província quem tema mais o meio-dia do que tu!\"",
                        "\"Portanto, declaro o serviço integralmente cumprido e informo que o laboratório de §6ArzanLion§0 não trabalha com devoluções de moedas nem de queijos já degustados.\"",
                        "\"Como gesto de boa vontade, deixo-te em anexo um chapéu de palha de aba larga para os dias de verão. Com estima alquímica, §6Grão-Mago ArzanLion§0.\""
                }));

        list.add(new ChapterSpec(21, "XXI", "Ato III: O Fim",
                "O Eclipse de Sangue",
                "Quando as hordas da calada cercaram a cidadela e o medo tomou os mortais.",
                new String[]{
                        "O destino, porém, guardava para §4Conde Jodevil§0 a oportunidade de provar que sob sua capa teatral batia o coração de um verdadeiro lorde imortal.",
                        "Na última noite da estação, um eclipse tingiu a lua de vermelho-sangue e uma névoa gélida subiu das ravinas, trazendo consigo legiões de criaturas famintas das profundezas.",
                        "As tochas das muralhas apagaram-se sob o vendaval. Sem a luz do dia e cegos pela cerração noturna, até os mais bravos guerreiros da cidadela recuaram diante dos uivos que vinham da escuridão.",
                        "Mas para §4Jodevil§0, aquela noite sem sol não era um obstáculo — era o seu reino. Sem o astro-rei no céu para drenar-lhe o sangue ou ameaçá-lo com cinzas, sua maldição calava-se perante a majestade da meia-noite.",
                        "No alto do baluarte norte, sua silhueta destacou-se contra a lua escarlate."
                }));

        list.add(new ChapterSpec(22, "XXII", "Ato III: O Fim",
                "A Dança nas Muralhas",
                "O triunfo épico de Conde Jodevil liderando a defesa sob a meia-noite.",
                new String[]{
                        "\"Erguei vossos olhos, filhos da cidadela!\", ecoou a voz de §4Conde Jodevil§0 sobre o estrondo dos trovões, desta vez sem tropeçar na capa e empunhando com glória seu reluzente §bMjölnir§0.",
                        "\"Enquanto o sol dorme no abismo do horizonte, esta noite pertence a mim!\"",
                        "Saltando das ameias para o campo de batalha — desta vez aterrissando de pé sobre a pedra úmida —, §4Jodevil§0 avançou como um espectro carmesim entre as fileiras de monstros.",
                        "Ao seu lado, os feitiços de §6ArzanLion§0 iluminavam o breu, as lâminas de §2Azarasquel§0 e §2Reznozada§0 abriam caminho, as engrenagens de §9Asminn§0 e §dKikkawa§0 rugiam e até §6Jacinto§0 defendia o perímetro dos cofres.",
                        "Durante horas a fio, o Conde lutou na vanguarda da escuridão, inspirando cada alma presente com a bravura romântica e indomável que sempre sonhara possuir."
                }));

        list.add(new ChapterSpec(23, "XXIII", "Ato III: O Fim",
                "O Brinde da Corte",
                "O reconhecimento solene de todos os companheiros ao Conde da Meia-Noite.",
                new String[]{
                        "Quando a última criatura das sombras foi banida das muralhas e a calmaria retornou aos pátios de pedra, toda a irmandade da fortaleza reuniu-se no grande salão.",
                        "§9Lorde Asminn§0 ergueu sua taça primeiro, seguido por §6ArzanLion§0, §dKikkawa§0, §dLuwuana§0, §2Azarasquel§0, §2Reznozada§0, §8Corvo§0 e até §5AsterZZZz§0, que acordara exatamente na hora do banquete.",
                        "\"Ao §4Conde Jodevil§0!\", proclamou §9Asminn§0 com um sorriso. \"Que pode ter nascido do ritual mais duvidoso da história da alquimia, mas provou ser o mais nobre guardião das nossas madrugadas!\"",
                        "\"Eu sempre disse que a receita de beterraba era genial!\", gabou-se §6ArzanLion§0, enquanto §4Jodevil§0 agradecia com uma reverência impecável, enxugando discretamente uma lágrima de emoção gótica.",
                        "Naquela noite, ninguém riu por escárnio; riram juntos como irmãos de armas que compartilhavam o mesmo mundo e a mesma lenda."
                }));

        list.add(new ChapterSpec(24, "XXIV", "Ato III: O Fim",
                "O Castelo sobre o Mar",
                "O refúgio gótico de Jodevil nas alturas e seu leito eterno.",
                new String[]{
                        "Com a paz restaurada, §4Conde Jodevil§0 ergueu seu santuário definitivo no alto das muralhas banhadas pelo oceano morno, onde as nuvens tocam as torres de pedra.",
                        "Ali dispôs seu leito real — guardado pela vigília silenciosa das estátuas e visitado pelos ventos marinhos — onde repousa sempre que o sol ameaça subir ao zênite.",
                        "Aprendera a amar até mesmo as idiossincrasias de sua maldição: a necessidade de caminhar pelas sombras das árvores ao meio-dia, os guarda-sóis elegantes e a adrenalina poética de desafiar o sol.",
                        "Pois que graça teria a imortalidade se não houvesse nela um toque de perigo — aquela uma chance em cem de virar lenda e cinzas a qualquer minuto de descuido?",
                        "Seu castelo tornou-se o farol noturno da província: silencioso sob o sol, mas vibrante e imponente assim que a primeira estrela desponta no firmamento."
                }));

        list.add(new ChapterSpec(25, "XXV", "Ato III: O Fim",
                "Filosofia do Sangue",
                "As reflexões finais de Jodevil sobre perfeição, amizade e imortalidade.",
                new String[]{
                        "A vela de sebo já estava quase no fim quando §4Jodevil§0 concluiu sua narrativa, olhando serenamente para os vinte e sete tomos que §9Asminn§0 ia preenchendo sobre a mesa.",
                        "\"Sabes o que compreendi após tudo isso, meu caro §9Asminn§0?\", perguntou o Conde com voz suave e inspiradora. \"Quando eu lia os livros antigos, julgava que ser imortal era afastar-se dos outros em fria perfeição.\"",
                        "\"Mas os antigos vampiros das lendas eram solitários e amargos. A minha maldição torta, por obra do acaso e da incompetência de §6ArzanLion§0, deu-me algo muito mais precioso.\"",
                        "\"Deu-me histórias para rir ao redor do fogo, companheiros que guardam minhas costas quando o sol nasce e uma corte inteira que celebra cada uma das minhas noites.\"",
                        "\"Não troco meu vampirismo de meia-tigela por nenhum trono de alabastro dos Cárpatos. Pois nesta terra, sou único.\""
                }));

        list.add(new ChapterSpec(26, "XXVI", "Ato III: O Fim",
                "A Alvorada se Aproxima",
                "As últimas anotações do cronista Asminn antes do nascer do sol.",
                new String[]{
                        "§8Nota Final do Cronista Lorde Asminn — Alvorada de 25 de Outubro.§0 Minha mão dói de tanto escrever, mas o vigésimo sexto volume destas crônicas chega ao fim exatamente quando o céu a leste começa a clarear.",
                        "Observo §4Conde Jodevil§0 levantar-se da poltrona. Uma réstia de luz matinal acaba de atravessar a fresta da janela, tocando a ponta de sua bota.",
                        "Imediatamente ouve-se um leve §o'tsssss'§0 e um fiozinho de fumaça sobe do couro. \"Por todos os ancestrais!\", exclama §4Jodevil§0 puxando o pé para a sombra. \"Já está começando a contagem do minuto!\"",
                        "Com a dignidade apressada que lhe é característica, o Conde fecha as cortinas pesadas, ajeita a capa e dirige-se ao seu leito real enquanto o dia desperta lá fora.",
                        "Deixo estes vinte e sete volumes guardados na arca sagrada ao lado de seu repouso, para que todo viajante conheça a verdade sobre o nosso Príncipe da Noite."
                }));

        list.add(new ChapterSpec(27, "XXVII", "Ato III: O Fim",
                "Epílogo: O Imortal",
                "O juramento eterno de Conde Jodevil que encerra os 27 volumes.",
                new String[]{
                        "\"Aos que abrirem este último tomo das minhas crônicas\", escreveu o próprio §4Conde Jodevil§0 com tinta carmesim na página final, \"deixo minha bênção noturna.\"",
                        "\"Se me virdes caminhando sob a lua cheia, saudai-me como o Soberano das Sombras. Se me virdes correndo debaixo das árvores ao meio-dia fugindo do sol, fingi que não vistes a fumaça.\"",
                        "\"Que §6ArzanLion§0 guarde suas receitas longe da cozinha, que §6Jacinto§0 cuide bem dos cofres sem roubar meu martelo, e que a noite seja sempre generosa com os nossos sonhos.\"",
                        "\"Pois enquanto houver estrelas sobre estas terras e amigos para rir das nossas desventuras, o sangue de §4Conde Jodevil§0 jamais deixará de pulsar.\"",
                        "§4§l— FIM DO VIGÉSIMO SÉTIMO E ÚLTIMO VOLUME DAS CRÔNICAS DO SANGUE DUVIDOSO —"
                }));

        return list;
    }
}
