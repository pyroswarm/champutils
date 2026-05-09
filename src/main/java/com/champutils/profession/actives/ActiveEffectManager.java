package com.champutils.profession.actives;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ActiveEffectManager {

    private static final Map<UUID, Map<String, TimedEffect>> TIMED_EFFECTS =
            new HashMap<>();

    private static final Map<UUID, Set<String>> TOGGLED_EFFECTS =
            new HashMap<>();

    private ActiveEffectManager() {
    }

    public static void activateTimed(
            ServerPlayer player,
            String effectId,
            String displayName,
            int seconds
    ) {

        int safeSeconds =
                Math.max(
                        1,
                        seconds
                );

        TimedEffect effect =
                new TimedEffect(
                        normalize(effectId),
                        displayName,
                        System.currentTimeMillis() + safeSeconds * 1000L
                );

        TIMED_EFFECTS.computeIfAbsent(
                player.getUUID(),
                id -> new HashMap<>()
        ).put(
                normalize(effectId),
                effect
        );
    }

    public static void activateExcavation(
            ServerPlayer player,
            int seconds
    ) {

        activateTimed(
                player,
                "excavation",
                "Excavation",
                seconds
        );
    }

    public static boolean hasExcavation(
            ServerPlayer player
    ) {

        return hasTimedEffect(
                player,
                "excavation"
        );
    }

    public static long getExcavationSecondsLeft(
            ServerPlayer player
    ) {

        return getTimedSecondsLeft(
                player,
                "excavation"
        );
    }

    public static boolean hasTimedEffect(
            ServerPlayer player,
            String effectId
    ) {

        TimedEffect effect =
                getTimedEffect(
                        player,
                        effectId
                );

        if (effect == null) {
            return false;
        }

        if (System.currentTimeMillis() > effect.expiresAt) {
            removeTimedEffect(
                    player,
                    effectId
            );
            return false;
        }

        return true;
    }

    public static long getTimedSecondsLeft(
            ServerPlayer player,
            String effectId
    ) {

        TimedEffect effect =
                getTimedEffect(
                        player,
                        effectId
                );

        if (effect == null) {
            return 0L;
        }

        long remaining =
                effect.expiresAt - System.currentTimeMillis();

        if (remaining <= 0L) {
            removeTimedEffect(
                    player,
                    effectId
            );
            return 0L;
        }

        return (long) Math.ceil(
                remaining / 1000D
        );
    }

    public static boolean toggleEffect(
            ServerPlayer player,
            String effectId,
            String displayName
    ) {

        String normalized =
                normalize(effectId);

        Set<String> effects =
                TOGGLED_EFFECTS.computeIfAbsent(
                        player.getUUID(),
                        id -> new HashSet<>()
                );

        boolean nowEnabled;

        if (effects.contains(normalized)) {
            effects.remove(normalized);
            nowEnabled = false;
        }
        else {
            effects.add(normalized);
            nowEnabled = true;
        }

        sendToggleMessage(
                player,
                displayName,
                nowEnabled
        );

        return nowEnabled;
    }

    public static boolean hasToggle(
            ServerPlayer player,
            String effectId
    ) {

        Set<String> effects =
                TOGGLED_EFFECTS.get(
                        player.getUUID()
                );

        return effects != null &&
                effects.contains(
                        normalize(effectId)
                );
    }

    public static boolean hasAutoSmelt(
            ServerPlayer player
    ) {

        return hasTimedEffect(
                player,
                "auto_smelt"
        ) || hasToggle(
                player,
                "auto_smelt"
        );
    }

    public static void tick(
            MinecraftServer server
    ) {

        long now =
                System.currentTimeMillis();

        Iterator<Map.Entry<UUID, Map<String, TimedEffect>>> playerIterator =
                TIMED_EFFECTS.entrySet()
                        .iterator();

        while (playerIterator.hasNext()) {

            Map.Entry<UUID, Map<String, TimedEffect>> playerEntry =
                    playerIterator.next();

            ServerPlayer player =
                    server.getPlayerList()
                            .getPlayer(
                                    playerEntry.getKey()
                            );

            if (player == null) {
                continue;
            }

            Iterator<Map.Entry<String, TimedEffect>> effectIterator =
                    playerEntry.getValue()
                            .entrySet()
                            .iterator();

            while (effectIterator.hasNext()) {

                TimedEffect effect =
                        effectIterator.next()
                                .getValue();

                long remainingMillis =
                        effect.expiresAt - now;

                if (remainingMillis <= 0L) {
                    effectIterator.remove();
                    sendExpiredMessage(
                            player,
                            effect.displayName
                    );
                    continue;
                }

                long secondsLeft =
                        (long) Math.ceil(
                                remainingMillis / 1000D
                        );

                warnIfNeeded(
                        player,
                        effect,
                        secondsLeft
                );
            }

            if (playerEntry.getValue().isEmpty()) {
                playerIterator.remove();
            }
        }

        if (server.getTickCount() % 10 == 0) {
            tickMagnetToggles(server);
        }
    }

    private static void tickMagnetToggles(
            MinecraftServer server
    ) {

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            if (!hasToggle(
                    player,
                    "ore_magnet"
            )) {
                continue;
            }

            double radius =
                    8.0D;

            AABB box =
                    player.getBoundingBox()
                            .inflate(radius);

            for (ItemEntity itemEntity : player.serverLevel().getEntitiesOfClass(ItemEntity.class, box)) {

                if (!itemEntity.isAlive()) {
                    continue;
                }

                Vec3 pull =
                        player.position()
                                .add(0.0D, 0.75D, 0.0D)
                                .subtract(itemEntity.position())
                                .normalize()
                                .scale(0.35D);

                itemEntity.setDeltaMovement(
                        itemEntity.getDeltaMovement()
                                .add(pull)
                );
            }
        }
    }

    private static TimedEffect getTimedEffect(
            ServerPlayer player,
            String effectId
    ) {

        Map<String, TimedEffect> effects =
                TIMED_EFFECTS.get(
                        player.getUUID()
                );

        if (effects == null) {
            return null;
        }

        return effects.get(
                normalize(effectId)
        );
    }

    private static void removeTimedEffect(
            ServerPlayer player,
            String effectId
    ) {

        Map<String, TimedEffect> effects =
                TIMED_EFFECTS.get(
                        player.getUUID()
                );

        if (effects == null) {
            return;
        }

        effects.remove(
                normalize(effectId)
        );

        if (effects.isEmpty()) {
            TIMED_EFFECTS.remove(
                    player.getUUID()
            );
        }
    }

    private static void warnIfNeeded(
            ServerPlayer player,
            TimedEffect effect,
            long secondsLeft
    ) {

        if (secondsLeft == 10L && !effect.warned10) {
            effect.warned10 = true;
            Component message =
                    Component.literal(
                            "§e" + effect.displayName + " ends in 10s!"
                    );

            player.displayClientMessage(
                    message,
                    true
            );
            player.sendSystemMessage(message);
            showCountdownTitle(
                    player,
                    "§e" + effect.displayName,
                    "§fends in §e10s",
                    5,
                    25,
                    5
            );
            player.playNotifySound(
                    SoundEvents.NOTE_BLOCK_PLING.value(),
                    SoundSource.PLAYERS,
                    0.4F,
                    1.2F
            );
            return;
        }

        if (secondsLeft <= 5L && secondsLeft >= 1L) {
            if (effect.warnedFinalSeconds.add(secondsLeft)) {
                Component message =
                        Component.literal(
                                "§c" + effect.displayName + " ends in " + secondsLeft + "..."
                        );

                player.displayClientMessage(
                        message,
                        true
                );
                showCountdownTitle(
                        player,
                        "§c" + secondsLeft,
                        "§f" + effect.displayName + " ending",
                        0,
                        20,
                        5
                );
                player.playNotifySound(
                        SoundEvents.NOTE_BLOCK_PLING.value(),
                        SoundSource.PLAYERS,
                        0.5F,
                        1.6F
                );
            }
        }
    }

    private static void showCountdownTitle(
            ServerPlayer player,
            String title,
            String subtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks
    ) {

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

        player.connection.send(
                new ClientboundSetSubtitleTextPacket(
                        Component.literal(
                                subtitle
                        )
                )
        );
    }

    private static void sendExpiredMessage(
            ServerPlayer player,
            String displayName
    ) {

        player.displayClientMessage(
                Component.literal(
                        "§7" + displayName + " has ended."
                ),
                true
        );

        player.sendSystemMessage(
                Component.literal(
                        "§7" + displayName + " has ended."
                )
        );

        player.playNotifySound(
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.5F,
                0.8F
        );
    }

    private static void sendToggleMessage(
            ServerPlayer player,
            String displayName,
            boolean enabled
    ) {

        String status =
                enabled ? "§aON" : "§cOFF";

        Component message =
                Component.literal(
                        "§6" + displayName + " toggled " + status + "§6."
                );

        player.sendSystemMessage(message);
        player.displayClientMessage(message, true);

        player.playNotifySound(
                enabled ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS,
                0.75F,
                enabled ? 1.4F : 0.7F
        );
    }

    private static String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase();
    }

    private static class TimedEffect {

        private final String effectId;
        private final String displayName;
        private final long expiresAt;
        private boolean warned10 = false;
        private final Set<Long> warnedFinalSeconds =
                new HashSet<>();

        private TimedEffect(
                String effectId,
                String displayName,
                long expiresAt
        ) {

            this.effectId = effectId;
            this.displayName = displayName;
            this.expiresAt = expiresAt;
        }
    }
}
