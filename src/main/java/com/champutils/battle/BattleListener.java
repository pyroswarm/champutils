package com.champutils.battle;

import com.champutils.config.Config;
import com.champutils.config.Rank;
import com.champutils.database.RankedStatsDatabaseRepository;
import com.champutils.matchmaking.ArenaManager;
import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.ProfileManager;
import com.champutils.guild.GuildXpManager;
import com.champutils.guild.GuildBossManager;
import java.util.UUID;

import com.champutils.profession.*;

import com.champutils.rank.RankManager;
import com.champutils.scoreboard.PlayerSidebarManager;
import com.champutils.validation.TeamSnapshotManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BattleListener {

    public static void onBattleEnd(
            ServerPlayer winner,
            ServerPlayer loser
    ) {
        onBattleEnd(winner, loser, null);
    }

    public static void onBattleEnd(
            ServerPlayer winner,
            ServerPlayer loser,
            UUID losingNpcUuid
    ) {

        if (winner == null) {
            return;
        }

        BattleContextManager.BattleType battleType =
                BattleContextManager.getContext(
                        winner.getUUID()
                );

        if (battleType == null) {
            battleType =
                    BattleContextManager.BattleType.UNKNOWN;
        }

        if (battleType == BattleContextManager.BattleType.WORLD_BOSS &&
                (losingNpcUuid == null || !GuildBossManager.isActiveWorldBossNpc(losingNpcUuid))) {
            battleType = BattleContextManager.BattleType.UNKNOWN;
        }

        com.champutils.quest.QuestManager.recordBattleWin(
                winner,
                battleType
        );

        com.champutils.cosmetic.TitleRegistry.handleBattleWin(
                winner,
                battleType
        );

        awardBattleProfessionXp(
                winner,
                battleType
        );

        if (loser == null) {
            cleanupSingle(winner);
            return;
        }

        boolean ranked =
                battleType ==
                        BattleContextManager.BattleType.RANKED;

        if (battleType == BattleContextManager.BattleType.RANKED ||
                battleType == BattleContextManager.BattleType.CASUAL) {
            GuildXpManager.awardBattleWin(winner, ranked);
        }

        boolean upsetWin =
                getRankIndex(loser) >
                        getRankIndex(winner);

        ProfileManager.recordWin(
                winner,
                ranked,
                upsetWin
        );

        ProfileManager.recordLoss(
                loser,
                ranked
        );

        if (
                Config.arenas != null &&
                        !Config.arenas.isEmpty()
        ) {
            ArenaManager.returnPlayer(winner);
            ArenaManager.returnPlayer(loser);

            ArenaManager.releaseArena(winner);
            ArenaManager.releaseArena(loser);
        }

        if (!ranked) {
            cleanup(winner, loser);
            return;
        }

        int winnerElo =
                ProfileManager.getCurrentRp(winner);

        int loserElo =
                ProfileManager.getCurrentRp(loser);

        int change =
                calculateRpChange(
                        winner,
                        loser
                );

        int newWinner =
                winnerElo + change;

        int newLoser =
                Math.max(
                        0,
                        loserElo - change
                );

        ProfileManager.setElo(
                winner,
                newWinner
        );

        ProfileManager.setElo(
                loser,
                newLoser
        );

        PlayerDataManager.setRp(
                winner.getUUID(),
                winner.getName().getString(),
                newWinner
        );

        PlayerDataManager.setRp(
                loser.getUUID(),
                loser.getName().getString(),
                newLoser
        );

        RankedStatsDatabaseRepository.syncPlayer(
                winner.getUUID(),
                winner.getName().getString(),
                PlayerDataManager.load(
                        winner.getUUID(),
                        winner.getName().getString()
                )
        );

        RankedStatsDatabaseRepository.syncPlayer(
                loser.getUUID(),
                loser.getName().getString(),
                PlayerDataManager.load(
                        loser.getUUID(),
                        loser.getName().getString()
                )
        );

        RankManager.updatePlayerRank(
                winner,
                winnerElo,
                newWinner
        );

        RankManager.updatePlayerRank(
                loser,
                loserElo,
                newLoser
        );

        winner.sendSystemMessage(
                Component.literal(
                        "§a+" + change + " RP"
                )
        );

        loser.sendSystemMessage(
                Component.literal(
                        "§c-" + change + " RP"
                )
        );

        PlayerSidebarManager.update(winner);
        PlayerSidebarManager.update(loser);

        cleanup(winner, loser);
    }

    private static void awardBattleProfessionXp(
            ServerPlayer winner,
            BattleContextManager.BattleType type
    ) {
        int xp = 0;

        switch (type) {

            case RANKED:
            case CASUAL:
            case GYM:
            case ELITE_FOUR:
            case TOURNAMENT:
                xp = 0;
                break;

            case NPC:
                xp = getBattleXp("npc");
                NpcBattleRewardManager.rollReward(
                        winner
                );
                break;

            case WORLD_BOSS:
                xp = getBattleXp("world_boss");
                WorldBossRewardManager.rollReward(
                        winner
                );
                break;

            case MEGA_BOSS:
                // Rewards are handled by MegaBossBattleListener so these fights do not roll normal wild/NPC loot.
                xp = 0;
                break;

            case PROFESSION:
                xp = getBattleXp("profession");
                break;

            case UNKNOWN:
            default:
                xp = getBattleXp("wild");

                WildBattleRewardManager.rollReward(
                        winner
                );
                break;
        }

        if (xp <= 0) {
            return;
        }

        ProfessionManager.addXp(
                winner,
                ProfessionType.BATTLING,
                xp
        );
    }

    private static int getBattleXp(String key) {
        Integer value =
                ProfessionConfig
                        .SETTINGS
                        .battleXp
                        .get(key);

        return value == null ? 0 : value;
    }

    private static void cleanup(
            ServerPlayer winner,
            ServerPlayer loser
    ) {
        TeamSnapshotManager.clear(winner);
        TeamSnapshotManager.clear(loser);

        BattleContextManager.clearContext(
                winner.getUUID()
        );

        BattleContextManager.clearContext(
                loser.getUUID()
        );

        MatchmakingManager.clearMatch(winner);
        MatchmakingManager.clearMatch(loser);
    }

    private static void cleanupSingle(
            ServerPlayer player
    ) {
        BattleContextManager.clearContext(
                player.getUUID()
        );

        MatchmakingManager.clearMatch(player);
    }

    private static int calculateRpChange(
            ServerPlayer winner,
            ServerPlayer loser
    ) {
        int winnerRankIndex = getRankIndex(winner);
        int loserRankIndex = getRankIndex(loser);

        int rankGap = winnerRankIndex - loserRankIndex;

        // Positive gap = the higher-ranked player won.
        // In that case, the lower-ranked loser is protected,
        // but still loses at least 10 RP so the ladder keeps moving.
        if (rankGap > 0) {
            return Math.max(
                    10,
                    20 - (rankGap * 4)
            );
        }

        // Negative gap = an upset win. Reward the lower-ranked winner more,
        // but cap it at 30 RP to avoid ladder inflation.
        if (rankGap < 0) {
            int upsetGap = Math.abs(rankGap);
            return Math.min(
                    30,
                    20 + (upsetGap * 4)
            );
        }

        return 20;
    }

    private static int getRankIndex(
            ServerPlayer player
    ) {
        if (player == null || Config.ranks == null || Config.ranks.isEmpty()) {
            return 0;
        }

        int rp = ProfileManager.getCurrentRp(player);
        Rank currentRank = RankManager.getRank(rp);

        if (currentRank == null) {
            return 0;
        }

        int index = 0;

        for (Rank rank : Config.ranks) {
            if (rank == null) {
                continue;
            }

            if (rank.min_elo < currentRank.min_elo) {
                index++;
            }
        }

        return index;
    }
}