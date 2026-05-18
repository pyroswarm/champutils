package com.champutils.rank;

import com.champutils.profile.ProfileManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerDataManager.PlayerData;
import com.champutils.database.SeasonDatabaseRepository;

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

    private static boolean pendingSeasonReset = false;
    private static int resetTickCountdown = 0;
    private static String pendingSeasonName = null;

    public static class SeasonState {
        public int currentSeason = 1;
        public String currentName = "Indigo Cup";
    }

    public static class PlayerSnapshot {
        public String uuid;
        public String name;

        public int rp;
        public int peakRp;

        public int rankedWins;
        public int rankedLosses;

        public int casualWins;
        public int casualLosses;

        public int currentStreak;
        public int bestStreak;

        public int upsetWins;
        public int highestRank;
        public int seasonsPlayed;
    }

    public static class RollbackState {
        public int season;
        public String seasonName;

        public Map<String, PlayerSnapshot> players =
                new HashMap<>();
    }

    private static File getConfigDir() {
        File dir = new File("config/champutils");

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private static File getStateFile() {
        return new File(
                getConfigDir(),
                "season_state.json"
        );
    }

    private static File getRollbackFile() {
        return new File(
                getConfigDir(),
                "rollback_state.json"
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

        pendingSeasonReset = true;
        resetTickCountdown = 100;
        pendingSeasonName = name;
    }

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

            archiveOfflinePlayer(d);

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
        SeasonDatabaseRepository.setActiveSeason(
                CURRENT_SEASON,
                CURRENT_NAME
        );
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

        r.peakRank =
                RankManager.getRank(
                        r.peakRp
                ).name;

        SeasonArchiveManager.archive(
                player.getName().getString(),
                r
        );
    }

    private static void archiveOfflinePlayer(
            PlayerData d
    ) {
        var r =
                new SeasonArchiveManager.SeasonRecord();

        r.season = CURRENT_SEASON;
        r.seasonName = CURRENT_NAME;
        r.finalRp = d.rp;
        r.peakRp = d.peakRp;
        r.wins = d.rankedWins;
        r.losses = d.rankedLosses;
        r.bestStreak = d.bestStreak;

        r.finishRank =
                RankManager.getRank(
                        r.finalRp
                ).name;

        r.peakRank =
                RankManager.getRank(
                        r.peakRp
                ).name;

        SeasonArchiveManager.archive(
                d.name,
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

        PlayerData d =
                PlayerDataManager.load(
                        player.getUUID(),
                        player.getName().getString()
                );

        d.rp = reset;
        d.peakRp = reset;
        d.rankedWins = 0;
        d.rankedLosses = 0;
        d.currentStreak = 0;
        d.bestStreak = 0;
        d.upsetWins = 0;
        d.seasonsPlayed++;

        PlayerDataManager.save(
                player.getUUID(),
                d
        );
    }

    private static PlayerSnapshot snapshotOf(
            PlayerData d
    ) {
        PlayerSnapshot s =
                new PlayerSnapshot();

        s.uuid = d.uuid;
        s.name = d.name;
        s.rp = d.rp;
        s.peakRp = d.peakRp;
        s.rankedWins = d.rankedWins;
        s.rankedLosses = d.rankedLosses;
        s.casualWins = d.casualWins;
        s.casualLosses = d.casualLosses;
        s.currentStreak = d.currentStreak;
        s.bestStreak = d.bestStreak;
        s.upsetWins = d.upsetWins;
        s.highestRank = d.highestRank;
        s.seasonsPlayed = d.seasonsPlayed;

        return s;
    }

    private static PlayerData dataFromSnapshot(
            PlayerSnapshot s
    ) {
        PlayerData d =
                new PlayerData();

        d.uuid = s.uuid;
        d.name = s.name;
        d.rp = s.rp;
        d.peakRp = s.peakRp;
        d.rankedWins = s.rankedWins;
        d.rankedLosses = s.rankedLosses;
        d.casualWins = s.casualWins;
        d.casualLosses = s.casualLosses;
        d.currentStreak = s.currentStreak;
        d.bestStreak = s.bestStreak;
        d.upsetWins = s.upsetWins;
        d.highestRank = s.highestRank;
        d.seasonsPlayed = s.seasonsPlayed;

        return d;
    }

    private static void saveRollbackState(
            MinecraftServer server
    ) {
        try {
            RollbackState state =
                    new RollbackState();

            state.season = CURRENT_SEASON;
            state.seasonName = CURRENT_NAME;

            for (var entry :
                    PlayerDataManager.getAllPlayers()) {

                PlayerData d = entry.data;

                if (d.uuid == null || d.uuid.isBlank()) {
                    d.uuid = entry.uuid;
                }

                if (d.name == null || d.name.isBlank()) {
                    d.name = entry.name;
                }

                state.players.put(
                        d.uuid,
                        snapshotOf(d)
                );
            }

            for (ServerPlayer player :
                    server.getPlayerList().getPlayers()) {

                PlayerData d =
                        PlayerDataManager.load(
                                player.getUUID(),
                                player.getName().getString()
                        );

                d.rp = ProfileManager.getCurrentRp(player);

                state.players.put(
                        player.getUUID().toString(),
                        snapshotOf(d)
                );
            }

            try (FileWriter w =
                         new FileWriter(
                                 getRollbackFile()
                         )) {
                GSON.toJson(
                        state,
                        w
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void rollbackSeason(
            MinecraftServer server
    ) {
        try {
            File f = getRollbackFile();

            if (!f.exists()) {
                server.getPlayerList()
                        .broadcastSystemMessage(
                                Component.literal(
                                        "§cNo rollback_state.json found. Start a season once before rolling back."
                                ),
                                false
                        );
                return;
            }

            RollbackState state;

            try (FileReader r = new FileReader(f)) {
                state = GSON.fromJson(
                        r,
                        RollbackState.class
                );
            }

            if (state == null || state.players == null) {
                server.getPlayerList()
                        .broadcastSystemMessage(
                                Component.literal(
                                        "§cRollback failed: rollback_state.json was empty or invalid."
                                ),
                                false
                        );
                return;
            }

            pendingSeasonReset = false;
            resetTickCountdown = 0;
            pendingSeasonName = null;

            int archivedSeason = CURRENT_SEASON - 1;

            CURRENT_SEASON = state.season;
            CURRENT_NAME = state.seasonName;

            for (PlayerSnapshot snapshot :
                    state.players.values()) {

                if (snapshot.uuid == null || snapshot.uuid.isBlank()) {
                    continue;
                }

                UUID uuid = UUID.fromString(snapshot.uuid);
                PlayerData d = dataFromSnapshot(snapshot);

                PlayerDataManager.save(
                        uuid,
                        d
                );

                ServerPlayer online =
                        server.getPlayerList()
                                .getPlayer(uuid);

                if (online != null) {
                    ProfileManager.setElo(
                            online,
                            d.rp
                    );
                }
            }

            int seasonToRemove =
                    archivedSeason >= CURRENT_SEASON
                            ? archivedSeason
                            : CURRENT_SEASON;

            File dir =
                    new File(
                            "config/champutils/seasons"
                    );

            File[] files =
                    dir.listFiles(
                            (d, n) -> n.endsWith(".json")
                    );

            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith("season_")) {
                        continue;
                    }

                    String player =
                            file.getName()
                                    .replace(
                                            ".json",
                                            ""
                                    );

                    SeasonArchiveManager.removeSeason(
                            player,
                            seasonToRemove
                    );
                }
            }

            SeasonArchiveManager.removeSeasonSnapshot(
                    seasonToRemove
            );

            saveState();
            LeaderboardManager.refresh(server);

            server.getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(
                                    "§aRolled back to Season " +
                                            CURRENT_SEASON +
                                            " §e" +
                                            CURRENT_NAME +
                                            "§a."
                            ),
                            false
                    );

        } catch (Exception e) {
            e.printStackTrace();

            server.getPlayerList()
                    .broadcastSystemMessage(
                            Component.literal(
                                    "§cRollback failed. Check console for the error."
                            ),
                            false
                    );
        }
    }
}
