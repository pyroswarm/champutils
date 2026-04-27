package com.champutils.battle;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;

import net.minecraft.server.level.ServerPlayer;

public class BattlePrepManager {

    private BattlePrepManager(){}

    public static void healParty(
            ServerPlayer player
    ) {

        PartyStore party =
                Cobblemon.INSTANCE
                        .getStorage()
                        .getParty(player);

        if (party == null) {
            return;
        }

        for (
                Pokemon mon :
                party
        ) {

            if (mon == null) {
                continue;
            }

            try {

                mon.heal();

            } catch(Exception e) {
                e.printStackTrace();
            }
        }
    }


    public static void prepareRankedBattle(
            ServerPlayer p1,
            ServerPlayer p2
    ) {

        healParty(p1);
        healParty(p2);
    }
}