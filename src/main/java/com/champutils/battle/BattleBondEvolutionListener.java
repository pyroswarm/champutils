package com.champutils.battle;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Grants Battle Bond through a safe Cobblemon evolution-complete event. */
public final class BattleBondEvolutionListener {
    private BattleBondEvolutionListener() {}

    public static void register() {
        BattleBondEvolutionConfig.load();
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(BattleBondEvolutionListener::handleEvolutionComplete);
    }

    private static void handleEvolutionComplete(EvolutionCompleteEvent event) {
        try {
            if (!BattleBondEvolutionConfig.enabled) return;
            Pokemon source = event.getSourcePokemon();
            Pokemon result = event.getPokemon();
            if (source == null || result == null) return;

            String sourceSpecies = speciesName(source);
            String resultSpecies = speciesName(result);
            if (!"frogadier".equals(sourceSpecies) || !"greninja".equals(resultSpecies)) return;

            String sourceAbility = abilityName(source);
            String resultAbility = abilityName(result);
            if (BattleBondEvolutionConfig.protectedAbilities.contains(sourceAbility) || BattleBondEvolutionConfig.protectedAbilities.contains(resultAbility)) {
                return;
            }

            double chance = Math.max(0.0D, BattleBondEvolutionConfig.chancePercent) / 100.0D;
            if (chance <= 0.0D || ThreadLocalRandom.current().nextDouble() >= chance) return;

            AbilityTemplate battleBond = Abilities.get("battlebond");
            if (battleBond == null) battleBond = Abilities.get("battle_bond");
            if (battleBond == null) return;

            result.setAbility$common(battleBond.create(false, result.getAbility() == null ? com.cobblemon.mod.common.api.Priority.LOWEST : result.getAbility().getPriority()));

            ServerPlayer owner = result.getOwnerPlayer();
            if (owner != null) {
                owner.displayClientMessage(
                        Component.literal("§3Battle Bond awakened! §fYour Greninja gained Battle Bond."),
                        false
                );
            }
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] BattleBond evolution listener failed safely: " + throwable.getMessage());
        }
    }

    private static String speciesName(Pokemon pokemon) {
        try {
            return pokemon.getSpecies().getName().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String abilityName(Pokemon pokemon) {
        try {
            return pokemon.getAbility().getName().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }
}
