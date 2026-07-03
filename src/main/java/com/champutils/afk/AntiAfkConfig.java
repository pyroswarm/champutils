package com.champutils.afk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class AntiAfkConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public boolean enabled = true;
    public int kickAfterSeconds = 20 * 60;
    public int warnAfterSeconds = 15 * 60;
    public int minMeaningfulMoveBlocks = 5;
    public int repeatedPatternWindowSeconds = 45;
    public int maxTinyLoopRadiusBlocks = 4;
    public boolean kickDuringProfileLoading = false;

    public boolean pvpBattleStallEnabled = true;
    public int pvpChoiceTimeoutSeconds = 90;
    public int pvpChoiceWarnSeconds = 60;
    public int pvpChoiceFinalWarnSeconds = 80;
    public boolean pvpForfeitOnTimeout = true;
    public boolean pvpKickAfterForfeit = false;

    private static AntiAfkConfig INSTANCE = new AntiAfkConfig();

    public static AntiAfkConfig get() {
        return INSTANCE;
    }

    public static void load() {
        File dir = new File("config/champutils");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "anti_afk.json");

        if (!file.exists()) {
            INSTANCE = new AntiAfkConfig();
            save(file);
            return;
        }

        try (FileReader reader = new FileReader(file)) {
            AntiAfkConfig loaded = GSON.fromJson(reader, AntiAfkConfig.class);
            INSTANCE = loaded == null ? new AntiAfkConfig() : loaded;
            normalize();
            save(file);
        } catch (Exception e) {
            System.err.println("[ChampUtils][AntiAFK] Failed to load anti_afk.json, using defaults.");
            e.printStackTrace();
            INSTANCE = new AntiAfkConfig();
        }
    }

    private static void normalize() {
        INSTANCE.kickAfterSeconds = Math.max(60, INSTANCE.kickAfterSeconds);
        INSTANCE.warnAfterSeconds = Math.max(30, Math.min(INSTANCE.warnAfterSeconds, INSTANCE.kickAfterSeconds - 10));
        INSTANCE.minMeaningfulMoveBlocks = Math.max(1, INSTANCE.minMeaningfulMoveBlocks);
        INSTANCE.repeatedPatternWindowSeconds = Math.max(10, INSTANCE.repeatedPatternWindowSeconds);
        INSTANCE.maxTinyLoopRadiusBlocks = Math.max(1, INSTANCE.maxTinyLoopRadiusBlocks);
        INSTANCE.pvpChoiceTimeoutSeconds = Math.max(30, INSTANCE.pvpChoiceTimeoutSeconds);
        INSTANCE.pvpChoiceWarnSeconds = Math.max(10, Math.min(INSTANCE.pvpChoiceWarnSeconds, INSTANCE.pvpChoiceTimeoutSeconds - 5));
        INSTANCE.pvpChoiceFinalWarnSeconds = Math.max(INSTANCE.pvpChoiceWarnSeconds, Math.min(INSTANCE.pvpChoiceFinalWarnSeconds, INSTANCE.pvpChoiceTimeoutSeconds - 1));
    }

    private static void save(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(INSTANCE, writer);
        } catch (Exception e) {
            System.err.println("[ChampUtils][AntiAFK] Failed to save anti_afk.json.");
            e.printStackTrace();
        }
    }
}
