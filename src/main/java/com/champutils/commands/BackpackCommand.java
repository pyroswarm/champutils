package com.champutils.commands;

import com.champutils.menu.ProfessionBackpackMenu;
import com.champutils.profession.ProfessionBackpackManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class BackpackCommand {
    private BackpackCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("backpack")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ProfessionBackpackMenu.open(player);
                            return 1;
                        })
                        .then(Commands.literal("toggle")
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    boolean enabled = ProfessionBackpackManager.toggleAutopickup(player);
                                    player.sendSystemMessage(Component.literal(enabled
                                            ? "§aProfession backpack autopickup enabled."
                                            : "§cProfession backpack autopickup disabled."));
                                    return 1;
                                }))
                        .then(Commands.literal("search")
                                .then(Commands.argument("item", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            ServerPlayer player = context.getSource().getPlayerOrException();
                                            ProfessionBackpackMenu.open(player, StringArgumentType.getString(context, "item"));
                                            return 1;
                                        })))
        ));
    }
}
