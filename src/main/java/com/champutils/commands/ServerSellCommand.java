package com.champutils.commands;

import com.champutils.economy.EconomyManager;
import com.champutils.economy.SellPriceConfig;
import com.champutils.menu.ConfirmationMenu;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ServerSellCommand {

    private static final long CONFIRM_WINDOW_MILLIS = 30_000L;
    private static final Map<UUID, PendingSellPreview> PENDING_PREVIEWS = new ConcurrentHashMap<>();

    private ServerSellCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("sell")
                            .then(Commands.literal("hand")
                                    .executes(context -> sellHand(context.getSource().getPlayerOrException(), 1))
                                    .then(Commands.literal("all")
                                            .executes(context -> sellHand(context.getSource().getPlayerOrException(), -1)))
                                    .then(Commands.literal("inventory")
                                            .executes(context -> previewSellHandInventory(context.getSource().getPlayerOrException()))
                                            .then(Commands.literal("confirm")
                                                    .executes(context -> sellHandInventory(context.getSource().getPlayerOrException()))))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                            .executes(context -> sellHand(
                                                    context.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(context, "amount")
                                            ))))
                            .then(Commands.literal("all")
                                    .executes(context -> previewSellAll(context.getSource().getPlayerOrException()))
                                    .then(Commands.literal("confirm")
                                            .executes(context -> sellAll(context.getSource().getPlayerOrException()))))
                            .then(Commands.literal("price")
                                    .executes(context -> showPrice(context.getSource().getPlayerOrException())))
            );

            dispatcher.register(
                    Commands.literal("sellhand")
                            .executes(context -> sellHand(context.getSource().getPlayerOrException(), 1))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                    .executes(context -> sellHand(
                                            context.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(context, "amount")
                                    )))
            );

            dispatcher.register(
                    Commands.literal("sellall")
                            .executes(context -> previewSellAll(context.getSource().getPlayerOrException()))
                            .then(Commands.literal("confirm")
                                    .executes(context -> sellAll(context.getSource().getPlayerOrException())))
            );
        });
    }

    private static int showPrice(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (stack == null || stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cHold an item to check its server sell price."));
            return 0;
        }

        long unit = SellPriceConfig.getUnitPrice(stack);
        if (unit <= 0L) {
            player.sendSystemMessage(Component.literal("§cThe server does not buy this item."));
            return 0;
        }

        player.sendSystemMessage(Component.literal(
                "§7Server price for §f" + stack.getHoverName().getString() + "§7: §6" + EconomyManager.format(unit) + " each§7."
        ));
        player.sendSystemMessage(Component.literal(
                "§7This stack is worth: §6" + EconomyManager.format(unit * stack.getCount()) + "§7."
        ));
        player.sendSystemMessage(Component.literal(
                "§7Use §e/sell hand <amount>§7, §e/sell hand all§7, or §e/sell hand inventory§7."
        ));
        return 1;
    }

    private static int sellHand(ServerPlayer player, int requestedAmount) {
        if (!SellPriceConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cServer selling is currently disabled."));
            return 0;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack == null || stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cHold an item to sell it."));
            return 0;
        }

        long unit = SellPriceConfig.getUnitPrice(stack);
        if (unit <= 0L) {
            player.sendSystemMessage(Component.literal("§cThe server does not buy this item."));
            return 0;
        }

        int amount = requestedAmount <= 0 ? stack.getCount() : Math.min(requestedAmount, stack.getCount());
        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("§cYou do not have enough of that item to sell."));
            return 0;
        }

        long total = unit * amount;

        EconomyManager.TransactionResult result = EconomyManager.deposit(player, total, "server_sell:" + SellPriceConfig.getItemId(stack));
        if (!result.success) {
            player.sendSystemMessage(Component.literal("§c" + result.error));
            return 0;
        }

        String name = stack.getHoverName().getString();
        stack.shrink(amount);
        player.getInventory().setChanged();

        player.sendSystemMessage(Component.literal(
                "§aSold §f" + amount + "x " + name + " §afor §6" + EconomyManager.format(total) + "§a."
        ));
        player.sendSystemMessage(Component.literal("§7New Balance: §6" + EconomyManager.format(result.newBalance)));
        return 1;
    }

    private static int previewSellHandInventory(ServerPlayer player) {
        if (!SellPriceConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cServer selling is currently disabled."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cHold an item to preview selling all matching inventory stacks."));
            return 0;
        }

        long unit = SellPriceConfig.getUnitPrice(held);
        if (unit <= 0L) {
            player.sendSystemMessage(Component.literal("§cThe server does not buy this item."));
            return 0;
        }

        HandInventoryPreview preview = scanMatchingInventory(player, held);
        if (preview.amount <= 0 || preview.total <= 0L) {
            player.sendSystemMessage(Component.literal("§cYou do not have any matching items to sell."));
            return 0;
        }

        String itemId = SellPriceConfig.getItemId(held);
        String name = held.getHoverName().getString();
        PENDING_PREVIEWS.put(player.getUUID(), PendingSellPreview.handInventory(itemId, preview.amount, preview.stacks, preview.total));

        ConfirmationMenu.open(
                player,
                "Confirm Sale",
                held.getItem(),
                "§eSell Matching Inventory",
                new String[]{
                        "§7Item: §f" + name,
                        "§7Amount: §f" + preview.amount + "x §8(" + preview.stacks + " stacks)",
                        "§7Total: §6" + EconomyManager.format(preview.total),
                        "§cThis cannot be undone."
                },
                () -> sellHandInventory(player),
                () -> {
                    PENDING_PREVIEWS.remove(player.getUUID());
                    player.sendSystemMessage(Component.literal("§eSale cancelled."));
                }
        );
        return 1;
    }

    private static int sellHandInventory(ServerPlayer player) {
        if (!SellPriceConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cServer selling is currently disabled."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cHold an item to sell all matching inventory stacks."));
            return 0;
        }

        long unit = SellPriceConfig.getUnitPrice(held);
        if (unit <= 0L) {
            player.sendSystemMessage(Component.literal("§cThe server does not buy this item."));
            return 0;
        }

        HandInventoryPreview preview = scanMatchingInventory(player, held);
        int amount = preview.amount;
        int stacks = preview.stacks;
        long total = preview.total;
        String itemId = SellPriceConfig.getItemId(held);

        if (amount <= 0 || total <= 0L) {
            player.sendSystemMessage(Component.literal("§cYou do not have any matching items to sell."));
            return 0;
        }

        PendingSellPreview pending = PENDING_PREVIEWS.get(player.getUUID());
        if (!isValidPending(pending, PendingSellType.HAND_INVENTORY, itemId, amount, stacks, total)) {
            PENDING_PREVIEWS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§cPlease preview this sale first with §e/sell hand inventory§c, then confirm in the UI."));
            return 0;
        }
        PENDING_PREVIEWS.remove(player.getUUID());

        EconomyManager.TransactionResult result = EconomyManager.deposit(player, total, "server_sell_hand_inventory:" + itemId);
        if (!result.success) {
            player.sendSystemMessage(Component.literal("§c" + result.error));
            return 0;
        }

        String name = held.getHoverName().getString();
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack == null || stack.isEmpty() || !ItemStack.isSameItemSameComponents(held, stack)) {
                continue;
            }
            inventory.items.set(i, ItemStack.EMPTY);
        }

        inventory.setChanged();
        player.sendSystemMessage(Component.literal(
                "§aSold §f" + amount + "x " + name + " §7(" + stacks + " stacks§7) §afor §6" + EconomyManager.format(total) + "§a."
        ));
        player.sendSystemMessage(Component.literal("§7New Balance: §6" + EconomyManager.format(result.newBalance)));
        return 1;
    }

    private static int previewSellAll(ServerPlayer player) {
        if (!SellPriceConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cServer selling is currently disabled."));
            return 0;
        }

        SellAllPreview preview = scanSellableInventory(player);
        if (preview.total <= 0L) {
            player.sendSystemMessage(Component.literal("§cYou do not have any items the server buys."));
            return 0;
        }

        PENDING_PREVIEWS.put(player.getUUID(), PendingSellPreview.sellAll(preview.itemsSold, preview.stacksSold, preview.total));
        ConfirmationMenu.open(
                player,
                "Confirm Sell All",
                Items.EMERALD,
                "§eSell All Sellable Items",
                new String[]{
                        "§7Items: §f" + preview.itemsSold,
                        "§7Stacks: §f" + preview.stacksSold,
                        "§7Total: §6" + EconomyManager.format(preview.total),
                        "§cThis cannot be undone."
                },
                () -> sellAll(player),
                () -> {
                    PENDING_PREVIEWS.remove(player.getUUID());
                    player.sendSystemMessage(Component.literal("§eSale cancelled."));
                }
        );
        return 1;
    }

    private static int sellAll(ServerPlayer player) {
        if (!SellPriceConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cServer selling is currently disabled."));
            return 0;
        }

        SellAllPreview preview = scanSellableInventory(player);
        if (preview.total <= 0L) {
            player.sendSystemMessage(Component.literal("§cYou do not have any items the server buys."));
            return 0;
        }

        PendingSellPreview pending = PENDING_PREVIEWS.get(player.getUUID());
        if (!isValidPending(pending, PendingSellType.SELL_ALL, null, preview.itemsSold, preview.stacksSold, preview.total)) {
            PENDING_PREVIEWS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("§cPlease preview this sale first with §e/sellall §cor §e/sell all§c, then confirm in the UI."));
            return 0;
        }
        PENDING_PREVIEWS.remove(player.getUUID());

        EconomyManager.TransactionResult result = EconomyManager.deposit(player, preview.total, "server_sell_all");
        if (!result.success) {
            player.sendSystemMessage(Component.literal("§c" + result.error));
            return 0;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            long unit = SellPriceConfig.getUnitPrice(stack);
            if (unit <= 0L || unit * stack.getCount() <= 0L) {
                continue;
            }

            inventory.items.set(i, ItemStack.EMPTY);
        }

        inventory.setChanged();
        player.sendSystemMessage(Component.literal(
                "§aSold §f" + preview.itemsSold + " items §7(" + preview.stacksSold + " stacks§7) §afor §6" + EconomyManager.format(preview.total) + "§a."
        ));
        player.sendSystemMessage(Component.literal("§7New Balance: §6" + EconomyManager.format(result.newBalance)));
        return 1;
    }


    private static HandInventoryPreview scanMatchingInventory(ServerPlayer player, ItemStack held) {
        Inventory inventory = player.getInventory();
        long unit = SellPriceConfig.getUnitPrice(held);
        long total = 0L;
        int amount = 0;
        int stacks = 0;

        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack == null || stack.isEmpty() || !ItemStack.isSameItemSameComponents(held, stack)) {
                continue;
            }
            int count = stack.getCount();
            long value = unit * count;
            if (value <= 0L) {
                continue;
            }
            amount += count;
            stacks++;
            total += value;
        }

        return new HandInventoryPreview(total, stacks, amount);
    }

    private static boolean isValidPending(PendingSellPreview pending, PendingSellType type, String itemId, int items, int stacks, long total) {
        if (pending == null || pending.type != type) {
            return false;
        }
        if (System.currentTimeMillis() > pending.expiresAtMillis) {
            return false;
        }
        if (pending.items != items || pending.stacks != stacks || pending.total != total) {
            return false;
        }
        if (type == PendingSellType.HAND_INVENTORY) {
            return pending.itemId != null && pending.itemId.equals(itemId);
        }
        return true;
    }

    private static SellAllPreview scanSellableInventory(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        long total = 0L;
        int stacksSold = 0;
        int itemsSold = 0;

        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            long unit = SellPriceConfig.getUnitPrice(stack);
            if (unit <= 0L) {
                continue;
            }

            int count = stack.getCount();
            long value = unit * count;
            if (value <= 0L) {
                continue;
            }

            total += value;
            itemsSold += count;
            stacksSold++;
        }

        return new SellAllPreview(total, stacksSold, itemsSold);
    }

    private record HandInventoryPreview(long total, int stacks, int amount) {
    }

    private record SellAllPreview(long total, int stacksSold, int itemsSold) {
    }

    private enum PendingSellType {
        HAND_INVENTORY,
        SELL_ALL
    }

    private static final class PendingSellPreview {
        private final PendingSellType type;
        private final String itemId;
        private final int items;
        private final int stacks;
        private final long total;
        private final long expiresAtMillis;

        private PendingSellPreview(PendingSellType type, String itemId, int items, int stacks, long total) {
            this.type = type;
            this.itemId = itemId;
            this.items = items;
            this.stacks = stacks;
            this.total = total;
            this.expiresAtMillis = System.currentTimeMillis() + CONFIRM_WINDOW_MILLIS;
        }

        private static PendingSellPreview handInventory(String itemId, int items, int stacks, long total) {
            return new PendingSellPreview(PendingSellType.HAND_INVENTORY, itemId, items, stacks, total);
        }

        private static PendingSellPreview sellAll(int items, int stacks, long total) {
            return new PendingSellPreview(PendingSellType.SELL_ALL, null, items, stacks, total);
        }
    }
}
