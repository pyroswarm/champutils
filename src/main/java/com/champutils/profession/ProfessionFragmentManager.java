package com.champutils.profession;

import eu.pb4.polymer.core.api.item.PolymerItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
                        ProfessionFragmentConfig.normalizeRarity(fragmentKey),
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
            return Items.PAPER;
        }

        try {
            Item item =
                    BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse(baseItemId)
                    );

            return item == null || item == Items.AIR
                    ? Items.PAPER
                    : item;
        } catch (Exception e) {
            return Items.PAPER;
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
                        "Right-click to deposit into your fragment storage."
                ).withStyle(ChatFormatting.DARK_GRAY)
        );

        stack.set(
                DataComponents.LORE,
                new ItemLore(lore)
        );

        if (data.customModelData > 0) {
            stack.set(
                    DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(data.customModelData)
            );
        }
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

    public static boolean depositFragmentStack(
            ServerPlayer player,
            ItemStack stack,
            String fragmentKey
    ) {
        if (player == null || stack == null || stack.isEmpty()) {
            return false;
        }

        String normalized =
                ProfessionFragmentConfig.normalizeRarity(fragmentKey);

        if (!ProfessionFragmentConfig.FRAGMENTS.containsKey(normalized)) {
            return false;
        }

        int amount =
                stack.getCount();

        if (amount <= 0) {
            return false;
        }

        ProfessionManager.addFragments(
                player,
                normalized,
                amount
        );

        stack.shrink(amount);

        player.sendSystemMessage(
                Component.literal(
                        "§aDeposited §6" +
                                amount +
                                "x " +
                                formatWords(normalized) +
                                " Fragment§a."
                )
        );

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
                ProfessionFragmentConfig.UPGRADES.get(upgradeId);

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

    public static CraftResult craftRandomUnidentifiedTool(
            ServerPlayer player,
            String rarity,
            String toolType
    ) {
        if (player == null) {
            return CraftResult.fail("Player missing.");
        }

        String normalizedToolType =
                normalizeToolType(toolType);

        if (
                !normalizedToolType.equals("pickaxe") &&
                        !normalizedToolType.equals("axe") &&
                        !normalizedToolType.equals("hoe")
        ) {
            return CraftResult.fail("Choose pickaxe, axe, or hoe.");
        }

        String normalizedRarity =
                ProfessionFragmentConfig.normalizeRarity(rarity);

        ProfessionFragmentConfig.ToolCraftingData trade =
                ProfessionFragmentConfig.TOOL_CRAFTING.get(normalizedRarity);

        if (trade == null) {
            return CraftResult.fail("No fragment crafting rule exists for rarity: " + normalizedRarity);
        }

        String fragmentKey =
                ProfessionFragmentConfig.normalizeRarity(trade.fragment);

        int cost =
                Math.max(1, trade.cost);

        int available =
                countFragments(
                        player,
                        fragmentKey
                );

        if (available < cost) {
            return CraftResult.fail(
                    "You need " + cost + " " + formatWords(fragmentKey) + " fragments. You have " + available + "."
            );
        }

        List<String> candidates =
                findToolCandidates(
                        normalizedRarity,
                        normalizedToolType
                );

        if (candidates.isEmpty()) {
            return CraftResult.fail(
                    "No " + formatWords(normalizedRarity) + " " + formatWords(normalizedToolType) + " tools exist in profession_tools.json."
            );
        }

        String selectedToolId =
                candidates.get(
                        RANDOM.nextInt(candidates.size())
                );

        ProfessionToolConfig.ToolData toolData =
                ProfessionToolConfig.TOOLS.get(selectedToolId);

        ItemStack reward =
                ProfessionToolManager.createTool(
                        selectedToolId,
                        false
                );

        if (reward.isEmpty()) {
            return CraftResult.fail("Could not create selected tool: " + selectedToolId);
        }

        if (!removeFragments(player, fragmentKey, cost)) {
            return CraftResult.fail("Could not remove fragments.");
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

        return CraftResult.success(
                selectedToolId,
                ProfessionToolConfig.getDisplayName(selectedToolId, toolData),
                normalizedRarity,
                normalizedToolType,
                fragmentKey,
                cost
        );
    }

    private static List<String> findToolCandidates(
            String rarity,
            String toolType
    ) {
        List<String> candidates =
                new ArrayList<>();

        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            String toolId =
                    entry.getKey();

            ProfessionToolConfig.ToolData data =
                    entry.getValue();

            if (data == null) {
                continue;
            }

            if (!ProfessionFragmentConfig.normalizeRarity(data.rarity).equals(rarity)) {
                continue;
            }

            if (!matchesToolType(toolId, data, toolType)) {
                continue;
            }

            candidates.add(toolId);
        }

        return candidates;
    }

    private static boolean matchesToolType(
            String toolId,
            ProfessionToolConfig.ToolData data,
            String toolType
    ) {
        String normalizedToolType =
                normalizeToolType(toolType);

        String haystack =
                ((toolId == null ? "" : toolId) + " " +
                        (data == null || data.baseItem == null ? "" : data.baseItem))
                        .toLowerCase();

        return switch (normalizedToolType) {
            case "pickaxe" -> haystack.contains("pickaxe");
            case "axe" -> !haystack.contains("pickaxe") && (haystack.contains("_axe") || haystack.endsWith("axe") || haystack.contains(":axe"));
            case "hoe" -> haystack.contains("hoe");
            case "sword" -> haystack.contains("sword");
            case "shovel" -> haystack.contains("shovel");
            default -> haystack.contains(normalizedToolType);
        };
    }

    public static String normalizeToolType(String toolType) {
        if (toolType == null || toolType.isBlank()) {
            return "pickaxe";
        }

        String normalized =
                toolType.trim()
                        .toLowerCase();

        if (normalized.equals("pick") || normalized.equals("pickaxes")) {
            return "pickaxe";
        }

        if (normalized.equals("axes")) {
            return "axe";
        }

        if (normalized.equals("hoes")) {
            return "hoe";
        }

        return normalized;
    }

    public static boolean isFragmentId(String itemId) {
        return getFragmentKeyByItemId(itemId) != null;
    }

    public static String getFragmentKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        for (Map.Entry<String, Item> entry : REGISTERED_FRAGMENTS.entrySet()) {
            if (stack.getItem() == entry.getValue()) {
                return ProfessionFragmentConfig.normalizeRarity(entry.getKey());
            }
        }

        return null;
    }

    public static String getFragmentKeyByItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        String normalizedInput =
                itemId.trim().toLowerCase();

        for (Map.Entry<String, ProfessionFragmentConfig.FragmentData> entry : ProfessionFragmentConfig.FRAGMENTS.entrySet()) {
            ProfessionFragmentConfig.FragmentData data = entry.getValue();

            if (data == null || data.itemId == null) {
                continue;
            }

            if (data.itemId.equalsIgnoreCase(normalizedInput)) {
                return ProfessionFragmentConfig.normalizeRarity(entry.getKey());
            }
        }

        String rarityKey =
                ProfessionFragmentConfig.normalizeRarity(itemId);

        return ProfessionFragmentConfig.FRAGMENTS.containsKey(rarityKey)
                ? rarityKey
                : null;
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

    public record CraftResult(
            boolean success,
            String error,
            String toolId,
            String displayName,
            String rarity,
            String toolType,
            String fragmentKey,
            int cost
    ) {
        public static CraftResult fail(String error) {
            return new CraftResult(false, error, null, null, null, null, null, 0);
        }

        public static CraftResult success(
                String toolId,
                String displayName,
                String rarity,
                String toolType,
                String fragmentKey,
                int cost
        ) {
            return new CraftResult(true, null, toolId, displayName, rarity, toolType, fragmentKey, cost);
        }
    }

    public static class FragmentItem extends Item implements PolymerItem {

        private final String fragmentKey;
        private final Item baseItem;

        public FragmentItem(
                String fragmentKey,
                Item baseItem,
                Properties properties
        ) {
            super(properties);
            this.fragmentKey = fragmentKey;
            this.baseItem = baseItem;
        }

        @Override
        public InteractionResultHolder<ItemStack> use(
                Level level,
                net.minecraft.world.entity.player.Player player,
                InteractionHand hand
        ) {
            ItemStack stack =
                    player.getItemInHand(hand);

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                depositFragmentStack(
                        serverPlayer,
                        stack,
                        fragmentKey
                );
            }

            return InteractionResultHolder.sidedSuccess(
                    stack,
                    level.isClientSide
            );
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
