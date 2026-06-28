package com.champutils.commands;

import com.champutils.menu.GenesisShopMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class GenesisShopCommand {

    private GenesisShopCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("genesisshop")
                        .requires(source -> source.hasPermission(2) || com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            GenesisShopMenu.open(player);
                            return 1;
                        })
        ));
    }
}
