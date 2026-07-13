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
                            .executes(ctx -> openTrails(ctx.getSource().getPlayerOrException())))
                    .then(Commands.literal("ranks")
                            .executes(ctx -> openRanks(ctx.getSource().getPlayerOrException()))));

            dispatcher.register(Commands.literal("accountupgrade")
                    .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("buy")
                            .then(Commands.argument("tier", StringArgumentType.word())
                                    .executes(ctx -> buyRank(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "tier"))))));
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

    private static int openRanks(ServerPlayer player) { AccountUpgradeMenu.openRanks(player); return 1; }

    private static int buyRank(ServerPlayer player, String raw) {
        String tier = raw == null ? "" : raw.toLowerCase().replace("+", "plus");
        AccountUpgradeManager.Tier selected = tier.equals("vip") ? AccountUpgradeManager.Tier.VIP : (tier.equals("vipplus") || tier.equals("vip_plus") ? AccountUpgradeManager.Tier.VIP_PLUS : null);
        if (selected == null) { player.sendSystemMessage(Component.literal("Use vip or vipplus.")); return 0; }
        AccountUpgradeMenu.openRanks(player);
        player.sendSystemMessage(Component.literal("Select the rank in the Champs Shop menu to review and confirm the purchase."));
        return 1;
    }
}
