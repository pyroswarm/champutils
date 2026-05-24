package com.champutils.commands;

import com.champutils.teleport.BackManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BackCommand {
    private BackCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("back")
                .executes(context -> run(context.getSource().getPlayerOrException()))));
    }

    private static int run(ServerPlayer player) {
        if (BackManager.teleportBack(player)) {
            player.sendSystemMessage(Component.literal("Returned to your previous location.").withStyle(ChatFormatting.GREEN));
            return 1;
        }
        player.sendSystemMessage(Component.literal("No previous location saved.").withStyle(ChatFormatting.RED));
        return 0;
    }
}
