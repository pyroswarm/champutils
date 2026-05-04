package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Random;

public class MiningProfessionListener {

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

                    if (ProfessionBlockTracker.isPlayerPlaced(pos)) {
                        ProfessionBlockTracker.remove(pos);
                        return;
                    }

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .miningXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
                        return;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.MINING,
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
                        .get("MINING_RARE_DROP");

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