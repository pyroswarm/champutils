package com.champutils.rank;

import com.champutils.profile.ProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.HashMap;
import java.util.Map;

public class SeasonManager {

    public static int CURRENT_SEASON = 1;
    public static String CURRENT_NAME = "Indigo Cup";

    public static int RESET_FLOOR = 300;
    public static double RESET_PERCENT = .50;

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();


    // =====================================
    // PERSISTENT SEASON STATE
    // =====================================

    public static class SeasonState {

        public int currentSeason = 1;

        public String currentName =
                "Indigo Cup";
    }


    private static File getStateFile(){

        File dir =
                new File(
                        "config/champutils"
                );

        if(!dir.exists()){
            dir.mkdirs();
        }

        return new File(
                dir,
                "season_state.json"
        );
    }



    public static void loadState(){

        try{

            File f =
                    getStateFile();

            if(!f.exists()){
                saveState();
                return;
            }

            try(
                    FileReader r =
                            new FileReader(f)
            ){

                SeasonState s =
                        GSON.fromJson(
                                r,
                                SeasonState.class
                        );

                if(s != null){

                    CURRENT_SEASON =
                            s.currentSeason;

                    CURRENT_NAME =
                            s.currentName;
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    public static void saveState(){

        try{

            SeasonState s =
                    new SeasonState();

            s.currentSeason =
                    CURRENT_SEASON;

            s.currentName =
                    CURRENT_NAME;

            try(
                    FileWriter w =
                            new FileWriter(
                                    getStateFile()
                            )
            ){

                GSON.toJson(
                        s,
                        w
                );
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    // =====================================
    // ROLLBACK SNAPSHOT
    // =====================================

    public static class PlayerSnapshot {

        public int rp;
        public int peakRp;
        public int wins;
        public int losses;
        public int currentStreak;
        public int bestStreak;
        public int upsetWins;
    }


    public static class RollbackState {

        public int season;

        public String seasonName;

        public Map<String, PlayerSnapshot>
                players =
                new HashMap<>();
    }



    private static File getRollbackFile(){

        return new File(
                "config/champutils/rollback_state.json"
        );
    }



    private static void saveRollbackState(
            MinecraftServer server
    ){

        try{

            RollbackState r =
                    new RollbackState();

            r.season =
                    CURRENT_SEASON;

            r.seasonName =
                    CURRENT_NAME;


            for(
                    ServerPlayer p :
                    server.getPlayerList()
                            .getPlayers()
            ){

                PlayerSnapshot s =
                        new PlayerSnapshot();

                s.rp =
                        ProfileManager
                                .getCurrentRp(p);

                s.peakRp =
                        ProfileManager
                                .getPeakRp(p);

                s.wins =
                        ProfileManager
                                .getRankedWins(p);

                s.losses =
                        ProfileManager
                                .getRankedLosses(p);

                s.currentStreak =
                        ProfileManager
                                .getCurrentStreak(p);

                s.bestStreak =
                        ProfileManager
                                .getBestStreak(p);

                s.upsetWins =
                        ProfileManager
                                .getUpsetWins(p);


                r.players.put(
                        p.getName()
                                .getString(),
                        s
                );
            }


            try(
                    FileWriter w =
                            new FileWriter(
                                    getRollbackFile()
                            )
            ){

                GSON.toJson(
                        r,
                        w
                );
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    private static RollbackState
    loadRollbackState(){

        try{

            File f =
                    getRollbackFile();

            if(!f.exists()){
                return null;
            }

            try(
                    FileReader r =
                            new FileReader(f)
            ){

                return GSON.fromJson(
                        r,
                        RollbackState.class
                );
            }

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }



    // =====================================
    // SOFT RESET
    // =====================================

    public static int softReset(
            int rp
    ){

        return Math.max(
                RESET_FLOOR,

                (int)Math.round(
                        RESET_FLOOR
                                +
                                (
                                        (rp-RESET_FLOOR)
                                                *
                                                RESET_PERCENT
                                )
                )
        );
    }



    // =====================================
    // START NEW SEASON
    // =====================================

    public static void startNewSeason(
            MinecraftServer server,
            String name
    ){

        saveRollbackState(
                server
        );


        for(
                ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()
        ){

            archivePlayer(
                    p
            );

            resetPlayer(
                    p
            );
        }


        CURRENT_SEASON++;

        CURRENT_NAME =
                name;

        saveState();


        server.getPlayerList()
                .broadcastSystemMessage(

                        Component.literal(
                                "§6New Season "
                                        +CURRENT_SEASON
                                        +" §e"
                                        +CURRENT_NAME
                        ),

                        false
                );
    }



    // =====================================
    // ROLLBACK
    // =====================================

    public static void rollbackSeason(
            MinecraftServer server
    ){

        RollbackState state =
                loadRollbackState();

        if(state==null){
            return;
        }


        CURRENT_SEASON =
                state.season;

        CURRENT_NAME =
                state.seasonName;


        for(
                ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()
        ){

            PlayerSnapshot snap =
                    state.players.get(
                            p.getName()
                                    .getString()
                    );

            if(snap==null){
                continue;
            }


            ProfileManager.setElo(
                    p,
                    snap.rp
            );


            ProfileManager.setSeasonStat(
                    p,
                    "peak_rp",
                    snap.peakRp
            );

            ProfileManager.setSeasonStat(
                    p,
                    "ranked_wins",
                    snap.wins
            );

            ProfileManager.setSeasonStat(
                    p,
                    "ranked_losses",
                    snap.losses
            );

            ProfileManager.setSeasonStat(
                    p,
                    "current_streak",
                    snap.currentStreak
            );

            ProfileManager.setSeasonStat(
                    p,
                    "best_streak",
                    snap.bestStreak
            );

            ProfileManager.setSeasonStat(
                    p,
                    "upset_wins",
                    snap.upsetWins
            );


            SeasonArchiveManager
                    .removeLastSeason(
                            p.getName()
                                    .getString()
                    );
        }


        saveState();


        server.getPlayerList()
                .broadcastSystemMessage(
                        Component.literal(
                                "§cSeason rollback restored."
                        ),
                        false
                );
    }



    // =====================================
    // ARCHIVE PLAYER
    // =====================================

    private static void archivePlayer(
            ServerPlayer player
    ){

        SeasonArchiveManager.SeasonRecord r =
                new SeasonArchiveManager
                        .SeasonRecord();

        r.season =
                CURRENT_SEASON;

        r.seasonName =
                CURRENT_NAME;

        r.finishRank =
                ProfileManager
                        .getCurrentRankName(
                                player
                        );

        r.finalRp =
                ProfileManager
                        .getCurrentRp(
                                player
                        );

        r.peakRp =
                ProfileManager
                        .getPeakRp(
                                player
                        );

        r.wins =
                ProfileManager
                        .getRankedWins(
                                player
                        );

        r.losses =
                ProfileManager
                        .getRankedLosses(
                                player
                        );

        r.bestStreak =
                ProfileManager
                        .getBestStreak(
                                player
                        );


        SeasonArchiveManager.archive(
                player.getName()
                        .getString(),
                r
        );
    }



    // =====================================
    // RESET PLAYER
    // =====================================

    private static void resetPlayer(
            ServerPlayer player
    ){

        int newRp =
                softReset(
                        ProfileManager
                                .getCurrentRp(
                                        player
                                )
                );


        ProfileManager.setElo(
                player,
                newRp
        );


        ProfileManager.setSeasonStat(
                player,
                "ranked_wins",
                0
        );

        ProfileManager.setSeasonStat(
                player,
                "ranked_losses",
                0
        );

        ProfileManager.setSeasonStat(
                player,
                "current_streak",
                0
        );

        ProfileManager.setSeasonStat(
                player,
                "best_streak",
                0
        );

        ProfileManager.setSeasonStat(
                player,
                "upset_wins",
                0
        );

        ProfileManager.setSeasonStat(
                player,
                "peak_rp",
                newRp
        );


        ProfileManager.incrementSeasons(
                player
        );
    }

}