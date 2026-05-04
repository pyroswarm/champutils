package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;

import java.util.Random;

public class ForestryProfessionListener {

    private static final Random RANDOM =
            new Random();

    public static void register() {

        PlayerBlockBreakEvents.AFTER.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    Block block =
                            state.getBlock();

                    String blockId =
                            block.builtInRegistryHolder()
                                    .key()
                                    .location()
                                    .toString();

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .forestryXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
                        return;
                    }

                    /*
                     Prevent manually placed log farming
                     */
                    if (!looksNaturalTree(world, pos)) {
                        return;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.FORESTRY,
                            xp
                    );

                    rollReward(serverPlayer);
                }
        );
    }

    private static boolean looksNaturalTree(
            net.minecraft.world.level.Level world,
            BlockPos pos
    ) {

        /*
         Check nearby leaves
         */
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {

                    BlockPos check =
                            pos.offset(x, y, z);

                    Block nearby =
                            world.getBlockState(check)
                                    .getBlock();

                    if (nearby instanceof LeavesBlock) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void rollReward(
            ServerPlayer player
    ) {

        var reward =
                ProfessionConfig
                        .SETTINGS
                        .rewards
                        .get("FORESTRY_RARE_DROP");

        if (reward == null) {
            return;
        }

        if (RANDOM.nextDouble() > reward.chance) {
            return;
        }

        int amount =
                reward.minAmount +
                        RANDOM.nextInt(
                                reward.maxAmount -
                                        reward.minAmount + 1
                        );

        ProfessionActionBarManager.sendRareDropMessage(
                player,
                reward.itemId,
                amount
        );
    }
}