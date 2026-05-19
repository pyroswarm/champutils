package com.champutils.matchmaking;

import com.champutils.util.ServerLocation;
import com.champutils.util.ServerLocationManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class ArenaLocationConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final File FILE =
            new File(
                    "config/champutils",
                    "arena_locations.json"
            );

    private static final Map<String, ServerLocation> ARENAS =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private ArenaLocationConfig() {
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
                Data data = GSON.fromJson(reader, Data.class);
                ARENAS.clear();

                if (data != null && data.arenas != null) {
                    ARENAS.putAll(data.arenas);
                }
            }

            System.out.println("[ChampUtils] Loaded arena_locations.json.");
        } catch (Exception e) {
            System.out.println("[ChampUtils] Failed to load arena_locations.json.");
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            Data data = new Data();
            data.arenas = new TreeMap<>(ARENAS);

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            System.out.println("[ChampUtils] Failed to save arena_locations.json.");
            e.printStackTrace();
        }
    }

    public static void setArena(String id, ServerPlayer player) {
        ARENAS.put(clean(id), ServerLocationManager.fromPlayer(player));
        save();
    }

    public static ServerLocation getArena(String id) {
        return ARENAS.get(clean(id));
    }

    public static boolean deleteArena(String id) {
        ServerLocation removed = ARENAS.remove(clean(id));
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public static Map<String, ServerLocation> arenas() {
        return Collections.unmodifiableMap(ARENAS);
    }

    private static String clean(String id) {
        return id == null ? "" : id.trim().toLowerCase();
    }

    private static final class Data {
        Map<String, ServerLocation> arenas = new TreeMap<>();
    }
}
