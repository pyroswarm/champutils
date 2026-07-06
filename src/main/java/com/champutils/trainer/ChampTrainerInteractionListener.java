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
import com.champutils.battle.PluginTrainerBattleStarter;
import com.champutils.battle.AITestGymLeaderBuilder;
import com.champutils.validation.TeamValidator;

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
                    if (!GuildBossManager.prepareGuildBossBattle(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    try {
                        PluginTrainerBattleStarter.StartResult result = PluginTrainerBattleStarter.startOrMessage(
                                serverPlayer,
                                npc,
                                BattleContextManager.BattleType.WORLD_BOSS,
                                "guild_boss",
                                null,
                                Component.literal("§cThat guild boss battle could not start. Try again in a few seconds.")
                        );
                        if (!result.started()) {
                            GuildBossManager.releaseBossBattleStart(serverPlayer, npc.getUUID());
                        }
                    } catch (Exception battleStartError) {
                        GuildBossManager.releaseBossBattleStart(serverPlayer, npc.getUUID());
                        throw battleStartError;
                    }
                    return InteractionResult.SUCCESS;
                }

                if (worldBoss) {
                    if (!GuildBossManager.prepareWorldBossBattle(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    try {
                        PluginTrainerBattleStarter.StartResult result = PluginTrainerBattleStarter.startOrMessage(
                                serverPlayer,
                                npc,
                                BattleContextManager.BattleType.WORLD_BOSS,
                                "world_boss",
                                null,
                                Component.literal("§cThat world boss battle could not start. Try again in a few seconds.")
                        );
                        if (!result.started()) {
                            GuildBossManager.releaseBossBattleStart(serverPlayer, npc.getUUID());
                        }
                    } catch (Exception battleStartError) {
                        GuildBossManager.releaseBossBattleStart(serverPlayer, npc.getUUID());
                        throw battleStartError;
                    }
                    return InteractionResult.SUCCESS;
                }

                if (roaming) {
                    if (!RoamingTrainerManager.tryStartChallenge(serverPlayer, npc)) {
                        return InteractionResult.SUCCESS;
                    }
                    try {
                        PluginTrainerBattleStarter.StartResult result = PluginTrainerBattleStarter.startOrMessage(
                                serverPlayer,
                                npc,
                                RoamingTrainerManager.battleTypeFor(npc.getUUID()),
                                "roaming_trainer",
                                null,
                                true,
                                true,
                                Component.literal("§cThat Adventurer could not start a battle. Try again in a few seconds.")
                        );
                        if (!result.started()) {
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
                    PluginTrainerBattleStarter.startOrMessage(
                            serverPlayer,
                            npc,
                            BattleContextManager.BattleType.WORLD_BOSS,
                            "world_event",
                            null,
                            Component.literal("§cThat event battle could not start. Try again in a few seconds.")
                    );
                    return InteractionResult.SUCCESS;
                }

                if (aiTestGym) {
                    AITestGymLeaderBuilder.applyTeam(npc);
                    PluginTrainerBattleStarter.startOrMessage(
                            serverPlayer,
                            npc,
                            BattleContextManager.BattleType.GYM,
                            "ai_test_gym",
                            PvPBattleFormatRules.getCobblemonFormat("ranked"),
                            Component.literal("§cThat test gym battle could not start. Try again in a few seconds.")
                    );
                    return InteractionResult.SUCCESS;
                }

                String rankedViolation = TeamValidator.validate(serverPlayer, "ranked");
                if (rankedViolation != null) {
                    serverPlayer.sendSystemMessage(Component.literal("§cGym teams must follow ranked rules: §f" + rankedViolation));
                    return InteractionResult.SUCCESS;
                }

                if (!GymNpcPartyBuilder.applyGymTeam(npc, badge)) {
                    GymNpcPartyBuilder.clearStoredGymTeam(npc);
                    serverPlayer.sendSystemMessage(Component.literal("§cThis gym could not build a battle team. Check gyms.json."));
                    return InteractionResult.SUCCESS;
                }

                PluginTrainerBattleStarter.StartResult gymBattleResult = PluginTrainerBattleStarter.startOrMessage(
                        serverPlayer,
                        npc,
                        BattleContextManager.BattleType.GYM,
                        "gym",
                        PvPBattleFormatRules.getCobblemonFormat("ranked"),
                        Component.literal("§cThat gym battle could not start. Try again in a few seconds.")
                );

                // BattleBuilder has already copied the NPCPartyStore into the NPCBattleActor.
                // Clear the entity's saved party immediately so the bound NPC never persists a static team.
                GymNpcPartyBuilder.clearStoredGymTeam(npc);

                if (!gymBattleResult.started()) {
                    BattleContextManager.clearPendingTrainerBattleContext(serverPlayer.getUUID(), npc.getUUID());
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
