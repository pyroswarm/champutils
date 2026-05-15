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
    private static final long XP_COOLDOWN_MS = 900L;

    private ProfessionActionBarManager() {
    }

    public static void sendXpMessage(ServerPlayer player, ProfessionType type, int xp) {
        if (player == null || type == null || xp <= 0) {
            return;
        }

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

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.4f,
                1.8f
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

        String color = getProfessionColor(type);

        player.displayClientMessage(
                Component.literal(color + type.name() + " Level Up! §fLevel " + level),
                true
        );

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                0.8f,
                1.25f
        );
    }

    public static void sendRareDropMessage(ServerPlayer player, String itemId, int amount) {
        if (player == null || itemId == null || itemId.isBlank() || amount <= 0) {
            return;
        }

        if (!ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            return;
        }

        player.displayClientMessage(
                Component.literal("§6Rare Drop! §e" + itemId + " x" + amount),
                true
        );

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS,
                1.0f,
                1.2f
        );
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
            default -> "§f";
        };
    }
}
