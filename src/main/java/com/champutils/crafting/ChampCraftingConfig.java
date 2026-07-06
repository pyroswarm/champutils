package com.champutils.crafting;

import com.champutils.genesis.GenesisShopConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChampCraftingConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/champ_crafting.json");

    public static Root CONFIG = new Root();

    private ChampCraftingConfig() {}

    public static final class Root {
        public boolean enabled = true;
        public String title = "Champ Crafting";
        public boolean autoAddRegisteredGenesisItems = true;
        public boolean autoAddValuableDefaults = true;
        public List<String> defaultExcludedItemIds = defaultExcludedItemIds();
        public Map<String, RecipeData> recipes = new LinkedHashMap<>();
    }

    public static final class RecipeData {
        public String id = "genesisforms:abomasite";
        public String displayName = "Abomasite";
        public String category = "Mega Stones";
        public boolean enabled = true;
        public String outputItem = "genesisforms:abomasite";
        public int outputAmount = 1;
        public String icon = "";
        public List<CostData> costs = new ArrayList<>();
        public List<String> lore = new ArrayList<>();
        public int sort = 0;
    }

    public static final class CostData {
        /** backpack, inventory, either, or credits. Credit amounts are whole Credits. */
        public String source = "backpack";
        public String item = "minecraft:cobblestone";
        public long amount = 1L;

        public CostData() {}

        public CostData(String source, String item, long amount) {
            this.source = source;
            this.item = item;
            this.amount = amount;
        }
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                CONFIG = createDefault();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Root loaded = GSON.fromJson(reader, Root.class);
                CONFIG = loaded == null ? createDefault() : loaded;
            }
            sanitize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = createDefault();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            sanitize();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static RecipeData get(String recipeId) {
        if (recipeId == null) return null;
        return CONFIG.recipes.get(normalizeId(recipeId));
    }

    public static List<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        if (CONFIG.recipes == null) return new ArrayList<>();
        for (RecipeData recipe : CONFIG.recipes.values()) {
            if (recipe == null || !recipe.enabled) continue;
            if (recipe.category == null || recipe.category.isBlank()) continue;
            categories.add(recipe.category);
        }
        return new ArrayList<>(categories);
    }

    public static List<RecipeData> recipesForCategory(String category) {
        String target = category == null ? "" : category.trim();
        List<RecipeData> list = new ArrayList<>();
        if (CONFIG.recipes != null) {
            for (RecipeData recipe : CONFIG.recipes.values()) {
                if (recipe == null || !recipe.enabled) continue;
                if (recipe.category == null) continue;
                if (recipe.category.trim().equalsIgnoreCase(target)) list.add(recipe);
            }
        }
        list.sort(recipeComparator());
        return list;
    }

    public static String normalizeId(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "";
        if (!value.contains(":")) value = "genesisforms:" + value;
        return value;
    }

    public static String formatName(String itemId) {
        String path = path(itemId);
        if (path.isBlank()) return "Item";
        String[] parts = path.replace('-', '_').split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            if (part.equalsIgnoreCase("z")) builder.append('Z');
            else builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.length() == 0 ? path : builder.toString();
    }

    private static Root createDefault() {
        Root root = new Root();
        root.defaultExcludedItemIds = defaultExcludedItemIds();
        CONFIG = root;
        addMissingDefaults();
        root.recipes = sorted(root.recipes);
        return root;
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = new Root();
        if (CONFIG.title == null || CONFIG.title.isBlank()) CONFIG.title = "Crafting";
        if (CONFIG.defaultExcludedItemIds == null) CONFIG.defaultExcludedItemIds = defaultExcludedItemIds();
        if (!CONFIG.defaultExcludedItemIds.contains("genesisforms:ash_cap")) CONFIG.defaultExcludedItemIds.add("genesisforms:ash_cap");
        CONFIG.defaultExcludedItemIds = normalizeSet(CONFIG.defaultExcludedItemIds);
        if (CONFIG.recipes == null) CONFIG.recipes = new LinkedHashMap<>();
        if (CONFIG.autoAddValuableDefaults) addMissingValuableDefaults();
        if (CONFIG.autoAddRegisteredGenesisItems) addMissingRegisteredGenesisItems();
        addMissingStaticGenesisDefaults();

        LinkedHashMap<String, RecipeData> fixed = new LinkedHashMap<>();
        for (Map.Entry<String, RecipeData> entry : CONFIG.recipes.entrySet()) {
            RecipeData recipe = entry.getValue();
            if (recipe == null) continue;
            String id = normalizeId(recipe.id == null || recipe.id.isBlank() ? entry.getKey() : recipe.id);
            if (id.isBlank()) continue;
            recipe.id = id;
            recipe.outputItem = normalizeOutputId(recipe.outputItem == null || recipe.outputItem.isBlank() ? id : recipe.outputItem);
            if (isExcludedByDefault(id) || isExcludedByDefault(recipe.outputItem)) continue;
            if (recipe.displayName == null || recipe.displayName.isBlank()) recipe.displayName = formatName(recipe.outputItem);
            if (recipe.category == null || recipe.category.isBlank()) recipe.category = defaultCategory(recipe.outputItem);
            if (recipe.outputAmount <= 0) recipe.outputAmount = 1;
            if (recipe.icon == null || recipe.icon.isBlank()) recipe.icon = recipe.outputItem;
            if (recipe.lore == null) recipe.lore = new ArrayList<>();
            if (recipe.costs == null) recipe.costs = new ArrayList<>();
            LinkedHashMap<String, CostData> fixedCosts = new LinkedHashMap<>();
            for (CostData cost : recipe.costs) {
                if (cost == null || cost.item == null || cost.item.isBlank() || cost.amount <= 0L) continue;
                cost.item = normalizeCostItem(cost.item);
                cost.source = normalizeSource(cost.source);
                String key = cost.source + "|" + cost.item;
                CostData existing = fixedCosts.get(key);
                if (existing == null) fixedCosts.put(key, cost);
                else existing.amount += cost.amount;
            }
            recipe.costs = new ArrayList<>(fixedCosts.values());
            // Do not rebalance existing recipes during sanitize/load.
            // champ_crafting.json is the source of truth for economy tuning.
            fixed.put(id, recipe);
        }
        CONFIG.recipes = sorted(fixed);
    }


    private static void rebalanceRecipeCosts(RecipeData recipe) {
        if (recipe == null || recipe.costs == null) return;
        String category = recipe.category == null ? "" : recipe.category.toLowerCase(Locale.ROOT);
        for (CostData cost : recipe.costs) {
            if (cost == null || cost.item == null) continue;
            cost.amount = Math.max(1L, Math.min(cost.amount, maxBalancedCost(cost.item, cost.source, category)));
        }
    }

    private static long maxBalancedCost(String itemId, String source, String category) {
        String item = normalizeCostItem(itemId);
        String path = path(item).toLowerCase(Locale.ROOT);
        String normalizedSource = normalizeSource(source);
        if ("credits".equals(normalizedSource)) return 1_000_000L;
        if ("inventory".equals(normalizedSource)) {
            if (item.equals("genesisforms:mega_shard")) return 2L;
            if (item.equals("genesisforms:sparkling_stone")) return 1L;
            return 8L;
        }
        if (path.equals("cobblestone") || path.equals("stone")) return category.contains("orb") || category.contains("crystal") || category.contains("key") ? 450L : 250L;
        if (path.equals("raw_iron") || path.equals("raw_copper") || path.equals("raw_gold")) return category.contains("d") || category.contains("hyper") ? 125L : 90L;
        if (path.equals("redstone")) return 80L;
        if (path.equals("diamond")) return category.contains("hyper") || category.contains("key") ? 48L : 8L;
        if (path.equals("emerald")) return category.contains("key") ? 48L : 12L;
        if (path.equals("apple") || path.endsWith("_apricorn") || path.endsWith("_berry")) return 32L;
        if (path.endsWith("_stone") || path.endsWith("_gem")) return category.contains("key") ? 12L : 4L;
        if (path.contains("fossil") || path.equals("old_amber_fossil")) return 1L;
        if (path.equals("wheat") || path.equals("carrot") || path.equals("potato")) return 64L;
        if (path.endsWith("tumblestone")) return 64L;
        if (path.endsWith("log")) return 500L;
        return 64L;
    }

    private static void addMissingDefaults() {
        addMissingStaticGenesisDefaults();
        addMissingValuableDefaults();
        addMissingRegisteredGenesisItems();
    }

    private static void addMissingStaticGenesisDefaults() {
        Set<String> existing = existingIds();
        int sort = CONFIG.recipes == null ? 0 : CONFIG.recipes.size();
        for (String path : STATIC_GENESIS_ITEMS) {
            String id = "genesisforms:" + path;
            if (existing.contains(id)) continue;
            if (isExcludedByDefault(id)) continue;
            addRecipe(defaultRecipe(id, ++sort));
            existing.add(id);
        }
    }

    private static void addMissingRegisteredGenesisItems() {
        Set<String> existing = existingIds();
        int sort = CONFIG.recipes == null ? 0 : CONFIG.recipes.size();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId == null || item == Items.AIR) continue;
            if (!"genesisforms".equals(itemId.getNamespace())) continue;
            String id = itemId.toString().toLowerCase(Locale.ROOT);
            if (existing.contains(id)) continue;
            if (isExcludedByDefault(id)) continue;
            addRecipe(defaultRecipe(id, ++sort));
            existing.add(id);
        }
    }

    private static void addMissingValuableDefaults() {
        Set<String> existing = existingIds();
        int sort = CONFIG.recipes == null ? 0 : CONFIG.recipes.size();
        String[] ids = new String[] {
                "minecraft:enchanted_golden_apple",
                "cobblemon:ability_patch",
                "cobblemon:ability_capsule",
                "cobblemon:master_ball",
                "cobblemon:rare_candy",
                "bottlecaps:silver_bottle_cap",
                "bottlecaps:silver_bottle_cap_attack",
                "bottlecaps:silver_bottle_cap_defence",
                "bottlecaps:silver_bottle_cap_hp",
                "bottlecaps:silver_bottle_cap_special_attack",
                "bottlecaps:silver_bottle_cap_special_defence",
                "bottlecaps:silver_bottle_cap_speed",
                "bottlecaps:golden_bottle_cap"
        };
        for (String id : ids) {
            id = normalizeOutputId(id);
            if (existing.contains(id)) continue;
            addRecipe(defaultRecipe(id, ++sort));
            existing.add(id);
        }
    }

    private static void addRecipe(RecipeData recipe) {
        if (recipe == null || recipe.id == null || recipe.id.isBlank()) return;
        if (CONFIG.recipes == null) CONFIG.recipes = new LinkedHashMap<>();
        CONFIG.recipes.putIfAbsent(normalizeId(recipe.id), recipe);
    }

    private static RecipeData defaultRecipe(String itemId, int sort) {
        String id = normalizeOutputId(itemId);
        String path = path(id);
        String category = defaultCategory(id);
        RecipeData recipe = new RecipeData();
        recipe.id = id;
        recipe.outputItem = id;
        recipe.outputAmount = defaultOutputAmount(id, category);
        recipe.displayName = formatName(id);
        recipe.category = category;
        recipe.icon = id;
        recipe.sort = sort;
        recipe.enabled = true;
        recipe.costs = defaultCosts(id, category);
        recipe.lore = new ArrayList<>();
        recipe.lore.add("§7Crafted with profile backpack materials.");
        recipe.lore.add("§8Costs are premium, but balanced for the current economy.");
        if (path.contains("bottle_cap")) recipe.lore.add("§8If your BottleCaps mod uses a different namespace, edit this recipe ID/outputItem.");
        return recipe;
    }

    public static String defaultCategory(String itemId) {
        String id = normalizeOutputId(itemId);
        String path = path(id).toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) return "Rare Vanilla";
        if (path.contains("bottle_cap")) return "Hyper Training";
        if (path.contains("ability_patch") || path.contains("ability_capsule")) return "Ability Items";
        if (path.equals("master_ball") || path.endsWith("_ball")) return "Poké Balls";
        if (path.equals("rare_candy") || path.contains("candy")) return "Candies";
        if (isOfficialMegaStone(path)) return "Mega Stones";
        if (path.equals("key_stone") || path.startsWith("mega_") || path.equals("sparkling_stone") || path.equals("z_ring") || path.equals("z_power_ring") || path.equals("tera_orb")) return "Key Items";
        if (path.endsWith("ium-z") || path.endsWith("-z")) return "Z-Crystals";
        if (path.endsWith("_tera_shard")) return "Tera Shards";
        if (path.endsWith("_memory")) return "Memories";
        if (path.endsWith("_plate")) return "Plates";
        if (path.endsWith("_drive")) return "Drives";
        if (path.endsWith("_mask")) return "Masks";
        if (path.contains("orb") || path.contains("crystal") || path.equals("berserk_gene") || path.equals("soul_dew")) return "Orbs & Crystals";
        if (path.contains("rotom")) return "Rotom Items";
        return id.startsWith("genesisforms:") ? "Genesis Form Items" : GenesisShopConfig.defaultCategory(id, formatName(id));
    }

    private static List<CostData> defaultCosts(String itemId, String category) {
        String id = normalizeOutputId(itemId);
        String path = path(id).toLowerCase(Locale.ROOT);
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
        String stone = typeStone(path);
        String apricorn = typeApricorn(path);
        String berry = typeBerry(path);
        List<CostData> costs = new ArrayList<>();

        if (id.equals("minecraft:enchanted_golden_apple")) {
            add(costs, "minecraft:apple", 16);
            add(costs, "minecraft:raw_gold", 100);
            addCreditCost(costs, category, id);
            return costs;
        }
        if (path.equals("ability_patch")) {
            add(costs, "minecraft:cobblestone", 35000);
            add(costs, "minecraft:diamond", 192);
            add(costs, "minecraft:emerald", 160);
            add(costs, "cobblemon:shiny_stone", 48);
            add(costs, "cobblemon:lansat_berry", 96);
            add(costs, "cobblemon:starf_berry", 32);
            addCreditCost(costs, category, id);
            return costs;
        }
        if (path.equals("ability_capsule")) {
            add(costs, "minecraft:cobblestone", 20000);
            add(costs, "minecraft:raw_iron", 2250);
            add(costs, "minecraft:redstone", 1500);
            add(costs, "minecraft:diamond", 24);
            add(costs, "cobblemon:shiny_stone", 24);
            add(costs, "cobblemon:sitrus_berry", 192);
            addCreditCost(costs, category, id);
            return costs;
        }
        if (path.equals("bottle_cap") || path.equals("silver_bottle_cap")
                || path.equals("silver_bottle_cap_attack") || path.equals("silver_bottle_cap_atk")
                || path.equals("silver_bottle_cap_defence") || path.equals("silver_bottle_cap_defense") || path.equals("silver_bottle_cap_def")
                || path.equals("silver_bottle_cap_hp")
                || path.equals("silver_bottle_cap_special_attack") || path.equals("silver_bottle_cap_sp_atk")
                || path.equals("silver_bottle_cap_special_defence") || path.equals("silver_bottle_cap_special_defense") || path.equals("silver_bottle_cap_sp_def")
                || path.equals("silver_bottle_cap_speed")) {
            add(costs, "minecraft:cobblestone", 30000);
            add(costs, "minecraft:raw_iron", 3750);
            add(costs, "minecraft:raw_gold", 2500);
            add(costs, "minecraft:diamond", 192);
            add(costs, "minecraft:raw_iron", 5000);
            add(costs, "cobblemon:shiny_stone", 32);
            addCreditCost(costs, category, id);
            return costs;
        }
        if (path.equals("gold_bottle_cap") || path.equals("golden_bottle_cap")) {
            add(costs, "minecraft:cobblestone", 75000);
            add(costs, "minecraft:raw_gold", 8750);
            add(costs, "minecraft:diamond", 128);
            add(costs, "minecraft:emerald", 512);
            add(costs, "cobblemon:shiny_stone", 128);
            add(costs, "cobblemon:old_amber_fossil", 4);
            addCreditCost(costs, category, id);
            return costs;
        }
        if (path.equals("master_ball")) {
            add(costs, "minecraft:cobblestone", 50000);
            add(costs, "minecraft:raw_copper", 5000);
            add(costs, "minecraft:raw_iron", 5000);
            add(costs, "minecraft:raw_gold", 5000);
            add(costs, "minecraft:diamond", 384);
            add(costs, "cobblemon:black_tumblestone", 1024);
            add(costs, "cobblemon:sky_tumblestone", 1024);
            add(costs, "cobblemon:tumblestone", 1024);
            addCreditCost(costs, category, id);
            return costs;
        }
        if (path.equals("rare_candy")) {
            add(costs, "minecraft:wheat", 1200);
            add(costs, "minecraft:carrot", 1000);
            add(costs, "minecraft:potato", 1000);
            add(costs, "minecraft:apple", 320);
            add(costs, "cobblemon:oran_berry", 200);
            add(costs, "cobblemon:sitrus_berry", 104);
            addCreditCost(costs, category, id);
            return costs;
        }

        if (path.endsWith("_nectar")) {
            add(costs, "cobblemon:red_apricorn", 16);
            add(costs, "cobblemon:oran_berry", 8);
            add(costs, stone, 1);
        } else if (c.contains("mega")) {
            add(costs, "minecraft:cobblestone", 33000);
            add(costs, "minecraft:raw_iron", 3500);
            add(costs, "minecraft:raw_gold", 2750);
            add(costs, "minecraft:diamond", 224);
            add(costs, "minecraft:emerald", 160);
            add(costs, stone, 56);
            add(costs, apricorn, 224);
            add(costs, berry, 224);
            add(costs, fossilFor(path), path.contains("aerodactyl") ? 6 : 2);
            add(costs, "genesisforms:mega_shard", 16, "inventory");
        } else if (c.contains("z-crystal")) {
            add(costs, "minecraft:cobblestone", 18000);
            add(costs, "minecraft:redstone", 1600);
            add(costs, "minecraft:diamond", 128);
            add(costs, stone, 32);
            add(costs, apricorn, 170);
            add(costs, berry, 170);
            add(costs, "genesisforms:sparkling_stone", 1, "inventory");
        } else if (c.contains("tera")) {
            add(costs, "minecraft:cobblestone", 8000);
            add(costs, "minecraft:redstone", 650);
            add(costs, stone, 12);
            add(costs, apricorn, 128);
            add(costs, berry, 96);
            add(costs, "minecraft:diamond", 24);
        } else if (c.contains("plate")) {
            add(costs, "minecraft:stone", 24000);
            add(costs, "minecraft:raw_gold", 2500);
            add(costs, "minecraft:diamond", 128);
            add(costs, stone, 48);
            add(costs, berry, 128);
            add(costs, fossilFor(path), 3);
        } else if (c.contains("memory")) {
            add(costs, "minecraft:cobblestone", 19000);
            add(costs, "minecraft:redstone", 3000);
            add(costs, "minecraft:raw_iron", 2200);
            add(costs, "minecraft:diamond", 24);
            add(costs, stone, 32);
            add(costs, apricorn, 128);
        } else if (c.contains("drive") || c.contains("rotom")) {
            add(costs, "minecraft:raw_copper", 4000);
            add(costs, "minecraft:raw_iron", 3000);
            add(costs, "minecraft:redstone", 3000);
            add(costs, stone, 32);
            add(costs, "minecraft:diamond", 256);
        } else if (c.contains("orb") || c.contains("crystal") || c.contains("key")) {
            add(costs, "minecraft:cobblestone", 55000);
            add(costs, "minecraft:raw_gold", 5000);
            add(costs, "minecraft:diamond", 384);
            add(costs, "minecraft:emerald", 384);
            add(costs, stone, 96);
            add(costs, apricorn, 384);
            add(costs, berry, 384);
            add(costs, fossilFor(path), 6);
        } else if (c.contains("mask")) {
            add(costs, "minecraft:oak_log", 5000);
            add(costs, "minecraft:apple", 512);
            add(costs, apricorn, 512);
            add(costs, berry, 512);
            add(costs, stone, 48);
            add(costs, "minecraft:diamond", 128);
        } else {
            add(costs, "minecraft:cobblestone", 22000);
            add(costs, "minecraft:raw_iron", 2200);
            add(costs, "minecraft:raw_gold", 1800);
            add(costs, "minecraft:diamond", 128);
            add(costs, stone, 32);
            add(costs, apricorn, 170);
            add(costs, berry, 170);
        }
        addCreditCost(costs, category, id);
            return costs;
    }

    private static void add(List<CostData> costs, String item, long amount) {
        add(costs, item, amount, "backpack");
    }

    private static void add(List<CostData> costs, String item, long amount, String source) {
        if (costs == null || item == null || item.isBlank() || amount <= 0L) return;
        String normalizedSource = normalizeSource(source);
        String normalizedItem = normalizeCostItem(item);
        for (CostData existing : costs) {
            if (existing != null && normalizedSource.equals(normalizeSource(existing.source)) && normalizedItem.equals(normalizeCostItem(existing.item))) {
                existing.source = normalizedSource;
                existing.item = normalizedItem;
                existing.amount += amount;
                return;
            }
        }
        costs.add(new CostData(normalizedSource, normalizedItem, amount));
    }

    private static void addCreditCost(List<CostData> costs, String category, String itemId) {
        add(costs, "credits", defaultCreditCost(itemId, category), "credits");
    }

    private static long defaultCreditCost(String itemId, String category) {
        String id = normalizeOutputId(itemId);
        String path = path(id).toLowerCase(Locale.ROOT);
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (id.equals("minecraft:enchanted_golden_apple")) return 50L;
        if (path.equals("rare_candy")) return 25L;
        if (path.equals("exp_candy_xs")) return 5L;
        if (path.equals("exp_candy_s")) return 10L;
        if (path.equals("exp_candy_m")) return 25L;
        if (path.equals("exp_candy_l")) return 50L;
        if (path.equals("exp_candy_xl")) return 100L;
        if (path.equals("ability_capsule")) return 100L;
        if (path.equals("ability_patch")) return 250L;
        if (path.equals("master_ball")) return 750L;
        if (path.contains("bottle_cap")) return path.contains("gold") ? 500L : 150L;
        if (path.endsWith("_nectar")) return 10L;
        if (path.equals("ash_cap")) return 0L;
        if (c.contains("mega")) return 250L;
        if (c.contains("key")) return 500L;
        if (c.contains("z-crystal")) return 150L;
        if (c.contains("tera")) return 25L;
        if (c.contains("plate")) return 100L;
        if (c.contains("memory") || c.contains("drive") || c.contains("mask")) return 100L;
        if (c.contains("orb") || c.contains("crystal")) return 250L;
        return 75L;
    }

    private static int defaultOutputAmount(String id, String category) {
        String path = path(id);
        if (path.endsWith("_tera_shard")) return 5;
        if (path.equals("rare_candy")) return 1;
        return 1;
    }

    private static String normalizeOutputId(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) return "";
        if (!value.contains(":")) value = "minecraft:" + value;
        if (value.equals("minecraft:netherack")) value = "minecraft:netherrack";
        return value;
    }

    private static String normalizeSource(String raw) {
        String source = raw == null ? "backpack" : raw.trim().toLowerCase(Locale.ROOT);
        if (!source.equals("inventory") && !source.equals("either") && !source.equals("credits")) source = "backpack";
        return source;
    }

    private static String normalizeCostItem(String raw) {
        if (raw != null) {
            String trimmed = raw.trim().toLowerCase(Locale.ROOT);
            if (trimmed.equals("credits") || trimmed.equals("credit") || trimmed.equals("economy:credits")) return "credits";
        }
        String item = normalizeOutputId(raw);
        if (item.equals("minecraft:iron_ingot")) return "minecraft:raw_iron";
        if (item.equals("minecraft:copper_ingot")) return "minecraft:raw_copper";
        if (item.equals("minecraft:gold_ingot")) return "minecraft:raw_gold";
        return item;
    }

    private static Set<String> existingIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (CONFIG != null && CONFIG.recipes != null) {
            for (RecipeData recipe : CONFIG.recipes.values()) {
                if (recipe != null && recipe.id != null) ids.add(normalizeId(recipe.id));
            }
            ids.addAll(CONFIG.recipes.keySet().stream().map(ChampCraftingConfig::normalizeId).toList());
        }
        return ids;
    }

    private static List<String> normalizeSet(List<String> list) {
        LinkedHashSet<String> fixed = new LinkedHashSet<>();
        for (String value : list) {
            String id = normalizeOutputId(value);
            if (!id.isBlank()) fixed.add(id);
        }
        return new ArrayList<>(fixed);
    }

    private static LinkedHashMap<String, RecipeData> sorted(Map<String, RecipeData> input) {
        LinkedHashMap<String, RecipeData> out = new LinkedHashMap<>();
        input.values().stream().filter(r -> r != null).sorted(recipeComparator()).forEach(recipe -> out.put(normalizeId(recipe.id), recipe));
        return out;
    }

    private static Comparator<RecipeData> recipeComparator() {
        return Comparator.comparingInt((RecipeData recipe) -> categoryOrder(recipe.category))
                .thenComparingInt(recipe -> recipe.sort)
                .thenComparing(recipe -> recipe.category == null ? "" : recipe.category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(recipe -> recipe.displayName == null ? recipe.id : recipe.displayName, String.CASE_INSENSITIVE_ORDER);
    }

    private static int categoryOrder(String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (c.contains("mega")) return 10;
        if (c.contains("hyper")) return 15;
        if (c.contains("ability")) return 20;
        if (c.contains("key")) return 25;
        if (c.contains("z-crystal")) return 30;
        if (c.contains("tera")) return 35;
        if (c.contains("plate")) return 40;
        if (c.contains("memory")) return 45;
        if (c.contains("orb") || c.contains("crystal")) return 50;
        if (c.contains("drive")) return 55;
        if (c.contains("mask")) return 60;
        if (c.contains("d")) return 65;
        return 100;
    }

    private static String path(String id) {
        if (id == null) return "";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static boolean isExcludedByDefault(String itemId) {
        String id = normalizeOutputId(itemId);
        String path = path(id).toLowerCase(Locale.ROOT);
        if (CONFIG.defaultExcludedItemIds != null && CONFIG.defaultExcludedItemIds.contains(id)) return true;
        if (path.contains("dynamax") || path.startsWith("max_") || path.equals("wishing_star")) return true;
        if (EXCLUDED_ZA_MEGA_STONES.contains(path)) return true;
        return false;
    }

    private static List<String> defaultExcludedItemIds() {
        List<String> ids = new ArrayList<>();
        ids.add("genesisforms:dynamax_band");
        ids.add("genesisforms:dynamax_candy");
        ids.add("genesisforms:max_honey");
        ids.add("genesisforms:max_mushrooms");
        ids.add("genesisforms:max_soup");
        ids.add("genesisforms:wishing_star");
        ids.add("genesisforms:ash_cap");
        ids.add("genesisforms:absolite-z");
        ids.add("genesisforms:barbaracite");
        ids.add("genesisforms:baxcalibrite");
        ids.add("genesisforms:chandelurite");
        ids.add("genesisforms:chesnaughtite");
        ids.add("genesisforms:chimechite");
        ids.add("genesisforms:clefablite");
        ids.add("genesisforms:crabominite");
        ids.add("genesisforms:darkranite");
        ids.add("genesisforms:delphoxite");
        ids.add("genesisforms:dragalgite");
        ids.add("genesisforms:dragoninite");
        ids.add("genesisforms:drampanite");
        ids.add("genesisforms:eelektrossite");
        ids.add("genesisforms:emboarite");
        ids.add("genesisforms:excadrite");
        ids.add("genesisforms:falinksite");
        ids.add("genesisforms:feraligite");
        ids.add("genesisforms:floettite");
        ids.add("genesisforms:froslassite");
        ids.add("genesisforms:garchompite-z");
        ids.add("genesisforms:glimmoranite");
        ids.add("genesisforms:golisopite");
        ids.add("genesisforms:golurkite");
        ids.add("genesisforms:greninjite");
        ids.add("genesisforms:hawluchanite");
        ids.add("genesisforms:heatranite");
        ids.add("genesisforms:lucarionite-z");
        ids.add("genesisforms:magearnite");
        ids.add("genesisforms:malamarite");
        ids.add("genesisforms:meganiumite");
        ids.add("genesisforms:meowsticite");
        ids.add("genesisforms:pyroarite");
        ids.add("genesisforms:raichunite-x");
        ids.add("genesisforms:raichunite-y");
        ids.add("genesisforms:scolipite");
        ids.add("genesisforms:scovillainite");
        ids.add("genesisforms:scraftinite");
        ids.add("genesisforms:skarmorite");
        ids.add("genesisforms:staraptite");
        ids.add("genesisforms:starminite");
        ids.add("genesisforms:tatsugirinite");
        ids.add("genesisforms:victreebelite");
        ids.add("genesisforms:zeraorite");
        ids.add("genesisforms:zygardite");
        return ids;
    }

    private static boolean isOfficialMegaStone(String path) {
        return OFFICIAL_MEGA_STONES.contains(path.toLowerCase(Locale.ROOT));
    }

    private static final Set<String> OFFICIAL_MEGA_STONES = new LinkedHashSet<>(List.of(
            "abomasite", "absolite", "aerodactylite", "aggronite", "alakazite", "altarianite", "ampharosite", "audinite",
            "banettite", "beedrillite", "blastoisinite", "blazikenite", "cameruptite", "charizardite-x", "charizardite-y",
            "diancite", "galladite", "garchompite", "gardevoirite", "gengarite", "glalitite", "gyaradosite", "heracronite",
            "houndoominite", "kangaskhanite", "latiasite", "latiosite", "lopunnite", "lucarionite", "manectite", "mawilite",
            "medichamite", "metagrossite", "mewtwonite-x", "mewtwonite-y", "pidgeotite", "pinsirite", "sablenite",
            "salamencite", "sceptilite", "scizorite", "sharpedonite", "slowbronite", "steelixite", "swampertite",
            "tyranitarite", "venusaurite"
    ));

    private static final Set<String> EXCLUDED_ZA_MEGA_STONES = new LinkedHashSet<>(List.of(
            "absolite-z", "barbaracite", "baxcalibrite", "chandelurite", "chesnaughtite", "chimechite", "clefablite", "crabominite",
            "darkranite", "delphoxite", "dragalgite", "dragoninite", "drampanite", "eelektrossite", "emboarite", "excadrite",
            "falinksite", "feraligite", "floettite", "froslassite", "garchompite-z", "glimmoranite", "golisopite", "golurkite",
            "greninjite", "hawluchanite", "heatranite", "lucarionite-z", "magearnite", "malamarite", "meganiumite", "meowsticite",
            "pyroarite", "raichunite-x", "raichunite-y", "scolipite", "scovillainite", "scraftinite", "skarmorite", "staraptite",
            "starminite", "tatsugirinite", "victreebelite", "zeraorite", "zygardite"
    ));

    private static String typeStone(String path) {
        if (containsAny(path, "fire", "flame", "firium", "blaze", "heat", "charizard", "houndoom", "camerupt")) return "cobblemon:fire_stone";
        if (containsAny(path, "water", "splash", "douse", "kyogre", "blue", "blastois", "gyarados", "slowbro", "sharpedo", "swampert")) return "cobblemon:water_stone";
        if (containsAny(path, "grass", "meadow", "leaf", "sceptil", "venusaur", "abomasnow")) return "cobblemon:leaf_stone";
        if (containsAny(path, "electric", "zap", "shock", "pika", "raichu", "manect", "ampharos")) return "cobblemon:thunder_stone";
        if (containsAny(path, "ice", "icicle", "glalit", "froslass")) return "cobblemon:ice_stone";
        if (containsAny(path, "dark", "dread", "ghost", "spooky", "gengar", "banette", "sable")) return "cobblemon:dusk_stone";
        if (containsAny(path, "psychic", "mind", "mew", "latia", "latio", "alakazam", "gardevoir", "gallade", "medicham")) return "cobblemon:dawn_stone";
        if (containsAny(path, "fairy", "pixie", "diancie", "altaria", "mawile", "audino")) return "cobblemon:shiny_stone";
        if (containsAny(path, "dragon", "draco", "garchomp", "salamence", "latia", "latio")) return "cobblemon:moon_stone";
        if (containsAny(path, "rock", "stone", "ground", "earth", "steel", "iron", "aggron", "steelix", "metagross", "tyranitar", "aerodactyl")) return "cobblemon:moon_stone";
        if (containsAny(path, "bug", "insect", "beedrill", "scizor", "pinsir", "heracross")) return "cobblemon:leaf_stone";
        if (containsAny(path, "fighting", "fist", "lucario", "lopunny", "blaziken")) return "cobblemon:sun_stone";
        if (containsAny(path, "flying", "sky", "pidgeot")) return "cobblemon:shiny_stone";
        return "cobblemon:shiny_stone";
    }

    private static String typeApricorn(String path) {
        if (containsAny(path, "fire", "flame", "fighting", "dragon", "red", "blaze", "charizard")) return "cobblemon:red_apricorn";
        if (containsAny(path, "water", "blue", "ice", "splash", "douse")) return "cobblemon:blue_apricorn";
        if (containsAny(path, "grass", "bug", "leaf", "green", "meadow")) return "cobblemon:green_apricorn";
        if (containsAny(path, "electric", "yellow", "zap", "pika")) return "cobblemon:yellow_apricorn";
        if (containsAny(path, "fairy", "psychic", "pink", "mind", "pixie")) return "cobblemon:pink_apricorn";
        if (containsAny(path, "dark", "ghost", "poison", "black", "dread", "spooky")) return "cobblemon:black_apricorn";
        return "cobblemon:white_apricorn";
    }

    private static String typeBerry(String path) {
        if (containsAny(path, "fire", "flame", "blaze")) return "cobblemon:cheri_berry";
        if (containsAny(path, "water", "splash", "blue")) return "cobblemon:oran_berry";
        if (containsAny(path, "grass", "meadow", "bug")) return "cobblemon:sitrus_berry";
        if (containsAny(path, "electric", "zap", "pika")) return "cobblemon:wacan_berry";
        if (containsAny(path, "ice", "icicle")) return "cobblemon:aspear_berry";
        if (containsAny(path, "dragon", "draco")) return "cobblemon:haban_berry";
        if (containsAny(path, "dark", "dread")) return "cobblemon:colbur_berry";
        if (containsAny(path, "ghost", "spooky")) return "cobblemon:kasib_berry";
        if (containsAny(path, "fairy", "pixie")) return "cobblemon:roseli_berry";
        if (containsAny(path, "psychic", "mind")) return "cobblemon:payapa_berry";
        if (containsAny(path, "poison", "toxic")) return "cobblemon:kebia_berry";
        if (containsAny(path, "rock", "stone")) return "cobblemon:charti_berry";
        if (containsAny(path, "ground", "earth")) return "cobblemon:shuca_berry";
        if (containsAny(path, "flying", "sky")) return "cobblemon:coba_berry";
        if (containsAny(path, "fighting", "fist")) return "cobblemon:chople_berry";
        if (containsAny(path, "steel", "iron")) return "cobblemon:babiri_berry";
        return "cobblemon:lum_berry";
    }

    private static String fossilFor(String path) {
        if (path.contains("aerodactyl")) return "cobblemon:old_amber_fossil";
        if (containsAny(path, "dragon", "draco", "garchomp", "salamence", "tyranitar")) return "cobblemon:jaw_fossil";
        if (containsAny(path, "water", "splash", "gyarados", "slowbro", "sharpedo", "swampert")) return "cobblemon:helix_fossil";
        if (containsAny(path, "rock", "stone", "ground", "earth")) return "cobblemon:dome_fossil";
        if (containsAny(path, "grass", "leaf")) return "cobblemon:root_fossil";
        return "cobblemon:old_amber_fossil";
    }

    private static boolean containsAny(String value, String... needles) {
        String v = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (v.contains(needle)) return true;
        }
        return false;
    }

    private static final String[] STATIC_GENESIS_ITEMS = new String[] {
            "abomasite", "absolite-z", "absolite", "adamant_crystal", "adamant_orb", "adrenaline_orb", "aerodactylite", "aggronite", "alakazite", "aloraichium-z", "altarianite", "ampharosite", "audinite", "banettite", "barbaracite", "baxcalibrite", "beedrillite", "berserk_gene", "blank_plate", "blastoisinite", "blazikenite", "blue_orb", "booster_energy", "bug_memory", "bug_tera_shard", "buginium-z", "burn_drive", "cameruptite", "chandelurite", "charizardite-x", "charizardite-y", "chesnaughtite", "chill_drive", "chimechite", "clefablite", "cornerstone_mask", "crabominite", "dark_memory", "dark_tera_shard", "darkinium-z", "darkranite", "decidium-z", "delphoxite", "diancite", "dna_splicers", "douse_drive", "draco_plate", "dragalgite", "dragon_memory", "dragon_tera_shard", "dragoninite", "dragonium-z", "drampanite", "dread_plate", "dynamax_band", "dynamax_candy", "earth_plate", "eelektrossite", "eevium-z", "electric_memory", "electric_tera_shard", "electrium-z", "emboarite", "excadrite", "fairium-z", "fairy_memory", "fairy_tera_shard", "falinksite", "feraligite", "fighting_memory", "fighting_tera_shard", "fightinium-z", "fire_memory", "fire_tera_shard", "firium-z", "fist_plate", "flame_plate", "floettite", "flying_memory", "flying_tera_shard", "flyinium-z", "froslassite", "galladite", "garchompite-z", "garchompite", "gardevoirite", "gengarite", "ghost_memory", "ghost_tera_shard", "ghostium-z", "glalitite", "glimmoranite", "golisopite", "golurkite", "gracidea_flower", "grass_memory", "grass_tera_shard", "grassium-z", "greninjite", "griseous_core", "griseous_orb", "ground_memory", "ground_tera_shard", "groundium-z", "gyaradosite", "hawluchanite", "hearthflame_mask", "heatranite", "heracronite", "houndoominite", "ice_memory", "ice_tera_shard", "icicle_plate", "icium-z", "incinium-z", "insect_plate", "iron_plate", "kangaskhanite", "key_stone", "kommonium-z", "latiasite", "latiosite", "legend_plate", "lopunnite", "lucarionite-z", "lucarionite", "lucky_punch", "lunalium-z", "lustrous_globe", "lustrous_orb", "lycanium-z", "macho_brace", "magearnite", "malamarite", "manectite", "marshadium-z", "mawilite", "max_honey", "max_mushrooms", "max_soup", "meadow_plate", "medichamite", "mega_bracelet", "mega_charm", "mega_cuff", "mega_ring", "mega_shard", "meganiumite", "meowsticite", "metagrossite", "meteorite", "mewnium-z", "mewtwonite-x", "mewtwonite-y", "mimikium-z", "mind_plate", "n_lunarizer", "n_solarizer", "normal_tera_shard", "normalium-z", "pidgeotite", "pikanium-z", "pikashunium-z", "pink_nectar", "pinsirite", "pixie_plate", "poison_memory", "poison_tera_shard", "poisonium-z", "primarium-z", "prison_bottle", "psychic_memory", "psychic_tera_shard", "psychium-z", "purple_nectar", "pyroarite", "raichunite-x", "raichunite-y", "red_nectar", "red_orb", "reins_of_unity", "reveal_glass", "rock_memory", "rock_tera_shard", "rockium-z", "rotom_catalog", "rotom_fan", "rotom_lawn_mower", "rotom_light_bulb", "rotom_microwave_oven", "rotom_refrigerator", "rotom_washing_machine", "rusted_shield", "rusted_sword", "sablenite", "salamencite", "sceptilite", "scizorite", "scolipite", "scovillainite", "scraftinite", "sharpedonite", "shock_drive", "skarmorite", "sky_plate", "slowbronite", "snorlium-z", "solganium-z", "soul_dew", "sparkling_stone", "splash_plate", "spooky_plate", "staraptite", "starminite", "steel_memory", "steel_tera_shard", "steelium-z", "steelixite", "stellar_tera_shard", "stone_plate", "swampertite", "tapunium-z", "tatsugirinite", "teal_mask", "tera_orb", "toxic_plate", "tyranitarite", "ultranecrozium-z", "venusaurite", "victreebelite", "water_memory", "water_tera_shard", "waterium-z", "wellspring_mask", "wishing_star", "yellow_nectar", "z_power_ring", "z_ring", "zap_plate", "zeraorite", "zygarde_cube", "zygardite"
    };
}
