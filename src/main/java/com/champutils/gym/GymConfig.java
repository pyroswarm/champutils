package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GymConfig {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final File FILE =
            new File(
                    "config/champutils/gyms.json"
            );


    private static final Map<String,GymDefinition> GYMS =
            new HashMap<>();



/* =========================
 LOAD
========================= */

    static{
        load();
    }

    public static void load(){

        try{

            if(
                    !FILE.exists()
            ){

                System.out.println(
                        "[ChampUtils] gyms.json missing."
                );

                return;
            }

            try(
                    FileReader reader =
                            new FileReader(
                                    FILE
                            )
            ){

                Wrapper data =
                        GSON.fromJson(
                                reader,
                                Wrapper.class
                        );

                GYMS.clear();

                if(
                        data != null
                                &&
                                data.gyms != null
                ){
                    GYMS.putAll(
                            data.gyms
                    );
                }

                System.out.println(
                        "[ChampUtils] Loaded "
                                + GYMS.size()
                                + " gym configs."
                );
            }

        }
        catch(Exception e){
            e.printStackTrace();
        }

    }



/* =========================
 LOOKUPS
========================= */

    public static GymDefinition getGym(
            BadgeType badge
    ){

        if(
                badge == null
        ){
            return null;
        }

        return GYMS.get(
                badge.name()
        );
    }



    public static GymDefinition getGym(
            String name
    ){

        if(
                name == null
        ){
            return null;
        }

        return GYMS.get(
                name.toUpperCase()
        );
    }


    public static boolean hasGym(
            BadgeType badge
    ){
        return getGym(
                badge
        ) != null;
    }



/* =========================
 DATA WRAPPER
========================= */

    public static class Wrapper {

        public Map<String,GymDefinition> gyms;

    }



/* =========================
 GYM DEFINITION
========================= */

    public static class GymDefinition {

        public String leaderName;

        public boolean debug;

        public int levelCap;

        public int partySize;

        public boolean itemsAllowed;

        public int rewardMoney;

        public List<PokemonSet> party;

    }



/* =========================
 POKEMON SET
========================= */

    public static class PokemonSet {

        public String species;

        public int level;

        public String nature;

        public String ability;

        public String heldItem;

        public Stats ivs;

        public Stats evs;

        public List<String> moves;

    }



/* =========================
 IV / EV STATS
========================= */

    public static class Stats {

        public int hp;
        public int atk;
        public int def;
        public int spa;
        public int spd;
        public int spe;

    }

}