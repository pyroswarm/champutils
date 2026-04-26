package com.champutils.config;

import com.google.gson.Gson;
import com.champutils.matchmaking.ArenaManager;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

public class Config {

    public static Map<String, Format> formats;

    public static Matchmaking matchmaking;

    public static List<Rank> ranks;

    public static List<ArenaManager.Arena> arenas;



/* =========================
 NEW WARP CONFIGS
========================= */

    public static Warp evTrainingWarp =
            new Warp(
                    100,
                    70,
                    100
            );

    public static Warp eliteFourWarp =
            new Warp(
                    200,
                    70,
                    200
            );



    public static void load(
            File file
    ){

        try{

            Gson gson =
                    new Gson();

            FileReader reader =
                    new FileReader(
                            file
                    );


            Wrapper data =
                    gson.fromJson(
                            reader,
                            Wrapper.class
                    );


            reader.close();


            if(
                    data == null
            ){
                return;
            }


            formats =
                    data.formats;

            matchmaking =
                    data.matchmaking;

            ranks =
                    data.ranks;

            arenas =
                    data.arenas;



/* =========================
 LOAD WARPS
========================= */

            if(
                    data.evTrainingWarp != null
            ){
                evTrainingWarp =
                        data.evTrainingWarp;
            }

            if(
                    data.eliteFourWarp != null
            ){
                eliteFourWarp =
                        data.eliteFourWarp;
            }



            System.out.println(
                    "[ChampUtils] Config loaded successfully."
            );

            System.out.println(
                    "[ChampUtils] Loaded "
                            +
                            (
                                    arenas == null
                                            ? 0
                                            : arenas.size()
                            )
                            +
                            " arenas."
            );


        }
        catch(Exception e){

            System.out.println(
                    "[ChampUtils] Failed to load config!"
            );

            e.printStackTrace();
        }

    }



/* =========================
 WRAPPER
========================= */

    public static class Wrapper {

        public Map<String, Format> formats;

        public Matchmaking matchmaking;

        public List<Rank> ranks;

        public List<ArenaManager.Arena> arenas;



        /* NEW */
        public Warp evTrainingWarp;

        public Warp eliteFourWarp;

    }



/* =========================
 WARP CLASS
========================= */

    public static class Warp {

        public int x;
        public int y;
        public int z;

        public Warp(){}

        public Warp(
                int x,
                int y,
                int z
        ){
            this.x=x;
            this.y=y;
            this.z=z;
        }

    }

}