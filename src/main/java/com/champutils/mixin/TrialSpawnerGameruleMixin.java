package com.champutils.mixin;

import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows trial spawners to operate while doMobSpawning is false.
 * Peaceful difficulty remains respected, matching ordinary spawner behavior.
 */
@Mixin(TrialSpawner.class)
public final class TrialSpawnerGameruleMixin {
    @Inject(method = "canSpawnInLevel", at = @At("HEAD"), cancellable = true)
    private void champutils$allowTrialSpawnerWithMobSpawningDisabled(
            Level level,
            CallbackInfoReturnable<Boolean> cir
    ) {
        cir.setReturnValue(level.getDifficulty() != Difficulty.PEACEFUL);
    }
}
