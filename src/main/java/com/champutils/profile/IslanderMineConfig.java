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
        public int radius = 128;
        public int height = 144;
        public int blocksPerTick = 12000;

        /**
         * Ore pocket start chance per scanned block, out of 10,000.
         * Higher = denser mines. Islander mines are intentionally very ore-rich
         * because this is the main contained resource loop for islander profiles.
         * This still creates pockets, not random single-block ore confetti.
         */
        public int orePocketStartChancePer10000 = 4500;

        /** Shared mine dimensions are named like islander_mine_1, islander_mine_2, etc. */
        public String worldPrefix = "islander_mine_";
        public int maxPlayersPerWorld = 25;

        /** EVERY_N_HOURS is a rolling reset using resetHours. DEBUG_MINUTES is for testing only. */
        public String resetMode = "EVERY_N_HOURS";
        public int debugResetMinutes = 30;
        public int resetHours = 2;
        public boolean allowManualReset = true;

        public int spawnRoomHalfSize = 5;
        public int spawnRoomHeight = 5;
        public int protectedSpawnRadius = 7;
        public int protectedSpawnHeight = 8;

        /** Chance per 16x16x8 mine cell to place one exploration loot chest. */
        public int lootChestChancePer10000 = 180;

        /** Vanilla stripmine-style ore pockets. Values are weighted pocket-start chances, not per-block ore spam. */
        public Map<String, OreRule> ores = new LinkedHashMap<>();

        public static Data defaults() {
            Data d = new Data();
            d.blocksPerTick = 12000;
            d.orePocketStartChancePer10000 = 4500;
            d.ores.put("minecraft:coal_ore", new OreRule(300, 20, 48, 12, 136, false));
            d.ores.put("minecraft:deepslate_coal_ore", new OreRule(160, 12, 32, 0, 52, false));
            d.ores.put("minecraft:copper_ore", new OreRule(290, 20, 46, 16, 136, false));
            d.ores.put("minecraft:deepslate_copper_ore", new OreRule(170, 12, 32, 0, 60, false));
            d.ores.put("minecraft:iron_ore", new OreRule(340, 20, 48, 4, 132, false));
            d.ores.put("minecraft:deepslate_iron_ore", new OreRule(260, 14, 40, 0, 76, false));
            d.ores.put("minecraft:gold_ore", new OreRule(190, 12, 34, 0, 88, false));
            d.ores.put("minecraft:deepslate_gold_ore", new OreRule(170, 10, 30, 0, 64, false));
            d.ores.put("minecraft:redstone_ore", new OreRule(220, 12, 36, 0, 64, false));
            d.ores.put("minecraft:deepslate_redstone_ore", new OreRule(210, 12, 34, 0, 56, false));
            d.ores.put("minecraft:lapis_ore", new OreRule(160, 10, 30, 0, 84, false));
            d.ores.put("minecraft:deepslate_lapis_ore", new OreRule(145, 8, 26, 0, 58, false));
            d.ores.put("minecraft:diamond_ore", new OreRule(95, 6, 18, 0, 48, false));
            d.ores.put("minecraft:deepslate_diamond_ore", new OreRule(90, 5, 16, 0, 42, false));
            d.ores.put("minecraft:emerald_ore", new OreRule(55, 4, 12, 4, 96, false));
            d.ores.put("minecraft:deepslate_emerald_ore", new OreRule(42, 3, 10, 0, 44, false));
            d.ores.put("minecraft:nether_quartz_ore", new OreRule(210, 14, 38, 0, 110, false));
            d.ores.put("minecraft:nether_gold_ore", new OreRule(170, 10, 30, 0, 90, false));
            d.ores.put("minecraft:ancient_debris", new OreRule(8, 1, 1, 0, 28, true));
            return d;
        }

        public void normalize() {
            // Upgrade older small islander mines to the launch-size shared mine.
            if (radius <= 72) radius = 128;
            if (radius < 24) radius = 24;
            if (radius > 160) radius = 160;
            if (height <= 96) height = 144;
            if (height < 32) height = 32;
            if (height > 160) height = 160;
            if (centerY < -62) centerY = -62;
            if (centerY + height > 319) height = Math.max(32, 319 - centerY);
            if (blocksPerTick < 512) blocksPerTick = 512;
            if (blocksPerTick > 25000) blocksPerTick = 25000;
            if (orePocketStartChancePer10000 <= 0) orePocketStartChancePer10000 = 4500;
            // Older configs were capped at 1000, which made the islander mine feel nearly empty.
            // Treat those legacy values as under-tuned and upgrade them to the new rich mine default.
            if (orePocketStartChancePer10000 <= 1000) orePocketStartChancePer10000 = 4500;
            if (orePocketStartChancePer10000 < 25) orePocketStartChancePer10000 = 25;
            if (orePocketStartChancePer10000 > 9000) orePocketStartChancePer10000 = 9000;
            if (worldPrefix == null || worldPrefix.isBlank()) worldPrefix = "islander_mine_";
            worldPrefix = worldPrefix.trim().toLowerCase(Locale.ROOT);
            if (maxPlayersPerWorld < 1) maxPlayersPerWorld = 1;
            if (maxPlayersPerWorld > 100) maxPlayersPerWorld = 100;
            if (resetMode == null || resetMode.isBlank()) resetMode = "EVERY_N_HOURS";
            resetMode = resetMode.trim().toUpperCase(Locale.ROOT);
            boolean legacyTwentyFourHourMode = resetMode.equals("EVERY_24_HOURS");
            if (legacyTwentyFourHourMode) {
                resetMode = "EVERY_N_HOURS";
                if (resetHours == 24) resetHours = 2;
            }
            if (!resetMode.equals("EVERY_N_HOURS") && !resetMode.equals("DAILY_2AM") && !resetMode.equals("DEBUG_MINUTES")) resetMode = "EVERY_N_HOURS";
            if (debugResetMinutes < 1) debugResetMinutes = 1;
            if (debugResetMinutes > 1440) debugResetMinutes = 1440;
            if (resetHours < 2) resetHours = 2;
            if (resetHours > 168) resetHours = 168;
            if (spawnRoomHalfSize < 3) spawnRoomHalfSize = 3;
            if (spawnRoomHalfSize > 12) spawnRoomHalfSize = 12;
            if (spawnRoomHeight < 3) spawnRoomHeight = 3;
            if (spawnRoomHeight > 12) spawnRoomHeight = 12;
            if (protectedSpawnRadius < spawnRoomHalfSize + 1) protectedSpawnRadius = spawnRoomHalfSize + 1;
            if (protectedSpawnRadius > 24) protectedSpawnRadius = 24;
            if (protectedSpawnHeight < spawnRoomHeight + 1) protectedSpawnHeight = spawnRoomHeight + 1;
            if (protectedSpawnHeight > 24) protectedSpawnHeight = 24;
            if (lootChestChancePer10000 < 0) lootChestChancePer10000 = 0;
            if (lootChestChancePer10000 > 1000) lootChestChancePer10000 = 1000;
            if (ores == null || ores.isEmpty()) ores = defaults().ores;
            ores.entrySet().removeIf(e -> e.getKey() != null && e.getKey().startsWith("cobblemon:") && e.getKey().endsWith("_ore"));
            OreRule oldQuartz = ores.remove("minecraft:quartz_ore");
            if (oldQuartz != null) ores.putIfAbsent("minecraft:nether_quartz_ore", oldQuartz);
            for (Map.Entry<String, OreRule> entry : defaults().ores.entrySet()) {
                ores.putIfAbsent(entry.getKey(), entry.getValue());
            }
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
            if (maxPocketSize > 64) maxPocketSize = 64;
            if (singleOnly) { minPocketSize = 1; maxPocketSize = 1; }
            if (minLocalY < 0) minLocalY = 0;
            if (maxLocalY < minLocalY) maxLocalY = minLocalY;
        }
    }
}
