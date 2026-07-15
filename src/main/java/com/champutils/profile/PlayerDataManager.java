package com.champutils.profile;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.database.DatabaseManager;
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
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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

        /** Persisted profile battle-title counters. */
        public long pokemonDefeats = 0L;
        public long wildPokemonDefeats = 0L;
        public int highestLevelGapVictory = 0;
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

            SharedJsonStateRepository.saveProfileAsync(
                    profileId,
                    STATE_KEY,
                    data
            ).whenComplete((ignored, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                com.champutils.network.NetworkEventManager.publishCacheInvalidation(
                        "PLAYER_DATA",
                        profileId
                );
            });

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
            SharedJsonStateRepository.saveProfileAsync(profileId, STATE_KEY, data)
                    .whenComplete((ignored, error) -> {
                        if (error != null) error.printStackTrace();
                        else com.champutils.network.NetworkEventManager.publishCacheInvalidation("PLAYER_DATA", profileId);
                    });
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
        Map<String, OfflinePlayerEntry> merged = new HashMap<>();

        if (DatabaseManager.isEnabled()) {
            try {
                List<OfflinePlayerEntry> shared = DatabaseManager.supplyAsync(
                        "load all network profile player data",
                        connection -> {
                            SharedJsonStateRepository.ensureSchema(connection);
                            List<OfflinePlayerEntry> rows = new ArrayList<>();
                            String seasonId = "season_" + Math.max(0, com.champutils.rank.SeasonManager.CURRENT_SEASON);
                            try (var statement = connection.prepareStatement(
                                    "select p.id::text as profile_id, coalesce(pl.username, '') as player_name, js.payload, " +
                                            "rs.rp, rs.peak_rp, rs.wins, rs.losses, rs.streak, ps.playtime_seconds " +
                                            "from player_profiles p " +
                                            "left join players pl on pl.uuid = p.player_uuid " +
                                            "left join profile_json_state js on js.profile_id = p.id and js.state_key = ? " +
                                            "left join profile_ranked_stats rs on rs.profile_id = p.id and rs.season_id = ? " +
                                            "left join profile_player_stats ps on ps.profile_id = p.id " +
                                            "where p.deleted_at is null and coalesce(p.is_pending_delete, false) = false"
                            )) {
                                statement.setString(1, STATE_KEY);
                                statement.setString(2, seasonId);
                                try (var rs = statement.executeQuery()) {
                                    while (rs.next()) {
                                        String profileId = rs.getString("profile_id");
                                        String playerName = rs.getString("player_name");
                                        PlayerData data = null;
                                        String payload = rs.getString("payload");
                                        if (payload != null && !payload.isBlank()) {
                                            data = GSON.fromJson(payload, PlayerData.class);
                                        }
                                        if (data == null) data = new PlayerData();
                                        data.uuid = profileId;
                                        if (playerName != null && !playerName.isBlank()) data.name = playerName;
                                        Integer rankedRp = (Integer) rs.getObject("rp");
                                        if (rankedRp != null) {
                                            data.rp = Math.max(0, rankedRp);
                                            data.peakRp = Math.max(data.rp, rs.getInt("peak_rp"));
                                            data.rankedWins = Math.max(0, rs.getInt("wins"));
                                            data.rankedLosses = Math.max(0, rs.getInt("losses"));
                                            data.currentStreak = Math.max(0, rs.getInt("streak"));
                                            data.bestStreak = Math.max(data.bestStreak, data.currentStreak);
                                        }
                                        Long playtime = (Long) rs.getObject("playtime_seconds");
                                        if (playtime != null) data.playtimeSeconds = Math.max(data.playtimeSeconds, playtime);
                                        sanitize(data, UUID.fromString(profileId), playerName);
                                        rows.add(new OfflinePlayerEntry(profileId, data.name, data));
                                    }
                                }
                            }
                            return rows;
                        }
                ).get(8, TimeUnit.SECONDS);
                for (OfflinePlayerEntry entry : shared) {
                    if (entry != null && entry.uuid != null) merged.put(entry.uuid, entry);
                }
            } catch (Exception error) {
                System.err.println("[ChampUtils] Failed to enumerate network profile data; using local mirrors.");
                error.printStackTrace();
            }
        }

        File[] files = profilesDir().listFiles((d,n)-> n.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    PlayerData data = GSON.fromJson(reader, PlayerData.class);
                    if (data == null) continue;
                    String profileId = file.getName().replace(".json", "");
                    if (data.uuid == null || data.uuid.isBlank()) data.uuid = profileId;
                    merged.putIfAbsent(profileId, new OfflinePlayerEntry(profileId, data.name, data));
                } catch (Exception ignored) {
                }
            }
        }

        return new ArrayList<>(merged.values());
    }

    public static Map<String,Integer> getAllRatings(){
        Map<String,Integer> ratings = new HashMap<>();
        for (OfflinePlayerEntry entry : getAllProfilePlayers()) {
            if (entry == null || entry.data == null || entry.name == null) continue;
            ratings.put(entry.name, entry.data.rp);
        }
        return ratings;
    }

    public static void refreshOnlineProfileAsync(MinecraftServer server, UUID profileId) {
        if (server == null || profileId == null) return;
        invalidateSharedCache(profileId);
        ServerPlayer target = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID active = PlayerProfileManager.activeProfileId(player);
            if (profileId.equals(active)) {
                target = player;
                break;
            }
        }
        if (target == null) return;
        ServerPlayer player = target;
        DatabaseManager.supplyAsync("refresh online profile player data " + profileId, connection -> {
            SharedJsonStateRepository.ensureSchema(connection);
            try (var statement = connection.prepareStatement(
                    "select payload from profile_json_state where profile_id = ? and state_key = ?"
            )) {
                statement.setObject(1, profileId);
                statement.setString(2, STATE_KEY);
                try (var rs = statement.executeQuery()) {
                    if (!rs.next()) return null;
                    return GSON.fromJson(rs.getString(1), PlayerData.class);
                }
            }
        }).whenComplete((data, error) -> {
            if (error != null || data == null) return;
            server.execute(() -> {
                if (player.hasDisconnected() || !profileId.equals(PlayerProfileManager.activeProfileId(player))) return;
                sanitize(data, profileId, player.getGameProfile().getName());
                CACHE.put(profileId, data);
                ProfileManager.setElo(player, data.rp);
            });
        });
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
