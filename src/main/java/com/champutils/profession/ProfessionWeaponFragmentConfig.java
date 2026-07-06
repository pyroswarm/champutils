package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionWeaponFragmentConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static boolean ENABLED = true;
    public static DropSettings DROP_SETTINGS = new DropSettings();
    public static Map<String, FragmentData> FRAGMENTS = new LinkedHashMap<>();
    public static Map<String, Integer> RARITY_WEIGHTS = new LinkedHashMap<>();

    private ProfessionWeaponFragmentConfig() {
    }

    public static class ConfigRoot {
        public boolean enabled = true;
        public DropSettings dropSettings = new DropSettings();
        public Map<String, FragmentData> fragments = new LinkedHashMap<>();
        public Map<String, FragmentData> essence = new LinkedHashMap<>();
        public Map<String, Integer> rarityWeights = new LinkedHashMap<>();
    }

    public static class DropSettings {
        public double baseDropChance = 0.0125D;
        public double chancePerLevel = 0.0010D;
        public double maxDropChance = 0.15D;
        public boolean announceTopRanksToServer = true;
        public boolean actionBarMessage = true;
        /**
         * Guarantees a fragment after this many eligible profession actions without one.
         * Set to 0 or lower to disable pity.
         */
        public Integer pityActions = 40;
        /**
         * Pity awards from the normal eligible rarity pool for the player's tool rarity.
         * Keeping this true prevents pity from being abused as a high-rarity-only source.
         */
        public boolean pityUsesToolRarityPool = true;
        public Map<String, Double> professionMultipliers = new LinkedHashMap<>();

        public DropSettings() {
            professionMultipliers.put("MINING", 1.0D);
            professionMultipliers.put("FORESTRY", 1.0D);
            professionMultipliers.put("FARMING", 1.0D);
        }
    }

    public static class FragmentData {
        public String itemId = "";
        public String displayName = "";
        public String baseItem = "minecraft:paper";
        public int customModelData = 0;
        public String color = "WHITE";
        public String lore = "A weapon essence earned from profession activities.";
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "profession_weapon_essence.json");
            File legacyFile = new File(dir, "profession_weapon_fragments.json");
            if (!file.exists() && legacyFile.exists()) {
                file = legacyFile;
            }

            if (!file.exists()) {
                createDefault(file);
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);

                if (root == null) {
                    root = defaultRoot();
                }

                ENABLED = root.enabled;
                DROP_SETTINGS = root.dropSettings == null ? new DropSettings() : root.dropSettings;
                if (root.essence != null && !root.essence.isEmpty()) {
                    FRAGMENTS = root.essence;
                } else {
                    FRAGMENTS = root.fragments == null ? new LinkedHashMap<>() : root.fragments;
                }
                RARITY_WEIGHTS = root.rarityWeights == null ? new LinkedHashMap<>() : root.rarityWeights;
            }

            ensureDefaultsIfEmpty();
            applyRankFragmentDisplay();

            System.out.println("[ChampUtils] Loaded " + FRAGMENTS.size() + " weapon essence definitions.");
        } catch (Exception e) {
            e.printStackTrace();
            ConfigRoot defaults = defaultRoot();
            ENABLED = defaults.enabled;
            DROP_SETTINGS = defaults.dropSettings;
            FRAGMENTS = defaults.fragments;
            RARITY_WEIGHTS = defaults.rarityWeights;
        }
    }

    private static void createDefault(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(defaultRoot(), writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void ensureDefaultsIfEmpty() {
        ConfigRoot defaults = defaultRoot();

        if (DROP_SETTINGS == null) {
            DROP_SETTINGS = defaults.dropSettings;
        }

        if (DROP_SETTINGS.professionMultipliers == null || DROP_SETTINGS.professionMultipliers.isEmpty()) {
            DROP_SETTINGS.professionMultipliers = defaults.dropSettings.professionMultipliers;
        }

        if (DROP_SETTINGS.pityActions == null) {
            DROP_SETTINGS.pityActions = defaults.dropSettings.pityActions;
        }

        // Early beta defaults were so low that normal play could see 0 fragments after long sessions.
        // Keep custom higher values, but lift old/too-low values to the current intended baseline.
        if (DROP_SETTINGS.baseDropChance < defaults.dropSettings.baseDropChance) DROP_SETTINGS.baseDropChance = defaults.dropSettings.baseDropChance;
        if (DROP_SETTINGS.chancePerLevel < defaults.dropSettings.chancePerLevel) DROP_SETTINGS.chancePerLevel = defaults.dropSettings.chancePerLevel;
        if (DROP_SETTINGS.maxDropChance < defaults.dropSettings.maxDropChance) DROP_SETTINGS.maxDropChance = defaults.dropSettings.maxDropChance;
        if (DROP_SETTINGS.pityActions == null || DROP_SETTINGS.pityActions <= 0 || DROP_SETTINGS.pityActions > defaults.dropSettings.pityActions) DROP_SETTINGS.pityActions = defaults.dropSettings.pityActions;

        if (FRAGMENTS == null || FRAGMENTS.isEmpty()) {
            FRAGMENTS = defaults.fragments;
        } else {
            FRAGMENTS = normalizeFragmentMap(FRAGMENTS);
        }

        if (RARITY_WEIGHTS == null || RARITY_WEIGHTS.isEmpty()) {
            RARITY_WEIGHTS = defaults.rarityWeights;
        } else {
            RARITY_WEIGHTS = normalizeWeightMap(RARITY_WEIGHTS);
        }

        // If a custom config accidentally sets all eligible weights to zero/missing, restore defaults.
        boolean anyPositiveWeight = false;
        for (Integer weight : RARITY_WEIGHTS.values()) {
            if (weight != null && weight > 0) {
                anyPositiveWeight = true;
                break;
            }
        }
        if (!anyPositiveWeight) {
            RARITY_WEIGHTS = defaults.rarityWeights;
        }
    }

    private static Map<String, FragmentData> normalizeFragmentMap(Map<String, FragmentData> input) {
        LinkedHashMap<String, FragmentData> normalized = new LinkedHashMap<>();
        if (input == null) {
            return normalized;
        }

        for (Map.Entry<String, FragmentData> entry : input.entrySet()) {
            if (entry.getValue() != null) {
                normalized.put(normalizeRarity(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private static Map<String, Integer> normalizeWeightMap(Map<String, Integer> input) {
        LinkedHashMap<String, Integer> normalized = new LinkedHashMap<>();
        if (input == null) {
            return normalized;
        }

        for (Map.Entry<String, Integer> entry : input.entrySet()) {
            String rarity = normalizeRarity(entry.getKey());
            Integer weight = entry.getValue();
            if (weight != null) {
                normalized.put(rarity, weight);
            }
        }
        return normalized;
    }

    private static ConfigRoot defaultRoot() {
        ConfigRoot root = new ConfigRoot();

        addFragment(root, "F", "f_weapon_essence", "F Rank Weapon Essence", 9201, "WHITE");
        addFragment(root, "E", "e_weapon_essence", "E Rank Weapon Essence", 9202, "GREEN");
        addFragment(root, "D", "d_weapon_essence", "D Rank Weapon Essence", 9203, "BLUE");
        addFragment(root, "C", "c_weapon_essence", "C Rank Weapon Essence", 9204, "LIGHT_PURPLE");
        addFragment(root, "B", "b_weapon_essence", "B Rank Weapon Essence", 9205, "YELLOW");
        addFragment(root, "A", "a_weapon_essence", "A Rank Weapon Essence", 9206, "GOLD");
        addFragment(root, "S", "s_weapon_essence", "S Rank Weapon Essence", 9207, "DARK_PURPLE");

        root.rarityWeights.put("F", 800000);
        root.rarityWeights.put("E", 150000);
        root.rarityWeights.put("D", 40000);
        root.rarityWeights.put("C", 9000);
        root.rarityWeights.put("B", 2500);
        root.rarityWeights.put("A", 650);
        root.rarityWeights.put("S", 50);

        return root;
    }

    private static void addFragment(
            ConfigRoot root,
            String rarity,
            String itemId,
            String displayName,
            int customModelData,
            String color
    ) {
        FragmentData data = new FragmentData();
        data.itemId = itemId;
        data.displayName = displayName;
        data.baseItem = "minecraft:paper";
        data.customModelData = customModelData;
        data.color = color;
        data.lore = "A rare weapon crafting essence found while training professions.";
        root.fragments.put(normalizeRarity(rarity), data);
        root.essence.put(normalizeRarity(rarity), data);
    }


    private static void applyRankFragmentDisplay() {
        for (Map.Entry<String, FragmentData> entry : FRAGMENTS.entrySet()) {
            if (entry == null || entry.getValue() == null) continue;
            String rank = rankForRarity(entry.getKey());
            if (rank == null) continue;
            entry.getValue().displayName = rank + " Rank Weapon Essence";
            entry.getValue().lore = "A " + rank + " Rank weapon crafting essence found while training professions.";
        }
    }

    public static String rankForRarity(String rarity) {
        return normalizeRarity(rarity);
    }

    public static String normalizeRarity(String rarity) {
        return com.champutils.rarity.RarityScale.normalize(rarity);
    }
}
