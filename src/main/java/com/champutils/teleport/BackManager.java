package com.champutils.teleport;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BackManager {
    private static final String STATE_KEY = "network_back_location";
    private static final Map<UUID, TeleportLocation> LAST = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> DEATH_BACK_LOCK_UNTIL = new ConcurrentHashMap<>();

    private BackManager() {}

    public static void remember(ServerPlayer player) {
        if (player == null) return;
        Long lockedUntil = DEATH_BACK_LOCK_UNTIL.get(player.getUUID());
        if (lockedUntil != null && System.currentTimeMillis() < lockedUntil) return;
        remember(player, player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    public static void rememberDeath(ServerPlayer player) {
        if (player == null) return;
        remember(player, player.serverLevel().dimension().location().toString(), player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        DEATH_BACK_LOCK_UNTIL.put(player.getUUID(), System.currentTimeMillis() + 15_000L);
    }

    public static void remember(ServerPlayer player, String dimension, double x, double y, double z, float yaw, float pitch) {
        if (player == null || dimension == null || dimension.isBlank()) return;
        TeleportLocation location = new TeleportLocation(dimension, x, y, z, yaw, pitch);
        location.serverId = NetworkServerConfig.serverId();
        location.transferPending = false;
        LAST.put(player.getUUID(), location);
        SharedJsonStateRepository.savePlayer(player.getUUID(), STATE_KEY, location);
    }

    public static boolean teleportBack(ServerPlayer player) {
        if (player == null) return false;
        DEATH_BACK_LOCK_UNTIL.remove(player.getUUID());
        TeleportLocation loc = LAST.get(player.getUUID());
        if (loc == null) {
            loc = SharedJsonStateRepository.loadPlayer(player.getUUID(), STATE_KEY, TeleportLocation.class, null);
            if (isUsable(loc)) LAST.put(player.getUUID(), loc);
        }
        if (!isUsable(loc)) return false;

        String targetServer = loc.serverId == null ? "" : loc.serverId.trim();
        if (!targetServer.isBlank() && !targetServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
            if (active == null) return false;
            TeleportLocation retained = loc;
            retained.transferPending = true;
            LAST.put(player.getUUID(), retained);
            SharedJsonStateRepository.savePlayer(player.getUUID(), STATE_KEY, retained);
            player.sendSystemMessage(Component.literal("Sending you to " + displayServer(targetServer) + " for /back.").withStyle(ChatFormatting.YELLOW));
            ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, targetServer, message -> {
                boolean issued = message != null && message.startsWith("Profile transfer token issued");
                if (!issued) {
                    retained.transferPending = false;
                    LAST.put(player.getUUID(), retained);
                    SharedJsonStateRepository.savePlayer(player.getUUID(), STATE_KEY, retained);
                    if (SafeTeleportManager.isLive(player)) {
                        player.sendSystemMessage(Component.literal(message == null ? "Could not start the cross-server /back transfer." : message).withStyle(ChatFormatting.RED));
                    }
                }
            });
            return true;
        }

        if (loc.transferPending) {
            loc.transferPending = false;
            LAST.put(player.getUUID(), loc);
            SharedJsonStateRepository.savePlayer(player.getUUID(), STATE_KEY, loc);
        }
        ServerLevel level = getLevel(player.server, loc.dimension);
        if (level == null) return false;
        if (!SafeTeleportManager.canTeleportTo(player, level, loc.x, loc.y, loc.z)) {
            player.sendSystemMessage(Component.literal("That /back location is no longer safe or allowed.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!SafeTeleportManager.teleportNoBack(player, level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch)) return false;
        LAST.remove(player.getUUID());
        clearPersisted(player.getUUID());
        return true;
    }

    public static void handleProfileReady(ServerPlayer player) {
        if (player == null) return;
        SharedJsonStateRepository.loadPlayerAsync(player.getUUID(), STATE_KEY, TeleportLocation.class, null)
                .thenAccept(location -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player)) return;
                    if (!isUsable(location)) {
                        LAST.remove(player.getUUID());
                        return;
                    }
                    LAST.put(player.getUUID(), location);
                    String targetServer = location.serverId == null ? "" : location.serverId.trim();
                    if (location.transferPending && (targetServer.isBlank() || targetServer.equalsIgnoreCase(NetworkServerConfig.serverId()))) {
                        teleportBack(player);
                    }
                }));
    }

    private static boolean isUsable(TeleportLocation location) {
        return location != null && location.dimension != null && !location.dimension.isBlank();
    }

    private static void clearPersisted(UUID playerId) {
        TeleportLocation cleared = new TeleportLocation();
        cleared.dimension = "";
        cleared.serverId = "";
        SharedJsonStateRepository.savePlayer(playerId, STATE_KEY, cleared);
    }

    private static String displayServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return "the destination server";
        String clean = serverId.trim();
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    public static ServerLevel getLevel(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        try {
            ResourceLocation id = ResourceLocation.parse(dimension);
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        } catch (Exception ignored) {
            return null;
        }
    }
}
