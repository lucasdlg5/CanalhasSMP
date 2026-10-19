package com.canalhas.cummins;

import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Almoxarifado Cooperativo da Firma:
 * - Baú duplo compartilhado (54 slots) acessível por qualquer funcionário (/almoxarifado e /doar).
 * - Perfeito para pedras excedentes de escavações, pederneiras, ferramentas sobressalentes e recursos compartilhados.
 * - Persistência segura em NBT comprimido lossless (almoxarifado.dat).
 */
public class CumminsAlmoxarifadoManager {
    public static final int SLOTS = 54;
    public static final int ROWS = 6;
    private final CumminsMod mod;
    private final Path saveFile;
    private final SimpleInventory inventory;

    public CumminsAlmoxarifadoManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.saveFile = configDir.resolve("almoxarifado.dat");
        this.inventory = new SimpleInventory(SLOTS);
        this.inventory.addListener(this::onInventoryChanged);
        load();
    }

    public Inventory getInventory() {
        return inventory;
    }

    public synchronized void load() {
        try {
            if (Files.exists(saveFile)) {
                NbtCompound root = NbtIo.readCompressed(saveFile.toFile());
                if (root != null && root.contains("Items", 9)) {
                    NbtList list = root.getList("Items", 10);
                    inventory.clear();
                    for (int i = 0; i < list.size(); i++) {
                        NbtCompound itemTag = list.getCompound(i);
                        int slot = itemTag.getShort("Slot") & 0xFFFF;
                        if (slot < SLOTS) {
                            inventory.setStack(slot, ItemStack.fromNbt(itemTag));
                        }
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar almoxarifado.dat: {}", e.getMessage());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(saveFile.getParent());
            NbtCompound root = new NbtCompound();
            NbtList list = new NbtList();
            for (int i = 0; i < inventory.size(); i++) {
                ItemStack stack = inventory.getStack(i);
                if (!stack.isEmpty()) {
                    NbtCompound itemTag = new NbtCompound();
                    itemTag.putShort("Slot", (short) i);
                    stack.writeNbt(itemTag);
                    list.add(itemTag);
                }
            }
            root.put("Items", list);
            NbtIo.writeCompressed(root, saveFile.toFile());
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar almoxarifado.dat: {}", e.getMessage());
        }
    }

    private void onInventoryChanged(Inventory inv) {
        save();
    }

    public void openAlmoxarifado(ServerPlayerEntity player) {
        if (player == null) return;

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inventory, ROWS),
                Text.literal("🏛 Almoxarifado Cooperativo").formatted(Formatting.GOLD, Formatting.BOLD)
        ));

        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_CHEST_OPEN,
                SoundCategory.PLAYERS,
                0.8f, 1.0f
        );
    }

    public int donateHeldItem(ServerPlayerEntity player) {
        if (player == null) return 0;
        ItemStack held = player.getMainHandStack();
        if (held.isEmpty()) {
            player.sendMessage(Text.literal("❌ §cSegure o item que deseja doar para o Almoxarifado da firma!"), false);
            return 0;
        }

        ItemStack toDonate = held.copy();
        ItemStack leftover = inventory.addStack(toDonate);

        int donatedCount = held.getCount() - leftover.getCount();
        if (donatedCount <= 0) {
            player.sendMessage(Text.literal("❌ §cO Almoxarifado está completamente lotado no momento!"), false);
            return 0;
        }

        held.setCount(leftover.getCount());
        save();

        player.getWorld().playSound(
                null,
                player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.PLAYERS,
                0.8f, 1.2f
        );

        player.sendMessage(Text.literal("✅ §6[Almoxarifado] §aVocê doou §f" + donatedCount + "x §b" + toDonate.getName().getString() + "§a para o estoque coletivo!"), false);
        return 1;
    }
}
