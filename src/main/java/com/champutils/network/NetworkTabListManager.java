package com.champutils.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NetworkTabListManager {
    private static int tickCounter = 0;

    private NetworkTabListManager() {
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter < 100) return;
        tickCounter = 0;
        NetworkPlayerDirectory.syncLocalPlayers(server);
        update(server);
    }

    public static void update(MinecraftServer server) {
        if (server == null) return;
        Map<String, Integer> byServer = new LinkedHashMap<>();
        for (NetworkPlayerDirectory.OnlinePlayer player : NetworkPlayerDirectory.onlinePlayers()) {
            String serverId = player.serverId() == null || player.serverId().isBlank() ? "unknown" : player.serverId();
            byServer.merge(displayServer(serverId), 1, Integer::sum);
        }

        int networkTotal = byServer.values().stream().mapToInt(Integer::intValue).sum();
        int localTotal = server.getPlayerList().getPlayerCount();
        Component header = Component.literal("Cobble Champs").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nNetwork Online: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(networkTotal)).withStyle(ChatFormatting.GREEN));

        Component footer = Component.literal("This Server: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(localTotal)).withStyle(ChatFormatting.AQUA));
        if (!byServer.isEmpty()) {
            footer = footer.copy().append(Component.literal("\n"));
            boolean first = true;
            for (Map.Entry<String, Integer> entry : byServer.entrySet()) {
                if (!first) footer = footer.copy().append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
                first = false;
                footer = footer.copy()
                        .append(Component.literal(entry.getKey()).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.valueOf(entry.getValue())).withStyle(ChatFormatting.WHITE));
            }
        }
        String players = NetworkPlayerDirectory.onlinePlayers().stream()
                .limit(24)
                .map(player -> player.playerName() + " (" + displayServer(player.serverId()) + ")")
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        if (!players.isBlank()) {
            footer = footer.copy()
                    .append(Component.literal("\nPlayers: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(players).withStyle(ChatFormatting.WHITE));
            if (networkTotal > 24) {
                footer = footer.copy().append(Component.literal(" +" + (networkTotal - 24) + " more").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        ClientboundTabListPacket packet = new ClientboundTabListPacket(header, footer);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(packet);
        }
    }

    private static String displayServer(String serverId) {
        if ("main_survival1".equalsIgnoreCase(serverId)) return "Alpha";
        if ("survival2".equalsIgnoreCase(serverId)) return "Omega";
        if ("profile_lobby".equalsIgnoreCase(serverId)) return "Lobby";
        return serverId;
    }
}
