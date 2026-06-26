package com.champutils.mixin;

import com.champutils.profile.ProfileLobbyLockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents item pickups while profile storage is not fully attached. */
@Mixin(ItemEntity.class)
public abstract class ProfileLoadingPickupLockMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void champutils$blockPickupWhileProfileLocked(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (ProfileLobbyLockManager.isLocked(serverPlayer) && !ProfileLobbyLockManager.hasBypass(serverPlayer)) {
            ci.cancel();
        }
    }
}
