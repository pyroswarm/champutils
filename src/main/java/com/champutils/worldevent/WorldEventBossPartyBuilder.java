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

import com.champutils.util.CobblemonHeldItemUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WorldEventBossPartyBuilder {

    private WorldEventBossPartyBuilder() {}

    private static final int MAX_MOVES = 4;

    private static final Set<String> DISALLOWED_BOSS_MOVES = Set.of(
            "protect", "detect", "endure", "wideguard", "quickguard", "kingsshield",
            "spikyshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark"
    );

    private static final List<String> SAFE_FALLBACK_MOVES = List.of(
            "earthquake", "thunderbolt", "flamethrower", "icebeam", "shadowball", "closecombat", "dragonpulse", "psychic"
    );

    public static boolean applyTeam(NPCEntity npc, WorldEventConfig.TeamDefinition team) {
        int level = team == null ? 1 : team.levelCap;
        return applyTeam(npc, team, level);
    }

    public static boolean applyTeam(NPCEntity npc, WorldEventConfig.TeamDefinition team, int forcedLevel) {
        if (npc == null || team == null || team.party == null || team.party.isEmpty()) return false;

        try {
            int battleLevel = Math.max(1, Math.min(100, forcedLevel));
            npc.initialize(battleLevel);
            NPCPartyStore party = new NPCPartyStore(npc);
            int slot = 0;

            java.util.List<WorldEventConfig.PokemonSet> pool = new java.util.ArrayList<>(team.party);
            java.util.Collections.shuffle(pool);
            for (WorldEventConfig.PokemonSet set : pool) {
                if (slot >= Math.max(1, Math.min(6, team.partySize))) break;
                Pokemon pokemon = createPokemon(set, battleLevel);
                if (pokemon != null) {
                    try { pokemon.heal(); } catch (Exception ignored) {}
                    party.set(slot++, pokemon);
                }
            }

            party.initialize();
            npc.setParty(party);
            npc.setSkill(5);
            npc.setHealth(npc.getMaxHealth());
            npc.setPersistenceRequired();
            return slot > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Pokemon createPokemon(WorldEventConfig.PokemonSet set, int forcedLevel) {
        try {
            String species = normalizeSpecies(set.species);
            Pokemon pokemon = PokemonProperties.Companion
                    .parse("species=\"" + species + "\" level=" + Math.max(1, Math.min(100, forcedLevel)))
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

            applyBossMoves(pokemon, set.moves);

            applyIVs(pokemon, set.ivs);
            applyEVs(pokemon, set.evs);
            applyHeldItem(pokemon, set.heldItem);
            pokemon.heal();
            return pokemon;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean applyHeldItem(Pokemon pokemon, String heldItemId) {
        if (pokemon == null) return false;
        try {
            ItemStack heldItem = CobblemonHeldItemUtil.createHeldItemStack(heldItemId);
            if (heldItem.isEmpty()) return false;
            pokemon.swapHeldItem(heldItem, false, false);
            return !pokemon.heldItem().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void applyBossMoves(Pokemon pokemon, List<String> configuredMoves) {
        if (pokemon == null) return;
        try {
            pokemon.getMoveSet().clear();
            LinkedHashSet<String> candidates = new LinkedHashSet<>();
            if (configuredMoves != null) {
                for (String move : configuredMoves) {
                    String key = cleanMoveKey(move);
                    if (!key.isBlank() && !isDisallowedBossMove(key)) candidates.add(key);
                }
            }
            for (String fallback : SAFE_FALLBACK_MOVES) candidates.add(fallback);

            int learned = 0;
            for (String move : candidates) {
                if (learned >= MAX_MOVES) break;
                if (tryAddMove(pokemon, move)) learned++;
            }
        } catch (Exception ignored) {}
    }

    private static boolean tryAddMove(Pokemon pokemon, String moveKey) {
        if (pokemon == null || moveKey == null || moveKey.isBlank() || isDisallowedBossMove(moveKey)) return false;
        try {
            pokemon.getMoveSet().add(Moves.getByName(moveKey).create());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isDisallowedBossMove(String moveKey) {
        return DISALLOWED_BOSS_MOVES.contains(cleanMoveKey(moveKey));
    }

    private static String normalizeSpecies(String species) {
        if (species == null || species.isBlank()) return "cobblemon:pikachu";
        String s = species.trim().toLowerCase(Locale.ROOT);
        if (!s.contains(":")) s = "cobblemon:" + s;
        return s;
    }

    private static String cleanKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9_]", "");
    }

    private static String cleanMoveKey(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9]", "");
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
