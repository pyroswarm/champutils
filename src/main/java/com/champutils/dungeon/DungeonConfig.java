package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DungeonConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils", "champ_dungeons.json");

    public static final String DEFAULT_DUNGEON_WORLD = "multiworld:spawn1";

    public static Map<String, DungeonData> DUNGEONS = new LinkedHashMap<>();

    private DungeonConfig() {
    }

    public static class DungeonData {
        public String displayName = "Common Dungeon";
        public String theme = "forest";
        public String rarity = "COMMON";
        public String keyId = "common_dungeon_key";
        public int trainerCount = 3;
        public boolean soloOnly = true;
        public String world = DEFAULT_DUNGEON_WORLD;
        public double x = 0.5D;
        public double y = 80.0D;
        public double z = 0.5D;
        public float yaw = 0.0F;
        public float pitch = 0.0F;
        public Map<String, SpawnData> trainerSpawns = new LinkedHashMap<>();
    }

    public static class SpawnData {
        public String world = DEFAULT_DUNGEON_WORLD;
        public double x = 0.5D;
        public double y = 80.0D;
        public double z = 0.5D;
        public float yaw = 0.0F;
        public float pitch = 0.0F;
    }

    public static class ConfigRoot {
        public Map<String, DungeonData> dungeons = new LinkedHashMap<>();
    }

    public static void load() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = FILE;
            if (!file.exists()) {
                ConfigRoot root = createDefaultRoot();
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(root, writer);
                }
            }

            try (FileReader reader = new FileReader(file)) {
                ConfigRoot root = GSON.fromJson(reader, ConfigRoot.class);
                DUNGEONS.clear();
                if (root != null && root.dungeons != null) {
                    DUNGEONS.putAll(root.dungeons);
                    normalizeLoadedWorlds();
                }
            }

            if (DUNGEONS.isEmpty()) {
                DUNGEONS.putAll(createDefaultRoot().dungeons);
                normalizeLoadedWorlds();
            }

            System.out.println("[ChampUtils] Loaded " + DUNGEONS.size() + " dungeons. Default dungeon world: " + DEFAULT_DUNGEON_WORLD);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void save() {
        try {
            File dir = new File("config/champutils");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            ConfigRoot root = new ConfigRoot();
            root.dungeons.putAll(DUNGEONS);

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            System.out.println("[ChampUtils] Failed to save champ_dungeons.json.");
            e.printStackTrace();
        }
    }

    public static void setTeleport(String dungeonId, String world, double x, double y, double z, float yaw, float pitch) {
        DungeonData data = DUNGEONS.get(dungeonId);
        if (data == null) {
            return;
        }

        data.world = world;
        data.x = x;
        data.y = y;
        data.z = z;
        data.yaw = yaw;
        data.pitch = pitch;
        save();
    }

    public static void setTrainerSpawn(String dungeonId, int wave, String world, double x, double y, double z, float yaw, float pitch) {
        DungeonData data = DUNGEONS.get(dungeonId);
        if (data == null) {
            return;
        }

        if (data.trainerSpawns == null) {
            data.trainerSpawns = new LinkedHashMap<>();
        }

        SpawnData spawn = new SpawnData();
        spawn.world = world;
        spawn.x = x;
        spawn.y = y;
        spawn.z = z;
        spawn.yaw = yaw;
        spawn.pitch = pitch;

        data.trainerSpawns.put(String.valueOf(Math.max(1, wave)), spawn);
        save();
    }

    public static SpawnData getTrainerSpawn(String dungeonId, int wave) {
        DungeonData data = DUNGEONS.get(dungeonId);
        if (data == null || data.trainerSpawns == null) {
            return null;
        }

        return data.trainerSpawns.get(String.valueOf(Math.max(1, wave)));
    }

    private static void normalizeLoadedWorlds() {
        for (DungeonData data : DUNGEONS.values()) {
            if (data == null) {
                continue;
            }
            if (data.world == null || data.world.isBlank() || data.world.equalsIgnoreCase("minecraft:overworld") || data.world.equalsIgnoreCase("multiworld:instances")) {
                data.world = DEFAULT_DUNGEON_WORLD;
            }

            if (data.trainerSpawns == null) {
                data.trainerSpawns = new LinkedHashMap<>();
            }

            for (SpawnData spawn : data.trainerSpawns.values()) {
                if (spawn != null && (spawn.world == null || spawn.world.isBlank() || spawn.world.equalsIgnoreCase("multiworld:instances"))) {
                    spawn.world = DEFAULT_DUNGEON_WORLD;
                }
            }
        }
    }

    private static ConfigRoot createDefaultRoot() {
        ConfigRoot root = new ConfigRoot();
        addDefault(root, "verdant_ruins", "Verdant Ruins", "forest", "COMMON", "common_dungeon_key", 3, 0.5, 80, 0.5);
        addDefault(root, "ember_cavern", "Ember Cavern", "fire", "UNCOMMON", "uncommon_dungeon_key", 3, 20.5, 80, 0.5);
        addDefault(root, "tidal_sanctum", "Tidal Sanctum", "water", "RARE", "rare_dungeon_key", 4, 40.5, 80, 0.5);
        addDefault(root, "storm_spire", "Storm Spire", "electric", "EPIC", "epic_dungeon_key", 4, 60.5, 80, 0.5);
        addDefault(root, "dragon_altar", "Dragon Altar", "dragon", "LEGENDARY", "legendary_dungeon_key", 5, 80.5, 80, 0.5);
        addDefault(root, "void_nexus", "Void Nexus", "mythic", "MYTHIC", "mythic_dungeon_key", 6, 100.5, 80, 0.5);
        return root;
    }

    private static void addDefault(ConfigRoot root, String id, String name, String theme, String rarity, String keyId, int trainerCount, double x, double y, double z) {
        DungeonData data = new DungeonData();
        data.displayName = name;
        data.theme = theme;
        data.rarity = rarity;
        data.keyId = keyId;
        data.trainerCount = trainerCount;
        data.x = x;
        data.y = y;
        data.z = z;
        data.world = DEFAULT_DUNGEON_WORLD;
        root.dungeons.put(id, data);
    }
}
