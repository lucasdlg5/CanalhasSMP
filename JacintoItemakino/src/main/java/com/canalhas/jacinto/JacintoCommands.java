package com.canalhas.jacinto;

import com.mojang.brigadier.CommandDispatcher;
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

        // Alias /jacinto
        d.register(CommandManager.literal("jacinto")
                .executes(c -> handleOrganizarStatus(c.getSource(), mod))
                .then(CommandManager.literal("chamar").executes(c -> handleJacintoChamar(c.getSource(), mod)))
                .then(CommandManager.literal("base").executes(c -> handleJacintoSetBase(c.getSource(), mod)))
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
        mod.getOrganizer().tryStartOrganizing(player);
        return 1;
    }

    private static int handleOrganizarStop(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        mod.getOrganizer().stopOrganizing(player);
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
            src.sendFeedback(() -> Text.literal("§fAtividade: §b" + (mod.getOrganizer().isBusy() ? "Organizando Baús..." : "Descansando / Patrulhando a Base")), false);
        } else {
            src.sendFeedback(() -> Text.literal("§fStatus: §cFora de combate / Aguardando Respawn"), false);
        }
        src.sendFeedback(() -> Text.literal("§fTotal de Itens Organizados: §e" + data.totalItemsOrganized + " itens"), false);
        src.sendFeedback(() -> Text.literal("§7Comandos: §e/organizar§7 (olhando p/ placa) | §e/organizar stop§7 | §e/organizar chamar"), false);
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
            player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §fCheguei, patrão! O que temos pra organizar hoje?"), false);
        }
        return 1;
    }

    private static int handleJacintoSetBase(ServerCommandSource src, JacintoMod mod) {
        ServerPlayerEntity player = src.getPlayer();
        if (player == null) return 0;

        mod.getData().setBase(player.getPos(), player.getServerWorld().getRegistryKey().getValue().toString());
        player.sendMessage(Text.literal("§e[Jacinto Itemakino Baú] §aBase principal do Jacinto redefinida para a sua localização atual!\n§7(Sempre que o Jacinto renascer, voltará para cá)."), false);
        return 1;
    }

    private static int handleJacintoForceRespawn(ServerCommandSource src, JacintoMod mod) {
        mod.getNpc().spawnOrRespawn(src.getServer(), true);
        src.sendFeedback(() -> Text.literal("§a[Jacinto] Jacinto reinvocado com sucesso!"), true);
        return 1;
    }
}
