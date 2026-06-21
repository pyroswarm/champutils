package com.champutils.scoreboard;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ScoreboardPreferenceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Boolean>>() {}.getType();
    private static final Map<String, Boolean> ENABLED = new HashMap<>();

    private ScoreboardPreferenceManager() {
    }

    public static void load() {
        ENABLED.clear();

        try {
            File file = file();
            if (!file.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(file)) {
                Map<String, Boolean> loaded = GSON.fromJson(reader, TYPE);
                if (loaded != null) {
                    ENABLED.putAll(loaded);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(ENABLED, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean isEnabled(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        return ENABLED.getOrDefault(uuid.toString(), true);
    }

    public static boolean toggle(UUID uuid) {
        boolean enabled = !isEnabled(uuid);
        setEnabled(uuid, enabled);
        return enabled;
    }

    public static void setEnabled(UUID uuid, boolean enabled) {
        if (uuid == null) {
            return;
        }

        ENABLED.put(uuid.toString(), enabled);
        save();
    }

    private static File file() {
        return new File("config/champutils/scoreboard_toggles.json");
    }
}
