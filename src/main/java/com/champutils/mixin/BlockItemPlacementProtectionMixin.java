package com.champutils.mixin;

import com.champutils.claims.LandClaimProtectionListener;
import com.champutils.protection.SpawnRealmProtectionListener;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public abstract class BlockItemPlacementProtectionMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void champutils$blockProtectedPlacement(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context == null || !(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) return;
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        BlockPos placedPos = placeContext.getClickedPos();
        if (!SpawnRealmProtectionListener.canPlaceBlock(player, level, placedPos) || !LandClaimProtectionListener.canPlaceBlock(player, level, placedPos)) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
