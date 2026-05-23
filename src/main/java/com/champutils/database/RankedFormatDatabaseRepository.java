package com.champutils.database;

import com.champutils.config.Config;
import com.champutils.config.Format;
import com.champutils.rank.SeasonManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RankedFormatDatabaseRepository {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RankedFormatDatabaseRepository() {
    }

    public static void syncCurrentFormats() {
        if (Config.formats == null) {
            return;
        }

        syncFormat("ranked");
        syncFormat("casual");
    }

    public static void syncCurrentRankedFormat() {
        syncCurrentFormats();
    }

    private static void syncFormat(String formatKey) {
        Format format = Config.formats.get(formatKey);

        if (format == null) {
            return;
        }

        int seasonNumber = Math.max(1, SeasonManager.CURRENT_SEASON);
        String seasonName = SeasonManager.CURRENT_NAME == null || SeasonManager.CURRENT_NAME.isBlank()
                ? "Season " + seasonNumber
                : SeasonManager.CURRENT_NAME;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("seasonNumber", seasonNumber);
        payload.put("seasonName", seasonName);
        payload.put("formatKey", formatKey);
        payload.put("displayName", displayName(formatKey));
        payload.put("description", description(formatKey));
        payload.put("levelCap", format.level_cap);
        payload.put("battleItemsAllowed", format.allow_battle_items);
        payload.put("matchStyle", "6v6 Singles");
        payload.put("teamPreview", true);
        payload.put("teraAllowed", true);
        payload.put("sleepAllowed", true);
        payload.put("choiceItemsAllowed", !containsAny(format.banned_items, List.of("choice_band", "choice_scarf", "choice_specs")));
        payload.put("bannedPokemon", safeList(format.banned_pokemon));
        payload.put("bannedMoves", safeList(format.banned_moves));
        payload.put("bannedItems", safeList(format.banned_items));
        payload.put("bannedAbilities", safeList(format.banned_abilities));

        String json = GSON.toJson(payload);
        String displayName = displayName(formatKey);

        DatabaseManager.executeAsync("sync battle format " + formatKey, connection -> {
            try (PreparedStatement schema = connection.prepareStatement(
                    "create table if not exists public.ranked_formats (" +
                            "id text primary key, " +
                            "season integer not null, " +
                            "format_key text not null, " +
                            "display_name text not null, " +
                            "rules jsonb not null, " +
                            "updated_at timestamptz not null default now()" +
                            ")"
            )) {
                schema.executeUpdate();
            }

            try (PreparedStatement addSeason = connection.prepareStatement(
                    "alter table public.ranked_formats add column if not exists season integer"
            )) {
                addSeason.executeUpdate();
            }

            try (PreparedStatement addFormatKey = connection.prepareStatement(
                    "alter table public.ranked_formats add column if not exists format_key text"
            )) {
                addFormatKey.executeUpdate();
            }

            try (PreparedStatement addDisplayName = connection.prepareStatement(
                    "alter table public.ranked_formats add column if not exists display_name text"
            )) {
                addDisplayName.executeUpdate();
            }

            try (PreparedStatement addRules = connection.prepareStatement(
                    "alter table public.ranked_formats add column if not exists rules jsonb"
            )) {
                addRules.executeUpdate();
            }

            try (PreparedStatement addUpdatedAt = connection.prepareStatement(
                    "alter table public.ranked_formats add column if not exists updated_at timestamptz default now()"
            )) {
                addUpdatedAt.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into public.ranked_formats (id, season, format_key, display_name, rules, updated_at) " +
                            "values (?, ?, ?, ?, ?::jsonb, now()) " +
                            "on conflict (id) do update set " +
                            "season = excluded.season, " +
                            "format_key = excluded.format_key, " +
                            "display_name = excluded.display_name, " +
                            "rules = excluded.rules, " +
                            "updated_at = now()"
            )) {
                statement.setString(1, formatKey);
                statement.setInt(2, seasonNumber);
                statement.setString(3, formatKey);
                statement.setString(4, displayName);
                statement.setString(5, json);
                statement.executeUpdate();
            }
        });
    }

    private static String displayName(String formatKey) {
        if ("casual".equalsIgnoreCase(formatKey)) {
            return "CobbleChamps Casual Format";
        }

        return "CobbleChamps Ranked Format";
    }

    private static String description(String formatKey) {
        if ("casual".equalsIgnoreCase(formatKey)) {
            return "Relaxed practice battles with fewer restrictions and no ranked RP pressure.";
        }

        return "Stable OU-style CobbleChamps ranked rules for the current season.";
    }

    private static List<String> safeList(List<String> value) {
        if (value == null) {
            return Collections.emptyList();
        }
        return value;
    }

    private static boolean containsAny(List<String> source, List<String> targets) {
        if (source == null || source.isEmpty()) {
            return false;
        }

        for (String item : source) {
            if (item == null) {
                continue;
            }

            for (String target : targets) {
                if (item.equalsIgnoreCase(target)) {
                    return true;
                }
            }
        }

        return false;
    }
}
