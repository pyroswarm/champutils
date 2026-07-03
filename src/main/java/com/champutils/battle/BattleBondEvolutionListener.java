package com.champutils.battle;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.pokemon.evolution.EvolutionCompleteEvent;
import com.cobblemon.mod.common.api.pokemon.feature.StringSpeciesFeature;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Grants and repairs Battle Bond through safe Cobblemon battle/evolution events. */
public final class BattleBondEvolutionListener {
    private static final String BATTLE_BOND_FEATURE = "battle_bond";
    private static final String BATTLE_BOND_MARKER = "champutils_battle_bond_greninja";

    private BattleBondEvolutionListener() {}

    public static void register() {
        BattleBondEvolutionConfig.load();
        CobblemonEvents.EVOLUTION_COMPLETE.subscribe(BattleBondEvolutionListener::handleEvolutionComplete);
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(BattleBondEvolutionListener::handleBattleStarted);
        CobblemonEvents.BATTLE_FAINTED.subscribe(BattleBondEvolutionListener::handleBattleFainted);
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

            applyBattleBondBaseForm(result);

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

    private static void handleBattleStarted(BattleStartedEvent.Post event) {
        try {
            if (!BattleBondEvolutionConfig.enabled || event == null || event.getBattle() == null) return;
            for (BattleActor actor : event.getBattle().getActors()) {
                if (actor == null || actor.getPokemonList() == null) continue;
                for (BattlePokemon battlePokemon : actor.getPokemonList()) {
                    repairBattleBondBaseIfNeeded(safeOriginalPokemon(battlePokemon));
                    repairBattleBondBaseIfNeeded(safeEffectedPokemon(battlePokemon));
                }
            }
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] BattleBond battle-start repair failed safely: " + throwable.getMessage());
        }
    }

    private static void repairBattleBondBaseIfNeeded(Pokemon pokemon) {
        if (isBattleBondGreninja(pokemon) && !isAshBattleBond(pokemon)) {
            applyBattleBondBaseForm(pokemon);
        }
    }

    private static void handleBattleFainted(BattleFaintedEvent event) {
        try {
            if (!BattleBondEvolutionConfig.enabled || event == null || event.getBattle() == null) return;
            BattlePokemon fainted = event.getKilled();
            if (fainted == null) return;

            BattleSide side1 = event.getBattle().getSide1();
            BattleSide side2 = event.getBattle().getSide2();
            BattleSide faintedSide = containsBattlePokemon(side1, fainted) ? side1 : containsBattlePokemon(side2, fainted) ? side2 : null;
            BattleSide triggerSide = faintedSide == side1 ? side2 : faintedSide == side2 ? side1 : null;
            if (triggerSide == null) return;

            for (ActiveBattlePokemon active : triggerSide.getActivePokemon()) {
                if (active == null || active.isGone()) continue;
                BattlePokemon battlePokemon = active.getBattlePokemon();
                if (battlePokemon == null) continue;
                if (triggerBattleBondAfterKo(battlePokemon)) {
                    try { battlePokemon.sendUpdate(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] BattleBond faint listener failed safely: " + throwable.getMessage());
        }
    }

    private static boolean containsBattlePokemon(BattleSide side, BattlePokemon pokemon) {
        if (side == null || pokemon == null) return false;
        try {
            for (BattleActor actor : side.getActors()) {
                if (actor == null) continue;
                if (actor == pokemon.getActor()) return true;
                if (actor.getPokemonList() != null && actor.getPokemonList().contains(pokemon)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean triggerBattleBondAfterKo(BattlePokemon battlePokemon) {
        Pokemon original = safeOriginalPokemon(battlePokemon);
        Pokemon effected = safeEffectedPokemon(battlePokemon);
        Pokemon target = effected != null ? effected : original;
        if (!isBattleBondGreninja(original) && !isBattleBondGreninja(effected)) {
            return false;
        }
        if (target == null || isAshBattleBond(target)) {
            return false;
        }

        // Persistent storage should remain the legal Battle Bond base form.
        applyBattleBondBaseForm(original);
        addPostBattleBaseFormRepair(battlePokemon);

        // The battle copy becomes Ash-Greninja until battle cleanup reverts it.
        applyBattleBondBattleForm(target);
        return true;
    }

    private static void addPostBattleBaseFormRepair(BattlePokemon battlePokemon) {
        try {
            if (battlePokemon == null || battlePokemon.getPostBattlePokemonOperations() == null) return;
            battlePokemon.getPostBattlePokemonOperations().add(updated -> {
                applyBattleBondBaseForm(safeOriginalPokemon(updated));
                return kotlin.Unit.INSTANCE;
            });
        } catch (Throwable ignored) {}
    }

    private static Pokemon safeOriginalPokemon(BattlePokemon battlePokemon) {
        try { return battlePokemon == null ? null : battlePokemon.getOriginalPokemon(); } catch (Throwable ignored) { return null; }
    }

    private static Pokemon safeEffectedPokemon(BattlePokemon battlePokemon) {
        try { return battlePokemon == null ? null : battlePokemon.getEffectedPokemon(); } catch (Throwable ignored) { return null; }
    }

    /**
     * Cobblemon 1.7.3 defines Battle Bond Greninja as battle_bond=bond and
     * Ash-Greninja as battle_bond=ash. Setting only the ability lets the form
     * recalculate back to normal Torrent Greninja on reload.
     */
    private static void applyBattleBondBaseForm(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            setBattleBondFeature(pokemon, "bond");
            markBattleBond(pokemon);
            pokemon.updateForm();
            setBattleBondAbility(pokemon);
            pokemon.updateAspects();
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Could not apply Battle Bond base form safely: " + throwable.getClass().getSimpleName());
        }
    }

    private static void applyBattleBondBattleForm(Pokemon pokemon) {
        if (pokemon == null) return;
        try {
            setBattleBondFeature(pokemon, "ash");
            pokemon.updateForm();
            setBattleBondAbility(pokemon);
            pokemon.updateAspects();
        } catch (Throwable throwable) {
            System.err.println("[ChampUtils] Could not apply Battle Bond battle form safely: " + throwable.getClass().getSimpleName());
        }
    }

    private static void setBattleBondFeature(Pokemon pokemon, String value) {
        StringSpeciesFeature feature = pokemon.getFeature(BATTLE_BOND_FEATURE);
        if (feature == null) {
            pokemon.getFeatures().add(new StringSpeciesFeature(BATTLE_BOND_FEATURE, value));
        } else {
            feature.setValue(value);
        }
    }

    private static void setBattleBondAbility(Pokemon pokemon) {
        AbilityTemplate battleBond = Abilities.get("battlebond");
        if (battleBond == null) battleBond = Abilities.get("battle_bond");
        if (battleBond == null) return;
        Priority priority = pokemon.getAbility() == null ? Priority.LOWEST : pokemon.getAbility().getPriority();
        pokemon.setAbility$common(battleBond.create(false, priority));
    }

    private static boolean isBattleBondGreninja(Pokemon pokemon) {
        if (pokemon == null || !"greninja".equals(speciesName(pokemon))) return false;
        String ability = abilityName(pokemon);
        String feature = battleBondFeatureValue(pokemon);
        return "battlebond".equals(ability)
                || "battle_bond".equals(ability)
                || "bond".equals(feature)
                || "ash".equals(feature)
                || hasBattleBondMarker(pokemon);
    }

    private static boolean isAshBattleBond(Pokemon pokemon) {
        return "ash".equals(battleBondFeatureValue(pokemon));
    }

    private static void markBattleBond(Pokemon pokemon) {
        try { pokemon.getPersistentData().putBoolean(BATTLE_BOND_MARKER, true); } catch (Throwable ignored) {}
    }

    private static boolean hasBattleBondMarker(Pokemon pokemon) {
        try {
            CompoundTag tag = pokemon.getPersistentData();
            return tag != null && tag.getBoolean(BATTLE_BOND_MARKER);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String battleBondFeatureValue(Pokemon pokemon) {
        try {
            StringSpeciesFeature feature = pokemon.getFeature(BATTLE_BOND_FEATURE);
            return feature == null || feature.getValue() == null ? "" : feature.getValue().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
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
