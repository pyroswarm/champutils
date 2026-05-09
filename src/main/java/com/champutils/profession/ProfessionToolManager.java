package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfessionToolManager {

    private static final Map<String, Item> REGISTERED_TOOLS =
            new HashMap<>();

    public static void registerTools() {

        REGISTERED_TOOLS.clear();

        for (
                Map.Entry<String, ProfessionToolConfig.ToolData> entry :
                ProfessionToolConfig.TOOLS.entrySet()
        ) {
            registerTool(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        System.out.println(
                "[ChampUtils] Registered " +
                        REGISTERED_TOOLS.size() +
                        " profession tools."
        );
    }

    private static void registerTool(
            String toolId,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData == null ||
                        toolData.baseItem == null ||
                        toolData.baseItem.isBlank()
        ) {
            System.out.println(
                    "[ChampUtils] Invalid config for tool: " +
                            toolId
            );
            return;
        }

        ResourceLocation resourceLocation;

        try {
            resourceLocation =
                    ResourceLocation.parse(
                            toolData.baseItem
                    );
        } catch (Exception e) {
            System.out.println(
                    "[ChampUtils] Invalid item id for tool: " +
                            toolId +
                            " -> " +
                            toolData.baseItem
            );
            return;
        }

        Item baseItem =
                BuiltInRegistries.ITEM.get(
                        resourceLocation
                );

        if (baseItem == Items.AIR) {
            System.out.println(
                    "[ChampUtils] Invalid base item for tool: " +
                            toolId +
                            " -> " +
                            toolData.baseItem
            );
            return;
        }

        Item customTool =
                createProperToolType(
                        toolData,
                        baseItem
                );

        if (customTool == null) {
            System.out.println(
                    "[ChampUtils] Failed to create tool: " +
                            toolId
            );
            return;
        }

        try {

            Registry.register(
                    BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(
                            "champutils",
                            toolId
                    ),
                    customTool
            );

            REGISTERED_TOOLS.put(
                    toolId,
                    customTool
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Item createProperToolType(
            ProfessionToolConfig.ToolData toolData,
            Item baseItem
    ) {

        String base =
                toolData.baseItem == null
                        ? ""
                        : toolData.baseItem.toLowerCase();

        int configuredDurability =
                getBaseMaxDurability(
                        toolData,
                        baseItem
                );

        Item.Properties properties =
                new Item.Properties()
                        .stacksTo(1)
                        .durability(
                                Math.max(
                                        1,
                                        configuredDurability
                                )
                        )
                        .rarity(
                                getRarity(
                                        toolData.rarity
                                )
                        );

        Tier configuredTier =
                getConfiguredTier(
                        toolData
                );

        if (base.contains("pickaxe")) {
            return new CustomPickaxeItem(
                    baseItem,
                    configuredTier,
                    properties
            );
        }

        if (base.contains("axe")) {
            return new CustomAxeItem(
                    baseItem,
                    configuredTier,
                    properties
            );
        }

        if (base.contains("hoe")) {
            return new CustomHoeItem(
                    baseItem,
                    configuredTier,
                    properties
            );
        }

        return new CustomGenericToolItem(
                baseItem,
                properties
        );
    }

    public static Tier getConfiguredTier(
            ProfessionToolConfig.ToolData toolData
    ) {

        String tierName =
                getConfiguredTierName(
                        toolData
                );

        return switch (
                tierName
        ) {
            case "WOOD" -> Tiers.WOOD;
            case "STONE" -> Tiers.STONE;
            case "IRON" -> Tiers.IRON;
            case "NETHERITE" -> Tiers.NETHERITE;
            case "DIAMOND" -> Tiers.DIAMOND;
            default -> Tiers.WOOD;
        };
    }

    public static String getConfiguredTierName(
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData != null &&
                        toolData.toolTier != null &&
                        !toolData.toolTier.isBlank()
        ) {
            return toolData.toolTier
                    .trim()
                    .toUpperCase();
        }

        String base =
                toolData == null || toolData.baseItem == null
                        ? ""
                        : toolData.baseItem.toLowerCase();

        if (base.contains("netherite")) {
            return "NETHERITE";
        }

        if (base.contains("diamond")) {
            return "DIAMOND";
        }

        if (base.contains("iron")) {
            return "IRON";
        }

        if (base.contains("stone")) {
            return "STONE";
        }

        if (base.contains("wooden") || base.contains("wood")) {
            return "WOOD";
        }

        return "WOOD";
    }

    public static int getConfiguredTierLevel(
            ProfessionToolConfig.ToolData toolData
    ) {

        return switch (
                getConfiguredTierName(
                        toolData
                )
        ) {
            case "STONE" -> 1;
            case "IRON" -> 2;
            case "DIAMOND", "NETHERITE" -> 3;
            default -> 0;
        };
    }

    public static int getRequiredTierLevel(
            BlockState state
    ) {

        if (state == null) {
            return 0;
        }

        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return 3;
        }

        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return 2;
        }

        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return 1;
        }

        return 0;
    }

    public static boolean canHarvestWithConfiguredTier(
            ItemStack stack,
            BlockState state
    ) {

        String toolId =
                ProfessionToolUtil.getToolId(
                        stack
                );

        if (toolId == null) {
            return true;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return true;
        }

        int toolTier =
                getConfiguredTierLevel(
                        toolData
                );

        int requiredTier =
                getRequiredTierLevel(
                        state
                );

        return toolTier >= requiredTier;
    }

    public static ItemStack createTool(
            String toolId
    ) {

        return createTool(
                toolId,
                false
        );
    }

    public static ItemStack createLootTool(
            String toolId,
            boolean ascended
    ) {

        ItemStack stack =
                createTool(
                        toolId,
                        ascended
                );

        if (
                !stack.isEmpty() &&
                        ascended
        ) {
            ProfessionToolMetadata.setDiscoveryAnnouncementEligible(
                    stack,
                    true
            );
        }

        return stack;
    }

    public static ItemStack createTool(
            String toolId,
            boolean ascended
    ) {

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            System.out.println(
                    "[ChampUtils] Unknown tool id: " +
                            toolId
            );
            return ItemStack.EMPTY;
        }

        if (
                ascended &&
                        !toolData.hasAscendedVariant
        ) {
            System.out.println(
                    "[ChampUtils] Tool does not have an ascended variant enabled: " +
                            toolId
            );
            return ItemStack.EMPTY;
        }

        Item item =
                REGISTERED_TOOLS.get(
                        toolId
                );

        if (item == null) {

            try {

                item =
                        BuiltInRegistries.ITEM.get(
                                ResourceLocation.parse(
                                        toolData.baseItem
                                )
                        );

                if (
                        item == null ||
                                item == Items.AIR
                ) {
                    System.out.println(
                            "[ChampUtils] Failed fallback tool lookup: " +
                                    toolId
                    );
                    return ItemStack.EMPTY;
                }

                REGISTERED_TOOLS.put(
                        toolId,
                        item
                );

                System.out.println(
                        "[ChampUtils] Late-registered tool: " +
                                toolId
                );

            } catch (Exception e) {
                e.printStackTrace();
                return ItemStack.EMPTY;
            }
        }

        ItemStack stack =
                new ItemStack(
                        item
                );

        ProfessionToolMetadata.initializeUnidentifiedTool(
                stack,
                toolId,
                ascended
        );

        initializeDurabilityIfNeeded(
                stack,
                toolData,
                true
        );

        applyDurabilityComponents(
                stack,
                toolData
        );

        /*
         Keep legacy key for your older helper/listener code until we migrate
         ProfessionToolUtil fully to ProfessionToolMetadata.
         */
        setLegacyToolId(
                stack,
                toolId
        );

        refreshToolStack(
                stack
        );

        return stack;
    }

    public static void refreshToolStack(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return;
        }

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        if (
                toolId == null ||
                        toolId.isBlank()
        ) {
            return;
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return;
        }

        initializeDurabilityIfNeeded(
                stack,
                toolData,
                false
        );

        applyDurabilityComponents(
                stack,
                toolData
        );

        applyAscendedGlint(
                stack
        );

        boolean identified =
                ProfessionToolMetadata.isIdentified(
                        stack
                );

        if (identified) {
            applyIdentifiedDisplay(
                    stack,
                    toolId,
                    toolData
            );
        } else {
            applyUnidentifiedDisplay(
                    stack,
                    toolId,
                    toolData
            );
        }
    }

    private static void applyUnidentifiedDisplay(
            ItemStack stack,
            String toolId,
            ProfessionToolConfig.ToolData toolData
    ) {

        String displayName =
                ProfessionToolConfig.getDisplayName(
                        toolId,
                        toolData
                );

        boolean ascended =
                ProfessionToolMetadata.isAscended(
                        stack
                );

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(
                        (ascended ? "Ascended Unidentified " : "Unidentified ") +
                                displayName
                ).withStyle(
                        ChatFormatting.DARK_GRAY
                )
        );

        List<Component> lore =
                new ArrayList<>();

        addHeaderLore(
                lore,
                toolData
        );

        addDurabilityLore(
                lore,
                stack
        );

        addItemLockLore(
                lore,
                stack
        );

        if (ascended) {
            lore.add(
                    Component.literal(
                            "Ascended Tracker Variant"
                    ).withStyle(
                            ChatFormatting.GRAY
                    )
            );

            lore.add(
                    Component.literal(
                            "Tracker rolls when identified."
                    ).withStyle(
                            ChatFormatting.DARK_GRAY
                    )
            );
        }

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "Unidentified"
                ).withStyle(
                        ChatFormatting.GOLD
                )
        );

        lore.add(
                Component.literal(
                        " Cost: $" +
                                ProfessionToolConfig.getBaseRollCost(
                                        toolData
                                )
                ).withStyle(
                        ChatFormatting.GOLD
                )
        );

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "This item's stats have not been revealed."
                ).withStyle(
                        ChatFormatting.DARK_GRAY
                )
        );

        lore.add(
                Component.literal(
                        "Cannot be used until identified."
                ).withStyle(
                        ChatFormatting.RED
                )
        );

        stack.set(
                DataComponents.LORE,
                new ItemLore(
                        lore
                )
        );
    }

    private static void applyIdentifiedDisplay(
            ItemStack stack,
            String toolId,
            ProfessionToolConfig.ToolData toolData
    ) {

        String displayName =
                ProfessionToolConfig.getDisplayName(
                        toolId,
                        toolData
                );

        double quality =
                ProfessionToolMetadata.getQuality(
                        stack
                );

        boolean ascended =
                ProfessionToolMetadata.isAscended(
                        stack
                );

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(
                        (ascended ? "Ascended " : "") +
                                displayName +
                                " "
                ).withStyle(
                        getRarityColor(
                                toolData.rarity
                        )
                ).append(
                        buildPercentComponent(
                                quality
                        )
                )
        );

        List<Component> lore =
                new ArrayList<>();

        addHeaderLore(
                lore,
                toolData
        );

        addDurabilityLore(
                lore,
                stack
        );

        addItemLockLore(
                lore,
                stack
        );

        if (ascended) {
            lore.add(
                    Component.literal(
                            "Ascended Tracker Variant"
                    ).withStyle(
                            ChatFormatting.GRAY
                    )
            );
        }

        lore.add(
                Component.literal(
                        "Quality: "
                ).withStyle(
                        ChatFormatting.WHITE
                ).append(
                        buildPercentComponent(
                                quality
                        )
                )
        );

        lore.add(
                Component.literal(
                        "Rerolls: " +
                                ProfessionToolMetadata.getRerolls(
                                        stack
                                )
                ).withStyle(
                        ChatFormatting.GRAY
                )
        );

        addRolledStatsLore(
                lore,
                stack,
                toolData
        );


        addTrackerLore(
                lore,
                stack
        );

        addPassivesLore(
                lore,
                toolData
        );

        addActiveAbilityLore(
                lore,
                toolData
        );

        stack.set(
                DataComponents.LORE,
                new ItemLore(
                        lore
                )
        );
    }

    private static void addHeaderLore(
            List<Component> lore,
            ProfessionToolConfig.ToolData toolData
    ) {

        lore.add(
                Component.literal(
                        formatRarity(
                                toolData.rarity
                        ) +
                                " " +
                                formatWords(
                                        toolData.profession
                                ) +
                                " Tool"
                ).withStyle(
                        getRarityColor(
                                toolData.rarity
                        )
                )
        );

        lore.add(
                Component.literal(
                        "Requires " +
                                formatWords(
                                        toolData.profession
                                ) +
                                " level " +
                                toolData.requiredLevel
                ).withStyle(
                        ChatFormatting.GRAY
                )
        );
    }


    private static void addItemLockLore(
            List<Component> lore,
            ItemStack stack
    ) {

        if (!ProfessionToolMetadata.isLocked(stack)) {
            return;
        }

        lore.add(
                Component.literal(
                        "🔒 Locked - protected from reroll/salvage"
                ).withStyle(
                        ChatFormatting.GOLD,
                        ChatFormatting.BOLD
                )
        );
    }

    private static void addDurabilityLore(
            List<Component> lore,
            ItemStack stack
    ) {

        int max =
                ProfessionToolMetadata.getMaxDurability(
                        stack
                );

        if (max <= 0) {
            return;
        }

        int current =
                Math.max(
                        0,
                        Math.min(
                                max,
                                ProfessionToolMetadata.getCurrentDurability(
                                        stack
                                )
                        )
                );

        ChatFormatting color =
                current <= 0
                        ? ChatFormatting.RED
                        : ChatFormatting.GRAY;

        lore.add(
                Component.literal(
                        "Durability: " +
                                current +
                                "/" +
                                max
                ).withStyle(
                        color
                )
        );

        if (current <= 0) {
            lore.add(
                    Component.literal(
                            "Broken - repair before use."
                    ).withStyle(
                            ChatFormatting.RED
                    )
            );
        }
    }

    private static void addRolledStatsLore(
            List<Component> lore,
            ItemStack stack,
            ProfessionToolConfig.ToolData toolData
    ) {

        Map<String, Double> rolledStats =
                ProfessionToolMetadata.getRolledStats(
                        stack
                );

        if (
                rolledStats == null ||
                        rolledStats.isEmpty()
        ) {
            return;
        }

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "Rolled Stats"
                ).withStyle(
                        ChatFormatting.GOLD
                )
        );

        for (
                Map.Entry<String, Double> stat :
                rolledStats.entrySet()
        ) {

            double statQuality =
                    getStatQualityPercent(
                            toolData,
                            stat.getKey(),
                            stat.getValue()
                    );

            Component line;

            if (statQuality >= 100.0D) {
                line =
                        Component.literal(
                                " +" +
                                        formatStatValue(
                                                stat.getKey(),
                                                stat.getValue()
                                        ) +
                                        " " +
                                        formatStatName(
                                                stat.getKey()
                                        ) +
                                        " [100%]"
                        ).withStyle(
                                ChatFormatting.GOLD,
                                ChatFormatting.BOLD
                        );
            } else {
                line =
                        Component.literal(
                                " +"
                        ).withStyle(
                                ChatFormatting.WHITE
                        ).append(
                                buildStatValueComponent(
                                        stat.getKey(),
                                        stat.getValue(),
                                        statQuality
                                )
                        ).append(
                                Component.literal(
                                        " " +
                                                formatStatName(
                                                        stat.getKey()
                                                ) +
                                                " "
                                ).withStyle(
                                        ChatFormatting.WHITE
                                )
                        ).append(
                                buildPercentComponent(
                                        statQuality
                                )
                        );
            }

            lore.add(
                    line
            );
        }
    }



    private static void addTrackerLore(
            List<Component> lore,
            ItemStack stack
    ) {

        if (
                !ProfessionToolMetadata.isAscended(
                        stack
                )
        ) {
            return;
        }

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "Ascended Tracker"
                ).withStyle(
                        ChatFormatting.GRAY
                )
        );

        String selectedTracker =
                ProfessionToolMetadata.getSelectedTracker(
                        stack
                );

        if (
                selectedTracker == null ||
                        selectedTracker.isBlank()
        ) {
            lore.add(
                    Component.literal(
                            " No tracker selected yet."
                    ).withStyle(
                            ChatFormatting.DARK_GRAY
                    )
            );
            return;
        }

        long value =
                ProfessionToolMetadata.getTracker(
                        stack,
                        selectedTracker
                );

        lore.add(
                Component.literal(
                        " " +
                                formatTrackerName(
                                        selectedTracker
                                ) +
                                ": " +
                                value
                ).withStyle(
                        ChatFormatting.WHITE
                )
        );
    }


    private static void addPassivesLore(
            List<Component> lore,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData.passives == null ||
                        toolData.passives.isEmpty()
        ) {
            return;
        }

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "Passives"
                ).withStyle(
                        ChatFormatting.GOLD
                )
        );

        for (
                String passive :
                toolData.passives
        ) {

            lore.add(
                    Component.literal(
                            " " +
                                    formatWords(
                                            passive
                                    )
                    ).withStyle(
                            ChatFormatting.GRAY
                    )
            );
        }
    }

    private static void addActiveAbilityLore(
            List<Component> lore,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData.activeAbility == null ||
                        toolData.activeAbility.isBlank()
        ) {
            return;
        }

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "Active Ability"
                ).withStyle(
                        ChatFormatting.AQUA
                )
        );

        lore.add(
                Component.literal(
                        " " +
                                formatWords(
                                        toolData.activeAbility
                                )
                ).withStyle(
                        ChatFormatting.GRAY
                )
        );

        lore.add(
                Component.literal(
                        " Cooldown: " +
                                toolData.activeCooldownSeconds +
                                "s"
                ).withStyle(
                        ChatFormatting.DARK_GRAY
                )
        );

        if (toolData.activeDurationSeconds > 0) {
            lore.add(
                    Component.literal(
                            " Duration: " +
                                    toolData.activeDurationSeconds +
                                    "s"
                    ).withStyle(
                            ChatFormatting.DARK_GRAY
                    )
            );
        }
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
                toolData.statRanges.get(
                        statId
                );

        if (range == null) {
            return 0.0D;
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

        /*
         * Lore displays stat values as whole percentages.
         * Quality should therefore be based on the displayed value, not the
         * hidden decimal.
         *
         * Example: range 0.0 -> 1.0
         *   rolled 0.9 displays as +0%, so quality should show [0%]
         *   rolled 1.0 displays as +1%, so quality should show [100%]
         */
        double displayedMin =
                Math.floor(
                        min
                );

        double displayedMax =
                Math.floor(
                        max
                );

        double displayedValue =
                Math.floor(
                        rolledValue
                );

        if (displayedMax > displayedMin) {
            min = displayedMin;
            max = displayedMax;
            rolledValue = displayedValue;
        }

        if (max <= min) {
            return 100.0D;
        }

        double percent =
                ((rolledValue - min) /
                        (max - min)) *
                        100.0D;

        percent =
                Math.max(
                        0.0D,
                        Math.min(
                                100.0D,
                                percent
                        )
                );

        return Math.floor(
                percent
        );
    }

    private static Component buildPercentComponent(
            double quality
    ) {

        int rounded =
                (int) Math.floor(
                        quality
                );

        if (rounded >= 100) {
            return buildRainbowText(
                    "[100%]"
            );
        }

        ChatFormatting color =
                getStatQualityColor(
                        rounded
                );

        return Component.literal(
                "[" +
                        rounded +
                        "%]"
        ).withStyle(
                color
        );
    }

    private static Component buildStatValueComponent(
            String statId,
            double value,
            double quality
    ) {

        int rounded =
                (int) Math.floor(
                        quality
                );

        String text =
                formatStatValue(
                        statId,
                        value
                );

        if (rounded >= 100) {
            return buildRainbowText(
                    text
            );
        }

        return Component.literal(
                text
        ).withStyle(
                getStatQualityColor(
                        rounded
                )
        );
    }

    private static ChatFormatting getStatQualityColor(
            int quality
    ) {

        if (quality <= 25) {
            return ChatFormatting.RED;
        }

        if (quality <= 50) {
            return ChatFormatting.YELLOW;
        }

        if (quality <= 75) {
            return ChatFormatting.GREEN;
        }

        return ChatFormatting.BLUE;
    }

    private static Component buildRainbowText(
            String text
    ) {

        ChatFormatting[] colors =
                new ChatFormatting[]{
                        ChatFormatting.RED,
                        ChatFormatting.GOLD,
                        ChatFormatting.YELLOW,
                        ChatFormatting.GREEN,
                        ChatFormatting.AQUA,
                        ChatFormatting.BLUE,
                        ChatFormatting.LIGHT_PURPLE
                };

        Component result =
                Component.empty();

        for (int i = 0; i < text.length(); i++) {
            result =
                    result.copy()
                            .append(
                                    Component.literal(
                                            String.valueOf(
                                                    text.charAt(i)
                                            )
                                    ).withStyle(
                                            colors[i % colors.length]
                                    )
                            );
        }

        return result;
    }

    private static void setLegacyToolId(
            ItemStack stack,
            String toolId
    ) {

        CustomData customData =
                stack.getOrDefault(
                        DataComponents.CUSTOM_DATA,
                        CustomData.EMPTY
                );

        CompoundTag tag =
                customData.copyTag();

        tag.putString(
                "ChampUtilsToolId",
                toolId
        );

        stack.set(
                DataComponents.CUSTOM_DATA,
                CustomData.of(
                        tag
                )
        );
    }


    public static boolean damageTool(
            ItemStack stack,
            int amount
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        amount <= 0 ||
                        !ProfessionToolMetadata.isProfessionTool(stack)
        ) {
            return false;
        }

        String toolId =
                ProfessionToolMetadata.getToolId(
                        stack
                );

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return false;
        }

        initializeDurabilityIfNeeded(
                stack,
                toolData,
                false
        );

        int max =
                ProfessionToolMetadata.getMaxDurability(
                        stack
                );

        if (max <= 0) {
            return false;
        }

        int current =
                ProfessionToolMetadata.getCurrentDurability(
                        stack
                );

        if (current <= 0) {
            applyDurabilityComponents(
                    stack,
                    toolData
            );
            return true;
        }


        ProfessionToolMetadata.setCurrentDurability(
                stack,
                Math.max(
                        0,
                        current - amount
                )
        );

        refreshToolStack(
                stack
        );

        return true;
    }

    public static boolean repairTool(
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
                ProfessionToolMetadata.getToolId(
                        stack
                );

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(
                        toolId
                );

        if (toolData == null) {
            return false;
        }

        initializeDurabilityIfNeeded(
                stack,
                toolData,
                false
        );

        int max =
                ProfessionToolMetadata.getMaxDurability(
                        stack
                );

        if (max <= 0) {
            return false;
        }

        int current =
                ProfessionToolMetadata.getCurrentDurability(
                        stack
                );

        double percent =
                toolData.repairDurabilityPercent <= 0.0D
                        ? 100.0D
                        : toolData.repairDurabilityPercent;

        int restore =
                Math.max(
                        1,
                        (int) Math.ceil(
                                max * (percent / 100.0D)
                        )
                );

        ProfessionToolMetadata.setCurrentDurability(
                stack,
                Math.min(
                        max,
                        current + restore
                )
        );

        refreshToolStack(
                stack
        );

        return true;
    }

    public static void initializeDurabilityIfNeeded(
            ItemStack stack,
            ProfessionToolConfig.ToolData toolData,
            boolean forceFull
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        toolData == null
        ) {
            return;
        }

        int max =
                calculateMaxDurability(
                        stack,
                        toolData
                );

        if (max <= 0) {
            return;
        }

        int oldMax =
                ProfessionToolMetadata.getMaxDurability(
                        stack
                );

        int oldCurrent =
                ProfessionToolMetadata.getCurrentDurability(
                        stack
                );

        if (forceFull || oldMax <= 0) {
            ProfessionToolMetadata.setMaxDurability(
                    stack,
                    max
            );

            ProfessionToolMetadata.setCurrentDurability(
                    stack,
                    max
            );
            return;
        }

        if (oldMax != max) {
            double ratio =
                    oldMax <= 0
                            ? 1.0D
                            : Math.max(
                                    0.0D,
                                    Math.min(
                                            1.0D,
                                            oldCurrent / (double) oldMax
                                    )
                            );

            ProfessionToolMetadata.setMaxDurability(
                    stack,
                    max
            );

            ProfessionToolMetadata.setCurrentDurability(
                    stack,
                    Math.max(
                            0,
                            Math.min(
                                    max,
                                    (int) Math.round(
                                            max * ratio
                                    )
                            )
                    )
            );
        }
    }

    private static int calculateMaxDurability(
            ItemStack stack,
            ProfessionToolConfig.ToolData toolData
    ) {

        int base =
                getBaseMaxDurability(
                        toolData,
                        null
                );

        double durabilityBonus =
                ProfessionToolMetadata.isIdentified(
                        stack
                )
                        ? ProfessionToolUtil.getStat(
                                stack,
                                "durabilityBonus"
                        )
                        : 0.0D;

        return Math.max(
                1,
                (int) Math.round(
                        base * (1.0D + Math.max(0.0D, durabilityBonus) / 100.0D)
                )
        );
    }

    private static int getBaseMaxDurability(
            ProfessionToolConfig.ToolData toolData,
            Item fallbackBaseItem
    ) {

        if (
                toolData != null &&
                        toolData.baseDurability > 0
        ) {
            return toolData.baseDurability;
        }

        Item baseItem =
                fallbackBaseItem;

        if (
                baseItem == null &&
                        toolData != null &&
                        toolData.baseItem != null &&
                        !toolData.baseItem.isBlank()
        ) {
            try {
                baseItem =
                        BuiltInRegistries.ITEM.get(
                                ResourceLocation.parse(
                                        toolData.baseItem
                                )
                        );
            } catch (Exception ignored) {
            }
        }

        if (
                baseItem != null &&
                        baseItem != Items.AIR
        ) {
            ItemStack baseStack =
                    new ItemStack(
                            baseItem
                    );

            if (baseStack.isDamageableItem()) {
                return Math.max(
                        1,
                        baseStack.getMaxDamage()
                );
            }
        }

        return 250;
    }

    private static void applyDurabilityComponents(
            ItemStack stack,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return;
        }

        initializeDurabilityIfNeeded(
                stack,
                toolData,
                false
        );

        int max =
                ProfessionToolMetadata.getMaxDurability(
                        stack
                );

        if (max <= 0) {
            return;
        }

        int current =
                Math.max(
                        0,
                        Math.min(
                                max,
                                ProfessionToolMetadata.getCurrentDurability(
                                        stack
                                )
                        )
                );

        stack.remove(
                DataComponents.UNBREAKABLE
        );

        stack.set(
                DataComponents.MAX_DAMAGE,
                max
        );

        stack.set(
                DataComponents.DAMAGE,
                Math.max(
                        0,
                        Math.min(
                                max - 1,
                                max - current
                        )
                )
        );
    }

    private static void applyAscendedGlint(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return;
        }

        stack.set(
                DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
                ProfessionToolMetadata.isAscended(
                        stack
                )
        );
    }



    private static String formatTrackerName(
            String trackerId
    ) {

        if (
                trackerId == null ||
                        trackerId.isBlank()
        ) {
            return "Unknown";
        }

        return formatWords(
                trackerId
        );
    }

    private static String formatRarity(
            String rarity
    ) {

        if (
                rarity == null ||
                        rarity.isBlank()
        ) {
            return "COMMON";
        }

        return rarity
                .replace("_", " ")
                .toUpperCase();
    }

    private static String formatWords(
            String value
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {
            return "";
        }

        String[] parts =
                value.replace("_", " ")
                        .replace("-", " ")
                        .trim()
                        .split("\\s+");

        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {

            if (part.isBlank()) {
                continue;
            }

            builder.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            if (part.length() > 1) {
                builder.append(
                        part.substring(1)
                                .toLowerCase()
                );
            }

            builder.append(" ");
        }

        return builder
                .toString()
                .trim();
    }

    private static String formatStatName(
            String stat
    ) {

        if (
                stat == null ||
                        stat.isBlank()
        ) {
            return "";
        }

        return formatWords(
                stat.replaceAll(
                        "([a-z])([A-Z])",
                        "$1 $2"
                )
        );
    }

    private static String formatStatValue(
            String statId,
            double value
    ) {

        return formatDecimal(
                value
        ) + "%";
    }

    private static String formatDecimal(
            double value
    ) {

        return String.valueOf(
                (int) Math.floor(
                        value
                )
        );
    }

    private static ChatFormatting getQualityColor(
            double quality
    ) {

        if (quality >= 90.0D) {
            return ChatFormatting.LIGHT_PURPLE;
        }

        if (quality >= 75.0D) {
            return ChatFormatting.GOLD;
        }

        return ChatFormatting.WHITE;
    }

    public static Item getTool(
            String toolId
    ) {

        return REGISTERED_TOOLS.get(
                toolId
        );
    }

    public static Map<String, Item> getRegisteredTools() {

        return REGISTERED_TOOLS;
    }

    private static Rarity getRarity(
            String rarity
    ) {

        if (rarity == null) {
            return Rarity.COMMON;
        }

        return switch (
                rarity.toUpperCase()
                ) {
            case "UNCOMMON" -> Rarity.UNCOMMON;
            case "RARE" -> Rarity.RARE;
            case "EPIC", "LEGENDARY", "MYTHIC" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }


    private static String toRoman(
            int value
    ) {

        if (value <= 0) {
            return "0";
        }

        String[] romans =
                new String[]{
                        "",
                        "I",
                        "II",
                        "III",
                        "IV",
                        "V",
                        "VI",
                        "VII",
                        "VIII",
                        "IX",
                        "X"
                };

        if (value < romans.length) {
            return romans[value];
        }

        return String.valueOf(
                value
        );
    }

    private static ChatFormatting getRarityColor(
            String rarity
    ) {

        if (rarity == null) {
            return ChatFormatting.WHITE;
        }

        return switch (
                rarity.toUpperCase()
                ) {
            case "UNCOMMON" -> ChatFormatting.DARK_GREEN;
            case "RARE" -> ChatFormatting.AQUA;
            case "EPIC" -> ChatFormatting.DARK_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    public static float applyMiningSpeedStat(
            ItemStack stack,
            float baseSpeed
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        baseSpeed <= 1.0F ||
                        !ProfessionToolMetadata.isProfessionTool(stack) ||
                        !ProfessionToolMetadata.isIdentified(stack) ||
                        ProfessionToolMetadata.isBroken(stack)
        ) {
            return baseSpeed;
        }

        double miningSpeed =
                ProfessionToolUtil.getStat(
                        stack,
                        "miningSpeed"
                );

        if (miningSpeed <= 0.0D) {
            return baseSpeed;
        }

        return (float) (
                baseSpeed +
                        getEfficiencyStyleMiningSpeedBonus(
                                miningSpeed
                        )
        );
    }

    public static double getMiningSpeedMultiplier(
            double miningSpeedPercent
    ) {

        if (miningSpeedPercent <= 0.0D) {
            return 1.0D;
        }

        return 1.0D +
                (miningSpeedPercent / 100.0D);
    }

    public static double getEfficiencyStyleMiningSpeedBonus(
            double miningSpeedPercent
    ) {

        if (miningSpeedPercent <= 0.0D) {
            return 0.0D;
        }

        /*
         * Vanilla Efficiency does not multiply speed by a flat percent.
         * It adds an efficiency bonus to the tool's destroy speed:
         *
         *   level 1 -> +2
         *   level 2 -> +5
         *   level 3 -> +10
         *   level 4 -> +17
         *   level 5 -> +26
         *
         * ChampUtils keeps the config/display as percentages, then converts
         * every 50% miningSpeed into one virtual Efficiency level. Fractional
         * values are allowed so 23% and 230% are no longer in the same
         * barely-noticeable vanilla multiplier bucket.
         */
        double virtualEfficiencyLevel =
                miningSpeedPercent / 50.0D;

        return (virtualEfficiencyLevel * virtualEfficiencyLevel) + 1.0D;
    }

    public static class CustomPickaxeItem extends PickaxeItem implements PolymerItem {

        private final Item baseItem;

        public CustomPickaxeItem(
                Item baseItem,
                Tier tier,
                Properties properties
        ) {

            super(
                    tier,
                    properties
            );

            this.baseItem =
                    baseItem;
        }

        @Override
        public float getDestroySpeed(
                ItemStack stack,
                BlockState state
        ) {

            return ProfessionToolManager.applyMiningSpeedStat(
                    stack,
                    super.getDestroySpeed(
                            stack,
                            state
                    )
            );
        }

        @Override
        public boolean isCorrectToolForDrops(
                ItemStack stack,
                BlockState state
        ) {

            return ProfessionToolManager.canHarvestWithConfiguredTier(
                    stack,
                    state
            );
        }

        @Override
        public boolean mineBlock(
                ItemStack stack,
                Level level,
                BlockState state,
                BlockPos pos,
                LivingEntity miningEntity
        ) {

            if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
                ProfessionToolManager.damageTool(
                        stack,
                        1
                );
            }

            return true;
        }

        @Override
        public boolean isEnchantable(
                ItemStack stack
        ) {

            return false;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {

            return baseItem;
        }
    }

    public static class CustomAxeItem extends AxeItem implements PolymerItem {

        private final Item baseItem;

        public CustomAxeItem(
                Item baseItem,
                Tier tier,
                Properties properties
        ) {

            super(
                    tier,
                    properties
            );

            this.baseItem =
                    baseItem;
        }

        @Override
        public float getDestroySpeed(
                ItemStack stack,
                BlockState state
        ) {

            return ProfessionToolManager.applyMiningSpeedStat(
                    stack,
                    super.getDestroySpeed(
                            stack,
                            state
                    )
            );
        }

        @Override
        public boolean isEnchantable(
                ItemStack stack
        ) {

            return false;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {

            return baseItem;
        }
    }

    public static class CustomHoeItem extends HoeItem implements PolymerItem {

        private final Item baseItem;

        public CustomHoeItem(
                Item baseItem,
                Tier tier,
                Properties properties
        ) {

            super(
                    tier,
                    properties
            );

            this.baseItem =
                    baseItem;
        }

        @Override
        public float getDestroySpeed(
                ItemStack stack,
                BlockState state
        ) {

            return ProfessionToolManager.applyMiningSpeedStat(
                    stack,
                    super.getDestroySpeed(
                            stack,
                            state
                    )
            );
        }

        @Override
        public boolean isEnchantable(
                ItemStack stack
        ) {

            return false;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {

            return baseItem;
        }
    }

    public static class CustomGenericToolItem extends Item implements PolymerItem {

        private final Item baseItem;

        public CustomGenericToolItem(
                Item baseItem,
                Properties properties
        ) {

            super(
                    properties
            );

            this.baseItem =
                    baseItem;
        }

        @Override
        public boolean isEnchantable(
                ItemStack stack
        ) {

            return false;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {

            return baseItem;
        }
    }
}