package com.champutils.menu;

import com.champutils.profession.ProfessionBackpackConfig;
import com.champutils.profession.ProfessionBackpackManager;
import com.champutils.profession.ProfessionType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ProfessionBackpackMenu {
    private static final int[] CONTENT = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43};
    private ProfessionBackpackMenu() {}

    public static void open(ServerPlayer player) { open(player, ProfessionType.MINING, 0, ""); }

    public static void open(ServerPlayer player, String search) { open(player, ProfessionType.MINING, 0, search); }

    public static void open(ServerPlayer player, ProfessionType profession, int page) { open(player, profession, page, ""); }

    public static void open(ServerPlayer player, ProfessionType profession, int page, String search) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(search == null || search.isBlank() ? "Profession Backpack" : "Backpack Search: " + search));
        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43,45,49,53);

        gui.setSlot(4, new GuiElementBuilder(Items.CHEST).hideDefaultTooltip()
                .setName(Component.literal("§6Profession Backpack"))
                .addLoreLine(Component.literal("§7Profile-bound digital profession storage."))
                .addLoreLine(Component.literal("§7Items appear after players collect them."))
                .addLoreLine(Component.literal("§7Search: §f/backpack search <item>")));

        tab(gui, 0, player, profession, ProfessionType.MINING, Items.IRON_PICKAXE);
        tab(gui, 1, player, profession, ProfessionType.FORESTRY, Items.OAK_LOG);
        tab(gui, 2, player, profession, ProfessionType.FARMING, Items.WHEAT);

        boolean auto = ProfessionBackpackManager.isAutopickupEnabled(player);
        gui.setSlot(8, new GuiElementBuilder(auto ? Items.LIME_DYE : Items.RED_DYE).hideDefaultTooltip()
                .setName(Component.literal(auto ? "§aAutopickup: ON" : "§cAutopickup: OFF"))
                .addLoreLine(Component.literal("§7When on, profession drops are stored digitally."))
                .addLoreLine(Component.literal("§eClick to toggle."))
                .setCallback((i,c,t) -> {
                    boolean enabled = ProfessionBackpackManager.toggleAutopickup(player);
                    player.sendSystemMessage(Component.literal(enabled ? "§aProfession backpack autopickup enabled." : "§cProfession backpack autopickup disabled."));
                    open(player, profession, page, search);
                }));

        List<ProfessionBackpackConfig.ItemData> items = visibleItems(profession, player, search);
        int maxPage = Math.max(0, (items.size() - 1) / CONTENT.length);
        int fixedPage = Math.max(0, Math.min(page, maxPage));
        int start = fixedPage * CONTENT.length;
        for (int idx = 0; idx < CONTENT.length && start + idx < items.size(); idx++) {
            ProfessionBackpackConfig.ItemData data = items.get(start + idx);
            long have = ProfessionBackpackManager.count(player, data.item);
            gui.setSlot(CONTENT[idx], new GuiElementBuilder(icon(data.item)).hideDefaultTooltip()
                    .setName(Component.literal("§e" + data.displayName))
                    .addLoreLine(Component.literal("§7Stored: §a" + have))
                    .addLoreLine(Component.literal("§7Left Click: withdraw §f1"))
                    .addLoreLine(Component.literal("§7Shift Click: withdraw §f64"))
                    .setCallback((slot, click, type) -> {
                        int amount = type == net.minecraft.world.inventory.ClickType.QUICK_MOVE ? 64 : 1;
                        if (!ProfessionBackpackManager.withdraw(player, data.item, amount)) {
                            player.sendSystemMessage(Component.literal("§cYou do not have enough " + data.displayName + "."));
                        }
                        open(player, profession, fixedPage, search);
                    }));
        }

        if (fixedPage > 0) gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page")).setCallback((i,c,t) -> open(player, profession, fixedPage - 1, search)));
        gui.setSlot(49, new GuiElementBuilder(Items.BOOK).hideDefaultTooltip().setName(Component.literal("§7Page §f" + (fixedPage + 1) + "§7/§f" + (maxPage + 1))));
        if (fixedPage < maxPage) gui.setSlot(53, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page")).setCallback((i,c,t) -> open(player, profession, fixedPage + 1, search)));
        gui.open();
    }

    private static void tab(SimpleGui gui, int slot, ServerPlayer player, ProfessionType selected, ProfessionType tab, Item icon) {
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal((selected == tab ? "§a" : "§7") + label(tab)))
                .addLoreLine(Component.literal("§eClick to open."))
                .setCallback((i,c,t) -> open(player, tab, 0, "")));
    }

    static List<ProfessionBackpackConfig.ItemData> visibleItems(ProfessionType profession, ServerPlayer player) { return visibleItems(profession, player, ""); }

    static List<ProfessionBackpackConfig.ItemData> visibleItems(ProfessionType profession, ServerPlayer player, String search) {
        Map<String, Long> balances = ProfessionBackpackManager.balances(player);
        return ProfessionBackpackConfig.CONFIG.items.values().stream()
                .filter(data -> profession.name().equalsIgnoreCase(data.profession))
                .filter(data -> balances.getOrDefault(data.item, 0L) > 0L)
                .filter(data -> search == null || search.isBlank() || data.displayName.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)) || data.item.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing((ProfessionBackpackConfig.ItemData d) -> d.displayName.toLowerCase(Locale.ROOT)).thenComparing(d -> d.item))
                .toList();
    }

    private static String label(ProfessionType type) {
        return switch (type) { case MINING -> "Mining"; case FORESTRY -> "Forestry"; case FARMING -> "Farming"; default -> type.name(); };
    }

    private static Item icon(String itemId) {
        Item item = ProfessionBackpackManager.item(itemId);
        return item == Items.AIR ? Items.BARRIER : item;
    }
}
