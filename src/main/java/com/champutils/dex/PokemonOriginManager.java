package com.champutils.dex;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkEventManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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
    private static boolean loaded;

    private PokemonOriginManager() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        ORIGINS.clear();
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> loadedData = GSON.fromJson(reader, type);
                if (loadedData != null) {
                    for (Map.Entry<String, String> entry : loadedData.entrySet()) {
                        try {
                            UUID uuid = UUID.fromString(entry.getKey());
                            String origin = normalizeOrigin(entry.getValue());
                            if (!origin.isBlank()) ORIGINS.put(uuid, origin);
                        } catch (Throwable ignored) {}
                    }
                }
            } catch (Exception exception) {
                System.err.println("[ChampUtils] Failed to load local Pokémon origin data.");
                exception.printStackTrace();
            }
        }
        if (DatabaseManager.isEnabled()) {
            Map<UUID, String> legacy = new LinkedHashMap<>(ORIGINS);
            DatabaseManager.supplyAsync("load network Pokémon origins", connection -> {
                ensureSchema(connection);
                try (PreparedStatement upsert = connection.prepareStatement(
                        "insert into pokemon_origins (pokemon_uuid, origin, updated_at) values (?, ?, now()) on conflict (pokemon_uuid) do nothing")) {
                    for (Map.Entry<UUID, String> entry : legacy.entrySet()) {
                        upsert.setObject(1, entry.getKey());
                        upsert.setString(2, entry.getValue());
                        upsert.addBatch();
                    }
                    upsert.executeBatch();
                }
                Map<UUID, String> network = new LinkedHashMap<>();
                try (PreparedStatement ps = connection.prepareStatement("select pokemon_uuid, origin from pokemon_origins");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) network.put((UUID) rs.getObject(1), normalizeOrigin(rs.getString(2)));
                }
                return network;
            }).whenComplete((network, error) -> {
                if (error != null) {
                    System.err.println("[ChampUtils] Failed to load network Pokémon origins.");
                    error.printStackTrace();
                    return;
                }
                if (network != null) ORIGINS.putAll(network);
                saveLocal();
            });
        }
    }

    public static synchronized void save() { saveLocal(); }

    private static synchronized void saveLocal() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, String> out = new LinkedHashMap<>();
            for (Map.Entry<UUID, String> entry : ORIGINS.entrySet()) out.put(entry.getKey().toString(), entry.getValue());
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(out, writer); }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to save Pokémon origin mirror.");
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
        saveLocal();
        if (DatabaseManager.isEnabled()) {
            DatabaseManager.executeCoalescedAsync("pokemon-origin:" + uuid, "save Pokémon origin", connection -> {
                ensureSchema(connection);
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into pokemon_origins (pokemon_uuid, origin, updated_at) values (?, ?, now()) " +
                                "on conflict (pokemon_uuid) do update set origin = excluded.origin, updated_at = now()")) {
                    ps.setObject(1, uuid);
                    ps.setString(2, normalized);
                    ps.executeUpdate();
                }
            });
            NetworkEventManager.publishCacheInvalidation("POKEMON_ORIGIN", uuid);
        }
    }

    public static String getOrigin(Pokemon pokemon) {
        if (pokemon == null) return "";
        load();
        UUID uuid = pokemonUuid(pokemon);
        return uuid == null ? "" : ORIGINS.getOrDefault(uuid, "");
    }

    public static void refreshAsync(UUID pokemonUuid) {
        if (pokemonUuid == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.supplyAsync("refresh Pokémon origin " + pokemonUuid, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("select origin from pokemon_origins where pokemon_uuid = ?")) {
                ps.setObject(1, pokemonUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? normalizeOrigin(rs.getString(1)) : ""; }
            }
        }).whenComplete((origin, error) -> {
            if (error != null) return;
            if (origin == null || origin.isBlank()) ORIGINS.remove(pokemonUuid);
            else ORIGINS.put(pokemonUuid, origin);
        });
    }

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists pokemon_origins (pokemon_uuid uuid primary key, origin text not null, updated_at timestamptz not null default now())");
            statement.executeUpdate("create index if not exists pokemon_origins_updated_idx on pokemon_origins (updated_at desc)");
        }
    }

    private static String normalizeOrigin(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static UUID pokemonUuid(Pokemon pokemon) {
        for (String methodName : new String[] { "getUuid", "getUUID", "uuid" }) {
            try {
                Method method = pokemon.getClass().getMethod(methodName);
                method.setAccessible(true);
                if (method.getParameterCount() != 0) continue;
                Object value = method.invoke(pokemon);
                if (value instanceof UUID uuid) return uuid;
                if (value != null) return UUID.fromString(String.valueOf(value));
            } catch (Throwable ignored) {}
        }
        try {
            java.lang.reflect.Field field = pokemon.getClass().getDeclaredField("uuid");
            field.setAccessible(true);
            Object value = field.get(pokemon);
            if (value instanceof UUID uuid) return uuid;
            if (value != null) return UUID.fromString(String.valueOf(value));
        } catch (Throwable ignored) {}
        return null;
    }
}
