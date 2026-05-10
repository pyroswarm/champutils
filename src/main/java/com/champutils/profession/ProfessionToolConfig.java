package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProfessionToolConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static Map<String, ToolData> TOOLS =
            new LinkedHashMap<>();

    public static Map<String, Long> RARITY_COSTS =
            new LinkedHashMap<>();

    public static double REROLL_COST_MULTIPLIER =
            2.0D;

    public static Map<String, Map<String, EnchantData>> ENCHANTING =
            new LinkedHashMap<>();

    public static class ConfigRoot {

        public Map<String, ToolData> tools =
                new LinkedHashMap<>();

        public Map<String, Long> rarityCosts =
                new LinkedHashMap<>();

        public double rerollCostMultiplier =
                2.0D;

        /**
         * Deprecated. Custom tool enchanting has been removed.
         * Kept transient so old configs can contain the field without
         * ChampUtils writing it back into newly generated configs.
         */
        public transient Map<String, Map<String, EnchantData>> enchanting =
                new LinkedHashMap<>();
    }

    public static class ToolData {

        public String profession;
        public int requiredLevel;

        public String displayName;
        public String rarity;
        public String baseItem;
        public int customModelData;

        /*
         Mining/harvest tier for custom tools.
         Supported values: WOOD, STONE, IRON, DIAMOND, NETHERITE.
         If omitted, ChampUtils derives the tier from baseItem.
         */
        public String toolTier = "";

        /*
         Custom durability. The item will never vanish when this reaches 0.
         Instead, ChampUtils marks it as broken and blocks use until repaired.
         If omitted or <= 0, ChampUtils uses the vanilla max durability of baseItem.
         durabilityBonus rolled stats are treated as a percent bonus to this value.
         */
        public int baseDurability = 0;

        /*
         Materials consumed by /itemroll repair.
         Example: { "minecraft:iron_ingot": 4 }
         */
        public Map<String, Integer> repairMaterials =
                new LinkedHashMap<>();

        /*
         Percent of max durability restored per repair.
         100 = full repair, 25 = restore 25% of max each repair.
         */
        public double repairDurabilityPercent = 100.0D;

        /*
         If true, this item can be created as an ascended/stat-tracker variant.
         Normal and ascended copies use the same stats, rolls, passives, and active ability.
         The ascended variant only adds glint + tracker lore.
         */
        public boolean hasAscendedVariant = false;

        /*
         Old fixed-stat support.
         Kept so older configs/tools do not instantly break while migrating to statRanges.
         */
        public Map<String, Double> stats =
                new LinkedHashMap<>();

        /*
         Wynncraft-style roll ranges.
         */
        public Map<String, StatRange> statRanges =
                new LinkedHashMap<>();

        public List<String> passives =
                new ArrayList<>();

        public String activeAbility;
        public int activeCooldownSeconds = 30;

        /*
         Optional duration for active abilities that use timed effects.
         0 or omitted = the ability uses its own default duration.
         Example: excavation uses this for how long 3x3 mining stays active.
         */
        public int activeDurationSeconds = 0;

        /*
         Maximum extra connected ore blocks mined by vein_miner_burst.
         This is intentionally capped for server safety.
         */
        public int maxVeinBlocks = 30;

        /*
         Optional scan radius for treasure_sense.
         0 or omitted = the ability uses its own default radius.
         */
        public int treasureSenseRadius = 0;

        /*
         Optional scan radius for nature_sense.
         0 or omitted = the ability uses its own default radius.
         */
        public int natureSenseRadius = 0;

        /*
         Maximum connected logs felled by timber_burst.
         */
        public int maxTimberBlocks = 32;

        /*
         Radius around the chopped log cleared by leafstorm.
         */
        public int leafstormRadius = 5;
    }


    public static class EnchantData {

        public String displayName = "";
        public int maxLevel = 1;

        /*
         Materials consumed each time this enchant is upgraded by 1 level.
         Example: { "minecraft:lapis_lazuli": 16 }
         */
        public Map<String, Integer> cost =
                new LinkedHashMap<>();

        /*
         Optional multiplier applied to the material cost based on the NEXT level.
         1.0 = flat cost every level.
         2.0 = level 1 costs 1x, level 2 costs 2x, level 3 costs 4x, etc.
         */
        public double costMultiplierPerLevel = 1.0D;

        /*
         Used by efficiency. Each custom efficiency level adds this much virtual
         miningSpeed percent to the held tool.
         */
        public double statBonusPerLevel = 50.0D;
    }

    public static class StatRange {

        public double min;
        public double max;
        public double weight = 1.0D;

        public StatRange() {
        }

        public StatRange(
                double min,
                double max,
                double weight
        ) {
            this.min = min;
            this.max = max;
            this.weight = weight;
        }
    }

    public static void load() {
        try {

            File dir =
                    new File("config/champutils");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file =
                    new File(
                            dir,
                            "profession_tools.json"
                    );

            if (!file.exists()) {
                createDefault(file);
            }

            loadFile(file);

            if (TOOLS == null) {
                TOOLS =
                        new LinkedHashMap<>();
            }

            if (RARITY_COSTS == null || RARITY_COSTS.isEmpty()) {
                RARITY_COSTS =
                        defaultRarityCosts();
            }

            ENCHANTING =
                    new LinkedHashMap<>();

            System.out.println(
                    "[ChampUtils] Loaded " +
                            TOOLS.size() +
                            " profession tools."
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadFile(
            File file
    ) throws Exception {

        try (
                FileReader reader =
                        new FileReader(file)
        ) {

            JsonObject root =
                    JsonParser.parseReader(reader)
                            .getAsJsonObject();

            /*
             New format:
             {
               "rarityCosts": {},
               "rerollCostMultiplier": 2.0,
               "tools": {}
             }
             */
            if (root.has("tools")) {

                ConfigRoot config =
                        GSON.fromJson(
                                root,
                                ConfigRoot.class
                        );

                TOOLS =
                        config.tools == null
                                ? new LinkedHashMap<>()
                                : config.tools;

                RARITY_COSTS =
                        config.rarityCosts == null ||
                                config.rarityCosts.isEmpty()
                                ? defaultRarityCosts()
                                : config.rarityCosts;

                REROLL_COST_MULTIPLIER =
                        config.rerollCostMultiplier <= 0
                                ? 2.0D
                                : config.rerollCostMultiplier;

                ENCHANTING =
                        new LinkedHashMap<>();

                return;
            }

            /*
             Old format:
             {
               "miners_fang": {}
             }
             */
            Type oldType =
                    new TypeToken<
                            Map<String, ToolData>
                            >() {
                    }.getType();

            TOOLS =
                    GSON.fromJson(
                            root,
                            oldType
                    );

            RARITY_COSTS =
                    defaultRarityCosts();

            REROLL_COST_MULTIPLIER =
                    2.0D;

            ENCHANTING =
                    new LinkedHashMap<>();
        }
    }

    private static void createDefault(
            File file
    ) {
        try {

            ConfigRoot root =
                    new ConfigRoot();

            root.rarityCosts =
                    defaultRarityCosts();

            root.rerollCostMultiplier =
                    2.0D;

            root.tools.put(
                    "miners_fang",
                    createTool(
                            "MINING",
                            25,
                            "Miner's Fang",
                            "RARE",
                            "minecraft:diamond_pickaxe",
                            1001,
                            true,
                            Map.of(
                                    "miningSpeed",
                                    new StatRange(
                                            10.0D,
                                            25.0D,
                                            2.0D
                                    ),
                                    "fortuneBonus",
                                    new StatRange(
                                            2.0D,
                                            8.0D,
                                            3.0D
                                    ),
                                    "durabilityBonus",
                                    new StatRange(
                                            50.0D,
                                            150.0D,
                                            1.0D
                                    )
                            ),
                            List.of(
                                    "bonus_ore_drops"
                            ),
                            "prospect",
                            30
                    )
            );

            root.tools.put(
                    "titanbreaker",
                    createTool(
                            "MINING",
                            100,
                            "Titanbreaker",
                            "LEGENDARY",
                            "minecraft:netherite_pickaxe",
                            1002,
                            true,
                            Map.of(
                                    "miningSpeed",
                                    new StatRange(
                                            25.0D,
                                            60.0D,
                                            2.0D
                                    ),
                                    "fortuneBonus",
                                    new StatRange(
                                            8.0D,
                                            25.0D,
                                            3.0D
                                    ),
                                    "durabilityBonus",
                                    new StatRange(
                                            200.0D,
                                            600.0D,
                                            1.0D
                                    )
                            ),
                            List.of(
                                    "vein_mining",
                                    "bonus_ore_drops"
                            ),
                            "vein_burst",
                            60
                    )
            );

            root.tools.put(
                    "woodcleaver",
                    createTool(
                            "FORESTRY",
                            25,
                            "Woodcleaver",
                            "RARE",
                            "minecraft:diamond_axe",
                            2001,
                            false,
                            Map.of(
                                    "chopSpeed",
                                    new StatRange(
                                            10.0D,
                                            25.0D,
                                            2.0D
                                    ),
                                    "bonusLogs",
                                    new StatRange(
                                            2.0D,
                                            8.0D,
                                            3.0D
                                    )
                            ),
                            List.of(
                                    "faster_tree_chopping"
                            ),
                            null,
                            30
                    )
            );

            root.tools.put(
                    "worldtree_axe",
                    createTool(
                            "FORESTRY",
                            100,
                            "Worldtree Axe",
                            "LEGENDARY",
                            "minecraft:netherite_axe",
                            2002,
                            false,
                            Map.of(
                                    "chopSpeed",
                                    new StatRange(
                                            25.0D,
                                            60.0D,
                                            2.0D
                                    ),
                                    "bonusLogs",
                                    new StatRange(
                                            8.0D,
                                            25.0D,
                                            3.0D
                                    )
                            ),
                            List.of(
                                    "timber_break"
                            ),
                            null,
                            60
                    )
            );

            root.tools.put(
                    "gaias_blessing",
                    createTool(
                            "FARMING",
                            100,
                            "Gaia's Blessing",
                            "LEGENDARY",
                            "minecraft:diamond_hoe",
                            4001,
                            false,
                            Map.of(
                                    "bonusCropYield",
                                    new StatRange(
                                            8.0D,
                                            25.0D,
                                            3.0D
                                    )
                            ),
                            List.of(
                                    "auto_replant"
                            ),
                            null,
                            60
                    )
            );

            try (
                    FileWriter writer =
                            new FileWriter(file)
            ) {
                GSON.toJson(
                        root,
                        writer
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ToolData createTool(
            String profession,
            int requiredLevel,
            String displayName,
            String rarity,
            String baseItem,
            int customModelData,
            boolean hasAscendedVariant,
            Map<String, StatRange> statRanges,
            List<String> passives,
            String activeAbility,
            int activeCooldownSeconds
    ) {

        ToolData tool =
                new ToolData();

        tool.profession =
                profession;

        tool.requiredLevel =
                requiredLevel;

        tool.displayName =
                displayName;

        tool.rarity =
                rarity;

        tool.baseItem =
                baseItem;

        tool.customModelData =
                customModelData;

        tool.hasAscendedVariant =
                hasAscendedVariant;

        tool.statRanges =
                statRanges == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(statRanges);

        tool.passives =
                passives == null
                        ? new ArrayList<>()
                        : new ArrayList<>(passives);

        tool.activeAbility =
                activeAbility;

        tool.activeCooldownSeconds =
                activeCooldownSeconds;

        tool.baseDurability =
                0;

        tool.repairDurabilityPercent =
                100.0D;

        tool.repairMaterials =
                defaultRepairMaterials(baseItem);

        return tool;
    }

    private static Map<String, Integer> defaultRepairMaterials(
            String baseItem
    ) {

        Map<String, Integer> materials =
                new LinkedHashMap<>();

        String base =
                baseItem == null
                        ? ""
                        : baseItem.toLowerCase();

        if (base.contains("netherite")) {
            materials.put(
                    "minecraft:netherite_ingot",
                    1
            );
            materials.put(
                    "minecraft:diamond",
                    2
            );
            return materials;
        }

        if (base.contains("diamond")) {
            materials.put(
                    "minecraft:diamond",
                    3
            );
            return materials;
        }

        if (base.contains("iron")) {
            materials.put(
                    "minecraft:iron_ingot",
                    3
            );
            return materials;
        }

        if (base.contains("stone")) {
            materials.put(
                    "minecraft:cobblestone",
                    3
            );
            return materials;
        }

        materials.put(
                "minecraft:iron_ingot",
                1
        );

        return materials;
    }

    private static Map<String, Long> defaultRarityCosts() {

        Map<String, Long> costs =
                new LinkedHashMap<>();

        costs.put(
                "COMMON",
                100L
        );

        costs.put(
                "UNCOMMON",
                300L
        );

        costs.put(
                "RARE",
                750L
        );

        costs.put(
                "EPIC",
                2500L
        );

        costs.put(
                "LEGENDARY",
                10000L
        );

        costs.put(
                "MYTHIC",
                50000L
        );

        return costs;
    }


    private static Map<String, Map<String, EnchantData>> defaultEnchanting() {
        return new LinkedHashMap<>();
    }


    private static EnchantData createEnchant(
            String displayName,
            int maxLevel,
            double statBonusPerLevel,
            double costMultiplierPerLevel,
            Map<String, Integer> cost
    ) {

        EnchantData data =
                new EnchantData();

        data.displayName =
                displayName;

        data.maxLevel =
                Math.max(
                        1,
                        maxLevel
                );

        data.statBonusPerLevel =
                Math.max(
                        0.0D,
                        statBonusPerLevel
                );

        data.costMultiplierPerLevel =
                costMultiplierPerLevel <= 0.0D
                        ? 1.0D
                        : costMultiplierPerLevel;

        data.cost =
                cost == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(cost);

        return data;
    }

    private static Map<String, Map<String, EnchantData>> cleanEnchanting(
            Map<String, Map<String, EnchantData>> input
    ) {

        Map<String, Map<String, EnchantData>> clean =
                new LinkedHashMap<>();

        if (input == null) {
            return clean;
        }

        for (Map.Entry<String, Map<String, EnchantData>> professionEntry : input.entrySet()) {
            if (
                    professionEntry.getKey() == null ||
                            professionEntry.getKey().isBlank() ||
                            professionEntry.getValue() == null
            ) {
                continue;
            }

            Map<String, EnchantData> enchants =
                    new LinkedHashMap<>();

            for (Map.Entry<String, EnchantData> enchantEntry : professionEntry.getValue().entrySet()) {
                if (
                        enchantEntry.getKey() == null ||
                                enchantEntry.getKey().isBlank() ||
                                enchantEntry.getValue() == null
                ) {
                    continue;
                }

                String enchantId =
                        enchantEntry.getKey()
                                .trim()
                                .toLowerCase();

                /*
                 Fortune is intentionally blocked for custom tool enchanting.
                 Keep fortuneBonus as a rolled item stat only.
                 */
                if ("fortune".equals(enchantId)) {
                    continue;
                }

                EnchantData data =
                        enchantEntry.getValue();

                if (data.displayName == null || data.displayName.isBlank()) {
                    data.displayName =
                            formatEnchantName(
                                    enchantId
                            );
                }

                data.maxLevel =
                        Math.max(
                                1,
                                data.maxLevel
                        );

                data.costMultiplierPerLevel =
                        data.costMultiplierPerLevel <= 0.0D
                                ? 1.0D
                                : data.costMultiplierPerLevel;

                if (data.cost == null) {
                    data.cost =
                            new LinkedHashMap<>();
                }

                enchants.put(
                        enchantId,
                        data
                );
            }

            clean.put(
                    professionEntry.getKey()
                            .trim()
                            .toUpperCase(),
                    enchants
            );
        }

        return clean;
    }

    public static Map<String, EnchantData> getAllowedEnchantments(
            String profession
    ) {

        if (profession == null || profession.isBlank()) {
            return new LinkedHashMap<>();
        }

        Map<String, EnchantData> enchants =
                ENCHANTING.get(
                        profession.trim().toUpperCase()
                );

        return enchants == null
                ? new LinkedHashMap<>()
                : enchants;
    }

    public static EnchantData getEnchantData(
            String profession,
            String enchantId
    ) {

        if (enchantId == null || enchantId.isBlank()) {
            return null;
        }

        return getAllowedEnchantments(
                profession
        ).get(
                enchantId.trim().toLowerCase()
        );
    }

    public static String formatEnchantName(
            String enchantId
    ) {

        if (enchantId == null || enchantId.isBlank()) {
            return "Unknown";
        }

        String[] parts =
                enchantId.trim()
                        .toLowerCase()
                        .split("_");

        StringBuilder result =
                new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(" ");
            }

            result.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            if (part.length() > 1) {
                result.append(
                        part.substring(1)
                );
            }
        }

        return result.isEmpty()
                ? enchantId
                : result.toString();
    }

    public static long getBaseRollCost(
            ToolData toolData
    ) {

        if (
                toolData == null ||
                        toolData.rarity == null
        ) {
            return 0L;
        }

        return RARITY_COSTS.getOrDefault(
                toolData.rarity.toUpperCase(),
                0L
        );
    }

    public static long getRerollCost(
            ToolData toolData,
            int rerolls
    ) {

        long base =
                getBaseRollCost(
                        toolData
                );

        if (base <= 0) {
            return 0L;
        }

        int safeRerolls =
                Math.max(
                        0,
                        rerolls
                );

        double cost =
                base *
                        Math.pow(
                                REROLL_COST_MULTIPLIER,
                                safeRerolls
                        );

        return Math.min(
                Long.MAX_VALUE,
                Math.round(
                        cost
                )
        );
    }

    public static String getDisplayName(
            String toolId,
            ToolData toolData
    ) {

        if (
                toolData != null &&
                        toolData.displayName != null &&
                        !toolData.displayName.isBlank()
        ) {
            return toolData.displayName;
        }

        return formatWords(
                toolId
        );
    }

    private static String formatWords(
            String value
    ) {

        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized =
                value.replace("_", " ")
                        .replace("-", " ")
                        .trim();

        String[] parts =
                normalized.split("\\s+");

        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {

            if (part.isBlank()) {
                continue;
            }

            builder.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            if (part.length() > 1) {
                builder.append(
                        part.substring(1)
                                .toLowerCase()
                );
            }

            builder.append(" ");
        }

        return builder
                .toString()
                .trim()
                .replaceAll("\\bXp\\b", "XP");
    }
}
