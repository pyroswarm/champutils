package com.champutils.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.validation.TeamValidator;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;

public class CobblemonBattleStartHandler {

    public static void register() {

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {

            BattleStartedEvent.Pre e = (BattleStartedEvent.Pre) event;

            ServerPlayer p1 = null;
            ServerPlayer p2 = null;

            for (var actor : e.getBattle().getActors()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    if (p1 == null) p1 = (ServerPlayer) playerActor.getEntity();
                    else {
                        p2 = (ServerPlayer) playerActor.getEntity();
                        break;
                    }
                }
            }

            if (p1 == null || p2 == null) return;

            // mark battle state
            BattleStateManager.setInBattle(p1, true);
            BattleStateManager.setInBattle(p2, true);

            // ONLY ranked gets validation
            if (!MatchmakingManager.isRankedMatch(p1)) return;

            // ========================
            // 🔥 FINAL VALIDATION ONLY
            // ========================
            String err1 = TeamValidator.validate(p1, "ranked");
            String err2 = TeamValidator.validate(p2, "ranked");

            if (err1 != null || err2 != null) {
                cancelMatch(p1, p2,
                        err1 != null ? err1 : "Opponent has invalid team",
                        err2 != null ? err2 : "Opponent has invalid team");
            }
        });
    }

    private static void cancelMatch(ServerPlayer p1, ServerPlayer p2, String m1, String m2) {

        p1.sendSystemMessage(Component.literal("§c" + m1));
        p2.sendSystemMessage(Component.literal("§c" + m2));

        MatchmakingManager.clearMatch(p1);
        MatchmakingManager.clearMatch(p2);
    }
}