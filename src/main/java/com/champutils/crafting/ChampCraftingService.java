package com.champutils.crafting;

import com.champutils.profession.ProfessionBackpackManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChampCraftingService {
    private static final Map<UUID, Object> PLAYER_LOCKS = new ConcurrentHashMap<>();

    private ChampCraftingService() {}

    public static CraftResult craft(ServerPlayer player, String recipeId) {
        if (player == null) return CraftResult.fail("Player missing.");
        Object lock = PLAYER_LOCKS.computeIfAbsent(player.getUUID(), ignored -> new Object());
        synchronized (lock) {
            return craftLocked(player, recipeId);
        }
    }

    private static CraftResult craftLocked(ServerPlayer player, String recipeId) {
        if (!ChampCraftingConfig.CONFIG.enabled) return CraftResult.fail("Champ Crafting is disabled.");
        ChampCraftingConfig.RecipeData recipe = ChampCraftingConfig.get(recipeId);
        if (recipe == null || !recipe.enabled) return CraftResult.fail("That recipe is not available.");
        Item output = resolveItem(recipe.outputItem);
        if (output == Items.AIR) return CraftResult.fail("Output item is not registered: " + recipe.outputItem);
        int outputAmount = Math.max(1, recipe.outputAmount);
        if (!canFit(player, output, outputAmount)) return CraftResult.fail("Make room in your inventory before crafting this.");

        if (recipe.costs == null || recipe.costs.isEmpty()) return CraftResult.fail("This recipe has no costs configured.");
        for (ChampCraftingConfig.CostData cost : recipe.costs) {
            CostStatus status = status(player, cost);
            if (!status.valid()) return CraftResult.fail(status.message());
            if (status.have() < status.need()) {
                return CraftResult.fail("You need " + status.need() + "x " + itemName(cost.item) + " from " + sourceLabel(cost.source) + ". You have " + status.have() + ".");
            }
        }

        for (ChampCraftingConfig.CostData cost : recipe.costs) {
            if (!removeCost(player, cost)) {
                return CraftResult.fail("Could not remove one of the costs. Nothing was crafted. Try again.");
            }
        }

        give(player, output, outputAmount);
        String name = recipe.displayName == null || recipe.displayName.isBlank() ? itemName(output) : recipe.displayName;
        System.out.println("[ChampUtils][ChampCrafting] " + player.getGameProfile().getName() + " crafted " + outputAmount + "x " + recipe.outputItem + " via " + recipe.id);
        return CraftResult.success(recipe.id, name, recipe.outputItem, outputAmount);
    }

    public static CostStatus status(ServerPlayer player, ChampCraftingConfig.CostData cost) {
        if (player == null || cost == null || cost.item == null || cost.item.isBlank() || cost.amount <= 0L) {
            return CostStatus.invalid("Invalid recipe cost.");
        }
        String source = normalizeSource(cost.source);
        Item item = resolveItem(cost.item);
        if (item == Items.AIR) return CostStatus.invalid("Cost item is not registered: " + cost.item);
        long need = Math.max(1L, cost.amount);
        long have = switch (source) {
            case "inventory" -> countInventory(player, item);
            case "either" -> safeAdd(ProfessionBackpackManager.count(player, cost.item), countInventory(player, item));
            default -> ProfessionBackpackManager.count(player, cost.item);
        };
        return CostStatus.ok(source, cost.item, have, need);
    }

    private static boolean removeCost(ServerPlayer player, ChampCraftingConfig.CostData cost) {
        String source = normalizeSource(cost.source);
        Item item = resolveItem(cost.item);
        long amount = Math.max(1L, cost.amount);
        if (item == Items.AIR) return false;
        if (source.equals("inventory")) {
            return removeInventory(player, item, amount);
        }
        if (source.equals("either")) {
            long fromBackpack = Math.min(ProfessionBackpackManager.count(player, cost.item), amount);
            if (fromBackpack > 0 && !ProfessionBackpackManager.remove(player, cost.item, fromBackpack)) return false;
            long remaining = amount - fromBackpack;
            return remaining <= 0 || removeInventory(player, item, remaining);
        }
        return ProfessionBackpackManager.remove(player, cost.item, amount);
    }

    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return Items.AIR;
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.trim().toLowerCase(java.util.Locale.ROOT)));
            return item == null ? Items.AIR : item;
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }

    public static String itemName(String itemId) {
        Item item = resolveItem(itemId);
        if (item != Items.AIR) return item.getDescription().getString();
        return ChampCraftingConfig.formatName(itemId);
    }

    public static String itemName(Item item) {
        if (item == null || item == Items.AIR) return "Unknown Item";
        return item.getDescription().getString();
    }

    public static String sourceLabel(String raw) {
        return switch (normalizeSource(raw)) {
            case "inventory" -> "Inventory";
            case "either" -> "Backpack/Inventory";
            default -> "Backpack";
        };
    }

    private static String normalizeSource(String raw) {
        String source = raw == null ? "backpack" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!source.equals("inventory") && !source.equals("either")) source = "backpack";
        return source;
    }

    private static long countInventory(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) return 0L;
        long total = 0L;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) total = safeAdd(total, stack.getCount());
        }
        return total;
    }

    private static boolean removeInventory(ServerPlayer player, Item item, long amount) {
        if (amount <= 0L) return true;
        if (countInventory(player, item) < amount) return false;
        long remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0L; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int take = (int) Math.min((long) stack.getCount(), remaining);
            stack.shrink(take);
            remaining -= take;
        }
        player.getInventory().setChanged();
        return remaining <= 0L;
    }

    private static boolean canFit(ServerPlayer player, Item item, int amount) {
        if (player == null || item == null || item == Items.AIR || amount <= 0) return false;
        int remaining = amount;
        int maxStack = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                remaining -= maxStack;
            } else if (stack.is(item)) {
                remaining -= Math.max(0, maxStack - stack.getCount());
            }
        }
        return remaining <= 0;
    }

    private static void give(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        int maxStack = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(item, give);
            boolean added = player.getInventory().add(stack);
            if (!added && !stack.isEmpty()) player.drop(stack, false);
            remaining -= give;
        }
        player.getInventory().setChanged();
    }

    private static long safeAdd(long a, long b) {
        long result = a + b;
        if (((a ^ result) & (b ^ result)) < 0) return Long.MAX_VALUE;
        return result;
    }

    public record CostStatus(boolean valid, String message, String source, String item, long have, long need) {
        public static CostStatus ok(String source, String item, long have, long need) {
            return new CostStatus(true, "", source, item, have, need);
        }
        public static CostStatus invalid(String message) {
            return new CostStatus(false, message, "", "", 0L, 0L);
        }
    }

    public record CraftResult(boolean success, String error, String recipeId, String displayName, String outputItem, int outputAmount) {
        public static CraftResult fail(String error) {
            return new CraftResult(false, error == null ? "Craft failed." : error, "", "", "", 0);
        }
        public static CraftResult success(String recipeId, String displayName, String outputItem, int outputAmount) {
            return new CraftResult(true, "", recipeId, displayName, outputItem, outputAmount);
        }
    }
}
