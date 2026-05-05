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

        if (player == null) {
            return;
        }

        if (
                WildBattleLootConfig.TABLE == null ||
                        WildBattleLootConfig.TABLE.items == null ||
                        WildBattleLootConfig.TABLE.items.isEmpty()
        ) {
            return;
        }

        var table =
                WildBattleLootConfig.TABLE;

        if (table.dropChance <= 0) {
            return;
        }

        if (
                RANDOM.nextDouble() >
                        table.dropChance
        ) {
            return;
        }

        WildBattleLootConfig.LootEntry reward =
                getWeightedReward(
                        table.items
                );

        if (reward == null) {
            return;
        }

        int min =
                Math.max(
                        1,
                        reward.minAmount
                );

        int max =
                Math.max(
                        min,
                        reward.maxAmount
                );

        int amount =
                min +
                        RANDOM.nextInt(
                                max - min + 1
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

        int totalWeight =
                0;

        for (
                WildBattleLootConfig.LootEntry item :
                items
        ) {
            if (
                    item == null ||
                            item.itemId == null ||
                            item.itemId.isBlank() ||
                            item.weight <= 0
            ) {
                continue;
            }

            totalWeight +=
                    item.weight;
        }

        if (totalWeight <= 0) {
            return null;
        }

        int roll =
                RANDOM.nextInt(
                        totalWeight
                );

        int current =
                0;

        for (
                WildBattleLootConfig.LootEntry item :
                items
        ) {
            if (
                    item == null ||
                            item.itemId == null ||
                            item.itemId.isBlank() ||
                            item.weight <= 0
            ) {
                continue;
            }

            current +=
                    item.weight;

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

        if (
                itemId == null ||
                        itemId.isBlank() ||
                        amount <= 0
        ) {
            return;
        }

        String command =
                "give " +
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

        ProfessionActionBarManager.sendRareDropMessage(
                player,
                itemId,
                amount
        );
    }
}