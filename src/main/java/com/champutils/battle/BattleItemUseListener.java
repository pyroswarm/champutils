package com.champutils.battle;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class BattleItemUseListener {

    public static void register() {

        UseItemCallback.EVENT.register(
                (player, world, hand) -> {

                    if (
                            !(player instanceof ServerPlayer serverPlayer)
                    ) {

                        return InteractionResultHolder.pass(
                                player.getItemInHand(hand)
                        );
                    }


                    if (
                            !BattleStateManager.isInBattle(
                                    serverPlayer
                            )
                    ) {

                        return InteractionResultHolder.pass(
                                player.getItemInHand(hand)
                        );
                    }


                    if (
                            !BattleItemLockManager.blocked(
                                    serverPlayer
                            )
                    ) {

                        return InteractionResultHolder.pass(
                                player.getItemInHand(hand)
                        );
                    }


                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );


                    if (
                            stack.isEmpty()
                    ) {

                        return InteractionResultHolder.pass(
                                stack
                        );
                    }


                    serverPlayer.sendSystemMessage(
                            Component.literal(
                                    "§cBattle items are disabled in ranked battles."
                            )
                    );


                    return InteractionResultHolder.fail(
                            stack
                    );

                });
    }
}