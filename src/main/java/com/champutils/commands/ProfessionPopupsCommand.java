package com.champutils.commands;

import com.champutils.profession.ProfessionNotificationSettings;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ProfessionPopupsCommand {

    private ProfessionPopupsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (
                        dispatcher,
                        registryAccess,
                        environment
                ) -> {

                    dispatcher.register(
                            Commands.literal(
                                            "professionpopups"
                                    )
                                    .executes(context -> showStatus(
                                            context.getSource()
                                                    .getPlayerOrException()
                                    ))
                                    .then(
                                            Commands.literal(
                                                            "on"
                                                    )
                                                    .executes(context -> set(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            true
                                                    ))
                                    )
                                    .then(
                                            Commands.literal(
                                                            "off"
                                                    )
                                                    .executes(context -> set(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            false
                                                    ))
                                    )
                                    .then(
                                            Commands.literal(
                                                            "toggle"
                                                    )
                                                    .executes(context -> toggle(
                                                            context.getSource()
                                                                    .getPlayerOrException()
                                                    ))
                                    )
                    );

                    dispatcher.register(
                            Commands.literal(
                                            "profpops"
                                    )
                                    .executes(context -> showStatus(
                                            context.getSource()
                                                    .getPlayerOrException()
                                    ))
                                    .then(
                                            Commands.literal(
                                                            "on"
                                                    )
                                                    .executes(context -> set(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            true
                                                    ))
                                    )
                                    .then(
                                            Commands.literal(
                                                            "off"
                                                    )
                                                    .executes(context -> set(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            false
                                                    ))
                                    )
                                    .then(
                                            Commands.literal(
                                                            "toggle"
                                                    )
                                                    .executes(context -> toggle(
                                                            context.getSource()
                                                                    .getPlayerOrException()
                                                    ))
                                    )
                    );
                }
        );
    }

    private static int showStatus(
            ServerPlayer player
    ) {

        boolean enabled =
                ProfessionNotificationSettings.areProfessionPopupsEnabled(
                        player
                );

        player.sendSystemMessage(
                Component.literal(
                        enabled
                                ? "§aProfession popups are ON. Use §f/professionpopups off§a to disable them."
                                : "§cProfession popups are OFF. Use §f/professionpopups on§c to enable them."
                )
        );

        return 1;
    }

    private static int set(
            ServerPlayer player,
            boolean enabled
    ) {

        ProfessionNotificationSettings.setProfessionPopupsEnabled(
                player,
                enabled
        );

        player.sendSystemMessage(
                Component.literal(
                        enabled
                                ? "§aProfession popups enabled."
                                : "§cProfession popups disabled. Passive rewards still work normally."
                )
        );

        return 1;
    }

    private static int toggle(
            ServerPlayer player
    ) {

        boolean enabled =
                ProfessionNotificationSettings.toggleProfessionPopups(
                        player
                );

        player.sendSystemMessage(
                Component.literal(
                        enabled
                                ? "§aProfession popups enabled."
                                : "§cProfession popups disabled. Passive rewards still work normally."
                )
        );

        return 1;
    }
}
