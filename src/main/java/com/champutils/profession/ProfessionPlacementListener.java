package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class ProfessionPlacementListener {

    private static final List<PendingPlacement> PENDING =
            new ArrayList<>();

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

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return InteractionResult.PASS;
                    }

                    ItemStack stack =
                            player.getItemInHand(
                                    hand
                            );

                    if (!(stack.getItem() instanceof BlockItem blockItem)) {
                        return InteractionResult.PASS;
                    }

                    if (!(world instanceof ServerLevel level)) {
                        return InteractionResult.PASS;
                    }

                    BlockHitResult blockHit =
                            hitResult;

                    BlockPos clickedPos =
                            blockHit.getBlockPos();

                    BlockPos placePos =
                            level.getBlockState(
                                            clickedPos
                                    )
                                    .canBeReplaced()
                                    ? clickedPos
                                    : clickedPos.relative(
                                    blockHit.getDirection()
                            );

                    PendingPlacement pending =
                            new PendingPlacement();

                    pending.playerId =
                            serverPlayer.getUUID();

                    pending.level =
                            level;

                    pending.pos =
                            placePos;

                    pending.expectedBlock =
                            blockItem.getBlock();

                    pending.ticksLeft =
                            2;

                    PENDING.add(
                            pending
                    );

                    return InteractionResult.PASS;
                }
        );

        ServerTickEvents.END_SERVER_TICK.register(
                server -> {

                    Iterator<PendingPlacement> iterator =
                            PENDING.iterator();

                    while (iterator.hasNext()) {

                        PendingPlacement pending =
                                iterator.next();

                        pending.ticksLeft--;

                        if (pending.ticksLeft > 0) {
                            continue;
                        }

                        Block actualBlock =
                                pending.level
                                        .getBlockState(
                                                pending.pos
                                        )
                                        .getBlock();

                        if (
                                actualBlock ==
                                        pending.expectedBlock
                        ) {
                            markIfProfessionBlock(
                                    pending
                            );
                        }

                        iterator.remove();
                    }
                }
        );
    }

    private static void markIfProfessionBlock(
            PendingPlacement pending
    ) {

        String blockId =
                pending.expectedBlock
                        .builtInRegistryHolder()
                        .key()
                        .location()
                        .toString();

        boolean isMining =
                ProfessionConfig
                        .SETTINGS
                        .miningXp
                        .containsKey(
                                blockId
                        ) ||
                        isAscendedMiningTrackerBlock(
                                blockId
                        );

        boolean isForestry =
                ProfessionConfig
                        .SETTINGS
                        .forestryXp
                        .containsKey(
                                blockId
                        );

        if (
                !isMining &&
                        !isForestry
        ) {
            return;
        }

        ProfessionBlockTracker.markPlaced(
                pending.level,
                pending.pos,
                pending.playerId
        );
    }

    private static boolean isAscendedMiningTrackerBlock(
            String blockId
    ) {

        return switch (blockId) {
            case "minecraft:stone",
                 "minecraft:deepslate",
                 "minecraft:granite",
                 "minecraft:diorite",
                 "minecraft:andesite",
                 "minecraft:tuff",
                 "minecraft:calcite",
                 "minecraft:dripstone_block",
                 "minecraft:blackstone",
                 "minecraft:basalt",
                 "minecraft:smooth_basalt",
                 "minecraft:coal_ore",
                 "minecraft:deepslate_coal_ore",
                 "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore",
                 "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore",
                 "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore",
                 "minecraft:nether_gold_ore",
                 "minecraft:redstone_ore",
                 "minecraft:deepslate_redstone_ore",
                 "minecraft:lapis_ore",
                 "minecraft:deepslate_lapis_ore",
                 "minecraft:emerald_ore",
                 "minecraft:deepslate_emerald_ore",
                 "minecraft:diamond_ore",
                 "minecraft:deepslate_diamond_ore",
                 "minecraft:ancient_debris" ->
                    true;

            default ->
                    false;
        };
    }

    private static class PendingPlacement {

        UUID playerId;

        ServerLevel level;

        BlockPos pos;

        Block expectedBlock;

        int ticksLeft;
    }
}