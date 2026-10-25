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
 * ARCO 2: "O Ciclo do Uroboro — O Caçador das Sombras e a Saudade sem Nome" (27 Volumes: Tomos XXVIII a LIV).
 * Passado muitas décadas no futuro, onde um misterioso Viajante e Caçador de Sombras chega ao Castelo sobre o Mar
 * para caçar o "patético vampiro Jodevil", sem saber que sofre da amnésia cíclica do elixir defeituoso
 * e que ele próprio é o Conde Jodevil preso em um loop eterno de saudade da antiga corte.
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
     * Gera todos os livros do Arco 2 (O Ciclo do Uroboro) para compor os slots 27 a 53 do Baú Duplo.
     */
    public static List<ItemStack> createAllArc2ChapterBooks() {
        List<FutureChapterSpec> specs = new ArrayList<>(27);
        addAct1Chapters(specs); // Livros 1 a 9 (Ato I: A Ilusão do Caçador e a Saudade sem Nome)
        addAct2Chapters(specs); // Livros 10 a 18 (Ato II: Os Rastros do Fantasma Carmesim)
        addAct3Chapters(specs); // Livros 19 a 27 (Ato III: O Espelho do Uroboro — O Fim é o Início)

        List<ItemStack> books = new ArrayList<>(specs.size());
        for (FutureChapterSpec spec : specs) {
            books.add(createFutureChapterBook(spec));
        }
        return books;
    }

    public static ItemStack createFutureChapterBook(FutureChapterSpec spec) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        NbtCompound nbt = book.getOrCreateNbt();

        String shortTitle = "Ciclo II " + spec.roman() + " - " + spec.chapterTitle();
        if (shortTitle.length() > 32) {
            shortTitle = shortTitle.substring(0, 32);
        }
        nbt.putString("title", shortTitle);
        nbt.putString("author", "O Caçador Sem Nome");
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
                Text.literal("§4§lTome of Vampiric Knowledge — Ciclo II, Cap. " + spec.roman() + ": " + spec.chapterTitle())));

        NbtList tooltipLore = new NbtList();
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§5" + spec.actTitle()).formatted(Formatting.DARK_PURPLE))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§7\"" + spec.subtitle() + "\"").formatted(Formatting.GRAY, Formatting.ITALIC))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal(""))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§4🩸 O Ciclo do Uroboro (Livro " + spec.arcNumber() + " de 27 | Tomo Geral " + spec.globalRoman() + ")").formatted(Formatting.DARK_RED))));
        tooltipLore.add(NbtString.of(Text.Serializer.toJson(
                Text.literal("§8Manuscrito de um viajante que perdeu o próprio nome.").formatted(Formatting.DARK_GRAY))));
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
        coverLines.add("§4§lCICLO II • " + spec.roman() + "§r");
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
                pages.add(formatPageLines(currentPageLines));
                currentPageLines.clear();
            }
        }
        if (!currentPageLines.isEmpty()) {
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
                i++;
            } else {
                len++;
            }
        }
        return len;
    }

    // =========================================================================
    // ATO I — LIVROS 1 A 9: A ILUSÃO DO CAÇADOR E A SAUDADE SEM NOME
    // =========================================================================
    private static void addAct1Chapters(List<FutureChapterSpec> list) {

        // LIVRO 1 (Volume XXVIII)
        list.add(new FutureChapterSpec(1, 28, "I", "XXVIII",
                "Ato I: A Saudade sem Nome",
                "Tinta Carmesim nos Dedos",
                "Onde o viajante desperta ao som de um chiado com gosto de vinagre e um chapéu de palha.",
                new String[]{
                        "Despertei com um sobressalto, o coração lento batendo como um sino submerso, enquanto um chiado agudo — §o'tsssss'§0 — subia da ponta da minha bota tocada por uma fresta de sol na janela.",
                        "Puxei o pé para a sombra por puro reflexo antes mesmo de abrir inteiramente os olhos. Minha cabeça latejava em uma vertigem espessa, e em minha língua pairava um gosto inexplicável e amargo de vinagre barato, alho velho e sopa fria de beterraba.",
                        "Olhei para as minhas próprias mãos pálidas sobre a escrivaninha: as pontas dos meus dedos estavam manchadas de tinta carmesim ainda úmida, embora eu não me recorde de ter escrito uma única palavra antes de adormecer.",
                        "Ao meu lado, sobre o mármore frio, repousava um cálice vazio e um velho chapéu de palha desfiado pelo vento marinho. Não sei como vim parar nesta torre sobre o oceano morno, nem lembro o nome que minha mãe me deu.",
                        "Sei apenas duas coisas que queimam em meu peito: primeiro, que carrego uma saudade lancinante de rostos queridos que não consigo desenhar na memória; segundo, que vim a este castelo para caçar e destruir o patético vampiro chamado §4Conde Jodevil§0.",
                        "Se esta febre estranha dos trópicos que me faz chiar à luz do dia permitir, iniciarei minha caçada assim que o crepúsculo cair sobre as muralhas."
                }));

        // LIVRO 2 (Volume XXIX)
        list.add(new FutureChapterSpec(2, 29, "II", "XXIX",
                "Ato I: A Saudade sem Nome",
                "O Castelo Silencioso",
                "A exploração dos salões desertos onde outrora ecoaram risos de uma corte dourada.",
                new String[]{
                        "Quando a última claridade do entardecer mergulhou nas águas escuras do mar, levantei-me da poltrona e ajustei minha capa de veludo negro — uma peça surpreendentemente bem cortada para um simples caçador de monstros.",
                        "Por alguma razão que escapa à lógica, coloquei o chapéu de palha debaixo do braço; abandonar aquele objeto grotesco pareceu-me uma traição a um amigo antigo cujo rosto o nevoeiro da minha mente insiste em esconder.",
                        "Desci as escadarias em espiral da fortaleza segurando um candelabro de prata. Quanta beleza melancólica habita estas pedras! As tapeçarias desbotadas pelo sal marinho falam de uma era de ouro que se foi há muitas décadas.",
                        "Ao cruzar o grande salão de banquetes, parei diante da longa mesa vazia. Fechei os olhos e, por um fragmento de segundo, jurei ouvir o tilintar de taças de cristal e o calor de vozes familiares brindando na meia-noite.",
                        "Por que choram os meus olhos diante de cadeiras vazias, se sou apenas um forasteiro que chegou ontem a estas terras? Que feitiço sombrio aquele maldito §4Jodevil§0 lançou sobre a atmosfera deste castelo para contagiar-me com a sua solidão?",
                        "Juro pela minha honra de poeta e caçador: encontrarei o covil desse farsante imortal antes que a vigésima sétima lua se cumpra."
                }));

        // LIVRO 3 (Volume XXX)
        list.add(new FutureChapterSpec(3, 30, "III", "XXX",
                "Ato I: A Saudade sem Nome",
                "A Doença do Meio-Dia",
                "Onde o caçador tenta caminhar sob a manhã e descobre sua estranha insolação.",
                new String[]{
                        "No segundo dia de minha investigação, cometi a imprudência de sair ao pátio descoberto ao meio-dia para examinar as ameias voltadas para o leste.",
                        "Mal havia dado seis passos sob o céu limpo quando senti minhas veias ferverem como óleo na caldeira. Não foi uma dor comum de viajante cansado; foi um cozimento lento, humilhante e cronometrado.",
                        "Contei exatos sessenta batimentos do relógio de sol até que um chiado de toucinho na chapa subiu dos meus ombros, arrancando-me um gemido e drenando uma fração exata do meu vigor vital!",
                        "Mais assustador ainda foi o pressentimento súbito e inexplicável que me gelou a espinha sob aquele calor: a certeza matemática de que, a cada minuto sob o sol, havia uma chance em cem de meu corpo inteiro explodir em cinzas no ato!",
                        "Corri de volta para a sombra das arcadas de modo nada heroico, abanando-me com o chapéu de palha. 'Maldita peste costeira!', murmurei para mim mesmo, limpando a fuligem da testa.",
                        "Certamente o ar viciado pelas experiências profanas que §4Conde Jodevil§0 praticou nestas torres contaminou meus pulmões mortais com alguma alergia solar passageira."
                }));

        // LIVRO 4 (Volume XXXI)
        list.add(new FutureChapterSpec(4, 31, "IV", "XXXI",
                "Ato I: A Saudade sem Nome",
                "O Chapéu Sem Explicação",
                "O mistério do chapéu de palha surrado que o caçador não consegue abandonar.",
                new String[]{
                        "Recomposto do susto matinal, passei a quarta tarde trancado na biblioteca circular, examinando o ridículo chapéu de palha que encontrara ao despertar no primeiro dia.",
                        "Todo caçador de vampiros que se preze veste feltro negro, couro curtido ou elmos de ferro. Por que cargas d'água sinto uma afeição tão absurda por esta peça digna de um espantalho de lavoura?",
                        "Tentei atirá-lo pela janela três vezes; nas três vezes, meu próprio braço travou no ar, tomado por um remorso inexplicável, como se desfazer-me daquela palha trançada fosse rasgar a única carta restante de um velho amigo.",
                        "No forro interno da aba, descobri uma inscrição quase apagada pelo suor e pelas décadas: §o'Sem reembolso — Para os dias de sol.'§0",
                        "Quem escreveria semelhante insolência num presente? E por que, ao ler essa frase mesquinha, senti um calor fraternal no peito, seguido de uma vontade súbita de reclamar de trinta e sete cláusulas contratuais?",
                        "Coloquei o chapéu de volta sobre a mesa com um suspiro. Certamente pertenceu a alguma vítima anterior das trapaças de §4Conde Jodevil§0. Guardá-lo-ei como prova do crime."
                }));

        // LIVRO 5 (Volume XXXII)
        list.add(new FutureChapterSpec(5, 32, "V", "XXXII",
                "Ato I: A Saudade sem Nome",
                "A Cripta da Rainha",
                "Onde o caçador encontra códices antigos sobre a Mãe dos Imortais já anotados.",
                new String[]{
                        "Na quinta noite, desci às galerias da biblioteca subterrânea, onde o cheiro de pergaminho velho e cera de abelha domina o silêncio das abóbadas.",
                        "Sobre o atril de leitura principal, encontrei aberto o lendário códice da Rainha dos Condenados — o mesmo tratado que os antigos místicos diziam conter o segredo dos tronos de alabastro e do sangue ancestral.",
                        "Aproximei meu candelabro das páginas amareladas e notei que as margens estavam repletas de notas apaixonadas, escritas em tinta vermelha: §o'Eu nascera para esta noite! Eu terei a palidez do mármore e o domínio das sombras!'§0",
                        "Ri com sarcasmo diante daquelas linhas. 'Que criatura vaidosa e teatral é esse §4Jodevil§0!', murmurei para as estantes vazias. 'Imaginar-se um príncipe das trevas apenas por usar veludo e dormir até tarde!'",
                        "Contudo, ao virar a folha seguinte, percebi que meus próprios lábios recitavam de cor o parágrafo inteiro antes que meus olhos pudessem lê-lo na penumbra.",
                        "Fechei o livro com força, sentindo um frio percorrer minha nuca. Como posso conhecer palavra por palavra o livro de cabeceira do monstro que vim exterminar?"
                }));

        // LIVRO 6 (Volume XXXIII)
        list.add(new FutureChapterSpec(6, 33, "VI", "XXXIII",
                "Ato I: A Saudade sem Nome",
                "A Sombra do Cronista",
                "A descoberta de uma pena quebrada e a dor inexplicável pelo nome de Asminn.",
                new String[]{
                        "Subindo das galerias inferiores, adentrei a antiga sala de mapas e engenharia da cidadela, onde grandes engrenagens de latão repousavam imóveis pelo tempo.",
                        "Sobre uma bancada de carvalho, encontrei um tinteiro seco e uma placa de bronze gravada com o nome de §9Lorde Asminn§0, o Grande Cronista e Magistrado destas terras.",
                        "No instante em que meus dedos pálidos tocaram as letras daquele nome, uma dor tão aguda e doce atravessou meu peito que precisei apoiar-me na parede para não cair de joelhos.",
                        "'§9Asminn§0...', sussurrei para a sala silenciosa, e o som daquela palavra pareceu preencher a câmara inteira com o calor de mil madrugadas de conversa, de confissões à luz de velas e de uma amizade mais forte que a morte.",
                        "Quem fora aquele homem para mim? Por que sentia eu que lhe entregara toda a história da minha vida, se sou apenas um viajante solitário que perdeu o passado?",
                        "Enxuguei uma lágrima teimosa que manchou meu punho. 'Maldito sejas, §4Jodevil§0!', pensei com amargura. 'Foste tu que separaste este castelo da nobre presença de §9Asminn§0? Juro que honrarei a memória deste cronista!'"
                }));

        // LIVRO 7 (Volume XXXIV)
        list.add(new FutureChapterSpec(7, 34, "VII", "XXXIV",
                "Ato I: A Saudade sem Nome",
                "O Vinho de Terceira",
                "Onde o caçador prova uma garrafa antiga na despensa e sente gosto de beterraba.",
                new String[]{
                        "Na sétima noite, sentindo a garganta seca como pergaminho, vasculhei a despensa real em busca de algum néctar digno de fortalecer meu espírito para a vigília.",
                        "Atrás de barris cobertos de teias, encontrei uma fileira de garrafas empoeiradas sem rótulo, lacradas com cera cor-de-rosa.",
                        "Servi uma taça de cristal e ergui-a contra a luz da vela. O líquido era espesso, escuro como rubi líquido — quase semelhante ao sangue que os falsos vampiros dizem beber nas lendas.",
                        "Levei a taça aos lábios e tomei um gole generoso. No mesmo segundo, cuspi metade no chão com uma careta de horror: era o mesmo caldo intragável de vinho azedo, beterraba cozida e alho velho que eu sentira na boca ao acordar no primeiro dia!",
                        "'Mas que barbárie gastronômica é esta?!', exclamei, limpando a língua com o dorso da mão. 'Que tipo de psicopata guarda sopa de beterraba com vinho barato em garrafas de safra real?'",
                        "Mais perturbador ainda foi notar que a rolha da garrafa já fora aberta e fechada dezenas de vezes recentemente. Alguém neste castelo deserto ainda andava bebendo aquela abominação todas as noites!"
                }));

        // LIVRO 8 (Volume XXXV)
        list.add(new FutureChapterSpec(8, 35, "VIII", "XXXV",
                "Ato I: A Saudade sem Nome",
                "Passos no Mesmo Lugar",
                "A estranha descoberta de pegadas na poeira que calçam o mesmo número de bota.",
                new String[]{
                        "Decidido a rastrear os movimentos noturnos de §4Conde Jodevil§0, espalhei cinzas finas da lareira pelo corredor principal antes de recolher-me para descansar ao amanhecer.",
                        "Quando despertei ao cair da oitava noite — novamente com aquela maldita dor de cabeça e manchas frescas de tinta vermelha nos dedos —, corri imediatamente com meu candelabro para examinar o chão.",
                        "Meu coração deu um salto: havia pegadas nítidas cruzando as cinzas de ponta a ponta! O vampiro estivera ali enquanto eu dormia!",
                        "Ajoelhei-me para medir o rastro da criatura com minha adaga. O solado tinha o formato elegante das botas da velha guarda da corte, com um pequeno desgaste exatamente no calcanhar esquerdo e uma marca de queimadura na ponta do pé direito.",
                        "Por curiosidade, encostei minha própria bota ao lado da pegada na cinza. O tamanho, o desgaste no calcanhar e a marca chamuscada na ponta eram absolutamente idênticos aos meus.",
                        "'Astuto demônio!', rosnei, cerrando os punhos diante do corredor escuro. 'O infame §4Jodevil§0 comprou botas iguais às minhas só para confundir minha perseguição! Mas esse truque infantil não o salvará!'"
                }));

        // LIVRO 9 (Volume XXXVI)
        list.add(new FutureChapterSpec(9, 36, "IX", "XXXVI",
                "Ato I: A Saudade sem Nome",
                "Vozes na Ventania",
                "O encerramento do primeiro ato sob a tempestade e a promessa de vasculhar os jardins.",
                new String[]{
                        "A nona noite trouxe uma tempestade furiosa sobre o oceano morno. Os relâmpagos iluminavam os vitrais partidos da capela, projetando sombras dançantes que pareciam guerreiros, damas e magos de um tempo perdido.",
                        "Caminhei entre os pilares sentindo uma febre romântica crescer em minha alma. Quanto mais tempo passo neste castelo, menos sinto ódio pelo lugar e mais sinto uma ternura desesperada por cada pedra destas muralhas.",
                        "Às vezes, quando o trovão cessa, tenho a impressão de ouvir meu próprio timbre de voz ecoando nas abóbadas, declamando juramentos de proteção eterna aos habitantes desta cidadela.",
                        "Como pode o covil de um monstro transmitir tamanha nobreza? Será possível que §4Jodevil§0 tenha roubado este santuário de um antigo herói que caiu no esquecimento?",
                        "Amanhã à noite deixarei os salões internos e levarei minha busca aos terraços externos, à antiga adega alquímica e aos telhados da vila.",
                        "Pela saudade sem nome que me consome o peito, desmascararei cada fantasma deste labirinto de espelhos!"
                }));
    }

    // =========================================================================
    // ATO II — LIVROS 10 A 18: O LABIRINTO DE ESPELHOS E OS DESASTRES RECORRENTES
    // =========================================================================
    private static void addAct2Chapters(List<FutureChapterSpec> list) {

        // LIVRO 10 (Volume XXXVII)
        list.add(new FutureChapterSpec(10, 37, "X", "XXXVII",
                "Ato II: Labirinto de Espelhos",
                "O Fantasma das Roseiras",
                "Onde o caçador ouve os risos de Kikkawa e Luwuana e despenca nas mesmas roseiras.",
                new String[]{
                        "Na décima noite, segui o perfume das flores noturnas até os terraços orientais, onde uma escultura do peixe místico §bPipira§0 guardava a varanda em que §dLady Kikkawa§0 e §dDama Luwuana§0 costumavam tomar chá.",
                        "O luar banhava o parapeito de pedra com uma luz tão romântica e prateada que a brisa parecia sussurrar risos femininos atrás de leques de seda invisíveis.",
                        "Dominado por uma súbita embriaguez poética — e querendo mostrar às sombras daquelas nobres damas que ainda existia cavalheirismo no mundo —, subi ao parapeito, abri minha capa de veludo contra o vento e inclinei-me sobre o jardim.",
                        "'Contemplai a majestade de quem domina a meia-noite!', exclamei de olhos fechados, dando um passo dramático no ar com a inexplicável convicção de que meu corpo planaria como um morcego sobre o pátio.",
                        "Não planei. Minha bota enroscou na barra da capa e desabei como chumbo diretamente de cara sobre o mesmo arbusto de roseiras espinhosas lá embaixo!",
                        "Saí dos galhos mancando, com três espinhos no queixo e uma rosa presa atrás da orelha, apontando o dedo trêmulo para a varanda: 'Maldito sejas, §4Jodevil§0! Espalhaste óleo invisível no parapeito para emboscar-me com uma armadilha vampírica!'"
                }));

        // LIVRO 11 (Volume XXXVIII)
        list.add(new FutureChapterSpec(11, 38, "XI", "XXXVIII",
                "Ato II: Labirinto de Espelhos",
                "A Adega do Alquimista",
                "As relíquias de ArzanLion, Vitória e AsterZZZz, e o espirro arcano na poeira.",
                new String[]{
                        "Ainda tirando folhas de roseira do colarinho, desci na décima primeira noite às profundezas da antiga adega do §6Grão-Alquimista ArzanLion§0.",
                        "Junto à porta de carvalho, encontrei uma pequena coleira tricolor cuidadosamente guardada num pedestal de honra, dedicada à leal cadelinha guardiã §eVitória§0, cujo espírito fiel parecia ainda proteger a entrada.",
                        "No centro do laboratório subterrâneo repousava o famigerado caldeirão enferrujado sobre um círculo de giz torto como um ovo amassado, cercado de anotações de receitas que misturavam pimenta-do-reino, banha e beterraba.",
                        "Sobre o atril lateral, vi um pergaminho aramaico chamuscado, cuja metade das runas fora dissolvida há décadas pela baba ancestral do adormecido acólito §5AsterZZZz§0.",
                        "Ao inclinar-me para cheirar o fundo do caldeirão em busca de pistas alquímicas, uma nuvem secular de pimenta e pó cor-de-rosa subiu direto às minhas narinas. Tentei segurar, ergui os braços e bradei sem querer: §o'Sanguis Noctis... ATCHIM!'§0",
                        "O espirro sacudiu as teias do teto! 'Por todos os santos!', resmunguei limpando o nariz no lenço. 'Até a poeira desta adega está amaldiçoada com feitiços de rinite!'"
                }));

        // LIVRO 12 (Volume XXXIX)
        list.add(new FutureChapterSpec(12, 39, "XII", "XXXIX",
                "Ato II: Labirinto de Espelhos",
                "A Colher Torta e o Leão",
                "As relíquias de Azarasquel, Javidizineo e Reznozada e a coceira da prata.",
                new String[]{
                        "Na décima segunda noite, minha busca levou-me ao antigo salão da guarda, onde os bravos inquisidores §2Azarasquel§0 e §2Reznozada§0 costumavam reunir-se após as patrulhas.",
                        "Ao pé de um estandarte dourado, vi a estatueta esculpida do pequeno leão guardião §6Javidizineo§0, retratado sentado em sua pose serena de quem aguarda carinho eterno.",
                        "Sobre a mesa de armas, dentro de um estojo de veludo aberto, repousava um objeto inusitado ao lado de um frasco vazio de água benta: uma velha colher de sobremesa feita de prata pura, com uma inscrição de §2Reznozada§0 que dizia §o'Teste Oficial de Nosferatu'§0.",
                        "Distraído enquanto lia o bilhete, cocei o pescoço com a ponta daquela colher de prata. No mesmo instante, uma coceira alérgica e um formigamento ardente espalharam-se pela minha pele pálida!",
                        "Soltei a colher na mesa, coçando o colarinho furiosamente. 'Ora veja só!', disse para mim mesmo em voz alta, tentando manter a compostura científica. 'Essa prata velha oxidada no ar marinho causa dermatite de contato em qualquer caçador de sangue nobre!'",
                        "'Que bom que não sou um vampiro de meia-tigela como §4Jodevil§0, senão pensariam que tenho alergia a talheres de sobremesa!'"
                }));

        // LIVRO 13 (Volume XL)
        list.add(new FutureChapterSpec(13, 40, "XIII", "XL",
                "Ato II: Labirinto de Espelhos",
                "Apostas no Telhado",
                "O caderno de Corvo ao amanhecer e a bota chiando como toucinho na chapa.",
                new String[]{
                        "Pouco antes do amanhecer do décimo terceiro dia, escalei o telhado da antiga taverna da praça, onde diziam que o astuto vigia §8Corvo§0 observava todos os segredos da cidadela.",
                        "Escondido sob uma telha solta, envolto em couro impermeável, encontrei o lendário caderno de apostas de §8Corvo§0! Abri as páginas desbotadas com um sorriso triunfante.",
                        "Ali estavam registradas as moedas apostadas por toda a corte sobre quantos segundos o recém-transformado §4Conde Jodevil§0 duraria sob o primeiro raio de sol sem explodir ou passar vergonha.",
                        "'Hahaha!', gargalhei sozinho no alto do telhado, batendo no joelho. 'Que criatura patética! Imaginem só um vampiro que teme tomar sol como uma donzela de porcelana!'",
                        "Distraído pela minha própria risada, não percebi que o sol acabara de despontar no horizonte e que o minuto fatal corria. De repente, um cheiro forte de couro queimado subiu no ar e meu tornozelo começou a chiar alto — §o'tsssss!'§0 — exatamente como toucinho na frigideira!",
                        "'Fogo! Brasa invisível no telhado!', berrei em pânico, pulando de um pé só e atirando-me pela chaminé adentro para fugir da luz, convencido de que o telhado da taverna estava em combustão espontânea!"
                }));

        // LIVRO 14 (Volume XLI)
        list.add(new FutureChapterSpec(14, 41, "XIV", "XLI",
                "Ato II: Labirinto de Espelhos",
                "O Guardião do Martelo",
                "O confronto humilhante com Jacinto e o 'por favorzinho' que escapa da boca.",
                new String[]{
                        "Na décima quarta noite, decidido a alcançar a ala trancada dos cofres reais, dei de cara no corredor estreito com a figura implacável do servo imortal §6Jacinto§0.",
                        "Lá estava ele, de braços cruzados, bloqueando a passagem enquanto o poderoso martelo §bMjölnir§0 brilhava em sua cintura com uma inscrição gravada no cabo: §o'Mjölnir que não volta'§0.",
                        "'Afasta-te, guardião!', ordenei com minha voz mais cavernosa e imperial. 'Sou o Caçador das Sombras! Entrega-me esse martelo que teu mestre §4Jodevil§0 perdeu por burrice, ou serei forçado a incendiar este corredor!'",
                        "Risquei minha pederneira e lancei uma chama aos pés dele. §6Jacinto§0 sequer piscou; o fogo apagou-se contra sua bota como se tivesse vergonha de queimá-lo. Ele suspirou e disse: 'Patrão, a gente passa por isso toda semana. Tacar fogo não aumenta os dez por cento de chance!'",
                        "Senti uma frustração tão antiga e familiar subir-me à garganta que minha pose de caçador desmoronou. Sem entender por quê, juntei as mãos diante do peito, fiz beicinho e supliquei: 'Ah, vai, §6Jacinto§0, por favorzinho, devolve o meu martelo...'",
                        "Estaquei gelado no mesmo segundo, levando a mão à boca em choque: por que chamei aquela arma de §o'meu martelo'§0?! E por que aquela súplica humilhante saiu tão treinada da minha língua?!"
                }));

        // LIVRO 15 (Volume XLII)
        list.add(new FutureChapterSpec(15, 42, "XV", "XLII",
                "Ato II: Labirinto de Espelhos",
                "Os Olhos de Cummins",
                "O diálogo filosófico na torre onde o gato místico empurra um espelho quebrado.",
                new String[]{
                        "Perturbado pelo incidente no corredor, refugiei-me na torre alta durante a décima quinta noite. Lá me aguardavam o gato místico §6Senhor Cummins§0 e o velho morcego §8Juninho§0, que pairava silencioso perto da minha cintura.",
                        "Sentei-me no chão ao lado do felino e acariciei seu pelo macio. §6Senhor Cummins§0 fechou os olhos, soltou um ronronado profundo que fez vibrar o ar da sala e permaneceu imóvel por doze segundos inteiros, olhando-me com uma ternura infinita.",
                        "'Tu que tudo vês através das eras, pequeno oráculo', murmurei com melancolia, 'dize-me: por que sinto que perdi o amor de toda uma corte que jamais conheci? E onde se esconde esse fantasma chamado §4Jodevil§0?'",
                        "Sem dizer palavra, §6Senhor Cummins§0 caminhou até o canto da lareira e, com a pata de veludo, empurrou um fragmento de espelho quebrado até encostá-lo na minha bota.",
                        "Apanhei o caco de vidro, mas a camada de fuligem e poeira sobre a superfície prateada mostrava apenas um vulto pálido e borrado na escuridão da câmara.",
                        "'Compreendo teu enigma poético, sábio felino!', suspirei emocionado, deixando o espelho de lado sem limpá-lo. 'Queres dizer que minha alma está partida como este vidro até que eu cumpra minha missão!'"
                }));

        // LIVRO 16 (Volume XLIII)
        list.add(new FutureChapterSpec(16, 43, "XVI", "XLIII",
                "Ato II: Labirinto de Espelhos",
                "Cartas na Escuridão",
                "A descoberta de elegias românticas dedicadas a Asminn e aos amigos perdidos.",
                new String[]{
                        "Na décima sexta noite, ao abrir uma gaveta secreta no escritório superior, encontrei um maço de cartas inacabadas que fizeram meu coração estremecer de assombro e beleza.",
                        "Eram poemas e confissões noturnas de um lirismo dilacerante, dirigidas a §9Lorde Asminn§0, a §6ArzanLion§0, a §dKikkawa§0, a §dLuwuana§0, a §2Azarasquel§0, a §2Reznozada§0, a §5AsterZZZz§0 e a §8Corvo§0.",
                        "Li em voz baixa: §o'Ó meus irmãos da alvorada perdida! O tempo levou vossos passos para longe destas muralhas, e deixou-me apenas a eternidade fria de um castelo onde ninguém mais ri das minhas quedas...'§0",
                        "Cada palavra daquelas cartas ressoava dentro do meu próprio peito como se tivesse sido arrancada das minhas veias. Nunca li declaração de amor fraterno mais pura e desesperada do que aquela.",
                        "Mas quando aproximei a vela do rodapé da última folha para ver a data, um calafrio de puro terror percorreu minha espinha dorsal.",
                        "A tinta carmesim daquelas cartas não era de décadas atrás. Estava fresca. Tão fresca que, ao tocá-la, tingiu a ponta do meu dedo indicador exatamente sobre a mancha que eu já trazia na pele!"
                }));

        // LIVRO 17 (Volume XLIV)
        list.add(new FutureChapterSpec(17, 44, "XVII", "XLIV",
                "Ato II: Labirinto de Espelhos",
                "A Mesma Caligrafia",
                "O choque ao perceber que a letra do vampiro é idêntica à do próprio caçador.",
                new String[]{
                        "Com as mãos trêmulas, puxei do bolso os pergaminhos que eu mesmo viera escrevendo ao longo destas dezessete noites e coloquei-os lado a lado com as cartas deixadas pelo monstro.",
                        "A luz do candelabro revelou o impossível: a inclinação das letras, a curva exagerada e dramática dos 'J's e dos 'A's, até mesmo o pingo em forma de gota de sangue sobre os 'i's... a caligrafia era rigorosamente a mesma!",
                        "Recuei dois passos, derrubando a cadeira no tapete. 'Isto é bruxaria mental!', exclamei para as paredes de pedra, sentindo o suor frio escorrer pela minha fronte pálida.",
                        "'O demônio §4Jodevil§0 está me espionando pelas passagens secretas enquanto durmo! Ele estuda meus diários e falsifica a minha própria letra em suas cartas só para me fazer duvidar do meu juízo!'",
                        "Sim, só podia ser isso! Que outra explicação existiria para que dois homens distintos tivessem a mesma letra, o mesmo número de bota, a mesma alergia à prata e o mesmo gosto de beterraba na boca?",
                        "Aquele vampiro covarde estava travando uma guerra psicológica contra mim — mas ele não contava com a astúcia implacável deste caçador!"
                }));

        // LIVRO 18 (Volume XLV)
        list.add(new FutureChapterSpec(18, 45, "XVIII", "XLV",
                "Ato II: Labirinto de Espelhos",
                "A Emboscada a Si Mesmo",
                "Onde o caçador tranca todas as portas por dentro para capturar o intruso.",
                new String[]{
                        "Na décima oitava noite, tomei uma decisão drástica: se §4Conde Jodevil§0 visitava meus aposentos durante o meu sono diurno para falsificar cartas e sujar meus dedos de tinta carmesim, eu o prenderia em uma armadilha perfeita.",
                        "Antes que o sol nascesse, tranquei todas as janelas com barras de ferro, selei a porta de carvalho por dentro com três voltas de chave e engoli a chave junto com um gole de água para que ninguém pudesse roubá-la.",
                        "Amarrei um cordel no meu próprio pulso preso ao candelabro: se qualquer criatura se aproximasse da escrivaninha enquanto eu dormisse, o candelabro cairia e eu despertaria com a espada em punho!",
                        "Adormeci sorrindo, certo de que na noite seguinte teria a cabeça do farsante em minhas mãos.",
                        "Porém, quando acordei ao cair do crepúsculo, a porta continuava trancada por dentro, as janelas seguiam lacradas... mas o candelabro fora cuidadosamente desamarrado do meu pulso e sobre a mesa havia uma nova frase escrita em tinta fresca:",
                        "§o'Não procures lá fora quem chora cá dentro.'§0 O monstro já estava dentro do quarto comigo!"
                }));
    }

    // =========================================================================
    // ATO III — LIVROS 19 A 27: A REVELAÇÃO DO SANGUE DUVIDOSO E O CICLO ETERNO
    // =========================================================================
    private static void addAct3Chapters(List<FutureChapterSpec> list) {
        // LIVRO 19 (Volume XLVI)
        list.add(new FutureChapterSpec(19, 46, "XIX", "XLVI",
                "Ato III: O Espelho do Uroboro",
                "As Muralhas do Eclipse",
                "Onde os músculos do caçador recordam uma batalha que sua mente esqueceu.",
                new String[]{
                        "Fugindo do quarto trancado em busca de ar puro, subi às ameias exteriores do Castelo sobre o Mar, onde as pedras negras ainda exibiam cicatrizes profundas de garras e lâminas antigas.",
                        "Ao apoiar a mão sobre o parapeito da muralha leste, um arrepio percorreu minha espinha: meus dedos fecharam-se no vazio como se empunhassem uma espada invisível, e meu ombro esquerdo girou num golpe de defesa que ninguém jamais me ensinara.",
                        "Fechei os olhos e, por um lampejo vertiginoso, vi o céu tingido pelo antigo §4Eclipse de Sangue§0! Vi §2Azarasquel§0, §2Reznozada§0 e §6Javidizineo§0 mantendo a linha de frente, ouvi os sinos de §dLady Kikkawa§0 e §dDama Luwuana§0 e os gritos de §8Corvo§0 nas torres.",
                        "Mais do que isso: senti em meu próprio peito o orgulho ardente de quem lutara ao lado de §9Asminn§0 e §6ArzanLion§0 não como um monstro temido, mas como um irmão de armas amado por sua corte.",
                        "Quando abri os olhos, apenas o vento marinho soprava sobre as ameias vazias, e uma lágrima solitária escorria pelo meu rosto pálido.",
                        "'Que feitiçaria é esta?', sussurrei, apertando o peito. 'Por que sinto saudade de uma guerra que não lutei e de amigos cujo sangue jamais vi correr?'"
                }));

        // LIVRO 20 (Volume XLVII)
        list.add(new FutureChapterSpec(20, 47, "XX", "XLVII",
                "Ato III: O Espelho do Uroboro",
                "Compaixão pelo Monstro",
                "No qual o ódio do caçador se dissolve em uma estranha piedade pelo vampiro.",
                new String[]{
                        "Vaguei pelas galerias superiores na vigésima noite, mas já não carregava a estaca de madeira em punho; meus passos eram lentos, pesados pela melancolia que transbordava de cada canto da fortaleza.",
                        "Ao ler mais uma das cartas espalhadas pelos aparadores, compreendi enfim a tragédia daquele a quem eu jurara destruir: §4Conde Jodevil§0 não era um tirano sanguinário, mas uma alma partida pela imortalidade.",
                        "Os mortais da corte tinham partido, seguido seus destinos ou adormecido nas eras, enquanto ele permanecera preso entre estas paredes, vigiado apenas por um guardião com um martelo divino, um gato místico e um morcego cansado.",
                        "Percebi, com um nó na garganta, que eu já não odiava o vampiro. Pelo contrário: doía-me o peito com uma compaixão tão funda que era como se a alma de §4Jodevil§0 e a minha própria alma fossem feitas da mesma substância.",
                        "Se eu o encontrasse naquela noite, pensei, não lhe cravaria ferro no coração; sentar-me-ia ao seu lado no salão vazio e choraria com ele a falta de §9Asminn§0 e dos dias que não voltam mais.",
                        "Assustado com tamanha fraqueza poética, sacudi a cabeça: 'Recomponha-te, caçador! O demônio está usando teu próprio coração romântico como escudo!'"
                }));

        // LIVRO 21 (Volume XLVIII)
        list.add(new FutureChapterSpec(21, 48, "XXI", "XLVIII",
                "Ato III: O Espelho do Uroboro",
                "A Fresta na Cortina",
                "Onde o pavor absurdo de um por cento de cinzas quebra a solenidade gótica.",
                new String[]{
                        "Estava eu imerso nessas reflexões sublimes e dignas dos maiores poetas trágicos quando o alvorecer do vigésimo primeiro dia surpreendeu-me no corredor das tapeçarias.",
                        "Uma única fresta de um dedo de largura abriu-se entre duas pesadas cortinas de veludo, projetando um filete de sol matinal exatamente no tapete à minha frente.",
                        "Todo o meu porte aristocrático desapareceu num segundo: soltei um grito agudo digno de uma donzela assustada e atirei-me de bruços atrás de um vaso de samambaia seca, puxando o chapéu de palha até cobrir o nariz!",
                        "'Misericórdia!', arfei, abraçado ao vaso enquanto meu coração batia na garganta. 'E se hoje for o dia?! E se aquele maldito §4um por cento de chance§0 resolver funcionar justo agora e eu virar um montinho de cinzas antes do almoço?!'",
                        "Fiquei encolhido atrás do vaso por meia hora, empurrando a cortina com a ponta da bainha da espada para fechar a fresta sem expor sequer a unha do dedo mindinho à luz.",
                        "Quando a escuridão voltou a reinar no corredor, levantei-me, limpei a poeira do casaco e tossi solenemente, fingindo que aquela manobra tática atrás da samambaia fazia parte do treinamento avançado dos caçadores do norte."
                }));

        // LIVRO 22 (Volume XLIX)
        list.add(new FutureChapterSpec(22, 49, "XXII", "XLIX",
                "Ato III: O Espelho do Uroboro",
                "O Peso da Eternidade",
                "Onde o silêncio dos corredores revela que o tempo parou para um único morador.",
                new String[]{
                        "Na vigésima segunda noite, reparei em um detalhe que até então escapara aos meus olhos de investigador: em todo o castelo, não havia sinais de uma segunda pessoa vivendo nas sombras.",
                        "Havia apenas uma taça usada na copa, apenas uma cadeira puxada diante da lareira, apenas um par de botas — as que estavam nos meus próprios pés — e apenas um tinteiro de tinta carmesim que diminuía a cada noite.",
                        "Todas as portas levavam de volta aos mesmos corredores que eu já percorrera, e o morcego §8Juninho§0 voava atrás de mim não como quem espiona um intruso, mas como um cão fiel que acompanha o único dono que lhe resta.",
                        "Subi a grande escadaria helicoidal que conduzia ao ponto mais alto da cidadela: os aposentos reais onde, segundo as lendas, o próprio §4Conde Jodevil§0 mantinha seu leito perpétuo.",
                        "A cada degrau vencido, o cheiro agridoce de vinho tinto de terceira, beterraba cozida e cera de vela tornava-se mais forte, misturado a um perfume antigo de saudade que me fazia tremer os joelhos.",
                        "Diante da porta dupla de carvalho negro no topo da torre, levei a mão ao trinco: a caçada chegara ao seu último reduto."
                }));

        // LIVRO 23 (Volume L)
        list.add(new FutureChapterSpec(23, 50, "XXIII", "L",
                "Ato III: O Espelho do Uroboro",
                "No Limiar do Quarto Real",
                "No qual a porta do santuário se abre sem resistência para aquele que sempre teve a chave.",
                new String[]{
                        "Empurrei os batentes de carvalho esperando encontrar correntes, cadeados arcanos ou armadilhas mortais, mas a fechadura girou com um clique suave, como se reconhecesse o toque da minha mão.",
                        "O quarto real do Castelo sobre o Mar abriu-se diante de mim em toda a sua majestade gótica: candelabros de prata iluminavam o piso de pedra fria, cortinas rubras protegiam as janelas e, no centro, erguia-se o leito de dossel escuro.",
                        "Ao lado da cabeceira, uma escrivaninha de mogno exibia penas de ganso, um tinteiro carmesim idêntico ao meu e um cálice de prata onde repousava um líquido cor-de-rosa que exalava vapores sonolentos.",
                        "Na parede oposta ao leito, um grande espelho de moldura dourada encontrava-se inteiramente coberto por um pano de veludo negro, e ao pé do espelho estava sentado o gato §bSenhor Cummins§0, imóvel como uma esfinge.",
                        "Mas o que realmente capturou meu olhar foi o que repousava no piso de pedra firme, a poucos passos ao lado da cama: um imponente baú entalhado em madeira escura e ferragens rubras.",
                        "Aproximei-me devagar, sentindo que dentro daquela arca repousava toda a verdade sobre o vampiro e sobre a ferida sem nome que sangrava em meu peito."
                }));

        // LIVRO 24 (Volume LI)
        list.add(new FutureChapterSpec(24, 51, "XXIV", "LI",
                "Ato III: O Espelho do Uroboro",
                "O Baú ao Lado do Leito",
                "Onde o caçador abre a arca de relíquias e encontra os vinte e sete tomos do primeiro ciclo.",
                new String[]{
                        "Ajoelhei-me no chão de pedra ao lado do leito e ergui a tampa pesada do baú: lá dentro, alinhados com devoção sagrada, brilhavam vinte e sete tomos encadernados em couro carmesim.",
                        "Retirei o primeiro volume com mãos reverentes e li o título gravado a ouro na capa: §o'Crônicas do Sangue Duvidoso — Escrito por Conde Jodevil & Asminn'§0.",
                        "Abri as páginas banhadas pela luz trêmula das velas e comecei a ler: ali estava a história completa da era de ouro da corte! Li sobre a ambição poética de §4Jodevil§0 e sobre como §9Asminn§0 trouxera a pena e o tinteiro para eternizar sua confissão.",
                        "Li sobre a adega torta de §6ArzanLion§0, o vinho de terceira categoria misturado com beterraba e alho vencido, a baba de §5AsterZZZz§0 borrando o pergaminho em aramaico e o espirro colossal que selou a maldição.",
                        "Minhas mãos começaram a tremer violentamente: cada frase daquelas crônicas ressoava dentro do meu crânio não como uma história alheia, mas como uma lembrança arrancada das profundezas da minha própria carne!",
                        "Sem conseguir parar, puxei o tomo seguinte, e depois outro, devorando os capítulos no chão frio do quarto enquanto as horas da vigésima quarta noite escorriam como areia."
                }));

        // LIVRO 25 (Volume LII)
        list.add(new FutureChapterSpec(25, 52, "XXV", "LII",
                "Ato III: O Espelho do Uroboro",
                "As Crônicas de Asminn",
                "No qual a leitura dos tomos antigos abala todas as certezas do caçador.",
                new String[]{
                        "Na vigésima quinta noite, ainda sentado diante do baú aberto ao lado da cama, concluí a leitura dos vinte e sete volumes originais deixados pelo cronista §9Asminn§0.",
                        "Ali estavam todos os desastres que eu julgara ridículos: o salto do parapeito direto nas roseiras diante de §dLady Kikkawa§0, §dPipira§0 e §dDama Luwuana§0; a colher de prata de §2Azarasquel§0 e §2Reznozada§0 que dava coceira no pescoço; o livro de apostas de §8Corvo§0 no telhado.",
                        "Ali estava até mesmo o relato humilhante de como §4Jodevil§0 entregara o martelo divino por acidente ao guardião §6Jacinto§0 e fora condenado a pedir §o'por favorzinho'§0 sem jamais usar fogo!",
                        "E, no último daqueles vinte e sete tomos, li o juramento final de §9Asminn§0 prometendo que, enquanto aquelas páginas existissem no baú ao lado do leito, §4Jodevil§0 jamais estaria verdadeiramente sozinho na eternidade.",
                        "Fechei o vigésimo sétimo livro com o rosto lavado em lágrimas: 'Se tudo isto aconteceu há tantas décadas...', sussurrei com a voz embargada, '...por que minhas costas têm os mesmos espinhos de roseira, meu pescoço tem a mesma coceira e minha boca repete as mesmas súplicas a §6Jacinto§0?'",
                        "Um terror gelado e sublime tomou conta de mim: faltava apenas uma noite para que o mistério se revelasse por inteiro."
                }));

        // LIVRO 26 (Volume LIII)
        list.add(new FutureChapterSpec(26, 53, "XXVI", "LIII",
                "Ato III: O Espelho do Uroboro",
                "A Emboscada à Meia-Noite",
                "Onde o caçador aguarda o Conde no quarto trancado e puxa o véu negro do espelho.",
                new String[]{
                        "Na vigésima sexta noite, tranquei a porta dos aposentos reais, coloquei os vinte e sete volumes de volta no baú e postei-me no centro do quarto, aguardando as doze badaladas da meia-noite.",
                        "'Aparece, §4Conde Jodevil§0!', bradei sob a luz do candelabro. 'Sei que estás neste quarto! Sei que choras por §9Asminn§0 e pela corte perdida tanto quanto eu! Mostra a tua face!'",
                        "Ninguém respondeu além do som da minha própria respiração ofegante e do cheiro inconfundível de beterraba e vinho azedo que vinha das minhas próprias vestes.",
                        "No alto do dossel da cama, o velho morcego §8Juninho§0 pousou silenciosamente, olhando para mim com seus olhinhos escuros cheios de pena; no chão, §bSenhor Cummins§0 ergueu-se e miou, batendo com a pata no pano negro que cobria o grande espelho.",
                        "Caminhei até o espelho com o coração prestes a explodir. Se o monstro se escondia atrás daquele véu ou se usava o espelho como passagem secreta, nosso duelo terminaria agora.",
                        "Segurei a borda do veludo negro com a mão manchada de tinta carmesim e, num único golpe, arranquei o pano da frente do espelho!"
                }));

        // LIVRO 27 (Volume LIV — O FIM QUE É O INÍCIO)
        list.add(new FutureChapterSpec(27, 54, "XXVII", "LIV",
                "Ato III: O Espelho do Uroboro",
                "O Fim que é o Início",
                "A revelação suprema diante do espelho e o cálice que reinicia o primeiro capítulo.",
                new String[]{
                        "O pano negro caiu ao chão e, no cristal do espelho — pois minha maldição de meia-tigela nem sequer me negara o reflexo —, não vi um caçador estrangeiro: vi a mim mesmo, §4Conde Jodevil§0, pálido, de capa de veludo puída, e ao lado do cálice na cabeceira li o bilhete com a minha própria letra: §o'Para quando descobrires quem és, meu pobre amigo.'§0",
                        "Num único relâmpago, todas as peças se encaixaram com força esmagadora: o chiado na bota sob o sol e o pavor de §41% de virar cinzas§0 eram a minha própria maldição solar; o gosto de beterraba e vinagre na garganta era o feitiço torto de §6ArzanLion§0; o chapéu de palha sem reembolso, a queda nas roseiras de §dKikkawa§0 e §dLuwuana§0, o §o'por favorzinho'§0 para §6Jacinto§0 devolver meu §bMjölnir§0 e as cartas para §9Asminn§0... §4tudo fora feito por mim mesmo!§0",
                        "Lembrei-me de tudo: décadas após a partida da corte, a saudade dilacerante de §9Asminn§0, §6ArzanLion§0, §5AsterZZZz§0, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0 quase me enlouqueceu de solidão imortal, e bebi uma poção de esquecimento deixada na adega.",
                        "Porém, como a receita levava a mesma baba de §5AsterZZZz§0 que borrou as runas em aramaico, o esquecimento saiu tão defeituoso quanto o meu vampirismo: a cada vinte e sete noites, minha memória se apaga, acordo achando que sou um caçador recém-chegado e persigo meus próprios rastros desastrosos pelo castelo!",
                        "Caí de joelhos diante da arca de relíquias ao lado do meu leito real, chorando de rir e de dor ao compreender a beleza trágica do meu destino; mas o peso de recordar décadas de solidão de uma só vez era insuportável demais para o meu peito, então ergui o cálice cor-de-rosa que eu mesmo preparara na cabeceira e bebi até a última gota: §o'Até daqui a vinte e sete noites, meus amigos...'§0",
                        "Minha visão escureceu, deixei o chapéu de palha cair ao lado da poltrona, esbarrei no tinteiro sujando as pontas dos dedos de tinta carmesim e apaguei — até que um raio de sol atravessou a fresta da janela fazendo a ponta da minha bota chiar (§o'tsssss'§0), e acordei tonto, com gosto de vinagre e beterraba na boca, puxando este pergaminho para escrever: §o'Despertei com a cabeça pesada e o som de um chiado estranho...'§0"
                }));
    }
}
