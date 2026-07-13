package com.champutils.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public final class NicknameConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/nicknames.json");
    public static NicknameConfig INSTANCE = defaults();

    public boolean enabled = true;
    public int minLength = 3;
    public int maxLength = 20;
    public boolean allowSpaces = false;
    public boolean unique = true;
    public int cooldownMinutes = 60;
    public int cacheMinutes = 30;
    public int syncSeconds = 10;
    public boolean showInTabList = true;
    public boolean showInJoinQuit = true;
    public boolean auditHistory = true;
    public long changeCostCredits = 0L;
    public List<String> reservedNames = new ArrayList<>();

    private NicknameConfig() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) {
                INSTANCE = defaults();
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                NicknameConfig loaded = GSON.fromJson(reader, NicknameConfig.class);
                INSTANCE = loaded == null ? defaults() : loaded;
            }
            normalize();
        } catch (Exception error) {
            INSTANCE = defaults();
            System.err.println("[ChampUtils] Failed to load nicknames.json; using defaults.");
            error.printStackTrace();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(INSTANCE, writer); }
        } catch (Exception error) {
            System.err.println("[ChampUtils] Failed to save nicknames.json.");
            error.printStackTrace();
        }
    }

    private static void normalize() {
        if (INSTANCE.reservedNames == null) INSTANCE.reservedNames = new ArrayList<>();
        INSTANCE.minLength = Math.max(1, INSTANCE.minLength);
        INSTANCE.maxLength = Math.max(INSTANCE.minLength, Math.min(32, INSTANCE.maxLength));
        INSTANCE.cooldownMinutes = Math.max(0, INSTANCE.cooldownMinutes);
        INSTANCE.cacheMinutes = Math.max(1, INSTANCE.cacheMinutes);
        INSTANCE.syncSeconds = Math.max(5, INSTANCE.syncSeconds);
        INSTANCE.changeCostCredits = Math.max(0L, INSTANCE.changeCostCredits);
    }

    private static NicknameConfig defaults() {
        NicknameConfig config = new NicknameConfig();
        config.reservedNames.addAll(List.of("admin", "administrator", "owner", "moderator", "staff", "console", "server", "tebex"));
        return config;
    }
}
