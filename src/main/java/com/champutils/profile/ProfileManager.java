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

    // =========================
    public static void recordWin(
            ServerPlayer player,
            boolean ranked,
            boolean upsetWin
    ) {

        add(
                player,
                ranked
                        ? "ranked_wins"
                        : "casual_wins"
        );

        if(!ranked) return;

        add(
                player,
                "current_streak"
        );

        syncBestStreak(
                player
        );

        syncPeakRp(
                player
        );

        syncHighestRank(
                player
        );

        if(upsetWin){
            add(
                    player,
                    "upset_wins"
            );
        }
    }


    public static void recordLoss(
            ServerPlayer player,
            boolean ranked
    ) {

        add(
                player,
                ranked
                        ? "ranked_losses"
                        : "casual_losses"
        );

        if(ranked){

            setStat(
                    player,
                    "current_streak",
                    0
            );
        }
    }


    // =========================
    public static void syncPeakRp(
            ServerPlayer player
    ) {

        int rp=
                getCurrentRp(player);

        int peak=
                getStat(
                        player,
                        "peak_rp"
                );

        if(rp>peak){

            setStat(
                    player,
                    "peak_rp",
                    rp
            );
        }

        int seasonBest=
                getStat(
                        player,
                        "season_best_rp"
                );

        if(rp>seasonBest){

            setStat(
                    player,
                    "season_best_rp",
                    rp
            );
        }
    }


    private static void syncBestStreak(
            ServerPlayer player
    ){

        int current=
                getCurrentStreak(
                        player
                );

        int best=
                getBestStreak(
                        player
                );

        if(current>best){

            setStat(
                    player,
                    "best_streak",
                    current
            );
        }
    }


    private static void syncHighestRank(
            ServerPlayer player
    ){

        int current=
                getRankIndex(
                        player
                );

        int best=
                getStat(
                        player,
                        "highest_rank"
                );

        if(current>best){

            setStat(
                    player,
                    "highest_rank",
                    current
            );
        }
    }


    // =========================
    public static int getCurrentRp(
            ServerPlayer player
    ){
        return getElo(
                player
        );
    }

    public static int getPeakRp(
            ServerPlayer player
    ){
        return getStat(
                player,
                "peak_rp"
        );
    }


    public static double getWinRate(
            ServerPlayer player
    ){

        int wins=
                getRankedWins(
                        player
                );

        int losses=
                getRankedLosses(
                        player
                );

        if(wins+losses==0)
            return 0;

        return (
                (double)wins
                        /
                        (wins+losses)
        )*100;
    }


    public static int getCurrentStreak(
            ServerPlayer p
    ){
        return getStat(
                p,
                "current_streak"
        );
    }

    public static int getBestStreak(
            ServerPlayer p
    ){
        return getStat(
                p,
                "best_streak"
        );
    }

    public static int getUpsetWins(
            ServerPlayer p
    ){
        return getStat(
                p,
                "upset_wins"
        );
    }


    public static int getHighestRankIndex(
            ServerPlayer p
    ){
        return getStat(
                p,
                "highest_rank"
        );
    }


    public static String getHighestRankName(
            ServerPlayer p
    ){

        int index=
                getHighestRankIndex(p);

        if(index<
                Config.ranks.size()){
            return Config.ranks
                    .get(index)
                    .name;
        }

        return "Youngster";
    }


    public static String getCurrentRankName(
            ServerPlayer p
    ){

        var rank=
                RankManager.getRank(
                        getCurrentRp(p)
                );

        return rank==null
                ? "Youngster"
                : rank.name;
    }


    public static int getRankedWins(
            ServerPlayer p
    ){
        return getStat(
                p,
                "ranked_wins"
        );
    }

    public static int getRankedLosses(
            ServerPlayer p
    ){
        return getStat(
                p,
                "ranked_losses"
        );
    }

    public static int getCasualWins(
            ServerPlayer p
    ){
        return getStat(
                p,
                "casual_wins"
        );
    }

    public static int getCasualLosses(
            ServerPlayer p
    ){
        return getStat(
                p,
                "casual_losses"
        );
    }


    // =========================
    private static int getRankIndex(
            ServerPlayer player
    ){

        var rank=
                RankManager.getRank(
                        getElo(player)
                );

        if(rank==null)
            return 0;

        for(
                int i=0;
                i<Config.ranks.size();
                i++
        ){

            if(
                    Config.ranks
                            .get(i)
                            .name.equals(
                                    rank.name
                            )
            ){
                return i;
            }
        }

        return 0;
    }


    private static int getElo(
            ServerPlayer player
    ){

        Scoreboard sb=
                player.getScoreboard();

        Objective obj=
                sb.getObjective(
                        "elo"
                );

        if(obj==null)
            return 0;

        return sb.getOrCreatePlayerScore(
                player,
                obj
        ).get();
    }


    private static void add(
            ServerPlayer player,
            String stat
    ){

        setStat(
                player,
                stat,
                getStat(
                        player,
                        stat
                )+1
        );
    }


    private static void setStat(
            ServerPlayer player,
            String stat,
            int value
    ){

        Scoreboard sb=
                player.getScoreboard();

        Objective obj=
                getObjective(
                        sb,
                        stat
                );

        ScoreAccess score=
                sb.getOrCreatePlayerScore(
                        player,
                        obj
                );

        score.set(
                value
        );
    }


    private static int getStat(
            ServerPlayer player,
            String stat
    ){

        Scoreboard sb=
                player.getScoreboard();

        Objective obj=
                getObjective(
                        sb,
                        stat
                );

        return sb.getOrCreatePlayerScore(
                player,
                obj
        ).get();
    }


    private static Objective getObjective(
            Scoreboard sb,
            String name
    ){

        Objective obj=
                sb.getObjective(name);

        if(obj==null){

            obj=sb.addObjective(
                    name,
                    ObjectiveCriteria.DUMMY,
                    Component.literal(name),
                    RenderType.INTEGER,
                    false,
                    null
            );
        }

        return obj;
    }
    public static void setSeasonStat(
            ServerPlayer player,
            String name,
            int value
    ){
        setStat(
                player,
                name,
                value
        );
    }


    public static void setElo(
            ServerPlayer player,
            int rp
    ){

        var sb=
                player.getScoreboard();

        var obj=
                sb.getObjective(
                        "elo"
                );

        if(obj==null) return;

        sb.getOrCreatePlayerScore(
                player,
                obj
        ).set(rp);
    }


    public static void incrementSeasons(
            ServerPlayer player
    ){
        add(
                player,
                "seasons_played"
        );
    }


    public static int getSeasonsPlayed(
            ServerPlayer player
    ){
        return getStat(
                player,
                "seasons_played"
        );
    }

}