package com.canalhas.jacinto;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Tabela e Verificador de Compatibilidade de Mineração do Jacinto Itemakino:
 * Determina se a picareta atual do Jacinto possui o nível mínimo (Harvest Tier)
 * necessário para extrair o minério com sucesso e dropar os itens sem esfarelar.
 */
public final class JacintoMiningCompatibility {
    public static final int TIER_NONE = -1;
    public static final int TIER_WOOD = 0;
    public static final int TIER_STONE = 1;
    public static final int TIER_IRON = 2;
    public static final int TIER_DIAMOND = 3;
    public static final int TIER_NETHERITE = 4;

    private static final TagKey<Block> FABRIC_NEEDS_NETHERITE = TagKey.of(RegistryKeys.BLOCK, new Identifier("fabric", "needs_tool_level_4"));

    private JacintoMiningCompatibility() {}

    public static class CompatibilityResult {
        public final boolean canMine;
        public final int currentTier;
        public final int requiredTier;
        public final String currentTierName;
        public final String requiredTierName;
        public final String pickaxeName;
        public final String targetName;
        public final String explanation;

        public CompatibilityResult(boolean canMine, int currentTier, int requiredTier,
                                   String currentTierName, String requiredTierName,
                                   String pickaxeName, String targetName, String explanation) {
            this.canMine = canMine;
            this.currentTier = currentTier;
            this.requiredTier = requiredTier;
            this.currentTierName = currentTierName;
            this.requiredTierName = requiredTierName;
            this.pickaxeName = pickaxeName;
            this.targetName = targetName;
            this.explanation = explanation;
        }
    }

    /**
     * Valida se a picareta fornecida consegue minerar o item/minério desejado
     */
    public static CompatibilityResult check(ItemStack pickaxe, Item targetItem, String query, String targetDisplayName) {
        int currentTier = getPickaxeMiningLevel(pickaxe);
        int requiredTier = getRequiredMiningLevel(targetItem, query);

        String pickName = (pickaxe == null || pickaxe.isEmpty()) ? "Mão Vazia" : pickaxe.getName().getString();
        String currentTierName = getTierName(currentTier);
        String requiredTierName = getTierName(requiredTier);
        String targetName = targetDisplayName != null ? targetDisplayName : (targetItem != null ? targetItem.getName().getString() : query);

        if (currentTier < requiredTier) {
            String explanation = "O minério [" + targetName + "] exige no mínimo uma picareta de " +
                    requiredTierName + " (Nível " + requiredTier + "), mas estou com uma " +
                    pickName + " (" + currentTierName + ", Nível " + currentTier + ")!";
            return new CompatibilityResult(false, currentTier, requiredTier, currentTierName, requiredTierName, pickName, targetName, explanation);
        }

        return new CompatibilityResult(true, currentTier, requiredTier, currentTierName, requiredTierName, pickName, targetName, "Compatível");
    }

    /**
     * Retorna o nível de mineração (0 a 4) da picareta
     */
    public static int getPickaxeTier(ItemStack pickaxe) {
        return getPickaxeMiningLevel(pickaxe);
    }

    public static int getPickaxeMiningLevel(ItemStack pickaxe) {
        if (pickaxe == null || pickaxe.isEmpty()) return TIER_NONE;

        Item item = pickaxe.getItem();
        if (item instanceof MiningToolItem tool) {
            return tool.getMaterial().getMiningLevel();
        }

        // Fallbacks manuais
        if (item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE) return TIER_WOOD;
        if (item == Items.STONE_PICKAXE) return TIER_STONE;
        if (item == Items.IRON_PICKAXE) return TIER_IRON;
        if (item == Items.DIAMOND_PICKAXE) return TIER_DIAMOND;
        if (item == Items.NETHERITE_PICKAXE) return TIER_NETHERITE;

        return TIER_WOOD;
    }

    /**
     * Retorna o nível mínimo de mineração necessário para um bloco
     */
    public static int getRequiredMiningLevel(BlockState state) {
        if (state == null) return TIER_WOOD;

        if (state.isIn(FABRIC_NEEDS_NETHERITE)) return TIER_NETHERITE;
        if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) return TIER_DIAMOND;
        if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) return TIER_IRON;
        if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) return TIER_STONE;

        return TIER_WOOD;
    }

    /**
     * Retorna o nível mínimo de mineração baseado no item e query do comando
     */
    public static int getRequiredMiningLevel(Item item, String query) {
        String q = (query != null ? query : "").toLowerCase();

        // 1. TIER 3 (DIAMANTE): Obsidiana, Detritos Ancestrais (Netherite)
        if (item == Items.OBSIDIAN || item == Items.CRYING_OBSIDIAN || item == Items.ANCIENT_DEBRIS ||
            item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP || item == Items.NETHERITE_BLOCK ||
            q.contains("obsidian") || q.contains("ancient") || q.contains("debris") || q.contains("netherite") || q.contains("detrito")) {
            return TIER_DIAMOND;
        }

        // 2. TIER 2 (FERRO): Diamante, Ouro, Redstone, Esmeralda
        if (item == Items.DIAMOND || item == Items.DIAMOND_ORE || item == Items.DEEPSLATE_DIAMOND_ORE || item == Items.DIAMOND_BLOCK ||
            item == Items.RAW_GOLD || item == Items.GOLD_INGOT || item == Items.GOLD_ORE || item == Items.DEEPSLATE_GOLD_ORE || item == Items.RAW_GOLD_BLOCK ||
            item == Items.REDSTONE || item == Items.REDSTONE_ORE || item == Items.DEEPSLATE_REDSTONE_ORE || item == Items.REDSTONE_BLOCK ||
            item == Items.EMERALD || item == Items.EMERALD_ORE || item == Items.DEEPSLATE_EMERALD_ORE || item == Items.EMERALD_BLOCK ||
            q.contains("diaman") || q.contains("ouro") || q.contains("gold") ||
            q.contains("redstone") || q.contains("esmeralda") || q.contains("emerald")) {
            return TIER_IRON;
        }

        // 3. TIER 1 (PEDRA): Ferro, Cobre, Lápis-Lazúli
        if (item == Items.RAW_IRON || item == Items.IRON_INGOT || item == Items.IRON_ORE || item == Items.DEEPSLATE_IRON_ORE || item == Items.RAW_IRON_BLOCK ||
            item == Items.RAW_COPPER || item == Items.COPPER_INGOT || item == Items.COPPER_ORE || item == Items.DEEPSLATE_COPPER_ORE || item == Items.RAW_COPPER_BLOCK ||
            item == Items.LAPIS_LAZULI || item == Items.LAPIS_ORE || item == Items.DEEPSLATE_LAPIS_ORE || item == Items.LAPIS_BLOCK ||
            q.contains("ferro") || q.contains("iron") ||
            q.contains("cobre") || q.contains("copper") ||
            q.contains("lapis") || q.contains("lazuli")) {
            return TIER_STONE;
        }

        // 4. TIER 0 (MADEIRA): Carvão, Pedras, Andesito, Granito, Diorito, Tufo, Arenito, Quartzo, etc.
        return TIER_WOOD;
    }

    public static String getTierName(int tier) {
        return switch (tier) {
            case TIER_WOOD -> "Madeira";
            case TIER_STONE -> "Pedra";
            case TIER_IRON -> "Ferro";
            case TIER_DIAMOND -> "Diamante";
            case TIER_NETHERITE -> "Netherita";
            default -> "Nenhum";
        };
    }

    /**
     * Retorna uma lista didática de compatibilidade formatada para o chat
     */
    public static List<String> getFormattedCompatibilityGuide() {
        return getFormattedCompatibilityGuide(null);
    }

    /**
     * Retorna o guia de compatibilidade incluindo a picareta atual do Jacinto
     */
    public static List<String> getFormattedCompatibilityGuide(ItemStack currentPick) {
        List<String> lines = new ArrayList<>();
        lines.add("§6=== ⛏️ GUIA DE COMPATIBILIDADE DE PICARETAS DO JACINTO ===");
        lines.add("§e🪵 Madeira (Padrão Infinita): §fCarvão, Pedra, Andesito, Granito, Diorito, Quartzo, Tufo, Arenito.");
        lines.add("§7🪨 Pedra: §fTodos os acima + Ferro, Cobre, Lápis-Lazúli.");
        lines.add("§f⚙️ Ferro: §fTodos os acima + Diamante, Ouro, Redstone, Esmeralda.");
        lines.add("§b💎 Diamante: §fTodos os acima + Obsidiana, Detritos Ancestrais (Netherite).");
        lines.add("§8🌌 Netherita: §fQuebra e extrai absolutamente tudo!");
        if (currentPick != null && !currentPick.isEmpty()) {
            int tier = getPickaxeTier(currentPick);
            String tierName = getTierName(tier);
            lines.add("§6--------------------------------------------------");
            lines.add("§ePicareta em mãos no momento: §f" + currentPick.getName().getString() + " §7(Tier: §b" + tierName + "§7)");
        }
        lines.add("§aDica: Clique com o botão direito no Jacinto segurando uma picareta melhor para equipá-lo!");
        return lines;
    }
}
