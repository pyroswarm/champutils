package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class GymCommand {

    public static void register(){

        CommandRegistrationCallback.EVENT.register(
                (dispatcher,r,e)->{

                    dispatcher.register(
                            literal("gym")

                                    .then(
                                            literal("list")
                                                    .executes(ctx->{

                                                        ctx.getSource().sendSuccess(
                                                                ()->Component.literal(
                                                                        "§6Registered Gyms:"
                                                                ),
                                                                false
                                                        );

                                                        GymRegistry.getAllGyms()
                                                                .forEach(
                                                                        (uuid,badge)->{
                                                                            ctx.getSource().sendSuccess(
                                                                                    ()->Component.literal(
                                                                                            "§e"
                                                                                                    +badge.name()
                                                                                                    +" §7-> "
                                                                                                    +uuid
                                                                                    ),
                                                                                    false
                                                                            );
                                                                        }
                                                                );

                                                        return 1;
                                                    })
                                    )

                                    .then(
                                            literal("unbind")
                                                    .then(
                                                            argument(
                                                                    "badge",
                                                                    StringArgumentType.word()
                                                            )
                                                                    .executes(ctx->{

                                                                        BadgeType badge=
                                                                                BadgeType.fromString(
                                                                                        StringArgumentType.getString(
                                                                                                ctx,
                                                                                                "badge"
                                                                                        )
                                                                                );

                                                                        if(badge==null){
                                                                            return 0;
                                                                        }

                                                                        GymRegistry.unbindBadge(
                                                                                badge
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cUnbound "
                                                                                                +badge.name()
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;
                                                                    })
                                                    )
                                    )

                                    .then(
                                            literal("bind")
                                                    .then(
                                                            argument(
                                                                    "badge",
                                                                    StringArgumentType.word()
                                                            )
                                                                    .executes(ctx->{

                                                                        ServerPlayer player=
                                                                                ctx.getSource()
                                                                                        .getPlayerOrException();

                                                                        BadgeType badge=
                                                                                BadgeType.fromString(
                                                                                        StringArgumentType.getString(
                                                                                                ctx,
                                                                                                "badge"
                                                                                        )
                                                                                );

                                                                        if(
                                                                                badge==null
                                                                        ){
                                                                            return 0;
                                                                        }


                                                                        // Find nearest nearby NPC
                                                                        List<Entity> nearby =
                                                                                player.level()
                                                                                        .getEntities(
                                                                                                player,
                                                                                                new AABB(
                                                                                                        player.blockPosition()
                                                                                                ).inflate(8)
                                                                                        );

                                                                        Entity nearestNpc =
                                                                                nearby.stream()
                                                                                        .filter(
                                                                                                entity->
                                                                                                        entity.getClass()
                                                                                                                .getSimpleName()
                                                                                                                .contains("NPC")
                                                                                        )
                                                                                        .min(
                                                                                                Comparator.comparingDouble(
                                                                                                        entity->
                                                                                                                entity.distanceToSqr(
                                                                                                                        player
                                                                                                                )
                                                                                                )
                                                                                        )
                                                                                        .orElse(
                                                                                                null
                                                                                        );

                                                                        if(
                                                                                nearestNpc==null
                                                                        ){
                                                                            ctx.getSource().sendFailure(
                                                                                    Component.literal(
                                                                                            "§cNo NPC found nearby."
                                                                                    )
                                                                            );
                                                                            return 0;
                                                                        }


                                                                        GymRegistry.bindGym(
                                                                                nearestNpc.getUUID(),
                                                                                badge
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§aBound "
                                                                                                +badge.name()
                                                                                                +" to NPC "
                                                                                                +nearestNpc.getUUID()
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;

                                                                    })
                                                    )
                                    )

                    );

                }
        );
    }

}