package com.champutils.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server config expects Pokémon fullness to be disabled. Feeding should not mutate
 * the stored fullness value, which also prevents berries/food from filling Pokémon.
 */
@Mixin(Pokemon.class)
public abstract class PokemonFullnessDisabledMixin {
    @Inject(method = "setFullness", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void champutils$disableFullnessChanges(int fullness, CallbackInfo ci) {
        ci.cancel();
    }
}
