package com.champutils.validation;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;

import com.champutils.config.Config;
import com.champutils.config.Format;

import net.minecraft.server.level.ServerPlayer;

import com.champutils.util.CobblemonHeldItemUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TeamValidator {

    public static String validate(
            ServerPlayer player,
            String formatId
    ) {

        if (
                formatId == null ||
                        formatId.isBlank()
        ) {
            return null;
        }


        PartyStore party =
                Cobblemon.INSTANCE
                        .getStorage()
                        .getParty(player);

        if (party == null) {
            return "No party found.";
        }


        Format format =
                Config.formats.get(
                        formatId.toLowerCase()
                );


        if (format == null) {
            return "Invalid format: " + formatId;
        }


        Set<String> bannedPokemon =
                toSet(format.banned_pokemon);

        Set<String> bannedMoves =
                toSet(format.banned_moves);

        Set<String> bannedItems =
                toSet(format.banned_items);

        Set<String> bannedAbilities =
                toSet(format.banned_abilities);


        int levelCap =
                format.level_cap;


        for (Pokemon pokemon : party) {

            if (pokemon == null) {
                continue;
            }


            /*
             LEVEL CAP
             */
            if (
                    levelCap > 0 &&
                            pokemon.getLevel() > levelCap
            ) {
                return "Level cap exceeded (" + levelCap + ")";
            }


            /*
             BANNED POKEMON
             */
            String species =
                    pokemon.getSpecies()
                            .getResourceIdentifier()
                            .toString()
                            .toLowerCase();

            if (
                    bannedPokemon.contains(
                            species
                    )
            ) {
                return "Banned Pokémon: " + species;
            }


            /*
             BANNED ABILITIES
             */
            if (
                    pokemon.getAbility() != null
            ) {
                String ability =
                        pokemon.getAbility()
                                .getName()
                                .toLowerCase();

                if (
                        bannedAbilities.contains(
                                ability
                        )
                ) {
                    return "Banned ability: " + ability;
                }
            }


            /*
             BANNED HELD ITEMS
             */
            if (
                    pokemon.heldItem() != null &&
                            !pokemon.heldItem().isEmpty()
            ) {
                if (
                        CobblemonHeldItemUtil.matchesConfiguredItem(
                                pokemon.heldItem(),
                                bannedItems
                        )
                ) {
                    return "Banned item: " +
                            CobblemonHeldItemUtil.getDisplayId(
                                    pokemon.heldItem()
                            );
                }
            }


            /*
             BANNED MOVES
             */
            var moveSet =
                    pokemon.getMoveSet();

            if (
                    moveSet != null &&
                            moveSet.getMoves() != null
            ) {

                for (
                        var move :
                        moveSet.getMoves()
                ) {

                    if (
                            move == null ||
                                    move.getTemplate() == null
                    ) {
                        continue;
                    }

                    String moveName =
                            move.getTemplate()
                                    .getName()
                                    .toLowerCase();

                    if (
                            bannedMoves.contains(
                                    moveName
                            )
                    ) {
                        return "Banned move: " + moveName;
                    }
                }
            }
        }

        return null;
    }


    private static Set<String> toSet(
            List<String> list
    ) {
        Set<String> set =
                new HashSet<>();

        if (list == null) {
            return set;
        }

        for (String value : list) {
            if (
                    value != null &&
                            !value.isBlank()
            ) {
                set.add(
                        value.toLowerCase()
                );
            }
        }

        return set;
    }
}