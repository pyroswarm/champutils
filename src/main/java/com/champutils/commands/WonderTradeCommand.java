package com.champutils.commands;

import com.champutils.profile.ProfileRestrictions;
import com.champutils.wondertrade.WonderTradeSeeder;
import com.champutils.wondertrade.WonderTradeService;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class WonderTradeCommand {

    private WonderTradeCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("wondertrade")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Visit the Wonder Trade NPC at spawn to trade a Pokémon.").withStyle(ChatFormatting.AQUA), false);
                            context.getSource().sendSuccess(() -> Component.literal("Use the Wonder Trade NPC claim button if a trade was interrupted.").withStyle(ChatFormatting.GRAY), false);
                            context.getSource().sendSuccess(() -> Component.literal("Admins may still use /wondertrade <slot> for testing.").withStyle(ChatFormatting.GRAY), false);
                            return 1;
                        })
                        .then(literal("claim")
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    if (ProfileRestrictions.blockIronmanTrade(player, "Wonder Trade")) return 0;
                                    WonderTradeService.claimPending(player);
                                    return 1;
                                }))
                        .then(literal("cooldown")
                                .executes(context -> {
                                    WonderTradeService.sendCooldown(context.getSource().getPlayerOrException());
                                    return 1;
                                })
                                .then(argument("minutes", IntegerArgumentType.integer(0, 10080))
                                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                                        .executes(context -> {
                                            WonderTradeService.setCooldown(
                                                    context.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(context, "minutes")
                                            );
                                            return 1;
                                        })))
                        .then(literal("status")
                                .executes(context -> {
                                    WonderTradeService.sendStatus(context.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(literal("seed")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                                .executes(context -> {
                                    WonderTradeSeeder.seedIfNeeded(context.getSource().getPlayerOrException(), true);
                                    return 1;
                                }))
                        .then(literal("inject")
                                .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                                .then(argument("amount", IntegerArgumentType.integer(1, 1000))
                                        .executes(context -> {
                                            WonderTradeSeeder.inject(
                                                    context.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(context, "amount"),
                                                    true
                                            );
                                            return 1;
                                        })))
                        .then(argument("slot", IntegerArgumentType.integer(1, 6))
                                .executes(context -> {
                                    var player = context.getSource().getPlayerOrException();
                                    if (ProfileRestrictions.blockIronmanTrade(player, "Wonder Trade")) return 0;
                                    WonderTradeService.trade(
                                            player,
                                            IntegerArgumentType.getInteger(context, "slot")
                                    );
                                    return 1;
                                }))
        ));
    }
}
