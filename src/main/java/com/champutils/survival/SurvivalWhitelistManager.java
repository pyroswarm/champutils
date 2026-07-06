package com.champutils.survival;

import com.champutils.profile.ProfileNetworkTransferFlow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public final class SurvivalWhitelistManager {
    private SurvivalWhitelistManager() {}

    public static void handleJoin(ServerPlayer player) {
        if (player == null || player.server == null) return;
        if (!ProfileNetworkTransferFlow.isSurvivalServer()) return;
        if (!SurvivalWhitelistConfig.DATA.enabled) return;
        if (isAllowed(player)) return;
        player.connection.disconnect(Component.literal(SurvivalWhitelistConfig.DATA.kickMessage).withStyle(ChatFormatting.RED));
    }

    public static boolean isAllowed(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        if (player.server.getPlayerList().isOp(player.getGameProfile())) return true;
        String name = player.getGameProfile().getName() == null ? "" : player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        String uuid = player.getUUID().toString().toLowerCase(Locale.ROOT);
        return SurvivalWhitelistConfig.DATA.allowedNames.contains(name) || SurvivalWhitelistConfig.DATA.allowedUuids.contains(uuid);
    }

    public static boolean addName(String name) {
        if (name == null || name.isBlank()) return false;
        String clean = name.trim().toLowerCase(Locale.ROOT);
        if (!SurvivalWhitelistConfig.DATA.allowedNames.contains(clean)) SurvivalWhitelistConfig.DATA.allowedNames.add(clean);
        SurvivalWhitelistConfig.save();
        return true;
    }

    public static boolean removeName(String name) {
        if (name == null || name.isBlank()) return false;
        boolean removed = SurvivalWhitelistConfig.DATA.allowedNames.remove(name.trim().toLowerCase(Locale.ROOT));
        SurvivalWhitelistConfig.save();
        return removed;
    }
}
