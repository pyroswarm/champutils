package com.champutils.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AutoModCommand {
    private AutoModCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("automod")
                .requires(source -> {
                    try {
                        return source.hasPermission(4) || ModerationManager.canModerate(source.getPlayer());
                    } catch (Exception ignored) {
                        return source.hasPermission(4);
                    }
                })
                .then(Commands.literal("escalate")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(context -> {
                                ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                String reason = StringArgumentType.getString(context, "reason");

                                ModerationManager.manualEscalate(
                                    context.getSource().getPlayerOrException(),
                                    target,
                                    reason
                                );

                                context.getSource().sendSuccess(
                                    () -> Component.literal("Escalated AutoMod record for " + target.getGameProfile().getName() + "."),
                                    true
                                );
                                return 1;
                            })
                        )
                    )
                )
                .then(Commands.literal("reload")
                    .executes(context -> {
                        ModerationConfig.load();
                        context.getSource().sendSuccess(
                            () -> Component.literal("Reloaded moderation.json."),
                            true
                        );
                        return 1;
                    })
                )
            );
        });
    }
}
