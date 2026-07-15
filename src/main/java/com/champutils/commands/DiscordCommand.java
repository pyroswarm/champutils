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
        DiscordLinkManager.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("discord")
                        .executes(ctx -> sendInvite(ctx.getSource()))
                        .then(literal("link").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DiscordLinkManager.createCodeAsync(player.getUUID(), player.getGameProfile().getName())
                                    .whenComplete((code, error) -> player.server.execute(() -> {
                                        if (error != null || code == null || code.isBlank()) {
                                            player.sendSystemMessage(Component.literal("Could not create a Discord link code right now.").withStyle(ChatFormatting.RED));
                                            if (error != null) error.printStackTrace();
                                            return;
                                        }
                                        player.sendSystemMessage(Component.literal("Discord link code: ").withStyle(ChatFormatting.AQUA)
                                                .append(Component.literal(code).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                                        player.sendSystemMessage(Component.literal("Use this code in the Discord link channel. A Discord bot/bridge must call /discord verify after checking the code.").withStyle(ChatFormatting.GRAY));
                                    }));
                            return 1;
                        }))
                        .then(literal("status").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DiscordLinkManager.linkedAsync(player.getUUID()).whenComplete((linked, error) -> player.server.execute(() -> {
                                if (error != null) {
                                    player.sendSystemMessage(Component.literal("Could not check your Discord link right now.").withStyle(ChatFormatting.RED));
                                    error.printStackTrace();
                                } else if (linked == null) {
                                    player.sendSystemMessage(Component.literal("Your Minecraft account is not linked to Discord yet. Use /discord link.").withStyle(ChatFormatting.YELLOW));
                                } else {
                                    player.sendSystemMessage(Component.literal("Linked Discord: " + linked.discordName + " (" + linked.discordId + ")").withStyle(ChatFormatting.GREEN));
                                }
                            }));
                            return 1;
                        }))
                        .then(literal("unlink").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            DiscordLinkManager.unlinkAsync(player.getUUID()).whenComplete((removed, error) -> player.server.execute(() -> {
                                if (error != null) {
                                    player.sendSystemMessage(Component.literal("Could not unlink Discord right now.").withStyle(ChatFormatting.RED));
                                    error.printStackTrace();
                                    return;
                                }
                                boolean didRemove = Boolean.TRUE.equals(removed);
                                player.sendSystemMessage(Component.literal(didRemove ? "Discord account unlinked." : "You did not have a linked Discord account.").withStyle(didRemove ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                            }));
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
        final UUID uuid;
        try { uuid = UUID.fromString(uuidRaw); }
        catch (Exception e) { source.sendFailure(Component.literal("Invalid Minecraft UUID.")); return 0; }
        DiscordLinkManager.verifyAsync(uuid, code, discordId, discordName).whenComplete((result, error) -> source.getServer().execute(() -> {
            if (error != null || result == null) {
                source.sendFailure(Component.literal("Discord verify could not reach shared storage.").withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }
            if (result == DiscordLinkManager.VerifyResult.SUCCESS) {
                source.sendSuccess(() -> Component.literal("Linked Minecraft " + uuid + " to Discord " + discordName + " (" + discordId + "). Add the Discord role named Linked from your bot.").withStyle(ChatFormatting.GREEN), false);
                ServerPlayer player = source.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) player.sendSystemMessage(Component.literal("Your Discord account is now linked.").withStyle(ChatFormatting.GREEN));
                com.champutils.network.NetworkEventManager.publishPlayerNotice(uuid, "§aYour Discord account is now linked.");
            } else {
                source.sendFailure(Component.literal("Discord verify failed: " + result.name()).withStyle(ChatFormatting.RED));
            }
        }));
        return 1;
    }

    private static int bridgeChat(net.minecraft.commands.CommandSourceStack source, String discordId, String discordName, String message) {
        DiscordLinkManager.linkedByDiscordIdAsync(discordId).whenComplete((linked, error) -> source.getServer().execute(() -> {
            if (error != null) {
                source.sendFailure(Component.literal("Could not verify that Discord account against shared storage."));
                error.printStackTrace();
                return;
            }
            if (linked == null) {
                source.sendFailure(Component.literal("That Discord ID is not linked, so the chat message was blocked."));
                return;
            }
            Component line = Component.literal("[Discord] ").withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(discordName + ": ").withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
            source.getServer().getPlayerList().broadcastSystemMessage(line, false);
            com.champutils.network.NetworkEventManager.publishBroadcast(line);
        }));
        return 1;
    }

}
