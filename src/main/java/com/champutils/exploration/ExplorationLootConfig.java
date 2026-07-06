package com.champutils.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ExplorationLootConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/exploration_loot.json");
    private static Data data = new Data();

    private ExplorationLootConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                data = defaults();
                applyBetaBalance(data);
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? defaults() : loaded.withDefaults();
                applyBetaBalance(data);
            }
            save();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load exploration_loot.json. Using defaults.");
            e.printStackTrace();
            data = defaults();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data.withDefaults(), writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save exploration_loot.json.");
            e.printStackTrace();
        }
    }

    public static Data get() {
        return data.withDefaults();
    }

    private static Data defaults() {
        Data root = new Data();
        root.tables.put("overworld", table(1, 3,
                loot("F", "minecraft:iron_ingot", 80, 2, 8),
                loot("F", "minecraft:gold_ingot", 60, 2, 6),
                loot("F", "minecraft:emerald", 45, 1, 4),
                loot("F", "minecraft:name_tag", 18, 1, 1),
                loot("E", "minecraft:diamond", 20, 1, 2),
                loot("E", "minecraft:experience_bottle", 35, 4, 12),
                loot("E", "cobblemon:great_ball", 45, 2, 6),
                loot("E", "cobblemon:ultra_ball", 28, 1, 4),
                loot("E", "cobblemon:rare_candy", 16, 1, 2),
                loot("D", "cobblemon:exp_candy_l", 14, 1, 2),
                loot("D", "cobblemon:ability_capsule", 8, 1, 1),
                loot("D", "cobblemon:fire_stone", 7, 1, 1),
                loot("D", "cobblemon:water_stone", 7, 1, 1),
                loot("D", "cobblemon:thunder_stone", 7, 1, 1),
                loot("C", "cobblemon:ability_patch", 3, 1, 1),
                loot("C", "cobblemon:master_ball", 1, 1, 1),
                loot("C", "genesisforms:tera_orb", 2, 1, 1),
                loot("C", "genesisforms:adamant_crystal", 2, 1, 1),
                loot("C", "genesisforms:lustrous_globe", 2, 1, 1),
                loot("C", "genesisforms:griseous_core", 2, 1, 1)
        ));
        root.tables.put("nether", table(1, 3,
                loot("F", "minecraft:gold_ingot", 80, 3, 10),
                loot("F", "minecraft:quartz", 70, 6, 20),
                loot("F", "minecraft:blaze_rod", 45, 1, 4),
                loot("E", "minecraft:netherite_scrap", 12, 1, 1),
                loot("E", "minecraft:diamond", 20, 1, 2),
                loot("E", "cobblemon:ultra_ball", 35, 1, 5),
                loot("E", "cobblemon:burn_heal", 30, 2, 5),
                loot("D", "cobblemon:fire_stone", 14, 1, 2),
                loot("D", "cobblemon:exp_candy_l", 14, 1, 2),
                loot("D", "cobblemon:ability_capsule", 8, 1, 1),
                loot("C", "cobblemon:ability_patch", 3, 1, 1),
                loot("C", "genesisforms:adamant_crystal", 2, 1, 1),
                loot("C", "genesisforms:griseous_core", 2, 1, 1)
        ));
        root.tables.put("end", table(1, 3,
                loot("F", "minecraft:ender_pearl", 80, 4, 12),
                loot("F", "minecraft:chorus_fruit", 70, 8, 24),
                loot("E", "minecraft:diamond", 30, 1, 3),
                loot("E", "minecraft:shulker_shell", 18, 1, 2),
                loot("E", "cobblemon:ultra_ball", 35, 2, 6),
                loot("E", "cobblemon:quick_ball", 30, 2, 6),
                loot("D", "minecraft:elytra", 1, 1, 1),
                loot("D", "cobblemon:exp_candy_xl", 8, 1, 1),
                loot("D", "cobblemon:ability_capsule", 8, 1, 1),
                loot("C", "cobblemon:ability_patch", 3, 1, 1),
                loot("C", "genesisforms:lustrous_globe", 2, 1, 1),
                loot("C", "genesisforms:griseous_core", 2, 1, 1)
        ));
        return root;
    }


    private static void applyBetaBalance(Data d) {
        if (d == null) return;
        d.maxRarity = "D";
        if (d.bannedItemContains == null) d.bannedItemContains = new ArrayList<>();
        for (String banned : List.of("master_ball", "tera_orb", "adamant_crystal", "lustrous_globe", "griseous_core", "elytra", "netherite_block")) {
            if (!d.bannedItemContains.contains(banned)) d.bannedItemContains.add(banned);
        }
        if (d.tables != null) {
            for (Map.Entry<String, LootTable> tableEntry : d.tables.entrySet()) {
                LootTable table = tableEntry.getValue();
                if (table == null) continue;
                table.minRolls = Math.max(2, Math.min(table.minRolls, 3));
                table.maxRolls = Math.max(table.minRolls, Math.min(table.maxRolls, 5));
                if (table.items == null) table.items = new ArrayList<>();
                addDilutionItems(table, tableEntry.getKey());
                for (LootEntry entry : table.items) {
                    if (entry == null) continue;
                    if (entry.rarityRank() >= 2) entry.weight = Math.min(entry.weight, 10);
                    if (entry.rarityRank() >= 3) entry.weight = 0;
                    entry.maxAmount = Math.min(Math.max(entry.minAmount, entry.maxAmount), Math.max(entry.minAmount, 4));
                    if (entry.itemId != null && entry.itemId.contains("rare_candy")) entry.maxAmount = Math.min(entry.maxAmount, 1);
                    if (entry.itemId != null && entry.itemId.contains("exp_candy")) entry.maxAmount = Math.min(entry.maxAmount, 2);
                }
            }
        }
    }

    private static void addDilutionItems(LootTable table, String tableId) {
        addLootIfMissing(table, "F", "cobblemon:poke_ball", 120, 3, 8);
        addLootIfMissing(table, "F", "cobblemon:potion", 80, 2, 4);
        addLootIfMissing(table, "F", "cobblemon:super_potion", 55, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:antidote", 45, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:paralyze_heal", 45, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:awakening", 35, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:oran_berry", 80, 2, 6);
        addLootIfMissing(table, "F", "cobblemon:sitrus_berry", 35, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:exp_candy_xs", 55, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:exp_candy_s", 35, 1, 2);
        addLootIfMissing(table, "F", "cobblemon:red_apricorn", 60, 2, 6);
        addLootIfMissing(table, "F", "cobblemon:blue_apricorn", 60, 2, 6);
        addLootIfMissing(table, "F", "cobblemon:yellow_apricorn", 60, 2, 6);
        addLootIfMissing(table, "F", "cobblemon:green_apricorn", 50, 2, 6);
        addLootIfMissing(table, "F", "cobblemon:black_apricorn", 40, 1, 4);
        addLootIfMissing(table, "F", "cobblemon:white_apricorn", 40, 1, 4);
        addLootIfMissing(table, "F", "cobblemon:pink_apricorn", 40, 1, 4);
        addLootIfMissing(table, "E", "cobblemon:tumblestone", 55, 2, 6);
        addLootIfMissing(table, "E", "cobblemon:black_tumblestone", 45, 2, 5);
        addLootIfMissing(table, "E", "cobblemon:sky_tumblestone", 45, 2, 5);
        addLootIfMissing(table, "E", "cobblemon:great_ball", 70, 2, 5);
        addLootIfMissing(table, "E", "cobblemon:heal_ball", 45, 2, 4);
        addLootIfMissing(table, "E", "cobblemon:net_ball", 35, 1, 3);
        addLootIfMissing(table, "E", "cobblemon:dive_ball", 35, 1, 3);
        addLootIfMissing(table, "E", "cobblemon:dusk_ball", 30, 1, 2);
        addLootIfMissing(table, "E", "cobblemon:quick_ball", 28, 1, 2);
        addLootIfMissing(table, "E", "cobblemon:revive", 28, 1, 2);
        addLootIfMissing(table, "E", "cobblemon:exp_candy_m", 22, 1, 2);
        addLootIfMissing(table, "E", "cobblemon:link_cable", 8, 1, 1);
        addLootIfMissing(table, "D", "cobblemon:dawn_stone", 6, 1, 1);
        addLootIfMissing(table, "D", "cobblemon:dusk_stone", 6, 1, 1);
        addLootIfMissing(table, "D", "cobblemon:moon_stone", 6, 1, 1);
        addLootIfMissing(table, "D", "cobblemon:shiny_stone", 6, 1, 1);
        addLootIfMissing(table, "D", "cobblemon:rare_candy", 5, 1, 1);
        if (tableId != null && tableId.equalsIgnoreCase("nether")) {
            addLootIfMissing(table, "F", "cobblemon:burn_heal", 60, 1, 3);
            addLootIfMissing(table, "D", "cobblemon:nether_fire_stone_ore", 4, 1, 1);
        }
        if (tableId != null && tableId.equalsIgnoreCase("end")) {
            addLootIfMissing(table, "E", "cobblemon:ultra_ball", 45, 1, 3);
            addLootIfMissing(table, "D", "cobblemon:exp_candy_l", 6, 1, 1);
        }
    }

    private static void addLootIfMissing(LootTable table, String rarity, String itemId, int weight, int minAmount, int maxAmount) {
        if (table == null || itemId == null) return;
        for (LootEntry existing : table.items) {
            if (existing != null && itemId.equalsIgnoreCase(existing.itemId)) return;
        }
        table.items.add(loot(rarity, itemId, weight, minAmount, maxAmount));
    }

    private static LootTable table(int minRolls, int maxRolls, LootEntry... entries) {
        LootTable table = new LootTable();
        table.minRolls = minRolls;
        table.maxRolls = maxRolls;
        table.items = new ArrayList<>(Arrays.asList(entries));
        return table;
    }

    private static LootEntry loot(String rarity, String itemId, int weight, int minAmount, int maxAmount) {
        LootEntry entry = new LootEntry();
        entry.rarity = rarity;
        entry.itemId = itemId;
        entry.weight = weight;
        entry.minAmount = minAmount;
        entry.maxAmount = maxAmount;
        return entry;
    }

    public static final class Data {
        public boolean enabled = true;
        public boolean virtualPerPlayerLoot = true;
        public boolean protectDiscoveredLootStructures = true;
        public int discoveredStructureProtectionRadius = 24;
        public String maxRarity = "D";
        public boolean skipUnknownItems = true;

        /**
         * Any container block in this list opens ChampUtils instanced per-player loot in exploration worlds.
         * This includes Cobblemon's naturally generated gilded chest blocks.
         */
        public List<String> lootContainerBlockIds = new ArrayList<>(List.of(
                "minecraft:chest",
                "minecraft:barrel",
                "cobblemon:gilded_chest",
                "cobblemon:black_gilded_chest",
                "cobblemon:blue_gilded_chest",
                "cobblemon:green_gilded_chest",
                "cobblemon:pink_gilded_chest",
                "cobblemon:white_gilded_chest",
                "cobblemon:yellow_gilded_chest"
        ));

        /**
         * Fallback matcher for modded natural loot containers whose exact registry id may change.
         */
        public List<String> lootContainerIdContains = new ArrayList<>(List.of("gilded_chest"));

        /**
         * Optional per-block loot table override. Example: "cobblemon:gilded_chest": "overworld".
         */
        public Map<String, String> blockTableOverrides = new LinkedHashMap<>();
        public List<String> bannedItemContains = new ArrayList<>(List.of(
                "dynamax", "max_band", "dynamax_band", "mega_bracelet", "mega_charm", "mega_ring", "mega_cuff", "mega_anklet", "keystone", "key_stone"
        ));
        public Map<String, LootTable> tables = new LinkedHashMap<>();

        private Data withDefaults() {
            if (maxRarity == null || maxRarity.isBlank()) maxRarity = "D";
            if (rarityRank(maxRarity) > rarityRank("D")) maxRarity = "D";
            if (discoveredStructureProtectionRadius < 0) discoveredStructureProtectionRadius = 24;
            if (bannedItemContains == null) bannedItemContains = new ArrayList<>(List.of(
                    "dynamax", "max_band", "dynamax_band", "mega_bracelet", "mega_charm", "mega_ring", "mega_cuff", "mega_anklet", "keystone", "key_stone"
            ));

            if (lootContainerBlockIds == null || lootContainerBlockIds.isEmpty()) {
                lootContainerBlockIds = new ArrayList<>(List.of(
                        "minecraft:chest",
                                "minecraft:barrel",
                        "cobblemon:gilded_chest",
                        "cobblemon:black_gilded_chest",
                        "cobblemon:blue_gilded_chest",
                        "cobblemon:green_gilded_chest",
                        "cobblemon:pink_gilded_chest",
                        "cobblemon:white_gilded_chest",
                        "cobblemon:yellow_gilded_chest"
                ));
            }
            if (lootContainerIdContains == null || lootContainerIdContains.isEmpty()) {
                lootContainerIdContains = new ArrayList<>(List.of("gilded_chest"));
            }
            if (blockTableOverrides == null) blockTableOverrides = new LinkedHashMap<>();
            if (tables == null || tables.isEmpty()) tables = defaults().tables;
            tables.values().forEach(LootTable::withDefaults);
            return this;
        }
    }

    public static final class LootTable {
        public int minRolls = 4;
        public int maxRolls = 8;
        public List<LootEntry> items = new ArrayList<>();

        private void withDefaults() {
            if (minRolls < 1) minRolls = 1;
            if (maxRolls < minRolls) maxRolls = minRolls;
            if (items == null) items = new ArrayList<>();
        }
    }

    public static final class LootEntry {
        public String rarity = "F";
        public String itemId;
        public int weight = 1;
        public int minAmount = 1;
        public int maxAmount = 1;

        public int rarityRank() {
            if (rarity == null) return 0;
            return switch (rarity.toUpperCase(Locale.ROOT)) {
                case "E" -> 1;
                case "D" -> 2;
                case "C" -> 3;
                case "B" -> 4;
                case "A" -> 5;
                case "S" -> 6;
                default -> 0;
            };
        }
    }

    public static int rarityRank(String rarity) {
        if (rarity == null) return 3;
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "F" -> 0;
            case "E" -> 1;
            case "D" -> 2;
            case "C" -> 3;
            case "B" -> 4;
            case "A" -> 5;
            case "S" -> 6;
            default -> 3;
        };
    }
}
