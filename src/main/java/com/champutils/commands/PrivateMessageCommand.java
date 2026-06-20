package com.champutils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PrivateMessageCommand {
    private PrivateMessageCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var node = Commands.literal("msg")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> send(
                                            ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "message")
                                    ))));
            dispatcher.register(node);
            dispatcher.register(Commands.literal("pm").redirect(dispatcher.getRoot().getChild("msg")));
        });
    }

    private static int send(ServerPlayer sender, String targetName, String message) {
        if (sender == null || sender.server == null) return 0;
        if (message == null || message.isBlank()) {
            sender.sendSystemMessage(Component.literal("Usage: /msg <player> <message>").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target = sender.server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("That player is not online.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Component toTarget = Component.literal("[PM] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(sender.getGameProfile().getName() + " -> you: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        Component toSender = Component.literal("[PM] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("you -> " + target.getGameProfile().getName() + ": ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        target.sendSystemMessage(toTarget);
        sender.sendSystemMessage(toSender);
        return 1;
    }
}
