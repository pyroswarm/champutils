package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfessionActionBarManager {

    private static final Map<UUID, Long> LAST_MESSAGE =
            new HashMap<>();

    private static final long COOLDOWN_MS = 750;

    public static void sendXpMessage(
            ServerPlayer player,
            ProfessionType type,
            int xp
    ) {

        if (player == null || xp <= 0) {
            return;
        }

        if (isOnCooldown(player)) {
            return;
        }

        String color =
                switch (type) {
                    case MINING -> "§b";
                    case FORESTRY -> "§a";
                    case FARMING -> "§e";
                    case BATTLING -> "§6";
                };

        player.displayClientMessage(
                Component.literal(
                        color +
                                "+" +
                                xp +
                                " " +
                                type.name() +
                                " XP"
                ),
                true
        );

        /*
         XP ding sound
         */
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.4f,
                1.8f
        );

        LAST_MESSAGE.put(
                player.getUUID(),
                System.currentTimeMillis()
        );
    }

    public static void sendLevelUpMessage(
            ServerPlayer player,
            ProfessionType type,
            int level
    ) {

        if (player == null) {
            return;
        }

        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        5,
                        40,
                        10
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        Component.literal(
                                "§dLEVEL UP!"
                        )
                )
        );

        player.connection.send(
                new ClientboundSetSubtitleTextPacket(
                        Component.literal(
                                "§f" +
                                        type.name() +
                                        " Lv. " +
                                        level
                        )
                )
        );

        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS,
                1f,
                1.1f
        );

        LAST_MESSAGE.put(
                player.getUUID(),
                System.currentTimeMillis()
        );
    }

    public static void sendRareDropMessage(
            ServerPlayer player,
            String itemId,
            int amount
    ) {

        if (player == null) {
            return;
        }

        /*
         Big center-screen popup
         */
        player.connection.send(
                new ClientboundSetTitlesAnimationPacket(
                        5,
                        50,
                        15
                )
        );

        player.connection.send(
                new ClientboundSetTitleTextPacket(
                        Component.literal(
                                "§6✦ RARE FIND ✦"
                        )
                )
        );

        player.connection.send(
                new ClientboundSetSubtitleTextPacket(
                        Component.literal(
                                "§e" +
                                        itemId +
                                        " x" +
                                        amount
                        )
                )
        );

        /*
         Rare reward sound
         */
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS,
                1f,
                1f
        );

        LAST_MESSAGE.put(
                player.getUUID(),
                System.currentTimeMillis()
        );
    }

    private static boolean isOnCooldown(
            ServerPlayer player
    ) {

        long now =
                System.currentTimeMillis();

        long last =
                LAST_MESSAGE.getOrDefault(
                        player.getUUID(),
                        0L
                );

        return now - last < COOLDOWN_MS;
    }
}