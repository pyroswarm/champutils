package com.champutils.buff;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-wide temporary buff source. */
public final class ServerBuffManager {
    private static final Map<String, ActiveServerBuff> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, ActiveServerBoost> ACTIVE_BOOSTS = new ConcurrentHashMap<>();
    private static final BuffProvider PROVIDER = new BuffProvider() {
        @Override public String id() { return "server"; }
        @Override public int priority() { return 50; }
        @Override public double getBuff(BuffContext context, BuffType type) {
            if (context == null || type == null || !context.allows(type)) return 0.0D;
            clearExpired();
            double total = 0.0D;
            for (ActiveServerBuff buff : ACTIVE.values()) if (buff.type == type && !buff.isExpired()) total += Math.max(0.0D, buff.amount);
            return total;
        }
    };

    private ServerBuffManager() {}
    public static void init() { BuffManager.registerProvider(PROVIDER); }

    /**
     * Starts a visible server booster. Different booster ids may run together, but the same
     * booster id cannot be started again until its current timer ends.
     */
    public static synchronized boolean tryBeginExclusiveBoost(ServerPlayer activator, String id, String displayName, double amount, long durationMillis) {
        clearExpired();
        if (id == null || id.isBlank() || durationMillis <= 0L) return false;
        long now = System.currentTimeMillis();
        String key = id.toLowerCase(Locale.ROOT);
        ActiveServerBoost existing = ACTIVE_BOOSTS.get(key);
        if (existing != null && existing.expiresAt > now) {
            if (activator != null) {
                long remaining = Math.max(0L, existing.expiresAt - now);
                activator.sendSystemMessage(Component.literal("That booster is already active: " + existing.displayName + ". Time remaining: " + formatDuration(remaining) + ".").withStyle(ChatFormatting.RED));
            }
            return false;
        }
        String name = displayName == null || displayName.isBlank() ? id : displayName;
        String actor = activator == null ? "Console" : activator.getGameProfile().getName();
        ACTIVE_BOOSTS.put(key, new ActiveServerBoost(id, name, actor, Math.max(0.0D, amount), now + durationMillis));
        return true;
    }

    public static void activate(String id, BuffType type, double amount, long durationMillis) {
        if (id == null || id.isBlank() || type == null || amount <= 0.0D || durationMillis <= 0L) return;
        ACTIVE.put(id.toLowerCase(Locale.ROOT), new ActiveServerBuff(id, type, amount, System.currentTimeMillis() + durationMillis));
    }

    public static void activateAndAnnounce(MinecraftServer server, String id, BuffType type, double amount, long durationMillis) {
        activate(id, type, amount, durationMillis);
        if (server == null || type == null) return;
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Server Boost] +" + BuffManager.percent(amount) + " " + type.displayName + " is now active!").withStyle(ChatFormatting.GOLD), false);
    }

    public static void deactivate(String id) {
        if (id == null || id.isBlank()) return;
        String key = id.toLowerCase(Locale.ROOT);
        ACTIVE.remove(key);
        ACTIVE_BOOSTS.remove(key);
    }

    public static void deactivateBoost(String boostId) {
        if (boostId == null || boostId.isBlank()) return;
        ACTIVE_BOOSTS.remove(boostId.toLowerCase(Locale.ROOT));
    }

    public static void clearAllBoosters() {
        ACTIVE.clear();
        ACTIVE_BOOSTS.clear();
    }

    public static synchronized void clearExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ActiveServerBuff>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) if (iterator.next().getValue().expiresAt <= now) iterator.remove();
        Iterator<Map.Entry<String, ActiveServerBoost>> boostIterator = ACTIVE_BOOSTS.entrySet().iterator();
        while (boostIterator.hasNext()) if (boostIterator.next().getValue().expiresAt <= now) boostIterator.remove();
    }

    public static List<String> activeLines() {
        clearExpired();
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ActiveServerBuff buff : ACTIVE.values()) lines.add(buff.id + ": +" + BuffManager.percent(buff.amount) + " " + buff.type.displayName + " for " + Math.max(0L, (buff.expiresAt - now) / 1000L) + "s");
        return lines;
    }

    public static ActiveBoostView activeBoostView() {
        List<ActiveBoostView> views = activeBoostViews();
        return views.isEmpty() ? null : views.get(0);
    }

    public static List<ActiveBoostView> activeBoostViews() {
        clearExpired();
        List<ActiveBoostView> views = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ActiveServerBoost boost : ACTIVE_BOOSTS.values()) {
            views.add(new ActiveBoostView(boost.id, boost.displayName, boost.activatorName, boost.amount, Math.max(0L, boost.expiresAt - now)));
        }
        views.sort((a, b) -> a.displayName().compareToIgnoreCase(b.displayName()));
        return views;
    }

    public static String formatDuration(long millis) {
        long total = Math.max(0L, millis / 1000L);
        long m = total / 60L, s = total % 60L;
        if (m >= 60L) return (m / 60L) + "h " + (m % 60L) + "m " + s + "s";
        return m + "m " + s + "s";
    }

    private static final class ActiveServerBuff { final String id; final BuffType type; final double amount; final long expiresAt; ActiveServerBuff(String id, BuffType type, double amount, long expiresAt){this.id=id;this.type=type;this.amount=amount;this.expiresAt=expiresAt;} boolean isExpired(){return System.currentTimeMillis() >= expiresAt;} }
    private static final class ActiveServerBoost { final String id, displayName, activatorName; final double amount; final long expiresAt; ActiveServerBoost(String id, String displayName, String activatorName, double amount, long expiresAt){this.id=id;this.displayName=displayName;this.activatorName=activatorName;this.amount=amount;this.expiresAt=expiresAt;} }
    public record ActiveBoostView(String id, String displayName, String activatorName, double amount, long remainingMillis) {}
}
