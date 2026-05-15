package com.champutils.battle;

import com.champutils.worldevent.WorldEventManager;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;

import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Resolves world event boss battles.
 *
 * This is intentionally in the battle package so the event system is completed
 * by the same Cobblemon BattleVictoryEvent flow as ranked, gyms, NPC battles,
 * and wild battles.
 */
public final class WorldEventBattleListener {

    private WorldEventBattleListener() {
    }

    public static void register() {
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            try {
                handleVictory((BattleVictoryEvent) event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void handleVictory(BattleVictoryEvent event) {
        if (event == null) {
            return;
        }

        List<ServerPlayer> playerWinners = new ArrayList<>();
        UUID defeatedNpcUuid = null;

        for (var actor : event.getWinners()) {
            if (actor instanceof PlayerBattleActor playerActor) {
                ServerPlayer player = (ServerPlayer) playerActor.getEntity();
                if (player != null && !playerWinners.contains(player)) {
                    playerWinners.add(player);
                }
            }
        }

        for (var actor : event.getLosers()) {
            if (actor instanceof NPCBattleActor npcActor) {
                defeatedNpcUuid = npcActor.getEntity().getUUID();
                break;
            }
        }

        if (playerWinners.isEmpty() || defeatedNpcUuid == null) {
            return;
        }

        WorldEventManager.ActiveEvent active =
                WorldEventManager.getByNpc(defeatedNpcUuid);

        if (active == null) {
            return;
        }

        ServerPlayer firstWinner = playerWinners.get(0);
        if (firstWinner.getServer() == null) {
            return;
        }

        WorldEventManager.handleBossDefeated(
                firstWinner.getServer(),
                active,
                playerWinners
        );
    }
}
