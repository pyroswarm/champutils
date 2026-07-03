package com.champutils.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ChatCommand {
    private ChatCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("chat")
                    .executes(context -> status(context.getSource().getPlayerOrException()))
                    .then(Commands.literal("local").executes(context -> set(context.getSource().getPlayerOrException(), ChatMode.LOCAL)))
                    .then(Commands.literal("global").executes(context -> set(context.getSource().getPlayerOrException(), ChatMode.GLOBAL)))
                    .then(Commands.literal("party").executes(context -> set(context.getSource().getPlayerOrException(), ChatMode.PARTY)))
                    .then(Commands.literal("guild").executes(context -> set(context.getSource().getPlayerOrException(), ChatMode.GUILD)))
                    .then(Commands.literal("reloadtags")
                            .requires(source -> source.hasPermission(4))
                            .executes(context -> reload(context.getSource().getPlayerOrException())))
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .executes(context -> setParsed(
                                    context.getSource().getPlayerOrException(),
                                    StringArgumentType.getString(context, "mode")
                            ))));

            oneShot(dispatcher, "local", ChatMode.LOCAL);
            oneShot(dispatcher, "l", ChatMode.LOCAL);
            oneShot(dispatcher, "global", ChatMode.GLOBAL);
            oneShot(dispatcher, "gl", ChatMode.GLOBAL);
            oneShot(dispatcher, "partychat", ChatMode.PARTY);
            oneShot(dispatcher, "pc", ChatMode.PARTY);
            oneShot(dispatcher, "guildchat", ChatMode.GUILD);
            oneShot(dispatcher, "gc", ChatMode.GUILD);
        });
    }

    private static void oneShot(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher, String literal, ChatMode mode) {
        dispatcher.register(Commands.literal(literal)
                .executes(context -> set(context.getSource().getPlayerOrException(), mode))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(context -> send(
                                context.getSource().getPlayerOrException(),
                                mode,
                                StringArgumentType.getString(context, "message")
                        ))));
    }

    private static int status(ServerPlayer player) {
        ChatMode mode = ChatPreferenceManager.get(player.getUUID());
        player.sendSystemMessage(Component.literal("Current chat: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(mode.displayName).withStyle(mode.color))
                .append(Component.literal(". Use /chat local, /chat global, /chat party, or /chat guild.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int setParsed(ServerPlayer player, String raw) {
        ChatMode mode = ChatMode.parse(raw);
        if (mode == null) {
            player.sendSystemMessage(Component.literal("Unknown chat mode. Use local, global, party, or guild.").withStyle(ChatFormatting.RED));
            return 0;
        }
        return set(player, mode);
    }

    private static int set(ServerPlayer player, ChatMode mode) {
        ChatPreferenceManager.set(player.getUUID(), mode);
        player.sendSystemMessage(Component.literal("Chat mode set to ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(mode.displayName).withStyle(mode.color))
                .append(Component.literal(".").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int send(ServerPlayer player, ChatMode mode, String message) {
        return ServerChatManager.send(player, mode, message, true) ? 1 : 0;
    }

    private static int reload(ServerPlayer player) {
        ChatTagConfig.load();
        player.sendSystemMessage(Component.literal("Reloaded chat tags.").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
