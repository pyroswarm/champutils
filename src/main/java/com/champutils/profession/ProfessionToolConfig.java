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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    public static double ASCENDED_UNIDENTIFIED_CHANCE_PERCENT =
            1.0D;

    public static double SPEED_PERCENT_PER_VIRTUAL_EFFICIENCY_LEVEL =
            40.0D;

    public static double MAX_VIRTUAL_EFFICIENCY_LEVEL =
            5.0D;

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
         * Percent chance for newly-created legendary or mythic unidentified
         * tools to become ascended. 1.0 = 1%. Set to 0 to disable.
         */
        public double ascendedUnidentifiedChancePercent =
                1.0D;

        /**
         * Mining/chopping/digging speed scaling. The displayed stat stays as a
         * percent, but it is converted into a vanilla Efficiency-style bonus.
         * 40 = every 40% speed is one virtual Efficiency level.
         */
        public double speedPercentPerVirtualEfficiencyLevel = 40.0D;

        /** Maximum virtual Efficiency level used by custom speed stats. */
        public double maxVirtualEfficiencyLevel = 5.0D;

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

        /*
         Optional per-tool active pool. If omitted, ChampUtils rolls from every
         active ability used by the same base tool family and never crosses
         families. Example: pickaxes only roll pickaxe actives.
         */
        public List<String> activeAbilityPool =
                new ArrayList<>();

        public int activeCooldownSeconds = 30;

        /*
         Optional duration for active abilities that use timed effects.
         0 or omitted = the ability uses its own default duration.
         Example: excavation uses this for how long 3x3 mining stays active.
         */
        public int activeDurationSeconds = 0;

        /*
         Optional extra seconds added per profession level for timed active abilities.
         Example: timber_burst with activeDurationSeconds=10 and activeDurationSecondsPerLevel=0.1
         lasts 10.0s at level 1, 10.1s at level 2, etc.
         */
        public double activeDurationSecondsPerLevel = 0.0D;

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

            ensureDefaultShovelTools();
            ensureDefaultSwordTools();
            applyCobbleChampsProfessionToolRework();
            ensureDefaultBRankTools();
            normalizeToolDisplayNames();
            buffMeaningfulActivesExceptReplant();

            ENCHANTING =
                    new LinkedHashMap<>();

            saveLoadedConfig(file);

            System.out.println(
                    "[ChampUtils] Loaded " +
                            TOOLS.size() +
                            " profession tools."
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveLoadedConfig(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            ConfigRoot root = new ConfigRoot();
            root.tools = TOOLS == null ? new LinkedHashMap<>() : TOOLS;
            root.rarityCosts = RARITY_COSTS == null ? defaultRarityCosts() : RARITY_COSTS;
            root.rerollCostMultiplier = REROLL_COST_MULTIPLIER;
            root.ascendedUnidentifiedChancePercent = ASCENDED_UNIDENTIFIED_CHANCE_PERCENT;
            root.speedPercentPerVirtualEfficiencyLevel = SPEED_PERCENT_PER_VIRTUAL_EFFICIENCY_LEVEL;
            root.maxVirtualEfficiencyLevel = MAX_VIRTUAL_EFFICIENCY_LEVEL;
            GSON.toJson(root, writer);
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

                ASCENDED_UNIDENTIFIED_CHANCE_PERCENT =
                        Math.max(
                                0.0D,
                                Math.min(
                                        100.0D,
                                        config.ascendedUnidentifiedChancePercent
                                )
                        );

                SPEED_PERCENT_PER_VIRTUAL_EFFICIENCY_LEVEL =
                        Math.max(1.0D, config.speedPercentPerVirtualEfficiencyLevel);

                MAX_VIRTUAL_EFFICIENCY_LEVEL =
                        Math.max(0.0D, config.maxVirtualEfficiencyLevel);

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

            ASCENDED_UNIDENTIFIED_CHANCE_PERCENT =
                    1.0D;

            SPEED_PERCENT_PER_VIRTUAL_EFFICIENCY_LEVEL =
                    50.0D;

            MAX_VIRTUAL_EFFICIENCY_LEVEL =
                    5.0D;

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

            root.ascendedUnidentifiedChancePercent =
                    1.0D;

            root.tools.put(
                    "miners_fang",
                    createTool(
                            "MINING",
                            25,
                            "Pickaxe",
                            "D",
                            "minecraft:diamond_pickaxe",
                            1001,
                            true,
                            Map.of(
                                    "miningSpeed",
                                    new StatRange(
                                            35.0D,
                                            75.0D,
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
                            "Pickaxe",
                            "A",
                            "minecraft:netherite_pickaxe",
                            1002,
                            true,
                            Map.of(
                                    "miningSpeed",
                                    new StatRange(
                                            120.0D,
                                            220.0D,
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
                            "Axe",
                            "D",
                            "minecraft:diamond_axe",
                            2001,
                            false,
                            Map.of(
                                    "chopSpeed",
                                    new StatRange(
                                            35.0D,
                                            75.0D,
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
                            "Axe",
                            "A",
                            "minecraft:netherite_axe",
                            2002,
                            false,
                            Map.of(
                                    "chopSpeed",
                                    new StatRange(
                                            120.0D,
                                            220.0D,
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

            addDefaultShovelTools(root.tools);
            addDefaultSwordTools(root.tools);

            root.tools.put(
                    "gaias_blessing",
                    createTool(
                            "FARMING",
                            100,
                            "Hoe",
                            "A",
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
                                    "tree_replant_toggle"
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


    private static void applyCobbleChampsProfessionToolRework() {
        if (TOOLS == null) return;

        /*
         * Safety note: this method used to overwrite every profession tool
         * stat range on each /champreload, which made config-side balancing
         * impossible. It now only fills missing/legacy fields. Existing config
         * values are treated as the source of truth.
         */
        for (Map.Entry<String, ToolData> entry : TOOLS.entrySet()) {
            ToolData tool = entry.getValue();
            if (tool == null) continue;

            String base = tool.baseItem == null ? "" : tool.baseItem.toLowerCase();
            String rarity = normalizeRarity(tool.rarity);
            boolean pickaxe = base.contains("pickaxe");
            boolean axe = !pickaxe && base.contains("axe");
            boolean hoe = base.contains("hoe");
            boolean shovel = base.contains("shovel");
            boolean sword = base.contains("sword");
            if (!pickaxe && !axe && !hoe && !shovel && !sword) continue;

            // Tool material is intentionally vanilla now. Rarity selects the actual registered
            // Minecraft tier instead of hidden mining speed or server-side ore overrides.
            tool.toolTier = gameplayTier(rarity);

            if (tool.statRanges == null) {
                tool.statRanges = new LinkedHashMap<>();
            }

            forceVanillaEfficiencyOnly(tool.statRanges, rarity);
            if (pickaxe) {
                putIfMissing(tool.statRanges, "fortuneChance", range(rarity, 10, 25, 15, 35, 25, 50, 35, 65, 45, 75, 50, 100));
                putIfMissing(tool.statRanges, "durabilityBonus", range(rarity, 10, 40, 25, 75, 50, 150, 100, 250, 200, 500, 400, 900));
                putIfMissing(tool.statRanges, "durabilitySaveChance", range(rarity, 1, 5, 3, 8, 5, 12, 8, 16, 12, 22, 18, 30));
                putIfMissing(tool.statRanges, "stoneFinderChance", range(rarity, 0.05, 0.20, 0.15, 0.50, 0.40, 1.00, 0.80, 2.00, 1.50, 3.50, 2.50, 5.00));
                if (tool.passives == null || tool.passives.isEmpty()) tool.passives = new ArrayList<>(List.of("fortune_chance", "durability_save", "stone_finder"));
            } else if (axe) {
                putIfMissing(tool.statRanges, "fortuneChance", range(rarity, 10, 25, 15, 35, 25, 50, 35, 65, 45, 75, 50, 100));
                putIfMissing(tool.statRanges, "durabilityBonus", range(rarity, 10, 40, 25, 75, 50, 150, 100, 250, 200, 500, 400, 900));
                putIfMissing(tool.statRanges, "durabilitySaveChance", range(rarity, 1, 5, 3, 8, 5, 12, 8, 16, 12, 22, 18, 30));
                putIfMissing(tool.statRanges, "apricornFinderChance", range(rarity, 0.25, 0.75, 0.50, 1.25, 0.80, 2.0, 1.25, 3.0, 2.0, 5.0, 3.0, 8.0));
                if (tool.passives == null || tool.passives.isEmpty()) tool.passives = new ArrayList<>(List.of("fortune_chance", "durability_save", "apricorn_finder"));
            } else if (hoe) {
                putIfMissing(tool.statRanges, "fortuneChance", range(rarity, 10, 25, 15, 35, 25, 50, 35, 65, 45, 75, 50, 100));
                putIfMissing(tool.statRanges, "durabilityBonus", range(rarity, 10, 40, 25, 75, 50, 150, 100, 250, 200, 500, 400, 900));
                putIfMissing(tool.statRanges, "durabilitySaveChance", range(rarity, 1, 5, 3, 8, 5, 12, 8, 16, 12, 22, 18, 30));
                putIfMissing(tool.statRanges, "berryFinderChance", range(rarity, 0.05, 0.20, 0.15, 0.50, 0.40, 1.00, 0.80, 2.00, 1.50, 3.50, 2.50, 5.00));
                if (tool.passives == null || tool.passives.isEmpty()) tool.passives = new ArrayList<>(List.of("fortune_chance", "durability_save", "silk_touch", "berry_finder"));
            } else if (shovel) {
                putIfMissing(tool.statRanges, "fortuneChance", range(rarity, 10, 25, 15, 35, 25, 50, 35, 65, 45, 75, 50, 100));
                putIfMissing(tool.statRanges, "durabilityBonus", range(rarity, 10, 40, 25, 75, 50, 150, 100, 250, 200, 500, 400, 900));
                putIfMissing(tool.statRanges, "durabilitySaveChance", range(rarity, 1, 5, 3, 8, 5, 12, 8, 16, 12, 22, 18, 30));
                putIfMissing(tool.statRanges, "fossilFinderChance", range(rarity, 0.01, 0.05, 0.02, 0.08, 0.04, 0.15, 0.08, 0.25, 0.15, 0.40, 0.25, 0.75));
                if (tool.passives == null || tool.passives.isEmpty()) tool.passives = new ArrayList<>(List.of("fortune_chance", "durability_save", "fossil_finder"));
            } else if (sword) {
                putIfMissing(tool.statRanges, "sharpnessPercent", range(rarity, 2, 8, 6, 14, 12, 25, 20, 35, 35, 60, 50, 90));
                putIfMissing(tool.statRanges, "lootingChance", range(rarity, 1, 4, 3, 8, 6, 14, 10, 22, 16, 35, 25, 55));
                putIfMissing(tool.statRanges, "durabilityBonus", range(rarity, 10, 40, 25, 75, 50, 150, 100, 250, 200, 500, 400, 900));
                putIfMissing(tool.statRanges, "durabilitySaveChance", range(rarity, 1, 5, 3, 8, 5, 12, 8, 16, 12, 22, 18, 30));
                if (tool.passives == null || tool.passives.isEmpty()) tool.passives = new ArrayList<>(List.of("sharpness_percent", "looting_chance", "durability_save"));
            }

            if (tool.stats == null) tool.stats = new LinkedHashMap<>();
        }
    }

    private static void putIfMissing(Map<String, StatRange> ranges, String key, StatRange value) {
        if (ranges == null || key == null || value == null) return;
        if (!ranges.containsKey(key)) ranges.put(key, value);
    }

    private static void forceVanillaEfficiencyOnly(Map<String, StatRange> ranges, String rarity) {
        if (ranges == null) return;
        ranges.remove("miningSpeed");
        ranges.remove("chopSpeed");
        ranges.remove("diggingSpeed");
        ranges.remove("farmingSpeed");

        StatRange efficiency = efficiencyRange(rarity);
        LinkedHashMap<String, StatRange> reordered = new LinkedHashMap<>();
        reordered.put("efficiencyLevel", efficiency);
        for (Map.Entry<String, StatRange> entry : new LinkedHashMap<>(ranges).entrySet()) {
            if (!"efficiencyLevel".equals(entry.getKey())) {
                reordered.put(entry.getKey(), entry.getValue());
            }
        }
        ranges.clear();
        ranges.putAll(reordered);
    }

    private static StatRange efficiencyRange(String rarity) {
        return switch (normalizeRarity(rarity)) {
            case "E" -> new StatRange(1.0D, 2.0D, 1.0D);
            case "D" -> new StatRange(2.0D, 3.0D, 1.0D);
            case "C" -> new StatRange(4.0D, 5.0D, 1.0D);
            case "B" -> new StatRange(5.0D, 7.0D, 1.0D);
            case "A" -> new StatRange(7.0D, 9.0D, 1.0D);
            case "S" -> new StatRange(9.0D, 10.0D, 1.0D);
            default -> new StatRange(0.0D, 1.0D, 1.0D);
        };
    }

    private static String gameplayTier(String rarity) {
        return switch (normalizeRarity(rarity)) {
            case "F", "E" -> "IRON";
            case "D", "C" -> "DIAMOND";
            case "B", "A" -> "NETHERITE";
            case "S" -> "GOLD";
            default -> "IRON";
        };
    }

    private static void normalizeToolDisplayNames() {
        if (TOOLS == null) return;
        for (ToolData tool : TOOLS.values()) {
            if (tool == null) continue;
            String generic = genericToolDisplayName(tool.rarity, tool.baseItem);
            if (!generic.isBlank()) {
                tool.displayName = generic;
            }
        }
    }

    private static String genericToolDisplayName(String rarity, String baseItem) {
        String family = familyFromBaseItem(baseItem);
        if (family.isBlank() || "Tool".equals(family)) return "";
        String rank = normalizeRarity(rarity);
        if (rank == null || rank.isBlank()) rank = "F";
        return rank + " Rank " + family;
    }

    private static String familyFromBaseItem(String baseItem) {
        String base = baseItem == null ? "" : baseItem.toLowerCase(Locale.ROOT);
        if (base.contains("pickaxe")) return "Pickaxe";
        if (base.contains("shovel")) return "Shovel";
        if (base.contains("axe")) return "Axe";
        if (base.contains("hoe")) return "Hoe";
        if (base.contains("sword")) return "Sword";
        return "Tool";
    }

    private static void ensureDefaultSwordTools() {
        if (TOOLS == null) TOOLS = new LinkedHashMap<>();
        addDefaultSwordTools(TOOLS);
    }

    /**
     * Adds the dedicated B Rank tool tier to older configs. B sits between C and A:
     * stronger than C Rank diamond tools, but still below A Rank legendary/prestige tools.
     */
    private static void ensureDefaultBRankTools() {
        if (TOOLS == null) TOOLS = new LinkedHashMap<>();
        putBRankTool("b_rank_pickaxe_1", "MINING", 50, "Pickaxe", "minecraft:netherite_pickaxe", 1017,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(40.0D, 70.0D, 1.0D), "durabilityBonus", new StatRange(150.0D, 375.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "stoneFinderChance", new StatRange(1.15D, 2.75D, 1.0D)),
                List.of("fortune_chance", "durability_save", "stone_finder"), "vein_miner_burst", 40);
        putBRankTool("b_rank_pickaxe_2", "MINING", 54, "Pickaxe", "minecraft:netherite_pickaxe", 1018,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(42.0D, 72.0D, 1.0D), "durabilityBonus", new StatRange(160.0D, 400.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "stoneFinderChance", new StatRange(1.20D, 2.80D, 1.0D)),
                List.of("fortune_chance", "durability_save", "stone_finder"), "treasure_sense", 38);
        putBRankTool("b_rank_axe_1", "FORESTRY", 50, "Axe", "minecraft:netherite_axe", 2017,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(40.0D, 70.0D, 1.0D), "durabilityBonus", new StatRange(150.0D, 375.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "apricornFinderChance", new StatRange(1.6D, 4.0D, 1.0D)),
                List.of("fortune_chance", "durability_save", "apricorn_finder"), "timber_burst", 40);
        putBRankTool("b_rank_axe_2", "FORESTRY", 54, "Axe", "minecraft:netherite_axe", 2018,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(42.0D, 72.0D, 1.0D), "durabilityBonus", new StatRange(160.0D, 400.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "apricornFinderChance", new StatRange(1.7D, 4.2D, 1.0D)),
                List.of("fortune_chance", "durability_save", "apricorn_finder"), "leafstorm", 40);
        putBRankTool("b_rank_hoe_1", "FARMING", 50, "Hoe", "minecraft:netherite_hoe", 4017,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(40.0D, 70.0D, 1.0D), "durabilityBonus", new StatRange(150.0D, 375.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "berryFinderChance", new StatRange(1.15D, 2.75D, 1.0D)),
                List.of("fortune_chance", "durability_save", "silk_touch", "berry_finder"), "harvest_wave", 40);
        putBRankTool("b_rank_hoe_2", "FARMING", 54, "Hoe", "minecraft:netherite_hoe", 4018,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(42.0D, 72.0D, 1.0D), "durabilityBonus", new StatRange(160.0D, 400.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "berryFinderChance", new StatRange(1.20D, 2.80D, 1.0D)),
                List.of("fortune_chance", "durability_save", "silk_touch", "berry_finder"), "golden_rain", 40);
        putBRankTool("b_rank_shovel_1", "MINING", 50, "Shovel", "minecraft:netherite_shovel", 5007,
                Map.of("efficiencyLevel", new StatRange(5.0D, 7.0D, 1.0D), "fortuneChance", new StatRange(40.0D, 70.0D, 1.0D), "durabilityBonus", new StatRange(150.0D, 375.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D), "fossilFinderChance", new StatRange(0.115D, 0.325D, 1.0D)),
                List.of("fortune_chance", "durability_save", "fossil_finder"), "excavation", 40);
        TOOLS.putIfAbsent("b_rank_sword_1", createTool("", 80, "Sword", "B", "minecraft:netherite_sword", 6007, true,
                Map.of("sharpnessPercent", new StatRange(27.0D, 47.0D, 1.0D), "lootingChance", new StatRange(13.0D, 28.0D, 1.0D), "durabilityBonus", new StatRange(150.0D, 375.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));
    }

    private static void putBRankTool(String id, String profession, int requiredLevel, String displayName, String baseItem, int customModelData, Map<String, StatRange> stats, List<String> passives, String activeAbility, int cooldown) {
        TOOLS.putIfAbsent(id, createTool(profession, requiredLevel, displayName, "B", baseItem, customModelData, true, stats, passives, activeAbility, cooldown));
    }

    private static void buffMeaningfulActivesExceptReplant() {
        if (TOOLS == null) return;
        for (ToolData tool : TOOLS.values()) {
            if (tool == null || tool.activeAbility == null || tool.activeAbility.isBlank()) continue;
            String ability = tool.activeAbility.trim().toLowerCase();
            String rarity = normalizeRarity(tool.rarity);
            tool.activeCooldownSeconds = tunedCooldown(ability, rarity, Math.max(1, tool.activeCooldownSeconds));
            if (isTimedActiveAbility(ability)) {
                tool.activeDurationSeconds = 10;
                tool.activeDurationSecondsPerLevel = 0.1D;
            } else {
                tool.activeDurationSeconds = 0;
                tool.activeDurationSecondsPerLevel = 0.0D;
            }
            if (ability.contains("vein_miner")) tool.maxVeinBlocks = Math.max(tool.maxVeinBlocks, rarityValue(rarity, 48, 64, 80, 112, 160, 220));
            if (ability.contains("timber")) tool.maxTimberBlocks = Math.max(tool.maxTimberBlocks, rarityValue(rarity, 64, 80, 96, 128, 192, 256));
            if (ability.contains("leafstorm")) tool.leafstormRadius = Math.max(tool.leafstormRadius, rarityValue(rarity, 5, 6, 7, 8, 10, 12));
            if (ability.contains("treasure_sense")) tool.treasureSenseRadius = Math.max(tool.treasureSenseRadius, rarityValue(rarity, 96, 112, 128, 160, 192, 224));
            if (ability.contains("nature_sense")) tool.natureSenseRadius = Math.max(tool.natureSenseRadius, rarityValue(rarity, 96, 112, 128, 160, 192, 224));
        }
    }

    private static boolean isTimedActiveAbility(String ability) {
        if (ability == null || ability.isBlank()) return false;
        return switch (ability.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "excavation",
                    "auto_smelt_burst",
                    "miners_focus",
                    "vein_miner_burst",
                    "blast_mine",
                    "stonebreaker",
                    "timber_burst",
                    "leafstorm",
                    "lumberjack_focus",
                    "foresters_focus",
                    "harvest_wave",
                    "golden_rain" -> true;
            default -> false;
        };
    }

    private static int tunedCooldown(String ability, String rarity, int current) {
        int tuned = switch (ability) {
            case "treasure_sense", "nature_sense", "prospect" -> 30;
            default -> rarityValue(rarity, 60, 55, 50, 45, 40, 35);
        };
        return Math.min(current, tuned);
    }

    private static int tunedDuration(String ability, String rarity, int current) {
        if (current <= 0) return 0;
        int cap = switch (ability) {
            case "excavation", "vein_miner_burst", "timber_burst", "auto_smelt_burst", "harvest_wave" -> 10;
            case "blast_mine", "stonebreaker" -> 8;
            case "miners_focus", "foresters_focus", "lumberjack_focus", "golden_rain" -> 12;
            case "treasure_sense", "nature_sense" -> 8;
            default -> 12;
        };
        return Math.min(current, cap);
    }

    private static int rarityValue(String rarity, int common, int uncommon, int rare, int epic, int legendary, int mythic) {
        return switch (normalizeRarity(rarity)) {
            case "E" -> uncommon;
            case "D" -> rare;
            case "C" -> epic;
            case "B" -> (epic + legendary) / 2;
            case "A" -> legendary;
            case "S" -> mythic;
            default -> common;
        };
    }

    private static String normalizeRarity(String rarity) {
        return com.champutils.rarity.RarityScale.normalize(rarity);
    }

    private static StatRange range(String rarity,
                                   double cMin, double cMax, double uMin, double uMax,
                                   double rMin, double rMax, double eMin, double eMax,
                                   double lMin, double lMax, double mMin, double mMax) {
        return switch (normalizeRarity(rarity)) {
            case "E" -> new StatRange(uMin, uMax, 1.0D);
            case "D" -> new StatRange(rMin, rMax, 1.0D);
            case "C" -> new StatRange(eMin, eMax, 1.0D);
            case "B" -> new StatRange((eMin + lMin) / 2.0D, (eMax + lMax) / 2.0D, 1.0D);
            case "A" -> new StatRange(lMin, lMax, 1.0D);
            case "S" -> new StatRange(mMin, mMax, 1.0D);
            default -> new StatRange(cMin, cMax, 1.0D);
        };
    }

    private static void ensureDefaultShovelTools() {
        if (TOOLS == null) {
            TOOLS = new LinkedHashMap<>();
        }
        addDefaultShovelTools(TOOLS);
        addDefaultSwordTools(TOOLS);
    }

    private static void addDefaultSwordTools(Map<String, ToolData> tools) {
        if (tools == null) return;

        tools.putIfAbsent("f_rank_sword_1", createTool("", 1, "Sword", "F", "minecraft:iron_sword", 6001, false,
                Map.of("sharpnessPercent", new StatRange(2.0D, 8.0D, 1.0D), "lootingChance", new StatRange(1.0D, 4.0D, 1.0D), "durabilityBonus", new StatRange(10.0D, 40.0D, 1.0D), "durabilitySaveChance", new StatRange(1.0D, 5.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));

        tools.putIfAbsent("e_rank_sword_1", createTool("", 10, "Sword", "E", "minecraft:iron_sword", 6002, false,
                Map.of("sharpnessPercent", new StatRange(6.0D, 14.0D, 1.0D), "lootingChance", new StatRange(3.0D, 8.0D, 1.0D), "durabilityBonus", new StatRange(25.0D, 75.0D, 1.0D), "durabilitySaveChance", new StatRange(3.0D, 8.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));

        tools.putIfAbsent("d_rank_sword_1", createTool("", 25, "Sword", "D", "minecraft:diamond_sword", 6003, false,
                Map.of("sharpnessPercent", new StatRange(12.0D, 25.0D, 1.0D), "lootingChance", new StatRange(6.0D, 14.0D, 1.0D), "durabilityBonus", new StatRange(50.0D, 150.0D, 1.0D), "durabilitySaveChance", new StatRange(5.0D, 12.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));

        tools.putIfAbsent("c_rank_sword_1", createTool("", 60, "Sword", "C", "minecraft:diamond_sword", 6004, false,
                Map.of("sharpnessPercent", new StatRange(20.0D, 35.0D, 1.0D), "lootingChance", new StatRange(10.0D, 22.0D, 1.0D), "durabilityBonus", new StatRange(100.0D, 250.0D, 1.0D), "durabilitySaveChance", new StatRange(8.0D, 16.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));

        tools.putIfAbsent("b_rank_sword_1", createTool("", 80, "Sword", "B", "minecraft:netherite_sword", 6007, true,
                Map.of("sharpnessPercent", new StatRange(27.0D, 47.0D, 1.0D), "lootingChance", new StatRange(13.0D, 28.0D, 1.0D), "durabilityBonus", new StatRange(150.0D, 375.0D, 1.0D), "durabilitySaveChance", new StatRange(10.0D, 19.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));

        tools.putIfAbsent("a_rank_sword_1", createTool("", 100, "Sword", "A", "minecraft:netherite_sword", 6005, true,
                Map.of("sharpnessPercent", new StatRange(35.0D, 60.0D, 1.0D), "lootingChance", new StatRange(16.0D, 35.0D, 1.0D), "durabilityBonus", new StatRange(200.0D, 500.0D, 1.0D), "durabilitySaveChance", new StatRange(12.0D, 22.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));

        tools.putIfAbsent("s_rank_sword_1", createTool("", 100, "Sword", "S", "minecraft:netherite_sword", 6006, true,
                Map.of("sharpnessPercent", new StatRange(50.0D, 90.0D, 1.0D), "lootingChance", new StatRange(25.0D, 55.0D, 1.0D), "durabilityBonus", new StatRange(400.0D, 900.0D, 1.0D), "durabilitySaveChance", new StatRange(18.0D, 30.0D, 1.0D)),
                List.of("sharpness_percent", "looting_chance", "durability_save"), null, 0));
    }

    private static void addDefaultShovelTools(Map<String, ToolData> tools) {
        if (tools == null) return;

        tools.putIfAbsent(
                "trailblazer_shovel",
                createTool(
                        "MINING",
                        1,
                        "Shovel",
                        "F",
                        "minecraft:iron_shovel",
                        5001,
                        false,
                        Map.of(
                                "miningSpeed", new StatRange(25.0D, 60.0D, 2.0D),
                                "durabilityBonus", new StatRange(25.0D, 75.0D, 1.0D),
                                "excavationSeconds", new StatRange(8.0D, 14.0D, 1.0D)
                        ),
                        List.of("durability_save"),
                        "excavation",
                        45
                )
        );

        tools.putIfAbsent(
                "riverbed_spade",
                createTool(
                        "MINING",
                        10,
                        "Shovel",
                        "E",
                        "minecraft:iron_shovel",
                        5005,
                        false,
                        Map.of(
                                "miningSpeed", new StatRange(35.0D, 80.0D, 2.0D),
                                "durabilityBonus", new StatRange(50.0D, 120.0D, 1.0D),
                                "excavationSeconds", new StatRange(10.0D, 17.0D, 1.0D)
                        ),
                        List.of("durability_save"),
                        "excavation",
                        40
                )
        );

        tools.putIfAbsent(
                "sandsweeper",
                createTool(
                        "MINING",
                        25,
                        "Shovel",
                        "D",
                        "minecraft:diamond_shovel",
                        5002,
                        false,
                        Map.of(
                                "miningSpeed", new StatRange(50.0D, 110.0D, 2.0D),
                                "fortuneBonus", new StatRange(2.0D, 8.0D, 2.0D),
                                "durabilityBonus", new StatRange(75.0D, 175.0D, 1.0D),
                                "excavationSeconds", new StatRange(12.0D, 20.0D, 1.0D)
                        ),
                        List.of("bonus_ore_drops", "durability_save"),
                        "excavation",
                        35
                )
        );

        tools.putIfAbsent(
                "dune_cleaver",
                createTool(
                        "MINING",
                        60,
                        "Shovel",
                        "C",
                        "minecraft:diamond_shovel",
                        5006,
                        false,
                        Map.of(
                                "miningSpeed", new StatRange(70.0D, 145.0D, 2.0D),
                                "fortuneBonus", new StatRange(5.0D, 14.0D, 2.0D),
                                "durabilityBonus", new StatRange(125.0D, 300.0D, 1.0D),
                                "excavationSeconds", new StatRange(15.0D, 25.0D, 1.0D)
                        ),
                        List.of("bonus_ore_drops", "durability_save"),
                        "excavation",
                        32
                )
        );

        tools.putIfAbsent(
                "b_rank_shovel_1",
                createTool(
                        "MINING",
                        50,
                        "Shovel",
                        "B",
                        "minecraft:netherite_shovel",
                        5007,
                        true,
                        Map.of(
                                "miningSpeed", new StatRange(80.0D, 162.0D, 2.0D),
                                "fortuneBonus", new StatRange(6.0D, 17.0D, 2.0D),
                                "durabilityBonus", new StatRange(165.0D, 400.0D, 1.0D),
                                "excavationSeconds", new StatRange(16.0D, 27.0D, 1.0D)
                        ),
                        List.of("bonus_ore_drops", "durability_save"),
                        "excavation",
                        31
                )
        );

        tools.putIfAbsent(
                "earthshaper",
                createTool(
                        "MINING",
                        100,
                        "Shovel",
                        "A",
                        "minecraft:netherite_shovel",
                        5003,
                        true,
                        Map.of(
                                "miningSpeed", new StatRange(90.0D, 180.0D, 2.0D),
                                "fortuneBonus", new StatRange(8.0D, 20.0D, 2.0D),
                                "durabilityBonus", new StatRange(200.0D, 500.0D, 1.0D),
                                "excavationSeconds", new StatRange(18.0D, 30.0D, 1.0D)
                        ),
                        List.of("bonus_ore_drops", "durability_save"),
                        "excavation",
                        30
                )
        );

        tools.putIfAbsent(
                "worldcarver",
                createTool(
                        "MINING",
                        100,
                        "Shovel",
                        "S",
                        "minecraft:netherite_shovel",
                        5004,
                        true,
                        Map.of(
                                "miningSpeed", new StatRange(140.0D, 260.0D, 2.0D),
                                "fortuneBonus", new StatRange(15.0D, 30.0D, 2.0D),
                                "durabilityBonus", new StatRange(400.0D, 800.0D, 1.0D),
                                "excavationSeconds", new StatRange(24.0D, 40.0D, 1.0D)
                        ),
                        List.of("bonus_ore_drops", "durability_save"),
                        "excavation",
                        25
                )
        );
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

        String genericDisplayName =
                genericToolDisplayName(
                        rarity,
                        baseItem
                );

        tool.displayName =
                genericDisplayName.isBlank()
                        ? displayName
                        : genericDisplayName;

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
                "F",
                100L
        );

        costs.put(
                "E",
                250L
        );

        costs.put(
                "D",
                500L
        );

        costs.put(
                "C",
                900L
        );

        costs.put(
                "B",
                1250L
        );

        costs.put(
                "A",
                1800L
        );

        costs.put(
                "S",
                3000L
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

        if (toolData != null) {
            String generic = genericToolDisplayName(
                    toolData.rarity,
                    toolData.baseItem
            );
            if (!generic.isBlank()) {
                return generic;
            }
        }

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


    public static List<String> getActiveAbilityPool(
            String toolId,
            ToolData toolData
    ) {

        Set<String> pool = new LinkedHashSet<>();

        if (toolData != null && toolData.activeAbilityPool != null) {
            for (String ability : toolData.activeAbilityPool) {
                addCleanAbility(pool, ability);
            }
        }

        if (!pool.isEmpty()) {
            return new ArrayList<>(pool);
        }

        String family = getToolFamily(toolData);
        if (family != null && !family.isBlank()) {
            for (ToolData candidate : TOOLS.values()) {
                if (candidate == null) {
                    continue;
                }
                if (!family.equals(getToolFamily(candidate))) {
                    continue;
                }
                addCleanAbility(pool, candidate.activeAbility);
                if (candidate.activeAbilityPool != null) {
                    for (String ability : candidate.activeAbilityPool) {
                        addCleanAbility(pool, ability);
                    }
                }
            }
        }

        if (pool.isEmpty() && toolData != null) {
            addCleanAbility(pool, toolData.activeAbility);
        }

        return new ArrayList<>(pool);
    }

    private static void addCleanAbility(Set<String> pool, String ability) {
        if (ability == null || ability.isBlank()) {
            return;
        }
        pool.add(ability.trim().toLowerCase(Locale.ROOT));
    }

    public static String getToolFamily(ToolData toolData) {
        if (toolData == null || toolData.baseItem == null) {
            return "tool";
        }
        String base = toolData.baseItem.toLowerCase(Locale.ROOT);
        if (base.contains("pickaxe")) return "pickaxe";
        if (base.contains("shovel")) return "shovel";
        if (base.contains("hoe")) return "hoe";
        if (base.contains("sword")) return "sword";
        if (base.contains("axe")) return "axe";
        return "tool";
    }

    private static String formatToolFamilyName(String family) {
        if (family == null || family.isBlank()) {
            return "Tool";
        }
        return switch (family.trim().toLowerCase(Locale.ROOT)) {
            case "pickaxe" -> "Pickaxe";
            case "shovel" -> "Shovel";
            case "hoe" -> "Hoe";
            case "axe" -> "Axe";
            case "sword" -> "Sword";
            default -> "Tool";
        };
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
