package com.champutils.mixin;

import com.champutils.xplock.XpLockManager;
import com.cobblemon.mod.common.api.pokemon.experience.StandardExperienceCalculator;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StandardExperienceCalculator.class)
public abstract class StandardExperienceCalculatorMixin {

    @ModifyVariable(method = "calculate", at = @At("STORE"), name = "term4", remap = false)
    private double champutils$blockExperienceForLockedPokemon(double term4, BattlePokemon battlePokemon) {
        return XpLockManager.isLocked(battlePokemon.getOriginalPokemon()) ? 0.0D : term4;
    }
}
