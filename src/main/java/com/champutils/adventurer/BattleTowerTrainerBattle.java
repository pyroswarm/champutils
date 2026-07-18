package com.champutils.adventurer;

import com.champutils.battle.BattleContextManager;
import com.champutils.battle.PluginTrainerBattleStarter;
import com.champutils.roaming.RoamingTrainerManager;
import com.champutils.roaming.RoamingTrainerRarity;
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
        UUID npcUuid = null;
        try {
            String source = ultimate
                    ? AdventurerGuildManager.SOURCE_BATTLE_TOWER_ULTIMATE
                    : AdventurerGuildManager.SOURCE_BATTLE_TOWER;

            // Remove only stale tower trainers owned by this player through the roaming-trainer
            // registry. Directly discarding an old NPC while leaving its registry entry behind makes
            // the lifecycle cleaner think the trainer vanished mid-battle and cancel the brand-new fight.
            RoamingTrainerManager.removeOwnedAdventureTrainers(player.getServer(), player.getUUID(), source);

            BattleTowerPoolConfig.Tier tier = ultimate
                    ? BattleTowerPoolConfig.tier(10)
                    : BattleTowerPoolConfig.tierForFloor(floor);
            RoamingTrainerRarity rarity = RoamingTrainerRarity.parse(
                    AdventurerGuildConfig.floor(floor).rarity,
                    AdventurerGuildConfig.rarityForFloor(floor)
            );

            // Use the exact roaming-trainer spawn pipeline so tower trainers receive a real trainer
            // identity, configured skin, protections, registration, and the tower-specific party pool.
            npcUuid = RoamingTrainerManager.spawnForAdventureGuildAt(
                    player, level, npcPos, npcYaw, rarity, source, floor
            );
            if (npcUuid == null) return null;

            NPCEntity npc = RoamingTrainerManager.findTrainerNpc(player.getServer(), npcUuid);
            if (npc == null || npc.getParty() == null) {
                RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid);
                return null;
            }

            npc.addTag("champutils_battle_tower");
            npc.addTag("champutils_battle_tower_owner_" + player.getUUID());
            npc.setSkill(Math.max(0, Math.min(5, tier.aiSkill)));

            // Run the same challenge preparation used by ordinary roaming trainers, then launch the
            // battle automatically. Tower fights intentionally do not clone or heal the player's party,
            // preserving damage/PP across the ten-floor segment.
            if (!RoamingTrainerManager.tryStartChallenge(player, npc)) {
                RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid);
                return null;
            }

            PluginTrainerBattleStarter.StartResult result = PluginTrainerBattleStarter.start(
                    player,
                    npc,
                    BattleContextManager.BattleType.ADVENTURE_TOWER,
                    source,
                    BattleFormat.Companion.getGEN_9_SINGLES(),
                    false,
                    false
            );
            if (!result.started()) {
                RoamingTrainerManager.releaseChallenge(npcUuid, player.getUUID());
                RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid);
                return null;
            }
            return npcUuid;
        } catch (Throwable t) {
            if (npcUuid != null) {
                try { RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid); }
                catch (Throwable ignored) {}
            }
            t.printStackTrace();
            return null;
        }
    }
}
