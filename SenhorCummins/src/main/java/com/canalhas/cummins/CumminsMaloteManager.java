package com.canalhas.cummins;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Malote do Peugeot 206 / Correio da Firma:
 * Permite que jogadores enviem itens e recursos instantaneamente entre si, independentemente
 * da distância entre as bases (usina do Kikkawa, castelo do Jodevil, toca do ArzanLion).
 * Se o destinatário estiver online e com espaço, recebe em mãos; se estiver offline ou lotado,
 * a encomenda fica guardada de forma segura na caixa postal persistida em disco.
 */
public class CumminsMaloteManager {
    private final CumminsMod mod;
    private final Path saveFile;
    private final Map<String, List<ItemStack>> pendingDeliveries = new ConcurrentHashMap<>();

    public CumminsMaloteManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.saveFile = configDir.resolve("malotes_pendentes.dat");
        load();
    }

    public synchronized void load() {
        try {
            if (Files.exists(saveFile)) {
                NbtCompound root = NbtIo.readCompressed(saveFile.toFile());
                if (root != null && root.contains("Malotes", 10)) {
                    NbtCompound malotesTag = root.getCompound("Malotes");
                    pendingDeliveries.clear();
                    for (String key : malotesTag.getKeys()) {
                        NbtList itemList = malotesTag.getList(key, 10);
                        List<ItemStack> list = new ArrayList<>();
                        for (int i = 0; i < itemList.size(); i++) {
                            list.add(ItemStack.fromNbt(itemList.getCompound(i)));
                        }
                        if (!list.isEmpty()) {
                            pendingDeliveries.put(key.toLowerCase(Locale.ROOT), list);
                        }
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar malotes_pendentes.dat: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            NbtCompound root = new NbtCompound();
            NbtCompound malotesTag = new NbtCompound();
            for (Map.Entry<String, List<ItemStack>> entry : pendingDeliveries.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) continue;
                NbtList list = new NbtList();
                for (ItemStack stack : entry.getValue()) {
                    if (stack != null && !stack.isEmpty()) {
                        NbtCompound itemTag = new NbtCompound();
                        stack.writeNbt(itemTag);
                        list.add(itemTag);
                    }
                }
                if (!list.isEmpty()) {
                    malotesTag.put(entry.getKey(), list);
                }
            }
            root.put("Malotes", malotesTag);
            NbtIo.writeCompressed(root, saveFile.toFile());
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar malotes_pendentes.dat: {}", e.getMessage());
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (player == null) return;
        String key = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        List<ItemStack> pending = pendingDeliveries.get(key);
        if (pending != null && !pending.isEmpty()) {
            player.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §eVocê tem §f" + pending.size() + "§e encomenda(s) na caixa postal! Digite §f/malote §epara receber."), false);
        }
    }

    public int sendItem(ServerPlayerEntity sender, String targetName) {
        if (sender == null) return 0;
        ItemStack held = sender.getMainHandStack();
        if (held.isEmpty()) {
            sender.sendMessage(Text.literal("§6[Cummins]§r §cVocê precisa segurar um item na mão principal para despachar pelo malote!"), false);
            return 0;
        }

        if (targetName == null || targetName.isBlank()) {
            sender.sendMessage(Text.literal("§6[Cummins]§r §cUso correto: /malote <jogador> ou /enviar <jogador>"), false);
            return 0;
        }

        if (targetName.equalsIgnoreCase(sender.getGameProfile().getName())) {
            sender.sendMessage(Text.literal("§6[Cummins]§r §cVocê não pode despachar um malote para si mesmo, patrão! O Peugeot não dá ré em círculo. 😹"), false);
            return 0;
        }

        MinecraftServer server = sender.getServer();
        if (server == null) return 0;

        ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetName);
        if (target == null) {
            for (ServerPlayerEntity sp : server.getPlayerManager().getPlayerList()) {
                if (sp.getGameProfile().getName().equalsIgnoreCase(targetName)
                        || sp.getGameProfile().getName().toLowerCase(Locale.ROOT).startsWith(targetName.toLowerCase(Locale.ROOT))) {
                    target = sp;
                    break;
                }
            }
        }

        ItemStack toSend = held.copy();
        held.setCount(0); // Remove com segurança atômica da mão do remetente

        String itemName = toSend.getName().getString();
        int count = toSend.getCount();
        String itemDesc = count > 1 ? (count + "x " + itemName) : itemName;

        if (target != null && target.isAlive()) {
            // Destinatário online: tenta entregar direto na mochila
            if (target.getInventory().insertStack(toSend)) {
                playDeliveryEffects(target);
                target.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §eO Cummins acelerou o Peugeot fumacento e te entregou §f" + itemDesc + " §ede §b" + sender.getGameProfile().getName() + "§e!"), false);
                target.sendMessage(Text.literal("§7(Entrega expressa realizada com sucesso)"), true);

                sender.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §aEncomenda de §f" + itemDesc + " §aentregue em mãos para §e" + target.getGameProfile().getName() + "§a!"), false);
                playDispatchEffects(sender);
                return 1;
            } else {
                // Inventário do alvo cheio: guarda o restante na caixa postal
                addPending(target.getGameProfile().getName(), toSend);
                target.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §eSeu inventário estava cheio! Uma encomenda de §b" + sender.getGameProfile().getName() + " §ffoi guardada na sua caixa postal. Digite §f/malote §epara resgatar."), false);
                sender.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §aO inventário de §e" + target.getGameProfile().getName() + " §aestava cheio, então a encomenda foi guardada com segurança na caixa postal dele!"), false);
                playDispatchEffects(sender);
                return 1;
            }
        } else {
            // Destinatário offline: salva na caixa postal persistida
            addPending(targetName, toSend);
            sender.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §e" + targetName + " §aestá offline no momento, mas o malote de §f" + itemDesc + " §afoi guardado na caixa postal dele e será entregue assim que ele logar!"), false);
            playDispatchEffects(sender);
            return 1;
        }
    }

    public synchronized void addPending(String playerName, ItemStack stack) {
        if (playerName == null || stack == null || stack.isEmpty()) return;
        String key = playerName.toLowerCase(Locale.ROOT);
        pendingDeliveries.computeIfAbsent(key, k -> new ArrayList<>()).add(stack);
        save();
    }

    public synchronized int claimPending(ServerPlayerEntity player) {
        if (player == null) return 0;
        String key = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        List<ItemStack> list = pendingDeliveries.get(key);

        if (list == null || list.isEmpty()) {
            player.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §7Sua caixa postal está vazia no momento."), false);
            return 0;
        }

        Iterator<ItemStack> it = list.iterator();
        int receivedCount = 0;
        while (it.hasNext()) {
            ItemStack stack = it.next();
            if (player.getInventory().insertStack(stack)) {
                it.remove();
                receivedCount++;
            } else {
                break;
            }
        }
        save();

        if (receivedCount > 0) {
            playDeliveryEffects(player);
            player.sendMessage(Text.literal("📦 §6[Correio do Peugeot 206] §aVocê retirou §f" + receivedCount + " §aencomenda(s) da sua caixa postal!"), false);
        }

        if (!list.isEmpty()) {
            player.sendMessage(Text.literal("§eAinda restam §f" + list.size() + " §eitem(ns) na sua caixa porque seu inventário encheu. Abra espaço e digite §f/malote §enovamente!"), false);
        }

        return receivedCount;
    }

    private void playDispatchEffects(ServerPlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 0.7f, 1.3f);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_MINECART_RIDING, SoundCategory.PLAYERS, 0.5f, 1.6f);
            sw.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.3, player.getZ(), 8, 0.2, 0.2, 0.2, 0.03);
        }
    }

    private void playDeliveryEffects(ServerPlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0f, 1.2f);
            sw.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.PLAYERS, 0.8f, 1.1f);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.6, player.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
        }
    }
}
