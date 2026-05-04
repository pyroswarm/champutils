package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;

public class FarmingProfessionListener {

    public static void register() {

        PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, blockEntity) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    Block block =
                            state.getBlock();

                    if (!(block instanceof CropBlock crop)) {
                        return;
                    }

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

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.FARMING,
                            xp
                    );

                    ProfessionLootManager.rollReward(
                            serverPlayer,
                            ProfessionType.FARMING
                    );
                }
        );
    }
}