package com.champutils.teleport;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PortalManager {

    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final int COOLDOWN_TICKS = 60;

    private PortalManager() {
    }

    public static void tick(MinecraftServer server) {
        COOLDOWNS.entrySet().removeIf(entry -> {
            int next = entry.getValue() - 1;
            entry.setValue(next);
            return next <= 0;
        });

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (COOLDOWNS.containsKey(player.getUUID())) {
                continue;
            }

            for (PortalRegion portal : TeleportConfig.portals().values()) {
                if (!portal.contains(player)) {
                    continue;
                }

                runPortalCommand(player, portal.command);
                COOLDOWNS.put(player.getUUID(), COOLDOWN_TICKS);
                break;
            }
        }
    }

    public static boolean isAllowedPortalCommand(String command) {
        String lower = command.trim().toLowerCase();
        return lower.equals("rtp") || lower.equals("spawn") || lower.startsWith("warp ");
    }

    private static void runPortalCommand(ServerPlayer player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }

        if (!isAllowedPortalCommand(command)) {
            player.sendSystemMessage(Component.literal("This portal has an unsafe command configured. Ask an admin to fix it.").withStyle(ChatFormatting.RED));
            return;
        }

        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), command.startsWith("/") ? command : "/" + command);
    }
}
