package com.champutils.mixin;

import com.champutils.profession.VanillaArmorRestrictionManager;
import com.champutils.crafting.BottleCapCraftingGuard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
public abstract class CraftingArmorBlockerMixin {
    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void champutils$blockVanillaArmorCrafting(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (VanillaArmorRestrictionManager.blockCraftingIfRestricted(serverPlayer, stack)
                    || BottleCapCraftingGuard.blockIfBottleCap(serverPlayer, stack)) {
                ci.cancel();
            }
        }
    }
}
