package com.champutils.commands;

import com.champutils.dailylogin.DailyLoginMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static net.minecraft.commands.Commands.literal;

public final class DailyLoginCommand {
    private DailyLoginCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("dailylogin").executes(ctx -> {
                DailyLoginMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(literal("daily").executes(ctx -> {
                DailyLoginMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
        });
    }
}
