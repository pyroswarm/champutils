package com.champutils.gym;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;

import com.champutils.badge.BadgeType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.cobblemon.mod.common.entity.npc.NPCEntity;

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



/* =========================
 LIST
========================= */

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
                                                                                                    + badge.name()
                                                                                                    + " §7-> "
                                                                                                    + uuid
                                                                                    ),
                                                                                    false
                                                                            );

                                                                        }
                                                                );

                                                        return 1;
                                                    })
                                    )



/* =========================
 UNBIND
========================= */

                                    .then(
                                            literal("unbind")
                                                    .then(
                                                            argument(
                                                                    "badge",
                                                                    StringArgumentType.word()
                                                            )
                                                                    .executes(ctx->{

                                                                        BadgeType badge =
                                                                                BadgeType.fromString(
                                                                                        StringArgumentType.getString(
                                                                                                ctx,
                                                                                                "badge"
                                                                                        )
                                                                                );

                                                                        if(
                                                                                badge == null
                                                                        ){
                                                                            return 0;
                                                                        }

                                                                        GymRegistry.unbindBadge(
                                                                                badge
                                                                        );

                                                                        ctx.getSource().sendSuccess(
                                                                                ()->Component.literal(
                                                                                        "§cUnbound "
                                                                                                + badge.name()
                                                                                ),
                                                                                false
                                                                        );

                                                                        return 1;

                                                                    })
                                                    )
                                    )



/* =========================
 BIND
========================= */

                                    .then(
                                            literal("bind")
                                                    .then(
                                                            argument(
                                                                    "badge",
                                                                    StringArgumentType.word()
                                                            )
                                                                    .executes(ctx->{

                                                                        ServerPlayer player =
                                                                                ctx.getSource()
                                                                                        .getPlayerOrException();

                                                                        BadgeType badge =
                                                                                BadgeType.fromString(
                                                                                        StringArgumentType.getString(
                                                                                                ctx,
                                                                                                "badge"
                                                                                        )
                                                                                );

                                                                        if(
                                                                                badge == null
                                                                        ){
                                                                            return 0;
                                                                        }



                                                                        /* Find nearest NPC */

                                                                        List<Entity> nearby =
                                                                                player.level()
                                                                                        .getEntities(
                                                                                                player,
                                                                                                new AABB(
                                                                                                        player.blockPosition()
                                                                                                ).inflate(
                                                                                                        8
                                                                                                )
                                                                                        );


                                                                        Entity nearestNpc =
                                                                                nearby.stream()
                                                                                        .filter(
                                                                                                entity ->
                                                                                                        entity.getClass()
                                                                                                                .getSimpleName()
                                                                                                                .contains(
                                                                                                                        "NPC"
                                                                                                                )
                                                                                        )
                                                                                        .min(
                                                                                                Comparator.comparingDouble(
                                                                                                        entity ->
                                                                                                                entity.distanceToSqr(
                                                                                                                        player
                                                                                                                )
                                                                                                )
                                                                                        )
                                                                                        .orElse(
                                                                                                null
                                                                                        );


                                                                        if(
                                                                                nearestNpc == null
                                                                        ){

                                                                            ctx.getSource().sendFailure(
                                                                                    Component.literal(
                                                                                            "§cNo NPC found nearby."
                                                                                    )
                                                                            );

                                                                            return 0;
                                                                        }



                                                                        /* Bind Gym */

                                                                        GymRegistry.bindGym(
                                                                                nearestNpc.getUUID(),
                                                                                badge
                                                                        );



/* =========================
 APPLY CONFIG TEAM
========================= */

                                                                        boolean applied = false;

                                                                        if(
                                                                                nearestNpc instanceof NPCEntity npc
                                                                        ){

                                                                            System.out.println(
                                                                                    "[ChampUtils] Running applyGymTeam..."
                                                                            );

                                                                            applied =
                                                                                    GymNpcPartyBuilder.applyGymTeam(
                                                                                            npc,
                                                                                            badge
                                                                                    );
                                                                        }



                                                                        /* Feedback */

                                                                        if(
                                                                                applied
                                                                        ){

                                                                            ctx.getSource().sendSuccess(
                                                                                    ()->Component.literal(
                                                                                            "§aBound "
                                                                                                    + badge.name()
                                                                                                    + " and applied config team."
                                                                                    ),
                                                                                    false
                                                                            );

                                                                        }
                                                                        else{

                                                                            ctx.getSource().sendSuccess(
                                                                                    ()->Component.literal(
                                                                                            "§eBound "
                                                                                                    + badge.name()
                                                                                                    + " (team apply failed)"
                                                                                    ),
                                                                                    false
                                                                            );

                                                                        }

                                                                        return 1;

                                                                    })
                                                    )
                                    )

                    );


                    dispatcher.register(
                            literal("gymcooldown")
                                    .requires(source -> source.hasPermission(4))
                                    .executes(ctx -> showCooldown(ctx.getSource()))
                                    .then(
                                            argument("seconds", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> setCooldown(
                                                            ctx.getSource(),
                                                            IntegerArgumentType.getInteger(ctx, "seconds")
                                                    ))
                                    )
                    );

                }
        );

    }


    private static int showCooldown(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Gym global cooldown: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(GymSettingsConfig.cooldownDisplay()).withStyle(ChatFormatting.YELLOW)),
                false
        );
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, int seconds) {
        GymSettingsConfig.setGlobalCooldownSeconds(seconds);
        source.sendSuccess(
                () -> Component.literal("Set gym global cooldown to ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(GymSettingsConfig.cooldownDisplay()).withStyle(ChatFormatting.YELLOW)),
                true
        );
        return 1;
    }

}
