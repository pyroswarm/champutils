package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionToolMetadata;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
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

    private static final Map<UUID, Map<String, ToggleEffect>> TOGGLED_EFFECTS =
            new HashMap<>();

    private ActiveEffectManager() {
    }


    public static boolean canActivateAbility(
            ServerPlayer player,
            String abilityId,
            ItemStack stack
    ) {

        String current = getCurrentActiveDisplayName(player);

        if (current == null) {
            return true;
        }

        /*
         * Allow the player to use the same held toggle again to turn it off.
         * Starting any other active/toggle while one is running is blocked by
         * ProfessionToolActiveAbilityListener.
         */
        String toggleEffectId = effectIdForAbility(abilityId);
        return toggleEffectId != null && hasToggle(player, toggleEffectId, stack);
    }

    public static String getCurrentActiveDisplayName(
            ServerPlayer player
    ) {

        UUID playerId = player.getUUID();
        long now = System.currentTimeMillis();

        Map<String, TimedEffect> timed = TIMED_EFFECTS.get(playerId);

        if (timed != null) {
            Iterator<Map.Entry<String, TimedEffect>> iterator = timed.entrySet().iterator();

            while (iterator.hasNext()) {
                TimedEffect effect = iterator.next().getValue();

                if (now > effect.expiresAt) {
                    iterator.remove();
                    continue;
                }

                return effect.displayName;
            }

            if (timed.isEmpty()) {
                TIMED_EFFECTS.remove(playerId);
            }
        }

        Map<String, ToggleEffect> toggles = TOGGLED_EFFECTS.get(playerId);

        if (toggles != null && !toggles.isEmpty()) {
            return toggles.values().iterator().next().displayName;
        }

        return null;
    }

    private static String effectIdForAbility(
            String abilityId
    ) {

        String normalized = normalize(abilityId);

        return switch (normalized) {
            case "auto_smelt_toggle" -> "auto_smelt";
            case "ore_magnet_toggle" -> "ore_magnet";
            case "tree_replant_toggle" -> "tree_replant";
            case "auto_replant_toggle", "replant_toggle" -> "auto_replant";
            default -> null;
        };
    }

    public static void activateTimed(
            ServerPlayer player,
            String effectId,
            String displayName,
            int seconds,
            ItemStack stack
    ) {

        int safeSeconds =
                Math.max(
                        1,
                        seconds
                );

        String toolInstanceId =
                getOrCreateToolInstanceId(
                        stack
                );

        if (toolInstanceId == null) {
            return;
        }

        TimedEffect effect =
                new TimedEffect(
                        normalize(effectId),
                        displayName,
                        System.currentTimeMillis() + safeSeconds * 1000L,
                        toolInstanceId,
                        1.0D
                );

        TIMED_EFFECTS.computeIfAbsent(
                player.getUUID(),
                id -> new HashMap<>()
        ).put(
                normalize(effectId),
                effect
        );
    }

    public static void activateTimedWithMultiplier(
            ServerPlayer player,
            String effectId,
            String displayName,
            int seconds,
            ItemStack stack,
            double multiplier
    ) {

        int safeSeconds =
                Math.max(
                        1,
                        seconds
                );

        String toolInstanceId =
                getOrCreateToolInstanceId(
                        stack
                );

        if (toolInstanceId == null) {
            return;
        }

        TimedEffect effect =
                new TimedEffect(
                        normalize(effectId),
                        displayName,
                        System.currentTimeMillis() + safeSeconds * 1000L,
                        toolInstanceId,
                        Math.max(
                                1.0D,
                                multiplier
                        )
                );

        TIMED_EFFECTS.computeIfAbsent(
                player.getUUID(),
                id -> new HashMap<>()
        ).put(
                normalize(effectId),
                effect
        );
    }

    public static double getMiningPassiveChanceMultiplier(
            ServerPlayer player,
            ItemStack stack
    ) {

        TimedEffect effect =
                getTimedEffect(
                        player,
                        "miners_focus"
                );

        if (effect == null) {
            return 1.0D;
        }

        if (System.currentTimeMillis() > effect.expiresAt) {
            removeTimedEffect(
                    player,
                    "miners_focus"
            );
            return 1.0D;
        }

        if (!effect.matchesTool(stack)) {
            return 1.0D;
        }

        return Math.max(
                1.0D,
                effect.multiplier
        );
    }


    public static double getForestryPassiveChanceMultiplier(
            ServerPlayer player,
            ItemStack stack
    ) {

        TimedEffect effect =
                getTimedEffect(
                        player,
                        "forestry_focus"
                );

        if (effect == null) {
            return 1.0D;
        }

        if (System.currentTimeMillis() > effect.expiresAt) {
            removeTimedEffect(
                    player,
                    "forestry_focus"
            );
            return 1.0D;
        }

        if (!effect.matchesTool(stack)) {
            return 1.0D;
        }

        return Math.max(
                1.0D,
                effect.multiplier
        );
    }

    public static boolean hasTimberBurst(
            ServerPlayer player,
            ItemStack stack
    ) {

        return hasTimedEffect(
                player,
                "timber_burst",
                stack
        );
    }

    public static boolean hasLeafstorm(
            ServerPlayer player,
            ItemStack stack
    ) {

        return hasTimedEffect(
                player,
                "leafstorm",
                stack
        );
    }

    public static boolean hasForestryReplant(
            ServerPlayer player,
            ItemStack stack
    ) {

        return hasToggle(
                player,
                "forestry_replant",
                stack
        );
    }

    public static void activateTimed(
            ServerPlayer player,
            String effectId,
            String displayName,
            int seconds
    ) {

        activateTimed(
                player,
                effectId,
                displayName,
                seconds,
                player.getMainHandItem()
        );
    }

    public static void activateExcavation(
            ServerPlayer player,
            int seconds,
            ItemStack stack
    ) {

        activateTimed(
                player,
                "excavation",
                "Excavation",
                seconds,
                stack
        );
    }

    public static void activateExcavation(
            ServerPlayer player,
            int seconds
    ) {

        activateExcavation(
                player,
                seconds,
                player.getMainHandItem()
        );
    }

    public static boolean hasExcavation(
            ServerPlayer player,
            ItemStack stack
    ) {

        return hasTimedEffect(
                player,
                "excavation",
                stack
        );
    }

    public static boolean hasExcavation(
            ServerPlayer player
    ) {

        return hasExcavation(
                player,
                player.getMainHandItem()
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
            String effectId,
            ItemStack stack
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

        return effect.matchesTool(
                stack
        );
    }

    public static boolean hasTimedEffect(
            ServerPlayer player,
            String effectId
    ) {

        return hasTimedEffect(
                player,
                effectId,
                player.getMainHandItem()
        );
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
            String displayName,
            ItemStack stack
    ) {

        String normalized =
                normalize(effectId);

        String toolInstanceId =
                getOrCreateToolInstanceId(
                        stack
                );

        if (toolInstanceId == null) {
            return false;
        }

        Map<String, ToggleEffect> effects =
                TOGGLED_EFFECTS.computeIfAbsent(
                        player.getUUID(),
                        id -> new HashMap<>()
                );

        ToggleEffect existing =
                effects.get(
                        normalized
                );

        boolean currentlyEnabled =
                ProfessionToolMetadata.getActiveToggle(
                        stack,
                        normalized
                ) || (
                        existing != null &&
                                existing.matchesTool(
                                        stack
                                )
                );

        boolean nowEnabled =
                !currentlyEnabled;

        if (nowEnabled) {
            effects.put(
                    normalized,
                    new ToggleEffect(
                            normalized,
                            displayName,
                            toolInstanceId
                    )
            );
        } else {
            effects.remove(
                    normalized
            );
        }

        ProfessionToolMetadata.setActiveToggle(
                stack,
                normalized,
                nowEnabled
        );

        sendToggleMessage(
                player,
                displayName,
                nowEnabled
        );

        return nowEnabled;
    }

    public static boolean toggleEffect(
            ServerPlayer player,
            String effectId,
            String displayName
    ) {

        return toggleEffect(
                player,
                effectId,
                displayName,
                player.getMainHandItem()
        );
    }

    public static boolean hasToggle(
            ServerPlayer player,
            String effectId,
            ItemStack stack
    ) {

        String normalized =
                normalize(effectId);

        Map<String, ToggleEffect> effects =
                TOGGLED_EFFECTS.computeIfAbsent(
                        player.getUUID(),
                        id -> new HashMap<>()
                );

        ToggleEffect effect =
                effects.get(
                        normalized
                );

        if (
                effect != null &&
                        effect.matchesTool(
                                stack
                        )
        ) {
            return true;
        }

        /*
         * Safety resync:
         * Toggle state is also stored directly on the tool instance.
         * If the server-side map ever misses/desyncs, the held tool can
         * restore the runtime toggle without leaking to a different tool.
         */
        if (
                ProfessionToolMetadata.getActiveToggle(
                        stack,
                        normalized
                )
        ) {
            String toolInstanceId =
                    getOrCreateToolInstanceId(
                            stack
                    );

            if (toolInstanceId == null) {
                return false;
            }

            effects.put(
                    normalized,
                    new ToggleEffect(
                            normalized,
                            normalized,
                            toolInstanceId
                    )
            );

            return true;
        }

        return false;
    }

    public static boolean hasToggle(
            ServerPlayer player,
            String effectId
    ) {

        return hasToggle(
                player,
                effectId,
                player.getMainHandItem()
        );
    }

    public static boolean hasAutoSmelt(
            ServerPlayer player,
            ItemStack stack
    ) {

        return hasTimedEffect(
                player,
                "auto_smelt",
                stack
        ) || hasToggle(
                player,
                "auto_smelt",
                stack
        );
    }

    public static boolean hasAutoSmelt(
            ServerPlayer player
    ) {

        return hasAutoSmelt(
                player,
                player.getMainHandItem()
        );
    }

    public static void clearActiveEffectsForTool(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }

        String toolInstanceId = ProfessionToolMetadata.getActiveInstanceId(stack);
        if (toolInstanceId == null) {
            ProfessionToolMetadata.clearActiveToggles(stack);
            return;
        }

        UUID playerId = player.getUUID();
        Map<String, TimedEffect> timed = TIMED_EFFECTS.get(playerId);
        if (timed != null) {
            timed.entrySet().removeIf(entry -> entry.getValue().toolInstanceId.equals(toolInstanceId));
            if (timed.isEmpty()) TIMED_EFFECTS.remove(playerId);
        }

        Map<String, ToggleEffect> toggles = TOGGLED_EFFECTS.get(playerId);
        if (toggles != null) {
            toggles.entrySet().removeIf(entry -> entry.getValue().toolInstanceId.equals(toolInstanceId));
            if (toggles.isEmpty()) TOGGLED_EFFECTS.remove(playerId);
        }

        ProfessionToolMetadata.clearActiveToggles(stack);
    }

    public static void clearAllActiveEffects(
            ServerPlayer player
    ) {
        if (player == null) return;
        TIMED_EFFECTS.remove(player.getUUID());
        TOGGLED_EFFECTS.remove(player.getUUID());
    }

    private static void cleanupInvalidHeldToggles(
            MinecraftServer server
    ) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ItemStack held = player.getMainHandItem();
            if (held == null || held.isEmpty() || !ProfessionToolMetadata.isProfessionTool(held) || ProfessionToolMetadata.isBroken(held)) {
                clearAllActiveEffects(player);
                if (held != null && !held.isEmpty()) ProfessionToolMetadata.clearActiveToggles(held);
            }
        }
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
            cleanupInvalidHeldToggles(server);
            tickMagnetToggles(server);
        }
    }

    private static void tickMagnetToggles(
            MinecraftServer server
    ) {

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {

            if (!hasToggle(
                    player,
                    "ore_magnet",
                    player.getMainHandItem()
            )) {
                continue;
            }

            double radius =
                    Math.max(
                            4.0D,
                            Math.min(
                                    24.0D,
                                    getHeldStat(
                                            player,
                                            "oreMagnetRadius",
                                            8.0D
                                    )
                            )
                    );

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


    private static double getHeldStat(
            ServerPlayer player,
            String stat,
            double fallback
    ) {

        double value = com.champutils.profession.ProfessionToolUtil.getStat(
                player.getMainHandItem(),
                stat
        );

        return value <= 0.0D ? fallback : value;
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
            ProfessionNotificationSettings.playSound(player, 
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
                ProfessionNotificationSettings.playSound(player, 
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

        ProfessionNotificationSettings.playSound(player, 
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

        ProfessionNotificationSettings.playSound(player, 
                enabled ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.PLAYERS,
                0.75F,
                enabled ? 1.4F : 0.7F
        );
    }

    private static String getOrCreateToolInstanceId(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(
                                stack
                        )
        ) {
            return null;
        }

        return ProfessionToolMetadata.getOrCreateActiveInstanceId(
                stack
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
        private final String toolInstanceId;
        private final double multiplier;
        private boolean warned10 = false;
        private final Set<Long> warnedFinalSeconds =
                new HashSet<>();

        private TimedEffect(
                String effectId,
                String displayName,
                long expiresAt,
                String toolInstanceId,
                double multiplier
        ) {

            this.effectId = effectId;
            this.displayName = displayName;
            this.expiresAt = expiresAt;
            this.toolInstanceId = toolInstanceId;
            this.multiplier = multiplier;
        }

        private boolean matchesTool(
                ItemStack stack
        ) {

            return ProfessionToolMetadata.matchesActiveInstanceId(
                    stack,
                    toolInstanceId
            );
        }
    }

    private static class ToggleEffect {

        private final String effectId;
        private final String displayName;
        private final String toolInstanceId;

        private ToggleEffect(
                String effectId,
                String displayName,
                String toolInstanceId
        ) {

            this.effectId = effectId;
            this.displayName = displayName;
            this.toolInstanceId = toolInstanceId;
        }

        private boolean matchesTool(
                ItemStack stack
        ) {

            return ProfessionToolMetadata.matchesActiveInstanceId(
                    stack,
                    toolInstanceId
            );
        }
    }
}
