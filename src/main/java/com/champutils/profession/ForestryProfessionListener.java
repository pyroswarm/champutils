package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ForestryProfessionListener {

    private static final Random RANDOM =
            new Random();

    public static void register() {

        PlayerBlockBreakEvents.BEFORE.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return true;
                    }

                    Block block =
                            state.getBlock();

                    String blockId =
                            getBlockId(
                                    block
                            );

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .forestryXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
                        return true;
                    }

                    if (
                            ProfessionBlockTracker.isPlayerPlaced(
                                    serverPlayer.serverLevel(),
                                    pos
                            )
                    ) {
                        ProfessionBlockTracker.remove(
                                serverPlayer.serverLevel(),
                                pos
                        );
                        return true;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.FORESTRY,
                            xp
                    );

                    ProfessionLootManager.rollReward(
                            serverPlayer,
                            ProfessionType.FORESTRY
                    );

                    handleBonusLogs(
                            serverPlayer,
                            blockId
                    );

                    handleTimberBreak(
                            serverPlayer,
                            pos
                    );

                    return true;
                }
        );
    }

    private static void handleBonusLogs(
            ServerPlayer player,
            String blockId
    ) {

        ItemStack held =
                player.getMainHandItem();

        if (
                !ProfessionToolUtil.hasPassive(
                        held,
                        "faster_tree_chopping"
                ) &&
                        !ProfessionToolUtil.hasPassive(
                                held,
                                "timber_break"
                        )
        ) {
            return;
        }

        double bonusLogs =
                ProfessionToolUtil.getStat(
                        held,
                        "bonusLogs"
                );

        if (bonusLogs <= 0) {
            return;
        }

        double chance =
                bonusLogs / 100D;

        if (RANDOM.nextDouble() > chance) {
            return;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse(
                                blockId
                        )
                );

        if (item == null) {
            return;
        }

        ItemStack reward =
                new ItemStack(
                        item,
                        1
                );

        if (!player.getInventory().add(reward)) {
            player.drop(
                    reward,
                    false
            );
        }

        player.displayClientMessage(
                Component.literal(
                        "§aBonus log drop!"
                ),
                true
        );
    }

    private static void handleTimberBreak(
            ServerPlayer player,
            BlockPos start
    ) {

        ItemStack held =
                player.getMainHandItem();

        if (
                !ProfessionToolUtil.hasPassive(
                        held,
                        "timber_break"
                )
        ) {
            return;
        }

        ServerLevel level =
                player.serverLevel();

        int maxBlocks =
                24;

        Set<BlockPos> visited =
                new HashSet<>();

        ArrayDeque<BlockPos> queue =
                new ArrayDeque<>();

        queue.add(
                start
        );

        int broken =
                0;

        while (
                !queue.isEmpty() &&
                        broken < maxBlocks
        ) {

            BlockPos current =
                    queue.poll();

            if (!visited.add(current)) {
                continue;
            }

            if (current.equals(start)) {
                addNeighbors(
                        queue,
                        current
                );
                continue;
            }

            BlockState state =
                    level.getBlockState(
                            current
                    );

            String blockId =
                    getBlockId(
                            state.getBlock()
                    );

            Integer xp =
                    ProfessionConfig
                            .SETTINGS
                            .forestryXp
                            .get(
                                    blockId
                            );

            if (xp == null || xp <= 0) {
                continue;
            }

            if (
                    ProfessionBlockTracker.isPlayerPlaced(
                            level,
                            current
                    )
            ) {
                continue;
            }

            boolean destroyed =
                    level.destroyBlock(
                            current,
                            true,
                            player
                    );

            if (!destroyed) {
                continue;
            }

            ProfessionManager.addXp(
                    player,
                    ProfessionType.FORESTRY,
                    xp
            );

            broken++;

            addNeighbors(
                    queue,
                    current
            );
        }

        if (broken > 0) {
            player.displayClientMessage(
                    Component.literal(
                            "§2Timber Break felled §a" +
                                    broken +
                                    " §2extra logs!"
                    ),
                    true
            );
        }
    }

    private static void addNeighbors(
            ArrayDeque<BlockPos> queue,
            BlockPos pos
    ) {

        for (int x = -1; x <= 1; x++) {
            for (int y = 0; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {

                    if (
                            x == 0 &&
                                    y == 0 &&
                                    z == 0
                    ) {
                        continue;
                    }

                    queue.add(
                            pos.offset(
                                    x,
                                    y,
                                    z
                            )
                    );
                }
            }
        }
    }

    private static String getBlockId(
            Block block
    ) {

        return block.builtInRegistryHolder()
                .key()
                .location()
                .toString();
    }
}