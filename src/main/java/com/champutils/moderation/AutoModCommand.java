package com.champutils.moderation;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Duration;
import java.util.Locale;

public final class AutoModCommand {
    private AutoModCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("automod")
                .requires(AutoModCommand::canUse)
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
                .then(Commands.literal("mute")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("duration", StringArgumentType.word())
                            .executes(context -> mutePlayer(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                StringArgumentType.getString(context, "duration"),
                                "Manual staff mute"
                            ))
                            .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(context -> mutePlayer(
                                    context.getSource(),
                                    EntityArgument.getPlayer(context, "player"),
                                    StringArgumentType.getString(context, "duration"),
                                    StringArgumentType.getString(context, "reason")
                                ))
                            )
                        )
                    )
                )
                .then(Commands.literal("unmute")
                    .then(Commands.argument("player", EntityArgument.player())
                        .executes(context -> unmutePlayer(
                            context.getSource(),
                            EntityArgument.getPlayer(context, "player")
                        ))
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

            dispatcher.register(Commands.literal("mute")
                .requires(AutoModCommand::canUse)
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("duration", StringArgumentType.word())
                        .executes(context -> mutePlayer(
                            context.getSource(),
                            EntityArgument.getPlayer(context, "player"),
                            StringArgumentType.getString(context, "duration"),
                            "Manual staff mute"
                        ))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                            .executes(context -> mutePlayer(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "player"),
                                StringArgumentType.getString(context, "duration"),
                                StringArgumentType.getString(context, "reason")
                            ))
                        )
                    )
                )
            );

            dispatcher.register(Commands.literal("unmute")
                .requires(AutoModCommand::canUse)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(context -> unmutePlayer(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "player")
                    ))
                )
            );
        });
    }

    private static boolean canUse(CommandSourceStack source) {
        try {
            return source.hasPermission(4) || ModerationManager.canModerate(source.getPlayer());
        } catch (Exception ignored) {
            return source.hasPermission(4);
        }
    }

    private static int mutePlayer(CommandSourceStack source, ServerPlayer target, String durationText, String reason) {
        Duration duration;
        try {
            duration = parseDuration(durationText);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Invalid duration: " + durationText + ". Use 15m, 1h, 2h, 1d, or 30s.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ModerationManager.manualMute(source.getTextName(), target, duration, reason);
        source.sendSuccess(
            () -> Component.literal("Muted " + target.getGameProfile().getName() + " for " + durationText + "."),
            true
        );
        return 1;
    }

    private static int unmutePlayer(CommandSourceStack source, ServerPlayer target) {
        boolean changed = ModerationManager.manualUnmute(source.getTextName(), target);
        if (changed) {
            source.sendSuccess(
                () -> Component.literal("Unmuted " + target.getGameProfile().getName() + "."),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.literal(target.getGameProfile().getName() + " was not muted."),
                false
            );
        }
        return 1;
    }

    private static Duration parseDuration(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("blank duration");

        String text = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier;
        if (text.endsWith("s")) {
            multiplier = 1_000L;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 60_000L;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("h")) {
            multiplier = 3_600_000L;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("d")) {
            multiplier = 86_400_000L;
            text = text.substring(0, text.length() - 1);
        } else {
            throw new IllegalArgumentException("missing unit");
        }

        long amount = Long.parseLong(text);
        if (amount <= 0) throw new IllegalArgumentException("duration must be positive");
        return Duration.ofMillis(Math.multiplyExact(amount, multiplier));
    }
}
