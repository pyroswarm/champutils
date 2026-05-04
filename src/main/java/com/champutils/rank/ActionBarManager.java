package com.champutils.rank;

import com.champutils.config.Rank;
import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.battle.BattleContextManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class ActionBarManager {

    public static void update(ServerPlayer player) {

        /*
         Only show ranked display while:
         - queued
         - actively in PvP battle
         */
        if (!shouldShowRankDisplay(player)) {
            return;
        }

        int elo = getElo(player);

        Rank rank = RankManager.getRank(elo);

        if (rank == null) {
            return;
        }

        String rankName = rank.name;

        String text;

        if (rankName.equalsIgnoreCase("Monarch")) {

            text =
                    "§5" + rankName +
                            " §8| §dRP: §f" +
                            elo;
        }
        else if (rankName.equalsIgnoreCase("Grandmaster")) {

            text =
                    "§c" + rankName +
                            " §8| §6RP: §f" +
                            elo;
        }
        else {

            text =
                    "§" + color(rank.color) +
                            rankName +
                            " §8| §7RP: §f" +
                            elo;
        }

        player.displayClientMessage(
                Component.literal(text),
                true
        );
    }

    private static boolean shouldShowRankDisplay(
            ServerPlayer player
    ) {

        /*
         Show while queued
         */
        if (MatchmakingManager.isQueued(player)) {
            return true;
        }

        BattleContextManager.BattleType type =
                BattleContextManager.getContext(
                        player.getUUID()
                );

        /*
         Show only during PvP battles
         */
        return type ==
                BattleContextManager.BattleType.RANKED
                ||
                type ==
                        BattleContextManager.BattleType.CASUAL
                ||
                type ==
                        BattleContextManager.BattleType.TOURNAMENT;
    }

    private static int getElo(
            ServerPlayer player
    ) {
        var sb = player.getScoreboard();
        var obj = sb.getObjective("elo");

        if (obj == null) {
            return 0;
        }

        return sb.getOrCreatePlayerScore(
                player,
                obj
        ).get();
    }

    private static String color(
            String c
    ) {
        return switch (c) {

            case "dark_green" -> "2";
            case "gold" -> "6";
            case "yellow" -> "e";
            case "green" -> "a";
            case "aqua" -> "b";
            case "blue" -> "9";
            case "red" -> "c";
            case "light_purple" -> "d";
            case "dark_purple" -> "5";
            case "dark_red" -> "4";

            default -> "7";
        };
    }
}