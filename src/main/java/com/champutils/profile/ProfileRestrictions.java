package com.champutils.profile;

import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ProfileRestrictions {
    private ProfileRestrictions() {}

    public static boolean blockIronmanTrade(ServerPlayer player, String featureName) {
        if (player != null && PlayerProfileManager.isIronman(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("Ironman profiles cannot use " + featureName + ".").withStyle(ChatFormatting.RED));
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
            if (pokemon == null) continue;
            if (!hasType(pokemon, required)) {
                String name = pokemon.getSpecies() == null ? "A Pokémon" : pokemon.getSpecies().getName();
                return "Monotype profile requires every battle Pokémon to include " + required + " type. Invalid: " + name;
            }
        }
        return null;
    }

    private static boolean hasType(Pokemon pokemon, String required) {
        String needle = required.toLowerCase();
        try {
            Object types = pokemon.getClass().getMethod("getTypes").invoke(pokemon);
            if (types instanceof Iterable<?> iterable) {
                for (Object type : iterable) if (type != null && type.toString().toLowerCase().contains(needle)) return true;
            }
        } catch (Exception ignored) {}
        try {
            Object species = pokemon.getSpecies();
            Object types = species.getClass().getMethod("getTypes").invoke(species);
            if (types instanceof Iterable<?> iterable) {
                for (Object type : iterable) if (type != null && type.toString().toLowerCase().contains(needle)) return true;
            }
        } catch (Exception ignored) {}
        try {
            Object form = pokemon.getClass().getMethod("getForm").invoke(pokemon);
            Object types = form.getClass().getMethod("getTypes").invoke(form);
            if (types instanceof Iterable<?> iterable) {
                for (Object type : iterable) if (type != null && type.toString().toLowerCase().contains(needle)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
