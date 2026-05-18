package com.champutils.notifications;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class NotificationManager {

    private static final Set<UUID> CHECKING = ConcurrentHashMap.newKeySet();

    private NotificationManager() {}

    public static void handleJoin(ServerPlayer player) {
        checkAndDeliver(player, 5, false);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (server.getTickCount() <= 0 || server.getTickCount() % 400 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            checkAndDeliver(player, 5, true);
        }
    }

    public static void showLatest(ServerPlayer player) {
        if (player == null) return;
        CompletableFuture.supplyAsync(() -> {
            try { return NotificationRepository.fetchLatest(player.getUUID(), 10); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((notifications, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("Could not load notifications. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            if (notifications == null || notifications.isEmpty()) {
                player.sendSystemMessage(Component.literal("You have no notifications yet.").withStyle(ChatFormatting.YELLOW));
                return;
            }
            player.sendSystemMessage(Component.literal("━━━━ Cobble Champs Notifications ━━━━").withStyle(ChatFormatting.GOLD));
            for (NotificationRepository.PlayerNotification notification : notifications) {
                player.sendSystemMessage(Component.literal(notification.title).withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(Component.literal(notification.message).withStyle(ChatFormatting.GRAY)));
            }
        }));
    }

    private static void checkAndDeliver(ServerPlayer player, int limit, boolean quiet) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        if (!CHECKING.add(uuid)) return;

        CompletableFuture.supplyAsync(() -> {
            try { return NotificationRepository.fetchUndelivered(uuid, limit); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((notifications, error) -> player.server.execute(() -> {
            try {
                if (error != null) {
                    if (!quiet) player.sendSystemMessage(Component.literal("Could not check notifications.").withStyle(ChatFormatting.RED));
                    error.printStackTrace();
                    return;
                }
                if (notifications == null || notifications.isEmpty()) return;

                player.sendSystemMessage(Component.literal("━━━━ New Cobble Champs Notification" + (notifications.size() == 1 ? "" : "s") + " ━━━━").withStyle(ChatFormatting.GOLD));
                for (NotificationRepository.PlayerNotification notification : notifications) {
                    player.sendSystemMessage(Component.literal(notification.title).withStyle(ChatFormatting.AQUA)
                            .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(notification.message).withStyle(ChatFormatting.GRAY)));
                }
                try { NotificationRepository.markDelivered(notifications); }
                catch (Exception e) { e.printStackTrace(); }
            } finally {
                CHECKING.remove(uuid);
            }
        }));
    }
}
