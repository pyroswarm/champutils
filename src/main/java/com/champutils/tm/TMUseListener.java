package com.champutils.tm;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

public final class TMUseListener {
    private TMUseListener() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
            if (TMManager.getMoveId(stack) == null) return InteractionResultHolder.pass(stack);
            if (world.isClientSide()) return InteractionResultHolder.success(stack);

            TMManager.TeachResult result = TMManager.quickUse(serverPlayer, stack);
            if (result.message() != null) serverPlayer.sendSystemMessage(Component.literal(result.message()));
            return result.success() ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
        });
    }
}
