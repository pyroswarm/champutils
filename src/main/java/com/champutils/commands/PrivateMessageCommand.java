package com.champutils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrivateMessageCommand {
    private static final Map<UUID, UUID> LAST_REPLY = new ConcurrentHashMap<>();

    private PrivateMessageCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("pm")
                    .then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> send(
                                            ctx.getSource().getPlayerOrException(),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            StringArgumentType.getString(ctx, "message")
                                    )))));

            dispatcher.register(Commands.literal("reply")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> reply(
                                    ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "message")
                            ))));

            dispatcher.register(Commands.literal("r")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> reply(
                                    ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "message")
                            ))));
        });
    }

    private static int send(ServerPlayer sender, ServerPlayer target, String message) {
        if (sender == null || sender.server == null) return 0;
        if (message == null || message.isBlank()) {
            sender.sendSystemMessage(Component.literal("Usage: /pm <player> <message>").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target == null) {
            sender.sendSystemMessage(Component.literal("That player is not online.").withStyle(ChatFormatting.RED));
            return 0;
        }
        deliver(sender, target, message);
        return 1;
    }

    private static int reply(ServerPlayer sender, String message) {
        if (sender == null || sender.server == null) return 0;
        UUID targetId = LAST_REPLY.get(sender.getUUID());
        if (targetId == null) {
            sender.sendSystemMessage(Component.literal("Nobody has messaged you yet.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer target = sender.server.getPlayerList().getPlayer(targetId);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("That player is no longer online.").withStyle(ChatFormatting.RED));
            return 0;
        }
        deliver(sender, target, message);
        return 1;
    }

    private static void deliver(ServerPlayer sender, ServerPlayer target, String message) {
        // /r should target the player who most recently messaged you. Sending a message should not
        // steal your incoming reply target unless the target replies back.
        LAST_REPLY.put(target.getUUID(), sender.getUUID());
        Component toTarget = Component.literal("§d§l[PM] §d" + sender.getGameProfile().getName() + " → you: §d" + message);
        Component toSender = Component.literal("§d§l[PM] §dyou → " + target.getGameProfile().getName() + ": §d" + message);
        target.sendSystemMessage(toTarget);
        sender.sendSystemMessage(toSender);
    }
}
