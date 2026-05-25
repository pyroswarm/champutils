package com.champutils.worldborder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChampWorldBorderManager {

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final double INSIDE_PADDING = 2.0D;
    private static final Map<UUID, Long> LAST_WARNING_MS = new HashMap<>();

    private ChampWorldBorderManager() {
    }

    public static void applyAll(MinecraftServer server) {
        if (server == null || !ChampWorldBorderConfig.isEnabled()) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            apply(level);
        }
    }

    public static boolean apply(ServerLevel level) {
        if (level == null || !ChampWorldBorderConfig.isEnabled()) {
            return false;
        }

        String dimension = level.dimension().location().toString();
        ChampWorldBorderConfig.BorderEntry entry = ChampWorldBorderConfig.get(dimension);
        if (entry == null) {
            return false;
        }

        WorldBorder border = level.getWorldBorder();
        applyToBorder(border, entry);
        syncBorderToPlayers(level);
        return true;
    }

    public static boolean apply(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || !ChampWorldBorderConfig.isEnabled()) {
            return false;
        }

        String normalized = ChampWorldBorderConfig.normalizeDimension(dimension);
        ChampWorldBorderConfig.BorderEntry entry = ChampWorldBorderConfig.get(normalized);
        if (entry == null) {
            return false;
        }

        for (ServerLevel level : server.getAllLevels()) {
            String levelId = level.dimension().location().toString();
            if (levelId.equals(normalized)) {
                WorldBorder border = level.getWorldBorder();
                applyToBorder(border, entry);
                syncBorderToPlayers(level);
                return true;
            }
        }

        return false;
    }

    private static void applyToBorder(WorldBorder border, ChampWorldBorderConfig.BorderEntry entry) {
        if (border == null || entry == null) {
            return;
        }

        border.setCenter(entry.centerX, entry.centerZ);
        border.setSize(entry.radius * 2.0D);
        border.setWarningBlocks(ChampWorldBorderConfig.warningBlocks());
        border.setWarningTime(ChampWorldBorderConfig.warningTimeSeconds());
    }

    /**
     * Fabric/MultiWorld can leave players with a stale client-side border if we only mutate the ServerLevel border.
     * Re-sending the full border packet after every ChampUtils apply makes the visible wall update immediately.
     */
    public static void syncBorderToPlayers(ServerLevel level) {
        if (level == null) {
            return;
        }

        WorldBorder border = level.getWorldBorder();
        ClientboundInitializeBorderPacket packet = new ClientboundInitializeBorderPacket(border);
        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !ChampWorldBorderConfig.isEnabled()) {
            return;
        }

        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }

        applyAll(server);

        if (!ChampWorldBorderConfig.shouldEnforceWithTick()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforce(player);
        }
    }

    public static boolean isConfigured(ServerLevel level) {
        return level != null && ChampWorldBorderConfig.get(level.dimension().location().toString()) != null;
    }

    public static double radius(ServerLevel level) {
        if (level == null) return 0.0D;
        ChampWorldBorderConfig.BorderEntry entry = ChampWorldBorderConfig.get(level.dimension().location().toString());
        return entry == null ? 0.0D : Math.max(1.0D, entry.radius);
    }

    public static String actualBorderSummary(ServerLevel level) {
        if (level == null) return "unloaded";
        WorldBorder border = level.getWorldBorder();
        return "actualDiameter=" + border.getSize()
                + " actualRadius=" + (border.getSize() / 2.0D)
                + " actualCenter=" + border.getCenterX() + "," + border.getCenterZ()
                + " min=" + border.getMinX() + "," + border.getMinZ()
                + " max=" + border.getMaxX() + "," + border.getMaxZ();
    }

    private static void enforce(ServerPlayer player) {
        if (player == null) {
            return;
        }

        ServerLevel level = player.serverLevel();
        ChampWorldBorderConfig.BorderEntry entry = ChampWorldBorderConfig.get(level.dimension().location().toString());
        if (entry == null) {
            return;
        }

        double minX = entry.centerX - entry.radius + INSIDE_PADDING;
        double maxX = entry.centerX + entry.radius - INSIDE_PADDING;
        double minZ = entry.centerZ - entry.radius + INSIDE_PADDING;
        double maxZ = entry.centerZ + entry.radius - INSIDE_PADDING;

        double clampedX = clamp(player.getX(), minX, maxX);
        double clampedZ = clamp(player.getZ(), minZ, maxZ);

        if (Math.abs(clampedX - player.getX()) < 0.001D && Math.abs(clampedZ - player.getZ()) < 0.001D) {
            return;
        }

        player.teleportTo(level, clampedX, player.getY(), clampedZ, player.getYRot(), player.getXRot());
        warn(player);
    }

    private static void warn(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long last = LAST_WARNING_MS.getOrDefault(player.getUUID(), 0L);
        if (now - last < 3000L) {
            return;
        }

        LAST_WARNING_MS.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("You reached this world's border.").withStyle(ChatFormatting.RED));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
