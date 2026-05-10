package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ItemSafetyService {

    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final double HIGH_OVERALL_QUALITY = 80.0D;

    private static final Map<UUID, PendingConfirmation> PENDING =
            new HashMap<>();

    private ItemSafetyService() {
    }

    public static boolean needsRiskConfirmation(
            ItemStack stack
    ) {

        return getRiskReason(stack) != null;
    }

    public static String getRiskReason(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(stack) ||
                        !ProfessionToolMetadata.isIdentified(stack)
        ) {
            return null;
        }

        if (hasPerfectStat(stack)) {
            return "this item has at least one 100% stat";
        }

        double quality =
                ProfessionToolMetadata.getQuality(stack);

        if (quality >= HIGH_OVERALL_QUALITY) {
            return "this item has " +
                    (int) Math.floor(quality) +
                    "% overall quality";
        }

        return null;
    }

    public static boolean hasPerfectStat(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(stack)
        ) {
            return false;
        }

        String toolId =
                ProfessionToolMetadata.getToolId(stack);

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(toolId);

        if (
                toolData == null ||
                        toolData.statRanges == null ||
                        toolData.statRanges.isEmpty()
        ) {
            return false;
        }

        Map<String, Double> rolledStats =
                ProfessionToolMetadata.getRolledStats(stack);

        if (
                rolledStats == null ||
                        rolledStats.isEmpty()
        ) {
            return false;
        }

        for (Map.Entry<String, Double> entry : rolledStats.entrySet()) {
            if (
                    getStatQualityPercent(
                            toolData,
                            entry.getKey(),
                            entry.getValue()
                    ) >= 100.0D
            ) {
                return true;
            }
        }

        return false;
    }


    public static boolean blockIfLocked(
            ServerPlayer player,
            ItemStack stack,
            String actionName
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(stack) ||
                        !ProfessionToolMetadata.isLocked(stack)
        ) {
            return false;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§cThis item is locked. Use §f/itemlock §cto unlock it before you " +
                                actionName +
                                "."
                )
        );

        return true;
    }

    public static boolean requestConfirmationIfNeeded(
            ServerPlayer player,
            ItemStack stack,
            String actionId,
            String confirmCommand
    ) {

        String reason =
                getRiskReason(stack);

        if (reason == null) {
            clear(player, actionId);
            return false;
        }

        PendingConfirmation existing =
                PENDING.get(player.getUUID());

        String fingerprint =
                fingerprint(stack);

        long now =
                System.currentTimeMillis();

        if (
                existing != null &&
                        existing.matches(actionId, fingerprint, now)
        ) {
            PENDING.remove(player.getUUID());
            return false;
        }

        PENDING.put(
                player.getUUID(),
                new PendingConfirmation(
                        actionId,
                        fingerprint,
                        now + CONFIRM_WINDOW_MILLIS
                )
        );

        player.sendSystemMessage(
                Component.literal(
                        "§6§lCareful! §eYou are about to " +
                                actionId +
                                " an important item because §f" +
                                reason +
                                "§e."
                )
        );

        player.sendSystemMessage(
                Component.literal(
                        "§7Use the same action again within 30 seconds to confirm."
                )
        );

        return true;
    }

    public static void clear(
            ServerPlayer player,
            String actionId
    ) {

        if (player == null) {
            return;
        }

        PendingConfirmation existing =
                PENDING.get(player.getUUID());

        if (
                existing != null &&
                        existing.actionId.equalsIgnoreCase(actionId)
        ) {
            PENDING.remove(player.getUUID());
        }
    }

    private static String fingerprint(
            ItemStack stack
    ) {

        if (stack == null || stack.isEmpty()) {
            return "empty";
        }

        CustomData customData =
                stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY
                );

        String itemName =
                stack.getItem().toString();

        return itemName +
                "|" +
                stack.getCount() +
                "|" +
                customData.copyTag();
    }

    private static double getStatQualityPercent(
            ProfessionToolConfig.ToolData toolData,
            String statId,
            double rolledValue
    ) {

        if (
                toolData == null ||
                        toolData.statRanges == null ||
                        statId == null
        ) {
            return 0.0D;
        }

        ProfessionToolConfig.StatRange range =
                toolData.statRanges.get(statId);

        if (range == null) {
            return 0.0D;
        }

        double min = Math.min(range.min, range.max);
        double max = Math.max(range.min, range.max);

        double displayedMin = Math.floor(min);
        double displayedMax = Math.floor(max);
        double displayedValue = Math.floor(rolledValue);

        if (displayedMax > displayedMin) {
            min = displayedMin;
            max = displayedMax;
            rolledValue = displayedValue;
        }

        if (max <= min) {
            return 100.0D;
        }

        double percent =
                ((rolledValue - min) / (max - min)) * 100.0D;

        return Math.max(
                0.0D,
                Math.min(
                        100.0D,
                        Math.floor(percent)
                )
        );
    }

    private record PendingConfirmation(
            String actionId,
            String fingerprint,
            long expiresAtMillis
    ) {

        boolean matches(
                String incomingActionId,
                String incomingFingerprint,
                long now
        ) {
            return now <= expiresAtMillis &&
                    actionId.equalsIgnoreCase(incomingActionId) &&
                    fingerprint.equals(incomingFingerprint);
        }
    }
}
