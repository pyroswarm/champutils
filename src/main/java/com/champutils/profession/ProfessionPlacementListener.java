package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class ProfessionPlacementListener {

    public static void register() {

        UseBlockCallback.EVENT.register(
                (
                        player,
                        world,
                        hand,
                        hitResult
                ) -> {

                    if (world.isClientSide()) {
                        return InteractionResult.PASS;
                    }

                    ItemStack stack =
                            player.getItemInHand(hand);

                    if (
                            !(stack.getItem()
                                    instanceof BlockItem)
                    ) {
                        return InteractionResult.PASS;
                    }

                    BlockPos placedPos =
                            hitResult.getBlockPos()
                                    .relative(
                                            hitResult.getDirection()
                                    );

                    ProfessionBlockTracker.markPlaced(
                            placedPos
                    );

                    return InteractionResult.PASS;
                }
        );
    }
}