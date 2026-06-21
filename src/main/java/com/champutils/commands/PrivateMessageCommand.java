package com.champutils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
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
            dispatcher.register(Commands.literal("r")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> reply(
                                    ctx.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(ctx, "message")
                            ))));
            dispatcher.register(Commands.literal("reply").redirect(dispatcher.getRoot().getChild("r")));
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
        LAST_REPLY.put(target.getUUID(), sender.getUUID());
        LAST_REPLY.put(sender.getUUID(), target.getUUID());
        Component toTarget = Component.literal("[MSG] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(sender.getGameProfile().getName() + " -> you: ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        Component toSender = Component.literal("[MSG] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal("you -> " + target.getGameProfile().getName() + ": ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        target.sendSystemMessage(toTarget);
        sender.sendSystemMessage(toSender);
    }
}
