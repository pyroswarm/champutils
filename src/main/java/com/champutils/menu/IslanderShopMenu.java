package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.shop.IslanderShopConfig;
import com.champutils.shop.NpcShopConfig;
import com.champutils.shop.NpcShopService;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IslanderShopMenu {
    private IslanderShopMenu() {}

    private record Category(String id, String name, Item icon, String description) {}
    private static final List<Category> CATEGORIES = List.of(
            new Category("farming", "§aFarming", Items.BEETROOT_SEEDS, "§7Seeds and renewable crop starters."),
            new Category("saplings", "§2Saplings", Items.OAK_SAPLING, "§7Wood types and tree progression."),
            new Category("apricorns", "§dApricorn Seeds", Items.WHEAT_SEEDS, "§7All plantable apricorn colors."),
            new Category("tumblestones", "§bTumblestones", Items.COBBLESTONE, "§7Poké Ball crafting resources."),
            new Category("nether", "§cNether Resources", Items.NETHERRACK, "§7Brewing and Nether progression."),
            new Category("utility", "§eUtility Materials", Items.CHEST, "§7Building, redstone, and progression materials.")
    );

    public static void open(ServerPlayer player) { openRoot(player); }

    private static boolean validate(ServerPlayer player) {
        if (player == null) return false;
        if (!PlayerProfileManager.isIslander(player)) {
            player.sendSystemMessage(Component.literal("Only Islander profiles can use the Islander Resource Shop.").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static void openRoot(ServerPlayer player) {
        if (!validate(player)) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(IslanderShopConfig.CONFIG.title == null ? "Islander Resource Shop" : IslanderShopConfig.CONFIG.title));
        MenuUtil.fillBorders(gui);
        gui.setSlot(4, new GuiElementBuilder(Items.WATER_BUCKET).hideDefaultTooltip()
                .setName(Component.literal("§bIslander Resources"))
                .addLoreLine(Component.literal("§7Balance: §f" + EconomyManager.format(EconomyManager.getBalance(player))))
                .addLoreLine(Component.literal("§8Choose a category.")));
        int[] slots = {20, 21, 22, 23, 24, 25};
        for (int i = 0; i < CATEGORIES.size(); i++) {
            Category category = CATEGORIES.get(i);
            long count = IslanderShopConfig.CONFIG.entries.stream().filter(e -> e != null && category.id.equals(categoryFor(e))).count();
            GuiElementBuilder button = new GuiElementBuilder(category.icon).hideDefaultTooltip()
                    .setName(Component.literal(category.name))
                    .addLoreLine(Component.literal(category.description))
                    .addLoreLine(Component.literal("§8" + count + " items"))
                    .addLoreLine(Component.literal(""))
                    .addLoreLine(Component.literal("§eClick to browse"));
            button.setCallback((index, clickType, actionType) -> openCategory(player, category));
            gui.setSlot(slots[i], button);
        }
        gui.open();
    }

    private static void openCategory(ServerPlayer player, Category category) {
        if (!validate(player)) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Islander Shop - " + ChatFormatting.stripFormatting(category.name)));
        MenuUtil.fillBorders(gui);
        gui.setSlot(4, new GuiElementBuilder(category.icon).hideDefaultTooltip()
                .setName(Component.literal(category.name))
                .addLoreLine(Component.literal("§7Balance: §f" + EconomyManager.format(EconomyManager.getBalance(player)))));
        List<NpcShopConfig.ShopEntry> entries = new ArrayList<>();
        for (NpcShopConfig.ShopEntry entry : IslanderShopConfig.CONFIG.entries) if (entry != null && category.id.equals(categoryFor(entry))) entries.add(entry);
        int[] contentSlots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
        for (int i = 0; i < entries.size() && i < contentSlots.length; i++) {
            NpcShopConfig.ShopEntry entry = entries.get(i);
            Item icon = NpcShopService.resolveItem(entry.icon);
            if (icon == Items.AIR) icon = Items.CHEST;
            GuiElementBuilder builder = new GuiElementBuilder(icon).hideDefaultTooltip()
                    .setName(Component.literal(entry.displayName == null ? "§fShop Item" : entry.displayName));
            if (entry.lore != null) for (String line : entry.lore) builder.addLoreLine(Component.literal(line));
            builder.addLoreLine(Component.literal(""));
            builder.addLoreLine(Component.literal("§6Price: §f" + EconomyManager.format(Math.max(0L, entry.price))));
            builder.addLoreLine(Component.literal("§eClick to buy"));
            builder.setCallback((index, clickType, actionType) -> { NpcShopService.buy(player, entry); openCategory(player, category); });
            gui.setSlot(contentSlots[i], builder);
        }
        gui.setSlot(49, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack to Categories"))
                .setCallback((index, clickType, actionType) -> openRoot(player)));
        gui.open();
    }

    private static String categoryFor(NpcShopConfig.ShopEntry entry) {
        String id = entry.id == null ? "" : entry.id.toLowerCase(Locale.ROOT);
        if (id.contains("apricorn") && id.contains("seed")) return "apricorns";
        if (id.contains("sapling")) return "saplings";
        if (id.contains("tumblestone")) return "tumblestones";
        if (id.contains("seed") || id.endsWith(":sugar_cane") || id.endsWith(":cactus") || id.endsWith(":bamboo") || id.endsWith(":kelp")) return "farming";
        if (id.contains("netherrack") || id.contains("blackstone") || id.contains("soul_sand") || id.contains("soul_soil") || id.contains("nether_wart") || id.contains("blaze") || id.contains("magma") || id.contains("glowstone")) return "nether";
        return "utility";
    }
}
