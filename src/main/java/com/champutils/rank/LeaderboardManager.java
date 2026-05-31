package com.champutils.rank;

import com.champutils.profile.PlayerDataManager;
import com.champutils.leaderboard.ProfileLeaderboardRepository;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class LeaderboardManager {

    public static class Entry {

        public String playerName;
        public UUID uuid;
        public int rp;

        public Entry(String playerName, int rp) {
            this(playerName, null, rp);
        }

        public Entry(String playerName, UUID uuid, int rp) {
            this.playerName = playerName;
            this.uuid = uuid;
            this.rp = rp;
        }
    }

    private static final List<Entry> TOP = new ArrayList<>();

    public static void refresh(MinecraftServer server) {
        TOP.clear();

        var profileRows = ProfileLeaderboardRepository.top(ProfileLeaderboardRepository.Board.RANKED, 100);
        if (!profileRows.isEmpty()) {
            for (var row : profileRows) {
                String displayName = row.profileName() + " (" + row.playerName() + ")";
                TOP.add(new Entry(displayName, row.profileId(), row.rp()));
            }
            TOP.sort(Comparator.comparingInt((Entry e) -> e.rp).reversed());
            return;
        }

        for (PlayerDataManager.OfflinePlayerEntry player : PlayerDataManager.getAllProfilePlayers()) {
            if (player == null || player.data == null || player.name == null) {
                continue;
            }

            UUID uuid = null;
            try {
                if (player.uuid != null && !player.uuid.isBlank()) {
                    uuid = UUID.fromString(player.uuid);
                }
            } catch (Exception ignored) {}

            TOP.add(new Entry(player.name, uuid, player.data.rp));
        }

        TOP.sort(Comparator.comparingInt((Entry e) -> e.rp).reversed());
    }

    public static List<Entry> getTop(int amount) {
        if (amount >= TOP.size()) {
            return new ArrayList<>(TOP);
        }

        return new ArrayList<>(TOP.subList(0, amount));
    }

    public static int getRankPosition(String playerName) {
        for (int i = 0; i < TOP.size(); i++) {
            if (TOP.get(i).playerName.equalsIgnoreCase(playerName)) {
                return i + 1;
            }
        }

        return -1;
    }
}
