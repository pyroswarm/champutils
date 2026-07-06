package com.champutils.commands;

import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;

public class GiveChampItemCommand {

    private static final String[] TOOL_TYPES = {
            "pickaxe",
            "axe",
            "hoe",
            "shovel",
            "sword",
            "tool"
    };

    private static final String[] RARITIES = {
            "f",
            "e",
            "d",
            "c",
            "b",
            "a",
            "s"
    };

    public static void register() {

        CommandRegistrationCallback.EVENT.register(
                (
                        dispatcher,
                        registryAccess,
                        environment
                ) -> {

                    LiteralArgumentBuilder<CommandSourceStack> root =
                            Commands.literal(
                                            "givechampitem"
                                    )
                                    .requires(source -> source.hasPermission(4));

                    for (String toolType : TOOL_TYPES) {
                        root.then(buildTypedToolBranch(toolType));
                    }

                    root.then(
                            Commands.argument(
                                            "itemId",
                                            StringArgumentType.word()
                                    )

                                    .suggests(
                                            (
                                                    context,
                                                    builder
                                            ) -> {

                                                suggestGenericToolAliases(builder);

                                                for (ProfessionFragmentConfig.FragmentData fragmentData : ProfessionFragmentConfig.FRAGMENTS.values()) {
                                                    if (fragmentData != null && fragmentData.itemId != null && !fragmentData.itemId.isBlank()) {
                                                        builder.suggest(fragmentData.itemId);
                                                    }
                                                }

                                                return builder.buildFuture();
                                            }
                                    )

                                    .executes(context -> giveItem(
                                            context.getSource()
                                                    .getPlayerOrException(),
                                            StringArgumentType.getString(
                                                    context,
                                                    "itemId"
                                            ),
                                            1,
                                            false
                                    ))

                                    .then(
                                            Commands.argument(
                                                            "amount",
                                                            IntegerArgumentType.integer(1, 640)
                                                    )
                                                    .executes(context -> giveItem(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            StringArgumentType.getString(
                                                                    context,
                                                                    "itemId"
                                                            ),
                                                            IntegerArgumentType.getInteger(
                                                                    context,
                                                                    "amount"
                                                            ),
                                                            false
                                                    ))
                                                    .then(
                                                            Commands.literal(
                                                                            "ascended"
                                                                    )
                                                                    .executes(context -> giveItem(
                                                                            context.getSource()
                                                                                    .getPlayerOrException(),
                                                                            StringArgumentType.getString(
                                                                                    context,
                                                                                    "itemId"
                                                                            ),
                                                                            IntegerArgumentType.getInteger(
                                                                                    context,
                                                                                    "amount"
                                                                            ),
                                                                            true
                                                                    ))
                                                    )
                                    )

                                    .then(
                                            Commands.literal(
                                                            "ascended"
                                                    )
                                                    .executes(context -> giveItem(
                                                            context.getSource()
                                                                    .getPlayerOrException(),
                                                            StringArgumentType.getString(
                                                                    context,
                                                                    "itemId"
                                                            ),
                                                            1,
                                                            true
                                                    ))
                                                    .then(
                                                            Commands.argument(
                                                                            "amount",
                                                                            IntegerArgumentType.integer(1, 640)
                                                                    )
                                                                    .executes(context -> giveItem(
                                                                            context.getSource()
                                                                                    .getPlayerOrException(),
                                                                            StringArgumentType.getString(
                                                                                    context,
                                                                                    "itemId"
                                                                            ),
                                                                            IntegerArgumentType.getInteger(
                                                                                    context,
                                                                                    "amount"
                                                                            ),
                                                                            true
                                                                    ))
                                                    )
                                    )
                    );

                    dispatcher.register(root);
                }
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTypedToolBranch(
            String toolType
    ) {

        LiteralArgumentBuilder<CommandSourceStack> typeBranch =
                Commands.literal(
                        toolType
                );

        for (String rarity : RARITIES) {
            typeBranch.then(
                    Commands.literal(
                                    rarity
                            )
                            .then(
                                    Commands.argument(
                                                    "toolId",
                                                    StringArgumentType.word()
                                            )
                                            .suggests(
                                                    (
                                                            context,
                                                            builder
                                                    ) -> {
                                                        suggestTools(
                                                                builder,
                                                                toolType,
                                                                rarity
                                                        );
                                                        return builder.buildFuture();
                                                    }
                                            )
                                            .executes(context -> giveTypedTool(
                                                    context,
                                                    toolType,
                                                    rarity,
                                                    1,
                                                    false
                                            ))
                                            .then(
                                                    Commands.argument(
                                                                    "amount",
                                                                    IntegerArgumentType.integer(1, 640)
                                                            )
                                                            .executes(context -> giveTypedTool(
                                                                    context,
                                                                    toolType,
                                                                    rarity,
                                                                    IntegerArgumentType.getInteger(
                                                                            context,
                                                                            "amount"
                                                                    ),
                                                                    false
                                                            ))
                                                            .then(
                                                                    Commands.literal(
                                                                                    "ascended"
                                                                            )
                                                                            .executes(context -> giveTypedTool(
                                                                                    context,
                                                                                    toolType,
                                                                                    rarity,
                                                                                    IntegerArgumentType.getInteger(
                                                                                            context,
                                                                                            "amount"
                                                                                    ),
                                                                                    true
                                                                            ))
                                                            )
                                            )
                                            .then(
                                                    Commands.literal(
                                                                    "ascended"
                                                            )
                                                            .executes(context -> giveTypedTool(
                                                                    context,
                                                                    toolType,
                                                                    rarity,
                                                                    1,
                                                                    true
                                                            ))
                                                            .then(
                                                                    Commands.argument(
                                                                                    "amount",
                                                                                    IntegerArgumentType.integer(1, 640)
                                                                            )
                                                                            .executes(context -> giveTypedTool(
                                                                                    context,
                                                                                    toolType,
                                                                                    rarity,
                                                                                    IntegerArgumentType.getInteger(
                                                                                            context,
                                                                                            "amount"
                                                                                    ),
                                                                                    true
                                                                            ))
                                                            )
                                            )
                            )
            );
        }

        return typeBranch;
    }

    private static int giveTypedTool(
            CommandContext<CommandSourceStack> context,
            String toolType,
            String rarity,
            int amount,
            boolean ascended
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {

        ServerPlayer player =
                context.getSource()
                        .getPlayerOrException();

        String requestedToolId =
                StringArgumentType.getString(
                        context,
                        "toolId"
                );

        String resolvedToolId =
                resolveToolId(
                        requestedToolId,
                        toolType,
                        rarity
                );

        if (resolvedToolId == null) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cNo " + rarity.toLowerCase(Locale.ROOT) + " " + toolType.toLowerCase(Locale.ROOT) + " found for: " + requestedToolId
                    )
            );
            player.sendSystemMessage(
                    Component.literal(
                            "§7Use the command suggestions after /givechampitem " + toolType.toLowerCase(Locale.ROOT) + " " + rarity.toLowerCase(Locale.ROOT) + " to pick a valid tool."
                    )
            );
            return 0;
        }

        return giveItem(
                player,
                resolvedToolId,
                amount,
                ascended
        );
    }

    private static void suggestTools(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder,
            String toolType,
            String rarity
    ) {

        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            String toolId = entry.getKey();
            ProfessionToolConfig.ToolData toolData = entry.getValue();

            if (!matchesTypeAndRarity(toolData, toolType, rarity)) {
                continue;
            }

            builder.suggest(
                    aliasForTool(toolId, toolData)
            );
        }
    }

    private static void suggestGenericToolAliases(com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        java.util.Set<String> aliases = new java.util.LinkedHashSet<>();
        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData toolData = entry.getValue();
            if (toolData == null) continue;
            String alias = aliasForTool(entry.getKey(), toolData);
            if (alias != null && !alias.isBlank()) {
                aliases.add(alias);
            }
        }
        for (String alias : aliases) {
            builder.suggest(alias);
        }
    }

    private static String resolveToolId(
            String requestedToolId,
            String toolType,
            String rarity
    ) {

        if (requestedToolId == null || requestedToolId.isBlank()) {
            return null;
        }

        ProfessionToolConfig.ToolData exact =
                ProfessionToolConfig.TOOLS.get(
                        requestedToolId
                );

        if (matchesTypeAndRarity(
                exact,
                toolType,
                rarity
        )) {
            return requestedToolId;
        }

        String normalizedRequest =
                normalizeLookup(
                        requestedToolId
                );

        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData toolData = entry.getValue();

            if (!matchesTypeAndRarity(
                    toolData,
                    toolType,
                    rarity
            )) {
                continue;
            }

            String toolId =
                    entry.getKey();

            if (normalizeLookup(toolId).equals(normalizedRequest)) {
                return toolId;
            }

            if (normalizeLookup(aliasForTool(toolId, toolData)).equals(normalizedRequest)) {
                return toolId;
            }

            if (
                    toolData.displayName != null &&
                            normalizeLookup(toolData.displayName).equals(normalizedRequest)
            ) {
                return toolId;
            }
        }

        return null;
    }


    private static String aliasForTool(
            String toolId,
            ProfessionToolConfig.ToolData toolData
    ) {

        if (toolData == null) {
            return toolId == null ? "" : toolId;
        }

        String rarity = toolData.rarity == null ? "" : toolData.rarity.trim().toLowerCase(Locale.ROOT);
        String baseItem = toolData.baseItem == null ? "" : toolData.baseItem.toLowerCase(Locale.ROOT);
        String family;
        if (baseItem.contains("pickaxe")) family = "pickaxe";
        else if (baseItem.contains("shovel")) family = "shovel";
        else if (baseItem.contains("axe")) family = "axe";
        else if (baseItem.contains("hoe")) family = "hoe";
        else if (baseItem.contains("sword")) family = "sword";
        else family = "tool";

        if (rarity.isBlank()) {
            return toolId == null ? family : toolId;
        }

        return rarity + "_rank_" + family;
    }

    private static boolean matchesTypeAndRarity(
            ProfessionToolConfig.ToolData toolData,
            String toolType,
            String rarity
    ) {

        if (toolData == null) {
            return false;
        }

        String configuredRarity =
                toolData.rarity == null
                        ? ""
                        : toolData.rarity.trim().toLowerCase(Locale.ROOT);

        if (!configuredRarity.equals(rarity.toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (toolType.equalsIgnoreCase("tool")) {
            return true;
        }

        String baseItem =
                toolData.baseItem == null
                        ? ""
                        : toolData.baseItem.toLowerCase(Locale.ROOT);

        return switch (toolType.toLowerCase(Locale.ROOT)) {
            case "pickaxe" -> baseItem.contains("pickaxe");
            case "axe" -> baseItem.contains("axe") && !baseItem.contains("pickaxe");
            case "hoe" -> baseItem.contains("hoe");
            case "shovel" -> baseItem.contains("shovel");
            case "sword" -> baseItem.contains("sword");
            default -> false;
        };
    }

    private static String normalizeLookup(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("'", "")
                .replace(" ", "_")
                .replace("-", "_");
    }

    private static int giveItem(
            ServerPlayer player,
            String itemId,
            int amount,
            boolean ascended
    ) {
        String genericToolId = resolveGenericRankToolId(itemId);
        if (genericToolId != null) {
            return giveTool(player, genericToolId, amount, ascended);
        }

        String fragmentKey =
                ProfessionFragmentManager.getFragmentKeyByItemId(itemId);

        if (fragmentKey != null) {
            return giveFragment(
                    player,
                    itemId,
                    fragmentKey,
                    amount
            );
        }

        return giveTool(player, itemId, amount, ascended);
    }

    private static int giveTool(
            ServerPlayer player,
            String itemId,
            int amount,
            boolean ascended
    ) {
        int given =
                0;

        for (int i = 0; i < amount; i++) {
            ItemStack item =
                    ProfessionToolManager.createTool(
                            itemId,
                            ascended
                    );

            if (
                    item.isEmpty()
            ) {
                player.sendSystemMessage(
                        Component.literal(
                                ascended
                                        ? "§cInvalid custom item or ascended variant is not enabled: " + itemId
                                        : "§cInvalid custom item: " + itemId
                        )
                );

                return given;
            }

            boolean added =
                    player.getInventory()
                            .add(
                                    item
                            );

            if (!added) {
                player.drop(
                        item,
                        false
                );
            }

            given++;
        }

        player.sendSystemMessage(
                Component.literal(
                        ascended
                                ? "§dGiven " + given + "x ascended custom item: " + itemId
                                : "§aGiven " + given + "x custom item: " + itemId
                )
        );

        return given;
    }

    private static String resolveGenericRankToolId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        String normalized = normalizeLookup(itemId);
        for (Map.Entry<String, ProfessionToolConfig.ToolData> entry : ProfessionToolConfig.TOOLS.entrySet()) {
            ProfessionToolConfig.ToolData toolData = entry.getValue();
            if (toolData == null) continue;
            if (normalizeLookup(aliasForTool(entry.getKey(), toolData)).equals(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static int giveFragment(
            ServerPlayer player,
            String itemId,
            String fragmentKey,
            int amount
    ) {
        boolean given =
                ProfessionFragmentManager.giveFragments(
                        player,
                        fragmentKey,
                        amount
                );

        if (!given) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cCould not create essence item: " + itemId
                    )
            );

            return 0;
        }

        player.sendSystemMessage(
                Component.literal(
                        "§aGiven " + amount + "x " + ProfessionFragmentManager.displayRankName(fragmentKey) + " Essence."
                )
        );

        return 1;
    }
}
