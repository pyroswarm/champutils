package com.champutils.debug;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Central runtime debug switch for ChampUtils.
 * Debug output should be silent by default and enabled only through /champdebug.
 */
public final class ChampDebugManager {
    public enum Category {
        AI,
        PROFILES,
        SPAWNS,
        BOSSES,
        GYMS,
        ISLANDER,
        PERFORMANCE,
        DATABASE,
        CRAFTING,
        TERRITORIES,
        EXPLORATION,
        ANTILAG,
        ITEMS,
        GUILDS
    }

    private static final EnumSet<Category> ENABLED = EnumSet.noneOf(Category.class);

    private ChampDebugManager() {}

    public static synchronized void off() {
        ENABLED.clear();
    }

    public static synchronized void all() {
        ENABLED.clear();
        ENABLED.addAll(EnumSet.allOf(Category.class));
    }

    public static synchronized boolean setOnly(String raw) {
        Category category = parse(raw);
        if (category == null) return false;
        ENABLED.clear();
        ENABLED.add(category);
        return true;
    }

    public static synchronized void setOnly(Category category) {
        ENABLED.clear();
        if (category != null) ENABLED.add(category);
    }

    public static synchronized boolean enable(String raw) {
        Category category = parse(raw);
        if (category == null) return false;
        ENABLED.add(category);
        return true;
    }

    public static synchronized boolean disable(String raw) {
        Category category = parse(raw);
        if (category == null) return false;
        ENABLED.remove(category);
        return true;
    }

    public static synchronized Set<Category> enabledCategories() {
        return EnumSet.copyOf(ENABLED);
    }

    public static boolean isEnabled(Category category) {
        if (category == null) return false;
        synchronized (ChampDebugManager.class) {
            return ENABLED.contains(category);
        }
    }

    public static void log(Category category, String message) {
        if (!isEnabled(category)) return;
        System.out.println(message);
    }

    public static String status() {
        synchronized (ChampDebugManager.class) {
            if (ENABLED.isEmpty()) return "off";
            if (ENABLED.size() == Category.values().length) return "all";
            List<String> names = new ArrayList<>();
            for (Category category : ENABLED) names.add(category.name().toLowerCase(Locale.ROOT));
            return String.join(", ", names);
        }
    }

    public static List<String> suggestions() {
        List<String> values = new ArrayList<>();
        values.add("off");
        values.add("all");
        for (Category category : Category.values()) values.add(category.name().toLowerCase(Locale.ROOT));
        values.add("profile");
        values.add("profiles");
        values.add("spawn");
        values.add("spawns");
        values.add("boss");
        values.add("bosses");
        values.add("mega_bosses");
        values.add("performance");
        values.add("perf");
        return values;
    }

    public static Category parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (value) {
            case "ai", "battle_ai", "battles" -> Category.AI;
            case "profile", "profiles", "profile_timing", "profiletiming" -> Category.PROFILES;
            case "spawn", "spawns", "specialspawn", "special_spawns", "special" -> Category.SPAWNS;
            case "boss", "bosses", "megaboss", "mega_boss", "mega_bosses" -> Category.BOSSES;
            case "gym", "gyms", "gymleaders", "gym_leaders" -> Category.GYMS;
            case "islander", "islands", "islander_profile" -> Category.ISLANDER;
            case "performance", "perf", "timing", "tick", "ticktiming" -> Category.PERFORMANCE;
            case "database", "db", "sql" -> Category.DATABASE;
            case "crafting", "craft", "champcrafting" -> Category.CRAFTING;
            case "territory", "territories", "claims" -> Category.TERRITORIES;
            case "exploration", "explore" -> Category.EXPLORATION;
            case "antilag", "anti_lag", "lag" -> Category.ANTILAG;
            case "item", "items", "itemdebug" -> Category.ITEMS;
            case "guild", "guilds", "guilddebug" -> Category.GUILDS;
            default -> null;
        };
    }
}
