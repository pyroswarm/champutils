package com.champutils.commands;

import com.champutils.genesis.MegaShopConfig;
import com.champutils.menu.MegaShopMenu;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class MegaShopCommand {
    private MegaShopCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                Commands.literal("megashop")
                        .requires(source -> source.hasPermission(2) || com.champutils.permissions.PermissionUtil.has(source, "champutils.staff"))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            MegaShopMenu.open(player);
                            return 1;
                        })
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    MegaShopConfig.load();
                                    context.getSource().sendSuccess(() -> Component.literal("§aReloaded mega_shop.json."), false);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("save")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> {
                                    MegaShopConfig.save();
                                    context.getSource().sendSuccess(() -> Component.literal("§aSaved mega_shop.json."), false);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.0D))
                                                .then(Commands.argument("category", StringArgumentType.greedyString())
                                                        .executes(context -> {
                                                            String item = StringArgumentType.getString(context, "item");
                                                            double price = DoubleArgumentType.getDouble(context, "price");
                                                            String category = StringArgumentType.getString(context, "category");
                                                            MegaShopConfig.ShopEntry entry = new MegaShopConfig.ShopEntry();
                                                            entry.id = item.contains(":") ? item : "cobblemon:" + item;
                                                            entry.displayName = entry.id.substring(entry.id.indexOf(':') + 1).replace('_', ' ');
                                                            entry.category = category;
                                                            entry.priceCredits = price;
                                                            entry.amount = 1;
                                                            entry.icon = entry.id;
                                                            entry.available = true;
                                                            entry.lore.add("§7Added in-game by an operator.");
                                                            MegaShopConfig.CONFIG.entries.removeIf(e -> e != null && e.id != null && e.id.equalsIgnoreCase(entry.id));
                                                            MegaShopConfig.CONFIG.entries.add(entry);
                                                            MegaShopConfig.save();
                                                            context.getSource().sendSuccess(() -> Component.literal("§aAdded/updated §f" + entry.id + " §afor §6" + price + " credits§a."), false);
                                                            return 1;
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("remove")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .executes(context -> {
                                            String item = StringArgumentType.getString(context, "item");
                                            String id = item.contains(":") ? item : "cobblemon:" + item;
                                            boolean removed = MegaShopConfig.CONFIG.entries.removeIf(e -> e != null && e.id != null && e.id.equalsIgnoreCase(id));
                                            MegaShopConfig.save();
                                            context.getSource().sendSuccess(() -> Component.literal(removed ? "§aRemoved §f" + id + "§a." : "§cNo mega shop item matched §f" + id), false);
                                            return removed ? 1 : 0;
                                        })
                                )
                        )
                        .then(Commands.literal("price")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> {
                                                    String item = StringArgumentType.getString(context, "item");
                                                    String id = item.contains(":") ? item : "cobblemon:" + item;
                                                    double price = DoubleArgumentType.getDouble(context, "price");
                                                    boolean changed = false;
                                                    for (MegaShopConfig.ShopEntry entry : MegaShopConfig.CONFIG.entries) {
                                                        if (entry != null && entry.id != null && entry.id.equalsIgnoreCase(id)) { entry.priceCredits = price; changed = true; }
                                                    }
                                                    if (changed) MegaShopConfig.save();
                                                    final boolean ok = changed;
                                                    context.getSource().sendSuccess(() -> Component.literal(ok ? "§aUpdated price for §f" + id + "§a." : "§cNo mega shop item matched §f" + id), false);
                                                    return ok ? 1 : 0;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("amount")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                                .executes(context -> {
                                                    String item = StringArgumentType.getString(context, "item");
                                                    String id = item.contains(":") ? item : "cobblemon:" + item;
                                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                                    boolean changed = false;
                                                    for (MegaShopConfig.ShopEntry entry : MegaShopConfig.CONFIG.entries) {
                                                        if (entry != null && entry.id != null && entry.id.equalsIgnoreCase(id)) { entry.amount = amount; changed = true; }
                                                    }
                                                    if (changed) MegaShopConfig.save();
                                                    final boolean ok = changed;
                                                    context.getSource().sendSuccess(() -> Component.literal(ok ? "§aUpdated amount for §f" + id + "§a." : "§cNo mega shop item matched §f" + id), false);
                                                    return ok ? 1 : 0;
                                                })
                                        )
                                )
                        )
        ));
    }
}
