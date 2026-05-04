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
        public Map<String, Integer> fishingXp = new HashMap<>();
        public Map<String, Integer> farmingXp = new HashMap<>();
        public Map<String, RewardTable> rewards = new HashMap<>();
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDefault(File file) {
        try {
            ProfessionSettings config =
                    new ProfessionSettings();

            /*
             MINING
             */
            config.miningXp.put("minecraft:coal_ore", 10);
            config.miningXp.put("minecraft:iron_ore", 20);
            config.miningXp.put("minecraft:gold_ore", 25);
            config.miningXp.put("minecraft:diamond_ore", 50);
            config.miningXp.put("minecraft:emerald_ore", 60);
            config.miningXp.put("minecraft:ancient_debris", 100);

            /*
             COBBLEMON ORES
             Verify exact IDs if needed
             */
            config.miningXp.put("cobblemon:fossil_ore", 100);
            config.miningXp.put("cobblemon:fire_stone_ore", 75);
            config.miningXp.put("cobblemon:water_stone_ore", 75);
            config.miningXp.put("cobblemon:leaf_stone_ore", 75);
            config.miningXp.put("cobblemon:thunder_stone_ore", 75);

            /*
             VANILLA LOGS
             */
            config.forestryXp.put("minecraft:oak_log", 5);
            config.forestryXp.put("minecraft:spruce_log", 5);
            config.forestryXp.put("minecraft:birch_log", 5);
            config.forestryXp.put("minecraft:jungle_log", 7);
            config.forestryXp.put("minecraft:dark_oak_log", 8);
            config.forestryXp.put("minecraft:acacia_log", 6);
            config.forestryXp.put("minecraft:mangrove_log", 8);
            config.forestryXp.put("minecraft:cherry_log", 8);

            config.forestryXp.put("minecraft:crimson_stem", 10);
            config.forestryXp.put("minecraft:warped_stem", 10);

            /*
             COBBLEMON APRICORN TREES
             Verify exact block IDs
             */
            config.forestryXp.put("cobblemon:apricorn_log", 12);
            config.forestryXp.put("cobblemon:apricorn_wood", 12);

            config.forestryXp.put("cobblemon:black_apricorn_log", 12);
            config.forestryXp.put("cobblemon:blue_apricorn_log", 12);
            config.forestryXp.put("cobblemon:green_apricorn_log", 12);
            config.forestryXp.put("cobblemon:pink_apricorn_log", 12);
            config.forestryXp.put("cobblemon:red_apricorn_log", 12);
            config.forestryXp.put("cobblemon:white_apricorn_log", 12);
            config.forestryXp.put("cobblemon:yellow_apricorn_log", 12);

            /*
             Battle XP
             */
            config.battleXp.put("wild", 10);
            config.battleXp.put("npc", 20);
            config.battleXp.put("world_boss", 150);
            config.battleXp.put("profession", 25);

            /*
             Fishing XP
             */
            config.fishingXp.put("default", 15);

            /*
             Farming XP
             */
            config.farmingXp.put("default", 10);

            /*
             Rewards
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

            RewardTable fishingReward = new RewardTable();
            fishingReward.chance = 0.04;
            fishingReward.itemId = "minecraft:nautilus_shell";
            fishingReward.minAmount = 1;
            fishingReward.maxAmount = 2;
            config.rewards.put("FISHING_RARE_DROP", fishingReward);

            RewardTable farmingReward = new RewardTable();
            farmingReward.chance = 0.04;
            farmingReward.itemId = "minecraft:golden_carrot";
            farmingReward.minAmount = 1;
            farmingReward.maxAmount = 3;
            config.rewards.put("FARMING_RARE_DROP", farmingReward);

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}