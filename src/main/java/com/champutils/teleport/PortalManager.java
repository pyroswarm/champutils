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
        return cleaned.equals("spawn")
                || cleaned.startsWith("warp ")
                || isAllowedRtpPortalCommand(cleaned);
    }

    private static boolean isAllowedRtpPortalCommand(String cleaned) {
        if (cleaned.equals("rtp")) {
            return true;
        }

        String[] parts = cleaned.split("\\s+");
        if (parts.length != 3 || !parts[0].equals("rtp")) {
            return false;
        }

        boolean validCategory = parts[1].equals("survival");
        boolean validType = parts[2].equals("overworld") || parts[2].equals("nether") || parts[2].equals("end");
        return validCategory && validType;
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
