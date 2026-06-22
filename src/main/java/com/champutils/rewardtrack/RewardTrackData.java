package com.champutils.rewardtrack;

import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RewardTrackData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils/rewardtrack/profiles");
    private static final Map<UUID, Save> CACHE = new HashMap<>();

    private RewardTrackData() {}

    public static void load() {
        if (!DIR.exists()) DIR.mkdirs();
    }

    public static synchronized Save get(ServerPlayer player) {
        UUID id = PlayerProfileManager.activeProfileId(player);
        return CACHE.computeIfAbsent(id, RewardTrackData::read);
    }

    public static synchronized void save(ServerPlayer player) {
        UUID id = PlayerProfileManager.activeProfileId(player);
        Save save = CACHE.get(id);
        if (save != null) write(id, save);
    }

    private static Save read(UUID id) {
        File file = file(id);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Save save = GSON.fromJson(reader, Save.class);
                if (save != null) return save.sanitize();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new Save();
    }

    private static void write(UUID id, Save save) {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(file(id))) {
                GSON.toJson(save.sanitize(), writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File file(UUID id) {
        if (!DIR.exists()) DIR.mkdirs();
        return new File(DIR, id + ".json");
    }

    public static final class Save {
        public int xp = 0;
        public int claimedLevel = 0;
        public long dailyKey = 0L;
        public long lastWeeklyKey = 0L;
        public List<Mission> missions = new ArrayList<>();

        Save sanitize() {
            xp = Math.max(0, xp);
            claimedLevel = Math.max(0, Math.min(50, claimedLevel));
            if (missions == null) missions = new ArrayList<>();
            return this;
        }
    }

    public static final class Mission {
        public String id = "";
        public boolean daily = true;
        public long week = 0L;
        public String type = "PLAY";
        public String title = "Play ranked PvP";
        public int target = 1;
        public int progress = 0;
        public int xp = 100;
        public boolean claimed = false;
    }
}
