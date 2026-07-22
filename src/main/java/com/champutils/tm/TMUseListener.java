package com.champutils.tm;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
            if (hand != InteractionHand.MAIN_HAND) {
                serverPlayer.sendSystemMessage(Component.literal("Move the TM to your main hand to use it.").withStyle(ChatFormatting.RED));
                return InteractionResultHolder.fail(stack);
            }
            TMTeachMenu.open(serverPlayer);
            return InteractionResultHolder.success(stack);
        });
    }
}
