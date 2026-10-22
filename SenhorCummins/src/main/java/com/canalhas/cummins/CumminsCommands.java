package com.canalhas.cummins;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** /cummins status | reload | toggle | falar [assunto] | memoria | lembrar | esquecer */
public final class CumminsCommands {
    private CumminsCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d, CumminsMod mod) {
        d.register(CommandManager.literal("cummins")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("status").executes(c -> {
                    CumminsConfig cfg = mod.config();
                    String err = mod.brain().lastError();
                    String perf = mod.telemetry() != null ? " | §e" + mod.telemetry().getPerformanceSummary() + "§r" : "";
                    send(c.getSource(), "§6[Cummins]§r ativo: " + cfg.enabled
                            + " | chave API: " + (cfg.hasApiKey() ? "ok" : "§cFALTANDO§r")
                            + " | modelo: " + cfg.api.model
                            + " | contexto: " + mod.context().gameSnapshot().size()
                            + " | memória: " + mod.context().notesSnapshot().size()
                            + " | discord: " + mod.discord().status()
                            + perf
                            + (err != null ? " | §cúltimo erro: " + err : ""));
                    return 1;
                }))
                .then(CommandManager.literal("reload").executes(c -> {
                    mod.reload();
                    send(c.getSource(), "§6[Cummins]§r config recarregada e pasta de backup dos mods sincronizada.");
                    return 1;
                }))
                .then(CommandManager.literal("backupmods").executes(c -> {
                    if (mod.modBackup() != null) mod.modBackup().syncNow();
                    send(c.getSource(), "§6[Cummins]§r Pasta completa dos mods desenvolvidos sincronizada em world/mods_desenvolvidos!");
                    return 1;
                }))
                .then(CommandManager.literal("toggle").executes(c -> {
                    mod.config().enabled = !mod.config().enabled;
                    mod.saveConfig();
                    send(c.getSource(), "§6[Cummins]§r agora está " + (mod.config().enabled ? "§aATIVO" : "§cMUDO"));
                    return 1;
                }))
                .then(CommandManager.literal("falar")
                        .executes(c -> {
                            mod.brain().forceTalk(null);
                            return 1;
                        })
                        .then(CommandManager.argument("assunto", StringArgumentType.greedyString()).executes(c -> {
                            mod.brain().forceTalk(StringArgumentType.getString(c, "assunto"));
                            return 1;
                        })))
                .then(CommandManager.literal("dizer")
                        .then(CommandManager.argument("mensagem", StringArgumentType.greedyString()).executes(c -> {
                            String msg = StringArgumentType.getString(c, "mensagem").trim();
                            if (msg.length() >= 2 && ((msg.startsWith("\"") && msg.endsWith("\""))
                                    || (msg.startsWith("'") && msg.endsWith("'"))
                                    || (msg.startsWith("“") && msg.endsWith("”")))) {
                                msg = msg.substring(1, msg.length() - 1).trim();
                            }
                            if (msg.isEmpty()) return 0;
                            mod.brain().broadcast(msg, null);
                            return 1;
                        })))
                .then(CommandManager.literal("perola").executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    String name = player != null ? player.getGameProfile().getName() : null;
                    mod.brain().sendPerola(name);
                    send(c.getSource(), "§6[Cummins]§r Sorteando e enviando uma pérola do Discord...");
                    return 1;
                }))
                .then(CommandManager.literal("cantada")
                        .executes(c -> {
                            ServerPlayerEntity player = c.getSource().getPlayer();
                            String name = player != null ? player.getGameProfile().getName() : null;
                            mod.brain().sendCantada(name);
                            send(c.getSource(), "§6[Cummins]§r Soltando cantada no chat...");
                            return 1;
                        })
                        .then(CommandManager.argument("alvo", StringArgumentType.word()).executes(c -> {
                            String target = StringArgumentType.getString(c, "alvo");
                            mod.brain().sendCantada(target);
                            send(c.getSource(), "§6[Cummins]§r Soltando cantada para " + target + "...");
                            return 1;
                        })))
                .then(CommandManager.literal("novidade")
                        .executes(c -> {
                            ServerPlayerEntity player = c.getSource().getPlayer();
                            String name = player != null ? player.getGameProfile().getName() : null;
                            mod.brain().sendPlayerTopicNews(name);
                            send(c.getSource(), "§6[Cummins]§r Pesquisando novidade recente sobre o tema do jogador...");
                            return 1;
                        })
                        .then(CommandManager.argument("alvo", StringArgumentType.word()).executes(c -> {
                            String target = StringArgumentType.getString(c, "alvo");
                            mod.brain().sendPlayerTopicNews(target);
                            send(c.getSource(), "§6[Cummins]§r Pesquisando novidade recente para " + target + "...");
                            return 1;
                        })))
                .then(CommandManager.literal("conhecer")
                        .executes(c -> {
                            ServerPlayerEntity player = c.getSource().getPlayer();
                            String name = player != null ? player.getGameProfile().getName() : null;
                            mod.brain().startIndividualDiscoveryChat(name);
                            send(c.getSource(), "§6[Cummins]§r Puxando papo individual para conhecer melhor o jogador...");
                            return 1;
                        })
                        .then(CommandManager.argument("alvo", StringArgumentType.word()).executes(c -> {
                            String target = StringArgumentType.getString(c, "alvo");
                            mod.brain().startIndividualDiscoveryChat(target);
                            send(c.getSource(), "§6[Cummins]§r Puxando papo individual para conhecer melhor " + target + "...");
                            return 1;
                        })))
                .then(CommandManager.literal("humor")
                        .then(CommandManager.argument("jogador", StringArgumentType.word()).executes(c -> {
                            String target = StringArgumentType.getString(c, "jogador");
                            var p = mod.context().relationships().getProfile(target);
                            if (p == null) {
                                send(c.getSource(), "§cPerfil de " + target + " não encontrado.");
                                return 0;
                            }
                            send(c.getSource(), String.format("§6[Cummins] Relação com %s:§r Sarcasmo: %.0f%% | Carinho: %.0f%% | Amor: %.0f%% | Saudade: %.0f%% | Irritação: %.0f%% | Ignorado/Vácuo: %.0f%% (vácuos: %d)",
                                    target, p.sarcasm * 100, p.affection * 100, p.love * 100, p.saudade * 100, p.irritation * 100, p.ignored * 100, p.consecutiveUnansweredPokes));
                            if (!p.tastesAndInterests.isEmpty()) {
                                send(c.getSource(), "§eGostos/Interesses:§r " + String.join(", ", p.tastesAndInterests));
                            }
                            if (!p.personalContext.isEmpty()) {
                                send(c.getSource(), "§bContexto:§r " + String.join(" | ", p.personalContext));
                            }
                            return 1;
                        })))
                .then(CommandManager.literal("memoria").executes(c -> {
                    List<String> notes = mod.context().notesSnapshot();
                    send(c.getSource(), "§6[Cummins]§r " + notes.size() + " anotações:");
                    for (String n : notes) send(c.getSource(), " §7- " + n);
                    return 1;
                }))
                .then(CommandManager.literal("lagreports").executes(c -> {
                    if (mod.lagReports() == null || mod.lagReports().getReportCount() == 0) {
                        send(c.getSource(), "§6[Cummins]§r Nenhum relato de lag registrado ainda.");
                        return 1;
                    }
                    var list = mod.lagReports().getRecentReports();
                    send(c.getSource(), "§6[Cummins] Histórico de Relatos de Lag (" + list.size() + " total):§r");
                    int start = Math.max(0, list.size() - 5);
                    for (int i = start; i < list.size(); i++) {
                        var r = list.get(i);
                        send(c.getSource(), String.format(Locale.ROOT, " §e[%s] §b%s§r: \"%s\" §7(TPS: %.1f, MSPT: %.1fms, RAM: %dMB)§r",
                                r.timestamp, r.playerName, r.rawMessage, r.tps, r.meanMspt, r.ramUsedMb));
                    }
                    send(c.getSource(), "§7Arquivo completo salvo em config/senhor-cummins/lag_reports.log§r");
                    return 1;
                }))
                .then(CommandManager.literal("lembrar").executes(c -> {
                    mod.brain().updateMemory();
                    send(c.getSource(), "§6[Cummins]§r atualizando memória com o chat recente...");
                    return 1;
                }))
                .then(CommandManager.literal("esquecer").executes(c -> {
                    mod.context().clearAll();
                    mod.context().save();
                    send(c.getSource(), "§6[Cummins]§r memória e contexto apagados.");
                    return 1;
                }))
                .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("alvo", StringArgumentType.word()).executes(c -> {
                            ServerPlayerEntity requester = c.getSource().getPlayer();
                            if (requester == null) {
                                send(c.getSource(), "§cEste comando só pode ser executado por jogadores.");
                                return 0;
                            }
                            String targetArg = StringArgumentType.getString(c, "alvo");
                            MinecraftServer server = c.getSource().getServer();
                            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetArg);
                            if (target == null) {
                                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                                    if (p.getGameProfile().getName().equalsIgnoreCase(targetArg)
                                            || p.getGameProfile().getName().toLowerCase(Locale.ROOT).startsWith(targetArg.toLowerCase(Locale.ROOT))) {
                                        target = p;
                                        break;
                                    }
                                }
                            }
                            if (target == null) {
                                send(c.getSource(), "§6[Cummins]§r Jogador '§e" + targetArg + "§r' não encontrado online no servidor.");
                                return 0;
                            }
                            if (target.equals(requester)) {
                                send(c.getSource(), "§6[Cummins]§r Ué, mas você já tá aí em você mesmo, seu cabeçudo! 😹");
                                return 0;
                            }
                            mod.teleport().requestTeleport(requester, target, true);
                            return 1;
                        })))
                .then(CommandManager.literal("cooldown")
                        .executes(c -> {
                            ServerPlayerEntity player = c.getSource().getPlayer();
                            if (player == null) {
                                send(c.getSource(), "§cEspecifique o nome do jogador no console.");
                                return 0;
                            }
                            String name = player.getGameProfile().getName();
                            long rem = mod.teleport().getRemainingCooldownMillis(name);
                            if (rem > 0) {
                                send(c.getSource(), "§6[Cummins]§r Cooldown de teleporte restante para você: §e" + CumminsTeleportManager.formatDuration(rem));
                            } else {
                                send(c.getSource(), "§6[Cummins]§r Seu teleporte místico está §aDISPONÍVEL§r para uso! 🐾");
                            }
                            return 1;
                        })
                        .then(CommandManager.argument("jogador", StringArgumentType.word()).executes(c -> {
                            String target = StringArgumentType.getString(c, "jogador");
                            long rem = mod.teleport().getRemainingCooldownMillis(target);
                            if (rem > 0) {
                                send(c.getSource(), "§6[Cummins]§r Cooldown restante para §e" + target + "§r: §e" + CumminsTeleportManager.formatDuration(rem));
                            } else {
                                send(c.getSource(), "§6[Cummins]§r O teleporte para §e" + target + "§r está §aDISPONÍVEL§r. 🐾");
                            }
                            return 1;
                        })))
                .then(CommandManager.literal("resetcooldown")
                        .requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("jogador", StringArgumentType.word()).executes(c -> {
                            String target = StringArgumentType.getString(c, "jogador");
                            mod.teleport().resetCooldown(target);
                            send(c.getSource(), "§6[Cummins]§r Cooldown de teleporte resetado com sucesso para §e" + target + "§r!");
                            return 1;
                        })))
                .then(CommandManager.literal("gato")
                        .then(CommandManager.literal("tp").executes(c -> {
                            ServerPlayerEntity player = resolveTargetPlayer(c.getSource());
                            if (player == null) {
                                send(c.getSource(), "§cNenhum jogador online no momento para teleportar o gato.");
                                return 0;
                            }
                            net.minecraft.entity.passive.CatEntity cat = mod.body().getCat();
                            if (cat == null) cat = mod.body().spawnNear(player);
                            else mod.body().teleportToPlayer(cat, player);
                            mod.body().setFocusPlayer(player.getGameProfile().getName());
                            send(c.getSource(), "§6[Cummins]§r O gato Senhor Cummins se teleportou até " + player.getGameProfile().getName() + "!");
                            return 1;
                        }))
                        .then(CommandManager.literal("summon").executes(c -> {
                            ServerPlayerEntity player = resolveTargetPlayer(c.getSource());
                            if (player == null) {
                                send(c.getSource(), "§cNenhum jogador online no momento para invocar o gato.");
                                return 0;
                            }
                            net.minecraft.entity.passive.CatEntity old = mod.body().getCat();
                            if (old != null) old.discard();
                            mod.body().spawnNear(player);
                            mod.body().setFocusPlayer(player.getGameProfile().getName());
                            send(c.getSource(), "§6[Cummins]§r Corpo felino do Senhor Cummins invocado perto de " + player.getGameProfile().getName() + "!");
                            return 1;
                        }))
                        .then(CommandManager.literal("despawn").executes(c -> {
                            mod.body().dismiss();
                            send(c.getSource(), "§6[Cummins]§r Corpo felino do Senhor Cummins recolhido!");
                            return 1;
                        }))
                        .then(CommandManager.literal("recolher").executes(c -> {
                            mod.body().dismiss();
                            send(c.getSource(), "§6[Cummins]§r Corpo felino do Senhor Cummins recolhido!");
                            return 1;
                        }))
                        .then(CommandManager.literal("dismiss").executes(c -> {
                            mod.body().dismiss();
                            send(c.getSource(), "§6[Cummins]§r Corpo felino do Senhor Cummins recolhido!");
                            return 1;
                        }))
                        .then(CommandManager.literal("cor")
                                .executes(c -> showCatVariantsHelp(c.getSource(), "cummins gato", mod.config().body != null && mod.config().body.variant != null ? mod.config().body.variant : "jellie"))
                                .then(CommandManager.argument("variante", StringArgumentType.word()).executes(c ->
                                        setCumminsVariantCommand(c.getSource(), mod, StringArgumentType.getString(c, "variante")))))
                )
                .then(buildVitoriaLiteral("vitoria", mod))
                .then(buildVitoriaLiteral("Vitoria", mod))
                .then(buildPipiraLiteral("pipira", mod))
                .then(buildPipiraLiteral("Pipira", mod))
                .then(buildPipiraLiteral("magikarp", mod))
                .then(buildJuninhoLiteral("juninho", mod))
                .then(buildJuninhoLiteral("Juninho", mod))
                .then(buildJuninhoLiteral("morcego", mod))
                .then(buildJavidizineoLiteral("javidizineo", mod))
                .then(buildJavidizineoLiteral("Javidizineo", mod))
                .then(buildJavidizineoLiteral("javidinho", mod))
                .then(buildJavidizineoLiteral("leaozinho", mod))
                .then(buildJinqiuzineoLiteral("jinqiuzineo", mod))
                .then(buildJinqiuzineoLiteral("Jinqiuzineo", mod))
                .then(buildJinqiuzineoLiteral("jinqiu", mod))
                .then(buildJinqiuzineoLiteral("tigrezinho", mod))
                .then(CommandManager.literal("mural")
                        .executes(c -> {
                            ServerPlayerEntity sender = c.getSource().getPlayer();
                            if (sender != null) {
                                mod.mural().openMuralForPlayer(sender, false);
                                return 1;
                            }
                            List<ServerPlayerEntity> all = c.getSource().getServer().getPlayerManager().getPlayerList();
                            for (ServerPlayerEntity sp : all) {
                                mod.mural().openMuralForPlayer(sp, true);
                            }
                            send(c.getSource(), "§6[Cummins]§r Mural de Novidades aberto para " + all.size() + " jogador(es) online!");
                            return 1;
                        })
                        .then(CommandManager.literal("todos").executes(c -> {
                            List<ServerPlayerEntity> all = c.getSource().getServer().getPlayerManager().getPlayerList();
                            for (ServerPlayerEntity sp : all) {
                                mod.mural().openMuralForPlayer(sp, true);
                            }
                            send(c.getSource(), "§6[Cummins]§r Mural de Novidades aberto para todos os " + all.size() + " jogador(es) online!");
                            return 1;
                        }))
                        .then(CommandManager.literal("resetar").executes(c -> {
                            mod.mural().resetSeenForAll();
                            send(c.getSource(), "§6[Cummins]§r Histórico diário do Mural resetado. Todos verão ao entrar novamente.");
                            return 1;
                        })))
                .then(CommandManager.literal("playtime")
                        .then(CommandManager.literal("update").executes(c -> {
                            mod.playtime().doPeriodicUpdate(c.getSource().getServer());
                            send(c.getSource(), "§6[Cummins]§r Playtime e Scoreboard atualizados agora!");
                            return 1;
                        }))
                        .then(CommandManager.literal("resetseen").executes(c -> {
                            mod.playtime().resetSeenForAll();
                            send(c.getSource(), "§6[Cummins]§r Histórico diário do Mural de Horas resetado!");
                            return 1;
                        })))
                .then(CommandManager.literal("nome")
                        .then(CommandManager.argument("texto", StringArgumentType.greedyString()).executes(c -> {
                            String raw = StringArgumentType.getString(c, "texto").replace("&", "§");
                            c.getSource().getServer().setMotd(raw);
                            updateMotdInServerProperties(raw);
                            send(c.getSource(), "§6[Cummins]§r Nome/MOTD do servidor atualizado para: §r" + raw);
                            return 1;
                        })))
                .then(CommandManager.literal("boletim").executes(c -> {
                    mod.discord().postShiftReport(c.getSource().getServer());
                    send(c.getSource(), "§6[Cummins]§r Boletim de fim de turno postado no Discord!");
                    return 1;
                }))
                .then(CommandManager.literal("rh").executes(c -> {
                    var tickets = mod.rh().getRecentTickets(5);
                    if (tickets.isEmpty()) {
                        send(c.getSource(), "§6[Cummins RH]§r Nenhum chamado aberto no livro de reclamações.");
                    } else {
                        send(c.getSource(), "§6=== 📋 LIVRO DE RECLAMAÇÕES / RH DA FIRMA ===");
                        for (var t : tickets) {
                            send(c.getSource(), String.format("§7#%04d [§e%s§7] §f%s§7 -> §c%s§7: \"%s\"", t.id, t.date, t.author, t.target, t.reason));
                        }
                    }
                    return 1;
                }))
                .then(CommandManager.literal("contrato").executes(c -> {
                    var ct = mod.contracts().getCurrentContract();
                    send(c.getSource(), "§6=== 📋 CONTRATO DA FIRMA DE HOJE ===");
                    send(c.getSource(), "§e" + ct.title);
                    send(c.getSource(), "§7" + ct.description);
                    send(c.getSource(), "§fRequer: §b" + ct.requiredAmount + "x " + ct.requiredItemId);
                    send(c.getSource(), "§fRecompensa: §a" + ct.rewardDescription);
                    return 1;
                }))
                .then(CommandManager.literal("ponto")
                        .then(CommandManager.literal("listar").executes(c -> {
                            var wps = mod.tourGuide().getAllWaypoints();
                            if (wps.isEmpty()) {
                                send(c.getSource(), "§6[Guia]§r Nenhum ponto turístico cadastrado.");
                            } else {
                                send(c.getSource(), "§6=== 📍 PONTOS TURÍSTICOS DO SERVIDOR ===");
                                for (var wp : wps) {
                                    send(c.getSource(), "§e• " + wp.name + "§7 (" + wp.x + ", " + wp.y + ", " + wp.z + " em " + wp.dimension + ")");
                                }
                            }
                            return 1;
                        }))
                        .then(CommandManager.literal("criar")
                                .then(CommandManager.argument("nome", StringArgumentType.greedyString()).executes(c -> {
                                    ServerPlayerEntity player = c.getSource().getPlayer();
                                    if (player == null) {
                                        send(c.getSource(), "§cComando exclusivo para jogadores!");
                                        return 0;
                                    }
                                    String nome = StringArgumentType.getString(c, "nome").trim();
                                    mod.tourGuide().addWaypoint(nome, player);
                                    send(c.getSource(), "§6[Guia]§r Ponto '§e" + nome + "§r' criado na sua posição!");
                                    return 1;
                                })))
                        .then(CommandManager.literal("remover")
                                .then(CommandManager.argument("nome", StringArgumentType.greedyString()).executes(c -> {
                                    String nome = StringArgumentType.getString(c, "nome").trim();
                                    if (mod.tourGuide().removeWaypoint(nome)) {
                                        send(c.getSource(), "§6[Guia]§r Ponto '§e" + nome + "§r' removido!");
                                    } else {
                                        send(c.getSource(), "§c[Guia] Ponto não encontrado!");
                                    }
                                    return 1;
                                }))))
                .then(CommandManager.literal("net").executes(c -> sendConnectionDiagnostic(c.getSource(), mod)))
                .then(CommandManager.literal("cafe").executes(c -> serveCafeCommand(c.getSource(), mod))));

        // Comando público /mural acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("mural")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player != null) {
                        mod.mural().openMuralForPlayer(player, false);
                    } else {
                        List<ServerPlayerEntity> all = c.getSource().getServer().getPlayerManager().getPlayerList();
                        for (ServerPlayerEntity sp : all) {
                            mod.mural().openMuralForPlayer(sp, true);
                        }
                        send(c.getSource(), "§6[Cummins]§r Mural de Novidades exibido para " + all.size() + " jogador(es).");
                    }
                    return 1;
                }));

        // Comando público /horas acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("horas")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player != null) {
                        mod.playtime().openPlaytimeMural(player, false);
                    } else {
                        send(c.getSource(), "§6[Cummins]§r Este comando abre o Mural de Horas para jogadores!");
                    }
                    return 1;
                }));

        // Comando público /ranking acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("ranking")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player != null) {
                        mod.playtime().openPlaytimeMural(player, false);
                    } else {
                        send(c.getSource(), "§6[Cummins]§r Este comando abre o Mural de Horas para jogadores!");
                    }
                    return 1;
                }));

        // Comando público /cumminstp <alvo> acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("cumminstp")
                .then(CommandManager.argument("alvo", StringArgumentType.word()).executes(c -> {
                    ServerPlayerEntity requester = c.getSource().getPlayer();
                    if (requester == null) return 0;
                    String targetArg = StringArgumentType.getString(c, "alvo");
                    MinecraftServer server = c.getSource().getServer();
                    ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetArg);
                    if (target == null) {
                        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                            if (p.getGameProfile().getName().equalsIgnoreCase(targetArg)
                                    || p.getGameProfile().getName().toLowerCase(Locale.ROOT).startsWith(targetArg.toLowerCase(Locale.ROOT))) {
                                target = p;
                                break;
                            }
                        }
                    }
                    if (target == null) {
                        send(c.getSource(), "§6[Cummins]§r Jogador '§e" + targetArg + "§r' não encontrado online no momento.");
                        return 0;
                    }
                    if (target.equals(requester)) {
                        send(c.getSource(), "§6[Cummins]§r Ué, mas você já tá aí em você mesmo, seu cabeçudo! 😹");
                        return 0;
                    }
                    mod.teleport().requestTeleport(requester, target, true);
                    return 1;
                })));

        // Comando público /tpcooldown acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("tpcooldown")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player == null) return 0;
                    String name = player.getGameProfile().getName();
                    long rem = mod.teleport().getRemainingCooldownMillis(name);
                    if (rem > 0) {
                        send(c.getSource(), "§6[Cummins]§r Cooldown de teleporte restante para você: §e" + CumminsTeleportManager.formatDuration(rem));
                    } else {
                        send(c.getSource(), "§6[Cummins]§r Seu teleporte místico do Senhor Cummins está §aDISPONÍVEL§r para uso! 🐾");
                    }
                    return 1;
                }));

        // Comando público /contrato acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("contrato")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    var ct = mod.contracts().getCurrentContract();
                    boolean done = player != null && mod.contracts().hasPlayerCompletedToday(player.getGameProfile().getName());
                    send(c.getSource(), "§6=== 📋 CONTRATO DA FIRMA CANALHAS SMP ===");
                    send(c.getSource(), "§e" + ct.title + (done ? " §a[CONCLUÍDO HOJE! ✔]" : " §c[PENDENTE]"));
                    send(c.getSource(), "§7" + ct.description);
                    var item = net.minecraft.registry.Registries.ITEM.get(new net.minecraft.util.Identifier(ct.requiredItemId));
                    String itemName = item != net.minecraft.item.Items.AIR ? item.getName().getString() : ct.requiredItemId;
                    send(c.getSource(), "§fEntregar: §b" + ct.requiredAmount + "x " + itemName);
                    send(c.getSource(), "§fRecompensa: §a" + ct.rewardDescription);
                    if (done) {
                        send(c.getSource(), "§aVocê já cumpriu seu dever cívico na fábrica hoje! Descanse.");
                    } else {
                        send(c.getSource(), "§eJunte os itens no seu inventário e digite §6/entregar§e para receber!");
                    }
                    return 1;
                }));

        // Comando público /entregar acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("entregar")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player == null) {
                        send(c.getSource(), "§cComando exclusivo para jogadores!");
                        return 0;
                    }
                    mod.contracts().deliverContract(player);
                    return 1;
                }));

        // Comando público /reclamar <alvo> <motivo> acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("reclamar")
                .then(CommandManager.argument("alvo", StringArgumentType.word())
                        .then(CommandManager.argument("motivo", StringArgumentType.greedyString()).executes(c -> {
                            ServerPlayerEntity player = c.getSource().getPlayer();
                            if (player == null) {
                                send(c.getSource(), "§cComando exclusivo para jogadores!");
                                return 0;
                            }
                            String alvo = StringArgumentType.getString(c, "alvo");
                            String motivo = StringArgumentType.getString(c, "motivo").trim();
                            mod.rh().openTicket(player.getGameProfile().getName(), alvo, motivo);
                            return 1;
                        }))));

        // Comando público /levar <lugar> acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("levar")
                .executes(c -> {
                    send(c.getSource(), "§6[Guia Turístico]§r Use: §e/levar <nome do lugar>§r ou veja os pontos disponíveis com §e/pontos");
                    return 1;
                })
                .then(CommandManager.argument("lugar", StringArgumentType.greedyString()).executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player == null) {
                        send(c.getSource(), "§cComando exclusivo para jogadores!");
                        return 0;
                    }
                    String lugar = StringArgumentType.getString(c, "lugar").trim();
                    var wp = mod.tourGuide().findWaypoint(lugar);
                    if (wp == null) {
                        send(c.getSource(), "§6[Cummins]§r Não conheço nenhum ponto chamado '§e" + lugar + "§r'. Use §e/pontos§r para ver os disponíveis!");
                        return 0;
                    }
                    mod.tourGuide().startGuide(player, wp);
                    return 1;
                })));

        // Comando público /pontos acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("pontos")
                .executes(c -> {
                    var wps = mod.tourGuide().getAllWaypoints();
                    if (wps.isEmpty()) {
                        send(c.getSource(), "§6[Guia Turístico]§r Nenhum ponto turístico cadastrado ainda.");
                    } else {
                        send(c.getSource(), "§6=== 📍 PONTOS TURÍSTICOS DO CANALHAS SMP ===");
                        for (var wp : wps) {
                            send(c.getSource(), "§e• " + wp.name + "§7 (" + wp.x + ", " + wp.y + ", " + wp.z + ") §8- use §6/levar " + wp.name);
                        }
                    }
                    return 1;
                }));

        // Comando público /pochete acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("pochete")
                .executes(c -> {
                    ServerPlayerEntity player = c.getSource().getPlayer();
                    if (player == null) {
                        send(c.getSource(), "§cComando exclusivo para jogadores!");
                        return 0;
                    }
                    mod.pochete().openPochete(player);
                    return 1;
                }));

        // Comando público /conexao, /net e /ping acessível para qualquer jogador (permissão 0)
        d.register(CommandManager.literal("conexao").executes(c -> sendConnectionDiagnostic(c.getSource(), mod)));
        d.register(CommandManager.literal("net").executes(c -> sendConnectionDiagnostic(c.getSource(), mod)));
        d.register(CommandManager.literal("ping").executes(c -> sendConnectionDiagnostic(c.getSource(), mod)));

        // Comandos públicos dos mascotes (/vitoria, /juninho, /pipira, /javidizineo, /jinqiuzineo)
        d.register(buildVitoriaLiteral("vitoria", mod));
        d.register(buildVitoriaLiteral("cadela", mod));
        d.register(buildJuninhoLiteral("juninho", mod));
        d.register(buildJuninhoLiteral("morcego", mod));
        d.register(buildPipiraLiteral("pipira", mod));
        d.register(buildPipiraLiteral("magikarp", mod));
        d.register(buildJavidizineoLiteral("javidizineo", mod));
        d.register(buildJavidizineoLiteral("javidinho", mod));
        d.register(buildJavidizineoLiteral("leaozinho", mod));
        d.register(buildJinqiuzineoLiteral("jinqiuzineo", mod));
        d.register(buildJinqiuzineoLiteral("jinqiu", mod));
        d.register(buildJinqiuzineoLiteral("tigrezinho", mod));

        // Café da Madrugada (/cafe)
        d.register(CommandManager.literal("cafe")
                .executes(c -> serveCafeCommand(c.getSource(), mod)));

        // Malote do Peugeot 206 (/malote e /enviar)
        d.register(CommandManager.literal("malote")
                .executes(c -> handleMaloteDefault(c.getSource(), mod))
                .then(CommandManager.literal("receber").executes(c -> claimMaloteCommand(c.getSource(), mod)))
                .then(CommandManager.literal("enviar")
                        .then(CommandManager.argument("destinatario", StringArgumentType.word()).executes(c ->
                                sendMaloteCommand(c.getSource(), mod, StringArgumentType.getString(c, "destinatario")))))
                .then(CommandManager.argument("destinatario", StringArgumentType.word()).executes(c ->
                        sendMaloteCommand(c.getSource(), mod, StringArgumentType.getString(c, "destinatario")))));

        d.register(CommandManager.literal("enviar")
                .then(CommandManager.argument("destinatario", StringArgumentType.word()).executes(c ->
                        sendMaloteCommand(c.getSource(), mod, StringArgumentType.getString(c, "destinatario")))));

        // Almoxarifado Cooperativo (/almoxarifado e /doar)
        d.register(CommandManager.literal("almoxarifado")
                .executes(c -> openAlmoxarifadoCommand(c.getSource(), mod)));

        d.register(CommandManager.literal("doar")
                .executes(c -> donateAlmoxarifadoCommand(c.getSource(), mod)));

        // Canalhas TV / Modo Espectador (/tv, /canalhastv, /spectate)
        d.register(buildTvLiteral("tv", mod));
        d.register(buildTvLiteral("canalhastv", mod));
        d.register(buildTvLiteral("spectate", mod));

        // Mutirão da Firma (/mutirao)
        d.register(CommandManager.literal("mutirao")
                .executes(c -> showMutiraoCommand(c.getSource(), mod)));

        // Anúncio do Administrador (/administrador <mensagem> e /anuncio <mensagem>)
        d.register(CommandManager.literal("administrador")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.argument("mensagem", StringArgumentType.greedyString()).executes(c -> {
                    String msg = StringArgumentType.getString(c, "mensagem").trim();
                    broadcastAdminScreenMessage(c.getSource().getServer(), msg);
                    c.getSource().getServer().getPlayerManager().broadcast(
                            Text.literal("§6[Administrador]§r " + msg),
                            false
                    );
                    return 1;
                })));

        d.register(CommandManager.literal("anuncio")
                .requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.argument("mensagem", StringArgumentType.greedyString()).executes(c -> {
                    String msg = StringArgumentType.getString(c, "mensagem").trim();
                    broadcastAdminScreenMessage(c.getSource().getServer(), msg);
                    c.getSource().getServer().getPlayerManager().broadcast(
                            Text.literal("§6[Administrador]§r " + msg),
                            false
                    );
                    return 1;
                })));
    }

    private static void updateMotdInServerProperties(String newMotd) {
        try {
            java.nio.file.Path propsPath = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("server.properties");
            if (!java.nio.file.Files.exists(propsPath)) return;
            List<String> lines = java.nio.file.Files.readAllLines(propsPath, java.nio.charset.StandardCharsets.UTF_8);
            StringBuilder escaped = new StringBuilder();
            for (char ch : newMotd.toCharArray()) {
                if (ch > 127) {
                    escaped.append(String.format("\\u%04X", (int) ch));
                } else {
                    escaped.append(ch);
                }
            }
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("motd=")) {
                    lines.set(i, "motd=" + escaped);
                    found = true;
                    break;
                }
            }
            if (!found) lines.add("motd=" + escaped);
            java.nio.file.Files.write(propsPath, lines, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildVitoriaLiteral(String literalName, CumminsMod mod) {
        return CommandManager.literal(literalName)
                .executes(c -> summonVitoriaCommand(c.getSource(), mod))
                .then(CommandManager.literal("tp").executes(c -> summonVitoriaCommand(c.getSource(), mod)))
                .then(CommandManager.literal("summon").executes(c -> summonVitoriaCommand(c.getSource(), mod)))
                .then(CommandManager.literal("despawn").executes(c -> despawnVitoriaCommand(c.getSource(), mod)))
                .then(CommandManager.literal("recolher").executes(c -> despawnVitoriaCommand(c.getSource(), mod)))
                .then(CommandManager.literal("dismiss").executes(c -> despawnVitoriaCommand(c.getSource(), mod)))
                .then(CommandManager.literal("ligado").executes(c -> toggleVitoriaAi(c.getSource(), mod, true)))
                .then(CommandManager.literal("Ligado").executes(c -> toggleVitoriaAi(c.getSource(), mod, true)))
                .then(CommandManager.literal("desligado").executes(c -> toggleVitoriaAi(c.getSource(), mod, false)))
                .then(CommandManager.literal("Desligado").executes(c -> toggleVitoriaAi(c.getSource(), mod, false)));
    }

    private static int summonVitoriaCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = resolveVitoriaTargetPlayer(src);
        if (player == null) {
            send(src, "§d[Vitória]§r §cNenhum jogador online no momento para a Vitória aparecer!");
            return 0;
        }
        mod.vitoria().summonOrTeleportTo(player);
        String targetName = player.getGameProfile().getName();
        send(src, "§d[Vitória]§r A cachorrinha §dVitória§r (Fox Paulistinha tricolor do ArzanLion) apareceu abanando o rabinho perto de §e" + targetName + "§r!");
        mod.brain().onVitoriaSummoned(player);
        return 1;
    }

    private static int despawnVitoriaCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.vitoria() == null) return 0;
        mod.vitoria().dismiss();
        send(src, "§d[Vitória]§r A cachorrinha §dVitória§r foi recolhida com carinho! 🐾");
        return 1;
    }

    private static int toggleVitoriaAi(ServerCommandSource src, CumminsMod mod, boolean enabled) {
        mod.config().vitoriaAiEnabled = enabled;
        mod.saveConfig();
        if (enabled) {
            ServerPlayerEntity player = resolveVitoriaTargetPlayer(src);
            if (player != null && !mod.vitoria().isActiveInSession()) {
                mod.vitoria().summonOrTeleportTo(player);
            }
            send(src, "§d[Vitória]§r Inteligência da Vitória §aLIGADA§r! Agora ela usa a mesma IA do Senhor Cummins para conversar e interagir no chat!");
            mod.brain().broadcastAsVitoria("au au!! *abana o rabinho tricolor bem rápido* agora eu também posso conversar e ajudar vocês no chat!! 🐾💖", null);
        } else {
            send(src, "§d[Vitória]§r Inteligência da Vitória §cDESLIGADA§r! Ela continuará fazendo companhia cosmética aos jogadores.");
        }
        return 1;
    }

    private static ServerPlayerEntity resolveTargetPlayer(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) return p;
        List<ServerPlayerEntity> list = src.getServer().getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    private static ServerPlayerEntity resolveVitoriaTargetPlayer(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) return p;
        for (ServerPlayerEntity sp : src.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase("ArzanLion")) return sp;
        }
        List<ServerPlayerEntity> list = src.getServer().getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildPipiraLiteral(String literalName, CumminsMod mod) {
        return CommandManager.literal(literalName)
                .executes(c -> summonPipiraCommand(c.getSource(), mod))
                .then(CommandManager.literal("tp").executes(c -> summonPipiraCommand(c.getSource(), mod)))
                .then(CommandManager.literal("summon").executes(c -> summonPipiraCommand(c.getSource(), mod)))
                .then(CommandManager.literal("despawn").executes(c -> despawnPipiraCommand(c.getSource(), mod)))
                .then(CommandManager.literal("recolher").executes(c -> despawnPipiraCommand(c.getSource(), mod)))
                .then(CommandManager.literal("dismiss").executes(c -> despawnPipiraCommand(c.getSource(), mod)));
    }

    private static int summonPipiraCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = resolvePipiraTargetPlayer(src);
        if (player == null) {
            send(src, "§6[Pipira]§r §cNenhum jogador online no momento para o Pipira aparecer!");
            return 0;
        }
        mod.pipira().summonOrTeleportTo(player);
        String targetName = player.getGameProfile().getName();
        send(src, "§6[Pipira]§r O lendário peixe terrestre §6Pipira§r §c(Magikarp do Kikkawa)§r apareceu dando saltinhos perto de §e" + targetName + "§r! 🐟💦");
        return 1;
    }

    private static int despawnPipiraCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.pipira() == null) return 0;
        mod.pipira().dismiss();
        send(src, "§6[Pipira]§r O peixe Pipira foi recolhido de volta para a água! 🐟💦");
        return 1;
    }

    private static ServerPlayerEntity resolvePipiraTargetPlayer(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) return p;
        for (ServerPlayerEntity sp : src.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase("kikkawa")) return sp;
        }
        List<ServerPlayerEntity> list = src.getServer().getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildJuninhoLiteral(String literalName, CumminsMod mod) {
        return CommandManager.literal(literalName)
                .executes(c -> summonJuninhoCommand(c.getSource(), mod))
                .then(CommandManager.literal("tp").executes(c -> summonJuninhoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("summon").executes(c -> summonJuninhoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("despawn").executes(c -> despawnJuninhoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("recolher").executes(c -> despawnJuninhoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("dismiss").executes(c -> despawnJuninhoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("batleite").executes(c -> giveBatMilkCommand(c.getSource(), mod)))
                .then(CommandManager.literal("leite").executes(c -> giveBatMilkCommand(c.getSource(), mod)));
    }

    private static int giveBatMilkCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        ItemStack milk = JuninhoBody.createBatMilkItem();
        if (!player.getInventory().insertStack(milk)) {
            player.dropItem(milk, false);
        }
        send(src, "§8[Juninho]§r Você recebeu um frasco fresco de §f§lBat Leite§r! 🦇🥛 (Todos os buffs positivos por 15s ao beber)");
        return 1;
    }

    private static int summonJuninhoCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.juninho() == null) return 0;
        ServerPlayerEntity player = resolveJuninhoTargetPlayer(src);
        if (player == null) {
            send(src, "§8[Juninho]§r §cNenhum jogador online no momento para o Juninho aparecer!");
            return 0;
        }
        mod.juninho().summonOrTeleportTo(player);
        String targetName = player.getGameProfile().getName();
        send(src, "§8[Juninho]§r O fiel morcego guardião §8Juninho§r §5(de Jodevil)§r apareceu batendo asinhas nas sombras perto de §e" + targetName + "§r! 🦇✨");
        return 1;
    }

    private static int despawnJuninhoCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.juninho() == null) return 0;
        mod.juninho().dismiss();
        send(src, "§8[Juninho]§r Juninho recolheu as asas e voltou para as sombras! 🦇");
        return 1;
    }

    private static ServerPlayerEntity resolveJuninhoTargetPlayer(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) return p;
        for (ServerPlayerEntity sp : src.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(JuninhoBody.OWNER_NAME)) return sp;
        }
        List<ServerPlayerEntity> list = src.getServer().getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildJavidizineoLiteral(String literalName, CumminsMod mod) {
        return CommandManager.literal(literalName)
                .executes(c -> summonJavidizineoCommand(c.getSource(), mod))
                .then(CommandManager.literal("tp").executes(c -> summonJavidizineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("summon").executes(c -> summonJavidizineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("despawn").executes(c -> despawnJavidizineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("recolher").executes(c -> despawnJavidizineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("dismiss").executes(c -> despawnJavidizineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("cor")
                        .executes(c -> showCatVariantsHelp(c.getSource(), "javidizineo", mod.javidizineo() != null ? mod.javidizineo().getVariantName() : "red"))
                        .then(CommandManager.argument("variante", StringArgumentType.word()).executes(c ->
                                setJavidizineoVariantCommand(c.getSource(), mod, StringArgumentType.getString(c, "variante")))));
    }

    private static int summonJavidizineoCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.javidizineo() == null) return 0;
        ServerPlayerEntity player = resolveJavidizineoTargetPlayer(src);
        if (player == null) {
            send(src, "§6[Javidizineo]§r §cNenhum jogador online no momento para o Javidizineo aparecer!");
            return 0;
        }
        mod.javidizineo().summonOrTeleportTo(player);
        String targetName = player.getGameProfile().getName();
        send(src, "§6[Javidizineo]§r O leãozinho em miniatura §6Javidizineo§r §e(leal guardião de Azarasquel)§r apareceu soltando um mini rugido perto de §e" + targetName + "§r! 🦁✨ *rawrrr!*");
        return 1;
    }

    private static int despawnJavidizineoCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.javidizineo() == null) return 0;
        mod.javidizineo().dismiss();
        send(src, "§6[Javidizineo]§r Javidizineo retornou temporariamente ao seu templo solar! 🦁☀️");
        return 1;
    }

    private static ServerPlayerEntity resolveJavidizineoTargetPlayer(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) return p;
        for (ServerPlayerEntity sp : src.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(JavidizineoBody.OWNER_NAME)) return sp;
        }
        List<ServerPlayerEntity> list = src.getServer().getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildJinqiuzineoLiteral(String literalName, CumminsMod mod) {
        return CommandManager.literal(literalName)
                .executes(c -> summonJinqiuzineoCommand(c.getSource(), mod))
                .then(CommandManager.literal("tp").executes(c -> summonJinqiuzineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("summon").executes(c -> summonJinqiuzineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("despawn").executes(c -> despawnJinqiuzineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("recolher").executes(c -> despawnJinqiuzineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("dismiss").executes(c -> despawnJinqiuzineoCommand(c.getSource(), mod)))
                .then(CommandManager.literal("cor")
                        .executes(c -> showCatVariantsHelp(c.getSource(), "jinqiuzineo", mod.jinqiuzineo() != null ? mod.jinqiuzineo().getVariantName() : "white"))
                        .then(CommandManager.argument("variante", StringArgumentType.word()).executes(c ->
                                setJinqiuzineoVariantCommand(c.getSource(), mod, StringArgumentType.getString(c, "variante")))));
    }

    private static int summonJinqiuzineoCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.jinqiuzineo() == null) return 0;
        ServerPlayerEntity player = resolveJinqiuzineoTargetPlayer(src);
        if (player == null) {
            send(src, "§5[Jinqiuzineo]§r §cNenhum jogador online no momento para o Jinqiuzineo aparecer!");
            return 0;
        }
        mod.jinqiuzineo().summonOrTeleportTo(player);
        String targetName = player.getGameProfile().getName();
        send(src, "§5[Jinqiuzineo]§r O mini-tigre carcereiro §5Jinqiuzineo§r §d(guardião de Azarasquel)§r estalou suas correntes perto de §e" + targetName + "§r! 🐯⛓️");
        return 1;
    }

    private static int despawnJinqiuzineoCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.jinqiuzineo() == null) return 0;
        mod.jinqiuzineo().dismiss();
        send(src, "§5[Jinqiuzineo]§r Jinqiuzineo recolheu suas correntes e se retirou para a prisão de Aeterna! 🐯⛓️");
        return 1;
    }

    private static ServerPlayerEntity resolveJinqiuzineoTargetPlayer(ServerCommandSource src) {
        ServerPlayerEntity p = src.getPlayer();
        if (p != null) return p;
        for (ServerPlayerEntity sp : src.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getGameProfile().getName().equalsIgnoreCase(JinqiuzineoBody.OWNER_NAME)) return sp;
        }
        List<ServerPlayerEntity> list = src.getServer().getPlayerManager().getPlayerList();
        return list.isEmpty() ? null : list.get(0);
    }

    private static int showCatVariantsHelp(ServerCommandSource src, String commandPrefix, String currentVariant) {
        send(src, "§6=== 🐱 CORES / PELAGENS DE GATO DISPONÍVEIS ===");
        send(src, "§fPelagem atual: §e" + currentVariant);
        send(src, "§7Opções válidas no Minecraft:");
        send(src, "  §f• §ewhite §7(Branco puro)");
        send(src, "  §f• §ered §7(Ruivo / Laranja)");
        send(src, "  §f• §eblack §7(Preto com olhos verdes)");
        send(src, "  §f• §eall_black §7(Preto absoluto)");
        send(src, "  §f• §esiamese §7(Siamês)");
        send(src, "  §f• §ecalico §7(Tricolor Rajado)");
        send(src, "  §f• §epersian §7(Persa creme/amarelado)");
        send(src, "  §f• §eragdoll §7(Ragdoll)");
        send(src, "  §f• §ebritish_shorthair §7(Cinza azulado)");
        send(src, "  §f• §ejellie §7(Cinza e branco)");
        send(src, "  §f• §etabby §7(Listrado marrom clássico)");
        send(src, "§eUse: §6/" + commandPrefix + " cor <nome_da_opcao>");
        return 1;
    }

    private static int setJavidizineoVariantCommand(ServerCommandSource src, CumminsMod mod, String variant) {
        if (mod.javidizineo() == null) return 0;
        boolean ok = mod.javidizineo().setVariantName(variant);
        if (ok) {
            send(src, "§6[Javidizineo]§r Pelagem do leãozinho alterada com sucesso para §e" + variant.toLowerCase(Locale.ROOT) + "§r! 🦁✨");
            return 1;
        } else {
            send(src, "§c[Javidizineo] Pelagem '" + variant + "' inválida!");
            return showCatVariantsHelp(src, "javidizineo", mod.javidizineo().getVariantName());
        }
    }

    private static int setJinqiuzineoVariantCommand(ServerCommandSource src, CumminsMod mod, String variant) {
        if (mod.jinqiuzineo() == null) return 0;
        boolean ok = mod.jinqiuzineo().setVariantName(variant);
        if (ok) {
            send(src, "§5[Jinqiuzineo]§r Pelagem do tigre carcereiro alterada com sucesso para §e" + variant.toLowerCase(Locale.ROOT) + "§r! 🐯✨");
            return 1;
        } else {
            send(src, "§c[Jinqiuzineo] Pelagem '" + variant + "' inválida!");
            return showCatVariantsHelp(src, "jinqiuzineo", mod.jinqiuzineo().getVariantName());
        }
    }

    private static int setCumminsVariantCommand(ServerCommandSource src, CumminsMod mod, String variant) {
        if (mod.body() == null) return 0;
        boolean ok = mod.body().setVariant(variant);
        if (ok) {
            send(src, "§6[Cummins]§r Pelagem do Senhor Cummins alterada com sucesso para §e" + variant.toLowerCase(Locale.ROOT) + "§r! 🐱☕");
            return 1;
        } else {
            send(src, "§c[Cummins] Pelagem '" + variant + "' inválida!");
            return showCatVariantsHelp(src, "cummins gato", mod.config().body != null && mod.config().body.variant != null ? mod.config().body.variant : "jellie");
        }
    }

    private static ServerBossBar adminBossBar = null;
    private static int adminBossBarTicksRemaining = 0;
    private static int adminBossBarTotalTicks = 140; // 7 segundos (140 ticks)

    public static synchronized void broadcastAdminScreenMessage(MinecraftServer server, String message) {
        if (server == null || message == null || message.isBlank()) return;

        String clean = message.trim();
        Text bossBarText = Text.literal("§6§l📢 [ADMINISTRADOR] §f§l" + clean);

        adminBossBarTotalTicks = 140; // 7 segundos de exibição suave no topo da tela
        adminBossBarTicksRemaining = adminBossBarTotalTicks;

        if (adminBossBar == null) {
            adminBossBar = new ServerBossBar(bossBarText, BossBar.Color.YELLOW, BossBar.Style.PROGRESS);
        } else {
            adminBossBar.setName(bossBarText);
            adminBossBar.setPercent(1.0f);
            adminBossBar.setVisible(true);
        }

        // Limpa qualquer Title central caso ainda estivesse na tela para não tampar a visão
        TitleFadeS2CPacket clearFade = new TitleFadeS2CPacket(0, 0, 0);
        TitleS2CPacket clearTitle = new TitleS2CPacket(Text.empty());
        SubtitleS2CPacket clearSubtitle = new SubtitleS2CPacket(Text.empty());

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null || player.networkHandler == null) continue;
            try {
                player.networkHandler.sendPacket(clearFade);
                player.networkHandler.sendPacket(clearTitle);
                player.networkHandler.sendPacket(clearSubtitle);

                adminBossBar.addPlayer(player);

                // Efeito sonoro elegante de sino para chamar atenção sem assustar
                player.networkHandler.sendPacket(new PlaySoundS2CPacket(
                        SoundEvents.BLOCK_NOTE_BLOCK_BELL,
                        SoundCategory.VOICE,
                        player.getX(), player.getY(), player.getZ(),
                        1.0f,
                        1.0f,
                        player.getRandom().nextLong()
                ));
            } catch (Exception ignored) {}
        }
    }

    public static synchronized void tickAdminBossBar(MinecraftServer server) {
        if (adminBossBar == null || adminBossBarTicksRemaining <= 0) return;

        adminBossBarTicksRemaining--;
        if (adminBossBarTicksRemaining <= 0) {
            adminBossBar.clearPlayers();
            adminBossBar.setVisible(false);
        } else {
            float pct = (float) adminBossBarTicksRemaining / (float) adminBossBarTotalTicks;
            adminBossBar.setPercent(Math.max(0.0f, Math.min(1.0f, pct)));

            if (server != null) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (player != null && !adminBossBar.getPlayers().contains(player)) {
                        adminBossBar.addPlayer(player);
                    }
                }
            }
        }
    }

    public static synchronized void onPlayerJoin(ServerPlayerEntity player) {
        if (adminBossBar != null && adminBossBarTicksRemaining > 0 && player != null) {
            adminBossBar.addPlayer(player);
        }
    }

    public static synchronized void onPlayerLeave(ServerPlayerEntity player) {
        if (adminBossBar != null && player != null) {
            adminBossBar.removePlayer(player);
        }
    }

    private static void send(ServerCommandSource src, String msg) {
        src.sendFeedback(() -> Text.literal(msg), false);
    }

    private static int sendConnectionDiagnostic(ServerCommandSource src, CumminsMod mod) {
        if (mod.networkMonitor() == null) {
            send(src, "§c[Cummins] Monitor de rede não inicializado.");
            return 0;
        }
        var stats = mod.networkMonitor().get5MinStats();
        double tps = mod.telemetry() != null ? mod.telemetry().getCurrentTps() : 20.0;
        String target = mod.config().networkMonitor != null ? mod.config().networkMonitor.pingTarget : "8.8.8.8";

        send(src, "§6=== 🌐 SAÚDE DA CONEXÃO & INTERNET DO SERVIDOR ===");
        send(src, "§fStatus da Conexão: " + (stats.isOnline ? "§aONLINE (Estável)" : "§cINSTÁVEL / QUEDA DETECTADA"));
        send(src, "§fAlvo de Ping Contínuo: §e" + target + " §7(Google DNS)");
        send(src, "§fPing Atual: §b" + stats.currentPingMs + "ms §7| TPS Servidor: §a" + String.format(Locale.ROOT, "%.1f", tps));
        send(src, "§fÚltimos 5 Minutos:");
        send(src, "  §7• Perda de Pacotes: " + (stats.packetLossPercent < 2.0 ? "§a" : "§c") + stats.packetLossPercent + "% §7(" + stats.lostSamples + "/" + stats.totalSamples + " pings)");
        send(src, "  §7• Latência Média: §e" + stats.avgPingMs + "ms §7(Mín: §a" + stats.minPingMs + "ms§7, Máx: §c" + stats.maxPingMs + "ms§7)");
        if (stats.outagesCount > 0 && stats.lastOutage != null) {
            long agoSec = Math.max(1, (System.currentTimeMillis() - stats.lastOutage.endMs) / 1000L);
            String agoStr = agoSec < 60 ? (agoSec + "s atrás") : ((agoSec / 60) + "m atrás");
            send(src, "  §7• Quedas Registradas: §c" + stats.outagesCount + " oscilação(ões) §7(última durou §e" + stats.lastOutage.durationSeconds + "s§7, há §c" + agoStr + "§7)");
            send(src, "  §e⚠ Se você sentiu lag ou caiu recentemente, o problema foi no lado do servidor (host)!");
        } else {
            send(src, "  §7• Quedas Registradas: §aNenhuma queda nos últimos 5 minutos!");
            send(src, "  §a✔ Internet do servidor 100% estável. Se você sentiu lag, foi na sua conexão ou operadora.");
        }
        return 1;
    }

    private static int serveCafeCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.cafe() == null) return 0;
        return mod.cafe().serveCafe(player);
    }

    private static int handleMaloteDefault(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.malote() == null) return 0;
        int claimed = mod.malote().claimPending(player);
        if (claimed == 0) {
            send(src, "§6=== 📦 O MALOTE DO PEUGEOT 206 (CORREIO DA FIRMA) ===");
            send(src, "§eEnvie o item da sua mão principal para qualquer funcionário distante:");
            send(src, "§f• §a/malote <jogador> §7ou §a/enviar <jogador>");
            send(src, "§f• §a/malote receber §7- Coleta encomendas pendentes na caixa postal.");
        }
        return 1;
    }

    private static int claimMaloteCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.malote() == null) return 0;
        return mod.malote().claimPending(player);
    }

    private static int sendMaloteCommand(ServerCommandSource src, CumminsMod mod, String targetName) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.malote() == null) return 0;
        return mod.malote().sendItem(player, targetName);
    }

    private static int openAlmoxarifadoCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.almoxarifado() == null) return 0;
        mod.almoxarifado().openAlmoxarifado(player);
        return 1;
    }

    private static int donateAlmoxarifadoCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.almoxarifado() == null) return 0;
        return mod.almoxarifado().donateHeldItem(player);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> buildTvLiteral(String literalName, CumminsMod mod) {
        return CommandManager.literal(literalName)
                .executes(c -> handleTvDefault(c.getSource(), mod))
                .then(CommandManager.literal("start").executes(c -> startTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("ligar").executes(c -> startTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("on").executes(c -> startTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("stop").executes(c -> stopTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("sair").executes(c -> stopTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("desligar").executes(c -> stopTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("off").executes(c -> stopTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("next").executes(c -> nextTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("proximo").executes(c -> nextTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("prev").executes(c -> prevTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("anterior").executes(c -> prevTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("continuar").executes(c -> resumeTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("resume").executes(c -> resumeTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("refocar").executes(c -> refocusTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("fix").executes(c -> refocusTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("focar")
                        .then(CommandManager.argument("jogador", StringArgumentType.word()).executes(c ->
                                lockTvCommand(c.getSource(), mod, StringArgumentType.getString(c, "jogador")))))
                .then(CommandManager.literal("lock")
                        .then(CommandManager.argument("jogador", StringArgumentType.word()).executes(c ->
                                lockTvCommand(c.getSource(), mod, StringArgumentType.getString(c, "jogador")))))
                .then(CommandManager.literal("tempo")
                        .then(CommandManager.argument("segundos", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 300)).executes(c ->
                                setTvTimeCommand(c.getSource(), mod, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "segundos")))))
                .then(CommandManager.literal("bot")
                        .then(CommandManager.argument("nick", StringArgumentType.word()).executes(c ->
                                setTvBotCommand(c.getSource(), mod, StringArgumentType.getString(c, "nick")))))
                .then(CommandManager.literal("status").executes(c -> statusTvCommand(c.getSource(), mod)))
                .then(CommandManager.literal("modo")
                        .executes(c -> showTvModeCommand(c.getSource(), mod))
                        .then(CommandManager.argument("tipo", StringArgumentType.word()).executes(c ->
                                setTvModeCommand(c.getSource(), mod, StringArgumentType.getString(c, "tipo")))))
                .then(CommandManager.literal("mode")
                        .executes(c -> showTvModeCommand(c.getSource(), mod))
                        .then(CommandManager.argument("tipo", StringArgumentType.word()).executes(c ->
                                setTvModeCommand(c.getSource(), mod, StringArgumentType.getString(c, "tipo")))))
                .then(CommandManager.literal("modonext").executes(c -> cycleTvModeCommand(c.getSource(), mod)))
                .then(CommandManager.literal("modoproximo").executes(c -> cycleTvModeCommand(c.getSource(), mod)));
    }

    private static int handleTvDefault(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.tvManager() == null) return 0;
        if (mod.tvManager().isTvSpectator(player.getUuid())) {
            mod.tvManager().cycleNext(player);
        } else {
            mod.tvManager().startTv(player);
        }
        return 1;
    }

    private static int startTvCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.tvManager() == null) return 0;
        mod.tvManager().startTv(player);
        return 1;
    }

    private static int stopTvCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            send(src, "§cComando exclusivo para jogadores!");
            return 0;
        }
        if (mod.tvManager() == null) return 0;
        mod.tvManager().stopTv(player);
        return 1;
    }

    private static int nextTvCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;
        if (mod.tvManager() == null) return 0;
        mod.tvManager().cycleNext(player);
        return 1;
    }

    private static int prevTvCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;
        if (mod.tvManager() == null) return 0;
        mod.tvManager().cyclePrev(player);
        return 1;
    }

    private static int resumeTvCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;
        if (mod.tvManager() == null) return 0;
        mod.tvManager().resumeCycle(player);
        return 1;
    }

    private static int refocusTvCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || mod.tvManager() == null) return 0;
        mod.tvManager().refocusCamera(player);
        return 1;
    }

    private static int lockTvCommand(ServerCommandSource src, CumminsMod mod, String targetName) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;
        if (mod.tvManager() == null) return 0;
        ServerPlayerEntity target = src.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            send(src, "§6[Canalhas TV]§c Jogador '" + targetName + "' não encontrado online!");
            return 0;
        }
        mod.tvManager().lockTarget(player, target);
        return 1;
    }

    private static int setTvTimeCommand(ServerCommandSource src, CumminsMod mod, int seconds) {
        if (mod.tvManager() == null) return 0;
        mod.tvManager().setFocusDurationSeconds(seconds);
        send(src, "§6📺 [Canalhas TV]§a Tempo de foco por jogador atualizado para: §e" + seconds + " segundos§a.");
        return 1;
    }

    private static int setTvBotCommand(ServerCommandSource src, CumminsMod mod, String nick) {
        if (mod.tvManager() == null) return 0;
        mod.tvManager().setBotNick(nick);
        send(src, "§6📺 [Canalhas TV]§a Nick do bot de transmissão configurado para: §e" + nick + "§a.\nSempre que este nick conectar, entrará em modo TV automaticamente!");
        return 1;
    }

    private static int statusTvCommand(ServerCommandSource src, CumminsMod mod) {
        if (mod.tvManager() == null) return 0;
        var cfg = mod.tvManager().getConfig();
        send(src, "§6📺 [Canalhas TV - Status]§r\n" +
                "§7• Bot de Transmissão: §e" + cfg.botNick + "\n" +
                "§7• Tempo de Carrossel: §e" + cfg.focusDurationSeconds + "s\n" +
                "§7• Pular Jogadores AFK: §e" + (cfg.skipAfk ? "Sim (" + cfg.afkThresholdSeconds + "s)" : "Não") + "\n" +
                "§7• Auto-iniciar ao Conectar: §e" + (cfg.autoStartOnJoin ? "Sim" : "Não"));
        return 1;
    }

    private static int showTvModeCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || mod.tvManager() == null) return 0;
        var current = mod.tvManager().getCameraMode(player);
        send(src, "§6📺 [Canalhas TV - Modos de Visão]§r\n" +
                "§7Modo atual: §e" + current.icon + " " + current.displayName + "§r\n\n" +
                "§fModos disponíveis para alternar:\n" +
                " • §b/tv modo olhos§7 ➔ 🎥 1ª Pessoa (visão real do jogador)\n" +
                " • §b/tv modo drone§7 ➔ 🚁 Drone Traseiro (3ª pessoa de perseguição)\n" +
                " • §b/tv modo frente§7 ➔ 🎤 Câmera Frontal (estilo selfie / entrevista)\n" +
                " • §b/tv modo orbita§7 ➔ 🔄 Órbita 360° (giro cinemático contínuo)\n" +
                " • §b/tv modo topo§7 ➔ 🛰️ Visão Aérea (estilo satélite / GTA clássico)\n" +
                " • §b/tv modo auto§7 ➔ 🎬 Diretor Automático (mistura dinâmica inteligente)");
        return 1;
    }

    private static int setTvModeCommand(ServerCommandSource src, CumminsMod mod, String modeName) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || mod.tvManager() == null) return 0;
        var mode = CumminsTvManager.TvCameraMode.fromString(modeName);
        mod.tvManager().setCameraMode(player, mode);
        return 1;
    }

    private static int cycleTvModeCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || mod.tvManager() == null) return 0;
        mod.tvManager().cycleNextMode(player);
        return 1;
    }

    private static int showMutiraoCommand(ServerCommandSource src, CumminsMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null || mod.mutirao() == null) return 0;
        var state = mod.mutirao().getPlayerState(player.getUuid());
        var haste = player.getStatusEffect(net.minecraft.entity.effect.StatusEffects.HASTE);

        if (haste != null && state != null) {
            int remainingSec = haste.getDuration() / 20;
            int min = remainingSec / 60;
            int sec = remainingSec % 60;
            String roman = CumminsMutiraoManager.toRoman(state.level);
            String nearby = state.nearbyTeammates.isEmpty()
                    ? "§cNenhum no momento §7(fique a até 28 blocos para aprimorar ao renovar!)"
                    : "§a" + String.join(", ", state.nearbyTeammates) + " §e(a menos de 28 blocos)";

            send(src, "§6⚡ [Mutirão da Firma]§r\n" +
                    "§7• Nível Atual: §eNível " + roman + " §a(+Pressa " + roman + " & +Agilidade " + roman + ")\n" +
                    "§7• Duração Restante: §b" + min + "m " + String.format("%02d", sec) + "s §7(ciclo total de 5 min)\n" +
                    "§7• Colegas Trabalhando Junto: " + nearby);
        } else {
            send(src, "§6⚡ [Mutirão da Firma]§r Você não está em um mutirão no momento.\n" +
                    "§7Dica: Trabalhe a até §e28 blocos§7 de outro colega para ativar o bônus de equipe (Pressa & Agilidade por 5 min)!");
        }
        return 1;
    }
}
