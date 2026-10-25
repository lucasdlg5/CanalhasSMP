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
 * ARCO 2: "Autos da Expurgação Noturna" (27 Volumes: Tomos XXVIII a LIV).
 * Inspirado na estética e nos temas de "O Vampiro Lestat" (Anne Rice), escrito sob a perspectiva
 * do implacável "Inquisidor Valerius", que desperta no pó calcário da cripta inferior e sobe
 * nível a nível pelo Castelo sobre o Mar para expurgar o Conde Jodevil, culminando na
 * revelação visceral diante do espelho à meia-noite no Livro 27.
 */
public final class JodevilFutureLoreLibrary {

    private static final int MAX_CHARS_PER_LINE = 19;
    private static final int MAX_LINES_PER_PAGE = 12;

    private JodevilFutureLoreLibrary() {}

    public record FutureChapterSpec(
            int arcNumber,       // 1 a 27 (dentro do Arco 2)
            int globalNumber,    // 28 a 54 (na coleção geral do Baú Duplo)
            String roman,        // I a XXVII
            String globalRoman,  // XXVIII a LIV
            String actTitle,
            String chapterTitle,
            String subtitle,
            String[] paragraphs
    ) {}

    /**
     * Gera todos os 27 livros do Arco 2 para compor os slots 27 a 53 do Baú Duplo.
     */
    public static List<ItemStack> createAllArc2ChapterBooks() {
        List<FutureChapterSpec> specs = new ArrayList<>(27);
        addAct1Chapters(specs); // Livros 1 a 9 (Ato I: O Cerco Solitário)
        addAct2Chapters(specs); // Livros 10 a 18 (Ato II: A Sombra no Corredor)
        addAct3Chapters(specs); // Livros 19 a 27 (Ato III: A Hora do Julgamento)

        List<ItemStack> books = new ArrayList<>(specs.size());
        for (FutureChapterSpec spec : specs) {
            books.add(createFutureChapterBook(spec));
        }
        return books;
    }

    public static ItemStack createFutureChapterBook(FutureChapterSpec spec) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();

        String shortTitle = "Autos " + spec.roman() + " - " + spec.chapterTitle();
        if (shortTitle.length() > 32) {
            shortTitle = shortTitle.substring(0, 32);
        }
        nbt.putString("title", shortTitle);
        nbt.putString("author", "Inquisidor Valerius");
        nbt.putInt("generation", 0);
        nbt.putBoolean("resolved", true);

        // Aparência visual do item "Tome of Vampiric Knowledge" (death_knights:blood_spell_book) via Transmog
        NbtCompound transmogTag = new NbtCompound();
        transmogTag.putString("id", "death_knights:blood_spell_book");
        transmogTag.putByte("Count", (byte) 1);
        nbt.put("transmog:transmogItem", transmogTag);

        // Nome e Lore no Tooltip do item (100% blindado contra spoilers)
        NbtCompound display = nbt.getCompound("display");
        display.putString("Name", Text.Serializer.toJson(
                Text.literal("§4§lTome of Vampiric Knowledge — Cap. " + spec.roman() + ": " + spec.chapterTitle())));

        NbtList tooltipLore = new NbtList();
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§5" + spec.actTitle()).formatted(Formatting.DARK_PURPLE))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7\"" + spec.subtitle() + "\"").formatted(Formatting.GRAY, Formatting.ITALIC))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(""))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§4🩸 Autos da Expurgação Noturna (Tomo " + spec.arcNumber() + " de 27 | Vol. " + spec.globalRoman() + ")").formatted(Formatting.DARK_RED))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§8Relatório oficial de caça e extermínio no Castelo sobre o Mar.").formatted(Formatting.DARK_GRAY))));
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
    private static List<String> paginateChapter(FutureChapterSpec spec) {
        List<String> pages = new ArrayList<>();

        // Página 1: Folha de Rosto do Capítulo (máximo 11-12 linhas garantidas)
        List<String> coverLines = new ArrayList<>();
        coverLines.add("§4§lAUTOS • CAP. " + spec.roman() + "§r");
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
                coverLines.add("§5" + actLine + "§r");
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
            if (currentPageLines.isEmpty() && line.isEmpty()) {
                continue;
            }
            currentPageLines.add(line);
            if (currentPageLines.size() >= MAX_LINES_PER_PAGE) {
                pages.add("§0" + String.join("\n", currentPageLines));
                currentPageLines.clear();
            }
        }
        if (!currentPageLines.isEmpty()) {
            pages.add("§0" + String.join("\n", currentPageLines));
        }

        return pages;
    }

    private static List<String> wrapPlainText(String text, int maxVisibleChars) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();
        int currentVisible = 0;

        for (String word : words) {
            int wordVisible = visibleLength(word);
            if (currentVisible == 0) {
                if (wordVisible <= maxVisibleChars) {
                    current.append(word);
                    currentVisible = wordVisible;
                } else {
                    int splitAt = Math.min(word.length(), maxVisibleChars - 1);
                    lines.add(word.substring(0, splitAt) + "-");
                    String rest = word.substring(splitAt);
                    current.append(rest);
                    currentVisible = visibleLength(rest);
                }
            } else if (currentVisible + 1 + wordVisible <= maxVisibleChars) {
                current.append(" ").append(word);
                currentVisible += 1 + wordVisible;
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
                currentVisible = wordVisible;
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static int visibleLength(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '§' && i + 1 < s.length()) {
                i++;
                continue;
            }
            count++;
        }
        return count;
    }

    // =========================================================================
    // ATO I — LIVROS 1 A 9: O CERCO SOLITÁRIO
    // =========================================================================
    private static void addAct1Chapters(List<FutureChapterSpec> list) {
        // LIVRO 1 (Volume XXVIII)
        list.add(new FutureChapterSpec(1, 28, "I", "XXVIII",
                "Ato I: O Cerco Solitário",
                "Pó Branco e Carmesim",
                "Onde o Inquisidor Valerius desperta na cripta inferior e inicia os autos de caça.",
                new String[]{
                        "Despertei sobre a laje fria de pedra, coberto pelo pó branco das catacumbas, enquanto um chiado agudo — §o'tsssss'§0 — subia da ponta da minha bota...",
                        "Puxei o pé imediatamente da réstia de sol que descia pela grade alta da cripta de entrada, sacudindo a poeira calcária dos meus punhos de renda e do meu casaco de veludo negro, como uma estátua de mármore que acabasse de romper o silêncio da terra.",
                        "Meu nome é §9Valerius§0, Inquisidor da Ordem Noturna. Infiltrei-me pelo portão subterrâneo deste Castelo sobre o Mar na calada da noite para cumprir uma sentença sagrada: subir até a Torre Real, executar o famigerado §4Conde Jodevil§0 e libertar a antiga corte que ele mantém cativa.",
                        "O demônio certamente lançou algum vapor narcótico pelas frestas da cripta quando forcei a entrada, pois acordei com uma sede estética avassaladora por música e luz de velas — misturada a um gosto hediondo de vinagre barato, alho murcho e sopa de beterraba no céu da boca!",
                        "Ao olhar para a laje ao meu lado, vi que as pontas dos meus dedos estavam manchadas de tinta carmesim ainda fresca, junto a um tinteiro aberto e um ridículo chapéu de palha desfiado.",
                        "Verifiquei minha espada e minha estaca de freixo: o tirano escondeu a chave da Torre Real nos andares superiores, e vasculharei cada salão desta fortaleza até arrancar-lhe o coração!"
                }));

        // LIVRO 2 (Volume XXIX)
        list.add(new FutureChapterSpec(2, 29, "II", "XXIX",
                "Ato I: O Cerco Solitário",
                "O Banquete Interrompido",
                "A subida ao salão principal e a descoberta de rastros ainda mornos.",
                new String[]{
                        "Assim que o sol mergulhou no oceano, deixei a cripta de entrada levando a espada em punho e aquele chapéu de palha sob o braço, caso precisasse de um disfarce nos pátios.",
                        "Subi os degraus até o primeiro pavimento e invadi o grande salão de banquetes: os candelabros de prata ardiam em silêncio, e a mesa fora posta com taças de cristal para doze convivas, mas apenas a cabeceira estava ocupada.",
                        "Toquei a sopeira de prata: ainda estava morna! Um caldo espesso e cor-de-rosa, cheirando fortemente a beterraba cozida e vinho azedo, escorria pela borda como se o Conde tivesse se levantado segundos antes de eu abrir a porta.",
                        "De repente, ao contemplar as onze cadeiras vazias sob a luz dourada das velas, uma fome de beleza e de companhia humana atravessou meu peito com tanta violência que precisei apoiar-me no espaldar de carvalho.",
                        "Quase pude ouvir o tilintar dos cristais e o calor das gargalhadas daquela corte desaparecida — sentindo por aqueles desconhecidos um amor tão febril e apaixonado como se eu próprio tivesse reinado entre eles!",
                        "Um ruído seco ecoou atrás da tapeçaria rubra do fundo; saltei sobre a mesa e rasguei o veludo com a lâmina, mas encontrei apenas uma escada secreta trancada por dentro e um morcego gordo fugindo pelo teto."
                }));

        // LIVRO 3 (Volume XXX)
        list.add(new FutureChapterSpec(3, 30, "III", "XXX",
                "Ato I: O Cerco Solitário",
                "O Poço Envenenado",
                "Onde o Inquisidor descobre a praga solar que o impede de caçar ao meio-dia.",
                new String[]{
                        "Na manhã seguinte, decidi quebrar a cautela e atravessar o pátio interno sob o sol pleno do meio-dia para procurar a escadaria externa da torre, hora em que o covarde §4Jodevil§0 estaria adormecido.",
                        "Mal dei três passos fora da sombra das colunas, porém, o sol caiu sobre mim como chumbo derretido! Meu veludo começou a fumegar com um cheiro humilhante de toucinho na brasa e senti minha vitalidade escorrer pelas pedras.",
                        "Pior do que a queimadura lenta foi o terror sobrenatural que gelou minha espinha: o pressentimento da §4Centésima Centelha§0 — a certeza mórbida de que, a cada minuto sob o astro-rei, os dados da morte rolavam uma chance oculta de explodir meu corpo inteiro em cinzas instantâneas!",
                        "Atirei-me de volta para debaixo da arcada num rolamento desesperado, apagando com as luvas a fumaça que subia da ponta da minha bota direita.",
                        "'Maldita seja a bruxaria de §4Jodevil§0!', rosnei, chutando o balde de cobre ao lado do poço. 'O patife envenenou a água da cisterna com alguma maldição heliofóbica quando bebi dela ontem de madrugada!'",
                        "Compreendi a regra cruel deste cerco: enquanto eu não matasse o Conde para quebrar o feitiço da água, só poderia caminhar sob o manto protetor da meia-noite."
                }));

        // LIVRO 4 (Volume XXXI)
        list.add(new FutureChapterSpec(4, 31, "IV", "XXXI",
                "Ato I: O Cerco Solitário",
                "O Orgulho e a Palha",
                "No qual Valerius tenta queimar o chapéu de palha e sofre um surto de vaidade.",
                new String[]{
                        "Recolhido à sala da guarda após o incidente solar, olhei com desprezo estético para aquele chapéu de palha surrado que trouxera da cripta: um objeto camponês que ofendia a elegância do meu casaco de veludo e dos meus punhos de renda.",
                        "Aproximei a aba desfiada das chamas da lareira para destruí-lo — mas, no exato segundo em que o calor tocou a palha, minha própria mão esquerda agarrou meu pulso direito num tranco involuntário!",
                        "Perplexo com a rebeldia dos meus próprios nervos, virei o chapéu contra o candelabro e li, pirografada no couro interno, uma dedicatória sarcástica: §o'Sem reembolso — Para o nosso Príncipe do Meio-Dia.'§0",
                        "Ao ler aquelas palavras, uma indignação furiosa e inexplicável subiu-me à garganta: comecei a caminhar diante da lareira esbravejando contra 'trinta e sete cláusulas contratuais' e jurando que um queijo curado jamais pagaria tamanha afronta!",
                        "Estaquei no meio do tapete, levando a mão à testa suada: por todos os santos da Ordem, que feitiço mental fazia um austero Inquisidor agir como um nobre vaidoso e lesado por um charlatão?!",
                        "Antes que pudesse refletir, um som sublime e inesperado desceu pelas chaminés de pedra: o choro longínquo e apaixonado de um violino tocando nas galerias subterrâneas!"
                }));

        // LIVRO 5 (Volume XXXII)
        list.add(new FutureChapterSpec(5, 32, "V", "XXXII",
                "Ato I: O Cerco Solitário",
                "O Violino na Cripta",
                "Onde uma sonata melancólica termina num guincho desafinado junto ao altar antigo.",
                new String[]{
                        "Guiado pelas notas daquele violino noturno — uma melodia tão bela e dilacerante que parecia chorar a morte de um jovem músico em Paris —, desci na quinta noite às galerias profundas sob a capela.",
                        "Eu avançava de espada em punho, encantado pela virtuosidade do arco, quando de repente a sonata sublime foi interrompida por um guincho horrendo de gato pisado (§o'CRÉÉÉC!'§0) e o estalo seco de uma corda arrebentada!",
                        "Ouvi um palavrão abafado ecoar na abóbada e corri até o altar central de basalto, mas encontrei a câmara vazia: sobre um atril de ferro, um belo violino de madeira escura ainda vibrava com uma corda solta balançando.",
                        "Ao lado do instrumento, protegido por fios de armadilha que cortei com a adaga, repousava o lendário códice da §4Rainha dos Condenados§0 — a Mãe de Todos os Imortais.",
                        "As margens do livro sagrado estavam cheias de notas em tinta carmesim escritas com a letra de um rebelde vaidoso: §o'A Rainha tinha sangue milenar; já ArzanLion me deu tinto azedo com beterraba e exigiu que eu fizesse pose de lorde!'§0",
                        "Mais assustador ainda: quando virei a folha, minha própria boca recitou de cor o parágrafo inteiro sobre a solidão dos Antigos, enquanto uma sombra de capa dobrava a escadaria rumo à sala das engrenagens!"
                }));

        // LIVRO 6 (Volume XXXIII)
        list.add(new FutureChapterSpec(6, 33, "VI", "XXXIII",
                "Ato I: O Cerco Solitário",
                "O Príncipe Rebelde",
                "A perseguição até o gabinete de Lorde Asminn e o protesto escrito em tinta fresca.",
                new String[]{
                        "Subi a escadaria a toda brida e escancarei a porta da sala de arquivos e máquinas no segundo andar, onde uma placa de bronze anunciava: §o'Gabinete de Lorde Asminn — Cronista e Engenheiro do Reino'§0.",
                        "Não havia ninguém à vista, mas sobre a bancada de latão uma vela acabara de ser soprada, soltando um fio reto de fumaça perfumada ao lado de uma xícara de chá ainda morna!",
                        "Sobre o cavalete repousava uma cópia de rascunho das antigas crônicas do feudo, e nas margens do pergaminho brilhava uma anotação em tinta carmesim tão recente que ainda escorria pelo papel:",
                        "§o'Meu amado cronista Asminn escreveu estas páginas com pena de ouro, mas cometeu calúnias contra o meu charme! Eu sou o Príncipe Rebelde e Indomável desta Fortaleza, e não um desastrado que tropeça na própria capa!'§0",
                        "Vasculhei debaixo das mesas e dentro dos armários de engrenagens: o cômodo não tinha janelas nem saídas secretas, e o corredor lá fora era uma linha reta pela qual eu acabara de vir!",
                        "Como podia o vaidoso §4Jodevil§0 estar ali segundos antes, defendendo sua fama literária e elogiando §9Asminn§0, e desaparecer no ar deixando em meu próprio peito uma vontade absurda de chorar pelo cronista ausente?!"
                }));

        // LIVRO 7 (Volume XXXIV)
        list.add(new FutureChapterSpec(7, 34, "VII", "XXXIV",
                "Ato I: O Cerco Solitário",
                "A Reserva Carmesim",
                "Onde Valerius investiga a adega de vinhos do Conde e sofre um colapso gástrico.",
                new String[]{
                        "Se o monstro se julgava um 'Príncipe Rebelde' de alta linhagem, decidi atingir sua vaidade destruindo a adega real na sétima noite, onde ele certamente guardava safras de sangue nobre em cálices de rubi.",
                        "Arrombei a grade da despensa e encontrei fileiras de garrafas bojudas lacradas com cera cor-de-rosa, exibindo rótulos pomposos escritos à mão: §o'Safra Real da Meia-Noite — Proibido para Mortais Comuns'§0.",
                        "Puxei a rolha de uma das garrafas com a ponta da adaga e, preparando-me para identificar o veneno necromântico, levei o gargalo aos lábios.",
                        "Cuspi o líquido no mesmo instante sobre as lajes, dobrando-me em dois com uma crise de tosse: era a mesmíssima gororoba hedionda de vinho tinto barato, caldo de beterraba fria, cebola e alho velho cujo gosto eu sentira na boca ao despertar na cripta!",
                        "'Que espécie de Príncipe das Trevas engarrafa sopa de beterraba azeda com rótulo de safra real?!', esbravejei, limpando a língua nos punhos de renda enquanto a azia me queimava o esôfago.",
                        "Pior: ao contar os cascos vazios alinhados junto à parede, havia exatamente seis garrafas bebidas até a última gota — uma para cada noite desde que iniciei esta caçada!"
                }));

        // LIVRO 8 (Volume XXXV)
        list.add(new FutureChapterSpec(8, 35, "VIII", "XXXV",
                "Ato I: O Cerco Solitário",
                "Pegadas na Cinza",
                "Uma caçada em tempo real na galeria de mármore que fecha um círculo impossível.",
                new String[]{
                        "Decidido a emboscar o bebedor de beterraba em flagrante na oitava noite, espalhei uma camada fina de cinzas brancas da lareira por toda a galeria de colunas do segundo andar e apaguei os candelabros.",
                        "À meia-noite em ponto, ouvi o estalar ritmado de saltos de bota pisando na cinza logo à frente, caminhando com a arrogância cadenciada de um marquês antigo!",
                        "Abri a lanterna furta-fogo e corri de espada em riste seguindo as pegadas frescas no pó: o invasor calçava botas número quarenta e dois, com o salto esquerdo gasto e um chamuscado de sol na ponta do pé direito.",
                        "'Acabou o teu teatro, §4Jodevil§0!', bradei, acelerando a corrida à medida que os passos do vulto contornavam o imenso pilar central de mármore negro.",
                        "Saltei ao redor da coluna desferindo um golpe horizontal para decepar-lhe a cabeça — mas minha lâmina cortou apenas o ar vazio!",
                        "Quando baixei o facho da lanterna para o chão, senti os cabelos da nuca arrepiarem-se: o rastro solitário de pegadas dava a volta completa na coluna e terminava exatamente debaixo das minhas próprias botas!"
                }));

        // LIVRO 9 (Volume XXXVI)
        list.add(new FutureChapterSpec(9, 36, "IX", "XXXVI",
                "Ato I: O Cerco Solitário",
                "Sonata na Tempestade",
                "Onde o violino desafinado na capela atrai o Inquisidor para os terraços externos.",
                new String[]{
                        "Recusando-me a aceitar aquela ilusão diabólica das pegadas, corri até a capela gótica na nona noite, justamente quando um temporal marítimo açoitava os vitrais sobre o abismo.",
                        "No alto do coro dos órgãos, invisível na penumbra, o violino voltou a tocar! As primeiras notas eram de uma paixão sobrenatural digna dos concertos subterrâneos dos imortais, chorando os nomes da corte perdida.",
                        "'Mostra a tua face, músico do inferno!', gritei subindo os degraus do coro de dois em dois — mas, no clímax do solo, o arco escorregou num guincho medonho de porta enferrujada e uma voz sussurrou no vento: §o'Maldito breu, errei o sustenido!'§0",
                        "Quando cheguei ao topo do coro, o violino não estava mais lá; encontrei apenas uma partitura manchada de beterraba intitulada §o'Elegia para Asminn e os Amigos que o Tempo Levou'§0.",
                        "Através da janela quebrada do coro, vi uma porta lateral batendo ao vento, dando acesso ao terceiro pavimento: os terraços abertos, os telhados e as galerias externas da fortaleza.",
                        "Compreendi que o Conde usava sua música trágica (e seus erros de nota) para zombar de mim; a partir da décima noite, eu o caçaria sob a chuva e o luar dos terraços!"
                }));
    }

    // =========================================================================
    // ATO II — LIVROS 10 A 18: A SOMBRA NO CORREDOR
    // =========================================================================
    private static void addAct2Chapters(List<FutureChapterSpec> list) {
        // LIVRO 10 (Volume XXXVII)
        list.add(new FutureChapterSpec(10, 37, "X", "XXXVII",
                "Ato II: A Sombra no Corredor",
                "O Teatro das Roseiras",
                "No qual o Inquisidor descobre o palco de veludo no terraço e cai do parapeito.",
                new String[]{
                        "Na décima noite, alcancei o terraço do jardim de inverno e deparei-me com um cenário digno do lendário §oTeatro dos Vampiros§0: cortinas de veludo rubro, máscaras pintadas e três poltronas vazias com os nomes de §dLady Kikkawa§0, §dPipira§0 e §dDama Luwuana§0!",
                        "Pelo visto, na falta de plateia viva, o vaidoso §4Conde Jodevil§0 transformara a varanda das damas em seu palco particular para encenar tragédias à meia-noite!",
                        "Nesse instante, ouvi risos femininos suaves ecoando do jardim logo abaixo da balaustrada; julgando que o monstro mantinha as damas prisioneiras no pátio, subi num salto teatral sobre a mureta do parapeito.",
                        "Tomado por um súbito fervor dramático que inflamou meu peito, abri a capa contra o luar como um ator no ato final e bradei para a noite: §o'Contemplai o anjo vingador! Eu sou a tormenta!'§0",
                        "Mal terminei a sílaba, a pedra frouxa do parapeito girou sob minha bota e eu despenquei de cabeça no escuro feito uma bigorna de chumbo!",
                        "Aterrissei de cara no meio do mesmíssimo canteiro de roseiras bravas lá embaixo, saindo de lá mancando, com espinhos até nas sobrancelhas e convicto de que §4Jodevil§0 ensaboara o parapeito para humilhar a Inquisição!"
                }));

        // LIVRO 11 (Volume XXXVIII)
        list.add(new FutureChapterSpec(11, 38, "XI", "XXXVIII",
                "Ato II: A Sombra no Corredor",
                "A Torre do Alquimista",
                "Onde Valerius invade a fornalha de ArzanLion e derruba o laboratório num espirro.",
                new String[]{
                        "Arrancando espinhos de roseira do colarinho de renda, desci na décima primeira noite à câmara alquímica de §6ArzanLion§0 — um antro de pedra e fogo que lembrava a antiga torre onde o velho Magnus forjou seu herdeiro imortal nas chamas.",
                        "Junto à entrada, sobre um coxim de honra, repousava a coleira da brava cadelinha §eVitória§0; fiz-lhe uma mesura respeitosa e avancei de espada erguida até a pira central onde o caldeirão ainda fumegava.",
                        "Certo de que §4Jodevil§0 se escondia atrás das estantes, puxei da bancada o grimório em aramaico cujas runas haviam sido dissolvidas décadas atrás pela baba do dorminhoco §5AsterZZZz§0.",
                        "Ergui o pergaminho para ler a contraconjuração em voz alta, mas uma nuvem traiçoeira de pimenta-rosa seca e poeira secular subiu do papiro direto para o meu nariz!",
                        "Prendi o ar, arregalei os olhos, mas o espasmo foi mais forte que minha fé: §o'Sanguis Noctis... ATCHIM!'§0 — o espirro trovejante fez meu cotovelo derrubar a estante inteira de frascos sobre a minha cabeça!",
                        "Saí rolando no meio de uma explosão de fumaça cor-de-rosa e cheiro de alho torrado, jurando que o alquimista armara uma bomba nasal para proteger o vampiro!"
                }));

        // LIVRO 12 (Volume XXXIX)
        list.add(new FutureChapterSpec(12, 39, "XII", "XXXIX",
                "Ato II: A Sombra no Corredor",
                "A Relíquia da Inquisição",
                "O teste da colher de prata de Azarasquel e Reznozada e uma urticária suspeita.",
                new String[]{
                        "Tossindo fumaça cor-de-rosa, subi na décima segunda noite à galeria de armas onde os antigos inquisidores §2Azarasquel§0 e §2Reznozada§0 guardavam seus troféus sob a estátua dourada do leão §6Javidizineo§0.",
                        "Quebrei o lacre do mostruário de vidro e apanhei a relíquia que ambos usavam para testar a nobreza sombria do Conde: uma colher de sobremesa de prata torta com a inscrição §o'Terror dos Nosferatu'§0.",
                        "'Magnífico!', sorri, girando a colher entre os dedos. 'Quero ver o arrogante Príncipe Rebelde manter a pose quando eu encostar esta prata consagrada em seu pescoço!'",
                        "Enquanto estudava o mapa dos corredores, porém, passei distraído o cabo da colher de prata na minha própria nuca para aliviar o calor do colarinho de veludo.",
                        "Instantaneamente, minha pele empolou numa coceira ardente e enlouquecedora que me fez largar a espada e sapatear pelo salão enquanto me coçava com as duas mãos!",
                        "Atirei a colher no tapete, furioso: 'Maldito seja o desleixo deste castelo! §4Jodevil§0 mergulhou a prata em urtiga marinha para provocar dermatite alérgica nos agentes da Ordem!'"
                }));

        // LIVRO 13 (Volume XL)
        list.add(new FutureChapterSpec(13, 40, "XIII", "XL",
                "Ato II: A Sombra no Corredor",
                "Fogo no Telhado",
                "No qual Valerius encontra o livro de apostas de Corvo e foge da aurora fumegante.",
                new String[]{
                        "Na décima terceira noite, escalei a chaminé externa até o telhado da antiga taverna, buscando o ninho de vigia onde o astuto §8Corvo§0 costumava registrar os segredos da fortaleza.",
                        "Dentro de um cofre de chumbo sob as telhas, achei o livro de contabilidade de §8Corvo§0: §o'Grande Bolão da Alvorada — Quantos minutos o Conde chia ao sol antes da Centésima Centelha?'§0.",
                        "Sentado na cumeeira, soltei uma gargalhada de escárnio lendo as moedas apostadas por §2Reznozada§0, §6ArzanLion§0 e §dLuwuana§0: 'Hahaha! E ele ainda se chama de Príncipe Indomável nas margens dos livros! Não passa de um toucinho ambulante!'",
                        "Distraído pelo meu próprio riso triunfal, não notei que as nuvens a leste haviam se aberto e o primeiro dardo dourado do sol nascente acabara de cruzar o oceano.",
                        "A luz atingiu meu tornozelo esquerdo: no mesmo segundo, um chiado alto de gordura na chapa (§o'TSSSSS!'§0) subiu da minha bota, acompanhado de fumaça preta e do pavor gelado da §4Centésima Centelha§0!",
                        "Pulei do telhado direto para dentro de um tonel de água de chuva no beco escuro, convencido de que §4Jodevil§0 posicionara espelhos ustórios nas torres para fritar minhas botas à distância!"
                }));

        // LIVRO 14 (Volume XLI)
        list.add(new FutureChapterSpec(14, 41, "XIV", "XLI",
                "Ato II: A Sombra no Corredor",
                "O Guardião do Martelo",
                "O confronto humilhante com o imortal Jacinto nos corredores da tesouraria.",
                new String[]{
                        "Com a bota ainda cheirando a couro chamuscado, invadi na décima quarta noite o corredor blindado da tesouraria e dei de frente com o único ser ativo do castelo: o guardião imortal §6Jacinto§0.",
                        "Preso ao cinturão daquele funcionário incansável brilhava justamente a arma capaz de derrubar qualquer porta da Torre Real: o divino §bMjölnir que não volta§0!",
                        "Ergui o queixo com autoridade imperial: 'Servo §6Jacinto§0! Em nome do Alto Conselho, ordeno que entregues esse martelo do trovão imediatamente para que eu esmague o teu patrão §4Jodevil§0!'",
                        "Como §6Jacinto§0 continuou varrendo o chão sem me dar ouvidos, chutei um braseiro aceso contra as botas dele para intimidá-lo — mas as chamas apagaram-se sozinhas e ele disse: §o'Patrão, tacar fogo não ajuda em nada... pede com educação e carinho que quem sabe o senhor dá sorte.'§0",
                        "Inspirei fundo para soltar um grito de guerra inquisitorial, mas minha boca desobedeceu ao meu cérebro e, juntando as mãos no peito, ouvi minha própria voz choramingar num falsete patético: §o'Ah, vai, Jacinto, por favorzinho, devolve o meu Mjölnir, foi sem querer que eu te dei!'§0",
                        "Cobri o rosto com a capa em pânico absoluto diante daquela humilhação involuntária e fugi corredor acima enquanto §6Jacinto§0 me incentivava a continuar tentando com jeitinho!"
                }));

        // LIVRO 15 (Volume XLII)
        list.add(new FutureChapterSpec(15, 42, "XV", "XLII",
                "Ato II: A Sombra no Corredor",
                "O Santuário dos Guardados",
                "Onde Valerius descobre a cripta das estátuas da corte e o aviso de Senhor Cummins.",
                new String[]{
                        "Atordoado pelo vexame diante de §6Jacinto§0, subi na décima quinta noite à galeria circular do observatório e descobri uma câmara iluminada por círios perpétuos que me tirou o fôlego.",
                        "Tal qual o santuário ancestral d'§oAqueles Que Devem Ser Guardados§0 nas crônicas dos primeiros imortais, o salão exibia bustos de mármore branco e retratos a óleo perfeitamente conservados de §9Lorde Asminn§0, §6ArzanLion§0, §5AsterZZZz§0, §dLady Kikkawa§0, §dDama Luwuana§0, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0!",
                        "Diante dos retratos havia flores frescas, o violino de madeira escura com a corda remendada e uma placa de ouro: §o'Aqui repousa a memória daqueles que são a minha única luz na escuridão.'§0",
                        "Sentado ao pé do busto de §9Asminn§0, o gato místico §bSenhor Cummins§0 olhava para mim enquanto o velho morcego §8Juninho§0 dormia pendurado na moldura.",
                        "Silenciosamente, §bSenhor Cummins§0 caminhou até mim e empurrou com a pata um grande caco de espelho prateado até encostá-lo na minha bota, miando como quem implora para que eu olhe o reflexo.",
                        "'Não preciso de espelhos para saber quem sou, felino!', murmurei com a garganta embargada pela beleza daquele santuário, chutando o vidro para a sombra antes de me retirar."
                }));

        // LIVRO 16 (Volume XLIII)
        list.add(new FutureChapterSpec(16, 43, "XVI", "XLIII",
                "Ato II: A Sombra no Corredor",
                "O Sonambulismo Macabro",
                "O terror de despertar em pé escrevendo cartas apaixonadas para a corte.",
                new String[]{
                        "Na décima sexta noite, o mistério deixou de ser uma caçada e transformou-se num pesadelo visceral: não acordei deitado, mas em pé, no centro do salão de música, com uma pena gotejante entre os dedos!",
                        "Sobre o cravo aberto à minha frente, havia quatro cartas recém-escritas em tinta carmesim dedicadas a §9Asminn§0 e aos amigos do santuário, jurando que 'este Príncipe Rebelde prefere o esquecimento a viver mais um século sem o som da vossa voz'.",
                        "Aproximei meu diário de Inquisidor daquelas folhas e senti um calafrio percorrer cada vértebra da minha coluna: a letra rebuscada, os floreios nos 'J's e até os borrões de punho de renda eram idênticos aos meus!",
                        "Arremessei a pena longe como se queimasse meus dedos e recuei até derrubar o candelabro no tapete.",
                        "'Possessão vampírica!', bradei, ofegante, olhando para minhas próprias mãos manchadas de vermelho. 'O espírito de §4Conde Jodevil§0 invade meu corpo enquanto durmo e usa meu próprio braço para escrever suas elegias e tocar aquele violino desafinado!'",
                        "Tudo fazia sentido agora: os apagões, o gosto de beterraba na boca e até a frase humilhante dita a §6Jacinto§0 eram obra do espírito do Conde controlando minha língua!"
                }));

        // LIVRO 17 (Volume XLIV)
        list.add(new FutureChapterSpec(17, 44, "XVII", "XLIV",
                "Ato II: A Sombra no Corredor",
                "O Interrogatório do Gato",
                "Onde Valerius coloca Senhor Cummins e Juninho contra a parede na biblioteca.",
                new String[]{
                        "Se o espírito de §4Jodevil§0 usava meu corpo como hospedeiro durante o dia, havia duas testemunhas oculares do crime: na décima sétima noite, tranquei a biblioteca e coloquei §bSenhor Cummins§0 e o morcego §8Juninho§0 sentados diante da escrivaninha para um interrogatório inquisitorial.",
                        "'Falem, criaturas da meia-noite!', ordenei, batendo o punho de renda na mesa. 'Vocês conhecem §4Conde Jodevil§0 desde a noite da explosão cor-de-rosa! Por que então não saem do meu pé um único segundo?!'",
                        "Apontei o dedo para o gato: 'Hoje ao crepúsculo, quando acordei no divã, tu havias trazido no dente um robe de seda carmesim bordado com o brasão do Conde para me cobrir! E tu, §8Juninho§0, deixaste um frasco de §fBat Leite§0 na minha cabeceira!'",
                        "Em vez de demonstrar remorso pelo complô, §8Juninho§0 deu um voo curto até o meu chapéu de palha e pendurou-se na aba para cochilar, enquanto §bSenhor Cummins§0 saltava para o meu colo ronronando como um motor.",
                        "'Confissão tácita!', sentenciei, embora minhas próprias mãos já estivessem fazendo carinho atrás da orelha do gato por puro hábito involuntário. 'Eles sentem a aura do Conde possuindo minha carne!'",
                        "Decidi que na noite seguinte nenhum espírito sairia ou entraria no meu corpo: eu transformaria meu quarto numa fortaleza de prata."
                }));

        // LIVRO 18 (Volume XLV)
        list.add(new FutureChapterSpec(18, 45, "XVIII", "XLV",
                "Ato II: A Sombra no Corredor",
                "A Jaula de Ferro",
                "No qual o Inquisidor se amarra com fios de prata e acorda com um recado na testa.",
                new String[]{
                        "Na décima oitava noite, montei o cerco definitivo contra o sonambulismo: tranquei a porta da antecâmara superior com três voltas, selei os batentes com cera e engoli a chave de ferro com um gole de água.",
                        "Em seguida, amarrei meus próprios pulsos e tornozelos aos braços da poltrona usando fios de prata trançada do arsenal de §2Azarasquel§0: se o espírito de §4Jodevil§0 tentasse levantar meu corpo para passear pelo castelo, a prata o queimaria na hora!",
                        "Adormeci exausto, sorrindo com a certeza de que quebrara as pernas do fantasma.",
                        "Quando abri os olhos ao cair da noite seguinte, porém, meus tornozelos ardiam numa coceira vermelha insuportável: os nós de prata haviam sido desfeitos com destreza cirúrgica, e a chave engolida repousava limpa ao lado do tinteiro!",
                        "Corri até a bacia de cobre para jogar água no rosto e dei um salto para trás ao encarar a superfície líquida:",
                        "Com a minha própria tinta carmesim, alguém escrevera em letras garrafais espelhadas bem no meio da minha testa: §4§lPRÍNCIPE COVARDE§0!"
                }));
    }

    // =========================================================================
    // ATO III — LIVROS 19 A 27: A HORA DO JULGAMENTO
    // =========================================================================
    private static void addAct3Chapters(List<FutureChapterSpec> list) {
        // LIVRO 19 (Volume XLVI)
        list.add(new FutureChapterSpec(19, 46, "XIX", "XLVI",
                "Ato III: A Hora do Julgamento",
                "Duelo nas Sombras",
                "O manto rubro do caçador de lobos e o combate feroz na galeria espelhada.",
                new String[]{
                        "Esfregando a testa até arrancar a tinta, apanhei num cabide da galeria um pesado manto de veludo rubro forrado de peles — idêntico àquele que os antigos caçadores de lobos usavam nas montanhas nevadas antes de conhecerem a imortalidade — e saí para o corredor das muralhas leste.",
                        "Lá fora os trovões rugiam como uma alcateia faminta quando, no fim da longa galeria escura, os relâmpagos iluminaram finalmente o meu inimigo: alto, pálido, envolto num manto rubro idêntico ao meu e com um chapéu de palha torto sobre os olhos febris!",
                        "'Em guarda, §4Conde Jodevil§0!', rugi desembainhando a espada — e no mesmo lampejo o vulto sacou sua lâmina na exata postura real do antigo §4Eclipse de Sangue§0!",
                        "Avancei numa tempestade de aço, executando os passes lendários que defenderam as ameias ao lado de §2Azarasquel§0, §2Reznozada§0, §6Javidizineo§0 e §8Corvo§0, sentindo nos músculos o calor heroico daquela noite imortal.",
                        "Mas o demônio era o meu espelho vivo de batalha: aparava cada estocada no mesmo milésimo de segundo, avançava quando eu avançava e sorria com a mesma melancolia feroz!",
                        "Num bote desesperado, lancei todo o meu peso contra o peito do Conde — e uma explosão ensurdecedora de cristal partido cobriu-me de estilhaços: eu acabara de atravessar a imensa janela espelhada do fundo da galeria, duelando sozinho contra o meu próprio reflexo na tempestade!"
                }));

        // LIVRO 20 (Volume XLVII)
        list.add(new FutureChapterSpec(20, 47, "XX", "XLVII",
                "Ato III: A Hora do Julgamento",
                "A Fuga Impossível",
                "Onde Valerius tenta abandonar o castelo e esbarra na burocracia de Jacinto.",
                new String[]{
                        "Sacudindo os cacos de vidro do manto rubro, fui tomado por um pavor gelado na vigésima noite: os feitiços ópticos e o gás alucinógeno de §4Jodevil§0 estavam destruindo meu cérebro, e se eu permanecesse ali mais um dia, acabaria cravando a estaca no meu próprio peito!",
                        "'Que a Ordem envie um exército inteiro depois!', arfei, recolhendo meus manuscritos e descendo as escadarias em disparada até o pátio inferior para fugir pela ponte levadiça.",
                        "Ao chegar ao portão principal, contudo, encontrei as correntes de ferro travadas no alto por um mecanismo de engrenagens cujo ferrolho fora lacrado pelo lado de dentro!",
                        "E, sentado sobre um barril bem diante da alavanca, apoiando o queixo no cabo do reluzente §bMjölnir que não volta§0, estava o impassível guardião §6Jacinto§0.",
                        "'Baixa essa ponte imediatamente, §6Jacinto§0!', ordenei com a voz embargada pelo desespero. 'Este castelo é um manicômio arcano! Preciso sair daqui antes da alvorada!'",
                        "§6Jacinto§0 consultou sua prancheta imaginária com frieza burocrática de almoxarifado e respondeu: §o'Negativo, senhor. Ordem expressa assinada pelo dono do castelo: a ponte só baixa depois da meia-noite do dia vinte e sete. E não adianta fazer beicinho nem pedir por favorzinho hoje que o portão é da firma!'§0"
                }));

        // LIVRO 21 (Volume XLVIII)
        list.add(new FutureChapterSpec(21, 48, "XXI", "XLVIII",
                "Ato III: A Hora do Julgamento",
                "Emboscada Solar no Corredor",
                "Uma manobra tática atrás do vaso seco quando a aurora rasga o veludo.",
                new String[]{
                        "Prisioneiro no castelo até o vigésimo sétimo dia por culpa da teimosia de §6Jacinto§0, tentei encontrar uma rota alternativa pelas janelas altas da galeria sul na manhã seguinte.",
                        "Ao puxar o cordão de seda para espiar a falésia, porém, o varão enferrujado cedeu e a cortina inteira despencou no chão — abrindo caminho para uma cascata brutal de sol matinal que inundou o corredor!",
                        "A luz atingiu minha bota como o sopro de um dragão: o couro chiou alto (§o'tsssss!'§0), uma fumaça gordurosa de toucinho subiu pelo meu tornozelo e senti o pulso aterrador da §4Centésima Centelha§0 prestes a converter meu esqueleto em cinzas!",
                        "Perdendo toda a pose de caçador implacável, soltei um grito agudo e mergulhei de barriga no único palmo de sombra que restara no tapete: atrás de um pequeno vaso de barro com um arbusto seco!",
                        "Encolhido feito um caracol atrás daquele vaso minguado e segurando o chapéu de palha na frente do nariz como escudo balístico, fui empurrando o vaso milímetro a milímetro pelo corredor banhado de sol.",
                        "Foram quarenta minutos de rastejamento humilhante até alcançar a sombra da copa real; quando finalmente fechei a porta, abracei o vaso de arbusto seco jurando levá-lo comigo até a Torre Real como relíquia salvadora!"
                }));

        // LIVRO 22 (Volume XLIX)
        list.add(new FutureChapterSpec(22, 49, "XXII", "XLIX",
                "Ato III: A Hora do Julgamento",
                "O Alquimista Fantasma",
                "A descoberta do alambique oculto e o mistério do Vinum Lethes.",
                new String[]{
                        "Estirado no chão escuro da copa real abraçado ao meu vaso seco, ouvi um borbulhar rítmico e cristalino vindo de trás da cristaleira de carvalho.",
                        "Empurrei o móvel e descobri uma alcova secreta iluminada por uma lamparina azul, onde uma serpentina de vidro destilava gota a gota um néctar cor-de-rosa denso e perfumado!",
                        "O líquido recém-preparado recolhia-se num cálice de prata, exalando o aroma agridoce da receita original de §6ArzanLion§0 e §5AsterZZZz§0 — vinho velho, beterraba e flores noturnas.",
                        "Preso ao gargalo do alambique com lacre carmesim, havia um pergaminho escrito em caligrafia elegante há poucos dias: §o'Vinum Lethes — Para silenciar a música à meia-noite da vigésima sétima lua, nos aposentos da Torre Real.'§0",
                        "'§oVinum Lethes§0... o Vinho do Rio Letes!', murmurei com os olhos brilhando de compreensão tática. 'É uma poção de hibernação vampírica! §4Conde Jodevil§0 trancou o castelo por vinte e sete dias porque planeja beber este cálice à meia-noite na Torre Real e dormir sob a pedra por mais um século para escapar da Inquisição!'",
                        "Apanhei a bandeja com o cálice cor-de-rosa: se o monstro subiria à Torre Real para beber aquilo na vigésima sétima meia-noite, eu já estaria lá dentro esperando por ele!"
                }));

        // LIVRO 23 (Volume L)
        list.add(new FutureChapterSpec(23, 50, "XXIII", "L",
                "Ato III: A Hora do Julgamento",
                "A Invasão da Torre Real",
                "Onde Valerius usa um aríete contra uma porta destrancada no topo do castelo.",
                new String[]{
                        "Na vigésima terceira noite, subi a imensa escadaria em caracol até o último andar do Castelo sobre o Mar, carregando a espada, o cálice de §oVinum Lethes§0, o vaso de arbusto seco e um pesado banco de carvalho como aríete.",
                        "Diante da porta dupla dos aposentos reais, lembrei-me da chave que desaparecera na cripta no primeiro dia e afastei-me dez passos no corredor: 'Não dormirás por mais um século, §4Jodevil§0!', bradei preparando a carga.",
                        "Corri com toda a força das minhas botas e arremessei o banco de carvalho contra as folhas da porta — mas a fechadura estava completamente destrancada e os batentes abriram-se sozinhos com a corrente de ar um segundo antes!",
                        "Passei direto pelo vão aberto voando como uma bala de canhão, enganchei a espora na barra do meu próprio manto rubro, capotei três vezes sobre o tapete e bati com o queixo bem no pé do leito real!",
                        "Fiquei estendido no piso frio vendo constelações inteiras girarem no teto, enquanto o gato §bSenhor Cummins§0 descia do travesseiro e cheirava meu chapéu de palha amassado com ar de profunda comiseração.",
                        "Quando me sentei limpando o pó do casaco, contemplei enfim o santuário supremo do Conde: o leito de dossel rubro, o violino escuro apoiado ao pé da cama e, no piso de pedra ao lado, um majestoso Baú Duplo."
                }));

        // LIVRO 24 (Volume LI)
        list.add(new FutureChapterSpec(24, 51, "XXIV", "LI",
                "Ato III: A Hora do Julgamento",
                "O Cofre de Sangue",
                "As notas vaidosas e apaixonadas nas margens dos vinte e sete tomos de Asminn.",
                new String[]{
                        "Posicionei o vaso de arbusto seco junto à janela e o cálice cor-de-rosa no suporte da cabeceira antes de ajoelhar-me diante do Baú Duplo, esperando achar relíquias profanas — mas na metade superior brilhavam vinte e sete tomos encadernados em couro carmesim.",
                        "Eram os volumes originais das §oCrônicas do Sangue Duvidoso§0, escritos pelo próprio §9Lorde Asminn§0! Porém, ao folhear os livros na vigésima quarta noite, descobri que alguém passara décadas inteiras relendo e anotando aquelas margens!",
                        "Assim como os príncipes imortais que liam as crônicas de seus companheiros entre risos e lágrimas, o autor das notas protestava com charme vaidoso no Capítulo XIII: §o'Calúnia poética do meu amado Asminn! Eu não caí nas roseiras de Kikkawa, Pipira e Luwuana como uma bigorna; foi um mergulho trágico digno de um anjo rebelde!'§0",
                        "No capítulo de §6ArzanLion§0 e §5AsterZZZz§0, a nota marginal mudava do orgulho para uma ternura dilacerante: §o'Eles me deram um sangue de beterraba que chia ao sol... mas eu daria toda a minha eternidade para ouvir o espirro daquele charlatão e as risadas de Azarasquel, Reznozada e Corvo mais uma vez.'§0",
                        "E na última página do vigésimo sétimo volume — exatamente sobre a linha em que §9Asminn§0 prometia que o amigo jamais estaria sozinho —, brilhou à luz da vela uma gota de lágrima que acabara de cair, ainda úmida no papel!",
                        "Levei a mão ao rosto em choque: minhas pálpebras ardiam e um soluço mudo subia da minha própria garganta, como se aquela saudade colossal morasse dentro do meu próprio peito!"
                }));

        // LIVRO 25 (Volume LII)
        list.add(new FutureChapterSpec(25, 52, "XXV", "LII",
                "Ato III: A Hora do Julgamento",
                "O Cerco Final",
                "No qual Valerius tranca o santuário real e descobre o véu negro na parede.",
                new String[]{
                        "Fechei a tampa do Baú Duplo na vigésima quinta noite sentindo o coração bater nos ouvidos e tranquei a porta do quarto real por dentro: se a lágrima sobre o tomo de §9Asminn§0 ainda estava molhada, §4Conde Jodevil§0 jamais saíra desta câmara!",
                        "Vasculhei debaixo do dossel com a espada, verifiquei o suporte de poções na cabeceira — onde os três cálices cor-de-rosa de §oVinum Lethes§0 aguardavam a meia-noite — e olhei para os pequenos retratos da corte alinhados sobre a lareira como guardiões silenciosos.",
                        "Não havia passagens secretas nas pedras, mas meus olhos detiveram-se no único ponto da parede oeste onde um homem da minha estatura poderia ocultar-se em pé.",
                        "Lá, emoldurado por pedras negras entre dois candelabros acesos, pendia do teto ao chão um longo estandarte de veludo preto, diante do qual o gato §bSenhor Cummins§0 sentara-se imóvel como um juiz.",
                        "No alto da cabeceira, o velho morcego §8Juninho§0 abriu as asas e soltou um chiado suave, olhando fixamente para o pano negro e depois para o violino apoiado ao pé da cama.",
                        "Apertei o punho da estaca de freixo: o Príncipe Rebelde deste castelo estava escondido exatamente atrás daquele véu escuro, esperando as doze badaladas!"
                }));

        // LIVRO 26 (Volume LIII)
        list.add(new FutureChapterSpec(26, 53, "XXVI", "LIII",
                "Ato III: A Hora do Julgamento",
                "Doze Badaladas",
                "A contagem regressiva para a meia-noite diante do estandarte negro.",
                new String[]{
                        "Às vinte e três horas e cinquenta e nove minutos da vigésima sexta para a vigésima sétima noite, postei-me a um passo do estandarte negro, com a estaca erguida na mão direita e os dedos manchados de carmesim da mão esquerda cravados na borda do veludo.",
                        "No campanário acima do quarto real, o sino de bronze começou a anunciar a meia-noite: §oBADALADA UM!§0 — e uma dor aguda atravessou meu peito com a memória da voz de §9Lorde Asminn§0 pedindo que eu me sentasse diante da vela para contar minha história.",
                        "§oBADALADA QUATRO!§0 — minha boca encheu-se com o gosto quente do caldeirão de §6ArzanLion§0, ouvi o ronco profundo de §5AsterZZZz§0 sobre o aramaico e o latido de §eVitória§0 na taverna!",
                        "§oBADALADA OITO!§0 — senti nas costas cada espinho das roseiras sob o riso cristalino de §dLady Kikkawa§0, §dPipira§0 e §dDama Luwuana§0, e na nuca o ardor da colher de prata de §2Azarasquel§0, §2Reznozada§0 e §6Javidizineo§0!",
                        "§oBADALADA ONZE!§0 — ouvi §8Corvo§0 gritando as apostas da aurora no telhado e minha própria voz suplicando §o'por favorzinho'§0 ao imortal §6Jacinto§0 pelo meu §bMjölnir§0!",
                        "§oBADALADA DOZE!§0 — 'Mostra a tua face, §4Conde Jodevil§0!', urrei com os olhos inundados de lágrimas e, num puxão violento, arranquei o estandarte negro da parede para desferir o golpe final!"
                }));

        // LIVRO 27 (Volume LIV — A REVELAÇÃO SUPREMA)
        list.add(new FutureChapterSpec(27, 54, "XXVII", "LIV",
                "Ato III: A Hora do Julgamento",
                "A Sentença da Meia-Noite",
                "Onde a lâmina do inquisidor encontra finalmente o pescoço do Conde.",
                new String[]{
                        "O pano de veludo negro caiu aos meus pés, mas não havia esconderijo algum na parede: era um imenso espelho de cristal dourado. E porque minha maldição torta de beterraba e vinagre jamais teve poder para apagar minha imagem, o vulto que me encarou de volta no vidro — pálido, de manto rubro, punhos de renda sujos de tinta carmesim, chapéu de palha torto e estaca erguida contra o próprio peito — §4era eu mesmo, o Conde Jodevil!§0",
                        "A estaca caiu da minha mão trêmula batendo ao lado do meu velho violino escuro enquanto a ilusão do 'Inquisidor Valerius' se desfazia num único soluço: nunca houve caçador algum! Tal qual os antigos imortais que dormiam sob a terra tentando suportar a passagem das eras, quando o tempo levou meus amigos e deixou este Castelo sobre o Mar mergulhado num silêncio eterno, a saudade dilacerante de §9Asminn§0 e da nossa corte partiu minha alma.",
                        "Lembrei-me de cada detalhe: incapaz de suportar mais um século tocando violino para cadeiras vazias, desci à adega de §6ArzanLion§0 e destilei o §oVinum Lethes§0 para esquecer minha dor; mas, como segui o grimório manchado pela baba de §5AsterZZZz§0, o elixir condenou-me a um ciclo de vinte e sete noites!",
                        "Porque sou vaidoso e apaixonado demais para aceitar a solidão, a cada ciclo minha mente apaga meu nome e inventa um inquisidor implacável — fazendo-me caçar a mim mesmo pelo castelo durante vinte e sete noites apenas para poder sentir outra vez o cheiro das roseiras de §dKikkawa§0 e §dLuwuana§0, a colher de §2Azarasquel§0 e §2Reznozada§0, o bolão de §8Corvo§0, a teimosia de §6Jacinto§0 e o calor das páginas do meu irmão §9Asminn§0!",
                        "Caí de joelhos abraçando §bSenhor Cummins§0 enquanto §8Juninho§0 pousava em meu ombro, rindo e chorando da minha própria ópera gótica; passei os dedos pelas cordas do violino num último acorde desafinado e, vendo que a dor de lembrar que estou sozinho era grande demais para suportar acordado, apanhei o cálice cor-de-rosa no suporte da cabeceira e brindei ao espelho: §o'À nossa eterna meia-noite, meus amados amigos...'§0",
                        "Bebi o §oVinum Lethes§0 até a última gota e, sentindo a névoa do esquecimento subir pelo sangue, desci cambaleando as escadas com um tomo em branco, o tinteiro carmesim e meu chapéu de palha para trancar a cripta de entrada antes de cair sobre a laje — até que o sol penetrou pela grade alta e puxei a pena para escrever: §o'Despertei sobre a laje fria de pedra, coberto pelo pó branco das catacumbas, enquanto um chiado agudo — tsssss — subia da ponta da minha bota...'§0"
                }));
    }
}
