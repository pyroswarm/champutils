package com.champutils.genesis;

import com.champutils.economy.EconomyManager;
import com.champutils.shop.NpcShopService;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class MegaShopService {

    private MegaShopService() {}

    public static Item resolveItem(String itemId) {
        return NpcShopService.resolveItem(itemId);
    }

    public static void buy(ServerPlayer player, MegaShopConfig.ShopEntry entry) {
        if (player == null || entry == null) return;
        if (!entry.available) {
            player.sendSystemMessage(Component.literal("That Mega Shop item is not currently available.").withStyle(ChatFormatting.RED));
            return;
        }

        Item item = resolveItem(entry.id);
        if (item == Items.AIR) {
            player.sendSystemMessage(Component.literal("That Mega Shop item is not registered on this server: " + entry.id).withStyle(ChatFormatting.RED));
            return;
        }

        long price = EconomyManager.creditsToCents(entry.priceCredits);
        EconomyManager.withdrawAsync(player, price, "Mega shop purchase: " + safeName(entry)).thenAccept(result ->
                player.server.execute(() -> {
                    if (!result.success) {
                        player.sendSystemMessage(Component.literal(result.error == null ? "You cannot afford that." : result.error).withStyle(ChatFormatting.RED));
                        return;
                    }
                    int amount = Math.max(1, entry.amount);
                    int maxStack = Math.max(1, item.getDefaultMaxStackSize());
                    while (amount > 0) {
                        int give = Math.min(maxStack, amount);
                        giveOrDrop(player, new ItemStack(item, give));
                        amount -= give;
                    }
                    player.sendSystemMessage(Component.literal("Purchased " + stripColor(safeName(entry)) + " for " + EconomyManager.format(price) + ".").withStyle(ChatFormatting.GREEN));
                }));
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        boolean added = player.getInventory().add(stack);
        if (!added) {
            player.drop(stack, false);
        }
    }

    private static String safeName(MegaShopConfig.ShopEntry entry) {
        if (entry.displayName != null && !entry.displayName.isBlank()) return entry.displayName;
        return entry.id == null ? "Mega Shop Item" : entry.id;
    }

    private static String stripColor(String value) {
        return value == null ? "" : value.replaceAll("§.", "");
    }
}
