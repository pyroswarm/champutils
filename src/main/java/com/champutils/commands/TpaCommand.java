package com.champutils.commands;

import com.champutils.network.NetworkEventManager;
import com.champutils.network.NetworkPlayerDirectory;
import com.champutils.network.NetworkServerConfig;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.teleport.SafeTeleportManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaCommand {
    private static final long EXPIRE_MS = 60_000L;
    private static final String PENDING_TPA_LANDING_KEY = "pending_tpa_landing";
    private static final Map<UUID, Request> REQUESTS_BY_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, Landing> PENDING_LANDINGS = new ConcurrentHashMap<>();

    private TpaCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("tpa")
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests(NetworkPlayerDirectory::suggestNames)
                            .executes(context -> request(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "player")))));
            dispatcher.register(Commands.literal("tpaccept").executes(context -> accept(context.getSource().getPlayerOrException())));
            dispatcher.register(Commands.literal("tpdeny").executes(context -> deny(context.getSource().getPlayerOrException())));
        });
    }

    private static int request(ServerPlayer requester, String targetName) {
        if (requester == null || requester.server == null) return 0;
        ServerPlayer target = findLocalByName(requester.server, targetName);
        if (target != null) return requestLocal(requester, target);

        NetworkPlayerDirectory.OnlinePlayer remote = NetworkPlayerDirectory.find(targetName);
        if (remote == null) {
            requester.sendSystemMessage(Component.literal("Player not found on the network.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (remote.playerUuid().equals(requester.getUUID())) {
            requester.sendSystemMessage(Component.literal("You cannot send a TPA request to yourself.").withStyle(ChatFormatting.RED));
            return 0;
        }
        NetworkEventManager.publishTpaRequest(requester, remote.playerUuid());
        requester.sendSystemMessage(Component.literal("TPA request sent to " + remote.playerName() + " on " + displayServer(remote.serverId()) + ".").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int requestLocal(ServerPlayer requester, ServerPlayer target) {
        if (target == null) {
            requester.sendSystemMessage(Component.literal("Player not found.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (target.getUUID().equals(requester.getUUID())) {
            requester.sendSystemMessage(Component.literal("You cannot send a TPA request to yourself.").withStyle(ChatFormatting.RED));
            return 0;
        }
        REQUESTS_BY_TARGET.put(target.getUUID(), new Request(requester.getUUID(), requester.getGameProfile().getName(), NetworkServerConfig.serverId(), System.currentTimeMillis() + EXPIRE_MS));
        requester.sendSystemMessage(Component.literal("TPA request sent to " + target.getGameProfile().getName() + ".").withStyle(ChatFormatting.GREEN));
        sendRequestPrompt(target, requester.getGameProfile().getName(), NetworkServerConfig.serverId());
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
            Landing landing = landing(target);
            PENDING_LANDINGS.put(request.requesterId, landing);
            NetworkEventManager.publishTpaAccept(target, request.requesterId);
            target.sendSystemMessage(Component.literal("Accepted TPA request from " + request.requesterName + ".").withStyle(ChatFormatting.GREEN));
            return 1;
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
        else NetworkEventManager.publishTpaDeny(target, request.requesterId);
        target.sendSystemMessage(Component.literal("Denied TPA request.").withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    public static void handleNetworkRequest(MinecraftServer server, String scope, String message) {
        if (server == null || scope == null || message == null) return;
        UUID targetId = uuidFromScope(scope, "TPA_TARGET:");
        if (targetId == null) return;
        ServerPlayer target = server.getPlayerList().getPlayer(targetId);
        if (target == null) return;
        String[] parts = message.split("\t", -1);
        if (parts.length < 3) return;
        try {
            UUID requesterId = UUID.fromString(parts[0]);
            String requesterName = parts[1].isBlank() ? "Player" : parts[1];
            String requesterServer = parts[2];
            REQUESTS_BY_TARGET.put(targetId, new Request(requesterId, requesterName, requesterServer, System.currentTimeMillis() + EXPIRE_MS));
            sendRequestPrompt(target, requesterName, requesterServer);
        } catch (Exception ignored) {
        }
    }

    public static void handleNetworkAccept(MinecraftServer server, String scope, String message) {
        if (server == null || scope == null || message == null) return;
        UUID requesterId = uuidFromScope(scope, "TPA_REQUESTER:");
        if (requesterId == null) return;
        Landing landing = parseLanding(message);
        if (landing == null) return;
        PENDING_LANDINGS.put(requesterId, landing);
        ServerPlayer requester = server.getPlayerList().getPlayer(requesterId);
        if (requester == null) return;
        requester.sendSystemMessage(Component.literal(landing.targetName + " accepted your TPA request. Sending you to " + displayServer(landing.serverId) + "...").withStyle(ChatFormatting.GREEN));
        if (NetworkServerConfig.serverId().equalsIgnoreCase(landing.serverId)) {
            consumePendingLanding(requester);
            return;
        }
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(requester);
        if (active == null) {
            requester.sendSystemMessage(Component.literal("Select a profile before using cross-server TPA.").withStyle(ChatFormatting.RED));
            return;
        }
        SharedJsonStateRepository.savePlayerAsync(requester.getUUID(), PENDING_TPA_LANDING_KEY, landing)
                .whenComplete((ignored, error) -> server.execute(() -> {
                    ServerPlayer live = server.getPlayerList().getPlayer(requesterId);
                    if (!SafeTeleportManager.isLive(live)) return;
                    if (error != null) {
                        live.sendSystemMessage(Component.literal("Could not prepare the cross-server TPA transfer. Try again in a moment.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    ProfileNetworkTransferFlow.issueTransferFromLobby(live, active, landing.serverId, transferMessage -> {
                        if (transferMessage != null && transferMessage.startsWith("Could not")) {
                            live.sendSystemMessage(Component.literal(transferMessage).withStyle(ChatFormatting.RED));
                        }
                    });
                }));
    }

    public static void handleNetworkDeny(MinecraftServer server, String scope, String message) {
        if (server == null || scope == null) return;
        UUID requesterId = uuidFromScope(scope, "TPA_REQUESTER:");
        if (requesterId == null) return;
        ServerPlayer requester = server.getPlayerList().getPlayer(requesterId);
        if (requester == null) return;
        String name = message == null || message.isBlank() ? "That player" : message;
        requester.sendSystemMessage(Component.literal(name + " denied your TPA request.").withStyle(ChatFormatting.RED));
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) return;
        if (consumePendingLanding(player)) return;
        UUID playerUuid = player.getUUID();
        SharedJsonStateRepository
                .loadPlayerAsync(playerUuid, PENDING_TPA_LANDING_KEY, Landing.class, null)
                .thenAccept(landing -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player) || landing == null) return;
                    if (landing.expiresAtMs < System.currentTimeMillis()) {
                        clearSharedLanding(playerUuid);
                        return;
                    }
                    PENDING_LANDINGS.put(playerUuid, landing);
                    if (consumePendingLanding(player)) {
                        clearSharedLanding(playerUuid);
                    }
                }));
    }

    private static boolean consumePendingLanding(ServerPlayer player) {
        Landing landing = PENDING_LANDINGS.get(player.getUUID());
        if (landing == null || landing.expiresAtMs < System.currentTimeMillis()) {
            PENDING_LANDINGS.remove(player.getUUID());
            return false;
        }
        if (!NetworkServerConfig.serverId().equalsIgnoreCase(landing.serverId)) return false;
        var level = player.server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(landing.dimension)));
        if (level == null) return false;
        PENDING_LANDINGS.remove(player.getUUID());
        if (SafeTeleportManager.teleport(player, level, landing.x, landing.y, landing.z, landing.yaw, landing.pitch)) {
            player.sendSystemMessage(Component.literal("Teleported to " + landing.targetName + ".").withStyle(ChatFormatting.GREEN));
            return true;
        }
        return false;
    }

    private static void clearSharedLanding(UUID playerUuid) {
        if (playerUuid == null) return;
        Landing cleared = new Landing(playerUuid, "", "", "", 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 0L);
        SharedJsonStateRepository.savePlayer(playerUuid, PENDING_TPA_LANDING_KEY, cleared);
    }

    private static void sendRequestPrompt(ServerPlayer target, String requesterName, String requesterServer) {
        target.sendSystemMessage(Component.literal(requesterName + " wants to teleport to you" + (requesterServer == null || requesterServer.isBlank() ? "" : " from " + displayServer(requesterServer)) + ".").withStyle(ChatFormatting.AQUA));
        Component accept = Component.literal("[ACCEPT]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpaccept")));
        Component deny = Component.literal("[DENY]").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tpdeny")));
        target.sendSystemMessage(Component.literal("Click ").withStyle(ChatFormatting.GRAY).append(accept).append(Component.literal(" or ").withStyle(ChatFormatting.GRAY)).append(deny).append(Component.literal(". Expires in 60 seconds.").withStyle(ChatFormatting.GRAY)));
    }

    private static ServerPlayer findLocalByName(MinecraftServer server, String targetName) {
        if (server == null || targetName == null || targetName.isBlank()) return null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(targetName)) return player;
        }
        return null;
    }

    private static UUID uuidFromScope(String scope, String prefix) {
        if (scope == null || prefix == null || !scope.toUpperCase().startsWith(prefix)) return null;
        try {
            return UUID.fromString(scope.substring(prefix.length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Landing landing(ServerPlayer target) {
        return new Landing(target.getUUID(), target.getGameProfile().getName(), NetworkServerConfig.serverId(), target.serverLevel().dimension().location().toString(), target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot(), System.currentTimeMillis() + EXPIRE_MS);
    }

    private static Landing parseLanding(String message) {
        String[] parts = message.split("\t", -1);
        if (parts.length < 9) return null;
        try {
            return new Landing(
                    UUID.fromString(parts[0]),
                    parts[1].isBlank() ? "Player" : parts[1],
                    parts[2],
                    parts[3],
                    Double.parseDouble(parts[4]),
                    Double.parseDouble(parts[5]),
                    Double.parseDouble(parts[6]),
                    Float.parseFloat(parts[7]),
                    Float.parseFloat(parts[8]),
                    System.currentTimeMillis() + EXPIRE_MS
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String displayServer(String serverId) {
        if ("main_survival1".equalsIgnoreCase(serverId)) return "Alpha";
        if ("survival2".equalsIgnoreCase(serverId)) return "Omega";
        if ("profile_lobby".equalsIgnoreCase(serverId)) return "Lobby";
        return serverId == null || serverId.isBlank() ? "another server" : serverId;
    }

    private record Request(UUID requesterId, String requesterName, String requesterServerId, long expiresAtMs) {}
    private record Landing(UUID targetId, String targetName, String serverId, String dimension, double x, double y, double z, float yaw, float pitch, long expiresAtMs) {}
}
