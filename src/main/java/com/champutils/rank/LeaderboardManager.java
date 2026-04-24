package com.champutils.rank;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class LeaderboardManager {

    public static class Entry {

        public String playerName;
        public int rp;

        public Entry(
                String playerName,
                int rp
        ){
            this.playerName = playerName;
            this.rp = rp;
        }
    }


    private static final List<Entry> CACHE =
            new CopyOnWriteArrayList<>();



    public static void refresh(
            MinecraftServer server
    ){

        try{

            List<Entry> ladder =
                    new ArrayList<>();


            Scoreboard sb =
                    server.getScoreboard();


            Objective elo =
                    sb.getObjective(
                            "elo"
                    );


            if(elo == null){
                return;
            }



            for(
                    ScoreHolder holder :
                    sb.getTrackedPlayers()
            ){

                try{

                    int rp =
                            sb.getOrCreatePlayerScore(
                                    holder,
                                    elo
                            ).get();


                    if(rp > 0){

                        ladder.add(
                                new Entry(
                                        holder.getScoreboardName(),
                                        rp
                                )
                        );
                    }

                }catch(Exception ignored){}
            }



            ladder.sort(
                    (a,b)->
                            Integer.compare(
                                    b.rp,
                                    a.rp
                            )
            );


            CACHE.clear();

            CACHE.addAll(
                    ladder
            );

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    public static List<Entry> getTop(
            int amount
    ){

        int size =
                Math.min(
                        amount,
                        CACHE.size()
                );

        return new ArrayList<>(
                CACHE.subList(
                        0,
                        size
                )
        );
    }

}