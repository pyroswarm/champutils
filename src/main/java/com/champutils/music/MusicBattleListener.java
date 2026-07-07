package com.champutils.music;

import com.champutils.battle.BattleContextManager;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import net.minecraft.server.level.ServerPlayer;

public final class MusicBattleListener {
    private MusicBattleListener() {}

    public static void register() {
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            if (event instanceof BattleStartedEvent.Post post) handleBattleStarted(post);
        });
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {
            if (event instanceof BattleVictoryEvent victory) handleVictory(victory);
        });
    }

    private static void handleBattleStarted(BattleStartedEvent.Post event) {
        try {
            boolean hasNpc = false;
            boolean hasWild = false;
            for (var actor : event.getBattle().getActors()) {
                if (actor.getType() == ActorType.NPC) hasNpc = true;
                if (actor.getType() == ActorType.WILD) hasWild = true;
            }

            for (var actor : event.getBattle().getActors()) {
                if (!(actor instanceof PlayerBattleActor playerActor)) continue;
                ServerPlayer player = (ServerPlayer) playerActor.getEntity();
                BattleContextManager.BattleType type = BattleContextManager.getContext(player.getUUID());
                String mapped = MusicConfig.ROOT.battleTracks.get(type.name());
                if (mapped == null || mapped.isBlank()) mapped = hasNpc ? "npc_battle" : hasWild ? "wild_battle" : "ranked_battle";
                MusicManager.forceTrack(player, mapped, 60 * 60);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils][Music] Battle start hook failed: " + e.getMessage());
        }
    }

    private static void handleVictory(BattleVictoryEvent event) {
        try {
            for (var actor : event.getBattle().getActors()) {
                if (!(actor instanceof PlayerBattleActor playerActor)) continue;
                ServerPlayer player = (ServerPlayer) playerActor.getEntity();
                MusicManager.forceTrack(player, MusicConfig.ROOT.victoryTrack, MusicConfig.ROOT.victorySeconds);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils][Music] Battle victory hook failed: " + e.getMessage());
        }
    }
}
