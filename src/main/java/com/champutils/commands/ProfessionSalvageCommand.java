package com.champutils.commands;

import com.champutils.profession.ItemSafetyService;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
                            Commands.literal("fragments")
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
                                            Commands.literal("trade")
                                                    .then(
                                                            Commands.argument(
                                                                            "rarity",
                                                                            StringArgumentType.word()
                                                                    )
                                                                    .suggests((context, builder) -> {
                                                                        for (String rarity : ProfessionFragmentConfig.TRADES.keySet()) {
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
                                                                                        builder.suggest("sword");
                                                                                        builder.suggest("shovel");

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

                                                                                        return trade(
                                                                                                player,
                                                                                                rarity,
                                                                                                toolType
                                                                                        );
                                                                                    })
                                                                    )
                                                    )
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

        if (
                ItemSafetyService.requestConfirmationIfNeeded(
                        player,
                        stack,
                        "salvage",
                        "/salvage confirm"
                )
        ) {
            return 0;
        }

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
                                ProfessionFragmentManager.formatWords(result.fragmentKey()) +
                                " Fragment§a."
                )
        );

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
                                ProfessionFragmentManager.formatWords(result.fromFragment()) +
                                " Fragment §ato §d" +
                                result.output() +
                                "x " +
                                ProfessionFragmentManager.formatWords(result.toFragment()) +
                                " Fragment§a."
                )
        );

        return 1;
    }


    private static int trade(
            ServerPlayer player,
            String rarity,
            String toolType
    ) {
        ProfessionFragmentManager.TradeResult result =
                ProfessionFragmentManager.tradeForRandomTool(
                        player,
                        rarity,
                        toolType
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
                        "§aTraded §6" +
                                result.cost() +
                                "x " +
                                ProfessionFragmentManager.formatWords(result.fragmentKey()) +
                                " Fragment §afor an unidentified §f" +
                                result.displayName() +
                                " §7(" +
                                ProfessionFragmentManager.formatWords(result.rarity()) +
                                " " +
                                ProfessionFragmentManager.formatWords(result.toolType()) +
                                ")."
                )
        );

        return 1;
    }

    private static int listFragments(
            ServerPlayer player
    ) {
        player.sendSystemMessage(
                Component.literal(
                        "§6Profession Fragments:"
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
