package com.champutils.dungeon;

import com.champutils.util.CobblemonHeldItemUtil;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DungeonNpcPartyBuilder {

    private DungeonNpcPartyBuilder() {}

    public static boolean applyDungeonTeam(NPCEntity npc, DungeonTrainerConfig.TrainerWave wave, int level) {
        if (npc == null || wave == null || wave.pokemonPool == null || wave.pokemonPool.isEmpty()) return false;

        try {
            npc.initialize(level);

            NPCPartyStore party = new NPCPartyStore(npc);
            List<DungeonTrainerConfig.PokemonSet> pool = new ArrayList<>(wave.pokemonPool);
            Collections.shuffle(pool);

            int slot = 0;
            for (DungeonTrainerConfig.PokemonSet set : pool) {
                if (slot >= 6) break;
                Pokemon pokemon = createPokemon(set, level);
                if (pokemon == null) continue;
                try { pokemon.heal(); } catch (Exception ignored) {}
                party.set(slot++, pokemon);
            }

            if (slot <= 0) return false;

            party.initialize();
            npc.setParty(party);
            try { npc.setSkill(wave.skill); } catch (Exception ignored) {}
            try { npc.setCustomName(Component.literal(wave.trainerName == null || wave.trainerName.isBlank() ? "Dungeon Trainer" : wave.trainerName)); } catch (Exception ignored) {}
            try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
            try { npc.setHealth(npc.getMaxHealth()); } catch (Exception ignored) {}
            try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Pokemon createPokemon(DungeonTrainerConfig.PokemonSet set, int dungeonLevel) {
        try {
            String species = set.species == null || set.species.isBlank() ? "eevee" : set.species.trim().toLowerCase();
            int pokemonLevel = set.level > 0 ? set.level : dungeonLevel;
            Pokemon pokemon = PokemonProperties.Companion.parse("species=\"cobblemon:" + species + "\" level=" + pokemonLevel).create();

            try { pokemon.setShiny(set.shiny); } catch (Exception ignored) {}
            applyAbility(pokemon, set.ability);
            applyNature(pokemon, set.nature);
            applyHeldItem(pokemon, set.heldItem);
            applyMoves(pokemon, set.moves);
            applyIVs(pokemon, set.ivs);
            applyEVs(pokemon, set.evs);

            return pokemon;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void applyAbility(Pokemon pokemon, String ability) {
        try {
            if (ability == null || ability.isBlank()) return;
            pokemon.updateAbility(Abilities.INSTANCE.getOrException(ability.toLowerCase().replaceAll("[^a-z0-9_]", "")).create(false, Priority.NORMAL));
        } catch (Exception ignored) {}
    }

    private static void applyNature(Pokemon pokemon, String nature) {
        try {
            if (nature == null || nature.isBlank()) return;
            pokemon.setNature(Natures.INSTANCE.getNature(net.minecraft.resources.ResourceLocation.parse("cobblemon:" + nature.toLowerCase().replaceAll("[^a-z0-9_]", ""))));
        } catch (Exception ignored) {}
    }

    private static void applyHeldItem(Pokemon pokemon, String heldItemId) {
        try {
            if (heldItemId == null || heldItemId.isBlank()) return;
            ItemStack heldItem = CobblemonHeldItemUtil.createHeldItemStack(heldItemId);
            if (heldItem.isEmpty()) return;
            pokemon.swapHeldItem(heldItem, false, false);
        } catch (Exception ignored) {}
    }

    private static void applyMoves(Pokemon pokemon, List<String> moves) {
        try {
            if (moves == null || moves.isEmpty()) return;
            pokemon.getMoveSet().clear();
            int count = 0;
            for (String move : moves) {
                if (move == null || move.isBlank() || count >= 4) continue;
                try {
                    pokemon.getMoveSet().add(Moves.getByName(move.toLowerCase().replaceAll("[^a-z0-9_]", "")).create());
                    count++;
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static void applyIVs(Pokemon pokemon, DungeonTrainerConfig.IVs set) {
        if (set == null) return;
        try {
            var ivs = pokemon.getIvs();
            ivs.set(Stats.HP, set.hp); ivs.set(Stats.ATTACK, set.atk); ivs.set(Stats.DEFENCE, set.def);
            ivs.set(Stats.SPECIAL_ATTACK, set.spa); ivs.set(Stats.SPECIAL_DEFENCE, set.spd); ivs.set(Stats.SPEED, set.spe);
        } catch (Exception ignored) {}
    }

    private static void applyEVs(Pokemon pokemon, DungeonTrainerConfig.EVs set) {
        if (set == null) return;
        try {
            var evs = pokemon.getEvs();
            evs.set(Stats.HP, set.hp); evs.set(Stats.ATTACK, set.atk); evs.set(Stats.DEFENCE, set.def);
            evs.set(Stats.SPECIAL_ATTACK, set.spa); evs.set(Stats.SPECIAL_DEFENCE, set.spd); evs.set(Stats.SPEED, set.spe);
        } catch (Exception ignored) {}
    }
}
