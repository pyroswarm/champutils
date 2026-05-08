package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.component.Unbreakable;

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

        Item.Properties properties =
                new Item.Properties()
                        .stacksTo(1)
                        .rarity(
                                getRarity(
                                        toolData.rarity
                                )
                        );

        if (base.contains("pickaxe")) {
            return new CustomPickaxeItem(
                    baseItem,
                    Tiers.DIAMOND,
                    properties
            );
        }

        if (base.contains("axe")) {
            return new CustomAxeItem(
                    baseItem,
                    Tiers.DIAMOND,
                    properties
            );
        }

        if (base.contains("hoe")) {
            return new CustomHoeItem(
                    baseItem,
                    Tiers.DIAMOND,
                    properties
            );
        }

        return new CustomGenericToolItem(
                baseItem,
                properties
        );
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

        applyUnbreakable(
                stack
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

        applyUnbreakable(
                stack
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

            Component line =
                    Component.literal(
                            " +"
                    ).withStyle(
                            ChatFormatting.WHITE
                    ).append(
                            buildStatValueComponent(
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

        return Math.round(
                percent
        );
    }

    private static Component buildPercentComponent(
            double quality
    ) {

        int rounded =
                (int) Math.round(
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
            double value,
            double quality
    ) {

        int rounded =
                (int) Math.round(
                        quality
                );

        String text =
                formatStatValue(
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


    private static void applyUnbreakable(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return;
        }

        stack.set(
                DataComponents.UNBREAKABLE,
                new Unbreakable(
                        false
                )
        );

        stack.set(
                DataComponents.DAMAGE,
                0
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
            double value
    ) {

        return formatDecimal(
                value
        ) + "%";
    }

    private static String formatDecimal(
            double value
    ) {

        if (value == Math.floor(value)) {
            return String.valueOf(
                    (int) value
            );
        }

        return String.format(
                "%.1f",
                value
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
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {

            return baseItem;
        }
    }
}