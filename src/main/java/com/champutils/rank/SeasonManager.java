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

    public static int CURRENT_SEASON = 1;
    public static String CURRENT_NAME = "Indigo Cup";

    public static int RESET_FLOOR = 300;
    public static double RESET_PERCENT = .50;

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    /*
     NON-BLOCKING DELAY SYSTEM
     */
    private static boolean pendingSeasonReset = false;
    private static int resetTickCountdown = 0;
    private static String pendingSeasonName = null;

    public static class SeasonState {
        public int currentSeason = 1;
        public String currentName = "Indigo Cup";
    }

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

        public Map<String, PlayerSnapshot> players =
                new HashMap<>();
    }

    private static File getStateFile() {
        File dir = new File("config/champutils");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(
                dir,
                "season_state.json"
        );
    }

    private static File getRollbackFile() {
        return new File(
                "config/champutils/rollback_state.json"
        );
    }

    public static void loadState() {
        try {
            File f = getStateFile();

            if (!f.exists()) {
                saveState();
                return;
            }

            try (FileReader r = new FileReader(f)) {
                SeasonState s =
                        GSON.fromJson(
                                r,
                                SeasonState.class
                        );

                if (s != null) {
                    CURRENT_SEASON = s.currentSeason;
                    CURRENT_NAME = s.currentName;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveState() {
        try {
            SeasonState s =
                    new SeasonState();

            s.currentSeason = CURRENT_SEASON;
            s.currentName = CURRENT_NAME;

            try (
                    FileWriter w =
                            new FileWriter(
                                    getStateFile()
                            )
            ) {
                GSON.toJson(
                        s,
                        w
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int softReset(int rp) {
        return Math.max(
                RESET_FLOOR,
                (int) Math.round(
                        RESET_FLOOR +
                                ((rp - RESET_FLOOR) * RESET_PERCENT)
                )
        );
    }

    /*
     START NEW SEASON
     */
    public static void startNewSeason(
            MinecraftServer server,
            String name
    ) {
        if (pendingSeasonReset) {
            return;
        }

        saveRollbackState(server);

        ArrayList<SeasonArchiveManager.LadderEntry> top =
                new ArrayList<>();

        for (var e : LeaderboardManager.getTop(100)) {
            top.add(
                    new SeasonArchiveManager.LadderEntry(
                            e.playerName,
                            e.rp,
                            RankManager.getRank(e.rp).name
                    )
            );
        }

        SeasonArchiveManager.saveTop100Snapshot(
                CURRENT_SEASON,
                top
        );

        /*
         SHOW END SEASON POPUP
         */
        for (
                ServerPlayer p :
                server.getPlayerList().getPlayers()
        ) {
            int current =
                    ProfileManager.getCurrentRp(p);

            int peak =
                    ProfileManager.getPeakRp(p);

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                            Component.literal("§c§lSEASON END")
                    )
            );

            p.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    "§6Peak RP " +
                                            peak +
                                            " §7| Final RP " +
                                            current
                            )
                    )
            );

            p.playNotifySound(
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.MASTER,
                    1f,
                    .8f
            );
        }

        /*
         Delay actual reset by 5 seconds
         (100 ticks)
         */
        pendingSeasonReset = true;
        resetTickCountdown = 100;
        pendingSeasonName = name;
    }

    /*
     CALL THIS EVERY SERVER TICK
     */
    public static void tick(
            MinecraftServer server
    ) {
        if (!pendingSeasonReset) {
            return;
        }

        resetTickCountdown--;

        if (resetTickCountdown > 0) {
            return;
        }

        pendingSeasonReset = false;

        completeSeasonReset(
                server,
                pendingSeasonName
        );

        pendingSeasonName = null;
    }

    private static void completeSeasonReset(
            MinecraftServer server,
            String name
    ) {

        for (ServerPlayer p :
                server.getPlayerList().getPlayers()) {
            archivePlayer(p);
            resetPlayer(p);
        }

        for (var entry :
                PlayerDataManager.getAllPlayers()) {

            boolean online =
                    server.getPlayerList()
                            .getPlayerByName(
                                    entry.name
                            ) != null;

            if (online) {
                continue;
            }

            PlayerData d = entry.data;

            d.rp = softReset(d.rp);
            d.peakRp = d.rp;
            d.rankedWins = 0;
            d.rankedLosses = 0;
            d.currentStreak = 0;
            d.bestStreak = 0;
            d.upsetWins = 0;
            d.seasonsPlayed++;

            PlayerDataManager.save(
                    UUID.fromString(d.uuid),
                    d
            );
        }

        CURRENT_SEASON++;
        CURRENT_NAME = name;

        saveState();
        LeaderboardManager.refresh(server);

        for (
                ServerPlayer p :
                server.getPlayerList().getPlayers()
        ) {
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
                                    "§e(" +
                                            CURRENT_NAME +
                                            "!!!)"
                            )
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
                                "§6Season " +
                                        CURRENT_SEASON +
                                        " §e" +
                                        CURRENT_NAME +
                                        " has begun!"
                        ),
                        false
                );
    }

    private static void archivePlayer(
            ServerPlayer player
    ) {
        var r =
                new SeasonArchiveManager.SeasonRecord();

        r.season = CURRENT_SEASON;
        r.seasonName = CURRENT_NAME;
        r.finalRp = ProfileManager.getCurrentRp(player);
        r.peakRp = ProfileManager.getPeakRp(player);
        r.wins = ProfileManager.getRankedWins(player);
        r.losses = ProfileManager.getRankedLosses(player);
        r.bestStreak = ProfileManager.getBestStreak(player);

        r.finishRank =
                RankManager.getRank(
                        r.finalRp
                ).name;

        r.peakRank = r.finishRank;

        SeasonArchiveManager.archive(
                player.getName().getString(),
                r
        );
    }

    private static void resetPlayer(
            ServerPlayer player
    ) {
        int reset =
                softReset(
                        ProfileManager.getCurrentRp(player)
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

    private static void saveRollbackState(
            MinecraftServer server
    ) {
        // Keep your existing implementation here
        // unchanged from your original file
    }

    public static void rollbackSeason(
            MinecraftServer server
    ) {
        // Keep your existing rollback logic
        // unchanged from your original file
    }
}