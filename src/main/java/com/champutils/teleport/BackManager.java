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

    private BackManager() {}

    public static void remember(ServerPlayer player) {
        if (player == null) return;
        LAST.put(player.getUUID(), new TeleportLocation(
                player.serverLevel().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()
        ));
    }

    public static boolean teleportBack(ServerPlayer player) {
        if (player == null) return false;
        TeleportLocation loc = LAST.remove(player.getUUID());
        if (loc == null) return false;
        ServerLevel level = getLevel(player.server, loc.dimension);
        if (level == null) return false;
        if (!SafeTeleportManager.canTeleportTo(player, level, loc.x, loc.y, loc.z)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("That /back location is no longer safe or allowed.").withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        return SafeTeleportManager.teleport(player, level, loc.x, loc.y, loc.z, loc.yaw, loc.pitch);
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
