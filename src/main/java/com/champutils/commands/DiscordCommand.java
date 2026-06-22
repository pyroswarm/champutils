package com.champutils.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import static net.minecraft.commands.Commands.literal;

public final class DiscordCommand {
    // Change this in source/config later if you want a different invite.
    public static String DISCORD_URL = "https://discord.gg/replace-this";

    private DiscordCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("discord").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Discord: ").withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(DISCORD_URL)
                                    .withStyle(style -> style.withColor(ChatFormatting.BLUE)
                                            .withUnderlined(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DISCORD_URL))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to open the Discord invite."))))), false);
                    return 1;
                })
        ));
    }
}
