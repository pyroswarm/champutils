package com.champutils.badge;

import com.champutils.gym.GymProgressRepository;
import net.minecraft.server.level.ServerPlayer;

public class BadgeUnlockManager {

    public static void processUnlocks(ServerPlayer player) {
        // Gym progression is now stored per active SQL profile.
        // LuckPerms remains account-based only for staff/VIP/global perks.
    }

    public static boolean hasEvTrainingAccess(ServerPlayer player) {
        return GymProgressRepository.defeatedCount(player) >= 5;
    }

    public static boolean hasEliteFourAccess(ServerPlayer player) {
        return GymProgressRepository.defeatedCount(player) >= 8;
    }
}
