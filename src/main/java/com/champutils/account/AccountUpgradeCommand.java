package com.champutils.account;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AccountUpgradeCommand {
    private AccountUpgradeCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("champsshop")
                    .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("boosters")
                            .executes(ctx -> openBoosters(ctx.getSource().getPlayerOrException())))
                    .then(Commands.literal("trails")
                            .executes(ctx -> openTrails(ctx.getSource().getPlayerOrException()))));

            dispatcher.register(Commands.literal("accountupgrade")
                    .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("buy")
                            .then(Commands.argument("tier", StringArgumentType.word())
                                    .executes(ctx -> rankDisabled(ctx.getSource().getPlayerOrException())))));
        });
    }

    private static int open(ServerPlayer player) {
        AccountUpgradeMenu.open(player);
        return 1;
    }

    private static int openBoosters(ServerPlayer player) {
        AccountUpgradeMenu.openBoosters(player);
        return 1;
    }

    private static int openTrails(ServerPlayer player) {
        AccountUpgradeMenu.openTrails(player, 0);
        return 1;
    }

    private static int rankDisabled(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("VIP and VIP+ are Tebex-only so upgrade pricing and duplicate prevention stay correct. Use /champsshop for booster credits and cosmetics."));
        return 0;
    }
}
