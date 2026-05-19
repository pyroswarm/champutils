package com.champutils.gym;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class GymCooldownManager {

    private static final Map<UUID, Long> LAST_GYM_WIN = new HashMap<>();

    private GymCooldownManager() {
    }

    public static boolean isOnCooldown(ServerPlayer player) {
        return remainingSeconds(player) > 0L;
    }

    public static long remainingSeconds(ServerPlayer player) {
        long cooldown = GymSettingsConfig.globalCooldownSeconds();

        if (cooldown <= 0L) {
            return 0L;
        }

        Long lastWin = LAST_GYM_WIN.get(player.getUUID());
        if (lastWin == null) {
            return 0L;
        }

        long elapsed = (System.currentTimeMillis() - lastWin) / 1000L;
        long remaining = cooldown - elapsed;

        return Math.max(0L, remaining);
    }

    public static void markGymCompleted(ServerPlayer player) {
        LAST_GYM_WIN.put(player.getUUID(), System.currentTimeMillis());
    }

    public static String formatRemaining(ServerPlayer player) {
        long seconds = remainingSeconds(player);

        if (seconds <= 0L) {
            return "Ready";
        }

        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder builder = new StringBuilder();

        if (days > 0) {
            builder.append(days).append("d ");
        }

        if (hours > 0) {
            builder.append(hours).append("h ");
        }

        if (minutes > 0) {
            builder.append(minutes).append("m ");
        }

        if (seconds > 0 || builder.length() == 0) {
            builder.append(seconds).append("s");
        }

        return builder.toString().trim();
    }
}
