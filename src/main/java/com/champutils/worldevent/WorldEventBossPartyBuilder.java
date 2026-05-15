package com.champutils.worldevent;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.resources.ResourceLocation;

public final class WorldEventBossPartyBuilder {

    private WorldEventBossPartyBuilder() {}

    public static boolean applyTeam(NPCEntity npc, WorldEventConfig.TeamDefinition team) {
        if (npc == null || team == null || team.party == null || team.party.isEmpty()) return false;

        try {
            npc.initialize(Math.max(1, team.levelCap));
            NPCPartyStore party = new NPCPartyStore(npc);
            int slot = 0;

            for (WorldEventConfig.PokemonSet set : team.party) {
                if (slot >= Math.max(1, Math.min(6, team.partySize))) break;
                Pokemon pokemon = createPokemon(set);
                if (pokemon != null) {
                    try { pokemon.heal(); } catch (Exception ignored) {}
                    party.set(slot++, pokemon);
                }
            }

            party.initialize();
            npc.setParty(party);
            npc.setHealth(npc.getMaxHealth());
            npc.setPersistenceRequired();
            return slot > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Pokemon createPokemon(WorldEventConfig.PokemonSet set) {
        try {
            String species = normalizeSpecies(set.species);
            Pokemon pokemon = PokemonProperties.Companion
                    .parse("species=\"" + species + "\" level=" + Math.max(1, set.level))
                    .create();

            try {
                if (set.ability != null && !set.ability.isBlank()) {
                    pokemon.updateAbility(
                            Abilities.INSTANCE
                                    .getOrException(cleanKey(set.ability))
                                    .create(false, Priority.NORMAL)
                    );
                }
            } catch (Exception ignored) {}

            try {
                if (set.nature != null && !set.nature.isBlank()) {
                    pokemon.setNature(
                            Natures.INSTANCE.getNature(
                                    ResourceLocation.parse("cobblemon:" + cleanKey(set.nature))
                            )
                    );
                }
            } catch (Exception ignored) {}

            try {
                if (set.moves != null && !set.moves.isEmpty()) {
                    pokemon.getMoveSet().clear();
                    for (String move : set.moves) {
                        try {
                            pokemon.getMoveSet().add(Moves.getByName(cleanKey(move)).create());
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            applyIVs(pokemon, set.ivs);
            applyEVs(pokemon, set.evs);
            pokemon.heal();
            return pokemon;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String normalizeSpecies(String species) {
        if (species == null || species.isBlank()) return "cobblemon:pikachu";
        String s = species.trim().toLowerCase();
        if (!s.contains(":")) s = "cobblemon:" + s;
        return s;
    }

    private static String cleanKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9_]", "");
    }

    private static void applyIVs(Pokemon pokemon, WorldEventConfig.StatSet set) {
        if (pokemon == null || set == null) return;
        try {
            var ivs = pokemon.getIvs();
            ivs.set(Stats.HP, clampIv(set.hp));
            ivs.set(Stats.ATTACK, clampIv(set.atk));
            ivs.set(Stats.DEFENCE, clampIv(set.def));
            ivs.set(Stats.SPECIAL_ATTACK, clampIv(set.spa));
            ivs.set(Stats.SPECIAL_DEFENCE, clampIv(set.spd));
            ivs.set(Stats.SPEED, clampIv(set.spe));
        } catch (Exception ignored) {}
    }

    private static void applyEVs(Pokemon pokemon, WorldEventConfig.StatSet set) {
        if (pokemon == null || set == null) return;
        try {
            int hp = clampEv(set.hp);
            int atk = clampEv(set.atk);
            int def = clampEv(set.def);
            int spa = clampEv(set.spa);
            int spd = clampEv(set.spd);
            int spe = clampEv(set.spe);
            int total = hp + atk + def + spa + spd + spe;
            if (total > 510) {
                double scale = 510D / total;
                hp = (int)(hp * scale);
                atk = (int)(atk * scale);
                def = (int)(def * scale);
                spa = (int)(spa * scale);
                spd = (int)(spd * scale);
                spe = (int)(spe * scale);
            }
            var evs = pokemon.getEvs();
            evs.set(Stats.HP, hp);
            evs.set(Stats.ATTACK, atk);
            evs.set(Stats.DEFENCE, def);
            evs.set(Stats.SPECIAL_ATTACK, spa);
            evs.set(Stats.SPECIAL_DEFENCE, spd);
            evs.set(Stats.SPEED, spe);
        } catch (Exception ignored) {}
    }

    private static int clampIv(int value) { return Math.max(0, Math.min(31, value)); }
    private static int clampEv(int value) { return Math.max(0, Math.min(252, value)); }
}
