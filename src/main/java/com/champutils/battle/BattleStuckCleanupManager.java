package com.champutils.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class BattleStuckCleanupManager {

    private static final int CHECK_EVERY_TICKS = 20 * 30;
    private static final int STALE_LOCAL_STATE_TICKS = 20 * 120;

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() <= 0) {
            return;
        }

        if (server.getTickCount() % CHECK_EVERY_TICKS != 0) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            cleanupIfStale(player, false);
        }
    }

    public static boolean cleanupIfStale(
            ServerPlayer player,
            boolean notify
    ) {
        if (player == null) {
            return false;
        }

        if (!BattleStateManager.hasTrackedState(player)) {
            return false;
        }

        long ageTicks = BattleStateManager.getBattleAgeTicks(player);
        Object battle = BattleStateManager.getBattle(player);

        boolean stale = (battle == null && ageTicks >= STALE_LOCAL_STATE_TICKS)
                || (battle != null && !BattleStateManager.looksLikeActiveBattle(player));

        if (!stale) {
            return false;
        }

        BattleStateManager.clearAll(player);
        BattleContextManager.clearContext(player.getUUID());
        BattleProfileRecoveryManager.handleBattleEnded(player, "stale-battle-cleanup");

        if (notify) {
            player.sendSystemMessage(
                    Component.literal("Your stale battle state was cleared.")
                            .withStyle(ChatFormatting.GREEN)
            );
        }

        return true;
    }
}
