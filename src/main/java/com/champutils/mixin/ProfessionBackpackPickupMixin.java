package com.champutils.mixin;

import com.champutils.profession.ProfessionBackpackManager;
import com.champutils.profile.IronmanItemOwnership;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ProfessionBackpackPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void champutils$captureProfessionBackpackPickup(Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        ItemEntity entity = (ItemEntity) (Object) this;
        if (!IronmanItemOwnership.canPickup(serverPlayer, entity)) {
            ci.cancel();
            return;
        }
        ItemStack stack = entity.getItem();
        int captured = ProfessionBackpackManager.capturePickup(serverPlayer, stack);
        if (captured > 0) {
            entity.discard();
            ci.cancel();
        }
    }
}
