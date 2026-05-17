package com.champutils.dungeon;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonCommandLock {

    private DungeonCommandLock() {
    }

    public static void register() {
        DungeonInteractionLock.register();

        ServerMessageEvents.ALLOW_COMMAND_MESSAGE.register((message, source, params) -> {
            ServerPlayer player = getPlayer(source);
            if (player == null) {
                return true;
            }

            String command = message.signedContent();

            if (DungeonManager.isInDungeon(player) && DungeonManager.isSpawnCommand(command)) {
                DungeonManager.forfeitDungeon(player);
                return true;
            }

            if (DungeonManager.isAllowedDungeonCommand(player, command)) {
                return true;
            }

            player.sendSystemMessage(
                    Component.literal("You cannot use commands inside a dungeon. Use /dungeon forfeit or /spawn to leave.")
                            .withStyle(ChatFormatting.RED)
            );
            return false;
        });
    }

    private static ServerPlayer getPlayer(CommandSourceStack source) {
        if (source == null) {
            return null;
        }

        try {
            return source.getPlayer();
        } catch (Exception ignored) {
            return null;
        }
    }
}
