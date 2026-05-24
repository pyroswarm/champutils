package com.champutils.territory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class TerritoryConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/territories.json");

    private static Data data = new Data();

    private TerritoryConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded.withDefaults();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load territories.json. Using defaults.");
            e.printStackTrace();
            data = new Data();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data.withDefaults(), writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save territories.json.");
            e.printStackTrace();
        }
    }

    public static Data get() {
        return data.withDefaults();
    }

    public static final class Data {
        public boolean enabled = true;
        public String serverIdOverride = "";

        public String defaultPersonalWorld = "multiworld:territories";
        public String defaultGuildWorld = "multiworld:guild_territories";

        /** Radius from center. 500 = 1000 block diameter. */
        public int defaultRadius = 500;
        public int centerSpacing = 6000;
        public int gridWidth = 100;
        public int defaultSpawnY = 80;
        public int borderWarningCooldownSeconds = 5;

        /** If true, /territory create uses the player's current dimension instead of defaultPersonalWorld. */
        public boolean createPersonalInCurrentWorld = false;

        public List<String> allowedBiomePreferences = new ArrayList<>(List.of(
                "plains", "forest", "taiga", "snowy", "desert", "jungle", "savanna",
                "cherry_grove", "badlands", "swamp", "mountains"
        ));

        private Data withDefaults() {
            if (defaultPersonalWorld == null || defaultPersonalWorld.isBlank()) defaultPersonalWorld = "multiworld:territories";
            if (defaultGuildWorld == null || defaultGuildWorld.isBlank()) defaultGuildWorld = "multiworld:guild_territories";
            if (defaultRadius < 64) defaultRadius = 500;
            if (centerSpacing < (defaultRadius * 2 + 1000)) centerSpacing = defaultRadius * 2 + 4000;
            if (gridWidth < 1) gridWidth = 100;
            if (defaultSpawnY < -64) defaultSpawnY = 80;
            if (borderWarningCooldownSeconds < 1) borderWarningCooldownSeconds = 5;
            if (allowedBiomePreferences == null || allowedBiomePreferences.isEmpty()) {
                allowedBiomePreferences = new ArrayList<>(List.of("plains", "forest", "taiga", "snowy", "desert", "jungle", "savanna", "cherry_grove", "badlands", "swamp", "mountains"));
            }
            return this;
        }
    }
}
