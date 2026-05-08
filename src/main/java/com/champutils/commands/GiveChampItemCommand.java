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

                                                    .executes(context -> giveItem(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            StringArgumentType.getString(
                                                                    context,
                                                                    "itemId"
                                                            ),
                                                            false
                                                    ))

                                                    .then(
                                                            Commands.literal(
                                                                            "ascended"
                                                                    )
                                                                    .executes(context -> giveItem(
                                                                            context.getSource()
                                                                                    .getPlayerOrException(),
                                                                            StringArgumentType.getString(
                                                                                    context,
                                                                                    "itemId"
                                                                            ),
                                                                            true
                                                                    ))
                                                    )
                                    )
                    );
                }
        );
    }

    private static int giveItem(
            ServerPlayer player,
            String itemId,
            boolean ascended
    ) {

        ItemStack item =
                ProfessionToolManager.createTool(
                        itemId,
                        ascended
                );

        if (
                item.isEmpty()
        ) {
            player.sendSystemMessage(
                    Component.literal(
                            ascended
                                    ? "§cInvalid custom item or ascended variant is not enabled: " + itemId
                                    : "§cInvalid custom item: " + itemId
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

        player.sendSystemMessage(
                Component.literal(
                        ascended
                                ? "§dGiven ascended custom item: " + itemId
                                : "§aGiven custom item: " + itemId
                )
        );

        return 1;
    }
}
