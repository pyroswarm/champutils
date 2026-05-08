package com.champutils.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShowItemCommand {

    private static final long COOLDOWN_MILLIS = 5000L;

    private static final Map<UUID, Long> LAST_USED =
            new HashMap<>();

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            Commands.literal("showitem")
                                    .executes(context -> {

                                        ServerPlayer player =
                                                context.getSource()
                                                        .getPlayerOrException();

                                        return showHeldItem(
                                                player
                                        );
                                    })
                    );
                }
        );
    }

    private static int showHeldItem(
            ServerPlayer player
    ) {

        long now =
                System.currentTimeMillis();

        long lastUsed =
                LAST_USED.getOrDefault(
                        player.getUUID(),
                        0L
                );

        long remaining =
                COOLDOWN_MILLIS -
                        (now - lastUsed);

        if (remaining > 0) {
            long seconds =
                    (long) Math.ceil(
                            remaining / 1000.0D
                    );

            player.sendSystemMessage(
                    Component.literal(
                            "§cYou can use /showitem again in " +
                                    seconds +
                                    "s."
                    )
            );

            return 0;
        }

        ItemStack held =
                player.getMainHandItem();

        if (
                held == null ||
                        held.isEmpty()
        ) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cHold an item first."
                    )
            );

            return 0;
        }

        ItemStack shownStack =
                held.copy();

        Component itemName =
                shownStack.getHoverName()
                        .copy()
                        .withStyle(style -> style.withHoverEvent(
                                new HoverEvent(
                                        HoverEvent.Action.SHOW_ITEM,
                                        new HoverEvent.ItemStackInfo(
                                                shownStack
                                        )
                                )
                        ));

        Component message =
                Component.literal("§7[")
                        .append(
                                Component.literal(
                                        player.getGameProfile()
                                                .getName()
                                ).withStyle(
                                        net.minecraft.ChatFormatting.GRAY
                                )
                        )
                        .append(
                                Component.literal(
                                        " is showing: "
                                ).withStyle(
                                        net.minecraft.ChatFormatting.GRAY
                                )
                        )
                        .append(
                                itemName
                        )
                        .append(
                                Component.literal(
                                        "§7]"
                                )
                        );

        for (
                ServerPlayer target :
                player.server
                        .getPlayerList()
                        .getPlayers()
        ) {
            target.sendSystemMessage(
                    message
            );
        }

        LAST_USED.put(
                player.getUUID(),
                now
        );

        return 1;
    }
}
