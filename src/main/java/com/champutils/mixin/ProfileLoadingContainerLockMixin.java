package com.champutils.mixin;

import com.champutils.profile.ProfileLobbyLockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks inventory/container mutation while a player is in profile lobby or survival profile hydration quarantine. */
@Mixin(AbstractContainerMenu.class)
public abstract class ProfileLoadingContainerLockMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void champutils$blockContainerClicksWhileProfileLocked(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (ProfileLobbyLockManager.isLocked(serverPlayer) && !ProfileLobbyLockManager.hasBypass(serverPlayer)) {
            ProfileLobbyLockManager.deny(serverPlayer);
            ci.cancel();
            ((AbstractContainerMenu) (Object) this).broadcastChanges();
        }
    }
}
