package com.champutils.exploration;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ExplorationWorldCommand {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault());

    private ExplorationWorldCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("exploration")
                .executes(context -> list(context.getSource().getPlayerOrException()))
                .then(Commands.literal("list").executes(context -> list(context.getSource().getPlayerOrException())))
                .then(Commands.literal("admin")
                        .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                        .then(Commands.literal("go")
                                .then(Commands.argument("world", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> go(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "world")))))
                        .then(Commands.literal("ready")
                                .then(Commands.argument("world", StringArgumentType.greedyString())
                                        .executes(context -> ready(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "world")))))
                        .then(Commands.literal("wipe")
                                .then(Commands.argument("world", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> wipe(context.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(context, "world"))))))));
    }

    private static int list(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Exploration Worlds").withStyle(ChatFormatting.GOLD));
        for (ExplorationWorldManager.Entry entry : ExplorationWorldManager.entries()) {
            ChatFormatting color = entry.activeForRtp ? ("READY".equalsIgnoreCase(entry.status) ? ChatFormatting.GREEN : ChatFormatting.YELLOW) : ChatFormatting.DARK_GRAY;
            player.sendSystemMessage(Component.literal("#" + entry.index + " [" + entry.worldType + " " + entry.localIndex + "] " + entry.worldName + " - " + (entry.activeForRtp ? "ACTIVE" : "LOCKED") + " - " + entry.status + " - next wipe: " + TIME.format(Instant.ofEpochMilli(entry.nextWipeAtMillis))).withStyle(color));
        }
        player.sendSystemMessage(Component.literal("Exploration worlds are no longer used by RTP. Admin: /exploration admin ready <world>, /exploration admin wipe <number>. Survival RTP locks: /rtpworlds list.").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int go(ServerPlayer player, int index) {
        return ExplorationWorldManager.teleport(player, index) ? 1 : 0;
    }

    private static int goType(ServerPlayer player, String type, int index) {
        return ExplorationWorldManager.teleport(player, type, index) ? 1 : 0;
    }

    private static int ready(ServerPlayer player, String worldName) {
        boolean ok = ExplorationWorldManager.markReady(worldName);
        player.sendSystemMessage(Component.literal(ok ? "Marked " + worldName + " READY." : "Unknown exploration world: " + worldName).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
        return ok ? 1 : 0;
    }

    private static int wipe(ServerPlayer player, int index) {
        if (index < 1 || index > ExplorationWorldManager.entries().size()) {
            player.sendSystemMessage(Component.literal("Unknown exploration world number.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ExplorationWorldManager.startWipe(player.server, ExplorationWorldManager.entries().get(index - 1), true);
        player.sendSystemMessage(Component.literal("Forced wipe/recreate for exploration world #" + index + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
