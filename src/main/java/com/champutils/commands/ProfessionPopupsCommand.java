package com.champutils.commands;

import com.champutils.profession.ProfessionNotificationSettings;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
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
                    dispatcher.register(root("professionpopups"));
                    dispatcher.register(root("professionpopup"));
                    dispatcher.register(root("profpops"));
                    dispatcher.register(root("popups"));
                }
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(
            String name
    ) {
        return Commands.literal(name)
                .executes(context -> showStatus(
                        context.getSource().getPlayerOrException()
                ))
                .then(
                        Commands.literal("on")
                                .executes(context -> set(
                                        context.getSource().getPlayerOrException(),
                                        true
                                ))
                )
                .then(
                        Commands.literal("off")
                                .executes(context -> set(
                                        context.getSource().getPlayerOrException(),
                                        false
                                ))
                )
                .then(
                        Commands.literal("toggle")
                                .executes(context -> toggle(
                                        context.getSource().getPlayerOrException()
                                ))
                )
                .then(
                        Commands.literal("status")
                                .executes(context -> showStatus(
                                        context.getSource().getPlayerOrException()
                                ))
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
                                ? "§aProfession popups are ON. Use §f/professionpopup off§a to disable them."
                                : "§cProfession popups are OFF. Use §f/professionpopup on§c to enable them."
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
