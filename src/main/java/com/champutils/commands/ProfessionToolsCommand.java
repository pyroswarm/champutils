package com.champutils.commands;

import com.champutils.profession.ProfessionToolMigrationService;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ProfessionToolsCommand {

    private ProfessionToolsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("professiontools")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("audit")
                                .executes(context -> {
                                    ProfessionToolMigrationService.MigrationReport report =
                                            ProfessionToolMigrationService.auditOnlineAndLoaded(context.getSource().getServer());
                                    context.getSource().sendSuccess(() -> report.toComponent("Profession Tool Audit"), false);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("updateheld")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ProfessionToolMigrationService.MigrationReport report =
                                            ProfessionToolMigrationService.migrateHeld(player);
                                    context.getSource().sendSuccess(() -> report.toComponent("Held Profession Tool Update"), true);
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .then(Commands.literal("migrate")
                                .executes(context -> {
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "This will update profession tools for online players, their ender chests, and loaded dropped tool items. " +
                                                    "It does not run on reboot. Run /professiontools migrate confirm to start."
                                    ), false);
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("confirm")
                                        .executes(context -> {
                                            ProfessionToolMigrationService.MigrationReport report =
                                                    ProfessionToolMigrationService.migrateOnlineAndLoaded(context.getSource().getServer());
                                            context.getSource().sendSuccess(() -> report.toComponent("Profession Tool Migration Complete"), true);
                                            return Command.SINGLE_SUCCESS;
                                        })))
        ));
    }
}
