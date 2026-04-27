package com.champutils.rank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.List;

public class SeasonArchiveManager {

    public static class SeasonRecord {

        public int season;
        public String seasonName;

        public String finishRank;
        public String peakRank;

        public int finalRp;
        public int peakRp;

        public int wins;
        public int losses;

        public int bestStreak;
    }



    public static class LadderEntry {

        public String player;
        public int rp;
        public String rank;

        public LadderEntry(
                String player,
                int rp,
                String rank
        ){
            this.player=player;
            this.rp=rp;
            this.rank=rank;
        }
    }



    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();



    private static File seasonDir(){

        File dir=
                new File(
                        "config/champutils/seasons"
                );

        if(!dir.exists()){
            dir.mkdirs();
        }

        return dir;
    }



    private static File getPlayerFile(
            String player
    ){
        return new File(
                seasonDir(),
                player+".json"
        );
    }



    private static File getTop100File(
            int season
    ){
        return new File(
                seasonDir(),
                "season_"
                        +season
                        +"_top100.json"
        );
    }



    public static void ensurePlayerFile(
            String player
    ){

        File f=
                getPlayerFile(
                        player
                );

        if(f.exists()){
            return;
        }

        saveHistory(
                player,
                new ArrayList<>()
        );
    }



    public static List<SeasonRecord> getHistory(
            String player
    ){

        try{

            File f=
                    getPlayerFile(
                            player
                    );

            if(!f.exists()){

                ensurePlayerFile(
                        player
                );

                return new ArrayList<>();
            }


            Type type=
                    new TypeToken<
                            List<SeasonRecord>
                            >(){}.getType();

            try(
                    FileReader r=
                            new FileReader(f)
            ){

                List<SeasonRecord> d=
                        GSON.fromJson(
                                r,
                                type
                        );

                return d==null
                        ? new ArrayList<>()
                        : d;
            }

        }catch(Exception e){

            e.printStackTrace();

            return new ArrayList<>();
        }
    }



    public static void archive(
            String player,
            SeasonRecord record
    ){

        List<SeasonRecord> history=
                getHistory(
                        player
                );

        history.add(
                record
        );

        saveHistory(
                player,
                history
        );
    }



    private static void saveHistory(
            String player,
            List<SeasonRecord> history
    ){

        try(
                FileWriter w=
                        new FileWriter(
                                getPlayerFile(
                                        player
                                )
                        )
        ){

            GSON.toJson(
                    history,
                    w
            );

        }catch(Exception e){
            e.printStackTrace();
        }
    }



/* =========================
 TOP 100 SNAPSHOTS
========================= */

    public static void saveTop100Snapshot(
            int season,
            List<LadderEntry> top
    ){

        try(
                FileWriter w=
                        new FileWriter(
                                getTop100File(
                                        season
                                )
                        )
        ){

            GSON.toJson(
                    top,
                    w
            );

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    public static List<LadderEntry> getTop100Snapshot(
            int season
    ){

        try{

            File f=
                    getTop100File(
                            season
                    );

            if(!f.exists()){
                return new ArrayList<>();
            }

            Type type=
                    new TypeToken<
                            List<LadderEntry>
                            >(){}.getType();

            try(
                    FileReader r=
                            new FileReader(f)
            ){

                List<LadderEntry> d=
                        GSON.fromJson(
                                r,
                                type
                        );

                return d==null
                        ? new ArrayList<>()
                        : d;
            }

        }catch(Exception e){
            e.printStackTrace();
            return new ArrayList<>();
        }
    }



/* =========================
 ADMIN REMOVAL SUPPORT
========================= */

    public static void removeLastSeason(
            String player
    ){

        List<SeasonRecord> history=
                getHistory(
                        player
                );

        if(
                history.isEmpty()
        ){
            return;
        }

        history.remove(
                history.size()-1
        );

        saveHistory(
                player,
                history
        );
    }



    public static void removeSeason(
            String player,
            int seasonNumber
    ){

        List<SeasonRecord> history=
                getHistory(
                        player
                );

        history.removeIf(
                r->
                        r.season
                                ==
                                seasonNumber
        );

        saveHistory(
                player,
                history
        );
    }



    public static void removeSeasonSnapshot(
            int season
    ){

        File f=
                getTop100File(
                        season
                );

        if(
                f.exists()
        ){
            f.delete();
        }
    }

}