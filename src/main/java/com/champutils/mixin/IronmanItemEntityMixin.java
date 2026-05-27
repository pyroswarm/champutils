package com.champutils.mixin;

import com.champutils.profile.IronmanItemOwnership;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class IronmanItemEntityMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void champutils$blockForeignIronmanPickup(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (!IronmanItemOwnership.canPickup(serverPlayer, (ItemEntity) (Object) this)) {
                ci.cancel();
            }
        }
    }
}
