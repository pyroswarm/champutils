package com.champutils.battle;

import com.champutils.dungeon.DungeonManager;
import com.champutils.dungeon.DungeonSession;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class DungeonBattleListener {

    private DungeonBattleListener() {}

    public static void register() {
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            try { handleBattleStart((BattleStartedEvent.Pre) event); }
            catch (Exception e) { e.printStackTrace(); }
        });

        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            try { handleVictory((BattleVictoryEvent) event); }
            catch (Exception e) { e.printStackTrace(); }
        });
    }

    private static void handleBattleStart(BattleStartedEvent.Pre pre) {
        if (pre == null || pre.getBattle() == null) return;

        ServerPlayer player = null;
        NPCBattleActor npcActor = null;

        for (var actor : pre.getBattle().getActors()) {
            if (actor instanceof PlayerBattleActor p) player = (ServerPlayer) p.getEntity();
            if (actor instanceof NPCBattleActor n) npcActor = n;
        }

        if (player == null || npcActor == null) return;

        DungeonSession session = DungeonManager.getSession(player);
        if (session == null) return;

        UUID npcUuid = npcActor.getEntity().getUUID();
        if (session.activeTrainerUuid == null || !session.activeTrainerUuid.equals(npcUuid)) {
            player.sendSystemMessage(Component.literal("You cannot start outside battles while inside a dungeon.").withStyle(ChatFormatting.RED));
            pre.cancel();
            return;
        }

        int cap = session.rarity.getPokemonLevel();
        for (Pokemon mon : PlayerExtensionsKt.party(player)) {
            if (mon == null) continue;
            if (mon.getLevel() > cap) {
                player.sendSystemMessage(Component.literal("This dungeon has a level cap of " + cap + ".").withStyle(ChatFormatting.RED));
                pre.cancel();
                return;
            }
        }
    }

    private static void handleVictory(BattleVictoryEvent event) {
        if (event == null) return;

        ServerPlayer winningPlayer = null;
        ServerPlayer losingPlayer = null;
        UUID winningNpcUuid = null;
        UUID losingNpcUuid = null;

        for (var actor : event.getWinners()) {
            if (actor instanceof PlayerBattleActor p && winningPlayer == null) winningPlayer = (ServerPlayer) p.getEntity();
            if (actor instanceof NPCBattleActor n && winningNpcUuid == null) winningNpcUuid = n.getEntity().getUUID();
        }

        for (var actor : event.getLosers()) {
            if (actor instanceof PlayerBattleActor p && losingPlayer == null) losingPlayer = (ServerPlayer) p.getEntity();
            if (actor instanceof NPCBattleActor n && losingNpcUuid == null) losingNpcUuid = n.getEntity().getUUID();
        }

        if (winningPlayer != null && losingNpcUuid != null) {
            DungeonManager.handleTrainerVictory(winningPlayer, losingNpcUuid);
            return;
        }

        if (losingPlayer != null && winningNpcUuid != null) {
            DungeonManager.handlePlayerLost(losingPlayer, winningNpcUuid);
        }
    }
}
