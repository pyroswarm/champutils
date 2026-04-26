package com.champutils.commands;

import com.champutils.badge.BadgeUnlockManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

public class EliteFourCommand {

    /*
     * Change to your Elite Four arena later
     */
    private static final BlockPos ELITE_WARP =
            new BlockPos(
                    200,
                    70,
                    200
            );


    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            literal("elite4")

                                    .executes(ctx -> {

                                        ServerPlayer player =
                                                ctx.getSource()
                                                        .getPlayerOrException();



/* =========================
 ACCESS CHECK
========================= */

                                        if(
                                                !BadgeUnlockManager
                                                        .hasEliteFourAccess(
                                                                player
                                                        )
                                        ){

                                            ctx.getSource().sendFailure(
                                                    Component.literal(
                                                            "§cYou need 8 gym badges to challenge the Elite Four."
                                                    )
                                            );

                                            return 0;
                                        }



/* =========================
 TELEPORT
========================= */

                                        player.teleportTo(
                                                player.serverLevel(),
                                                ELITE_WARP.getX()+0.5,
                                                ELITE_WARP.getY(),
                                                ELITE_WARP.getZ()+0.5,
                                                player.getYRot(),
                                                player.getXRot()
                                        );


                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(
                                                        "§6Warped to the Elite Four."
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