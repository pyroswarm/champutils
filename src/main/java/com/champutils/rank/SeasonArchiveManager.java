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

        public int finalRp;

        public int peakRp;

        public int wins;

        public int losses;

        public int bestStreak;
    }


    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();



    private static File getFile(
            String player
    ){

        File dir =
                new File(
                        "config/champutils/seasons"
                );

        if(!dir.exists()){
            dir.mkdirs();
        }

        return new File(
                dir,
                player + ".json"
        );
    }



    // =====================================
    // CREATE PLAYER FILE ON FIRST JOIN
    // =====================================

    public static void ensurePlayerFile(
            String player
    ){

        try{

            File f =
                    getFile(player);

            if(f.exists()){
                return;
            }

            saveHistory(
                    player,
                    new ArrayList<>()
            );

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    public static List<SeasonRecord> getHistory(
            String player
    ){

        try{

            File f =
                    getFile(player);

            if(!f.exists()){

                ensurePlayerFile(
                        player
                );

                return new ArrayList<>();
            }


            Type type =
                    new TypeToken<
                            List<SeasonRecord>
                            >(){}.getType();

            try(
                    FileReader reader =
                            new FileReader(f)
            ){

                List<SeasonRecord> data =
                        GSON.fromJson(
                                reader,
                                type
                        );

                return data==null
                        ? new ArrayList<>()
                        : data;
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

        List<SeasonRecord> history =
                getHistory(player);

        history.add(
                record
        );

        saveHistory(
                player,
                history
        );
    }



    public static void removeLastSeason(
            String player
    ){

        List<SeasonRecord> history =
                getHistory(player);

        if(history.isEmpty()){
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

        List<SeasonRecord> history =
                getHistory(player);

        history.removeIf(
                s -> s.season
                        ==
                        seasonNumber
        );

        saveHistory(
                player,
                history
        );
    }



    public static void wipeHistory(
            String player
    ){

        saveHistory(
                player,
                new ArrayList<>()
        );
    }



    private static void saveHistory(
            String player,
            List<SeasonRecord> history
    ){

        try(
                FileWriter writer =
                        new FileWriter(
                                getFile(player)
                        )
        ){

            GSON.toJson(
                    history,
                    writer
            );

        }catch(Exception e){
            e.printStackTrace();
        }
    }

}