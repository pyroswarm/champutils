package com.champutils.network;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

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

        int networkTotal = online.size();
        Component header = Component.literal("Cobble Champs").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nOnline: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(networkTotal)).withStyle(ChatFormatting.GREEN));

        Component footer = Component.empty();
        if (!online.isEmpty()) {
            int shown = 0;
            for (NetworkPlayerDirectory.OnlinePlayer player : online) {
                if (shown >= 30) break;
                if (shown > 0) footer = footer.copy().append(Component.literal("\n"));
                footer = footer.copy().append(tabLine(player));
                shown++;
            }
            if (networkTotal > shown) {
                footer = footer.copy()
                        .append(Component.literal("\n+" + (networkTotal - shown) + " more online").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        ClientboundTabListPacket packet = new ClientboundTabListPacket(header, footer);
        List<UUID> vanillaEntries = server.getPlayerList().getPlayers().stream()
                .map(ServerPlayer::getUUID)
                .toList();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            hideVanillaTabName(player);
            player.connection.send(packet);
            if (!vanillaEntries.isEmpty()) {
                player.connection.send(new ClientboundPlayerInfoRemovePacket(vanillaEntries));
            }
        }
    }

    private static MutableComponent tabLine(NetworkPlayerDirectory.OnlinePlayer player) {
        MutableComponent line = Component.empty();
        if (player.rankTag() != null && !player.rankTag().isBlank()) {
            line.append(com.champutils.chat.ChatTagResolver.legacy(player.rankTag())).append(Component.literal(" "));
        }
        if (player.titleTag() != null && !player.titleTag().isBlank()) {
            line.append(com.champutils.chat.ChatTagResolver.legacy(player.titleTag())).append(Component.literal(" "));
        }
        line.append(Component.literal(player.playerName()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(displayServer(player.serverId())).withStyle(ChatFormatting.YELLOW));
        return line;
    }

    private static String displayServer(String serverId) {
        if ("main_survival1".equalsIgnoreCase(serverId)) return "Alpha";
        if ("survival2".equalsIgnoreCase(serverId)) return "Omega";
        if ("profile_lobby".equalsIgnoreCase(serverId)) return "Lobby";
        return serverId;
    }

    private static void hideVanillaTabName(ServerPlayer player) {
        if (player == null) return;
        try {
            player.getClass().getMethod("setTabListDisplayName", Component.class).invoke(player, Component.literal(" "));
        } catch (Throwable ignored) {
        }
    }
}
