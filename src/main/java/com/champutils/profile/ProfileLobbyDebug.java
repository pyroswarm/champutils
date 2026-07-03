package com.champutils.profile;

import com.champutils.debug.ChampDebugManager;
import net.minecraft.server.level.ServerPlayer;

public final class ProfileLobbyDebug {
    private ProfileLobbyDebug() {}

    public static void log(String step, ServerPlayer player) {
        if (!ChampDebugManager.isEnabled(ChampDebugManager.Category.PROFILES)) return;
        String name = player == null ? "unknown" : player.getGameProfile().getName();
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[ChampUtils][ProfileLobbyDebug] " + step + " player=" + name);
    }

    public static void log(String step, ServerPlayer player, Throwable t) {
        if (!ChampDebugManager.isEnabled(ChampDebugManager.Category.PROFILES)) return;
        String name = player == null ? "unknown" : player.getGameProfile().getName();
        String error = t == null ? "none" : t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
        ChampDebugManager.log(ChampDebugManager.Category.PROFILES, "[ChampUtils][ProfileLobbyDebug] " + step + " player=" + name + " error=" + error);
    }
}
