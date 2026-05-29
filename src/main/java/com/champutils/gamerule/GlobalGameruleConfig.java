package com.champutils.gamerule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public final class GlobalGameruleConfig {

    private static final File FILE = new File("config/champutils/global_gamerules.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Data DATA = new Data();

    private GlobalGameruleConfig() {}

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = defaults();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                DATA = loaded == null ? defaults() : loaded;
                DATA.sanitize();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load global_gamerules.json; using defaults.");
            e.printStackTrace();
            DATA = defaults();
        }
    }

    public static void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save global_gamerules.json.");
            e.printStackTrace();
        }
    }

    private static Data defaults() {
        Data data = new Data();
        data.sanitize();
        return data;
    }

    public static final class Data {
        public boolean enabled = true;

        public boolean doDaylightCycle = false;
        public boolean doWeatherCycle = false;
        public boolean keepInventory = true;
        public boolean mobGriefing = false;
        public boolean doMobSpawning = true;
        public boolean doInsomnia = false;

        public void sanitize() {
            // Currently no migration needed. Method exists so future gamerules can be added safely.
        }
    }
}
