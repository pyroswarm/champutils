package com.champutils.profile;

import com.champutils.database.PlayerDatabaseRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class PlayerDataManager {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static class PlayerData {

        public String uuid;
        public String name;

        public int rp = 300;
        public int peakRp = 300;

        public int rankedWins = 0;
        public int rankedLosses = 0;

        public int casualWins = 0;
        public int casualLosses = 0;

        public int currentStreak = 0;
        public int bestStreak = 0;

        public int upsetWins = 0;

        public int highestRank = 0;
        public int seasonsPlayed = 0;

        /**
         * Total tracked server playtime in seconds.
         * This is incremented once per minute while the player is online.
         */
        public long playtimeSeconds = 0L;
    }


    public static class OfflinePlayerEntry {

        public String uuid;
        public String name;
        public PlayerData data;

        public OfflinePlayerEntry(
                String uuid,
                String name,
                PlayerData data
        ){
            this.uuid=uuid;
            this.name=name;
            this.data=data;
        }
    }



    private static File playerDir(){

        File dir =
                new File(
                        "config/champutils/players"
                );

        if(!dir.exists()){
            dir.mkdirs();
        }

        return dir;
    }



    private static File getFile(
            UUID uuid
    ){

        return new File(
                playerDir(),
                uuid.toString()+".json"
        );
    }



    public static void ensurePlayer(
            UUID uuid,
            String name
    ){

        File file=
                getFile(uuid);

        if(file.exists()){
            PlayerDatabaseRepository.touchPlayer(
                    uuid,
                    name
            );
            return;
        }

        PlayerData data=
                new PlayerData();

        data.uuid=
                uuid.toString();

        data.name=
                name;

        save(
                uuid,
                data
        );
    }



    public static PlayerData load(
            UUID uuid,
            String fallbackName
    ){

        try{

            ensurePlayer(
                    uuid,
                    fallbackName
            );

            try(
                    FileReader r=
                            new FileReader(
                                    getFile(uuid)
                            )
            ){

                PlayerData data=
                        GSON.fromJson(
                                r,
                                PlayerData.class
                        );

                if(data==null){
                    data=
                            new PlayerData();
                }

                data.uuid=
                        uuid.toString();

                data.name=
                        fallbackName;

                return data;
            }

        }catch(Exception e){

            e.printStackTrace();

            PlayerData d=
                    new PlayerData();

            d.uuid=
                    uuid.toString();

            d.name=
                    fallbackName;

            return d;
        }
    }



    public static void save(
            UUID uuid,
            PlayerData data
    ){

        try(
                FileWriter w=
                        new FileWriter(
                                getFile(uuid)
                        )
        ){

            GSON.toJson(
                    data,
                    w
            );

            PlayerDatabaseRepository.sync(
                    data
            );

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    public static int getRp(
            UUID uuid,
            String name
    ){

        return load(
                uuid,
                name
        ).rp;
    }



    public static void setRp(
            UUID uuid,
            String name,
            int rp
    ){

        PlayerData data=
                load(
                        uuid,
                        name
                );

        data.rp=
                Math.max(
                        0,
                        rp
                );

        if(
                data.rp>
                        data.peakRp
        ){
            data.peakRp=
                    data.rp;
        }

        save(
                uuid,
                data
        );
    }



    public static void addRp(
            UUID uuid,
            String name,
            int amount
    ){

        PlayerData d=
                load(
                        uuid,
                        name
                );

        d.rp=
                Math.max(
                        0,
                        d.rp+amount
                );

        if(
                d.rp>d.peakRp
        ){
            d.peakRp=d.rp;
        }

        save(
                uuid,
                d
        );
    }



    public static void removeRp(
            UUID uuid,
            String name,
            int amount
    ){
        addRp(
                uuid,
                name,
                -amount
        );
    }



    public static Map<String,Integer> getAllRatings(){

        Map<String,Integer> map=
                new HashMap<>();

        File[] files=
                playerDir().listFiles(
                        (d,n)->
                                n.endsWith(".json")
                );

        if(files==null){
            return map;
        }

        for(
                File f :
                files
        ){

            try(
                    FileReader r=
                            new FileReader(f)
            ){

                PlayerData d=
                        GSON.fromJson(
                                r,
                                PlayerData.class
                        );

                if(d!=null){

                    map.put(
                            d.name,
                            d.rp
                    );
                }

            }catch(Exception ignored){}
        }

        return map;
    }



    public static List<OfflinePlayerEntry> getAllPlayers(){

        List<OfflinePlayerEntry> players=
                new ArrayList<>();

        File[] files=
                playerDir().listFiles(
                        (d,n)->
                                n.endsWith(".json")
                );

        if(files==null){
            return players;
        }

        for(
                File f :
                files
        ){

            try(
                    FileReader r=
                            new FileReader(f)
            ){

                PlayerData d=
                        GSON.fromJson(
                                r,
                                PlayerData.class
                        );

                if(d!=null){

                    players.add(
                            new OfflinePlayerEntry(
                                    d.uuid,
                                    d.name,
                                    d
                            )
                    );
                }

            }catch(Exception ignored){}
        }

        return players;
    }



    public static void incrementSeasons(
            UUID uuid,
            String name
    ){

        PlayerData d=
                load(
                        uuid,
                        name
                );

        d.seasonsPlayed++;

        save(
                uuid,
                d
        );
    }



    public static void addPlaytimeSeconds(
            UUID uuid,
            String name,
            long seconds
    ){

        if(uuid==null || seconds<=0){
            return;
        }

        PlayerData d=
                load(
                        uuid,
                        name
                );

        d.playtimeSeconds=
                Math.max(
                        0L,
                        d.playtimeSeconds
                )+seconds;

        save(
                uuid,
                d
        );
    }



    public static long getPlaytimeSeconds(
            UUID uuid,
            String name
    ){

        if(uuid==null){
            return 0L;
        }

        return Math.max(
                0L,
                load(
                        uuid,
                        name
                ).playtimeSeconds
        );
    }

}
