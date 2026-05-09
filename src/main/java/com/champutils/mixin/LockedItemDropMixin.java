package com.champutils.mixin;

import com.champutils.profession.ProfessionToolMetadata;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class LockedItemDropMixin {

    @Inject(
            method = "drop(Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void champutils$preventLockedItemDrop(
            boolean dropEntireStack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Player player = (Player)(Object)this;

        ItemStack stack = player.getInventory().getSelected();

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isLocked(stack)
        ) {
            return;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(
                    Component.literal(
                            "§cThis item is locked. Use §f/itemlock §cto unlock it first."
                    )
            );

            serverPlayer.playNotifySound(
                    SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS,
                    1.0F,
                    0.7F
            );
        }

        cir.setReturnValue(false);
    }
}