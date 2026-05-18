package com.champutils.menu;

import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;


public final class FragmentCraftingMenu {

    private FragmentCraftingMenu() {
    }

    public static void open(
            ServerPlayer player
    ) {
        SimpleGui gui =
                MenuUtil.createGui(MenuType.GENERIC_9x6, player);

        gui.setTitle(
                Component.literal(
                        "Fragment Crafting"
                )
        );

        MenuUtil.fillBorders(
                gui,
                4,
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43,
                49
        );

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        Items.EMERALD
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§aFragment Crafting"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Upgrade fragments and craft mystery tools."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7All costs come from profession_fragments.json."
                                )
                        )
        );

        addUpgradeColumn(gui, player);
        addToolCraftingGrid(gui, player);

        MenuUtil.addBackButton(
                gui,
                49,
                () -> ItemsMenu.open(player)
        );

        gui.open();
    }

    private static void addUpgradeColumn(
            SimpleGui gui,
            ServerPlayer player
    ) {
        addUpgradeButton(gui, player, 10, "COMMON_TO_UNCOMMON", Items.PAPER);
        addUpgradeButton(gui, player, 19, "UNCOMMON_TO_RARE", Items.PAPER);
        addUpgradeButton(gui, player, 28, "RARE_TO_EPIC", Items.PAPER);
        addUpgradeButton(gui, player, 37, "EPIC_TO_LEGENDARY", Items.PAPER);
        addUpgradeButton(gui, player, 46, "LEGENDARY_TO_MYTHIC", Items.PAPER);
    }

    private static void addToolCraftingGrid(
            SimpleGui gui,
            ServerPlayer player
    ) {
        // Layout:
        // Row 1 = Pickaxes, Row 2 = Axes, Row 3 = Hoes
        // Columns = Common, Uncommon, Rare, Epic, Legendary, Mythic
        addToolTypeRow(
                gui,
                player,
                "pickaxe",
                12,
                Items.STONE_PICKAXE,
                Items.IRON_PICKAXE,
                Items.DIAMOND_PICKAXE,
                Items.DIAMOND_PICKAXE,
                Items.NETHERITE_PICKAXE,
                Items.NETHERITE_PICKAXE
        );

        addToolTypeRow(
                gui,
                player,
                "axe",
                21,
                Items.STONE_AXE,
                Items.IRON_AXE,
                Items.DIAMOND_AXE,
                Items.DIAMOND_AXE,
                Items.NETHERITE_AXE,
                Items.NETHERITE_AXE
        );

        addToolTypeRow(
                gui,
                player,
                "hoe",
                30,
                Items.STONE_HOE,
                Items.IRON_HOE,
                Items.DIAMOND_HOE,
                Items.DIAMOND_HOE,
                Items.NETHERITE_HOE,
                Items.NETHERITE_HOE
        );
    }

    private static void addToolTypeRow(
            SimpleGui gui,
            ServerPlayer player,
            String toolType,
            int startSlot,
            Item commonIcon,
            Item uncommonIcon,
            Item rareIcon,
            Item epicIcon,
            Item legendaryIcon,
            Item mythicIcon
    ) {
        addToolCraftButton(gui, player, startSlot, "COMMON", toolType, commonIcon);
        addToolCraftButton(gui, player, startSlot + 1, "UNCOMMON", toolType, uncommonIcon);
        addToolCraftButton(gui, player, startSlot + 2, "RARE", toolType, rareIcon);
        addToolCraftButton(gui, player, startSlot + 3, "EPIC", toolType, epicIcon);
        addToolCraftButton(gui, player, startSlot + 4, "LEGENDARY", toolType, legendaryIcon);
        addToolCraftButton(gui, player, startSlot + 5, "MYTHIC", toolType, mythicIcon);
    }

    private static void addUpgradeButton(
            SimpleGui gui,
            ServerPlayer player,
            int slot,
            String upgradeId,
            Item icon
    ) {
        ProfessionFragmentConfig.UpgradeData upgrade =
                ProfessionFragmentConfig.UPGRADES.get(upgradeId);

        if (upgrade == null) {
            gui.setSlot(
                    slot,
                    new GuiElementBuilder(Items.BARRIER)
                            .hideDefaultTooltip()
                            .setName(Component.literal("§cMissing Upgrade"))
                            .addLoreLine(Component.literal("§7Missing config key: §f" + upgradeId))
            );
            return;
        }

        String from =
                ProfessionFragmentConfig.normalizeRarity(upgrade.fromFragment);
        String to =
                ProfessionFragmentConfig.normalizeRarity(upgrade.toFragment);
        int cost =
                Math.max(1, upgrade.cost);
        int output =
                Math.max(1, upgrade.output);
        int available =
                ProfessionFragmentManager.countFragments(player, from);

        gui.setSlot(
                slot,
                new GuiElementBuilder(icon)
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "Upgrade " + ProfessionFragmentManager.formatWords(from) + " → " + ProfessionFragmentManager.formatWords(to)
                                ).withStyle(getRarityColor(to))
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Cost: §6" + cost + "x " + ProfessionFragmentManager.formatWords(from) + " Fragment"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Output: §a" + output + "x " + ProfessionFragmentManager.formatWords(to) + " Fragment"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7You have: §e" + available
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        available >= cost ? "§eClick to upgrade" : "§cNot enough fragments"
                                )
                        )
                        .setCallback(
                                (i, c, t) -> {
                                    player.closeContainer();
                                    player.getServer()
                                            .getCommands()
                                            .performPrefixedCommand(
                                                    player.createCommandSourceStack(),
                                                    "fragments upgrade " + upgradeId
                                            );
                                }
                        )
        );
    }

    private static void addToolCraftButton(
            SimpleGui gui,
            ServerPlayer player,
            int slot,
            String rarity,
            String toolType,
            Item icon
    ) {
        String normalizedRarity =
                ProfessionFragmentConfig.normalizeRarity(rarity);

        ProfessionFragmentConfig.ToolCraftingData trade =
                ProfessionFragmentConfig.TOOL_CRAFTING.get(normalizedRarity);

        if (trade == null) {
            gui.setSlot(
                    slot,
                    new GuiElementBuilder(Items.BARRIER)
                            .hideDefaultTooltip()
                            .setName(Component.literal("§cMissing Tool Craft"))
                            .addLoreLine(Component.literal("§7Missing toolCrafting config for: §f" + normalizedRarity))
            );
            return;
        }

        String fragmentKey =
                ProfessionFragmentConfig.normalizeRarity(trade.fragment);
        int cost =
                Math.max(1, trade.cost);
        int available =
                ProfessionFragmentManager.countFragments(player, fragmentKey);

        gui.setSlot(
                slot,
                new GuiElementBuilder(icon)
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "Craft " +
                                                ProfessionFragmentManager.formatWords(normalizedRarity) +
                                                " Mystery " +
                                                ProfessionFragmentManager.formatWords(toolType)
                                ).withStyle(getRarityColor(normalizedRarity))
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Cost: §6" + cost + "x " + ProfessionFragmentManager.formatWords(fragmentKey) + " Fragment"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7You have: §e" + available
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§8Random tool from this rarity and type."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§8The exact item stays hidden until identified."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        available >= cost ? "§eClick to craft" : "§cNot enough fragments"
                                )
                        )
                        .setCallback(
                                (i, c, t) -> {
                                    player.closeContainer();
                                    player.getServer()
                                            .getCommands()
                                            .performPrefixedCommand(
                                                    player.createCommandSourceStack(),
                                                    "fragments craft " + normalizedRarity.toLowerCase() + " " + toolType
                                            );
                                }
                        )
        );
    }

    private static ChatFormatting getRarityColor(
            String rarity
    ) {
        if (rarity == null) {
            return ChatFormatting.WHITE;
        }

        return switch (rarity.trim().toUpperCase()) {
            case "UNCOMMON" -> ChatFormatting.GREEN;
            case "RARE" -> ChatFormatting.BLUE;
            case "EPIC" -> ChatFormatting.LIGHT_PURPLE;
            case "LEGENDARY" -> ChatFormatting.GOLD;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
