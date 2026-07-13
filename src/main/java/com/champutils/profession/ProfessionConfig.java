package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class ProfessionConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static ProfessionSettings SETTINGS =
            new ProfessionSettings();

    public static class ProfessionSettings {
        public Map<String, Integer> miningXp = new HashMap<>();
        public Map<String, Integer> forestryXp = new HashMap<>();
        public Map<String, Integer> battleXp = new HashMap<>();
        public Map<String, Integer> farmingXp = new HashMap<>();
        public Map<String, RewardTable> rewards = new HashMap<>();
        public int sublevelProgressionVersion = 2;
        public int sublevelXpBase = 60;
        public int sublevelXpPerLevel = 15;
        public double sublevelXpGrowthAfter50 = 1.07D;
    }

    public static class RewardTable {
        public double chance;
        public String itemId;
        public int minAmount;
        public int maxAmount;
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "professions.json");

            if (!file.exists()) {
                createDefault(file);
            }

            try (FileReader reader = new FileReader(file)) {
                SETTINGS = GSON.fromJson(
                        reader,
                        ProfessionSettings.class
                );
            }

            if (SETTINGS == null) {
                SETTINGS = new ProfessionSettings();
            }

            boolean changed = ensureDefaults(SETTINGS);
            if (changed) {
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(SETTINGS, writer);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault(File file) {
        try {
            ProfessionSettings config = buildDefaultSettings();

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean ensureDefaults(ProfessionSettings target) {
        if (target.miningXp == null) target.miningXp = new HashMap<>();
        if (target.forestryXp == null) target.forestryXp = new HashMap<>();
        if (target.battleXp == null) target.battleXp = new HashMap<>();
        if (target.farmingXp == null) target.farmingXp = new HashMap<>();
        if (target.rewards == null) target.rewards = new HashMap<>();

        ProfessionSettings defaults = buildDefaultSettings();
        boolean changed = false;

        // Version 2 intentionally makes individual sublevels much faster than the
        // overall profession. Migrate existing generated configs so servers do not
        // remain on the old main-profession-equivalent curve.
        if (target.sublevelProgressionVersion < defaults.sublevelProgressionVersion) {
            target.sublevelProgressionVersion = defaults.sublevelProgressionVersion;
            target.sublevelXpBase = defaults.sublevelXpBase;
            target.sublevelXpPerLevel = defaults.sublevelXpPerLevel;
            target.sublevelXpGrowthAfter50 = defaults.sublevelXpGrowthAfter50;
            changed = true;
        }

        if (target.sublevelXpBase <= 0) {
            target.sublevelXpBase = defaults.sublevelXpBase;
            changed = true;
        }
        if (target.sublevelXpPerLevel <= 0) {
            target.sublevelXpPerLevel = defaults.sublevelXpPerLevel;
            changed = true;
        }
        if (target.sublevelXpGrowthAfter50 < 1.0D) {
            target.sublevelXpGrowthAfter50 = defaults.sublevelXpGrowthAfter50;
            changed = true;
        }

        changed |= putMissing(target.miningXp, defaults.miningXp);
        changed |= putMissing(target.forestryXp, defaults.forestryXp);
        changed |= putMissing(target.battleXp, defaults.battleXp);
        changed |= putMissing(target.farmingXp, defaults.farmingXp);

        for (Map.Entry<String, RewardTable> entry : defaults.rewards.entrySet()) {
            if (!target.rewards.containsKey(entry.getKey())) {
                target.rewards.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        return changed;
    }

    private static boolean putMissing(Map<String, Integer> target, Map<String, Integer> defaults) {
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
            if (!target.containsKey(entry.getKey())) {
                target.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private static ProfessionSettings buildDefaultSettings() {
        ProfessionSettings config = new ProfessionSettings();

        /*
         MINING - VANILLA
         */
        config.miningXp.put("minecraft:coal_ore", 10);
        config.miningXp.put("minecraft:deepslate_coal_ore", 12);
        config.miningXp.put("minecraft:copper_ore", 12);
        config.miningXp.put("minecraft:deepslate_copper_ore", 14);
        config.miningXp.put("minecraft:iron_ore", 20);
        config.miningXp.put("minecraft:deepslate_iron_ore", 24);
        config.miningXp.put("minecraft:gold_ore", 25);
        config.miningXp.put("minecraft:deepslate_gold_ore", 30);
        config.miningXp.put("minecraft:nether_gold_ore", 18);
        config.miningXp.put("minecraft:redstone_ore", 20);
        config.miningXp.put("minecraft:deepslate_redstone_ore", 24);
        config.miningXp.put("minecraft:lapis_ore", 25);
        config.miningXp.put("minecraft:deepslate_lapis_ore", 30);
        config.miningXp.put("minecraft:diamond_ore", 50);
        config.miningXp.put("minecraft:deepslate_diamond_ore", 60);
        config.miningXp.put("minecraft:emerald_ore", 60);
        config.miningXp.put("minecraft:deepslate_emerald_ore", 72);
        config.miningXp.put("minecraft:quartz_ore", 20);
        config.miningXp.put("minecraft:ancient_debris", 100);

        /*
         MINING - SHOVEL BLOCKS
         Shovels use the Mining profession too. Keep these explicit so fresh
         and auto-migrated configs award normal XP every valid shovel break.
         */
        config.miningXp.put("minecraft:dirt", 1);
        config.miningXp.put("minecraft:grass_block", 1);
        config.miningXp.put("minecraft:coarse_dirt", 1);
        config.miningXp.put("minecraft:rooted_dirt", 1);
        config.miningXp.put("minecraft:podzol", 1);
        config.miningXp.put("minecraft:mycelium", 1);
        config.miningXp.put("minecraft:mud", 1);
        config.miningXp.put("minecraft:packed_mud", 1);
        config.miningXp.put("minecraft:clay", 1);
        config.miningXp.put("minecraft:sand", 1);
        config.miningXp.put("minecraft:red_sand", 1);
        config.miningXp.put("minecraft:gravel", 1);
        config.miningXp.put("minecraft:soul_sand", 1);
        config.miningXp.put("minecraft:soul_soil", 1);
        config.miningXp.put("minecraft:snow_block", 1);
        config.miningXp.put("minecraft:powder_snow", 1);

        /*
         MINING - COBBLEMON ORES
         */
        config.miningXp.put("cobblemon:dawn_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_dawn_stone_ore", 90);
        config.miningXp.put("cobblemon:dusk_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_dusk_stone_ore", 90);
        config.miningXp.put("cobblemon:fire_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_fire_stone_ore", 90);
        config.miningXp.put("cobblemon:nether_fire_stone_ore", 90);
        config.miningXp.put("cobblemon:ice_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_ice_stone_ore", 90);
        config.miningXp.put("cobblemon:leaf_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_leaf_stone_ore", 90);
        config.miningXp.put("cobblemon:moon_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_moon_stone_ore", 90);
        config.miningXp.put("cobblemon:dripstone_moon_stone_ore", 90);
        config.miningXp.put("cobblemon:shiny_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_shiny_stone_ore", 90);
        config.miningXp.put("cobblemon:sun_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_sun_stone_ore", 90);
        config.miningXp.put("cobblemon:terracotta_sun_stone_ore", 90);
        config.miningXp.put("cobblemon:thunder_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_thunder_stone_ore", 90);
        config.miningXp.put("cobblemon:water_stone_ore", 75);
        config.miningXp.put("cobblemon:deepslate_water_stone_ore", 90);
        config.miningXp.put("cobblemon:fossil_ore", 100);

        /*
         FORESTRY - VANILLA
         */
        config.forestryXp.put("minecraft:oak_log", 5);
        config.forestryXp.put("minecraft:oak_wood", 5);
        config.forestryXp.put("minecraft:stripped_oak_log", 5);
        config.forestryXp.put("minecraft:stripped_oak_wood", 5);
        config.forestryXp.put("minecraft:spruce_log", 5);
        config.forestryXp.put("minecraft:spruce_wood", 5);
        config.forestryXp.put("minecraft:stripped_spruce_log", 5);
        config.forestryXp.put("minecraft:stripped_spruce_wood", 5);
        config.forestryXp.put("minecraft:birch_log", 5);
        config.forestryXp.put("minecraft:birch_wood", 5);
        config.forestryXp.put("minecraft:stripped_birch_log", 5);
        config.forestryXp.put("minecraft:stripped_birch_wood", 5);
        config.forestryXp.put("minecraft:jungle_log", 7);
        config.forestryXp.put("minecraft:jungle_wood", 7);
        config.forestryXp.put("minecraft:stripped_jungle_log", 7);
        config.forestryXp.put("minecraft:stripped_jungle_wood", 7);
        config.forestryXp.put("minecraft:dark_oak_log", 8);
        config.forestryXp.put("minecraft:dark_oak_wood", 8);
        config.forestryXp.put("minecraft:stripped_dark_oak_log", 8);
        config.forestryXp.put("minecraft:stripped_dark_oak_wood", 8);
        config.forestryXp.put("minecraft:acacia_log", 6);
        config.forestryXp.put("minecraft:acacia_wood", 6);
        config.forestryXp.put("minecraft:stripped_acacia_log", 6);
        config.forestryXp.put("minecraft:stripped_acacia_wood", 6);
        config.forestryXp.put("minecraft:mangrove_log", 8);
        config.forestryXp.put("minecraft:mangrove_wood", 8);
        config.forestryXp.put("minecraft:stripped_mangrove_log", 8);
        config.forestryXp.put("minecraft:stripped_mangrove_wood", 8);
        config.forestryXp.put("minecraft:cherry_log", 8);
        config.forestryXp.put("minecraft:cherry_wood", 8);
        config.forestryXp.put("minecraft:stripped_cherry_log", 8);
        config.forestryXp.put("minecraft:stripped_cherry_wood", 8);
        config.forestryXp.put("minecraft:crimson_stem", 10);
        config.forestryXp.put("minecraft:crimson_hyphae", 10);
        config.forestryXp.put("minecraft:stripped_crimson_stem", 10);
        config.forestryXp.put("minecraft:stripped_crimson_hyphae", 10);
        config.forestryXp.put("minecraft:warped_stem", 10);
        config.forestryXp.put("minecraft:warped_hyphae", 10);
        config.forestryXp.put("minecraft:stripped_warped_stem", 10);
        config.forestryXp.put("minecraft:stripped_warped_hyphae", 10);

        /*
         FORESTRY - COBBLEMON APRICORN TREES
         */
        config.forestryXp.put("cobblemon:apricorn_log", 12);
        config.forestryXp.put("cobblemon:apricorn_wood", 12);
        config.forestryXp.put("cobblemon:stripped_apricorn_log", 12);
        config.forestryXp.put("cobblemon:stripped_apricorn_wood", 12);

        /*
         FORESTRY - KNOWN EXTRA MODDED TREE SUPPORT
         These are safe config entries only. They still respect placed-block tracking.
         */
        config.forestryXp.put("biomeswevegone:saccharine_log", 10);
        config.forestryXp.put("biomeswevegone:saccharine_wood", 10);
        config.forestryXp.put("biomeswevegone:stripped_saccharine_log", 10);
        config.forestryXp.put("biomeswevegone:stripped_saccharine_wood", 10);

        /*
         BATTLE XP
         */
        config.battleXp.put("wild", 10);
        config.battleXp.put("npc", 20);
        config.battleXp.put("world_boss", 150);
        config.battleXp.put("profession", 25);

        /*
         FARMING - VANILLA + GENERIC DEFAULT
         */
        config.farmingXp.put("default", 10);
        config.farmingXp.put("minecraft:wheat", 10);
        config.farmingXp.put("minecraft:carrots", 10);
        config.farmingXp.put("minecraft:potatoes", 10);
        config.farmingXp.put("minecraft:beetroots", 10);
        config.farmingXp.put("minecraft:nether_wart", 12);
        config.farmingXp.put("minecraft:cocoa", 12);
        config.farmingXp.put("minecraft:melon", 10);
        config.farmingXp.put("minecraft:pumpkin", 10);

        /*
         FARMING - COBBLEMON PLANTS / CROPS
         IDs are intentionally configurable. Blocks only award XP if they exist and are mature.
         */
        for (String color : new String[]{"black", "blue", "green", "pink", "red", "white", "yellow"}) {
            config.farmingXp.put("cobblemon:" + color + "_apricorn", 12);
            config.farmingXp.put("cobblemon:" + color + "_apricorn_seed", 12);
        }

        for (String color : new String[]{"blue", "cyan", "green", "pink", "red", "white"}) {
            config.farmingXp.put("cobblemon:" + color + "_mint_leaf", 12);
            config.farmingXp.put("cobblemon:" + color + "_mint_seeds", 12);
        }

        for (String id : new String[]{
                "aguav_berry", "apicot_berry", "aspear_berry", "babiri_berry", "belue_berry", "bluk_berry",
                "charti_berry", "cheri_berry", "chesto_berry", "chilan_berry", "chople_berry", "coba_berry",
                "colbur_berry", "cornn_berry", "custap_berry", "durin_berry", "enigma_berry", "figy_berry",
                "ganlon_berry", "grepa_berry", "haban_berry", "hondew_berry", "hopo_berry", "iapapa_berry",
                "jaboca_berry", "kasib_berry", "kebia_berry", "kee_berry", "kelpsy_berry", "lansat_berry",
                "leppa_berry", "liechi_berry", "lum_berry", "mago_berry", "magost_berry", "maranga_berry",
                "micle_berry", "nanab_berry", "nomel_berry", "occa_berry", "oran_berry", "pamtre_berry",
                "passho_berry", "payapa_berry", "pecha_berry", "persim_berry", "petaya_berry", "pinap_berry",
                "pomeg_berry", "qualot_berry", "rabuta_berry", "rawst_berry", "razz_berry", "rindo_berry",
                "roseli_berry", "rowap_berry", "salac_berry", "shuca_berry", "sitrus_berry", "spelon_berry",
                "starf_berry", "tamato_berry", "tanga_berry", "touga_berry", "wacan_berry", "watmel_berry",
                "wepear_berry", "wiki_berry", "yache_berry"
        }) {
            config.farmingXp.put("cobblemon:" + id, 10);
        }

        for (String id : new String[]{
                "big_root", "energy_root", "medicinal_leek", "pep_up_flower", "revival_herb", "vivichoke", "vivichoke_seeds"
        }) {
            config.farmingXp.put("cobblemon:" + id, 10);
        }

        /*
         REWARDS
         */
        RewardTable miningReward = new RewardTable();
        miningReward.chance = 0.05;
        miningReward.itemId = "minecraft:diamond";
        miningReward.minAmount = 1;
        miningReward.maxAmount = 2;
        config.rewards.put("MINING_RARE_DROP", miningReward);

        RewardTable forestryReward = new RewardTable();
        forestryReward.chance = 0.03;
        forestryReward.itemId = "minecraft:apple";
        forestryReward.minAmount = 1;
        forestryReward.maxAmount = 3;
        config.rewards.put("FORESTRY_RARE_DROP", forestryReward);

        RewardTable farmingReward = new RewardTable();
        farmingReward.chance = 0.04;
        farmingReward.itemId = "minecraft:golden_carrot";
        farmingReward.minAmount = 1;
        farmingReward.maxAmount = 3;
        config.rewards.put("FARMING_RARE_DROP", farmingReward);

        return config;
    }
}
