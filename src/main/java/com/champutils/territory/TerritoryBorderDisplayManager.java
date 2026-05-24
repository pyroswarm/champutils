package com.champutils.territory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryBorderDisplayManager {
    private static final Map<UUID, UUID> SHOWN_TERRITORIES = new ConcurrentHashMap<>();

    private static final int DRAW_EVERY_TICKS = 20;
    private static final int SAMPLE_SPACING_BLOCKS = 8;
    private static final int MAX_DISTANCE_BLOCKS = 96;
    private static final double EDGE_INSET = 0.5D;

    private TerritoryBorderDisplayManager() {}

    public static void show(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return;
        if (!TerritoryRepository.canEnter(player, territory)) {
            player.sendSystemMessage(Component.literal("You cannot display a territory you are not allowed to enter.").withStyle(ChatFormatting.RED));
            return;
        }
        SHOWN_TERRITORIES.put(player.getUUID(), territory.id);
        player.sendSystemMessage(Component.literal("Territory border display enabled. Use /territory border hide to turn it off.").withStyle(ChatFormatting.GREEN));
        drawForPlayer(player, territory);
    }

    public static void hide(ServerPlayer player) {
        if (player == null) return;
        SHOWN_TERRITORIES.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("Territory border display disabled.").withStyle(ChatFormatting.YELLOW));
    }

    public static boolean isShowing(ServerPlayer player) {
        return player != null && SHOWN_TERRITORIES.containsKey(player.getUUID());
    }

    public static void toggle(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (isShowing(player)) hide(player);
        else show(player, territory);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !TerritoryConfig.get().enabled) return;
        if (server.getTickCount() % DRAW_EVERY_TICKS != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID territoryId = SHOWN_TERRITORIES.get(player.getUUID());
            if (territoryId == null) continue;

            TerritoryRepository.Territory territory = findById(territoryId);
            if (territory == null || !TerritoryRepository.canEnter(player, territory)) {
                SHOWN_TERRITORIES.remove(player.getUUID());
                continue;
            }

            drawForPlayer(player, territory);
        }
    }

    private static TerritoryRepository.Territory findById(UUID id) {
        if (id == null) return null;
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (id.equals(territory.id)) return territory;
        }
        return null;
    }

    private static void drawForPlayer(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return;
        if (!territory.worldName.equalsIgnoreCase(player.serverLevel().dimension().location().toString())) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        double y = Math.max(level.getMinBuildHeight() + 2, Math.min(level.getMaxBuildHeight() - 2, player.getY() + 1.0D));
        double minX = territory.minX + EDGE_INSET;
        double maxX = territory.maxX + EDGE_INSET;
        double minZ = territory.minZ + EDGE_INSET;
        double maxZ = territory.maxZ + EDGE_INSET;

        drawEdgeNearPlayer(player, level, minX, minZ, maxX, minZ, y);
        drawEdgeNearPlayer(player, level, minX, maxZ, maxX, maxZ, y);
        drawEdgeNearPlayer(player, level, minX, minZ, minX, maxZ, y);
        drawEdgeNearPlayer(player, level, maxX, minZ, maxX, maxZ, y);
    }

    private static void drawEdgeNearPlayer(ServerPlayer player, ServerLevel level, double x1, double z1, double x2, double z2, double y) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(length / SAMPLE_SPACING_BLOCKS));

        double maxDistanceSq = MAX_DISTANCE_BLOCKS * MAX_DISTANCE_BLOCKS;
        for (int i = 0; i <= samples; i++) {
            double t = samples == 0 ? 0.0D : (double) i / (double) samples;
            double x = x1 + dx * t;
            double z = z1 + dz * t;
            double px = player.getX() - x;
            double pz = player.getZ() - z;
            if ((px * px) + (pz * pz) > maxDistanceSq) continue;
            level.sendParticles(player, ParticleTypes.END_ROD, true, x, y, z, 1, 0.0D, 0.08D, 0.0D, 0.0D);
        }
    }
}
