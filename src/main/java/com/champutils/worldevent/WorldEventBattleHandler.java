package com.champutils.worldevent;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;

import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class WorldEventBattleHandler {

    private WorldEventBattleHandler() {}

    public static void register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(WorldEventBattleHandler::handleVictory);
    }

    private static void handleVictory(BattleVictoryEvent event) {
        try {
            ServerPlayer winner = null;
            UUID npcUuid = null;

            for (var actor : event.getWinners()) {
                if (actor instanceof PlayerBattleActor playerActor) {
                    winner = (ServerPlayer) playerActor.getEntity();
                    break;
                }
            }

            for (var actor : event.getLosers()) {
                if (actor instanceof NPCBattleActor npcActor) {
                    npcUuid = npcActor.getEntity().getUUID();
                    break;
                }
            }

            if (winner == null || npcUuid == null) return;

            WorldEventManager.ActiveEvent active = WorldEventManager.getByNpc(npcUuid);
            if (active == null) return;

            WorldEventManager.handleBossDefeated(winner.getServer(), active, winner);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
