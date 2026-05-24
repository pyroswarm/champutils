package com.champutils.territory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryBorderManager {
    private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();

    private TerritoryBorderManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null || !TerritoryConfig.get().enabled) return;
        if (server.getTickCount() % 10 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(4)) continue;
            ServerLevel level = player.serverLevel();
            if (!TerritoryRepository.isTerritoryWorld(level)) continue;

            BlockPos pos = player.blockPosition();
            TerritoryRepository.Territory current = TerritoryRepository.findAt(level, pos);

            if (current != null) {
                if (!TerritoryRepository.canEnter(player, current)) {
                    sendWarn(player, "You are not allowed inside " + current.ownerName + "'s territory.");
                    teleportToOwnTerritory(player);
                    continue;
                }
                if (current.lockBorder) {
                    // contains() already passed, but this keeps players away from the hard edge after movement packet rounding.
                    boolean nearOutside = pos.getX() <= current.minX || pos.getX() >= current.maxX || pos.getZ() <= current.minZ || pos.getZ() >= current.maxZ;
                    if (nearOutside) {
                        sendWarn(player, "You cannot leave this territory border.");
                        TerritoryTeleportUtil.teleportInside(player, current);
                    }
                }
                continue;
            }

            TerritoryRepository.Territory own = preferredHomeTerritory(player);
            if (own != null && own.lockBorder) {
                sendWarn(player, "You cannot leave your territory border.");
                TerritoryTeleportUtil.teleportHome(player, own);
            }
        }
    }

    private static TerritoryRepository.Territory preferredHomeTerritory(ServerPlayer player) {
        TerritoryRepository.Territory guild = TerritoryRepository.cachedGuildForPlayer(player);
        if (guild != null && guild.worldName.equalsIgnoreCase(player.serverLevel().dimension().location().toString())) return guild;
        TerritoryRepository.Territory personal = TerritoryRepository.cachedPersonal(player);
        if (personal != null && personal.worldName.equalsIgnoreCase(player.serverLevel().dimension().location().toString())) return personal;
        if (guild != null) return guild;
        return personal;
    }

    private static void teleportToOwnTerritory(ServerPlayer player) {
        TerritoryRepository.Territory own = preferredHomeTerritory(player);
        if (own != null && TerritoryTeleportUtil.teleportHome(player, own)) return;
        player.teleportTo(player.server.overworld(), player.server.overworld().getSharedSpawnPos().getX() + 0.5D, player.server.overworld().getSharedSpawnPos().getY(), player.server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
    }

    private static void sendWarn(ServerPlayer player, String message) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN.getOrDefault(player.getUUID(), 0L);
        long cooldown = TerritoryConfig.get().borderWarningCooldownSeconds * 1000L;
        if (now - last < cooldown) return;
        LAST_WARN.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
