package com.champutils.cosmetic;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class TitleCommand {
    private TitleCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("titles")
                    .executes(ctx -> {
                        TitleMenu.open(ctx.getSource().getPlayerOrException());
                        return 1;
                    })
                    .then(Commands.literal("select")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .executes(ctx -> {
                                        TitleManager.select(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"));
                                        return 1;
                                    })))
                    .then(Commands.literal("unlock")
                            .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                            .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .executes(ctx -> unlock(
                                                    net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "id"),
                                                    null
                                            ))
                                            .then(Commands.argument("display", StringArgumentType.greedyString())
                                                    .executes(ctx -> unlock(
                                                            net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player"),
                                                            StringArgumentType.getString(ctx, "id"),
                                                            StringArgumentType.getString(ctx, "display")
                                                    )))))));
        });
    }

    private static int unlock(ServerPlayer player, String id, String display) {
        String resolved = display;
        if (resolved == null || resolved.isBlank()) resolved = TitleRegistry.defaultDisplay(id);
        if (resolved == null || resolved.isBlank()) resolved = "&7[" + id + "]";
        boolean changed = TitleManager.unlock(player, id, resolved);
        if (!changed) {
            player.sendSystemMessage(Component.literal("That title is already unlocked.").withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }
}

