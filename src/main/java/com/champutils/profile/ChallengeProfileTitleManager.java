package com.champutils.profile;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.cosmetic.TitleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ChallengeProfileTitleManager {
    private ChallengeProfileTitleManager() {}

    public static void handleChampionVictory(ServerPlayer player) {
        if (player == null) return;
        ProfileGameMode mode = PlayerProfileManager.gameMode(player);
        if (mode == ProfileGameMode.NORMAL) return;
        if (!hasCompletedEliteFourAndChampion(player)) return;

        String titleId = switch (mode) {
            case ISLANDER -> "islander_champion";
            case IRONMAN -> "ironman_champion";
            case NUZLOCKE -> "nuzlocke_champion";
            case MONOTYPE -> "monotype_champion";
            default -> null;
        };
        if (titleId == null) return;

        boolean unlocked = TitleManager.unlock(player, titleId);
        if (unlocked) {
            player.sendSystemMessage(Component.literal("Challenge complete! You unlocked an account-bound special profile title.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean hasCompletedEliteFourAndChampion(ServerPlayer player) {
        if (player == null) return false;
        BadgeType[] required = new BadgeType[] {
                BadgeType.BOULDER,
                BadgeType.CASCADE,
                BadgeType.THUNDER,
                BadgeType.RAINBOW,
                BadgeType.SOUL,
                BadgeType.MARSH,
                BadgeType.VOLCANO,
                BadgeType.EARTH,
                BadgeType.LORELEI,
                BadgeType.BRUNO,
                BadgeType.AGATHA,
                BadgeType.LANCE,
                BadgeType.CHAMPION
        };
        for (BadgeType badge : required) {
            if (!BadgeManager.hasBadge(player, badge)) return false;
        }
        return true;
    }
}
