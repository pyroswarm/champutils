package com.champutils.teleport;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PortalCommand {

    private PortalCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("portal")
                        .requires(source -> source.hasPermission(4))
                        .then(literal("pos1")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> setPos1(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(literal("pos2")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> setPos2(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(literal("command")
                                .then(argument("id", StringArgumentType.word())
                                        .then(argument("command", StringArgumentType.greedyString())
                                                .executes(ctx -> setCommand(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "command"))))))
                        .then(literal("delete")
                                .then(argument("id", StringArgumentType.word())
                                        .executes(ctx -> delete(ctx.getSource(), StringArgumentType.getString(ctx, "id")))))
                        .then(literal("list")
                                .executes(ctx -> list(ctx.getSource())))
        ));
    }

    private static int setPos1(CommandSourceStack source, String id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can set portal positions."));
            return 0;
        }

        PortalRegion portal = TeleportConfig.getOrCreatePortal(id);
        portal.setPos1(player);
        TeleportConfig.save();
        player.sendSystemMessage(Component.literal("Set portal " + id.toLowerCase() + " pos1.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setPos2(CommandSourceStack source, String id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can set portal positions."));
            return 0;
        }

        PortalRegion portal = TeleportConfig.getOrCreatePortal(id);
        portal.setPos2(player);
        TeleportConfig.save();
        player.sendSystemMessage(Component.literal("Set portal " + id.toLowerCase() + " pos2.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setCommand(CommandSourceStack source, String id, String command) {
        String cleaned = command.trim();
        if (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }

        if (!PortalManager.isAllowedPortalCommand(cleaned)) {
            source.sendFailure(Component.literal("Portal commands are limited to: rtp, spawn, warp <name>").withStyle(ChatFormatting.RED));
            return 0;
        }

        PortalRegion portal = TeleportConfig.getOrCreatePortal(id);
        portal.command = cleaned;
        TeleportConfig.save();

        final String commandDisplay = cleaned;
        source.sendSuccess(() -> Component.literal("Set portal " + id.toLowerCase() + " command to /" + commandDisplay).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int delete(CommandSourceStack source, String id) {
        boolean removed = TeleportConfig.deletePortal(id);
        if (removed) {
            source.sendSuccess(() -> Component.literal("Deleted portal: " + id.toLowerCase()).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("Unknown portal: " + id.toLowerCase()).withStyle(ChatFormatting.RED));
        }
        return removed ? 1 : 0;
    }

    private static int list(CommandSourceStack source) {
        if (TeleportConfig.portals().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No portals have been created.").withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("Portals:").withStyle(ChatFormatting.GOLD), false);
        TeleportConfig.portals().forEach((id, portal) -> source.sendSuccess(
                () -> Component.literal("- " + id + " -> /" + (portal.command == null ? "no command" : portal.command)).withStyle(ChatFormatting.GRAY),
                false
        ));
        return 1;
    }
}
