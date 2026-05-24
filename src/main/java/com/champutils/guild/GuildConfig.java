package com.champutils.guild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class GuildConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static GuildXp GUILD_XP = new GuildXp();
    public static GuildLevels GUILD_LEVELS = new GuildLevels();

    private GuildConfig() {}

    public static final class Root {
        public GuildXp guildXp = new GuildXp();
        public GuildLevels guildLevels = new GuildLevels();
    }

    public static final class GuildXp {
        public int rankedWin = 50;
        public int casualWin = 15;
        public int worldEventCommon = 100;
        public int worldEventUncommon = 175;
        public int worldEventRare = 300;
        public int worldEventEpic = 750;
        public int worldEventLegendary = 2000;
    }

    public static final class GuildLevels {
        public long baseXp = 1000L;
        public double scalingMultiplier = 1.35D;
        public int maxLevel = 100;
    }

    public static void load() {
        try {
            File dir = new File("config/champutils/guilds");
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dir, "guild_config.json");
            if (!file.exists()) {
                try (FileWriter writer = new FileWriter(file)) {
                    GSON.toJson(new Root(), writer);
                }
            }

            Root loaded;
            try (FileReader reader = new FileReader(file)) {
                loaded = GSON.fromJson(reader, Root.class);
            }

            if (loaded == null) loaded = new Root();
            if (loaded.guildXp == null) loaded.guildXp = new GuildXp();
            if (loaded.guildLevels == null) loaded.guildLevels = new GuildLevels();

            sanitize(loaded.guildXp, loaded.guildLevels);
            GUILD_XP = loaded.guildXp;
            GUILD_LEVELS = loaded.guildLevels;

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(loaded, writer);
            }

            System.out.println("[ChampUtils] Loaded guild_config.json.");
        } catch (Exception e) {
            e.printStackTrace();
            GUILD_XP = new GuildXp();
            GUILD_LEVELS = new GuildLevels();
        }
    }

    public static long requiredXpForLevel(int level) {
        int safeLevel = Math.max(2, level);
        long base = Math.max(1L, GUILD_LEVELS.baseXp);
        double multiplier = Math.max(1.01D, GUILD_LEVELS.scalingMultiplier);
        return Math.max(1L, Math.round(base * Math.pow(multiplier, safeLevel - 2)));
    }

    public static long totalXpRequiredForLevel(int level) {
        if (level <= 1) return 0L;
        long total = 0L;
        for (int next = 2; next <= level; next++) {
            total = safeAdd(total, requiredXpForLevel(next));
        }
        return total;
    }

    public static int levelForXp(long xp) {
        long safeXp = Math.max(0L, xp);
        int max = Math.max(1, GUILD_LEVELS.maxLevel);
        int level = 1;
        long spent = 0L;

        while (level < max) {
            long needed = requiredXpForLevel(level + 1);
            if (safeXp < safeAdd(spent, needed)) {
                break;
            }
            spent = safeAdd(spent, needed);
            level++;
        }
        return level;
    }

    public static long xpIntoCurrentLevel(long xp) {
        int level = levelForXp(xp);
        return Math.max(0L, xp - totalXpRequiredForLevel(level));
    }

    public static long xpNeededForNextLevel(long xp) {
        int level = levelForXp(xp);
        if (level >= Math.max(1, GUILD_LEVELS.maxLevel)) {
            return 0L;
        }
        return requiredXpForLevel(level + 1);
    }

    public static int worldEventXp(String tier) {
        String normalized = tier == null ? "RARE" : tier.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "COMMON" -> GUILD_XP.worldEventCommon;
            case "UNCOMMON" -> GUILD_XP.worldEventUncommon;
            case "EPIC" -> GUILD_XP.worldEventEpic;
            case "LEGENDARY", "MYTHIC" -> GUILD_XP.worldEventLegendary;
            case "RARE" -> GUILD_XP.worldEventRare;
            default -> GUILD_XP.worldEventRare;
        };
    }

    private static void sanitize(GuildXp xp, GuildLevels levels) {
        xp.rankedWin = Math.max(0, xp.rankedWin);
        xp.casualWin = Math.max(0, xp.casualWin);
        xp.worldEventCommon = Math.max(0, xp.worldEventCommon);
        xp.worldEventUncommon = Math.max(0, xp.worldEventUncommon);
        xp.worldEventRare = Math.max(0, xp.worldEventRare);
        xp.worldEventEpic = Math.max(0, xp.worldEventEpic);
        xp.worldEventLegendary = Math.max(0, xp.worldEventLegendary);
        levels.baseXp = Math.max(1L, levels.baseXp);
        levels.scalingMultiplier = Math.max(1.01D, levels.scalingMultiplier);
        levels.maxLevel = Math.max(1, levels.maxLevel);
    }

    private static long safeAdd(long a, long b) {
        if (Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }
}
