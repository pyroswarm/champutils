package com.champutils.commands;

import com.champutils.menu.ChampCraftingMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ChampCraftingCommand {
    private ChampCraftingCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("champcraft").executes(context -> open(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("crafting").executes(context -> open(context.getSource().getPlayerOrException())));
        });
    }

    private static int open(ServerPlayer player) {
        ChampCraftingMenu.open(player);
        return 1;
    }
}
