package com.champutils.commands;

import com.champutils.menu.IslanderShopMenu;
import com.champutils.profile.PlayerProfileManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class IslanderShopCommand {
    private IslanderShopCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("islandershop")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!PlayerProfileManager.isIslander(player)) {
                                player.sendSystemMessage(Component.literal("Only Islander profiles can use the Islander Resource Shop.").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            IslanderShopMenu.open(player);
                            return 1;
                        })
        ));
    }
}
