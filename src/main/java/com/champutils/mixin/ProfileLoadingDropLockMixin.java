package com.champutils.mixin;

import com.champutils.profile.ProfileLobbyLockManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents item drops during profile loading/lobby lock so no stack can escape a half-loaded profile. */
@Mixin(Player.class)
public abstract class ProfileLoadingDropLockMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void champutils$blockDropsWhileProfileLocked(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        Player player = (Player) (Object) this;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (ProfileLobbyLockManager.isLocked(serverPlayer) && !ProfileLobbyLockManager.hasBypass(serverPlayer)) {
            ProfileLobbyLockManager.deny(serverPlayer);
            cir.setReturnValue(null);
        }
    }
}
