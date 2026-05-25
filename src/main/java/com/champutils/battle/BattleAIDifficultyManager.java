package com.champutils.battle;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleStartedEvent;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.battles.ai.StrongBattleAI;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Forces ChampUtils-controlled PvE battles to use Cobblemon's strongest built-in battle AI.
 *
 * Cobblemon already ships StrongBattleAI(skill), where skill is clamped from 0-5.
 * Skill 5 means the AI always passes its skill checks, so this is the hardest
 * built-in Cobblemon AI currently available without replacing the battle engine.
 *
 * This manager intentionally does NOT touch PlayerBattleActor.
 */
public final class BattleAIDifficultyManager {

    private static final int MAX_COBBLEMON_AI_SKILL = 5;
    private static final BattleAI HARD_AI = new StrongBattleAI(MAX_COBBLEMON_AI_SKILL);

    private BattleAIDifficultyManager() {}

    public static void register() {
        /*
         * PRE catches NPC/wild actors before the battle fully launches.
         * POST is kept as a safety net for any actor Cobblemon finalizes during start.
         */
        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(event -> {
            if (event instanceof BattleStartedEvent.Pre pre) {
                forceHardAI(pre.getBattle());
            }
        });

        CobblemonEvents.BATTLE_STARTED_POST.subscribe(event -> {
            if (event instanceof BattleStartedEvent.Post post) {
                forceHardAI(post.getBattle());
            }
        });
    }

    private static void forceHardAI(Object battle) {
        if (battle == null) return;

        try {
            Method getActors = battle.getClass().getMethod("getActors");
            Object actorsObject = getActors.invoke(battle);

            if (!(actorsObject instanceof Iterable<?> actors)) return;

            for (Object actor : actors) {
                forceHardAI(actor);
            }
        } catch (Exception ignored) {
            /*
             * Do not ever break a battle start because AI reflection failed.
             * Worst case: Cobblemon keeps its original AI for that actor.
             */
        }
    }

    public static void prepareNpc(NPCEntity npc) {
        if (npc == null) return;

        try {
            npc.setSkill(MAX_COBBLEMON_AI_SKILL);
        } catch (Exception ignored) {
        }
    }

    private static void forceHardAI(Object actor) {
        if (actor == null) return;
        if (actor instanceof PlayerBattleActor) return;

        if (actor instanceof NPCBattleActor npcActor) {
            try {
                prepareNpc(npcActor.getEntity());
            } catch (Exception ignored) {
            }
        }

        /*
         * Cobblemon AI actors store a BattleAI instance internally.
         * This reflection path covers NPC actors, wild actors, and future AI-backed
         * Cobblemon actor types without hard-linking to unstable implementation classes.
         */
        Class<?> current = actor.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!BattleAI.class.isAssignableFrom(field.getType())) continue;

                try {
                    field.setAccessible(true);
                    field.set(actor, new StrongBattleAI(MAX_COBBLEMON_AI_SKILL));
                } catch (Exception ignored) {
                }
            }

            current = current.getSuperclass();
        }
    }
}
