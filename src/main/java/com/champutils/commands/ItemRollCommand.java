package com.champutils.commands;

import com.champutils.economy.EconomyCraftHook;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;
import com.champutils.profession.ProfessionToolAnnouncementManager;
import com.champutils.profession.ProfessionToolMetadata;
import com.champutils.profession.ProfessionToolRollService;
import com.champutils.profession.ItemSafetyService;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
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

                                                        long cost =
                                                                ProfessionToolRollService.getIdentifyCost(
                                                                        stack
                                                                );

                                                        EconomyCraftHook.AffordResult affordResult =
                                                                EconomyCraftHook.canAfford(
                                                                        player,
                                                                        cost
                                                                );

                                                        if (!affordResult.success) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§c" + affordResult.error
                                                                    )
                                                            );

                                                            return 0;
                                                        }

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

                                                        EconomyCraftHook.ChargeResult chargeResult =
                                                                EconomyCraftHook.withdraw(
                                                                        player,
                                                                        cost
                                                                );

                                                        if (!chargeResult.success) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§c" + chargeResult.error
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        ProfessionToolManager.refreshToolStack(
                                                                stack
                                                        );

                                                        ProfessionToolAnnouncementManager.announcePerfectRollIfNeeded(
                                                                player,
                                                                stack,
                                                                result.quality
                                                        );

                                                        player.sendSystemMessage(
                                                                ProfessionToolRollService.buildSuccessMessage(
                                                                        result
                                                                )
                                                        );

                                                        if (cost > 0L) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§7Paid §6" +
                                                                                    EconomyCraftHook.formatMoney(
                                                                                            cost
                                                                                    ) +
                                                                                    "§7. New Balance: §6" +
                                                                                    EconomyCraftHook.formatMoney(
                                                                                            chargeResult.newBalance
                                                                                    )
                                                                    )
                                                            );
                                                        }

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

                                                        if (
                                                                ItemSafetyService.blockIfLocked(
                                                                        player,
                                                                        stack,
                                                                        "reroll it"
                                                                )
                                                        ) {
                                                            return 0;
                                                        }

                                                        if (
                                                                ItemSafetyService.requestConfirmationIfNeeded(
                                                                        player,
                                                                        stack,
                                                                        "reroll",
                                                                        "/itemroll reroll confirm"
                                                                )
                                                        ) {
                                                            return 0;
                                                        }

                                                        return executeReroll(
                                                                player,
                                                                stack
                                                        );
                                                    })
                                                    .then(
                                                            Commands.literal("confirm")
                                                                    .executes(context -> {

                                                                        ServerPlayer player =
                                                                                context.getSource()
                                                                                        .getPlayerOrException();

                                                                        ItemStack stack =
                                                                                player.getMainHandItem();

                                                                        if (
                                                                                ItemSafetyService.blockIfLocked(
                                                                                        player,
                                                                                        stack,
                                                                                        "reroll it"
                                                                                )
                                                                        ) {
                                                                            return 0;
                                                                        }

                                                                        if (
                                                                                ItemSafetyService.requestConfirmationIfNeeded(
                                                                                        player,
                                                                                        stack,
                                                                                        "reroll",
                                                                                        "/itemroll reroll confirm"
                                                                                )
                                                                        ) {
                                                                            return 0;
                                                                        }

                                                                        return executeReroll(
                                                                                player,
                                                                                stack
                                                                        );
                                                                    })
                                                    )
                                    )


                                    .then(
                                            Commands.literal("repair")
                                                    .executes(context -> {

                                                        ServerPlayer player =
                                                                context.getSource()
                                                                        .getPlayerOrException();

                                                        ItemStack stack =
                                                                player.getMainHandItem();

                                                        if (
                                                                stack == null ||
                                                                        stack.isEmpty() ||
                                                                        !ProfessionToolMetadata.isProfessionTool(stack)
                                                        ) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cHold a profession item to repair."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        String toolId =
                                                                ProfessionToolMetadata.getToolId(
                                                                        stack
                                                                );

                                                        ProfessionToolConfig.ToolData toolData =
                                                                ProfessionToolConfig.TOOLS.get(
                                                                        toolId
                                                                );

                                                        if (toolData == null) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cUnknown profession item config."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        ProfessionToolManager.initializeDurabilityIfNeeded(
                                                                stack,
                                                                toolData,
                                                                false
                                                        );

                                                        int current =
                                                                ProfessionToolMetadata.getCurrentDurability(
                                                                        stack
                                                                );

                                                        int max =
                                                                ProfessionToolMetadata.getMaxDurability(
                                                                        stack
                                                                );

                                                        if (max <= 0) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cThis item cannot be repaired."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        if (current >= max) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§eThis item is already fully repaired."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        Map<String, Integer> materials =
                                                                getRepairMaterials(
                                                                        toolData
                                                                );

                                                        if (materials.isEmpty()) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cThis item has no repair materials configured."
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        String missing =
                                                                getMissingMaterials(
                                                                        player,
                                                                        materials
                                                                );

                                                        if (missing != null) {
                                                            player.sendSystemMessage(
                                                                    Component.literal(
                                                                            "§cMissing repair materials: §f" +
                                                                                    missing
                                                                    )
                                                            );

                                                            return 0;
                                                        }

                                                        consumeMaterials(
                                                                player,
                                                                materials
                                                        );

                                                        ProfessionToolManager.repairTool(
                                                                stack
                                                        );

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§aRepaired " +
                                                                                ProfessionToolConfig.getDisplayName(
                                                                                        toolId,
                                                                                        toolData
                                                                                ) +
                                                                                " to §f" +
                                                                                ProfessionToolMetadata.getCurrentDurability(
                                                                                        stack
                                                                                ) +
                                                                                "/" +
                                                                                ProfessionToolMetadata.getMaxDurability(
                                                                                        stack
                                                                                ) +
                                                                                "§a durability."
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

                                                        player.sendSystemMessage(
                                                                Component.literal(
                                                                        "§7Durability: §f" +
                                                                                ProfessionToolMetadata.getCurrentDurability(
                                                                                        stack
                                                                                ) +
                                                                                "/" +
                                                                                ProfessionToolMetadata.getMaxDurability(
                                                                                        stack
                                                                                )
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


    private static int executeReroll(
            ServerPlayer player,
            ItemStack stack
    ) {

        long cost =
                ProfessionToolRollService.getRerollCost(
                        stack
                );

        EconomyCraftHook.AffordResult affordResult =
                EconomyCraftHook.canAfford(
                        player,
                        cost
                );

        if (!affordResult.success) {
            player.sendSystemMessage(
                    Component.literal(
                            "§c" + affordResult.error
                    )
            );

            return 0;
        }

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

        EconomyCraftHook.ChargeResult chargeResult =
                EconomyCraftHook.withdraw(
                        player,
                        cost
                );

        if (!chargeResult.success) {
            player.sendSystemMessage(
                    Component.literal(
                            "§c" + chargeResult.error
                    )
            );

            return 0;
        }

        ProfessionToolManager.refreshToolStack(
                stack
        );

        ProfessionToolAnnouncementManager.announcePerfectRollIfNeeded(
                player,
                stack,
                result.quality
        );

        player.sendSystemMessage(
                ProfessionToolRollService.buildSuccessMessage(
                        result
                )
        );

        if (cost > 0L) {
            player.sendSystemMessage(
                    Component.literal(
                            "§7Paid §6" +
                                    EconomyCraftHook.formatMoney(
                                            cost
                                    ) +
                                    "§7. New Balance: §6" +
                                    EconomyCraftHook.formatMoney(
                                            chargeResult.newBalance
                                    )
                    )
            );
        }

        return 1;
    }

    private static Map<String, Integer> getRepairMaterials(
            ProfessionToolConfig.ToolData toolData
    ) {

        if (
                toolData == null ||
                        toolData.repairMaterials == null
        ) {
            return new LinkedHashMap<>();
        }

        Map<String, Integer> clean =
                new LinkedHashMap<>();

        for (Map.Entry<String, Integer> entry : toolData.repairMaterials.entrySet()) {
            if (
                    entry.getKey() == null ||
                            entry.getKey().isBlank() ||
                            entry.getValue() == null ||
                            entry.getValue() <= 0
            ) {
                continue;
            }

            clean.put(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        return clean;
    }

    private static String getMissingMaterials(
            ServerPlayer player,
            Map<String, Integer> materials
    ) {

        StringBuilder missing =
                new StringBuilder();

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            Item item =
                    getItem(
                            entry.getKey()
                    );

            if (item == null) {
                if (!missing.isEmpty()) {
                    missing.append(", " );
                }
                missing.append(entry.getKey());
                continue;
            }

            int required =
                    entry.getValue();

            int found =
                    countItem(
                            player,
                            item
                    );

            if (found < required) {
                if (!missing.isEmpty()) {
                    missing.append(", " );
                }

                missing.append(
                        entry.getKey()
                ).append(
                        " x"
                ).append(
                        required
                ).append(
                        " (have "
                ).append(
                        found
                ).append(
                        ")"
                );
            }
        }

        return missing.isEmpty()
                ? null
                : missing.toString();
    }

    private static void consumeMaterials(
            ServerPlayer player,
            Map<String, Integer> materials
    ) {

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            Item item =
                    getItem(
                            entry.getKey()
                    );

            if (item == null) {
                continue;
            }

            int remaining =
                    entry.getValue();

            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                if (remaining <= 0) {
                    break;
                }

                ItemStack slotStack =
                        player.getInventory().items.get(
                                slot
                        );

                if (
                        slotStack == null ||
                                slotStack.isEmpty() ||
                                !slotStack.is(item)
                ) {
                    continue;
                }

                int remove =
                        Math.min(
                                remaining,
                                slotStack.getCount()
                        );

                slotStack.shrink(
                        remove
                );

                remaining -=
                        remove;
            }
        }
    }

    private static int countItem(
            ServerPlayer player,
            Item item
    ) {

        int count =
                0;

        for (ItemStack stack : player.getInventory().items) {
            if (
                    stack != null &&
                            !stack.isEmpty() &&
                            stack.is(item)
            ) {
                count +=
                        stack.getCount();
            }
        }

        return count;
    }

    private static Item getItem(
            String itemId
    ) {

        try {
            Item item =
                    BuiltInRegistries.ITEM.get(
                            ResourceLocation.parse(
                                    itemId
                            )
                    );

            return item == Items.AIR
                    ? null
                    : item;
        } catch (Exception ignored) {
            return null;
        }
    }

}