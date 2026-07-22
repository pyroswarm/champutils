package com.champutils.commands;

import com.champutils.rank.RankedTokenManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RankedTokenCommand {
    private RankedTokenCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("rankedtokens")
                        .requires(source -> source.hasPermission(4))
                        .then(literal("balance")
                                .then(argument("player", EntityArgument.player())
                                        .executes(context -> balance(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(literal("give")
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> give(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))))
                        .then(literal("take")
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(context -> take(context.getSource(), EntityArgument.getPlayer(context, "player"), IntegerArgumentType.getInteger(context, "amount"))))))
        ));
    }

    private static int balance(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal("§d" + player.getName().getString() + " has " + RankedTokenManager.cachedBalance(player) + " Ranked Tokens."), false);
        return 1;
    }

    private static int give(CommandSourceStack source, ServerPlayer player, int amount) {
        RankedTokenManager.grant(player, amount, "Granted by staff");
        source.sendSuccess(() -> Component.literal("§aGave " + amount + " Ranked Tokens to " + player.getName().getString() + "."), true);
        return 1;
    }

    private static int take(CommandSourceStack source, ServerPlayer player, int amount) {
        RankedTokenManager.take(player, amount, "Removed by staff");
        source.sendSuccess(() -> Component.literal("§aRemoved up to " + amount + " Ranked Tokens from " + player.getName().getString() + "."), true);
        return 1;
    }
}
