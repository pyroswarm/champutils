package com.champutils.mixin;

import com.champutils.profile.IronmanItemOwnership;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class IronmanContainerMenuMixin {
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void champutils$blockForeignIronmanContainerMoves(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (slotId >= 0 && slotId < menu.slots.size()) {
            ItemStack slotStack = menu.slots.get(slotId).getItem();
            if (!IronmanItemOwnership.canMoveStackIntoRestrictedInventory(serverPlayer, slotStack)) {
                ci.cancel();
                menu.broadcastChanges();
                return;
            }
        }

        ItemStack carried = getCarried();
        if (!carried.isEmpty() && !IronmanItemOwnership.canMoveStackIntoRestrictedInventory(serverPlayer, carried)) {
            ci.cancel();
            menu.broadcastChanges();
            return;
        }

        if (slotId >= 0 && slotId < menu.slots.size()) {
            ItemStack slotStack = menu.slots.get(slotId).getItem();
            IronmanItemOwnership.stampContainerDeposit(serverPlayer, slotStack);
        }
        IronmanItemOwnership.stampContainerDeposit(serverPlayer, carried);
    }
}
