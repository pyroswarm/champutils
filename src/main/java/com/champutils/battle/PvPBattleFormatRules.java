package com.champutils.battle;

import com.champutils.config.Config;
import com.champutils.config.Format;
import com.cobblemon.mod.common.battles.BattleFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bridges ChampUtils PvP formats to Cobblemon's native Showdown battle rules.
 *
 * This intentionally uses a small reflection bridge because Cobblemon's Kotlin BattleFormat API has
 * changed JVM exposure between versions. If a method/field name changes, PvP still starts normally
 * instead of hard-crashing the server.
 */
public final class PvPBattleFormatRules {

    private PvPBattleFormatRules() {
    }

    public static BattleFormat getCobblemonFormat(String formatId) {
        return getCobblemonFormatWithExtraRules(formatId);
    }

    public static BattleFormat getCobblemonFormatWithExtraRules(String formatId, String... extraRules) {
        Format configured = getFormat(formatId);
        BattleFormat base = resolveBaseFormat(configured);
        Set<String> rules = collectRules(formatId, configured);
        if (extraRules != null) {
            for (String rule : extraRules) {
                String normalized = normalizeRule(rule);
                if (normalized != null) rules.add(normalized);
            }
        }

        if (rules.isEmpty()) {
            return base;
        }

        BattleFormat modified = applyRules(base, rules);
        return modified == null ? base : modified;
    }

    public static Set<String> getRulesForDisplay(String formatId) {
        return collectRules(formatId, getFormat(formatId));
    }

    private static Format getFormat(String formatId) {
        if (Config.formats == null || formatId == null || formatId.isBlank()) {
            return null;
        }

        return Config.formats.get(formatId.toLowerCase(Locale.ROOT));
    }

    private static Set<String> collectRules(String formatId, Format configured) {
        Set<String> rules = new LinkedHashSet<>();

        if (configured != null && configured.battle_rules != null) {
            for (String rule : configured.battle_rules) {
                String normalized = normalizeRule(rule);
                if (normalized != null) {
                    rules.add(normalized);
                }
            }
        }

        // Ranked should always have Sleep Clause even if an older rules.json is missing the new field.
        if ("ranked".equalsIgnoreCase(formatId)) {
            rules.add("Sleep Clause Mod");
        }

        return rules;
    }

    private static String normalizeRule(String rule) {
        if (rule == null) {
            return null;
        }

        String trimmed = rule.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String compact = trimmed
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");

        if (compact.equals("sleepclause") || compact.equals("sleepclausemod")) {
            return "Sleep Clause Mod";
        }
        if (compact.equals("speciesclause")) {
            return "Species Clause";
        }
        if (compact.equals("ohkoclause")) {
            return "OHKO Clause";
        }
        if (compact.equals("evasionmovesclause") || compact.equals("evasionclause")) {
            return "Evasion Moves Clause";
        }
        if (compact.equals("endlessbattleclause")) {
            return "Endless Battle Clause";
        }
        if (compact.equals("moodyclause")) {
            return "Moody Clause";
        }
        if (compact.equals("batonpassclause")) {
            return "Baton Pass Clause";
        }
        if (compact.equals("swaggerclause")) {
            return "Swagger Clause";
        }

        return trimmed;
    }

    private static BattleFormat resolveBaseFormat(Format configured) {
        String identifier = configured == null || configured.cobblemon_format == null || configured.cobblemon_format.isBlank()
                ? "gen9singles"
                : configured.cobblemon_format.trim();

        BattleFormat fromIdentifier = invokeFromFormatIdentifier(identifier);
        if (fromIdentifier != null) {
            applyLevelCap(fromIdentifier, configured);
            return fromIdentifier;
        }

        BattleFormat fallback = getDefaultGen9Singles();
        applyLevelCap(fallback, configured);
        return fallback;
    }

    private static BattleFormat invokeFromFormatIdentifier(String identifier) {
        try {
            Method method = BattleFormat.class.getMethod("fromFormatIdentifier", String.class);
            Object result = method.invoke(null, identifier);
            return result instanceof BattleFormat battleFormat ? battleFormat : null;
        }
        catch (Throwable ignored) {
            // Try companion below.
        }

        try {
            Field companionField = BattleFormat.class.getField("Companion");
            Object companion = companionField.get(null);
            Method method = companion.getClass().getMethod("fromFormatIdentifier", String.class);
            Object result = method.invoke(companion, identifier);
            return result instanceof BattleFormat battleFormat ? battleFormat : null;
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    private static BattleFormat getDefaultGen9Singles() {
        try {
            Field field = BattleFormat.class.getField("GEN_9_SINGLES");
            Object result = field.get(null);
            if (result instanceof BattleFormat battleFormat) {
                return battleFormat;
            }
        }
        catch (Throwable ignored) {
            // Try companion below.
        }

        try {
            Field companionField = BattleFormat.class.getField("Companion");
            Object companion = companionField.get(null);
            Method method = companion.getClass().getMethod("getGEN_9_SINGLES");
            Object result = method.invoke(companion);
            if (result instanceof BattleFormat battleFormat) {
                return battleFormat;
            }
        }
        catch (Throwable ignored) {
            // Let the caller/server log the actual Cobblemon API issue if this ever happens.
        }

        throw new IllegalStateException("Could not resolve Cobblemon BattleFormat.GEN_9_SINGLES");
    }

    private static void applyLevelCap(BattleFormat battleFormat, Format configured) {
        if (battleFormat == null || configured == null || configured.level_cap <= 0) {
            return;
        }

        // Do not set Cobblemon adjustLevel here. In Cobblemon this normalizes every party member
        // to the supplied level. For ranked/casual we want level_cap to mean maximum allowed level only.
        // ChampUtils TeamValidator/BattlePrep enforce the cap before the battle starts.
    }

    private static BattleFormat applyRules(BattleFormat battleFormat, Set<String> rules) {
        try {
            Method method = BattleFormat.class.getMethod("setBattleRules", BattleFormat.class, Set.class);
            Object result = method.invoke(null, battleFormat, rules);
            return result instanceof BattleFormat modified ? modified : null;
        }
        catch (Throwable ignored) {
            // Try companion below.
        }

        try {
            Field companionField = BattleFormat.class.getField("Companion");
            Object companion = companionField.get(null);
            Method method = companion.getClass().getMethod("setBattleRules", BattleFormat.class, Set.class);
            Object result = method.invoke(companion, battleFormat, rules);
            return result instanceof BattleFormat modified ? modified : null;
        }
        catch (Throwable throwable) {
            System.out.println("[ChampUtils] Failed to apply Cobblemon PvP battle rules " + rules + ": " + throwable.getMessage());
            return null;
        }
    }
}
