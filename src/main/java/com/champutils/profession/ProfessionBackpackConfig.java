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
        public int defaultTradeCost = 10;
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
        public String rewardItem = "cobblemon:rare_candy";
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
        ItemData data = CONFIG.items.get(id);
        if (data == null) data = new ItemData(profession, id, displayName == null || displayName.isBlank() ? formatName(id) : displayName, CONFIG.items.size() + 1);
        data.item = id;
        data.profession = profession.name();
        data.displayName = displayName == null || displayName.isBlank() ? formatName(id) : displayName;
        data.enabled = true;
        data.tradeCost = Math.max(1, Math.min(data.tradeCost <= 0 ? CONFIG.defaultTradeCost : data.tradeCost, 10));
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
        ItemData existing = CONFIG.items.get(id);
        if (existing != null) return existing;
        if (!CONFIG.autoDiscoverProfessionDrops) return null;
        ItemData data = new ItemData(profession, id, formatName(id), CONFIG.items.size() + 1);
        data.tradeCost = Math.max(1, CONFIG.defaultTradeCost);
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
            if (data.profession == null || data.profession.isBlank()) data.profession = ProfessionType.FARMING.name();
            data.profession = data.profession.trim().toUpperCase(Locale.ROOT);
            if (id.equals("minecraft:dirt") || id.equals("minecraft:coarse_dirt") || id.equals("minecraft:rooted_dirt") || id.equals("minecraft:grass_block") || id.equals("minecraft:podzol") || id.equals("minecraft:mycelium")) {
                data.profession = ProfessionType.MINING.name();
            }
            if (data.displayName == null || data.displayName.isBlank()) data.displayName = formatName(id);
            if (!isBackpackProfession(data.profession)) data.enabled = false;
            if (data.rewardItem == null || data.rewardItem.isBlank()) data.rewardItem = CONFIG.defaultRewardItem;
            data.rewardItem = normalizeTradeItemId(data.rewardItem);
            data.tradeCost = Math.max(1, Math.min(data.tradeCost <= 0 ? CONFIG.defaultTradeCost : data.tradeCost, 500000));
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
        String[] woods = {"oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry"};
        for (String w : woods) add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_log", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:crimson_stem", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:warped_stem", ++s);
        for (String w : woods) add(c, ProfessionType.FORESTRY, "minecraft:" + w + "_sapling", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:crimson_fungus", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:warped_fungus", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:apple", ++s);
        add(c, ProfessionType.FORESTRY, "minecraft:stick", ++s);
        String[] crops = {"wheat","wheat_seeds","carrot","potato","beetroot","beetroot_seeds","pumpkin","pumpkin_seeds","melon_slice","melon_seeds","sugar_cane","cocoa_beans","cactus","bamboo","sweet_berries","glow_berries","nether_wart"};
        for (String crop : crops) add(c, ProfessionType.FARMING, "minecraft:" + crop, ++s);
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
        c.items.put(item, new ItemData(type, item, formatName(item), sort));
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
