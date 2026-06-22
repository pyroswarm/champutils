package com.champutils.gym;

import com.champutils.badge.BadgeType;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.network.chat.Component;

public final class GymNpcNameUtil {
    private GymNpcNameUtil() {}

    public static void apply(NPCEntity npc, BadgeType badge) {
        if (npc == null || badge == null) return;
        GymConfig.GymDefinition gym = GymConfig.getGym(badge);
        String name = firstNonBlank(gym == null ? null : gym.leaderName, gym == null ? null : gym.spawnName, badge.getDisplayName() + " Leader");
        int levelCap = gym == null ? 0 : Math.max(0, gym.levelCap);
        String label = levelCap > 0 ? "§6" + name + "\n§eLv Cap " + levelCap : "§6" + name + "\n§eGym Leader";
        npc.setCustomName(Component.literal(label));
        npc.setCustomNameVisible(true);
    }

    private static String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) return value.trim();
            }
        }
        return "Gym Leader";
    }
}
