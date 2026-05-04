package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfessionToolManager {

    private static final Map<String, Item> REGISTERED_TOOLS =
            new HashMap<>();

    public static void registerTools() {
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
        Item customTool =
                createProperToolType(toolData);

        if (customTool == null) {
            System.out.println(
                    "[ChampUtils] Failed to create tool: " +
                            toolId
            );
            return;
        }

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
    }

    private static Item createProperToolType(
            ProfessionToolConfig.ToolData toolData
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

        Item baseItem =
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse(base)
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

        if (base.contains("fishing_rod")) {
            return new CustomFishingRodItem(
                    baseItem,
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
                ProfessionToolConfig.TOOLS.get(toolId);

        if (toolData == null) {
            return ItemStack.EMPTY;
        }

        Item item =
                REGISTERED_TOOLS.get(toolId);

        if (item == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack =
                new ItemStack(item);

        /*
         Fix Polymer name issue
         Convert tool_id -> Tool Id
         */
        String formattedName =
                formatToolName(toolId);

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(
                        formattedName
                ).withStyle(
                        getRarityColor(
                                toolData.rarity
                        )
                )
        );

        List<Component> lore =
                new ArrayList<>();

        lore.add(
                Component.literal(
                        "Requires " +
                                toolData.profession +
                                " Level " +
                                toolData.requiredLevel
                ).withStyle(
                        ChatFormatting.GRAY
                )
        );

        lore.add(
                Component.literal(
                        toolData.rarity
                ).withStyle(
                        getRarityColor(
                                toolData.rarity
                        )
                )
        );

        if (
                toolData.stats != null &&
                        !toolData.stats.isEmpty()
        ) {
            lore.add(
                    Component.literal(" ")
            );

            for (
                    Map.Entry<String, Double> stat :
                    toolData.stats.entrySet()
            ) {
                lore.add(
                        Component.literal(
                                stat.getKey() +
                                        ": +" +
                                        stat.getValue()
                        ).withStyle(
                                ChatFormatting.GREEN
                        )
                );
            }
        }

        if (
                toolData.passives != null &&
                        !toolData.passives.isEmpty()
        ) {
            lore.add(
                    Component.literal(" ")
            );

            for (
                    String passive :
                    toolData.passives
            ) {
                lore.add(
                        Component.literal(
                                "Passive: " +
                                        passive
                        ).withStyle(
                                ChatFormatting.GOLD
                        )
                );
            }
        }

        stack.set(
                DataComponents.LORE,
                new ItemLore(lore)
        );

        return stack;
    }

    private static String formatToolName(
            String toolId
    ) {
        String[] parts =
                toolId.split("_");

        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
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

        return builder.toString().trim();
    }

    public static Item getTool(
            String toolId
    ) {
        return REGISTERED_TOOLS.get(toolId);
    }

    private static Rarity getRarity(
            String rarity
    ) {
        return switch (
                rarity.toUpperCase()
                ) {
            case "COMMON" -> Rarity.COMMON;
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
            case "COMMON" -> ChatFormatting.WHITE;
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.DARK_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.GRAY;
        };
    }

    /*
     PICKAXE
     */
    public static class CustomPickaxeItem
            extends PickaxeItem
            implements PolymerItem {

        private final Item baseItem;

        public CustomPickaxeItem(
                Item baseItem,
                Tier tier,
                Properties properties
        ) {
            super(tier, properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {
            return baseItem;
        }
    }

    /*
     AXE
     */
    public static class CustomAxeItem
            extends AxeItem
            implements PolymerItem {

        private final Item baseItem;

        public CustomAxeItem(
                Item baseItem,
                Tier tier,
                Properties properties
        ) {
            super(tier, properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {
            return baseItem;
        }
    }

    /*
     HOE
     */
    public static class CustomHoeItem
            extends HoeItem
            implements PolymerItem {

        private final Item baseItem;

        public CustomHoeItem(
                Item baseItem,
                Tier tier,
                Properties properties
        ) {
            super(tier, properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {
            return baseItem;
        }
    }

    /*
     FISHING ROD
     */
    public static class CustomFishingRodItem
            extends FishingRodItem
            implements PolymerItem {

        private final Item baseItem;

        public CustomFishingRodItem(
                Item baseItem,
                Properties properties
        ) {
            super(properties);
            this.baseItem = baseItem;
        }

        @Override
        public Item getPolymerItem(
                ItemStack stack,
                ServerPlayer player
        ) {
            return baseItem;
        }
    }

    /*
     fallback
     */
    public static class CustomGenericToolItem
            extends Item
            implements PolymerItem {

        private final Item baseItem;

        public CustomGenericToolItem(
                Item baseItem,
                Properties properties
        ) {
            super(properties);
            this.baseItem = baseItem;
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