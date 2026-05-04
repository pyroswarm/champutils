package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

import java.util.Random;

public class FarmingProfessionListener {

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

                    if (!(block instanceof CropBlock crop)) {
                        return;
                    }

                    /*
                     Must be fully grown
                     */
                    if (!crop.isMaxAge(state)) {
                        return;
                    }

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .farmingXp
                                    .getOrDefault(
                                            "default",
                                            10
                                    );

                    if (xp <= 0) {
                        return;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.FARMING,
                            xp
                    );

                    rollReward(serverPlayer);
                }
        );
    }

    private static void rollReward(
            ServerPlayer player
    ) {
        var reward =
                ProfessionConfig
                        .SETTINGS
                        .rewards
                        .get("FARMING_RARE_DROP");

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