package com.champutils.profile;

import net.minecraft.server.MinecraftServer;

/**
 * Compatibility wrapper for the old once-per-minute playtime hook.
 * Actual tracking is now per active profile and cached in ProfilePlaytimeManager.
 */
public final class PlaytimeManager {
    private PlaytimeManager() {}

    public static void addOnlineMinute(MinecraftServer server) {
        ProfilePlaytimeManager.addOnlineMinute(server);
        ProfilePlaytimeManager.flushAsync();
    }
}
