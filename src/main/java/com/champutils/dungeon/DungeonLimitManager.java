package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class DungeonLimitManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils");
    private static final File FILE = new File(DIR, "dungeon_cooldowns.json");

    private static LimitData DATA = new LimitData();
    private static boolean loaded = false;

    private DungeonLimitManager() {
    }

    public static boolean canStartDungeon(ServerPlayer player, DungeonRarity rarity) {
        if (player == null || rarity == null) {
            return false;
        }

        ensureLoaded();
        DungeonLimitConfig.ensureLoaded();

        if (hasBypass(player)) {
            return true;
        }

        DungeonLimitConfig.RarityLimit config = DungeonLimitConfig.get(rarity);
        PlayerLimitData playerData = DATA.players.get(player.getUUID().toString());
        long now = Instant.now().toEpochMilli();

        if (playerData != null) {
            Long nextAllowed = playerData.nextAllowedAtByRarity.get(rarity.name());
            if (nextAllowed != null && nextAllowed > now) {
                player.sendSystemMessage(Component.literal("You are on " + rarity.name() + " dungeon cooldown for " + formatDuration(nextAllowed - now) + ".").withStyle(ChatFormatting.RED));
                return false;
            }
        }

        String today = todayKey();
        String week = weekKey();

        if (config.dailyLimit >= 0 && getCount(playerData, rarity, today, true) >= config.dailyLimit) {
            player.sendSystemMessage(Component.literal("You have reached your daily " + rarity.name() + " dungeon clear limit.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (config.weeklyLimit >= 0 && getCount(playerData, rarity, week, false) >= config.weeklyLimit) {
            player.sendSystemMessage(Component.literal("You have reached your weekly " + rarity.name() + " dungeon clear limit.").withStyle(ChatFormatting.RED));
            return false;
        }

        return true;
    }

    public static void recordDungeonClear(ServerPlayer player, DungeonRarity rarity) {
        if (player == null || rarity == null) {
            return;
        }

        ensureLoaded();
        DungeonLimitConfig.ensureLoaded();

        if (hasBypass(player)) {
            return;
        }

        PlayerLimitData playerData = DATA.players.computeIfAbsent(player.getUUID().toString(), ignored -> new PlayerLimitData());
        String rarityName = rarity.name();
        String today = todayKey();
        String week = weekKey();

        increment(nested(playerData.dailyClears, today), rarityName);
        increment(nested(playerData.weeklyClears, week), rarityName);

        DungeonLimitConfig.RarityLimit config = DungeonLimitConfig.get(rarity);
        if (config.cooldownMinutes > 0) {
            long next = Instant.now().toEpochMilli() + (config.cooldownMinutes * 60_000L);
            playerData.nextAllowedAtByRarity.put(rarityName, next);
        }

        cleanupOldCounters(playerData, today, week);
        save();
    }

    public static void sendLimits(ServerPlayer player) {
        if (player == null) {
            return;
        }

        ensureLoaded();
        DungeonLimitConfig.ensureLoaded();

        PlayerLimitData playerData = DATA.players.get(player.getUUID().toString());
        String today = todayKey();
        String week = weekKey();
        long now = Instant.now().toEpochMilli();

        player.sendSystemMessage(Component.literal("Dungeon limits:").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        if (hasBypass(player)) {
            player.sendSystemMessage(Component.literal("You bypass dungeon cooldowns/limits as an admin.").withStyle(ChatFormatting.GRAY));
            return;
        }

        for (DungeonRarity rarity : DungeonRarity.values()) {
            DungeonLimitConfig.RarityLimit config = DungeonLimitConfig.get(rarity);
            int dailyUsed = getCount(playerData, rarity, today, true);
            int weeklyUsed = getCount(playerData, rarity, week, false);
            Long nextAllowed = playerData == null ? null : playerData.nextAllowedAtByRarity.get(rarity.name());
            String cooldown = nextAllowed != null && nextAllowed > now ? formatDuration(nextAllowed - now) : "Ready";
            String daily = config.dailyLimit < 0 ? "No daily limit" : dailyUsed + "/" + config.dailyLimit + " today";
            String weekly = config.weeklyLimit < 0 ? "No weekly limit" : weeklyUsed + "/" + config.weeklyLimit + " this week";
            DungeonRarity lineRarity = rarity;
            player.sendSystemMessage(Component.literal(lineRarity.name() + ": " + cooldown + " | " + daily + " | " + weekly).withStyle(lineRarity.getColor()));
        }
    }

    public static void load() {
        try {
            if (!DIR.exists()) {
                DIR.mkdirs();
            }

            if (!FILE.exists()) {
                DATA = new LimitData();
                save();
                loaded = true;
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                LimitData loadedData = GSON.fromJson(reader, LimitData.class);
                DATA = loadedData == null ? new LimitData() : loadedData;
            }

            if (DATA.players == null) {
                DATA.players = new HashMap<>();
            }
            for (PlayerLimitData playerData : DATA.players.values()) {
                normalize(playerData);
            }
            loaded = true;
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new LimitData();
            loaded = true;
        }
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static boolean hasBypass(ServerPlayer player) {
        DungeonLimitConfig.ensureLoaded();
        return DungeonLimitConfig.CONFIG.opBypass && player.hasPermissions(4);
    }

    private static void normalize(PlayerLimitData playerData) {
        if (playerData == null) {
            return;
        }
        if (playerData.nextAllowedAtByRarity == null) {
            playerData.nextAllowedAtByRarity = new HashMap<>();
        }
        if (playerData.dailyClears == null) {
            playerData.dailyClears = new HashMap<>();
        }
        if (playerData.weeklyClears == null) {
            playerData.weeklyClears = new HashMap<>();
        }
    }

    private static int getCount(PlayerLimitData playerData, DungeonRarity rarity, String period, boolean daily) {
        if (playerData == null || rarity == null || period == null) {
            return 0;
        }
        Map<String, Map<String, Integer>> root = daily ? playerData.dailyClears : playerData.weeklyClears;
        Map<String, Integer> counts = root.get(period);
        if (counts == null) {
            return 0;
        }
        return Math.max(0, counts.getOrDefault(rarity.name(), 0));
    }

    private static Map<String, Integer> nested(Map<String, Map<String, Integer>> root, String key) {
        return root.computeIfAbsent(key, ignored -> new HashMap<>());
    }

    private static void increment(Map<String, Integer> map, String rarityName) {
        map.put(rarityName, Math.max(0, map.getOrDefault(rarityName, 0)) + 1);
    }

    private static void cleanupOldCounters(PlayerLimitData playerData, String today, String week) {
        playerData.dailyClears.keySet().removeIf(key -> !key.equals(today));
        playerData.weeklyClears.keySet().removeIf(key -> !key.equals(week));
    }

    private static String todayKey() {
        return LocalDate.now(ZoneId.systemDefault()).toString();
    }

    private static String weekKey() {
        LocalDate date = LocalDate.now(ZoneId.systemDefault());
        WeekFields fields = WeekFields.of(Locale.US);
        int week = date.get(fields.weekOfWeekBasedYear());
        int year = date.get(fields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(1L, millis / 1000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;

        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1L, minutes) + "m";
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

    private static final class LimitData {
        private Map<String, PlayerLimitData> players = new HashMap<>();
    }

    private static final class PlayerLimitData {
        private Map<String, Long> nextAllowedAtByRarity = new HashMap<>();
        private Map<String, Map<String, Integer>> dailyClears = new HashMap<>();
        private Map<String, Map<String, Integer>> weeklyClears = new HashMap<>();
    }
}
