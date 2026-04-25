package com.champutils.commands;

import com.champutils.badge.BadgeUnlockManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public class EVTrainingCommand {

    /*
     * Change these coordinates
     * to your EV training facility later.
     */
    private static final BlockPos EV_WARP =
            new BlockPos(
                    100,
                    70,
                    100
            );


    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            literal("evtrain")

                                    .executes(ctx -> {

                                        ServerPlayer player =
                                                ctx.getSource()
                                                        .getPlayerOrException();


/* =========================
 ACCESS CHECK
========================= */

                                        if(
                                                !BadgeUnlockManager
                                                        .hasEvTrainingAccess(
                                                                player
                                                        )
                                        ){

                                            ctx.getSource().sendFailure(
                                                    Component.literal(
                                                            "§cYou need 5 gym badges to access EV Training."
                                                    )
                                            );

                                            return 0;
                                        }



/* =========================
 WARP PLAYER
========================= */

                                        player.teleportTo(
                                                EV_WARP.getX() + 0.5,
                                                EV_WARP.getY(),
                                                EV_WARP.getZ() + 0.5
                                        );


                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(
                                                        "§aWarped to EV Training."
                                                ),
                                                false
                                        );

                                        return 1;
                                    })
                    );

                }
        );
    }

}