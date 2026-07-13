package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ProfessionBackpackConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config CONFIG = new Config();

    public static final class Config {
        public boolean enabled = true;
        public boolean autoDiscoverProfessionDrops = false;
        public boolean defaultAutopickup = false;
        public int recentProfessionActionSeconds = 4;
        public String defaultRewardItem = "cobblemon:exp_candy_xs";
        public int defaultTradeCost = 1000;
        public int defaultTradeRewardAmount = 1;
        /**
         * Items in this set can still be stored in the backpack, but they cannot be traded
         * through the Profession Trade/Rare Candy exchange. This is intentionally separate
         * from ItemData.enabled so junk materials can be collected safely without becoming
         * an economy exploit.
         */
        public Set<String> tradeDisabledItems = defaultTradeDisabledItems();
        public Map<String, ItemData> items = new LinkedHashMap<>();
    }

    public static final class ItemData {
        public String profession;
        public String item;
        public String displayName;
        public boolean enabled = true;
        public String rewardItem = "cobblemon:exp_candy_xs";
        public int tradeCost = 1000;
        public int rewardAmount = 1;
        public boolean tradeEnabled = true;
        public int sort = 0;

        public ItemData() {}
        public ItemData(ProfessionType profession, String item, String displayName, int sort) {
            this.profession = profession.name();
            this.item = item;
            this.displayName = displayName;
            this.sort = sort;
        }
    }

    public static void load() {
        try {
            File file = file();
            if (!file.exists()) {
                CONFIG = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(file)) {
                Config loaded = GSON.fromJson(reader, Config.class);
                CONFIG = loaded == null ? defaults() : loaded;
            }
            mergeMissingDefaults();
            normalize();
            save();
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = defaults();
            mergeMissingDefaults();
            normalize();
        }
    }

    public static void save() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (!parent.exists()) parent.mkdirs();
            normalize();
            File temp = new File(parent, file.getName() + ".tmp");
            try (FileWriter writer = new FileWriter(temp)) {
                GSON.toJson(CONFIG, writer);
                writer.flush();
            }
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static ItemData get(String itemId) {
        if (itemId == null) return null;
        return CONFIG.items.get(normalizeItem(itemId));
    }

    public static boolean isBackpackProfession(String profession) {
        if (profession == null) return false;
        String normalized = profession.trim().toUpperCase(Locale.ROOT);
        return normalized.equals(ProfessionType.MINING.name()) || normalized.equals(ProfessionType.FORESTRY.name()) || normalized.equals(ProfessionType.FARMING.name());
    }

    public static boolean isBackpackProfession(ProfessionType profession) {
        return profession == ProfessionType.MINING || profession == ProfessionType.FORESTRY || profession == ProfessionType.FARMING;
    }

    public static boolean isCollectableConfigured(String itemId) {
        ItemData data = get(itemId);
        return data != null && data.enabled && isBackpackProfession(data.profession);
    }

    public static boolean allowItem(String itemId, ProfessionType profession, String displayName) {
        if (itemId == null || itemId.isBlank() || !isBackpackProfession(profession)) return false;
        String id = normalizeItem(itemId);
        if (!isAllowedProfessionItem(id, profession)) return false;
        ItemData data = CONFIG.items.get(id);
        if (data == null) data = new ItemData(profession, id, displayName == null || displayName.isBlank() ? formatName(id) : displayName, CONFIG.items.size() + 1);
        data.item = id;
        data.profession = profession.name();
        data.displayName = displayName == null || displayName.isBlank() ? formatName(id) : displayName;
        data.enabled = true;
        data.tradeCost = normalizeConfiguredTradeCost(id, data.tradeCost);
        data.rewardItem = data.rewardItem == null || data.rewardItem.isBlank() ? CONFIG.defaultRewardItem : normalizeItem(data.rewardItem);
        data.rewardAmount = Math.max(1, data.rewardAmount <= 0 ? CONFIG.defaultTradeRewardAmount : data.rewardAmount);
        CONFIG.items.put(id, data);
        save();
        return true;
    }

    public static boolean setEnabled(String itemId, boolean enabled) {
        ItemData data = get(itemId);
        if (data == null) return false;
        data.enabled = enabled;
        save();
        return true;
    }

    public static boolean isTradeDisabled(String itemId) {
        if (itemId == null || itemId.isBlank()) return false;
        if (CONFIG.tradeDisabledItems == null) CONFIG.tradeDisabledItems = defaultTradeDisabledItems();
        return CONFIG.tradeDisabledItems.contains(normalizeTradeItemId(itemId));
    }

    public static boolean isTradeEnabled(String itemId) {
        ItemData data = get(itemId);
        return data != null && data.enabled && data.tradeEnabled && !isTradeDisabled(itemId);
    }

    public static boolean setTradeDisabled(String itemId, boolean disabled) {
        if (itemId == null || itemId.isBlank()) return false;
        String id = normalizeTradeItemId(itemId);
        if (id.isBlank()) return false;
        if (CONFIG.tradeDisabledItems == null) CONFIG.tradeDisabledItems = new LinkedHashSet<>();
        if (disabled) {
            CONFIG.tradeDisabledItems.add(id);
        } else {
            CONFIG.tradeDisabledItems.remove(id);
        }
        ItemData data = get(id);
        if (data != null) {
            data.tradeEnabled = !disabled;
        }
        save();
        return true;
    }

    public static boolean removeItem(String itemId) {
        if (itemId == null) return false;
        boolean removed = CONFIG.items.remove(normalizeItem(itemId)) != null;
        if (removed) save();
        return removed;
    }

    public static ItemData ensureDiscovered(ProfessionType profession, String itemId) {
        if (profession == null || itemId == null || itemId.isBlank()) return null;
        if (!isBackpackProfession(profession)) return null;
        String id = normalizeItem(itemId);
        if (!isAllowedProfessionItem(id, profession)) return null;
        ItemData existing = CONFIG.items.get(id);
        if (existing != null) return existing;
        if (!CONFIG.autoDiscoverProfessionDrops) return null;
        ItemData data = new ItemData(profession, id, formatName(id), CONFIG.items.size() + 1);
        data.tradeCost = Math.max(1, defaultTradeCostFor(id));
        data.rewardItem = CONFIG.defaultRewardItem;
        data.rewardAmount = Math.max(1, CONFIG.defaultTradeRewardAmount);
        CONFIG.items.put(id, data);
        save();
        return data;
    }

    public static String normalizeItem(String itemId) {
        return normalizeTradeItemId(itemId);
    }

    public static String normalizeTradeItemId(String itemId) {
        if (itemId == null) return "";
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) return "";
        if (!id.contains(":")) id = "minecraft:" + id;
        if (id.equals("minecraft:netherack")) id = "minecraft:netherrack";
        id = normalizeCobblemonStoneAlias(id);
        return id;
    }

    private static String normalizeCobblemonStoneAlias(String id) {
        String namespace = id.contains(":") ? id.substring(0, id.indexOf(':')) : "minecraft";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        String fixedPath = switch (path) {
            case "dawnstone" -> "dawn_stone";
            case "duskstone" -> "dusk_stone";
            case "firestone" -> "fire_stone";
            case "icestone" -> "ice_stone";
            case "leafstone" -> "leaf_stone";
            case "moonstone" -> "moon_stone";
            case "shinystone" -> "shiny_stone";
            case "sunstone" -> "sun_stone";
            case "thunderstone" -> "thunder_stone";
            case "waterstone" -> "water_stone";
            default -> path;
        };
        if ("minecraft".equals(namespace) && fixedPath.endsWith("_stone") && isCobblemonEvolutionStonePath(fixedPath)) {
            return "cobblemon:" + fixedPath;
        }
        return namespace + ":" + fixedPath;
    }

    private static boolean isCobblemonEvolutionStonePath(String path) {
        return switch (path) {
            case "dawn_stone", "dusk_stone", "fire_stone", "ice_stone", "leaf_stone", "moon_stone", "shiny_stone", "sun_stone", "thunder_stone", "water_stone" -> true;
            default -> false;
        };
    }

    public static ProfessionType allowedProfessionFor(String itemId) {
        String id = normalizeItem(itemId);
        if (id.isBlank()) return null;
        String path = id.substring(id.indexOf(':') + 1);

        if (id.equals("minecraft:stick") || id.equals("minecraft:apple") ||
                path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae") ||
                path.startsWith("stripped_") && (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem") || path.endsWith("_hyphae")) ||
                path.endsWith("_sapling") || path.endsWith("_fungus") || path.contains("apricorn") ||
                id.equals("cobblemon:sweet_apple") || id.equals("cobblemon:tart_apple")) {
            return ProfessionType.FORESTRY;
        }

        if (path.endsWith("_berry") || path.endsWith("_seeds") || path.endsWith("_seed") ||
                path.endsWith("_mint_leaf") || id.equals("minecraft:wheat") || id.equals("minecraft:carrot") ||
                id.equals("minecraft:potato") || id.equals("minecraft:poisonous_potato") || id.equals("minecraft:beetroot") ||
                id.equals("minecraft:pumpkin") || id.equals("minecraft:melon_slice") || id.equals("minecraft:sugar_cane") ||
                id.equals("minecraft:cocoa_beans") || id.equals("minecraft:cactus") || id.equals("minecraft:bamboo") ||
                id.equals("minecraft:nether_wart") || id.equals("cobblemon:vivichoke") ||
                id.equals("cobblemon:medicinal_leek") || id.equals("cobblemon:pep_up_flower") ||
                id.equals("cobblemon:revival_herb") || id.equals("cobblemon:energy_root") || id.equals("cobblemon:big_root")) {
            return ProfessionType.FARMING;
        }

        if (path.endsWith("_fossil") || path.startsWith("fossilized_") || path.contains("tumblestone") ||
                isCobblemonEvolutionStonePath(path) || path.endsWith("_ore") || path.contains("stone_ore") ||
                path.startsWith("raw_") || id.equals("minecraft:coal") || id.equals("minecraft:charcoal") ||
                id.equals("minecraft:diamond") || id.equals("minecraft:emerald") || id.equals("minecraft:redstone") ||
                id.equals("minecraft:lapis_lazuli") || id.equals("minecraft:quartz") || id.equals("minecraft:ancient_debris") ||
                id.equals("minecraft:flint") || isBulkMiningMaterial(id)) {
            return ProfessionType.MINING;
        }
        return null;
    }

    public static boolean isAllowedProfessionItem(String itemId, ProfessionType profession) {
        ProfessionType allowed = allowedProfessionFor(itemId);
        return allowed != null && allowed == profession;
    }

    private static boolean isBulkMiningMaterial(String id) {
        return switch (id) {
            case "minecraft:stone", "minecraft:cobblestone", "minecraft:deepslate", "minecraft:cobbled_deepslate",
                    "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff", "minecraft:calcite",
                    "minecraft:dripstone_block", "minecraft:pointed_dripstone", "minecraft:dirt", "minecraft:coarse_dirt",
                    "minecraft:rooted_dirt", "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
                    "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:clay", "minecraft:clay_ball",
                    "minecraft:netherrack", "minecraft:blackstone", "minecraft:basalt", "minecraft:smooth_basalt",
                    "minecraft:end_stone", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:magma_block",
                    "minecraft:moss_block", "minecraft:mud", "minecraft:packed_mud", "minecraft:smooth_stone",
                    "minecraft:stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks",
                    "minecraft:chiseled_stone_bricks", "minecraft:infested_stone", "minecraft:infested_cobblestone",
                    "minecraft:infested_stone_bricks", "minecraft:prismarine", "minecraft:dark_prismarine",
                    "minecraft:prismarine_bricks", "minecraft:sandstone", "minecraft:red_sandstone" -> true;
            default -> false;
        };
    }

    private static boolean isBulkEasyTradeItem(String id) {
        ProfessionType profession = allowedProfessionFor(id);
        if (profession == ProfessionType.FARMING || profession == ProfessionType.FORESTRY) return true;
        return isBulkMiningMaterial(id) || id.equals("minecraft:flint");
    }

    private static File file() {
        return new File("config/champutils/profession_backpack.json");
    }

    private static void normalize() {
        if (CONFIG.defaultRewardItem == null || CONFIG.defaultRewardItem.isBlank()) CONFIG.defaultRewardItem = "cobblemon:exp_candy_xs";
        CONFIG.defaultRewardItem = normalizeTradeItemId(CONFIG.defaultRewardItem);
        CONFIG.defaultTradeCost = Math.max(1, CONFIG.defaultTradeCost);
        CONFIG.defaultTradeRewardAmount = Math.max(1, CONFIG.defaultTradeRewardAmount);
        if (CONFIG.tradeDisabledItems == null) CONFIG.tradeDisabledItems = defaultTradeDisabledItems();
        LinkedHashSet<String> fixedDisabledTrades = new LinkedHashSet<>();
        for (String itemId : CONFIG.tradeDisabledItems) {
            String id = normalizeTradeItemId(itemId);
            if (!id.isBlank()) fixedDisabledTrades.add(id);
        }
        CONFIG.tradeDisabledItems = fixedDisabledTrades;
        if (CONFIG.items == null) CONFIG.items = new LinkedHashMap<>();
        LinkedHashMap<String, ItemData> fixed = new LinkedHashMap<>();
        for (Map.Entry<String, ItemData> entry : CONFIG.items.entrySet()) {
            ItemData data = entry.getValue();
            if (data == null) continue;
            String id = normalizeItem(data.item == null || data.item.isBlank() ? entry.getKey() : data.item);
            data.item = id;
            ProfessionType allowedProfession = allowedProfessionFor(id);
            if (allowedProfession == null) continue;
            data.profession = allowedProfession.name();
            if (data.displayName == null || data.displayName.isBlank()) data.displayName = formatName(id);
            if (!isBackpackProfession(data.profession)) data.enabled = false;
            if (data.rewardItem == null || data.rewardItem.isBlank()) data.rewardItem = CONFIG.defaultRewardItem;
            data.rewardItem = normalizeTradeItemId(data.rewardItem);
        data.tradeCost = normalizeConfiguredTradeCost(id, data.tradeCost);
            data.rewardAmount = Math.max(1, data.rewardAmount <= 0 ? CONFIG.defaultTradeRewardAmount : data.rewardAmount);
            fixed.put(id, data);
        }
        CONFIG.items = fixed;
    }

    private static void mergeMissingDefaults() {
        Config defaults = defaults();
        if (CONFIG.items == null) CONFIG.items = new LinkedHashMap<>();
        for (Map.Entry<String, ItemData> entry : defaults.items.entrySet()) {
            CONFIG.items.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private static Config defaults() {
        Config c = new Config();
        int s = 0;
        add(c, ProfessionType.MINING, "minecraft:cobblestone", ++s);
        add(c, ProfessionType.MINING, "minecraft:stone", ++s);
        add(c, ProfessionType.MINING, "minecraft:cobbled_deepslate", ++s);
        add(c, ProfessionType.MINING, "minecraft:deepslate", ++s);
        add(c, ProfessionType.MINING, "minecraft:coal", ++s);
        add(c, ProfessionType.MINING, "minecraft:raw_copper", ++s);
        add(c, ProfessionType.MINING, "minecraft:raw_iron", ++s);
        add(c, ProfessionType.MINING, "minecraft:raw_gold", ++s);
        add(c, ProfessionType.MINING, "minecraft:redstone", ++s);
        add(c, ProfessionType.MINING, "minecraft:lapis_lazuli", ++s);
        add(c, ProfessionType.MINING, "minecraft:diamond", ++s);
        add(c, ProfessionType.MINING, "minecraft:emerald", ++s);
        add(c, ProfessionType.MINING, "minecraft:quartz", ++s);
        add(c, ProfessionType.MINING, "minecraft:ancient_debris", ++s);
        add(c, ProfessionType.MINING, "minecraft:flint", ++s);
        add(c, ProfessionType.MINING, "minecraft:dirt", ++s);
        add(c, ProfessionType.MINING, "minecraft:coarse_dirt", ++s);
        add(c, ProfessionType.MINING, "minecraft:rooted_dirt", ++s);
        add(c, ProfessionType.MINING, "minecraft:grass_block", ++s);
        add(c, ProfessionType.MINING, "minecraft:podzol", ++s);
        add(c, ProfessionType.MINING, "minecraft:mycelium", ++s);
        String[] bulkMining = {"sand","red_sand","gravel","clay","clay_ball","netherrack","blackstone","basalt","smooth_basalt","end_stone","obsidian","crying_obsidian","magma_block","moss_block","mud","packed_mud","pointed_dripstone"};
        for (String block : bulkMining) add(c, ProfessionType.MINING, "minecraft:" + block, ++s);
        String[] stoneVariants = {"smooth_stone","stone_bricks","cracked_stone_bricks","mossy_stone_bricks","chiseled_stone_bricks","infested_stone","infested_cobblestone","infested_stone_bricks","prismarine","dark_prismarine","prismarine_bricks","sandstone","red_sandstone"};
        for (String block : stoneVariants) add(c, ProfessionType.MINING, "minecraft:" + block, ++s);
        String[] vanillaOres = {"coal_ore","deepslate_coal_ore","copper_ore","deepslate_copper_ore","iron_ore","deepslate_iron_ore","gold_ore","deepslate_gold_ore","nether_gold_ore","redstone_ore","deepslate_redstone_ore","lapis_ore","deepslate_lapis_ore","diamond_ore","deepslate_diamond_ore","emerald_ore","deepslate_emerald_ore","nether_quartz_ore"};
        for (String ore : vanillaOres) add(c, ProfessionType.MINING, "minecraft:" + ore, ++s);
        String[] woods = {"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry"};
        for (String w : woods) {
            add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_log", ++s);
            add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_wood", ++s);
            add(c, ProfessionType.FORESTRY, "minecraft:stripped_" + w + "_log", ++s);
            add(c, ProfessionType.FORESTRY, "minecraft:stripped_" + w + "_wood", ++s);
        }
        String[] netherWoods = {"crimson","warped"};
        for (String w : netherWoods) {
            add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_stem", ++s);
            add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_hyphae", ++s);
            add(c, ProfessionType.FORESTRY, "minecraft:stripped_" + w + "_stem", ++s);
            add(c, ProfessionType.FORESTRY, "minecraft:stripped_" + w + "_hyphae", ++s);
        }
        for (String w : woods) add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_sapling", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:crimson_fungus", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:warped_fungus", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:apple", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:stick", ++s);
        String[] crops = {"wheat","wheat_seeds","carrot","potato","beetroot","beetroot_seeds","pumpkin","pumpkin_seeds","melon_slice","melon_seeds","sugar_cane","cocoa_beans","cactus","bamboo","sweet_berries","glow_berries","nether_wart"};
        for (String crop : crops) add(c, ProfessionType.FARMING, "minecraft:" + crop, ++s);
        add(c, ProfessionType.FARMING, "minecraft:poisonous_potato", ++s);
        String[] berries = {"oran","sitrus","cheri","chesto","pecha","rawst","aspear","leppa","lum","figy","wiki","mago","aguav","iapapa","razz","bluk","nanab","wepear","pinap","pomeg","kelpsy","qualot","hondew","grepa","tamato"};
        for (String b : berries) add(c, ProfessionType.FARMING, "cobblemon:" + b + "_berry", ++s);

        add(c, ProfessionType.MINING, "minecraft:granite", ++s);
        add(c, ProfessionType.MINING, "minecraft:diorite", ++s);
        add(c, ProfessionType.MINING, "minecraft:andesite", ++s);
        add(c, ProfessionType.MINING, "minecraft:tuff", ++s);
        add(c, ProfessionType.MINING, "minecraft:calcite", ++s);
        add(c, ProfessionType.MINING, "minecraft:dripstone_block", ++s);
        String[] apricorns = {"black","blue","green","pink","red","white","yellow"};
        for (String a : apricorns) {
            add(c, ProfessionType.FORESTRY, "cobblemon:" + a + "_apricorn", ++s);
            add(c, ProfessionType.FORESTRY, "cobblemon:" + a + "_apricorn_seed", ++s);
        }
        add(c, ProfessionType.FORESTRY, "cobblemon:apricorn_log", ++s);
        add(c, ProfessionType.FORESTRY, "cobblemon:apricorn_wood", ++s);
        add(c, ProfessionType.FORESTRY, "cobblemon:stripped_apricorn_log", ++s);
        add(c, ProfessionType.FORESTRY, "cobblemon:stripped_apricorn_wood", ++s);
        add(c, ProfessionType.FORESTRY, "cobblemon:apricorn_leaves", ++s);
        add(c, ProfessionType.FORESTRY, "cobblemon:sweet_apple", ++s);
        add(c, ProfessionType.FORESTRY, "cobblemon:tart_apple", ++s);
        String[] allBerries = {"aguav","apicot","aspear","babiri","belue","bluk","charti","cheri","chesto","chilan","chople","coba","colbur","cornn","custap","durin","enigma","figy","ganlon","grepa","haban","hondew","hopo","iapapa","jaboca","kasib","kebia","kee","kelpsy","lansat","leppa","liechi","lum","mago","magost","maranga","micle","nanab","nomel","occa","oran","pamtre","passho","payapa","pecha","persim","petaya","pinap","pomeg","qualot","rabuta","rawst","razz","rindo","roseli","rowap","salac","shuca","sitrus","spelon","starf","tamato","tanga","touga","wacan","watmel","wepear","wiki","yache"};
        for (String b : allBerries) add(c, ProfessionType.FARMING, "cobblemon:" + b + "_berry", ++s);
        String[] mints = {"blue","cyan","green","pink","red","white"};
        for (String m : mints) {
            add(c, ProfessionType.FARMING, "cobblemon:" + m + "_mint_leaf", ++s);
            add(c, ProfessionType.FARMING, "cobblemon:" + m + "_mint_seeds", ++s);
        }
        add(c, ProfessionType.FARMING, "cobblemon:vivichoke", ++s);
        add(c, ProfessionType.FARMING, "cobblemon:vivichoke_seeds", ++s);
        add(c, ProfessionType.FARMING, "cobblemon:medicinal_leek", ++s);
        add(c, ProfessionType.FARMING, "cobblemon:pep_up_flower", ++s);
        add(c, ProfessionType.FARMING, "cobblemon:revival_herb", ++s);
        add(c, ProfessionType.FARMING, "cobblemon:energy_root", ++s);
        add(c, ProfessionType.FARMING, "cobblemon:big_root", ++s);

        String[] evolutionStones = {"dawn","dusk","fire","ice","leaf","moon","shiny","sun","thunder","water"};
        for (String stone : evolutionStones) add(c, ProfessionType.MINING, "cobblemon:" + stone + "_stone", ++s);
        String[] fossils = {"armor","claw","cover","dome","helix","jaw","old_amber","plume","root","sail","skull"};
        for (String fossil : fossils) add(c, ProfessionType.MINING, "cobblemon:" + fossil + "_fossil", ++s);
        add(c, ProfessionType.MINING, "cobblemon:fossilized_bird", ++s);
        add(c, ProfessionType.MINING, "cobblemon:fossilized_dino", ++s);
        add(c, ProfessionType.MINING, "cobblemon:fossilized_drake", ++s);
        add(c, ProfessionType.MINING, "cobblemon:fossilized_fish", ++s);
        add(c, ProfessionType.MINING, "cobblemon:tumblestone", ++s);
        add(c, ProfessionType.MINING, "cobblemon:black_tumblestone", ++s);
        add(c, ProfessionType.MINING, "cobblemon:sky_tumblestone", ++s);
        add(c, ProfessionType.MINING, "cobblemon:tumblestone_block", ++s);
        add(c, ProfessionType.MINING, "cobblemon:black_tumblestone_block", ++s);
        add(c, ProfessionType.MINING, "cobblemon:sky_tumblestone_block", ++s);
        c.tradeDisabledItems = defaultTradeDisabledItems();
        return c;
    }

    private static Set<String> defaultTradeDisabledItems() {
        // Keep this empty by default so easy materials can be balanced with very high
        // costs instead of being hard-disabled from the Profession Trade menu.
        return new LinkedHashSet<>();
    }

    private static void add(Config c, ProfessionType type, String item, int sort) {
        String id = normalizeItem(item);
        ItemData data = new ItemData(type, id, formatName(id), sort);
        data.rewardItem = c.defaultRewardItem == null || c.defaultRewardItem.isBlank()
                ? "cobblemon:exp_candy_xs"
                : normalizeTradeItemId(c.defaultRewardItem);
        data.tradeCost = defaultTradeCostFor(id);
        data.rewardAmount = Math.max(1, c.defaultTradeRewardAmount);
        c.items.put(id, data);
    }

    public static int normalizeConfiguredTradeCost(String itemId, int requestedCost) {
        String id = normalizeItem(itemId);
        int defaultCost = defaultTradeCostFor(id);
        int cost = requestedCost <= 0 ? defaultCost : requestedCost;
        if (shouldEnforceMinimumTradeCost(id)) {
            cost = Math.max(cost, defaultCost);
        }
        return Math.max(1, Math.min(cost, 500000));
    }

    private static boolean shouldEnforceMinimumTradeCost(String itemId) {
        String id = normalizeItem(itemId);
        return isBulkEasyTradeItem(id);
    }

    private static int defaultTradeCostFor(String itemId) {
        String id = normalizeItem(itemId);
        if (isBulkEasyTradeItem(id)) {
            if (id.endsWith("_seeds") || id.endsWith("_seed")) return 6500;
            if (id.endsWith("_berry") || id.equals("minecraft:wheat") || id.equals("minecraft:carrot") || id.equals("minecraft:potato") || id.equals("minecraft:beetroot")) return 5000;
            if (isBulkMiningMaterial(id)) return 5000;
            if (id.equals("minecraft:stick")) return 5000;
            if (id.endsWith("_sapling") || id.endsWith("_fungus")) return 3500;
            if (id.endsWith("_log") || id.endsWith("_stem")) return 2500;
            return 3000;
        }
        return switch (id) {
            case "cobblemon:absorb_bulb" -> 350;
            case "cobblemon:aguav_berry" -> 5000;
            case "cobblemon:apicot_berry" -> 3500;
            case "cobblemon:apricorn_leaves" -> 4500;
            case "cobblemon:apricorn_log" -> 2500;
            case "cobblemon:apricorn_wood" -> 3000;
            case "cobblemon:armor_fossil" -> 30;
            case "cobblemon:aspear_berry" -> 5000;
            case "cobblemon:babiri_berry" -> 4000;
            case "cobblemon:belue_berry" -> 3500;
            case "cobblemon:big_root" -> 350;
            case "cobblemon:black_apricorn" -> 2800;
            case "cobblemon:black_apricorn_seed" -> 1200;
            case "cobblemon:black_belt" -> 10;
            case "cobblemon:black_tumblestone" -> 1000;
            case "cobblemon:black_tumblestone_block" -> 350;
            case "cobblemon:blue_apricorn" -> 2800;
            case "cobblemon:blue_apricorn_seed" -> 1200;
            case "cobblemon:blue_mint_leaf" -> 1800;
            case "cobblemon:blue_mint_seeds" -> 900;
            case "cobblemon:bluk_berry" -> 5000;
            case "cobblemon:charti_berry" -> 4000;
            case "cobblemon:cheri_berry" -> 5000;
            case "cobblemon:chesto_berry" -> 5000;
            case "cobblemon:chilan_berry" -> 4000;
            case "cobblemon:chople_berry" -> 4000;
            case "cobblemon:claw_fossil" -> 30;
            case "cobblemon:coba_berry" -> 4000;
            case "cobblemon:colbur_berry" -> 4000;
            case "cobblemon:cornn_berry" -> 3500;
            case "cobblemon:cover_fossil" -> 30;
            case "cobblemon:custap_berry" -> 3500;
            case "cobblemon:cyan_mint_leaf" -> 1800;
            case "cobblemon:cyan_mint_seeds" -> 900;
            case "cobblemon:dawn_stone" -> 25;
            case "cobblemon:dome_fossil" -> 30;
            case "cobblemon:durin_berry" -> 3500;
            case "cobblemon:dusk_stone" -> 25;
            case "cobblemon:enigma_berry" -> 3500;
            case "cobblemon:figy_berry" -> 5000;
            case "cobblemon:fire_stone" -> 25;
            case "cobblemon:fossilized_bird" -> 30;
            case "cobblemon:fossilized_dino" -> 30;
            case "cobblemon:fossilized_drake" -> 30;
            case "cobblemon:fossilized_fish" -> 30;
            case "cobblemon:ganlon_berry" -> 3500;
            case "cobblemon:grassy_seed" -> 400;
            case "cobblemon:green_apricorn" -> 2800;
            case "cobblemon:green_apricorn_seed" -> 1200;
            case "cobblemon:green_mint_leaf" -> 1800;
            case "cobblemon:green_mint_seeds" -> 900;
            case "cobblemon:grepa_berry" -> 5000;
            case "cobblemon:haban_berry" -> 4000;
            case "cobblemon:hearty_grains" -> 5000;
            case "cobblemon:helix_fossil" -> 30;
            case "cobblemon:hondew_berry" -> 5000;
            case "cobblemon:hopo_berry" -> 3500;
            case "cobblemon:iapapa_berry" -> 5000;
            case "cobblemon:ice_stone" -> 25;
            case "cobblemon:jaboca_berry" -> 3500;
            case "cobblemon:jaw_fossil" -> 30;
            case "cobblemon:kasib_berry" -> 4000;
            case "cobblemon:kebia_berry" -> 4000;
            case "cobblemon:kee_berry" -> 3500;
            case "cobblemon:kelpsy_berry" -> 5000;
            case "cobblemon:kings_rock" -> 10;
            case "cobblemon:lansat_berry" -> 3500;
            case "cobblemon:leaf_stone" -> 25;
            case "cobblemon:leppa_berry" -> 5000;
            case "cobblemon:liechi_berry" -> 3500;
            case "cobblemon:lum_berry" -> 5000;
            case "cobblemon:mago_berry" -> 5000;
            case "cobblemon:magost_berry" -> 3500;
            case "cobblemon:maranga_berry" -> 3500;
            case "cobblemon:medicinal_leek" -> 1400;
            case "cobblemon:micle_berry" -> 3500;
            case "cobblemon:miracle_seed" -> 400;
            case "cobblemon:moon_stone" -> 25;
            case "cobblemon:mystic_water" -> 10;
            case "cobblemon:nanab_berry" -> 5000;
            case "cobblemon:nomel_berry" -> 3500;
            case "cobblemon:occa_berry" -> 4000;
            case "cobblemon:old_amber_fossil" -> 30;
            case "cobblemon:oran_berry" -> 5000;
            case "cobblemon:pamtre_berry" -> 3500;
            case "cobblemon:passho_berry" -> 4000;
            case "cobblemon:payapa_berry" -> 4000;
            case "cobblemon:pecha_berry" -> 5000;
            case "cobblemon:pep_up_flower" -> 1400;
            case "cobblemon:persim_berry" -> 5000;
            case "cobblemon:petaya_berry" -> 3500;
            case "cobblemon:pinap_berry" -> 5000;
            case "cobblemon:pink_apricorn" -> 2800;
            case "cobblemon:pink_apricorn_seed" -> 10;
            case "cobblemon:pink_mint_leaf" -> 1800;
            case "cobblemon:pink_mint_seeds" -> 900;
            case "cobblemon:plume_fossil" -> 30;
            case "cobblemon:pomeg_berry" -> 5000;
            case "cobblemon:qualot_berry" -> 5000;
            case "cobblemon:rabuta_berry" -> 3500;
            case "cobblemon:rawst_berry" -> 5000;
            case "cobblemon:razz_berry" -> 5000;
            case "cobblemon:red_apricorn" -> 2800;
            case "cobblemon:red_apricorn_seed" -> 1200;
            case "cobblemon:red_mint_leaf" -> 1800;
            case "cobblemon:red_mint_seeds" -> 900;
            case "cobblemon:relic_coin" -> 10;
            case "cobblemon:revival_herb" -> 600;
            case "cobblemon:rindo_berry" -> 4000;
            case "cobblemon:root_fossil" -> 30;
            case "cobblemon:roseli_berry" -> 4000;
            case "cobblemon:rowap_berry" -> 3500;
            case "cobblemon:sail_fossil" -> 30;
            case "cobblemon:salac_berry" -> 3500;
            case "cobblemon:shed_shell" -> 10;
            case "cobblemon:shiny_stone" -> 25;
            case "cobblemon:shuca_berry" -> 4000;
            case "cobblemon:silver_powder" -> 400;
            case "cobblemon:sitrus_berry" -> 5000;
            case "cobblemon:skull_fossil" -> 30;
            case "cobblemon:sky_tumblestone" -> 1000;
            case "cobblemon:sky_tumblestone_block" -> 350;
            case "cobblemon:spelon_berry" -> 3500;
            case "cobblemon:starf_berry" -> 3500;
            case "cobblemon:stripped_apricorn_log" -> 3000;
            case "cobblemon:stripped_apricorn_wood" -> 3500;
            case "cobblemon:sun_stone" -> 25;
            case "cobblemon:sweet_apple" -> 300;
            case "cobblemon:tamato_berry" -> 5000;
            case "cobblemon:tanga_berry" -> 4000;
            case "cobblemon:tart_apple" -> 300;
            case "cobblemon:thunder_stone" -> 25;
            case "cobblemon:touga_berry" -> 3500;
            case "cobblemon:tumblestone" -> 1000;
            case "cobblemon:tumblestone_block" -> 350;
            case "cobblemon:twisted_spoon" -> 10;
            case "cobblemon:vivichoke" -> 1800;
            case "cobblemon:vivichoke_seeds" -> 1600;
            case "cobblemon:wacan_berry" -> 4000;
            case "cobblemon:water_stone" -> 25;
            case "cobblemon:watmel_berry" -> 3500;
            case "cobblemon:wepear_berry" -> 5000;
            case "cobblemon:white_apricorn" -> 2800;
            case "cobblemon:white_apricorn_seed" -> 1200;
            case "cobblemon:white_mint_leaf" -> 1800;
            case "cobblemon:white_mint_seeds" -> 900;
            case "cobblemon:wiki_berry" -> 5000;
            case "cobblemon:yache_berry" -> 4000;
            case "cobblemon:yellow_apricorn" -> 2800;
            case "cobblemon:yellow_apricorn_seed" -> 1200;
            case "minecraft:acacia_log" -> 2500;
            case "minecraft:acacia_sapling" -> 3500;
            case "minecraft:ancient_debris" -> 20;
            case "minecraft:andesite" -> 4500;
            case "minecraft:apple" -> 1800;
            case "minecraft:bamboo" -> 7500;
            case "minecraft:beef" -> 10;
            case "minecraft:beetroot" -> 5000;
            case "minecraft:beetroot_seeds" -> 6500;
            case "minecraft:birch_log" -> 2500;
            case "minecraft:birch_sapling" -> 3500;
            case "minecraft:blaze_powder" -> 10;
            case "minecraft:bone" -> 10;
            case "minecraft:bone_block" -> 2500;
            case "minecraft:bone_meal" -> 5000;
            case "minecraft:brown_wool" -> 10;
            case "minecraft:cactus" -> 6500;
            case "minecraft:calcite" -> 3500;
            case "minecraft:candle" -> 10;
            case "minecraft:carrot" -> 5000;
            case "minecraft:cherry_log" -> 2500;
            case "minecraft:cherry_sapling" -> 3000;
            case "minecraft:chicken" -> 10;
            case "minecraft:clay_ball" -> 10;
            case "minecraft:coal" -> 900;
            case "minecraft:coarse_dirt" -> 5000;
            case "minecraft:cobbled_deepslate" -> 4500;
            case "minecraft:cobblestone" -> 5000;
            case "minecraft:cocoa_beans" -> 5000;
            case "minecraft:cod" -> 10;
            case "minecraft:crimson_fungus" -> 3000;
            case "minecraft:crimson_stem" -> 2200;
            case "minecraft:dark_oak_log" -> 2500;
            case "minecraft:dark_oak_sapling" -> 3200;
            case "minecraft:deepslate" -> 4500;
            case "minecraft:diamond" -> 150;
            case "minecraft:diorite" -> 4500;
            case "minecraft:dirt" -> 5000;
            case "minecraft:dripstone_block" -> 3500;
            case "minecraft:emerald" -> 180;
            case "minecraft:ender_pearl" -> 10;
            case "minecraft:feather" -> 10;
            case "minecraft:flint" -> 1200;
            case "minecraft:glow_berries" -> 5500;
            case "minecraft:gold_nugget" -> 10;
            case "minecraft:granite" -> 4500;
            case "minecraft:grass_block" -> 5000;
            case "minecraft:gravel" -> 10;
            case "minecraft:heart_of_the_sea" -> 10;
            case "minecraft:iron_nugget" -> 10;
            case "minecraft:jungle_log" -> 2500;
            case "minecraft:jungle_sapling" -> 3200;
            case "minecraft:lapis_lazuli" -> 1250;
            case "minecraft:leather" -> 10;
            case "minecraft:magma_block" -> 10;
            case "minecraft:mangrove_log" -> 2000;
            case "minecraft:mangrove_sapling" -> 2600;
            case "minecraft:melon_seeds" -> 6500;
            case "minecraft:melon_slice" -> 7000;
            case "minecraft:moss_block" -> 5000;
            case "minecraft:moss_carpet" -> 10;
            case "minecraft:mossy_cobblestone" -> 10;
            case "minecraft:mycelium" -> 5000;
            case "minecraft:nether_wart" -> 4500;
            case "minecraft:netherrack" -> 5000;
            case "minecraft:oak_log" -> 2500;
            case "minecraft:oak_sapling" -> 3500;
            case "minecraft:pearlescent_froglight" -> 10;
            case "minecraft:podzol" -> 5000;
            case "minecraft:pointed_dripstone" -> 10;
            case "minecraft:poisonous_potato" -> 800;
            case "minecraft:potato" -> 5000;
            case "minecraft:pumpkin" -> 6000;
            case "minecraft:pumpkin_seeds" -> 6500;
            case "minecraft:quartz" -> 850;
            case "minecraft:rabbit" -> 10;
            case "minecraft:rail" -> 10;
            case "minecraft:raw_copper" -> 850;
            case "minecraft:raw_gold" -> 550;
            case "minecraft:raw_iron" -> 450;
            case "minecraft:raw_iron_block" -> 10;
            case "minecraft:redstone" -> 1100;
            case "minecraft:rooted_dirt" -> 5000;
            case "minecraft:rotten_flesh" -> 10;
            case "minecraft:slime_ball" -> 10;
            case "minecraft:spruce_log" -> 2500;
            case "minecraft:spruce_planks" -> 5000;
            case "minecraft:spruce_sapling" -> 3500;
            case "minecraft:spruce_slab" -> 10;
            case "minecraft:stick" -> 5000;
            case "minecraft:stone" -> 5000;
            case "minecraft:stripped_spruce_log" -> 3000;
            case "minecraft:sugar_cane" -> 6500;
            case "minecraft:sunflower" -> 10;
            case "minecraft:sweet_berries" -> 5500;
            case "minecraft:torch" -> 10;
            case "minecraft:tuff" -> 4000;
            case "minecraft:warped_fungus" -> 3000;
            case "minecraft:warped_stem" -> 2200;
            case "minecraft:wheat" -> 5000;
            case "minecraft:wheat_seeds" -> 6500;
            default -> 1000;
        };
    }

    public static String formatName(String itemId) {
        String raw = itemId == null ? "Item" : itemId.substring(itemId.indexOf(':') + 1).replace('_', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : raw.split(" ")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
