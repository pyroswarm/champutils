package com.champutils.adventurer;

import com.champutils.battle.BattleContextManager;
import com.champutils.battle.PluginTrainerBattleStarter;
import com.champutils.roaming.RoamingTrainerManager;
import com.champutils.roaming.RoamingTrainerPartyBuilder;
import com.champutils.roaming.RoamingTrainerRarity;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Spawns one temporary, player-owned Battle Tower trainer and immediately starts a 3v-party NPC battle. */
public final class BattleTowerTrainerBattle {
    private BattleTowerTrainerBattle() {}

    public static UUID spawnAndStart(ServerPlayer player, ServerLevel level, Vec3 npcPos, float npcYaw, int floor, boolean ultimate) {
        if (player == null || level == null || npcPos == null) return null;
        NPCEntity npc = null;
        try {
            BattleTowerPoolConfig.Tier tier = ultimate ? BattleTowerPoolConfig.tier(10) : BattleTowerPoolConfig.tierForFloor(floor);
            String name = ultimate ? "Ultimate Tower Trainer" : "Battle Tower Trainer - Floor " + floor;
            ChampTrainerSpawner.SpawnResult spawned = ChampTrainerSpawner.spawnRoaming(level, npcPos, npcYaw, name, "");
            if (!spawned.success || spawned.npc == null) return null;
            npc = spawned.npc;
            npc.addTag("champutils_battle_tower");
            npc.addTag("champutils_battle_tower_owner_" + player.getUUID());

            RoamingTrainerManager.RoamingTrainerData data = new RoamingTrainerManager.RoamingTrainerData();
            data.npcUuid = npc.getUUID();
            data.ownerPlayerUuid = player.getUUID();
            data.rarity = RoamingTrainerRarity.parse(AdventurerGuildConfig.floor(floor).rarity,
                    AdventurerGuildConfig.rarityForFloor(floor));
            data.targetLevel = RoamingTrainerManager.playerPartyHighestLevelForRarity(player, data.rarity);
            data.displayName = name;
            data.adventureSource = ultimate ? AdventurerGuildManager.SOURCE_BATTLE_TOWER_ULTIMATE : AdventurerGuildManager.SOURCE_BATTLE_TOWER;
            data.towerFloor = floor;
            if (!RoamingTrainerPartyBuilder.apply(npc, data) || npc.getParty() == null) {
                npc.discard();
                return null;
            }
            npc.setSkill(Math.max(0, Math.min(5, tier.aiSkill)));

            PluginTrainerBattleStarter.StartResult result = PluginTrainerBattleStarter.start(
                    player, npc, BattleContextManager.BattleType.ADVENTURE_TOWER,
                    data.adventureSource, BattleFormat.Companion.getGEN_9_SINGLES(), false, false);
            if (!result.started()) {
                npc.discard();
                return null;
            }
            return npc.getUUID();
        } catch (Throwable t) {
            if (npc != null) try { npc.discard(); } catch (Throwable ignored) {}
            t.printStackTrace();
            return null;
        }
    }
}
