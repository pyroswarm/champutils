package com.champutils.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents endermen from picking up blocks while still allowing normal mobGriefing for other mobs. */
@Mixin(targets = "net.minecraft.world.entity.monster.EnderMan$EndermanTakeBlockGoal")
public abstract class EndermanNoBlockTakeMixin {
    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void champutils$neverTakeBlocks(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
