package com.champutils.teleport;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PortalManager {

    private static final Map<UUID, Long> LAST_TRIGGER_MS = new HashMap<>();
    private static final long COOLDOWN_MS = 2500L;

    private PortalManager() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }

        long now = System.currentTimeMillis();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            long last = LAST_TRIGGER_MS.getOrDefault(player.getUUID(), 0L);
            if (now - last < COOLDOWN_MS) {
                continue;
            }

            for (PortalRegion portal : TeleportConfig.portals().values()) {
                if (portal != null && portal.contains(player)) {
                    LAST_TRIGGER_MS.put(player.getUUID(), now);
                    runPortalCommand(player, portal.command);
                    break;
                }
            }
        }
    }

    public static boolean isAllowedPortalCommand(String command) {
        if (command == null) {
            return false;
        }

        String cleaned = command.trim().toLowerCase();
        return cleaned.equals("rtp")
                || cleaned.equals("spawn")
                || cleaned.startsWith("warp ");
    }

    private static void runPortalCommand(ServerPlayer player, String command) {
        if (player == null || command == null || command.isBlank()) {
            return;
        }

        String cleaned = command.trim();
        if (!cleaned.startsWith("/")) {
            cleaned = "/" + cleaned;
        }

        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), cleaned);
    }
}
