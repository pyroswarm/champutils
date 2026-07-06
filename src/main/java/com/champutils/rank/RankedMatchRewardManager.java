package com.champutils.rank;

import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class RankedMatchRewardManager {
    private static final Map<UUID, LocalDate> FIRST_WIN_CACHE = new ConcurrentHashMap<>();

    private RankedMatchRewardManager() {
    }

    public static void awardRankedMatch(ServerPlayer winner, ServerPlayer loser, boolean upsetWin) {
        if (winner == null || loser == null) return;

        RankedTokenConfig.Config config = RankedTokenConfig.CONFIG;
        long participationCredits = Math.max(0L, config.rankedParticipationCredits);
        long loserCredits = participationCredits;

        if (loserCredits > 0L) {
            EconomyManager.deposit(loser, loserCredits, "Ranked PvP participation");
            loser.sendSystemMessage(Component.literal("§6Ranked rewards: §a+" + EconomyManager.format(loserCredits) + "§7."));
        }

        long winnerCredits = participationCredits + Math.max(0L, config.rankedWinBonusCredits);
        if (upsetWin) winnerCredits += Math.max(0L, config.rankedUpsetWinBonusCredits);

        int streak = Math.max(0, ProfileManager.getCurrentStreak(winner));
        int streakBonusWins = Math.min(Math.max(0, config.rankedWinStreakBonusCap), Math.max(0, streak - 1));
        if (streakBonusWins > 0 && config.rankedWinStreakBonusCredits > 0L) {
            winnerCredits += config.rankedWinStreakBonusCredits * (long) streakBonusWins;
        }

        long firstWinCredits = Math.max(0L, config.rankedFirstWinOfDayCredits);
        if (firstWinCredits <= 0L) {
            payWinner(winner, winnerCredits, 0L, streakBonusWins, upsetWin);
            return;
        }

        MinecraftServer server = winner.getServer();
        if (server == null) return;
        long baseWinnerCredits = winnerCredits;
        claimFirstWinOfDay(winner)
                .exceptionally(error -> false)
                .thenAccept(firstWin -> {
                    server.execute(() -> payWinner(
                            winner,
                            baseWinnerCredits + (firstWin ? firstWinCredits : 0L),
                            firstWin ? firstWinCredits : 0L,
                            streakBonusWins,
                            upsetWin
                    ));
                });
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ranked reward schema", connection -> {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("create table if not exists ranked_daily_rewards (" +
                        "profile_uuid uuid not null, " +
                        "reward_day date not null, " +
                        "first_win_claimed_at timestamptz not null default now(), " +
                        "primary key(profile_uuid, reward_day))");
                statement.executeUpdate("create index if not exists idx_ranked_daily_rewards_day on ranked_daily_rewards(reward_day desc)");
            }
        });
    }

    private static CompletableFuture<Boolean> claimFirstWinOfDay(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (!DatabaseManager.isEnabled()) {
            LocalDate today = LocalDate.now();
            LocalDate previous = FIRST_WIN_CACHE.get(profileId);
            if (today.equals(previous)) {
                return CompletableFuture.completedFuture(false);
            }
            FIRST_WIN_CACHE.put(profileId, today);
            return CompletableFuture.completedFuture(true);
        }

        return DatabaseManager.supplyAsync("claim ranked first win " + profileId, connection -> {
            try (var statement = connection.prepareStatement(
                    "insert into ranked_daily_rewards(profile_uuid, reward_day, first_win_claimed_at) " +
                            "values (?, current_date, now()) on conflict(profile_uuid, reward_day) do nothing")) {
                statement.setObject(1, profileId);
                return statement.executeUpdate() > 0;
            }
        });
    }

    private static void payWinner(ServerPlayer winner, long credits, long firstWinCredits, int streakBonusWins, boolean upsetWin) {
        if (winner == null || credits <= 0L) return;
        EconomyManager.deposit(winner, credits, "Ranked PvP victory");

        StringBuilder message = new StringBuilder("§6Ranked rewards: §a+")
                .append(EconomyManager.format(credits))
                .append("§7.");
        if (firstWinCredits > 0L) {
            message.append(" §eFirst win +").append(EconomyManager.format(firstWinCredits)).append("§7.");
        }
        if (streakBonusWins > 0) {
            message.append(" §bWin streak bonus active§7.");
        }
        if (upsetWin) {
            message.append(" §dUpset bonus§7.");
        }
        winner.sendSystemMessage(Component.literal(message.toString()));
    }
}
