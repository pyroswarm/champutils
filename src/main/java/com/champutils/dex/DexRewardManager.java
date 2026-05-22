package com.champutils.dex;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class DexRewardManager {

    private DexRewardManager() {
    }

    public static boolean claim(ServerPlayer player, int percent) {
        if (player == null) {
            return false;
        }

        int unlocked = DexProgressManager.getUnlockedPercent(player);
        if (unlocked < percent) {
            int required = DexProgressManager.requiredCaughtForPercent(percent);
            player.sendSystemMessage(Component.literal("You have not reached " + percent + "% Pokédex completion yet. Catch " + required + " unique Pokémon to unlock this reward.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (DexRewardClaimData.hasClaimed(player.getUUID(), percent)) {
            player.sendSystemMessage(Component.literal("You already claimed the " + percent + "% Pokédex reward.").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        DexRewardConfig.DexRewardTier tier = DexRewardConfig.getTier(percent);
        for (String rawCommand : tier.commands) {
            runRewardCommand(player, rawCommand, percent);
        }

        DexRewardClaimData.markClaimed(player.getUUID(), percent);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.sendSystemMessage(Component.literal("Claimed " + percent + "% Pokédex reward!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        return true;
    }

    private static void runRewardCommand(ServerPlayer player, String rawCommand, int percent) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        String command = rawCommand
                .replace("%player%", player.getGameProfile().getName())
                .replace("%uuid%", player.getUUID().toString())
                .replace("%percent%", String.valueOf(percent))
                .replace("%caught%", String.valueOf(DexProgressManager.getCaughtCount(player)))
                .replace("%total%", String.valueOf(DexProgressManager.getTotalPokemon()));

        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        CommandSourceStack source = server.createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();

        server.getCommands().performPrefixedCommand(source, command);
    }
}
