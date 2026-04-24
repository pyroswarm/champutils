package com.champutils.commands;

import com.champutils.menu.MainMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static net.minecraft.commands.Commands.literal;

public class MenuCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            literal("menu")
                                    .executes(ctx -> {

                                        MainMenu.open(
                                                ctx.getSource()
                                                        .getPlayerOrException()
                                        );

                                        return 1;
                                    })
                    );

                }
        );
    }
}