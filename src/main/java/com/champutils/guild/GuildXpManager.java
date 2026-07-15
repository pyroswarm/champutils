package com.champutils.guild;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GuildXpManager {
    private GuildXpManager() {}

    public static void awardBattleWin(ServerPlayer winner, boolean ranked) {
        if (winner == null) return;
        int amount = ranked ? GuildConfig.GUILD_XP.rankedWin : GuildConfig.GUILD_XP.casualWin;
        awardPlayer(winner, ranked ? "ranked_win" : "casual_win", amount, true);
    }

    public static void awardPlayer(ServerPlayer player, String source, long amount, boolean notify) {
        if (player == null || amount <= 0) return;
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null || guild.id == null) return;

        GuildRepository.addXp(guild.id, player.getUUID(), player.getGameProfile().getName(), source, amount, result -> {
            if (player.getServer() == null) return;
            player.getServer().execute(() -> {
                GuildRepository.loadForPlayer(player.getUUID(), player.getGameProfile().getName());
                if (!notify) return;
                if (result.leveledUp) {
                    GuildBuffManager.clearCache();
                    player.sendSystemMessage(Component.literal("Your guild gained " + amount + " XP and reached level " + result.newLevel + "!").withStyle(ChatFormatting.GOLD));
                    for (Component unlockMessage : GuildBuffManager.unlockedBuffMessages(result.oldLevel, result.newLevel)) {
                        broadcastToGuild(player, unlockMessage);
                    }
                } else {
                    player.sendSystemMessage(Component.literal("Your guild gained " + amount + " XP.").withStyle(ChatFormatting.DARK_AQUA));
                }
            });
        });
    }

    private static void broadcastToGuild(ServerPlayer sourcePlayer, Component message) {
        if (sourcePlayer == null || sourcePlayer.getServer() == null || message == null) return;
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(sourcePlayer.getUUID());
        if (guild == null || guild.id == null) return;

        for (ServerPlayer online : sourcePlayer.getServer().getPlayerList().getPlayers()) {
            GuildRepository.GuildSnapshot otherGuild = GuildRepository.cachedGuild(online.getUUID());
            if (otherGuild != null && guild.id.equals(otherGuild.id)) {
                online.sendSystemMessage(message);
            }
        }
    }
}
