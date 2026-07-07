package com.champutils.music;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MusicConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Root ROOT = new Root();

    private MusicConfig() {}

    public static final class Root {
        public boolean enabled = true;
        public boolean debug = false;
        public int checkIntervalSeconds = 2;
        public String defaultTrack = "exploration_default";
        public String victoryTrack = "victory";
        public int victorySeconds = 7;
        public Map<String, Track> tracks = defaultTracks();
        public List<Region> regions = defaultRegions();
        public Map<String, String> battleTracks = defaultBattleTracks();
    }

    public static final class Track {
        public String sound;
        public float volume = 0.65F;
        public float pitch = 1.0F;
        public int loopSeconds = 120;
        public int priority = 0;

        public Track() {}
        public Track(String sound, float volume, int loopSeconds, int priority) {
            this.sound = sound;
            this.volume = volume;
            this.loopSeconds = loopSeconds;
            this.priority = priority;
        }
    }

    public static final class Region {
        public String id;
        public String track;
        public String dimension = "minecraft:overworld";
        public int minX;
        public int minY = -64;
        public int minZ;
        public int maxX;
        public int maxY = 320;
        public int maxZ;
        public int priority = 10;

        public Region() {}
        public Region(String id, String track, int minX, int minZ, int maxX, int maxZ, int priority) {
            this.id = id;
            this.track = track;
            this.minX = minX;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxZ = maxZ;
            this.priority = priority;
        }
    }

    public static void load() {
        try {
            File dir = new File("config/champutils/music");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "music_config.json");
            if (!file.exists()) {
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(new Root(), writer);
                }
            }
            Root loaded;
            try (FileReader reader = new FileReader(file)) {
                loaded = GSON.fromJson(reader, Root.class);
            }
            if (loaded == null) loaded = new Root();
            sanitize(loaded);
            ROOT = loaded;
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(ROOT, writer);
            }
            System.out.println("[ChampUtils] Loaded music_config.json.");
        } catch (Exception e) {
            e.printStackTrace();
            ROOT = new Root();
        }
    }

    public static Track getTrack(String key) {
        if (key == null) return null;
        return ROOT.tracks.get(normalize(key));
    }

    public static String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static void sanitize(Root root) {
        if (root.tracks == null || root.tracks.isEmpty()) root.tracks = defaultTracks();
        if (root.regions == null) root.regions = defaultRegions();
        if (root.battleTracks == null || root.battleTracks.isEmpty()) root.battleTracks = defaultBattleTracks();
        root.checkIntervalSeconds = Math.max(1, root.checkIntervalSeconds);
        root.victorySeconds = Math.max(1, root.victorySeconds);
        root.defaultTrack = normalize(root.defaultTrack == null || root.defaultTrack.isBlank() ? "exploration_default" : root.defaultTrack);
        root.victoryTrack = normalize(root.victoryTrack == null || root.victoryTrack.isBlank() ? "victory" : root.victoryTrack);
        Map<String, Track> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Track> entry : root.tracks.entrySet()) {
            String key = normalize(entry.getKey());
            Track track = entry.getValue() == null ? new Track() : entry.getValue();
            if (track.sound == null || track.sound.isBlank()) track.sound = "champutils:music." + key;
            track.volume = Math.max(0.0F, Math.min(2.0F, track.volume));
            track.pitch = Math.max(0.5F, Math.min(2.0F, track.pitch));
            track.loopSeconds = Math.max(10, track.loopSeconds);
            cleaned.put(key, track);
        }
        root.tracks = cleaned;
    }

    private static Map<String, Track> defaultTracks() {
        Map<String, Track> map = new LinkedHashMap<>();
        map.put("exploration_default", new Track("champutils:music.exploration_default", 0.55F, 95, 1));
        map.put("spawn", new Track("champutils:music.spawn", 0.60F, 92, 10));
        map.put("wild_battle", new Track("champutils:music.battle.wild", 0.72F, 74, 100));
        map.put("npc_battle", new Track("champutils:music.battle.npc", 0.75F, 72, 110));
        map.put("gym_battle", new Track("champutils:music.battle.gym", 0.78F, 80, 120));
        map.put("ranked_battle", new Track("champutils:music.battle.ranked", 0.78F, 76, 130));
        map.put("mega_boss", new Track("champutils:music.battle.mega_boss", 0.82F, 83, 150));
        map.put("world_boss", new Track("champutils:music.battle.world_boss", 0.85F, 86, 160));
        map.put("victory", new Track("champutils:music.events.victory", 0.80F, 30, 200));
        return map;
    }

    private static List<Region> defaultRegions() {
        List<Region> list = new ArrayList<>();
        list.add(new Region("spawn", "spawn", -160, -160, 160, 160, 50));
        return list;
    }

    private static Map<String, String> defaultBattleTracks() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("RANKED", "ranked_battle");
        map.put("CASUAL", "ranked_battle");
        map.put("GYM", "gym_battle");
        map.put("ELITE_FOUR", "gym_battle");
        map.put("NPC", "npc_battle");
        map.put("WORLD_BOSS", "world_boss");
        map.put("MEGA_BOSS", "mega_boss");
        map.put("ADVENTURE_ROAMING", "npc_battle");
        map.put("UNKNOWN", "wild_battle");
        return map;
    }
}
