package com.champutils.breeding;

import com.champutils.util.CobblemonEventReflection;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Persists the originating client language on newly captured and newly bred Pokémon.
 * Cobblemon does not expose a main-series language field on Pokemon, so ChampUtils
 * stores a small namespaced value in Pokemon persistent data for Masuda checks.
 */
public final class BreedingOriginLanguageTracker {
    public static final String KEY = "champutils_origin_language";
    private static boolean registered;

    private BreedingOriginLanguageTracker() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        try {
            Class<?> eventsClass = Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents");
            Object observable = eventsClass.getField("POKEMON_CAPTURED").get(null);
            if (!CobblemonEventReflection.subscribe(observable, BreedingOriginLanguageTracker::handleCapture)) {
                throw new IllegalStateException("No compatible capture-event subscribe method found.");
            }
            System.out.println("[ChampUtils][Breeding] Pokémon origin-language tracking registered.");
        } catch (Throwable error) {
            System.err.println("[ChampUtils][Breeding] Failed to register Pokémon origin-language tracking.");
            error.printStackTrace();
        }
    }

    public static void tag(Pokemon pokemon, ServerPlayer player) {
        tag(pokemon, player, true);
    }

    public static void tagSilently(Pokemon pokemon, ServerPlayer player) {
        tag(pokemon, player, false);
    }

    private static void tag(Pokemon pokemon, ServerPlayer player, boolean notifyStore) {
        if (pokemon == null || player == null) return;
        String language = playerLanguage(player);
        if (language.isBlank()) return;
        try {
            pokemon.getPersistentData().putString(KEY, language);
            if (notifyStore) pokemon.onChange(null);
        } catch (Throwable error) {
            System.err.println("[ChampUtils][Breeding] Failed to persist Pokémon origin language.");
            error.printStackTrace();
        }
    }

    public static String get(Pokemon pokemon) {
        if (pokemon == null) return "";
        try {
            return normalize(pokemon.getPersistentData().getString(KEY));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void handleCapture(Object event) {
        Pokemon pokemon = value(event, "pokemon", "getPokemon") instanceof Pokemon captured ? captured : null;
        ServerPlayer player = value(event, "player", "getPlayer") instanceof ServerPlayer capturingPlayer ? capturingPlayer : null;
        tag(pokemon, player);
    }

    private static String playerLanguage(ServerPlayer player) {
        try {
            Method clientInformation = player.getClass().getMethod("clientInformation");
            Object info = clientInformation.invoke(player);
            if (info == null) return "";
            Method language = info.getClass().getMethod("language");
            return normalize(String.valueOf(language.invoke(info)));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Object value(Object source, String fieldName, String getterName) {
        if (source == null) return null;
        try {
            Method getter = source.getClass().getMethod(getterName);
            return getter.invoke(source);
        } catch (Throwable ignored) {
        }
        Class<?> type = source.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
