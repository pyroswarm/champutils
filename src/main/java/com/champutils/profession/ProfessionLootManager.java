package com.champutils.profession;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

public class ProfessionLootManager {

    private static final Random RANDOM =
            new Random();

    public static void rollReward(
            ServerPlayer player,
            ProfessionType type
    ) {

        ProfessionLootConfig.LootTable table =
                ProfessionLootConfig.TABLES.get(
                        type.name()
                );

        if (table == null) {
            return;
        }

        if (
                RANDOM.nextDouble() >
                        table.dropChance
        ) {
            return;
        }

        ProfessionLootConfig.LootEntry chosen =
                getWeightedReward(
                        table.items
                );

        if (chosen == null) {
            return;
        }

        int amount =
                chosen.minAmount +
                        RANDOM.nextInt(
                                chosen.maxAmount -
                                        chosen.minAmount + 1
                        );

        giveReward(
                player,
                chosen.itemId,
                amount
        );
    }

    private static ProfessionLootConfig.LootEntry getWeightedReward(
            List<ProfessionLootConfig.LootEntry> items
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