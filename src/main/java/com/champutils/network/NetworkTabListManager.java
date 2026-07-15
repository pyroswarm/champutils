package com.champutils.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


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
        java.util.List<NetworkPlayerDirectory.OnlinePlayer> online = NetworkPlayerDirectory.onlinePlayers();
        String localServerId = NetworkServerConfig.serverId();
        Set<UUID> localPlayers = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID)
                .collect(Collectors.toSet());

        int networkTotal = online.size();
        Component header = Component.literal("Cobble Champs").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nOnline: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(networkTotal)).withStyle(ChatFormatting.GREEN));

        Component footer = remotePlayersFooter(online, localServerId, localPlayers);

        ClientboundTabListPacket packet = new ClientboundTabListPacket(header, footer);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Keep the real PlayerInfo entry alive so the client keeps GameProfile/skin data,
            // but make the vanilla row itself carry our rank/name/server formatting.
            setVanillaTabName(player, tabLine(player));
            player.connection.send(packet);
        }
    }

    private static Component remotePlayersFooter(java.util.List<NetworkPlayerDirectory.OnlinePlayer> online, String localServerId, Set<UUID> localPlayers) {
        MutableComponent footer = Component.empty();
        int shown = 0;
        for (NetworkPlayerDirectory.OnlinePlayer player : online) {
            if (player == null || localPlayers.contains(player.playerUuid())) continue;
            if (player.serverId() != null && player.serverId().equalsIgnoreCase(localServerId)) continue;
            if (shown == 0) {
                footer.append(Component.literal("\nOther Servers").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (shown >= 20) break;
            footer.append(Component.literal("\n")).append(tabLine(player));
            shown++;
        }
        int remoteTotal = (int) online.stream()
                .filter(p -> p != null && !localPlayers.contains(p.playerUuid()))
                .filter(p -> p.serverId() == null || !p.serverId().equalsIgnoreCase(localServerId))
                .count();
        if (remoteTotal > shown) {
            footer.append(Component.literal("\n+" + (remoteTotal - shown) + " more online").withStyle(ChatFormatting.DARK_GRAY));
        }
        return footer;
    }

    private static MutableComponent tabLine(ServerPlayer player) {
        MutableComponent line = Component.empty();
        String rankTag = com.champutils.chat.ChatTagResolver.donationRankLegacy(player);
        String titleTag = com.champutils.chat.ChatTagResolver.activeTitleLegacy(player);
        if (rankTag != null && !rankTag.isBlank()) {
            line.append(com.champutils.chat.ChatTagResolver.legacy(rankTag)).append(Component.literal(" "));
        }
        if (titleTag != null && !titleTag.isBlank()) {
            line.append(com.champutils.chat.ChatTagResolver.legacy(titleTag)).append(Component.literal(" "));
        }
        line.append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(displayServer(NetworkServerConfig.serverId())).withStyle(ChatFormatting.YELLOW));
        return line;
    }

    private static MutableComponent tabLine(NetworkPlayerDirectory.OnlinePlayer player) {
        MutableComponent line = Component.empty();
        String rankTag = player.rankTag();
        String titleTag = player.titleTag();
        if (rankTag != null && !rankTag.isBlank()) {
            line.append(com.champutils.chat.ChatTagResolver.legacy(rankTag)).append(Component.literal(" "));
        }
        if (titleTag != null && !titleTag.isBlank()) {
            line.append(com.champutils.chat.ChatTagResolver.legacy(titleTag)).append(Component.literal(" "));
        }
        line.append(Component.literal(player.playerName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(displayServer(player.serverId())).withStyle(ChatFormatting.YELLOW));
        return line;
    }

    private static String displayServer(String serverId) {
        if ("main_survival1".equalsIgnoreCase(serverId)) return "Nova";
        if ("survival2".equalsIgnoreCase(serverId)) return "Eclipse";
        if ("profile_lobby".equalsIgnoreCase(serverId)) return "Lobby";
        return serverId == null || serverId.isBlank() ? "Unknown" : serverId;
    }

    private static void setVanillaTabName(ServerPlayer player, Component displayName) {
        if (player == null) return;
        try {
            player.getClass().getMethod("setTabListDisplayName", Component.class).invoke(player, displayName);
        } catch (Throwable ignored) {
        }
    }
}
