package com.champutils.commands;

import com.champutils.util.ServerLocationManager;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class SpawnWarpCommand {

    private static final SuggestionProvider<CommandSourceStack> WARP_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(
                    ServerLocationManager.warps().keySet(),
                    builder
            );

    private SpawnWarpCommand() {
    }

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            Commands.literal("spawn")
                                    .executes(context -> spawn(
                                            context.getSource()
                                                    .getPlayerOrException()
                                    ))
                    );

                    dispatcher.register(
                            Commands.literal("setspawn")
                                    .requires(source -> source.hasPermission(4))
                                    .executes(context -> setSpawn(
                                            context.getSource()
                                                    .getPlayerOrException()
                                    ))
                    );

                    dispatcher.register(
                            Commands.literal("warp")
                                    .then(Commands.argument(
                                                    "name",
                                                    StringArgumentType.word()
                                            )
                                            .suggests(WARP_SUGGESTIONS)
                                            .executes(context -> warp(
                                                    context.getSource()
                                                            .getPlayerOrException(),
                                                    StringArgumentType.getString(
                                                            context,
                                                            "name"
                                                    )
                                            )))
                    );

                    dispatcher.register(
                            Commands.literal("setwarp")
                                    .requires(source -> source.hasPermission(4))
                                    .then(Commands.argument(
                                                    "name",
                                                    StringArgumentType.word()
                                            )
                                            .executes(context -> setWarp(
                                                    context.getSource()
                                                            .getPlayerOrException(),
                                                    StringArgumentType.getString(
                                                            context,
                                                            "name"
                                                    )
                                            )))
                    );

                    dispatcher.register(
                            Commands.literal("delwarp")
                                    .requires(source -> source.hasPermission(4))
                                    .then(Commands.argument(
                                                    "name",
                                                    StringArgumentType.word()
                                            )
                                            .suggests(WARP_SUGGESTIONS)
                                            .executes(context -> deleteWarp(
                                                    context.getSource(),
                                                    StringArgumentType.getString(
                                                            context,
                                                            "name"
                                                    )
                                            )))
                    );
                }
        );
    }

    private static int spawn(
            ServerPlayer player
    ) {

        if (
                !ServerLocationManager.teleportToSpawn(
                        player
                )
        ) {
            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§aWarped to spawn."
                )
        );

        return 1;
    }

    private static int setSpawn(
            ServerPlayer player
    ) {

        ServerLocationManager.setSpawn(
                player
        );

        player.sendSystemMessage(
                Component.literal(
                        "§aServer spawn set to your current location."
                )
        );

        return 1;
    }

    private static int warp(
            ServerPlayer player,
            String name
    ) {

        if (
                !ServerLocationManager.teleportToWarp(
                        player,
                        name
                )
        ) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cUnknown warp: §f" + name
                    )
            );

            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§aWarped to §6" + name + "§a."
                )
        );

        return 1;
    }

    private static int setWarp(
            ServerPlayer player,
            String name
    ) {

        ServerLocationManager.setWarp(
                name,
                player
        );

        player.sendSystemMessage(
                Component.literal(
                        "§aWarp §6" + name + " §aset to your current location."
                )
        );

        return 1;
    }

    private static int deleteWarp(
            CommandSourceStack source,
            String name
    ) {

        if (
                !ServerLocationManager.deleteWarp(
                        name
                )
        ) {
            source.sendFailure(
                    Component.literal(
                            "§cUnknown warp: §f" + name
                    )
            );

            return 0;
        }

        source.sendSuccess(
                () -> Component.literal(
                        "§aDeleted warp §6" + name + "§a."
                ),
                true
        );

        return 1;
    }
}
