package com.champutils.commands;

import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.ProfileManager;
import com.champutils.rank.RankManager;

import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class RpAdminCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher,registryAccess,environment) -> {

                    dispatcher.register(
                            literal("setrp")
                                    .requires(src -> src.hasPermission(4))
                                    .then(
                                            argument(
                                                    "target",
                                                    EntityArgument.player()
                                            )
                                                    .then(
                                                            argument(
                                                                    "amount",
                                                                    IntegerArgumentType.integer(0)
                                                            )
                                                                    .executes(ctx -> {

                                                                        ServerPlayer p=
                                                                                EntityArgument.getPlayer(
                                                                                        ctx,
                                                                                        "target"
                                                                                );

                                                                        int amount=
                                                                                IntegerArgumentType.getInteger(
                                                                                        ctx,
                                                                                        "amount"
                                                                                );

                                                                        setRp(
                                                                                p,
                                                                                amount
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                () -> Component.literal(
                                                                                        "Set RP to "+amount
                                                                                ),
                                                                                true
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )
                    );


                    dispatcher.register(
                            literal("addrp")
                                    .requires(src -> src.hasPermission(4))
                                    .then(
                                            argument(
                                                    "target",
                                                    EntityArgument.player()
                                            )
                                                    .then(
                                                            argument(
                                                                    "amount",
                                                                    IntegerArgumentType.integer(1)
                                                            )
                                                                    .executes(ctx -> {

                                                                        ServerPlayer p=
                                                                                EntityArgument.getPlayer(
                                                                                        ctx,
                                                                                        "target"
                                                                                );

                                                                        int amt=
                                                                                IntegerArgumentType.getInteger(
                                                                                        ctx,
                                                                                        "amount"
                                                                                );

                                                                        setRp(
                                                                                p,
                                                                                currentRp(p)+amt
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )
                    );


                    dispatcher.register(
                            literal("removerp")
                                    .requires(src -> src.hasPermission(4))
                                    .then(
                                            argument(
                                                    "target",
                                                    EntityArgument.player()
                                            )
                                                    .then(
                                                            argument(
                                                                    "amount",
                                                                    IntegerArgumentType.integer(1)
                                                            )
                                                                    .executes(ctx -> {

                                                                        ServerPlayer p=
                                                                                EntityArgument.getPlayer(
                                                                                        ctx,
                                                                                        "target"
                                                                                );

                                                                        int amt=
                                                                                IntegerArgumentType.getInteger(
                                                                                        ctx,
                                                                                        "amount"
                                                                                );

                                                                        setRp(
                                                                                p,
                                                                                Math.max(
                                                                                        0,
                                                                                        currentRp(p)-amt
                                                                                )
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )
                    );

                });
    }


    private static int currentRp(
            ServerPlayer p
    ){
        return PlayerDataManager.getRp(
                p.getUUID(),
                p.getName().getString()
        );
    }


    private static void setRp(
            ServerPlayer p,
            int value
    ){

        int old=
                currentRp(p);

        PlayerDataManager.setRp(
                p.getUUID(),
                p.getName().getString(),
                value
        );

        ProfileManager.setElo(
                p,
                value
        );

        RankManager.updatePlayerRank(
                p,
                old,
                value
        );
    }
}