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
 * Escrito sob a perspectiva do implacável "Inquisidor Valerius", que acredita ter invadido
 * o Castelo sobre o Mar para expurgar o Conde Jodevil e resgatar a antiga corte,
 * num thriller gótico de investigação, armadilhas desastrosas e paranoia crescente
 * que culmina na revelação visceral da meia-noite no Livro 27.
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
                "Tinta Carmesim nos Dedos",
                "Onde o Inquisidor Valerius inicia os autos de caça na antecâmara do castelo.",
                new String[]{
                        "Despertei com um sobressalto, o coração lento batendo como um sino submerso, enquanto um chiado agudo — §o'tsssss'§0 — subia da ponta da minha bota...",
                        "Puxei o pé imediatamente para a sombra da poltrona de veludo e sacudi a cabeça, tentando dissipar a vertigem que nublava meus sentidos. Meu nome é §9Valerius§0, Inquisidor da Ordem Noturna, e infiltrei-me neste Castelo sobre o Mar na calada da noite com uma única missão: executar o famigerado §4Conde Jodevil§0.",
                        "O demônio certamente lançou algum gás sonífero pela fechadura quando forcei a entrada de madrugada, pois acordei com uma enxaqueca atroz e um gosto hediondo de vinagre barato, alho velho e sopa de beterraba fria grudado no céu da boca.",
                        "Mais estranho ainda: ao olhar para minhas mãos sobre a escrivaninha, vi que as pontas dos meus dedos estavam manchadas de tinta carmesim ainda úmida, ao lado de uma taça vazia e de um ridículo chapéu de palha desfiado.",
                        "Pelo visto, antes que o gás do vampiro me derrubasse, tentei redigir meu primeiro relatório de infiltração e acabei esbarrando no tinteiro.",
                        "Verifiquei minha espada e minha estaca de freixo: o tirano sangrento mantém a antiga corte prisioneira em algum calabouço desta fortaleza, e não descansarei até libertar cada alma e cravar ferro no peito do Conde!"
                }));

        // LIVRO 2 (Volume XXIX)
        list.add(new FutureChapterSpec(2, 29, "II", "XXIX",
                "Ato I: O Cerco Solitário",
                "O Banquete Interrompido",
                "A descoberta de rastros frescos no salão principal da cidadela.",
                new String[]{
                        "Assim que o sol mergulhou no oceano, saí da antecâmara de espada em punho — levando, por puro pragmatismo tático, aquele chapéu de palha sob o braço, caso precisasse de um disfarce camponês.",
                        "Desci ao grande salão de banquetes esperando encontrar guardas mortos-vivos, mas deparei-me com uma cena que me gelou a espinha: a mesa principal fora posta para doze pessoas, porém apenas um prato na cabeceira estava sujo.",
                        "Toquei o fundo da sopeira de prata: ainda estava morno! Um caldo espesso e avermelhado, cheirando fortemente a beterraba cozida e vinho azedo, escorria pela borda como se o monstro tivesse fugido segundos antes de eu abrir a porta.",
                        "De repente, ao olhar para as onze cadeiras vazias ao redor da mesa, uma dor lancinante atravessou meu peito, tão aguda que precisei apoiar-me no espaldar de carvalho para não cair de joelhos.",
                        "Vi em minha mente o clarão de taças erguidas, ouvi gargalhadas cristalinas e senti uma falta desesperadora daqueles rostos que o vampiro sequestrara — como se eu mesmo os amasse há décadas!",
                        "Um ruído metálico ecoou atrás da tapeçaria do fundo; saltei sobre a mesa e rasguei o tecido com a espada, mas encontrei apenas uma passagem secreta trancada por dentro e um morcego gordo fugindo pelo teto."
                }));

        // LIVRO 3 (Volume XXX)
        list.add(new FutureChapterSpec(3, 30, "III", "XXX",
                "Ato I: O Cerco Solitário",
                "O Poço Envenenado",
                "Onde o Inquisidor descobre a maldição solar que o impede de caçar ao meio-dia.",
                new String[]{
                        "Na manhã seguinte, decidi quebrar a regra dos caçadores e vasculhar o pátio externo sob a luz plena do meio-dia, hora em que o covarde §4Jodevil§0 estaria dormindo indefeso em seu esquife.",
                        "Mal dei três passos sob o céu aberto, porém, uma febre abominável subiu pelas minhas veias! Minha pele começou a fumegar com cheiro de toucinho na chapa e senti minha força vital ser drenada gota a gota pela claridade.",
                        "Pior do que a queimadura lenta foi o pavor súbito e inexplicável que me paralisou: a certeza aterradora da §4Centésima Centelha§0 — o pressentimento de que, a qualquer batida do coração, um curto-circuito arcano poderia transformar meu corpo inteiro em cinzas num piscar de olhos!",
                        "Rolei de volta para a sombra da arcada, ofegante e suando frio, enquanto apagava com as mãos a fumaça que saía da minha capa.",
                        "'Maldita seja a perfídia de §4Jodevil§0!', rosnei, chutando um balde ao lado da cisterna. 'O canalha envenenou a água do poço do castelo com alguma praga heliofóbica para que nenhum inquisidor possa caçá-lo durante o dia!'",
                        "Jurei não beber mais nenhuma gota d'água daquela fortaleza: até que a cabeça do Conde rolasse, a caçada aconteceria exclusivamente entre o pôr do sol e a aurora."
                }));

        // LIVRO 4 (Volume XXXI)
        list.add(new FutureChapterSpec(4, 31, "IV", "XXXI",
                "Ato I: O Cerco Solitário",
                "A Armadilha do Chapéu",
                "No qual Valerius tenta descartar o chapéu de palha e sofre um ataque de fúria contratual.",
                new String[]{
                        "Furioso com a emboscada solar no pátio, voltei à base de operações e decidi livrar-me daquele ridículo chapéu de palha que encontrara no primeiro dia, suspeitando que o objeto contivesse algum feitiço de rastreamento.",
                        "Aproximei a aba desfiada das chamas da lareira para queimá-lo — mas, no exato instante em que o fogo lambeu a palha, minha própria mão esquerda agarrou meu pulso direito num golpe de defesa involuntário!",
                        "Atordoado com o espasmo do meu próprio braço, virei o chapéu contra a luz das velas e descobri, gravada a fogo no couro interno da carneira, uma inscrição cínica: §o'Sem reembolso — Para os dias de sol.'§0",
                        "Ao ler aquelas palavras, uma fúria completamente desproporcional subiu-me à garganta: comecei a marchar pelo quarto bradando contra 'trinta e sete cláusulas abusivas' e 'propaganda enganosa de taverna'!",
                        "Estaquei no meio do tapete, levando a mão à boca em choque: por que diabos um Inquisidor da Ordem se importaria com o código de defesa do consumidor de um feiticeiro charlatão?!",
                        "Antes que pudesse responder, ouvi três batidas secas vindas de dentro do armário de armas; quando o abri de golpe, encontrei apenas um pergaminho rasgado apontando para a cripta subterrânea."
                }));

        // LIVRO 5 (Volume XXXII)
        list.add(new FutureChapterSpec(5, 32, "V", "XXXII",
                "Ato I: O Cerco Solitário",
                "O Segredo da Cripta",
                "Onde o Inquisidor desce aos subterrâneos e quase é soterrado por um altar falso.",
                new String[]{
                        "Seguindo o mapa rasgado, desci na quinta noite às galerias úmidas abaixo da capela, onde o cheiro de salitre misturava-se ao aroma inconfundível de alho velho.",
                        "No centro da cripta, sobre um pedestal de basalto, repousava o lendário códice da §4Rainha dos Condenados§0 — mas, ao redor do pedestal, notei fios quase invisíveis ligados a contrapesos no teto.",
                        "Usando a ponta da bainha, desarmei a armadilha segundos antes que uma grade enferrujada caísse sobre minha cabeça, e puxei o livro aberto.",
                        "As margens do texto sagrado estavam cobertas de anotações furiosas em tinta carmesim: onde o livro falava do 'Sangue Real dos Cárpatos', o autor rabiscara: §o'Nota: ArzanLion usou tinto de taverna e disse que dá no mesmo!'§0",
                        "O mais assustador, porém, aconteceu quando virei a página: sem olhar para o pergaminho, minha boca começou a recitar em voz alta o parágrafo seguinte inteiro, incluindo os erros de tradução nas margens!",
                        "Um estrondo na escadaria fez-me apagar a lanterna; corri de volta ao topo exatamente a tempo de ver a sombra de uma capa dobrar o corredor rumo à sala das engrenagens."
                }));

        // LIVRO 6 (Volume XXXIII)
        list.add(new FutureChapterSpec(6, 33, "VI", "XXXIII",
                "Ato I: O Cerco Solitário",
                "A Xícara Ainda Morna",
                "A perseguição até o gabinete de Lorde Asminn e o mistério da presença invisível.",
                new String[]{
                        "Corri pelo corredor norte com a espada em riste e chutei a porta da sala de máquinas e arquivos, onde uma placa de bronze anunciava: §o'Gabinete de Lorde Asminn — Cronista e Engenheiro do Reino'§0.",
                        "O cômodo parecia vazio, mas meus olhos de caçador captaram o impossível sobre a bancada de latão: uma vela acabara de ser apagada, soltando um fio reto de fumaça branca, e ao lado dela havia uma xícara de chá ainda morna!",
                        "Sobre o cavalete, um pergaminho tinha uma frase interrompida pela metade, com a gota de tinta ainda brilhando: §o'Perdoa-me, meu velho amigo Asminn, pois hoje...'§0",
                        "Olhei debaixo da mesa, atrás das estantes de engrenagens e dentro dos dutos de ventilação: não havia absolutamente nenhuma outra saída naquela sala além da porta pela qual eu acabara de entrar!",
                        "Como poderia o autor daquela nota estar escrevendo ali segundos antes da minha chegada e evaporar no ar sem cruzar por mim no corredor?!",
                        "Mais perturbador ainda: ao tocar o nome de §9Asminn§0 gravado no bronze, uma onda de afeto fraterno e saudade tão devastadora invadiu meu peito que precisei morder o lábio até sangrar para não gritar por ele na escuridão."
                }));

        // LIVRO 7 (Volume XXXIV)
        list.add(new FutureChapterSpec(7, 34, "VII", "XXXIV",
                "Ato I: O Cerco Solitário",
                "Veneno na Despensa",
                "Onde Valerius investiga o suprimento de sangue do vampiro e sofre um colapso gástrico.",
                new String[]{
                        "Decidido a cortar os suprimentos vitais do monstro, invadi a despensa real na sétima noite, preparando-me para destruir os barris de sangue humano que §4Conde Jodevil§0 certamente estocava nas sombras.",
                        "Em vez de sangue nobre, encontrei dezenas de garrafas tortas lacradas com cera cor-de-rosa e guardanapos bordados com o brasão da corte.",
                        "Arranquei a rolha de uma das garrafas com os dentes e, para confirmar a natureza necromântica do fluido, tomei um gole cauteloso.",
                        "Cuspi tudo imediatamente no chão de pedra, tossindo até lacrimejar: era o mesmíssimogororoba intragável de vinho tinto azedo, caldo de beterraba, cebola e alho murcho cujo gosto eu sentira na boca ao acordar no primeiro dia!",
                        "'Mas que espécie de demônio das trevas alimenta-se de sopa de beterraba azeda?!', bradei, limpando a língua com a manga do casaco enquanto uma azia monumental queimava meu estômago.",
                        "Pior: ao olhar para as garrafas vazias empilhadas num canto, contei exatamente seis garrafas consumidas nos últimos seis dias — uma por noite desde que cheguei ao castelo!"
                }));

        // LIVRO 8 (Volume XXXV)
        list.add(new FutureChapterSpec(8, 35, "VIII", "XXXV",
                "Ato I: O Cerco Solitário",
                "Pegadas na Cinza",
                "Uma caçada em tempo real pelos corredores que termina em um círculo impossível.",
                new String[]{
                        "Se o vampiro vinha à despensa todas as noites beber sua gororoba cor-de-rosa, eu o pegaria em flagrante: espalhei uma camada fina de cinzas da lareira por todo o corredor central e apaguei os archotes.",
                        "À meia-noite em ponto, ouvi o som nítido de botas pisando na cinza logo à frente, dobrando a esquina da galeria de colunas!",
                        "Acendi a lanterna furta-fogo e avancei em passo de carga, seguindo o rastro fresco que se desenhava no pó cinzento: o inimigo usava botas de couro tamanho quarenta e dois, com o salto esquerdo gasto e a ponta do pé direito chamuscada.",
                        "'Agora és meu, §4Jodevil§0!', gritei, correndo atrás dos passos que ecoavam cada vez mais perto à medida que eu contornava a grande coluna central do salão.",
                        "Dei um salto mortal ao redor do pilar com a espada erguida para decapitar o vulto — mas encontrei apenas o corredor vazio!",
                        "Baixei a lanterna para o chão e meu sangue congelou nas veias: o rastro de pegadas na cinza dava a volta completa na coluna e terminava exatamente debaixo dos meus próprios calcanhares!"
                }));

        // LIVRO 9 (Volume XXXVI)
        list.add(new FutureChapterSpec(9, 36, "IX", "XXXVI",
                "Ato I: O Cerco Solitário",
                "O Eco na Capela",
                "Onde uma tempestade marítima revela sussurros e empurra a caçada para o exterior.",
                new String[]{
                        "Recusando-me a acreditar no truque de ilusionismo das pegadas, corri até a capela abandonada no final da galeria, onde uma tempestade violenta açoitava os vitrais quebrados sobre o mar.",
                        "Subi ao púlpito de pedra e desafiei o senhor do castelo aos berros: 'Aparece, farsante! Onde escondeste §9Lorde Asminn§0 e a nobre corte deste reino?!'",
                        "O trovão sacudiu as abóbadas, mas o eco que voltou do teto acústico não repetiu a minha pergunta: no meio do uivo do vento, ouvi minha própria voz responder num sussurro partido de dor: §o'Eles se foram... e o tempo levou a todos, menos a ti...'§0",
                        "Disparei uma adaga de prata na direção do coro alto, mas a lâmina cravou-se apenas na madeira podre, derrubando um ninho vazio.",
                        "Percebi então o plano diabólico de §4Jodevil§0: ele havia preparado os corredores internos com tubos acústicos, espelhos ocultos e poeira alucinógena para destruir a sanidade de qualquer inquisidor!",
                        "Se eu quisesse vencer o monstro, precisava sair das galerias internas e vasculhar os terraços abertos, a adega alquímica e os telhados da fortaleza a partir da décima noite."
                }));
    }

    // =========================================================================
    // ATO II — LIVROS 10 A 18: A SOMBRA NO CORREDOR
    // =========================================================================
    private static void addAct2Chapters(List<FutureChapterSpec> list) {
        // LIVRO 10 (Volume XXXVII)
        list.add(new FutureChapterSpec(10, 37, "X", "XXXVII",
                "Ato II: A Sombra no Corredor",
                "A Emboscada das Roseiras",
                "No qual o Inquisidor persegue vozes femininas no terraço e despenca do parapeito.",
                new String[]{
                        "Na décima noite, subi ao terraço do jardim de inverno de besta armada, onde um sino de vento enferrujado trazia gravados os nomes de §dLady Kikkawa§0, da fiel §dPipira§0 e de §dDama Luwuana§0.",
                        "O luar banhava as balaustradas quando ouvi, vindo exatamente do pátio abaixo do parapeito, o som inconfundível de duas damas rindo e cochichando!",
                        "'Prisioneiras da corte!', pensei com o coração aos saltos. Embainhei a arma, subi na mureta de pedra para enxergar melhor o jardim e, tomado por um impulso inexplicável de galanteria, abri os braços como se pudesse planar até lá embaixo bradando: §o'Não temais, eu sou a tormenta!'§0",
                        "No exato segundo em que pronunciei aquela frase absurda, a pedra frouxa do parapeito virou sob meu calcanhar e eu despenquei de cabeça no escuro como um saco de batatas!",
                        "Não planei um centímetro sequer: aterrissei de cara no meio do mesmo canteiro de roseiras bravas lá embaixo, ficando de pernas para o ar enquanto os espinhos rasgavam meu casaco e minha dignidade.",
                        "Enquanto arrancava espinhos das orelhas, notei que os galhos quebrados tinham cicatrizes antigas exatamente no formato do meu corpo — prova cabal de que §4Jodevil§0 serrara aquela pedra do parapeito para emboscar caçadores!"
                }));

        // LIVRO 11 (Volume XXXVIII)
        list.add(new FutureChapterSpec(11, 38, "XI", "XXXVIII",
                "Ato II: A Sombra no Corredor",
                "Explosão na Adega",
                "Onde Valerius invade o laboratório de ArzanLion e derruba o covil com um espirro.",
                new String[]{
                        "Mancando e cheio de arranhões de roseira, desci na décima primeira noite à adega subterrânea do antigo ocultista §6ArzanLion§0, certo de que o vampiro usava aquele antro para fabricar seus venenos.",
                        "Junto à porta, sobre uma almofada de veludo intacta, encontrei a coleira da leal cadelinha §eVitória§0; fiz uma reverência silenciosa à pequena guardiã e avancei de espada em riste até o caldeirão central.",
                        "O caldeirão ainda exalava um cheiro forte de cebola e pimenta-rosa! Convencido de que §4Jodevil§0 estava escondido atrás dos barris de carvalho, saltei sobre a bancada e puxei o pergaminho em aramaico manchado pela baba seca do monge §5AsterZZZz§0.",
                        "Tentei ler as runas borradas em voz alta para quebrar o encantamento da sala, mas uma nuvem de pimenta-rosa e poeira ancestral subiu do pergaminho direto para minhas narinas!",
                        "Tentei segurar, prendi a respiração, mas foi inútil: §o'Sanguis Noctis... ATCHIM!'§0 — soltei um espirro tão colossal que meu cotovelo bateu na estante e derrubou trinta frascos alquímicos em cima da minha própria cabeça!",
                        "Quando saí debaixo dos cacos tossindo numa nuvem de fumaça cor-de-rosa, percebi que o líquido derramado no meu casaco era idêntico ao das garrafas da despensa."
                }));

        // LIVRO 12 (Volume XXXIX)
        list.add(new FutureChapterSpec(12, 39, "XII", "XXXIX",
                "Ato II: A Sombra no Corredor",
                "A Relíquia da Inquisição",
                "O teste da colher de prata de Azarasquel e Reznozada e uma reação alérgica suspeita.",
                new String[]{
                        "Para provar a mim mesmo que a fumaça cor-de-rosa da adega não havia me contaminado com bruxaria, corri à sala de troféus onde os antigos inquisidores §2Azarasquel§0 e §2Reznozada§0 guardavam suas armas sagradas, sob a estátua do leão §6Javidizineo§0.",
                        "Forcei o cadeado do relicário de vidro e retirei de lá o artefato que ambos usavam para desmascarar criaturas das trevas: uma velha colher de sobremesa de prata torta com a etiqueta §o'Teste Oficial de Nosferatu'§0.",
                        "'Excelente!', comentei sozinho. 'Quando eu encostar esta prata sagrada na testa de §4Jodevil§0, o monstro se contorcerá em chamas!'",
                        "Enquanto caminhava pela sala planejando a emboscada, passei distraído o cabo da colher de prata no meu próprio pescoço para coçar uma picada de mosquito.",
                        "No mesmo instante, minha pele ardeu como fogo e uma coceira alucinante espalhou-se pela minha nuca, fazendo-me dançar pelo salão enquanto me coçava com as duas mãos!",
                        "Joguei a colher longe, indignado: 'Aquele vampiro imundo nem sequer limpa os talheres do castelo! Deixou a prata oxidar no ar salgado até criar uma dermatite marítima capaz de empolar até o pescoço de um santo inquisidor!'"
                }));

        // LIVRO 13 (Volume XL)
        list.add(new FutureChapterSpec(13, 40, "XIII", "XL",
                "Ato II: A Sombra no Corredor",
                "Fogo no Telhado",
                "No qual Valerius encontra o livro de apostas de Corvo e foge da aurora em chamas.",
                new String[]{
                        "Na décima terceira noite, escalei o telhado da antiga taverna do porto usando um arpéu, buscando o posto de observação onde o lendário vigia §8Corvo§0 costumava espreitar a corte.",
                        "Debaixo de uma telha solta, encontrei uma caixa de ferro contendo o caderno de couro de §8Corvo§0, intitulado: §o'Bolão Oficial — Quantos minutos o Conde dura ao Sol antes da Centésima Centelha?'§0.",
                        "Abri o livro à luz da lanterna e soltei uma gargalhada debochada ao ler as apostas de §2Reznozada§0, §6ArzanLion§0 e §dLuwuana§0: 'Hahaha! Que criatura patética é esse §4Jodevil§0! Um vampiro que vira toucinho frito sob o sol!'",
                        "Enquanto eu ria de barriga cheia no alto da cumeeira, porém, não percebi que o horizonte leste acabara de ficar dourado e o primeiro raio da aurora subia sobre o mar.",
                        "O feixe de sol atingiu em cheio o meu tornozelo esquerdo: no mesmo segundo, um chiado ensurdecedor de banha na frigideira (§o'TSSSSS!'§0) subiu da minha bota, acompanhado de uma fumaça negra e uma dor lancinante!",
                        "Atirei-me telhado abaixo num rolamento desesperado, caindo dentro de uma carroça de feno na sombra do beco, convicto de que §4Jodevil§0 havia instalado lentes de aumento invisíveis nas chaminés para queimar meu pé!"
                }));

        // LIVRO 14 (Volume XLI)
        list.add(new FutureChapterSpec(14, 41, "XIV", "XLI",
                "Ato II: A Sombra no Corredor",
                "O Guardião do Martelo",
                "O confronto humilhante com o imortal Jacinto nos corredores dos cofres.",
                new String[]{
                        "Precisando de armas mais poderosas após quase perder o pé no telhado, desci na décima quarta noite à galeria dos cofres, onde dei de cara com o único habitante vivo da fortaleza: o servo imortal §6Jacinto§0.",
                        "Ele estava parado diante da porta blindada segurando, como se fosse um espanador qualquer, uma arma divina que fez meus olhos brilharem: o lendário martelo §bMjölnir que não volta§0!",
                        "Estufei o peito e avancei com voz de trovão: 'Em nome da Inquisição, servo §6Jacinto§0, ordeno que me entregues esse martelo sagrado agora mesmo para que eu esmague o crânio do teu mestre §4Jodevil§0!'",
                        "§6Jacinto§0 sequer piscou; tentei então intimidá-lo encostando minha tocha acesa em sua túnica, mas o fogo apagou-se sozinho contra o couro dele, e o guardião murmurou: §o'Patrão, tacar fogo não adianta nada... pede com jeitinho que quem sabe eu devolvo.'§0",
                        "Abri a boca para proferir uma ameaça terrível de excomunhão, mas minha língua traiu meu cérebro e, por puro reflexo muscular, juntei as mãos e choraminguei com voz fina: §o'Ah, vai, Jacinto, por favorzinho, devolve o meu martelo, foi sem querer que eu te dei!'§0",
                        "Tapando a própria boca em horror absoluto diante daquela frase humilhante, saí correndo pelo corredor enquanto §6Jacinto§0 abanava a cabeça e voltava a limpar as unhas com o cabo do §bMjölnir§0."
                }));

        // LIVRO 15 (Volume XLII)
        list.add(new FutureChapterSpec(15, 42, "XV", "XLII",
                "Ato II: A Sombra no Corredor",
                "O Espelho Quebrado",
                "Onde o gato Senhor Cummins tenta revelar a verdade na torre do observatório.",
                new String[]{
                        "Ainda rubro de vergonha pelo vexame diante de §6Jacinto§0, refugiei-me na décima quinta noite na torre do observatório para recompor minha mente abalada.",
                        "Lá no alto, sentado sobre o astrolábio de bronze, aguardava-me o gato místico §bSenhor Cummins§0, enquanto o velho morcego §8Juninho§0 dormia pendurado de cabeça para baixo na viga.",
                        "'Até tu me olhas com essa cara de pena, felino?', resmunguei, sentando-me no degrau de pedra. 'Este castelo está enfeitiçado! Minha boca fala coisas que não pensei e meu corpo lembra de dores que não vivi!'",
                        "Em resposta, §bSenhor Cummins§0 desceu silenciosamente do astrolábio e, usando a pata dianteira, empurrou pelo chão um grande caco de espelho quebrado até encostá-lo na ponta da minha bota.",
                        "O gato miou duas vezes, batendo a unha no vidro prateado e olhando fixamente para o meu rosto, como se implorasse para que eu abaixasse a cabeça e encarasse o que havia no cristal.",
                        "Recusei-me a olhar: 'Não cairei em feitiços de hipnose óptica, bichano!', declarei, virando o caco de espelho para baixo com a bainha da espada antes de descer as escadas."
                }));

        // LIVRO 16 (Volume XLIII)
        list.add(new FutureChapterSpec(16, 43, "XVI", "XLIII",
                "Ato II: A Sombra no Corredor",
                "O Sonambulismo Macabro",
                "O terror de despertar em pé escrevendo cartas de amor e saudade para a corte.",
                new String[]{
                        "Na décima sexta noite, o terror deixou de ser externo e invadiu a minha própria carne: não acordei na poltrona da antecâmara, mas em pé, no meio do salão de armas, segurando uma pena gotejante!",
                        "Sobre o aparador à minha frente, havia três cartas recém-escritas em tinta carmesim, dedicadas a §9Lorde Asminn§0, §6ArzanLion§0, §dLady Kikkawa§0, §dDama Luwuana§0 e §2Azarasquel§0, implorando que voltassem para casa porque 'a eternidade sem vocês é um túmulo frio'.",
                        "Comparei a letra daquelas cartas com os meus próprios relatórios de Inquisidor: cada curva, cada traço, cada gota de tinta era rigorosamente a minha própria caligrafia!",
                        "Deixei a pena cair como se fosse uma víbora em brasa e recuei até bater as costas na armadura da parede, sentindo o ar faltar nos pulmões.",
                        "'Possessão!', gritei para o salão vazio. 'O demônio §4Jodevil§0 não se esconde nos corredores — ele é um espírito parasita que invade o meu corpo enquanto durmo e usa a minha própria mão para escrever suas lamentações!'",
                        "Essa era a única explicação lógica: durante o dia ou nos meus apagões, o espírito do Conde assumia o controle da minha carcaça!"
                }));

        // LIVRO 17 (Volume XLIV)
        list.add(new FutureChapterSpec(17, 44, "XVII", "XLIV",
                "Ato II: A Sombra no Corredor",
                "O Interrogatório do Gato",
                "Onde Valerius prende os animais do castelo para arrancar a confissão do complô.",
                new String[]{
                        "Convencido de que era vítima de possessão noturna, tomei uma medida digna dos manuais da Inquisição na décima sétima noite: fechei a porta da biblioteca e coloquei §bSenhor Cummins§0 e o morcego §8Juninho§0 sentados diante de uma vela acesa para um interrogatório formal.",
                        "'Confessem, cúmplices das trevas!', bradei, andando de um lado para o outro com o dedo em riste para o gato e o morcego. 'Vocês dois servem ao §4Conde Jodevil§0 há décadas! Por que então vivem atrás de mim o tempo todo?!'",
                        "Apontei a acusação suprema para §bSenhor Cummins§0: 'Ontem à noite, quando tirei as botas, tu trouxeste um par de chinelos de veludo bordados com a letra J e os colocaste nos meus pés! E tu, §8Juninho§0, pousaste no meu chapéu de palha para tirar uma soneca!'",
                        "O morcego abriu um olho preguiçoso, bocejou e voltou a dormir; já §bSenhor Cummins§0 apenas subiu no meu colo, amassou meu casaco com as patas e começou a ronronar alto.",
                        "'Estão vendo?!', exclamei, embora não houvesse ninguém na sala além de nós três. 'Os animais sentem o cheiro ectoplásmico do Conde impregnado no meu corpo! Eles acham que eu sou o dono deles!'",
                        "Havia apenas um jeito de impedir que o espírito de §4Jodevil§0 assumisse o controle do meu corpo na noite seguinte: construir uma jaula de contenção perfeita."
                }));

        // LIVRO 18 (Volume XLV)
        list.add(new FutureChapterSpec(18, 45, "XVIII", "XLV",
                "Ato II: A Sombra no Corredor",
                "A Jaula de Ferro",
                "No qual o Inquisidor engole a chave e amarra fios de prata no próprio corpo.",
                new String[]{
                        "Na décima oitava noite, preparei a armadilha definitiva contra a possessão: tranquei a porta de carvalho por dentro com três voltas, fechei as janelas com barras de ferro e engoli a chave pesada com um gole de água.",
                        "Não satisfeito, amarrei meus próprios tornozelos ao pé da poltrona com fios de prata trançada da sala de §2Azarasquel§0 e espalhei alho ao redor do tapete: se o espírito de §4Jodevil§0 tentasse mover minhas pernas durante o sono, a prata o queimaria vivo!",
                        "Adormeci com a espada cruzada sobre o peito, certo de que vencera a batalha espiritual.",
                        "Quando despertei no crepúsculo seguinte, porém, senti uma coceira infernal nos tornozelos: os fios de prata tinham sido cuidadosamente desamarrados, minha garganta arranhava e a chave que eu engolira estava limpa em cima da mesa!",
                        "Corri até uma bacia d'água para lavar o rosto suado e recuei com um grito de pavor ao ver o reflexo turvo na água:",
                        "Alguém havia usado o meu próprio dedo sujo de tinta carmesim para escrever, de trás para a frente na minha própria testa, uma única palavra: §4§lCOVARDE§0!"
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
                "O combate feroz de espadas no corredor das muralhas sob os relâmpagos.",
                new String[]{
                        "Limpando a testa com fúria homicida, chutei a porta e corri para a galeria fechada das muralhas leste, jurando que naquela décima nona noite um de nós dois morreria pelo aço.",
                        "Foi então que o vi! No fundo do corredor escuro, iluminado pelos relâmpagos da tempestade, erguia-se finalmente o vulto pálido de capa negra, olhos febris e um chapéu de palha torto na cabeça!",
                        "'Morre, §4Conde Jodevil§0!', rugi, sacando minha espada — mas no mesmo milésimo de segundo o vampiro sacou a lâmina dele na exata mesma postura de guarda!",
                        "Avancei num ataque furioso, desferindo a sequência secreta de estocadas que fora usada para defender a cidadela no antigo §4Eclipse de Sangue§0 ao lado de §2Azarasquel§0, §2Reznozada§0 e §6Javidizineo§0.",
                        "Para meu espanto, o monstro conhecia cada movimento da minha alma: quando eu girava à esquerda, ele girava; quando eu fintava por baixo, a espada dele subia no mesmo compasso, como se lutássemos com um único cérebro!",
                        "Reunindo todas as minhas forças, saltei num bote mortal direto no peito do Conde — e um estrondo ensurdecedor de vidro partido cobriu-me de cacos: não havia ninguém lá; eu acabara de estilhaçar a grande janela de cristal espelhado do fundo do corredor, lutando contra o meu próprio reflexo na tempestade!"
                }));

        // LIVRO 20 (Volume XLVII)
        list.add(new FutureChapterSpec(20, 47, "XX", "XLVII",
                "Ato III: A Hora do Julgamento",
                "A Fuga Impossível",
                "Onde o Inquisidor tenta abandonar o castelo e é barrado por suas próprias ordens.",
                new String[]{
                        "Sangrando pelos pequenos cortes do vidro estilhaçado, fui tomado por um terror que nenhum inquisidor jamais admitiria: a loucura deste castelo estava devorando minha razão, e se eu ficasse mais uma noite, acabaria matando a mim mesmo!",
                        "'Que o diabo carregue §4Jodevil§0!', arfei, guardando os diários na bolsa e descendo as escadas de quatro em quatro degraus rumo ao portão principal para fugir daquela fortaleza maldita.",
                        "Quando cheguei ao pátio da ponte levadiça, porém, encontrei as correntes recolhidas e presas por um cadeado gigantesco cujo ferrolho fora soldado pelo lado de dentro!",
                        "Mais do que isso: sentado num caixote bem na frente do portão, apoiado no §bMjölnir que não volta§0, estava o implacável §6Jacinto§0.",
                        "'Abre este portão agora, §6Jacinto§0!', gritei com a voz trêmula. 'Estou abandonando a missão! Preciso sair deste hospício antes que o vampiro destrua minha mente!'",
                        "§6Jacinto§0 coçou o queixo e respondeu com uma calma aterradora: §o'Negativo, patrão. Há vinte dias o senhor mesmo engoliu a chave da ponte e me deu ordem expressa de quebrar suas duas pernas se o senhor tentasse fugir antes de terminar os vinte e sete livros!'§0"
                }));

        // LIVRO 21 (Volume XLVIII)
        list.add(new FutureChapterSpec(21, 48, "XXI", "XLVIII",
                "Ato III: A Hora do Julgamento",
                "Emboscada Solar no Corredor",
                "Uma manobra de vida ou morte atrás do vaso seco quando a aurora rasga a cortina.",
                new String[]{
                        "Impedido por §6Jacinto§0 de sair pelo portão, tentei escapar na manhã do vigésimo primeiro dia escalando a janela lateral da galeria das tapeçarias.",
                        "Puxei a corda da cortina pesada para abrir passagem, mas o tecido podre rasgou-se de alto a baixo — e uma torrente brutal de sol matinal inundou o corredor inteiro!",
                        "O impacto da luz foi como cair dentro de uma fornalha: meu casaco começou a fumegar instantaneamente, o cheiro de toucinho frito subiu da minha bota e senti as garras da §4Centésima Centelha§0 prestes a explodir meu coração em cinzas!",
                        "Soltei um grito agudo completamente indigno da minha patente e atirei-me de bruços no único palmo de sombra que restava no corredor: atrás de um pequeno vaso com um arbusto seco!",
                        "Encolhido como um tatu atrás do vaso e usando o chapéu de palha como escudo tático para proteger o nariz do sol, rastejei milímetro a milímetro empurrando o vaso à minha frente pelo tapete.",
                        "Levei quarenta minutos rastejando atrás daquela planta morta até alcançar a porta escura da copa; quando finalmente caí na sombra, abracei o vaso seco jurando levá-lo comigo para os aposentos reais como amuleto de guerra!"
                }));

        // LIVRO 22 (Volume XLIX)
        list.add(new FutureChapterSpec(22, 49, "XXII", "XLIX",
                "Ato III: A Hora do Julgamento",
                "O Alquimista Fantasma",
                "A descoberta do elixir recém-preparado para a vigésima sétima noite.",
                new String[]{
                        "Caído no chão escuro da copa real após o resgate heroico do vaso seco, ouvi o gotejar lento de um alambique atrás do armário de cristais.",
                        "Forcei a porta secreta com o ombro e descobri um pequeno laboratório improvisado onde uma espiriteira a óleo ainda mantinha morno um balão de vidro borbulhante!",
                        "Dentro do frasco brilhava um elixir cor-de-rosa muito mais concentrado do que o das garrafas da despensa, exalando vapores densos de papoula, beterraba, vinho velho e ervas de esquecimento.",
                        "Ao lado do alambique, preso com uma adaga na madeira, havia um rótulo escrito há pouquíssimos dias: §o'Dose Única para a Meia-Noite do 27º Dia — Receita de ArzanLion e AsterZZZz contra a Dor da Eternidade. Levar à cabeceira da Torre Real.'§0",
                        "Meu coração disparou de triunfo: ali estava o segredo da imortalidade de §4Conde Jodevil§0! A cada vinte e sete dias, o monstro precisava beber aquela poção na Torre Real para renovar seu pacto sombrio!",
                        "Peguei o cálice preparado na bandeja: se eu invadisse a Torre Real no topo do castelo antes da meia-noite do vigésimo sétimo dia, pegaria o Conde no exato momento do seu ritual!"
                }));

        // LIVRO 23 (Volume L)
        list.add(new FutureChapterSpec(23, 50, "XXIII", "L",
                "Ato III: A Hora do Julgamento",
                "A Invasão da Torre Real",
                "Onde Valerius usa um aríete contra uma porta que estava destrancada.",
                new String[]{
                        "Na vigésima terceira noite, subi a grande escadaria helicoidal até o ponto mais alto do Castelo sobre o Mar, carregando minha espada, o vaso com o arbusto seco de escudo e um pesado banco de carvalho como aríete.",
                        "Diante da porta dupla dos aposentos reais, respirei fundo: 'Prepara-te para o inferno, §4Jodevil§0!', bradei, tomando distância no corredor para arrombar a fortaleza do monstro.",
                        "Corri com toda a velocidade das minhas pernas gritando um brado de guerra e lancei o banco contra os batentes — mas a porta não tinha tranca nenhuma e abriu-se sozinha com o vento um segundo antes do impacto!",
                        "Atravessei o vão aberto voando igual a um projétil desgovernado, tropecei na bainha da minha própria capa, rolei por cima do tapete persa e bati com a testa direto no pé da cama de dossel!",
                        "Fiquei estatelado no piso de pedra por cinco minutos vendo estrelas, enquanto o gato §bSenhor Cummins§0 me olhava de cima do travesseiro com uma expressão de profundo desapontamento profissional.",
                        "Quando finalmente me levantei massageando o galo na testa, olhei ao redor do santuário: o quarto estava silencioso, iluminado por candelabros, e ao lado da cama repousava um imenso Baú Duplo."
                }));

        // LIVRO 24 (Volume LI)
        list.add(new FutureChapterSpec(24, 51, "XXIV", "LI",
                "Ato III: A Hora do Julgamento",
                "O Cofre de Sangue",
                "As anotações recentes nas margens dos tomos de Asminn e a lágrima ainda fresca.",
                new String[]{
                        "Empunhando a estaca de freixo, aproximei-me do Baú Duplo no piso de pedra ao lado do leito, certo de que encontraria ossos ou ouro amaldiçoado — mas ao erguer a tampa, vi fileiras perfeitas de tomos carmesins.",
                        "Eram os vinte e sete volumes originais das §oCrônicas do Sangue Duvidoso§0, redigidos décadas atrás pelo lendário cronista §9Lorde Asminn§0!",
                        "Puxei os livros com mãos trêmulas, mas não foi o texto antigo que me fez perder o fôlego: foram as dezenas de notas desesperadas rabiscadas nas margens das páginas com tinta fresca!",
                        "No capítulo das roseiras, alguém escrevera ontem mesmo: §o'Que saudade da risada de Kikkawa, Pipira e Luwuana... o jardim ficou mudo sem elas.'§0 Na página da adega, lia-se: §o'ArzanLion e AsterZZZz me deram um vampirismo de meia-tigela, mas eu daria tudo para ouvir aquele espirro outra vez.'§0",
                        "E no vigésimo sétimo tomo de §9Asminn§0, sobre a frase em que o cronista prometia jamais abandonar o amigo, havia uma gota de lágrima que acabara de cair — ainda molhada sobre o pergaminho!",
                        "Olhei para o meu próprio rosto e toquei minha face: minhas bochechas estavam secas, mas o choro silencioso parecia ecoar dentro da minha própria caixa torácica."
                }));

        // LIVRO 25 (Volume LII)
        list.add(new FutureChapterSpec(25, 52, "XXV", "LII",
                "Ato III: A Hora do Julgamento",
                "O Cerco Final",
                "No qual o Inquisidor descobre o único esconderijo possível dentro do quarto real.",
                new String[]{
                        "Fechei o Baú Duplo com um estrondo na vigésima quinta noite e passei a tranca interna na porta do quarto: se a lágrima sobre o livro de §9Asminn§0 estava fresca, §4Conde Jodevil§0 nunca saíra deste aposento!",
                        "Vasculhei debaixo do leito com a lâmina da espada, golpeei as cortinas da janela — onde posicionei meu fiel vaso de arbusto seco por precaução contra o sol — e examinei o suporte de poções na cabeceira, onde o cálice cor-de-rosa já fumegava esperando a meia-noite.",
                        "Não havia alçapões no piso de pedra nem passagens no teto, mas meus olhos cravaram-se no único lugar da parede oeste onde um homem alto poderia esconder-se em pé.",
                        "Ali, entre dois candelabros acesos, havia uma moldura vertical inteiramente coberta por um longo estandarte de veludo negro, diante do qual o gato §bSenhor Cummins§0 montava guarda imóvel.",
                        "No alto do dossel da cama, o morcego §8Juninho§0 abriu as asas e guinchou baixinho, olhando diretamente para o pano negro.",
                        "Aproximei-me pé ante pé, sentindo o cheiro de vinho azedo e beterraba ficar mais forte a cada passo: o Conde estava parado exatamente atrás daquele véu escuro!"
                }));

        // LIVRO 26 (Volume LIII)
        list.add(new FutureChapterSpec(26, 53, "XXVI", "LIII",
                "Ato III: A Hora do Julgamento",
                "Doze Badaladas",
                "A contagem regressiva para a meia-noite diante do véu negro.",
                new String[]{
                        "Faltavam segundos para a meia-noite da última noite; posicionei-me a um passo do estandarte negro na parede, com a estaca erguida na mão direita e os dedos manchados de carmesim da mão esquerda agarrando a borda do tecido.",
                        "Na torre acima de nós, o grande sino de bronze começou a soar a meia-noite: §oBADALADA UM!§0 — e um choque lancinante atravessou meu crânio com a voz de §9Asminn§0 perguntando se eu queria confessar minha história.",
                        "§oBADALADA QUATRO!§0 — senti na garganta o gosto quente do caldeirão de §6ArzanLion§0, ouvi o ronco de §5AsterZZZz§0 e o latido alegre de §eVitória§0 na adega!",
                        "§oBADALADA OITO!§0 — minhas costas arderam com os espinhos das roseiras sob o riso de §dLady Kikkawa§0 e §dDama Luwuana§0, e meu pescoço queimou com a colher de prata de §2Azarasquel§0, §2Reznozada§0 e §6Javidizineo§0!",
                        "§oBADALADA ONZE!§0 — ouvi §8Corvo§0 gritando as apostas no telhado e minha própria voz implorando §o'por favorzinho'§0 ao imortal §6Jacinto§0 pelo §bMjölnir§0!",
                        "§oBADALADA DOZE!§0 — 'Acabou a tua farsa, §4Jodevil§0!', urrei com a alma partida ao meio e, num golpe violento, arranquei o pano negro da parede para cravar a estaca!"
                }));

        // LIVRO 27 (Volume LIV — O PLOT TWIST SUPREMO)
        list.add(new FutureChapterSpec(27, 54, "XXVII", "LIV",
                "Ato III: A Hora do Julgamento",
                "A Sentença da Meia-Noite",
                "Onde a lâmina do inquisidor encontra finalmente o pescoço do Conde.",
                new String[]{
                        "O veludo negro caiu aos meus pés, mas não havia nicho secreto nem monstro escondido atrás dele: era apenas um grande espelho de cristal. E porque minha maldição pífia de beterraba e vinagre jamais teve poder sequer para apagar minha imagem, o homem que me encarou de volta no vidro — pálido, de estaca erguida, chapéu de palha torto e olhos banhados em lágrimas — §4era eu mesmo, o Conde Jodevil!§0",
                        "A estaca escapou dos meus dedos e rolou pelo chão de pedra enquanto a mentira do 'Inquisidor Valerius' desmoronava num único soluço: nunca houve caçador algum! Anos após o fim do primeiro ciclo, quando o tempo levou meus amigos e deixou este castelo imenso mergulhado num silêncio insuportável, a saudade dilacerante de §9Asminn§0 e de toda a nossa corte partiu o meu coração imortal.",
                        "Lembrei-me de tudo com clareza devastadora: incapaz de suportar a eternidade sozinho, desci à adega de §6ArzanLion§0 e preparei o §oVinho do Esquecimento§0; mas, como usei o mesmo grimório borrado pela baba de §5AsterZZZz§0, o feitiço condenou-me a um ciclo de vinte e sete noites!",
                        "A cada ciclo, minha mente apaga quem sou e inventa um inquisidor implacável apenas para que eu não morra de solidão — fazendo-me perseguir meus próprios desastres pelo castelo para reviver, pista por pista, o calor das risadas de §dKikkawa§0, §dLuwuana§0, §2Azarasquel§0, §2Reznozada§0, §8Corvo§0, §6ArzanLion§0 e do meu irmão §9Asminn§0!",
                        "Caí de joelhos abraçando §bSenhor Cummins§0 enquanto §8Juninho§0 pousava em meu ombro, chorando e rindo da minha própria comédia trágica; mas a dor de lembrar que estou sozinho era esmagadora demais para aguentar acordado, então caminhei até o suporte na cabeceira, ergui o cálice cor-de-rosa em brinde aos amigos eternos e bebi até a última gota: §o'Até amanhã, minha querida corte...'§0",
                        "Minha visão escureceu, deixei o chapéu de palha cair ao lado da poltrona, esbarrei no tinteiro carmesim sujando as pontas dos dedos e apaguei — até que o primeiro raio da manhã atravessou a fresta da cortina e puxei um tomo em branco para escrever: §o'Despertei com um sobressalto, o coração lento batendo como um sino submerso, enquanto um chiado agudo — tsssss — subia da ponta da minha bota...'§0"
                }));
    }
}
