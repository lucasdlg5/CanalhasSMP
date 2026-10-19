package com.canalhas.cummins;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.*;

/**
 * Gerenciador de Teleportes Místicos do Senhor Cummins:
 * - Permite que um jogador peça ao Senhor Cummins para ser teleportado para perto de outro amigo
 *   ou para puxar outro amigo até ele (ex: "cummins teleporte o arzan para perto de mim").
 * - Cooldown persistente de 1 hora (60 minutos) por jogador, salvo em memoria.json.
 * - Detecta variações naturais em português no chat (ex: "cummins me teleporta pro kikkawa", "cummins teleporte o arzan para perto de mim", "cummins me leva pro arzan", etc.).
 * - Reconhece referências a si mesmo ("mim", "eu", "pra cá", "comigo", etc.) sem confundir com usernames.
 * - Suporta também comando /cumminstp <jogador>.
 * - Verifica segurança de terreno (2 a 3 blocos de distância, chão sólido, sem lava/sufocamento).
 * - Emite efeitos sonoros de enderman/chorus fruit e partículas cósmicas/corações.
 */
public class CumminsTeleportManager {
    private static final List<String> TP_KEYWORDS = List.of(
            "teleport", "teletransport", "tp", "me leva", "leva eu", "me puxa", "puxa eu",
            "me passa", "passa eu", "me manda", "manda eu", "da tp", "dá tp", "me poe", "me põe", "me bota", "me joga",
            "traz", "traga", "puxa", "leva", "teleporte", "teleporta", "teletransporta"
    );

    // Prefixos de destino ordenados por comprimento decrescente para priorizar matches mais específicos
    private static final List<String> TARGET_PREFIXES = List.of(
            "para perto de ", "para perto do ", "para perto da ", "para perto ",
            "pra perto de ", "pra perto do ", "pra perto da ", "pra perto ",
            "ate perto de ", "ate perto do ", "ate perto da ", "ate perto ",
            "para junto de ", "para junto do ", "para junto da ", "para junto ",
            "pra junto de ", "pra junto do ", "pra junto da ", "pra junto ",
            "ao lado de ", "ao lado do ", "ao lado da ",
            "do lado de ", "do lado do ", "do lado da ",
            "para o ", "para a ", "para ",
            "pra o ", "pra a ", "pro ", "pra ",
            "ate o ", "ate a ", "ate ",
            "no ", "na ", "ao ", "to "
    );

    private final CumminsMod mod;

    public CumminsTeleportManager(CumminsMod mod) {
        this.mod = mod;
    }

    public long getRemainingCooldownMillis(String playerName) {
        if (playerName == null) return 0L;
        long last = mod.context().getTeleportCooldown(playerName);
        if (last <= 0L) return 0L;

        int cooldownMin = mod.config().teleport != null ? mod.config().teleport.cooldownMinutes : 60;
        long cooldownMs = cooldownMin * 60L * 1000L;
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed < cooldownMs) {
            return cooldownMs - elapsed;
        }
        return 0L;
    }

    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(1, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0 && seconds > 0) {
            return minutes + " min e " + seconds + " s";
        } else if (minutes > 0) {
            return minutes + " min";
        } else {
            return seconds + " s";
        }
    }

    public void resetCooldown(String playerName) {
        if (playerName != null) {
            mod.context().resetTeleportCooldown(playerName);
            mod.context().save();
        }
    }

    public static boolean isSelfReference(String word) {
        if (word == null) return false;
        String w = CumminsScanner.normalize(word).trim();
        return w.equals("mim") || w.equals("eu") || w.equals("me")
                || w.equals("mim mesmo") || w.equals("eu mesmo")
                || w.equals("ca") || w.equals("aqui") || w.equals("comigo")
                || w.equals("meu lado") || w.equals("minha pessoa");
    }

    public static boolean isSpecialPlace(String word) {
        if (word == null) return false;
        String w = CumminsScanner.normalize(word).trim();
        return w.equals("casa") || w.equals("lar") || w.equals("base")
                || w.equals("cama") || w.equals("minha casa") || w.equals("minha base");
    }

    /**
     * Tenta identificar se uma mensagem de chat é uma solicitação de teleporte direcionada ao Cummins.
     * Retorna true caso a mensagem tenha sido tratada.
     */
    public boolean tryHandleChatTeleport(ServerPlayerEntity player, String text) {
        if (mod.config().teleport != null && !mod.config().teleport.enabled) {
            return false;
        }

        String norm = CumminsScanner.normalize(text);
        if (norm.isBlank()) return false;

        boolean hasTpKeyword = TP_KEYWORDS.stream().anyMatch(norm::contains);
        if (!hasTpKeyword) return false;

        String senderName = player.getGameProfile().getName();
        boolean mentioned = mod.brain() != null
                ? mod.brain().isCumminsMentioned(text)
                : mod.config().triggerWords.stream().anyMatch(w -> !w.isBlank() && norm.contains(CumminsScanner.normalize(w)));
        boolean inConv = mod.brain() != null && senderName.equals(mod.brain().lastTalkedWith())
                && (System.currentTimeMillis() - mod.brain().lastTalkedAt() < 75_000L);
        boolean startsWithTp = norm.startsWith("tp ") || norm.startsWith("teleporte ") || norm.startsWith("teleporta ");

        // Deve ser endereçado ao Cummins ou estar em diálogo com ele
        if (!mentioned && !inConv && !startsWithTp) {
            return false;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return false;

        ParsedTeleportRequest parsed = parseTargetFromChat(player, norm, server.getPlayerManager().getPlayerList());
        if (parsed == null) {
            return false;
        }

        // Falta o destino (ex: "cummins me teleporte")
        if (parsed.missingDestination) {
            String helpMsg = "§6[Cummins]§r Miau! Teleportar você pra quem? Me diz o nome do amigo que tá online! 😸 (Ex: §ecummins me teleporte para perto do "
                    + getSuggestedTarget(player, server) + "§r)";
            broadcastOrReply(player, helpMsg);
            return true;
        }

        // Falta quem teleportar (ex: "cummins teleporte para perto de mim")
        if (parsed.missingSubject) {
            String helpMsg = "§6[Cummins]§r Miau! Teleportar quem pra perto de você? Me diz o nome do amigo online que você quer que eu puxe pra cá! 😸 (Ex: §ecummins teleporte o "
                    + getSuggestedTarget(player, server) + " para perto de mim§r)";
            broadcastOrReply(player, helpMsg);
            return true;
        }

        // Pediu para teleportar pra casa/base
        if (parsed.isSpecialPlace) {
            String homeMsg = "§6[Cummins]§r Miau! Meus poderes mágicos de teleporte só conseguem te levar para perto de outros amigos online (ex: §ecummins me teleporte para perto do "
                    + getSuggestedTarget(player, server) + "§r)! Pra voltar pra casa ou base, use sua cama ou suas perninhas! 😸";
            broadcastOrReply(player, homeMsg);
            return true;
        }

        // Teleporte de alguém para ele mesmo (ex: "mim para mim", ou "arzan para arzan")
        if (parsed.isSelfToSelf) {
            if (parsed.whoToTeleport != null && parsed.whoToTeleport.equals(player)) {
                String selfMsg = "§6[Cummins]§r Ué, você disse para perto de você mesmo (\"mim\")?! Mas você já tá aí em você mesmo, ô cabeçudo! 😹 Escolhe outro amigo pra ir visitar ou me diz quem você quer que eu puxe pra cá!";
                broadcastOrReply(player, selfMsg);
            } else {
                String name = parsed.rawSubjectName != null && !parsed.rawSubjectName.isEmpty() ? parsed.rawSubjectName : "esse amigo";
                String selfMsg = "§6[Cummins]§r Ué, teleporte do §e" + name + "§f para perto dele mesmo?! Mas ele já tá onde ele tá, ô cabeçudo! 😹";
                broadcastOrReply(player, selfMsg);
            }
            return true;
        }

        // Quem deve ser teleportado não foi encontrado online
        if (parsed.whoToTeleport == null) {
            String missingMsg = "§6[Cummins]§r Miau! Não encontrei o jogador §e\"" + parsed.rawSubjectName + "\"§f online no servidor agora! Só consigo puxar ou te levar até quem estiver jogando no momento. 🐾";
            broadcastOrReply(player, missingMsg);
            return true;
        }

        // O destino não foi encontrado online
        if (parsed.destinationPlayer == null) {
            String missingMsg = "§6[Cummins]§r Miau! Não encontrei o jogador §e\"" + parsed.rawDestinationName + "\"§f online no servidor agora! Só consigo te teleportar para amigos que estiverem jogando no momento (ex: §ecummins me teleporte para perto do "
                    + getSuggestedTarget(player, server) + "§r). 🐾";
            broadcastOrReply(player, missingMsg);
            return true;
        }

        // Executa com validação de cooldown (o player que solicitou paga o cooldown)
        return requestTeleport(player, parsed.whoToTeleport, parsed.destinationPlayer, false);
    }

    public boolean requestTeleport(ServerPlayerEntity requester, ServerPlayerEntity target, boolean isCommand) {
        return requestTeleport(requester, requester, target, isCommand);
    }

    /**
     * Executa o pedido de teleporte (seja via chat ou via comando).
     */
    public boolean requestTeleport(ServerPlayerEntity requester, ServerPlayerEntity toTeleport, ServerPlayerEntity destination, boolean isCommand) {
        String reqName = requester.getGameProfile().getName();
        long remaining = getRemainingCooldownMillis(reqName);

        if (remaining > 0) {
            String timeFmt = formatDuration(remaining);
            String cdMsg = "§6[Cummins]§r Ô §e" + reqName + "§f, segura a ansiedade! 🐾⏳ Meus poderes de teleporte têm recarga de 1 hora! Falta(m) §e"
                    + timeFmt + "§f pra você poder pedir de novo.";
            broadcastOrReply(requester, cdMsg);
            return true;
        }

        executeTeleport(requester, toTeleport, destination);
        return true;
    }

    public void executeTeleport(ServerPlayerEntity requester, ServerPlayerEntity target) {
        executeTeleport(requester, requester, target);
    }

    public void executeTeleport(ServerPlayerEntity requester, ServerPlayerEntity toTeleport, ServerPlayerEntity destination) {
        MinecraftServer server = requester.getServer();
        if (server == null) return;

        server.execute(() -> {
            if (toTeleport.isRemoved() || destination.isRemoved()) return;

            ServerWorld destWorld = destination.getServerWorld();
            ServerWorld originWorld = toTeleport.getServerWorld();

            BlockPos safePos = findSafeGroundAroundPlayer(destination.getBlockPos(), destWorld);
            double destX = safePos.getX() + 0.5;
            double destY = safePos.getY();
            double destZ = safePos.getZ() + 0.5;
            float destYaw = destination.getYaw();
            float destPitch = destination.getPitch();

            // 1. Efeitos de partida
            originWorld.playSound(null, toTeleport.getX(), toTeleport.getY(), toTeleport.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            originWorld.spawnParticles(ParticleTypes.PORTAL, toTeleport.getX(), toTeleport.getY() + 1.0, toTeleport.getZ(),
                    35, 0.5, 0.5, 0.5, 0.15);

            // Desmonta montaria se estiver montado
            if (toTeleport.hasVehicle()) {
                toTeleport.stopRiding();
            }

            // 2. Teleporte seguro (suporta mudança de dimensões)
            toTeleport.teleport(destWorld, destX, destY, destZ, destYaw, destPitch);
            toTeleport.fallDistance = 0;

            // 3. Efeitos de chegada no destino
            destWorld.playSound(null, destX, destY, destZ,
                    SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            destWorld.playSound(null, destX, destY, destZ,
                    SoundEvents.ENTITY_CAT_PURREOW, SoundCategory.PLAYERS, 0.9f, 1.15f);
            destWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, destX, destY + 1.0, destZ,
                    35, 0.5, 0.5, 0.5, 0.1);
            destWorld.spawnParticles(ParticleTypes.HEART, destX, destY + 1.8, destZ,
                    6, 0.3, 0.3, 0.3, 0.05);

            // 4. Grava cooldown de 1 hora para o solicitante
            String reqName = requester.getGameProfile().getName();
            String teleportedName = toTeleport.getGameProfile().getName();
            String destName = destination.getGameProfile().getName();
            mod.context().setTeleportCooldown(reqName, System.currentTimeMillis());
            mod.context().save();

            // 5. Anúncio festivo no chat
            String broadcastMsg;
            if (toTeleport.equals(requester)) {
                broadcastMsg = "§6[Cummins]§r *MIAAAU!* 🌀✨ Canalizei energias místicas felinas e teleportei §e"
                        + reqName + "§f para perto de §e" + destName + "§f! (Cooldown de 1 hora ativado) 🐾";
            } else {
                broadcastMsg = "§6[Cummins]§r *MIAAAU!* 🌀✨ A pedido de §e" + reqName
                        + "§f, puxei §e" + teleportedName + "§f pelas fendas do espaço até §e" + destName + "§f! (Cooldown de 1 hora ativado) 🐾";
            }
            server.getPlayerManager().broadcast(Text.literal(broadcastMsg), false);

            mod.context().addGame("* Senhor Cummins teleportou " + teleportedName + " para perto de " + destName + (toTeleport.equals(requester) ? "" : " (a pedido de " + reqName + ")"), mod.config().timing.contextSize);
            CumminsMod.LOG.info("[Cummins] Teleporte executado: {} -> {} a pedido de {} (cooldown 1h iniciado)", teleportedName, destName, reqName);
        });
    }

    private void broadcastOrReply(ServerPlayerEntity player, String msg) {
        MinecraftServer server = player.getServer();
        if (server != null) {
            server.getPlayerManager().broadcast(Text.literal(msg), false);
        } else {
            player.sendMessage(Text.literal(msg), false);
        }
    }

    private String getSuggestedTarget(ServerPlayerEntity sender, MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.equals(sender)) {
                return p.getGameProfile().getName();
            }
        }
        return "kikkawa";
    }

    public static class ParsedTeleportRequest {
        public final ServerPlayerEntity whoToTeleport;
        public final ServerPlayerEntity destinationPlayer;
        public final String rawSubjectName;
        public final String rawDestinationName;
        public final boolean isSelfToSelf;
        public final boolean isSpecialPlace;
        public final boolean missingSubject;
        public final boolean missingDestination;

        public ParsedTeleportRequest(ServerPlayerEntity whoToTeleport, ServerPlayerEntity destinationPlayer,
                                     String rawSubjectName, String rawDestinationName,
                                     boolean isSelfToSelf, boolean isSpecialPlace,
                                     boolean missingSubject, boolean missingDestination) {
            this.whoToTeleport = whoToTeleport;
            this.destinationPlayer = destinationPlayer;
            this.rawSubjectName = rawSubjectName;
            this.rawDestinationName = rawDestinationName;
            this.isSelfToSelf = isSelfToSelf;
            this.isSpecialPlace = isSpecialPlace;
            this.missingSubject = missingSubject;
            this.missingDestination = missingDestination;
        }
    }

    private ParsedTeleportRequest parseTargetFromChat(ServerPlayerEntity requester, String norm, List<ServerPlayerEntity> onlinePlayers) {
        String clean = norm;
        // Remove saudações/menções ao Cummins
        clean = clean.replaceFirst("^(senhor cummins|sr cummins|tio cummins|cummins|gato)[\\s,:]*", "").trim();
        clean = clean.replaceFirst("[\\s,:]*(senhor cummins|sr cummins|tio cummins|cummins)$", "").trim();

        // 1. Procura prefixos de destino ("para perto de", "pra mim", "pro kikkawa", etc.)
        String destPart = null;
        String subjectPart = null;

        for (String pref : TARGET_PREFIXES) {
            int idx = clean.indexOf(pref);
            if (idx != -1) {
                destPart = clean.substring(idx + pref.length()).trim();
                subjectPart = clean.substring(0, idx).trim();
                break;
            }
        }

        // Se encontrou conector de destino
        if (destPart != null) {
            // Limpa sufixos de aproximação se sobrou algum
            for (String sub : List.of("perto do ", "perto da ", "perto de ", "perto ", "junto do ", "junto da ", "junto de ", "junto ", "lado do ", "lado da ", "lado de ")) {
                if (destPart.startsWith(sub)) {
                    destPart = destPart.substring(sub.length()).trim();
                }
            }

            // Extrai o primeiro token significativo do destino
            String rawDest = extractFirstToken(destPart);

            // Agora limpa o subjectPart (que veio antes do prefixo)
            String rawSubject = extractSubject(subjectPart);

            // Casos especiais de destino
            if (isSpecialPlace(rawDest)) {
                return new ParsedTeleportRequest(requester, null, rawSubject, rawDest, false, true, false, false);
            }

            boolean destIsSelf = isSelfReference(rawDest);
            boolean subjIsSelf = rawSubject.isEmpty() || isSelfReference(rawSubject);

            ServerPlayerEntity destPlayer = destIsSelf ? requester : findOnlinePlayer(rawDest, onlinePlayers);

            // Se o sujeito for vazio e o destino for o próprio jogador (ex: "cummins teleporte para perto de mim")
            if (rawSubject.isEmpty() && destIsSelf) {
                return new ParsedTeleportRequest(null, requester, "", rawDest, false, false, true, false);
            }

            ServerPlayerEntity subjPlayer;
            if (subjIsSelf) {
                subjPlayer = requester;
            } else {
                subjPlayer = findOnlinePlayer(rawSubject, onlinePlayers);
            }

            boolean selfToSelf = (destIsSelf && subjIsSelf)
                    || (destPlayer != null && subjPlayer != null && destPlayer.equals(subjPlayer))
                    || (rawDest != null && rawSubject != null && !rawDest.isEmpty() && rawDest.equalsIgnoreCase(rawSubject));

            return new ParsedTeleportRequest(subjPlayer, destPlayer, rawSubject.isEmpty() ? requester.getGameProfile().getName() : rawSubject, rawDest, selfToSelf, false, false, false);
        }

        // 2. Não tem conectores de destino explícitos: verifica verbos do tipo "traz o <player>", "puxa o <player>"
        String pullPrefix = getPullVerbPrefix(clean);
        if (pullPrefix != null) {
            String afterPull = clean.substring(pullPrefix.length()).trim();
            String rawSubject = stripArticles(afterPull);
            rawSubject = extractFirstToken(rawSubject);
            if (!rawSubject.isEmpty()) {
                if (isSelfReference(rawSubject)) {
                    // "cummins me traz" -> falta destino
                    return new ParsedTeleportRequest(requester, null, "me", "", false, false, false, true);
                }
                ServerPlayerEntity subjPlayer = findOnlinePlayer(rawSubject, onlinePlayers);
                return new ParsedTeleportRequest(subjPlayer, requester, rawSubject, "mim", false, false, false, false);
            }
        }

        // 3. Casos como "cummins tp <player>", "cummins teleporte <player>", "cummins leva <player>"
        String tpPrefix = getTpVerbPrefix(clean);
        if (tpPrefix != null) {
            String remainder = clean.substring(tpPrefix.length()).trim();
            remainder = stripArticles(remainder);
            String token = extractFirstToken(remainder);
            if (token.isEmpty() || isSelfReference(token)) {
                // "cummins me teleporte", "cummins tp mim"
                return new ParsedTeleportRequest(requester, null, requester.getGameProfile().getName(), "", false, false, false, true);
            }
            if (isSpecialPlace(token)) {
                return new ParsedTeleportRequest(requester, null, requester.getGameProfile().getName(), token, false, true, false, false);
            }
            ServerPlayerEntity dest = findOnlinePlayer(token, onlinePlayers);
            return new ParsedTeleportRequest(requester, dest, requester.getGameProfile().getName(), token, false, false, false, false);
        }

        // 4. Verificação de fallback: se apenas mencionou um player online na mensagem
        for (ServerPlayerEntity p : onlinePlayers) {
            if (p.equals(requester)) continue;
            String pNorm = CumminsScanner.normalize(p.getGameProfile().getName());
            if (clean.contains(pNorm)) {
                return new ParsedTeleportRequest(requester, p, requester.getGameProfile().getName(), p.getGameProfile().getName(), false, false, false, false);
            }
        }

        // Se tem palavra de teleporte mas nenhum player foi identificado
        return new ParsedTeleportRequest(requester, null, requester.getGameProfile().getName(), "", false, false, false, true);
    }

    private static String extractFirstToken(String str) {
        if (str == null) return "";
        String[] tokens = str.trim().split("\\s+");
        if (tokens.length == 0) return "";
        if (tokens.length >= 2) {
            String combined = (tokens[0] + " " + tokens[1]).replaceAll("[^a-zA-Z0-9_\\s]", "").trim();
            if (isSelfReference(combined)) return combined;
        }
        return tokens[0].replaceAll("[^a-zA-Z0-9_]", "").trim();
    }

    private static String stripArticles(String str) {
        if (str == null) return "";
        String s = str.trim();
        for (String art : List.of("o ", "a ", "os ", "as ", "um ", "uma ", "uns ", "umas ", "ao ", "pro ", "pra ")) {
            if (s.startsWith(art)) {
                s = s.substring(art.length()).trim();
                break;
            }
        }
        return s;
    }

    private static String extractSubject(String str) {
        if (str == null) return "";
        String s = str.trim();
        for (String verb : List.of(
                "teletransporte ", "teletransporta ", "teletransportar ",
                "teleporte ", "teleporta ", "teleportar ", "tp ",
                "me leva ", "leva eu ", "leva ", "levar ",
                "me puxa ", "puxa eu ", "puxa ", "puxar ",
                "me traz ", "traz eu ", "traz ", "traga ", "trazer ",
                "me manda ", "manda eu ", "manda ", "mandar ",
                "me passa ", "passa eu ", "passa ", "passar ",
                "da tp no ", "da tp pro ", "da tp pra ", "da tp ",
                "dá tp no ", "dá tp pro ", "dá tp pra ", "dá tp "
        )) {
            if (s.startsWith(verb)) {
                s = s.substring(verb.length()).trim();
                break;
            }
        }
        s = stripArticles(s);
        return extractFirstToken(s);
    }

    private static String getPullVerbPrefix(String clean) {
        for (String verb : List.of("traz ", "traga ", "trazer ", "puxa ", "puxar ", "me traz ", "me traga ", "me puxa ")) {
            if (clean.startsWith(verb)) return verb;
        }
        return null;
    }

    private static String getTpVerbPrefix(String clean) {
        for (String verb : List.of(
                "teletransporte ", "teletransporta ", "teletransportar ",
                "teleporte ", "teleporta ", "teleportar ", "tp ",
                "me teleporte ", "me teleporta ", "me teletransporta ",
                "me leva ", "leva ", "levar ", "me manda ", "manda ",
                "da tp ", "dá tp "
        )) {
            if (clean.startsWith(verb)) return verb;
        }
        return null;
    }

    private ServerPlayerEntity findOnlinePlayer(String candidate, List<ServerPlayerEntity> onlinePlayers) {
        String candNorm = CumminsScanner.normalize(candidate);
        if (candNorm.isEmpty()) return null;

        // Match exato
        for (ServerPlayerEntity p : onlinePlayers) {
            String pNorm = CumminsScanner.normalize(p.getGameProfile().getName());
            if (pNorm.equalsIgnoreCase(candNorm)) {
                return p;
            }
        }

        // Match por prefixo ou substring (ex: "arzan" -> "arzanlion")
        for (ServerPlayerEntity p : onlinePlayers) {
            String pNorm = CumminsScanner.normalize(p.getGameProfile().getName());
            if (pNorm.startsWith(candNorm) || (candNorm.length() >= 3 && pNorm.contains(candNorm))) {
                return p;
            }
        }

        return null;
    }

    public static boolean isWalkable(ServerWorld world, BlockPos feet) {
        BlockPos floor = feet.down();
        BlockPos head = feet.up();

        var floorState = world.getBlockState(floor);
        if (floorState.getCollisionShape(world, floor).isEmpty()) return false;
        if (!world.getFluidState(floor).isEmpty()) return false;
        if (floorState.isOf(Blocks.LAVA) || floorState.isOf(Blocks.MAGMA_BLOCK) || floorState.isOf(Blocks.CACTUS)) return false;

        var feetState = world.getBlockState(feet);
        if (!feetState.getCollisionShape(world, feet).isEmpty()) return false;
        if (!world.getFluidState(feet).isEmpty()) return false;

        var headState = world.getBlockState(head);
        if (!headState.getCollisionShape(world, head).isEmpty()) return false;
        if (!world.getFluidState(head).isEmpty()) return false;

        return true;
    }

    public static BlockPos findSafeGroundAroundPlayer(BlockPos center, ServerWorld world) {
        // Prioriza posição a 2 ou 3 blocos de distância do alvo
        for (int r = 2; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos target = center.add(dx, 0, dz);
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos pos = target.add(0, dy, 0);
                        if (isWalkable(world, pos)) return pos;
                    }
                }
            }
        }
        // Testa próximo ao centro
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos pos = center.add(0, dy, 0);
            if (isWalkable(world, pos)) return pos;
        }
        return center;
    }
}
