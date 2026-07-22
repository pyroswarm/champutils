package com.champutils.buff;

import com.champutils.database.DatabaseManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.network.NetworkServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/** Network-wide temporary buff source shared by every Survival backend. */
public final class ServerBuffManager {
    private static final String STATE_KEY = "network_server_boosts_v2";
    private static final UUID INVALIDATION_OWNER = new UUID(0L, 0L);
    private static final Map<String, ActiveServerBuff> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<String, ActiveServerBoost> ACTIVE_BOOSTS = new ConcurrentHashMap<>();
    private static volatile boolean providerRegistered = false;

    public static final class SharedState {
        public Map<String, SharedBuff> buffs = new LinkedHashMap<>();
        public Map<String, SharedBoost> boosts = new LinkedHashMap<>();
    }

    public static final class SharedBuff {
        public String id;
        public String type;
        public double amount;
        public long expiresAt;
        public SharedBuff() {}
        SharedBuff(String id, BuffType type, double amount, long expiresAt) {
            this.id = id;
            this.type = type == null ? "" : type.name();
            this.amount = amount;
            this.expiresAt = expiresAt;
        }
    }

    public static final class SharedBoost {
        public String id;
        public String displayName;
        public String activatorName;
        public double amount;
        public long expiresAt;
        public SharedBoost() {}
        SharedBoost(String id, String displayName, String activatorName, double amount, long expiresAt) {
            this.id = id;
            this.displayName = displayName;
            this.activatorName = activatorName;
            this.amount = amount;
            this.expiresAt = expiresAt;
        }
    }

    private record ActivationMutation(SharedState state, boolean activated, SharedBoost existing) {}

    private static final BuffProvider PROVIDER = new BuffProvider() {
        @Override public String id() { return "server"; }
        @Override public int priority() { return 50; }
        @Override public double getBuff(BuffContext context, BuffType type) {
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

    public static synchronized void init() {
        if (!providerRegistered) {
            BuffManager.registerProvider(PROVIDER);
            providerRegistered = true;
        }
        refreshAsync();
    }

    public static void refreshAsync() {
        if (!DatabaseManager.isEnabled()) return;
        SharedJsonStateRepository.loadGlobalAsync(STATE_KEY, SharedState.class, new SharedState())
                .whenComplete((state, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    applySharedState(state == null ? new SharedState() : state);
                });
    }

    /**
     * Starts a visible server booster. The exclusivity check is transaction-locked in PostgreSQL,
     * so Nova and Eclipse cannot consume two copies of the same boost at the same time.
     */
    public static synchronized CompletableFuture<Boolean> tryBeginExclusiveBoostAsync(ServerPlayer activator, String id, String displayName, double amount, long durationMillis) {
        clearExpired();
        if (id == null || id.isBlank() || durationMillis <= 0L) return CompletableFuture.completedFuture(false);
        long now = System.currentTimeMillis();
        String key = id.toLowerCase(Locale.ROOT);
        String name = displayName == null || displayName.isBlank() ? id : displayName;
        String actor = activator == null ? "Console" : activator.getGameProfile().getName();

        if (!DatabaseManager.isEnabled()) {
            ActiveServerBoost existing = ACTIVE_BOOSTS.get(key);
            if (existing != null && existing.expiresAt > now) {
                sendAlreadyActive(activator, existing.displayName, existing.expiresAt - now);
                return CompletableFuture.completedFuture(false);
            }
            ACTIVE_BOOSTS.put(key, new ActiveServerBoost(id, name, actor, Math.max(0.0D, amount), now + durationMillis));
            syncSpecialSpawnBoosts();
            return CompletableFuture.completedFuture(true);
        }

        return SharedJsonStateRepository.mutateGlobalAsync(
                STATE_KEY,
                SharedState.class,
                new SharedState(),
                state -> {
                    sanitizeState(state);
                    clearExpired(state, now);
                    SharedBoost existing = state.boosts.get(key);
                    if (existing != null && existing.expiresAt > now) {
                        return new ActivationMutation(state, false, existing);
                    }
                    SharedBoost created = new SharedBoost(id, name, actor, Math.max(0.0D, amount), now + durationMillis);
                    state.boosts.put(key, created);
                    return new ActivationMutation(state, true, null);
                }
        ).handle((result, error) -> {
            if (error != null) {
                sendActivationFailure(activator);
                error.printStackTrace();
                return false;
            }
            if (result == null || !result.activated()) {
                SharedBoost existing = result == null ? null : result.existing();
                sendAlreadyActive(
                        activator,
                        existing == null ? name : existing.displayName,
                        existing == null ? 0L : Math.max(0L, existing.expiresAt - now)
                );
                return false;
            }
            applySharedState(result.state());
            publishInvalidation();
            return true;
        });
    }

    public static void activate(String id, BuffType type, double amount, long durationMillis) {
        if (id == null || id.isBlank() || type == null || amount <= 0.0D || durationMillis <= 0L) return;
        String key = id.toLowerCase(Locale.ROOT);
        long expiresAt = System.currentTimeMillis() + durationMillis;
        ACTIVE.put(key, new ActiveServerBuff(id, type, amount, expiresAt));
        if (!DatabaseManager.isEnabled()) return;

        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, SharedState.class, new SharedState(), state -> {
            sanitizeState(state);
            clearExpired(state, System.currentTimeMillis());
            state.buffs.put(key, new SharedBuff(id, type, amount, expiresAt));
            return state;
        }).whenComplete((state, error) -> {
            if (error != null) error.printStackTrace();
            else {
                applySharedState(state);
                publishInvalidation();
            }
        });
    }

    public static void activateAndAnnounce(MinecraftServer server, String id, BuffType type, double amount, long durationMillis) {
        activate(id, type, amount, durationMillis);
        if (server == null || type == null) return;
        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                server,
                Component.literal("[Boost] +" + BuffManager.percent(amount) + " " + type.displayName + " is now active!").withStyle(type.color)
        );
    }

    public static void deactivate(String id) {
        if (id == null || id.isBlank()) return;
        String key = id.toLowerCase(Locale.ROOT);
        ACTIVE.remove(key);
        if (!DatabaseManager.isEnabled()) return;
        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, SharedState.class, new SharedState(), state -> {
            sanitizeState(state);
            state.buffs.remove(key);
            return state;
        }).whenComplete((state, error) -> {
            if (error != null) error.printStackTrace();
            else {
                applySharedState(state);
                publishInvalidation();
            }
        });
    }

    public static void deactivateBoost(String boostId) {
        if (boostId == null || boostId.isBlank()) return;
        String key = boostId.toLowerCase(Locale.ROOT);
        ACTIVE_BOOSTS.remove(key);
        syncSpecialSpawnBoosts();
        if (!DatabaseManager.isEnabled()) return;
        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, SharedState.class, new SharedState(), state -> {
            sanitizeState(state);
            state.boosts.remove(key);
            return state;
        }).whenComplete((state, error) -> {
            if (error != null) error.printStackTrace();
            else {
                applySharedState(state);
                publishInvalidation();
            }
        });
    }

    public static void clearAllBoosters() {
        ACTIVE.clear();
        ACTIVE_BOOSTS.clear();
        syncSpecialSpawnBoosts();
        if (!DatabaseManager.isEnabled()) return;
        SharedJsonStateRepository.mutateGlobalAsync(STATE_KEY, SharedState.class, new SharedState(), state -> {
            sanitizeState(state);
            state.buffs.clear();
            state.boosts.clear();
            return state;
        }).whenComplete((state, error) -> {
            if (error != null) error.printStackTrace();
            else {
                applySharedState(state);
                publishInvalidation();
            }
        });
    }

    /** Only the primary gameplay backend sends global countdown notices. */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        clearExpired();
        if (!NetworkServerConfig.isAuthoritativeGameplayServer()) return;
        long now = System.currentTimeMillis();
        for (ActiveServerBoost boost : ACTIVE_BOOSTS.values()) {
            long remaining = Math.max(0L, boost.expiresAt - now);
            maybeBroadcastReminder(server, boost, remaining, 1);
        }
    }

    private static void maybeBroadcastReminder(MinecraftServer server, ActiveServerBoost boost, long remainingMillis, int minutes) {
        long threshold = minutes * 60_000L;
        if (remainingMillis <= 0L || remainingMillis > threshold) return;
        synchronized (boost.remindedMinutes) {
            if (!boost.remindedMinutes.add(minutes)) return;
        }
        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                server,
                Component.literal("[Boost] " + playerFacingBoostName(boost.displayName) + " has " + minutes + " minute" + (minutes == 1 ? "" : "s") + " left.").withStyle(boostColor(boost.id))
        );
    }

    private static void sendAlreadyActive(ServerPlayer activator, String name, long remaining) {
        if (activator == null) return;
        activator.server.execute(() -> {
            if (activator.hasDisconnected()) return;
            activator.sendSystemMessage(Component.literal(
                    "That booster is already active: " + name + ". Time remaining: " + formatDuration(remaining) + "."
            ).withStyle(ChatFormatting.RED));
        });
    }

    private static void sendActivationFailure(ServerPlayer activator) {
        if (activator == null) return;
        activator.server.execute(() -> {
            if (!activator.hasDisconnected()) {
                activator.sendSystemMessage(Component.literal("Could not activate that network boost right now.").withStyle(ChatFormatting.RED));
            }
        });
    }

    private static String playerFacingBoostName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "Boost";
        String clean = displayName.replaceAll("§.", "").trim();
        if (clean.toLowerCase(Locale.ROOT).startsWith("server ")) clean = clean.substring(7).trim();
        return clean.isBlank() ? "Boost" : clean;
    }

    private static ChatFormatting boostColor(String id) {
        String key = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (key.contains("shiny")) return ChatFormatting.LIGHT_PURPLE;
        if (key.contains("special") || key.contains("legend")) return ChatFormatting.GOLD;
        if (key.contains("paradox")) return ChatFormatting.DARK_PURPLE;
        if (key.contains("ultra")) return ChatFormatting.AQUA;
        if (key.contains("pokemon")) return ChatFormatting.BLUE;
        if (key.contains("mining")) return ChatFormatting.DARK_AQUA;
        if (key.contains("forestry")) return ChatFormatting.GREEN;
        if (key.contains("farming")) return ChatFormatting.YELLOW;
        if (key.contains("battling")) return ChatFormatting.RED;
        return ChatFormatting.GOLD;
    }

    public static synchronized void clearExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Map.Entry<String, ActiveServerBuff>> iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt <= now) {
                iterator.remove();
                changed = true;
            }
        }
        Iterator<Map.Entry<String, ActiveServerBoost>> boostIterator = ACTIVE_BOOSTS.entrySet().iterator();
        while (boostIterator.hasNext()) {
            if (boostIterator.next().getValue().expiresAt <= now) {
                boostIterator.remove();
                changed = true;
            }
        }
        if (changed) syncSpecialSpawnBoosts();
    }

    public static List<String> activeLines() {
        clearExpired();
        List<String> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ActiveServerBuff buff : ACTIVE.values()) {
            lines.add(buff.id + ": +" + BuffManager.percent(buff.amount) + " " + buff.type.displayName + " for " + Math.max(0L, (buff.expiresAt - now) / 1000L) + "s");
        }
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
        long minutes = total / 60L;
        long seconds = total % 60L;
        if (minutes >= 60L) return (minutes / 60L) + "h " + (minutes % 60L) + "m " + seconds + "s";
        return minutes + "m " + seconds + "s";
    }

    private static synchronized void applySharedState(SharedState state) {
        sanitizeState(state);
        clearExpired(state, System.currentTimeMillis());
        Map<String, ActiveServerBoost> previous = new ConcurrentHashMap<>(ACTIVE_BOOSTS);
        ACTIVE.clear();
        ACTIVE_BOOSTS.clear();

        for (Map.Entry<String, SharedBuff> entry : state.buffs.entrySet()) {
            SharedBuff shared = entry.getValue();
            if (shared == null || shared.expiresAt <= System.currentTimeMillis()) continue;
            try {
                BuffType type = BuffType.valueOf(shared.type);
                ACTIVE.put(entry.getKey(), new ActiveServerBuff(shared.id, type, shared.amount, shared.expiresAt));
            } catch (Exception ignored) {
            }
        }
        for (Map.Entry<String, SharedBoost> entry : state.boosts.entrySet()) {
            SharedBoost shared = entry.getValue();
            if (shared == null || shared.expiresAt <= System.currentTimeMillis()) continue;
            ActiveServerBoost active = new ActiveServerBoost(shared.id, shared.displayName, shared.activatorName, shared.amount, shared.expiresAt);
            ActiveServerBoost old = previous.get(entry.getKey());
            if (old != null && old.expiresAt == active.expiresAt) active.remindedMinutes.addAll(old.remindedMinutes);
            ACTIVE_BOOSTS.put(entry.getKey(), active);
        }
        syncSpecialSpawnBoosts();
    }

    private static void sanitizeState(SharedState state) {
        if (state.buffs == null) state.buffs = new LinkedHashMap<>();
        if (state.boosts == null) state.boosts = new LinkedHashMap<>();
    }

    private static void clearExpired(SharedState state, long now) {
        sanitizeState(state);
        state.buffs.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt <= now);
        state.boosts.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt <= now);
    }

    private static void syncSpecialSpawnBoosts() {
        long now = System.currentTimeMillis();
        syncSpecial("special_surge", now, 0);
        syncSpecial("paradox_surge", now, 1);
        syncSpecial("ultrabeast_surge", now, 2);
    }

    private static void syncSpecial(String id, long now, int type) {
        ActiveServerBoost boost = ACTIVE_BOOSTS.get(id);
        if (boost == null || boost.expiresAt <= now) {
            if (type == 0) com.champutils.specialspawn.SpecialWildSpawnManager.deactivateCashShopBoost();
            else if (type == 1) com.champutils.specialspawn.SpecialWildSpawnManager.deactivateParadoxCashShopBoost();
            else com.champutils.specialspawn.SpecialWildSpawnManager.deactivateUltraBeastCashShopBoost();
            return;
        }
        long remaining = Math.max(1L, boost.expiresAt - now);
        // These three boosters are defined as +100% spawn chance. Clamp legacy persisted
        // +50% records to the current value so every eligible spawn check is truly doubled.
        double effectiveAmount = Math.max(1.0D, boost.amount);
        if (type == 0) com.champutils.specialspawn.SpecialWildSpawnManager.activateCashShopBoost(effectiveAmount, remaining);
        else if (type == 1) com.champutils.specialspawn.SpecialWildSpawnManager.activateParadoxCashShopBoost(effectiveAmount, remaining);
        else com.champutils.specialspawn.SpecialWildSpawnManager.activateUltraBeastCashShopBoost(effectiveAmount, remaining);
    }

    private static void publishInvalidation() {
        NetworkEventManager.publishCacheInvalidation("SERVER_BUFFS", INVALIDATION_OWNER);
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
        boolean isExpired() { return System.currentTimeMillis() >= expiresAt; }
    }

    private static final class ActiveServerBoost {
        final String id;
        final String displayName;
        final String activatorName;
        final double amount;
        final long expiresAt;
        final java.util.Set<Integer> remindedMinutes = new HashSet<>();
        ActiveServerBoost(String id, String displayName, String activatorName, double amount, long expiresAt) {
            this.id = id;
            this.displayName = displayName;
            this.activatorName = activatorName;
            this.amount = amount;
            this.expiresAt = expiresAt;
        }
    }

    public record ActiveBoostView(String id, String displayName, String activatorName, double amount, long remainingMillis) {}
}
