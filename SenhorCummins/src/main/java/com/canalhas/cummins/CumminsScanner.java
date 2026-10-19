package com.canalhas.cummins;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.biome.Biome;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de Scanner do Senhor Cummins:
 * - Identifica quando o jogador pergunta onde encontrar um item/bloco ("Onde posso encontrar vinhas?", "Onde tem ferro?", etc.).
 * - Suporta busca tanto em Português (PT-BR) quanto em Inglês (EN) com dicionário oficial do Minecraft.
 * - Prioriza correspondência exata para evitar falsos positivos (ex: "osso" nunca aponta para "farinha de osso").
 * - Vasculha baús/inventários, itens no chão e blocos no mundo em raio próximo (<= 24 blocos) e médio (25..72 blocos).
 * - Identifica a posição relativa do container ("no baú de cima", "no baú de baixo", "no baú duplo", "no barril", etc.).
 * - Caso não encontre por perto, faz um scan de longo alcance nos biomas ao redor e indica a direção cardeal
 *   (Norte, Sul, Leste, Oeste, Nordeste, Sudeste, Sudoeste, Noroeste).
 * - Registra o último scan de cada jogador para orientá-lo caso ele diga "não tô achando".
 */
public class CumminsScanner {

    public enum DistanceCategory {
        NEAR,       // <= 24 blocos ("Tem um no baú aqui perto, vem cá!" / "Tem um aqui perto, vem cá!")
        MEDIUM_FAR, // 25 a 72 blocos ("Tem um um pouco longe daqui, vamos lá!")
        NOT_FOUND   // > 72 blocos ("Ih, por aqui não tem, vai ter que procurar! Deve ter um no bioma a Sudeste daqui.")
    }

    public static class ScanResult {
        public final String queryLabel;
        public final DistanceCategory category;
        public final boolean inContainer;
        public final BlockPos targetPos;
        public final BlockPos standPos;
        public final double distanceBlocks;
        public final String compassDirection;
        public final String biomeHintName;
        public final String matchedItemNamePt;
        public final String matchedItemNameEn;
        public final int itemCount;
        public final String containerDescription;

        public ScanResult(String queryLabel, DistanceCategory category, boolean inContainer,
                          BlockPos targetPos, BlockPos standPos, double distanceBlocks,
                          String compassDirection, String biomeHintName,
                          String matchedItemNamePt, String matchedItemNameEn,
                          int itemCount, String containerDescription) {
            this.queryLabel = queryLabel;
            this.category = category;
            this.inContainer = inContainer;
            this.targetPos = targetPos;
            this.standPos = standPos;
            this.distanceBlocks = distanceBlocks;
            this.compassDirection = compassDirection;
            this.biomeHintName = biomeHintName;
            this.matchedItemNamePt = matchedItemNamePt;
            this.matchedItemNameEn = matchedItemNameEn;
            this.itemCount = itemCount;
            this.containerDescription = containerDescription;
        }

        public ScanResult(String queryLabel, DistanceCategory category, boolean inContainer,
                          BlockPos targetPos, BlockPos standPos, double distanceBlocks,
                          String compassDirection, String biomeHintName) {
            this(queryLabel, category, inContainer, targetPos, standPos, distanceBlocks,
                    compassDirection, biomeHintName, queryLabel, queryLabel, 1, inContainer ? "no baú" : "no chão");
        }
    }

    public static class ContainerItemMatch {
        public final Item item;
        public final int count;
        public final String namePt;
        public final String nameEn;

        public ContainerItemMatch(Item item, int count, String namePt, String nameEn) {
            this.item = item;
            this.count = count;
            this.namePt = namePt;
            this.nameEn = nameEn;
        }
    }

    private static class SearchEntry {
        final List<String> keywords;
        final List<String> itemOrBlockIds;
        final List<String> biomeHints;

        SearchEntry(List<String> keywords, List<String> itemOrBlockIds, List<String> biomeHints) {
            this.keywords = keywords;
            this.itemOrBlockIds = itemOrBlockIds;
            this.biomeHints = biomeHints;
        }
    }

    private static final List<SearchEntry> DICTIONARY = new ArrayList<>();
    private static final Map<String, List<Identifier>> PT_TO_IDS = new HashMap<>();
    private static final Map<Identifier, String> ID_TO_PT = new HashMap<>();
    private static final Map<String, List<Identifier>> EN_TO_IDS = new HashMap<>();
    private static final Map<Identifier, String> ID_TO_EN = new HashMap<>();
    private static volatile boolean LANG_LOADED = false;

    private static final Map<UUID, PlayerScanRecord> LAST_PLAYER_SCANS = new ConcurrentHashMap<>();

    public static PlayerScanRecord getLastScan(UUID uuid) {
        if (uuid == null) return null;
        return LAST_PLAYER_SCANS.get(uuid);
    }

    public static void recordScan(PlayerScanRecord record) {
        if (record != null && record.playerUuid != null) {
            LAST_PLAYER_SCANS.put(record.playerUuid, record);
        }
    }

    static {
        // Plantas, Natureza e Agricultura
        addEntry(List.of("vinha", "vinhas", "vine", "vines", "cipo"),
                List.of("vine", "weeping_vines", "twisting_vines", "cave_vines", "cave_vines_plant"),
                List.of("jungle", "sparse_jungle", "bamboo_jungle", "swamp", "mangrove_swamp", "lush_caves"));
        addEntry(List.of("bambu", "bamboo"),
                List.of("bamboo", "bamboo_block", "bamboo_sapling"),
                List.of("bamboo_jungle", "jungle", "sparse_jungle"));
        addEntry(List.of("cacto", "cactus"),
                List.of("cactus"),
                List.of("desert", "badlands", "eroded_badlands", "wooded_badlands"));
        addEntry(List.of("cana", "cana de acucar", "acucar", "sugar cane", "sugarcane"),
                List.of("sugar_cane", "sugar"),
                List.of("river", "beach", "swamp", "mangrove_swamp", "desert"));
        addEntry(List.of("abobora", "pumpkin"),
                List.of("pumpkin", "carved_pumpkin", "pumpkin_seeds"),
                List.of("plains", "meadow", "taiga", "snowy_taiga", "forest"));
        addEntry(List.of("melancia", "melon"),
                List.of("melon", "melon_slice", "melon_seeds", "melon_stem"),
                List.of("jungle", "sparse_jungle", "bamboo_jungle", "savanna"));
        addEntry(List.of("cacau", "cocoa"),
                List.of("cocoa_beans", "cocoa"),
                List.of("jungle", "sparse_jungle", "bamboo_jungle"));
        addEntry(List.of("cogumelo", "cogumelos", "mushroom"),
                List.of("red_mushroom", "brown_mushroom", "mushroom_stem", "red_mushroom_block", "brown_mushroom_block", "crimson_fungus", "warped_fungus"),
                List.of("mushroom_fields", "dark_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga", "swamp"));
        addEntry(List.of("baga", "bagas", "frutinha", "frutinhas", "sweet berries", "glow berries", "amora"),
                List.of("sweet_berries", "sweet_berry_bush", "glow_berries", "cave_vines"),
                List.of("taiga", "snowy_taiga", "old_growth_pine_taiga", "lush_caves"));
        addEntry(List.of("vitória régia", "vitoria regia", "lily pad", "nenufar"),
                List.of("lily_pad"),
                List.of("swamp", "mangrove_swamp"));
        addEntry(List.of("musgo", "moss"),
                List.of("moss_block", "moss_carpet"),
                List.of("lush_caves", "old_growth_spruce_taiga"));
        addEntry(List.of("argila", "barro", "clay"),
                List.of("clay", "clay_ball", "terracotta"),
                List.of("lush_caves", "river", "swamp", "mangrove_swamp", "badlands"));
        addEntry(List.of("lama", "mud", "mangue"),
                List.of("mud", "packed_mud", "mangrove_propagule", "mangrove_log", "mangrove_roots"),
                List.of("mangrove_swamp", "swamp"));
        addEntry(List.of("alga", "kelp"),
                List.of("kelp", "kelp_plant", "dried_kelp", "dried_kelp_block", "seagrass"),
                List.of("ocean", "deep_ocean", "lukewarm_ocean", "cold_ocean"));
        addEntry(List.of("coral", "corais"),
                List.of("tube_coral", "brain_coral", "bubble_coral", "fire_coral", "horn_coral",
                        "tube_coral_block", "brain_coral_block", "bubble_coral_block", "fire_coral_block", "horn_coral_block"),
                List.of("warm_ocean", "lukewarm_ocean"));
        addEntry(List.of("mel", "favo", "colmeia", "abelha", "honey", "honeycomb", "bee"),
                List.of("honey_bottle", "honeycomb", "bee_nest", "beehive", "honey_block"),
                List.of("meadow", "flower_forest", "sunflower_plains", "plains", "birch_forest"));
        addEntry(List.of("trigo", "wheat", "semente", "sementes"),
                List.of("wheat", "wheat_seeds", "hay_block"),
                List.of("plains", "sunflower_plains", "meadow", "savanna"));
        addEntry(List.of("cenoura", "carrot"),
                List.of("carrot", "carrots", "golden_carrot"),
                List.of("plains", "meadow", "taiga", "snowy_plains"));
        addEntry(List.of("batata", "potato"),
                List.of("potato", "potatoes", "baked_potato"),
                List.of("plains", "taiga", "meadow", "savanna"));
        addEntry(List.of("beterraba", "beetroot"),
                List.of("beetroot", "beetroots", "beetroot_seeds"),
                List.of("plains", "savanna", "desert", "taiga"));
        addEntry(List.of("flor", "flores", "rosa", "tulipa", "margarida", "orquidea", "girassol"),
                List.of("dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                        "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "sunflower",
                        "lilac", "rose_bush", "peony", "spore_blossom"),
                List.of("flower_forest", "meadow", "sunflower_plains", "plains", "cherry_grove"));

        // Árvores e Madeiras
        addEntry(List.of("betula", "eucalipto", "birch"),
                List.of("birch_log", "birch_planks", "birch_sapling", "birch_leaves"),
                List.of("birch_forest", "old_growth_birch_forest", "forest"));
        addEntry(List.of("carvalho", "oak"),
                List.of("oak_log", "oak_planks", "oak_sapling", "oak_leaves"),
                List.of("forest", "plains", "windswept_forest", "river"));
        addEntry(List.of("carvalho escuro", "dark oak"),
                List.of("dark_oak_log", "dark_oak_planks", "dark_oak_sapling", "dark_oak_leaves"),
                List.of("dark_forest"));
        addEntry(List.of("pinheiro", "abeto", "spruce"),
                List.of("spruce_log", "spruce_planks", "spruce_sapling", "spruce_leaves"),
                List.of("taiga", "snowy_taiga", "old_growth_spruce_taiga", "old_growth_pine_taiga", "grove"));
        addEntry(List.of("acacia", "savana"),
                List.of("acacia_log", "acacia_planks", "acacia_sapling", "acacia_leaves"),
                List.of("savanna", "savanna_plateau", "windswept_savanna"));
        addEntry(List.of("cerejeira", "sakura", "cherry"),
                List.of("cherry_log", "cherry_planks", "cherry_sapling", "cherry_leaves", "pink_petals"),
                List.of("cherry_grove"));
        addEntry(List.of("madeira da selva", "arvore da selva", "jungle log"),
                List.of("jungle_log", "jungle_planks", "jungle_sapling", "jungle_leaves"),
                List.of("jungle", "sparse_jungle", "bamboo_jungle"));
        addEntry(List.of("madeira", "tronco", "tabua", "lenha"),
                List.of("oak_log", "birch_log", "spruce_log", "jungle_log", "acacia_log", "dark_oak_log", "cherry_log",
                        "oak_planks", "birch_planks", "spruce_planks"),
                List.of("forest", "birch_forest", "taiga", "dark_forest", "jungle"));

        // Minérios e Pedras (Vanilla + Create + Mythic Metals)
        addEntry(List.of("diamante", "diamantes", "diamond"),
                List.of("diamond", "diamond_ore", "deepslate_diamond_ore", "diamond_block"),
                List.of("dripstone_caves", "deep_dark", "lush_caves", "windswept_hills"));
        addEntry(List.of("ferro", "iron"),
                List.of("iron_ingot", "raw_iron", "iron_ore", "deepslate_iron_ore", "iron_block", "raw_iron_block"),
                List.of("stony_peaks", "jagged_peaks", "frozen_peaks", "windswept_hills", "dripstone_caves"));
        addEntry(List.of("carvao", "coal"),
                List.of("coal", "charcoal", "coal_ore", "deepslate_coal_ore", "coal_block"),
                List.of("stony_peaks", "windswept_hills", "jagged_peaks", "meadow"));
        addEntry(List.of("ouro", "gold"),
                List.of("gold_ingot", "raw_gold", "gold_ore", "deepslate_gold_ore", "nether_gold_ore", "gold_block", "raw_gold_block"),
                List.of("badlands", "eroded_badlands", "wooded_badlands", "dripstone_caves"));
        addEntry(List.of("cobre", "copper"),
                List.of("copper_ingot", "raw_copper", "copper_ore", "deepslate_copper_ore", "copper_block", "raw_copper_block"),
                List.of("dripstone_caves", "stony_peaks", "windswept_hills"));
        addEntry(List.of("esmeralda", "esmeraldas", "emerald"),
                List.of("emerald", "emerald_ore", "deepslate_emerald_ore", "emerald_block"),
                List.of("stony_peaks", "jagged_peaks", "frozen_peaks", "windswept_hills", "grove", "meadow"));
        addEntry(List.of("redstone"),
                List.of("redstone", "redstone_ore", "deepslate_redstone_ore", "redstone_block"),
                List.of("dripstone_caves", "deep_dark", "lush_caves"));
        addEntry(List.of("lapis", "lapis lazuli"),
                List.of("lapis_lazuli", "lapis_ore", "deepslate_lapis_ore", "lapis_block"),
                List.of("dripstone_caves", "stony_peaks", "desert"));
        addEntry(List.of("ametista", "amethyst", "geodo"),
                List.of("amethyst_shard", "amethyst_block", "budding_amethyst", "amethyst_cluster"),
                List.of("stony_peaks", "deep_ocean", "dripstone_caves"));
        addEntry(List.of("zinco", "zinc"),
                List.of("zinc_ingot", "raw_zinc", "zinc_ore", "deepslate_zinc_ore", "zinc_block"),
                List.of("windswept_hills", "dripstone_caves", "stony_peaks"));
        addEntry(List.of("latao", "brass"),
                List.of("brass_ingot", "brass_sheet", "brass_block", "brass_casing"),
                List.of("plains", "windswept_hills"));
        addEntry(List.of("andesito", "andesite"),
                List.of("andesite", "andesite_alloy", "andesite_casing", "polished_andesite"),
                List.of("windswept_hills", "stony_peaks", "dripstone_caves", "taiga"));
        addEntry(List.of("obsidiana", "obsidian"),
                List.of("obsidian", "crying_obsidian"),
                List.of("dripstone_caves", "deep_dark", "stony_peaks"));
        addEntry(List.of("areia", "sand"),
                List.of("sand", "red_sand", "sandstone"),
                List.of("desert", "beach", "river", "badlands"));
        addEntry(List.of("cascalho", "gravel", "pederneira", "flint"),
                List.of("gravel", "flint"),
                List.of("windswept_gravelly_hills", "river", "ocean", "beach"));
        addEntry(List.of("gelo", "neve", "ice", "snow"),
                List.of("ice", "packed_ice", "blue_ice", "snow_block", "snowball", "powder_snow_bucket"),
                List.of("ice_spikes", "frozen_peaks", "jagged_peaks", "snowy_plains", "snowy_taiga", "frozen_ocean"));
        addEntry(List.of("lava"),
                List.of("lava", "lava_bucket", "magma_block"),
                List.of("dripstone_caves", "badlands", "desert"));
        addEntry(List.of("agua", "water"),
                List.of("water", "water_bucket"),
                List.of("river", "ocean", "beach", "swamp"));

        // Drops e Alimentos Comuns
        addEntry(List.of("pao", "paes", "pães", "bread"),
                List.of("bread"),
                List.of("plains", "savanna", "desert"));
        addEntry(List.of("couro", "leather"),
                List.of("leather"),
                List.of("plains", "meadow", "savanna"));
        addEntry(List.of("la", "wool"),
                List.of("white_wool", "black_wool", "gray_wool", "light_gray_wool", "brown_wool", "red_wool", "blue_wool"),
                List.of("meadow", "plains", "windswept_hills"));
        addEntry(List.of("linha", "teia", "string", "cobweb"),
                List.of("string", "cobweb"),
                List.of("lush_caves", "dripstone_caves", "dark_forest", "desert"));
        addEntry(List.of("slime", "gosma"),
                List.of("slime_ball", "slime_block"),
                List.of("swamp", "mangrove_swamp"));
        addEntry(List.of("graveto", "gravetos", "stick", "sticks", "twig", "twigs", "gravetinho"),
                List.of("stick"),
                List.of("forest", "taiga", "plains"));
        addEntry(List.of("osso", "ossos", "bone", "bones"),
                List.of("bone"),
                List.of("desert", "soul_sand_valley", "plains"));
        addEntry(List.of("farinha de osso", "po de osso", "bone meal", "bonemeal"),
                List.of("bone_meal"),
                List.of("desert", "soul_sand_valley", "plains"));
        addEntry(List.of("bloco de osso", "bloco de ossos", "bone block"),
                List.of("bone_block"),
                List.of("soul_sand_valley", "desert"));
        addEntry(List.of("polvora", "gunpowder"),
                List.of("gunpowder", "tnt"),
                List.of("desert", "plains", "dripstone_caves"));
        addEntry(List.of("perola do fim", "ender pearl", "perola"),
                List.of("ender_pearl", "ender_eye"),
                List.of("warped_forest", "desert", "plains"));
        addEntry(List.of("livro", "estante", "papel", "book", "bookshelf"),
                List.of("book", "enchanted_book", "bookshelf", "writable_book", "paper"),
                List.of("plains", "meadow", "savanna"));
        addEntry(List.of("sela", "saddle"),
                List.of("saddle"),
                List.of("desert", "jungle", "plains"));
        addEntry(List.of("tocha", "lanterna", "torch", "lantern"),
                List.of("torch", "soul_torch", "lantern", "soul_lantern"),
                List.of("plains", "windswept_hills"));
        addEntry(List.of("bau", "baus", "chest", "barril"),
                List.of("chest", "trapped_chest", "barrel", "ender_chest"),
                List.of("plains", "forest"));
    }

    private static void addEntry(List<String> keywords, List<String> itemOrBlockIds, List<String> biomeHints) {
        DICTIONARY.add(new SearchEntry(keywords, itemOrBlockIds, biomeHints));
    }

    public static void ensureLanguageLoaded() {
        if (!LANG_LOADED) {
            synchronized (CumminsScanner.class) {
                if (!LANG_LOADED) {
                    loadLanguageMaps();
                    LANG_LOADED = true;
                }
            }
        }
    }

    private static void loadLanguageMaps() {
        // 1. Carrega todas as traduções oficiais em Português do Brasil (minecraft_pt_br.json)
        try (InputStream stream = CumminsScanner.class.getResourceAsStream("/assets/cummins/lang/minecraft_pt_br.json")) {
            if (stream != null) {
                try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                        String key = entry.getKey();
                        String ptName = entry.getValue().getAsString();
                        Identifier id = null;
                        if (key.startsWith("item.minecraft.")) {
                            id = Identifier.of("minecraft", key.substring("item.minecraft.".length()));
                        } else if (key.startsWith("block.minecraft.")) {
                            id = Identifier.of("minecraft", key.substring("block.minecraft.".length()));
                        } else if (key.startsWith("item.")) {
                            String[] parts = key.split("\\.", 3);
                            if (parts.length >= 3) id = Identifier.of(parts[1], parts[2]);
                        } else if (key.startsWith("block.")) {
                            String[] parts = key.split("\\.", 3);
                            if (parts.length >= 3) id = Identifier.of(parts[1], parts[2]);
                        }
                        if (id != null) {
                            ID_TO_PT.put(id, ptName);
                            String normPt = normalize(ptName);
                            if (!normPt.isBlank()) {
                                PT_TO_IDS.computeIfAbsent(normPt, k -> new ArrayList<>()).add(id);
                                if (normPt.endsWith("s") && normPt.length() > 3) {
                                    PT_TO_IDS.computeIfAbsent(normPt.substring(0, normPt.length() - 1), k -> new ArrayList<>()).add(id);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[CumminsScanner] Aviso: Não foi possível carregar minecraft_pt_br.json: " + e.getMessage());
        }

        // 2. Registra nomes em Inglês do registro de itens e blocos
        for (Item item : Registries.ITEM) {
            Identifier id = Registries.ITEM.getId(item);
            registerEnglishTokens(id);
        }
        for (Block block : Registries.BLOCK) {
            Identifier id = Registries.BLOCK.getId(block);
            registerEnglishTokens(id);
        }

        // 3. Aliases e termos de pesquisa comuns de jogadores
        Identifier stickId = Identifier.of("minecraft", "stick");
        registerAlias("twig", stickId);
        registerAlias("twigs", stickId);
        registerAlias("graveto", stickId);
        registerAlias("gravetos", stickId);
        registerAlias("stick", stickId);
        registerAlias("sticks", stickId);

        Identifier breadId = Identifier.of("minecraft", "bread");
        registerAlias("pao", breadId);
        registerAlias("paes", breadId);
        registerAlias("bread", breadId);
        registerAlias("breads", breadId);

        Identifier boneId = Identifier.of("minecraft", "bone");
        registerAlias("osso", boneId);
        registerAlias("ossos", boneId);
        registerAlias("bone", boneId);
        registerAlias("bones", boneId);

        Identifier boneMealId = Identifier.of("minecraft", "bone_meal");
        registerAlias("farinha de osso", boneMealId);
        registerAlias("po de osso", boneMealId);
        registerAlias("bone meal", boneMealId);
        registerAlias("bonemeal", boneMealId);
    }

    private static void registerAlias(String alias, Identifier id) {
        String norm = normalize(alias);
        PT_TO_IDS.computeIfAbsent(norm, k -> new ArrayList<>()).add(id);
        EN_TO_IDS.computeIfAbsent(norm, k -> new ArrayList<>()).add(id);
    }

    private static void registerEnglishTokens(Identifier id) {
        String path = id.getPath();
        String enFormatted = formatEnglishName(path);
        ID_TO_EN.put(id, enFormatted);

        String normPath = normalize(path.replace('_', ' '));
        EN_TO_IDS.computeIfAbsent(normPath, k -> new ArrayList<>()).add(id);
        String rawNorm = normalize(path);
        if (!rawNorm.equals(normPath)) {
            EN_TO_IDS.computeIfAbsent(rawNorm, k -> new ArrayList<>()).add(id);
        }
        if (normPath.endsWith("s") && normPath.length() > 3) {
            EN_TO_IDS.computeIfAbsent(normPath.substring(0, normPath.length() - 1), k -> new ArrayList<>()).add(id);
        }
    }

    private static String formatEnglishName(String path) {
        if (path == null || path.isBlank()) return "";
        String[] words = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    public static String getItemNamePt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        if (stack.hasCustomName()) return stack.getName().getString();
        ensureLanguageLoaded();
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String pt = ID_TO_PT.get(id);
        if (pt != null && !pt.isBlank()) return pt;
        return stack.getName().getString();
    }

    public static String getItemNameEn(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ensureLanguageLoaded();
        Identifier id = Registries.ITEM.getId(stack.getItem());
        String en = ID_TO_EN.get(id);
        if (en != null && !en.isBlank()) return en;
        return formatEnglishName(id.getPath());
    }

    public static String getBlockNamePt(Block block) {
        if (block == null) return "";
        ensureLanguageLoaded();
        Identifier id = Registries.BLOCK.getId(block);
        String pt = ID_TO_PT.get(id);
        if (pt != null && !pt.isBlank()) return pt;
        return block.getName().getString();
    }

    public static String getBlockNameEn(Block block) {
        if (block == null) return "";
        ensureLanguageLoaded();
        Identifier id = Registries.BLOCK.getId(block);
        String en = ID_TO_EN.get(id);
        if (en != null && !en.isBlank()) return en;
        return formatEnglishName(id.getPath());
    }

    public static String normalize(String input) {
        if (input == null) return "";
        String norm = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return norm.replaceAll("[^a-z0-9_\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private static final List<String> SCAN_PREFIXES = List.of(
            "onde posso encontrar", "onde eu posso encontrar", "onde eu encontro", "onde encontro",
            "onde posso achar", "onde eu acho", "onde acho", "onde tem", "onde consigo", "onde eu consigo",
            "onde havia", "onde tinha", "aonde havia", "aonde tinha",
            "onde fica", "onde ta", "onde esta", "aonde posso encontrar", "aonde eu encontro", "aonde encontro",
            "aonde acho", "aonde tem", "cade o", "cade a", "cade os", "cade as", "cade meu", "cade minha",
            "tem algum", "tem alguma", "procura pra mim", "escaneia", "acha pra mim"
    );

    /**
     * Verifica se a frase do jogador é uma pergunta de localização de item/bloco e extrai o termo buscado.
     * Retorna null se não for uma pergunta de busca de item/bloco.
     */
    public static String extractSearchTarget(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String norm = normalize(rawText);

        // Ignora perguntas puramente de receita ("como faço", "como crafto", "pra que serve")
        if (norm.startsWith("como faco") || norm.startsWith("como faz") || norm.startsWith("como crafto")
                || norm.startsWith("como funciona") || norm.startsWith("pra que serve") || norm.startsWith("para que serve")
                || norm.startsWith("o que e") || norm.startsWith("oque e") || norm.startsWith("por que")) {
            return null;
        }

        String remainder = null;
        for (String prefix : SCAN_PREFIXES) {
            if (norm.startsWith(prefix + " ")) {
                remainder = norm.substring(prefix.length()).trim();
                break;
            }
            int idx = norm.indexOf(" " + prefix + " ");
            if (idx >= 0) {
                remainder = norm.substring(idx + prefix.length() + 2).trim();
                break;
            }
        }

        if (remainder == null) {
            if (norm.startsWith("onde ") || norm.startsWith("aonde ") || norm.startsWith("cade ")) {
                int firstSpace = norm.indexOf(' ');
                if (firstSpace > 0) {
                    remainder = norm.substring(firstSpace + 1).trim();
                }
            }
        }

        if (remainder == null || remainder.isBlank()) return null;

        // Remove palavras de preenchimento no começo e fim
        List<String> stopWords = List.of(
                "cummins", "cumins", "cummings", "cumings", "cumming", "cuming", "cummin", "cumin", "cummis", "cumis", "cummim", "cumim",
                "senhor cummins", "seu cummins", "sr cummins", "tio cummins",
                "senhor cummings", "seu cummings", "sr cummings", "tio cummings",
                "senhor cumming", "seu cumming", "sr cumming", "tio cumming",
                "vitoria",
                "por favor", "pfv", "pf", "aqui perto", "por aqui", "aqui", "no servidor", "no mapa", "no mundo",
                "eu", "posso", "encontrar", "encontro", "achar", "acho", "conseguir", "consigo", "pegar", "pego", "tem",
                "havia", "houve", "tinha", "teria", "ha", "eh",
                "item", "itens", "bloco", "blocos",
                "um", "uma", "uns", "umas", "algum", "alguma", "alguns", "algumas", "o", "a", "os", "as", "de", "do", "da", "dos", "das", "meu", "minha"
        );

        String cleaned = remainder.replaceAll("[\"'`]+", "").trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String sw : stopWords) {
                if (cleaned.equals(sw)) break;
                if (cleaned.startsWith(sw + " ")) {
                    cleaned = cleaned.substring(sw.length()).trim();
                    changed = true;
                }
                if (cleaned.endsWith(" " + sw)) {
                    cleaned = cleaned.substring(0, cleaned.length() - sw.length()).trim();
                    changed = true;
                }
            }
        }

        cleaned = cleaned.replaceAll("[\"'`]+", "").trim();
        if (cleaned.length() < 2) return null;
        return cleaned;
    }

    /**
     * Descreve detalhadamente a posição do container (ex: "no baú de cima", "no baú de baixo", "no baú duplo", "no barril").
     */
    public static String describeContainer(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) return "no baú";
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        String name = block.getName().getString().toLowerCase(Locale.ROOT);

        if (block instanceof ChestBlock || name.contains("chest") || name.contains("bau")) {
            BlockPos above = pos.up();
            BlockPos below = pos.down();
            boolean hasChestAbove = world.getBlockState(above).getBlock() instanceof ChestBlock;
            boolean hasChestBelow = world.getBlockState(below).getBlock() instanceof ChestBlock;

            if (hasChestBelow) {
                return "no baú de cima";
            } else if (hasChestAbove) {
                return "no baú de baixo";
            }

            try {
                if (state.contains(ChestBlock.CHEST_TYPE)) {
                    var type = state.get(ChestBlock.CHEST_TYPE);
                    if (type == net.minecraft.block.enums.ChestType.LEFT || type == net.minecraft.block.enums.ChestType.RIGHT) {
                        return "no baú duplo";
                    }
                }
            } catch (Exception ignored) {}
            return "no baú";
        }

        if (name.contains("barrel") || name.contains("barril")) {
            return "no barril";
        }
        if (name.contains("shulker")) {
            return "na shulker box";
        }
        if (name.contains("hopper") || name.contains("funil")) {
            return "no funil";
        }
        if (name.contains("furnace") || name.contains("fornalha") || name.contains("smoker") || name.contains("defumador")) {
            return "na fornalha";
        }
        if (name.contains("dispenser") || name.contains("dropper") || name.contains("ejetor") || name.contains("liberador")) {
            return "no ejetor/liberador";
        }

        return "no baú";
    }

    /**
     * Executa o scanner completo ao redor do jogador.
     */
    public static ScanResult scanForPlayer(ServerPlayerEntity player, String rawQuery) {
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) return null;
        String normQuery = normalize(rawQuery);
        if (normQuery.isEmpty()) return null;

        String singularQuery = normQuery.endsWith("oes") ? normQuery.substring(0, normQuery.length() - 3) + "ao"
                : normQuery.endsWith("ais") ? normQuery.substring(0, normQuery.length() - 3) + "al"
                : normQuery.endsWith("eis") ? normQuery.substring(0, normQuery.length() - 3) + "el"
                : (normQuery.endsWith("s") && normQuery.length() > 3) ? normQuery.substring(0, normQuery.length() - 1)
                : normQuery;

        ensureLanguageLoaded();
        Set<Item> exactMatchedItems = new HashSet<>();
        Set<Block> exactMatchedBlocks = new HashSet<>();
        List<String> biomeHints = new ArrayList<>();

        // 1. Busca correspondência exata no dicionário PT-BR oficial
        List<Identifier> ptMatches = PT_TO_IDS.get(normQuery);
        if (ptMatches == null && !singularQuery.equals(normQuery)) {
            ptMatches = PT_TO_IDS.get(singularQuery);
        }
        if (ptMatches != null) {
            for (Identifier id : ptMatches) {
                if (Registries.ITEM.containsId(id)) exactMatchedItems.add(Registries.ITEM.get(id));
                if (Registries.BLOCK.containsId(id)) {
                    Block b = Registries.BLOCK.get(id);
                    exactMatchedBlocks.add(b);
                    if (b.asItem() != null && b.asItem() != net.minecraft.item.Items.AIR) {
                        exactMatchedItems.add(b.asItem());
                    }
                }
            }
        }

        // 2. Busca correspondência exata no dicionário em Inglês (EN)
        List<Identifier> enMatches = EN_TO_IDS.get(normQuery);
        if (enMatches == null && !singularQuery.equals(normQuery)) {
            enMatches = EN_TO_IDS.get(singularQuery);
        }
        if (enMatches != null) {
            for (Identifier id : enMatches) {
                if (Registries.ITEM.containsId(id)) exactMatchedItems.add(Registries.ITEM.get(id));
                if (Registries.BLOCK.containsId(id)) {
                    Block b = Registries.BLOCK.get(id);
                    exactMatchedBlocks.add(b);
                    if (b.asItem() != null && b.asItem() != net.minecraft.item.Items.AIR) {
                        exactMatchedItems.add(b.asItem());
                    }
                }
            }
        }

        // 3. Busca exata no dicionário manual customizado
        for (SearchEntry entry : DICTIONARY) {
            for (String kw : entry.keywords) {
                String nkw = normalize(kw);
                if (normQuery.equals(nkw) || singularQuery.equals(nkw)) {
                    for (String tid : entry.itemOrBlockIds) {
                        Identifier id = tid.contains(":") ? Identifier.tryParse(tid) : Identifier.of("minecraft", tid);
                        if (id != null) {
                            if (Registries.ITEM.containsId(id)) exactMatchedItems.add(Registries.ITEM.get(id));
                            if (Registries.BLOCK.containsId(id)) {
                                Block b = Registries.BLOCK.get(id);
                                exactMatchedBlocks.add(b);
                                if (b.asItem() != null && b.asItem() != net.minecraft.item.Items.AIR) {
                                    exactMatchedItems.add(b.asItem());
                                }
                            }
                        }
                    }
                    for (String bh : entry.biomeHints) {
                        if (!biomeHints.contains(bh)) biomeHints.add(bh);
                    }
                    break;
                }
            }
        }

        Set<Item> matchedItems;
        Set<Block> matchedBlocks;

        if (!exactMatchedItems.isEmpty() || !exactMatchedBlocks.isEmpty()) {
            // Correspondência exata encontrada com sucesso! (NÃO busca substrings para não misturar 'osso' com 'pó de osso')
            matchedItems = exactMatchedItems;
            matchedBlocks = exactMatchedBlocks;
        } else {
            // Fallback para correspondência parcial de mods e termos compostos
            matchedItems = new HashSet<>();
            matchedBlocks = new HashSet<>();
            Set<String> targetPathTokens = new HashSet<>();
            for (SearchEntry entry : DICTIONARY) {
                for (String kw : entry.keywords) {
                    String nkw = normalize(kw);
                    if (normQuery.contains(nkw) || nkw.contains(singularQuery)) {
                        targetPathTokens.addAll(entry.itemOrBlockIds);
                        for (String bh : entry.biomeHints) {
                            if (!biomeHints.contains(bh)) biomeHints.add(bh);
                        }
                        break;
                    }
                }
            }
            targetPathTokens.add(normQuery.replace(" ", "_"));
            targetPathTokens.add(singularQuery.replace(" ", "_"));

            for (Item item : Registries.ITEM) {
                Identifier id = Registries.ITEM.getId(item);
                if (matchesIdentifierOrName(id, item.getName().getString(), normQuery, singularQuery, targetPathTokens)) {
                    matchedItems.add(item);
                }
            }
            for (Block block : Registries.BLOCK) {
                Identifier id = Registries.BLOCK.getId(block);
                if (matchesIdentifierOrName(id, block.getName().getString(), normQuery, singularQuery, targetPathTokens)) {
                    matchedBlocks.add(block);
                    Item blockItem = block.asItem();
                    if (blockItem != null && blockItem != net.minecraft.item.Items.AIR) {
                        matchedItems.add(blockItem);
                    }
                }
            }
        }

        BlockPos origin = player.getBlockPos();

        // 4. Varredura em Baús/Containers e Itens Dropados no chão + Blocos do mundo
        BlockPos bestContainerPos = null;
        double bestContainerDistSq = Double.MAX_VALUE;

        BlockPos bestWorldPos = null;
        double bestWorldDistSq = Double.MAX_VALUE;

        // 4a. Verifica ItemEntities dropados no chão num raio de 64 blocos
        Box itemBox = new Box(origin).expand(64.0, 24.0, 64.0);
        for (ItemEntity ie : world.getEntitiesByClass(ItemEntity.class, itemBox, Entity::isAlive)) {
            ItemStack st = ie.getStack();
            if (!st.isEmpty() && matchesStack(st, matchedItems, normQuery, singularQuery)) {
                double dSq = ie.squaredDistanceTo(player);
                if (dSq < bestWorldDistSq) {
                    bestWorldDistSq = dSq;
                    bestWorldPos = ie.getBlockPos();
                }
            }
        }

        // 4b. Varre blocos e baús ao redor do jogador em espiral
        int maxRadius = 68;
        int minY = Math.max(world.getBottomY(), origin.getY() - 20);
        int maxY = Math.min(world.getTopY() - 1, origin.getY() + 20);

        for (int r = 1; r <= maxRadius; r++) {
            if (r > 24 && (bestContainerDistSq <= 24.0 * 24.0 || bestWorldDistSq <= 24.0 * 24.0)) {
                break;
            }
            int yStep = (r <= 26) ? 1 : 2;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = origin.getX() + dx;
                    int z = origin.getZ() + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                    for (int y = origin.getY() - (r <= 24 ? 10 : 18); y <= origin.getY() + (r <= 24 ? 12 : 18); y += yStep) {
                        if (y < minY || y > maxY) continue;
                        BlockPos pos = new BlockPos(x, y, z);
                        double dSq = pos.getSquaredDistance(origin);
                        if (dSq >= bestContainerDistSq && dSq >= bestWorldDistSq) continue;

                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) continue;

                        // Verifica se é um container (Baú, Barril, Shulker, Funil, etc.)
                        if (state.hasBlockEntity() && dSq < bestContainerDistSq) {
                            BlockEntity be = world.getBlockEntity(pos);
                            if (be instanceof Inventory inv && containerHasItem(inv, matchedItems, normQuery, singularQuery)) {
                                bestContainerDistSq = dSq;
                                bestContainerPos = pos.toImmutable();
                                continue;
                            }
                        }

                        // Verifica se o próprio bloco é o que o jogador procura
                        if (dSq < bestWorldDistSq && matchedBlocks.contains(state.getBlock())) {
                            bestWorldDistSq = dSq;
                            bestWorldPos = pos.toImmutable();
                        }
                    }
                }
            }
        }

        // Decide entre o baú encontrado ou o bloco/item no mundo (prioriza baú se estiver no mesmo tier de distância)
        boolean foundInContainer = false;
        BlockPos chosenPos = null;
        double chosenDist = Double.MAX_VALUE;

        if (bestContainerPos != null && bestContainerDistSq <= 24.0 * 24.0) {
            foundInContainer = true;
            chosenPos = bestContainerPos;
            chosenDist = Math.sqrt(bestContainerDistSq);
        } else if (bestWorldPos != null && bestWorldDistSq <= 24.0 * 24.0) {
            foundInContainer = false;
            chosenPos = bestWorldPos;
            chosenDist = Math.sqrt(bestWorldDistSq);
        } else if (bestContainerPos != null) {
            foundInContainer = true;
            chosenPos = bestContainerPos;
            chosenDist = Math.sqrt(bestContainerDistSq);
        } else if (bestWorldPos != null) {
            foundInContainer = false;
            chosenPos = bestWorldPos;
            chosenDist = Math.sqrt(bestWorldDistSq);
        }

        if (chosenPos != null) {
            DistanceCategory cat = (chosenDist <= 24.0) ? DistanceCategory.NEAR : DistanceCategory.MEDIUM_FAR;
            BlockPos standPos = computeStandPosInFrontOf(world, chosenPos, origin);
            String dir = getCompassDirectionPt(origin, chosenPos);

            String containerDesc = foundInContainer ? describeContainer(world, chosenPos) : "no chão";
            String itemPt = rawQuery;
            String itemEn = rawQuery;
            int count = 1;

            if (foundInContainer) {
                BlockEntity be = world.getBlockEntity(chosenPos);
                if (be instanceof Inventory inv) {
                    ContainerItemMatch details = findMatchingItemInContainer(inv, matchedItems, normQuery, singularQuery);
                    if (details != null) {
                        itemPt = details.namePt;
                        itemEn = details.nameEn;
                        count = details.count;
                    }
                }
            } else {
                BlockState bs = world.getBlockState(chosenPos);
                if (!bs.isAir() && matchedBlocks.contains(bs.getBlock())) {
                    itemPt = getBlockNamePt(bs.getBlock());
                    itemEn = getBlockNameEn(bs.getBlock());
                }
            }

            // Registra o scan recente para suporte caso o jogador diga que não achou!
            PlayerScanRecord record = new PlayerScanRecord(
                    player.getUuid(),
                    player.getGameProfile().getName(),
                    rawQuery,
                    itemPt,
                    itemEn,
                    count,
                    chosenPos,
                    standPos,
                    foundInContainer,
                    containerDesc,
                    System.currentTimeMillis()
            );
            recordScan(record);

            return new ScanResult(rawQuery, cat, foundInContainer, chosenPos, standPos, chosenDist, dir, null,
                    itemPt, itemEn, count, containerDesc);
        }

        // 5. Não achou num raio de 68 blocos -> Faz o Scan de Bioma a longa distância nas 8 direções cardeais
        CompassBiomeMatch distantBiome = scanDistantBiomes(world, origin, biomeHints);
        return new ScanResult(rawQuery, DistanceCategory.NOT_FOUND, false, null, null, -1,
                distantBiome.directionPt, distantBiome.biomeNamePt, rawQuery, rawQuery, 0, "");
    }

    private static boolean matchesIdentifierOrName(Identifier id, String displayName, String normQuery, String singularQuery, Set<String> targetPathTokens) {
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            for (String token : targetPathTokens) {
                if (path.equals(token) || path.endsWith("_" + token) || path.startsWith(token + "_")) {
                    return true;
                }
            }
        }
        if (displayName != null && !displayName.isBlank()) {
            String normName = normalize(displayName);
            if (normName.equals(normQuery) || normName.equals(singularQuery)
                    || normName.contains(" " + singularQuery) || normName.startsWith(singularQuery + " ")) {
                return true;
            }
        }
        return false;
    }

    public static ContainerItemMatch findMatchingItemInContainer(Inventory inv, Set<Item> matchedItems, String normQuery, String singularQuery) {
        if (inv == null) return null;
        int totalCount = 0;
        Item firstItem = null;
        String firstNamePt = null;
        String firstNameEn = null;

        try {
            int size = inv.size();
            for (int i = 0; i < size; i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty() && matchesStack(stack, matchedItems, normQuery, singularQuery)) {
                    totalCount += stack.getCount();
                    if (firstItem == null) {
                        firstItem = stack.getItem();
                        firstNamePt = getItemNamePt(stack);
                        firstNameEn = getItemNameEn(stack);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (totalCount > 0 && firstItem != null) {
            return new ContainerItemMatch(firstItem, totalCount, firstNamePt, firstNameEn);
        }
        return null;
    }

    private static boolean containerHasItem(Inventory inv, Set<Item> matchedItems, String normQuery, String singularQuery) {
        if (inv == null) return false;
        try {
            int size = inv.size();
            for (int i = 0; i < size; i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty() && matchesStack(stack, matchedItems, normQuery, singularQuery)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean matchesStack(ItemStack stack, Set<Item> matchedItems, String normQuery, String singularQuery) {
        if (stack.isEmpty()) return false;
        if (matchedItems != null && !matchedItems.isEmpty()) {
            return matchedItems.contains(stack.getItem());
        }
        String customOrTransName = normalize(stack.getName().getString());
        if (customOrTransName.equals(normQuery) || customOrTransName.equals(singularQuery)) {
            return true;
        }
        return false;
    }

    /**
     * Calcula o bloco seguro exatamente na frente do baú, ao lado do bloco/item, ou no ponto caminhável
     * mais próximo possível quando o bloco está cercado por pedra.
     */
    public static BlockPos computeStandPosInFrontOf(ServerWorld world, BlockPos targetPos, BlockPos playerPos) {
        BlockState state = world.getBlockState(targetPos);
        try {
            if (state.contains(ChestBlock.FACING)) {
                Direction facing = state.get(ChestBlock.FACING);
                BlockPos front = targetPos.offset(facing);
                BlockPos safeFront = findFloorNear(world, front);
                if (safeFront != null && safeFront.getSquaredDistance(targetPos) <= 5.5) {
                    return safeFront;
                }
            }
        } catch (Exception ignored) {}

        // 1. Testa os 4 lados adjacentes no mesmo nível do container
        Direction[] dirs = new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction d : dirs) {
            BlockPos adj = targetPos.offset(d);
            BlockPos floor = findFloorNear(world, adj);
            if (floor != null) {
                double dist = floor.getSquaredDistance(playerPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = floor;
                }
            }
        }
        if (best != null) return best;

        // 2. Testa diagonais imediatas (r=1) ao redor do alvo
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos diag = targetPos.add(dx, 0, dz);
                BlockPos floor = findFloorNear(world, diag);
                if (floor != null && floor.getSquaredDistance(targetPos) <= 6.0) {
                    return floor;
                }
            }
        }

        // 3. Fallback direto abaixo/acima do próprio alvo (ex: baú suspenso)
        BlockPos directFallback = findFloorNear(world, targetPos);
        if (directFallback != null) return directFallback;

        // 4. Busca em raio crescente SEMPRE ancorada na altitude do alvo (targetPos.getY() ± 2), NUNCA no playerPos.getY()!
        BlockPos bestReachable = null;
        double bestScore = Double.MAX_VALUE;

        for (int r = 2; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos candidate = new BlockPos(targetPos.getX() + dx, targetPos.getY() + dy, targetPos.getZ() + dz);
                        if (isWalkableSpot(world, candidate)) {
                            double distToTargetSq = candidate.getSquaredDistance(targetPos);
                            if (distToTargetSq < bestScore) {
                                bestScore = distToTargetSq;
                                bestReachable = candidate;
                            }
                        }
                    }
                }
            }
            if (bestReachable != null) {
                return bestReachable;
            }
        }

        return targetPos;
    }

    private static BlockPos findFloorNear(ServerWorld world, BlockPos pos) {
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos check = pos.add(0, dy, 0);
            if (isWalkableSpot(world, check)) {
                return check;
            }
        }
        return null;
    }

    private static boolean isWalkableSpot(ServerWorld world, BlockPos check) {
        BlockState floor = world.getBlockState(check.down());
        BlockState body = world.getBlockState(check);
        BlockState head = world.getBlockState(check.up());

        // O chão precisa ser sólido ou com colisão física (blocos, lajes, escadas...)
        boolean hasFloor = !floor.isAir()
                && !floor.getCollisionShape(world, check.down()).isEmpty()
                && floor.getFluidState().isEmpty();
        if (!hasFloor) return false;

        // O bloco do corpo pode ser ar, tapete (carpet), neve fina, etc.
        boolean bodyPassable = body.getFluidState().isEmpty() && (
                body.isAir()
                || body.getCollisionShape(world, check).isEmpty()
                || body.getBlock() instanceof net.minecraft.block.CarpetBlock
                || body.getBlock() instanceof net.minecraft.block.SnowBlock
                || body.getCollisionShape(world, check).getMax(Direction.Axis.Y) <= 0.25
        );
        if (!bodyPassable) return false;

        // O bloco da cabeça precisa estar livre
        return head.isAir() || head.getCollisionShape(world, check.up()).isEmpty();
    }

    /**
     * Converte o vetor (origem -> destino) em uma das 8 direções cardeais do Minecraft:
     * Norte (-Z), Nordeste (+X, -Z), Leste (+X), Sudeste (+X, +Z),
     * Sul (+Z), Sudoeste (-X, +Z), Oeste (-X), Noroeste (-X, -Z).
     */
    public static String getCompassDirectionPt(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angleDeg = Math.toDegrees(Math.atan2(dx, -dz));
        if (angleDeg < 0) angleDeg += 360.0;

        if (angleDeg >= 337.5 || angleDeg < 22.5) return "Norte";
        if (angleDeg < 67.5) return "Nordeste";
        if (angleDeg < 112.5) return "Leste";
        if (angleDeg < 157.5) return "Sudeste";
        if (angleDeg < 202.5) return "Sul";
        if (angleDeg < 247.5) return "Sudoeste";
        if (angleDeg < 292.5) return "Oeste";
        return "Noroeste";
    }

    /**
     * Faz um scan rápido de biomas em anéis de 160 a 1600 blocos nas 8 direções cardeais
     * sem gerar chunks novos (usa apenas o BiomeSource da seed do mundo).
     */
    private static CompassBiomeMatch scanDistantBiomes(ServerWorld world, BlockPos origin, List<String> biomeHints) {
        int[][] dirs = new int[][]{
                {0, -1},  // Norte
                {1, -1},  // Nordeste
                {1, 0},   // Leste
                {1, 1},   // Sudeste
                {0, 1},   // Sul
                {-1, 1},  // Sudoeste
                {-1, 0},  // Oeste
                {-1, -1}  // Noroeste
        };
        String[] dirNames = new String[]{
                "Norte", "Nordeste", "Leste", "Sudeste", "Sul", "Sudoeste", "Oeste", "Noroeste"
        };

        String currentBiomePath = world.getBiome(origin).getKey()
                .map(k -> k.getValue().getPath().toLowerCase(Locale.ROOT)).orElse("");

        // 1. Procura bioma específico do item
        if (biomeHints != null && !biomeHints.isEmpty()) {
            for (int dist = 160; dist <= 1800; dist += 160) {
                for (int i = 0; i < dirs.length; i++) {
                    int bx = origin.getX() + (int) (dirs[i][0] * dist * (dirs[i][0] != 0 && dirs[i][1] != 0 ? 0.7071 : 1.0));
                    int bz = origin.getZ() + (int) (dirs[i][1] * dist * (dirs[i][0] != 0 && dirs[i][1] != 0 ? 0.7071 : 1.0));
                    BlockPos samplePos = new BlockPos(bx, 64, bz);
                    RegistryEntry<Biome> biomeEntry = world.getBiome(samplePos);
                    String biomePath = biomeEntry.getKey().map(k -> k.getValue().getPath().toLowerCase(Locale.ROOT)).orElse("");
                    for (String hint : biomeHints) {
                        if (biomePath.equals(hint) || biomePath.contains(hint)) {
                            return new CompassBiomeMatch(dirNames[i], translateBiomeName(biomePath));
                        }
                    }
                }
            }
        }

        // 2. Procura primeiro bioma diferente do atual
        for (int dist = 240; dist <= 1200; dist += 240) {
            for (int i = 0; i < dirs.length; i++) {
                int bx = origin.getX() + dirs[i][0] * dist;
                int bz = origin.getZ() + dirs[i][1] * dist;
                RegistryEntry<Biome> biomeEntry = world.getBiome(new BlockPos(bx, 64, bz));
                String biomePath = biomeEntry.getKey().map(k -> k.getValue().getPath().toLowerCase(Locale.ROOT)).orElse("");
                if (!biomePath.isEmpty() && !biomePath.equals(currentBiomePath) && !biomePath.contains("ocean") && !biomePath.contains("river")) {
                    return new CompassBiomeMatch(dirNames[i], translateBiomeName(biomePath));
                }
            }
        }

        return new CompassBiomeMatch("Sudeste", null);
    }

    private static String translateBiomeName(String biomePath) {
        if (biomePath == null || biomePath.isEmpty()) return null;
        if (biomePath.contains("bamboo_jungle")) return "Selva de Bambu";
        if (biomePath.contains("jungle")) return "Selva";
        if (biomePath.contains("mangrove")) return "Manguezal";
        if (biomePath.contains("swamp")) return "Pântano";
        if (biomePath.contains("desert")) return "Deserto";
        if (biomePath.contains("badlands")) return "Mesa / Badlands";
        if (biomePath.contains("cherry")) return "Bosque de Cerejeiras";
        if (biomePath.contains("dark_forest")) return "Floresta Escura";
        if (biomePath.contains("birch")) return "Floresta de Bétulas";
        if (biomePath.contains("flower_forest")) return "Floresta de Flores";
        if (biomePath.contains("taiga")) return "Taiga";
        if (biomePath.contains("savanna")) return "Savana";
        if (biomePath.contains("meadow")) return "Prado";
        if (biomePath.contains("plains")) return "Planície";
        if (biomePath.contains("lush_caves")) return "Cavernas Verdejantes";
        if (biomePath.contains("dripstone")) return "Cavernas de Espeleotema";
        if (biomePath.contains("peaks")) return "Montanhas";
        return null;
    }
}
