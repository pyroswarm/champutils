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
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;

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
        }
        catch (Exception e) {
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

        if (
                baseItem == Items.AIR
        ) {
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
                toolData.baseItem;

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

        Item item =
                REGISTERED_TOOLS.get(
                        toolId
                );

        /*
         Fallback lookup for config-defined tools.
         */
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

        CompoundTag tag =
                new CompoundTag();

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

        String displayName =
                toolData.displayName == null ||
                        toolData.displayName.isBlank()
                        ? formatWords(
                        toolId
                )
                        : toolData.displayName;

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(
                        displayName
                ).withStyle(
                        getRarityColor(
                                toolData.rarity
                        )
                )
        );

        List<Component> lore =
                new ArrayList<>();

        addHeaderLore(
                lore,
                toolData
        );

        addStatsLore(
                lore,
                toolData
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

        return stack;
    }

    private static void addHeaderLore(
            List<Component> lore,
            ProfessionToolConfig.ToolData toolData
    ) {

        lore.add(
                Component.literal(
                        formatRarity(
                                toolData.rarity
                        )
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

    private static void addStatsLore(
            List<Component> lore,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData.stats == null ||
                        toolData.stats.isEmpty()
        ) {
            return;
        }

        lore.add(
                Component.literal(" ")
        );

        lore.add(
                Component.literal(
                        "Stats"
                ).withStyle(
                        ChatFormatting.YELLOW
                )
        );

        for (
                Map.Entry<String, Double> stat :
                toolData.stats.entrySet()
        ) {

            lore.add(
                    Component.literal(
                            " " +
                                    formatStatName(
                                            stat.getKey()
                                    ) +
                                    ": +" +
                                    formatStatValue(
                                            stat.getValue()
                                    )
                    ).withStyle(
                            ChatFormatting.GREEN
                    )
            );
        }
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

    private static String formatRarity(
            String rarity
    ) {
        return rarity
                .replace("_", " ")
                .toUpperCase();
    }

    private static String formatWords(
            String value
    ) {

        String[] parts =
                value.replace("_", " ")
                        .split(" ");

        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {
            builder.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            ).append(
                    part.substring(1)
                            .toLowerCase()
            ).append(" ");
        }

        return builder.toString().trim();
    }

    private static String formatStatName(
            String stat
    ) {
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

        if (value == Math.floor(value)) {
            return ((int) value) + "%";
        }

        return String.format(
                "%.1f%%",
                value
        );
    }

    public static Item getTool(
            String toolId
    ) {
        return REGISTERED_TOOLS.get(toolId);
    }

    public static Map<String, Item> getRegisteredTools() {
        return REGISTERED_TOOLS;
    }

    private static Rarity getRarity(
            String rarity
    ) {

        return switch (
                rarity.toUpperCase()
                ) {
            case "UNCOMMON" -> Rarity.UNCOMMON;
            case "RARE" -> Rarity.RARE;
            case "EPIC" -> Rarity.EPIC;
            default -> Rarity.COMMON;
        };
    }

    private static ChatFormatting getRarityColor(
            String rarity
    ) {

        return switch (
                rarity.toUpperCase()
                ) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.DARK_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    public static class CustomPickaxeItem extends PickaxeItem implements PolymerItem {
        private final Item baseItem;

        public CustomPickaxeItem(Item baseItem, Tier tier, Properties properties) {
            super(tier, properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return baseItem;
        }
    }

    public static class CustomAxeItem extends AxeItem implements PolymerItem {
        private final Item baseItem;

        public CustomAxeItem(Item baseItem, Tier tier, Properties properties) {
            super(tier, properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return baseItem;
        }
    }

    public static class CustomHoeItem extends HoeItem implements PolymerItem {
        private final Item baseItem;

        public CustomHoeItem(Item baseItem, Tier tier, Properties properties) {
            super(tier, properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return baseItem;
        }
    }

    public static class CustomGenericToolItem extends Item implements PolymerItem {
        private final Item baseItem;

        public CustomGenericToolItem(Item baseItem, Properties properties) {
            super(properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return baseItem;
        }
    }
}