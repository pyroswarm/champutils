package com.champutils.dex;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PokemonOriginManager {

    public static final String ORIGIN_WILD_CAPTURE = "wild_capture";
    public static final String ORIGIN_CRATE = "crate";
    public static final String ORIGIN_WONDERTRADE = "wondertrade";
    public static final String ORIGIN_TRADE = "trade";
    public static final String ORIGIN_GIFT = "gift";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/pokemon_origins.json");
    private static final Map<UUID, String> ORIGINS = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    private PokemonOriginManager() {
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        ORIGINS.clear();
        if (!FILE.exists()) return;

        try (FileReader reader = new FileReader(FILE)) {
            Type type = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> loadedData = GSON.fromJson(reader, type);
            if (loadedData == null) return;
            for (Map.Entry<String, String> entry : loadedData.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    String origin = normalizeOrigin(entry.getValue());
                    if (!origin.isBlank()) ORIGINS.put(uuid, origin);
                } catch (Throwable ignored) {
                }
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to load Pokémon origin data.");
            exception.printStackTrace();
        }
    }

    public static synchronized void save() {
        load();
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> entry : ORIGINS.entrySet()) {
                out.put(entry.getKey().toString(), entry.getValue());
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to save Pokémon origin data.");
            exception.printStackTrace();
        }
    }

    public static void markOrigin(Pokemon pokemon, String origin) {
        if (pokemon == null) return;
        load();
        UUID uuid = pokemonUuid(pokemon);
        if (uuid == null) return;
        String normalized = normalizeOrigin(origin);
        if (normalized.isBlank()) return;
        ORIGINS.put(uuid, normalized);
        save();
    }

    public static String getOrigin(Pokemon pokemon) {
        if (pokemon == null) return "";
        load();
        UUID uuid = pokemonUuid(pokemon);
        return uuid == null ? "" : ORIGINS.getOrDefault(uuid, "");
    }

    private static String normalizeOrigin(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static UUID pokemonUuid(Pokemon pokemon) {
        for (String methodName : new String[] { "getUuid", "getUUID", "getUuid", "uuid" }) {
            try {
                Method method = pokemon.getClass().getMethod(methodName);
                method.setAccessible(true);
                if (method.getParameterCount() != 0) continue;
                Object value = method.invoke(pokemon);
                if (value instanceof UUID uuid) return uuid;
                if (value != null) return UUID.fromString(String.valueOf(value));
            } catch (Throwable ignored) {
            }
        }
        try {
            java.lang.reflect.Field field = pokemon.getClass().getDeclaredField("uuid");
            field.setAccessible(true);
            Object value = field.get(pokemon);
            if (value instanceof UUID uuid) return uuid;
            if (value != null) return UUID.fromString(String.valueOf(value));
        } catch (Throwable ignored) {
        }
        return null;
    }
}
