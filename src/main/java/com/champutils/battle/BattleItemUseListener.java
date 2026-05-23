package com.champutils.battle;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class BattleItemUseListener {

    private static final TagKey<Item> COBBLEMON_BATTLE_ITEMS =
            TagKey.create(
                    Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(
                            "cobblemon",
                            "battle_items"
                    )
            );

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
                                    ||
                            !stack.is(COBBLEMON_BATTLE_ITEMS)
                    ) {

                        return InteractionResultHolder.pass(
                                stack
                        );
                    }


                    serverPlayer.sendSystemMessage(
                            Component.literal(
                                    "§cBattle items are disabled in this battle format."
                            )
                    );


                    return InteractionResultHolder.fail(
                            stack
                    );

                });
    }
}