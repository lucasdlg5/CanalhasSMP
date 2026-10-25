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
 * Inspirado na espinha dorsal de "O Vampiro Lestat" (Anne Rice): o implacável "Inquisidor Valerius"
 * infiltra-se no Castelo sobre o Mar para sabotar e impedir o "Grande Concerto da 27ª Lua"
 * anunciado por Conde Jodevil, travando uma guerra de gato e rato nos bastidores durante 27 noites
 * até a revelação suprema diante do espelho à meia-noite no Livro 27.
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
    // ATO I — LIVROS 1 A 9: O CERCO SOLITÁRIO (A GUERRA DE BASTIDORES)
    // =========================================================================
    private static void addAct1Chapters(List<FutureChapterSpec> list) {
        // LIVRO 1 (Volume XXVIII)
        list.add(new FutureChapterSpec(1, 28, "I", "XXVIII",
                "Ato I: O Cerco Solitário",
                "Pó Branco e Carmesim",
                "Onde o Inquisidor Valerius desperta na cripta para impedir o Concerto do Século.",
                new String[]{
                        "Despertei sobre a laje fria de pedra, coberto pelo pó branco das catacumbas, enquanto um chiado agudo — §o'tsssss'§0 — subia da ponta da minha bota...",
                        "Recuei o pé imediatamente da réstia de sol que descia pela grade da cripta inferior e sacudi a poeira calcária dos meus punhos de renda e do meu veludo negro, erguendo-me da pedra como uma estátua antiga que voltasse à vida.",
                        "Meu nome é §9Valerius§0, Inquisidor da Ordem Noturna. Infiltrei-me neste Castelo sobre o Mar após interceptar o édito dourado em que o infame §4Conde Jodevil§0 anunciava seu 'Grande Concerto da Vigésima Sétima Lua' para reunir sua antiga corte à meia-noite.",
                        "Ao forçar a grade subterrânea de madrugada, devo ter acionado alguma armadilha de vapor alquímico do Conde, pois acordei com a mente turva, uma fome estética inexplicável por música e luz de velas, e um gosto medonho de vinagre barato e beterraba na boca!",
                        "Mais curioso ainda: sobre a laje ao meu lado havia um tinteiro aberto que manchara as pontas dos meus dedos de carmesim, além de um ridículo chapéu de palha desfiado.",
                        "Ajustei a espada e a estaca no cinturão: tenho exatamente vinte e sete noites para sabotar este espetáculo profano e cravar ferro no coração do Príncipe Rebelde antes que ele toque sua primeira nota!"
                }));

        // LIVRO 2 (Volume XXIX)
        list.add(new FutureChapterSpec(2, 29, "II", "XXIX",
                "Ato I: O Cerco Solitário",
                "Convites para o Silêncio",
                "A descoberta do banquete de gala preparado para os antigos senhores da corte.",
                new String[]{
                        "Assim que a noite caiu sobre as ondas, subi da cripta com a espada em riste — levando aquele chapéu de palha preso ao cinto como troféu da primeira armadilha — e invadi o grande salão de banquetes.",
                        "Estaquei na soleira diante do luxo extravagante: cem velas ardiam nos lustres de prata, e a longa mesa fora posta com porcelanas reais para a recepção de gala do vigésimo sétimo dia!",
                        "Diante de cada taça de cristal brilhava um convite caligrafado em ouro com os nomes de §9Lorde Asminn§0, §6ArzanLion§0, §5AsterZZZz§0, §dLady Kikkawa§0 (e §bPipira§0), §dDama Luwuana§0, §2Azarasquel§0 (e §6Javidizineo§0), §2Reznozada§0 e §8Corvo§0.",
                        "Na cabeceira do anfitrião, uma sopeira de prata ainda fumegava com um caldo cor-de-rosa de beterraba e vinho azedo; ao ler aqueles nomes nos cartões dourados, porém, senti um aperto tão fundo e apaixonado no peito que quase deixei cair a espada.",
                        "'Não haverá banquete nenhum para monstros!', rosnei para sufocar aquela estranha comoção, chutando as cadeiras, virando as taças sobre a toalha bordada e rasgando o convite da cabeceira.",
                        "No mesmo instante, ouvi o farfalhar apressado de uma capa atrás da cortina rubra da galeria; saltei sobre a mesa com a lâmina erguida, mas encontrei apenas o corredor vazio e um morcego gordo fugindo pela janela."
                }));

        // LIVRO 3 (Volume XXX)
        list.add(new FutureChapterSpec(3, 30, "III", "XXX",
                "Ato I: O Cerco Solitário",
                "A Maldição da Cisterna",
                "Onde o Inquisidor descobre a praga heliofóbica que o prende aos horários da noite.",
                new String[]{
                        "Na manhã do terceiro dia, decidi aproveitar o horário de sono do vampiro para cruzar o pátio central sob o sol do meio-dia e trancar as portas externas do teatro.",
                        "Porém, assim que pisei fora da sombra da colunata, o sol atingiu minha pele como óleo fervente! Minha bota começou a chiar alto, um cheiro humilhante de toucinho na frigideira subiu da minha capa e senti meu sangue evaporar gota a gota.",
                        "Pior do que a dor foi o terror súbito da §4Centésima Centelha§0 — a sensação apavorante de que, a cada minuto sob o sol aberto, uma fagulha invisível corria o risco de incendiar meu coração e reduzir-me a um montinho de cinzas na mesma hora!",
                        "Mergulhei de volta para a sombra da galeria abafando a fumaça do casaco no chão frio, com o peito arfando de fúria e espanto.",
                        "'Astuto e miserável §4Jodevil§0!', esbravejei, olhando para a cisterna de pedra do pátio onde eu bebera água na primeira madrugada. 'O demônio batizou a água da fortaleza com feitiçaria heliofóbica para que nenhum caçador possa caminhar sob o sol!'",
                        "O plano do Conde ficava claro: ao contaminar minha sede, ele me forçava a enfrentá-lo exclusivamente no seu próprio território — a meia-noite!"
                }));

        // LIVRO 4 (Volume XXXI)
        list.add(new FutureChapterSpec(4, 31, "IV", "XXXI",
                "Ato I: O Cerco Solitário",
                "O Chapéu do Camarim",
                "Os espelhos estilhaçados do camarim e a inexplicável defesa do chapéu de palha.",
                new String[]{
                        "Na quarta noite, arrombei uma sala anexa ao salão nobre que servia de camarim para o tal Concerto: havia casacos de veludo dignos da corte de Versalhes, partituras espalhadas e dezenas de molduras nas paredes.",
                        "Curiosamente, todos os espelhos daquele camarim haviam sido quebrados a golpes de candelabro, restando apenas as tábuas de madeira no fundo — sinal evidente da fúria de um vampiro que odeia encarar a própria maldição no vidro!",
                        "Decidido a dar o troco, puxei do cinto aquele chapéu de palha surrado que encontrara na cripta e aproximei-o do braseiro aceso para reduzi-lo a cinzas no meio do camarim do Conde.",
                        "Mas, quando a primeira língua de fogo tocou a aba de palha, senti uma fisgada tão aguda no meio do peito que minha própria mão esquerda puxou o chapéu de volta num reflexo desesperado!",
                        "Dentro da copa de palha, li a inscrição queimada no couro: §o'Sem reembolso — Cortesia de ArzanLion para o Príncipe do Sol'§0 — e imediatamente minha boca começou a resmungar sozinha contra 'trinta e sete cláusulas abusivas e um queijo curado perdido'!",
                        "Sacudi a cabeça, assustado comigo mesmo, quando um som hipnótico subiu do subsolo: alguém acabara de iniciar um solo de violino nas catacumbas!"
                }));

        // LIVRO 5 (Volume XXXII)
        list.add(new FutureChapterSpec(5, 32, "V", "XXXII",
                "Ato I: O Cerco Solitário",
                "O Violino de Nicolas",
                "Onde uma sonata digna dos antigos imortais termina num guincho de corda partida.",
                new String[]{
                        "Corri escada abaixo seguindo a melodia daquele violino: era uma música de beleza feroz e apaixonada, idêntica às sonatas que os antigos vampiros tocavam em Paris para desafiar a morte e celebrar a noite.",
                        "'Peguei-te no meio do ensaio, §4Jodevil§0!', pensei, preparando a estaca ao dobrar o corredor da cripta da capela.",
                        "Mas, no auge do movimento mais dramático da peça, o arco escorregou sobre as cordas num guincho pavoroso de gato escaldado (§o'CRÉÉÉC!'§0), seguido pelo estalo de uma corda arrebentada e um resmungo furioso: §o'Maldito breu úmido, lá se foi a corda Ré!'§0",
                        "Chutei a porta da cripta um segundo depois, mas o músico já havia escapado pelas sombras; sobre o atril de ferro, um magnífico violino de madeira escura ainda oscilava com a corda partida pendurada.",
                        "Ao lado do atril, aberto sobre o pedestal, estava o códice proibido da §4Rainha dos Condenados§0, cheio de notas em tinta carmesim onde o Conde se gabava de que seu concerto superaria a própria Mãe dos Imortais.",
                        "Confisquei o códice e desafinei as três cordas restantes do violino com um sorriso cruel: 'Quero ver como tocarás tua sonata agora, charlatão!'"
                }));

        // LIVRO 6 (Volume XXXIII)
        list.add(new FutureChapterSpec(6, 33, "VI", "XXXIII",
                "Ato I: O Cerco Solitário",
                "As Margens do Rebelde",
                "A invasão ao gabinete de Lorde Asminn e a carta aberta do Conde ao seu cronista.",
                new String[]{
                        "Seguindo o cheiro de cera quente, subi na sexta noite até o gabinete de engenharia e arquivos de §9Lorde Asminn§0, cuja porta encontrei entreaberta.",
                        "Sobre a escrivaninha de latão, uma xícara de chá ainda soltava vapor e a pena de ganso repousava molhada sobre o discurso de abertura do Concerto da Vigésima Sétima Lua!",
                        "Li as linhas recém-escritas em tinta carmesim com o coração aos saltos: §o'Meu querido Asminn, quando escreveste nosso primeiro livro, retrataste-me com adorável ironia como um conde que cai em roseiras e implora por martelos.'§0",
                        "§o'Mas, tal qual o Príncipe Rebelde das crônicas antigas, recuso-me a ser lembrado apenas pelas minhas quedas! Neste Concerto da 27ª Lua, quando tu e toda a corte cruzardes aquele portão, verão o mais belo e indomável senhor da meia-noite!'§0",
                        "A arrogância — e ao mesmo tempo a ternura desesperada daquelas palavras — deixou-me paralisado diante da bancada.",
                        "Para quebrar o encanto daquela prosa, derramei areia sobre o discurso do Conde e tranquei o gabinete de §9Asminn§0 com duas voltas de chave, guardando a chave no meu próprio bolso."
                }));

        // LIVRO 7 (Volume XXXIV)
        list.add(new FutureChapterSpec(7, 34, "VII", "XXXIV",
                "Ato I: O Cerco Solitário",
                "Safra de Sangue Real",
                "Onde Valerius sabota a adega do banquete e descobre o ingrediente secreto do Conde.",
                new String[]{
                        "Na sétima noite, decidi atacar a logística da festa: desci à despensa real para destruir as reservas de sangue humano que §4Conde Jodevil§0 certamente preparara para brindar na noite do concerto.",
                        "Encontrei três prateleiras repletas de garrafas de cristal lacradas com fita dourada e o rótulo pomposo: §o'Reserva Privada do Conde — Colheita Carmesim para a Gala da 27ª Lua'§0.",
                        "Puxei a rolha da primeira garrafa e, seguindo o protocolo da Inquisição para análise de fluidos arcanos, provei uma gota na ponta da língua.",
                        "Cuspi o gole inteiro na parede, tossindo e lacrimejando de desgosto: não havia sangue nenhum ali! Era uma mistura pavorosa de vinho tinto barato de taverna, caldo grosso de beterraba cozida, cebola roxa e alho velho!",
                        "'Por todos os abismos!', exclamei limpando a boca com o lenço de renda. 'Esse monstro pretende servir sopa de beterraba azeda em taças de cristal para a nobreza inteira do reino?!'",
                        "Por puro dever sanitário e inquisitorial, varri a prateleira de cima com a bainha da espada, espatifando seis garrafas no piso de pedra antes de deixar a despensa."
                }));

        // LIVRO 8 (Volume XXXV)
        list.add(new FutureChapterSpec(8, 35, "VIII", "XXXV",
                "Ato I: O Cerco Solitário",
                "O Vândalo Noturno",
                "O choque ao descobrir que todas as sabotagens foram desfeitas durante o dia.",
                new String[]{
                        "Na oitava noite, subi de volta ao salão de banquetes esperando encontrar o caos que eu deixara — mas estanquei na porta com o sangue gelado nas veias.",
                        "Todas as doze cadeiras que eu havia chutado no segundo dia estavam perfeitamente alinhadas ao redor da mesa! As taças de cristal brilhavam limpas, os convites dourados haviam sido reescritos e, lá embaixo na despensa, os cacos de vidro tinham sido varridos!",
                        "Mais do que isso: do alto da galeria, ouvi o som do violino escuro — cujas cordas eu mesmo desafinara na cripta — tocando perfeitamente afinado antes de parar ao som dos meus passos!",
                        "No centro da mesa de banquetes, preso a um candelabro de prata, encontrei um bilhete escrito em tinta carmesim perfumada dirigido a mim:",
                        "§o'Caro Inquisidor Valerius, é feio visitar o castelo de um gentil-homem e quebrar-lhe as garrafas e desafinar-lhe o violino. Comporta-te nos bastidores, pois teu lugar na primeira fila já está reservado. — Conde J.'§0",
                        "Amassei o bilhete no punho trêmulo de ódio: o vampiro não apenas desfazia minhas sabotagens enquanto eu dormia, como ousava zombar da minha missão!"
                }));

        // LIVRO 9 (Volume XXXVI)
        list.add(new FutureChapterSpec(9, 36, "IX", "XXXVI",
                "Ato I: O Cerco Solitário",
                "O Cerco na Cinza",
                "Uma emboscada no escuro que revela o molde impossível das botas do inimigo.",
                new String[]{
                        "Se §4Jodevil§0 saía de seu esconderijo todas as madrugadas para arrumar o salão e afinar o violino, na nona noite ele cairia na minha rede: espalhei cinza fina de carvão por todas as entradas do salão nobre e apaguei as velas.",
                        "Fiquei de tocaia atrás da armadura real até que, perto das três da madrugada, ouvi o estalo inconfundível de botas pisando na cinza do corredor leste!",
                        "Saí do esconderijo num salto felino com a lanterna aberta e a espada em riste, correndo atrás do som dos passos que dobravam a grande coluna de mármore do vestíbulo.",
                        "'Não escaparás desta vez, anfitrião das trevas!', gritei, contornando o pilar em velocidade máxima para cravar a lâmina nas costas do vulto.",
                        "Completei a volta ao redor da coluna — mas o vestíbulo estava absolutamente deserto!",
                        "Baixei o facho de luz para o chão de pedra e recuei um passo, sentindo um arrepio percorrer minha nuca: as pegadas do vampiro na cinza davam a volta no pilar e tinham o exato tamanho, o salto esquerdo gasto e o bico direito chamuscado das minhas próprias botas!"
                }));
    }

    // =========================================================================
    // ATO II — LIVROS 10 A 18: A SOMBRA NO CORREDOR (SABOTAGENS INÉDITAS)
    // =========================================================================
    private static void addAct2Chapters(List<FutureChapterSpec> list) {
        // LIVRO 10 (Volume XXXVII)
        list.add(new FutureChapterSpec(10, 37, "X", "XXXVII",
                "Ato II: A Sombra no Corredor",
                "O Teatro dos Vampiros",
                "Onde Valerius tenta serrar as cordas do palco de Kikkawa e Luwuana e derruba o lustre.",
                new String[]{
                        "Convencido de que o Conde usava botas idênticas às minhas para confundir meu rastreamento, subi na décima noite ao terraço de inverno de §dLady Kikkawa§0, §dPipira§0 e §dDama Luwuana§0.",
                        "O vampiro transformara a varanda das damas num suntuoso §oTeatro dos Vampiros§0 ao ar livre: havia bambolinas de veludo rubro, cenários pintados à mão e um grande lustre de madeira dourada suspenso por cordas sobre o palco do concerto!",
                        "No camarote de honra, vi almofadas de seda bordadas para §dKikkawa§0 e §dLuwuana§0 e até um aquário de cristal preparado para o peixe §bPipira§0 assistir ao espetáculo.",
                        "'Vamos ver se haverá concerto sem palco!', murmurei com um sorriso frio, subindo na viga lateral com meu punhal serrilhado para cortar a corda mestre que sustentava a cortina principal.",
                        "No escuro da ventania, porém, serrei o cabo errado: ouvi um estalo seco acima da minha cabeça e o pesado lustre cenográfico de madeira despencou direto sobre o meu chapéu de palha, arremessando-me da viga direto nos arbustos de hortênsias!",
                        "Saí do meio das folhas com um galo na testa e o lustre enfiado nos ombros feito um colar de pelourinho, jurando que §4Jodevil§0 havia invertido as roldanas do palco como armadilha!"
                }));

        // LIVRO 11 (Volume XXXVIII)
        list.add(new FutureChapterSpec(11, 38, "XI", "XXXVIII",
                "Ato II: A Sombra no Corredor",
                "O Fogo de Magnus",
                "A sabotagem dos fogos de artifício cor-de-rosa na fornalha de ArzanLion.",
                new String[]{
                        "Mancando do tombo no teatro, desci na décima primeira noite à antiga fornalha subterrânea de §6ArzanLion§0 — uma câmara digna da torre de Magnus, guardada pelo retrato da fiel cadelinha §eVitória§0 e pelo grimório borrado pela baba de §5AsterZZZz§0.",
                        "Sobre a bancada de pedra, descobri a mais nova extravagância que o Conde preparara para o encerramento do seu concerto: doze morteiros de 'Fogos de Artifício Alquímicos Cor-de-Rosa'!",
                        "Segundo a fórmula anotada ao lado, os fogos levavam pólvora de magnésio, essência de beterraba seca e pimenta-rosa moída para explodir no céu na vigésima sétima meia-noite.",
                        "Decidido a inutilizar os explosivos, abri o barril principal para despejar água — mas uma lufada de vento vinda da chaminé soprou a pimenta-rosa fina direto nas minhas fossas nasais!",
                        "Tentei tapar o nariz com as duas mãos, mas soltei um espirro tão violento que minha própria lanterna virou sobre o pavio dos morteiros: §o'ATCHIM... BUM!'§0",
                        "Os doze fogos cor-de-rosa dispararam dentro da adega fechada num bombardeio ensurdecedor; saí correndo escada acima com a capa fumegando, as sobrancelhas chamuscadas e um apito agudo nos dois ouvidos!"
                }));

        // LIVRO 12 (Volume XXXIX)
        list.add(new FutureChapterSpec(12, 39, "XII", "XXXIX",
                "Ato II: A Sombra no Corredor",
                "A Armadilha de Prata",
                "No qual Valerius monta uma ratoeira com a colher de Azarasquel e acerta o próprio queixo.",
                new String[]{
                        "Com os ouvidos ainda zunindo da explosão na adega, invadi na décima segunda noite o arsenal onde §2Azarasquel§0 e §2Reznozada§0 guardavam suas relíquias sob o estandarte do leão dourado §6Javidizineo§0.",
                        "Se eu não conseguia pegar §4Conde Jodevil§0 na corrida, usaria a engenharia dos antigos inquisidores: apanhei correntes de ferro, uma mola de besta e a famosa colher de sobremesa de prata de §2Reznozada§0.",
                        "Subi até a porta da sala de música e passei duas horas montando uma ratoeira inquisitorial perfeita acima da maçaneta: assim que o Conde abrisse a porta para pegar seu violino, a mola dispararia a colher de prata consagrada direto no meio de sua testa!",
                        "Orgulhoso da minha criação mecânica, inclinei-me junto ao batente para ajustar o gatilho final com a ponta do dedo.",
                        "A trava escapou com um estalo metálico (§o'TLAC!'§0) e a colher de prata voou na velocidade de um raio, acertando em cheio a ponta do meu próprio queixo!",
                        "Pior que a pancada foi o efeito imediato: em trinta segundos meu queixo e meu pescoço ficaram cobertos por uma urticária vermelha e ardente que me fez passar o resto da noite passando pomada fria e amaldiçoando a prata oxidada deste castelo!"
                }));

        // LIVRO 13 (Volume XL)
        list.add(new FutureChapterSpec(13, 40, "XIII", "XL",
                "Ato II: A Sombra no Corredor",
                "O Fantasma da Bilheteria",
                "Onde o livro de apostas de Corvo no telhado traz o nome do próprio Inquisidor.",
                new String[]{
                        "Coçando o queixo empolado, escalei na décima terceira noite a torre externa até o posto de observação de §8Corvo§0 no telhado da antiga taverna, buscando uma visão aérea das rotas do Conde.",
                        "Dentro da guarita de madeira, encontrei uma caixa cheia de ingressos dourados numerados para o Concerto e o velho livro de apostas de §8Corvo§0 aberto numa página escrita esta semana!",
                        "Aproximei a lanterna e senti o sangue subir-me à cabeça ao ler o título da nova tabela: §o'Bolão Especial de Bastidores — Quantos dias o desastrado Inquisidor Valerius aguenta no castelo antes de queimar o bico da bota na Centésima Centelha?'§0",
                        "'Aquele demônio está anotando cada passo meu do alto das chaminés!', bradei, furioso ao ver que a tabela registrava até o lustre que caíra na minha cabeça no terraço e o meu espirro nos fogos de artifício!",
                        "Enquanto eu arrancava a página do caderno espumando de raiva, o céu a leste clareou sem aviso e o primeiro raio de sol da manhã bateu diretamente na fivela da minha bota sobre as telhas.",
                        "O couro chiou na hora (§o'tsssss!'§0) soltando fumaça preta; com o coração na boca pelo terror da §4Centésima Centelha§0, atirei-me de cabeça pela claraboia de vidro, caindo sobre um monte de cortinas velhas no sótão!"
                }));

        // LIVRO 14 (Volume XLI)
        list.add(new FutureChapterSpec(14, 41, "XIV", "XLI",
                "Ato II: A Sombra no Corredor",
                "O Segurança do Evento",
                "A tentativa frustrada de confiscar o Mjölnir de Jacinto na entrada do salão.",
                new String[]{
                        "Decidido a demolir o palco do concerto a marretadas na décima quarta noite, desci à galeria dos cofres para requisitar a única arma do castelo capaz de pulverizar pedra e madeira num só golpe: o lendário §bMjölnir que não volta§0, guardado pelo servo imortal §6Jacinto§0.",
                        "Encontrei §6Jacinto§0 postado diante do corredor nobre com o martelo divino preso ao cinto e uma faixa no braço onde se lia: §o'Chefe de Segurança e Patrimônio do Concerto'§0.",
                        "'Afasta-te e entrega-me esse martelo em nome da Santa Inquisição, funcionário!', ordenei com voz trovejante. 'Este espetáculo vampírico está oficialmente interditado!'",
                        "Como §6Jacinto§0 nem piscou, joguei uma tocha acesa aos pés dele para mostrar o poder do fogo purificador — mas as labaredas apagaram-se no couro de suas botas e ele respondeu calmamente: §o'Chefia, sem ingresso não entra, e tacar fogo no segurança não dá desconto. O patrão avisou que esse martelo só sai da minha mão se pedirem na conversa com muita humildade e por favorzinho.'§0",
                        "Olhei para os lados para ter certeza de que ninguém me via e, engolindo todo o meu orgulho de Inquisidor apenas para conseguir a arma, resmunguei entre os dentes: 'Está bem... dá-me esse martelo, por favorzinho.'",
                        "§6Jacinto§0 balançou a cabeça com um sorriso burocrático: §o'Negativo, senhor. Faltou emoção no pedido! Continue tentando que uma hora a sorte sorri!'§0 — deixando-me plantado no corredor a espumar de humilhação!"
                }));

        // LIVRO 15 (Volume XLII)
        list.add(new FutureChapterSpec(15, 42, "XV", "XLII",
                "Ato II: A Sombra no Corredor",
                "O Santuário dos Guardados",
                "A câmara secreta das estátuas da corte e a compaixão de Senhor Cummins.",
                new String[]{
                        "Fugindo da teimosia inabalável de §6Jacinto§0, subi na décima quinta noite à rotunda do observatório e abri uma porta de bronze que me revelou o coração oculto do castelo.",
                        "Diante de mim erguia-se um santuário silencioso inspirado n'§oAqueles Que Devem Ser Guardados§0 dos antigos vampiros: sobre pedestais de mármore iluminados por centenas de velas, repousavam bustos esculpidos e retratos solenes de §9Lorde Asminn§0, §6ArzanLion§0, §5AsterZZZz§0, §dLady Kikkawa§0, §dDama Luwuana§0, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0.",
                        "Cada pedestal fora espanado com devoção religiosa naquela mesma tarde, adornado com rosas frescas e partituras dedicadas a cada um dos amigos da antiga era de ouro.",
                        "Ao contemplar a expressão serena do retrato de §9Asminn§0 e o sorriso fanfarrão de §6ArzanLion§0, senti um nó inexplicável apertar minha garganta: §4Conde Jodevil§0 não era apenas um farsante vaidoso; era uma criatura consumida por uma saudade que atravessava as décadas.",
                        "De repente, o gato místico §bSenhor Cummins§0 surgiu detrás do pedestal trazendo o morcego §8Juninho§0 empoleirado em suas costas.",
                        "O gato encostou a cabeça na minha bota e puxou suavemente a barra da minha capa em direção à escada de saída, miando com um olhar tão cheio de pena que parecia implorar para que eu desistisse daquela caçada antes da meia-noite do dia vinte e sete."
                }));

        // LIVRO 16 (Volume XLIII)
        list.add(new FutureChapterSpec(16, 43, "XVI", "XLIII",
                "Ato II: A Sombra no Corredor",
                "O Dueto Impossível",
                "Onde Valerius monta guarda diante do violino e desperta com os dedos marcados.",
                new String[]{
                        "Recompondo minha frieza de caçador na décima sexta noite, decidi usar a paixão musical do Conde como armadilha: levei o violino de madeira escura para o centro do salão de música, tranquei todas as janelas por dentro e sentei-me na poltrona escura de besta apontada para o atril.",
                        "'Se vieres ensaiar tua sonata hoje à noite, §4Jodevil§0, receberás um virote de prata no peito!', jurei no silêncio da sala.",
                        "As horas arrastaram-se pesadas; o som da chuva nos vitrais e o perfume adocicado das velas começaram a pesar sobre minhas pálpebras até que, sem perceber, minha cabeça pendeu para o lado.",
                        "Despertei num sobressalto duas horas depois, quando o eco vibrante de um acorde final de violino ainda morria no teto acústico do salão!",
                        "Saltei da poltrona gritando 'Em guarda!', mas a sala continuava trancada por dentro e completamente vazia — exceto pelo fato de que as pontas dos dedos da minha própria mão esquerda ardiam, marcadas pelos sulcos profundos das cordas de aço do violino!",
                        "'Hipnose nosferatu!', arfei, olhando aterrorizado para os meus dedos calejados pelas cordas. 'O monstro entrou por alguma fresta de névoa, hipnotizou-me com seu olhar vampírico e obrigou minhas próprias mãos a tocarem o violino para humilhar a minha vigília!'"
                }));

        // LIVRO 17 (Volume XLIV)
        list.add(new FutureChapterSpec(17, 44, "XVII", "XLIV",
                "Ato II: A Sombra no Corredor",
                "Cúmplices Peludos",
                "A tentativa de prender Senhor Cummins e Juninho e o café da manhã misterioso.",
                new String[]{
                        "Como poderia §4Conde Jodevil§0 saber exatamente quando eu cochilava na poltrona? Na décima sétima noite, descobri a resposta: os dois espiões dele que me seguiam por toda parte — o gato §bSenhor Cummins§0 e o morcego §8Juninho§0!",
                        "Atraindo ambos com um pedaço de queijo e frutinhas silvestres, fechei o gato e o morcego dentro de um grande cesto de vime na biblioteca e passei uma corda na tampa.",
                        "'Pronto, pequenos familiares das trevas!', declarei satisfeito. 'Sem vocês dois para miarem e guincharem avisando minha posição ao vosso mestre, terei uma noite de sono livre de hipnose!'",
                        "Deitei-me no divã de couro ao lado do cesto trancado, segurando a espada contra o peito.",
                        "Quando acordei no fim da tarde seguinte, porém, o cesto de vime estava aberto e vazio; alguém havia coberto meus ombros com o pesado roupão de seda carmesim do próprio Conde, §8Juninho§0 roncava feliz pendurado na aba do meu chapéu de palha e §bSenhor Cummins§0 dormia enrolado sobre a minha barriga!",
                        "Pior ainda: na mesinha ao lado do divã, havia uma bandeja de prata com biscoitos quentes, um frasco de §fBat Leite§0 e uma taça de suco de beterraba com um bilhete: §o'Alimenta-te bem, Inquisidor; faltam dez dias para a nossa estreia.'§0"
                }));

        // LIVRO 18 (Volume XLV)
        list.add(new FutureChapterSpec(18, 45, "XVIII", "XLV",
                "Ato II: A Sombra no Corredor",
                "O Bilhete sob a Porta",
                "Onde o quarto lacrado com sal e prata amanhece com um convite cravado no peito.",
                new String[]{
                        "Na décima oitava noite, tomei medidas extremas de guerra espiritual: recolhi-me ao quarto de hóspedes blindado, tranquei a porta de carvalho maciço, selei todas as frestas com cera benta, desenhei uma linha grossa de sal e limalha de prata no chão e engoli a chave de ferro.",
                        "'Quero ver tua névoa atravessar sal e prata, §4Conde Jodevil§0!', desafiei, deitando-me de botas e casaco no centro do círculo protetor.",
                        "Dormi um sono pesado e sem sonhos enquanto a tempestade fustigava as muralhas lá fora.",
                        "Ao despertar no crepúsculo da décima nona noite, corri os olhos pelo quarto: os lacres de cera na porta estavam absolutamente intactos por dentro! Ninguém havia cruzado a soleira!",
                        "No entanto, senti uma coceira ardente no meio do peito e a garganta arranhando: a chave que eu engolira estava limpa sobre o travesseiro, e a linha de prata no chão fora varrida pelos meus próprios pés!",
                        "Quando olhei para a lapela do meu casaco, vi um cartão negro com bordas de ouro pregado no meu próprio peito por um broche de prata (que já me empolara a pele toda), onde se lia em tinta fresca: §o'Convite de Honra para o Inquisidor Valerius — Camarote Real na Torre Alta, à meia-noite do 27º Dia.'§0"
                }));
    }

    // =========================================================================
    // ATO III — LIVROS 19 A 27: A HORA DO JULGAMENTO (ESCALADA ATÉ A REVELAÇÃO)
    // =========================================================================
    private static void addAct3Chapters(List<FutureChapterSpec> list) {
        // LIVRO 19 (Volume XLVI)
        list.add(new FutureChapterSpec(19, 46, "XIX", "XLVI",
                "Ato III: A Hora do Julgamento",
                "O Caçador nas Ameias",
                "O manto rubro na tempestade e o duelo de espadas contra a armadilha das muralhas.",
                new String[]{
                        "Arrancando aquele convite insolente do peito, vesti um pesado manto de veludo rubro forrado de peles de lobo — digno dos caçadores das montanhas antigas — e subi às ameias externas na décima nona noite, debaixo de um aguaceiro torrencial.",
                        "Foi quando um relâmpago rasgou o céu sobre o mar e iluminou, na ponta da muralha leste onde ocorrera o antigo §4Eclipse de Sangue§0, a silhueta alta de um homem de capa negra e espada em punho!",
                        "'Finalmente mostraste a cara fora das sombras, §4Jodevil§0!', rugi, correndo pelas pedras molhadas e cruzando meu aço contra a lâmina do inimigo num estrondo de faíscas!",
                        "Lutei com uma fúria que parecia brotar da própria memória das pedras, desferindo os golpes cruzados que §2Azarasquel§0, §2Reznozada§0, §6Javidizineo§0 e §8Corvo§0 usaram na grande defesa da cidadela.",
                        "Na escuridão da chuva, o vulto girava nos calcanhares com precisão mecânica, devolvendo o impacto de cada golpe meu com a mesma força em que eu o atacava!",
                        "Reunindo todo o meu fôlego, apliquei uma estocada mortal que partiu o adversário ao meio — mas, quando o relâmpago brilhou de novo, vi que minha espada rachara um boneco de treino articulado com molas de relojoaria e vestido com a capa reserva do Conde! Era apenas uma isca para me cansar na chuva!"
                }));

        // LIVRO 20 (Volume XLVII)
        list.add(new FutureChapterSpec(20, 47, "XX", "XLVII",
                "Ato III: A Hora do Julgamento",
                "O Portão da Firma",
                "Onde Valerius tenta buscar reforços da Ordem e é barrado por Jacinto.",
                new String[]{
                        "Diante daquele boneco de molas rindo de mim na tempestade, compreendi na vigésima noite que §4Conde Jodevil§0 conhecia cada corredor desta fortaleza melhor do que eu: para impedir o Concerto da 27ª Lua, eu precisava sair do castelo e trazer um pelotão inteiro da Ordem!",
                        "Desci as escadarias a passo de carga até o pátio externo, decidido a cruzar a ponte levadiça antes do amanhecer.",
                        "Porém, ao alcançar a muralha do portão, vi que as enormes correntes da ponte estavam recolhidas e travadas por um cadeado de ferro maciço cujo brasão trazia o selo pessoal do Conde!",
                        "Sentado num banco logo abaixo da alavanca, limpando tranquilamente o cabo do §bMjölnir que não volta§0 com uma flanela, estava o guardião §6Jacinto§0.",
                        "'Abre este portão agora, §6Jacinto§0!', bradei, batendo o punho na grade. 'Preciso sair para convocar as tropas da Inquisição antes da noite do concerto!'",
                        "§6Jacinto§0 nem levantou os olhos da flanela: §o'Negativo, senhor. Ordem expressa assinada pelo dono do castelo: ninguém entra e ninguém sai antes do último acorde à meia-noite do dia vinte e sete. E hoje não adianta nem pedir por favorzinho que a portaria está fechada para balanço!'§0"
                }));

        // LIVRO 21 (Volume XLVIII)
        list.add(new FutureChapterSpec(21, 48, "XXI", "XLVIII",
                "Ato III: A Hora do Julgamento",
                "O Escudo de Barro",
                "A travessia desesperada atrás do vaso seco quando o sol invade a galeria.",
                new String[]{
                        "Trancado dentro da fortaleza até a noite do concerto pelo regulamento inflexível de §6Jacinto§0, subi de volta às galerias ao alvorecer do vigésimo primeiro dia procurando uma passagem pelos contrafortes.",
                        "Ao apoiar a mão na cortina da janela sul para espiar o penhasco, o tecido ressecado rasgou-se de cima a baixo — e um jorro incandescente de sol matinal inundou todo o corredor de pedra!",
                        "A claridade atingiu minha bota direita como ferro em brasa: ouviu-se o maldito chiado de banha na frigideira (§o'tsssss!'§0), a fumaça subiu pelo meu tornozelo e senti o pavor agudo da §4Centésima Centelha§0 prestes a transformar meu corpo em cinzas no tapete!",
                        "Sem pensar na dignidade do meu cargo, soltei um grito estridente e atirei-me de barriga na única sombra que restava no corredor: atrás de um pequeno vaso de barro contendo um arbusto seco!",
                        "Encolhido como um tatu atrás daquele vaso minguado e usando o chapéu de palha como escudo para proteger as orelhas da luz, fui empurrando o vaso centímetro por centímetro ao longo de todo o corredor ensolarado.",
                        "Após quarenta minutos rastejando atrás da planta morta, alcancei a sombra salvadora da copa real — e abracei o vaso de arbusto seco jurando que ele seria meu escudo pessoal até o fim da missão!"
                }));

        // LIVRO 22 (Volume XLIX)
        list.add(new FutureChapterSpec(22, 49, "XXII", "XLIX",
                "Ato III: A Hora do Julgamento",
                "O Brinde de Letes",
                "Onde Valerius confisca os três cálices cor-de-rosa para armar a emboscada na Torre Real.",
                new String[]{
                        "Recuperando o fôlego no escuro da copa real com meu vaso seco debaixo do braço, notei um brilho violáceo saindo de trás da adega de cristais.",
                        "Empurrei a estante giratória e descobri uma pequena câmara de destilação onde três garrafas de vidro lapidado acabavam de ser preenchidas com um licor cor-de-rosa denso e fumegante!",
                        "Ao lado das garrafas, sobre uma bandeja de prata, havia uma instrução escrita pelo próprio Conde: §o'Vinum Lethes (Cálice da 27ª Lua) — Receita especial de ArzanLion & AsterZZZz. Levar para o suporte da cabeceira na Torre Real para o brinde solene após o último acorde da meia-noite.'§0",
                        "Um sorriso frio e implacável desenhou-se em meu rosto ao ler aquela nota: ali estava a fraqueza fatal do meu inimigo!",
                        "Independentemente de onde §4Conde Jodevil§0 se escondesse durante o concerto, à meia-noite em ponto da vigésima sétima lua ele iria até a cabeceira da Torre Real para beber seu precioso §oVinum Lethes§0 em celebração!",
                        "Recolhi os três cálices cor-de-rosa na bandeja: eu mesmo levaria a bebida até a Torre Real, colocaria as garrafas no suporte da cabeceira como isca e esperaria escondido no quarto com a estaca na mão!"
                }));

        // LIVRO 23 (Volume L)
        list.add(new FutureChapterSpec(23, 50, "XXIII", "L",
                "Ato III: A Hora do Julgamento",
                "A Queda na Torre Real",
                "No qual o Inquisidor usa um banco como aríete contra uma porta destrancada.",
                new String[]{
                        "Na vigésima terceira noite, subi a longa escadaria helicoidal até o ponto mais alto do Castelo sobre o Mar, carregando a bandeja com o §oVinum Lethes§0, meu fiel vaso de arbusto seco e um pesado banco de carvalho para arrombar o santuário do Conde.",
                        "Diante da porta dupla dos aposentos reais, depositei a bandeja no chão, empunhei o banco como aríete e tomei distância: 'Tua fortaleza caiu, §4Jodevil§0!', bradei lançando-me em corrida total contra a madeira.",
                        "Um segundo antes do impacto, porém, uma lufada de vento vinda da janela abriu os batentes sozinhos — pois a porta da Torre Real estava completamente destrancada!",
                        "Sem conseguir frear, atravessei a porta aberta voando baixo igual a uma pedra de catapulta, pisei na barra do meu próprio manto rubro, dei duas cambalhotas sobre o tapete e bati com o queixo direto no pé da cama real!",
                        "Fiquei estatelado no piso de pedra por cinco minutos vendo estrelas dançarem sob o dossel, enquanto o gato §bSenhor Cummins§0 me observava de cima do travesseiro com absoluto desprezo felino.",
                        "Quando me levantei esfregando o queixo, examinei finalmente o covil supremo do vampiro: o leito real, o violino de madeira escura apoiado ao pé da cama e, no piso de pedra ao lado, um imponente Baú Duplo."
                }));

        // LIVRO 24 (Volume LI)
        list.add(new FutureChapterSpec(24, 51, "XXIV", "LI",
                "Ato III: A Hora do Julgamento",
                "Os Diários de Sangue",
                "As notas apaixonadas nas margens dos tomos de Asminn e a lágrima ainda fresca.",
                new String[]{
                        "Montando minha armadilha na vigésima quarta noite, posicionei o vaso de arbusto seco junto à janela e coloquei os três cálices cor-de-rosa de §oVinum Lethes§0 no suporte da cabeceira para atrair o Conde à meia-noite.",
                        "Em seguida, abri a tampa do Baú Duplo ao lado do leito: na metade superior estavam guardados os vinte e sete volumes originais das §oCrônicas do Sangue Duvidoso§0, escritos décadas atrás por §9Lorde Asminn§0!",
                        "Puxei os tomos à luz do candelabro e vi que o Conde passara anos inteiros escrevendo comentários apaixonados e vaidosos nas margens de quase todas as páginas, como um príncipe imortal dialogando com seu cronista.",
                        "No relato da queda do parapeito, o Conde rabiscara: §o'Calúnia poética do meu amado Asminn! Eu não caí nas roseiras de Kikkawa, Pipira e Luwuana como um saco de batatas; foi um salto trágico digno de um anjo rebelde!'§0",
                        "Mas no Capítulo XXV — onde o Conde confessava a §9Asminn§0 seu pavor de um dia ficar sozinho no castelo —, a vaidade desaparecia para dar lugar a uma nota curta: §o'Tu tinhas razão, meu irmão... o silêncio chegou, e dói mais do que o sol.'§0",
                        "Toquei o papel com a ponta do dedo e estremeci: sobre aquela última frase brilhava uma lágrima que acabara de cair, ainda molhada no pergaminho!"
                }));

        // LIVRO 25 (Volume LII)
        list.add(new FutureChapterSpec(25, 52, "XXV", "LII",
                "Ato III: A Hora do Julgamento",
                "A Sala Vazia",
                "A descoberta aterradora sobre os convidados do concerto e o véu na parede.",
                new String[]{
                        "Na vigésima quinta noite, faltando apenas um dia para o anunciado 'Grande Concerto do Século', fui até a janela alta da Torre Real com minha luneta para vigiar a chegada das carruagens da corte pela estrada do mar.",
                        "Olhei para o porto, para a ponte e para a estrada das colinas sob o luar: não havia uma única carruagem, nem barco, nem tocha no horizonte.",
                        "Corri de volta à escrivaninha do quarto e abri a gaveta selada: lá dentro estavam todos os convites originais de ouro para §9Asminn§0, §6ArzanLion§0, §5AsterZZZz§0, §dKikkawa§0, §dLuwuana§0, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0 — nenhum convite jamais fora enviado pelo correio, porque os amigos mortais daquela era haviam partido há muitas décadas!",
                        "Um calafrio de gelar a alma percorreu meu corpo: o Concerto da Vigésima Sétima Lua nunca fora uma reunião real; era uma ópera solitária encenada por um imortal que não aceitava que o tempo havia levado sua corte!",
                        "E se a lágrima no livro do Baú Duplo estava fresca, o Conde já estava escondido dentro deste quarto real, esperando a meia-noite!",
                        "Cravei os olhos no único lugar da parede oeste onde um homem alto poderia esconder-se em pé: atrás do longo estandarte de veludo negro que ia do teto ao chão, guardado pelo gato §bSenhor Cummins§0."
                }));

        // LIVRO 26 (Volume LIII)
        list.add(new FutureChapterSpec(26, 53, "XXVI", "LIII",
                "Ato III: A Hora do Julgamento",
                "A Última Sonata",
                "As doze badaladas da vigésima sétima meia-noite diante do estandarte negro.",
                new String[]{
                        "Na vigésima sétima noite, tranquei a porta da Torre Real por dentro, deixei o cálice de §oVinum Lethes§0 fumegando na cabeceira e postei-me a um passo do longo estandarte de veludo negro na parede, com a estaca erguida na mão direita.",
                        "No alto do campanário, o grande sino de bronze começou a badalar a meia-noite do concerto: §oBADALADA UM!§0 — e um trovão de saudade explodiu dentro do meu crânio com a voz de §9Lorde Asminn§0 chamando-me pelo nome diante da vela.",
                        "§oBADALADA QUATRO!§0 — senti na língua o gosto vivo do caldeirão de §6ArzanLion§0, ouvi o ronco de §5AsterZZZz§0 sobre o papiro e o latido festivo da pequena §eVitória§0!",
                        "§oBADALADA OITO!§0 — minhas próprias costas arderam com a memória das roseiras sob o riso doce de §dLady Kikkawa§0, §dPipira§0 e §dDama Luwuana§0, e meu pescoço queimou com a colher de prata de §2Azarasquel§0, §2Reznozada§0 e §6Javidizineo§0!",
                        "§oBADALADA ONZE!§0 — ouvi §8Corvo§0 gritando as apostas no telhado e a minha própria voz implorando §o'por favorzinho'§0 ao fiel §6Jacinto§0 para devolver o meu §bMjölnir§0!",
                        "§oBADALADA DOZE!§0 — 'Acabou o teu concerto solitário, §4Conde Jodevil§0!', gritei com o rosto banhado em lágrimas e, num golpe único, arranquei o estandarte negro da parede para encarar o monstro!"
                }));

        // LIVRO 27 (Volume LIV — O CLÍMAX SUPREMO)
        list.add(new FutureChapterSpec(27, 54, "XXVII", "LIV",
                "Ato III: A Hora do Julgamento",
                "A Sentença da Meia-Noite",
                "Onde a lâmina do inquisidor encontra finalmente o pescoço do Conde.",
                new String[]{
                        "O estandarte negro caiu no chão de pedra, mas não havia passagem secreta nem inimigo escondido na parede: era o único espelho inteiro que restara no castelo. E porque minha maldição de beterraba e vinagre jamais teve poder para apagar meu reflexo, o homem que me encarou de volta no cristal — pálido, de manto rubro, punhos de renda sujos de tinta carmesim, chapéu de palha torto e estaca apontada para o próprio peito — §4era eu mesmo, o Conde Jodevil!§0",
                        "A estaca rolou dos meus dedos batendo contra o violino escuro aos pés da cama enquanto a farsa do 'Inquisidor Valerius' desmoronava num soluço visceral: nunca existiu inquisidor algum! Décadas após o fim do primeiro ciclo, cumpriu-se a profecia que confessei a §9Asminn§0: quando o tempo levou nossa corte e deixou este Castelo sobre o Mar mergulhado num silêncio eterno, a saudade dos meus amigos partiu a minha razão.",
                        "Lembrei-me de tudo de uma só vez: incapaz de suportar a eternidade diante de cadeiras vazias, desci à fornalha de §6ArzanLion§0 e destilei o §oVinum Lethes§0 para apagar minha dor; mas, como usei o grimório manchado pela baba de §5AsterZZZz§0, o feitiço prendeu-me num ciclo de vinte e sete noites!",
                        "Durante o sono diurno, meu coração apaixonado de Príncipe Rebelde prepara o Grande Concerto, escreve convites dourados e afina o violino esperando o retorno de §9Asminn§0, §6ArzanLion§0, §dKikkawa§0, §dLuwuana§0, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0; e à noite, minha mente desperta como um caçador implacável que sabota a própria festa apenas para não ter que encarar a sala vazia à meia-noite!",
                        "Caí de joelhos abraçando §bSenhor Cummins§0 enquanto §8Juninho§0 pousava em meu ombro, chorando e rindo da minha própria ópera trágica; puxei o violino para tocar um último acorde desafinado em honra à corte que tanto amei e, sentindo que a dor de lembrar da solidão era insuportável demais para continuar acordado, ergui da cabeceira o cálice cor-de-rosa que eu mesmo trouxera: §o'Até o nosso próximo concerto, meus eternos amigos...'§0",
                        "Bebi o §oVinum Lethes§0 até a última gota e, enquanto a névoa apagava meu nome, desci cambaleando até a cripta inferior com um tomo em branco, o tinteiro carmesim e meu chapéu de palha para trancar o portão antes de tombar sobre a pedra — até que um raio de sol atravessou a grade alta e puxei a pena para escrever: §o'Despertei sobre a laje fria de pedra, coberto pelo pó branco das catacumbas, enquanto um chiado agudo — tsssss — subia da ponta da minha bota...'§0"
                }));
    }
}
