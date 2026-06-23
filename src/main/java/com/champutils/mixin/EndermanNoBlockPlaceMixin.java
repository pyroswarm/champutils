package com.champutils.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents endermen from placing carried blocks while still allowing normal mobGriefing for other mobs. */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanLeaveBlockGoal")
public abstract class EndermanNoBlockPlaceMixin {
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void champutils$neverPlaceBlocks(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
