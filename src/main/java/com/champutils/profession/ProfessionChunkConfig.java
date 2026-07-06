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
        public String fragmentRarity = "F";
        public String essenceRarity = "";
        public double sellCredits = 1.0D;
        public int chunksPerFragment = 1;
        public int chunksPerEssence = 0;
        public int fragmentsPerTrade = 1;
        public int essencePerTrade = 0;
    }

    public static class ActivityData {
        public double activityMultiplier = 1.0D;
        public Map<String, RollData> rolls = new LinkedHashMap<>();
    }

    public static class RollData {
        /** Minimum profession level required before this chunk can roll. */
        public int minProfessionLevel = 1;
        /** Level where per-level scaling begins. Use this with minProfessionLevel for late-game chunks. */
        public int levelScalingStart = 1;
        /** Percent chance at/after the minimum level before per-level scaling is applied. */
        public double baseChancePercent = 0.01D;
        /** Percent chance added per profession level after levelScalingStart. */
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
            if ((data.fragmentRarity == null || data.fragmentRarity.isBlank()) && data.essenceRarity != null && !data.essenceRarity.isBlank()) data.fragmentRarity = data.essenceRarity;
            if (data.fragmentRarity == null || data.fragmentRarity.isBlank()) data.fragmentRarity = "F";
            data.essenceRarity = data.fragmentRarity;
            data.sellCredits = Math.max(0.0D, data.sellCredits);
            if (data.chunksPerFragment <= 0 && data.chunksPerEssence > 0) data.chunksPerFragment = data.chunksPerEssence;
            data.chunksPerFragment = Math.max(1, data.chunksPerFragment);
            data.chunksPerEssence = data.chunksPerFragment;
            if (data.fragmentsPerTrade <= 0 && data.essencePerTrade > 0) data.fragmentsPerTrade = data.essencePerTrade;
            data.fragmentsPerTrade = Math.max(1, data.fragmentsPerTrade);
            data.essencePerTrade = data.fragmentsPerTrade;
        }
        for (ActivityData activity : CONFIG.activities.values()) {
            if (activity == null) continue;
            if (activity.rolls == null) activity.rolls = new LinkedHashMap<>();
            defaults.activities.getOrDefault("MINING", new ActivityData()).rolls.forEach(activity.rolls::putIfAbsent);
            activity.activityMultiplier = Math.max(0.0D, activity.activityMultiplier);
            for (RollData roll : activity.rolls.values()) {
                if (roll == null) continue;
                roll.minProfessionLevel = Math.max(1, roll.minProfessionLevel);
                roll.levelScalingStart = Math.max(1, roll.levelScalingStart);
                roll.baseChancePercent = Math.max(0.0D, roll.baseChancePercent);
                roll.chancePerLevelPercent = Math.max(0.0D, roll.chancePerLevelPercent);
                roll.maxChancePercent = Math.max(0.0D, Math.min(100.0D, roll.maxChancePercent));
            }
        }
    }

    private static ConfigRoot defaultConfig() {
        ConfigRoot root = new ConfigRoot();
        addChunk(root, "COBBLESTONE", "Cobblestone Chunk", "F", 2.0D);
        addChunk(root, "COPPER", "Copper Chunk", "E", 10.0D);
        addChunk(root, "IRON", "Iron Chunk", "D", 25.0D);
        addChunk(root, "GOLD", "Gold Chunk", "C", 100.0D);
        addChunk(root, "DIAMOND", "Diamond Chunk", "A", 300.0D);
        addChunk(root, "NETHERITE", "Netherite Chunk", "S", 1000.0D);

        // Each chunk rolls independently. These odds are tuned around action speed:
        // farming is almost instant, forestry/mining are steady, and battling is intentionally best.
        addActivity(root, "MINING", 0.45D);
        addActivity(root, "FORESTRY", 0.70D);
        addActivity(root, "FARMING", 0.08D);
        addActivity(root, "BATTLING", 40.0D);
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

    private static void addActivity(ConfigRoot root, String id, double multiplier) {
        ActivityData activity = new ActivityData();
        activity.activityMultiplier = multiplier;
        addRoll(activity, "COBBLESTONE", 0.25D, 0.18000D, 18.0D, 1, 1);
        addRoll(activity, "COPPER", 0.050D, 0.08000D, 8.0D, 1, 1);
        addRoll(activity, "IRON", 0.010D, 0.03500D, 3.5D, 15, 15);
        addRoll(activity, "GOLD", 0.004D, 0.01800D, 1.5D, 25, 25);
        addRoll(activity, "DIAMOND", 0.0015D, 0.00800D, 0.65D, 40, 40);
        addRoll(activity, "NETHERITE", 0.040D, 0.00080D, 0.080D, 50, 50);
        root.activities.put(id, activity);
    }

    private static void addRoll(ActivityData activity, String chunk, double base, double per, double max, int minLevel, int scalingStart) {
        RollData roll = new RollData();
        roll.minProfessionLevel = minLevel;
        roll.levelScalingStart = scalingStart;
        roll.baseChancePercent = base;
        roll.chancePerLevelPercent = per;
        roll.maxChancePercent = max;
        activity.rolls.put(chunk, roll);
    }

}
