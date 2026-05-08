package com.champutils.commands;

import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;
import com.champutils.profession.ProfessionToolMetadata;
import com.champutils.profession.ProfessionToolRollService;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class ItemRollCommand {

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(
                            Commands.literal("itemroll")

                                    .then(
                                            Commands.literal("identify")
                                                    .executes(context -> {

                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        ItemStack stack =
                                                                player.getMainHandItem();

                                                        ProfessionToolRollService.RollResult result =
                                                                ProfessionToolRollService.identify(
                                                                        player,
                                                                        stack
                                                                );

                                                        if (!result.success) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§c" + result.error
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        ProfessionToolManager.refreshToolStack(
                                                                stack
                                                        );

                                                        player.sendSystemMessage(
                                                                ProfessionToolRollService.buildSuccessMessage(
                                                                        result
                                                                )
                                                        );

                                                        return 1;
                                                    })
                                    )

                                    .then(
                                            Commands.literal("reroll")
                                                    .executes(context -> {

                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        ItemStack stack =
                                                                player.getMainHandItem();

                                                        ProfessionToolRollService.RollResult result =
                                                                ProfessionToolRollService.reroll(
                                                                        player,
                                                                        stack
                                                                );

                                                        if (!result.success) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§c" + result.error
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        ProfessionToolManager.refreshToolStack(
                                                                stack
                                                        );

                                                        player.sendSystemMessage(
                                                                ProfessionToolRollService.buildSuccessMessage(
                                                                        result
                                                                )
                                                        );

                                                        return 1;
                                                    })
                                    )

                                    .then(
                                            Commands.literal("iteminfo")
                                                    .executes(context -> {

                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        ItemStack stack =
                                                                player.getMainHandItem();

                                                        if (
                                                                stack == null ||
                                                                        stack.isEmpty()
                                                        ) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cHold a rollable item first."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        String toolId =
                                                                ProfessionToolMetadata.getToolId(
                                                                        stack
                                                                );

                                                        if (
                                                                toolId == null ||
                                                                        toolId.isBlank()
                                                        ) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cThis is not a rollable item."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        ProfessionToolConfig.ToolData toolData =
                                                                ProfessionToolConfig.TOOLS.get(
                                                                        toolId
                                                                );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§6Item Roll Info"
                                                                )
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Item ID: §f" +
                                                                                toolId
                                                                )
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Name: §f" +
                                                                                ProfessionToolConfig.getDisplayName(
                                                                                        toolId,
                                                                                        toolData
                                                                                )
                                                                )
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Identified: §f" +
                                                                                ProfessionToolMetadata.isIdentified(
                                                                                        stack
                                                                                )
                                                                )
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Rerolls: §f" +
                                                                                ProfessionToolMetadata.getRerolls(
                                                                                        stack
                                                                                )
                                                                )
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Quality: §e" +
                                                                                ProfessionToolMetadata.getQuality(
                                                                                        stack
                                                                                ) +
                                                                                "%"
                                                                )
                                                        );

                                                        Map<String, Double> stats =
                                                                ProfessionToolMetadata.getRolledStats(
                                                                        stack
                                                                );

                                                        if (stats.isEmpty()) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§7Rolled Stats: §8None"
                                                                    )
                                                            );
                                                        } else {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§7Rolled Stats:"
                                                                    )
                                                            );

                                                            for (
                                                                    Map.Entry<String, Double> entry :
                                                                    stats.entrySet()
                                                            ) {
                                                                player.sendSystemMessage(
                                                                        Component.literal(
                                                                                " §a" +
                                                                                        entry.getKey() +
                                                                                        ": +" +
                                                                                        entry.getValue() +
                                                                                        "%"
                                                                        )
                                                                );
                                                            }
                                                        }

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Identify Cost: §6$" +
                                                                                ProfessionToolRollService.getIdentifyCost(
                                                                                        stack
                                                                                )
                                                                )
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Next Reroll Cost: §6$" +
                                                                                ProfessionToolRollService.getRerollCost(
                                                                                        stack
                                                                                )
                                                                )
                                                        );

                                                        return 1;
                                                    })
                                    )
                    );
                }
        );
    }
}