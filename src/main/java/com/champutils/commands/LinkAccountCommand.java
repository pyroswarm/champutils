package com.champutils.commands;

import com.champutils.database.AccountLinkDatabaseRepository;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class LinkAccountCommand {

    private LinkAccountCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(
                        Commands.literal("linkaccount")
                                .then(Commands.argument("code", StringArgumentType.word())
                                        .executes(context -> run(
                                                context.getSource().getPlayerOrException(),
                                                StringArgumentType.getString(context, "code")
                                        )))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    player.sendSystemMessage(Component.literal("§cUsage: /linkaccount CC-12345"));
                                    return 0;
                                })
                )
        );
    }

    private static int run(ServerPlayer player, String code) {
        AccountLinkDatabaseRepository.linkAsync(player, code);
        player.sendSystemMessage(Component.literal("§7Checking link code..."));
        return 1;
    }
}
