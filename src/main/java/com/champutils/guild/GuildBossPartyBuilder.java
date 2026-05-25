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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GuildBossPartyBuilder {
    private GuildBossPartyBuilder() {}

    private static final int MAX_MOVES = 4;

    /**
     * These moves make NPC bosses feel terrible because the battle AI can repeatedly choose them.
     * Bosses should beat players by using strong curated attacks/setup, not by wasting turns.
     */
    private static final Set<String> DISALLOWED_BOSS_MOVES = Set.of(
            "protect", "detect", "endure", "wideguard", "quickguard", "kingsshield",
            "spikyshield", "banefulbunker", "obstruct", "silktrap", "burningbulwark"
    );

    private static final List<String> SAFE_FALLBACK_MOVES = List.of(
            "earthquake", "thunderbolt", "flamethrower", "icebeam", "shadowball", "closecombat", "dragonpulse", "psychic"
    );

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

            applyBossMoves(pokemon, set.moves);
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
        String s = species.trim().toLowerCase(Locale.ROOT);
        if (!s.contains(":")) s = "cobblemon:" + s;
        return s;
    }

    private static String cleanKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9_]", "");
    }

    private static String cleanMoveKey(String value) {
        if (value == null) return "";
        // Cobblemon/Showdown move IDs are compact IDs: U-turn -> uturn, Ice Beam -> icebeam, King's Shield -> kingsshield.
        return value.toLowerCase(Locale.ROOT).replace("cobblemon:", "").replaceAll("[^a-z0-9]", "");
    }
}
