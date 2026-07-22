package com.champutils.battle;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.battles.BattleRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BattleStateManager {

    private static final Map<UUID, Boolean> IN_BATTLE =
            new ConcurrentHashMap<>();

    private static final Map<UUID, Object> ACTIVE_BATTLES =
            new ConcurrentHashMap<>();

    private static final Map<UUID, Long> BATTLE_STARTED_AT_TICK =
            new ConcurrentHashMap<>();

    public static void setInBattle(
            ServerPlayer player,
            boolean inBattle
    ) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();

        if (inBattle) {
            IN_BATTLE.put(playerId, true);
            BATTLE_STARTED_AT_TICK.putIfAbsent(
                    playerId,
                    currentTick(player)
            );
        } else {
            clearAll(player);
        }
    }

    public static boolean isInBattle(
            ServerPlayer player
    ) {
        return player != null && IN_BATTLE.containsKey(player.getUUID());
    }

    public static void setBattle(
            ServerPlayer player,
            Object battle
    ) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();

        if (battle == null) {
            ACTIVE_BATTLES.remove(playerId);
            return;
        }

        ACTIVE_BATTLES.put(playerId, battle);
        IN_BATTLE.put(playerId, true);
        BATTLE_STARTED_AT_TICK.putIfAbsent(
                playerId,
                currentTick(player)
        );
    }

    public static Object getBattle(
            ServerPlayer player
    ) {
        if (player == null) {
            return null;
        }

        return ACTIVE_BATTLES.get(player.getUUID());
    }

    public static long getBattleAgeTicks(
            ServerPlayer player
    ) {
        if (player == null) {
            return 0L;
        }

        Long startedAt = BATTLE_STARTED_AT_TICK.get(player.getUUID());
        if (startedAt == null) {
            return 0L;
        }

        return Math.max(
                0L,
                currentTick(player) - startedAt
        );
    }

    public static void clearBattle(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        ACTIVE_BATTLES.remove(player.getUUID());
    }

    public static void clearAll(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }

        UUID playerId = player.getUUID();
        IN_BATTLE.remove(playerId);
        ACTIVE_BATTLES.remove(playerId);
        BATTLE_STARTED_AT_TICK.remove(playerId);
        BattleItemLockManager.unlock(player);
    }

    public static boolean hasTrackedState(
            ServerPlayer player
    ) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        return IN_BATTLE.containsKey(playerId)
                || ACTIVE_BATTLES.containsKey(playerId)
                || BATTLE_STARTED_AT_TICK.containsKey(playerId);
    }

    public static boolean looksLikeActiveBattle(
            ServerPlayer player
    ) {
        if (player == null) return false;

        PokemonBattle registered;
        try { registered = BattleRegistry.getBattleByParticipatingPlayer(player); }
        catch (Throwable ignored) { registered = null; }
        if (registered == null || registered.getEnded() || registered.getActor(player) == null) return false;

        Object tracked = getBattle(player);
        if (tracked instanceof PokemonBattle trackedBattle) {
            try {
                if (!trackedBattle.getBattleId().equals(registered.getBattleId())) return false;
            } catch (Throwable ignored) {
                if (trackedBattle != registered) return false;
            }
        } else if (tracked != null) {
            Boolean ended = readBoolean(tracked, "isEnded");
            if (ended == null) ended = readBoolean(tracked, "getEnded");
            if (Boolean.TRUE.equals(ended)) return false;
        }

        return true;
    }

    private static Boolean readBoolean(
            Object target,
            String methodName
    ) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            if (value instanceof Boolean bool) {
                return bool;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static long currentTick(
            ServerPlayer player
    ) {
        try {
            return player.getServer().getTickCount();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
