package com.champutils.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class IslanderMineConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/islander_mines.json");

    private static Data data = Data.defaults();

    private IslanderMineConfig() {}

    public static Data get() { return data; }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            if (!FILE.exists()) {
                data = Data.defaults();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? Data.defaults() : loaded;
            }
            data.normalize();
            save();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load islander_mines.json; using defaults.");
            e.printStackTrace();
            data = Data.defaults();
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(data, writer); }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save islander_mines.json.");
            e.printStackTrace();
        }
    }

    public static final class Data {
        public boolean enabled = true;
        public int centerX = 0;
        public int centerY = -60;
        public int centerZ = 0;
        public int radius = 72;
        public int height = 96;
        public int blocksPerTick = 5000;

        /** Shared mine dimensions are named like islander_mine_1, islander_mine_2, etc. */
        public String worldPrefix = "islander_mine_";
        public int maxPlayersPerWorld = 25;

        /** EVERY_24_HOURS is a rolling 24 hour reset. DEBUG_MINUTES is for testing only. */
        public String resetMode = "EVERY_24_HOURS";
        public int debugResetMinutes = 30;
        public int resetHours = 24;
        public boolean allowManualReset = true;

        public int spawnRoomHalfSize = 5;
        public int spawnRoomHeight = 5;
        public int protectedSpawnRadius = 7;
        public int protectedSpawnHeight = 8;

        /** Vanilla stripmine-style ore pockets. Values are weighted pocket-start chances, not per-block ore spam. */
        public Map<String, OreRule> ores = new LinkedHashMap<>();

        public static Data defaults() {
            Data d = new Data();
            d.ores.put("minecraft:coal_ore", new OreRule(34, 5, 12, 18, 95, false));
            d.ores.put("minecraft:copper_ore", new OreRule(26, 4, 10, 28, 90, false));
            d.ores.put("minecraft:iron_ore", new OreRule(30, 4, 9, 8, 84, false));
            d.ores.put("minecraft:gold_ore", new OreRule(13, 3, 8, 0, 44, false));
            d.ores.put("minecraft:redstone_ore", new OreRule(15, 4, 8, 0, 36, false));
            d.ores.put("minecraft:lapis_ore", new OreRule(8, 3, 7, 0, 42, false));
            d.ores.put("minecraft:diamond_ore", new OreRule(5, 1, 5, 0, 28, false));
            d.ores.put("minecraft:emerald_ore", new OreRule(2, 1, 3, 0, 24, false));
            d.ores.put("minecraft:quartz_ore", new OreRule(9, 2, 6, 0, 42, false));
            d.ores.put("minecraft:ancient_debris", new OreRule(1, 1, 1, 0, 18, true));
            d.ores.put("cobblemon:fire_stone_ore", new OreRule(2, 1, 4, 0, 34, false));
            d.ores.put("cobblemon:water_stone_ore", new OreRule(2, 1, 4, 0, 34, false));
            d.ores.put("cobblemon:thunder_stone_ore", new OreRule(2, 1, 4, 0, 34, false));
            d.ores.put("cobblemon:moon_stone_ore", new OreRule(2, 1, 4, 0, 28, false));
            return d;
        }

        public void normalize() {
            if (radius < 24) radius = 24;
            if (radius > 160) radius = 160;
            if (height < 32) height = 32;
            if (height > 160) height = 160;
            if (centerY < -62) centerY = -62;
            if (centerY + height > 319) height = Math.max(32, 319 - centerY);
            if (blocksPerTick < 512) blocksPerTick = 512;
            if (blocksPerTick > 25000) blocksPerTick = 25000;
            if (worldPrefix == null || worldPrefix.isBlank()) worldPrefix = "islander_mine_";
            worldPrefix = worldPrefix.trim().toLowerCase(Locale.ROOT);
            if (maxPlayersPerWorld < 1) maxPlayersPerWorld = 1;
            if (maxPlayersPerWorld > 100) maxPlayersPerWorld = 100;
            if (resetMode == null || resetMode.isBlank()) resetMode = "EVERY_24_HOURS";
            resetMode = resetMode.trim().toUpperCase(Locale.ROOT);
            if (!resetMode.equals("EVERY_24_HOURS") && !resetMode.equals("DAILY_2AM") && !resetMode.equals("DEBUG_MINUTES")) resetMode = "EVERY_24_HOURS";
            if (debugResetMinutes < 1) debugResetMinutes = 1;
            if (debugResetMinutes > 1440) debugResetMinutes = 1440;
            if (resetHours < 1) resetHours = 24;
            if (resetHours > 168) resetHours = 168;
            if (spawnRoomHalfSize < 3) spawnRoomHalfSize = 3;
            if (spawnRoomHalfSize > 12) spawnRoomHalfSize = 12;
            if (spawnRoomHeight < 3) spawnRoomHeight = 3;
            if (spawnRoomHeight > 12) spawnRoomHeight = 12;
            if (protectedSpawnRadius < spawnRoomHalfSize + 1) protectedSpawnRadius = spawnRoomHalfSize + 1;
            if (protectedSpawnRadius > 24) protectedSpawnRadius = 24;
            if (protectedSpawnHeight < spawnRoomHeight + 1) protectedSpawnHeight = spawnRoomHeight + 1;
            if (protectedSpawnHeight > 24) protectedSpawnHeight = 24;
            if (ores == null || ores.isEmpty()) ores = defaults().ores;
            ores.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue().weight <= 0);
            if (ores.isEmpty()) ores = defaults().ores;
            for (OreRule rule : ores.values()) rule.normalize();
        }
    }

    public static final class OreRule {
        public int weight;
        public int minPocketSize;
        public int maxPocketSize;
        public int minLocalY;
        public int maxLocalY;
        public boolean singleOnly;

        public OreRule() {}

        public OreRule(int weight, int minPocketSize, int maxPocketSize, int minLocalY, int maxLocalY, boolean singleOnly) {
            this.weight = weight;
            this.minPocketSize = minPocketSize;
            this.maxPocketSize = maxPocketSize;
            this.minLocalY = minLocalY;
            this.maxLocalY = maxLocalY;
            this.singleOnly = singleOnly;
        }

        public void normalize() {
            if (weight < 1) weight = 1;
            if (weight > 1000) weight = 1000;
            if (minPocketSize < 1) minPocketSize = 1;
            if (maxPocketSize < minPocketSize) maxPocketSize = minPocketSize;
            if (maxPocketSize > 32) maxPocketSize = 32;
            if (singleOnly) { minPocketSize = 1; maxPocketSize = 1; }
            if (minLocalY < 0) minLocalY = 0;
            if (maxLocalY < minLocalY) maxLocalY = minLocalY;
        }
    }
}
