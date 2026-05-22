package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonDigitalKeyManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "dungeon_digital_keys.json");

    private static KeyRoot DATA = new KeyRoot();

    private DungeonDigitalKeyManager() {
    }

    public static synchronized void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = new KeyRoot();
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                KeyRoot loaded = GSON.fromJson(reader, KeyRoot.class);
                DATA = loaded == null ? new KeyRoot() : loaded;
                if (DATA.players == null) {
                    DATA.players = new LinkedHashMap<>();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new KeyRoot();
        }
    }

    public static synchronized void save() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(DATA, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void grantKeys(UUID playerId, String keyId, int amount) {
        if (playerId == null || keyId == null || keyId.isBlank() || amount <= 0) {
            return;
        }

        Map<String, Integer> keys = getOrCreate(playerId);
        keys.put(keyId, Math.max(0, keys.getOrDefault(keyId, 0)) + amount);
        save();
    }

    public static synchronized boolean consumeKey(UUID playerId, String keyId) {
        if (playerId == null || keyId == null || keyId.isBlank()) {
            return false;
        }

        Map<String, Integer> keys = DATA.players.get(playerId.toString());
        if (keys == null) {
            return false;
        }

        int current = Math.max(0, keys.getOrDefault(keyId, 0));
        if (current <= 0) {
            return false;
        }

        if (current == 1) {
            keys.remove(keyId);
        } else {
            keys.put(keyId, current - 1);
        }

        cleanup(playerId, keys);
        save();
        return true;
    }

    public static synchronized boolean hasKey(UUID playerId, String keyId) {
        return getKeyCount(playerId, keyId) > 0;
    }

    public static synchronized int getKeyCount(UUID playerId, String keyId) {
        if (playerId == null || keyId == null || keyId.isBlank()) {
            return 0;
        }

        Map<String, Integer> keys = DATA.players.get(playerId.toString());
        if (keys == null) {
            return 0;
        }

        return Math.max(0, keys.getOrDefault(keyId, 0));
    }

    public static synchronized int getTotalKeys(UUID playerId) {
        if (playerId == null) {
            return 0;
        }

        Map<String, Integer> keys = DATA.players.get(playerId.toString());
        if (keys == null) {
            return 0;
        }

        int total = 0;
        for (Integer amount : keys.values()) {
            total += Math.max(0, amount == null ? 0 : amount);
        }
        return total;
    }

    public static synchronized Map<String, Integer> getKeys(UUID playerId) {
        if (playerId == null) {
            return Map.of();
        }

        Map<String, Integer> keys = DATA.players.get(playerId.toString());
        if (keys == null) {
            return Map.of();
        }

        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : keys.entrySet()) {
            int value = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (value > 0) {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    private static Map<String, Integer> getOrCreate(UUID playerId) {
        if (DATA.players == null) {
            DATA.players = new LinkedHashMap<>();
        }
        return DATA.players.computeIfAbsent(playerId.toString(), ignored -> new LinkedHashMap<>());
    }

    private static void cleanup(UUID playerId, Map<String, Integer> keys) {
        keys.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        if (keys.isEmpty()) {
            DATA.players.remove(playerId.toString());
        }
    }

    private static final class KeyRoot {
        Map<String, Map<String, Integer>> players = new LinkedHashMap<>();
    }
}
