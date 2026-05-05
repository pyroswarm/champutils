package com.champutils.commands;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ProfessionAdminCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            Commands.literal("professionlevel")
                                    .requires(source ->
                                            source.hasPermission(4)
                                    )

                                    .then(
                                            Commands.literal("set")
                                                    .then(
                                                            Commands.argument(
                                                                            "target",
                                                                            EntityArgument.player()
                                                                    )
                                                                    .then(
                                                                            Commands.argument(
                                                                                            "profession",
                                                                                            StringArgumentType.word()
                                                                                    )
                                                                                    .suggests(
                                                                                            (context, builder) -> {

                                                                                                for (
                                                                                                        ProfessionType type :
                                                                                                        ProfessionType.values()
                                                                                                ) {
                                                                                                    builder.suggest(
                                                                                                            type.name()
                                                                                                                    .toLowerCase()
                                                                                                    );
                                                                                                }

                                                                                                return builder.buildFuture();
                                                                                            }
                                                                                    )
                                                                                    .then(
                                                                                            Commands.argument(
                                                                                                            "level",
                                                                                                            IntegerArgumentType.integer(1)
                                                                                                    )
                                                                                                    .executes(context -> {

                                                                                                        ServerPlayer target =
                                                                                                                EntityArgument.getPlayer(
                                                                                                                        context,
                                                                                                                        "target"
                                                                                                                );

                                                                                                        ProfessionType type =
                                                                                                                getProfession(
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "profession"
                                                                                                                        )
                                                                                                                );

                                                                                                        if (type == null) {
                                                                                                            context.getSource()
                                                                                                                    .sendFailure(
                                                                                                                            Component.literal(
                                                                                                                                    "Invalid profession."
                                                                                                                            )
                                                                                                                    );

                                                                                                            return 0;
                                                                                                        }

                                                                                                        int level =
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "level"
                                                                                                                );

                                                                                                        setLevel(
                                                                                                                target,
                                                                                                                type,
                                                                                                                level
                                                                                                        );

                                                                                                        context.getSource()
                                                                                                                .sendSuccess(
                                                                                                                        () -> Component.literal(
                                                                                                                                "Set " +
                                                                                                                                        target.getName().getString() +
                                                                                                                                        "'s " +
                                                                                                                                        type.name() +
                                                                                                                                        " level to " +
                                                                                                                                        level
                                                                                                                        ),
                                                                                                                        true
                                                                                                                );

                                                                                                        return 1;
                                                                                                    })
                                                                                    )
                                                                    )
                                                    )
                                    )

                                    .then(
                                            Commands.literal("add")
                                                    .then(
                                                            Commands.argument(
                                                                            "target",
                                                                            EntityArgument.player()
                                                                    )
                                                                    .then(
                                                                            Commands.argument(
                                                                                            "profession",
                                                                                            StringArgumentType.word()
                                                                                    )
                                                                                    .suggests(
                                                                                            (context, builder) -> {

                                                                                                for (
                                                                                                        ProfessionType type :
                                                                                                        ProfessionType.values()
                                                                                                ) {
                                                                                                    builder.suggest(
                                                                                                            type.name()
                                                                                                                    .toLowerCase()
                                                                                                    );
                                                                                                }

                                                                                                return builder.buildFuture();
                                                                                            }
                                                                                    )
                                                                                    .then(
                                                                                            Commands.argument(
                                                                                                            "amount",
                                                                                                            IntegerArgumentType.integer(1)
                                                                                                    )
                                                                                                    .executes(context -> {

                                                                                                        ServerPlayer target =
                                                                                                                EntityArgument.getPlayer(
                                                                                                                        context,
                                                                                                                        "target"
                                                                                                                );

                                                                                                        ProfessionType type =
                                                                                                                getProfession(
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "profession"
                                                                                                                        )
                                                                                                                );

                                                                                                        if (type == null) {
                                                                                                            context.getSource()
                                                                                                                    .sendFailure(
                                                                                                                            Component.literal(
                                                                                                                                    "Invalid profession."
                                                                                                                            )
                                                                                                                    );

                                                                                                            return 0;
                                                                                                        }

                                                                                                        int amount =
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "amount"
                                                                                                                );

                                                                                                        int current =
                                                                                                                ProfessionManager.getLevel(
                                                                                                                        target,
                                                                                                                        type
                                                                                                                );

                                                                                                        int newLevel =
                                                                                                                current + amount;

                                                                                                        setLevel(
                                                                                                                target,
                                                                                                                type,
                                                                                                                newLevel
                                                                                                        );

                                                                                                        context.getSource()
                                                                                                                .sendSuccess(
                                                                                                                        () -> Component.literal(
                                                                                                                                "Added " +
                                                                                                                                        amount +
                                                                                                                                        " " +
                                                                                                                                        type.name() +
                                                                                                                                        " levels to " +
                                                                                                                                        target.getName().getString() +
                                                                                                                                        ". New level: " +
                                                                                                                                        newLevel
                                                                                                                        ),
                                                                                                                        true
                                                                                                                );

                                                                                                        return 1;
                                                                                                    })
                                                                                    )
                                                                    )
                                                    )
                                    )

                                    .then(
                                            Commands.literal("remove")
                                                    .then(
                                                            Commands.argument(
                                                                            "target",
                                                                            EntityArgument.player()
                                                                    )
                                                                    .then(
                                                                            Commands.argument(
                                                                                            "profession",
                                                                                            StringArgumentType.word()
                                                                                    )
                                                                                    .suggests(
                                                                                            (context, builder) -> {

                                                                                                for (
                                                                                                        ProfessionType type :
                                                                                                        ProfessionType.values()
                                                                                                ) {
                                                                                                    builder.suggest(
                                                                                                            type.name()
                                                                                                                    .toLowerCase()
                                                                                                    );
                                                                                                }

                                                                                                return builder.buildFuture();
                                                                                            }
                                                                                    )
                                                                                    .then(
                                                                                            Commands.argument(
                                                                                                            "amount",
                                                                                                            IntegerArgumentType.integer(1)
                                                                                                    )
                                                                                                    .executes(context -> {

                                                                                                        ServerPlayer target =
                                                                                                                EntityArgument.getPlayer(
                                                                                                                        context,
                                                                                                                        "target"
                                                                                                                );

                                                                                                        ProfessionType type =
                                                                                                                getProfession(
                                                                                                                        StringArgumentType.getString(
                                                                                                                                context,
                                                                                                                                "profession"
                                                                                                                        )
                                                                                                                );

                                                                                                        if (type == null) {
                                                                                                            context.getSource()
                                                                                                                    .sendFailure(
                                                                                                                            Component.literal(
                                                                                                                                    "Invalid profession."
                                                                                                                            )
                                                                                                                    );

                                                                                                            return 0;
                                                                                                        }

                                                                                                        int amount =
                                                                                                                IntegerArgumentType.getInteger(
                                                                                                                        context,
                                                                                                                        "amount"
                                                                                                                );

                                                                                                        int current =
                                                                                                                ProfessionManager.getLevel(
                                                                                                                        target,
                                                                                                                        type
                                                                                                                );

                                                                                                        int newLevel =
                                                                                                                Math.max(
                                                                                                                        1,
                                                                                                                        current - amount
                                                                                                                );

                                                                                                        setLevel(
                                                                                                                target,
                                                                                                                type,
                                                                                                                newLevel
                                                                                                        );

                                                                                                        context.getSource()
                                                                                                                .sendSuccess(
                                                                                                                        () -> Component.literal(
                                                                                                                                "Removed " +
                                                                                                                                        amount +
                                                                                                                                        " " +
                                                                                                                                        type.name() +
                                                                                                                                        " levels from " +
                                                                                                                                        target.getName().getString() +
                                                                                                                                        ". New level: " +
                                                                                                                                        newLevel
                                                                                                                        ),
                                                                                                                        true
                                                                                                                );

                                                                                                        return 1;
                                                                                                    })
                                                                                    )
                                                                    )
                                                    )
                                    )
                    );
                }
        );
    }

    private static ProfessionType getProfession(
            String input
    ) {

        if (input == null || input.isBlank()) {
            return null;
        }

        try {
            return ProfessionType.valueOf(
                    input.toUpperCase()
            );
        }
        catch (Exception e) {
            return null;
        }
    }

    private static void setLevel(
            ServerPlayer player,
            ProfessionType type,
            int level
    ) {

        var data =
                ProfessionManager.getData(
                        player
                );

        String key =
                type.name();

        data.levels.put(
                key,
                Math.max(
                        1,
                        level
                )
        );

        data.xp.put(
                key,
                0
        );

        ProfessionDataManager.save(
                player.getUUID(),
                data
        );
    }
}