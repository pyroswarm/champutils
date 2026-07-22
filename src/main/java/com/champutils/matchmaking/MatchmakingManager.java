package com.champutils.matchmaking;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.teleport.SafeTeleportManager;

import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattlePrepManager;
import com.champutils.battle.BattleStateManager;
import com.champutils.config.Config;
import com.champutils.config.Rank;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.profile.ProfileRestrictions;
import com.champutils.profile.ProfileLoadingStateManager;
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

    private static final Map<UUID, PendingAcceptance> ACCEPTANCE =
            new HashMap<>();

    private static final Set<UUID> ACCEPTED_MATCH =
            new HashSet<>();

    private static final Map<UUID, RemotePendingAcceptance> REMOTE_ACCEPTANCE =
            new HashMap<>();

    private static final Set<UUID> STARTED_GLOBAL_SESSIONS =
            new HashSet<>();

    private static final Map<UUID, GlobalWaitingMatch> GLOBAL_WAITING_MATCHES =
            new HashMap<>();

    private static net.minecraft.server.MinecraftServer CURRENT_SERVER;

    private static class GlobalWaitingMatch {
        final GlobalMatchmakingRepository.Session session;
        final ArenaManager.Arena arena;
        final Set<UUID> announced = new HashSet<>();
        int ticksWaiting = 0;

        GlobalWaitingMatch(GlobalMatchmakingRepository.Session session, ArenaManager.Arena arena) {
            this.session = session;
            this.arena = arena;
        }
    }

    private static final int ACCEPT_TIMEOUT_TICKS = 30 * 20;
    private static final int MATCHMAKING_FAILURE_WINDOW_TICKS = 30 * 60 * 20;
    private static final int MATCHMAKING_BLOCK_TICKS = 30 * 60 * 20;
    private static final int MATCHMAKING_FAILURE_LIMIT = 3;
    private static final int FAKE_BATTLE_WINDOW_TICKS = 30 * 60 * 20;
    private static final int FAKE_BATTLE_BLOCK_TICKS = 60 * 60 * 20;
    private static final int FAKE_BATTLE_LIMIT = 3;

    private static final Map<UUID, List<Integer>> FAKE_BATTLE_FORFEITS =
            new HashMap<>();

    private static final Map<UUID, List<Integer>> MATCHMAKING_FAILURES =
            new HashMap<>();

    private static final Map<UUID, Integer> MATCHMAKING_BLOCKS =
            new HashMap<>();

    private static final int QUEUE_ANNOUNCE_TICKS = 3 * 60 * 20;
    private static int queueAnnounceTimer = 0;
    private static int globalMatchmakingTimer = 0;
    private static boolean globalMatchmakingPollInFlight = false;

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

    private static class RemotePendingAcceptance {
        final GlobalMatchmakingRepository.Session session;
        final UUID opponentId;
        final String opponentName;

        RemotePendingAcceptance(GlobalMatchmakingRepository.Session session, UUID opponentId, String opponentName) {
            this.session = session;
            this.opponentId = opponentId;
            this.opponentName = opponentName == null ? "opponent" : opponentName;
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

        GlobalMatchmakingRepository.saveReturnLocation(
                player.getUUID(),
                NetworkServerConfig.serverId(),
                player.serverLevel().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        );

        GlobalMatchmakingRepository.enqueue(
                player.getUUID(),
                PlayerProfileManager.activeProfileId(player),
                player.getGameProfile().getName(),
                type,
                NetworkServerConfig.serverId(),
                getRp(player),
                getRankIndex(player)
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
            ProfessionNotificationSettings.sendQueueNotification(
                    player.getServer(),
                    Component.literal("§dA trainer has entered the Ranked Queue!")
            );
        }
    }

    public static void leaveQueue(
            ServerPlayer player
    ) {

        if (player == null) return;
        UUID playerId = player.getUUID();

        for (List<ServerPlayer> q : QUEUES.values()) {
            q.removeIf(queued -> queued == null || playerId.equals(queued.getUUID()) || !SafeTeleportManager.isLive(queued));
        }

        QueueBossBarManager.stop(player);
        TeamSnapshotManager.clear(player);
        TeamPreviewManager.forceCleanup(player);
        ArenaManager.releaseArena(player);

        QUEUE_TIME.remove(playerId);
        PENDING_MATCH.remove(playerId);
        ACCEPTED_MATCH.remove(playerId);
        REMOTE_ACCEPTANCE.remove(playerId);
        GlobalMatchmakingRepository.leave(playerId);
        GlobalMatchmakingRepository.clearReturnLocation(playerId);

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

    public static int queueSize(String type) {
        List<ServerPlayer> queue = QUEUES.get(normalizeType(type));
        if (queue == null) return 0;
        queue.removeIf(player -> !SafeTeleportManager.isLive(player) || !player.isAlive());
        return queue.size();
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
        return SafeTeleportManager.isLive(player) &&
                player.isAlive() &&
                !BattleStateManager.isInBattle(player) &&
                !TeamPreviewManager.isInPreview(player) &&
                !PENDING_MATCH.contains(
                        player.getUUID()
                );
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        CURRENT_SERVER = server;
        tickTasks();
        tickGlobalWaitingMatches(server);
        tickAcceptance();
        tickQueues();
        tickGlobalMatchmaking();
        tickQueueAnnouncements();
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

            queue.removeIf(player -> !SafeTeleportManager.isLive(player) || !player.isAlive());

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

    private static void tickQueueAnnouncements() {
        queueAnnounceTimer++;
        if (queueAnnounceTimer < QUEUE_ANNOUNCE_TICKS) return;
        queueAnnounceTimer = 0;

        ServerPlayer anchor = firstQueuedPlayer();
        if (anchor == null || anchor.getServer() == null) return;

        announceQueue(anchor, "ranked");
        announceQueue(anchor, "casual");
    }

    private static void announceQueue(ServerPlayer anchor, String type) {
        int count = queueSize(type);
        if (count <= 0) return;
        ProfessionNotificationSettings.sendQueueNotification(
                anchor.getServer(),
                Component.literal("Queue up! Currently " + count + " player(s) in the " + type + " queue!")
        );
    }

    private static ServerPlayer firstQueuedPlayer() {
        for (List<ServerPlayer> queue : QUEUES.values()) {
            if (queue == null) continue;
            for (ServerPlayer player : queue) {
                if (SafeTeleportManager.isLive(player) && player.isAlive()) return player;
            }
        }
        return null;
    }

    private static net.minecraft.server.MinecraftServer firstKnownServer() {
        ServerPlayer player = firstQueuedPlayer();
        if (player != null) return player.getServer();
        for (PendingAcceptance pending : ACCEPTANCE.values()) {
            if (pending != null && SafeTeleportManager.isLive(pending.p1)) return pending.p1.getServer();
            if (pending != null && SafeTeleportManager.isLive(pending.p2)) return pending.p2.getServer();
        }
        return null;
    }

    private static void tickGlobalMatchmaking() {
        globalMatchmakingTimer++;
        if (globalMatchmakingTimer < 100 || globalMatchmakingPollInFlight) return;
        globalMatchmakingTimer = 0;

        net.minecraft.server.MinecraftServer server = CURRENT_SERVER != null ? CURRENT_SERVER : firstKnownServer();
        if (server == null) return;
        globalMatchmakingPollInFlight = true;
        // Each backend may claim a global session only while it currently has a free
        // local arena. Because all backends contend on the same locked queue rows, the
        // winning eligible backend is naturally distributed instead of hard-coded.
        String battleServer = NetworkServerConfig.serverId();
        boolean canHostBattle = ArenaManager.hasOpenArena();
        int allowedSpread = globalAllowedRankSpread();

        GlobalMatchmakingRepository.tick(battleServer, allowedSpread, canHostBattle)
                .thenCombine(GlobalMatchmakingRepository.acceptedForBattleServer(NetworkServerConfig.serverId()), (pending, accepted) -> {
                    List<GlobalMatchmakingRepository.Session> combined = new ArrayList<>();
                    if (pending != null) combined.addAll(pending);
                    if (accepted != null) combined.addAll(accepted);
                    return combined;
                })
                .thenCombine(GlobalMatchmakingRepository.returningForServer(NetworkServerConfig.serverId()), PollResult::new)
                .whenComplete((result, error) -> server.execute(() -> {
                    List<GlobalMatchmakingRepository.Session> sessions = result == null ? null : result.sessions;
                    List<GlobalMatchmakingRepository.ReturnLocation> returns = result == null ? null : result.returns;
                    globalMatchmakingPollInFlight = false;
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    if (sessions != null) {
                        for (GlobalMatchmakingRepository.Session session : sessions) {
                            if ("PENDING_ACCEPT".equalsIgnoreCase(session.status())) {
                                applyRemotePendingSession(server, session);
                            } else if ("ACCEPTED".equalsIgnoreCase(session.status())) {
                                handleAcceptedGlobalSession(server, session);
                            }
                        }
                    }
                    processReturningPlayers(server, returns);
                }));
    }

    private static final class PollResult {
        final List<GlobalMatchmakingRepository.Session> sessions;
        final List<GlobalMatchmakingRepository.ReturnLocation> returns;
        PollResult(List<GlobalMatchmakingRepository.Session> sessions, List<GlobalMatchmakingRepository.ReturnLocation> returns) {
            this.sessions = sessions;
            this.returns = returns;
        }
    }

    private static void processReturningPlayers(net.minecraft.server.MinecraftServer server, List<GlobalMatchmakingRepository.ReturnLocation> returns) {
        if (server == null || returns == null || returns.isEmpty()) return;
        for (GlobalMatchmakingRepository.ReturnLocation loc : returns) {
            ServerPlayer player = server.getPlayerList().getPlayer(loc.playerUuid());
            if (!isProfileReadyForGlobalBattle(player)) continue;
            if (ArenaManager.returnPlayerToStoredLocation(player, loc)) {
                player.setInvulnerable(false);
                PENDING_MATCH.remove(player.getUUID());
                GlobalMatchmakingRepository.clearReturnLocation(player.getUUID());
                player.sendSystemMessage(Component.literal("§aReturned to your original location."));
            }
        }
    }

    private static void applyRemotePendingSession(net.minecraft.server.MinecraftServer server, GlobalMatchmakingRepository.Session session) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!GlobalMatchmakingRepository.hasPlayer(session, player.getUUID())) continue;
            if (ACCEPTANCE.containsKey(player.getUUID())) continue;
            if (REMOTE_ACCEPTANCE.containsKey(player.getUUID())) {
                MatchAcceptanceMenu.ensureOpen(player, session.queueType());
                continue;
            }
            UUID opponent = GlobalMatchmakingRepository.opponent(session, player.getUUID());
            String opponentName = GlobalMatchmakingRepository.opponentName(session, player.getUUID());
            REMOTE_ACCEPTANCE.put(player.getUUID(), new RemotePendingAcceptance(session, opponent, opponentName));
            PENDING_MATCH.add(player.getUUID());
            removeFromLocalQueues(player);
            QueueBossBarManager.stop(player);
            QUEUE_TIME.remove(player.getUUID());
            sendMatchAcceptPrompt(player, session.queueType());
            player.sendSystemMessage(Component.literal("§7Opponent: §f" + opponentName));
        }
    }

    private static void handleAcceptedGlobalSession(net.minecraft.server.MinecraftServer server, GlobalMatchmakingRepository.Session session) {
        String battleServer = session.battleServerId() == null ? "" : session.battleServerId();
        if (!battleServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (GlobalMatchmakingRepository.hasPlayer(session, player.getUUID())) {
                    routeToGlobalBattleServer(player, session);
                }
            }
            return;
        }
        tryStartAcceptedGlobalSession(server, session);
    }

    private static void tryStartAcceptedGlobalSession(net.minecraft.server.MinecraftServer server, GlobalMatchmakingRepository.Session session) {
        if (session == null || STARTED_GLOBAL_SESSIONS.contains(session.id())) return;

        GlobalWaitingMatch waiting = GLOBAL_WAITING_MATCHES.get(session.id());
        if (waiting == null) {
            ServerPlayer p1 = server.getPlayerList().getPlayer(session.playerOneUuid());
            ServerPlayer p2 = server.getPlayerList().getPlayer(session.playerTwoUuid());
            if (!SafeTeleportManager.isLive(p1) && !SafeTeleportManager.isLive(p2)) return;

            ArenaManager.Arena arena = ArenaManager.reserveArenaForSession(
                    session.playerOneUuid(), session.playerTwoUuid(), p1, p2
            );
            if (arena == null) return;

            waiting = new GlobalWaitingMatch(session, arena);
            GLOBAL_WAITING_MATCHES.put(session.id(), waiting);
        }

        prepareWaitingPlayer(server.getPlayerList().getPlayer(session.playerOneUuid()), waiting, true);
        prepareWaitingPlayer(server.getPlayerList().getPlayer(session.playerTwoUuid()), waiting, false);
    }

    private static void tickGlobalWaitingMatches(net.minecraft.server.MinecraftServer server) {
        if (server == null || GLOBAL_WAITING_MATCHES.isEmpty()) return;
        Iterator<Map.Entry<UUID, GlobalWaitingMatch>> iterator = GLOBAL_WAITING_MATCHES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, GlobalWaitingMatch> entry = iterator.next();
            GlobalWaitingMatch waiting = entry.getValue();
            waiting.ticksWaiting++;

            ServerPlayer p1 = server.getPlayerList().getPlayer(waiting.session.playerOneUuid());
            ServerPlayer p2 = server.getPlayerList().getPlayer(waiting.session.playerTwoUuid());

            prepareWaitingPlayer(p1, waiting, true);
            prepareWaitingPlayer(p2, waiting, false);

            boolean p1Ready = isProfileReadyForGlobalBattle(p1);
            boolean p2Ready = isProfileReadyForGlobalBattle(p2);
            if (!p1Ready || !p2Ready) {
                if (waiting.ticksWaiting > 5 * 60 * 20) {
                    if (SafeTeleportManager.isLive(p1)) {
                        p1.sendSystemMessage(Component.literal("§cCross-server match timed out while waiting for both profiles to load."));
                        p1.setInvulnerable(false);
                        ArenaManager.returnPlayer(p1);
                        PENDING_MATCH.remove(p1.getUUID());
                    }
                    if (SafeTeleportManager.isLive(p2)) {
                        p2.sendSystemMessage(Component.literal("§cCross-server match timed out while waiting for both profiles to load."));
                        p2.setInvulnerable(false);
                        ArenaManager.returnPlayer(p2);
                        PENDING_MATCH.remove(p2.getUUID());
                    }
                    if (p1 != null) ArenaManager.releaseArena(p1);
                    else if (p2 != null) ArenaManager.releaseArena(p2);
                    GlobalMatchmakingRepository.markExpired(waiting.session.id());
                    iterator.remove();
                }
                continue;
            }

            // Last legality gate after both profiles are fully loaded and immediately before launch.
            // This catches party edits made during transfer/loading or after accepting the match.
            String p1Legality = TeamValidator.validate(p1, waiting.session.queueType());
            String p2Legality = TeamValidator.validate(p2, waiting.session.queueType());
            if (p1Legality != null || p2Legality != null) {
                cancelGlobalWaitingMatch(waiting, p1, p2, p1Legality, p2Legality);
                iterator.remove();
                continue;
            }

            if (!STARTED_GLOBAL_SESSIONS.add(waiting.session.id())) {
                iterator.remove();
                continue;
            }

            GlobalMatchmakingRepository.markStarted(waiting.session.id());
            p1.setInvulnerable(false);
            p2.setInvulnerable(false);
            REMOTE_ACCEPTANCE.remove(p1.getUUID());
            REMOTE_ACCEPTANCE.remove(p2.getUUID());
            PENDING_MATCH.add(p1.getUUID());
            PENDING_MATCH.add(p2.getUUID());
            iterator.remove();
            beginAcceptedMatch(p1, p2, waiting.session.queueType(), new ArrayList<>(), waiting.arena);
        }
    }

    private static void cancelGlobalWaitingMatch(
            GlobalWaitingMatch waiting,
            ServerPlayer p1,
            ServerPlayer p2,
            String p1Legality,
            String p2Legality
    ) {
        if (SafeTeleportManager.isLive(p1)) {
            p1.sendSystemMessage(Component.literal(p1Legality == null
                    ? "§cMatch canceled because your opponent's party is no longer legal."
                    : "§cMatch canceled because your party is no longer legal: " + p1Legality));
            p1.setInvulnerable(false);
            PENDING_MATCH.remove(p1.getUUID());
            returnPlayerAfterQueuedPvp(p1);
        }
        if (SafeTeleportManager.isLive(p2)) {
            p2.sendSystemMessage(Component.literal(p2Legality == null
                    ? "§cMatch canceled because your opponent's party is no longer legal."
                    : "§cMatch canceled because your party is no longer legal: " + p2Legality));
            p2.setInvulnerable(false);
            PENDING_MATCH.remove(p2.getUUID());
            returnPlayerAfterQueuedPvp(p2);
        }
        if (p1 != null) ArenaManager.releaseArena(p1);
        else if (p2 != null) ArenaManager.releaseArena(p2);
        REMOTE_ACCEPTANCE.remove(waiting.session.playerOneUuid());
        REMOTE_ACCEPTANCE.remove(waiting.session.playerTwoUuid());
        GlobalMatchmakingRepository.markExpired(waiting.session.id());
    }

    private static boolean isProfileReadyForGlobalBattle(ServerPlayer player) {
        return SafeTeleportManager.isLive(player)
                && player.isAlive()
                && PlayerProfileManager.hasActiveProfile(player)
                && !ProfileLoadingStateManager.isLoading(player)
                && !BattleStateManager.isInBattle(player)
                && !TeamPreviewManager.isInPreview(player);
    }

    private static void prepareWaitingPlayer(ServerPlayer player, GlobalWaitingMatch waiting, boolean firstSide) {
        if (!isProfileReadyForGlobalBattle(player)) return;
        ArenaManager.teleportPlayerToArenaSide(player, waiting.arena, firstSide);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
        player.setInvulnerable(true);
        PENDING_MATCH.add(player.getUUID());
        if (waiting.announced.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§eWaiting for other player and their profile to finish loading..."));
        }
    }

    public static boolean isWaitingForCrossServerMatch(ServerPlayer player) {
        if (player == null) return false;
        UUID id = player.getUUID();
        for (GlobalWaitingMatch waiting : GLOBAL_WAITING_MATCHES.values()) {
            if (waiting.session.playerOneUuid().equals(id) || waiting.session.playerTwoUuid().equals(id)) return true;
        }
        return false;
    }

    private static int globalAllowedRankSpread() {
        int initial = Config.matchmaking == null ? 0 : Math.max(0, Config.matchmaking.initial_rank_spread);
        int expand = Config.matchmaking == null ? 1 : Math.max(0, Config.matchmaking.expand_rank_spread);
        return initial + Math.max(1, expand * 2);
    }

    private static void removeFromLocalQueues(ServerPlayer player) {
        if (player == null) return;
        UUID playerId = player.getUUID();
        for (List<ServerPlayer> q : QUEUES.values()) {
            q.removeIf(queued -> queued == null || playerId.equals(queued.getUUID()));
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
                        ? 300
                        : Math.max(
                                300,
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

        if (!SafeTeleportManager.isLive(p1) || !SafeTeleportManager.isLive(p2)) {
            return;
        }

        PENDING_MATCH.add(p1.getUUID());
        PENDING_MATCH.add(p2.getUUID());

        queue.remove(p1);
        queue.remove(p2);
        GlobalMatchmakingRepository.leave(p1.getUUID());
        GlobalMatchmakingRepository.leave(p2.getUUID());

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
        beginAcceptedMatch(p1, p2, type, queue, null);
    }

    private static void beginAcceptedMatch(
            ServerPlayer p1,
            ServerPlayer p2,
            String type,
            List<ServerPlayer> queue,
            ArenaManager.Arena reservedArena
    ) {

        if (!SafeTeleportManager.isLive(p1) || !SafeTeleportManager.isLive(p2)) {
            cancelPendingMatch(p1, p2);
            return;
        }

        ArenaManager.Arena arena = reservedArena != null
                ? reservedArena
                : ArenaManager.reserveArena(p1, p2);

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

                            String p1Legality = TeamValidator.validate(p1, type);
                            String p2Legality = TeamValidator.validate(p2, type);
                            if (p1Legality != null || p2Legality != null) {
                                p1.sendSystemMessage(Component.literal(p1Legality == null
                                        ? "§cMatch canceled because your opponent's party is no longer legal."
                                        : "§cMatch canceled because your party is no longer legal: " + p1Legality));
                                p2.sendSystemMessage(Component.literal(p2Legality == null
                                        ? "§cMatch canceled because your opponent's party is no longer legal."
                                        : "§cMatch canceled because your party is no longer legal: " + p2Legality));
                                ArenaManager.returnPlayer(p1);
                                ArenaManager.returnPlayer(p2);
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
        return SafeTeleportManager.isLive(p1) &&
                SafeTeleportManager.isLive(p2) &&
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

        if (p1 != null) {
            BattleContextManager.clearContext(p1.getUUID());
            ArenaManager.releaseArena(p1);
            PENDING_MATCH.remove(p1.getUUID());
        }
        if (p2 != null) {
            BattleContextManager.clearContext(p2.getUUID());
            ArenaManager.releaseArena(p2);
            PENDING_MATCH.remove(p2.getUUID());
        }
    }

    public static boolean acceptMatch(ServerPlayer player) {
        if (!SafeTeleportManager.isLive(player)) return false;
        PendingAcceptance pending = ACCEPTANCE.get(player.getUUID());
        if (pending == null || pending.launched) {
            RemotePendingAcceptance remote = REMOTE_ACCEPTANCE.get(player.getUUID());
            if (remote != null) {
                return acceptRemoteMatch(player, remote);
            }
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
        MatchAcceptanceMenu.resolve(player);
        ServerPlayer other = pending.other(player);
        player.sendSystemMessage(Component.literal("§aMatch accepted. Waiting for opponent..."));
        if (SafeTeleportManager.isLive(other)) {
            other.sendSystemMessage(Component.literal("§eOpponent accepted the match."));
        }
        if ((!SafeTeleportManager.isLive(pending.p1) || !SafeTeleportManager.isLive(pending.p2))) {
            handleAcceptanceFailure(pending, !SafeTeleportManager.isLive(pending.p1) ? pending.p1 : pending.p2, "left the server");
            return true;
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
        if (!SafeTeleportManager.isLive(player)) return false;
        PendingAcceptance pending = ACCEPTANCE.get(player.getUUID());
        if (pending == null || pending.launched) {
            RemotePendingAcceptance remote = REMOTE_ACCEPTANCE.get(player.getUUID());
            if (remote != null) {
                return declineRemoteMatch(player, remote);
            }
            player.sendSystemMessage(Component.literal("§cYou do not have a match waiting for acceptance."));
            return false;
        }
        MatchAcceptanceMenu.resolve(player);
        recordMatchmakingFailure(player);
        handleAcceptanceFailure(pending, player, "declined");
        return true;
    }

    private static boolean acceptRemoteMatch(ServerPlayer player, RemotePendingAcceptance remote) {
        if (remote == null || remote.session == null) return false;
        if (rankedType(remote.session.queueType())) {
            String error = TeamValidator.validate(player, remote.session.queueType());
            if (error != null) {
                recordMatchmakingFailure(player);
                declineRemoteMatch(player, remote);
                player.sendSystemMessage(Component.literal("§cMatch canceled because your team is illegal: " + error));
                return true;
            }
        }
        GlobalMatchmakingRepository.respond(remote.session.id(), player.getUUID(), true)
                .whenComplete((result, error) -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player)) return;
                    if (error != null || result == null || !result.recorded()) {
                        player.sendSystemMessage(Component.literal("§cCould not accept that match. Please requeue."));
                        REMOTE_ACCEPTANCE.remove(player.getUUID());
                        PENDING_MATCH.remove(player.getUUID());
                        return;
                    }
                    MatchAcceptanceMenu.resolve(player);
                    player.sendSystemMessage(Component.literal("§aMatch accepted. Waiting for opponent..."));
                    if (result.bothAccepted()) {
                        routeToGlobalBattleServer(player, remote.session);
                    }
                }));
        return true;
    }

    private static boolean declineRemoteMatch(ServerPlayer player, RemotePendingAcceptance remote) {
        if (remote == null || remote.session == null) return false;
        MatchAcceptanceMenu.resolve(player);
        recordMatchmakingFailure(player);
        GlobalMatchmakingRepository.respond(remote.session.id(), player.getUUID(), false);
        REMOTE_ACCEPTANCE.remove(player.getUUID());
        PENDING_MATCH.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("§cMatch declined. You were removed from queue."));
        return true;
    }

    private static void routeToGlobalBattleServer(ServerPlayer player, GlobalMatchmakingRepository.Session session) {
        if (player == null || session == null) return;
        String battleServer = session.battleServerId() == null ? "" : session.battleServerId();
        if (battleServer.isBlank() || battleServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            player.sendSystemMessage(Component.literal("§aBoth players accepted. Preparing battle..."));
            return;
        }
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
        if (active == null) {
            player.sendSystemMessage(Component.literal("§cCould not route you to the battle server. Please requeue."));
            return;
        }
        player.sendSystemMessage(Component.literal("§aBoth players accepted. Sending you to the battle server..."));
        ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, battleServer, message -> {
            if (message != null && message.startsWith("Could not") && SafeTeleportManager.isLive(player)) {
                player.sendSystemMessage(Component.literal("§c" + message));
            }
        });
    }

    public static void returnPlayerAfterQueuedPvp(ServerPlayer player) {
        if (!SafeTeleportManager.isLive(player)) return;
        UUID playerId = player.getUUID();
        net.minecraft.server.MinecraftServer server = player.getServer();
        GlobalMatchmakingRepository.markReturning(playerId);
        GlobalMatchmakingRepository.getReturnLocation(playerId).whenComplete((loc, error) -> {
            if (server == null) return;
            server.execute(() -> {
                if (error != null || loc == null || !SafeTeleportManager.isLive(player)) {
                    ArenaManager.returnPlayer(player);
                    return;
                }
                String current = NetworkServerConfig.serverId();
                if (loc.originalServerId() == null || loc.originalServerId().isBlank() || loc.originalServerId().equalsIgnoreCase(current)) {
                    if (ArenaManager.returnPlayerToStoredLocation(player, loc)) {
                        GlobalMatchmakingRepository.clearReturnLocation(playerId);
                    } else {
                        ArenaManager.returnPlayer(player);
                    }
                    return;
                }
                PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
                if (active == null) {
                    player.sendSystemMessage(Component.literal("§cCould not return to your original server because your profile was unavailable."));
                    return;
                }
                player.sendSystemMessage(Component.literal("§aReturning you to your original server and location..."));
                ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, loc.originalServerId(), message -> {
                    if (message != null && message.startsWith("Could not") && SafeTeleportManager.isLive(player)) {
                        player.sendSystemMessage(Component.literal("§c" + message));
                    }
                });
            });
        });
    }

    private static void tickAcceptance() {
        Set<PendingAcceptance> pendingSet = new HashSet<>(ACCEPTANCE.values());
        for (PendingAcceptance pending : pendingSet) {
            if (pending == null || pending.launched) continue;
            if (!SafeTeleportManager.isLive(pending.p1) || !SafeTeleportManager.isLive(pending.p2)) {
                ServerPlayer failed = !SafeTeleportManager.isLive(pending.p1) ? pending.p1 : pending.p2;
                handleAcceptanceFailure(pending, failed, "left the server");
                continue;
            }
            // Retry the GUI periodically. Network/container timing can occasionally drop the initial open packet.
            MatchAcceptanceMenu.ensureOpen(pending.p1, pending.type);
            MatchAcceptanceMenu.ensureOpen(pending.p2, pending.type);
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
        if (SafeTeleportManager.isLive(failed)) {
            failed.sendSystemMessage(Component.literal("§cMatch canceled because you " + reason + ". You were removed from queue."));
            if (isMatchmakingBlocked(failed)) {
                failed.sendSystemMessage(Component.literal("§cYou are blocked from matchmaking for 30 minutes after too many failed accepts or illegal teams."));
            }
        }
        if (SafeTeleportManager.isLive(other) && other.isAlive() && !BattleStateManager.isInBattle(other) && !isMatchmakingBlocked(other)) {
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
            pending.launched = true;
            ServerPlayer other = pending.other(player);
            if (other != null) ACCEPTANCE.remove(other.getUUID());
        }
    }

    private static void sendMatchAcceptPrompt(ServerPlayer player, String type) {
        if (!SafeTeleportManager.isLive(player)) return;
        sendTitle(player, "§aMatch Found!", "§eAccept within 30 seconds");
        ProfessionNotificationSettings.playSound(player, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.2f);
        MatchAcceptanceMenu.open(player, type);
    }


    /**
     * Records a forfeiter from a queued battle rejected by the shared PvP
     * integrity rules. Three rejected forfeits inside 30 minutes impose a
     * one-hour matchmaking cooldown. Acceptance failures are tracked separately.
     */
    public static void recordFakeBattleForfeit(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        List<Integer> forfeits = FAKE_BATTLE_FORFEITS.computeIfAbsent(uuid, k -> new ArrayList<>());
        forfeits.removeIf(ticks -> ticks == null || ticks <= 0);
        forfeits.add(FAKE_BATTLE_WINDOW_TICKS);

        int count = forfeits.size();
        if (count >= FAKE_BATTLE_LIMIT) {
            forfeits.clear();
            MATCHMAKING_BLOCKS.merge(uuid, FAKE_BATTLE_BLOCK_TICKS, Math::max);
            leaveQueue(player);
            player.sendSystemMessage(Component.literal(
                    "§cYou forfeited 3 fake matches within 30 minutes. Matchmaking is locked for 1 hour."
            ));
        } else {
            player.sendSystemMessage(Component.literal(
                    "§eFake-match forfeit warning: " + count + "/" + FAKE_BATTLE_LIMIT +
                            " within 30 minutes. Three causes a 1-hour matchmaking cooldown."
            ));
        }
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
        Iterator<Map.Entry<UUID, List<Integer>>> fakeBattleIt = FAKE_BATTLE_FORFEITS.entrySet().iterator();
        while (fakeBattleIt.hasNext()) {
            Map.Entry<UUID, List<Integer>> entry = fakeBattleIt.next();
            List<Integer> next = new ArrayList<>();
            for (Integer ticks : entry.getValue()) {
                if (ticks != null && ticks > 1) next.add(ticks - 1);
            }
            if (next.isEmpty()) fakeBattleIt.remove();
            else entry.setValue(next);
        }

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

        if (!SafeTeleportManager.isLive(p1) || !SafeTeleportManager.isLive(p2)) return;

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
        if (!SafeTeleportManager.isLive(player)) return;
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
