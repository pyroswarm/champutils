package com.champutils.profile;

import com.champutils.rank.RankManager;
import com.champutils.config.Config;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;

import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

public class ProfileManager {

    public static void recordWin(
            ServerPlayer player,
            boolean ranked,
            boolean upsetWin
    ) {
        var data = PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        );

        if (ranked) {
            data.rankedWins++;
            setStat(player, "ranked_wins", data.rankedWins);

            data.currentStreak++;
            setStat(player, "current_streak", data.currentStreak);

            if (data.currentStreak > data.bestStreak) {
                data.bestStreak = data.currentStreak;
                setStat(player, "best_streak", data.bestStreak);
            }

            if (upsetWin) {
                data.upsetWins++;
                setStat(player, "upset_wins", data.upsetWins);
            }

            syncPeakRp(player);
            syncHighestRank(player);
        }
        else {
            data.casualWins++;
            setStat(player, "casual_wins", data.casualWins);
        }

        PlayerDataManager.save(
                player.getUUID(),
                data
        );
    }

    public static void recordLoss(
            ServerPlayer player,
            boolean ranked
    ) {
        var data = PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        );

        if (ranked) {
            data.rankedLosses++;
            data.currentStreak = 0;

            setStat(player, "ranked_losses", data.rankedLosses);
            setStat(player, "current_streak", 0);
        }
        else {
            data.casualLosses++;
            setStat(player, "casual_losses", data.casualLosses);
        }

        PlayerDataManager.save(
                player.getUUID(),
                data
        );
    }

    public static void syncPeakRp(ServerPlayer player) {
        int rp = getCurrentRp(player);

        var data = PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        );

        if (rp > data.peakRp) {
            data.peakRp = rp;
            setStat(player, "peak_rp", rp);

            PlayerDataManager.save(
                    player.getUUID(),
                    data
            );
        }
    }

    private static void syncHighestRank(ServerPlayer player) {
        int currentRank = getRankIndex(player);

        var data = PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        );

        if (currentRank > data.highestRank) {
            data.highestRank = currentRank;

            PlayerDataManager.save(
                    player.getUUID(),
                    data
            );
        }
    }

    public static int getCurrentRp(ServerPlayer player) {
        return getElo(player);
    }

    public static int getPeakRp(ServerPlayer player) {
        return PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        ).peakRp;
    }

    public static int getRankedWins(ServerPlayer player) {
        return PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        ).rankedWins;
    }

    public static int getRankedLosses(ServerPlayer player) {
        return PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        ).rankedLosses;
    }

    public static int getCurrentStreak(ServerPlayer player) {
        return PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        ).currentStreak;
    }

    public static int getBestStreak(ServerPlayer player) {
        return PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        ).bestStreak;
    }

    public static int getUpsetWins(ServerPlayer player) {
        return PlayerDataManager.load(
                player.getUUID(),
                player.getName().getString()
        ).upsetWins;
    }

    public static String getCurrentRankName(ServerPlayer player) {
        var rank = RankManager.getRank(getCurrentRp(player));
        return rank == null ? "Youngster" : rank.name;
    }

    public static void setElo(
            ServerPlayer player,
            int rp
    ) {
        var sb = player.getScoreboard();
        var obj = sb.getObjective("elo");

        if (obj == null) {
            obj = sb.addObjective(
                    "elo",
                    ObjectiveCriteria.DUMMY,
                    Component.literal("RP"),
                    RenderType.INTEGER,
                    false,
                    null
            );
        }

        sb.getOrCreatePlayerScore(
                player,
                obj
        ).set(rp);
    }

    private static int getElo(ServerPlayer player) {
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

    private static void setStat(
            ServerPlayer player,
            String stat,
            int value
    ) {
        Scoreboard sb = player.getScoreboard();

        Objective obj = sb.getObjective(stat);

        if (obj == null) {
            obj = sb.addObjective(
                    stat,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(stat),
                    RenderType.INTEGER,
                    false,
                    null
            );
        }

        ScoreAccess score = sb.getOrCreatePlayerScore(
                player,
                obj
        );

        score.set(value);
    }

    private static int getRankIndex(ServerPlayer player) {
        var rank = RankManager.getRank(getCurrentRp(player));

        if (rank == null) {
            return 0;
        }

        for (int i = 0; i < Config.ranks.size(); i++) {
            if (Config.ranks.get(i).name.equals(rank.name)) {
                return i;
            }
        }

        return 0;
    }
}