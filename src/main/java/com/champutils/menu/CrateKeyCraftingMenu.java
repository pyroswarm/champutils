package com.champutils.menu;

import com.champutils.crate.CrateConfig;
import com.champutils.crate.CrateCreditManager;
import com.champutils.crate.CrateKeyCraftingConfig;
import com.champutils.crate.CrateKeyCraftingService;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class CrateKeyCraftingMenu {
    private CrateKeyCraftingMenu() {}

    public static void open(ServerPlayer player) {
        open(player, GearWorkshopMenu::open);
    }

    public static void open(ServerPlayer player, Consumer<ServerPlayer> backTarget) {
        CrateKeyCraftingConfig.load();
        CrateConfig.load();

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Crate Key Crafting"));

        MenuUtil.fillBorders(gui, 4, 11,12,13,14,15, 20,21,22,23,24, 29,30,31,32,33, 49);

        gui.setSlot(4, new GuiElementBuilder(Items.TRIPWIRE_HOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Crate Key Crafting"))
                .addLoreLine(Component.literal("§7Craft regular crate keys with huge material costs."))
                .addLoreLine(Component.literal("§7Rewards are added as crate key credits."))
                .addLoreLine(Component.literal("§8Edit costs in crate_key_crafting.json.")));

        addRecipe(gui, player, 11, "common", Items.TRIPWIRE_HOOK);
        addRecipe(gui, player, 12, "uncommon", Items.TRIPWIRE_HOOK);
        addRecipe(gui, player, 13, "rare", Items.TRIPWIRE_HOOK);
        addRecipe(gui, player, 14, "epic", Items.TRIPWIRE_HOOK);
        addRecipe(gui, player, 15, "legendary", Items.TRIPWIRE_HOOK);
        addRecipe(gui, player, 22, "mythic", Items.TRIPWIRE_HOOK);

        MenuUtil.addBackButton(gui, 49, () -> {
            if (backTarget != null) backTarget.accept(player); else GearWorkshopMenu.open(player);
        });

        gui.open();
    }

    private static void addRecipe(SimpleGui gui, ServerPlayer player, int slot, String crateId, Item icon) {
        String id = CrateCreditManager.normalize(crateId);
        CrateKeyCraftingConfig.RecipeData recipe = CrateKeyCraftingConfig.recipes.get(id);
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);

        if (recipe == null || crate == null) {
            gui.setSlot(slot, new GuiElementBuilder(Items.BARRIER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§cMissing Recipe"))
                    .addLoreLine(Component.literal("§7Missing crate key recipe for: §f" + id)));
            return;
        }

        String fragment = ProfessionFragmentConfig.normalizeRarity(recipe.fragment);
        int fragmentCost = Math.max(0, recipe.fragmentCost);
        int fragmentHave = ProfessionFragmentManager.countFragments(player, fragment);
        int ownedKeys = CrateCreditManager.getCredits(player, id);
        boolean canCraft = fragmentHave >= fragmentCost;

        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal("Craft " + crate.displayName + " Key").withStyle(color(id)))
                .addLoreLine(Component.literal("§7Output: §a" + Math.max(1, recipe.outputKeys) + "x key credit"))
                .addLoreLine(Component.literal("§7Current keys: §e" + ownedKeys))
                .addLoreLine(Component.literal("§6Costs:"));

        builder.addLoreLine(Component.literal(status(fragmentHave, fragmentCost) + fragmentCost + "x " + ProfessionFragmentManager.formatWords(fragment) + " Fragment §8(you: " + fragmentHave + ")"));

        if (recipe.items != null) {
            for (CrateKeyCraftingConfig.ItemCost cost : recipe.items) {
                Item item = CrateKeyCraftingService.resolveItem(cost == null ? null : cost.item);
                int amount = cost == null ? 0 : Math.max(0, cost.amount);
                if (item == Items.AIR || amount <= 0) continue;
                int have = CrateKeyCraftingService.countItem(player, item);
                if (have < amount) canCraft = false;
                builder.addLoreLine(Component.literal(status(have, amount) + amount + "x " + CrateKeyCraftingService.itemName(item) + " §8(you: " + have + ")"));
            }
        }

        boolean finalCanCraft = canCraft;
        builder.addLoreLine(Component.literal(finalCanCraft ? "§eClick to craft" : "§cMissing materials"));
        builder.setCallback((i, c, t) -> {
            if (!finalCanCraft) return;
            CrateKeyCraftingService.CraftResult result = CrateKeyCraftingService.craft(player, id);
            if (!result.success()) {
                player.sendSystemMessage(Component.literal("§c" + result.error()));
                open(player);
                return;
            }
            player.sendSystemMessage(Component.literal("§aCrafted §6" + result.outputKeys() + "x " + result.crateName() + " key credit§a."));
            open(player);
        });

        gui.setSlot(slot, builder);
    }

    private static String status(int have, int need) {
        return have >= need ? "§a" : "§c";
    }

    private static ChatFormatting color(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        return switch (rarity.trim().toLowerCase(Locale.ROOT)) {
            case "uncommon" -> ChatFormatting.GREEN;
            case "rare" -> ChatFormatting.BLUE;
            case "epic" -> ChatFormatting.LIGHT_PURPLE;
            case "legendary" -> ChatFormatting.GOLD;
            case "mythic" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }
}
