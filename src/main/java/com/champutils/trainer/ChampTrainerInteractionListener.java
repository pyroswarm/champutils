package com.champutils.trainer;

import com.champutils.badge.BadgeType;
import com.champutils.gym.GymNpcPartyBuilder;
import com.champutils.guild.GuildBossManager;
import com.champutils.gym.GymRegistry;
import com.champutils.worldevent.WorldEventManager;
import com.champutils.roaming.RoamingTrainerManager;
import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattleAIDifficultyManager;
import com.champutils.battle.PvPBattleFormatRules;
import com.champutils.battle.PvPBattleStarter;
import com.champutils.battle.AITestGymLeaderBuilder;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChampTrainerInteractionListener {

    private ChampTrainerInteractionListener() {}

    private static final Map<UUID, Long> LAST_TRAINER_CLICK = new ConcurrentHashMap<>();
    private static final long CLICK_DEBOUNCE_MS = 1000L;

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!(entity instanceof NPCEntity npc)) return InteractionResult.PASS;

            BattleAIDifficultyManager.prepareNpc(npc);

            try {
                WorldEventManager.ActiveEvent active = WorldEventManager.getByNpc(npc.getUUID());
                BadgeType badge = GymRegistry.getBadgeForNpc(npc.getUUID());

                boolean roaming = RoamingTrainerManager.isRoamingTrainer(npc.getUUID());
                boolean guildBoss = GuildBossManager.getActiveGuildBossByNpc(npc.getUUID()) != null;
                boolean worldBoss = GuildBossManager.isActiveWorldBossNpc(npc.getUUID());
                boolean aiTestGym = npc.getTags().contains(AITestGymLeaderBuilder.TAG);

                if (active == null && badge == null && !roaming && !guildBoss && !worldBoss && !aiTestGym) {
                    return InteractionResult.PASS;
                }

                // Native trainer NPCs are handled here. Consume off-hand/duplicate callbacks
                // so Cobblemon/Fabric does not start or validate the same battle twice.
                if (hand != InteractionHand.MAIN_HAND) {
                    return InteractionResult.SUCCESS;
                }

                UUID key = serverPlayer.getUUID();
                long now = System.currentTimeMillis();
                Long previous = LAST_TRAINER_CLICK.get(key);
                if (previous != null && now - previous < CLICK_DEBOUNCE_MS) {
                    return InteractionResult.SUCCESS;
                }
                LAST_TRAINER_CLICK.put(key, now);

                if (guildBoss) {
                    BattleContextManager.setContext(serverPlayer.getUUID(), BattleContextManager.BattleType.WORLD_BOSS);
                    if (!GuildBossManager.prepareGuildBossBattle(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    BattleBuilder.INSTANCE.pvn(serverPlayer, npc);
                    return InteractionResult.SUCCESS;
                }

                if (worldBoss) {
                    BattleContextManager.setContext(serverPlayer.getUUID(), BattleContextManager.BattleType.WORLD_BOSS);
                    if (!GuildBossManager.prepareWorldBossBattle(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    BattleBuilder.INSTANCE.pvn(serverPlayer, npc);
                    return InteractionResult.SUCCESS;
                }

                if (roaming) {
                    if (!RoamingTrainerManager.tryStartChallenge(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    try {
                        BattleContextManager.setContext(serverPlayer.getUUID(), BattleContextManager.BattleType.NPC);
                        Object result = BattleBuilder.INSTANCE.pvn(serverPlayer, npc);
                        if (result == null) {
                            serverPlayer.sendSystemMessage(Component.literal("§cThat roaming trainer could not start a battle. Try again in a few seconds."));
                            RoamingTrainerManager.releaseChallenge(npc.getUUID(), serverPlayer.getUUID());
                        }
                    } catch (Exception battleStartError) {
                        RoamingTrainerManager.releaseChallenge(npc.getUUID(), serverPlayer.getUUID());
                        throw battleStartError;
                    }
                    return InteractionResult.SUCCESS;
                }

                if (active != null) {
                    if (!WorldEventManager.prepareBattle(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    BattleBuilder.INSTANCE.pvn(serverPlayer, npc);
                    return InteractionResult.SUCCESS;
                }

                if (aiTestGym) {
                    AITestGymLeaderBuilder.applyTeam(npc);
                    BattleContextManager.setContext(serverPlayer.getUUID(), BattleContextManager.BattleType.GYM);
                    PvPBattleStarter.startPvn(serverPlayer, npc, PvPBattleFormatRules.getCobblemonFormat("ranked"));
                    return InteractionResult.SUCCESS;
                }

                if (!GymNpcPartyBuilder.applyGymTeam(npc, badge)) {
                    GymNpcPartyBuilder.clearStoredGymTeam(npc);
                    serverPlayer.sendSystemMessage(Component.literal("§cThis gym could not build a battle team. Check gyms.json."));
                    return InteractionResult.SUCCESS;
                }

                BattleContextManager.setContext(serverPlayer.getUUID(), BattleContextManager.BattleType.GYM);
                Object gymBattleResult = PvPBattleStarter.startPvn(serverPlayer, npc, PvPBattleFormatRules.getCobblemonFormat("ranked"));

                // BattleBuilder has already copied the NPCPartyStore into the NPCBattleActor.
                // Clear the entity's saved party immediately so the bound NPC never persists a static team.
                GymNpcPartyBuilder.clearStoredGymTeam(npc);

                if (gymBattleResult == null) {
                    serverPlayer.sendSystemMessage(Component.literal("§cThat gym battle could not start. Try again in a few seconds."));
                }
                return InteractionResult.SUCCESS;
            } catch (Exception e) {
                e.printStackTrace();
                serverPlayer.sendSystemMessage(Component.literal("§cCould not start trainer battle. Check server console."));
                return InteractionResult.FAIL;
            }
        });
    }
}
