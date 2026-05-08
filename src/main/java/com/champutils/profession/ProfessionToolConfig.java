package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import java.util.List;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    public static class ConfigRoot {

        public Map<String, ToolData> tools =
                new LinkedHashMap<>();

        public Map<String, Long> rarityCosts =
                new LinkedHashMap<>();

        public double rerollCostMultiplier =
                2.0D;
    }

    public static class ToolData {

        public String profession;
        public int requiredLevel;

        public String displayName;
        public String rarity;
        public String baseItem;
        public int customModelData;
        public List<String> passives;

        /*
         Old fixed-stat support.
         Keep this for compatibility while we migrate.
         */
        public Map<String, Double> stats =
                new LinkedHashMap<>();

        /*
         New Wynncraft-style roll ranges.
         */
        public Map<String, StatRange> statRanges =
                new LinkedHashMap<>();

        public String activeAbility;
        public int activeCooldownSeconds = 30;
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
                            Map.of(
                                    "bonusCropYield",
                                    new StatRange(
                                            8.0D,
                                            25.0D,
                                            3.0D
                                    )
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
            Map<String, StatRange> statRanges,
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

        tool.statRanges =
                statRanges == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(statRanges);

        tool.activeAbility =
                activeAbility;

        tool.activeCooldownSeconds =
                activeCooldownSeconds;

        return tool;
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
                .trim();
    }
}