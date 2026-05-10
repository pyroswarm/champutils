package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.Random;

public class ProfessionLootManager {

    private static final Random RANDOM = new Random();

    private ProfessionLootManager() {}

    public static void rollReward(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return;

        ProfessionLootConfig.LootTable table = ProfessionLootConfig.TABLES.get(profession.name());
        if (table == null || table.items == null || table.items.isEmpty()) return;
        if (table.dropChance <= 0.0D) return;

        if (RANDOM.nextDouble() >= table.dropChance) return;

        ProfessionLootConfig.LootEntry reward = rollEntry(table.items);
        if (reward == null) return;

        int min = Math.max(1, reward.minAmount);
        int max = Math.max(min, reward.maxAmount);
        int amount = min + RANDOM.nextInt((max - min) + 1);

        giveReward(player, reward.itemId, amount);
    }

    public static void giveReward(ServerPlayer player, String itemId, int amount) {
        if (player == null || itemId == null || itemId.isEmpty() || amount <= 0) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        String command =
                "give " +
                        player.getGameProfile().getName() +
                        " " +
                        itemId +
                        " " +
                        amount;

        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(),
                command
        );

        sendRareDropMessage(player, itemId, amount);
    }

    private static ProfessionLootConfig.LootEntry rollEntry(List<ProfessionLootConfig.LootEntry> items) {
        int totalWeight = 0;

        for (ProfessionLootConfig.LootEntry entry : items) {
            if (entry != null && entry.weight > 0) {
                totalWeight += entry.weight;
            }
        }

        if (totalWeight <= 0) return null;

        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (ProfessionLootConfig.LootEntry entry : items) {
            if (entry == null || entry.weight <= 0) continue;

            current += entry.weight;
            if (roll < current) {
                return entry;
            }
        }

        return null;
    }

    private static void sendRareDropMessage(ServerPlayer player, String itemId, int amount) {
        player.displayClientMessage(
                Component.literal("§6Rare Drop! §e" + itemId + " x" + amount),
                true
        );

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS,
                1.0F,
                1.2F
        );
    }
}
