package com.champutils.profession;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Random;

public class WildBattleRewardManager {

    private static final Random RANDOM =
            new Random();

    public static void rollReward(
            ServerPlayer player
    ) {

        var table =
                WildBattleLootConfig.TABLE;

        if (
                RANDOM.nextDouble() >
                        table.dropChance
        ) {
            return;
        }

        var reward =
                getWeightedReward(
                        table.items
                );

        if (reward == null) {
            return;
        }

        int amount =
                reward.minAmount +
                        RANDOM.nextInt(
                                reward.maxAmount -
                                        reward.minAmount + 1
                        );

        giveReward(
                player,
                reward.itemId,
                amount
        );
    }

    private static WildBattleLootConfig.LootEntry getWeightedReward(
            List<WildBattleLootConfig.LootEntry> items
    ) {

        int totalWeight = 0;

        for (var item : items) {
            totalWeight += item.weight;
        }

        int roll =
                RANDOM.nextInt(totalWeight);

        int current = 0;

        for (var item : items) {
            current += item.weight;

            if (roll < current) {
                return item;
            }
        }

        return null;
    }

    private static void giveReward(
            ServerPlayer player,
            String itemId,
            int amount
    ) {

        MinecraftServer server =
                player.getServer();

        if (server == null) {
            return;
        }

        String command =
                "nucleus item give " +
                        player.getName().getString() +
                        " " +
                        itemId +
                        " " +
                        amount;

        server.getCommands()
                .performPrefixedCommand(
                        server.createCommandSourceStack(),
                        command
                );

        ProfessionActionBarManager
                .sendRareDropMessage(
                        player,
                        itemId,
                        amount
                );
    }
}