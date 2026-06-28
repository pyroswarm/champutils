package com.champutils.matchmaking;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattlePrepManager;
import com.champutils.battle.BattleStateManager;
import com.champutils.config.Config;
import com.champutils.config.Rank;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.ProfileRestrictions;
import com.champutils.validation.TeamSnapshotManager;
import com.champutils.validation.TeamValidator;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
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

    private static final Map<UUID, PendingAcceptance> ACCEPTANCE =
            new HashMap<>();

    private static final Set<UUID> ACCEPTED_MATCH =
            new HashSet<>();

    private static final int ACCEPT_TIMEOUT_TICKS = 30 * 20;
    private static final int MATCHMAKING_FAILURE_WINDOW_TICKS = 30 * 60 * 20;
    private static final int MATCHMAKING_BLOCK_TICKS = 30 * 60 * 20;
    private static final int MATCHMAKING_FAILURE_LIMIT = 3;

    private static final Map<UUID, List<Integer>> MATCHMAKING_FAILURES =
            new HashMap<>();

    private static final Map<UUID, Integer> MATCHMAKING_BLOCKS =
            new HashMap<>();

    private static class PendingAcceptance {
        final ServerPlayer p1;
        final ServerPlayer p2;
        final String type;
        final List<ServerPlayer> queue;
        int ticksLeft = ACCEPT_TIMEOUT_TICKS;
        boolean launched = false;

        PendingAcceptance(ServerPlayer p1, ServerPlayer p2, String type, List<ServerPlayer> queue) {
            this.p1 = p1;
            this.p2 = p2;
            this.type = type;
            this.queue = queue;
        }

        ServerPlayer other(ServerPlayer player) {
            if (player == null) return null;
            return player.getUUID().equals(p1.getUUID()) ? p2 : p1;
        }
    }

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

        if (ProfileRestrictions.blockPvp(player, "PvP queues")) {
            return;
        }

        if (isMatchmakingBlocked(player)) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cYou are blocked from matchmaking for " + formatRemainingBlock(player) + "."
                    )
            );
            return;
        }

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

        if (rankedType(type) && player.getServer() != null) {
            player.getServer().getPlayerList().broadcastSystemMessage(
                    Component.literal("§dA trainer has entered the Ranked Queue!"),
                    false
            );
        }
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

        clearAcceptance(player);
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
        clearAcceptance(player);
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
        tickAcceptance();
        tickQueues();
        tickRecentMatches();
        tickMatchmakingPenalties();
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

        int rankDiff =
                Math.abs(
                        getRankIndex(p1) - getRankIndex(p2)
                );

        return rankDiff <= getAllowedRankSpread(p1, p2);
    }

    private static int getAllowedRankSpread(
            ServerPlayer p1,
            ServerPlayer p2
    ) {

        int initialSpread =
                Config.matchmaking == null
                        ? 0
                        : Math.max(
                                0,
                                Config.matchmaking.initial_rank_spread
                        );

        int expandSpread =
                Config.matchmaking == null
                        ? 1
                        : Math.max(
                                0,
                                Config.matchmaking.expand_rank_spread
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

        return initialSpread +
                (expandSpread * expansions);
    }

    private static int getRankIndex(
            ServerPlayer player
    ) {

        int rp = getRp(player);
        int bestIndex = 0;
        int bestMin = Integer.MIN_VALUE;

        if (Config.ranks == null || Config.ranks.isEmpty()) {
            return 0;
        }

        for (int i = 0; i < Config.ranks.size(); i++) {
            Rank rank = Config.ranks.get(i);

            if (rank == null) {
                continue;
            }

            if (rp >= rank.min_elo && rank.min_elo >= bestMin) {
                bestMin = rank.min_elo;
                bestIndex = i;
            }
        }

        return bestIndex;
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

        PendingAcceptance pending = new PendingAcceptance(p1, p2, type, queue);
        ACCEPTANCE.put(p1.getUUID(), pending);
        ACCEPTANCE.put(p2.getUUID(), pending);
        ACCEPTED_MATCH.remove(p1.getUUID());
        ACCEPTED_MATCH.remove(p2.getUUID());

        sendMatchAcceptPrompt(p1, type);
        sendMatchAcceptPrompt(p2, type);
    }

    private static void beginAcceptedMatch(
            ServerPlayer p1,
            ServerPlayer p2,
            String type,
            List<ServerPlayer> queue
    ) {

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
        BattleContextManager.setFormatId(
                p1.getUUID(),
                type
        );

        BattleContextManager.setContext(
                p2.getUUID(),
                rankedType(type)
                        ? BattleContextManager.BattleType.RANKED
                        : BattleContextManager.BattleType.CASUAL
        );
        BattleContextManager.setFormatId(
                p2.getUUID(),
                type
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

    public static boolean acceptMatch(ServerPlayer player) {
        if (player == null) return false;
        PendingAcceptance pending = ACCEPTANCE.get(player.getUUID());
        if (pending == null || pending.launched) {
            player.sendSystemMessage(Component.literal("§cYou do not have a match waiting for acceptance."));
            return false;
        }
        if (rankedType(pending.type)) {
            String error = TeamValidator.validate(player, pending.type);
            if (error != null) {
                recordMatchmakingFailure(player);
                handleAcceptanceFailure(pending, player, "had an illegal team: " + error);
                return true;
            }
        }

        ACCEPTED_MATCH.add(player.getUUID());
        ServerPlayer other = pending.other(player);
        player.sendSystemMessage(Component.literal("§aMatch accepted. Waiting for opponent..."));
        if (other != null) {
            other.sendSystemMessage(Component.literal("§eOpponent accepted the match."));
        }
        if (ACCEPTED_MATCH.contains(pending.p1.getUUID()) && ACCEPTED_MATCH.contains(pending.p2.getUUID())) {
            pending.launched = true;
            ACCEPTANCE.remove(pending.p1.getUUID());
            ACCEPTANCE.remove(pending.p2.getUUID());
            ACCEPTED_MATCH.remove(pending.p1.getUUID());
            ACCEPTED_MATCH.remove(pending.p2.getUUID());
            beginAcceptedMatch(pending.p1, pending.p2, pending.type, pending.queue);
        }
        return true;
    }

    public static boolean declineMatch(ServerPlayer player) {
        if (player == null) return false;
        PendingAcceptance pending = ACCEPTANCE.get(player.getUUID());
        if (pending == null || pending.launched) {
            player.sendSystemMessage(Component.literal("§cYou do not have a match waiting for acceptance."));
            return false;
        }
        recordMatchmakingFailure(player);
        handleAcceptanceFailure(pending, player, "declined");
        return true;
    }

    private static void tickAcceptance() {
        Set<PendingAcceptance> pendingSet = new HashSet<>(ACCEPTANCE.values());
        for (PendingAcceptance pending : pendingSet) {
            if (pending == null || pending.launched) continue;
            pending.ticksLeft--;
            if (pending.ticksLeft <= 0) {
                ServerPlayer failed = !ACCEPTED_MATCH.contains(pending.p1.getUUID()) ? pending.p1 : pending.p2;
                recordMatchmakingFailure(failed);
                handleAcceptanceFailure(pending, failed, "did not accept in time");
            }
        }
    }

    private static void handleAcceptanceFailure(PendingAcceptance pending, ServerPlayer failed, String reason) {
        if (pending == null || pending.launched) return;
        pending.launched = true;
        ServerPlayer other = pending.other(failed);
        ACCEPTANCE.remove(pending.p1.getUUID());
        ACCEPTANCE.remove(pending.p2.getUUID());
        ACCEPTED_MATCH.remove(pending.p1.getUUID());
        ACCEPTED_MATCH.remove(pending.p2.getUUID());
        PENDING_MATCH.remove(pending.p1.getUUID());
        PENDING_MATCH.remove(pending.p2.getUUID());
        clearMatch(pending.p1);
        clearMatch(pending.p2);
        if (failed != null) {
            failed.sendSystemMessage(Component.literal("§cMatch canceled because you " + reason + ". You were removed from queue."));
            if (isMatchmakingBlocked(failed)) {
                failed.sendSystemMessage(Component.literal("§cYou are blocked from matchmaking for 30 minutes after too many failed accepts or illegal teams."));
            }
        }
        if (other != null && other.getServer() != null && other.isAlive() && !BattleStateManager.isInBattle(other) && !isMatchmakingBlocked(other)) {
            if (!pending.queue.contains(other)) {
                pending.queue.add(other);
            }
            QUEUE_TIME.put(other.getUUID(), 0);
            QueueBossBarManager.start(other, pending.type);
            other.sendSystemMessage(Component.literal("§eOpponent could not start the match. You are still searching."));
        }
    }

    private static void clearAcceptance(ServerPlayer player) {
        if (player == null) return;
        PendingAcceptance pending = ACCEPTANCE.remove(player.getUUID());
        ACCEPTED_MATCH.remove(player.getUUID());
        if (pending != null) {
            ServerPlayer other = pending.other(player);
            if (other != null) ACCEPTANCE.remove(other.getUUID());
        }
    }

    private static void sendMatchAcceptPrompt(ServerPlayer player, String type) {
        if (player == null) return;
        sendTitle(player, "§aMatch Found!", "§eAccept within 30 seconds");
        ProfessionNotificationSettings.playSound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        Component accept = Component.literal("§a[ACCEPT]").withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/queue accept")));
        Component deny = Component.literal("§c[DENY]").withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/queue deny")));
        player.sendSystemMessage(Component.literal("§aMatch found. ").append(accept).append(Component.literal(" ")).append(deny));
    }

    private static boolean isMatchmakingBlocked(ServerPlayer player) {
        if (player == null) return false;
        return MATCHMAKING_BLOCKS.getOrDefault(player.getUUID(), 0) > 0;
    }

    private static String formatRemainingBlock(ServerPlayer player) {
        int ticks = player == null ? 0 : MATCHMAKING_BLOCKS.getOrDefault(player.getUUID(), 0);
        int seconds = Math.max(1, (ticks + 19) / 20);
        int minutes = (seconds + 59) / 60;
        return minutes + " minute" + (minutes == 1 ? "" : "s");
    }

    private static void recordMatchmakingFailure(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        List<Integer> failures = MATCHMAKING_FAILURES.computeIfAbsent(uuid, k -> new ArrayList<>());
        failures.removeIf(ticks -> ticks <= 0);
        failures.add(MATCHMAKING_FAILURE_WINDOW_TICKS);
        if (failures.size() >= MATCHMAKING_FAILURE_LIMIT) {
            failures.clear();
            MATCHMAKING_BLOCKS.put(uuid, MATCHMAKING_BLOCK_TICKS);
            leaveQueue(player);
        }
    }

    private static void tickMatchmakingPenalties() {
        Iterator<Map.Entry<UUID, List<Integer>>> failureIt = MATCHMAKING_FAILURES.entrySet().iterator();
        while (failureIt.hasNext()) {
            Map.Entry<UUID, List<Integer>> entry = failureIt.next();
            List<Integer> next = new ArrayList<>();
            for (Integer ticks : entry.getValue()) {
                if (ticks != null && ticks > 1) {
                    next.add(ticks - 1);
                }
            }
            if (next.isEmpty()) {
                failureIt.remove();
            }
            else {
                entry.setValue(next);
            }
        }

        Iterator<Map.Entry<UUID, Integer>> blockIt = MATCHMAKING_BLOCKS.entrySet().iterator();
        while (blockIt.hasNext()) {
            Map.Entry<UUID, Integer> entry = blockIt.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                blockIt.remove();
            }
            else {
                entry.setValue(next);
            }
        }
    }

    private static void sendMatchFound(
            ServerPlayer p1,
            ServerPlayer p2,
            String type
    ) {

        if (ProfessionNotificationSettings.areQueueNotificationsEnabled(p1)) {
            sendTitle(
                    p1,
                    "§aMatch Found!",
                    "§7Opponent: §f" + p2.getName().getString()
            );
        }

        if (ProfessionNotificationSettings.areQueueNotificationsEnabled(p2)) {
            sendTitle(
                    p2,
                    "§aMatch Found!",
                    "§7Opponent: §f" + p1.getName().getString()
            );
        }

        if (ProfessionNotificationSettings.areQueueNotificationsEnabled(p1)) {
            ProfessionNotificationSettings.playSound(
                    p1,
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.2f
            );
        }

        if (ProfessionNotificationSettings.areQueueNotificationsEnabled(p2)) {
            ProfessionNotificationSettings.playSound(
                    p2,
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    1.0f,
                    1.2f
            );
        }

        if (ProfessionNotificationSettings.areQueueNotificationsEnabled(p1)) {
            p1.sendSystemMessage(
                    Component.literal(
                            "§aMatch found against §f" +
                                    p2.getName().getString() +
                                    "§a."
                    )
            );
        }

        if (ProfessionNotificationSettings.areQueueNotificationsEnabled(p2)) {
            p2.sendSystemMessage(
                    Component.literal(
                            "§aMatch found against §f" +
                                    p1.getName().getString() +
                                    "§a."
                    )
            );
        }
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
