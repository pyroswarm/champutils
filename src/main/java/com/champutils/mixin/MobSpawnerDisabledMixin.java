package com.champutils.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fully disables vanilla mob spawner ticking while leaving the block itself intact. */
@Mixin(BaseSpawner.class)
public abstract class MobSpawnerDisabledMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void champutils$disableMobSpawner(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        ci.cancel();
    }
}
