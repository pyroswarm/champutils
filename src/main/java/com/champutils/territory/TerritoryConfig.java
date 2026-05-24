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

        /**
         * Worlds are packed instead of making one Minecraft dimension per territory.
         * Example with defaults:
         *   multiworld:territories_1 has slots 0-99
         *   multiworld:territories_2 has slots 0-99
         */
        public String personalWorldPrefix = "multiworld:territories";
        public String guildWorldPrefix = "multiworld:guild_territories";

        /** Kept for old configs/database rows. New territories use the prefixes above. */
        public String defaultPersonalWorld = "multiworld:territories_1";
        public String defaultGuildWorld = "multiworld:guild_territories_1";

        public int territoriesPerWorld = 100;
        public int slotGridWidth = 10;

        /** Radius from center. 500 = 1000 block diameter. */
        public int defaultRadius = 500;
        public int centerSpacing = 6000;
        public int gridWidth = 10;
        public int defaultSpawnY = 80;
        public int borderWarningCooldownSeconds = 5;

        /**
         * If true, players cannot teleport into a newly allocated territory until an op marks it ready.
         * This prevents players entering terrain before Chunky finishes.
         */
        public boolean requirePregenerationBeforeEntry = true;

        /** Commands are run from console after a new territory is allocated. */
        public boolean runGenerationCommands = true;
        public List<String> worldCreateCommands = new ArrayList<>(List.of(
                "mw create {world}",
                "mw load {world}"
        ));
        public List<String> chunkyPregenerationCommands = new ArrayList<>(List.of(
                "chunky world {world}",
                "chunky center {center_x} {center_z}",
                "chunky radius {radius}",
                "chunky start"
        ));

        /** If true, /territory create uses the player's current dimension instead of packed territory worlds. */
        public boolean createPersonalInCurrentWorld = false;

        public List<String> allowedBiomePreferences = new ArrayList<>(List.of(
                "plains", "forest", "taiga", "snowy", "desert", "jungle", "savanna",
                "cherry_grove", "badlands", "swamp", "mountains"
        ));

        private Data withDefaults() {
            if (personalWorldPrefix == null || personalWorldPrefix.isBlank()) personalWorldPrefix = "multiworld:territories";
            if (guildWorldPrefix == null || guildWorldPrefix.isBlank()) guildWorldPrefix = "multiworld:guild_territories";
            if (defaultPersonalWorld == null || defaultPersonalWorld.isBlank()) defaultPersonalWorld = personalWorldPrefix + "_1";
            if (defaultGuildWorld == null || defaultGuildWorld.isBlank()) defaultGuildWorld = guildWorldPrefix + "_1";
            if (territoriesPerWorld < 1) territoriesPerWorld = 100;
            if (slotGridWidth < 1) slotGridWidth = (int) Math.ceil(Math.sqrt(territoriesPerWorld));
            if (defaultRadius < 64) defaultRadius = 500;
            if (centerSpacing < (defaultRadius * 2 + 1000)) centerSpacing = defaultRadius * 2 + 4000;
            if (gridWidth < 1) gridWidth = slotGridWidth;
            if (worldCreateCommands == null) worldCreateCommands = new ArrayList<>();
            if (chunkyPregenerationCommands == null) chunkyPregenerationCommands = new ArrayList<>();
            if (defaultSpawnY < -64) defaultSpawnY = 80;
            if (borderWarningCooldownSeconds < 1) borderWarningCooldownSeconds = 5;
            if (allowedBiomePreferences == null || allowedBiomePreferences.isEmpty()) {
                allowedBiomePreferences = new ArrayList<>(List.of("plains", "forest", "taiga", "snowy", "desert", "jungle", "savanna", "cherry_grove", "badlands", "swamp", "mountains"));
            }
            return this;
        }
    }
}
