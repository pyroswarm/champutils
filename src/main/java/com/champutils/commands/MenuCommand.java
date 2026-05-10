package com.champutils.commands;

import com.champutils.menu.MainMenu;
import com.champutils.menu.ProfileMenu;
import com.champutils.menu.PlayerProfileMenu;
import com.champutils.menu.ItemsMenu;
import com.champutils.menu.ProfessionLeaderboardMenu;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import com.mojang.brigadier.arguments.StringArgumentType;

import static net.minecraft.commands.Commands.literal;
import static net.minecraft.commands.Commands.argument;

public class MenuCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment)->{

                    dispatcher.register(

                            literal("menu")

                                    .executes(ctx->{

                                        MainMenu.open(
                                                ctx.getSource()
                                                        .getPlayerOrException()
                                        );

                                        return 1;
                                    })

                    );




                    dispatcher.register(

                            literal("items")

                                    .executes(ctx->{

                                        ItemsMenu.open(
                                                ctx.getSource()
                                                        .getPlayerOrException()
                                        );

                                        return 1;
                                    })

                    );



                    dispatcher.register(

                            literal("professionleaderboard")

                                    .executes(ctx->{

                                        ProfessionLeaderboardMenu.open(
                                                ctx.getSource()
                                                        .getPlayerOrException()
                                        );

                                        return 1;
                                    })

                    );


                    dispatcher.register(

                            literal("profile")

                                    // /profile = your own card
                                    .executes(ctx->{

                                        ProfileMenu.open(
                                                ctx.getSource()
                                                        .getPlayerOrException()
                                        );

                                        return 1;
                                    })


                                    // /profile Andrew
                                    .then(
                                            argument(
                                                    "player",
                                                    StringArgumentType.greedyString()
                                            )

                                                    .executes(ctx->{

                                                        PlayerProfileMenu.open(

                                                                ctx.getSource()
                                                                        .getPlayerOrException(),

                                                                StringArgumentType.getString(
                                                                        ctx,
                                                                        "player"
                                                                )
                                                        );

                                                        return 1;
                                                    })
                                    )

                    );

                }
        );
    }

}