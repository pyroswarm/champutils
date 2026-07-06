package com.champutils.dex;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.database.DatabaseManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.leaderboard.ProfileLeaderboardRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrueCaughtDexManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/true_caught_dex.json");
    private static final String STATE_KEY = "true_caught_dex";
    private static final Map<UUID, Set<String>> TRUE_CAUGHT = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    private TrueCaughtDexManager() {
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        TRUE_CAUGHT.clear();

        State state = new State();
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, Set<String>>>() {}.getType();
                Map<String, Set<String>> loadedData = GSON.fromJson(reader, type);
                if (loadedData != null) {
                    state.caught.putAll(loadedData);
                }
            } catch (Exception exception) {
                System.err.println("[ChampUtils] Failed to load true caught dex data.");
                exception.printStackTrace();
            }
        }

        state = SharedJsonStateRepository.loadGlobal(STATE_KEY, State.class, state);
        applyState(state);
    }

    public static synchronized void save() {
        load();
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            Map<String, Set<String>> out = new LinkedHashMap<>();
            for (Map.Entry<UUID, Set<String>> entry : TRUE_CAUGHT.entrySet()) {
                out.put(entry.getKey().toString(), new LinkedHashSet<>(entry.getValue()));
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(out, writer);
            }
            State state = new State();
            state.caught.putAll(out);
            SharedJsonStateRepository.saveGlobal(STATE_KEY, state);
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to save true caught dex data.");
            exception.printStackTrace();
        }
    }

    public static boolean markTrueCaught(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null) return false;
        String species = speciesId(pokemon);
        boolean added = markTrueCaught(PlayerProfileManager.activeProfileId(player), species);
        if (added) AdventureGuideManager.increment(player, "catch_species", 1);
        return added;
    }

    public static boolean markTrueCaught(UUID playerId, String species) {
        if (playerId == null) return false;
        load();
        String key = normalizeSpecies(species);
        if (key.isBlank()) return false;

        Set<String> set = TRUE_CAUGHT.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet());
        boolean added = set.add(key);
        if (added) {
            save();
            syncTrueCaughtSql(playerId, key);
            com.champutils.network.NetworkEventManager.publishCacheInvalidation("TRUE_CAUGHT_DEX", playerId);
            ProfileLeaderboardRepository.invalidateCache();
        }
        return added;
    }

    public static synchronized void invalidateSharedCache(UUID profileId) {
        loaded = false;
        TRUE_CAUGHT.clear();
        ProfileLeaderboardRepository.invalidateCache();
    }

    private static void applyState(State state) {
        if (state == null || state.caught == null) return;
        for (Map.Entry<String, Set<String>> entry : state.caught.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                Set<String> normalized = ConcurrentHashMap.newKeySet();
                if (entry.getValue() != null) {
                    for (String species : entry.getValue()) {
                        String key = normalizeSpecies(species);
                        if (!key.isBlank()) normalized.add(key);
                    }
                }
                TRUE_CAUGHT.put(uuid, normalized);
            } catch (Throwable ignored) {
            }
        }
    }

    public static boolean hasTrueCaught(ServerPlayer player, String species) {
        return player != null && hasTrueCaught(PlayerProfileManager.activeProfileId(player), species);
    }

    public static boolean hasTrueCaught(UUID playerId, String species) {
        if (playerId == null) return false;
        load();
        Set<String> set = TRUE_CAUGHT.get(playerId);
        return set != null && set.contains(normalizeSpecies(species));
    }

    public static int getCaughtCount(ServerPlayer player) {
        if (player == null) return 0;
        load();
        Set<String> set = TRUE_CAUGHT.get(PlayerProfileManager.activeProfileId(player));
        return set == null ? 0 : set.size();
    }

    public static Set<String> getCaughtSpecies(ServerPlayer player) {
        if (player == null) return Collections.emptySet();
        load();
        Set<String> set = TRUE_CAUGHT.get(PlayerProfileManager.activeProfileId(player));
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    private static void syncTrueCaughtSql(UUID profileId, String species) {
        if (profileId == null || species == null || species.isBlank() || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("sync true caught dex " + profileId, connection -> {
            try (java.sql.PreparedStatement ps = connection.prepareStatement(
                    "insert into true_caught_dex (player_uuid, species_id, caught_at) values (?, ?, now()) on conflict (player_uuid, species_id) do nothing")) {
                ps.setObject(1, profileId);
                ps.setString(2, species);
                ps.executeUpdate();
            }
        });
    }

    public static String speciesId(Object pokemon) {
        if (pokemon == null) return "";
        try {
            Object species = call(pokemon, "getSpecies");
            if (species == null) species = call(pokemon, "species");
            if (species != null) {
                Object id = firstValue(species, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId");
                if (id != null) return normalizeSpecies(String.valueOf(id));
                Object name = firstValue(species, "name", "getName");
                if (name != null) return normalizeSpecies(String.valueOf(name));
                return normalizeSpecies(String.valueOf(species));
            }
        } catch (Throwable ignored) {
        }
        return normalizeSpecies(String.valueOf(pokemon));
    }

    public static String normalizeSpecies(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) value = value.substring(colon + 1);
        value = value.replace(' ', '_').replace('-', '_');
        value = value.replace("♀", "_f").replace("♂", "_m");
        return value.replaceAll("[^a-z0-9_]", "");
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            Object value = null;
            if (name.startsWith("get")) value = call(source, name);
            if (value == null) value = field(source, name);
            if (value == null) value = call(source, name);
            if (value != null) return value;
        }
        return null;
    }

    private static Object call(Object source, String methodName) {
        if (source == null || methodName == null || methodName.isBlank()) return null;
        try {
            Method method = source.getClass().getMethod(methodName);
            method.setAccessible(true);
            if (method.getParameterCount() == 0) return method.invoke(source);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object field(Object source, String fieldName) {
        if (source == null || fieldName == null || fieldName.isBlank()) return null;
        Class<?> type = source.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    public static final class State {
        public Map<String, Set<String>> caught = new LinkedHashMap<>();
    }
}
