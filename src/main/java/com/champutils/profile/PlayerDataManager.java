package com.champutils.profile;

import com.champutils.database.SharedJsonStateRepository;
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
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {
    private static final String STATE_KEY = "player_profile_stats";
    private static final Map<UUID, PlayerData> CACHE = new ConcurrentHashMap<>();

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

        UUID profileId =
                PlayerProfileManager.activeProfileId(uuid);

        File dir =
                new File(
                        playerDir(),
                        "profiles"
                );

        if(!dir.exists()){
            dir.mkdirs();
        }

        return new File(
                dir,
                profileId.toString()+".json"
        );
    }



    public static void ensurePlayer(
            UUID uuid,
            String name
    ){

        PlayerDatabaseRepository.touchPlayer(
                uuid,
                name
        );
    }



    public static PlayerData load(
            UUID uuid,
            String fallbackName
    ){
        UUID profileId =
                PlayerProfileManager.activeProfileId(uuid);

        try{
            PlayerData cached = CACHE.get(profileId);
            if (cached != null) {
                return cached;
            }

            ensurePlayer(
                    uuid,
                    fallbackName
            );

            PlayerData data =
                    new PlayerData();

            File file =
                    getFile(uuid);

            if(file.exists()){
                try(
                        FileReader r=
                                new FileReader(
                                        file
                                )
                ){
                    PlayerData local =
                            GSON.fromJson(
                                    r,
                                    PlayerData.class
                            );
                    if(local!=null){
                        data =
                                local;
                    }
                }
            }

            data =
                    SharedJsonStateRepository.loadProfile(
                            profileId,
                            STATE_KEY,
                            PlayerData.class,
                            data
                    );

            sanitize(
                    data,
                    profileId,
                    fallbackName
            );

            CACHE.put(profileId, data);
            return data;

        }catch(Exception e){

            e.printStackTrace();

            PlayerData d=
                    new PlayerData();

            d.uuid=
                    profileId.toString();

            d.name=
                    fallbackName;

            CACHE.put(profileId, d);
            return d;
        }
    }



    public static void save(
            UUID uuid,
            PlayerData data
    ){
        UUID profileId =
                PlayerProfileManager.activeProfileId(uuid);

        sanitize(
                data,
                profileId,
                data == null ? null : data.name
        );
        if (data != null) {
            CACHE.put(profileId, data);
        }

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

            SharedJsonStateRepository.saveProfile(
                    profileId,
                    STATE_KEY,
                    data
            );

            com.champutils.network.NetworkEventManager.publishCacheInvalidation(
                    "PLAYER_DATA",
                    profileId
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



    private static File profilesDir(){
        File dir = new File(playerDir(), "profiles");
        if(!dir.exists()){
            dir.mkdirs();
        }
        return dir;
    }



    public static void saveProfileDataById(UUID profileId, PlayerData data){
        if(profileId==null || data==null){
            return;
        }

        data.uuid = profileId.toString();
        sanitize(data, profileId, data.name);
        CACHE.put(profileId, data);

        try(FileWriter w = new FileWriter(new File(profilesDir(), profileId.toString()+".json"))){
            GSON.toJson(data, w);
            PlayerDatabaseRepository.sync(data);
            SharedJsonStateRepository.saveProfile(profileId, STATE_KEY, data);
            com.champutils.network.NetworkEventManager.publishCacheInvalidation("PLAYER_DATA", profileId);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void invalidateSharedCache(UUID profileId) {
        if (profileId != null) {
            CACHE.remove(profileId);
        }
    }

    private static void sanitize(PlayerData data, UUID profileId, String fallbackName){
        if(data==null || profileId==null){
            return;
        }
        data.uuid = profileId.toString();
        if(fallbackName!=null && !fallbackName.isBlank()){
            data.name = fallbackName;
        } else if(data.name==null){
            data.name = "";
        }
        data.rp = Math.max(0, data.rp);
        data.peakRp = Math.max(data.rp, data.peakRp);
        data.rankedWins = Math.max(0, data.rankedWins);
        data.rankedLosses = Math.max(0, data.rankedLosses);
        data.casualWins = Math.max(0, data.casualWins);
        data.casualLosses = Math.max(0, data.casualLosses);
        data.currentStreak = Math.max(0, data.currentStreak);
        data.bestStreak = Math.max(data.currentStreak, data.bestStreak);
        data.upsetWins = Math.max(0, data.upsetWins);
        data.highestRank = Math.max(0, data.highestRank);
        data.seasonsPlayed = Math.max(0, data.seasonsPlayed);
        data.playtimeSeconds = Math.max(0L, data.playtimeSeconds);
    }



    public static List<OfflinePlayerEntry> getAllProfilePlayers(){
        List<OfflinePlayerEntry> players = new ArrayList<>();

        File[] files = profilesDir().listFiles((d,n)-> n.endsWith(".json"));

        if(files==null){
            return players;
        }

        for(File f : files){
            try(FileReader r = new FileReader(f)){
                PlayerData d = GSON.fromJson(r, PlayerData.class);
                if(d!=null){
                    String profileId = f.getName().replace(".json", "");
                    if(d.uuid==null || d.uuid.isBlank()){
                        d.uuid = profileId;
                    }
                    players.add(new OfflinePlayerEntry(profileId, d.name, d));
                }
            }catch(Exception ignored){}
        }

        return players;
    }



    public static Map<String,Integer> getAllRatings(){

        Map<String,Integer> map=
                new HashMap<>();

        File[] files=
                profilesDir().listFiles(
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
        return getAllProfilePlayers();
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
