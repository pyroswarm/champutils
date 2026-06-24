package com.champutils.battle;

import com.cobblemon.mod.common.battles.BattleBuilder;
import com.cobblemon.mod.common.battles.BattleFormat;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;

/** Starts PvP battles with a BattleFormat when the installed Cobblemon API exposes that overload. */
public final class PvPBattleStarter {

    private PvPBattleStarter() {
    }

    public static void start1v1(ServerPlayer p1, ServerPlayer p2, BattleFormat battleFormat) throws Exception {
        Object builder = BattleBuilder.INSTANCE;

        Method withFormat = findPvp1v1Method(3);
        if (withFormat != null && battleFormat != null) {
            withFormat.invoke(builder, p1, p2, battleFormat);
            return;
        }

        Method withoutFormat = findPvp1v1Method(2);
        if (withoutFormat != null) {
            System.out.println("[ChampUtils] Cobblemon pvp1v1 BattleFormat overload was not found. Starting default PvP battle without format rules.");
            withoutFormat.invoke(builder, p1, p2);
            return;
        }

        throw new NoSuchMethodException("Could not find Cobblemon BattleBuilder.pvp1v1 overload.");
    }

    public static Object startPvn(ServerPlayer player, com.cobblemon.mod.common.entity.npc.NPCEntity npc, BattleFormat battleFormat) throws Exception {
        Object builder = BattleBuilder.INSTANCE;

        Method withFormat = findPvnMethod(3);
        if (withFormat != null && battleFormat != null) {
            return withFormat.invoke(builder, player, npc, battleFormat);
        }

        Method withoutFormat = findPvnMethod(2);
        if (withoutFormat != null) {
            System.out.println("[ChampUtils] Cobblemon pvn BattleFormat overload was not found. Starting default PvN battle without format rules.");
            return withoutFormat.invoke(builder, player, npc);
        }

        throw new NoSuchMethodException("Could not find Cobblemon BattleBuilder.pvn overload.");
    }

    private static Method findPvnMethod(int parameterCount) {
        for (Method method : BattleBuilder.class.getMethods()) {
            if (!"pvn".equals(method.getName())) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != parameterCount) {
                continue;
            }

            if (!parameters[0].isAssignableFrom(ServerPlayer.class) || !parameters[1].isAssignableFrom(com.cobblemon.mod.common.entity.npc.NPCEntity.class)) {
                continue;
            }

            if (parameterCount == 3 && !parameters[2].isAssignableFrom(BattleFormat.class)) {
                continue;
            }

            return method;
        }

        return null;
    }

    private static Method findPvp1v1Method(int parameterCount) {
        for (Method method : BattleBuilder.class.getMethods()) {
            if (!"pvp1v1".equals(method.getName())) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != parameterCount) {
                continue;
            }

            if (!parameters[0].isAssignableFrom(ServerPlayer.class) || !parameters[1].isAssignableFrom(ServerPlayer.class)) {
                continue;
            }

            if (parameterCount == 3 && !parameters[2].isAssignableFrom(BattleFormat.class)) {
                continue;
            }

            return method;
        }

        return null;
    }
}
