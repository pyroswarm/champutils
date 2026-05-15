package com.champutils.menu;

import com.champutils.profession.ProfessionType;

import net.minecraft.server.level.ServerPlayer;

public class ProfessionLeaderboardMenu {

    public static void open(
            ServerPlayer player
    ) {
        LeaderboardMenu.openProfessionOverall(
                player
        );
    }

    public static void openOverall(
            ServerPlayer player
    ) {
        LeaderboardMenu.openProfessionOverall(
                player
        );
    }

    public static void openProfession(
            ServerPlayer player,
            ProfessionType type
    ) {
        LeaderboardMenu.openProfession(
                player,
                type
        );
    }
}
