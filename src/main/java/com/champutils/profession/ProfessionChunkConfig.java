package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionChunkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/profession_chunks.json");

    public static ConfigRoot CONFIG = new ConfigRoot();

    private ProfessionChunkConfig() {}

    public static class ConfigRoot {
        public boolean enabled = true;
        public boolean announceFinds = true;
        public Map<String, ChunkData> chunks = new LinkedHashMap<>();
        public Map<String, ActivityData> activities = new LinkedHashMap<>();
    }

    public static class ChunkData {
        public String displayName = "Cobblestone Chunk";
        public String fragmentRarity = "COMMON";
        public double sellCredits = 1.0D;
        public int chunksPerFragment = 1;
        public int fragmentsPerTrade = 1;
    }

    public static class ActivityData {
        public double activityMultiplier = 1.0D;
        public Map<String, RollData> rolls = new LinkedHashMap<>();
    }

    public static class RollData {
        /** Percent chance at profession level 1. */
        public double baseChancePercent = 0.01D;
        /** Percent chance added per profession level. */
        public double chancePerLevelPercent = 0.0D;
        /** Hard percent cap after level scaling. */
        public double maxChancePercent = 100.0D;
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            if (!FILE.exists()) {
                CONFIG = defaultConfig();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                ConfigRoot loaded = GSON.fromJson(reader, ConfigRoot.class);
                CONFIG = loaded == null ? defaultConfig() : loaded;
            }
            sanitize();
            save();
            System.out.println("[ChampUtils] Loaded profession chunk config.");
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = defaultConfig();
        }
    }

    public static void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void sanitize() {
        if (CONFIG == null) CONFIG = defaultConfig();
        if (CONFIG.chunks == null) CONFIG.chunks = new LinkedHashMap<>();
        if (CONFIG.activities == null) CONFIG.activities = new LinkedHashMap<>();
        ConfigRoot defaults = defaultConfig();
        defaults.chunks.forEach(CONFIG.chunks::putIfAbsent);
        defaults.activities.forEach(CONFIG.activities::putIfAbsent);
        for (ChunkData data : CONFIG.chunks.values()) {
            if (data == null) continue;
            if (data.displayName == null || data.displayName.isBlank()) data.displayName = "Chunk";
            if (data.fragmentRarity == null || data.fragmentRarity.isBlank()) data.fragmentRarity = "COMMON";
            data.sellCredits = Math.max(0.0D, data.sellCredits);
            data.chunksPerFragment = Math.max(1, data.chunksPerFragment);
            data.fragmentsPerTrade = Math.max(1, data.fragmentsPerTrade);
        }
        for (ActivityData activity : CONFIG.activities.values()) {
            if (activity == null) continue;
            if (activity.rolls == null) activity.rolls = new LinkedHashMap<>();
            defaults.activities.getOrDefault("MINING", new ActivityData()).rolls.forEach(activity.rolls::putIfAbsent);
            activity.activityMultiplier = Math.max(0.0D, activity.activityMultiplier);
            for (RollData roll : activity.rolls.values()) {
                if (roll == null) continue;
                roll.baseChancePercent = Math.max(0.0D, roll.baseChancePercent);
                roll.chancePerLevelPercent = Math.max(0.0D, roll.chancePerLevelPercent);
                roll.maxChancePercent = Math.max(0.0D, Math.min(100.0D, roll.maxChancePercent));
            }
        }
    }

    private static ConfigRoot defaultConfig() {
        ConfigRoot root = new ConfigRoot();
        addChunk(root, "COBBLESTONE", "Cobblestone Chunk", "COMMON", 1.0D);
        addChunk(root, "COPPER", "Copper Chunk", "UNCOMMON", 5.0D);
        addChunk(root, "IRON", "Iron Chunk", "RARE", 15.0D);
        addChunk(root, "GOLD", "Gold Chunk", "EPIC", 50.0D);
        addChunk(root, "DIAMOND", "Diamond Chunk", "LEGENDARY", 150.0D);
        addChunk(root, "NETHERITE", "Netherite Chunk", "MYTHIC", 500.0D);

        // Each chunk rolls independently. Level 100 mining roughly lands at 25/10/5/2/1/0.5 percent.
        addActivity(root, "MINING", 1.0D, 0.10D, 0.2515D, 25.0D, 0.01D, 0.1010D, 10.0D, 0.005D, 0.0505D, 5.0D, 0.002D, 0.0202D, 2.0D, 0.001D, 0.0101D, 1.0D, 0.0005D, 0.00505D, 0.5D);
        addActivity(root, "FORESTRY", 0.45D, 0.10D, 0.2515D, 25.0D, 0.01D, 0.1010D, 10.0D, 0.005D, 0.0505D, 5.0D, 0.002D, 0.0202D, 2.0D, 0.001D, 0.0101D, 1.0D, 0.0005D, 0.00505D, 0.5D);
        addActivity(root, "FARMING", 0.20D, 0.10D, 0.2515D, 25.0D, 0.01D, 0.1010D, 10.0D, 0.005D, 0.0505D, 5.0D, 0.002D, 0.0202D, 2.0D, 0.001D, 0.0101D, 1.0D, 0.0005D, 0.00505D, 0.5D);
        addActivity(root, "BATTLING", 0.35D, 0.10D, 0.2515D, 25.0D, 0.01D, 0.1010D, 10.0D, 0.005D, 0.0505D, 5.0D, 0.002D, 0.0202D, 2.0D, 0.001D, 0.0101D, 1.0D, 0.0005D, 0.00505D, 0.5D);
        return root;
    }

    private static void addChunk(ConfigRoot root, String id, String name, String rarity, double sell) {
        ChunkData data = new ChunkData();
        data.displayName = name;
        data.fragmentRarity = rarity;
        data.sellCredits = sell;
        data.chunksPerFragment = 1;
        data.fragmentsPerTrade = 1;
        root.chunks.put(id, data);
    }

    private static void addActivity(ConfigRoot root, String id, double multiplier,
                                    double cBase, double cPer, double cMax,
                                    double cuBase, double cuPer, double cuMax,
                                    double iBase, double iPer, double iMax,
                                    double gBase, double gPer, double gMax,
                                    double dBase, double dPer, double dMax,
                                    double nBase, double nPer, double nMax) {
        ActivityData activity = new ActivityData();
        activity.activityMultiplier = multiplier;
        addRoll(activity, "COBBLESTONE", cBase, cPer, cMax);
        addRoll(activity, "COPPER", cuBase, cuPer, cuMax);
        addRoll(activity, "IRON", iBase, iPer, iMax);
        addRoll(activity, "GOLD", gBase, gPer, gMax);
        addRoll(activity, "DIAMOND", dBase, dPer, dMax);
        addRoll(activity, "NETHERITE", nBase, nPer, nMax);
        root.activities.put(id, activity);
    }

    private static void addRoll(ActivityData activity, String chunk, double base, double per, double max) {
        RollData roll = new RollData();
        roll.baseChancePercent = base;
        roll.chancePerLevelPercent = per;
        roll.maxChancePercent = max;
        activity.rolls.put(chunk, roll);
    }
}
