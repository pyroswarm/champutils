package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

public final class ProfessionSpecialCelebration {

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
                    5,
                    50,
                    15,
                    1.0F,
                    1.6F,
                    CelebrationSound.BIG
            );
            default -> show(
                    player,
                    "§6§lQUINTUPLE DROP!",
                    "§e§l5x JACKPOT DROPS!",
                    5,
                    70,
                    20,
                    1.2F,
                    1.0F,
                    CelebrationSound.JACKPOT
            );
        }
    }

    public static void celebrateSpecialActive(
            ServerPlayer player,
            String title,
            String subtitle
    ) {

        show(
                player,
                title == null || title.isBlank()
                        ? "§6Active Ability!"
                        : title,
                subtitle == null
                        ? ""
                        : subtitle,
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
            case SMALL -> player.playNotifySound(
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
            case MEDIUM -> player.playNotifySound(
                    SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
            case BIG -> player.playNotifySound(
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    volume,
                    pitch
            );
            case JACKPOT -> {
                player.playNotifySound(
                        SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                        SoundSource.PLAYERS,
                        volume,
                        pitch
                );
                player.playNotifySound(
                        SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS,
                        1.0F,
                        0.7F
                );
            }
        }
    }

    private enum CelebrationSound {
        SMALL,
        MEDIUM,
        BIG,
        JACKPOT
    }
}
