package com.champutils.battle;

import com.champutils.rank.RankedTokenConfig;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks enough server-authoritative battle activity to reject immediate queue
 * forfeits as reward-eligible matches. This deliberately uses only battle
 * lifecycle/faint events, so clients cannot spoof the result.
 */
public final class PvPMatchIntegrityManager {
    private static final Map<String, MatchState> MATCHES = new ConcurrentHashMap<>();
    private static final Map<String, Long> CONSOLATION_COOLDOWNS = new ConcurrentHashMap<>();

    private PvPMatchIntegrityManager() {}

    public record Result(boolean trackedQueuedPvp, boolean rewardEligible, boolean immediateForfeit,
                         long elapsedSeconds, int faintCount) {
        public static Result notTracked() {
            return new Result(false, true, false, 0L, 0);
        }
    }

    private static final class MatchState {
        final long startedAtMillis = System.currentTimeMillis();
        final AtomicInteger faintCount = new AtomicInteger();
    }

    public static void recordBattleStarted(Object battle) {
        if (!isQueuedPvpBattle(battle)) return;
        MATCHES.put(key(battle), new MatchState());
    }

    public static void recordPokemonFainted(Object battle) {
        MatchState state = MATCHES.get(key(battle));
        if (state != null) state.faintCount.incrementAndGet();
    }

    public static Result finish(Object battle) {
        MatchState state = MATCHES.remove(key(battle));
        if (state == null) return Result.notTracked();

        long elapsedSeconds = Math.max(0L, (System.currentTimeMillis() - state.startedAtMillis) / 1000L);
        int faintCount = Math.max(0, state.faintCount.get());
        int threshold = Math.max(15, RankedTokenConfig.CONFIG.immediateForfeitSeconds);
        boolean immediateForfeit = faintCount == 0 && elapsedSeconds < threshold;
        return new Result(true, !immediateForfeit, immediateForfeit, elapsedSeconds, faintCount);
    }


    public static boolean claimConsolation(UUID winner, UUID loser) {
        if (winner == null || loser == null) return false;
        long now = System.currentTimeMillis();
        long cooldownMillis = Math.max(300_000L,
                Math.max(0, RankedTokenConfig.CONFIG.sameOpponentCooldownHours) * 3_600_000L);
        String pair = winner + ":" + loser;
        Long previous = CONSOLATION_COOLDOWNS.putIfAbsent(pair, now);
        if (previous == null) return true;
        if (now - previous < cooldownMillis) return false;
        return CONSOLATION_COOLDOWNS.replace(pair, previous, now);
    }

    public static void discard(Object battle) {
        MATCHES.remove(key(battle));
    }

    private static boolean isQueuedPvpBattle(Object battle) {
        int playerCount = 0;
        boolean queuedContext = false;
        Object actors = invokeNoArg(battle, "getActors");
        if (!(actors instanceof Iterable<?> iterable)) return false;

        for (Object actor : iterable) {
            if (!(actor instanceof PlayerBattleActor playerActor)) continue;
            playerCount++;
            ServerPlayer player = (ServerPlayer) playerActor.getEntity();
            BattleContextManager.BattleType type = BattleContextManager.getContext(player.getUUID());
            if (type == BattleContextManager.BattleType.RANKED || type == BattleContextManager.BattleType.CASUAL) {
                queuedContext = true;
            }
        }
        return playerCount == 2 && queuedContext;
    }

    private static String key(Object battle) {
        if (battle == null) return "null";
        Object id = invokeNoArg(battle, "getBattleId");
        if (id == null) id = invokeNoArg(battle, "getBattleID");
        if (id == null) id = invokeNoArg(battle, "getUuid");
        if (id == null) id = invokeNoArg(battle, "getUUID");
        return id == null ? "identity:" + System.identityHashCode(battle) : String.valueOf(id);
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }
}
