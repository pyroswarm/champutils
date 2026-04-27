package com.champutils.battle;

import com.champutils.rank.RankManager;
import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.matchmaking.ArenaManager;
import com.champutils.validation.TeamSnapshotManager;
import com.champutils.config.Config;
import com.champutils.profile.ProfileManager;
import com.champutils.profile.PlayerDataManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;

import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType;

public class BattleListener {

    public static void onBattleEnd(
            ServerPlayer winner,
            ServerPlayer loser
    ) {

        if(
                winner==null
                        ||
                        loser==null
        ){
            return;
        }


        boolean ranked =
                MatchmakingManager
                        .isRankedMatch(
                                winner
                        );



        boolean upsetWin =
                getRankIndex(
                        loser
                )
                        >
                        getRankIndex(
                                winner
                        );


        ProfileManager.recordWin(
                winner,
                ranked,
                upsetWin
        );

        ProfileManager.recordLoss(
                loser,
                ranked
        );



        if(
                Config.arenas!=null
                        &&
                        !Config.arenas.isEmpty()
        ){

            ArenaManager.returnPlayer(
                    winner
            );

            ArenaManager.returnPlayer(
                    loser
            );

            ArenaManager.releaseArena(
                    winner
            );

            ArenaManager.releaseArena(
                    loser
            );
        }



        // Casual battles:
        // no RP changes
        if(
                !ranked
        ){

            MatchmakingManager.clearMatch(
                    winner
            );

            MatchmakingManager.clearMatch(
                    loser
            );

            TeamSnapshotManager.clear(
                    winner
            );

            TeamSnapshotManager.clear(
                    loser
            );

            return;
        }



        // =========================
        // Ranked RP changes
        // =========================

        int winnerElo =
                getElo(
                        winner
                );

        int loserElo =
                getElo(
                        loser
                );


        int change =
                calculateRpChange(
                        winner,
                        loser
                );


        int newWinner =
                winnerElo
                        +
                        change;

        int newLoser =
                Math.max(
                        0,
                        loserElo-change
                );



        // update scoreboard RP
        setElo(
                winner,
                newWinner
        );

        setElo(
                loser,
                newLoser
        );



        // =========================
        // NEW:
        // update persistent offline RP
        // =========================

        PlayerDataManager.setRp(
                winner.getUUID(),
                winner.getName()
                        .getString(),
                newWinner
        );

        PlayerDataManager.setRp(
                loser.getUUID(),
                loser.getName()
                        .getString(),
                newLoser
        );



        ProfileManager.syncPeakRp(
                winner
        );



        RankManager.updatePlayerRank(
                winner,
                winnerElo,
                newWinner
        );

        RankManager.updatePlayerRank(
                loser,
                loserElo,
                newLoser
        );



        winner.sendSystemMessage(
                Component.literal(
                        "§a+"
                                +change
                                +" RP (§f"
                                +newWinner
                                +"§a)"
                )
        );

        loser.sendSystemMessage(
                Component.literal(
                        "§c-"
                                +change
                                +" RP (§f"
                                +newLoser
                                +"§c)"
                )
        );



        MatchmakingManager.clearMatch(
                winner
        );

        MatchmakingManager.clearMatch(
                loser
        );

        TeamSnapshotManager.clear(
                winner
        );

        TeamSnapshotManager.clear(
                loser
        );
    }




    private static int calculateRpChange(
            ServerPlayer winner,
            ServerPlayer loser
    ){

        int winnerRank =
                getRankIndex(
                        winner
                );

        int loserRank =
                getRankIndex(
                        loser
                );

        int rankDiff =
                loserRank-winnerRank;

        int change=
                20
                        +
                        (
                                rankDiff*2
                        );


        if(change<15){
            change=15;
        }

        if(change>30){
            change=30;
        }

        return change;
    }




    private static int getRankIndex(
            ServerPlayer player
    ){

        var rank=
                RankManager.getRank(
                        getElo(player)
                );

        if(
                rank==null
        ){
            return 0;
        }


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


        if(obj==null){

            obj=
                    sb.addObjective(
                            "elo",
                            ObjectiveCriteria.DUMMY,
                            Component.literal(
                                    "RP"
                            ),
                            RenderType.INTEGER,
                            false,
                            null
                    );
        }


        return sb
                .getOrCreatePlayerScore(
                        player,
                        obj
                )
                .get();
    }




    private static void setElo(
            ServerPlayer player,
            int value
    ){

        Scoreboard sb=
                player.getScoreboard();

        Objective obj=
                sb.getObjective(
                        "elo"
                );


        if(obj==null){

            obj=
                    sb.addObjective(
                            "elo",
                            ObjectiveCriteria.DUMMY,
                            Component.literal(
                                    "RP"
                            ),
                            RenderType.INTEGER,
                            false,
                            null
                    );
        }


        ScoreAccess score=
                sb.getOrCreatePlayerScore(
                        player,
                        obj
                );

        score.set(
                value
        );
    }

}