package com.canalhas.jacinto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persistência de dados do Jacinto:
 * - Posição da base/spawn
 * - Equipamentos salvos (NBT completo: encantamentos, trims, etc.)
 * - Estatísticas (total de itens organizados)
 */
public class JacintoData {
    private static final Logger LOG = LoggerFactory.getLogger("JacintoData");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String jacintoUuid = null;
    public double baseX = 0.0;
    public double baseY = 64.0;
    public double baseZ = 0.0;
    public String baseDimension = "minecraft:overworld";
    public boolean baseSet = false;

    public long totalItemsOrganized = 0;
    public long totalBlocksMined = 0;

    // Estado da tarefa atual: "IDLE", "ORGANIZING", "MINING"
    public String currentTask = "IDLE";
    public String miningTarget = "diamante";
    public String miningDimension = "minecraft:overworld";
    public double miningX = 0.0;
    public double miningY = 0.0;
    public double miningZ = 0.0;

    // Baú de depósito da mineração passiva
    public boolean hasMiningChest = false;
    public int miningChestX = 0;
    public int miningChestY = 0;
    public int miningChestZ = 0;
    public String miningChestDimension = "minecraft:overworld";
    public int carriedMinedCount = 0;

    // Baú de Lore Vampírica do Jodevil (criado ao lado do ponto de spawn salvo dele)
    public boolean jodevilLoreChestCreated = false;
    public int jodevilLoreChestX = -676;
    public int jodevilLoreChestY = 94;
    public int jodevilLoreChestZ = -273;
    public String jodevilLoreChestDimension = "minecraft:overworld";

    // Equipamentos salvos como SNBT (String NBT) para preservar encantamentos, durabilidade e atributos
    public Map<String, String> equipmentSnbt = new HashMap<>();

    // Ferramentas dedicadas fornecidas por jogadores (espada, picareta, pá)
    public String customSwordSnbt = null;
    public String customPickaxeSnbt = null;
    public String customShovelSnbt = null;

    // Regras de permissão de baús: colocar / retirar itens
    public static class ChestRule {
        public boolean canInsert = true;
        public boolean canExtract = true;

        public ChestRule() {}

        public ChestRule(boolean canInsert, boolean canExtract) {
            this.canInsert = canInsert;
            this.canExtract = canExtract;
        }
    }

    public Map<String, ChestRule> chestRules = new HashMap<>();

    private transient Path filePath;

    public static JacintoData load(Path configDir) {
        Path p = configDir.resolve("jacinto_data.json");
        JacintoData data = null;
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                data = GSON.fromJson(r, JacintoData.class);
            } catch (Exception e) {
                LOG.error("[Jacinto] Falha ao ler jacinto_data.json: {}", e.getMessage());
            }
        }
        if (data == null) {
            data = new JacintoData();
        }
        data.filePath = p;
        return data;
    }

    public synchronized void save() {
        if (filePath == null) return;
        try {
            Files.createDirectories(filePath.getParent());
            try (Writer w = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (Exception e) {
            LOG.error("[Jacinto] Falha ao salvar jacinto_data.json: {}", e.getMessage());
        }
    }

    public void setBase(Vec3d pos, String dimension) {
        this.baseX = pos.x;
        this.baseY = pos.y;
        this.baseZ = pos.z;
        this.baseDimension = dimension;
        this.baseSet = true;
        save();
    }

    public void setMiningTask(String targetOre, Vec3d pos, String dimension) {
        setMiningTask(targetOre, pos, dimension, null, null);
    }

    public void setMiningTask(String targetOre, Vec3d pos, String dimension, BlockPos chestPos, String chestDim) {
        this.currentTask = "MINING";
        this.miningTarget = targetOre;
        if (pos != null) {
            this.miningX = pos.x;
            this.miningY = pos.y;
            this.miningZ = pos.z;
        }
        if (dimension != null) {
            this.miningDimension = dimension;
        }
        if (chestPos != null) {
            this.hasMiningChest = true;
            this.miningChestX = chestPos.getX();
            this.miningChestY = chestPos.getY();
            this.miningChestZ = chestPos.getZ();
            this.miningChestDimension = chestDim != null ? chestDim : dimension;
        } else {
            this.hasMiningChest = false;
        }
        save();
    }

    public void setOrganizingTask() {
        this.currentTask = "ORGANIZING";
        save();
    }

    public void setIdleTask() {
        this.currentTask = "IDLE";
        save();
    }

    public void setChestRule(String dimension, BlockPos pos, boolean canInsert, boolean canExtract) {
        String key = getPosKey(dimension, pos);
        if (canInsert && canExtract) {
            chestRules.remove(key);
        } else {
            chestRules.put(key, new ChestRule(canInsert, canExtract));
        }
        save();
    }

    public ChestRule getChestRule(String dimension, BlockPos pos) {
        if (pos == null) return null;
        String key = getPosKey(dimension, pos);
        return chestRules.get(key);
    }

    public boolean canInsertIntoChest(String dimension, BlockPos pos) {
        ChestRule rule = getChestRule(dimension, pos);
        return rule == null || rule.canInsert;
    }

    public boolean canExtractFromChest(String dimension, BlockPos pos) {
        ChestRule rule = getChestRule(dimension, pos);
        return rule == null || rule.canExtract;
    }

    private static String getPosKey(String dimension, BlockPos pos) {
        return (dimension != null ? dimension : "minecraft:overworld") + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public void saveEquipment(EquipmentSlot slot, ItemStack stack) {
        String key = slot.getName();
        if (stack == null || stack.isEmpty()) {
            equipmentSnbt.remove(key);
        } else {
            NbtCompound tag = new NbtCompound();
            stack.writeNbt(tag);
            equipmentSnbt.put(key, tag.toString());
        }
        save();
    }

    public ItemStack getSavedEquipment(EquipmentSlot slot) {
        String key = slot.getName();
        String snbt = equipmentSnbt.get(key);
        if (snbt == null || snbt.isBlank()) return ItemStack.EMPTY;
        try {
            NbtCompound tag = StringNbtReader.parse(snbt);
            return ItemStack.fromNbt(tag);
        } catch (Exception e) {
            LOG.warn("[Jacinto] Falha ao ler SNBT para slot {}: {}", slot, e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /* =========================================================================
     *  GERENCIAMENTO DE FERRAMENTAS (ESPADA, PICARETA, PÁ)
     *  - Padrão de Madeira: Durabilidade Infinita (Unbreakable)
     *  - Ferramentas de Jogadores: Usadas até quebrar; ao quebrar, voltam para a de madeira!
     * ========================================================================= */

    public void saveSword(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isDefaultTool(stack)) {
            customSwordSnbt = null;
        } else {
            NbtCompound tag = new NbtCompound();
            stack.writeNbt(tag);
            customSwordSnbt = tag.toString();
        }
        save();
    }

    public ItemStack getSavedSword() {
        return parseItemFromSnbt(customSwordSnbt);
    }

    public void savePickaxe(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isDefaultTool(stack)) {
            customPickaxeSnbt = null;
        } else {
            NbtCompound tag = new NbtCompound();
            stack.writeNbt(tag);
            customPickaxeSnbt = tag.toString();
        }
        save();
    }

    public ItemStack getSavedPickaxe() {
        return parseItemFromSnbt(customPickaxeSnbt);
    }

    public void saveShovel(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isDefaultTool(stack)) {
            customShovelSnbt = null;
        } else {
            NbtCompound tag = new NbtCompound();
            stack.writeNbt(tag);
            customShovelSnbt = tag.toString();
        }
        save();
    }

    public ItemStack getSavedShovel() {
        return parseItemFromSnbt(customShovelSnbt);
    }

    private static ItemStack parseItemFromSnbt(String snbt) {
        if (snbt == null || snbt.isBlank()) return ItemStack.EMPTY;
        try {
            NbtCompound tag = StringNbtReader.parse(snbt);
            return ItemStack.fromNbt(tag);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public static boolean isDefaultTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtCompound tag = stack.getNbt();
        return tag != null && tag.getBoolean("Unbreakable") &&
                (stack.getItem() == Items.WOODEN_SWORD || stack.getItem() == Items.WOODEN_PICKAXE || stack.getItem() == Items.WOODEN_SHOVEL);
    }

    public static ItemStack createDefaultWoodenSword() {
        ItemStack stack = new ItemStack(Items.WOODEN_SWORD);
        stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        stack.setCustomName(Text.literal("§eEspada de Madeira da Firma"));
        return stack;
    }

    public static ItemStack createDefaultWoodenPickaxe() {
        ItemStack stack = new ItemStack(Items.WOODEN_PICKAXE);
        stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        stack.setCustomName(Text.literal("§ePicareta de Madeira da Firma"));
        return stack;
    }

    public static ItemStack createDefaultWoodenShovel() {
        ItemStack stack = new ItemStack(Items.WOODEN_SHOVEL);
        stack.getOrCreateNbt().putBoolean("Unbreakable", true);
        stack.setCustomName(Text.literal("§ePá de Madeira da Firma"));
        return stack;
    }

    public ItemStack getEffectiveSword() {
        ItemStack s = getSavedSword();
        return !s.isEmpty() ? s : createDefaultWoodenSword();
    }

    public ItemStack getEffectivePickaxe() {
        ItemStack p = getSavedPickaxe();
        return !p.isEmpty() ? p : createDefaultWoodenPickaxe();
    }

    public ItemStack getEffectiveShovel() {
        ItemStack sh = getSavedShovel();
        return !sh.isEmpty() ? sh : createDefaultWoodenShovel();
    }
}
