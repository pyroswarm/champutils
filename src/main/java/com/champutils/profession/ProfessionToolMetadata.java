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
    private static final String ASCENDED_KEY = "Ascended";
    private static final String REROLLS_KEY = "Rerolls";
    private static final String QUALITY_KEY = "Quality";
    private static final String ROLLED_STATS_KEY = "RolledStats";
    private static final String TRACKERS_KEY = "Trackers";
    private static final String SELECTED_TRACKER_KEY = "SelectedTracker";
    private static final String DISCOVERY_ANNOUNCEMENT_ELIGIBLE_KEY = "DiscoveryAnnouncementEligible";
    private static final String DISCOVERY_ANNOUNCED_KEY = "DiscoveryAnnounced";
    private static final String PERFECT_ROLL_ANNOUNCED_KEY = "PerfectRollAnnounced";

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

    public static boolean isAscended(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return root.getBoolean(ASCENDED_KEY);
    }

    public static void setAscended(
            ItemStack stack,
            boolean ascended
    ) {

        updateRoot(
                stack,
                root -> root.putBoolean(
                        ASCENDED_KEY,
                        ascended
                )
        );
    }

    public static boolean isDiscoveryAnnouncementEligible(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return root.getBoolean(DISCOVERY_ANNOUNCEMENT_ELIGIBLE_KEY);
    }

    public static void setDiscoveryAnnouncementEligible(
            ItemStack stack,
            boolean eligible
    ) {

        updateRoot(
                stack,
                root -> root.putBoolean(
                        DISCOVERY_ANNOUNCEMENT_ELIGIBLE_KEY,
                        eligible
                )
        );
    }

    public static boolean isDiscoveryAnnounced(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return root.getBoolean(DISCOVERY_ANNOUNCED_KEY);
    }

    public static void setDiscoveryAnnounced(
            ItemStack stack,
            boolean announced
    ) {

        updateRoot(
                stack,
                root -> root.putBoolean(
                        DISCOVERY_ANNOUNCED_KEY,
                        announced
                )
        );
    }

    public static boolean isPerfectRollAnnounced(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        return root.getBoolean(PERFECT_ROLL_ANNOUNCED_KEY);
    }

    public static void setPerfectRollAnnounced(
            ItemStack stack,
            boolean announced
    ) {

        updateRoot(
                stack,
                root -> root.putBoolean(
                        PERFECT_ROLL_ANNOUNCED_KEY,
                        announced
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


    public static String getSelectedTracker(
            ItemStack stack
    ) {

        CompoundTag root =
                getRoot(stack);

        if (!root.contains(SELECTED_TRACKER_KEY)) {
            return null;
        }

        String trackerId =
                root.getString(SELECTED_TRACKER_KEY);

        return trackerId == null || trackerId.isBlank()
                ? null
                : trackerId;
    }

    public static void setSelectedTracker(
            ItemStack stack,
            String trackerId
    ) {

        updateRoot(
                stack,
                root -> {
                    if (trackerId == null || trackerId.isBlank()) {
                        root.remove(SELECTED_TRACKER_KEY);
                    } else {
                        root.putString(
                                SELECTED_TRACKER_KEY,
                                trackerId
                        );
                    }
                }
        );
    }

    public static void setTracker(
            ItemStack stack,
            String trackerId,
            long value
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        trackerId == null ||
                        trackerId.isBlank()
        ) {
            return;
        }

        updateRoot(
                stack,
                root -> {
                    CompoundTag trackerTag =
                            root.contains(TRACKERS_KEY)
                                    ? root.getCompound(TRACKERS_KEY)
                                    : new CompoundTag();

                    trackerTag.putLong(
                            trackerId,
                            Math.max(
                                    0L,
                                    value
                            )
                    );

                    root.put(
                            TRACKERS_KEY,
                            trackerTag
                    );
                }
        );
    }

    public static Map<String, Long> getTrackers(
            ItemStack stack
    ) {

        Map<String, Long> trackers =
                new LinkedHashMap<>();

        CompoundTag root =
                getRoot(stack);

        if (!root.contains(TRACKERS_KEY)) {
            return trackers;
        }

        CompoundTag trackerTag =
                root.getCompound(TRACKERS_KEY);

        for (String key : trackerTag.getAllKeys()) {
            trackers.put(
                    key,
                    trackerTag.getLong(key)
            );
        }

        return trackers;
    }

    public static long getTracker(
            ItemStack stack,
            String trackerId
    ) {

        if (
                trackerId == null ||
                        trackerId.isBlank()
        ) {
            return 0L;
        }

        CompoundTag root =
                getRoot(stack);

        if (!root.contains(TRACKERS_KEY)) {
            return 0L;
        }

        CompoundTag trackerTag =
                root.getCompound(TRACKERS_KEY);

        return Math.max(
                0L,
                trackerTag.getLong(trackerId)
        );
    }

    public static void incrementTracker(
            ItemStack stack,
            String trackerId,
            long amount
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        trackerId == null ||
                        trackerId.isBlank() ||
                        amount <= 0
        ) {
            return;
        }

        updateRoot(
                stack,
                root -> {
                    CompoundTag trackerTag =
                            root.contains(TRACKERS_KEY)
                                    ? root.getCompound(TRACKERS_KEY)
                                    : new CompoundTag();

                    long current =
                            Math.max(
                                    0L,
                                    trackerTag.getLong(trackerId)
                            );

                    trackerTag.putLong(
                            trackerId,
                            current + amount
                    );

                    root.put(
                            TRACKERS_KEY,
                            trackerTag
                    );
                }
        );
    }

    public static void initializeUnidentifiedTool(
            ItemStack stack,
            String toolId
    ) {

        initializeUnidentifiedTool(
                stack,
                toolId,
                false
        );
    }

    public static void initializeUnidentifiedTool(
            ItemStack stack,
            String toolId,
            boolean ascended
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

                    root.putBoolean(
                            ASCENDED_KEY,
                            ascended
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

                    root.remove(
                            TRACKERS_KEY
                    );

                    root.remove(
                            SELECTED_TRACKER_KEY
                    );

                    root.putBoolean(
                            DISCOVERY_ANNOUNCEMENT_ELIGIBLE_KEY,
                            false
                    );

                    root.putBoolean(
                            DISCOVERY_ANNOUNCED_KEY,
                            false
                    );

                    root.putBoolean(
                            PERFECT_ROLL_ANNOUNCED_KEY,
                            false
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
