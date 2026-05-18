package com.champutils.dungeon;

import com.champutils.database.CrateCreditDatabaseRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonCrateCreditManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "dungeon_crate_credits.json");

    private static CreditRoot DATA = new CreditRoot();
    private static boolean loaded = false;

    private DungeonCrateCreditManager() {
    }

    public static void grantCredits(UUID playerId, DungeonRarity rarity, int normalCredits, int pokemonCredits) {
        if (playerId == null || rarity == null) {
            return;
        }

        ensureLoaded();
        PlayerCredits credits = getOrCreate(playerId);
        add(credits.normal, rarity, normalCredits);
        add(credits.pokemon, rarity, pokemonCredits);
        save();
        syncPlayer(playerId, credits);
    }

    public static boolean consumeNormalCredit(UUID playerId, DungeonRarity rarity) {
        return consume(playerId, rarity, true);
    }

    public static boolean consumePokemonCredit(UUID playerId, DungeonRarity rarity) {
        return consume(playerId, rarity, false);
    }

    public static int getNormalCredits(UUID playerId, DungeonRarity rarity) {
        return get(playerId, rarity, true);
    }

    public static int getPokemonCredits(UUID playerId, DungeonRarity rarity) {
        return get(playerId, rarity, false);
    }

    public static int getTotalCredits(UUID playerId) {
        if (playerId == null) {
            return 0;
        }

        ensureLoaded();
        PlayerCredits credits = DATA.players.get(playerId.toString());
        if (credits == null) {
            return 0;
        }

        int total = 0;
        for (Integer value : credits.normal.values()) {
            total += Math.max(0, value == null ? 0 : value);
        }
        for (Integer value : credits.pokemon.values()) {
            total += Math.max(0, value == null ? 0 : value);
        }
        return total;
    }

    private static boolean consume(UUID playerId, DungeonRarity rarity, boolean normal) {
        if (playerId == null || rarity == null) {
            return false;
        }

        ensureLoaded();
        PlayerCredits credits = DATA.players.get(playerId.toString());
        if (credits == null) {
            return false;
        }

        Map<String, Integer> map = normal ? credits.normal : credits.pokemon;
        String key = rarity.name();
        int current = Math.max(0, map.getOrDefault(key, 0));
        if (current <= 0) {
            return false;
        }

        if (current == 1) {
            map.remove(key);
        } else {
            map.put(key, current - 1);
        }

        cleanup(playerId, credits);
        save();
        syncPlayer(playerId, credits);
        return true;
    }

    private static int get(UUID playerId, DungeonRarity rarity, boolean normal) {
        if (playerId == null || rarity == null) {
            return 0;
        }

        ensureLoaded();
        PlayerCredits credits = DATA.players.get(playerId.toString());
        if (credits == null) {
            return 0;
        }

        Map<String, Integer> map = normal ? credits.normal : credits.pokemon;
        return Math.max(0, map.getOrDefault(rarity.name(), 0));
    }

    private static void add(Map<String, Integer> map, DungeonRarity rarity, int amount) {
        if (amount <= 0) {
            return;
        }
        String key = rarity.name();
        map.put(key, Math.max(0, map.getOrDefault(key, 0)) + amount);
    }

    private static PlayerCredits getOrCreate(UUID playerId) {
        return DATA.players.computeIfAbsent(playerId.toString(), ignored -> new PlayerCredits());
    }

    private static void cleanup(UUID playerId, PlayerCredits credits) {
        credits.normal.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        credits.pokemon.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= 0);
        if (credits.normal.isEmpty() && credits.pokemon.isEmpty()) {
            DATA.players.remove(playerId.toString());
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        load();
    }

    public static void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = new CreditRoot();
                save();
                loaded = true;
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                CreditRoot loadedData = GSON.fromJson(reader, CreditRoot.class);
                DATA = loadedData == null ? new CreditRoot() : loadedData;
            }

            if (DATA.players == null) {
                DATA.players = new HashMap<>();
            }
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new CreditRoot();
            loaded = true;
        }
    }

    private static void save() {
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


    private static void syncPlayer(UUID playerId, PlayerCredits credits) {
        if (playerId == null || credits == null) {
            return;
        }

        for (DungeonRarity rarity : DungeonRarity.values()) {
            String rarityKey = rarity.name();
            CrateCreditDatabaseRepository.setCredits(
                    playerId,
                    "dungeon_normal_" + rarityKey,
                    Math.max(0, credits.normal.getOrDefault(rarityKey, 0))
            );
            CrateCreditDatabaseRepository.setCredits(
                    playerId,
                    "dungeon_pokemon_" + rarityKey,
                    Math.max(0, credits.pokemon.getOrDefault(rarityKey, 0))
            );
        }
    }

    private static final class CreditRoot {
        private Map<String, PlayerCredits> players = new HashMap<>();
    }

    private static final class PlayerCredits {
        private Map<String, Integer> normal = new HashMap<>();
        private Map<String, Integer> pokemon = new HashMap<>();
    }
}
