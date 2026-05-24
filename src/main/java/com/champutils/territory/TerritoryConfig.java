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

        /** Minutes a player/guild must wait after deleting a territory before creating another. */
        public int recreateCooldownMinutes = 30;

        /**
         * Territories no longer wait on Chunky pregeneration. New territories are marked READY once the
         * packed territory world has been requested/loaded through Multiworld.
         */
        public boolean requirePregenerationBeforeEntry = false;

        /** Commands are run from console only when the packed territory world is missing/unloaded. */
        public boolean runGenerationCommands = true;

        /** Kept for old configs. Territory creation no longer uses Chunky. */
        public boolean autoMarkReadyAfterGenerationRequest = true;
        public List<String> worldCreateCommands = new ArrayList<>(List.of(
                "mw create {world_id} NORMAL"
        ));
        public List<String> chunkyPregenerationCommands = new ArrayList<>();

        /** If true, /territory create uses the player's current dimension instead of packed territory worlds. */
        public boolean createPersonalInCurrentWorld = false;

        public List<String> allowedBiomePreferences = new ArrayList<>(defaultOverworldBiomes());

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
            if (worldCreateCommands == null || worldCreateCommands.isEmpty()) {
                worldCreateCommands = new ArrayList<>(List.of(
                        "mw create {world_id} NORMAL"
                ));
            }
            // Migrate older generated configs to the correct Multiworld 1.13.1 syntax. Multiworld creates by
            // plain world id (territories_1), while Minecraft stores the dimension as multiworld:territories_1.
            // A normal overworld does not need -g=NORMAL, and /mw load is not required for newly created worlds.
            worldCreateCommands.replaceAll(command -> command == null ? "" : command
                    .replace("mw create {world_key} NORMAL -g=NORMAL", "mw create {world_id} NORMAL")
                    .replace("mw create {world} NORMAL -g=NORMAL", "mw create {world_id} NORMAL")
                    .replace("mw create {world_id} NORMAL -g=NORMAL", "mw create {world_id} NORMAL")
                    .replace("mw create {world_key} NORMAL", "mw create {world_id} NORMAL")
                    .replace("mw create {world} NORMAL", "mw create {world_id} NORMAL")
                    .replace("mw load {world_key}", "mw load {world_id}")
                    .replace("mw load {world}", "mw load {world_id}"));
            worldCreateCommands.removeIf(command -> command == null || command.isBlank() || command.toLowerCase().contains(" load "));
            if (worldCreateCommands.isEmpty()) {
                worldCreateCommands = new ArrayList<>(List.of("mw create {world_id} NORMAL"));
            }
            if (chunkyPregenerationCommands == null) chunkyPregenerationCommands = new ArrayList<>();
            // Important: Chunky is intentionally not used for territories. Existing configs may still contain
            // old Chunky commands, so clear them on load to avoid territories getting stuck in GENERATING.
            chunkyPregenerationCommands.clear();
            if (defaultSpawnY < -64) defaultSpawnY = 80;
            if (borderWarningCooldownSeconds < 1) borderWarningCooldownSeconds = 5;
            if (recreateCooldownMinutes < 0) recreateCooldownMinutes = 30;
            if (allowedBiomePreferences == null || allowedBiomePreferences.isEmpty()) {
                allowedBiomePreferences = new ArrayList<>(defaultOverworldBiomes());
            } else {
                allowedBiomePreferences = new ArrayList<>(defaultOverworldBiomes());
            }
            return this;
        }
    }

    public static void setRecreateCooldownMinutes(int minutes) {
        data.withDefaults().recreateCooldownMinutes = Math.max(0, minutes);
        save();
    }

    public static List<String> defaultOverworldBiomes() {
        return List.of(
                "badlands", "bamboo_jungle", "beach", "birch_forest", "cherry_grove",
                "cold_ocean", "dark_forest", "deep_cold_ocean", "deep_dark", "deep_frozen_ocean",
                "deep_lukewarm_ocean", "deep_ocean", "desert", "dripstone_caves", "eroded_badlands",
                "flower_forest", "forest", "frozen_ocean", "frozen_peaks", "frozen_river",
                "grove", "ice_spikes", "jagged_peaks", "jungle", "lukewarm_ocean",
                "lush_caves", "mangrove_swamp", "meadow", "mushroom_fields", "ocean",
                "old_growth_birch_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga",
                "plains", "river", "savanna", "savanna_plateau", "snowy_beach",
                "snowy_plains", "snowy_slopes", "snowy_taiga", "sparse_jungle", "stony_peaks",
                "stony_shore", "sunflower_plains", "swamp", "taiga", "warm_ocean",
                "windswept_forest", "windswept_gravelly_hills", "windswept_hills",
                "windswept_savanna", "wooded_badlands"
        );
    }
}

