package com.champutils.battle;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Prevents normal Minecraft damage from killing/interrupting players while a Cobblemon battle is active. */
public final class BattleDamageProtectionListener {
    private BattleDamageProtectionListener() {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && isProtectedBattlePlayer(player)) {
                player.clearFire();
                player.setRemainingFireTicks(0);
                return false;
            }
            return true;
        });
    }

    /**
     * BattleStateManager is the fast path, but this also asks Cobblemon directly.
     * That closes the gap where a battle starts/continues but our local marker was
     * missed or cleared early, which let creeper/explosion damage kill battlers.
     */
    private static boolean isProtectedBattlePlayer(ServerPlayer player) {
        if (player == null) return false;
        if (BattleStateManager.isInBattle(player) || BattleStateManager.looksLikeActiveBattle(player)) return true;
        return cobblemonBattleState(player) != null;
    }

    private static Object cobblemonBattleState(ServerPlayer player) {
        Object state = invokeStatic("com.cobblemon.mod.common.util.PlayerExtensionsKt", "getBattleState", player);
        if (state == null) state = invokeStatic("com.cobblemon.mod.common.util.PlayerExtensionsKt", "battleState", player);
        if (state == null) state = firstValue(player, "battleState", "getBattleState");
        if (state == null) return null;
        Object battle = firstValue(state, "first", "getFirst");
        return battle == null ? state : battle;
    }

    private static Object invokeStatic(String className, String methodName, Object arg) {
        try {
            Class<?> clazz = Class.forName(className);
            for (Method method : clazz.getMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) continue;
                method.setAccessible(true);
                return method.invoke(null, arg);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get")) {
                    Method method = source.getClass().getMethod(name);
                    if (method.getParameterCount() == 0) {
                        method.setAccessible(true);
                        Object value = method.invoke(source);
                        if (value != null) return value;
                    }
                } else {
                    Field field = findField(source.getClass(), name);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(source);
                        if (value != null) return value;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); } catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }
}
