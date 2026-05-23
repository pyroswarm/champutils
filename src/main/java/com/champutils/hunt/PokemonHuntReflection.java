package com.champutils.hunt;

import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class PokemonHuntReflection {

    private PokemonHuntReflection() {}

    public static ServerPlayer extractPlayer(Object event) {
        Object player = firstValue(event,
                "player", "getPlayer",
                "capturer", "getCapturer",
                "catcher", "getCatcher",
                "owner", "getOwner"
        );
        return player instanceof ServerPlayer ? (ServerPlayer) player : null;
    }

    public static Object extractPokemon(Object event) {
        Object pokemon = firstValue(event,
                "pokemon", "getPokemon",
                "caught", "getCaught",
                "captured", "getCaptured",
                "pokemonEntity", "getPokemonEntity"
        );
        if (pokemon == null) return null;
        Object nested = firstValue(pokemon, "pokemon", "getPokemon");
        return nested == null ? pokemon : nested;
    }

    public static String speciesId(Object pokemon) {
        if (pokemon == null) return "";
        try {
            Object species = call(pokemon, "getSpecies");
            if (species == null) species = call(pokemon, "species");
            if (species != null) {
                Object id = firstValue(species, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId");
                if (id != null) return normalizeId(String.valueOf(id));
                Object name = firstValue(species, "name", "getName");
                if (name != null) return normalizeId(String.valueOf(name));
                return normalizeId(String.valueOf(species));
            }
        } catch (Exception ignored) {}
        return normalizeId(String.valueOf(pokemon));
    }

    public static String natureName(Object pokemon) {
        if (pokemon == null) return "";
        Object nature = firstValue(pokemon, "nature", "getNature");
        if (nature == null) return "";
        String direct = cleanFromKnown(String.valueOf(nature), allNatures());
        if (!direct.isBlank()) return direct;
        Object id = firstValue(nature, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId", "name", "getName", "path", "getPath");
        if (id != null) return cleanFromKnown(String.valueOf(id), allNatures());
        Object template = firstValue(nature, "template", "getTemplate");
        if (template != null) {
            Object nested = firstValue(template, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId", "name", "getName", "path", "getPath");
            if (nested != null) return cleanFromKnown(String.valueOf(nested), allNatures());
        }
        return "";
    }

    public static String genderName(Object pokemon) {
        if (pokemon == null) return "";
        Object gender = firstValue(pokemon, "gender", "getGender", "sex", "getSex");
        if (gender == null) return "genderless";
        String lower = String.valueOf(gender).toLowerCase(Locale.ROOT);
        if (lower.contains("female")) return "female";
        if (lower.contains("male")) return "male";
        if (lower.contains("genderless") || lower.contains("none") || lower.contains("unknown")) return "genderless";
        return normalizeTrait(lower);
    }

    public static String abilityName(Object pokemon) {
        if (pokemon == null) return "";
        Object ability = firstValue(pokemon, "ability", "getAbility");
        if (ability == null) return "";
        Object id = firstValue(ability, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId", "name", "getName", "path", "getPath");
        if (id != null) return normalizeTrait(String.valueOf(id));
        Object template = firstValue(ability, "template", "getTemplate");
        if (template != null) {
            Object nested = firstValue(template, "resourceIdentifier", "getResourceIdentifier", "identifier", "getIdentifier", "id", "getId", "name", "getName", "path", "getPath");
            if (nested != null) return normalizeTrait(String.valueOf(nested));
        }
        return normalizeTrait(String.valueOf(ability));
    }

    public static String normalizeId(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) value = value.substring(colon + 1);
        value = value.replaceAll("[^a-z0-9_-]", "");
        return value;
    }

    public static String normalizeTrait(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < value.length()) value = value.substring(colon + 1);
        return value.replaceAll("[^a-z0-9]", "");
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            Object value;
            if (name.startsWith("get") || name.endsWith("()")) {
                value = call(source, name.replace("()", ""));
            } else {
                value = field(source, name);
                if (value == null) value = call(source, name);
            }
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
        } catch (Exception ignored) {}
        return null;
    }

    private static Object field(Object source, String fieldName) {
        if (source == null || fieldName == null || fieldName.isBlank()) return null;
        Class<?> type = source.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(source);
            } catch (Exception ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static String cleanFromKnown(String raw, String[] known) {
        if (raw == null) return "";
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String value : known) {
            if (lower.matches(".*(^|[^a-z])" + value + "($|[^a-z]).*")) return value;
        }
        return "";
    }

    private static String[] allNatures() {
        return new String[] {
                "hardy", "lonely", "brave", "adamant", "naughty",
                "bold", "docile", "relaxed", "impish", "lax",
                "timid", "hasty", "serious", "jolly", "naive",
                "modest", "mild", "quiet", "bashful", "rash",
                "calm", "gentle", "sassy", "careful", "quirky"
        };
    }
}
