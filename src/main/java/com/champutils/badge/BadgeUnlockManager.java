package com.champutils.badge;

import com.champutils.cosmetic.TitleManager;
import com.champutils.gym.GymProgressRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Set;

public class BadgeUnlockManager {

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

        if (!commands.isEmpty() || !permissions.isEmpty() || !titles.isEmpty()) {
            player.sendSystemMessage(Component.literal("Badge rewards updated. Unlocked commands: " + (commands.isEmpty() ? "none" : String.join(", ", commands))).withStyle(ChatFormatting.GOLD));
        }
    }

    public static boolean grantsPermission(ServerPlayer player, String permission) {
        return BadgeUnlockConfig.grantsPermission(player, permission);
    }

    public static boolean hasEvTrainingAccess(ServerPlayer player) {
        return GymProgressRepository.defeatedCount(player) >= 5;
    }

    public static boolean hasEliteFourAccess(ServerPlayer player) {
        return GymProgressRepository.defeatedCount(player) >= 8;
    }
}
