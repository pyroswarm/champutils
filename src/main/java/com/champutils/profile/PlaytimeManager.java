package com.champutils.profile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Tracks total online time for website player profiles.
 *
 * The server tick loop calls this once every 60 seconds. Each online player
 * receives +60 playtime seconds, which is persisted locally and synced to Supabase
 * through PlayerDataManager.save(...).
 */
public final class PlaytimeManager {

    private static final long PLAYTIME_SYNC_SECONDS = 60L;

    private PlaytimeManager() {
    }

    public static void addOnlineMinute(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }

            PlayerDataManager.addPlaytimeSeconds(
                    player.getUUID(),
                    player.getName().getString(),
                    PLAYTIME_SYNC_SECONDS
            );
        }
    }
}
