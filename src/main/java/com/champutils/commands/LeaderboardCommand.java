package com.champutils.commands;

import com.champutils.menu.LeaderboardMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import static net.minecraft.commands.Commands.literal;

public class LeaderboardCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            literal("leaderboard")
                                    .executes(ctx -> {

                                        LeaderboardMenu.open(
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
