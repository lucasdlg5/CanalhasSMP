package com.canalhas.jacinto;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class JacintoCommands {
    private JacintoCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d, JacintoMod mod) {
        // Sub-árvores para permissões de baú: /organizar <colocar> <tirar>
        var simBranch = CommandManager.literal("sim")
                .then(CommandManager.literal("sim").executes(c -> handleChestPermission(c.getSource(), mod, true, true)))
                .then(CommandManager.literal("nao").executes(c -> handleChestPermission(c.getSource(), mod, true, false)))
                .then(CommandManager.literal("não").executes(c -> handleChestPermission(c.getSource(), mod, true, false)));

        var naoBranch = CommandManager.literal("nao")
                .then(CommandManager.literal("sim").executes(c -> handleChestPermission(c.getSource(), mod, false, true)))
                .then(CommandManager.literal("nao").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                .then(CommandManager.literal("não").executes(c -> handleChestPermission(c.getSource(), mod, false, false)));

        var naoAcentoBranch = CommandManager.literal("não")
                .then(CommandManager.literal("sim").executes(c -> handleChestPermission(c.getSource(), mod, false, true)))
                .then(CommandManager.literal("nao").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                .then(CommandManager.literal("não").executes(c -> handleChestPermission(c.getSource(), mod, false, false)));

        // Comando principal /organizar
        d.register(CommandManager.literal("organizar")
                .executes(c -> handleOrganizarStart(c.getSource(), mod))
                .then(CommandManager.literal("ignore").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                .then(CommandManager.literal("ignorar").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                .then(simBranch)
                .then(naoBranch)
                .then(naoAcentoBranch)
                .then(CommandManager.literal("stop").executes(c -> handleOrganizarStop(c.getSource(), mod)))
                .then(CommandManager.literal("parar").executes(c -> handleOrganizarStop(c.getSource(), mod)))
                .then(CommandManager.literal("cancelar").executes(c -> handleOrganizarStop(c.getSource(), mod)))
                .then(CommandManager.literal("status").executes(c -> handleOrganizarStatus(c.getSource(), mod)))
                .then(CommandManager.literal("chamar").executes(c -> handleJacintoChamar(c.getSource(), mod)))
                .then(CommandManager.literal("base").executes(c -> handleJacintoSetBase(c.getSource(), mod)))
        );

        // Comando de Mineração Passiva /minerar <item>
        d.register(CommandManager.literal("minerar")
                .then(CommandManager.literal("stop").executes(c -> handleMinerarStop(c.getSource(), mod)))
                .then(CommandManager.literal("parar").executes(c -> handleMinerarStop(c.getSource(), mod)))
                .then(CommandManager.literal("cancelar").executes(c -> handleMinerarStop(c.getSource(), mod)))
                .then(CommandManager.literal("status").executes(c -> handleMinerarStatus(c.getSource(), mod)))
                .then(CommandManager.argument("item", StringArgumentType.greedyString())
                        .executes(c -> handleMinerarWithChest(c.getSource(), mod, StringArgumentType.getString(c, "item"))))
                .executes(c -> handleMinerarWithChest(c.getSource(), mod, null))
        );

        // Alias e centralizador /jacinto
        d.register(CommandManager.literal("jacinto")
                .executes(c -> handleOrganizarStatus(c.getSource(), mod))
                .then(CommandManager.literal("ignore").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                .then(CommandManager.literal("ignorar").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                .then(CommandManager.literal("sim")
                        .then(CommandManager.literal("sim").executes(c -> handleChestPermission(c.getSource(), mod, true, true)))
                        .then(CommandManager.literal("nao").executes(c -> handleChestPermission(c.getSource(), mod, true, false)))
                        .then(CommandManager.literal("não").executes(c -> handleChestPermission(c.getSource(), mod, true, false))))
                .then(CommandManager.literal("nao")
                        .then(CommandManager.literal("sim").executes(c -> handleChestPermission(c.getSource(), mod, false, true)))
                        .then(CommandManager.literal("nao").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                        .then(CommandManager.literal("não").executes(c -> handleChestPermission(c.getSource(), mod, false, false))))
                .then(CommandManager.literal("não")
                        .then(CommandManager.literal("sim").executes(c -> handleChestPermission(c.getSource(), mod, false, true)))
                        .then(CommandManager.literal("nao").executes(c -> handleChestPermission(c.getSource(), mod, false, false)))
                        .then(CommandManager.literal("não").executes(c -> handleChestPermission(c.getSource(), mod, false, false))))
                .then(CommandManager.literal("minerar")
                        .then(CommandManager.literal("stop").executes(c -> handleMinerarStop(c.getSource(), mod)))
                        .then(CommandManager.literal("parar").executes(c -> handleMinerarStop(c.getSource(), mod)))
                        .then(CommandManager.literal("status").executes(c -> handleMinerarStatus(c.getSource(), mod)))
                        .then(CommandManager.argument("item", StringArgumentType.greedyString())
                                .executes(c -> handleMinerarWithChest(c.getSource(), mod, StringArgumentType.getString(c, "item"))))
                        .executes(c -> handleMinerarWithChest(c.getSource(), mod, null)))
                .then(CommandManager.literal("parar").executes(c -> handlePararGeral(c.getSource(), mod)))
                .then(CommandManager.literal("stop").executes(c -> handlePararGeral(c.getSource(), mod)))
                .then(CommandManager.literal("chamar").executes(c -> handleJacintoChamar(c.getSource(), mod)))
                .then(CommandManager.literal("base").executes(c -> handleJacintoSetBase(c.getSource(), mod)))
                .then(CommandManager.literal("status").executes(c -> handleOrganizarStatus(c.getSource(), mod)))
                .then(CommandManager.literal("respawn").requires(s -> s.hasPermissionLevel(2))
                        .executes(c -> handleJacintoForceRespawn(c.getSource(), mod)))
        );
    }

    private static int handleChestPermission(ServerCommandSource src, JacintoMod mod, boolean canInsert, boolean canExtract) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendFeedback(() -> Text.literal("§cComando exclusivo para jogadores dentro do jogo!"), false);
            return 0;
        }
        mod.getOrganizer().setChestPermission(player, canInsert, canExtract);
        return 1;
    }

    private static int handleOrganizarStart(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendFeedback(() -> Text.literal("§cComando exclusivo para jogadores dentro do jogo!"), false);
            return 0;
        }
        // Se estava minerando, interrompe mineração para iniciar organização
        if (mod.getMiner().isMining()) {
            mod.getMiner().stopMining(player);
        }
        mod.getOrganizer().tryStartOrganizing(player);
        return 1;
    }

    private static int handleOrganizarStop(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        mod.getOrganizer().stopOrganizing(player);
        return 1;
    }

    private static int handleMinerarWithChest(ServerCommandSource src, JacintoMod mod, String itemArg) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendFeedback(() -> Text.literal("§cComando exclusivo para jogadores dentro do jogo!"), false);
            return 0;
        }

        // 1. Validação obrigatória: o player DEVE estar olhando diretamente para um baú ou barril!
        BlockPos chestPos = mod.getOrganizer().findTargetChestFromPlayerLook(player);
        if (chestPos == null) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, você precisa estar olhando diretamente para um baú (ou barril) para que eu possa iniciar a mineração e saber onde guardar os itens!"), false);
            return 0;
        }

        // 2. Determina o item pretendido
        Item targetItem = null;
        String query = itemArg;
        String displayName = null;

        if (query != null && !query.isBlank()) {
            targetItem = JacintoOrganizer.matchItemName(query);
            if (targetItem != null && targetItem != Items.AIR) {
                displayName = new ItemStack(targetItem).getName().getString();
            } else {
                displayName = query.trim();
            }
        } else {
            // Se rodou sem argumentos (/minerar), tenta ler a moldura afixada no baú mirado
            ItemFrameEntity frame = mod.getOrganizer().findAttachedItemFrame(player.getServerWorld(), chestPos);
            if (frame != null && !frame.getHeldItemStack().isEmpty()) {
                targetItem = frame.getHeldItemStack().getItem();
                displayName = frame.getHeldItemStack().getName().getString();
                query = JacintoMiner.resolveQueryFromItem(targetItem);
            } else {
                player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cPatrão, especifique o que deseja minerar para guardar neste baú (ex: §e/minerar Andesito§c) ou coloque uma moldura com o item nele!"), false);
                return 0;
            }
        }

        if (targetItem == null && (query == null || query.isBlank())) {
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §cNão consegui identificar qual item minerar!"), false);
            return 0;
        }

        // 3. Verifica se o baú já está cheio deste item
        Inventory inv = JacintoOrganizer.getInventoryAtPos(player.getServerWorld(), chestPos);
        if (inv != null && targetItem != null && JacintoOrganizer.isInventoryFullOfItem(inv, targetItem)) {
            String name = displayName != null ? displayName : "este item";
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §ePatrão, este baú já está completamente CHEIO de [" + name + "]! Esvazie um pouco ou aponte para outro baú antes de iniciar a mineração."), false);
            return 0;
        }

        // 4. Inicia mineração passiva com baú de destino
        mod.getMiner().startMiningWithChest(player, targetItem, displayName, query, chestPos);
        return 1;
    }

    private static int handleMinerarStop(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        mod.getMiner().stopMining(player);
        return 1;
    }

    private static int handleMinerarHelp(ServerCommandSource src, JacintoMod mod) {
        src.sendFeedback(() -> Text.literal("§6=== ⛏️ JACINTO ITEMAKINO - MINERAÇÃO PASSIVA ==="), false);
        src.sendFeedback(() -> Text.literal("§fComo usar: Olhe diretamente para o baú onde deseja guardar e use:"), false);
        src.sendFeedback(() -> Text.literal("§61. Com argumento: §e/minerar <item> §7(ex: /minerar Andesito, /minerar Diamante, /minerar Ferro)"), false);
        src.sendFeedback(() -> Text.literal("§62. Com Moldura: §fColoque a moldura com o item no baú, olhe para ele e digite §e/minerar"), false);
        src.sendFeedback(() -> Text.literal("§7(O Jacinto minerará passivamente e guardará no baú até enchê-lo; ao ficar cheio, entra em IDLE!)"), false);
        src.sendFeedback(() -> Text.literal("§fOutros comandos: §e/minerar stop§7 | §e/minerar status"), false);
        return 1;
    }

    private static int handleMinerarStatus(ServerCommandSource src, JacintoMod mod) {
        JacintoMiner miner = mod.getMiner();
        src.sendFeedback(() -> Text.literal("§6=== ⛏️ STATUS DA MINERAÇÃO DO JACINTO ==="), false);
        if (miner.isMining()) {
            src.sendFeedback(() -> Text.literal("§fStatus: §aAtivo minerando §6§l[" + miner.getTargetDisplayName() + "]"), false);
            if (miner.hasMiningChest() && miner.getTargetChestPos() != null) {
                BlockPos cp = miner.getTargetChestPos();
                src.sendFeedback(() -> Text.literal("§fBaú de Destino: §eX: " + cp.getX() + ", Y: " + cp.getY() + ", Z: " + cp.getZ() + " §7(enche até lotar)"), false);
            }
            src.sendFeedback(() -> Text.literal("§fMinerados nesta sessão: §e" + miner.getSessionBlocksMined() + " blocos"), false);
            src.sendFeedback(() -> Text.literal("§fChunks Carregados (3x3 Forçados): §b" + miner.getForcedChunks().size() + " chunks"), false);
        } else {
            src.sendFeedback(() -> Text.literal("§fStatus: §7Inativo / Ocioso. Olhe para um baú e use §e/minerar <item>§7 para ativá-lo!"), false);
        }
        src.sendFeedback(() -> Text.literal("§fTotal de Minérios na Carreira: §b" + mod.getData().totalBlocksMined + " blocos"), false);
        return 1;
    }

    private static int handlePararGeral(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (mod.getMiner().isMining()) {
            mod.getMiner().stopMining(player);
        }
        if (mod.getOrganizer().isBusy()) {
            mod.getOrganizer().stopOrganizing(player);
        }
        return 1;
    }

    private static int handleOrganizarStatus(ServerCommandSource src, JacintoMod mod) {
        HuskEntity entity = mod.getNpc().getEntity();
        JacintoData data = mod.getData();

        src.sendFeedback(() -> Text.literal("§6=== 📦 JACINTO ITEMAKINO BAÚ - STATUS ==="), false);
        if (entity != null && entity.isAlive()) {
            src.sendFeedback(() -> Text.literal("§fStatus: §aOnline e Ativo na Firma"), false);
            src.sendFeedback(() -> Text.literal("§fVida: §a" + String.format("%.0f", entity.getHealth()) + "/20 ❤"), false);
            src.sendFeedback(() -> Text.literal("§fPosição: §eX: " + (int)entity.getX() + ", Y: " + (int)entity.getY() + ", Z: " + (int)entity.getZ() +
                    " §7(" + entity.getWorld().getRegistryKey().getValue().getPath() + ")"), false);

            String activity;
            if (mod.getMiner().isMining()) {
                activity = "Minerando " + mod.getMiner().getTargetDisplayName() + " (" + mod.getMiner().getSessionBlocksMined() + " nesta sessão)";
            } else if (mod.getOrganizer().isBusy()) {
                activity = "Organizando Baús...";
            } else {
                activity = "Descansando / Patrulhando a Base";
            }
            src.sendFeedback(() -> Text.literal("§fAtividade: §b" + activity), false);
        } else {
            src.sendFeedback(() -> Text.literal("§fStatus: §cFora de combate / Aguardando Respawn"), false);
        }
        src.sendFeedback(() -> Text.literal("§fTotal de Itens Organizados: §e" + data.totalItemsOrganized + " itens"), false);
        src.sendFeedback(() -> Text.literal("§fTotal de Minérios Extraídos: §b" + data.totalBlocksMined + " blocos"), false);
        src.sendFeedback(() -> Text.literal("§7Comandos: §e/organizar§7 | §e/organizar ignore§7 | §e/organizar sim nao§7 | §e/minerar <item>"), false);
        return 1;
    }

    private static int handleJacintoChamar(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;

        HuskEntity entity = mod.getNpc().getEntity();
        if (entity == null || !entity.isAlive()) {
            entity = mod.getNpc().spawnOrRespawn(src.getServer(), false);
        }

        if (entity != null) {
            if (entity.getWorld() != player.getServerWorld()) {
                entity.teleport(player.getServerWorld(), player.getX(), player.getY(), player.getZ(), java.util.Collections.emptySet(), player.getYaw(), 0.0f);
            } else {
                entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0f);
            }
            player.getServerWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
            player.getServerWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.NEUTRAL, 1.0f, 1.0f);
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fCheguei, patrão! Às suas ordens para organizar ou minerar!"), false);
        }
        return 1;
    }

    private static int handleJacintoSetBase(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;

        mod.getData().setBase(player.getPos(), player.getServerWorld().getRegistryKey().getValue().toString());
        player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §aBase principal do Jacinto redefinida para a sua localização atual!\n§7(Sempre que o Jacinto renascer fora de serviço, voltará para cá)."), false);
        return 1;
    }

    private static int handleJacintoForceRespawn(ServerCommandSource src, JacintoMod mod) {
        mod.getNpc().spawnOrRespawn(src.getServer(), true);
        src.sendFeedback(() -> Text.literal("§a[Jacinto] Jacinto reinvocado com sucesso!"), true);
        return 1;
    }
}
