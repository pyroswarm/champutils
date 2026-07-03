package com.champutils.dex;

import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class CatchStreakManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DATA_FILE = new File("config/champutils/catch_streaks.json");
    private static final File CONFIG_FILE = new File("config/champutils/catch_streak_config.json");
    private static final Map<UUID, CatchStreak> STREAKS = new ConcurrentHashMap<>();
    public static final double BASE_SHINY_CHANCE = 1.0D / 8192.0D;
    public static final double DEFAULT_STREAK_BONUS_PER_CATCH = BASE_SHINY_CHANCE / 10.0D;
    public static final double DEFAULT_MAX_STREAK_SHINY_CHANCE = 1.0D / 2048.0D;
    public static CatchStreakConfig CONFIG = new CatchStreakConfig();
    private static boolean loaded = false;

    private CatchStreakManager() {}

    public static final class CatchStreak {
        public String species = "";
        public int count = 0;
        public long updatedAt = 0L;
    }

    public static final class CatchStreakConfig {
        public boolean enabled = true;
        public int minimumMessageStreak = 3;
        public int minimumBonusStreak = 3;
        public double baseShinyChance = BASE_SHINY_CHANCE;
        public double bonusPerCatch = DEFAULT_STREAK_BONUS_PER_CATCH;
        public double maxShinyChance = DEFAULT_MAX_STREAK_SHINY_CHANCE;
        public int nearbyPlayerSpawnRadius = 96;
        public boolean announceShinyBoostProc = true;
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        loadConfig();
        STREAKS.clear();
        if (!DATA_FILE.exists()) return;
        try (FileReader reader = new FileReader(DATA_FILE)) {
            Type type = new TypeToken<Map<String, CatchStreak>>() {}.getType();
            Map<String, CatchStreak> loadedData = GSON.fromJson(reader, type);
            if (loadedData == null) return;
            for (Map.Entry<String, CatchStreak> entry : loadedData.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    CatchStreak streak = sanitize(entry.getValue());
                    if (!streak.species.isBlank() && streak.count > 0) STREAKS.put(uuid, streak);
                } catch (Throwable ignored) {}
            }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to load catch streak data.");
            exception.printStackTrace();
        }
    }

    public static synchronized void save() {
        load();
        try {
            File parent = DATA_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, CatchStreak> out = new LinkedHashMap<>();
            for (Map.Entry<UUID, CatchStreak> entry : STREAKS.entrySet()) out.put(entry.getKey().toString(), entry.getValue());
            try (FileWriter writer = new FileWriter(DATA_FILE)) { GSON.toJson(out, writer); }
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to save catch streak data.");
            exception.printStackTrace();
        }
    }

    private static void loadConfig() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!CONFIG_FILE.exists()) {
                sanitizeConfig(true);
                try (FileWriter writer = new FileWriter(CONFIG_FILE)) { GSON.toJson(CONFIG, writer); }
                return;
            }
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                CatchStreakConfig loadedConfig = GSON.fromJson(reader, CatchStreakConfig.class);
                if (loadedConfig != null) CONFIG = loadedConfig;
            }
            sanitizeConfig(true);
        } catch (Exception exception) {
            System.err.println("[ChampUtils] Failed to load catch streak config; using defaults.");
            exception.printStackTrace();
            CONFIG = new CatchStreakConfig();
        }
    }

    private static void sanitizeConfig(boolean persist) {
        boolean changed = false;
        if (CONFIG == null) {
            CONFIG = new CatchStreakConfig();
            changed = true;
        }
        if (CONFIG.baseShinyChance <= 0.0D || CONFIG.baseShinyChance > BASE_SHINY_CHANCE) {
            CONFIG.baseShinyChance = BASE_SHINY_CHANCE;
            changed = true;
        }
        if (CONFIG.bonusPerCatch <= 0.0D || CONFIG.bonusPerCatch > DEFAULT_STREAK_BONUS_PER_CATCH) {
            CONFIG.bonusPerCatch = DEFAULT_STREAK_BONUS_PER_CATCH;
            changed = true;
        }
        if (CONFIG.maxShinyChance < CONFIG.baseShinyChance || CONFIG.maxShinyChance > DEFAULT_MAX_STREAK_SHINY_CHANCE) {
            CONFIG.maxShinyChance = DEFAULT_MAX_STREAK_SHINY_CHANCE;
            changed = true;
        }
        if (CONFIG.minimumMessageStreak < 1) {
            CONFIG.minimumMessageStreak = 1;
            changed = true;
        }
        if (CONFIG.minimumBonusStreak < 1) {
            CONFIG.minimumBonusStreak = 1;
            changed = true;
        }
        if (CONFIG.nearbyPlayerSpawnRadius < 8) {
            CONFIG.nearbyPlayerSpawnRadius = 8;
            changed = true;
        }
        if (persist && changed) {
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(CONFIG, writer);
            } catch (Exception exception) {
                System.err.println("[ChampUtils] Failed to save sanitized catch streak config.");
                exception.printStackTrace();
            }
        }
    }

    public static void handleCatch(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null || !CONFIG.enabled) return;
        load();
        String species = TrueCaughtDexManager.speciesId(pokemon);
        if (species.isBlank()) return;

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        CatchStreak previous = STREAKS.get(profileId);
        int previousCount = previous == null ? 0 : previous.count;
        String previousSpecies = previous == null ? "" : previous.species;

        CatchStreak next = new CatchStreak();
        next.species = species;
        next.updatedAt = System.currentTimeMillis();
        next.count = species.equals(previousSpecies) ? previousCount + 1 : 1;
        STREAKS.put(profileId, next);
        save();

        int minMessage = Math.max(1, CONFIG.minimumMessageStreak);
        if (!previousSpecies.isBlank() && !species.equals(previousSpecies) && previousCount >= minMessage) {
            player.sendSystemMessage(Component.literal("Catch streak broken! ").withStyle(ChatFormatting.RED)
                    .append(Component.literal(pretty(previousSpecies) + " ended at " + previousCount + ".").withStyle(ChatFormatting.GRAY)));
        }
        if (next.count >= minMessage) {
            player.sendSystemMessage(Component.literal("Catch streak: ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(next.count + "x " + pretty(species)).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                    .append(Component.literal(" | Shiny chance: " + formatPercent(getShinyChance(player, species))).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
    }


    public static CatchStreak getActiveStreak(ServerPlayer player) {
        if (player == null) return null;
        load();
        CatchStreak streak = STREAKS.get(PlayerProfileManager.activeProfileId(player));
        if (streak == null) return null;
        CatchStreak copy = new CatchStreak();
        copy.species = streak.species;
        copy.count = streak.count;
        copy.updatedAt = streak.updatedAt;
        return copy;
    }

    public static double getCatchStreakExtraChance(ServerPlayer player, String species) {
        if (player == null || species == null) return 0.0D;
        load();
        return Math.max(0.0D, getShinyChance(player, species) - CONFIG.baseShinyChance);
    }

    public static boolean shouldForceShiny(ServerPlayer player, Object pokemon) {
        if (player == null || pokemon == null || !CONFIG.enabled) return false;
        load();
        String species = TrueCaughtDexManager.speciesId(pokemon);
        if (species.isBlank()) return false;

        // Cobblemon already performs the base shiny roll. Only roll the extra
        // chance from catch-streak bonuses so the base odds do not get doubled.
        double chance = getShinyChance(player, species);
        double extraChance = Math.max(0.0D, chance - CONFIG.baseShinyChance);
        return extraChance > 0.0D && ThreadLocalRandom.current().nextDouble() < extraChance;
    }

    public static double getShinyChance(ServerPlayer player, String species) {
        if (player == null || species == null) return CONFIG.baseShinyChance;
        load();
        CatchStreak streak = STREAKS.get(PlayerProfileManager.activeProfileId(player));
        String key = TrueCaughtDexManager.normalizeSpecies(species);
        if (streak == null || !key.equals(streak.species) || streak.count < CONFIG.minimumBonusStreak) return CONFIG.baseShinyChance;
        int bonusCatches = Math.max(0, streak.count - CONFIG.minimumBonusStreak + 1);
        return Math.min(Math.max(CONFIG.baseShinyChance, CONFIG.maxShinyChance), CONFIG.baseShinyChance + (bonusCatches * CONFIG.bonusPerCatch));
    }

    public static boolean setShiny(Object pokemon, boolean shiny) {
        if (pokemon == null) return false;
        Object target = unwrapPokemon(pokemon);
        try {
            Method method = target.getClass().getMethod("setShiny", boolean.class);
            method.setAccessible(true);
            method.invoke(target, shiny);
            return true;
        } catch (Throwable ignored) {}
        try {
            Field field = findField(target.getClass(), "shiny");
            if (field != null && (field.getType() == boolean.class || field.getType() == Boolean.class)) {
                field.setAccessible(true);
                field.set(target, shiny);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isShiny(Object pokemon) {
        Object target = unwrapPokemon(pokemon);
        Object value = firstValue(target, "getShiny", "isShiny", "shiny");
        return value instanceof Boolean b && b;
    }

    public static Object unwrapPokemon(Object value) {
        if (value == null) return null;
        Object nested = firstValue(value, "pokemon", "getPokemon");
        return nested == null ? value : nested;
    }

    private static CatchStreak sanitize(CatchStreak streak) {
        CatchStreak out = new CatchStreak();
        if (streak != null) {
            out.species = TrueCaughtDexManager.normalizeSpecies(streak.species);
            out.count = Math.max(0, streak.count);
            out.updatedAt = Math.max(0L, streak.updatedAt);
        }
        return out;
    }

    private static String pretty(String species) {
        String normalized = TrueCaughtDexManager.normalizeSpecies(species).replace('_', ' ');
        String[] words = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return builder.length() == 0 ? species : builder.toString();
    }

    private static String formatPercent(double value) {
        return String.format(Locale.US, "%.3f%%", value * 100.0D);
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get") || name.startsWith("is")) {
                    Method method = source.getClass().getMethod(name);
                    method.setAccessible(true);
                    if (method.getParameterCount() == 0) {
                        Object value = method.invoke(source);
                        if (value != null) return value;
                    }
                } else {
                    Field field = findField(source.getClass(), name);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(source);
                        if (value != null) return value;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); } catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }
}
