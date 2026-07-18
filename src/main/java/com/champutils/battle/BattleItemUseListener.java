package com.champutils.battle;

import com.champutils.adventurer.AdventurerGuildManager;

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
            cobblemonTag("battle_items");

    private static final TagKey<Item> COBBLEMON_POTIONS =
            cobblemonTag("potions");

    private static final TagKey<Item> COBBLEMON_RESTORES =
            cobblemonTag("restores");

    private static final TagKey<Item> COBBLEMON_REVIVES =
            cobblemonTag("revives");

    private static final TagKey<Item> COBBLEMON_REMEDIES =
            cobblemonTag("remedies");

    private static final TagKey<Item> COBBLEMON_ETHERS =
            cobblemonTag("ethers");

    private static TagKey<Item> cobblemonTag(String name) {
        return TagKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(
                        "cobblemon",
                        name
                )
        );
    }

    private static boolean isBattleTowerHealingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(COBBLEMON_POTIONS)
                || stack.is(COBBLEMON_RESTORES)
                || stack.is(COBBLEMON_REVIVES)
                || stack.is(COBBLEMON_REMEDIES)
                || stack.is(COBBLEMON_ETHERS);
    }

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


                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );


                    if (
                            AdventurerGuildManager.isAttemptingBattleTower(serverPlayer)
                                    &&
                            isBattleTowerHealingItem(stack)
                    ) {
                        serverPlayer.sendSystemMessage(
                                Component.literal(
                                        "§cYou cannot heal Pokémon during a Battle Tower attempt. You will be healed at checkpoints."
                                )
                        );
                        return InteractionResultHolder.fail(stack);
                    }


                    if (
                            !BattleStateManager.isInBattle(
                                    serverPlayer
                            )
                    ) {

                        return InteractionResultHolder.pass(
                                stack
                        );
                    }


                    if (
                            !BattleItemLockManager.blocked(
                                    serverPlayer
                            )
                    ) {

                        return InteractionResultHolder.pass(
                                stack
                        );
                    }


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
