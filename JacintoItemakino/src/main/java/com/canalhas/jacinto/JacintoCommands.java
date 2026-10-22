package com.canalhas.jacinto;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public final class JacintoCommands {
    private JacintoCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d, JacintoMod mod) {
        // Comando principal /organizar
        d.register(CommandManager.literal("organizar")
                .executes(c -> handleOrganizarStart(c.getSource(), mod))
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
                        .executes(c -> handleMinerarStart(c.getSource(), mod, StringArgumentType.getString(c, "item"))))
                .executes(c -> handleMinerarHelp(c.getSource(), mod))
        );

        // Alias e centralizador /jacinto
        d.register(CommandManager.literal("jacinto")
                .executes(c -> handleOrganizarStatus(c.getSource(), mod))
                .then(CommandManager.literal("minerar")
                        .then(CommandManager.literal("stop").executes(c -> handleMinerarStop(c.getSource(), mod)))
                        .then(CommandManager.literal("parar").executes(c -> handleMinerarStop(c.getSource(), mod)))
                        .then(CommandManager.literal("status").executes(c -> handleMinerarStatus(c.getSource(), mod)))
                        .then(CommandManager.argument("item", StringArgumentType.greedyString())
                                .executes(c -> handleMinerarStart(c.getSource(), mod, StringArgumentType.getString(c, "item")))))
                .then(CommandManager.literal("parar").executes(c -> handlePararGeral(c.getSource(), mod)))
                .then(CommandManager.literal("stop").executes(c -> handlePararGeral(c.getSource(), mod)))
                .then(CommandManager.literal("chamar").executes(c -> handleJacintoChamar(c.getSource(), mod)))
                .then(CommandManager.literal("base").executes(c -> handleJacintoSetBase(c.getSource(), mod)))
                .then(CommandManager.literal("status").executes(c -> handleOrganizarStatus(c.getSource(), mod)))
                .then(CommandManager.literal("respawn").requires(s -> s.hasPermissionLevel(2))
                        .executes(c -> handleJacintoForceRespawn(c.getSource(), mod)))
        );
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

    private static int handleMinerarStart(ServerCommandSource src, JacintoMod mod, String item) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) {
            src.sendFeedback(() -> Text.literal("§cComando exclusivo para jogadores dentro do jogo!"), false);
            return 0;
        }
        mod.getMiner().tryStartMining(player, item);
        return 1;
    }

    private static int handleMinerarStop(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        mod.getMiner().stopMining(player);
        return 1;
    }

    private static int handleMinerarHelp(ServerCommandSource src, JacintoMod mod) {
        src.sendFeedback(() -> Text.literal("§6=== ⛏️ JACINTO ITEMAKINO - MINERAÇÃO PASSIVA ==="), false);
        src.sendFeedback(() -> Text.literal("§fComo usar: §e/minerar <minério ou item>"), false);
        src.sendFeedback(() -> Text.literal("§fExemplos: §e/minerar diamante§7, §e/minerar ferro§7, §e/minerar ouro§7, §e/minerar ancient_debris"), false);
        src.sendFeedback(() -> Text.literal("§fOutros comandos: §e/minerar stop§7 | §e/minerar status"), false);
        src.sendFeedback(() -> Text.literal("§7(O Jacinto usa um scanner de veios, mantém seus chunks forçados e nunca despawna mesmo longe!)"), false);
        return 1;
    }

    private static int handleMinerarStatus(ServerCommandSource src, JacintoMod mod) {
        JacintoMiner miner = mod.getMiner();
        src.sendFeedback(() -> Text.literal("§6=== ⛏️ STATUS DA MINERAÇÃO DO JACINTO ==="), false);
        if (miner.isMining()) {
            src.sendFeedback(() -> Text.literal("§fStatus: §aAtivo minerando §6§l[" + miner.getTargetDisplayName() + "]"), false);
            src.sendFeedback(() -> Text.literal("§fMinerados nesta sessão: §e" + miner.getSessionBlocksMined() + " blocos"), false);
            src.sendFeedback(() -> Text.literal("§fChunks Carregados (3x3 Forçados): §b" + miner.getForcedChunks().size() + " chunks"), false);
        } else {
            src.sendFeedback(() -> Text.literal("§fStatus: §7Inativo / Ocioso. Use §e/minerar <item>§7 para ativá-lo!"), false);
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
        src.sendFeedback(() -> Text.literal("§7Comandos: §e/organizar§7 | §e/minerar <item>§7 | §e/minerar stop§7 | §e/jacinto chamar"), false);
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
