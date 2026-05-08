package com.champutils.profession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionToolMetadata {

    private static final String ROOT_KEY = "ChampUtilsProfessionTool";

    private static final String TOOL_ID_KEY = "ToolId";
    private static final String IDENTIFIED_KEY = "Identified";
    private static final String REROLLS_KEY = "Rerolls";
    private static final String QUALITY_KEY = "Quality";
    private static final String ROLLED_STATS_KEY = "RolledStats";

    private ProfessionToolMetadata() {
    }

    public static boolean isProfessionTool(
            ItemStack stack
    ) {

        return getRoot(stack).contains(TOOL_ID_KEY);
    }

    public static String getToolId(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        if (!root.contains(TOOL_ID_KEY)) {
            return null;
        }

        return root.getString(TOOL_ID_KEY);
    }

    public static void setToolId(
            ItemStack stack,
            String toolId
    ) {

        updateRoot(
                stack,
                root -> root.putString(
                        TOOL_ID_KEY,
                        toolId
                )
        );
    }

    public static boolean isIdentified(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return root.getBoolean(IDENTIFIED_KEY);
    }

    public static void setIdentified(
            ItemStack stack,
            boolean identified
    ) {

        updateRoot(
                stack,
                root -> root.putBoolean(
                        IDENTIFIED_KEY,
                        identified
                )
        );
    }

    public static int getRerolls(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return Math.max(
                0,
                root.getInt(REROLLS_KEY)
        );
    }

    public static void setRerolls(
            ItemStack stack,
            int rerolls
    ) {

        updateRoot(
                stack,
                root -> root.putInt(
                        REROLLS_KEY,
                        Math.max(
                                0,
                                rerolls
                        )
                )
        );
    }

    public static void incrementRerolls(
            ItemStack stack
    ) {

        setRerolls(
                stack,
                getRerolls(stack) + 1
        );
    }

    public static double getQuality(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return root.getDouble(QUALITY_KEY);
    }

    public static void setQuality(
            ItemStack stack,
            double quality
    ) {

        double clamped =
                Math.max(
                        0.0D,
                        Math.min(
                                100.0D,
                                quality
                        )
                );

        updateRoot(
                stack,
                root -> root.putDouble(
                        QUALITY_KEY,
                        clamped
                )
        );
    }

    public static Map<String, Double> getRolledStats(
            ItemStack stack
    ) {

        Map<String, Double> stats =
                new LinkedHashMap<>();

        CompoundTag root =
                getRoot(stack);

        if (!root.contains(ROLLED_STATS_KEY)) {
            return stats;
        }

        CompoundTag rolledStats =
                root.getCompound(ROLLED_STATS_KEY);

        for (String key : rolledStats.getAllKeys()) {
            stats.put(
                    key,
                    rolledStats.getDouble(key)
            );
        }

        return stats;
    }

    public static void setRolledStats(
            ItemStack stack,
            Map<String, Double> stats
    ) {

        CompoundTag rolledStats =
                new CompoundTag();

        if (stats != null) {
            for (Map.Entry<String, Double> entry : stats.entrySet()) {

                if (
                        entry.getKey() == null ||
                                entry.getKey().isBlank() ||
                                entry.getValue() == null
                ) {
                    continue;
                }

                rolledStats.putDouble(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }

        updateRoot(
                stack,
                root -> root.put(
                        ROLLED_STATS_KEY,
                        rolledStats
                )
        );
    }

    public static void clearRolledStats(
            ItemStack stack
    ) {

        updateRoot(
                stack,
                root -> root.remove(
                        ROLLED_STATS_KEY
                )
        );
    }

    public static void initializeUnidentifiedTool(
            ItemStack stack,
            String toolId
    ) {

        updateRoot(
                stack,
                root -> {
                    root.putString(
                            TOOL_ID_KEY,
                            toolId
                    );

                    root.putBoolean(
                            IDENTIFIED_KEY,
                            false
                    );

                    root.putInt(
                            REROLLS_KEY,
                            0
                    );

                    root.putDouble(
                            QUALITY_KEY,
                            0.0D
                    );

                    root.remove(
                            ROLLED_STATS_KEY
                    );
                }
        );
    }

    public static void applyRoll(
            ItemStack stack,
            Map<String, Double> rolledStats,
            double quality,
            boolean incrementReroll
    ) {

        updateRoot(
                stack,
                root -> {
                    CompoundTag statsTag =
                            new CompoundTag();

                    if (rolledStats != null) {
                        for (Map.Entry<String, Double> entry : rolledStats.entrySet()) {

                            if (
                                    entry.getKey() == null ||
                                            entry.getKey().isBlank() ||
                                            entry.getValue() == null
                            ) {
                                continue;
                            }

                            statsTag.putDouble(
                                    entry.getKey(),
                                    entry.getValue()
                            );
                        }
                    }

                    root.putBoolean(
                            IDENTIFIED_KEY,
                            true
                    );

                    root.put(
                            ROLLED_STATS_KEY,
                            statsTag
                    );

                    root.putDouble(
                            QUALITY_KEY,
                            Math.max(
                                    0.0D,
                                    Math.min(
                                            100.0D,
                                            quality
                                    )
                            )
                    );

                    if (incrementReroll) {
                        root.putInt(
                                REROLLS_KEY,
                                Math.max(
                                        0,
                                        root.getInt(REROLLS_KEY)
                                ) + 1
                        );
                    }
                }
        );
    }

    private static CompoundTag getRoot(
            ItemStack stack
    ) {

        if (stack == null || stack.isEmpty()) {
            return new CompoundTag();
        }

        CustomData customData =
                stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY
                );

        CompoundTag tag =
                customData.copyTag();

        if (!tag.contains(ROOT_KEY)) {
            return new CompoundTag();
        }

        return tag.getCompound(ROOT_KEY);
    }

    private static void updateRoot(
            ItemStack stack,
            RootEditor editor
    ) {

        if (stack == null || stack.isEmpty()) {
            return;
        }

        CustomData customData =
                stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY
                );

        CompoundTag tag =
                customData.copyTag();

        CompoundTag root =
                tag.contains(ROOT_KEY)
                        ? tag.getCompound(ROOT_KEY)
                        : new CompoundTag();

        editor.edit(root);

        tag.put(
                ROOT_KEY,
                root
        );

        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(tag)
        );
    }

    private interface RootEditor {

        void edit(
                CompoundTag root
        );
    }
}