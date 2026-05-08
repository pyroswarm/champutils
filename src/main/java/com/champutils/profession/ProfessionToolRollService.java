package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class ProfessionToolRollService {

    private static final Random RANDOM =
            new Random();

    private ProfessionToolRollService() {
    }

    public static RollResult identify(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return RollResult.fail(
                    "You must hold a profession tool."
            );
        }

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (
                toolId == null ||
                        toolId.isBlank()
        ) {
            return RollResult.fail(
                    "This is not a profession tool."
            );
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return RollResult.fail(
                    "Unknown profession tool: " +
                            toolId
            );
        }

        if (
                ProfessionToolMetadata.isIdentified(
                        stack
                )
        ) {
            return RollResult.fail(
                    "This item is already identified. Use reroll instead."
            );
        }

        Map<String, Double> rolledStats =
                rollStats(
                        toolData
                );

        double quality =
                calculateQuality(
                        toolData,
                        rolledStats
                );

        ProfessionToolMetadata.applyRoll(
                stack,
                rolledStats,
                quality,
                false
        );

        assignAscendedTrackerOnIdentify(
                stack,
                toolData
        );

        return RollResult.success(
                toolId,
                toolData,
                rolledStats,
                quality,
                ProfessionToolMetadata.getRerolls(stack),
                "Identified " +
                        ProfessionToolConfig.getDisplayName(
                                toolId,
                                toolData
                        ) +
                        "!"
        );
    }

    public static RollResult reroll(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return RollResult.fail(
                    "You must hold a profession tool."
            );
        }

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (
                toolId == null ||
                        toolId.isBlank()
        ) {
            return RollResult.fail(
                    "This is not a profession tool."
            );
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return RollResult.fail(
                    "Unknown profession tool: " +
                            toolId
            );
        }

        if (
                !ProfessionToolMetadata.isIdentified(
                        stack
                )
        ) {
            return RollResult.fail(
                    "This item must be identified before it can be rerolled."
            );
        }

        Map<String, Double> rolledStats =
                rollStats(
                        toolData
                );

        double quality =
                calculateQuality(
                        toolData,
                        rolledStats
                );

        ProfessionToolMetadata.applyRoll(
                stack,
                rolledStats,
                quality,
                true
        );

        return RollResult.success(
                toolId,
                toolData,
                rolledStats,
                quality,
                ProfessionToolMetadata.getRerolls(stack),
                "Rerolled " +
                        ProfessionToolConfig.getDisplayName(
                                toolId,
                                toolData
                        ) +
                        "!"
        );
    }

    public static long getIdentifyCost(
            ItemStack stack
    ) {

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (
                toolId == null ||
                        toolId.isBlank()
        ) {
            return 0L;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        return ProfessionToolConfig.getBaseRollCost(
                toolData
        );
    }

    public static long getRerollCost(
            ItemStack stack
    ) {

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (
                toolId == null ||
                        toolId.isBlank()
        ) {
            return 0L;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        int rerolls =
                ProfessionToolMetadata.getRerolls(
                        stack
                );

        return ProfessionToolConfig.getRerollCost(
                toolData,
                rerolls
        );
    }

    public static Map<String, Double> rollStats(
            ProfessionToolConfig.ToolData toolData
    ) {

        Map<String, Double> rolledStats =
                new LinkedHashMap<>();

        if (
                toolData == null ||
                        toolData.statRanges == null ||
                        toolData.statRanges.isEmpty()
        ) {
            return rolledStats;
        }

        for (
                Map.Entry<String, ProfessionToolConfig.StatRange> entry :
                toolData.statRanges.entrySet()
        ) {

            String statId =
                    entry.getKey();

            ProfessionToolConfig.StatRange range =
                    entry.getValue();

            if (
                    statId == null ||
                            statId.isBlank() ||
                            range == null
            ) {
                continue;
            }

            double min =
                    Math.min(
                            range.min,
                            range.max
                    );

            double max =
                    Math.max(
                            range.min,
                            range.max
                    );

            double value =
                    min +
                            RANDOM.nextDouble() *
                                    (max - min);

            rolledStats.put(
                    statId,
                    roundOneDecimal(
                            value
                    )
            );
        }

        return rolledStats;
    }

    public static double calculateQuality(
            ProfessionToolConfig.ToolData toolData,
            Map<String, Double> rolledStats
    ) {

        if (
                toolData == null ||
                        toolData.statRanges == null ||
                        toolData.statRanges.isEmpty() ||
                        rolledStats == null ||
                        rolledStats.isEmpty()
        ) {
            return 0.0D;
        }

        double weightedTotal =
                0.0D;

        double totalWeight =
                0.0D;

        for (
                Map.Entry<String, ProfessionToolConfig.StatRange> entry :
                toolData.statRanges.entrySet()
        ) {

            String statId =
                    entry.getKey();

            ProfessionToolConfig.StatRange range =
                    entry.getValue();

            Double rolledValue =
                    rolledStats.get(
                            statId
                    );

            if (
                    range == null ||
                            rolledValue == null
            ) {
                continue;
            }

            double min =
                    Math.min(
                            range.min,
                            range.max
                    );

            double max =
                    Math.max(
                            range.min,
                            range.max
                    );

            if (max <= min) {
                continue;
            }

            double weight =
                    range.weight <= 0
                            ? 1.0D
                            : range.weight;

            double statPercent =
                    (rolledValue - min) /
                            (max - min);

            statPercent =
                    Math.max(
                            0.0D,
                            Math.min(
                                    1.0D,
                                    statPercent
                            )
                    );

            weightedTotal +=
                    statPercent *
                            weight;

            totalWeight +=
                    weight;
        }

        if (totalWeight <= 0) {
            return 0.0D;
        }

        return roundOneDecimal(
                (weightedTotal / totalWeight) *
                        100.0D
        );
    }

    private static void assignAscendedTrackerOnIdentify(
            ItemStack stack,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        toolData == null ||
                        !ProfessionToolMetadata.isAscended(stack) ||
                        toolData.profession == null ||
                        !toolData.profession.equalsIgnoreCase("MINING")
        ) {
            return;
        }

        String trackerId =
                rollMiningTrackerId();

        ProfessionToolMetadata.setSelectedTracker(
                stack,
                trackerId
        );

        ProfessionToolMetadata.setTracker(
                stack,
                trackerId,
                0L
        );
    }

    private static String rollMiningTrackerId() {

        double roll =
                RANDOM.nextDouble() * 100.0D;

        if (roll >= 90.0D) {
            return "ancient_debris_mined";
        }

        if (roll >= 70.0D) {
            return "diamonds_mined";
        }

        String[] commonTrackers =
                new String[]{
                        "stone_mined",
                        "coal_mined",
                        "copper_mined",
                        "iron_mined",
                        "gold_mined",
                        "redstone_mined",
                        "lapis_mined",
                        "emerald_mined"
                };

        return commonTrackers[
                RANDOM.nextInt(
                        commonTrackers.length
                )
        ];
    }

    public static Component buildSuccessMessage(
            RollResult result
    ) {

        if (
                result == null ||
                        !result.success
        ) {
            return Component.literal(
                    "§cRoll failed."
            );
        }

        return Component.literal(
                "§a" +
                        result.message +
                        " §7Quality: §e" +
                        result.quality +
                        "%"
        );
    }

    private static double roundOneDecimal(
            double value
    ) {

        return Math.round(
                value * 10.0D
        ) / 10.0D;
    }

    public static final class RollResult {

        public final boolean success;
        public final String error;

        public final String toolId;
        public final ProfessionToolConfig.ToolData toolData;
        public final Map<String, Double> rolledStats;
        public final double quality;
        public final int rerolls;
        public final String message;

        private RollResult(
                boolean success,
                String error,
                String toolId,
                ProfessionToolConfig.ToolData toolData,
                Map<String, Double> rolledStats,
                double quality,
                int rerolls,
                String message
        ) {

            this.success =
                    success;

            this.error =
                    error;

            this.toolId =
                    toolId;

            this.toolData =
                    toolData;

            this.rolledStats =
                    rolledStats;

            this.quality =
                    quality;

            this.rerolls =
                    rerolls;

            this.message =
                    message;
        }

        public static RollResult success(
                String toolId,
                ProfessionToolConfig.ToolData toolData,
                Map<String, Double> rolledStats,
                double quality,
                int rerolls,
                String message
        ) {

            return new RollResult(
                    true,
                    null,
                    toolId,
                    toolData,
                    rolledStats,
                    quality,
                    rerolls,
                    message
            );
        }

        public static RollResult fail(
                String error
        ) {

            return new RollResult(
                    false,
                    error,
                    null,
                    null,
                    new LinkedHashMap<>(),
                    0.0D,
                    0,
                    null
            );
        }
    }
}