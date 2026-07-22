package com.champutils.profession;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

        String activeAbility =
                rollActiveAbility(
                        toolId,
                        toolData,
                        null
                );

        ProfessionToolMetadata.setActiveAbility(
                stack,
                activeAbility
        );

        ProfessionToolMetadata.applyRoll(
                stack,
                rolledStats,
                quality,
                false
        );

        rollAscendedOnIdentify(stack);

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
                activeAbility,
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

        String previousActiveAbility =
                ProfessionToolMetadata.getResolvedActiveAbility(
                        stack,
                        toolData
                );

        String activeAbility =
                rollActiveAbility(
                        toolId,
                        toolData,
                        previousActiveAbility
                );

        ProfessionToolMetadata.setActiveAbility(
                stack,
                activeAbility
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
                activeAbility,
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

        if (toolId == null || toolId.isBlank()) {
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

        return ProfessionToolConfig.getRerollCost(toolData, rerolls);
    }

    public static int getRerollFragmentCost(
            ItemStack stack
    ) {

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (toolId == null || toolId.isBlank()) {
            return 0;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        int rerolls =
                ProfessionToolMetadata.getRerolls(
                        stack
                );

        return getRerollFragmentCost(toolData, rerolls);
    }

    public static int getRerollFragmentCost(
            ProfessionToolConfig.ToolData toolData,
            int rerolls
    ) {

        if (toolData == null || toolData.rarity == null || toolData.rarity.isBlank()) {
            return 0;
        }

        int safeRerolls =
                Math.max(0, rerolls);

        if (safeRerolls >= 30) {
            return 1_073_741_824;
        }

        return Math.max(1, 1 << safeRerolls);
    }

    public static String getRerollFragmentKey(
            ItemStack stack
    ) {

        String toolId = ProfessionToolMetadata.getToolId(stack);
        ProfessionToolConfig.ToolData toolData = toolId == null ? null : ProfessionToolConfig.TOOLS.get(toolId);
        if (toolData == null || toolData.rarity == null || toolData.rarity.isBlank()) {
            return null;
        }
        return ProfessionFragmentConfig.normalizeRarity(toolData.rarity);
    }

    public static String rollActiveAbility(
            String toolId,
            ProfessionToolConfig.ToolData toolData,
            String previousAbility
    ) {

        List<String> pool =
                ProfessionToolConfig.getActiveAbilityPool(
                        toolId,
                        toolData
                );

        if (pool.isEmpty()) {
            return null;
        }

        if (pool.size() == 1) {
            return pool.get(0);
        }

        String previous = previousAbility == null ? null : previousAbility.trim().toLowerCase(Locale.ROOT);
        String selected = pool.get(RANDOM.nextInt(pool.size()));

        for (int attempt = 0; attempt < 8 && previous != null && previous.equals(selected); attempt++) {
            selected = pool.get(RANDOM.nextInt(pool.size()));
        }

        return selected;
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

            // Roll uniformly across the configured numeric range. The previous whole-number
            // flooring collapsed narrow ranges (for example 0.0-1.0) into only two outcomes and
            // badly distorted both average power and displayed quality.
            double value;
            if ("efficiencyLevel".equals(statId)) {
                // Efficiency is a discrete enchantment level. Never display misleading partial rolls.
                value = min == max ? min : (RANDOM.nextBoolean() ? min : max);
            } else {
                value = min == max ? min : min + (RANDOM.nextDouble() * (max - min));
                value = Math.round(value * 100.0D) / 100.0D;
            }

            rolledStats.put(
                    statId,
                    value
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


    private static void rollAscendedOnIdentify(ItemStack stack) {
        if (stack == null || stack.isEmpty() || ProfessionToolMetadata.isAscended(stack)) return;
        if (RANDOM.nextDouble() < 0.01D) {
            ProfessionToolMetadata.setAscended(stack, true);
            ProfessionToolMetadata.setDiscoveryAnnouncementEligible(stack, true);
        }
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

    public static String rollMiningTrackerId() {

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
                        (int) Math.floor(result.quality) +
                        "%" +
                        (result.activeAbility == null || result.activeAbility.isBlank()
                                ? ""
                                : " §7Active: §b" + formatWords(result.activeAbility))
        );
    }

    private static String formatWords(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.replace("_", " ").replace("-", " ").trim().toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) builder.append(part.substring(1));
            builder.append(" ");
        }
        return builder.toString().trim().replaceAll("\\bXp\\b", "XP");
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
        public final String activeAbility;
        public final String message;

        private RollResult(
                boolean success,
                String error,
                String toolId,
                ProfessionToolConfig.ToolData toolData,
                Map<String, Double> rolledStats,
                double quality,
                int rerolls,
                String activeAbility,
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

            this.activeAbility =
                    activeAbility;

            this.message =
                    message;
        }

        public static RollResult success(
                String toolId,
                ProfessionToolConfig.ToolData toolData,
                Map<String, Double> rolledStats,
                double quality,
                int rerolls,
                String activeAbility,
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
                    activeAbility,
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
                    null,
                    null
            );
        }
    }
}
