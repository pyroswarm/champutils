package com.champutils.mixin;

import com.champutils.claims.LandClaimProtectionListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents water/lava from flowing across land-claim borders. */
@Mixin(FlowingFluid.class)
public abstract class ClaimFluidFlowMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void champutils$blockClaimBorderFluid(LevelAccessor level, BlockPos toPos, BlockState blockState, Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (!(level instanceof ServerLevel serverLevel) || direction == null) return;
        BlockPos fromPos = toPos.relative(direction.getOpposite());
        if (LandClaimProtectionListener.shouldBlockFluidFlow(serverLevel, fromPos, toPos)) {
            ci.cancel();
        }
    }
}
