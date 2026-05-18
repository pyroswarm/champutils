package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProfessionSpecialCelebration {

    private static final Map<UUID, Long> TITLE_BUSY_UNTIL_MS =
            new HashMap<>();

    private ProfessionSpecialCelebration() {
    }

    public static void celebrateDropMultiplier(
            ServerPlayer player,
            int multiplier
    ) {

        if (
                player == null ||
                        multiplier <= 1
        ) {
            return;
        }

        switch (multiplier) {
            case 2 -> show(
                    player,
                    "§bDouble Drop!",
                    "§f2x drops triggered",
                    "§bMining passive: §f2x drops triggered!",
                    5,
                    30,
                    10,
                    0.75F,
                    1.35F,
                    CelebrationSound.SMALL
            );
            case 3 -> show(
                    player,
                    "§aTriple Drop!",
                    "§f3x drops triggered",
                    "§aMining passive: §f3x drops triggered!",
                    5,
                    40,
                    10,
                    0.9F,
                    1.45F,
                    CelebrationSound.MEDIUM
            );
            case 4 -> show(
                    player,
                    "§dQuadruple Drop!",
                    "§f4x drops triggered",
                    "§dMining passive: §f4x drops triggered!",
                    5,
                    35,
                    10,
                    0.85F,
                    1.75F,
                    CelebrationSound.PING
            );
            default -> show(
                    player,
                    "§6§lQUINTUPLE DROP!",
                    "§e§l5x JACKPOT DROPS!",
                    "§6Mining passive: §f5x jackpot drops triggered!",
                    5,
                    40,
                    10,
                    0.9F,
                    1.95F,
                    CelebrationSound.PING
            );
        }
    }

    public static void celebrateSpecialActive(
            ServerPlayer player,
            String title,
            String subtitle
    ) {

        String safeTitle =
                title == null || title.isBlank()
                        ? "§6Active Ability!"
                        : title;

        String safeSubtitle =
                subtitle == null
                        ? ""
                        : subtitle;

        String chatFallback =
                safeSubtitle.isBlank()
                        ? safeTitle
                        : safeTitle + " §7- " + safeSubtitle;

        show(
                player,
                safeTitle,
                safeSubtitle,
                chatFallback,
                5,
                35,
                10,
                0.85F,
                1.25F,
                CelebrationSound.MEDIUM
        );
    }

    private static void show(
            ServerPlayer player,
            String title,
            String subtitle,
            String chatFallback,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks,
            float volume,
            float pitch,
            CelebrationSound sound
    ) {

        if (player == null) {
            return;
        }

        if (
                !ProfessionNotificationSettings.areProfessionPopupsEnabled(
                        player
                )
        ) {
            return;
        }

        long nowMs =
                System.currentTimeMillis();

        UUID playerId =
                player.getUUID();

        long busyUntilMs =
                TITLE_BUSY_UNTIL_MS.getOrDefault(
                        playerId,
                        0L
                );

        if (nowMs < busyUntilMs) {
            if (
                    chatFallback != null &&
                            !chatFallback.isBlank()
            ) {
                player.sendSystemMessage(
                        Component.literal(
                                chatFallback
                        )
                );
            }
            return;
        }

        long durationMs =
                Math.max(
                        750L,
                        (long) (fadeInTicks + stayTicks + fadeOutTicks) * 50L
                );

        TITLE_BUSY_UNTIL_MS.put(
                playerId,
                nowMs + durationMs
        );

        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        fadeInTicks,
                        stayTicks,
                        fadeOutTicks
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        Component.literal(
                                title
                        )
                )
        );

        if (
                subtitle != null &&
                        !subtitle.isBlank()
        ) {
            player.connection.send(
                    new ClientboundSetSubtitleTextPacket(
                            Component.literal(
                                    subtitle
                            )
                    )
            );
        }

        playSound(
                player,
                sound,
                volume,
                pitch
        );
    }

    private static void playSound(
            ServerPlayer player,
            CelebrationSound sound,
            float volume,
            float pitch
    ) {

        switch (sound) {
            case SMALL -> ProfessionNotificationSettings.playSound(
                    player,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
            case MEDIUM -> ProfessionNotificationSettings.playSound(
                    player,
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
            case PING -> ProfessionNotificationSettings.playSound(
                    player,
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
        }
    }

    private enum CelebrationSound {
        SMALL,
        MEDIUM,
        PING
    }
}
