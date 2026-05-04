package com.champutils.battle;

import com.champutils.config.Config;
import com.champutils.matchmaking.ArenaManager;
import com.champutils.profile.PlayerDataManager;
import com.champutils.profile.ProfileManager;

import com.champutils.profession.*;

import com.champutils.rank.RankManager;
import com.champutils.validation.TeamSnapshotManager;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BattleListener {

    public static void onBattleEnd(
            ServerPlayer winner,
            ServerPlayer loser
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
    }

    private static void cleanupSingle(
            ServerPlayer player
    ) {
        BattleContextManager.clearContext(
                player.getUUID()
        );
    }

    private static int calculateRpChange(
            ServerPlayer winner,
            ServerPlayer loser
    ) {
        return 20;
    }

    private static int getRankIndex(
            ServerPlayer player
    ) {
        return 0;
    }
}