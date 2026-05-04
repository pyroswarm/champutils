package com.champutils.matchmaking;

import com.champutils.validation.TeamSnapshotManager;
import com.champutils.validation.TeamValidator;
import com.champutils.battle.BattleStateManager;
import com.champutils.rank.RankManager;
import com.champutils.config.Config;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.*;

public class MatchmakingManager {

    private static final Map<String,List<ServerPlayer>> QUEUES =
            new HashMap<>();

    private static final Map<UUID,String> MATCH_TYPE =
            new HashMap<>();

    private static final Map<UUID,UUID> OPPONENTS =
            new HashMap<>();

    private static final Map<UUID,Integer> QUEUE_TIME =
            new HashMap<>();

    private static final Map<UUID,Map<UUID,Integer>> RECENT_MATCHES =
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

    /*
     NEW PUBLIC METHOD
     Used by ActionBarManager
     */
    public static boolean isQueued(
            ServerPlayer player
    ) {
        return isInAnyQueue(player);
    }

    public static void joinQueue(
            ServerPlayer player,
            String type
    ) {

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

        QUEUE_TIME.putIfAbsent(
                player.getUUID(),
                0
        );

        if ("ranked".equalsIgnoreCase(type)) {
            TeamSnapshotManager.saveSnapshot(
                    player
            );
        }

        QueueBossBarManager.start(
                player,
                type
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
        /*
         Keep your existing tick logic exactly as-is
         (unchanged from your original file)
         */
    }

    private static int getElo(
            ServerPlayer player
    ) {

        var sb = player.getScoreboard();
        var obj = sb.getObjective("elo");

        if (obj == null) {
            return 0;
        }

        return sb.getOrCreatePlayerScore(
                player,
                obj
        ).get();
    }
}