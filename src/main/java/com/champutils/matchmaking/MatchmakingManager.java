package com.champutils.matchmaking;

import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattlePrepManager;
import com.champutils.battle.BattleStateManager;
import com.champutils.config.Config;
import com.champutils.profile.PlayerDataManager;
import com.champutils.validation.TeamSnapshotManager;
import com.champutils.validation.TeamValidator;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MatchmakingManager {

    private static final Map<String, List<ServerPlayer>> QUEUES =
            new HashMap<>();

    private static final Map<UUID, String> MATCH_TYPE =
            new HashMap<>();

    private static final Map<UUID, UUID> OPPONENTS =
            new HashMap<>();

    private static final Map<UUID, Integer> QUEUE_TIME =
            new HashMap<>();

    private static final Map<UUID, Map<UUID, Integer>> RECENT_MATCHES =
            new HashMap<>();

    private static final List<DelayedTask> TASKS =
            new ArrayList<>();

    private static final Set<UUID> PENDING_MATCH =
            new HashSet<>();

    private static class DelayedTask {
        int ticks;
        Runnable action;

        DelayedTask(
                int ticks,
                Runnable action
        ) {
            this.ticks = ticks;
            this.action = action;
        }
    }

    public static boolean isQueued(
            ServerPlayer player
    ) {
        return isInAnyQueue(player);
    }

    public static void joinQueue(
            ServerPlayer player,
            String type
    ) {

        type = normalizeType(type);

        if (isInAnyQueue(player)) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cAlready in queue."
                    )
            );
            return;
        }

        if (BattleStateManager.isInBattle(player)) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cAlready in battle."
                    )
            );
            return;
        }

        if (TeamPreviewManager.isInPreview(player)) {
            return;
        }

        String error =
                TeamValidator.validate(
                        player,
                        type
                );

        if (error != null) {
            player.sendSystemMessage(
                    Component.literal(
                            "§c" + error
                    )
            );
            return;
        }

        QUEUES.computeIfAbsent(
                type,
                k -> new ArrayList<>()
        ).add(player);

        QUEUE_TIME.put(
                player.getUUID(),
                0
        );

        if (rankedType(type)) {
            TeamSnapshotManager.saveSnapshot(
                    player
            );
        }

        QueueBossBarManager.start(
                player,
                type
        );

        player.sendSystemMessage(
                Component.literal(
                        rankedType(type)
                                ? "§aJoined ranked queue."
                                : "§aJoined casual queue."
                )
        );
    }

    public static void leaveQueue(
            ServerPlayer player
    ) {

        for (List<ServerPlayer> q : QUEUES.values()) {
            q.remove(player);
        }

        QueueBossBarManager.stop(player);
        TeamSnapshotManager.clear(player);

        QUEUE_TIME.remove(
                player.getUUID()
        );

        PENDING_MATCH.remove(
                player.getUUID()
        );
    }

    public static boolean isRankedMatch(
            ServerPlayer player
    ) {
        return "ranked".equalsIgnoreCase(
                MATCH_TYPE.get(
                        player.getUUID()
                )
        );
    }

    public static boolean isMatchmadeBattle(
            ServerPlayer player
    ) {
        return player != null &&
                OPPONENTS.containsKey(
                        player.getUUID()
                );
    }

    public static ServerPlayer getOpponent(
            ServerPlayer player
    ) {

        if (player == null) {
            return null;
        }

        UUID opponentId =
                OPPONENTS.get(
                        player.getUUID()
                );

        if (opponentId == null) {
            return null;
        }

        if (player.getServer() == null) {
            return null;
        }

        return player.getServer()
                .getPlayerList()
                .getPlayer(opponentId);
    }

    public static void clearMatch(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        MATCH_TYPE.remove(
                player.getUUID()
        );

        OPPONENTS.remove(
                player.getUUID()
        );

        PENDING_MATCH.remove(
                player.getUUID()
        );
    }

    private static boolean rankedType(
            String type
    ) {
        return "ranked".equalsIgnoreCase(type);
    }

    private static String normalizeType(
            String type
    ) {
        return rankedType(type) ? "ranked" : "casual";
    }

    private static boolean isInAnyQueue(
            ServerPlayer player
    ) {

        for (List<ServerPlayer> q : QUEUES.values()) {
            if (q.contains(player)) {
                return true;
            }
        }

        return false;
    }

    private static boolean canMatch(
            ServerPlayer player
    ) {
        return player != null &&
                player.isAlive() &&
                !BattleStateManager.isInBattle(player) &&
                !TeamPreviewManager.isInPreview(player) &&
                !PENDING_MATCH.contains(
                        player.getUUID()
                );
    }

    public static void tick() {
        tickTasks();
        tickQueues();
        tickRecentMatches();
    }

    private static void tickTasks() {
        Iterator<DelayedTask> it =
                TASKS.iterator();

        while (it.hasNext()) {
            DelayedTask task =
                    it.next();

            task.ticks--;

            if (task.ticks <= 0) {
                it.remove();

                try {
                    task.action.run();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static void tickQueues() {
        for (String type : new ArrayList<>(QUEUES.keySet())) {
            List<ServerPlayer> queue =
                    QUEUES.get(type);

            if (queue == null) {
                continue;
            }

            queue.removeIf(player ->
                    player == null ||
                            player.getServer() == null ||
                            !player.isAlive()
            );

            for (ServerPlayer player : new ArrayList<>(queue)) {
                QUEUE_TIME.put(
                        player.getUUID(),
                        QUEUE_TIME.getOrDefault(
                                player.getUUID(),
                                0
                        ) + 1
                );
            }

            boolean matched;

            do {
                matched = tryMatchQueue(
                        type,
                        queue
                );
            }
            while (matched);
        }
    }

    private static boolean tryMatchQueue(
            String type,
            List<ServerPlayer> queue
    ) {

        if (queue.size() < 2) {
            return false;
        }

        for (ServerPlayer p1 : new ArrayList<>(queue)) {
            if (!queue.contains(p1) || !canMatch(p1)) {
                continue;
            }

            ServerPlayer p2 =
                    findBestOpponent(
                            p1,
                            type,
                            queue
                    );

            if (p2 == null) {
                continue;
            }

            startMatch(
                    p1,
                    p2,
                    type,
                    queue
            );

            return true;
        }

        return false;
    }

    private static ServerPlayer findBestOpponent(
            ServerPlayer player,
            String type,
            List<ServerPlayer> queue
    ) {

        ServerPlayer best = null;
        int bestScore = Integer.MAX_VALUE;

        for (ServerPlayer candidate : new ArrayList<>(queue)) {
            if (candidate == player) {
                continue;
            }

            if (!canMatch(candidate)) {
                continue;
            }

            if (!canPair(
                    player,
                    candidate,
                    type
            )) {
                continue;
            }

            int score =
                    Math.abs(
                            getRp(player) - getRp(candidate)
                    );

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    private static boolean canPair(
            ServerPlayer p1,
            ServerPlayer p2,
            String type
    ) {

        if (!rankedType(type)) {
            return true;
        }

        if (recentlyMatched(p1, p2)) {
            return false;
        }

        int diff =
                Math.abs(
                        getRp(p1) - getRp(p2)
                );

        return diff <= getAllowedRange(p1, p2);
    }

    private static int getAllowedRange(
            ServerPlayer p1,
            ServerPlayer p2
    ) {

        int initialRange =
                Config.matchmaking == null
                        ? 100
                        : Math.max(
                                0,
                                Config.matchmaking.initial_range
                        );

        int expandRange =
                Config.matchmaking == null
                        ? 100
                        : Math.max(
                                0,
                                Config.matchmaking.expand_range
                        );

        int expandSeconds =
                Config.matchmaking == null
                        ? 30
                        : Math.max(
                                1,
                                Config.matchmaking.expand_time_seconds
                        );

        int waitedTicks =
                Math.max(
                        QUEUE_TIME.getOrDefault(
                                p1.getUUID(),
                                0
                        ),
                        QUEUE_TIME.getOrDefault(
                                p2.getUUID(),
                                0
                        )
                );

        int expansions =
                waitedTicks / (expandSeconds * 20);

        return initialRange +
                (expandRange * expansions);
    }

    private static boolean recentlyMatched(
            ServerPlayer p1,
            ServerPlayer p2
    ) {
        Map<UUID, Integer> map =
                RECENT_MATCHES.get(
                        p1.getUUID()
                );

        return map != null &&
                map.getOrDefault(
                        p2.getUUID(),
                        0
                ) > 0;
    }

    private static void rememberMatch(
            ServerPlayer p1,
            ServerPlayer p2,
            String type
    ) {
        if (!rankedType(type)) {
            return;
        }

        int seconds =
                Config.matchmaking == null
                        ? 60
                        : Math.max(
                                0,
                                Config.matchmaking.rematch_cooldown_seconds
                        );

        int ticks = seconds * 20;

        if (ticks <= 0) {
            return;
        }

        RECENT_MATCHES.computeIfAbsent(
                p1.getUUID(),
                k -> new HashMap<>()
        ).put(
                p2.getUUID(),
                ticks
        );

        RECENT_MATCHES.computeIfAbsent(
                p2.getUUID(),
                k -> new HashMap<>()
        ).put(
                p1.getUUID(),
                ticks
        );
    }

    private static void tickRecentMatches() {
        Iterator<Map.Entry<UUID, Map<UUID, Integer>>> outer =
                RECENT_MATCHES.entrySet().iterator();

        while (outer.hasNext()) {
            Map<UUID, Integer> opponents =
                    outer.next().getValue();

            Iterator<Map.Entry<UUID, Integer>> inner =
                    opponents.entrySet().iterator();

            while (inner.hasNext()) {
                Map.Entry<UUID, Integer> entry =
                        inner.next();

                int next = entry.getValue() - 1;

                if (next <= 0) {
                    inner.remove();
                }
                else {
                    entry.setValue(next);
                }
            }

            if (opponents.isEmpty()) {
                outer.remove();
            }
        }
    }

    private static void startMatch(
            ServerPlayer p1,
            ServerPlayer p2,
            String type,
            List<ServerPlayer> queue
    ) {

        type = normalizeType(type);

        PENDING_MATCH.add(p1.getUUID());
        PENDING_MATCH.add(p2.getUUID());

        queue.remove(p1);
        queue.remove(p2);

        QueueBossBarManager.stop(p1);
        QueueBossBarManager.stop(p2);

        QUEUE_TIME.remove(p1.getUUID());
        QUEUE_TIME.remove(p2.getUUID());

        ArenaManager.Arena arena =
                ArenaManager.reserveArena(
                        p1,
                        p2
                );

        if (arena == null) {
            PENDING_MATCH.remove(p1.getUUID());
            PENDING_MATCH.remove(p2.getUUID());

            queue.add(p1);
            queue.add(p2);

            QUEUE_TIME.put(p1.getUUID(), 0);
            QUEUE_TIME.put(p2.getUUID(), 0);

            QueueBossBarManager.start(p1, type);
            QueueBossBarManager.start(p2, type);

            p1.sendSystemMessage(
                    Component.literal(
                            "§eMatch found, but no arena is open. Waiting..."
                    )
            );

            p2.sendSystemMessage(
                    Component.literal(
                            "§eMatch found, but no arena is open. Waiting..."
                    )
            );
            return;
        }

        MATCH_TYPE.put(p1.getUUID(), type);
        MATCH_TYPE.put(p2.getUUID(), type);

        OPPONENTS.put(p1.getUUID(), p2.getUUID());
        OPPONENTS.put(p2.getUUID(), p1.getUUID());

        BattleContextManager.setContext(
                p1.getUUID(),
                rankedType(type)
                        ? BattleContextManager.BattleType.RANKED
                        : BattleContextManager.BattleType.CASUAL
        );

        BattleContextManager.setContext(
                p2.getUUID(),
                rankedType(type)
                        ? BattleContextManager.BattleType.RANKED
                        : BattleContextManager.BattleType.CASUAL
        );

        BattlePrepManager.healParty(p1);
        BattlePrepManager.healParty(p2);

        rememberMatch(
                p1,
                p2,
                type
        );

        sendMatchFound(
                p1,
                p2,
                type
        );

        TASKS.add(
                new DelayedTask(
                        40,
                        () -> {
                            if (!canStartPreview(p1, p2)) {
                                cancelPendingMatch(p1, p2);
                                return;
                            }

                            ArenaManager.teleportPlayersToArena(
                                    p1,
                                    p2,
                                    arena
                            );

                            TeamPreviewManager.startPreview(
                                    p1,
                                    p2
                            );

                            PENDING_MATCH.remove(p1.getUUID());
                            PENDING_MATCH.remove(p2.getUUID());
                        }
                )
        );
    }

    private static boolean canStartPreview(
            ServerPlayer p1,
            ServerPlayer p2
    ) {
        return p1 != null &&
                p2 != null &&
                p1.getServer() != null &&
                p2.getServer() != null &&
                p1.isAlive() &&
                p2.isAlive() &&
                !BattleStateManager.isInBattle(p1) &&
                !BattleStateManager.isInBattle(p2) &&
                !TeamPreviewManager.isInPreview(p1) &&
                !TeamPreviewManager.isInPreview(p2);
    }

    private static void cancelPendingMatch(
            ServerPlayer p1,
            ServerPlayer p2
    ) {
        clearMatch(p1);
        clearMatch(p2);

        BattleContextManager.clearContext(
                p1.getUUID()
        );

        BattleContextManager.clearContext(
                p2.getUUID()
        );

        ArenaManager.releaseArena(p1);
        ArenaManager.releaseArena(p2);

        PENDING_MATCH.remove(p1.getUUID());
        PENDING_MATCH.remove(p2.getUUID());
    }

    private static void sendMatchFound(
            ServerPlayer p1,
            ServerPlayer p2,
            String type
    ) {

        sendTitle(
                p1,
                "§aMatch Found!",
                "§7Opponent: §f" + p2.getName().getString()
        );

        sendTitle(
                p2,
                "§aMatch Found!",
                "§7Opponent: §f" + p1.getName().getString()
        );

        p1.playNotifySound(
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                1.0f,
                1.2f
        );

        p2.playNotifySound(
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                1.0f,
                1.2f
        );

        p1.sendSystemMessage(
                Component.literal(
                        "§aMatch found against §f" +
                                p2.getName().getString() +
                                "§a."
                )
        );

        p2.sendSystemMessage(
                Component.literal(
                        "§aMatch found against §f" +
                                p1.getName().getString() +
                                "§a."
                )
        );
    }

    private static void sendTitle(
            ServerPlayer player,
            String title,
            String subtitle
    ) {
        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        5,
                        40,
                        10
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        Component.literal(title)
                )
        );

        player.connection.send(
                new ClientboundSetSubtitleTextPacket(
                        Component.literal(subtitle)
                )
        );
    }

    private static int getRp(
            ServerPlayer player
    ) {
        return PlayerDataManager.getRp(
                player.getUUID(),
                player.getName().getString()
        );
    }
}
