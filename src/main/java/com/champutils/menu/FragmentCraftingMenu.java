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

import java.util.function.Consumer;

public final class FragmentCraftingMenu {
    private FragmentCraftingMenu() {}

    public static void open(ServerPlayer player) { open(player, GearWorkshopMenu::open); }

    public static void open(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        openTabs(player, backTarget);
    }

    private static void openTabs(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Fragment Crafting");
        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aFragment Crafting"))
                .addLoreLine(Component.literal("§7Choose an item type or fragment action.")));

        addTab(gui, player, 10, "Pickaxe", Items.DIAMOND_PICKAXE, () -> openCraft(player, backTarget, "pickaxe", "Pickaxe", Items.DIAMOND_PICKAXE));
        addTab(gui, player, 11, "Axe", Items.DIAMOND_AXE, () -> openCraft(player, backTarget, "axe", "Axe", Items.DIAMOND_AXE));
        addTab(gui, player, 12, "Hoe", Items.DIAMOND_HOE, () -> openCraft(player, backTarget, "hoe", "Hoe", Items.DIAMOND_HOE));
        addTab(gui, player, 13, "Shovel", Items.DIAMOND_SHOVEL, () -> openCraft(player, backTarget, "shovel", "Shovel", Items.DIAMOND_SHOVEL));
        addTab(gui, player, 14, "Boots", Items.DIAMOND_BOOTS, () -> openCraft(player, backTarget, "boots", "Running Shoes", Items.DIAMOND_BOOTS));
        addTab(gui, player, 20, "Upgrade Fragment", Items.AMETHYST_SHARD, () -> openUpgrade(player, backTarget, false));
        addTab(gui, player, 21, "Downgrade Fragment", Items.PAPER, () -> openUpgrade(player, backTarget, true));
        addTab(gui, player, 22, "Withdraw Fragment", Items.CHEST, () -> openWithdraw(player, backTarget));

        MenuUtil.addBackButton(gui, 49, () -> { if (backTarget != null) backTarget.accept(player); else GearWorkshopMenu.open(player); });
        gui.open();
    }

    private static SimpleGui base(ServerPlayer player, String title) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal(title));
        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43, 49);
        return gui;
    }

    private static void addTab(SimpleGui gui, ServerPlayer player, int slot, String name, Item icon, Runnable callback) {
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal("§e" + name))
                .addLoreLine(Component.literal("§7Click to open."))
                .setCallback((i,c,t) -> callback.run()));
    }

    private static void openCraft(ServerPlayer player, Consumer<ServerPlayer> backTarget, String toolType, String title, Item icon) {
        SimpleGui gui = base(player, "Craft " + title);
        gui.setSlot(4, new GuiElementBuilder(icon).hideDefaultTooltip().setName(Component.literal("§aCraft " + title))
                .addLoreLine(Component.literal("§7Craft using stored fragments.")));
        int[] slots = {20,21,22,23,24,25};
        String[] rarities = {"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};
        Item[] icons = iconsFor(toolType);
        for (int i = 0; i < rarities.length; i++) addCraftButton(gui, player, slots[i], rarities[i], toolType, icons[i]);
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static Item[] iconsFor(String toolType) {
        return switch (toolType) {
            case "axe" -> new Item[]{Items.STONE_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE, Items.NETHERITE_AXE};
            case "hoe" -> new Item[]{Items.STONE_HOE, Items.IRON_HOE, Items.DIAMOND_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE, Items.NETHERITE_HOE};
            case "shovel" -> new Item[]{Items.STONE_SHOVEL, Items.IRON_SHOVEL, Items.DIAMOND_SHOVEL, Items.DIAMOND_SHOVEL, Items.NETHERITE_SHOVEL, Items.NETHERITE_SHOVEL};
            case "boots" -> new Item[]{Items.LEATHER_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS, Items.NETHERITE_BOOTS};
            default -> new Item[]{Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE, Items.NETHERITE_PICKAXE};
        };
    }

    private static void addCraftButton(SimpleGui gui, ServerPlayer player, int slot, String rarity, String toolType, Item icon) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        ProfessionFragmentConfig.ToolCraftingData trade = ProfessionFragmentConfig.TOOL_CRAFTING.get(normalizedRarity);
        if (trade == null) {
            gui.setSlot(slot, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cMissing Craft Config")));
            return;
        }
        String fragmentKey = ProfessionFragmentConfig.normalizeRarity(trade.fragment);
        int cost = Math.max(1, trade.cost);
        int available = ProfessionFragmentManager.countFragments(player, fragmentKey);
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal("Craft " + ProfessionFragmentManager.formatWords(normalizedRarity) + " " + ProfessionFragmentManager.formatWords(toolType)).withStyle(getRarityColor(normalizedRarity)))
                .addLoreLine(Component.literal("§7Cost: §6" + cost + "x " + ProfessionFragmentManager.formatWords(fragmentKey) + " Fragment"))
                .addLoreLine(Component.literal("§7You have: §e" + available))
                .addLoreLine(Component.literal(available >= cost ? "§eClick to craft" : "§cNot enough fragments"))
                .setCallback((i,c,t) -> {
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fragments craft " + normalizedRarity.toLowerCase() + " " + toolType);
                }));
    }

    private static void openUpgrade(ServerPlayer player, Consumer<ServerPlayer> backTarget, boolean downgrade) {
        SimpleGui gui = base(player, downgrade ? "Downgrade Fragments" : "Upgrade Fragments");
        gui.setSlot(4, new GuiElementBuilder(downgrade ? Items.PAPER : Items.AMETHYST_SHARD).hideDefaultTooltip()
                .setName(Component.literal(downgrade ? "§cDowngrade Fragments" : "§aUpgrade Fragments"))
                .addLoreLine(Component.literal(downgrade ? "§7Downgrades return half of upgrade value." : "§7Upgrade stored fragments.")));
        String[] ids = downgrade
                ? new String[]{"UNCOMMON_TO_COMMON_DOWNGRADE","RARE_TO_UNCOMMON_DOWNGRADE","EPIC_TO_RARE_DOWNGRADE","LEGENDARY_TO_EPIC_DOWNGRADE","MYTHIC_TO_LEGENDARY_DOWNGRADE"}
                : new String[]{"COMMON_TO_UNCOMMON","UNCOMMON_TO_RARE","RARE_TO_EPIC","EPIC_TO_LEGENDARY","LEGENDARY_TO_MYTHIC"};
        int[] slots = {20,21,22,23,24};
        for (int i = 0; i < ids.length; i++) addUpgradeButton(gui, player, slots[i], ids[i], downgrade ? Items.PAPER : Items.AMETHYST_SHARD);
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void addUpgradeButton(SimpleGui gui, ServerPlayer player, int slot, String upgradeId, Item icon) {
        ProfessionFragmentConfig.UpgradeData upgrade = ProfessionFragmentConfig.UPGRADES.get(upgradeId);
        if (upgrade == null) {
            gui.setSlot(slot, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cMissing Upgrade")).addLoreLine(Component.literal("§7" + upgradeId)));
            return;
        }
        String from = ProfessionFragmentConfig.normalizeRarity(upgrade.fromFragment);
        String to = ProfessionFragmentConfig.normalizeRarity(upgrade.toFragment);
        int cost = Math.max(1, upgrade.cost);
        int output = Math.max(1, upgrade.output);
        int available = ProfessionFragmentManager.countFragments(player, from);
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal(ProfessionFragmentManager.formatWords(from) + " → " + ProfessionFragmentManager.formatWords(to)).withStyle(getRarityColor(to)))
                .addLoreLine(Component.literal("§7Cost: §6" + cost + "x " + ProfessionFragmentManager.formatWords(from) + " Fragment"))
                .addLoreLine(Component.literal("§7Output: §a" + output + "x " + ProfessionFragmentManager.formatWords(to) + " Fragment"))
                .addLoreLine(Component.literal("§7You have: §e" + available))
                .addLoreLine(Component.literal(available >= cost ? "§eClick to convert" : "§cNot enough fragments"))
                .setCallback((i,c,t) -> {
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fragments upgrade " + upgradeId);
                }));
    }

    private static void openWithdraw(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        SimpleGui gui = base(player, "Withdraw Fragments");
        gui.setSlot(4, new GuiElementBuilder(Items.CHEST).hideDefaultTooltip().setName(Component.literal("§aWithdraw Fragments")));
        int[] slots = {20,21,22,23,24,25};
        String[] rarities = {"COMMON","UNCOMMON","RARE","EPIC","LEGENDARY","MYTHIC"};
        for (int i = 0; i < rarities.length; i++) addWithdrawButton(gui, player, slots[i], rarities[i], Items.PAPER);
        MenuUtil.addBackButton(gui, 49, () -> openTabs(player, backTarget));
        gui.open();
    }

    private static void addWithdrawButton(SimpleGui gui, ServerPlayer player, int slot, String rarity, Item icon) {
        String normalizedRarity = ProfessionFragmentConfig.normalizeRarity(rarity);
        int available = ProfessionFragmentManager.countFragments(player, normalizedRarity);
        int amount = Math.min(16, Math.max(1, available));
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal("Withdraw " + ProfessionFragmentManager.formatWords(normalizedRarity) + " Fragments").withStyle(getRarityColor(normalizedRarity)))
                .addLoreLine(Component.literal("§7Stored: §e" + available))
                .addLoreLine(Component.literal(available > 0 ? "§eClick to withdraw " + amount : "§cNo stored fragments"))
                .setCallback((i,c,t) -> {
                    if (available <= 0) return;
                    player.closeContainer();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "fragments withdraw " + normalizedRarity.toLowerCase() + " " + amount);
                }));
    }

    private static ChatFormatting getRarityColor(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
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
