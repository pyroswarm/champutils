package com.champutils.commands;

import com.champutils.economy.EconomyManager;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class EconomyCommand {

    private EconomyCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    literal("credits")
                            .executes(context -> showBalance(context.getSource(), context.getSource().getPlayerOrException()))
            );

            dispatcher.register(
                    literal("balance")
                            .executes(context -> showBalance(context.getSource(), context.getSource().getPlayerOrException()))
            );

            dispatcher.register(
                    literal("bal")
                            .executes(context -> showBalance(context.getSource(), context.getSource().getPlayerOrException()))
            );

            dispatcher.register(
                    literal("pay")
                            .then(argument("player", EntityArgument.player())
                                    .then(argument("amount", DoubleArgumentType.doubleArg(0.01D, 90_000_000_000_000D))
                                            .executes(context -> pay(
                                                    context.getSource().getPlayerOrException(),
                                                    EntityArgument.getPlayer(context, "player"),
                                                    EconomyManager.creditsToCents(DoubleArgumentType.getDouble(context, "amount"))
                                            ))))
            );

            dispatcher.register(
                    literal("eco")
                            .requires(source -> source.hasPermission(4))
                            .then(literal("balance")
                                    .then(argument("player", EntityArgument.player())
                                            .executes(context -> showBalance(
                                                    context.getSource(),
                                                    EntityArgument.getPlayer(context, "player")
                                            ))))
                            .then(literal("give")
                                    .then(argument("player", EntityArgument.player())
                                            .then(argument("amount", DoubleArgumentType.doubleArg(0.01D, 90_000_000_000_000D))
                                                    .executes(context -> adminGive(
                                                            context.getSource(),
                                                            EntityArgument.getPlayer(context, "player"),
                                                            EconomyManager.creditsToCents(DoubleArgumentType.getDouble(context, "amount"))
                                                    )))))
                            .then(literal("take")
                                    .then(argument("player", EntityArgument.player())
                                            .then(argument("amount", DoubleArgumentType.doubleArg(0.01D, 90_000_000_000_000D))
                                                    .executes(context -> adminTake(
                                                            context.getSource(),
                                                            EntityArgument.getPlayer(context, "player"),
                                                            EconomyManager.creditsToCents(DoubleArgumentType.getDouble(context, "amount"))
                                                    )))))
                            .then(literal("set")
                                    .then(argument("player", EntityArgument.player())
                                            .then(argument("amount", DoubleArgumentType.doubleArg(0.0D, 90_000_000_000_000D))
                                                    .executes(context -> adminSet(
                                                            context.getSource(),
                                                            EntityArgument.getPlayer(context, "player"),
                                                            EconomyManager.creditsToCents(DoubleArgumentType.getDouble(context, "amount"))
                                                    )))))
                            .then(literal("reset")
                                    .then(argument("player", EntityArgument.player())
                                            .executes(context -> adminSet(
                                                    context.getSource(),
                                                    EntityArgument.getPlayer(context, "player"),
                                                    0L
                                            ))))
            );
        });
    }

    private static int showBalance(CommandSourceStack source, ServerPlayer player) {
        long balance = EconomyManager.getBalance(player);
        source.sendSuccess(
                () -> Component.literal("§6" + player.getName().getString() + " has " + EconomyManager.format(balance) + "."),
                false
        );
        return 1;
    }

    private static int pay(ServerPlayer from, ServerPlayer to, long amount) {
        EconomyManager.transferAsync(from, to, amount, "player_pay").thenAccept(result ->
                from.server.execute(() -> {
                    if (!result.success) {
                        from.sendSystemMessage(Component.literal("§c" + result.error));
                        return;
                    }
                    from.sendSystemMessage(Component.literal("§aPaid §6" + EconomyManager.format(amount) + " §ato " + to.getName().getString() + "."));
                    from.sendSystemMessage(Component.literal("§7New Balance: §6" + EconomyManager.format(result.newBalance)));
                    to.sendSystemMessage(Component.literal("§aYou received §6" + EconomyManager.format(amount) + " §afrom " + from.getName().getString() + "."));
                }));
        return 1;
    }

    private static int adminGive(CommandSourceStack source, ServerPlayer player, long amount) {
        EconomyManager.depositAsync(player, amount, "admin_give").thenAccept(result -> player.server.execute(() -> {
            if (!result.success) source.sendFailure(Component.literal(result.error));
            else source.sendSuccess(() -> Component.literal("Gave " + EconomyManager.format(amount) + " to " + player.getName().getString() + ". New Balance: " + EconomyManager.format(result.newBalance)), true);
        }));
        return 1;
    }

    private static int adminTake(CommandSourceStack source, ServerPlayer player, long amount) {
        EconomyManager.withdrawAsync(player, amount, "admin_take").thenAccept(result -> player.server.execute(() -> {
            if (!result.success) source.sendFailure(Component.literal(result.error));
            else source.sendSuccess(() -> Component.literal("Took " + EconomyManager.format(amount) + " from " + player.getName().getString() + ". New Balance: " + EconomyManager.format(result.newBalance)), true);
        }));
        return 1;
    }

    private static int adminSet(CommandSourceStack source, ServerPlayer player, long amount) {
        EconomyManager.setBalanceAsync(player, amount, "admin_set").thenAccept(result -> player.server.execute(() -> {
            if (!result.success) source.sendFailure(Component.literal(result.error));
            else source.sendSuccess(() -> Component.literal("Set " + player.getName().getString() + " to " + EconomyManager.format(result.newBalance) + "."), true);
        }));
        return 1;
    }

}
