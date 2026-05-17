package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DungeonLimitConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "dungeon_limits.json");

    public static LimitRoot CONFIG = new LimitRoot();
    private static boolean loaded = false;

    private DungeonLimitConfig() {
    }

    public static class LimitRoot {
        /** OP level 4 bypass. Keep true for easy admin testing. */
        public boolean opBypass = true;
        /** LuckPerms-style bypass node documented for your staff setup. OP bypass is handled directly here. */
        public String bypassPermission = "champutils.dungeon.bypasslimits";
        public Map<String, RarityLimit> limitsByRarity = new LinkedHashMap<>();
    }

    public static class RarityLimit {
        /** Cooldown after a successful clear. Set 0 or negative to disable. */
        public int cooldownMinutes = 30;
        /** Clears allowed per Minecraft/server calendar day. Set -1 to disable. */
        public int dailyLimit = 5;
        /** Clears allowed per ISO week. Set -1 to disable. */
        public int weeklyLimit = -1;
    }

    public static void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                CONFIG = createDefaultRoot();
                save();
                loaded = true;
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                LimitRoot loadedRoot = GSON.fromJson(reader, LimitRoot.class);
                CONFIG = loadedRoot == null ? createDefaultRoot() : loadedRoot;
            }

            if (CONFIG.limitsByRarity == null) {
                CONFIG.limitsByRarity = new LinkedHashMap<>();
            }
            for (DungeonRarity rarity : DungeonRarity.values()) {
                CONFIG.limitsByRarity.putIfAbsent(rarity.name(), createDefaultLimit(rarity));
            }

            loaded = true;
            save();
            System.out.println("[ChampUtils] Loaded dungeon limits for " + CONFIG.limitsByRarity.size() + " rarities.");
        } catch (Exception e) {
            e.printStackTrace();
            CONFIG = createDefaultRoot();
            loaded = true;
        }
    }

    public static RarityLimit get(DungeonRarity rarity) {
        ensureLoaded();
        DungeonRarity resolvedRarity = rarity == null ? DungeonRarity.COMMON : rarity;
        return CONFIG.limitsByRarity.computeIfAbsent(
                resolvedRarity.name(),
                key -> createDefaultLimit(resolvedRarity)
        );
    }

    public static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static void save() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(CONFIG, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static LimitRoot createDefaultRoot() {
        LimitRoot root = new LimitRoot();
        for (DungeonRarity rarity : DungeonRarity.values()) {
            root.limitsByRarity.put(rarity.name(), createDefaultLimit(rarity));
        }
        return root;
    }

    private static RarityLimit createDefaultLimit(DungeonRarity rarity) {
        RarityLimit limit = new RarityLimit();
        switch (rarity) {
            case COMMON -> {
                limit.cooldownMinutes = 30;
                limit.dailyLimit = 5;
                limit.weeklyLimit = -1;
            }
            case UNCOMMON -> {
                limit.cooldownMinutes = 60;
                limit.dailyLimit = 3;
                limit.weeklyLimit = -1;
            }
            case RARE -> {
                limit.cooldownMinutes = 120;
                limit.dailyLimit = 2;
                limit.weeklyLimit = -1;
            }
            case EPIC -> {
                limit.cooldownMinutes = 240;
                limit.dailyLimit = 1;
                limit.weeklyLimit = 5;
            }
            case LEGENDARY -> {
                limit.cooldownMinutes = 720;
                limit.dailyLimit = 1;
                limit.weeklyLimit = 2;
            }
            case MYTHIC -> {
                limit.cooldownMinutes = 1440;
                limit.dailyLimit = 1;
                limit.weeklyLimit = 1;
            }
        }
        return limit;
    }
}
