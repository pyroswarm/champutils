package com.champutils.commands;

import com.champutils.profession.ProfessionToolMigrationService;
import com.champutils.menu.ConfirmationMenu;
import com.mojang.brigadier.Command;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionToolsCommand {

    private static final long SELF_MIGRATION_COOLDOWN_MS = 60L * 60L * 1000L;
    private static final Map<UUID, Long> SELF_MIGRATION_COOLDOWNS = new ConcurrentHashMap<>();

    private ProfessionToolsCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                Commands.literal("migratecobble")
                        .executes(context -> migrateSelf(context.getSource().getPlayerOrException(), context.getSource().hasPermission(4)))
            );

            dispatcher.register(
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
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    ConfirmationMenu.open(
                                            player,
                                            "Confirm Tool Migration",
                                            Items.ANVIL,
                                            "§eProfession Tool Migration",
                                            new String[]{
                                                    "§7Updates profession tools, swords, trinkets, and pouches.",
                                                    "§7Includes online players, ender chests, and loaded dropped items.",
                                                    "§cIt does not run on reboot."
                                            },
                                            () -> {
                                                ProfessionToolMigrationService.MigrationReport report =
                                                        ProfessionToolMigrationService.migrateOnlineAndLoaded(player.server);
                                                player.sendSystemMessage(report.toComponent("Profession Tool Migration Complete"));
                                            },
                                            () -> player.sendSystemMessage(Component.literal("§eProfession tool migration cancelled."))
                                    );
                                    return Command.SINGLE_SUCCESS;
                                })
                                .then(Commands.literal("confirm")
                                        .executes(context -> {
                                            ProfessionToolMigrationService.MigrationReport report =
                                                    ProfessionToolMigrationService.migrateOnlineAndLoaded(context.getSource().getServer());
                                            context.getSource().sendSuccess(() -> report.toComponent("Profession Tool Migration Complete"), true);
                                            return Command.SINGLE_SUCCESS;
                                        })))
        );
        });
    }

    private static int migrateSelf(ServerPlayer player, boolean bypassCooldown) {
        long now = System.currentTimeMillis();
        Long last = SELF_MIGRATION_COOLDOWNS.get(player.getUUID());
        if (!bypassCooldown && last != null && now - last < SELF_MIGRATION_COOLDOWN_MS) {
            long remainingSeconds = (SELF_MIGRATION_COOLDOWN_MS - (now - last) + 999L) / 1000L;
            player.sendSystemMessage(Component.literal("§cYou can use /migratecobble again in " + (remainingSeconds / 60L) + "m " + (remainingSeconds % 60L) + "s."));
            return 0;
        }
        ProfessionToolMigrationService.MigrationReport report = ProfessionToolMigrationService.migratePlayerInventory(player);
        SELF_MIGRATION_COOLDOWNS.put(player.getUUID(), now);
        player.sendSystemMessage(report.toComponent("Your Cobble Gear Migration Complete"));
        return Command.SINGLE_SUCCESS;
    }
}
