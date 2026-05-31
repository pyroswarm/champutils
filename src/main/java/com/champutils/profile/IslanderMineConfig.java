package com.champutils.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IslanderMineConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/islander_mines.json");

    private static Data data = Data.defaults();

    private IslanderMineConfig() {}

    public static Data get() {
        return data;
    }

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
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save islander_mines.json.");
            e.printStackTrace();
        }
    }

    public static final class Data {
        public boolean enabled = true;
        public int centerX = 0;
        public int centerY = -20;
        public int centerZ = 5000;
        public int radius = 48;
        public int height = 28;
        public int blocksPerTick = 1600;

        /**
         * DAILY_2AM uses ChampUtils' shared DailyResetManager reset key/time.
         * DEBUG_MINUTES is for testing servers only and uses debugResetMinutes below.
         */
        public String resetMode = "DAILY_2AM";
        public int debugResetMinutes = 30;

        /** Legacy fallback. If an old config still uses resetHours, DEBUG_MINUTES can use it. */
        public int resetHours = 24;
        public boolean allowManualReset = true;
        public Map<String, Integer> ores = new LinkedHashMap<>();

        public static Data defaults() {
            Data d = new Data();
            d.ores.put("minecraft:coal_ore", 34);
            d.ores.put("minecraft:copper_ore", 30);
            d.ores.put("minecraft:iron_ore", 24);
            d.ores.put("minecraft:gold_ore", 10);
            d.ores.put("minecraft:redstone_ore", 10);
            d.ores.put("minecraft:lapis_ore", 7);
            d.ores.put("minecraft:diamond_ore", 3);
            d.ores.put("minecraft:emerald_ore", 1);
            d.ores.put("cobblemon:fire_stone_ore", 1);
            d.ores.put("cobblemon:water_stone_ore", 1);
            d.ores.put("cobblemon:thunder_stone_ore", 1);
            d.ores.put("cobblemon:moon_stone_ore", 1);
            return d;
        }

        public void normalize() {
            if (radius < 16) radius = 16;
            if (radius > 128) radius = 128;
            if (height < 10) height = 10;
            if (height > 80) height = 80;
            if (blocksPerTick < 128) blocksPerTick = 128;
            if (blocksPerTick > 10000) blocksPerTick = 10000;
            if (resetMode == null || resetMode.isBlank()) resetMode = "DAILY_2AM";
            resetMode = resetMode.trim().toUpperCase(java.util.Locale.ROOT);
            if (!resetMode.equals("DAILY_2AM") && !resetMode.equals("DEBUG_MINUTES")) resetMode = "DAILY_2AM";
            if (debugResetMinutes < 1) debugResetMinutes = 1;
            if (debugResetMinutes > 1440) debugResetMinutes = 1440;
            if (resetHours < 1) resetHours = 1;
            if (ores == null || ores.isEmpty()) ores = defaults().ores;
            ores.entrySet().removeIf(e -> e.getKey() == null || e.getKey().isBlank() || e.getValue() == null || e.getValue() <= 0);
            if (ores.isEmpty()) ores = defaults().ores;
        }
    }
}
