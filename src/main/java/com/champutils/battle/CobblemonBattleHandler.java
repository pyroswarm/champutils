package com.champutils.battle;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;

import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.champutils.afk.PvPBattleStallManager;
import com.champutils.adventurer.AdventurerGuildManager;

import net.minecraft.server.level.ServerPlayer;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionSubLevelManager;
import com.champutils.profession.ProfessionType;

public class CobblemonBattleHandler {

    public static void register() {

        /*
         =========================
         TRACK BATTLE STATE
         =========================
         */
        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {

            BattleStartedEvent e =
                    (BattleStartedEvent) event;

            UUID firstPlayerUuid = null;
            UUID firstNpcUuid = null;

            for (
                    Object actor :
                    e.getBattle().getActors()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {

                    ServerPlayer player =
                            (ServerPlayer) p.getEntity();

                    if (firstPlayerUuid == null) {
                        firstPlayerUuid = player.getUUID();
                    }

                    BattleStateManager.setInBattle(
                            player,
                            true
                    );

                    BattleStateManager.setBattle(
                            player,
                            e.getBattle()
                    );

                    BattleProfileRecoveryManager.handleBattleStarted(
                            player,
                            e.getBattle()
                    );
                }

                if (actor instanceof NPCBattleActor n && firstNpcUuid == null) {
                    firstNpcUuid = n.getEntity().getUUID();
                }
            }

            PvPBattleStallManager.recordBattleStarted(e.getBattle());

            PluginTrainerBattleStarter.releaseStartLocks(firstPlayerUuid, firstNpcUuid);

            BattleContextManager.TrainerBattleContext trainerBattleContext =
                    BattleContextManager.attachTrainerBattleContext(
                            readBattleId(e.getBattle()),
                            firstPlayerUuid,
                            firstNpcUuid
                    );
            if (trainerBattleContext != null && trainerBattleContext.playerId() != null) {
                BattleContextManager.setContext(trainerBattleContext.playerId(), trainerBattleContext.type());
            }
        });



        /*
         =========================
         BATTLE END
         =========================
         */
        CobblemonEvents.BATTLE_VICTORY.subscribe(event -> {

            BattleVictoryEvent e =
                    (BattleVictoryEvent) event;

            BattleContextManager.TrainerBattleContext trainerBattleContext =
                    BattleContextManager.removeTrainerBattleContext(readBattleId(e.getBattle()));
            if (trainerBattleContext != null && trainerBattleContext.playerId() != null) {
                BattleContextManager.setContext(trainerBattleContext.playerId(), trainerBattleContext.type());
            }

            PvPBattleStallManager.recordBattleEnded(e.getBattle());

            finishPlayerProfileGuards(
                    e.getBattle()
                            .getActors(),
                    "battle-victory"
            );

            clearPlayersFromBattle(
                    e.getBattle()
                            .getActors()
            );

            ServerPlayer winner = null;
            ServerPlayer loser = null;
            UUID losingNpcUuid = null;
            UUID anyRoamingNpcUuid = null;
            UUID anyNpcUuid = null;

            /*
             Find player winner
             */
            for (
                    var actor :
                    e.getWinners()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {

                    winner =
                            (ServerPlayer) p.getEntity();

                    break;
                }
            }

            /*
             Find player loser (if one exists)
             */
            for (
                    var actor :
                    e.getLosers()
            ) {

                if (
                        actor instanceof PlayerBattleActor p
                ) {

                    loser =
                            (ServerPlayer) p.getEntity();
                }

                if (
                        actor instanceof NPCBattleActor n
                ) {
                    losingNpcUuid = n.getEntity().getUUID();
                    anyRoamingNpcUuid = losingNpcUuid;
                    anyNpcUuid = losingNpcUuid;
                }
            }

            for (
                    var actor :
                    e.getWinners()
            ) {
                if (
                        actor instanceof NPCBattleActor n
                ) {
                    anyRoamingNpcUuid = n.getEntity().getUUID();
                    anyNpcUuid = anyRoamingNpcUuid;
                }
            }

            if (anyRoamingNpcUuid != null) {
                com.champutils.roaming.RoamingTrainerManager.handleBattleEnded(anyRoamingNpcUuid);
            }
            PluginTrainerBattleStarter.releaseStartLocks(
                    trainerBattleContext == null ? null : trainerBattleContext.playerId(),
                    anyNpcUuid
            );

            if (anyNpcUuid != null) {
                for (Object actor : e.getBattle().getActors()) {
                    if (actor instanceof PlayerBattleActor playerActor) {
                        com.champutils.guild.GuildBossManager.recordBossBattleEnded((ServerPlayer) playerActor.getEntity(), anyNpcUuid);
                    }
                }
            }

            if (winner == null && loser != null) {
                AdventurerGuildManager.recordBattleLoss(
                        loser,
                        BattleContextManager.getContext(loser.getUUID())
                );
                BattleContextManager.clearContext(loser.getUUID());
            }

            /*
             IMPORTANT FIX:

             Wild battles:
             - winner exists
             - loser is wild pokemon

             NPC battles:
             - winner exists
             - loser may be NPC

             We still want profession XP.
             */
            if (winner != null) {

                com.champutils.guild.GuildBossManager.recordBossVictory(winner, losingNpcUuid);

                BattleListener.onBattleEnd(
                        winner,
                        loser,
                        losingNpcUuid
                );

                if (losingNpcUuid != null) {
                    com.champutils.roaming.RoamingTrainerManager.handleVictory(
                            winner,
                            losingNpcUuid
                    );
                }
            }
        });



        /*
         =========================
         SAFETY CLEANUP
         =========================
         */
        CobblemonEvents.BATTLE_FAINTED.subscribe(event -> {

            BattleFaintedEvent e =
                    (BattleFaintedEvent) event;

            try {

                recordDefeatedTypeQuestProgress(e);
                awardWildDefeatBattleXp(e);

            } catch (Exception ignored) {
            }
        });
    }


    private static void awardWildDefeatBattleXp(BattleFaintedEvent event) {
        try {
            int defeatedLevel = defeatedPokemonLevel(event);
            if (defeatedLevel <= 0) return;

            for (Object actor : event.getBattle().getActors()) {
                if (!(actor instanceof PlayerBattleActor p)) continue;
                ServerPlayer player = (ServerPlayer) p.getEntity();
                if (player == null) continue;

                BattleContextManager.BattleType type = BattleContextManager.getContext(player.getUUID());
                if (type != null && type != BattleContextManager.BattleType.UNKNOWN) continue;

                // Do not pay Battling XP if the fainted Pokémon belongs to the player. This keeps
                // the level-scaled XP limited to wild Pokémon defeats, not trainer/PvP/world-boss faints.
                if (actorOwnsKilledPokemon(actor, event.getKilled())) continue;

                int xp = Math.max(1, defeatedLevel);
                ProfessionManager.addXp(player, ProfessionType.BATTLING, xp);
                ProfessionSubLevelManager.addPokemonTypeXp(player, findPokemonTypes(event), xp);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean actorOwnsKilledPokemon(Object actor, Object killed) {
        if (actor == null || killed == null) return false;
        try {
            Object list = invokeNoArg(actor, "getPokemonList");
            return list instanceof java.util.Collection<?> collection && collection.contains(killed);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int defeatedPokemonLevel(BattleFaintedEvent event) {
        try {
            Object killed = event.getKilled();
            Object pokemon = invokeNoArg(killed, "getOriginalPokemon");
            if (pokemon == null) pokemon = invokeNoArg(killed, "getEffectedPokemon");
            if (pokemon == null) pokemon = invokeNoArg(killed, "getPokemon");
            Object level = invokeNoArg(pokemon, "getLevel");
            return level instanceof Number n ? n.intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void recordDefeatedTypeQuestProgress(BattleFaintedEvent event) {
        try {
            List<String> types = findPokemonTypes(event);
            if (types.isEmpty()) {
                return;
            }

            for (Object actor : event.getBattle().getActors()) {
                if (actor instanceof PlayerBattleActor p) {
                    ServerPlayer player = (ServerPlayer) p.getEntity();
                    for (String type : types) {
                        com.champutils.quest.QuestManager.recordDefeatedPokemonType(player, type);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static List<String> findPokemonTypes(BattleFaintedEvent event) {
        List<String> result = new ArrayList<>();
        try {
            Object killed = event.getKilled();
            if (killed == null) {
                return result;
            }

            Object pokemon = invokeNoArg(killed, "getOriginalPokemon");
            if (pokemon == null) pokemon = invokeNoArg(killed, "getEffectedPokemon");
            if (pokemon == null) pokemon = invokeNoArg(killed, "getPokemon");
            if (pokemon == null) pokemon = killed;

            Object form = invokeNoArg(pokemon, "getForm");
            Object types = form == null ? null : invokeNoArg(form, "getTypes");
            if (types == null) types = invokeNoArg(pokemon, "getTypes");

            if (types instanceof Iterable<?> iterable) {
                for (Object t : iterable) {
                    String name = typeName(t);
                    if (name != null && !name.isBlank() && !result.contains(name)) result.add(name);
                }
            } else {
                String text = String.valueOf(types);
                if (text != null && !"null".equals(text)) {
                    result.add(text.toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static Object invokeNoArg(Object target, String method) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String typeName(Object type) {
        if (type == null) return null;
        try {
            Object name = invokeNoArg(type, "getName");
            if (name != null) return String.valueOf(name).toLowerCase(java.util.Locale.ROOT);
        } catch (Exception ignored) {
        }
        return String.valueOf(type).toLowerCase(java.util.Locale.ROOT);
    }


    private static String readBattleId(Object battle) {
        if (battle == null) return "unknown";
        Object id = invokeNoArg(battle, "getBattleId");
        if (id == null) id = invokeNoArg(battle, "getBattleID");
        if (id == null) id = invokeNoArg(battle, "getUuid");
        if (id == null) id = invokeNoArg(battle, "getUUID");
        return id == null ? "unknown" : String.valueOf(id);
    }


    private static void finishPlayerProfileGuards(
            Iterable<?> actors,
            String reason
    ) {
        for (Object actor : actors) {
            if (actor instanceof PlayerBattleActor p) {
                ServerPlayer player = (ServerPlayer) p.getEntity();
                BattleProfileRecoveryManager.handleBattleEnded(player, reason);
            }
        }
    }

    private static void clearPlayersFromBattle(
            Iterable<?> actors
    ) {

        for (
                Object actor :
                actors
        ) {

            if (
                    actor instanceof PlayerBattleActor p
            ) {

                ServerPlayer player =
                        (ServerPlayer) p.getEntity();

                BattleStateManager.setInBattle(
                        player,
                        false
                );

                BattleStateManager.clearBattle(
                        player
                );

                BattleItemLockManager.unlock(
                        player
                );
            }
        }
    }
}