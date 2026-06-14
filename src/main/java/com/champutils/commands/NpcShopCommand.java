package com.champutils.commands;

import com.champutils.menu.NpcShopMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class NpcShopCommand {

    private NpcShopCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("npcshop")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            NpcShopMenu.open(player);
                            return 1;
                        })
        ));
    }
}
