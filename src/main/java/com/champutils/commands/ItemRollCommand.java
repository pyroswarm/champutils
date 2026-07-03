package com.champutils.commands;

import com.champutils.economy.EconomyCraftHook;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;
import com.champutils.profession.ProfessionToolAnnouncementManager;
import com.champutils.profession.ProfessionToolMetadata;
import com.champutils.profession.ProfessionToolRollService;
import com.champutils.profession.ItemSafetyService;
import com.champutils.profession.ProfessionNotificationSettings;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

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
                                                        ServerPlayer player = context.getSource().getPlayerOrException();
                                                        ItemStack stack = player.getMainHandItem();
                                                        long cost = ProfessionToolRollService.getIdentifyCost(stack);
                                                        player.sendSystemMessage(Component.literal("§eIdentify this item for §6" + EconomyCraftHook.formatMoney(cost) + "§e?"));
                                                        player.sendSystemMessage(Component.literal("§7Run §a/itemroll identify confirm §7to continue."));
                                                        return 1;
                                                    })
                                                    .then(
                                                            Commands.literal("confirm")
                                                                    .executes(context -> executeIdentify(
                                                                            context.getSource().getPlayerOrException(),
                                                                            context.getSource().getPlayerOrException().getMainHandItem()
                                                                    ))
                                                    )
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

                                                        long cost = ProfessionToolRollService.getRerollCost(stack);
                                                        player.sendSystemMessage(Component.literal("§eReroll this item for §6" + EconomyCraftHook.formatMoney(cost) + "§e?"));
                                                        player.sendSystemMessage(Component.literal("§7Run §a/itemroll reroll confirm §7to continue."));
                                                        return 1;
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
                                                    .executes(context -> executeRepair(
                                                            context.getSource().getPlayerOrException()
                                                    ))
                                                    .then(
                                                            Commands.literal("confirm")
                                                                    .executes(context -> executeRepairConfirmed(
                                                                            context.getSource().getPlayerOrException()
                                                                    ))
                                                    )
                                    )
                    );
                }
        );
    }


    private static int executeIdentify(ServerPlayer player, ItemStack stack) {
        long cost = ProfessionToolRollService.getIdentifyCost(stack);
        EconomyCraftHook.AffordResult affordResult = EconomyCraftHook.canAfford(player, cost);
        if (!affordResult.success) {
            player.sendSystemMessage(Component.literal("§c" + affordResult.error));
            return 0;
        }
        ProfessionToolRollService.RollResult result = ProfessionToolRollService.identify(player, stack);
        if (!result.success) {
            player.sendSystemMessage(Component.literal("§c" + result.error));
            return 0;
        }
        EconomyCraftHook.ChargeResult chargeResult = EconomyCraftHook.withdraw(player, cost);
        if (!chargeResult.success) {
            player.sendSystemMessage(Component.literal("§c" + chargeResult.error));
            return 0;
        }
        ProfessionToolManager.refreshToolStack(stack);
        ProfessionToolManager.applyVanillaEfficiencyEnchant(player, stack);
        ProfessionToolAnnouncementManager.announcePerfectRollIfNeeded(player, stack, result.quality);
        player.sendSystemMessage(ProfessionToolRollService.buildSuccessMessage(result));
        if (cost > 0L) {
            player.sendSystemMessage(Component.literal("§7Paid §6" + EconomyCraftHook.formatMoney(cost) + "§7. New Balance: §6" + EconomyCraftHook.formatMoney(chargeResult.newBalance)));
        }
        return 1;
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
        ProfessionToolManager.applyVanillaEfficiencyEnchant(player, stack);

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


    private static int executeRepair(
            ServerPlayer player
    ) {
        if (Boolean.getBoolean("champutils.repairNoConfirm") || !ProfessionNotificationSettings.isRepairConfirmationEnabled(player)) {
            return executeRepairConfirmed(player);
        }
        return executeRepairPreview(player);
    }

    private static int executeRepairPreview(
            ServerPlayer player
    ) {

        RepairCheck check = validateRepair(player);
        if (check == null) {
            return 0;
        }

        if (check.missing != null) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cNot enough credits to repair: §f" + check.missing
                    )
            );
            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§eRepair " + check.displayName + " from §f" + check.current + "/" + check.max +
                                "§e to §f" + check.after + "/" + check.max + "§e durability."
                )
        );
        player.sendSystemMessage(
                Component.literal(
                        "§eCost: §6" + EconomyCraftHook.formatMoney(check.creditCost)
                )
        );
        player.sendSystemMessage(
                Component.literal(
                        "§7Run §a/itemroll repair confirm §7to repair."
                )
        );

        return 1;
    }

    private static int executeRepairConfirmed(
            ServerPlayer player
    ) {

        RepairCheck check = validateRepair(player);
        if (check == null) {
            return 0;
        }

        EconomyCraftHook.ChargeResult chargeResult = EconomyCraftHook.withdraw(player, check.creditCost);
        if (!chargeResult.success) {
            player.sendSystemMessage(Component.literal("§c" + chargeResult.error));
            return 0;
        }

        ProfessionToolManager.repairTool(
                check.stack
        );
        ProfessionToolManager.applyVanillaEfficiencyEnchant(player, check.stack);

        player.sendSystemMessage(
                Component.literal(
                        "§aRepaired " + check.displayName + " to §f" +
                                ProfessionToolMetadata.getCurrentDurability(check.stack) + "/" +
                                ProfessionToolMetadata.getMaxDurability(check.stack) + "§a durability."
                )
        );

        return 1;
    }

    private static RepairCheck validateRepair(
            ServerPlayer player
    ) {

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
            return null;
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
            return null;
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
            return null;
        }

        if (current >= max) {
            player.sendSystemMessage(
                    Component.literal(
                            "§eThis item is already fully repaired."
                    )
            );
            return null;
        }

        long creditCost = ProfessionToolManager.getRepairCreditCost(toolData);
        EconomyCraftHook.AffordResult afford = EconomyCraftHook.canAfford(player, creditCost);
        String missing = afford.success ? null : afford.error;

        int after = getRepairPreviewDurability(
                current,
                max,
                toolData
        );

        return new RepairCheck(
                stack,
                ProfessionToolConfig.getDisplayName(toolId, toolData),
                current,
                max,
                after,
                creditCost,
                missing
        );
    }

    private static int getRepairPreviewDurability(
            int current,
            int max,
            ProfessionToolConfig.ToolData toolData
    ) {

        double percent =
                toolData.repairDurabilityPercent <= 0.0D
                        ? 100.0D
                        : toolData.repairDurabilityPercent;

        int restore =
                Math.max(
                        1,
                        (int) Math.ceil(
                                max * (percent / 100.0D)
                        )
                );

        return Math.min(
                max,
                current + restore
        );
    }

    private static final class RepairCheck {
        final ItemStack stack;
        final String displayName;
        final int current;
        final int max;
        final int after;
        final long creditCost;
        final String missing;

        RepairCheck(
                ItemStack stack,
                String displayName,
                int current,
                int max,
                int after,
                long creditCost,
                String missing
        ) {
            this.stack = stack;
            this.displayName = displayName;
            this.current = current;
            this.max = max;
            this.after = after;
            this.creditCost = creditCost;
            this.missing = missing;
        }
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
            String material = entry.getKey();

            int required =
                    entry.getValue();

            int found =
                    countMaterial(
                            player,
                            material
                    );

            if (found < required) {
                if (!missing.isEmpty()) {
                    missing.append(", " );
                }

                missing.append(
                        displayMaterialName(material)
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


    private static String formatMaterials(
            Map<String, Integer> materials
    ) {

        StringBuilder builder =
                new StringBuilder();

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(displayMaterialName(entry.getKey()))
                    .append(" x")
                    .append(entry.getValue());
        }

        return builder.toString();
    }

    private static String displayMaterialName(
            String material
    ) {

        if (material == null || material.isBlank()) {
            return "Unknown Item";
        }

        String normalized = material.trim();
        if (isLogMaterial(normalized)) {
            return "Any Log";
        }
        if (isStoneMaterial(normalized)) {
            return "Any Stone";
        }

        Item item = getItem(material);
        if (item != null) {
            return item.getName(ItemStack.EMPTY).getString();
        }

        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        int colon = normalized.indexOf(':');
        if (colon >= 0 && colon + 1 < normalized.length()) {
            normalized = normalized.substring(colon + 1);
        }

        normalized = normalized.replace('_', ' ').replace('/', ' ');
        StringBuilder title = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                title.append(c);
                upper = true;
            } else if (upper) {
                title.append(Character.toUpperCase(c));
                upper = false;
            } else {
                title.append(c);
            }
        }
        return title.toString();
    }

    private static void consumeMaterials(
            ServerPlayer player,
            Map<String, Integer> materials
    ) {

        for (Map.Entry<String, Integer> entry : materials.entrySet()) {
            String material = entry.getKey();

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
                                !matchesMaterial(slotStack, material)
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

    private static int countMaterial(
            ServerPlayer player,
            String material
    ) {

        int count = 0;

        for (ItemStack stack : player.getInventory().items) {
            if (matchesMaterial(stack, material)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static boolean matchesMaterial(
            ItemStack stack,
            String material
    ) {

        if (stack == null || stack.isEmpty() || material == null || material.isBlank()) {
            return false;
        }

        String id = normalizeMaterialId(material);

        if (matchesItemTag(stack, id)) {
            return true;
        }

        if (isLogMaterial(id)) {
            return matchesAnyItemTag(
                    stack,
                    "minecraft:logs",
                    "minecraft:logs_that_burn",
                    "c:logs"
            );
        }

        if (isStoneMaterial(id)) {
            return matchesAnyItemTag(
                    stack,
                    "minecraft:stone_tool_materials",
                    "minecraft:stone_crafting_materials",
                    "c:stones",
                    "c:cobblestones"
            );
        }

        Item item = getItem(material);
        return item != null && stack.is(item);
    }

    private static String normalizeMaterialId(
            String material
    ) {

        String id = material.trim();
        if (id.startsWith("#")) {
            id = id.substring(1);
        }
        return id;
    }

    private static boolean matchesAnyItemTag(
            ItemStack stack,
            String... tags
    ) {

        for (String tag : tags) {
            if (matchesItemTag(stack, tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesItemTag(
            ItemStack stack,
            String id
    ) {

        try {
            ResourceLocation location = ResourceLocation.parse(id);
            TagKey<Item> tag = TagKey.create(Registries.ITEM, location);
            return stack.is(tag);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isLogMaterial(
            String material
    ) {

        String id = normalizeMaterialId(material).toLowerCase();
        return id.equals("minecraft:oak_log") ||
                id.equals("minecraft:spruce_log") ||
                id.equals("minecraft:birch_log") ||
                id.equals("minecraft:jungle_log") ||
                id.equals("minecraft:acacia_log") ||
                id.equals("minecraft:dark_oak_log") ||
                id.equals("minecraft:mangrove_log") ||
                id.equals("minecraft:cherry_log") ||
                id.equals("minecraft:crimson_stem") ||
                id.equals("minecraft:warped_stem") ||
                id.equals("minecraft:oak_wood") ||
                id.equals("minecraft:spruce_wood") ||
                id.equals("minecraft:birch_wood") ||
                id.equals("minecraft:jungle_wood") ||
                id.equals("minecraft:acacia_wood") ||
                id.equals("minecraft:dark_oak_wood") ||
                id.equals("minecraft:mangrove_wood") ||
                id.equals("minecraft:cherry_wood") ||
                id.equals("minecraft:crimson_hyphae") ||
                id.equals("minecraft:warped_hyphae") ||
                id.equals("cobblemon:apricorn_log") ||
                id.equals("cobblemon:apricorn_wood") ||
                id.equals("cobblemon:stripped_apricorn_log") ||
                id.equals("cobblemon:stripped_apricorn_wood") ||
                id.endsWith(":logs") ||
                id.endsWith(":logs_that_burn");
    }

    private static boolean isStoneMaterial(
            String material
    ) {

        String id = normalizeMaterialId(material).toLowerCase();
        return id.equals("minecraft:stone") ||
                id.equals("minecraft:cobblestone") ||
                id.equals("minecraft:deepslate") ||
                id.equals("minecraft:cobbled_deepslate") ||
                id.equals("minecraft:granite") ||
                id.equals("minecraft:diorite") ||
                id.equals("minecraft:andesite") ||
                id.equals("minecraft:tuff") ||
                id.equals("minecraft:blackstone") ||
                id.equals("minecraft:basalt") ||
                id.equals("minecraft:smooth_basalt") ||
                id.endsWith(":stones") ||
                id.endsWith(":cobblestones") ||
                id.endsWith(":stone_tool_materials") ||
                id.endsWith(":stone_crafting_materials");
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
