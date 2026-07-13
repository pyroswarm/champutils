package com.champutils.profile;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;
import com.champutils.cosmetic.TitleManager;
import com.champutils.worldfirst.WorldFirstManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class ChallengeProfileTitleManager {
    private ChallengeProfileTitleManager() {}

    public static void handleChampionVictory(ServerPlayer player) {
        if (player == null) return;
        ProfileGameMode mode = PlayerProfileManager.gameMode(player);
        if (!hasCompletedEliteFourAndChampion(player)) return;

        String titleId = switch (mode) {
            case NORMAL -> "normal_champion";
            case ISLANDER -> "islander_champion";
            case IRONMAN -> "ironman_champion";
            case NUZLOCKE -> "nuzlocke_champion";
            case MONOTYPE -> "monotype_champion";
        };
        String worldFirstId = switch (mode) {
            case NORMAL -> "first_normal_profile_complete";
            case ISLANDER -> "first_islander_profile_complete";
            case IRONMAN -> "first_ironman_profile_complete";
            case NUZLOCKE -> "first_nuzlocke_profile_complete";
            case MONOTYPE -> "first_monotype_profile_complete";
        };

        boolean unlocked = TitleManager.unlock(player, titleId);
        // WorldFirstManager is database-backed and idempotent, so this remains safe
        // if the Champion victory callback is delivered more than once.
        WorldFirstManager.award(player, worldFirstId);
        if (unlocked) {
            String label = mode == ProfileGameMode.NORMAL ? "profile completion" : "challenge profile completion";
            player.sendSystemMessage(Component.literal("Champion Umbra defeated! Account-bound " + label + " title unlocked.").withStyle(ChatFormatting.GOLD));
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
