package com.champutils.gym;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class GymSettingsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/gym_settings.json");

    private static Data data = new Data();

    private GymSettingsConfig() {
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }

            if (data.globalCooldownSeconds < 0) {
                data.globalCooldownSeconds = 0;
                save();
            }
        } catch (Exception e) {
            data = new Data();
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception ignored) {
        }
    }

    public static long globalCooldownSeconds() {
        return Math.max(0L, data.globalCooldownSeconds);
    }

    public static void setGlobalCooldownSeconds(long seconds) {
        data.globalCooldownSeconds = Math.max(0L, seconds);
        save();
    }

    public static String cooldownDisplay() {
        long seconds = globalCooldownSeconds();

        if (seconds <= 0) {
            return "None";
        }

        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder builder = new StringBuilder();

        if (days > 0) {
            builder.append(days).append("d ");
        }

        if (hours > 0) {
            builder.append(hours).append("h ");
        }

        if (minutes > 0) {
            builder.append(minutes).append("m ");
        }

        if (seconds > 0 || builder.length() == 0) {
            builder.append(seconds).append("s");
        }

        return builder.toString().trim();
    }

    private static final class Data {
        long globalCooldownSeconds = 86400L;
    }
}
