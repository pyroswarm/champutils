package com.champutils.crafting;

import com.champutils.profession.ProfessionBackpackManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profile.PlayerProfileManager;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

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
        if (recipe.category != null && recipe.category.equalsIgnoreCase("Islander Resources") && !PlayerProfileManager.isIslander(player)) {
            return CraftResult.fail("Only Islander profiles can use Islander Resource recipes.");
        }
        ItemStack outputStack = createOutputStack(recipe.outputItem, Math.max(1, recipe.outputAmount));
        Item output = outputStack.getItem();
        if (outputStack.isEmpty() || output == Items.AIR) return CraftResult.fail("Output item is not registered: " + recipe.outputItem);
        int outputAmount = outputStack.getCount();
        if (!canFit(player, output, outputAmount)) return CraftResult.fail("Make room in your inventory before crafting this.");

        if (recipe.costs == null || recipe.costs.isEmpty()) return CraftResult.fail("This recipe has no costs configured.");
        for (ChampCraftingConfig.CostData cost : recipe.costs) {
            CostStatus status = status(player, cost);
            if (!status.valid()) return CraftResult.fail(status.message());
            if (status.have() < status.need()) {
                if ("credits".equals(status.source())) {
                    return CraftResult.fail("You need " + status.need() + " Credits. You have " + status.have() + " Credits.");
                }
                return CraftResult.fail("You need " + status.need() + "x " + itemName(cost.item) + " from " + sourceLabel(cost.source) + ". You have " + status.have() + ".");
            }
        }

        for (ChampCraftingConfig.CostData cost : recipe.costs) {
            if (!removeCost(player, cost)) {
                return CraftResult.fail("Could not remove one of the costs. Nothing was crafted. Try again.");
            }
        }

        give(player, outputStack);
        String name = recipe.displayName == null || recipe.displayName.isBlank() ? itemName(output) : recipe.displayName;
        System.out.println("[ChampUtils][ChampCrafting] " + player.getGameProfile().getName() + " crafted " + outputAmount + "x " + recipe.outputItem + " via " + recipe.id);
        return CraftResult.success(recipe.id, name, recipe.outputItem, outputAmount);
    }

    public static CostStatus status(ServerPlayer player, ChampCraftingConfig.CostData cost) {
        if (player == null || cost == null || cost.amount <= 0L) {
            return CostStatus.invalid("Invalid recipe cost.");
        }
        String source = normalizeSource(cost.source);
        long need = Math.max(1L, cost.amount);
        if (source.equals("credits")) {
            long have = Math.max(0L, EconomyManager.getBalance(player) / 100L);
            return CostStatus.ok(source, "credits", have, need);
        }
        if (cost.item == null || cost.item.isBlank()) {
            return CostStatus.invalid("Invalid recipe cost item.");
        }
        Item item = resolveItem(cost.item);
        if (item == Items.AIR) return CostStatus.invalid("Cost item is not registered: " + cost.item);
        long have = switch (source) {
            case "inventory" -> countInventory(player, item);
            case "either" -> safeAdd(ProfessionBackpackManager.count(player, cost.item), countInventory(player, item));
            default -> ProfessionBackpackManager.count(player, cost.item);
        };
        return CostStatus.ok(source, cost.item, have, need);
    }

    private static boolean removeCost(ServerPlayer player, ChampCraftingConfig.CostData cost) {
        String source = normalizeSource(cost.source);
        long amount = Math.max(1L, cost.amount);
        if (source.equals("credits")) {
            return EconomyManager.withdraw(player, EconomyManager.wholeCreditsToCents(amount), "champ_crafting:" + (cost.item == null ? "credits" : cost.item)).success;
        }
        Item item = resolveItem(cost.item);
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
        if (isBottleCapId(itemId)) return Items.PAPER;
        if (itemId == null || itemId.isBlank()) return Items.AIR;
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.trim().toLowerCase(java.util.Locale.ROOT)));
            return item == null ? Items.AIR : item;
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }

    public static ItemStack createOutputStack(String itemId, int amount) {
        int count = Math.max(1, amount);
        BottleCap cap = bottleCap(itemId);
        if (cap != null) {
            ItemStack stack = new ItemStack(Items.PAPER, count);
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(cap.modelData()));
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(cap.itemName()).withStyle(cap.modelData() == 2 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            stack.set(DataComponents.ITEM_NAME, Component.literal(cap.itemName()));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal(cap.lore()).withStyle(ChatFormatting.GRAY));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            return stack;
        }
        Item item = resolveItem(itemId);
        if (item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item, count);
    }

    private static boolean isBottleCapId(String itemId) {
        return bottleCap(itemId) != null;
    }

    private static BottleCap bottleCap(String itemId) {
        if (itemId == null) return null;
        String path = itemId.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = path.indexOf(':');
        if (colon >= 0) path = path.substring(colon + 1);
        return switch (path) {
            case "gold_bottle_cap", "golden_bottle_cap" -> new BottleCap(2, "Golden Bottle Cap", "Golden Bottle Cap");
            case "bottle_cap", "silver_bottle_cap", "silver_bottle_cap_atk", "silver_bottle_cap_attack", "attack_bottle_cap" -> new BottleCap(1, "Atk", "Silver Bottle Cap - Attack IV");
            case "silver_bottle_cap_def", "silver_bottle_cap_defence", "silver_bottle_cap_defense", "defence_bottle_cap", "defense_bottle_cap" -> new BottleCap(1, "Def", "Silver Bottle Cap - Defence IV");
            case "silver_bottle_cap_hp", "hp_bottle_cap" -> new BottleCap(1, "HP", "Silver Bottle Cap - HP IV");
            case "silver_bottle_cap_sp_atk", "silver_bottle_cap_special_attack", "special_attack_bottle_cap", "sp_atk_bottle_cap" -> new BottleCap(1, "Sp.Atk", "Silver Bottle Cap - Special Attack IV");
            case "silver_bottle_cap_sp_def", "silver_bottle_cap_special_defence", "silver_bottle_cap_special_defense", "special_defence_bottle_cap", "special_defense_bottle_cap", "sp_def_bottle_cap" -> new BottleCap(1, "Sp.Def", "Silver Bottle Cap - Special Defence IV");
            case "silver_bottle_cap_speed", "speed_bottle_cap" -> new BottleCap(1, "Speed", "Silver Bottle Cap - Speed IV");
            default -> null;
        };
    }

    private record BottleCap(int modelData, String itemName, String lore) {}

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
            case "credits" -> "Credits";
            default -> "Backpack";
        };
    }

    private static String normalizeSource(String raw) {
        String source = raw == null ? "backpack" : raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (!source.equals("inventory") && !source.equals("either") && !source.equals("credits")) source = "backpack";
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

    private static void give(ServerPlayer player, ItemStack template) {
        if (template == null || template.isEmpty()) return;
        int remaining = template.getCount();
        int maxStack = Math.max(1, template.getMaxStackSize());
        while (remaining > 0) {
            int give = Math.min(maxStack, remaining);
            ItemStack stack = template.copy();
            stack.setCount(give);
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
