package com.champutils.dailylogin;

import com.champutils.database.SharedJsonStateRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DailyLoginData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils/data");
    private static final File FILE = new File(DIR, "daily_login_state.json");
    private static final String STATE_KEY = "daily_login";

    public static Root DATA = new Root();
    private static final Set<String> SQL_LOADED_PLAYERS = new HashSet<>();

    private DailyLoginData() {}

    public static synchronized void load() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            if (!FILE.exists()) { DATA = new Root(); save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                Root loaded = GSON.fromJson(reader, Root.class);
                DATA = loaded == null ? new Root() : loaded;
            }
            if (DATA.players == null) DATA.players = new HashMap<>();
            Root shared = SharedJsonStateRepository.loadGlobal(STATE_KEY, Root.class, DATA);
            if (shared != null && shared.players != null && !shared.players.isEmpty()) {
                DATA = shared;
            }
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new Root();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) DIR.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(DATA, writer); }
            SharedJsonStateRepository.saveGlobal(STATE_KEY, DATA);
            if (DATA.players != null) {
                for (Map.Entry<String, PlayerState> entry : DATA.players.entrySet()) {
                    try {
                        SharedJsonStateRepository.savePlayer(UUID.fromString(entry.getKey()), STATE_KEY, entry.getValue());
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Daily login rewards are account-based.
     *
     * Do not key this by active profile id. A player should not be able to swap
     * profiles to earn/claim multiple daily login rewards on the same account.
     */
    public static synchronized PlayerState state(UUID uuid, String name) {
        String key = uuid == null ? "" : uuid.toString();
        PlayerState fallback = DATA.players.computeIfAbsent(key, ignored -> new PlayerState());
        PlayerState state = fallback;
        if (uuid != null && SQL_LOADED_PLAYERS.add(key)) {
            state = SharedJsonStateRepository.loadPlayer(uuid, STATE_KEY, PlayerState.class, fallback);
            DATA.players.put(key, state);
        }
        state.uuid = key;
        state.name = name == null ? state.name : name;
        if (state.claimedDays == null) state.claimedDays = new HashSet<>();
        return state;
    }

    public static class Root { public Map<String, PlayerState> players = new HashMap<>(); }
    public static class PlayerState {
        public String uuid = "";
        public String name = "";
        public String monthKey = "";
        public long lastQualifiedResetKey = -1L;
        public long activeResetKey = -1L;
        public int minutesThisReset = 0;
        public int trackProgress = 0;
        public Set<Integer> claimedDays = new HashSet<>();
    }
}
