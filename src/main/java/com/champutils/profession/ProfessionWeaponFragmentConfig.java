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
        public Map<String, Integer> rarityWeights = new LinkedHashMap<>();
    }

    public static class DropSettings {
        public double baseDropChance = 0.001D;
        public double chancePerLevel = 0.00012D;
        public double maxDropChance = 0.0125D;
        public boolean announceLegendaryAndMythicToServer = true;
        public boolean actionBarMessage = true;
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
        public String lore = "A weapon fragment earned from profession activities.";
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "profession_weapon_fragments.json");

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
                FRAGMENTS = root.fragments == null ? new LinkedHashMap<>() : root.fragments;
                RARITY_WEIGHTS = root.rarityWeights == null ? new LinkedHashMap<>() : root.rarityWeights;
            }

            ensureDefaultsIfEmpty();

            System.out.println("[ChampUtils] Loaded " + FRAGMENTS.size() + " weapon fragment definitions.");
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

        if (FRAGMENTS == null || FRAGMENTS.isEmpty()) {
            FRAGMENTS = defaults.fragments;
        }

        if (RARITY_WEIGHTS == null || RARITY_WEIGHTS.isEmpty()) {
            RARITY_WEIGHTS = defaults.rarityWeights;
        }
    }

    private static ConfigRoot defaultRoot() {
        ConfigRoot root = new ConfigRoot();

        addFragment(root, "COMMON", "common_weapon_fragment", "Common Weapon Fragment", 9201, "WHITE");
        addFragment(root, "UNCOMMON", "uncommon_weapon_fragment", "Uncommon Weapon Fragment", 9202, "GREEN");
        addFragment(root, "RARE", "rare_weapon_fragment", "Rare Weapon Fragment", 9203, "BLUE");
        addFragment(root, "EPIC", "epic_weapon_fragment", "Epic Weapon Fragment", 9204, "LIGHT_PURPLE");
        addFragment(root, "LEGENDARY", "legendary_weapon_fragment", "Legendary Weapon Fragment", 9205, "GOLD");
        addFragment(root, "MYTHIC", "mythic_weapon_fragment", "Mythic Weapon Fragment", 9206, "DARK_PURPLE");

        root.rarityWeights.put("COMMON", 800000);
        root.rarityWeights.put("UNCOMMON", 150000);
        root.rarityWeights.put("RARE", 40000);
        root.rarityWeights.put("EPIC", 9000);
        root.rarityWeights.put("LEGENDARY", 950);
        root.rarityWeights.put("MYTHIC", 50);

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
        data.lore = "A rare weapon crafting fragment found while training professions.";
        root.fragments.put(normalizeRarity(rarity), data);
    }

    public static String normalizeRarity(String rarity) {
        if (rarity == null || rarity.isBlank()) {
            return "COMMON";
        }

        return rarity.trim().toUpperCase();
    }
}
