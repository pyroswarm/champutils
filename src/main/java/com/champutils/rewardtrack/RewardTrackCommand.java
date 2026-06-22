package com.champutils.rewardtrack;

import com.champutils.economy.EconomyManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static net.minecraft.commands.Commands.literal;

public final class RewardTrackCommand {
    private RewardTrackCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(literal("rewardtrack")
                .executes(ctx -> { show(ctx.getSource().getPlayerOrException()); return 1; })
                .then(literal("claim").executes(ctx -> { claim(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(literal("missions").executes(ctx -> { RewardTrackMissionManager.show(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(literal("menu").executes(ctx -> { show(ctx.getSource().getPlayerOrException()); RewardTrackMissionManager.show(ctx.getSource().getPlayerOrException()); return 1; }))));
    }

    public static void addXp(ServerPlayer player, int xp, String reason) {
        if (player == null || xp <= 0) return;
        RewardTrackMissionManager.onRankedEvent(player, reason);
        addRawXp(player, xp, reason);
    }

    static void addRawXp(ServerPlayer player, int xp, String reason) {
        if (player == null || xp <= 0) return;
        RewardTrackData.Save data = RewardTrackData.get(player);
        int before = level(data.xp);
        data.xp = Math.max(0, data.xp + xp);
        RewardTrackData.save(player);
        int after = level(data.xp);
        if (after > before) {
            player.sendSystemMessage(Component.literal("Reward Track level up! Level " + after + "/" + RewardTrackConfig.maxLevel() + ". Use /rewardtrack claim.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static int level(int xp) {
        return Math.min(RewardTrackConfig.maxLevel(), Math.max(0, xp) / RewardTrackConfig.xpPerLevel());
    }

    private static void show(ServerPlayer player) {
        RewardTrackData.Save data = RewardTrackData.get(player);
        int level = level(data.xp);
        int nextLevelXp = Math.min(RewardTrackConfig.maxLevel(), level + 1) * RewardTrackConfig.xpPerLevel();
        player.sendSystemMessage(Component.literal("Ranked Reward Track: level " + level + "/" + RewardTrackConfig.maxLevel()).withStyle(ChatFormatting.GOLD));
        if (level < RewardTrackConfig.maxLevel()) {
            player.sendSystemMessage(Component.literal("XP: " + data.xp + "/" + nextLevelXp).withStyle(ChatFormatting.YELLOW));
        }
        player.sendSystemMessage(Component.literal("Claimed through level " + data.claimedLevel + ". Use /rewardtrack claim for ready rewards.").withStyle(ChatFormatting.AQUA));
        RewardTrackMissionManager.ensure(player);
        player.sendSystemMessage(Component.literal("Use /rewardtrack missions to view daily and weekly ranked PvP missions.").withStyle(ChatFormatting.GRAY));
    }

    private static void claim(ServerPlayer player) {
        RewardTrackData.Save data = RewardTrackData.get(player);
        int currentLevel = level(data.xp);
        if (data.claimedLevel >= currentLevel) {
            player.sendSystemMessage(Component.literal("No reward track rewards ready.").withStyle(ChatFormatting.GRAY));
            return;
        }

        for (int level = data.claimedLevel + 1; level <= currentLevel; level++) {
            List<ItemStack> stacks = RewardTrackConfig.itemStacks(level);
            if (!canFit(player, stacks)) {
                player.sendSystemMessage(Component.literal("Make inventory space before claiming reward track level " + level + ".").withStyle(ChatFormatting.RED));
                return;
            }

            RewardTrackConfig.Reward reward = RewardTrackConfig.reward(level);
            if (reward != null && reward.credits > 0) {
                EconomyManager.deposit(player, reward.credits, "rewardtrack_level_" + level);
            }
            for (ItemStack stack : stacks) {
                player.getInventory().add(stack.copy());
            }
            data.claimedLevel = level;
        }

        RewardTrackData.save(player);
        player.sendSystemMessage(Component.literal("Claimed reward track rewards through level " + data.claimedLevel + ".").withStyle(ChatFormatting.GREEN));
    }

    private static boolean canFit(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return true;
        int emptySlots = 0;
        for (ItemStack slot : player.getInventory().items) {
            if (slot.isEmpty()) emptySlots++;
        }
        int needed = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            needed += Math.max(1, (int) Math.ceil(stack.getCount() / (double) stack.getMaxStackSize()));
        }
        return emptySlots >= needed;
    }
}
