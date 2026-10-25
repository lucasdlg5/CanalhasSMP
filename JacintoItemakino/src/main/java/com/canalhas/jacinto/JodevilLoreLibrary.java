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
 * Biblioteca Épica de Lore do Conde Jodevil — 27 Volumes ("Crônicas do Sangue Duvidoso").
 * Escrita no estilo gótico, romântico, epistolar e cômico de "Entrevista com o Vampiro",
 * "A Rainha dos Condenados" (Anne Rice) e "Drácula" (Bram Stoker), sem qualquer referência
 * a mecânicas de jogo ou spoilers nos títulos, tratando o mundo dos Canalhas como um universo real.
 */
public final class JodevilLoreLibrary {

    // Limites estritos e testados para a interface do livro escrito (14 linhas máx, ~114px largura):
    // 19 caracteres visíveis por linha e no máximo 12 linhas por página garantem ZERO corte de palavras!
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
     * Gera os 27 Livros (Capítulos I a XXVII) para preencher os slots 0 a 26 do Baú Duplo ao lado da cama de Jodevil.
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
                if (wordVisibleLen <= maxCharsPerLine) {
                    current.append(word);
                    currentVisibleLen = wordVisibleLen;
                } else {
                    int splitAt = Math.min(word.length(), maxCharsPerLine - 1);
                    lines.add(word.substring(0, splitAt) + "-");
                    String rest = word.substring(splitAt);
                    current.append(rest);
                    currentVisibleLen = visibleLength(rest);
                }
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

    private static List<ChapterSpec> buildAll27Chapters() {
        List<ChapterSpec> list = new ArrayList<>(27);

        // =========================================================================
        // ATO I — O COMEÇO: A SEDE DE ETERNIDADE E O CREPÚSCULO MORTAL (Cap. I a IX)
        // =========================================================================

        // CAPÍTULO I
        list.add(new ChapterSpec(1, "I", "Ato I: O Começo",
                "O Sussurro da Vela",
                "Onde Conde Jodevil abre sua alma ao cronista Asminn na torre gótica.",
                new String[]{
                        "\"Vejo que trouxeste a pena, o pergaminho e o tinteiro, meu caro §9Asminn§0\", murmurou §4Conde Jodevil§0, enquanto a chama trêmula do candelabro de prata desenhava sombras longas sobre sua face pálida.",
                        "Lá fora, o oceano noturno batia contra os rochedos da cidadela como um coração antigo e inconsolável, trazendo pela fresta do vitral o perfume das folhas mortas de outubro.",
                        "\"Pára de olhar para a minha capa com esse sorriso de canto de boca, §9Asminn§0!\", reclamou o Conde, fechando o semblante num muxoxo aristocrático. \"Se vais registrar a minha tragédia para a posteridade, exijo a solenidade que cabe a um Príncipe das Trevas!\"",
                        "\"Perdoa-me, §4Jodevil§0\", respondeu §9Asminn§0 mergulhando a pena na tinta carmesim. \"É que ainda há uma folha de roseira presa no teu colarinho de veludo. Mas prossegue: como nasceu tua sede pela meia-noite?\"",
                        "§4Jodevil§0 arrancou a folha com dignidade ferida, serviu duas taças de vinho — do bom, não da gororoba que usaram nele — e recostou-se na poltrona de espaldar alto.",
                        "\"Escreve cada palavra, meu irmão de armas. Pois a história da minha condenação começa com o mais sublime orgulho romântico... e desaba no maior crime alquímico que estas terras já testemunharam.\""
                }));

        // CAPÍTULO II
        list.add(new ChapterSpec(2, "II", "Ato I: O Começo",
                "A Dor de Ser Mortal",
                "O baile de outono, o espelho cruel e a primeira ferida do tempo.",
                new String[]{
                        "\"Tu te lembras do Grande Baile da Colheita do ano passado, §9Asminn§0?\", indagou §4Jodevil§0, tamborilando os dedos pálidos no braço da cadeira. \"Dizem as más línguas que decidi virar vampiro por mera vaidade literária. Calúnia!\"",
                        "\"Eu estava me preparando para o baile diante do espelho de bronze quando o vi: um fio de cabelo branco, insolente e brilhante, bem na minha têmpora esquerda! A velhice mortal estava me emboscando antes dos trinta!\"",
                        "\"Mais tarde, no salão iluminado por tochas, tentei impressionar a corte fazendo uma reverência profunda à moda dos duques antigos... mas meu espadim enroscou na toalha da mesa de queijos.\"",
                        "Neste ponto da narrativa, §9Asminn§0 precisou morder a ponta da pena e tossir dentro do punho para esconder o riso, lembrando-se perfeitamente do estrondo da prataria.",
                        "\"Eu vi a tua pena tremer, §9Asminn§0! Não te atrevas a escrever que caí sentado na tigela de ponche!\", bradou §4Jodevil§0 apontando o dedo. \"Escreve que sofri uma vertigem existencial diante da fragilidade da carne humana!\"",
                        "\"Naquela mesma madrugada, limpando ponche das botas, jurei às estrelas: eu jamais teria rugas nem reumatismo. Eu conquistaria a perfeição imortal da noite, custasse o que custasse!\""
                }));

        // CAPÍTULO III
        list.add(new ChapterSpec(3, "III", "Ato I: O Começo",
                "A Rainha Antiga",
                "A invasão noturna às catacumbas proibidas da Mãe dos Imortais.",
                new String[]{
                        "Decidido a encontrar o segredo do sangue eterno, §4Jodevil§0 não esperou pela sorte: numa noite de nevoeiro cerrado, arrombou com um pé-de-cabra enferrujado o alçapão das criptas esquecidas sob a antiga capela.",
                        "Desceu os degraus limosos segurando um único archote, desviando de teias de aranha seculares até alcançar a câmara funerária onde repousavam os arquivos proibidos dos primeiros séculos.",
                        "Ao empurrar a pesada tampa de um sarcófago de mármore para usar de apoio, porém, a manga de sua capa nova ficou presa na fresta da pedra e a tocha caiu no chão, apagando-se na água fria!",
                        "\"Fiquei vinte minutos preso no breu puxando o tecido, §9Asminn§0, achando que um esqueleto ancestral estava segurando meu cotovelo!\", confessou §4Jodevil§0 com um calafrio.",
                        "Quando finalmente rasgou a manga e reacendeu a pederneira, viu brilhar dentro do sarcófago aberto o lendário códice encadernado em couro rubro: os fragmentos da §4Rainha dos Condenados§0.",
                        "\"Li aquelas páginas ali mesmo, sentado nos ossos antigos: falavam de lordes com pele de alabastro, olhar hipnótico e domínio eterno sobre a meia-noite. Saí da cripta ao alvorecer com a capa rasgada, mas com o destino traçado!\""
                }));

        // CAPÍTULO IV
        list.add(new ChapterSpec(4, "IV", "Ato I: O Começo",
                "Serenata ao Luar",
                "As vigílias poéticas nos terraços superiores da fortaleza.",
                new String[]{
                        "Antes mesmo de encontrar quem o transformasse, §4Jodevil§0 concluiu que precisava preparar seu espírito — e seu guarda-roupa — para a majestade sombria que em breve habitaria seu corpo.",
                        "Durante sete noites seguidas, subiu à balaustrada norte vestindo veludo negro e gola alta, declamando versos melancólicos para a lua cheia enquanto treinava seu olhar penetrante de predador aristocrático.",
                        "O que ele ignorava era que, na varanda logo acima, §dLady Kikkawa§0 — acompanhada de seu inseparável e místico peixe flutuante §bPipira§0 — e a nobre §dDama Luwuana§0 assistiam a cada ensaio com uma bandeja de biscoitos.",
                        "\"Repara na mão sobre o peito, §dKikkawa§0\", sussurrava §dLuwuana§0 atrás do leque de seda. \"Será que ele está invocando as legiões do abismo ou está apenas com soluço depois do jantar?\"",
                        "\"Acho que §bPipira§0 gostou do teatro, mas a fivela da capa dele acabou de prender no candelabro de parede\", observou §dKikkawa§0 apontando discretamente.",
                        "No segundo seguinte, §4Jodevil§0 deu um giro dramático para encerrar o poema e arrancou o candelabro inteiro com cortina e tudo — saindo do terraço enrolado no pano vermelho de queixo erguido, como se a tapeçaria fizesse parte do traje real."
                }));

        // CAPÍTULO V
        list.add(new ChapterSpec(5, "V", "Ato I: O Começo",
                "O Clamor na Floresta",
                "A peregrinação solitária sob a névoa em busca do beijo imortal.",
                new String[]{
                        "Vendo que os versos no terraço não atraíam nenhum lorde vampiro dos Cárpatos, §4Jodevil§0 tomou uma medida extrema: embrenhou-se sozinho, à meia-noite, na mata fechada além das colinas do cemitério velho.",
                        "Desabotoou estrategicamente o colarinho da camisa de linho, inclinou o pescoço pálido sobre uma lápide coberta de musgo e abriu os braços para a escuridão das árvores.",
                        "\"Vinde a mim, filhos da eterna escuridão!\", clamou ele com voz embargada de paixão gótica. \"Cravai vossas presas nesta garganta mortal e concedei-me o Dote Sombrio da meia-noite!\"",
                        "O silêncio da floresta durou três minutos inteiros — até que um zumbido agudo desceu das copas dos ciprestes e uma nuvem implacável de pernilongos do pântano atacou o pescoço descoberto do futuro conde!",
                        "Enquanto §4Jodevil§0 se estapeava no escuro fugindo dos insetos, uma única criatura noturna pousou mansamente em seu ombro: um morceguinho gordo, zarolho e extremamente simpático que cheirava a amora silvestre.",
                        "\"Ao menos tu reconheces a minha nobreza das trevas!\", declarou §4Jodevil§0 coçando quatro calombos de mosquito no queixo e batizando solenemente o pequeno companheiro de §8Juninho§0."
                }));

        // CAPÍTULO VI
        list.add(new ChapterSpec(6, "VI", "Ato I: O Começo",
                "Diário de Asminn",
                "Registros epistolares de movimentos ocultos nas fronteiras do feudo.",
                new String[]{
                        "§8Trecho do Diário de Lorde Asminn — 10 de Outubro.§0 A cerração de outono desceu sobre nossas muralhas, e com ela começaram a surgir indícios de que uma grande insensatez está sendo tramada nos subterrâneos.",
                        "Esta manhã, ao inspecionar as engrenagens do portão leste, interceptei o carroceiro da vila: ele jurava que o autoproclamado Grão-Alquimista §6ArzanLion§0 comprara dezenove garrafões do vinho mais azedo e barato da taverna.",
                        "Meia hora depois, vi o próprio §6ArzanLion§0 saindo da horta comunitária com um saco de estopa cheio de beterrabas passadas e três tranças de alho mofado, murmurando cálculos em latim macarrônico.",
                        "Ao cair do crepúsculo, cruzei com §4Jodevil§0 na galeria das armas; ele trazia o morcego §8Juninho§0 empoleirado no chapéu e um brilho febril nos olhos que me deixou genuinamente alarmado.",
                        "Segurando-me pelos ombros, sussurrou-me como quem revela um segredo de Estado: 'Prepara teus pergaminhos, §9Asminn§0! Em três noites, a morte já não terá domínio sobre o meu nome!'",
                        "Tentei lembrar-lhe de que a última 'poção de transmutação heroica' de §6ArzanLion§0 havia deixado as sobrancelhas de §2Reznozada§0 azuis por duas semanas, mas §4Jodevil§0 já caminhava nas nuvens."
                }));

        // CAPÍTULO VII
        list.add(new ChapterSpec(7, "VII", "Ato I: O Começo",
                "O Pacto na Taverna",
                "O juramento selado à meia-noite sob as abóbadas enfumaçadas.",
                new String[]{
                        "Na calada da noite seguinte, na mesa mais escura dos fundos da taverna, §4Jodevil§0 reuniu-se em segredo absoluto com §6ArzanLion§0, sob a vigilância atenta da fiel cadelinha §eVitória§0.",
                        "\"Dize-me a verdade sem rodeios, §6ArzanLion§0\", sussurrou §4Jodevil§0 inclinando-se sobre a vela de sebo. \"Tens mesmo o poder de reescrever o meu sangue segundo o rito dos antigos Condes da Noite?\"",
                        "\"Ora, por quem me tomas, meu caro §4Jodevil§0?!\", ofendeu-se §6ArzanLion§0 ajeitando o manto manchado de fuligem. \"Para um mestre das artes herméticas como eu, a imortalidade vampírica é procedimento de rotina!\"",
                        "\"Sairás da minha adega com a palidez dos mármores romanos, a sede rubra dos reis noturnos e a clássica maldição solar que todo vampiro de respeito exibe nas crônicas!\"",
                        "§4Jodevil§0 sentiu o coração bater de júbilo poético e empurrou a bolsa sobre a mesa: \"Pede o teu preço, mago! Ouro? Relíquias? Um pacto de sangue eterno?\"",
                        "\"Três moedas de prata, metade daquele queijo curado da tua despensa e um petisco defumado para a §eVitória§0 não latir durante o cântico\", respondeu §6ArzanLion§0 fechando o negócio com um aperto de mão."
                }));

        // CAPÍTULO VIII
        list.add(new ChapterSpec(8, "VIII", "Ato I: O Começo",
                "O Grimório de Aramaico",
                "A vigília sagrada do acólito sobre as escrituras proibidas da noite.",
                new String[]{
                        "Para que o rito supremo de transmutação sanguínea não falhasse, era indispensável decifrar o único manuscrito original trazido do Oriente, cujas runas em aramaico antigo guardavam a chave da imortalidade pura.",
                        "Na véspera da Lua Sangrenta, §6ArzanLion§0 abriu o pergaminho milenar sobre a bancada da adega e confiou sua guarda solene ao mais silencioso e contemplativo dos iniciados da fortaleza: o monge §5AsterZZZz§0.",
                        "\"Vigia estas letras sagradas com a própria vida, §5AsterZZZz§0, enquanto vou buscar o carvão ritualístico!\", ordenou o alquimista antes de trancar a porta.",
                        "§5AsterZZZz§0 assumiu uma postura de profunda meditação zen diante do papiro... e, passados exatos três minutos e meio, tombou a testa pesadamente sobre o texto aberto num ronco capaz de rachar barris!",
                        "Durante seis horas ininterruptas de sono abissal, um fio generoso e constante de baba escorreu pelo canto da boca do acólito, dissolvendo a tinta secular das três estrofes que protegiam o vampiro contra a luz e o ridículo.",
                        "Quando §6ArzanLion§0 retornou e sacudiu o dorminhoco, metade das runas virara uma mancha roxa ilegível. \"Detalhes técnicos!\", murmurou o mago enxugando a baba do papiro na manga. \"Onde estiver borrado, eu completo com latim de missa!\""
                }));

        // CAPÍTULO IX
        list.add(new ChapterSpec(9, "IX", "Ato I: O Começo",
                "A Última Aurora",
                "A partilha solene dos bens terrenos antes da descida ao abismo.",
                new String[]{
                        "Ao entardecer do dia catorze de outubro, convicto de que morreria para o mundo mortal naquela meia-noite, §4Jodevil§0 convocou os companheiros ao alto da muralha oeste para ler seu Testamento Dramático.",
                        "Com o vento açoitando a capa e a mão sobre o peito, ele declamou olhando para o pôr do sol: \"Meus irmãos! Quando este astro tornar a erguer-se amanhã, o homem que conhecestes estará morto, e em seu lugar reinará uma criatura do abismo!\"",
                        "Lá embaixo, porém, a comoção passava longe do esperado: §8Corvo§0 anotava medidas com uma fita métrica, enquanto §2Azarasquel§0 e §2Reznozada§0 examinavam atentamente os pés de §4Jodevil§0.",
                        "\"Dize-me uma coisa, §4Jodevil§0\", interrompeu §2Reznozada§0 com absoluta seriedade prática, \"se o caldeirão de §6ArzanLion§0 te explodir em pedacinhos hoje à noite, tu já deixaste assinado quem herda essas tuas botas de couro forrado?\"",
                        "\"Eu pedi as botas primeiro na terça-feira!\", protestou §8Corvo§0, enquanto o leãozinho §6Javidizineo§0 mordiscava a ponta da capa do futuro defunto.",
                        "Indignado com a falta de reverência fúnebre da corte, §4Jodevil§0 enrolou o pergaminho do testamento, declarou que levaria as botas para a eternidade e desceu marchando rumo à adega de §6ArzanLion§0!"
                }));

        // =========================================================================
        // ATO II — O MEIO: O RITUAL DESASTROSO E AS PROVAÇÕES (Cap. X a XVIII)
        // =========================================================================

        // CAPÍTULO X
        list.add(new ChapterSpec(10, "X", "Ato II: O Meio",
                "A Câmara do Alquimista",
                "O descenso aos subterrâneos e o preparo do cálice primordial.",
                new String[]{
                        "\"Quando desci o último degrau da adega à meia-noite, §9Asminn§0\", relatou §4Jodevil§0 com um tremor na voz, \"meu coração esperava encontrar um templo de obsidiana digno dos antigos mistérios do Egito.\"",
                        "\"Em vez disso, encontrei §6ArzanLion§0 usando um avental sujo de farinha no meio de um círculo de giz torto que parecia um ovo estrelado, cercado por quatro tocos de vela colados em tampas de barril!\"",
                        "No centro do círculo fervia o caldeirão profano: como não possuía uma única gota do Sangue Ancestral da Transilvânia, §6ArzanLion§0 despejara três garrafas daquele vinho tinto azedo de taverna.",
                        "Para dar a cor rubra e a viscosidade das veias nobres, o alquimista acrescentara dois litros de caldo concentrado de beterraba cozida, três cabeças de alho murcho, banha derretida e pimenta-rosa moída na hora.",
                        "\"Por todos os demônios, §6ArzanLion§0!\", recuou §4Jodevil§0 tapando o nariz. \"Isto cheira a sopa de quartel estragada! Tens certeza de que o elixir dos imortais leva alho e beterraba?!\"",
                        "\"Cala-te e entra no círculo, homem de pouca fé!\", bradou §6ArzanLion§0 erguendo a concha fumegante. \"O alho é para criar resistência homeopática desde o berço!\""
                }));

        // CAPÍTULO XI
        list.add(new ChapterSpec(11, "XI", "Ato II: O Meio",
                "A Invocação Suprema",
                "O momento exato em que o latim ancestral rasga o véu da mortalidade.",
                new String[]{
                        "Lá fora, um trovão providencial sacudiu os alicerces da cidadela quando §6ArzanLion§0 encheu um cálice de estanho amassado com aquele caldo violáceo e o estendeu solenemente a §4Jodevil§0.",
                        "\"Bebe até a última gota ao som do meu cântico!\", ordenou o ocultista. Fechando os olhos com devoção heroica e prendendo o fôlego para não sentir o gosto de cebola e vinagre, §4Jodevil§0 virou o cálice de uma só vez.",
                        "§6ArzanLion§0 abriu os braços, ergueu o pergaminho manchado pela baba de §5AsterZZZz§0 e, estreitando os olhos míopes contra a luz da vela, começou a berrar em latim improvisado:",
                        "\"§oSanguis Noctis! Tenebras Vampiricus... hã... Beterrabus Perpetuum Mobile!§0\" As sombras da adega pareceram esticar-se nas paredes à medida que a energia arcana se acumulava no teto baixo.",
                        "Mas, no exato segundo em que a última sílaba deveria selar a perfeição imortal, o vapor de pimenta-rosa do caldeirão entrou direto nas narinas do alquimista — e §6ArzanLion§0 soltou um estrondoso: \"§oATCHIM!§0\"",
                        "O espirro colossal arremessou o pergaminho babado direto dentro do fogo do braseiro! Uma explosão cor-de-rosa engoliu a adega inteira com cheiro de alho torrado, lançando §4Jodevil§0 desacordado contra os barris!"
                }));

        // CAPÍTULO XII
        list.add(new ChapterSpec(12, "XII", "Ato II: O Meio",
                "Renascido nas Cinzas",
                "O lento despertar do coração imortal sob as pedras da adega.",
                new String[]{
                        "Duas horas antes da alvorada, §4Jodevil§0 abriu os olhos no meio da fuligem cor-de-rosa. Levou a mão ao peito e sentiu um choque de triunfo: seu coração batia agora num compasso lento, grave e glacial.",
                        "Ergueu os dedos diante da vela: sua pele adquirira a palidez marmórea dos antigos príncipes da noite, e seus olhos enxergavam cada fresta das pedras na escuridão sem esforço algum!",
                        "Porém, ao tentar levantar-se com a graça sobrenatural de um predador, sentiu uma pontada de azia monumental, um gosto amargo de salada de beterraba na garganta e um estalo seco no joelho esquerdo.",
                        "Ao seu lado, §6ArzanLion§0 limpava a fuligem das sobrancelhas com um pano de prato, abrindo um sorriso amarelo: \"Meus parabéns, §4Conde Jodevil§0! Tirando o pequeno imprevisto nasal no final, a transmutação foi um sucesso quase absoluto!\"",
                        "\"Quase absoluto?!\", rugiu §4Jodevil§0, assustando até a cadelinha §eVitória§0 com seu novo timbre cavernoso — que infelizmente falhou num guincho agudo na última sílaba. \"Por que sinto gosto de vinagre nos dentes, charlatão?!\"",
                        "\"É o buquê aromático da eternidade!\", garantiu §6ArzanLion§0 empurrando-o gentilmente escada acima. \"Vai lá fora testar tuas asas da noite antes que o sol nasça!\""
                }));

        // CAPÍTULO XIII
        list.add(new ChapterSpec(13, "XIII", "Ato II: O Meio",
                "As Asas da Meia-Noite",
                "O relato epistolar de Lady Kikkawa e Dama Luwuana sobre o primeiro salto do Conde.",
                new String[]{
                        "§8Depoimento oficial de Lady Kikkawa e Dama Luwuana arquivado na Torre Real.§0 \"Deixamos registrado este testemunho ocular para que nenhuma geração futura duvide do que ocorreu às quatro da madrugada no jardim de inverno.\"",
                        "\"Estávamos recolhendo os bules de chá no terraço quando vimos §4Conde Jodevil§0 emergir da porta da adega, ainda soltando pequenos tufos de fumaça cor-de-rosa pela gola da capa.\"",
                        "\"Ignorando nossa presença, ele subiu com passo imperial sobre a mureta mais alta do parapeito norte, abriu os braços como um condor de veludo e encarou o abismo do pátio.\"",
                        "\"Bradou então com voz trovejante: §o'Contemplai, ó estrelas, o despertar do Soberano do Abismo! Desfaze-te em névoa e asas, matéria mortal!'§0 — e atirou-se de peito aberto no vazio!\"",
                        "\"Eu e §dKikkawa§0 corremos até a borda — e até o pequeno §bPipira§0 tapou os olhos com as nadadeiras: pois §4Jodevil§0 não virou morcego nem névoa; caiu reto feito uma bigorna de ferro!\"",
                        "\"Aterrissou de cabeça no meio do grande canteiro de roseiras bravas do pátio, de onde saiu cinco minutos depois mancando, cheio de espinhos nas costas, dizendo que uma corrente de ar traiçoeira atrapalhara seu pouso.\""
                }));

        // CAPÍTULO XIV
        list.add(new ChapterSpec(14, "XIV", "Ato II: O Meio",
                "O Tribunal da Prata",
                "O ordálio sagrado conduzido pelos inquisidores da fortaleza.",
                new String[]{
                        "Na noite seguinte, quando §4Conde Jodevil§0 anunciou solenemente no salão principal que não mais comeria pão mortal por ser uma criatura maldita das trevas, os guerreiros §2Azarasquel§0 e §2Reznozada§0 decidiram tirar a história a limpo.",
                        "Cercando a poltrona do Conde ao lado do fiel leãozinho dourado §6Javidizineo§0, §2Azarasquel§0 puxou um frasco de água benta da capela, enquanto §2Reznozada§0 escondia um objeto brilhante atrás das costas.",
                        "\"Se tu és mesmo um demônio nosferatu saído das páginas antigas\", declarou §2Azarasquel§0 com solenidade inquisitorial, \"então o toque da prata consagrada reduzirá tua carne imunda a cinzas fumegantes!\"",
                        "Num golpe rápido, §2Reznozada§0 encostou no pescoço pálido de §4Jodevil§0 uma velha colher de sobremesa de prata que surrupiara da copa.",
                        "Todos prenderam a respiração esperando o clarão profano — mas não houve fogo nem fumaça: cinco segundos depois, porém, uma mancha vermelha brotou na pele e §4Jodevil§0 começou a se coçar desesperadamente como um cão com pulgas!",
                        "\"Tira essa colher oxidada de perto de mim!\", gritava o Conde coçando a nuca com as duas mãos enquanto §2Reznozada§0 ria de rolar no tapete. \"O meu vampirismo me deu alergia cutânea a talher mal lavado!\""
                }));

        // CAPÍTULO XV
        list.add(new ChapterSpec(15, "XV", "Ato II: O Meio",
                "O Desafio do Horizonte",
                "A reunião da corte nos telhados diante do nascer do astro-rei.",
                new String[]{
                        "Ferido em sua honra pelas gargalhadas de §2Reznozada§0, §4Conde Jodevil§0 declarou que provaria a autenticidade de sua maldição enfrentando o juiz supremo de todos os imortais: o sol da manhã.",
                        "Meia hora antes da alvorada, o engenhoso §8Corvo§0 já havia subido ao telhado da taverna com um caderno de couro debaixo do braço e um saco de moedas tilintantes, abrindo a banca oficial de apostas da corte.",
                        "\"Façam seu jogo, cavalheiros e damas!\", gritava §8Corvo§0 empoleirado na chaminé. \"Pagando cinco para um se o Conde virar uma bola de fogo dramática! Pagando dois para um se ele só espirrar e pedir um guarda-sol!\"",
                        "Sob a sombra segura dos arcos de pedra, §9Asminn§0, §6ArzanLion§0, §dKikkawa§0, §dLuwuana§0, §2Azarasquel§0, §2Reznozada§0 e até o sonolento §5AsterZZZz§0 observavam em silêncio tenso.",
                        "De queixo erguido e manto fechado, §4Jodevil§0 caminhou até a linha exata onde terminava a sombra do telhado, aguardando que o disco dourado emergisse das águas do oceano.",
                        "Quando o primeiro feixe de luz solar atravessou o pátio e atingiu em cheio a bota e a fronte pálida do Conde, toda a cidadela prendeu o fôlego."
                }));

        // CAPÍTULO XVI
        list.add(new ChapterSpec(16, "XVI", "Ato II: O Meio",
                "O Julgamento da Aurora",
                "Onde a luz da manhã revela o verdadeiro preço do sangue imortal.",
                new String[]{
                        "\"Ninguém levou o ouro da banca de §8Corvo§0 naquela manhã, meu caro §9Asminn§0\", lamentou §4Jodevil§0 cobrindo os olhos com o antebraço. \"Pois a receita torta de §6ArzanLion§0 negou-me até mesmo a dignidade de uma morte rápida!\"",
                        "\"Nos romances de Bram Stoker e nas crônicas de Paris, o sol é um carrasco majestoso que consome o vampiro numa pira de tragédia instantânea. Mas em mim? O sol agiu como um cozinheiro entediado!\"",
                        "Assim que a claridade tocou a pele do Conde no pátio, subiu de seu colarinho e da ponta de sua bota um chiado lento e gorduroso — §o'tsssss'§0 — idêntico ao de uma tira de toucinho jogada numa frigideira morna.",
                        "\"Compreendi naquele instante a lei cruel do meu §4Sangue Duvidoso§0\", suspirou o Conde. \"A cada minuto inteiro que permaneço sob o sol aberto, o astro-rei cozinha minha dignidade e arranca uma fração exata da minha força vital!\"",
                        "\"Mas o verdadeiro horror que me fez saltar de volta para a sombra como um gato escaldado foi sentir no peito o tic-tac da §4Centésima Centelha§0!\"",
                        "\"Pois a cada minuto que o sol me drena, as runas borradas por §5AsterZZZz§0 rolam os dados invisíveis do abismo: há sempre uma chance em cem de o feitiço entrar em curto-circuito e explodir meu corpo em cinzas na mesma hora!\""
                }));

        // CAPÍTULO XVII
        list.add(new ChapterSpec(17, "XVII", "Ato II: O Meio",
                "O Guardião dos Cofres",
                "O confronto de autoridade entre o Senhor das Sombras e seu servo eterno.",
                new String[]{
                        "Ao cair da noite seguinte, ainda sentindo o cheiro de couro chamuscado nas botas, §4Conde Jodevil§0 desceu ao salão dos baús decidido a restaurar seu prestígio diante do único ser que não rira dele no pátio: o guardião §6Jacinto§0.",
                        "Imortal, calado e dotado daquela paciência mineral de quem passa a eternidade separando minérios e organizando estoques, §6Jacinto§0 limpava metodicamente a fileira central de arcas.",
                        "\"Detém-te, autômato das areias!\", trovoou §4Jodevil§0 abrindo a capa e sacando do cinturão sua relíquia mais sagrada e devastadora: o martelo das tempestades, o lendário §bMjölnir que não volta§0!",
                        "\"Olha bem para esta arma divina e reconhece o teu soberano noturno!\", bradou o Conde, fazendo um floreio teatral tão exagerado com o braço que acabou estendendo o cabo do martelo bem na frente do peito do guardião.",
                        "Treinado pelos séculos para receber ferramentas de trabalho entregues pelos patrões, §6Jacinto§0 fechou a mão calejada no cabo do §bMjölnir§0 num reflexo infalível, prendeu-o no cinto e bateu continência!",
                        "\"Agradecido pelo equipamento novo, patrão §4Jodevil§0!\", declarou §6Jacinto§0 com voz solene. \"Já dei entrada no almoxarifado da firma sob minha guarda pessoal!\""
                }));

        // CAPÍTULO XVIII
        list.add(new ChapterSpec(18, "XVIII", "Ato II: O Meio",
                "A Vontade Inabalável",
                "O duelo de fogo e orgulho nos corredores da tesouraria.",
                new String[]{
                        "Um silêncio sepulcral caiu sobre os cofres antes que §4Jodevil§0 perdesse completamente a compostura aristocrática: \"Devolve-me esse martelo agora mesmo, §6Jacinto§0! Eu estava apontando para ti, não te presenteando!\"",
                        "§6Jacinto§0 apenas deu meia-volta e continuou guardando pedras nos baús com o §bMjölnir§0 pendurado na cintura. Fora de si, §4Jodevil§0 puxou a espada, mas o golpe resvalou como brisa na carcaça imortal do funcionário.",
                        "Em desespero, o Conde derramou óleo de lanterna no chão e ateou fogo aos pés do guardião para forçá-lo a soltar a arma — mas §6Jacinto§0 caminhou tranquilamente no meio das labaredas sem chamuscar um único fio da roupa!",
                        "\"Ei, patrão\", repreendeu §6Jacinto§0 sacudindo a cinza do ombro enquanto §9Asminn§0, §2Reznozada§0 e §8Corvo§0 já espiavam da porta segurando o riso, \"me bater ou tacar fogo não vai ajudar nada no processo!\"",
                        "\"Se o senhor quiser um presente do meu inventário\", completou o guardião cruzando os braços, \"vai ter que pedir no diálogo, com muita educação e carinho, e contar com a minha boa vontade!\"",
                        "E assim as pedras da cidadela testemunharam o temível Conde das Trevas seguindo o organizador de baús corredor afora, de mãos postas, implorando: \"Ah, vai, §6Jacinto§0, por favorzinho, devolve o meu §bMjölnir§0...\""
                }));

        // =========================================================================
        // ATO III — O FIM: A REDENÇÃO NOTURNA E O REINADO IMORTAL (Cap. XIX a XXVII)
        // =========================================================================

        // CAPÍTULO XIX
        list.add(new ChapterSpec(19, "XIX", "Ato III: O Fim",
                "O Oráculo da Torre",
                "O conselho silencioso das criaturas da noite sob a tempestade.",
                new String[]{
                        "Depois de trinta e duas súplicas humilhantes no corredor, §4Conde Jodevil§0 finalmente ouviu o tilintar metálico do §bMjölnir§0 sendo devolvido por §6Jacinto§0 e recolheu-se, exausto, ao alto de sua torre.",
                        "Atirou-se na poltrona diante da janela aberta para a chuva, perguntando-se se valera a pena trocar a mortalidade por uma existência de alergia a talheres, insolação chiante e sermões de almoxarifado.",
                        "Antes que a melancolia o vencesse, porém, um baque suave ecoou sobre a escrivaninha: o gato místico §bSenhor Cummins§0 saltara do peitoril e, com um olhar de sabedoria ancestral, empurrou o tinteiro com a pata até encostá-lo na mão do Conde.",
                        "Ao mesmo tempo, o pequeno morcego §8Juninho§0 desceu do teto voando torto e depositou sobre o colo de §4Jodevil§0 um frasquinho morno de §fBat Leite§0.",
                        "§4Jodevil§0 olhou para os olhos dourados de §bSenhor Cummins§0 e para as orelhas peludas de §8Juninho§0, e um sorriso genuíno substituiu sua carranca dramática.",
                        "\"Tendes razão, meus pequenos irmãos da sombra\", murmurou o Conde acariciando o gato que ronronava alto. \"Um verdadeiro lorde não se mede pela perfeição do feitiço, mas pela lealdade daqueles que dividem a noite com ele.\""
                }));

        // CAPÍTULO XX
        list.add(new ChapterSpec(20, "XX", "Ato III: O Fim",
                "O Édito do Alquimista",
                "O parecer hermético de ArzanLion acerca das cláusulas do ritual.",
                new String[]{
                        "§8Documento selado deixado sob a porta de Conde Jodevil pelo Grão-Alquimista ArzanLion.§0 \"Ilustríssimo cliente §4Conde Jodevil§0, acuso o recebimento das tuas trinta e sete cartas exigindo reparação moral e material.\"",
                        "\"Reclamas no item doze que não consegues atravessar fechaduras em forma de neblina, e no item vinte e quatro que tua bota solta fumaça com cheiro de toucinho sempre que pisas no sol do meio-dia.\"",
                        "\"Cumpre-me recordar-te os termos exatos do nosso contrato verbal celebrado perante a testemunha canina §eVitória§0: pediste para tornar-te um imortal pálido que jamais envelhece e que teme a luz do sol como nos livros!\"",
                        "\"Ora, estás pálido como cal, não terás mais fios brancos e tens tanto pavor da §4Centésima Centelha§0 solar que atravessas o pátio correndo debaixo das marquises! O contrato foi cumprido com louvor!\"",
                        "\"Isto posto, comunico que o gabinete de §6ArzanLion§0 opera sob a cláusula irrevogável de §oSem Reembolso§0 — especialmente porque o queijo curado do pagamento já foi inteiramente digerido.\"",
                        "\"Como brinde de cortesia ao nosso primeiro vampiro oficial, deixo-te esta caixa contendo um elegante chapéu de palha camponês de aba larga. Usa-o com garbo nos dias de verão! — §6ArzanLion§0.\""
                }));

        // CAPÍTULO XXI
        list.add(new ChapterSpec(21, "XXI", "Ato III: O Fim",
                "O Eclipse de Sangue",
                "Quando as hordas da calada cercaram a cidadela e o medo tomou os mortais.",
                new String[]{
                        "As piadas da corte sobre o chapéu de palha de §4Jodevil§0 cessaram abruptamente na última sexta-feira do mês, quando o céu ao entardecer não ficou dourado, mas tingiu-se de um púrpura espesso e sufocante.",
                        "Um antigo Eclipse de Sangue engoliu os astros e, das fendas abissais além da costa, subiu uma maré de criaturas famintas da escuridão que cercou todas as muralhas da cidadela.",
                        "O vendaval apagou os archotes das torres de vigia. No breu absoluto, os olhos dos mortais não conseguiam enxergar um palmo à frente, e o som de garras escalando a pedra fez soar o sino de alarme.",
                        "Na praça d'armas, §9Asminn§0, §2Azarasquel§0, §2Reznozada§0, §dKikkawa§0, §dLuwuana§0, §8Corvo§0 e §6ArzanLion§0 formaram um círculo defensivo, mas precisavam de alguém que enxergasse através daquela treva espessa.",
                        "Foi então que os passos firmes de botas ecoaram na escadaria central: sem o sol no céu para drenar seu sangue ou ameaçá-lo com a §4Centésima Centelha§0, a meia-noite eterna do eclipse era o território natural de §4Conde Jodevil§0!",
                        "No alto do parapeito norte, com a capa negra esvoaçando na tempestade e as pupilas acesas no escuro, o Conde ergueu sua voz sobre o rugido dos monstros."
                }));

        // CAPÍTULO XXII
        list.add(new ChapterSpec(22, "XXII", "Ato III: O Fim",
                "A Dança nas Muralhas",
                "O triunfo épico de Conde Jodevil liderando a defesa sob a meia-noite.",
                new String[]{
                        "\"Firmes na linha, meus irmãos!\", trovoou §4Conde Jodevil§0 no alto das ameias, empunhando com glória o reconquistado §bMjölnir§0 enquanto os relâmpagos escarlates desenhavam sua silhueta.",
                        "\"Eles vieram achando que a escuridão lhes pertence — mas esqueceram que esta fortaleza já tem o seu Príncipe da Noite!\"",
                        "Desta vez sem escorregar em nenhuma pedra e passando longe das roseiras, §4Jodevil§0 saltou para a vanguarda da muralha leste, enxergando no breu cada investida inimiga e cantando as posições para a corte!",
                        "Guiados pela visão noturna do Conde, as lâminas de §2Azarasquel§0 e §2Reznozada§0 — ladeados pelo feroz §6Javidizineo§0 — cortavam a primeira fila, enquanto §9Asminn§0, §dKikkawa§0 e §dLuwuana§0 disparavam das torres e §8Corvo§0 cobria os flancos.",
                        "Mais atrás, §6ArzanLion§0 arremessava frascos explosivos ao lado de §eVitória§0, e na porta da tesouraria o implacável §6Jacinto§0 barrava qualquer monstro que tentasse chegar perto dos baús da firma!",
                        "Durante seis horas de treva cerrada, §4Conde Jodevil§0 lutou sem recuar um único passo, transformando sua maldição torta no escudo invencível que salvou todas as almas da cidadela."
                }));

        // CAPÍTULO XXIII
        list.add(new ChapterSpec(23, "XXIII", "Ato III: O Fim",
                "O Banquete do Eclipse",
                "A celebração triunfal dos defensores no grande salão de inverno.",
                new String[]{
                        "Quando a última criatura foi arremessada de volta ao mar e as tochas do grande salão foram reacendidas, a corte inteira reuniu-se em torno da mesa de carvalho para celebrar a vitória — num verdadeiro caos digno da irmandade.",
                        "Antes mesmo do primeiro discurso, §6ArzanLion§0 já puxava um pergaminho tentando cobrar de §4Jodevil§0 uma 'taxa adicional de eficiência em combate noturno', sendo imediatamente vaiado por todos os presentes.",
                        "Debaixo da mesa, a cadelinha §eVitória§0 e o leãozinho §6Javidizineo§0 travavam uma operação conjunta perfeitamente coordenada para furtar uma travessa inteira de pernil assado sem que §2Azarasquel§0 percebesse.",
                        "Na outra ponta, o monge §5AsterZZZz§0 — que dormira durante metade do Eclipse de Sangue — acabara de despertar apenas para apoiar a cabeça dentro da fruteira de prata e voltar a roncar entre as maçãs.",
                        "§9Lorde Asminn§0 bateu na taça pedindo silêncio, enquanto §dLady Kikkawa§0 (com §bPipira§0 dando piruetas no ar), §dDama Luwuana§0, §2Reznozada§0 e §8Corvo§0 enchiam o cálice do Conde com o melhor suco de beterraba da safra.",
                        "\"Ao §4Conde Jodevil§0!\", proclamou §9Asminn§0 sob aplausos ensurdecedores. \"Que entrou na adega buscando a vaidade dos vampiros antigos e saiu dela como o maior herói das nossas madrugadas!\""
                }));

        // CAPÍTULO XXIV
        list.add(new ChapterSpec(24, "XXIV", "Ato III: O Fim",
                "O Castelo sobre o Mar",
                "A grande procissão noturna rumo à nova fortaleza nas falésias.",
                new String[]{
                        "Para honrar seu título definitivo de Senhor das Sombras, §4Conde Jodevil§0 decidiu transferir seus aposentos para o ponto mais alto do Castelo sobre o Mar — mas a mudança na madrugada seguinte foi mais difícil que a própria guerra.",
                        "O novo leito gótico do Conde, entalhado em madeira maciça com dossel rubro, simplesmente entalou na curva da escadaria helicoidal do segundo andar!",
                        "\"Empurra pela esquerda, §2Reznozada§0! Cuidado com o verniz da minha cabeceira, §8Corvo§0!\", gritava §4Jodevil§0 de cima do corrimão, usando o chapéu de palha para abanar o suor enquanto coordenava a manobra.",
                        "Lá embaixo, quando pediram ajuda ao forte §6Jacinto§0 para erguer as caixas de livros, o guardião recusou solenemente: disse que suas mãos estavam ocupadas segurando o §bMjölnir§0 de prontidão e que mudança não constava no comando de organização!",
                        "Quando finalmente conseguiram levar o leito e a grande arca até o quarto real no topo da torre, encontraram o gato §bSenhor Cummins§0 já deitado bem no centro do travesseiro principal, recusando-se a ceder lugar até ganhar carinho.",
                        "Exausto, mas radiante em seu novo santuário cercado pelas ondas do mar, §4Jodevil§0 fechou as pesadas cortinas contra a brisa da manhã: finalmente tinha um castelo à altura da sua lenda."
                }));

        // CAPÍTULO XXV
        list.add(new ChapterSpec(25, "XXV", "Ato III: O Fim",
                "A Confissão a Asminn",
                "O diálogo íntimo entre o imortal e seu cronista ao findar das velas.",
                new String[]{
                        "A última vela do candelabro já derretia sobre a prata quando o barulho da festa lá embaixo silenciou, deixando apenas §4Conde Jodevil§0 e §9Lorde Asminn§0 na câmara alta da torre.",
                        "Por um longo momento, §4Jodevil§0 não fez poses teatrais nem ajeitou a capa; caminhou até a janela entreaberta e olhou para o pátio silencioso com uma expressão de tristeza tão profunda que fez §9Asminn§0 pousar a pena.",
                        "\"Sabes qual é o único peso verdadeiro desta minha maldição torta, §9Asminn§0?\", sussurrou o Conde com a voz embargada. \"Não é o chiado na bota sob o sol, nem o gosto de beterraba, nem o medo da §4Centésima Centelha§0.\"",
                        "\"Hoje todos vocês estão aqui comigo: tu escreves minhas crônicas, §6ArzanLion§0 reclama do queijo, §dKikkawa§0 e §dLuwuana§0 riem no jardim, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0 brindam no salão...\"",
                        "\"Mas o feitiço me tornou imortal, meu irmão. O que será de mim daqui a cinquenta ou cem anos, quando o tempo levar a corte e este Castelo sobre o Mar ficar mergulhado num silêncio eterno?\"",
                        "Uma lágrima escorreu pela face pálida de §4Jodevil§0: \"Juro-te, §9Asminn§0: no dia em que eu ficar sozinho nestes corredores, a saudade de vocês doerá tanto no meu peito que serei capaz de beber qualquer veneno da adega apenas para esquecer quem sou!\""
                }));

        // CAPÍTULO XXVI
        list.add(new ChapterSpec(26, "XXVI", "Ato III: O Fim",
                "A Alvorada se Aproxima",
                "As últimas anotações do cronista Asminn antes do nascer do sol.",
                new String[]{
                        "§8Nota Final do Cronista Lorde Asminn — Alvorada de 25 de Outubro.§0 Engoli o nó na garganta diante daquelas palavras proféticas do meu amigo e ergui-me da escrivaninha para tocar-lhe o ombro frio.",
                        "\"Enquanto estas palavras existirem, §4Jodevil§0\", prometi-lhe solenemente, \"tu jamais estarás sozinho. Guardarei estes vinte e sete tomos na arca ao lado do teu leito para que, mesmo que os séculos passem, nossa voz sempre te responda no escuro.\"",
                        "Nesse exato instante, a primeira fresta de sol da manhã atravessou a cortina da janela e tocou a ponta da bota direita do Conde.",
                        "Imediatamente subiu do couro um chiado agudo — §o'tsssss'§0 — acompanhado de um fiozinho de fumaça branca! \"Por todos os abismos!\", saltou §4Jodevil§0 puxando o pé para trás da poltrona. \"Fecha essa cortina antes que a §4Centésima Centelha§0 me pegue!\"",
                        "Rindo juntos do susto matinal, ajudei-o a lacrar o veludo da janela enquanto o morcego §8Juninho§0 se acomodava no dossel e §bSenhor Cummins§0 subia aos pés do leito real.",
                        "Recolho agora meus tinteiros e fecho a tampa desta arca ao lado da cama: a noite terminou, e o Conde da Meia-Noite precisa descansar."
                }));

        // CAPÍTULO XXVII
        list.add(new ChapterSpec(27, "XXVII", "Ato III: O Fim",
                "Epílogo: O Imortal",
                "O juramento eterno de Conde Jodevil que encerra o primeiro ciclo.",
                new String[]{
                        "\"Aos que abrirem este vigésimo sétimo tomo na arca ao lado do meu leito\", escreveu o próprio §4Conde Jodevil§0 com tinta carmesim na última página, \"deixo a minha bênção das sombras.\"",
                        "\"Se caminhardes por estas galerias quando a lua estiver alta, erguei uma taça à nossa corte: a §9Lorde Asminn§0, §6ArzanLion§0, §5AsterZZZz§0, §dLady Kikkawa§0, §dDama Luwuana§0, §2Azarasquel§0, §2Reznozada§0 e §8Corvo§0.\"",
                        "\"Se cruzardes com o imortal §6Jacinto§0 nos corredores, nunca lhe entregueis vossa arma favorita por engano — e, se o fizerdes, lembrai-vos de que o fogo não funciona e só o carinho devolve o que é vosso.\"",
                        "\"E se algum dia, num futuro distante, encontrardes este castelo silencioso e ouvirdes o choro desafinado de um violino ecoando pelas torres vazias... sabei que o meu coração ainda bate por aqueles que amei.\"",
                        "\"Pois a eternidade pode ser longa e o sangue pode ser duvidoso, mas a lenda que escrevemos juntos jamais conhecerá o fim.\"",
                        "§4§l— FIM DO PRIMEIRO CICLO: CRÔNICAS DO SANGUE DUVIDOSO —"
                }));

        return list;
    }
}
