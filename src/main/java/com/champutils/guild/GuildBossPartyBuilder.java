package com.champutils.guild;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class GuildBossPartyBuilder {
    private GuildBossPartyBuilder() {}

    public static boolean applyBossPokemon(NPCEntity npc, BossConfig.BossPokemon set, BossConfig.BossSettings settings) {
        return applyBossTeam(npc, set == null ? null : List.of(set), settings);
    }

    public static boolean applyBossTeam(NPCEntity npc, List<BossConfig.BossPokemon> team, BossConfig.BossSettings settings) {
        if (npc == null || team == null || team.isEmpty() || settings == null) return false;
        try {
            int level = Math.max(1, Math.min(100, settings.level));
            npc.initialize(level);
            NPCPartyStore party = new NPCPartyStore(npc);
            int slot = 0;
            for (BossConfig.BossPokemon set : team) {
                if (set == null || slot >= 6) continue;
                Pokemon pokemon = createPokemon(set, level);
                if (pokemon == null) continue;
                try { pokemon.heal(); } catch (Exception ignored) {}
                party.set(slot++, pokemon);
            }
            if (slot <= 0) return false;
            party.initialize();
            npc.setParty(party);
            npc.setSkill(5);
            npc.setHealth(npc.getMaxHealth());
            npc.setPersistenceRequired();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static Pokemon createPokemon(BossConfig.BossPokemon set, int level) {
        try {
            Pokemon pokemon = PokemonProperties.Companion
                    .parse("species=\"" + normalizeSpecies(set.species) + "\" level=" + level)
                    .create();

            try {
                if (set.ability != null && !set.ability.isBlank()) {
                    pokemon.updateAbility(Abilities.INSTANCE.getOrException(cleanKey(set.ability)).create(false, Priority.NORMAL));
                }
            } catch (Exception ignored) {}

            try {
                if (set.nature != null && !set.nature.isBlank()) {
                    pokemon.setNature(Natures.INSTANCE.getNature(ResourceLocation.parse("cobblemon:" + cleanKey(set.nature))));
                }
            } catch (Exception ignored) {}

            try {
                pokemon.getMoveSet().clear();
                if (set.moves != null) {
                    int learnedMoves = 0;
                    for (String move : set.moves) {
                        if (move == null || move.isBlank()) continue;
                        if (learnedMoves >= 4) break;
                        try {
                            pokemon.getMoveSet().add(Moves.getByName(cleanMoveKey(move)).create());
                            learnedMoves++;
                        } catch (Exception ignored) {}
                    }
                }
                if (pokemon.getMoveSet().getMoves() == null || pokemon.getMoveSet().getMoves().isEmpty()) {
                    for (String move : new String[] {"tackle", "protect"}) {
                        try { pokemon.getMoveSet().add(Moves.getByName(move).create()); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            applyPerfectIvs(pokemon);
            applyBossEvs(pokemon, set.evs);
            applyHeldItem(pokemon, set.heldItem);
            pokemon.heal();
            return pokemon;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean applyHeldItem(Pokemon pokemon, String heldItemId) {
        if (pokemon == null || heldItemId == null || heldItemId.isBlank()) return false;
        try {
            ItemStack heldItem = CobblemonHeldItemUtil.createHeldItemStack(heldItemId);
            if (heldItem.isEmpty()) return false;
            pokemon.swapHeldItem(heldItem, false, false);
            return !pokemon.heldItem().isEmpty();
        } catch (Exception ignored) { return false; }
    }

    private static void applyPerfectIvs(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            var ivs = pokemon.getIvs();
            ivs.set(Stats.HP, 31);
            ivs.set(Stats.ATTACK, 31);
            ivs.set(Stats.DEFENCE, 31);
            ivs.set(Stats.SPECIAL_ATTACK, 31);
            ivs.set(Stats.SPECIAL_DEFENCE, 31);
            ivs.set(Stats.SPEED, 31);
        } catch (Exception ignored) {}
    }

    private static void applyBossEvs(Pokemon pokemon, BossConfig.EvSpread spread) {
        if (pokemon == null) return;
        BossConfig.EvSpread evSpread = spread == null ? new BossConfig.EvSpread(252, 252, 252, 252, 252, 252) : spread;
        try {
            evSpread.normalize();
            var evs = pokemon.getEvs();
            evs.set(Stats.HP, evSpread.hp);
            evs.set(Stats.ATTACK, evSpread.attack);
            evs.set(Stats.DEFENCE, evSpread.defence);
            evs.set(Stats.SPECIAL_ATTACK, evSpread.specialAttack);
            evs.set(Stats.SPECIAL_DEFENCE, evSpread.specialDefence);
            evs.set(Stats.SPEED, evSpread.speed);
        } catch (Exception ignored) {}
    }

    private static String normalizeSpecies(String species) {
        if (species == null || species.isBlank()) return "cobblemon:mewtwo";
        String s = species.trim().toLowerCase();
        if (!s.contains(":")) s = "cobblemon:" + s;
        return s;
    }

    private static String cleanKey(String value) {
        return value == null ? "" : value.toLowerCase().replace("cobblemon:", "").replaceAll("[^a-z0-9_]", "");
    }

    private static String cleanMoveKey(String value) {
        if (value == null) return "";
        return value.toLowerCase().replace("cobblemon:", "").replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }
}
