package com.champutils.battle;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single ChampUtils entry point for plugin-owned trainer battles.
 *
 * Every custom trainer-style fight must register its context before Cobblemon builds
 * the battle. Reward code then reads the captured context from BattleVictoryEvent
 * instead of relying on Cobblemon's base NPCBattleActor reward placeholder.
 */
public final class PluginTrainerBattleStarter {
    private PluginTrainerBattleStarter() {}

    private static final long START_LOCK_TTL_MS = 10_000L;
    private static final Map<UUID, Long> PLAYER_START_LOCKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NPC_START_LOCKS = new ConcurrentHashMap<>();

    public record StartResult(boolean started, Object rawResult) {}

    public static StartResult start(
            ServerPlayer player,
            NPCEntity npc,
            BattleContextManager.BattleType type,
            String source,
            BattleFormat battleFormat
    ) throws Exception {
        return start(player, npc, type, source, battleFormat, false, false);
    }

    public static StartResult start(
            ServerPlayer player,
            NPCEntity npc,
            BattleContextManager.BattleType type,
            String source,
            BattleFormat battleFormat,
            boolean cloneParties,
            boolean healFirst
    ) throws Exception {
        if (player == null || npc == null) {
            return new StartResult(false, null);
        }

        if (!acquireStartLocks(player, npc)) {
            return new StartResult(false, null);
        }

        BattleContextManager.TrainerBattleContext context =
                BattleContextManager.registerTrainerBattleContext(
                        player.getUUID(),
                        npc.getUUID(),
                        type == null ? BattleContextManager.BattleType.NPC : type,
                        normalizeSource(source)
                );

        Object result;
        try {
            result = invokePvn(player, npc, battleFormat, cloneParties, healFirst);
        } catch (Throwable throwable) {
            BattleContextManager.clearPendingTrainerBattleContext(player.getUUID(), npc.getUUID());
            BattleContextManager.clearContext(player.getUUID());
            releaseStartLocks(player.getUUID(), npc.getUUID());
            if (throwable instanceof Exception exception) throw exception;
            throw new RuntimeException(throwable);
        }

        // Some Cobblemon start methods return a failed BattleStartResult instead of null/throwing.
        // Keep the context until BATTLE_STARTED attaches it, but remove it immediately for obvious failures.
        if (result == null) {
            BattleContextManager.clearPendingTrainerBattleContext(player.getUUID(), npc.getUUID());
            BattleContextManager.clearContext(player.getUUID());
            releaseStartLocks(player.getUUID(), npc.getUUID());
            return new StartResult(false, null);
        }

        return new StartResult(true, result);
    }

    public static StartResult startOrMessage(
            ServerPlayer player,
            NPCEntity npc,
            BattleContextManager.BattleType type,
            String source,
            BattleFormat battleFormat,
            Component failureMessage
    ) throws Exception {
        return startOrMessage(player, npc, type, source, battleFormat, false, false, failureMessage);
    }

    public static StartResult startOrMessage(
            ServerPlayer player,
            NPCEntity npc,
            BattleContextManager.BattleType type,
            String source,
            BattleFormat battleFormat,
            boolean cloneParties,
            boolean healFirst,
            Component failureMessage
    ) throws Exception {
        StartResult result = start(player, npc, type, source, battleFormat, cloneParties, healFirst);
        if (!result.started() && player != null && failureMessage != null) {
            player.sendSystemMessage(failureMessage);
        }
        return result;
    }

    public static void releaseStartLocks(UUID playerUuid, UUID npcUuid) {
        if (playerUuid != null) PLAYER_START_LOCKS.remove(playerUuid);
        if (npcUuid != null) NPC_START_LOCKS.remove(npcUuid);
    }

    private static boolean acquireStartLocks(ServerPlayer player, NPCEntity npc) {
        UUID playerUuid = player.getUUID();
        UUID npcUuid = npc.getUUID();
        long now = System.currentTimeMillis();
        cleanupExpiredLocks(now);

        Long playerLock = PLAYER_START_LOCKS.putIfAbsent(playerUuid, now);
        if (playerLock != null && now - playerLock < START_LOCK_TTL_MS) {
            player.sendSystemMessage(Component.literal("§cYou are already starting a trainer battle. Try again in a few seconds."));
            return false;
        }
        PLAYER_START_LOCKS.put(playerUuid, now);

        Long npcLock = NPC_START_LOCKS.putIfAbsent(npcUuid, now);
        if (npcLock != null && now - npcLock < START_LOCK_TTL_MS) {
            PLAYER_START_LOCKS.remove(playerUuid);
            player.sendSystemMessage(Component.literal("§cThat trainer is already starting a battle. Try again in a few seconds."));
            return false;
        }
        NPC_START_LOCKS.put(npcUuid, now);
        return true;
    }

    private static void cleanupExpiredLocks(long now) {
        PLAYER_START_LOCKS.entrySet().removeIf(entry -> now - entry.getValue() > START_LOCK_TTL_MS);
        NPC_START_LOCKS.entrySet().removeIf(entry -> now - entry.getValue() > START_LOCK_TTL_MS);
    }

    private static Object invokePvn(ServerPlayer player, NPCEntity npc, BattleFormat battleFormat, boolean cloneParties, boolean healFirst) throws Exception {
        Object builder = BattleBuilder.INSTANCE;

        Method withCloneHeal = findPvnMethod(5);
        if (withCloneHeal != null) {
            BattleFormat format = copyFormat(battleFormat == null ? getDefaultGen9Singles() : battleFormat);
            return withCloneHeal.invoke(builder, player, npc, format, cloneParties, healFirst);
        }

        Method withFormat = findPvnMethod(3);
        if (withFormat != null && battleFormat != null) {
            return withFormat.invoke(builder, player, npc, copyFormat(battleFormat));
        }

        Method withoutFormat = findPvnMethod(2);
        if (withoutFormat != null) {
            return withoutFormat.invoke(builder, player, npc);
        }

        throw new NoSuchMethodException("Could not find Cobblemon BattleBuilder.pvn overload.");
    }

    private static BattleFormat copyFormat(BattleFormat source) {
        if (source == null) return null;
        // BattleFormat contains mutable ruleSet/adjustLevel fields and Cobblemon exposes shared singleton
        // defaults. Never hand those shared objects to a battle, or one battle can leak format state into
        // later fights (including move-resolution rules).
        return new BattleFormat(
                source.getMod(),
                source.getBattleType(),
                new java.util.LinkedHashSet<>(source.getRuleSet()),
                source.getGen(),
                source.getAdjustLevel()
        );
    }

    private static BattleFormat getDefaultGen9Singles() throws Exception {
        try {
            Field field = BattleFormat.class.getField("GEN_9_SINGLES");
            Object result = field.get(null);
            if (result instanceof BattleFormat battleFormat) return battleFormat;
        } catch (Throwable ignored) {
            // Try Kotlin companion getter below.
        }

        Field companionField = BattleFormat.class.getField("Companion");
        Object companion = companionField.get(null);
        Method method = companion.getClass().getMethod("getGEN_9_SINGLES");
        Object result = method.invoke(companion);
        if (result instanceof BattleFormat battleFormat) return battleFormat;
        throw new NoSuchMethodException("Could not resolve Cobblemon BattleFormat.GEN_9_SINGLES.");
    }

    private static Method findPvnMethod(int parameterCount) {
        for (Method method : BattleBuilder.class.getMethods()) {
            if (!"pvn".equals(method.getName())) continue;
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != parameterCount) continue;
            if (!parameters[0].isAssignableFrom(ServerPlayer.class) || !parameters[1].isAssignableFrom(NPCEntity.class)) continue;
            if (parameterCount == 3 && !parameters[2].isAssignableFrom(BattleFormat.class)) continue;
            if (parameterCount == 5) {
                if (!parameters[2].isAssignableFrom(BattleFormat.class)) continue;
                if (!(parameters[3] == boolean.class || parameters[3] == Boolean.class)) continue;
                if (!(parameters[4] == boolean.class || parameters[4] == Boolean.class)) continue;
            }
            return method;
        }
        return null;
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "plugin_trainer";
        return source.trim().toLowerCase(Locale.ROOT);
    }
}
