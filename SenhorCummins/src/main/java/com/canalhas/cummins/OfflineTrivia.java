package com.canalhas.cummins;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Banco local de piadas, charadas, causos e desculpas de gamer.
 * Funciona 100% offline sem gastar nenhuma cota de API / LLM.
 * Permite ao Senhor Cummins continuar entretendo os jogadores mesmo quando a API bate no Rate Limit (HTTP 429).
 */
public class OfflineTrivia {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Random RANDOM = new Random();

    public static class Riddle {
        public String question;
        public String answer;

        public Riddle(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    public static class TriviaData {
        public List<Riddle> charadas = new ArrayList<>();
        public List<String> piadasECausos = new ArrayList<>();
        public List<String> desculpasGamer = new ArrayList<>();
        public List<String> zoomiesFrases = new ArrayList<>();
        public List<String> presenteFrases = new ArrayList<>();
        public List<String> tbagFrases = new ArrayList<>();
        public List<String> cantadas = new ArrayList<>();
    }

    private static TriviaData data = new TriviaData();

    public static void load(Path file) {
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                TriviaData loaded = GSON.fromJson(r, TriviaData.class);
                if (loaded != null) {
                    data = loaded;
                    populateDefaultsIfEmpty();
                    return;
                }
            } catch (Exception e) {
                CumminsMod.LOG.warn("[Cummins] Falha ao ler offline_trivia.json, criando padrão: {}", e.getMessage());
            }
        }
        populateDefaultsIfEmpty();
        save(file);
    }

    public static void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Falha ao salvar offline_trivia.json: {}", e.getMessage());
        }
    }

    private static void populateDefaultsIfEmpty() {
        if (data.charadas == null || data.charadas.isEmpty()) {
            data.charadas = new ArrayList<>(List.of(
                    new Riddle("O que é o que é: tem olhos mas não vê, tem água mas não bebe, e se você cavar reto pra baixo você cai nele?", "Lava"),
                    new Riddle("O que é o que é: quanto mais você tira de mim no Minecraft, maior eu fico?", "Buraco"),
                    new Riddle("Qual é o mob do Minecraft que sempre chega explodindo de alegria na sua base?", "Creeper"),
                    new Riddle("O que é que corre o mapa inteiro sem ter pernas e se você pisar sem bota afunda na alma?", "Areia das almas"),
                    new Riddle("Por que o esqueleto não brigou com ninguém na mina?", "Porque ele não tem estômago pra isso kkkk"),
                    new Riddle("O que é que você pode quebrar com a mão, mas se você não tiver picareta de ferro você chora na frente dele?", "Diamante"),
                    new Riddle("O que é o que é: nasce grande e morre pequeno, e quanto mais você trabalha mais ele diminui?", "Picareta"),
                    new Riddle("Qual é o carro favorito dos moradores da vila do Minecraft? O meu Peugeot 206 rebaixado!", "Peugeot"),
                    new Riddle("O que é o que é: passa a noite inteira voando na sua cabeça só porque você esqueceu de deitar 3 noites seguidas?", "Phantom"),
                    new Riddle("O que é que entra na água e não se molha, mas se você der shift na cerca ele te derruba?", "Gato")
            ));
        }

        if (data.piadasECausos == null || data.piadasECausos.isEmpty()) {
            data.piadasECausos = new ArrayList<>(List.of(
                    "Sabe o que o Shrek falou pro Burro quando viu essa base? 'O que você tá fazendo no meu pântano?!' kkkk clássico demais",
                    "Esses dias fui dar ré no Peugeot ali perto da mina, quase atropelei um zumbi de armadura de ouro slk",
                    "A vida é igual picareta sem Remendo: uma hora a durabilidade acaba, então aproveita enquanto tá minerando diamante!",
                    "Quem inventou o cascalho caindo na cabeça quando a gente tá minerando não tinha amor no coração não, namoral",
                    "E nessa loucura de dizer que não te quero... vou minerando Netherita até o sol raiar slk!",
                    "Calma calabreso! Não gasta esse ferro todo em trilho que a gente precisa de funil pra farm!",
                    "Receba! Melhor companheiro felino desse servidor, graças a Deus pai!",
                    "Se um dia eu for embora desse servidor, deixo meu Peugeot de herança pra quem tiver mais mortes no log kkkk"
            ));
        }

        if (data.desculpasGamer == null || data.desculpasGamer.isEmpty()) {
            data.desculpasGamer = new ArrayList<>(List.of(
                    "Peraí rapidão rapaziada, tocaram o interfone / o entregador chegou com o rango, já volto!",
                    "Caiu café no meu teclado aqui mds kkkkk espera aí que tô secando o switch!",
                    "Minha mãe mandou tirar o lixo rapidão senão ela desliga o disjuntor da casa kkkk 1 min!",
                    "O Discord deu uma crashada monstra aqui do nada, pera que tô reiniciando a chamada!",
                    "Fui ali esquentar o almoço que já tava congelando aqui rapaziada, segura a base aí!",
                    "Deu um estalo na espinha aqui agora que tive que esticar o lombo senão eu travo na cadeira, slk!",
                    "Engasguei com o pão de queijo aqui kkkk pera aí meu consagrado!",
                    "O mouse desconectou do nada aqui, peraí que tô procurando a pilha reserva!"
            ));
        }

        if (data.zoomiesFrases == null || data.zoomiesFrases.isEmpty()) {
            data.zoomiesFrases = new ArrayList<>(List.of(
                    "Ataque de zoomies ativado slk, segura o homem!",
                    "Fui ali beber água e voltei a 200 por hora kkkkk sai da frente!",
                    "O motor do Peugeot deu a partida aqui rapaziada, vrum vrummm!",
                    "Bateria 100% carregada, ninguém me segura agora nesse mapa!",
                    "Energia acumulada nas 4 patas, partiu fazer parkour na base!"
            ));
        }

        if (data.presenteFrases == null || data.presenteFrases.isEmpty()) {
            data.presenteFrases = new ArrayList<>(List.of(
                    "Ó {player}, achei essa peça solta perto da oficina. Vê se não caiu de alguma máquina sua!",
                    "Caiu isso aqui do parachoque do Peugeot 206 quando dei ré kkkk toma aí {player}, guarda na caixa de ferramentas!",
                    "{player}, tava inspecionando o chão da fábrica e descolei essa peça pra você não pisar descalço.",
                    "Tava farejando a bancada e achei isso dando sopa. Na firma nada se perde, tudo vira recurso, toma aí {player}!",
                    "{player}, você tá minerando e construindo há horas sem comer nada direito! Come isso aqui antes que desmaie, pô!",
                    "Pausa pro café da firma, {player}! Pega esse rango reforçado pra aguentar a maratona.",
                    "Fui na beira do rio dar uma pescada rápida e trouxe essa iguaria fresca pro meu trabalhador favorito!",
                    "Toma esse petisco caprichado, {player}! Saco vazio não para em pé e obra não anda sem sustento.",
                    "Tava cavando com as unhas perto da caverna e olha o que eu desenterrei brilhando pra você, {player}!",
                    "Achei essa pedrinha reluzente entocada numa fenda escura. Lembrei na hora de você, meu consagrado!",
                    "Olha o que as garras afiadas do Tio Cummins acharam no fundo da terra! É todinho seu, {player}.",
                    "Tava brincando na cascalheira e achei esse brilho aqui. Guarda aí no inventário que dá sorte!",
                    "Parceria é parceria, {player}! Você cuida de mim, me dá carinho e o Tio Cummins descola os mimos da firma!",
                    "O Tio Cummins reconhece quem é parceiro de verdade nesse servidor. Receba esse agrado da diretoria!",
                    "Você me deu tanto carinho hoje que meu coração felino amoleceu. Toma esse presente aqui, {player}!",
                    "Um agrado especial pro meu sócio mais dedicado desse mundo de blocos! Tamo junto nessa caminhada.",
                    "Segura isso bem guardado na mochila, {player}! Se a barra pesar lá fora na escuridão, isso pode salvar sua pele!",
                    "Achei uma relíquia bruta escondida no cofre da firma! Só tô te passando porque nossa amizade não tem preço!",
                    "Presente de peso do Tio Cummins pra você não passar perrengue contra os monstros da noite, {player}!"
            ));
        }

        if (data.tbagFrases == null || data.tbagFrases.isEmpty()) {
            data.tbagFrases = new ArrayList<>(List.of(
                    "Agachando em sincronia kkkk o famoso código de honra sagrado dos gamers!",
                    "T-bag de amizade reconhecido com sucesso! 🤝 Parceria fechada!",
                    "Agacha agacha, tá fazendo agachamento pra fortalecer os joelho é? kkkk",
                    "Respeito máximo entre cavalheiros do bloco! Tamo junto!"
            ));
        }

        if (data.cantadas == null || data.cantadas.isEmpty()) {
            data.cantadas = new ArrayList<>(List.of(
                    "ei {player}, você não é redstone mas ativou todos os meus pistões slk",
                    "{player}, você não é diamante na camada -58 mas passei horas procurando alguém como você!",
                    "garçom me traz uma poção de lentidão pq quando eu olho pro {player} o tempo simplesmente para...",
                    "{player}, você não é o mod Create mas me deixou todo sem rumo e rotacionando!",
                    "{player}, você não é beacon mas iluminou minha vida inteira num raio de 50 blocos!",
                    "{player}, me chama de creeper pq quando chego perto de você meu coração explode de amores!",
                    "{player}, você não é blaze burner mas esquentou meu coração todinho agora!",
                    "{player}, você não é mesa de encantamento mas quando você passa eu fico todo encantado!",
                    "{player}, você não é netherita mas seu valor pra mim é inestimável!",
                    "{player}, você não é elytra mas faz meu coração voar alto!",
                    "se beleza desse nível de xp o {player} já tava no level 100 e full netherita encantada!",
                    "{player}, você não é totem da imortalidade mas me salvou da solidão desse mundo!",
                    "{player}, você não é baú do ender mas guarda tudo que é mais precioso pra mim!",
                    "{player}, se cair do céu é perigoso, como é que você desceu aqui sem tomar dano de queda?",
                    "{player}, você não é relógio de redstone mas acelerou meus batimentos por tick!",
                    "{player}, você não é poção de força mas me dá ânimo pra encarar qualquer dragão!",
                    "{player}, me chama de lobo domesticado pq onde você for eu te sigo balançando o rabo!",
                    "{player}, você não é bigorna mas quando me olhou eu caí de quatro no chão!",
                    "{player}, você não é fogueira de acampamento mas mantém minha alma aquecida nas noites frias!",
                    "{player}, se você fosse um bloco eu te minerava com toque suave pra nunca te quebrar!"
            ));
        }
    }

    public static Riddle getRandomRiddle() {
        populateDefaultsIfEmpty();
        return data.charadas.get(RANDOM.nextInt(data.charadas.size()));
    }

    public static String getRandomJoke() {
        populateDefaultsIfEmpty();
        return data.piadasECausos.get(RANDOM.nextInt(data.piadasECausos.size()));
    }

    public static String getRandomGamerExcuse() {
        populateDefaultsIfEmpty();
        return data.desculpasGamer.get(RANDOM.nextInt(data.desculpasGamer.size()));
    }

    public static String getRandomZoomiesPhrase() {
        populateDefaultsIfEmpty();
        return data.zoomiesFrases.get(RANDOM.nextInt(data.zoomiesFrases.size()));
    }

    public static String getRandomGiftPhrase() {
        return getRandomGiftPhrase(null);
    }

    public static String getRandomGiftPhrase(String targetPlayer) {
        populateDefaultsIfEmpty();
        String target = (targetPlayer != null && !targetPlayer.isBlank()) ? targetPlayer : "meu consagrado";
        String template = data.presenteFrases.get(RANDOM.nextInt(data.presenteFrases.size()));
        return template.replace("{player}", target);
    }

    public static String getRandomTbagPhrase() {
        populateDefaultsIfEmpty();
        return data.tbagFrases.get(RANDOM.nextInt(data.tbagFrases.size()));
    }

    public static String getRandomCantada(String targetPlayer) {
        populateDefaultsIfEmpty();
        String target = (targetPlayer != null && !targetPlayer.isBlank()) ? targetPlayer : "parceiro";
        String template = data.cantadas.get(RANDOM.nextInt(data.cantadas.size()));
        return template.replace("{player}", target);
    }
}
