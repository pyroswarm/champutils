package com.champutils.scoreboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardPreferenceManager {

    public enum Line {
        ADVENTURER_RANK("Adventurer Rank", "Your Adventurer Guild rank and progress."),
        PVP_RANK("PvP Rank", "Your competitive rank and RP."),
        CREDITS("Credits", "Your current credit balance."),
        ADVENTURER_MARKS("Adventurer's Marks", "Your Adventurer's Marks balance."),
        DEX_PROGRESS("Dex Progress", "Caught Pokémon and Pokédex completion."),
        PROFILE_TIME("Profile Time", "Playtime on your active profile."),
        LEGENDARY_TIMER("Legendary Timer", "Time since the last Legendary spawn."),
        PARADOX_TIMER("Paradox Timer", "Time since the last Paradox spawn."),
        ULTRA_BEAST_TIMER("Ultra Beast Timer", "Time since the last Ultra Beast spawn."),
        LAST_BOSS("Last Boss", "Time since the last world boss spawn."),
        BATTLING("Battling", "Battling profession level and progress."),
        MINING("Mining", "Mining profession level and progress."),
        FORESTRY("Forestry", "Forestry profession level and progress."),
        FARMING("Farming", "Farming profession level and progress.");

        private final String displayName;
        private final String description;

        Line(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type ENABLED_TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();
    private static final Type LINE_TYPE = new TypeToken<Map<String, Map<String, Boolean>>>() {}.getType();
    private static final Map<String, Boolean> ENABLED = new HashMap<>();
    private static final Map<String, EnumMap<Line, Boolean>> LINES = new HashMap<>();

    private ScoreboardPreferenceManager() {
    }

    public static void load() {
        ENABLED.clear();
        LINES.clear();
        loadEnabled();
        loadLines();
    }

    private static void loadEnabled() {
        try {
            File file = enabledFile();
            if (!file.exists()) {
                saveEnabled();
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                Map<String, Boolean> loaded = GSON.fromJson(reader, ENABLED_TYPE);
                if (loaded != null) ENABLED.putAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadLines() {
        try {
            File file = linesFile();
            if (!file.exists()) {
                saveLines();
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                Map<String, Map<String, Boolean>> loaded = GSON.fromJson(reader, LINE_TYPE);
                if (loaded == null) return;

                loaded.forEach((uuid, values) -> {
                    EnumMap<Line, Boolean> preferences = new EnumMap<>(Line.class);
                    if (values != null) {
                        values.forEach((lineName, enabled) -> {
                            try {
                                preferences.put(Line.valueOf(lineName), enabled == null || enabled);
                            } catch (IllegalArgumentException ignored) {
                            }
                        });
                    }
                    LINES.put(uuid, preferences);
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled(UUID uuid) {
        return uuid != null && ENABLED.getOrDefault(uuid.toString(), true);
    }

    public static boolean toggle(UUID uuid) {
        boolean enabled = !isEnabled(uuid);
        setEnabled(uuid, enabled);
        return enabled;
    }

    public static void setEnabled(UUID uuid, boolean enabled) {
        if (uuid == null) return;
        ENABLED.put(uuid.toString(), enabled);
        saveEnabled();
    }

    public static boolean isLineEnabled(UUID uuid, Line line) {
        if (uuid == null || line == null) return true;
        EnumMap<Line, Boolean> preferences = LINES.get(uuid.toString());
        return preferences == null || preferences.getOrDefault(line, true);
    }

    public static boolean toggleLine(UUID uuid, Line line) {
        boolean enabled = !isLineEnabled(uuid, line);
        setLineEnabled(uuid, line, enabled);
        return enabled;
    }

    public static void setLineEnabled(UUID uuid, Line line, boolean enabled) {
        if (uuid == null || line == null) return;
        LINES.computeIfAbsent(uuid.toString(), ignored -> new EnumMap<>(Line.class)).put(line, enabled);
        saveLines();
    }

    private static void saveEnabled() {
        writeJson(enabledFile(), ENABLED);
    }

    private static void saveLines() {
        Map<String, Map<String, Boolean>> serialized = new HashMap<>();
        LINES.forEach((uuid, values) -> {
            Map<String, Boolean> lineValues = new HashMap<>();
            values.forEach((line, enabled) -> lineValues.put(line.name(), enabled));
            serialized.put(uuid, lineValues);
        });
        writeJson(linesFile(), serialized);
    }

    private static void writeJson(File file, Object value) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(value, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File enabledFile() {
        return new File("config/champutils/scoreboard_toggles.json");
    }

    private static File linesFile() {
        return new File("config/champutils/scoreboard_line_toggles.json");
    }
}
