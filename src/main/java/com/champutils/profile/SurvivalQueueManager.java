package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.teleport.SafeTeleportManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Keeps profile loading/transfer out of the hot path when the survival backend is full.
 * The queued action is only executed after a slot is confirmed, so profile data is not
 * prewarmed, transferred, or loaded until the player is actually allowed through.
 */
public final class SurvivalQueueManager {

    private static final Object LOCK = new Object();
    private static final PriorityQueue<QueueEntry> QUEUE = new PriorityQueue<>(
            Comparator.<QueueEntry>comparingInt(entry -> -entry.priority)
                    .thenComparingLong(entry -> entry.sequence)
    );
    private static final ConcurrentMap<UUID, QueueEntry> BY_PLAYER = new ConcurrentHashMap<>();
    private static long sequence = 0L;
    private static int tickCounter = 0;
    private static boolean registered = false;
    private static volatile boolean capacityCheckInFlight = false;

    private SurvivalQueueManager() {
    }

    private record QueueEntry(
            UUID playerId,
            String playerName,
            String profileName,
            String preferredTargetServerId,
            boolean allowFallback,
            int priority,
            long sequence,
            Consumer<String> action,
            Consumer<String> messageConsumer
    ) {
    }

    private record CapacityResult(boolean available, String targetServerId, int onlinePlayers, int cap) {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(SurvivalQueueManager::tick);
    }

    public static void enqueueOrRun(
            ServerPlayer player,
            String profileName,
            Consumer<String> action,
            Consumer<String> messageConsumer,
            Runnable onQueued
    ) {
        enqueueOrRun(player, profileName, null, true, action, messageConsumer, onQueued);
    }

    public static void enqueueOrRun(
            ServerPlayer player,
            String profileName,
            String preferredTargetServerId,
            boolean allowFallback,
            Consumer<String> action,
            Consumer<String> messageConsumer,
            Runnable onQueued
    ) {
        NetworkServerConfig config = NetworkServerConfig.get();
        if (!config.enableSurvivalQueue || config.survivalPlayerCap <= 0) {
            String target = targetOrDefault(preferredTargetServerId);
            runSafely(player, () -> action.accept(target));
            return;
        }

        if (player == null || player.server == null || action == null) {
            return;
        }

        if (isQueued(player.getUUID())) {
            send(messageConsumer, "You are already in the survival queue. Position: " + position(player.getUUID()) + ".");
            return;
        }

        checkCapacity(player.server, preferredTargetServerId, allowFallback, result -> {
            if (!isQueued(player.getUUID()) && result.available && isQueueEmpty()) {
                runSafely(player, () -> action.accept(result.targetServerId));
                return;
            }

            add(player, profileName, targetOrDefault(preferredTargetServerId), allowFallback, action, messageConsumer);
            if (onQueued != null) {
                onQueued.run();
            }
            int pos = position(player.getUUID());
            send(messageConsumer, PreferredSurvivalServerManager.displayName(result.targetServerId) + " is full at " + result.onlinePlayers + "/" + result.cap + ". You joined the queue at position #" + pos + ". VIP and VIP+ players have priority.");
        });
    }

    public static boolean leave(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        QueueEntry removed = BY_PLAYER.remove(player.getUUID());
        if (removed == null) {
            return false;
        }
        synchronized (LOCK) {
            QUEUE.remove(removed);
        }
        return true;
    }

    public static int position(UUID playerId) {
        if (playerId == null) {
            return 0;
        }
        synchronized (LOCK) {
            int index = 1;
            for (QueueEntry entry : QUEUE.stream().sorted(QUEUE.comparator()).toList()) {
                if (entry.playerId.equals(playerId)) {
                    return index;
                }
                index++;
            }
        }
        return 0;
    }

    private static boolean isQueued(UUID playerId) {
        return playerId != null && BY_PLAYER.containsKey(playerId);
    }

    private static boolean isQueueEmpty() {
        synchronized (LOCK) {
            return QUEUE.isEmpty();
        }
    }

    private static void add(ServerPlayer player, String profileName, String preferredTargetServerId, boolean allowFallback, Consumer<String> action, Consumer<String> messageConsumer) {
        QueueEntry entry = new QueueEntry(
                player.getUUID(),
                player.getGameProfile().getName(),
                profileName == null ? "profile" : profileName,
                targetOrDefault(preferredTargetServerId),
                allowFallback,
                priority(player),
                nextSequence(),
                action,
                messageConsumer
        );
        BY_PLAYER.put(player.getUUID(), entry);
        synchronized (LOCK) {
            QUEUE.add(entry);
        }
    }

    private static long nextSequence() {
        synchronized (LOCK) {
            return sequence++;
        }
    }

    private static int priority(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        if (player.hasPermissions(4)) {
            return 3;
        }
        if ((LuckPermsHook.hasPermissionCached(player, "champutils.queue.vipplus") || LuckPermsHook.hasGroupCached(player, "vip+") || LuckPermsHook.hasGroupCached(player, "vipplus"))) {
            return 2;
        }
        if (LuckPermsHook.hasPermissionCached(player, "champutils.queue.vip") || LuckPermsHook.hasGroupCached(player, "vip")) {
            return 1;
        }
        NetworkServerConfig config = NetworkServerConfig.get();
        String[] groups = config.survivalQueuePriorityGroups == null ? new String[0] : config.survivalQueuePriorityGroups;
        for (int i = 0; i < groups.length; i++) {
            String group = groups[i];
            if (group == null || group.isBlank()) {
                continue;
            }
            if (LuckPermsHook.hasGroupCached(player, group)) {
                return Math.max(1, groups.length - i);
            }
        }
        return 0;
    }

    private static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        NetworkServerConfig config = NetworkServerConfig.get();
        if (!config.enableSurvivalQueue || config.survivalPlayerCap <= 0) {
            return;
        }
        tickCounter++;
        int intervalTicks = Math.max(20, config.survivalQueuePollSeconds * 20);
        if (tickCounter < intervalTicks) {
            return;
        }
        tickCounter = 0;
        process(server);
    }

    private static void process(MinecraftServer server) {
        QueueEntry entry = peekLiveEntry(server);
        if (entry == null || capacityCheckInFlight) {
            return;
        }
        capacityCheckInFlight = true;
        checkCapacity(server, entry.preferredTargetServerId, entry.allowFallback, result -> {
            capacityCheckInFlight = false;
            if (!result.available) {
                notifyPositions(server);
                return;
            }
            QueueEntry selected = poll(entry.playerId);
            if (selected == null) {
                return;
            }
            ServerPlayer live = server.getPlayerList().getPlayer(selected.playerId);
            if (!SafeTeleportManager.isLive(live)) {
                return;
            }
            send(selected.messageConsumer, "A slot opened on " + PreferredSurvivalServerManager.displayName(result.targetServerId) + ". Loading " + selected.profileName + " now.");
            runSafely(live, () -> selected.action.accept(result.targetServerId));
        });
    }

    private static QueueEntry peekLiveEntry(MinecraftServer server) {
        while (true) {
            QueueEntry entry;
            synchronized (LOCK) {
                entry = QUEUE.peek();
            }
            if (entry == null) {
                return null;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId);
            if (SafeTeleportManager.isLive(player)) {
                return entry;
            }
            poll(entry.playerId);
        }
    }

    private static QueueEntry poll(UUID playerId) {
        QueueEntry removed = BY_PLAYER.remove(playerId);
        if (removed == null) {
            return null;
        }
        synchronized (LOCK) {
            QUEUE.remove(removed);
        }
        return removed;
    }

    private static void notifyPositions(MinecraftServer server) {
        synchronized (LOCK) {
            int index = 1;
            for (QueueEntry entry : QUEUE.stream().sorted(QUEUE.comparator()).toList()) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId);
                if (SafeTeleportManager.isLive(player)) {
                    player.displayClientMessage(Component.literal("Survival queue position #" + index).withStyle(ChatFormatting.YELLOW), true);
                }
                index++;
            }
        }
    }

    private static void checkCapacity(MinecraftServer server, Consumer<CapacityResult> callback) {
        checkCapacity(server, null, true, callback);
    }

    private static void checkCapacity(MinecraftServer server, String preferredTargetServerId, boolean allowFallback, Consumer<CapacityResult> callback) {
        NetworkServerConfig config = NetworkServerConfig.get();
        int configuredCap = Math.max(1, config.survivalPlayerCap);

        if (!DatabaseManager.isEnabled() || NetworkServerConfig.serverRole() != NetworkServerConfig.ServerRole.PROFILE_LOBBY) {
            int online = server == null ? 0 : server.getPlayerList().getPlayerCount();
            int localCap = Math.max(1, configuredCap);
            CapacityResult result = new CapacityResult(online < localCap, config.serverId, online, localCap);
            if (server != null) server.execute(() -> callback.accept(result));
            else callback.accept(result);
            return;
        }

        String target = targetOrDefault(preferredTargetServerId);
        String backendCsv = config.survivalBackendCsv();
        DatabaseManager.supplyAsync("check survival queue capacity", connection -> {
            CapacityResult exact = readCapacity(connection, target, backendCsv, configuredCap, true, false);
            if (exact != null && exact.available) {
                return exact;
            }

            if (!allowFallback) {
                if (exact != null) return exact;
                return new CapacityResult(true, target, 0, configuredCap);
            }

            // Prefer the configured survival backend, but if it is full, choose the least-filled
            // live SURVIVAL backend with room. This is what lets main_survival1 overflow to survival2.
            CapacityResult availableSurvival = readCapacity(connection, target, backendCsv, configuredCap, false, true);
            if (availableSurvival != null) {
                return availableSurvival;
            }

            if (exact != null) {
                return exact;
            }

            CapacityResult leastFilledSurvival = readCapacity(connection, target, backendCsv, configuredCap, false, false);
            if (leastFilledSurvival != null) {
                return leastFilledSurvival;
            }

            // No live survival heartbeat was found. Do not put players into a fake full queue;
            // allow the transfer attempt so a stale/missing heartbeat cannot soft-lock profiles.
            return new CapacityResult(true, target, 0, configuredCap);
        }).whenComplete((result, error) -> {
            Runnable complete = () -> {
                if (error != null || result == null) {
                    if (error != null) error.printStackTrace();
                    callback.accept(new CapacityResult(true, target, 0, configuredCap));
                    return;
                }
                callback.accept(result);
            };
            if (server != null) server.execute(complete);
            else complete.run();
        });
    }

    private static CapacityResult readCapacity(java.sql.Connection connection, String target, String backendCsv, int configuredCap, boolean exactTarget, boolean onlyWithRoom) throws java.sql.SQLException {
        String roomFilter = onlyWithRoom ? "and online_players < ? " : "";
        String backendFilter = backendCsv == null || backendCsv.isBlank()
                ? ""
                : "and server_id = any(string_to_array(?, ',')) ";
        String sql = exactTarget
                ? "select server_id, online_players, max_players from server_nodes " +
                        "where server_id = ? and upper(server_role) = 'SURVIVAL' " +
                        roomFilter +
                        "and last_heartbeat > now() - interval '30 seconds' " +
                        "order by last_heartbeat desc limit 1"
                : "select server_id, online_players, max_players from server_nodes " +
                        "where upper(server_role) = 'SURVIVAL' " +
                        backendFilter +
                        roomFilter +
                        "and last_heartbeat > now() - interval '30 seconds' " +
                        "order by online_players asc, last_heartbeat desc limit 1";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (exactTarget) {
                statement.setString(index++, target);
            }
            else if (backendCsv != null && !backendCsv.isBlank()) {
                statement.setString(index++, backendCsv);
            }
            if (onlyWithRoom) {
                statement.setInt(index, configuredCap);
            }
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String serverId = rs.getString(1);
                int online = Math.max(0, rs.getInt(2));
                int heartbeatMax = Math.max(0, rs.getInt(3));
                int cap = configuredCap > 0 ? configuredCap : heartbeatMax;
                if (cap <= 0) {
                    cap = Math.max(1, heartbeatMax);
                }
                return new CapacityResult(online < cap, serverId == null || serverId.isBlank() ? target : serverId, online, cap);
            }
        }
    }

    private static String targetOrDefault(String preferredTargetServerId) {
        if (preferredTargetServerId != null && PreferredSurvivalServerManager.isAllowedTarget(preferredTargetServerId)) {
            return PreferredSurvivalServerManager.toPreference(preferredTargetServerId)
                    .map(PreferredSurvivalServerManager.Preference::serverId)
                    .orElse(preferredTargetServerId.trim());
        }
        NetworkServerConfig config = NetworkServerConfig.get();
        return config.survivalServerId == null || config.survivalServerId.isBlank() ? "main_survival1" : config.survivalServerId.trim();
    }

    private static void runSafely(ServerPlayer player, Runnable action) {
        if (player == null || player.server == null || action == null) {
            return;
        }
        player.server.execute(() -> {
            if (!SafeTeleportManager.isLive(player)) {
                return;
            }
            action.run();
        });
    }

    private static void send(Consumer<String> consumer, String message) {
        if (consumer != null && message != null) {
            consumer.accept(message);
        }
    }
}
