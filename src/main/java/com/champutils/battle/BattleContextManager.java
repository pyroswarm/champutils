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
        MEGA_BOSS,
        PROFESSION,
        UNKNOWN
    }

    private static final Map<UUID, BattleType> PLAYER_CONTEXT =
            new HashMap<>();

    private static final Map<UUID, String> PLAYER_FORMAT =
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

    public static void setFormatId(
            UUID playerId,
            String formatId
    ) {
        if (formatId == null || formatId.isBlank()) {
            PLAYER_FORMAT.remove(playerId);
            return;
        }

        PLAYER_FORMAT.put(
                playerId,
                formatId.toLowerCase()
        );
    }

    public static String getFormatId(
            UUID playerId
    ) {
        String explicit = PLAYER_FORMAT.get(playerId);
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }

        BattleType type = getContext(playerId);
        if (type == BattleType.RANKED) {
            return "ranked";
        }
        if (type == BattleType.CASUAL) {
            return "casual";
        }

        return null;
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
        PLAYER_FORMAT.remove(playerId);
    }
}