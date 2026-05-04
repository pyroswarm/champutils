package com.champutils.battle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BattleContextManager {

    public enum BattleType {
        RANKED,
        CASUAL,
        GYM,
        ELITE_FOUR,
        NPC,
        TOURNAMENT,
        WORLD_BOSS,
        PROFESSION,
        UNKNOWN
    }

    private static final Map<UUID, BattleType> PLAYER_CONTEXT =
            new HashMap<>();

    public static void setContext(
            UUID playerId,
            BattleType type
    ) {
        PLAYER_CONTEXT.put(
                playerId,
                type
        );
    }

    public static BattleType getContext(
            UUID playerId
    ) {
        return PLAYER_CONTEXT.getOrDefault(
                playerId,
                BattleType.UNKNOWN
        );
    }

    public static boolean isRanked(
            UUID playerId
    ) {
        return getContext(playerId) ==
                BattleType.RANKED;
    }

    public static void clearContext(
            UUID playerId
    ) {
        PLAYER_CONTEXT.remove(playerId);
    }
}