package com.champutils.buff;

import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-wide temporary buff source.
 *
 * This is the plug-in point for future cash shop items, weekend events, holiday
 * events, admin-started boosters, or timed community rewards. It provides buffs
 * for every player through the same BuffManager pipeline as guild buffs.
 */
public final class ServerBuffManager {
    private static final Map<String, ActiveServerBuff> ACTIVE = new ConcurrentHashMap<>();
    private static final BuffProvider PROVIDER = new BuffProvider() {
        @Override
        public String id() {
            return "server";
        }

        @Override
        public int priority() {
            return 50;
        }

        @Override
        public double getBuff(BuffContext context, BuffType type) {
            if (context == null || type == null || !context.allows(type)) return 0.0D;
            clearExpired();
            double total = 0.0D;
            for (ActiveServerBuff buff : ACTIVE.values()) {
                if (buff.type == type && !buff.isExpired()) total += Math.max(0.0D, buff.amount);
            }
            return total;
        }
    };

    private ServerBuffManager() {}

    public static void init() {
        BuffManager.registerProvider(PROVIDER);
    }

    /**
     * Activates or replaces a full-server temporary buff.
     *
     * @param id stable unique id, e.g. cash_shop_mining_xp
     * @param type shared buff type
     * @param amount decimal amount, e.g. 0.10 = +10%
     * @param durationMillis duration in milliseconds
     */
    public static void activate(String id, BuffType type, double amount, long durationMillis) {
        if (id == null || id.isBlank() || type == null || amount <= 0.0D || durationMillis <= 0L) return;
        long expiresAt = System.currentTimeMillis() + durationMillis;
        ACTIVE.put(id.toLowerCase(Locale.ROOT), new ActiveServerBuff(id, type, amount, expiresAt));
    }

    public static void activateAndAnnounce(MinecraftServer server, String id, BuffType type, double amount, long durationMillis) {
        activate(id, type, amount, durationMillis);
        if (server == null || type == null) return;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[Server Boost] +" + BuffManager.percent(amount) + " " + type.displayName + " is now active!")
                        .withStyle(ChatFormatting.GOLD),
                false
        );
    }

    public static void deactivate(String id) {
        if (id == null || id.isBlank()) return;
        ACTIVE.remove(id.toLowerCase(Locale.ROOT));
    }

    public static void clearExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ActiveServerBuff>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt <= now) iterator.remove();
        }
    }

    public static List<String> activeLines() {
        clearExpired();
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ActiveServerBuff buff : ACTIVE.values()) {
            long seconds = Math.max(0L, (buff.expiresAt - now) / 1000L);
            lines.add(buff.id + ": +" + BuffManager.percent(buff.amount) + " " + buff.type.displayName + " for " + seconds + "s");
        }
        return lines;
    }

    private static final class ActiveServerBuff {
        final String id;
        final BuffType type;
        final double amount;
        final long expiresAt;

        ActiveServerBuff(String id, BuffType type, double amount, long expiresAt) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
