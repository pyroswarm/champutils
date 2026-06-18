package com.champutils.commands;

import com.champutils.teleport.SafeTeleportManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaCommand {
    private static final long EXPIRE_MS = 60_000L;
    private static final Map<UUID, Request> REQUESTS_BY_TARGET = new ConcurrentHashMap<>();

    private TpaCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("tpa")
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(context -> request(context.getSource().getPlayerOrException(), EntityArgument.getPlayer(context, "player")))));
            dispatcher.register(Commands.literal("tpaccept").executes(context -> accept(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("tpdeny").executes(context -> deny(context.getSource().getPlayerOrException())));
        });
    }

    private static int request(ServerPlayer requester, ServerPlayer target) {
        if (target == null) {
            requester.sendSystemMessage(Component.literal("Player not found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target.getUUID().equals(requester.getUUID())) {
            requester.sendSystemMessage(Component.literal("You cannot send a TPA request to yourself.").withStyle(ChatFormatting.RED));
            return 0;
        }
        REQUESTS_BY_TARGET.put(target.getUUID(), new Request(requester.getUUID(), System.currentTimeMillis() + EXPIRE_MS));
        requester.sendSystemMessage(Component.literal("TPA request sent to " + target.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal(requester.getGameProfile().getName() + " wants to teleport to you.").withStyle(ChatFormatting.AQUA));
        Component accept = Component.literal("[ACCEPT]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept")));
        Component deny = Component.literal("[DENY]").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny")));
        target.sendSystemMessage(Component.literal("Click ").withStyle(ChatFormatting.GRAY).append(accept).append(Component.literal(" or ").withStyle(ChatFormatting.GRAY)).append(deny).append(Component.literal(". Expires in 60 seconds.").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int accept(ServerPlayer target) {
        Request request = REQUESTS_BY_TARGET.remove(target.getUUID());
        if (request == null || request.expiresAtMs < System.currentTimeMillis()) {
            target.sendSystemMessage(Component.literal("You do not have a pending TPA request.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requesterId);
        if (requester == null) {
            target.sendSystemMessage(Component.literal("That player is no longer online.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!SafeTeleportManager.teleport(requester, target.serverLevel(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot())) return 0;
        requester.sendSystemMessage(Component.literal("Teleported to " + target.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal("Accepted TPA request from " + requester.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int deny(ServerPlayer target) {
        Request request = REQUESTS_BY_TARGET.remove(target.getUUID());
        if (request == null) {
            target.sendSystemMessage(Component.literal("You do not have a pending TPA request.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requesterId);
        if (requester != null) requester.sendSystemMessage(Component.literal(target.getGameProfile().getName() + " denied your TPA request.").withStyle(ChatFormatting.RED));
        target.sendSystemMessage(Component.literal("Denied TPA request.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private record Request(UUID requesterId, long expiresAtMs) {}
}
