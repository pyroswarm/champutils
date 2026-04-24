package com.champutils.validation;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;

import com.champutils.config.Config;
import com.champutils.config.Format;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TeamValidator {

    public static String validate(ServerPlayer player, String formatId) {

        // ONLY enforce ranked
        if (!"ranked".equalsIgnoreCase(formatId)) return null;

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        if (party == null) return "No party";

        Format format = Config.formats.get("ranked");
        if (format == null) return null;

        Set<String> bannedPokemon = toSet(format.banned_pokemon);
        Set<String> bannedMoves = toSet(format.banned_moves);
        Set<String> bannedItems = toSet(format.banned_items);
        Set<String> bannedAbilities = toSet(format.banned_abilities);

        int levelCap = format.level_cap;

        for (Pokemon p : party) {
            if (p == null) continue;

            // LEVEL CAP
            if (levelCap > 0 && p.getLevel() > levelCap) {
                return "Level cap exceeded (" + levelCap + ")";
            }

            // POKEMON
            String species = p.getSpecies().getResourceIdentifier().toString().toLowerCase();
            if (bannedPokemon.contains(species)) {
                return "Banned Pokémon: " + species;
            }

            // ABILITY
            if (p.getAbility() != null) {
                String ability = p.getAbility().getName().toLowerCase();
                if (bannedAbilities.contains(ability)) {
                    return "Banned ability: " + ability;
                }
            }

            // ITEM
            if (p.heldItem() != null && !p.heldItem().isEmpty()) {
                String item = p.heldItem().getItem().toString().toLowerCase();
                if (bannedItems.contains(item)) {
                    return "Banned item: " + item;
                }
            }

            // MOVES (SAFE)
            var moveSet = p.getMoveSet();
            if (moveSet != null && moveSet.getMoves() != null) {

                for (var move : moveSet.getMoves()) {

                    if (move == null || move.getTemplate() == null) continue;

                    String moveName = move.getTemplate().getName().toLowerCase();

                    if (bannedMoves.contains(moveName)) {
                        return "Banned move: " + moveName;
                    }
                }
            }
        }

        return null;
    }

    private static Set<String> toSet(List<String> list) {
        Set<String> set = new HashSet<>();
        if (list == null) return set;

        for (String s : list) {
            if (s != null) set.add(s.toLowerCase());
        }

        return set;
    }
}