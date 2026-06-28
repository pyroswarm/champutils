package com.champutils.mixin;

import com.cobblemon.mod.common.pokemon.Pokemon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server config expects Pokémon fullness to be disabled. Cobblemon 1.7.x stores
 * fullness in the Kotlin currentFullness property, whose JVM setter is
 * setCurrentFullness(int). Blocking the old setFullness name did nothing.
 */
@Mixin(Pokemon.class)
public abstract class PokemonFullnessDisabledMixin {
    @Inject(method = "setCurrentFullness", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void champutils$disableCurrentFullnessChanges(int fullness, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "feedPokemon", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void champutils$disableFeedFullness(int feedCount, boolean playSound, CallbackInfo ci) {
        ci.cancel();
    }
}
