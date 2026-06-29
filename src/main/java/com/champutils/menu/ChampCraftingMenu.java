package com.champutils.menu;

import com.champutils.crafting.ChampCraftingConfig;
import com.champutils.crafting.ChampCraftingService;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ChampCraftingMenu {
    private static final int[] CONTENT = new int[] {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private ChampCraftingMenu() {}

    public static void open(ServerPlayer player) {
        open(player, ProfessionForemanMenu::open);
    }

    public static void open(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        openCategories(player, backTarget);
    }

    private static void openCategories(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        ChampCraftingConfig.load();
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(ChampCraftingConfig.CONFIG.title == null ? "Champ Crafting" : ChampCraftingConfig.CONFIG.title));
        MenuUtil.fillBorders(gui);

        gui.setSlot(4, new GuiElementBuilder(Items.CRAFTING_TABLE)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Champ Crafting"))
                .addLoreLine(Component.literal("§7Craft rare Genesis and competitive items"))
                .addLoreLine(Component.literal("§7with huge profession backpack costs."))
                .addLoreLine(Component.literal("§8Edit: config/champutils/champ_crafting.json")));

        List<String> categories = ChampCraftingConfig.categories();
        if (categories.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§cNo recipes available"))
                    .addLoreLine(Component.literal("§7Check champ_crafting.json.")));
        } else {
            int limit = Math.min(categories.size(), CONTENT.length);
            for (int i = 0; i < limit; i++) {
                String category = categories.get(i);
                List<ChampCraftingConfig.RecipeData> recipes = ChampCraftingConfig.recipesForCategory(category);
                Item icon = iconForCategory(category, recipes);
                gui.setSlot(CONTENT[i], new GuiElementBuilder(icon)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§e" + category))
                        .addLoreLine(Component.literal("§7Recipes: §f" + recipes.size()))
                        .addLoreLine(Component.literal("§eClick to browse."))
                        .setCallback((slot, click, type) -> openCategory(player, category, 0, backTarget)));
            }
        }

        MenuUtil.addBackButton(gui, 49, () -> {
            if (backTarget != null) backTarget.accept(player); else ProfessionForemanMenu.open(player);
        });
        gui.open();
    }

    private static void openCategory(ServerPlayer player, String category, int page, Consumer<ServerPlayer> backTarget) {
        List<ChampCraftingConfig.RecipeData> all = ChampCraftingConfig.recipesForCategory(category);
        int maxPage = Math.max(0, (all.size() - 1) / CONTENT.length);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * CONTENT.length;
        int end = Math.min(all.size(), start + CONTENT.length);
        List<ChampCraftingConfig.RecipeData> pageRecipes = start >= end ? new ArrayList<>() : all.subList(start, end);

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Champ Crafting: " + category + " " + (safePage + 1) + "/" + (maxPage + 1)));
        MenuUtil.fillBorders(gui);

        gui.setSlot(4, new GuiElementBuilder(iconForCategory(category, all))
                .hideDefaultTooltip()
                .setName(Component.literal("§6" + category))
                .addLoreLine(Component.literal("§7Costs pull from your profile backpack"))
                .addLoreLine(Component.literal("§7and inventory where configured.")));

        for (int i = 0; i < pageRecipes.size(); i++) {
            ChampCraftingConfig.RecipeData recipe = pageRecipes.get(i);
            gui.setSlot(CONTENT[i], recipeButton(player, recipe, category, safePage, backTarget));
        }

        gui.setSlot(45, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("§eBack to Categories"))
                .setCallback((slot, click, type) -> openCategories(player, backTarget)));
        if (safePage > 0) {
            gui.setSlot(48, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§ePrevious Page"))
                    .setCallback((slot, click, type) -> openCategory(player, category, safePage - 1, backTarget)));
        }
        if (safePage < maxPage) {
            gui.setSlot(50, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eNext Page"))
                    .setCallback((slot, click, type) -> openCategory(player, category, safePage + 1, backTarget)));
        }

        gui.open();
    }

    private static GuiElementBuilder recipeButton(ServerPlayer player, ChampCraftingConfig.RecipeData recipe, String category, int page, Consumer<ServerPlayer> backTarget) {
        Item icon = ChampCraftingService.resolveItem(recipe.icon == null || recipe.icon.isBlank() ? recipe.outputItem : recipe.icon);
        if (icon == Items.AIR) icon = ChampCraftingService.resolveItem(recipe.outputItem);
        if (icon == Items.AIR) icon = Items.BARRIER;

        boolean canCraft = true;
        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal("§f" + (recipe.displayName == null ? recipe.outputItem : recipe.displayName)))
                .addLoreLine(Component.literal("§7Output: §a" + Math.max(1, recipe.outputAmount) + "x " + ChampCraftingService.itemName(recipe.outputItem)))
                .addLoreLine(Component.literal("§7ID: §8" + recipe.outputItem));

        if (recipe.lore != null) {
            for (String line : recipe.lore) {
                builder.addLoreLine(Component.literal(line));
            }
        }

        builder.addLoreLine(Component.literal(""));
        builder.addLoreLine(Component.literal("§6Costs:"));
        if (recipe.costs == null || recipe.costs.isEmpty()) {
            canCraft = false;
            builder.addLoreLine(Component.literal("§cNo costs configured."));
        } else {
            for (ChampCraftingConfig.CostData cost : recipe.costs) {
                ChampCraftingService.CostStatus status = ChampCraftingService.status(player, cost);
                if (!status.valid()) {
                    canCraft = false;
                    builder.addLoreLine(Component.literal("§c" + status.message()));
                    continue;
                }
                if (status.have() < status.need()) canCraft = false;
                builder.addLoreLine(Component.literal((status.have() >= status.need() ? "§a" : "§c")
                        + status.need() + "x " + ChampCraftingService.itemName(cost.item)
                        + " §8(" + ChampCraftingService.sourceLabel(cost.source) + ", you: " + status.have() + ")"));
            }
        }

        boolean finalCanCraft = canCraft;
        builder.addLoreLine(Component.literal(""));
        builder.addLoreLine(Component.literal(finalCanCraft ? "§eClick to craft." : "§cMissing materials."));
        builder.setCallback((slot, click, type) -> {
            if (!finalCanCraft) {
                player.sendSystemMessage(Component.literal("§cYou are missing materials for this recipe."));
                openCategory(player, category, page, backTarget);
                return;
            }
            ChampCraftingService.CraftResult result = ChampCraftingService.craft(player, recipe.id);
            if (!result.success()) {
                player.sendSystemMessage(Component.literal("§c" + result.error()));
            } else {
                player.sendSystemMessage(Component.literal("§aCrafted §6" + result.outputAmount() + "x " + result.displayName() + "§a."));
            }
            openCategory(player, category, page, backTarget);
        });
        return builder;
    }

    private static Item iconForCategory(String category, List<ChampCraftingConfig.RecipeData> recipes) {
        String c = category == null ? "" : category.toLowerCase(java.util.Locale.ROOT);
        if (c.contains("mega")) return Items.AMETHYST_SHARD;
        if (c.contains("hyper")) return Items.GOLD_INGOT;
        if (c.contains("ability")) return Items.NETHER_STAR;
        if (c.contains("key")) return Items.NETHER_STAR;
        if (c.contains("z-crystal")) return Items.PRISMARINE_CRYSTALS;
        if (c.contains("tera")) return Items.AMETHYST_CLUSTER;
        if (c.contains("plate")) return Items.SMOOTH_STONE;
        if (c.contains("memory")) return Items.REDSTONE;
        if (c.contains("drive")) return Items.COPPER_INGOT;
        if (c.contains("mask")) return Items.CARVED_PUMPKIN;
        if (c.contains("rare")) return Items.ENCHANTED_GOLDEN_APPLE;
        if (recipes != null) {
            for (ChampCraftingConfig.RecipeData recipe : recipes) {
                if (recipe == null) continue;
                Item item = ChampCraftingService.resolveItem(recipe.icon == null || recipe.icon.isBlank() ? recipe.outputItem : recipe.icon);
                if (item != Items.AIR) return item;
                item = ChampCraftingService.resolveItem(recipe.outputItem);
                if (item != Items.AIR) return item;
            }
        }
        return Items.CRAFTING_TABLE;
    }
}
