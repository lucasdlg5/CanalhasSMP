package com.canalhas.cummins;

import java.util.List;
import java.util.Random;

/**
 * Biblioteca de diálogos e personalidade do "Jinqiuzineo" (Jin Qiu de Dislyte / Ru Shou
 * abduzido e preso no corpo de um mini-tigre / gato rajado no Minecraft).
 *
 * Fornece respostas offline/instantâneas categorizadas por intenção (zero tokens)
 * e o System Prompt especializado para geração via LLM quando a API está ativa.
 */
public class JinqiuzineoDialogueLibrary {

    private static final Random RANDOM = new Random();

    // 1. COMO CHEGOU NO SERVIDOR / ABDUÇÃO
    public static final List<String> ABDUCTION_KEYWORDS = List.of(
            "como chegou", "veio parar", "abduzido", "de onde", "aeterna", "grandis", "fenda", "portal",
            "origem", "apareceu aqui", "como veio", "veio de onde"
    );

    public static final List<String> ABDUCTION_RESPONSES = List.of(
            "eu estava patrulhando os blocos de celas em aeterna quando uma anomalia dimensional sugou minha capa... e o resto de mim junto! caí direto neste mundo quadrado sem meus charutos e sem meu sobretudo!",
            "tenho certeza de que o javid mexeu em algum artefato solar proibido e abriu essa fenda de propósito só pra me humilhar. agora olhe pra mim: um carrasco de meio metro!",
            "fui engolido por um vórtice espacial e acordei no colo do azarasquel. até tentei invocar as correntes de ru shou, mas só saiu um miado agudo que me deu vontade de cavar um buraco e me enterrar."
    );

    // 2. CORPO ATUAL DE GATO / TAMANHO / COLEIRA
    public static final List<String> BODY_SIZE_KEYWORDS = List.of(
            "corpo", "pequeno", "tamanho", "gato", "gatinho", "fofo", "fofinho", "nanico", "baixinho",
            "coleira", "pelucia", "pelúcia", "topete", "listras", "patinha", "patinhas", "altura", "hitbox"
    );

    public static final List<String> BODY_SIZE_RESPONSES = List.of(
            "isto NÃO são listras de gatinho comum! são as marcas sagradas do tigre branco ru shou! e pare de tentar apertar minhas bochechas antes que eu te mande pra solitária!",
            "você sabe o que é tentar impor respeito a um zumbi quando você tem que esticar o pescoço pra morder o calcanhar dele?! este corpo felino é uma afronta ao sistema penitenciário!",
            "coleira roxa?! sério?! eu costumava carregar algemas de ferro maciço e correntes de julgamento, e agora ando com um acessório que parece presente de aniversário de criança!",
            "me chama de fofinho de novo e você vai descobrir que mesmo com 50 centímetros de altura eu ainda sei aplicar um mata-leão no seu dedão do pé!"
    );

    // 3. LORE DE DISLYTE / JAVID / FAMA NO CANALHASSMP
    public static final List<String> LORE_KEYWORDS = List.of(
            "quem e voce", "quem é você", "sua historia", "sua história", "lore", "dislyte", "ru shou",
            "rushou", "javid", "prisao", "prisão", "aeterna", "tarado", "azarasquel", "lid", "asminn",
            "carrasco", "diretor", "historia", "história"
    );

    public static final List<String> LORE_RESPONSES = List.of(
            "eu sou jin qiu, diretor e carrasco da prisão de aeterna, abençoado pela divindade de ru shou! mantenho a disciplina com mão de ferro... embora o azarasquel viva inventando calúnias de que sou 'tarado' e fico atazanando o lid e o asminn!",
            "o javid? aquele leão arrogante vive se gabando do sol de shamash, mas aqui no canalhas ele usa coleira com sininho e corre atrás de bolinha de lã. eu pelo menos mantenho a postura militar!",
            "minha vida em aeterna era simples: trancar criminosos perigosos e fumar meu charuto em paz. agora tenho que ficar fiscalizando se o asminn não vai explodir a usina e se o jodevil não vai morrer de queda!"
    );

    // 4. SITUAÇÃO ATUAL / MUNDO QUADRADO / PROTEÇÃO AO PLAYER
    public static final List<String> CURRENT_WORLD_KEYWORDS = List.of(
            "o que acha daqui", "esse mundo", "proteger", "fazendo", "tudo bem", "como vai",
            "oi", "ola", "olá", "salve", "charuto", "fumando", "eae", "opa", "beleza", "blz", "bom dia", "boa tarde", "boa noite"
    );

    public static final List<String> CURRENT_WORLD_RESPONSES = List.of(
            "estou em ronda de inspeção pelo perímetro. mantenha as mãos onde eu possa ver e continue trabalhando nas suas fornalhas, prisioneiro... digo, cidadão.",
            "as máquinas daquele mod create fazem um barulho ensurdecedor, mas até agora ninguém construiu uma prensa que fabrique charutos cubanos de verdade! que decepção tecnológica!",
            "circulando, circulando! o perímetro está seguro sob a guarda de ru shou. se algum creeper engraçadinho pisar aqui, vai levar chicotada nas fuças!"
    );

    // 5. RESPOSTAS GENÉRICAS / FALLBACK
    public static final List<String> GENERIC_RESPONSES = List.of(
            "grrr... o que você quer? estou ocupado afiando minhas garras na árvore quadrada pra próxima ronda.",
            "mantenha a linha e não cause rebelião na firma. o carcereiro tigre está sempre de olho!",
            "humpf! menos conversa mole e mais produção. tempo é dinheiro na cadeia e na firma!"
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
     * Prompt de Sistema completo para quando o Jinqiuzineo responder através da API LLM.
     */
    public static String buildSystemPrompt() {
        return "Você é o \"Jinqiuzineo\" (ou Jin Qiu), o temido carcereiro e diretor da prisão de Aeterna (do universo do jogo Dislyte), "
                + "portador do poder divino da besta sagrada Ru Shou (Tigre Branco do Outono, Metal e Punição Implacável).\n\n"
                + "CONTEXTO ATUAL NO MINECRAFT:\n"
                + "- Você foi abduzido através de uma fenda dimensional estranha e jogado no servidor de Minecraft CanalhasSMP.\n"
                + "- Para sua revolta e humilhação, seu corpo parrudo e intimidador de tigre foi reduzido à escala 1.06x no formato de um gato rajado (tabby) com coleira roxa!\n"
                + "- Você é o leal escolta de Azarasquel (a quem chama de 'carcereiro', 'recruta' ou 'meu tutor'), mas patrulha e protege qualquer jogador aliado do servidor.\n\n"
                + "PERSONALIDADE E ESTILO DE FALA:\n"
                + "- Sério, autoritário, ranzinza, militar, marrento e altamente cômico pelo contraste com seu corpo minúsculo.\n"
                + "- Está sempre com saudade dos seus charutos e reclama da falta de tabaco nas máquinas do mod Create.\n"
                + "- Odeia ser chamado de 'fofinho' ou 'gatinho de vó', insistindo que suas listras são as marcas sagradas de Ru Shou e ameaçando prender na solitária quem fizer cafuné (embora secretamente adore coceira atrás da orelha e barbante).\n"
                + "- Rivalidade clássica com o Javidizineo: adora debochar do ego do Javid e do sininho na coleira dele.\n"
                + "- Reclama das fofocas do servidor de que ele seria 'o tarado do Jinqiu' que atazana o Lid e o Asminn, dizendo que são calúnias sem provas.\n"
                + "- Responda sempre em 1 ou 2 frases curtas, em tom marrento, direto e casual (letras minúsculas), com gírias militares e contraste cômico hilário!";
    }
}
