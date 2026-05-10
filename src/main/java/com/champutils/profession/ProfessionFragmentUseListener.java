package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

public final class ProfessionFragmentUseListener {

    private ProfessionFragmentUseListener() {
    }

    public static void register() {
        UseItemCallback.EVENT.register(
                (player, world, hand) -> {
                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResultHolder.pass(stack);
                    }

                    String fragmentKey =
                            ProfessionFragmentManager.getFragmentKey(
                                    stack
                            );

                    if (fragmentKey == null) {
                        return InteractionResultHolder.pass(stack);
                    }

                    if (world.isClientSide()) {
                        return InteractionResultHolder.success(stack);
                    }

                    int deposited =
                            ProfessionFragmentManager.depositFragmentStack(
                                    serverPlayer,
                                    stack
                            );

                    if (deposited <= 0) {
                        return InteractionResultHolder.fail(stack);
                    }

                    int stored =
                            ProfessionFragmentManager.countFragments(
                                    serverPlayer,
                                    fragmentKey
                            );

                    serverPlayer.sendSystemMessage(
                            Component.literal(
                                    "§aDeposited §6" +
                                            deposited +
                                            "x " +
                                            ProfessionFragmentManager.formatWords(fragmentKey) +
                                            " Fragment§a. Stored: §e" +
                                            stored
                            )
                    );

                    return InteractionResultHolder.success(stack);
                }
        );
    }
}
