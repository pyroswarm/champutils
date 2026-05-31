package com.champutils.crate;

import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class CrateKeyCraftingService {
    private CrateKeyCraftingService() {}

    public static CraftResult craft(ServerPlayer player, String crateId) {
        if (player == null) return CraftResult.fail("Player missing.");
        if (!CrateKeyCraftingConfig.enabled) return CraftResult.fail("Crate key crafting is disabled.");
        String id = CrateCreditManager.normalize(crateId);
        CrateKeyCraftingConfig.RecipeData recipe = CrateKeyCraftingConfig.recipes.get(id);
        if (recipe == null) return CraftResult.fail("No crate key recipe exists for: " + id);
        if (CrateConfig.getCrate(id) == null) return CraftResult.fail("Unknown crate: " + id);

        String fragment = ProfessionFragmentConfig.normalizeRarity(recipe.fragment);
        int fragmentCost = Math.max(0, recipe.fragmentCost);
        int output = Math.max(1, recipe.outputKeys);

        if (fragmentCost > 0) {
            int available = ProfessionFragmentManager.countFragments(player, fragment);
            if (available < fragmentCost) {
                return CraftResult.fail("You need " + fragmentCost + " " + ProfessionFragmentManager.formatWords(fragment) + " fragments. You have " + available + ".");
            }
        }

        if (recipe.items != null) {
            for (CrateKeyCraftingConfig.ItemCost cost : recipe.items) {
                Item item = resolveItem(cost == null ? null : cost.item);
                int amount = cost == null ? 0 : Math.max(0, cost.amount);
                if (item == Items.AIR || amount <= 0) continue;
                int available = countItem(player, item);
                if (available < amount) {
                    return CraftResult.fail("You need " + amount + "x " + itemName(item) + ". You have " + available + ".");
                }
            }
        }

        if (fragmentCost > 0 && !ProfessionFragmentManager.removeFragments(player, fragment, fragmentCost)) {
            return CraftResult.fail("Could not remove fragments.");
        }

        if (recipe.items != null) {
            for (CrateKeyCraftingConfig.ItemCost cost : recipe.items) {
                Item item = resolveItem(cost == null ? null : cost.item);
                int amount = cost == null ? 0 : Math.max(0, cost.amount);
                if (item == Items.AIR || amount <= 0) continue;
                removeItem(player, item, amount);
            }
        }

        CrateCreditManager.addCredits(player, id, output);
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        return CraftResult.success(id, crate == null ? id : crate.displayName, output, fragment, fragmentCost);
    }

    public static int countItem(ServerPlayer player, Item item) {
        if (player == null || item == null || item == Items.AIR) return 0;
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void removeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        player.getInventory().setChanged();
    }

    public static Item resolveItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return Items.AIR;
        try {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId.trim().toLowerCase(Locale.ROOT)));
            return item == null ? Items.AIR : item;
        } catch (Exception e) {
            return Items.AIR;
        }
    }

    public static String itemName(Item item) {
        if (item == null) return "Unknown Item";
        return item.getDescription().getString();
    }

    public record CraftResult(boolean success, String error, String crateId, String crateName, int outputKeys, String fragment, int fragmentCost) {
        public static CraftResult fail(String error) { return new CraftResult(false, error, "", "", 0, "", 0); }
        public static CraftResult success(String crateId, String crateName, int outputKeys, String fragment, int fragmentCost) {
            return new CraftResult(true, "", crateId, crateName, outputKeys, fragment, fragmentCost);
        }
    }
}
