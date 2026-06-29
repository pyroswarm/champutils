package com.champutils.badge;

import com.champutils.cosmetic.TitleManager;
import com.champutils.gym.GymProgressRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

public class BadgeUnlockManager {

    private static final Set<BadgeType> GYM_BADGES = Set.of(
            BadgeType.CASCADE,
            BadgeType.MARSH,
            BadgeType.EARTH,
            BadgeType.BOULDER,
            BadgeType.THUNDER,
            BadgeType.RAINBOW,
            BadgeType.SOUL,
            BadgeType.VOLCANO
    );

    public static void init() {
        BadgeUnlockConfig.load();
        BadgeManager.initSql();
    }

    public static void processUnlocks(ServerPlayer player) {
        if (player == null) return;
        Set<String> commands = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        Set<String> titles = new LinkedHashSet<>();

        for (BadgeType badge : BadgeManager.getBadges(player)) {
            commands.addAll(BadgeUnlockConfig.commands(badge));
            permissions.addAll(BadgeUnlockConfig.permissions(badge));
            for (String title : BadgeUnlockConfig.titles(badge)) {
                titles.add(title);
                TitleManager.unlock(player, title);
            }
        }

        BadgeSqlRepository.saveUnlockSnapshotAsync(player, commands, permissions, titles);

        if (!titles.isEmpty()) {
            player.sendSystemMessage(Component.literal("Badge rewards updated. New title rewards have been synced.").withStyle(ChatFormatting.GOLD));
        }
    }

    public static boolean grantsPermission(ServerPlayer player, String permission) {
        return BadgeUnlockConfig.grantsPermission(player, permission);
    }

    public static boolean hasEvTrainingAccess(ServerPlayer player) {
        return player != null && Math.max(gymBadgeCount(player), GymProgressRepository.defeatedCount(player)) >= 5;
    }

    public static boolean hasEliteFourAccess(ServerPlayer player) {
        return player != null && (BadgeManager.getBadges(player).containsAll(GYM_BADGES) || GymProgressRepository.defeatedCount(player) >= GYM_BADGES.size());
    }

    private static int gymBadgeCount(ServerPlayer player) {
        if (player == null) return 0;
        int count = 0;
        Set<BadgeType> badges = BadgeManager.getBadges(player);
        for (BadgeType badge : GYM_BADGES) {
            if (badges.contains(badge)) count++;
        }
        return count;
    }
}
