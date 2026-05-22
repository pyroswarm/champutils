package com.champutils.commands;

import com.champutils.scoreboard.PlayerSidebarManager;
import com.champutils.scoreboard.ScoreboardPreferenceManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ScoreboardToggleCommand {

    private ScoreboardToggleCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> dispatcher.register(
                        Commands.literal("scoreboardtoggle")
                                .executes(context -> toggle(context.getSource().getPlayerOrException()))
                                .then(Commands.literal("on")
                                        .executes(context -> set(context.getSource().getPlayerOrException(), true)))
                                .then(Commands.literal("off")
                                        .executes(context -> set(context.getSource().getPlayerOrException(), false)))
                )
        );
    }

    private static int toggle(ServerPlayer player) {
        boolean enabled = ScoreboardPreferenceManager.toggle(player.getUUID());
        apply(player, enabled);
        return 1;
    }

    private static int set(ServerPlayer player, boolean enabled) {
        ScoreboardPreferenceManager.setEnabled(player.getUUID(), enabled);
        apply(player, enabled);
        return 1;
    }

    private static void apply(ServerPlayer player, boolean enabled) {
        if (enabled) {
            PlayerSidebarManager.update(player);
            player.sendSystemMessage(Component.literal("Scoreboard display enabled. Use /scoreboardtoggle off to hide it.").withStyle(ChatFormatting.GREEN));
        } else {
            PlayerSidebarManager.clear(player);
            player.sendSystemMessage(Component.literal("Scoreboard display disabled. Use /scoreboardtoggle on to show it again.").withStyle(ChatFormatting.YELLOW));
        }
    }
}
