package com.champutils.profile;

import com.champutils.breeding.BreedingEggData;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class ProfileRestrictions {
    private ProfileRestrictions() {}

    public static boolean blockIronmanTrade(ServerPlayer player, String featureName) {
        if (player != null && PlayerProfileManager.blocksAuctionHouse(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal(PlayerProfileManager.gameMode(player).displayName() + " profiles cannot use " + featureName + ".").withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    public static boolean blockPvp(ServerPlayer player, String featureName) {
        if (player != null && PlayerProfileManager.isNuzlocke(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("Nuzlocke profiles cannot use " + featureName + ".").withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    public static String validateBattleProfile(ServerPlayer player, Iterable<Pokemon> partyPokemon) {
        if (player == null) return null;
        if (PlayerProfileManager.gameMode(player) != ProfileGameMode.MONOTYPE) return null;
        String required = PlayerProfileManager.monotypeType(player);
        if (required == null || required.isBlank()) return null;

        for (Pokemon pokemon : partyPokemon) {
            if (pokemon == null || BreedingEggData.isEgg(pokemon)) continue;
            if (!hasType(pokemon, required)) {
                String name = pokemon.getSpecies() == null ? "A Pokémon" : pokemon.getSpecies().getName();
                return "Monotype profile requires every battle Pokémon to include " + normalizeTypeName(required) + " type. Invalid: " + name;
            }
        }
        return null;
    }

    public static boolean hasType(Pokemon pokemon, String required) {
        if (pokemon == null || required == null || required.isBlank()) return false;

        String needle = normalizeTypeName(required);
        ElementalType requiredType = ElementalTypes.get(needle);

        for (ElementalType type : collectPokemonTypes(pokemon)) {
            if (matchesType(type, needle, requiredType)) {
                return true;
            }
        }

        return false;
    }

    private static Set<ElementalType> collectPokemonTypes(Pokemon pokemon) {
        Set<ElementalType> result = new LinkedHashSet<>();

        // Primary source: Cobblemon's Pokemon.types property. This is backed by the
        // active form and is the safest source for regional/form-specific typing.
        try {
            for (ElementalType type : pokemon.getTypes()) {
                if (type != null) result.add(type);
            }
        } catch (Throwable ignored) {}

        // Defensive fallbacks: in some load/migration paths a Pokemon object can be
        // partially hydrated for a tick. Check the active FormData and Species directly.
        addTypesFromObject(result, safeInvoke(pokemon, "getForm"));
        addTypesFromObject(result, safeInvoke(pokemon, "getSpecies"));

        return result;
    }

    private static void addTypesFromObject(Set<ElementalType> result, Object source) {
        if (source == null) return;

        Object types = safeInvoke(source, "getTypes");
        if (types instanceof Iterable<?> iterable) {
            for (Object type : iterable) {
                if (type instanceof ElementalType elementalType) {
                    result.add(elementalType);
                }
            }
        }

        Object primary = safeInvoke(source, "getPrimaryType");
        if (primary instanceof ElementalType primaryType) {
            result.add(primaryType);
        }

        Object secondary = safeInvoke(source, "getSecondaryType");
        if (secondary instanceof ElementalType secondaryType) {
            result.add(secondaryType);
        }
    }

    private static boolean matchesType(ElementalType type, String required, ElementalType requiredType) {
        if (type == null) return false;
        if (requiredType != null && type == requiredType) return true;
        if (requiredType != null && type.equals(requiredType)) return true;

        if (normalizeTypeName(type.getName()).equals(required)) return true;
        if (normalizeTypeName(type.showdownId()).equals(required)) return true;

        try {
            if (normalizeTypeName(type.getDisplayName().getString()).equals(required)) return true;
        } catch (Throwable ignored) {}

        try {
            String path = type.getResourceLocation().getPath();
            if (normalizeTypeName(path).equals(required)) return true;
        } catch (Throwable ignored) {}


        return false;
    }

    private static Object safeInvoke(Object target, String method) {
        if (target == null) return null;
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String normalizeTypeName(String raw) {
        return raw == null
                ? ""
                : raw.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "").replace("-", "");
    }
}
