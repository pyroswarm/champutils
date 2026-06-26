package com.champutils.profile;

import net.minecraft.server.level.ServerPlayer;

public final class ProfileLobbyDebug {
    private static final boolean ENABLED = true;
    private ProfileLobbyDebug() {}

    public static void log(String step, ServerPlayer player) {
        if (!ENABLED) return;
        String name = player == null ? "<null>" : player.getGameProfile().getName();
        String dim = "<none>";
        String menu = "<none>";
        try {
            if (player != null && player.serverLevel() != null) dim = player.serverLevel().dimension().location().toString();
            if (player != null && player.containerMenu != null) menu = player.containerMenu.getClass().getName();
        } catch (Throwable ignored) {}
        System.out.println("[PROFILE-LOBBY-DEBUG] " + step + " player=" + name + " dim=" + dim + " menu=" + menu);
    }

    public static void log(String step, ServerPlayer player, Throwable t) {
        log(step + " ERROR=" + (t == null ? "null" : t.getClass().getName() + ": " + t.getMessage()), player);
        if (t != null) t.printStackTrace();
    }
}
