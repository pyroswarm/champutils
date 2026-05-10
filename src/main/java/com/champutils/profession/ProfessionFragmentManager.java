package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class ProfessionFragmentManager {

    private static final Map<String, Item> REGISTERED_FRAGMENTS =
            new HashMap<>();

    private static final Random RANDOM =
            new Random();

    private ProfessionFragmentManager() {
    }

    public static void registerFragments() {
        REGISTERED_FRAGMENTS.clear();

        for (Map.Entry<String, ProfessionFragmentConfig.FragmentData> entry : ProfessionFragmentConfig.FRAGMENTS.entrySet()) {
            registerFragment(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        System.out.println(
                "[ChampUtils] Registered " +
                        REGISTERED_FRAGMENTS.size() +
                        " profession fragment items."
        );
    }

    private static void registerFragment(
            String fragmentKey,
            ProfessionFragmentConfig.FragmentData data
    ) {
        if (
                fragmentKey == null ||
                        fragmentKey.isBlank() ||
                        data == null ||
                        data.itemId == null ||
                        data.itemId.isBlank()
        ) {
            return;
        }

        Item baseItem =
                getBaseItem(data.baseItem);

        Item fragmentItem =
                new FragmentItem(
                        baseItem,
                        new Item.Properties()
                                .stacksTo(64)
                );

        try {
            Registry.register(
                    BuiltInRegistries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(
                            "champutils",
                            data.itemId
                    ),
                    fragmentItem
            );

            REGISTERED_FRAGMENTS.put(
                    ProfessionFragmentConfig.normalizeRarity(fragmentKey),
                    fragmentItem
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Item getBaseItem(String baseItemId) {
        if (baseItemId == null || baseItemId.isBlank()) {
            return Items.AMETHYST_SHARD;
        }

        try {
            Item item =
                    BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse(baseItemId)
                    );

            return item == null || item == Items.AIR
                    ? Items.AMETHYST_SHARD
                    : item;
        } catch (Exception e) {
            return Items.AMETHYST_SHARD;
        }
    }

    public static ItemStack createFragmentStack(
            String fragmentKey,
            int amount
    ) {
        String normalized =
                ProfessionFragmentConfig.normalizeRarity(fragmentKey);

        ProfessionFragmentConfig.FragmentData data =
                ProfessionFragmentConfig.FRAGMENTS.get(normalized);

        Item item =
                REGISTERED_FRAGMENTS.get(normalized);

        if (data == null || item == null || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack stack =
                new ItemStack(
                        item,
                        Math.min(64, amount)
                );

        applyFragmentDisplay(
                stack,
                normalized,
                data
        );

        return stack;
    }

    public static void applyFragmentDisplay(
            ItemStack stack,
            String fragmentKey,
            ProfessionFragmentConfig.FragmentData data
    ) {
        if (stack == null || stack.isEmpty() || data == null) {
            return;
        }

        ChatFormatting color =
                parseColor(data.color);

        stack.set(
                DataComponents.CUSTOM_NAME,
                Component.literal(data.displayName)
                        .withStyle(color)
        );

        List<Component> lore =
                new ArrayList<>();

        lore.add(
                Component.literal(
                        formatWords(fragmentKey) + " Fragment"
                ).withStyle(color)
        );

        if (data.lore != null && !data.lore.isBlank()) {
            lore.add(
                    Component.literal(data.lore)
                            .withStyle(ChatFormatting.GRAY)
            );
        }

        lore.add(
                Component.literal(
                        "Used by the profession salvage system."
                ).withStyle(ChatFormatting.DARK_GRAY)
        );

        stack.set(
                DataComponents.LORE,
                new ItemLore(lore)
        );

        /*
         * customModelData is kept in profession_fragments.json for when you add
         * server resource-pack models later. It is intentionally not applied here
         * yet because Minecraft 1.21.1 changed the component type across mappings.
         */
    }


    public static String getFragmentKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Item> entry : REGISTERED_FRAGMENTS.entrySet()) {
            if (stack.getItem() == entry.getValue()) {
                return entry.getKey();
            }
        }

        return null;
    }

    public static boolean isFragmentStack(ItemStack stack) {
        return getFragmentKey(stack) != null;
    }

    public static int depositFragmentStack(
            ServerPlayer player,
            ItemStack stack
    ) {
        if (player == null || stack == null || stack.isEmpty()) {
            return 0;
        }

        String fragmentKey =
                getFragmentKey(stack);

        if (fragmentKey == null || fragmentKey.isBlank()) {
            return 0;
        }

        int amount =
                stack.getCount();

        stack.shrink(amount);

        ProfessionManager.addFragments(
                player,
                fragmentKey,
                amount
        );

        return amount;
    }

    public static boolean giveFragments(
            ServerPlayer player,
            String fragmentKey,
            int amount
    ) {
        if (player == null || amount <= 0) {
            return false;
        }

        int remaining =
                amount;

        while (remaining > 0) {
            int stackSize =
                    Math.min(64, remaining);

            ItemStack stack =
                    createFragmentStack(
                            fragmentKey,
                            stackSize
                    );

            if (stack.isEmpty()) {
                return false;
            }

            boolean added =
                    player.getInventory()
                            .add(stack);

            if (!added) {
                player.drop(
                        stack,
                        false
                );
            }

            remaining -= stackSize;
        }

        return true;
    }

    public static SalvageResult salvageHeldTool(
            ServerPlayer player,
            ItemStack stack
    ) {
        if (player == null || stack == null || stack.isEmpty()) {
            return SalvageResult.fail("Hold a profession tool in your main hand.");
        }

        if (!ProfessionToolMetadata.isProfessionTool(stack)) {
            return SalvageResult.fail("This is not a profession tool.");
        }

        String toolId =
                ProfessionToolMetadata.getToolId(stack);

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(toolId);

        if (toolData == null) {
            return SalvageResult.fail("This profession tool is no longer in profession_tools.json.");
        }

        String rarity =
                ProfessionFragmentConfig.normalizeRarity(toolData.rarity);

        ProfessionFragmentConfig.SalvageData salvageData =
                ProfessionFragmentConfig.SALVAGE.get(rarity);

        if (salvageData == null) {
            return SalvageResult.fail("No salvage rule exists for rarity: " + rarity);
        }

        String fragmentKey =
                ProfessionFragmentConfig.normalizeRarity(salvageData.fragment);

        if (!ProfessionFragmentConfig.FRAGMENTS.containsKey(fragmentKey)) {
            return SalvageResult.fail("Salvage rule points to an unknown fragment: " + fragmentKey);
        }

        int min =
                Math.max(1, Math.min(salvageData.min, salvageData.max));

        int max =
                Math.max(min, Math.max(salvageData.min, salvageData.max));

        int amount =
                min + RANDOM.nextInt((max - min) + 1);

        ItemStack original =
                stack.copy();

        stack.shrink(1);

        giveFragments(
                player,
                fragmentKey,
                amount
        );

        return SalvageResult.success(
                toolId,
                ProfessionToolConfig.getDisplayName(toolId, toolData),
                rarity,
                fragmentKey,
                amount,
                original
        );
    }

    public static int countFragments(
            ServerPlayer player,
            String fragmentKey
    ) {
        if (player == null) {
            return 0;
        }

        return ProfessionManager.getFragments(
                player,
                ProfessionFragmentConfig.normalizeRarity(fragmentKey)
        );

    }

    public static boolean removeFragments(
            ServerPlayer player,
            String fragmentKey,
            int amount
    ) {
        if (player == null || amount <= 0) {
            return false;
        }

        return ProfessionManager.removeFragments(
                player,
                ProfessionFragmentConfig.normalizeRarity(fragmentKey),
                amount
        );
    }

    public static UpgradeResult upgrade(
            ServerPlayer player,
            String upgradeId
    ) {
        if (player == null) {
            return UpgradeResult.fail("Player missing.");
        }

        ProfessionFragmentConfig.UpgradeData upgrade =
                ProfessionFragmentConfig.UPGRADES.get(
                        upgradeId
                );

        if (upgrade == null) {
            return UpgradeResult.fail("Unknown upgrade: " + upgradeId);
        }

        String from =
                ProfessionFragmentConfig.normalizeRarity(upgrade.fromFragment);

        String to =
                ProfessionFragmentConfig.normalizeRarity(upgrade.toFragment);

        int cost =
                Math.max(1, upgrade.cost);

        int output =
                Math.max(1, upgrade.output);

        int available =
                countFragments(
                        player,
                        from
                );

        if (available < cost) {
            return UpgradeResult.fail(
                    "You need " + cost + " " + formatWords(from) + " fragments. You have " + available + "."
            );
        }

        if (!removeFragments(player, from, cost)) {
            return UpgradeResult.fail("Could not remove input fragments.");
        }

        ProfessionManager.addFragments(
                player,
                to,
                output
        );

        return UpgradeResult.success(
                upgradeId,
                from,
                cost,
                to,
                output
        );
    }


    public static TradeResult tradeForRandomTool(
            ServerPlayer player,
            String rarity,
            String toolType
    ) {
        if (player == null) {
            return TradeResult.fail("Player missing.");
        }

        String normalizedRarity =
                ProfessionFragmentConfig.normalizeRarity(rarity);

        String normalizedToolType =
                normalizeToolType(toolType);

        if (normalizedToolType.isBlank()) {
            return TradeResult.fail("Choose a tool type: pickaxe, axe, hoe, sword, or shovel.");
        }

        ProfessionFragmentConfig.TradeData trade =
                ProfessionFragmentConfig.TRADES.get(normalizedRarity);

        if (trade == null) {
            return TradeResult.fail("No fragment trade exists for rarity: " + normalizedRarity);
        }

        String fragmentKey =
                ProfessionFragmentConfig.normalizeRarity(trade.fragment);

        int cost =
                Math.max(1, trade.cost);

        int available =
                countFragments(player, fragmentKey);

        if (available < cost) {
            return TradeResult.fail(
                    "You need " + cost + " " + formatWords(fragmentKey) + " fragments. You have " + available + "."
            );
        }

        List<String> eligibleTools =
                getEligibleToolIds(
                        normalizedRarity,
                        normalizedToolType
                );

        if (eligibleTools.isEmpty()) {
            return TradeResult.fail(
                    "No " + formatWords(normalizedRarity) + " " + normalizedToolType + " tools exist in profession_tools.json."
            );
        }

        String selectedToolId =
                eligibleTools.get(
                        RANDOM.nextInt(eligibleTools.size())
                );

        ItemStack reward =
                ProfessionToolManager.createTool(
                        selectedToolId,
                        false
                );

        if (reward.isEmpty()) {
            return TradeResult.fail("Could not create selected tool: " + selectedToolId);
        }

        if (!removeFragments(player, fragmentKey, cost)) {
            return TradeResult.fail("Could not remove input fragments.");
        }

        boolean added =
                player.getInventory()
                        .add(reward);

        if (!added) {
            player.drop(
                    reward,
                    false
            );
        }

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(selectedToolId);

        return TradeResult.success(
                normalizedRarity,
                fragmentKey,
                cost,
                normalizedToolType,
                selectedToolId,
                ProfessionToolConfig.getDisplayName(selectedToolId, toolData),
                eligibleTools.size()
        );
    }

    public static List<String> getEligibleToolIds(
            String rarity,
            String toolType
    ) {
        String normalizedRarity =
                ProfessionFragmentConfig.normalizeRarity(rarity);

        String normalizedToolType =
                normalizeToolType(toolType);

        List<String> eligible =
                new ArrayList<>();

        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            String toolId =
                    entry.getKey();

            ProfessionToolConfig.ToolData toolData =
                    entry.getValue();

            if (toolId == null || toolData == null) {
                continue;
            }

            String toolRarity =
                    ProfessionFragmentConfig.normalizeRarity(toolData.rarity);

            if (!toolRarity.equals(normalizedRarity)) {
                continue;
            }

            if (!toolMatchesType(toolData, normalizedToolType)) {
                continue;
            }

            eligible.add(toolId);
        }

        return eligible;
    }

    public static String normalizeToolType(String toolType) {
        if (toolType == null) {
            return "";
        }

        String normalized =
                toolType.trim()
                        .toLowerCase(Locale.ROOT);

        if (normalized.endsWith("s")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return switch (normalized) {
            case "pick", "pickaxe" -> "pickaxe";
            case "axe" -> "axe";
            case "hoe" -> "hoe";
            case "sword" -> "sword";
            case "shovel", "spade" -> "shovel";
            default -> normalized;
        };
    }

    private static boolean toolMatchesType(
            ProfessionToolConfig.ToolData toolData,
            String toolType
    ) {
        if (toolData == null || toolType == null || toolType.isBlank()) {
            return false;
        }

        String baseItem =
                toolData.baseItem == null
                        ? ""
                        : toolData.baseItem.toLowerCase(Locale.ROOT);

        return switch (toolType) {
            case "pickaxe" -> baseItem.endsWith("_pickaxe");
            case "axe" -> baseItem.endsWith("_axe") && !baseItem.endsWith("_pickaxe");
            case "hoe" -> baseItem.endsWith("_hoe");
            case "sword" -> baseItem.endsWith("_sword");
            case "shovel" -> baseItem.endsWith("_shovel");
            default -> false;
        };
    }

    private static ChatFormatting parseColor(String color) {
        if (color == null || color.isBlank()) {
            return ChatFormatting.WHITE;
        }

        try {
            return ChatFormatting.valueOf(
                    color.trim().toUpperCase()
            );
        } catch (Exception e) {
            return ChatFormatting.WHITE;
        }
    }

    public static String formatWords(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }

        String[] parts =
                value.toLowerCase()
                        .split("[_ -]+");

        StringBuilder builder =
                new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(
                    Character.toUpperCase(part.charAt(0))
            );

            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }

        return builder.toString();
    }

    public record SalvageResult(
            boolean success,
            String error,
            String toolId,
            String displayName,
            String rarity,
            String fragmentKey,
            int amount,
            ItemStack originalStack
    ) {
        public static SalvageResult fail(String error) {
            return new SalvageResult(false, error, null, null, null, null, 0, ItemStack.EMPTY);
        }

        public static SalvageResult success(
                String toolId,
                String displayName,
                String rarity,
                String fragmentKey,
                int amount,
                ItemStack originalStack
        ) {
            return new SalvageResult(true, null, toolId, displayName, rarity, fragmentKey, amount, originalStack);
        }
    }

    public record UpgradeResult(
            boolean success,
            String error,
            String upgradeId,
            String fromFragment,
            int cost,
            String toFragment,
            int output
    ) {
        public static UpgradeResult fail(String error) {
            return new UpgradeResult(false, error, null, null, 0, null, 0);
        }

        public static UpgradeResult success(
                String upgradeId,
                String fromFragment,
                int cost,
                String toFragment,
                int output
        ) {
            return new UpgradeResult(true, null, upgradeId, fromFragment, cost, toFragment, output);
        }
    }

    public record TradeResult(
            boolean success,
            String error,
            String rarity,
            String fragmentKey,
            int cost,
            String toolType,
            String toolId,
            String displayName,
            int possibleRolls
    ) {
        public static TradeResult fail(String error) {
            return new TradeResult(false, error, null, null, 0, null, null, null, 0);
        }

        public static TradeResult success(
                String rarity,
                String fragmentKey,
                int cost,
                String toolType,
                String toolId,
                String displayName,
                int possibleRolls
        ) {
            return new TradeResult(true, null, rarity, fragmentKey, cost, toolType, toolId, displayName, possibleRolls);
        }
    }

    public static class FragmentItem extends Item implements PolymerItem {

        private final Item baseItem;

        public FragmentItem(
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
