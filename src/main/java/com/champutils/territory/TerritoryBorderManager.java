package com.champutils.territory;

import com.champutils.teleport.SafeTeleportManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TerritoryBorderManager {
    private static final Map<UUID, Long> LAST_WARN = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> LAST_ALLOWED_TERRITORY = new ConcurrentHashMap<>();
    private static int tickCounter = 0;

    private TerritoryBorderManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null || !TerritoryConfig.get().enabled) return;

        tickCounter++;
        if (tickCounter < 5) return;
        tickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) continue;

            ServerLevel level = player.serverLevel();
            if (!TerritoryRepository.isTerritoryWorld(level)) {
                LAST_ALLOWED_TERRITORY.remove(player.getUUID());
                continue;
            }

            BlockPos pos = player.blockPosition();
            TerritoryRepository.Territory current = TerritoryRepository.findAt(level, pos);

            if (current != null) {
                if (!TerritoryRepository.canEnter(player, current)) {
                    LAST_ALLOWED_TERRITORY.remove(player.getUUID());
                    stopPlayerMovement(player);
                    sendWarn(player, "You are not allowed inside " + current.ownerName + "'s territory.");
                    teleportToOwnTerritory(player);
                    continue;
                }

                LAST_ALLOWED_TERRITORY.put(player.getUUID(), current.id);

                if (current.lockBorder && isOutsidePrecise(player, current)) {
                    stopPlayerMovement(player);
                    sendWarn(player, "You cannot leave this territory border.");
                    TerritoryTeleportUtil.teleportInside(player, current);
                }
                continue;
            }

            TerritoryRepository.Territory lockedTerritory = lastAllowedTerritory(player);
            if (lockedTerritory == null) {
                lockedTerritory = nearestEnterableLockedTerritory(level, player);
            }
            if (lockedTerritory == null) {
                lockedTerritory = preferredHomeTerritory(player);
            }

            if (lockedTerritory != null && lockedTerritory.lockBorder) {
                stopPlayerMovement(player);
                sendWarn(player, "You cannot leave this territory border.");
                TerritoryTeleportUtil.teleportInside(player, lockedTerritory);
            }
        }
    }

    private static boolean isOutsidePrecise(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        int minBuildY = player.serverLevel().getMinBuildHeight();
        int maxBuildY = player.serverLevel().getMaxBuildHeight();

        return x < territory.minX + 0.05D
                || x > territory.maxX + 0.95D
                || z < territory.minZ + 0.05D
                || z > territory.maxZ + 0.95D
                // Barrier blocks can only be placed up to maxBuildHeight - 1. This catches ender pearls,
                // vehicles, elytra, or command/plugin teleports that put a player above the physical wall.
                || y < minBuildY + 0.05D
                || y >= maxBuildY - 0.05D;
    }

    private static TerritoryRepository.Territory lastAllowedTerritory(ServerPlayer player) {
        UUID territoryId = LAST_ALLOWED_TERRITORY.get(player.getUUID());
        if (territoryId == null) return null;
        TerritoryRepository.Territory territory = TerritoryRepository.get(territoryId);
        if (territory == null) return null;
        if (!territory.worldName.equalsIgnoreCase(player.serverLevel().dimension().location().toString())) return null;
        if (!TerritoryRepository.canEnter(player, territory)) return null;
        return territory;
    }

    private static TerritoryRepository.Territory nearestEnterableLockedTerritory(ServerLevel level, ServerPlayer player) {
        if (level == null || player == null) return null;
        double bestDistanceSq = Double.MAX_VALUE;
        TerritoryRepository.Territory best = null;

        for (TerritoryRepository.Territory territory : TerritoryRepository.cachedInWorld(level)) {
            if (!territory.lockBorder) continue;
            if (!TerritoryRepository.canEnter(player, territory)) continue;

            double clampedX = Math.max(territory.minX, Math.min(territory.maxX, player.getX()));
            double clampedZ = Math.max(territory.minZ, Math.min(territory.maxZ, player.getZ()));
            double dx = player.getX() - clampedX;
            double dz = player.getZ() - clampedZ;
            double distanceSq = dx * dx + dz * dz;

            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                best = territory;
            }
        }

        return best;
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
        SafeTeleportManager.teleportUncheckedNoBack(player, player.server.overworld(), player.server.overworld().getSharedSpawnPos().getX() + 0.5D, player.server.overworld().getSharedSpawnPos().getY(), player.server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
    }

    private static void stopPlayerMovement(ServerPlayer player) {
        try {
            player.stopRiding();
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
        } catch (Exception ignored) {
        }
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
