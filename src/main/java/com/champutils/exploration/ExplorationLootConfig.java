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
        root.maxRarity = "S";
        root.tables.put("overworld", table(2, 4,
                loot("F", "minecraft:coal", 95, 2, 5),
                loot("F", "minecraft:raw_copper", 90, 2, 5),
                loot("F", "minecraft:raw_iron", 55, 1, 3),
                loot("F", "cobblemon:poke_ball", 110, 2, 4),
                loot("F", "cobblemon:premier_ball", 45, 1, 2),
                loot("F", "cobblemon:heal_ball", 25, 1, 2),

                loot("E", "minecraft:raw_copper", 70, 4, 8),
                loot("E", "minecraft:raw_iron", 65, 2, 5),
                loot("E", "minecraft:redstone", 45, 3, 8),
                loot("E", "cobblemon:poke_ball", 65, 3, 6),
                loot("E", "cobblemon:great_ball", 60, 1, 3),
                loot("E", "cobblemon:nest_ball", 25, 1, 2),
                loot("E", "cobblemon:net_ball", 25, 1, 2),
                loot("E", "cobblemon:dive_ball", 20, 1, 2),

                loot("D", "minecraft:raw_iron", 70, 4, 8),
                loot("D", "minecraft:raw_gold", 55, 2, 5),
                loot("D", "minecraft:lapis_lazuli", 50, 3, 8),
                loot("D", "minecraft:redstone", 60, 4, 10),
                loot("D", "minecraft:emerald", 22, 1, 2),
                loot("D", "cobblemon:great_ball", 70, 2, 5),
                loot("D", "cobblemon:lure_ball", 35, 1, 3),
                loot("D", "cobblemon:level_ball", 35, 1, 3),
                loot("D", "cobblemon:heavy_ball", 30, 1, 3),
                loot("D", "cobblemon:dusk_ball", 22, 1, 2),

                loot("C", "minecraft:raw_gold", 65, 4, 8),
                loot("C", "minecraft:lapis_lazuli", 55, 6, 12),
                loot("C", "minecraft:redstone", 55, 8, 14),
                loot("C", "minecraft:diamond", 25, 1, 2),
                loot("C", "minecraft:emerald", 35, 1, 3),
                loot("C", "cobblemon:ultra_ball", 60, 1, 3),
                loot("C", "cobblemon:timer_ball", 35, 1, 3),
                loot("C", "cobblemon:repeat_ball", 35, 1, 3),
                loot("C", "cobblemon:luxury_ball", 30, 1, 2),
                loot("C", "cobblemon:moon_ball", 22, 1, 2),

                loot("B", "minecraft:diamond", 45, 1, 3),
                loot("B", "minecraft:emerald", 45, 2, 4),
                loot("B", "minecraft:raw_gold", 45, 6, 10),
                loot("B", "cobblemon:ultra_ball", 75, 2, 5),
                loot("B", "cobblemon:timer_ball", 45, 2, 4),
                loot("B", "cobblemon:repeat_ball", 45, 2, 4),
                loot("B", "cobblemon:luxury_ball", 35, 1, 3),
                loot("B", "cobblemon:dusk_ball", 30, 1, 3),

                loot("A", "minecraft:diamond", 55, 2, 4),
                loot("A", "minecraft:emerald", 55, 3, 6),
                loot("A", "minecraft:raw_gold", 45, 8, 12),
                loot("A", "cobblemon:ultra_ball", 75, 3, 6),
                loot("A", "cobblemon:quick_ball", 12, 1, 2),
                loot("A", "cobblemon:dream_ball", 4, 1, 1),

                loot("S", "minecraft:diamond", 70, 3, 6),
                loot("S", "minecraft:emerald", 60, 4, 8),
                loot("S", "cobblemon:ultra_ball", 80, 4, 8),
                loot("S", "cobblemon:quick_ball", 16, 1, 3),
                loot("S", "cobblemon:dream_ball", 6, 1, 1),
                loot("S", "cobblemon:master_ball", 2, 1, 1)
        ));
        root.tables.put("nether", table(2, 4,
                loot("F", "minecraft:quartz", 95, 4, 10),
                loot("F", "minecraft:gold_nugget", 90, 6, 14),
                loot("F", "minecraft:coal", 50, 2, 5),
                loot("F", "cobblemon:poke_ball", 95, 2, 4),
                loot("F", "cobblemon:premier_ball", 35, 1, 2),

                loot("E", "minecraft:quartz", 80, 8, 16),
                loot("E", "minecraft:gold_nugget", 70, 10, 20),
                loot("E", "minecraft:raw_iron", 40, 2, 4),
                loot("E", "cobblemon:great_ball", 60, 1, 3),
                loot("E", "cobblemon:heal_ball", 35, 1, 3),
                loot("E", "cobblemon:nest_ball", 25, 1, 2),

                loot("D", "minecraft:gold_ingot", 60, 2, 5),
                loot("D", "minecraft:quartz", 65, 12, 24),
                loot("D", "minecraft:raw_gold", 45, 2, 5),
                loot("D", "minecraft:blaze_rod", 18, 1, 2),
                loot("D", "cobblemon:great_ball", 70, 2, 5),
                loot("D", "cobblemon:dusk_ball", 45, 1, 3),
                loot("D", "cobblemon:heavy_ball", 30, 1, 3),

                loot("C", "minecraft:raw_gold", 65, 4, 8),
                loot("C", "minecraft:gold_ingot", 55, 4, 8),
                loot("C", "minecraft:diamond", 20, 1, 2),
                loot("C", "minecraft:blaze_rod", 25, 2, 4),
                loot("C", "cobblemon:ultra_ball", 65, 1, 3),
                loot("C", "cobblemon:dusk_ball", 45, 2, 4),
                loot("C", "cobblemon:timer_ball", 35, 1, 3),

                loot("B", "minecraft:diamond", 40, 1, 3),
                loot("B", "minecraft:emerald", 35, 1, 3),
                loot("B", "minecraft:gold_ingot", 55, 6, 10),
                loot("B", "cobblemon:ultra_ball", 75, 2, 5),
                loot("B", "cobblemon:timer_ball", 45, 2, 4),
                loot("B", "cobblemon:repeat_ball", 35, 1, 3),

                loot("A", "minecraft:diamond", 55, 2, 4),
                loot("A", "minecraft:emerald", 45, 2, 5),
                loot("A", "cobblemon:ultra_ball", 75, 3, 6),
                loot("A", "cobblemon:quick_ball", 12, 1, 2),
                loot("A", "cobblemon:dream_ball", 4, 1, 1),

                loot("S", "minecraft:diamond", 70, 3, 6),
                loot("S", "minecraft:emerald", 55, 3, 7),
                loot("S", "cobblemon:ultra_ball", 80, 4, 8),
                loot("S", "cobblemon:quick_ball", 16, 1, 3),
                loot("S", "cobblemon:dream_ball", 6, 1, 1),
                loot("S", "cobblemon:master_ball", 2, 1, 1)
        ));
        root.tables.put("end", table(3, 5,
                loot("F", "minecraft:ender_pearl", 90, 2, 6),
                loot("F", "minecraft:chorus_fruit", 85, 4, 10),
                loot("F", "cobblemon:poke_ball", 85, 2, 4),
                loot("F", "cobblemon:premier_ball", 35, 1, 2),

                loot("E", "minecraft:ender_pearl", 85, 4, 10),
                loot("E", "minecraft:chorus_fruit", 80, 8, 16),
                loot("E", "minecraft:raw_iron", 45, 2, 5),
                loot("E", "cobblemon:great_ball", 65, 1, 3),
                loot("E", "cobblemon:nest_ball", 25, 1, 2),

                loot("D", "minecraft:lapis_lazuli", 55, 4, 10),
                loot("D", "minecraft:redstone", 55, 4, 10),
                loot("D", "minecraft:emerald", 25, 1, 2),
                loot("D", "cobblemon:great_ball", 70, 2, 5),
                loot("D", "cobblemon:moon_ball", 35, 1, 3),
                loot("D", "cobblemon:dusk_ball", 35, 1, 3),

                loot("C", "minecraft:diamond", 30, 1, 2),
                loot("C", "minecraft:emerald", 35, 1, 3),
                loot("C", "minecraft:lapis_lazuli", 60, 8, 14),
                loot("C", "cobblemon:ultra_ball", 65, 1, 4),
                loot("C", "cobblemon:dusk_ball", 50, 2, 4),
                loot("C", "cobblemon:timer_ball", 35, 1, 3),

                loot("B", "minecraft:diamond", 50, 1, 3),
                loot("B", "minecraft:emerald", 45, 2, 4),
                loot("B", "cobblemon:ultra_ball", 80, 2, 5),
                loot("B", "cobblemon:timer_ball", 45, 2, 4),
                loot("B", "cobblemon:repeat_ball", 35, 1, 3),
                loot("B", "cobblemon:luxury_ball", 35, 1, 3),

                loot("A", "minecraft:diamond", 60, 2, 5),
                loot("A", "minecraft:emerald", 55, 3, 6),
                loot("A", "cobblemon:ultra_ball", 80, 3, 6),
                loot("A", "cobblemon:quick_ball", 12, 1, 2),
                loot("A", "cobblemon:dream_ball", 4, 1, 1),

                loot("S", "minecraft:diamond", 75, 3, 7),
                loot("S", "minecraft:emerald", 65, 4, 8),
                loot("S", "cobblemon:ultra_ball", 85, 4, 8),
                loot("S", "cobblemon:quick_ball", 16, 1, 3),
                loot("S", "cobblemon:dream_ball", 6, 1, 1),
                loot("S", "cobblemon:master_ball", 2, 1, 1)
        ));
        return root;
    }


    private static void applyBetaBalance(Data d) {
        if (d == null) return;
        if (d.maxRarity == null || d.maxRarity.isBlank()) d.maxRarity = "S";
        if (rarityRank(d.maxRarity) > rarityRank("S")) d.maxRarity = "S";
        if (d.bannedItemContains == null) d.bannedItemContains = new ArrayList<>();

        // Keep unsupported or economy-breaking special systems out of exploration loot.
        for (String banned : List.of(
                "dynamax", "max_band", "dynamax_band",
                "mega_bracelet", "mega_charm", "mega_ring", "mega_cuff", "mega_anklet",
                "keystone", "key_stone", "tera_orb", "adamant_crystal", "lustrous_globe",
                "griseous_core", "elytra", "netherite_block", "netherite_scrap")) {
            if (!d.bannedItemContains.contains(banned)) d.bannedItemContains.add(banned);
        }
        // S-rank exploration chests intentionally have a very low master ball chance.
        d.bannedItemContains.removeIf(value -> value != null && value.equalsIgnoreCase("master_ball"));

        if (d.tables != null) {
            for (Map.Entry<String, LootTable> tableEntry : d.tables.entrySet()) {
                LootTable table = tableEntry.getValue();
                if (table == null) continue;
                table.minRolls = Math.max(1, Math.min(table.minRolls, 6));
                table.maxRolls = Math.max(table.minRolls, Math.min(table.maxRolls, 8));
                if (table.items == null) table.items = new ArrayList<>();
                addBalancedLootIfMissing(table, tableEntry.getKey());
                for (LootEntry entry : table.items) {
                    if (entry == null) continue;
                    entry.weight = Math.max(0, Math.min(entry.weight, 250));
                    entry.minAmount = Math.max(1, entry.minAmount);
                    entry.maxAmount = Math.min(Math.max(entry.minAmount, entry.maxAmount), 64);
                    if (entry.itemId != null && entry.itemId.contains("master_ball")) {
                        entry.rarity = "S";
                        entry.minAmount = 1;
                        entry.maxAmount = 1;
                        entry.weight = Math.min(entry.weight, 2);
                    }
                    if (entry.itemId != null && entry.itemId.contains("dream_ball")) {
                        entry.rarity = entry.rarityRank() >= rarityRank("A") ? entry.rarity : "A";
                        entry.maxAmount = Math.min(entry.maxAmount, 1);
                    }
                    if (entry.itemId != null && entry.itemId.contains("quick_ball")) {
                        entry.rarity = entry.rarityRank() >= rarityRank("A") ? entry.rarity : "A";
                        entry.maxAmount = Math.min(entry.maxAmount, 3);
                    }
                }
            }
        }
    }

    private static void addBalancedLootIfMissing(LootTable table, String tableId) {
        String id = tableId == null ? "overworld" : tableId.toLowerCase(Locale.ROOT);
        if ("nether".equals(id)) {
            addLootIfMissing(table, "F", "minecraft:quartz", 95, 4, 10);
            addLootIfMissing(table, "F", "minecraft:gold_nugget", 90, 6, 14);
            addLootIfMissing(table, "F", "cobblemon:poke_ball", 95, 2, 4);
            addLootIfMissing(table, "E", "cobblemon:great_ball", 60, 1, 3);
            addLootIfMissing(table, "D", "minecraft:gold_ingot", 60, 2, 5);
            addLootIfMissing(table, "C", "cobblemon:ultra_ball", 65, 1, 3);
            addLootIfMissing(table, "A", "cobblemon:quick_ball", 12, 1, 2);
            addLootIfMissing(table, "A", "cobblemon:dream_ball", 4, 1, 1);
            addLootIfMissing(table, "S", "cobblemon:master_ball", 2, 1, 1);
            return;
        }
        if ("end".equals(id)) {
            addLootIfMissing(table, "F", "minecraft:ender_pearl", 90, 2, 6);
            addLootIfMissing(table, "F", "minecraft:chorus_fruit", 85, 4, 10);
            addLootIfMissing(table, "F", "cobblemon:poke_ball", 85, 2, 4);
            addLootIfMissing(table, "E", "cobblemon:great_ball", 65, 1, 3);
            addLootIfMissing(table, "D", "minecraft:lapis_lazuli", 55, 4, 10);
            addLootIfMissing(table, "C", "cobblemon:ultra_ball", 65, 1, 4);
            addLootIfMissing(table, "A", "cobblemon:quick_ball", 12, 1, 2);
            addLootIfMissing(table, "A", "cobblemon:dream_ball", 4, 1, 1);
            addLootIfMissing(table, "S", "cobblemon:master_ball", 2, 1, 1);
            return;
        }
        addLootIfMissing(table, "F", "minecraft:coal", 95, 2, 5);
        addLootIfMissing(table, "F", "minecraft:raw_copper", 90, 2, 5);
        addLootIfMissing(table, "F", "minecraft:raw_iron", 55, 1, 3);
        addLootIfMissing(table, "F", "cobblemon:poke_ball", 110, 2, 4);
        addLootIfMissing(table, "F", "cobblemon:premier_ball", 45, 1, 2);
        addLootIfMissing(table, "E", "cobblemon:great_ball", 60, 1, 3);
        addLootIfMissing(table, "D", "minecraft:raw_gold", 55, 2, 5);
        addLootIfMissing(table, "C", "cobblemon:ultra_ball", 60, 1, 3);
        addLootIfMissing(table, "A", "cobblemon:quick_ball", 12, 1, 2);
        addLootIfMissing(table, "A", "cobblemon:dream_ball", 4, 1, 1);
        addLootIfMissing(table, "S", "cobblemon:master_ball", 2, 1, 1);
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
        public String maxRarity = "S";
        public boolean skipUnknownItems = true;

        /**
         * Any container block in this list opens ChampUtils instanced per-player loot in exploration worlds.
         * This includes Cobblemon's naturally generated gilded chest blocks.
         */
        public List<String> lootContainerBlockIds = new ArrayList<>(List.of(
                "minecraft:chest",
                "minecraft:trapped_chest",
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
            if (maxRarity == null || maxRarity.isBlank()) maxRarity = "S";
            if (rarityRank(maxRarity) > rarityRank("S")) maxRarity = "S";
            if (discoveredStructureProtectionRadius < 0) discoveredStructureProtectionRadius = 24;
            if (bannedItemContains == null) bannedItemContains = new ArrayList<>(List.of(
                    "dynamax", "max_band", "dynamax_band", "mega_bracelet", "mega_charm", "mega_ring", "mega_cuff", "mega_anklet", "keystone", "key_stone"
            ));

            if (lootContainerBlockIds == null || lootContainerBlockIds.isEmpty()) {
                lootContainerBlockIds = new ArrayList<>(List.of(
                        "minecraft:chest",
                        "minecraft:trapped_chest",
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
