package com.champutils.profession;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfessionPlacementListener {

    private static final Map<UUID, PlacementSnapshot> SNAPSHOTS =
            new HashMap<>();

    public static void register() {

        ServerTickEvents.END_SERVER_TICK.register(
                server -> {

                    for (
                            ServerPlayer player :
                            server.getPlayerList().getPlayers()
                    ) {

                        ItemStack stack =
                                player.getMainHandItem();

                        if (!(stack.getItem() instanceof BlockItem blockItem)) {
                            SNAPSHOTS.remove(
                                    player.getUUID()
                            );
                            continue;
                        }

                        UUID playerId =
                                player.getUUID();

                        PlacementSnapshot previous =
                                SNAPSHOTS.get(playerId);

                        int currentCount =
                                stack.getCount();

                        /*
                         Detect actual placement:
                         block count decreased
                         */
                        if (
                                previous != null &&
                                        currentCount < previous.itemCount
                        ) {

                            findPlacedBlock(
                                    player,
                                    blockItem.getBlock()
                            );
                        }

                        PlacementSnapshot snapshot =
                                new PlacementSnapshot();

                        snapshot.itemCount =
                                currentCount;

                        SNAPSHOTS.put(
                                playerId,
                                snapshot
                        );
                    }
                }
        );
    }

    private static void findPlacedBlock(
            ServerPlayer player,
            Block expectedBlock
    ) {

        ServerLevel level =
                player.serverLevel();

        BlockPos center =
                player.blockPosition();

        for (int x = -5; x <= 5; x++) {
            for (int y = -5; y <= 5; y++) {
                for (int z = -5; z <= 5; z++) {

                    BlockPos checkPos =
                            center.offset(
                                    x,
                                    y,
                                    z
                            );

                    Block block =
                            level.getBlockState(
                                    checkPos
                            ).getBlock();

                    if (block == expectedBlock) {

                        String blockId =
                                block.builtInRegistryHolder()
                                        .key()
                                        .location()
                                        .toString();

                        boolean isMining =
                                ProfessionConfig
                                        .SETTINGS
                                        .miningXp
                                        .containsKey(blockId);

                        boolean isForestry =
                                ProfessionConfig
                                        .SETTINGS
                                        .forestryXp
                                        .containsKey(blockId);

                        if (
                                isMining ||
                                        isForestry
                        ) {

                            ProfessionBlockTracker.markPlaced(
                                    level,
                                    checkPos,
                                    player.getUUID()
                            );

                            return;
                        }
                    }
                }
            }
        }
    }

    private static class PlacementSnapshot {
        int itemCount;
    }
}