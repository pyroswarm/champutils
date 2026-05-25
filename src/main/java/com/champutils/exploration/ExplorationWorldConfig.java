package com.champutils.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class ExplorationWorldConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/exploration_worlds.json");
    private static Data data = new Data();

    private ExplorationWorldConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded.withDefaults();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load exploration_worlds.json. Using defaults.");
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
            System.err.println("[ChampUtils] Failed to save exploration_worlds.json.");
            e.printStackTrace();
        }
    }

    public static Data get() { return data.withDefaults(); }

    public static final class Data {
        public boolean enabled = true;
        public int worldCount = 6;
        public int netherWorldCount = 2;
        public int endWorldCount = 2;
        public String worldPrefix = "multiworld:exploration";
        public String netherWorldPrefix = "multiworld:nether_exploration";
        public String endWorldPrefix = "multiworld:end_exploration";
        public int borderRadius = 10000;
        public int pregenerationRadius = 1500;
        public int chunkySpeed = 20;
        public long autoReadyAfterPregenerationMinutes = 30;
        public int spawnY = 100;
        public long wipeIntervalHours = 168;
        public long staggerHours = 28; // 168 / 6, so only one wipes at a time by default.
        public boolean requirePregenerationBeforeEntry = true;
        public boolean runWorldCommands = true;
        public long rtpAvoidWipeMinutes = 60;

        public List<String> deleteCommands = new ArrayList<>(List.of(
                "mw unload {world}",
                "mw delete {world}"
        ));
        public List<String> createCommands = new ArrayList<>(List.of(
                "mw create {world_id} NORMAL -g=NORMAL",
                "mw load {world_id}"
        ));
        public List<String> netherCreateCommands = new ArrayList<>(List.of(
                "mw create {world_id} NETHER -g=NETHER",
                "mw load {world_id}"
        ));
        public List<String> endCreateCommands = new ArrayList<>(List.of(
                "mw create {world_id} THE_END -g=THE_END",
                "mw load {world_id}"
        ));
        public List<String> chunkyPregenerationCommands = new ArrayList<>(List.of(
                "chunky pause",
                "chunky world {world}",
                "chunky center 0 0",
                "chunky radius {pregeneration_radius}",
                "chunky speed {chunky_speed}",
                "chunky start"
        ));

        private Data withDefaults() {
            if (worldCount < 1) worldCount = 6;
            if (netherWorldCount < 0) netherWorldCount = 2;
            if (endWorldCount < 0) endWorldCount = 2;
            if (worldPrefix == null || worldPrefix.isBlank()) worldPrefix = "multiworld:exploration";
            if (netherWorldPrefix == null || netherWorldPrefix.isBlank()) netherWorldPrefix = "multiworld:nether_exploration";
            if (endWorldPrefix == null || endWorldPrefix.isBlank()) endWorldPrefix = "multiworld:end_exploration";
            if (borderRadius != 10000) borderRadius = 10000;
            if (pregenerationRadius < 250) pregenerationRadius = 1500;
            if (pregenerationRadius > borderRadius) pregenerationRadius = borderRadius;
            if (chunkySpeed < 1) chunkySpeed = 20;
            if (autoReadyAfterPregenerationMinutes < 0) autoReadyAfterPregenerationMinutes = 30;
            if (spawnY < -64) spawnY = 100;
            if (wipeIntervalHours < 1) wipeIntervalHours = 168;
            if (staggerHours < 1) staggerHours = Math.max(1, wipeIntervalHours / Math.max(1, worldCount));
            if (rtpAvoidWipeMinutes < 0) rtpAvoidWipeMinutes = 60;
            if (deleteCommands == null) deleteCommands = new ArrayList<>();
            if (createCommands == null) createCommands = new ArrayList<>();
            if (createCommands.isEmpty()) {
                createCommands = new ArrayList<>(List.of(
                        "mw create {world_id} NORMAL -g=NORMAL",
                        "mw load {world_id}"
                ));
            }
            // Multiworld command arguments use the plain world id, not the namespaced dimension id.
            // Keep old config files working by rewriting the previous placeholders/syntax on load.
            createCommands.replaceAll(command -> command == null ? "" : command
                    .replace("mw create {world}", "mw create {world_id} NORMAL -g=NORMAL")
                    .replace("mw load {world}", "mw load {world_id}"));
            deleteCommands.replaceAll(command -> command == null ? "" : command
                    .replace("mw unload {world}", "mw unload {world_id}")
                    .replace("mw delete {world}", "mw delete {world_id}"));
            if (netherCreateCommands == null || netherCreateCommands.isEmpty()) {
                netherCreateCommands = new ArrayList<>(List.of(
                        "mw create {world_id} NETHER -g=NETHER",
                        "mw load {world_id}"
                ));
            }
            if (endCreateCommands == null || endCreateCommands.isEmpty()) {
                endCreateCommands = new ArrayList<>(List.of(
                        "mw create {world_id} THE_END -g=THE_END",
                        "mw load {world_id}"
                ));
            }
            netherCreateCommands.replaceAll(command -> command == null ? "" : command
                    .replace("mw create {world}", "mw create {world_id} NETHER -g=NETHER")
                    .replace("mw load {world}", "mw load {world_id}"));
            endCreateCommands.replaceAll(command -> command == null ? "" : command
                    .replace("mw create {world}", "mw create {world_id} THE_END -g=THE_END")
                    .replace("mw load {world}", "mw load {world_id}"));
            if (chunkyPregenerationCommands == null) chunkyPregenerationCommands = new ArrayList<>();
            chunkyPregenerationCommands.replaceAll(command -> command == null ? "" : command
                    .replace("chunky radius {border_radius}", "chunky radius {pregeneration_radius}"));
            return this;
        }
    }
}
