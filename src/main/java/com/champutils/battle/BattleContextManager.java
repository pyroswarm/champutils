package com.champutils.battle;

import java.util.concurrent.ConcurrentHashMap;
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
        ADVENTURE_TOWER,
        ADVENTURE_ROAMING,
        UNKNOWN
    }

    private static final Map<UUID, BattleType> PLAYER_CONTEXT =
            new ConcurrentHashMap<>();

    private static final Map<UUID, String> PLAYER_FORMAT =
            new ConcurrentHashMap<>();

    public record TrainerBattleContext(
            UUID playerId,
            UUID npcId,
            BattleType type,
            String source,
            long createdMillis
    ) {}

    private static final Map<UUID, TrainerBattleContext> PENDING_TRAINER_BY_PLAYER =
            new ConcurrentHashMap<>();

    private static final Map<UUID, TrainerBattleContext> PENDING_TRAINER_BY_NPC =
            new ConcurrentHashMap<>();

    private static final Map<String, TrainerBattleContext> TRAINER_BY_BATTLE_ID =
            new ConcurrentHashMap<>();

    public static void setContext(
            UUID playerId,
            BattleType type
    ) {
        PLAYER_CONTEXT.put(
                playerId,
                type
        );
    }

    public static TrainerBattleContext registerTrainerBattleContext(
            UUID playerId,
            UUID npcId,
            BattleType type,
            String source
    ) {
        TrainerBattleContext context = new TrainerBattleContext(
                playerId,
                npcId,
                type == null ? BattleType.NPC : type,
                source == null || source.isBlank() ? "plugin_trainer" : source,
                System.currentTimeMillis()
        );

        if (playerId != null) {
            PLAYER_CONTEXT.put(playerId, context.type());
            PENDING_TRAINER_BY_PLAYER.put(playerId, context);
        }
        if (npcId != null) {
            PENDING_TRAINER_BY_NPC.put(npcId, context);
        }
        return context;
    }

    public static TrainerBattleContext attachTrainerBattleContext(
            String battleId,
            UUID playerId,
            UUID npcId
    ) {
        TrainerBattleContext context = null;
        if (playerId != null) {
            context = PENDING_TRAINER_BY_PLAYER.remove(playerId);
        }
        if (context == null && npcId != null) {
            context = PENDING_TRAINER_BY_NPC.remove(npcId);
        }
        if (context == null) {
            return null;
        }
        if (context.npcId() != null) {
            PENDING_TRAINER_BY_NPC.remove(context.npcId());
        }
        if (context.playerId() != null) {
            PENDING_TRAINER_BY_PLAYER.remove(context.playerId());
            PLAYER_CONTEXT.put(context.playerId(), context.type());
        }
        if (battleId != null && !battleId.isBlank()) {
            TRAINER_BY_BATTLE_ID.put(battleId, context);
        }
        return context;
    }

    public static TrainerBattleContext registerTrainerBattleContextForBattle(
            String battleId,
            UUID playerId,
            UUID npcId,
            BattleType type,
            String source
    ) {
        TrainerBattleContext context = new TrainerBattleContext(
                playerId,
                npcId,
                type == null ? BattleType.NPC : type,
                source == null || source.isBlank() ? "plugin_trainer" : source,
                System.currentTimeMillis()
        );
        if (playerId != null) {
            PLAYER_CONTEXT.put(playerId, context.type());
        }
        if (battleId != null && !battleId.isBlank()) {
            TRAINER_BY_BATTLE_ID.put(battleId, context);
        }
        return context;
    }

    public static TrainerBattleContext getTrainerBattleContext(String battleId) {
        if (battleId == null || battleId.isBlank()) return null;
        return TRAINER_BY_BATTLE_ID.get(battleId);
    }

    public static TrainerBattleContext removeTrainerBattleContext(String battleId) {
        if (battleId == null || battleId.isBlank()) return null;
        return TRAINER_BY_BATTLE_ID.remove(battleId);
    }

    public static void clearPendingTrainerBattleContext(UUID playerId, UUID npcId) {
        if (playerId != null) PENDING_TRAINER_BY_PLAYER.remove(playerId);
        if (npcId != null) PENDING_TRAINER_BY_NPC.remove(npcId);
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
        TrainerBattleContext pending = PENDING_TRAINER_BY_PLAYER.remove(playerId);
        if (pending != null && pending.npcId() != null) {
            PENDING_TRAINER_BY_NPC.remove(pending.npcId());
        }
    }
}