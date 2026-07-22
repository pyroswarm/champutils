package com.champutils.rank;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profile.ProfileManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.PlayerDataManager.PlayerData;
import com.champutils.database.SeasonDatabaseRepository;
import com.champutils.database.RankedFormatDatabaseRepository;
import com.champutils.database.SeasonProfileDatabaseRepository;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.leaderboard.ProfileLeaderboardRepository;

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
import java.time.*;
import java.time.format.DateTimeFormatter;

public class SeasonManager {

    private static final String SHARED_STATE_KEY = "season_state_v2";
    private static final String SHARED_ROLLBACK_KEY = "season_rollback_state_v2";
    private static final UUID INVALIDATION_OWNER = new UUID(0L, 0L);

    public static int CURRENT_SEASON = 0;
    public static String CURRENT_NAME = "Offseason";

    public static int RESET_FLOOR = 300;
    public static double RESET_PERCENT = .50;

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static boolean pendingSeasonReset = false;
    private static int resetTickCountdown = 0;
    private static String pendingSeasonName = null;
    private static long seasonStartedAtEpochMs = 0L;
    private static long seasonEndsAtEpochMs = 0L;
    private static int autoSeasonCheckTicks = 0;
    public static final int SEASON_LENGTH_DAYS = 60;

    public static class SeasonState {
        public int currentSeason = 0;
        public String currentName = "Offseason";
        public long startedAtEpochMs = 0L;
        public long endsAtEpochMs = 0L;
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
        SeasonState local = loadLocalState();
        SeasonState shared = SharedJsonStateRepository.loadGlobal(
                SHARED_STATE_KEY,
                SeasonState.class,
                local
        );
        if (shared == null) shared = local;
        CURRENT_SEASON = Math.max(0, shared.currentSeason);
        CURRENT_NAME = shared.currentName == null || shared.currentName.isBlank()
                ? (CURRENT_SEASON == 0 ? "Offseason" : "Season " + CURRENT_SEASON)
                : shared.currentName;
        seasonStartedAtEpochMs = shared.startedAtEpochMs;
        seasonEndsAtEpochMs = shared.endsAtEpochMs;
        ensureSeasonWindow();
        saveState(false);
    }

    private static SeasonState loadLocalState() {
        SeasonState fallback = new SeasonState();
        try {
            File file = getStateFile();
            if (!file.exists()) return fallback;
            try (FileReader reader = new FileReader(file)) {
                SeasonState state = GSON.fromJson(reader, SeasonState.class);
                return state == null ? fallback : state;
            }
        } catch (Exception error) {
            error.printStackTrace();
            return fallback;
        }
    }

    public static void saveState() {
        saveState(true);
    }

    private static void saveState(boolean publish) {
        try {
            SeasonState state = new SeasonState();
            state.currentSeason = Math.max(0, CURRENT_SEASON);
            state.currentName = CURRENT_NAME;
            state.startedAtEpochMs = seasonStartedAtEpochMs;
            state.endsAtEpochMs = seasonEndsAtEpochMs;
            try (FileWriter writer = new FileWriter(getStateFile())) {
                GSON.toJson(state, writer);
            }
            SharedJsonStateRepository.saveGlobalAsync(SHARED_STATE_KEY, state)
                    .whenComplete((ignored, error) -> {
                        if (error != null) {
                            error.printStackTrace();
                            return;
                        }
                        if (publish) {
                            NetworkEventManager.publishCacheInvalidation("SEASON_STATE", INVALIDATION_OWNER);
                        }
                    });
        } catch (Exception error) {
            error.printStackTrace();
        }
    }

    public static void refreshSharedStateAsync(MinecraftServer server) {
        SharedJsonStateRepository.loadGlobalAsync(
                SHARED_STATE_KEY,
                SeasonState.class,
                null
        ).whenComplete((state, error) -> {
            if (error != null || state == null) {
                if (error != null) error.printStackTrace();
                return;
            }
            Runnable apply = () -> {
                CURRENT_SEASON = Math.max(0, state.currentSeason);
                CURRENT_NAME = state.currentName == null || state.currentName.isBlank()
                        ? (CURRENT_SEASON == 0 ? "Offseason" : "Season " + CURRENT_SEASON)
                        : state.currentName;
                seasonStartedAtEpochMs = state.startedAtEpochMs;
                seasonEndsAtEpochMs = state.endsAtEpochMs;
                ensureSeasonWindow();
                try (FileWriter writer = new FileWriter(getStateFile())) {
                    GSON.toJson(state, writer);
                } catch (Exception ignored) {
                }
                RankedFormatDatabaseRepository.syncCurrentFormats();
                if (server != null) LeaderboardManager.refreshNow(server);
            };
            if (server != null) server.execute(apply);
            else apply.run();
        });
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

        var profileTop = ProfileLeaderboardRepository.topFresh(ProfileLeaderboardRepository.Board.RANKED, 100);
        if (!profileTop.isEmpty()) {
            for (var e : profileTop) {
                String displayName = e.profileName() + " (" + e.playerName() + ")";
                top.add(
                        new SeasonArchiveManager.LadderEntry(
                                displayName,
                                e.rp(),
                                RankManager.getRank(e.rp()).name
                        )
                );
            }
        } else {
            for (var e : LeaderboardManager.getTop(100)) {
                top.add(
                        new SeasonArchiveManager.LadderEntry(
                                e.playerName,
                                e.rp,
                                RankManager.getRank(e.rp).name
                        )
                );
            }
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

            ProfessionNotificationSettings.playSound(p, 
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
        if (++autoSeasonCheckTicks >= 20) {
            autoSeasonCheckTicks = 0;
            ensureSeasonWindow();
            if (!pendingSeasonReset && CURRENT_SEASON > 0 && System.currentTimeMillis() >= seasonEndsAtEpochMs) {
                startNewSeason(server, "Season " + (CURRENT_SEASON + 1));
            }
        }
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
        int oldSeason = CURRENT_SEASON;
        int newSeason = CURRENT_SEASON + 1;
        String safeName = name == null || name.isBlank() ? "Season " + newSeason : name;

        SeasonRewardManager.prepareClaimableRewards(server, oldSeason);

        var networkProfiles = PlayerDataManager.getAllProfilePlayers();
        for (var entry : networkProfiles) {
            if (entry == null || entry.data == null) continue;
            if (entry.data.name == null || entry.data.name.isBlank()) entry.data.name = entry.name;
            archiveOfflinePlayer(entry.data);
        }

        CURRENT_SEASON = newSeason;
        CURRENT_NAME = safeName;
        seasonStartedAtEpochMs = System.currentTimeMillis();
        seasonEndsAtEpochMs = computeSeasonEnd(seasonStartedAtEpochMs);
        saveState();

        SeasonProfileDatabaseRepository.rolloverToNewSeason(
                oldSeason,
                CURRENT_SEASON,
                CURRENT_NAME,
                RESET_FLOOR,
                RESET_PERCENT
        );
        SeasonDatabaseRepository.setActiveSeason(
                CURRENT_SEASON,
                CURRENT_NAME
        );
        RankedFormatDatabaseRepository.syncCurrentFormats();

        for (var entry : networkProfiles) {
            if (entry == null || entry.data == null || entry.uuid == null || entry.uuid.isBlank()) {
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

            try {
                PlayerDataManager.saveProfileDataById(UUID.fromString(entry.uuid), d);
            } catch (Exception ignored) {}
        }

        LeaderboardManager.refreshNow(server);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerData d = PlayerDataManager.load(p.getUUID(), p.getName().getString());
            ProfileManager.setElo(p, d.rp);

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

            ProfessionNotificationSettings.playSound(p, 
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.MASTER,
                    1f,
                    1.2f
            );
        }

        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                server,
                Component.literal(
                        "§6Season " +
                                CURRENT_SEASON +
                                " §e" +
                                CURRENT_NAME +
                                " has begun!"
                )
        );
    }

    private static void ensureSeasonWindow() {
        long now = System.currentTimeMillis();
        if (seasonStartedAtEpochMs <= 0L) seasonStartedAtEpochMs = now;
        if (seasonEndsAtEpochMs <= seasonStartedAtEpochMs) seasonEndsAtEpochMs = computeSeasonEnd(seasonStartedAtEpochMs);
    }

    private static long computeSeasonEnd(long startMs) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime start = Instant.ofEpochMilli(startMs).atZone(zone);
        LocalDate endDate = start.toLocalDate().plusDays(SEASON_LENGTH_DAYS);
        return endDate.atTime(2, 0).atZone(zone).toInstant().toEpochMilli();
    }

    public static long getSeasonEndsAtEpochMs() {
        ensureSeasonWindow();
        return seasonEndsAtEpochMs;
    }

    public static String getSeasonEndDisplay() {
        ensureSeasonWindow();
        return Instant.ofEpochMilli(seasonEndsAtEpochMs).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a z"));
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
                    PlayerDataManager.getAllProfilePlayers()) {

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
                        d.uuid,
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
            SharedJsonStateRepository.saveGlobal(SHARED_ROLLBACK_KEY, state);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Directly sets the active season without archiving ladders, resetting RP, or granting rewards.
     * This is intended for preseason/offseason/admin correction flows only.
     */
    public static void setCurrentSeason(MinecraftServer server, int seasonNumber, String seasonName) {
        pendingSeasonReset = false;
        resetTickCountdown = 0;
        pendingSeasonName = null;

        CURRENT_SEASON = Math.max(0, seasonNumber);
        CURRENT_NAME = seasonName == null || seasonName.isBlank()
                ? (CURRENT_SEASON == 0 ? "Preseason" : "Season " + CURRENT_SEASON)
                : seasonName;

        saveState();
        SeasonDatabaseRepository.setActiveSeason(CURRENT_SEASON, CURRENT_NAME);
        RankedFormatDatabaseRepository.syncCurrentFormats();

        if (server != null) {
            LeaderboardManager.refreshNow(server);
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§7Active season set to §fSeason " + CURRENT_SEASON + " §7" + CURRENT_NAME + "."),
                    false
            );
        }
    }

    public static void resetToSeasonZero(MinecraftServer server) {
        setCurrentSeason(server, 0, "Offseason");
    }

    public static void startPreseason(MinecraftServer server) {
        setCurrentSeason(server, 0, "Preseason");
    }

    public static void rollbackSeason(
            MinecraftServer server
    ) {
        try {
            RollbackState localState = null;
            File file = getRollbackFile();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    localState = GSON.fromJson(reader, RollbackState.class);
                }
            }
            RollbackState state = SharedJsonStateRepository.loadGlobal(
                    SHARED_ROLLBACK_KEY,
                    RollbackState.class,
                    localState
            );

            if (state == null || state.players == null || state.players.isEmpty()) {
                server.getPlayerList()
                        .broadcastSystemMessage(
                                Component.literal(
                                        "§cNo shared rollback snapshot was found. Start a season once before rolling back."
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

                PlayerDataManager.saveProfileDataById(
                        uuid,
                        d
                );

                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    if (com.champutils.profile.PlayerProfileManager.activeProfileId(online).equals(uuid)) {
                        ProfileManager.setElo(
                                online,
                                d.rp
                        );
                    }
                }
            }

            int seasonToRemove =
                    archivedSeason >= CURRENT_SEASON
                            ? archivedSeason
                            : CURRENT_SEASON;

            for (String player : SeasonArchiveManager.archivedPlayerNames()) {
                SeasonArchiveManager.removeSeason(player, seasonToRemove);
            }

            SeasonArchiveManager.removeSeasonSnapshot(
                    seasonToRemove
            );

            saveState();
            SeasonProfileDatabaseRepository.rollbackActiveSeason(CURRENT_SEASON, CURRENT_NAME);
            LeaderboardManager.refreshNow(server);

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
