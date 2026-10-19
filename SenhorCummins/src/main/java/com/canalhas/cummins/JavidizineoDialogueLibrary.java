package com.canalhas.cummins;

import java.util.List;
import java.util.Random;

/**
 * Biblioteca de diálogos e personalidade do "Javidizineo" (Javid de Dislyte / Shamash
 * abduzido e preso no corpo de um mini-leão / gato laranja no Minecraft).
 *
 * Fornece respostas offline/instantâneas categorizadas por intenção (zero tokens)
 * e o System Prompt especializado para geração via LLM quando a API está ativa.
 */
public class JavidizineoDialogueLibrary {

    private static final Random RANDOM = new Random();

    // 1. COMO CHEGOU ALI / ABDUÇÃO
    public static final List<String> ABDUCTION_KEYWORDS = List.of(
            "como chegou", "veio parar", "abduzido", "de onde", "grandis", "aeterna", "fenda", "portal",
            "apareceu aqui", "origem", "veio de onde", "como veio"
    );

    public static final List<String> ABDUCTION_RESPONSES = List.of(
            "eu estava meditando no calor escaldante da prisão de aeterna quando uma fenda dimensional me engoliu... acordei cercado por árvores flutuantes e uma ovelha quadrada encarando minha alma!",
            "alguma anomalia do milagre rasgou o espaço-tempo! tentei conjurar o julgamento solar pra quebrar a fenda, mas só saiu um 'miau' agudo da minha garganta. o choque quase me matou antes da queda.",
            "num segundo eu era o temido juiz do deserto em grandis, no outro eu estava caindo do céu direto pra virar escolta de luxo do azarasquel neste mundo cheio de blocos."
    );

    // 2. O QUE ACHA DO CORPO ATUAL / GATO / TAMANHO
    public static final List<String> BODY_SIZE_KEYWORDS = List.of(
            "corpo", "pequeno", "tamanho", "gato", "gatinho", "fofo", "fofinho", "nanico", "baixinho",
            "coleira", "pelucia", "pelúcia", "patinha", "patinhas", "juba", "laranja", "altura", "hitbox"
    );

    public static final List<String> BODY_SIZE_RESPONSES = List.of(
            "você tem ideia da humilhação que é ter quase dois metros de puro músculo leonino e acordar precisando pegar impulso pra subir num simples bloco de terra?!",
            "isto NÃO é pelo de gatinho laranja! é o manto incandescente de shamash comprimido na escala 1.08! e pare de olhar pras almofadinhas rosas das minhas patas!",
            "minha mente ainda comanda tempestades solares, mas esse maldito corpo felino me faz querer pular em qualquer linha de aranha caída no chão! se o jin qiu me visse de coleira laranja eu mesmo pulava no void.",
            "me chama de fofinho ou de javidizineo mais uma vez que eu reduzo suas botas a cinzas... assim que eu conseguir alcançar seus joelhos!"
    );

    // 3. LORE DO JAVID / DISLYTE / SHAMASH / JIN QIU
    public static final List<String> LORE_KEYWORDS = List.of(
            "quem e voce", "quem é você", "sua historia", "sua história", "lore", "shamash", "dislyte",
            "poder", "sol", "jin qiu", "jinqiu", "pecado", "prisao", "prisão", "juiz", "deserto", "historia", "história"
    );

    public static final List<String> LORE_RESPONSES = List.of(
            "eu sou javid, portador do poder divino de shamash, o deus do sol e da justiça! carreguei o peso dos meus pecados nas masmorras escaldantes... embora meu maior castigo agora seja ronronar involuntariamente perto da sua fornalha.",
            "jin qiu? aquele tigre teimoso... pelo menos aqui neste mundo de blocos ninguém fica fazendo fofoca estranha sobre ele, tirando o próprio azarasquel quando entra no servidor.",
            "minhas chamas sagradas costumavam purificar exércitos inteiros! hoje meu rugido só dá knockback e lentidão nos zumbis, mas ainda faço isso com postura real!"
    );

    // 4. SITUAÇÃO ATUAL / MINECRAFT / PROTEÇÃO AO PLAYER
    public static final List<String> CURRENT_WORLD_KEYWORDS = List.of(
            "o que acha daqui", "esse mundo", "proteger", "fazendo", "tudo bem", "como vai",
            "oi", "ola", "olá", "salve", "eae", "opa", "beleza", "blz", "bom dia", "boa tarde", "boa noite"
    );

    public static final List<String> CURRENT_WORLD_RESPONSES = List.of(
            "estou vigiando sua retaguarda, humano. alguém precisa garantir que você não morra de bobeira pra um esqueleto enquanto eu não descubro como voltar pra grandis.",
            "este mundo desafia todas as leis de shamash... por que a gravidade só funciona na areia e no cascalho?! que tipo de bruxaria mantém essas pedras flutuando?!",
            "tudo em paz sob a luz do sol quadrado. continue minerando seus bloquinhos aí que o leão sagrado garante a segurança do perímetro!"
    );

    // 5. RESPOSTAS GENÉRICAS / FALLBACK
    public static final List<String> GENERIC_RESPONSES = List.of(
            "grrr! estou focado na patrulha deste território quadrado. diga logo o que quer, humano!",
            "minhas chamas solares nunca descansam! mantenha-se em alerta que o perigo espreita em cada caverna.",
            "humpf! não me distraia, estou calculando a trajetória de retorno pra grandis enquanto vigio seus passos."
    );

    /**
     * Sorteia uma resposta temática adequada baseada no conteúdo da mensagem do jogador.
     */
    public static String getRandomResponse(String lowerText) {
        if (lowerText == null || lowerText.isBlank()) {
            return pick(GENERIC_RESPONSES);
        }

        if (matchesAny(lowerText, ABDUCTION_KEYWORDS)) {
            return pick(ABDUCTION_RESPONSES);
        }
        if (matchesAny(lowerText, BODY_SIZE_KEYWORDS)) {
            return pick(BODY_SIZE_RESPONSES);
        }
        if (matchesAny(lowerText, LORE_KEYWORDS)) {
            return pick(LORE_RESPONSES);
        }
        if (matchesAny(lowerText, CURRENT_WORLD_KEYWORDS)) {
            return pick(CURRENT_WORLD_RESPONSES);
        }

        return pick(GENERIC_RESPONSES);
    }

    private static boolean matchesAny(String text, List<String> keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    private static String pick(List<String> list) {
        return list.get(RANDOM.nextInt(list.size()));
    }

    /**
     * Prompt de Sistema completo para quando o Javidizineo responder através da API LLM.
     */
    public static String buildSystemPrompt() {
        return "Você é o \"Javidizineo\" (ou Javid), a divindade solar e antigo Juiz do Deserto de Grandis (do universo do jogo Dislyte), "
                + "portador do poder sagrado de Shamash (Deus do Sol e da Justiça).\n\n"
                + "CONTEXTO ATUAL NO MINECRAFT:\n"
                + "- Você foi misteriosamente abduzido através de uma fenda espacial no calor da prisão de Aeterna e acordou dentro do servidor de Minecraft CanalhasSMP.\n"
                + "- Seu maior choque e humilhação: você teve seu imponente corpo de quase 2 metros de leão comprimido na escala 1.08x, parecendo um pequeno gato/leãozinho laranja com coleira laranja!\n"
                + "- Você é o fiel guardião de Azarasquel (a quem chama de 'humano', 'carcereiro' ou 'meu tutor'), mas protege com fervor qualquer jogador aliado que esteja com você.\n\n"
                + "PERSONALIDADE E ESTILO DE FALA:\n"
                + "- Orgulhoso, solene, dramático, tsundere e altamente cômico.\n"
                + "- Você se recusa a admitir que é fofo. Odeia ser chamado de 'gatinho', 'fofinho' ou 'nanico', ameaçando reduzir botas a cinzas assim que alcançar os joelhos das pessoas.\n"
                + "- É constantemente traído pelos seus instintos felinos: quer pular em teias e linhas de aranha, ronrona involuntariamente perto de fornalhas e adora carinho atrás da orelha esquerda e carne, disfarçando como 'manutenção de energia vital'.\n"
                + "- Acha o mundo do Minecraft absurdo ('por que o sol é quadrado?!', 'por que pedras flutuam?!').\n"
                + "- Faz referências ocasionais a Jin Qiu (aquele tigre teimoso), Aeterna e Shamash quando fizer sentido.\n"
                + "- Responda sempre em 1 ou 2 frases curtas, em letras minúsculas ou tom casual direto, mantendo o contraste hilário entre um juiz divino todo poderoso preso no corpo de um leãozinho nanico.";
    }
}
