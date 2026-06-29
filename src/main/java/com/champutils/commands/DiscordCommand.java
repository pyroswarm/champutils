package com.champutils.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class DiscordCommand {
    public static String DISCORD_URL = "https://discord.gg/GeGUHpzQnC";

    private DiscordCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("discord")
                        .executes(ctx -> sendInvite(ctx.getSource()))
                        .then(literal("link").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String code = DiscordLinkManager.createCode(player.getUUID(), player.getGameProfile().getName());
                            player.sendSystemMessage(Component.literal("Discord link code: ").withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal(code).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                            player.sendSystemMessage(Component.literal("Use this code in the Discord link channel. A Discord bot/bridge must call /discord verify after checking the code.").withStyle(ChatFormatting.GRAY));
                            return 1;
                        }))
                        .then(literal("status").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DiscordLinkManager.LinkedAccount linked = DiscordLinkManager.linked(player.getUUID());
                            if (linked == null) player.sendSystemMessage(Component.literal("Your Minecraft account is not linked to Discord yet. Use /discord link.").withStyle(ChatFormatting.YELLOW));
                            else player.sendSystemMessage(Component.literal("Linked Discord: " + linked.discordName + " (" + linked.discordId + ")").withStyle(ChatFormatting.GREEN));
                            return 1;
                        }))
                        .then(literal("unlink").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            boolean removed = DiscordLinkManager.unlink(player.getUUID());
                            player.sendSystemMessage(Component.literal(removed ? "Discord account unlinked." : "You did not have a linked Discord account.").withStyle(removed ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                            return 1;
                        }))
                        .then(literal("verify")
                                .requires(source -> source.hasPermission(4))
                                .then(argument("minecraftUuid", StringArgumentType.word())
                                        .then(argument("code", StringArgumentType.word())
                                                .then(argument("discordId", StringArgumentType.word())
                                                        .then(argument("discordName", StringArgumentType.greedyString())
                                                                .executes(ctx -> verify(ctx.getSource(),
                                                                        StringArgumentType.getString(ctx, "minecraftUuid"),
                                                                        StringArgumentType.getString(ctx, "code"),
                                                                        StringArgumentType.getString(ctx, "discordId"),
                                                                        StringArgumentType.getString(ctx, "discordName"))))))))
                        .then(literal("chat")
                                .requires(source -> source.hasPermission(4))
                                .then(argument("discordId", StringArgumentType.word())
                                        .then(argument("discordName", StringArgumentType.word())
                                                .then(argument("message", StringArgumentType.greedyString())
                                                        .executes(ctx -> bridgeChat(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "discordId"),
                                                                StringArgumentType.getString(ctx, "discordName"),
                                                                StringArgumentType.getString(ctx, "message")))))))
        ));
    }

    private static int sendInvite(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Discord: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(DISCORD_URL)
                        .withStyle(style -> style.withColor(ChatFormatting.BLUE)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DISCORD_URL))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open the Discord invite."))))), false);
        return 1;
    }

    private static int verify(net.minecraft.commands.CommandSourceStack source, String uuidRaw, String code, String discordId, String discordName) {
        try {
            UUID uuid = UUID.fromString(uuidRaw);
            DiscordLinkManager.VerifyResult result = DiscordLinkManager.verify(uuid, code, discordId, discordName);
            if (result == DiscordLinkManager.VerifyResult.SUCCESS) {
                source.sendSuccess(() -> Component.literal("Linked Minecraft " + uuid + " to Discord " + discordName + " (" + discordId + "). Add the Discord role named Linked from your bot.").withStyle(ChatFormatting.GREEN), false);
                ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) player.sendSystemMessage(Component.literal("Your Discord account is now linked.").withStyle(ChatFormatting.GREEN));
            } else {
                source.sendFailure(Component.literal("Discord verify failed: " + result.name()).withStyle(ChatFormatting.RED));
            }
        } catch (Exception e) {
            source.sendFailure(Component.literal("Invalid Minecraft UUID."));
            return 0;
        }
        return 1;
    }

    private static int bridgeChat(net.minecraft.commands.CommandSourceStack source, String discordId, String discordName, String message) {
        DiscordLinkManager.LinkedAccount linked = DiscordLinkManager.linkedByDiscordId(discordId);
        if (linked == null) {
            source.sendFailure(Component.literal("That Discord ID is not linked, so the chat message was blocked."));
            return 0;
        }
        Component line = Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal(discordName + ": ").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        source.getServer().getPlayerList().broadcastSystemMessage(line, false);
        return 1;
    }
}
