package com.champutils.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.battles.ActiveBattlePokemon;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.battles.DefaultActionResponse;
import com.cobblemon.mod.common.battles.ShowdownActionResponse;
import com.cobblemon.mod.common.battles.ShowdownMoveset;
import com.cobblemon.mod.common.battles.SwitchActionResponse;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.battles.ai.RandomBattleAI;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;

/**
 * Failure-proof PvE AI wrapper:
 * 1) RCT/Radical-Cobblemon-Trainers BattleAI if the installed RCT jar exposes one.
 * 2) ChampUtils Radical-Red-style scoring AI.
 * 3) Cobblemon StrongBattleAI.
 * 4) Cobblemon RandomBattleAI.
 */
public final class ChampSmartBattleAI implements BattleAI {
    private final BattleAI primary;
    private final BattleAI strongFallback;
    private final BattleAI randomFallback;
    private final boolean fallbackToStrong;
    private final boolean fallbackToRandom;

    public ChampSmartBattleAI(int skill, boolean competitiveLayer, boolean fallbackToStrong, boolean fallbackToRandom) {
        int safeSkill = Math.max(0, Math.min(5, skill));
        this.primary = RctBattleAIFactory.create(safeSkill)
                .orElseGet(() -> new ChampSmarterBattleAI(safeSkill, competitiveLayer, false));
        this.strongFallback = new StrongBattleAI(safeSkill);
        this.randomFallback = new RandomBattleAI();
        this.fallbackToStrong = fallbackToStrong;
        this.fallbackToRandom = fallbackToRandom;
    }

    @Override
    public ShowdownActionResponse choose(ActiveBattlePokemon active, PokemonBattle battle, BattleSide aiSide, ShowdownMoveset moveset, boolean forceSwitch) {
        ShowdownActionResponse response = chooseSafely(primary, active, battle, aiSide, moveset, forceSwitch, "primary");
        if (isUsable(response, forceSwitch)) return response;

        if (fallbackToStrong) {
            response = chooseSafely(strongFallback, active, battle, aiSide, moveset, forceSwitch, "strong");
            if (isUsable(response, forceSwitch)) return response;
        }

        if (fallbackToRandom) {
            response = chooseSafely(randomFallback, active, battle, aiSide, moveset, forceSwitch, "random");
            if (isUsable(response, forceSwitch)) return response;
        }

        if (forceSwitch) {
            ShowdownActionResponse emergencySwitch = emergencySwitch(active);
            if (emergencySwitch != null) return emergencySwitch;
        }

        return new DefaultActionResponse();
    }

    private ShowdownActionResponse chooseSafely(BattleAI ai, ActiveBattlePokemon active, PokemonBattle battle, BattleSide aiSide, ShowdownMoveset moveset, boolean forceSwitch, String label) {
        if (ai == null) return null;
        try {
            return ai.choose(active, battle, aiSide, moveset, forceSwitch);
        } catch (Throwable t) {
            BattleAIDifficultyManager.debug("AI " + label + " failed; falling back. error=" + t.getClass().getSimpleName());
            return null;
        }
    }

    private boolean isUsable(ShowdownActionResponse response, boolean forceSwitch) {
        if (response == null) return false;
        if (!forceSwitch) return true;
        return response instanceof SwitchActionResponse;
    }

    private ShowdownActionResponse emergencySwitch(ActiveBattlePokemon active) {
        try {
            for (BattlePokemon pokemon : active.getActor().getPokemonList()) {
                if (pokemon != null && pokemon.canBeSentOut()) {
                    pokemon.setWillBeSwitchedIn(true);
                    BattleAIDifficultyManager.debug("AI emergency switch selected after all normal force-switch handlers failed.");
                    return new SwitchActionResponse(pokemon.getUuid());
                }
            }
        } catch (Throwable t) {
            BattleAIDifficultyManager.debug("AI emergency switch failed: " + t.getClass().getSimpleName());
        }
        return null;
    }
}
