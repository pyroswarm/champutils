package com.champutils.commands;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.economy.EconomyManager;
import com.champutils.profession.ItemSafetyService;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.menu.FragmentCraftingMenu;
import com.champutils.menu.ProfessionCurrencyInventoryMenu;
import com.champutils.menu.ConfirmationMenu;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProfessionSalvageCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {
                    dispatcher.register(
                            Commands.literal("salvage")
                                    .executes(context -> {
                                        ServerPlayer player =
                                                context.getSource()
                                                        .getPlayerOrException();

                                        return trySalvage(
                                                player,
                                                false
                                        );
                                    })
                                    .then(
                                            Commands.literal("confirm")
                                                    .executes(context -> {
                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        return trySalvage(
                                                                player,
                                                                true
                                                        );
                                                    })
                                    )
                    );

                    dispatcher.register(
                            Commands.literal("chunks")
                                    .executes(context -> {
                                        ServerPlayer player =
                                                context.getSource()
                                                        .getPlayerOrException();

                                        ProfessionCurrencyInventoryMenu.openChunks(player);
                                        return 1;
                                    })
                                    .then(
                                            Commands.literal("inventory")
                                                    .executes(context -> {
                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        ProfessionCurrencyInventoryMenu.openChunks(player);
                                                        return 1;
                                                    })
                                    )
                    );

                    dispatcher.register(
                            Commands.literal("essence")
                                    .then(
                                            Commands.literal("upgrade")
                                                    .then(
                                                            Commands.argument(
                                                                            "upgradeId",
                                                                            StringArgumentType.word()
                                                                    )
                                                                    .suggests((context, builder) -> {
                                                                        for (String upgradeId : ProfessionFragmentConfig.UPGRADES.keySet()) {
                                                                            builder.suggest(upgradeId);
                                                                        }

                                                                        return builder.buildFuture();
                                                                    })
                                                                    .executes(context -> {
                                                                        ServerPlayer player =
                                                                                context.getSource()
                                                                                        .getPlayerOrException();

                                                                        String upgradeId =
                                                                                StringArgumentType.getString(
                                                                                        context,
                                                                                        "upgradeId"
                                                                                );

                                                                        return upgrade(
                                                                                player,
                                                                                upgradeId
                                                                        );
                                                                    })
                                                    )
                                    )
                                    .then(
                                            Commands.literal("craft")
                                                    .then(
                                                            Commands.argument(
                                                                            "rarity",
                                                                            StringArgumentType.word()
                                                                    )
                                                                    .suggests((context, builder) -> {
                                                                        for (String rarity : ProfessionFragmentConfig.TOOL_CRAFTING.keySet()) {
                                                                            builder.suggest(rarity.toLowerCase());
                                                                        }

                                                                        return builder.buildFuture();
                                                                    })
                                                                    .then(
                                                                            Commands.argument(
                                                                                            "toolType",
                                                                                            StringArgumentType.word()
                                                                                    )
                                                                                    .suggests((context, builder) -> {
                                                                                        builder.suggest("pickaxe");
                                                                                        builder.suggest("axe");
                                                                                        builder.suggest("hoe");
                                                                                        builder.suggest("shovel");
                                                                                        builder.suggest("helmet");
                                                                                        builder.suggest("chestplate");
                                                                                        builder.suggest("leggings");
                                                                                        builder.suggest("boots");
                                                                                        builder.suggest("magnet");
                                                                                        builder.suggest("shiny_charm");
                                                                                        builder.suggest("trinket_pouch");

                                                                                        return builder.buildFuture();
                                                                                    })
                                                                                    .executes(context -> {
                                                                                        ServerPlayer player =
                                                                                                context.getSource()
                                                                                                        .getPlayerOrException();

                                                                                        String rarity =
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "rarity"
                                                                                                );

                                                                                        String toolType =
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "toolType"
                                                                                                );

                                                                                        return craft(
                                                                                                player,
                                                                                                rarity,
                                                                                                toolType
                                                                                        );
                                                                                    })
                                                                    )
                                                    )
                                    )
                                    .then(
                                            Commands.literal("trade")
                                                    .then(
                                                            Commands.argument(
                                                                            "rarity",
                                                                            StringArgumentType.word()
                                                                    )
                                                                    .suggests((context, builder) -> {
                                                                        for (String rarity : ProfessionFragmentConfig.TOOL_CRAFTING.keySet()) {
                                                                            builder.suggest(rarity.toLowerCase());
                                                                        }

                                                                        return builder.buildFuture();
                                                                    })
                                                                    .then(
                                                                            Commands.argument(
                                                                                            "toolType",
                                                                                            StringArgumentType.word()
                                                                                    )
                                                                                    .suggests((context, builder) -> {
                                                                                        builder.suggest("pickaxe");
                                                                                        builder.suggest("axe");
                                                                                        builder.suggest("hoe");
                                                                                        builder.suggest("shovel");
                                                                                        builder.suggest("helmet");
                                                                                        builder.suggest("chestplate");
                                                                                        builder.suggest("leggings");
                                                                                        builder.suggest("boots");
                                                                                        builder.suggest("magnet");
                                                                                        builder.suggest("shiny_charm");
                                                                                        builder.suggest("trinket_pouch");

                                                                                        return builder.buildFuture();
                                                                                    })
                                                                                    .executes(context -> {
                                                                                        ServerPlayer player =
                                                                                                context.getSource()
                                                                                                        .getPlayerOrException();

                                                                                        String rarity =
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "rarity"
                                                                                                );

                                                                                        String toolType =
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "toolType"
                                                                                                );

                                                                                        return craft(
                                                                                                player,
                                                                                                rarity,
                                                                                                toolType
                                                                                        );
                                                                                    })
                                                                    )
                                                    )
                                    )
                                    .then(
                                            Commands.literal("withdraw")
                                                    .then(
                                                            Commands.argument(
                                                                            "rarity",
                                                                            StringArgumentType.word()
                                                                    )
                                                                    .suggests((context, builder) -> {
                                                                        for (String rarity : ProfessionFragmentConfig.FRAGMENTS.keySet()) {
                                                                            builder.suggest(rarity.toLowerCase());
                                                                        }

                                                                        return builder.buildFuture();
                                                                    })
                                                                    .then(
                                                                            Commands.argument(
                                                                                            "amount",
                                                                                            IntegerArgumentType.integer(1, 2304)
                                                                                    )
                                                                                    .executes(context -> {
                                                                                        ServerPlayer player =
                                                                                                context.getSource()
                                                                                                        .getPlayerOrException();

                                                                                        String rarity =
                                                                                                StringArgumentType.getString(
                                                                                                        context,
                                                                                                        "rarity"
                                                                                                );

                                                                                        int amount =
                                                                                                IntegerArgumentType.getInteger(
                                                                                                        context,
                                                                                                        "amount"
                                                                                                );

                                                                                        return withdraw(
                                                                                                player,
                                                                                                rarity,
                                                                                                amount
                                                                                        );
                                                                                    })
                                                                    )
                                                    )
                                    )
                                    .then(
                                            Commands.literal("menu")
                                                    .executes(context -> {
                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        FragmentCraftingMenu.open(player);
                                                        return 1;
                                                    })
                                    )
                                    .then(
                                            Commands.literal("inventory")
                                                    .executes(context -> {
                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        ProfessionCurrencyInventoryMenu.openFragments(player);
                                                        return 1;
                                                    })
                                    )
                                    .then(
                                            Commands.literal("list")
                                                    .executes(context -> {
                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        return listFragments(player);
                                                    })
                                    )
                    );
                }
        );
    }

    private static int trySalvage(
            ServerPlayer player,
            boolean confirm
    ) {
        ItemStack stack =
                player.getMainHandItem();

        if (
                ItemSafetyService.blockIfLocked(
                        player,
                        stack,
                        "salvage it"
                )
        ) {
            return 0;
        }

        if (!confirm) {
            java.util.List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.literal("§7This will permanently salvage the item in your main hand."));
            String riskReason = ItemSafetyService.getRiskReason(stack);
            if (riskReason != null) {
                lore.add(Component.literal("§cCareful: " + riskReason + "."));
            }
            lore.add(Component.literal("§cThis cannot be undone."));
            ConfirmationMenu.open(
                    player,
                    "Confirm Salvage",
                    stack == null || stack.isEmpty() ? Items.ANVIL : stack.getItem(),
                    "§eSalvage Item",
                    lore,
                    () -> trySalvage(player, true),
                    () -> player.sendSystemMessage(Component.literal("§eSalvage cancelled."))
            );
            return 1;
        }

        ItemSafetyService.clear(player, "salvage");

        ProfessionFragmentManager.SalvageResult result =
                ProfessionFragmentManager.salvageHeldTool(
                        player,
                        stack
                );

        if (!result.success()) {
            player.sendSystemMessage(
                    Component.literal(
                            "§c" + result.error()
                    )
            );

            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§aSalvaged §f" +
                                result.displayName() +
                                " §7(" +
                                ProfessionFragmentManager.formatWords(result.rarity()) +
                                ") §afor §6" +
                                result.amount() +
                                "x " +
                                ProfessionFragmentManager.displayRankName(result.fragmentKey()) +
                                " Essence§a."
                )
        );
        if ("F".equalsIgnoreCase(result.rarity())) {
            AdventureGuideManager.increment(player, "salvage_common_tool", 1);
        }

        return 1;
    }

    private static int upgrade(
            ServerPlayer player,
            String upgradeId
    ) {
        ProfessionFragmentManager.UpgradeResult result =
                ProfessionFragmentManager.upgrade(
                        player,
                        upgradeId
                );

        if (!result.success()) {
            player.sendSystemMessage(
                    Component.literal(
                            "§c" + result.error()
                    )
            );

            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§aUpgraded §6" +
                                result.cost() +
                                "x " +
                                ProfessionFragmentManager.displayRankName(result.fromFragment()) +
                                " Essence §ato §d" +
                                result.output() +
                                "x " +
                                ProfessionFragmentManager.displayRankName(result.toFragment()) +
                                " Essence§a."
                )
        );

        return 1;
    }


    private static int craft(ServerPlayer player, String rarity, String toolType) {
        String normalizedRarity = com.champutils.profession.ProfessionFragmentConfig.normalizeRarity(rarity);
        long creditCost = ProfessionFragmentManager.craftCreditCost(normalizedRarity);
        long cents = EconomyManager.wholeCreditsToCents(creditCost);
        EconomyManager.withdrawAsync(player, cents, "profession_craft:" + normalizedRarity.toLowerCase() + ":" + toolType).thenAccept(charge ->
                player.server.execute(() -> {
                    if (!charge.success) {
                        player.sendSystemMessage(Component.literal("§c" + (charge.error == null ? "Could not remove Credits." : charge.error)));
                        return;
                    }
                    ProfessionFragmentManager.CraftResult result = ProfessionFragmentManager.craftRandomUnidentifiedTool(player, rarity, toolType);
                    if (!result.success()) {
                        EconomyManager.depositAsync(player, cents, "profession_craft_refund:" + normalizedRarity.toLowerCase() + ":" + toolType);
                        player.sendSystemMessage(Component.literal("§c" + result.error() + " Credits were refunded."));
                        return;
                    }
                    player.sendSystemMessage(Component.literal("§aCrafted using §6" + result.cost() + "x " + ProfessionFragmentManager.displayRankName(result.fragmentKey()) + " Essence §a+ §6" + EconomyManager.formatWholeCredits(result.creditCost()) + "§a. Result: §f" + ProfessionFragmentManager.formatWords(result.toolType()) + "§a."));
                    AdventureGuideManager.markIntroECraft(player, result.rarity(), result.toolType());
                }));
        return 1;
    }

    private static int withdraw(
            ServerPlayer player,
            String rarity,
            int amount
    ) {
        ProfessionFragmentManager.WithdrawResult result =
                ProfessionFragmentManager.withdrawFragments(
                        player,
                        rarity,
                        amount
                );

        if (!result.success()) {
            player.sendSystemMessage(
                    Component.literal(
                            "§c" + result.error()
                    )
            );

            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§aWithdrew §6" +
                                result.amount() +
                                "x " +
                                ProfessionFragmentManager.displayRankName(result.fragmentKey()) +
                                " Essence§a."
                )
        );

        return 1;
    }


    private static int listFragments(
            ServerPlayer player
    ) {
        player.sendSystemMessage(
                Component.literal(
                        "§6Profession Essences:"
                )
        );

        for (String fragmentKey : ProfessionFragmentConfig.FRAGMENTS.keySet()) {
            int amount =
                    ProfessionFragmentManager.countFragments(
                            player,
                            fragmentKey
                    );

            player.sendSystemMessage(
                    Component.literal(
                            "§7- §f" +
                                    ProfessionFragmentManager.formatWords(fragmentKey) +
                                    ": §e" +
                                    amount
                    )
            );
        }

        return 1;
    }
}
