package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfessionActionBarManager {

    private static final Map<UUID, Long> XP_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> BATTLE_LOOT_SOUND_COOLDOWNS = new HashMap<>();
    private static final long XP_COOLDOWN_MS = 900L;
    private static final long BATTLE_LOOT_SOUND_COOLDOWN_MS = 750L;

    private ProfessionActionBarManager() {
    }

    public static void sendXpMessage(ServerPlayer player, ProfessionType type, int xp) {
        if (player == null || type == null || xp <= 0) {
            return;
        }

        // Sound feedback is intentionally not throttled with the action-bar popup. Every XP
        // award gets its own ding, including multiple professions rewarding on the same tick.
        ProfessionNotificationSettings.playStackingSound(player,
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.4f,
                1.8f
        );

        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        if (isXpOnCooldown(player)) {
            return;
        }

        String color = getProfessionColor(type);

        player.displayClientMessage(
                Component.literal(color + "+" + xp + " " + type.name() + " XP"),
                true
        );

        XP_COOLDOWNS.put(player.getUUID(), System.currentTimeMillis());
    }

    public static void sendLevelUpMessage(ServerPlayer player, ProfessionType type, int level) {
        if (player == null || type == null || level <= 0) {
            return;
        }

        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        ProfessionSpecialCelebration.celebrateProfessionLevelUp(
                player,
                type,
                level
        );
    }

    public static void sendRareDropMessage(ServerPlayer player, String itemId, int amount) {
        sendRareDropMessage(player, itemId, amount, true);
    }

    public static void sendBattleLootMessage(ServerPlayer player, String itemId, int amount) {
        sendRareDropMessage(player, itemId, amount, false);
    }

    public static void playBattleSuperRareSound(ServerPlayer player) {
        if (player == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Long last = BATTLE_LOOT_SOUND_COOLDOWNS.get(player.getUUID());
        if (last != null && now - last < BATTLE_LOOT_SOUND_COOLDOWN_MS) {
            return;
        }
        BATTLE_LOOT_SOUND_COOLDOWNS.put(player.getUUID(), now);
        ProfessionNotificationSettings.playSound(player,
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS,
                1.0f,
                1.2f
        );
    }

    public static void sendRareDropMessage(ServerPlayer player, String itemId, int amount, boolean playSound) {
        if (player == null || itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }

        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        player.sendSystemMessage(
                Component.literal("§6Battle loot roll: §e" + itemId + " x" + amount)
        );

        if (playSound) {
            playBattleSuperRareSound(player);
        }
    }

    private static boolean isXpOnCooldown(ServerPlayer player) {
        Long last = XP_COOLDOWNS.get(player.getUUID());
        if (last == null) {
            return false;
        }
        return System.currentTimeMillis() - last < XP_COOLDOWN_MS;
    }

    private static String getProfessionColor(ProfessionType type) {
        return switch (type) {
            case MINING -> "§b";
            case FORESTRY -> "§a";
            case FARMING -> "§e";
            case BATTLING -> "§6";
            case BREEDING -> "§d";
            default -> "§f";
        };
    }
}
