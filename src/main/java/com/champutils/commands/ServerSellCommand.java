package com.champutils.commands;

import com.champutils.economy.EconomyManager;
import com.champutils.economy.SellPriceConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class ServerSellCommand {

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
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                            .executes(context -> sellHand(
                                                    context.getSource().getPlayerOrException(),
                                                    IntegerArgumentType.getInteger(context, "amount")
                                            ))))
                            .then(Commands.literal("all")
                                    .executes(context -> sellAll(context.getSource().getPlayerOrException())))
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
                            .executes(context -> sellAll(context.getSource().getPlayerOrException()))
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
                "§7Use §e/sell hand <amount> §7or §e/sell hand all§7 to sell from your held stack."
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

    private static int sellAll(ServerPlayer player) {
        if (!SellPriceConfig.isEnabled()) {
            player.sendSystemMessage(Component.literal("§cServer selling is currently disabled."));
            return 0;
        }

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
            inventory.items.set(i, ItemStack.EMPTY);
        }

        if (total <= 0L) {
            player.sendSystemMessage(Component.literal("§cYou do not have any items the server buys."));
            return 0;
        }

        EconomyManager.TransactionResult result = EconomyManager.deposit(player, total, "server_sell_all");
        if (!result.success) {
            player.sendSystemMessage(Component.literal("§c" + result.error));
            return 0;
        }

        inventory.setChanged();
        player.sendSystemMessage(Component.literal(
                "§aSold §f" + itemsSold + " items §7(" + stacksSold + " stacks§7) §afor §6" + EconomyManager.format(total) + "§a."
        ));
        player.sendSystemMessage(Component.literal("§7New Balance: §6" + EconomyManager.format(result.newBalance)));
        return 1;
    }
}
