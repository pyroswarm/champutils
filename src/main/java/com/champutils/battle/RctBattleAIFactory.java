package com.champutils.battle;

import com.cobblemon.mod.common.api.battles.model.ai.BattleAI;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection-only bridge for RCT/Radical Cobblemon Trainers so ChampUtils never
 * has a hard runtime dependency on a single RCT internals class name.
 */
final class RctBattleAIFactory {
    private static final String[] CANDIDATE_CLASSES = new String[]{
            "com.gitlab.srcmc.rctmod.api.algorithm.BattleAIAlgorithm",
            "com.gitlab.srcmc.rctmod.api.algorithm.RCTBattleAI",
            "com.gitlab.srcmc.rctmod.api.battle.RCTBattleAI",
            "com.gitlab.srcmc.rctmod.battle.RCTBattleAI",
            "com.gitlab.srcmc.rctmod.world.battle.RCTBattleAI"
    };

    private RctBattleAIFactory() {}

    static Optional<BattleAI> create(int skill) {
        debugRctPresence();
        for (String className : CANDIDATE_CLASSES) {
            Optional<BattleAI> ai = instantiate(className, skill);
            if (ai.isPresent()) {
                BattleAIDifficultyManager.debug("RCT AI bridge loaded class=" + className);
                return ai;
            }
        }
        BattleAIDifficultyManager.debug("RCT AI bridge did not find a public BattleAI implementation; using ChampUtils smart AI fallback.");
        return Optional.empty();
    }

    private static Optional<BattleAI> instantiate(String className, int skill) {
        try {
            Class<?> clazz = Class.forName(className);
            if (!BattleAI.class.isAssignableFrom(clazz)) {
                BattleAIDifficultyManager.debug("RCT AI bridge candidate exists but is not Cobblemon BattleAI class=" + className);
                return Optional.empty();
            }
            for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                if (types.length == 1 && (types[0] == int.class || types[0] == Integer.class)) {
                    constructor.setAccessible(true);
                    return Optional.of((BattleAI) constructor.newInstance(skill));
                }
                if (types.length == 0) {
                    constructor.setAccessible(true);
                    Object instance = constructor.newInstance();
                    trySetSkill(instance, skill);
                    return Optional.of((BattleAI) instance);
                }
            }
        } catch (Throwable t) {
            BattleAIDifficultyManager.debug("RCT AI bridge candidate unavailable class=" + className + " reason=" + t.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    private static void debugRctPresence() {
        boolean rctApiPresent = classExists("com.gitlab.srcmc.rctmod.api.algorithm.Algorithm")
                || classExists("com.gitlab.srcmc.rctmod.api.data.TrainerBattle")
                || classExists("com.gitlab.srcmc.rctmod.world.entities.goals.PokemonBattleGoal");
        BattleAIDifficultyManager.debug("RCT AI bridge probe: rctApiPresent=" + rctApiPresent
                + "; looking for a public Cobblemon BattleAI implementation before using ChampUtils scoring fallback.");
    }

    private static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void trySetSkill(Object instance, int skill) {
        if (instance == null) return;
        for (String methodName : new String[]{"setSkill", "skill"}) {
            try {
                Method method = instance.getClass().getMethod(methodName, int.class);
                method.invoke(instance, skill);
                return;
            } catch (Throwable ignored) {
            }
        }
    }
}
