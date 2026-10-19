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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Mula de Carga / Pochete do Senhor Cummins:
 * - Inventário rápido portátil de 9 slots acessível ao clicar com Shift + Botão Direito no gato.
 * - Armazena ferramentas de emergência, tochas, comida, minérios e mimos da firma.
 * - Salva e carrega automaticamente em NBT comprimido (lossless para encantamentos e NBT de mods).
 */
public class CumminsPocheteManager {
    public static final int SLOTS = 9;
    private final CumminsMod mod;
    private final Path saveFile;
    private final SimpleInventory inventory;

    public CumminsPocheteManager(CumminsMod mod, Path configDir) {
        this.mod = mod;
        this.saveFile = configDir.resolve("pochete.dat");
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
                        int slot = itemTag.getByte("Slot") & 255;
                        if (slot < SLOTS) {
                            inventory.setStack(slot, ItemStack.fromNbt(itemTag));
                        }
                    }
                }
            }
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao carregar pochete.dat: {}", e.getMessage());
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
                    itemTag.putByte("Slot", (byte) i);
                    stack.writeNbt(itemTag);
                    list.add(itemTag);
                }
            }
            root.put("Items", list);
            NbtIo.writeCompressed(root, saveFile.toFile());
        } catch (Exception e) {
            CumminsMod.LOG.warn("[Cummins] Erro ao salvar pochete.dat: {}", e.getMessage());
        }
    }

    private void onInventoryChanged(Inventory inv) {
        save();
    }

    public void openPochete(ServerPlayerEntity player) {
        if (player == null) return;

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, p) -> new GenericContainerScreenHandler(ScreenHandlerType.GENERIC_9X1, syncId, playerInv, inventory, 1),
                Text.literal("🎒 Pochete do Senhor Cummins").formatted(Formatting.GOLD, Formatting.BOLD)
        ));

        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
                SoundCategory.PLAYERS,
                1.0f,
                1.2f
        );
        player.getWorld().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.ENTITY_CAT_PURREOW,
                SoundCategory.PLAYERS,
                0.7f,
                1.3f
        );
    }
}
