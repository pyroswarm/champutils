package com.champutils.profile;

import net.minecraft.server.level.ServerPlayer;

public final class ProfileLobbyDebug {
    private static final boolean ENABLED = false;
    private ProfileLobbyDebug() {}

    public static void log(String step, ServerPlayer player) {
        if (!ENABLED) return;
    }

    public static void log(String step, ServerPlayer player, Throwable t) {
        if (!ENABLED) return;
    }
}
