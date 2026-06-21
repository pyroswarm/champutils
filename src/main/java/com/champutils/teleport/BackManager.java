package com.champutils.teleport;

import net.minecraft.core.registries.Registries;
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
        LAST.put(player.getUUID(), new TeleportLocation(dimension, x, y, z, yaw, pitch));
    }

    public static boolean teleportBack(ServerPlayer player) {
        if (player == null) return false;
        DEATH_BACK_LOCK_UNTIL.remove(player.getUUID());
        TeleportLocation loc = LAST.remove(player.getUUID());
        if (loc == null) return false;
        ServerLevel level = getLevel(player.server, loc.dimension);
        if (level == null) return false;
        if (!SafeTeleportManager.canTeleportTo(player, level, loc.x, loc.y, loc.z)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("That /back location is no longer safe or allowed.").withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        return SafeTeleportManager.teleportNoBack(player, level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
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
