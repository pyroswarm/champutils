package com.champutils.shop;

import com.champutils.economy.EconomyManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class ChestShopService {

    private static final Set<String> TRANSACTION_LOCKS = new HashSet<>();

    private ChestShopService() {
    }

    public static InteractionResult handleInteract(ServerPlayer player, ServerLevel level, BlockPos pos) {
        ChestShopRegistry.ChestShop shop = ChestShopRegistry.getAt(level, pos);
        if (shop == null) {
            return InteractionResult.PASS;
        }

        if (!ChestShopRegistry.isValidShopContainer(level, pos)) {
            ChestShopRegistry.cleanupStaleShop(level, pos);
            player.sendSystemMessage(Component.literal("This chest shop was invalid and has been cleaned up.").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }

        if (shop.isOwner(player.getUUID())) {
            if (player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }

            sendInfo(player, shop);
            player.sendSystemMessage(Component.literal("Sneak-right-click to open your shop chest.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }

        String lockKey = ChestShopRegistry.key(level, pos);
        synchronized (TRANSACTION_LOCKS) {
            if (TRANSACTION_LOCKS.contains(lockKey)) {
                player.sendSystemMessage(Component.literal("This shop is already processing another transaction.").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }
            TRANSACTION_LOCKS.add(lockKey);
        }

        try {
            if (shop.mode() == ChestShopRegistry.ShopMode.SELL) {
                buyFromShop(player, level, pos, shop);
            } else {
                sellToShop(player, level, pos, shop);
            }
        } finally {
            synchronized (TRANSACTION_LOCKS) {
                TRANSACTION_LOCKS.remove(lockKey);
            }
        }

        return InteractionResult.SUCCESS;
    }

    public static void sendInfo(ServerPlayer player, ChestShopRegistry.ChestShop shop) {
        if (player == null || shop == null) {
            return;
        }

        String verb = shop.mode() == ChestShopRegistry.ShopMode.SELL ? "Selling" : "Buying";
        player.sendSystemMessage(Component.literal("==== Chest Shop ====").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(verb + ": " + shop.amount + "x " + shop.itemName).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Price: " + EconomyManager.format(shop.price)).withStyle(ChatFormatting.GREEN));
        player.sendSystemMessage(Component.literal("Owner: " + shop.ownerName).withStyle(ChatFormatting.GRAY));
    }

    private static void buyFromShop(ServerPlayer buyer, ServerLevel level, BlockPos pos, ChestShopRegistry.ChestShop shop) {
        Container chest = getContainer(level, pos);
        if (chest == null) {
            buyer.sendSystemMessage(Component.literal("Shop chest not found.").withStyle(ChatFormatting.RED));
            return;
        }

        Item item = shop.item();
        if (item == null) {
            buyer.sendSystemMessage(Component.literal("This shop item is invalid.").withStyle(ChatFormatting.RED));
            return;
        }

        int amount = Math.max(1, shop.amount);
        if (countItem(chest, item) < amount) {
            buyer.sendSystemMessage(Component.literal("This shop is out of stock.").withStyle(ChatFormatting.RED));
            return;
        }

        if (!canFit(buyer.getInventory(), item, amount)) {
            buyer.sendSystemMessage(Component.literal("You need inventory space first.").withStyle(ChatFormatting.RED));
            return;
        }

        EconomyManager.TransactionResult withdraw = EconomyManager.withdraw(buyer, shop.price, "chest_shop_buy");
        if (!withdraw.success) {
            buyer.sendSystemMessage(Component.literal(withdraw.error).withStyle(ChatFormatting.RED));
            return;
        }

        int removed = removeItem(chest, item, amount);
        if (removed < amount) {
            addItem(chest, new ItemStack(item, removed));
            EconomyManager.deposit(buyer, shop.price, "chest_shop_refund_failed_stock");
            buyer.sendSystemMessage(Component.literal("Transaction failed because the stock changed. You were refunded.").withStyle(ChatFormatting.RED));
            return;
        }

        EconomyManager.deposit(shop.ownerUuid(), shop.ownerName, shop.price, "chest_shop_sale");
        addItem(buyer.getInventory(), new ItemStack(item, amount));
        chest.setChanged();
        buyer.getInventory().setChanged();

        buyer.sendSystemMessage(Component.literal("Bought " + amount + "x " + shop.itemName + " for " + EconomyManager.format(shop.price) + ".").withStyle(ChatFormatting.GREEN));
        notifyOwner(level.getServer(), shop.ownerUuid(), "Your shop sold " + amount + "x " + shop.itemName + " for " + EconomyManager.format(shop.price) + ".");
    }

    private static void sellToShop(ServerPlayer seller, ServerLevel level, BlockPos pos, ChestShopRegistry.ChestShop shop) {
        Container chest = getContainer(level, pos);
        if (chest == null) {
            seller.sendSystemMessage(Component.literal("Shop chest not found.").withStyle(ChatFormatting.RED));
            return;
        }

        Item item = shop.item();
        if (item == null) {
            seller.sendSystemMessage(Component.literal("This shop item is invalid.").withStyle(ChatFormatting.RED));
            return;
        }

        int amount = Math.max(1, shop.amount);
        if (countItem(seller.getInventory(), item) < amount) {
            seller.sendSystemMessage(Component.literal("You do not have " + amount + "x " + shop.itemName + ".").withStyle(ChatFormatting.RED));
            return;
        }

        if (!canFit(chest, item, amount)) {
            seller.sendSystemMessage(Component.literal("This buy shop chest is full.").withStyle(ChatFormatting.RED));
            return;
        }

        UUID ownerId = shop.ownerUuid();
        if (ownerId == null) {
            seller.sendSystemMessage(Component.literal("This shop owner is invalid.").withStyle(ChatFormatting.RED));
            return;
        }

        EconomyManager.TransactionResult withdrawOwner = EconomyManager.withdraw(ownerId, shop.ownerName, shop.price, "chest_shop_buy_order");
        if (!withdrawOwner.success) {
            seller.sendSystemMessage(Component.literal("This buy shop does not have enough owner funds right now.").withStyle(ChatFormatting.RED));
            return;
        }

        int removed = removeItem(seller.getInventory(), item, amount);
        if (removed < amount) {
            addItem(seller.getInventory(), new ItemStack(item, removed));
            EconomyManager.deposit(ownerId, shop.ownerName, shop.price, "chest_shop_refund_failed_seller_items");
            seller.sendSystemMessage(Component.literal("Transaction failed because your inventory changed. The owner was refunded.").withStyle(ChatFormatting.RED));
            return;
        }

        addItem(chest, new ItemStack(item, amount));
        EconomyManager.deposit(seller, shop.price, "chest_shop_sell_to_buy_order");
        chest.setChanged();
        seller.getInventory().setChanged();

        seller.sendSystemMessage(Component.literal("Sold " + amount + "x " + shop.itemName + " for " + EconomyManager.format(shop.price) + ".").withStyle(ChatFormatting.GREEN));
        notifyOwner(level.getServer(), ownerId, "Your buy shop purchased " + amount + "x " + shop.itemName + " for " + EconomyManager.format(shop.price) + ".");
    }

    private static Container getContainer(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    private static int countItem(Container container, Item item) {
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean canFit(Container container, Item item, int amount) {
        int remaining = amount;
        int maxStack = new ItemStack(item).getMaxStackSize();

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                remaining -= maxStack;
            } else if (stack.is(item) && stack.getCount() < Math.min(maxStack, stack.getMaxStackSize())) {
                remaining -= Math.min(maxStack, stack.getMaxStackSize()) - stack.getCount();
            }

            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    private static int removeItem(Container container, Item item, int amount) {
        int remaining = amount;
        int removed = 0;

        for (int slot = 0; slot < container.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !stack.is(item)) {
                continue;
            }

            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            } else {
                container.setItem(slot, stack);
            }
            remaining -= take;
            removed += take;
        }

        container.setChanged();
        return removed;
    }

    private static boolean addItem(Container container, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return true;
        }

        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !existing.is(remaining.getItem())) {
                continue;
            }

            int max = Math.min(existing.getMaxStackSize(), container.getMaxStackSize());
            int space = max - existing.getCount();
            if (space <= 0) {
                continue;
            }

            int move = Math.min(space, remaining.getCount());
            existing.grow(move);
            remaining.shrink(move);
            container.setItem(slot, existing);
        }

        for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (!existing.isEmpty()) {
                continue;
            }

            int move = Math.min(remaining.getMaxStackSize(), remaining.getCount());
            ItemStack insert = remaining.copy();
            insert.setCount(move);
            container.setItem(slot, insert);
            remaining.shrink(move);
        }

        container.setChanged();
        return remaining.isEmpty();
    }

    private static void notifyOwner(MinecraftServer server, UUID ownerId, String message) {
        if (server == null || ownerId == null || message == null) {
            return;
        }

        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
        }
    }
}
