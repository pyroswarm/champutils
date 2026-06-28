package com.champutils.cosmetic;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class TitleMenu {
    private static final int[] TITLE_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private TitleMenu() {}

    public static void open(ServerPlayer player) {
        open(player, 0);
    }

    public static void open(ServerPlayer player, int requestedPage) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        java.util.Set<String> owned = TitleManager.unlocked(player.getUUID());
        List<TitleConfig.TitleDef> titles = new ArrayList<>(TitleConfig.titles());
        titles.sort(java.util.Comparator.comparing(t -> t.name == null ? t.id : t.name));
        String selected = TitleManager.selected(player.getUUID());

        List<GuiElementBuilder> entries = new ArrayList<>();
        for (TitleConfig.TitleDef def : titles) {
            if (def == null || def.id == null) continue;
            boolean unlocked = owned.contains(def.id);
            boolean active = def.id.equals(selected);
            GuiElementBuilder b = new GuiElementBuilder(active ? Items.NAME_TAG : unlocked ? Items.PAPER : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal((active ? "§aSelected §r" : unlocked ? "§e" : "§7") + TitleManager.displayFor(def.id).replace('&','§')))
                    .addLoreLine(Component.literal("§7Objective: §f" + (def.description == null ? "Unknown" : def.description)))
                    .addLoreLine(Component.literal("§7Scope: §f" + (TitleConfig.isAccountBound(def.id) ? "Account" : "Profile")))
                    .addLoreLine(Component.literal("§7Passive: §a" + TitleConfig.buffText(def)))
                    .addLoreLine(Component.literal(unlocked ? "§eClick to select" : "§8Locked"));
            if (unlocked) b.setCallback((i,c,t) -> { TitleManager.select(player, def.id); open(player, requestedPage); });
            entries.add(b);
        }

        for (String ownedId : owned) {
            boolean known = false;
            for (TitleConfig.TitleDef def : titles) {
                if (def != null && def.id != null && def.id.equals(ownedId)) { known = true; break; }
            }
            if (known || ownedId == null || ownedId.isBlank()) continue;
            String display = TitleManager.displayFor(player.getUUID(), ownedId).replace('&','§');
            boolean active = ownedId.equals(selected);
            GuiElementBuilder b = new GuiElementBuilder(active ? Items.NAME_TAG : Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal((active ? "§aSelected §r" : "§6World First §r") + display))
                    .addLoreLine(Component.literal("§7World First trophy title."))
                    .addLoreLine(Component.literal("§7Passive: §a" + TitleConfig.buffText(TitleConfig.get(ownedId))))
                    .addLoreLine(Component.literal("§eClick to select"));
            b.setCallback((i,c,t) -> { TitleManager.select(player, ownedId); open(player, requestedPage); });
            entries.add(b);
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) TITLE_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        gui.setTitle(Component.literal("Titles §7(" + (page + 1) + "/" + totalPages + ")"));

        gui.setSlot(0, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§7Hide Title"))
                .setCallback((i,c,t) -> { TitleManager.select(player, "none"); open(player, page); }));

        int start = page * TITLE_SLOTS.length;
        for (int i = 0; i < TITLE_SLOTS.length; i++) {
            int entryIndex = start + i;
            if (entryIndex >= entries.size()) break;
            gui.setSlot(TITLE_SLOTS[i], entries.get(entryIndex));
        }

        if (page > 0) {
            gui.setSlot(48, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§ePrevious Page"))
                    .addLoreLine(Component.literal("§7Page " + page + " of " + totalPages))
                    .setCallback((i,c,t) -> open(player, page - 1)));
        }

        gui.setSlot(49, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§fPage " + (page + 1) + "§7/§f" + totalPages))
                .addLoreLine(Component.literal("§7Showing " + Math.min(entries.size(), start + 1) + "-" + Math.min(entries.size(), start + TITLE_SLOTS.length) + " of " + entries.size() + " titles.")));

        if (page + 1 < totalPages) {
            gui.setSlot(50, new GuiElementBuilder(Items.ARROW)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eNext Page"))
                    .addLoreLine(Component.literal("§7Page " + (page + 2) + " of " + totalPages))
                    .setCallback((i,c,t) -> open(player, page + 1)));
        }

        MenuUtil.addBackButton(gui, 45, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }
}
