package com.champutils.commands;

import com.champutils.profile.IslanderMineConfig;
import com.champutils.profile.IslanderMineManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class IslanderMineCommand {
    private IslanderMineCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("island")
                    .then(literal("mine")
                            .executes(ctx -> openMine(ctx.getSource().getPlayerOrException()))));

            dispatcher.register(literal("islandermine")
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                    .then(literal("reload")
                            .executes(ctx -> {
                                IslanderMineConfig.load();
                                ctx.getSource().sendSuccess(() -> Component.literal("Reloaded islander_mines.json.").withStyle(ChatFormatting.GREEN), false);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .then(literal("reset")
                            .executes(ctx -> {
                                IslanderMineManager.queueManualReset(ctx.getSource().getServer(), ctx.getSource().getPlayer());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(argument("playerOrProfile", StringArgumentType.word())
                                    .executes(ctx -> {
                                        IslanderMineManager.queueManualReset(
                                                ctx.getSource().getServer(),
                                                ctx.getSource().getPlayer(),
                                                StringArgumentType.getString(ctx, "playerOrProfile")
                                        );
                                        return Command.SINGLE_SUCCESS;
                                    }))));
        });
    }

    private static int openMine(ServerPlayer player) {
        return IslanderMineManager.teleportToMine(player) ? Command.SINGLE_SUCCESS : 0;
    }
}
