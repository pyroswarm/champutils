package com.champutils.commands;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class GiveChampItemCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (
                        dispatcher,
                        registryAccess,
                        environment
                ) -> {

                    dispatcher.register(
                            Commands.literal(
                                            "givechampitem"
                                    )
                                    .requires(source ->
                                            source.hasPermission(2)
                                    )

                                    .then(
                                            Commands.argument(
                                                            "itemId",
                                                            StringArgumentType.word()
                                                    )

                                                    /*
                                                     Tab autocomplete
                                                     */
                                                    .suggests(
                                                            (
                                                                    context,
                                                                    builder
                                                            ) -> {

                                                                for (
                                                                        String toolId :
                                                                        ProfessionToolConfig.TOOLS.keySet()
                                                                ) {
                                                                    builder.suggest(
                                                                            toolId
                                                                    );
                                                                }

                                                                return builder.buildFuture();
                                                            }
                                                    )

                                                    .executes(context -> {

                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        String itemId =
                                                                StringArgumentType.getString(
                                                                        context,
                                                                        "itemId"
                                                                );

                                                        ItemStack item =
                                                                ProfessionToolManager.createTool(
                                                                        itemId
                                                                );

                                                        if (
                                                                item.isEmpty()
                                                        ) {
                                                            context.getSource()
                                                                    .sendFailure(
                                                                            Component.literal(
                                                                                    "Invalid custom item: " +
                                                                                            itemId
                                                                            )
                                                                    );

                                                            return 0;
                                                        }

                                                        boolean added =
                                                                player.getInventory()
                                                                        .add(
                                                                                item
                                                                        );

                                                        if (!added) {
                                                            player.drop(
                                                                    item,
                                                                    false
                                                            );
                                                        }

                                                        context.getSource()
                                                                .sendSuccess(
                                                                        () ->
                                                                                Component.literal(
                                                                                        "Given custom item: " +
                                                                                                itemId
                                                                                ),
                                                                        false
                                                                );

                                                        return 1;
                                                    })
                                    )
                    );
                }
        );
    }
}