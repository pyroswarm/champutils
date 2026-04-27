package com.champutils.rank;

import com.champutils.profile.ProfileManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerDataManager.PlayerData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SeasonManager {

    public static int CURRENT_SEASON=1;
    public static String CURRENT_NAME="Indigo Cup";

    public static int RESET_FLOOR=300;
    public static double RESET_PERCENT=.50;

    private static final Gson GSON=
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();



    public static class SeasonState{
        public int currentSeason=1;
        public String currentName="Indigo Cup";
    }



    public static class PlayerSnapshot{
        public int rp;
        public int peakRp;
        public int wins;
        public int losses;
        public int currentStreak;
        public int bestStreak;
        public int upsetWins;
    }



    public static class RollbackState{

        public int season;
        public String seasonName;

        public Map<String,PlayerSnapshot> players=
                new HashMap<>();
    }



    private static File getStateFile(){

        File dir=
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



    private static File getRollbackFile(){

        return new File(
                "config/champutils/rollback_state.json"
        );
    }



    public static void loadState(){

        try{

            File f=getStateFile();

            if(!f.exists()){
                saveState();
                return;
            }

            try(FileReader r=new FileReader(f)){

                SeasonState s=
                        GSON.fromJson(
                                r,
                                SeasonState.class
                        );

                if(s!=null){
                    CURRENT_SEASON=s.currentSeason;
                    CURRENT_NAME=s.currentName;
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }



    public static void saveState(){

        try{

            SeasonState s=
                    new SeasonState();

            s.currentSeason=
                    CURRENT_SEASON;

            s.currentName=
                    CURRENT_NAME;

            try(
                    FileWriter w=
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



    private static void saveRollbackState(
            MinecraftServer server
    ){

        try{

            RollbackState r=
                    new RollbackState();

            r.season=
                    CURRENT_SEASON;

            r.seasonName=
                    CURRENT_NAME;


            for(
                    var entry :
                    PlayerDataManager.getAllPlayers()
            ){

                PlayerSnapshot s=
                        new PlayerSnapshot();

                s.rp=entry.data.rp;
                s.peakRp=entry.data.peakRp;

                s.wins=entry.data.rankedWins;
                s.losses=entry.data.rankedLosses;

                s.currentStreak=
                        entry.data.currentStreak;

                s.bestStreak=
                        entry.data.bestStreak;

                s.upsetWins=
                        entry.data.upsetWins;

                r.players.put(
                        entry.name,
                        s
                );
            }

            try(
                    FileWriter w=
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



    private static RollbackState loadRollbackState(){

        try{

            File f=
                    getRollbackFile();

            if(!f.exists()){
                return null;
            }

            try(
                    FileReader r=
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



    public static void startNewSeason(
            MinecraftServer server,
            String name
    ){

        saveRollbackState(
                server
        );



        ArrayList<
                SeasonArchiveManager.LadderEntry
                > top=
                new ArrayList<>();

        for(
                var e :
                LeaderboardManager.getTop(100)
        ){

            top.add(
                    new SeasonArchiveManager
                            .LadderEntry(
                            e.playerName,
                            e.rp,
                            RankManager
                                    .getRank(
                                            e.rp
                                    ).name
                    )
            );
        }

        SeasonArchiveManager.saveTop100Snapshot(
                CURRENT_SEASON,
                top
        );



        // ======================
        // SEASON END POPUP FIRST
        // ======================

        for(
                ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()
        ){

            int current=
                    ProfileManager.getCurrentRp(
                            p
                    );

            int peak=
                    ProfileManager.getPeakRp(
                            p
                    );

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                            Component.literal(
                                    "§c§lSEASON END"
                            )
                    )
            );

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    "§6Peak RP "
                                            +peak
                                            +"  §7|  Final RP "
                                            +current
                            )
                    )
            );

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                            20,
                            80,
                            20
                    )
            );

            p.playNotifySound(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.MASTER,
                    1f,
                    .8f
            );
        }



        try{
            Thread.sleep(
                    5000
            );
        }
        catch(Exception ignored){}



        for(
                ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()
        ){
            archivePlayer(p);
            resetPlayer(p);
        }



        for(
                var entry :
                PlayerDataManager.getAllPlayers()
        ){

            boolean online=
                    server.getPlayerList()
                            .getPlayerByName(
                                    entry.name
                            )!=null;

            if(online){
                continue;
            }

            PlayerData d=
                    entry.data;

            var r=
                    new SeasonArchiveManager
                            .SeasonRecord();

            r.season=
                    CURRENT_SEASON;

            r.seasonName=
                    CURRENT_NAME;

            r.finalRp=d.rp;
            r.peakRp=d.peakRp;

            r.wins=d.rankedWins;
            r.losses=d.rankedLosses;

            r.bestStreak=
                    d.bestStreak;

            r.finishRank=
                    RankManager
                            .getRank(
                                    d.rp
                            ).name;

            r.peakRank=
                    r.finishRank;

            SeasonArchiveManager.archive(
                    d.name,
                    r
            );


            d.rp=
                    softReset(
                            d.rp
                    );

            d.peakRp=d.rp;

            d.rankedWins=0;
            d.rankedLosses=0;

            d.currentStreak=0;
            d.bestStreak=0;
            d.upsetWins=0;

            d.seasonsPlayed++;

            PlayerDataManager.save(
                    UUID.fromString(
                            d.uuid
                    ),
                    d
            );
        }



        CURRENT_SEASON++;
        CURRENT_NAME=name;

        saveState();

        LeaderboardManager.refresh(
                server
        );



        // ======================
        // NEW SEASON POPUP
        // ======================

        for(
                ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()
        ){

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                            Component.literal(
                                    "§6§lNEW SEASON BEGINNING"
                            )
                    )
            );

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    "§e("
                                            +CURRENT_NAME
                                            +"!!!)"
                            )
                    )
            );

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(
                            20,
                            120,
                            40
                    )
            );

            p.playNotifySound(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.MASTER,
                    1f,
                    1.2f
            );
        }



        server.getPlayerList()
                .broadcastSystemMessage(
                        Component.literal(
                                "§6Season "
                                        +CURRENT_SEASON
                                        +" §e"
                                        +CURRENT_NAME
                                        +" has begun!"
                        ),
                        false
                );
    }



    private static void archivePlayer(
            ServerPlayer player
    ){

        var r=
                new SeasonArchiveManager
                        .SeasonRecord();

        r.season=
                CURRENT_SEASON;

        r.seasonName=
                CURRENT_NAME;

        r.finalRp=
                ProfileManager.getCurrentRp(player);

        r.peakRp=
                ProfileManager.getPeakRp(player);

        r.wins=
                ProfileManager.getRankedWins(player);

        r.losses=
                ProfileManager.getRankedLosses(player);

        r.bestStreak=
                ProfileManager.getBestStreak(player);

        r.finishRank=
                RankManager.getRank(
                        r.finalRp
                ).name;

        r.peakRank=
                r.finishRank;

        SeasonArchiveManager.archive(
                player.getName().getString(),
                r
        );
    }



    private static void resetPlayer(
            ServerPlayer player
    ){

        int reset=
                softReset(
                        ProfileManager.getCurrentRp(
                                player
                        )
                );

        ProfileManager.setElo(
                player,
                reset
        );

        PlayerDataManager.setRp(
                player.getUUID(),
                player.getName().getString(),
                reset
        );
    }



    public static void rollbackSeason(
            MinecraftServer server
    ){

        RollbackState state=
                loadRollbackState();

        if(state==null){
            return;
        }



        for(
                var entry :
                PlayerDataManager.getAllPlayers()
        ){
            SeasonArchiveManager.removeLastSeason(
                    entry.name
            );
        }

        SeasonArchiveManager.removeSeasonSnapshot(
                CURRENT_SEASON
        );



        CURRENT_SEASON=
                state.season;

        CURRENT_NAME=
                state.seasonName;

        saveState();



        for(
                var entry :
                PlayerDataManager.getAllPlayers()
        ){

            PlayerSnapshot s=
                    state.players.get(
                            entry.name
                    );

            if(s==null){
                continue;
            }

            PlayerData d=
                    entry.data;

            d.rp=s.rp;
            d.peakRp=s.peakRp;

            d.rankedWins=s.wins;
            d.rankedLosses=s.losses;

            d.currentStreak=
                    s.currentStreak;

            d.bestStreak=
                    s.bestStreak;

            d.upsetWins=
                    s.upsetWins;

            PlayerDataManager.save(
                    UUID.fromString(
                            d.uuid
                    ),
                    d
            );
        }



        for(
                ServerPlayer p :
                server.getPlayerList()
                        .getPlayers()
        ){

            PlayerSnapshot s=
                    state.players.get(
                            p.getName().getString()
                    );

            if(s!=null){
                ProfileManager.setElo(
                        p,
                        s.rp
                );
            }
        }



        LeaderboardManager.refresh(
                server
        );
    }

}