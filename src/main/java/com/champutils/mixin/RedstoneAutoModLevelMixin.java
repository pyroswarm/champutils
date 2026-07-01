package com.champutils.mixin;

import com.champutils.moderation.RedstoneAutoModManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class RedstoneAutoModLevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void champutils$redstoneAutoMod(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.isClientSide) return;
        BlockState oldState = level.getBlockState(pos);
        if (RedstoneAutoModManager.shouldCancelRedstoneChange(level, pos, oldState, state)) {
            cir.setReturnValue(false);
            return;
        }
        RedstoneAutoModManager.recordBlockStateChange(level, pos, oldState, state);
    }
}
