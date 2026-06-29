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
import java.util.Locale;
import java.util.Set;

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
        Set<String> owned = TitleManager.unlocked(player.getUUID());
        Set<String> subTitles = TitleManager.subtitles(player.getUUID());
        List<TitleConfig.TitleDef> titles = new ArrayList<>(TitleConfig.titles());
        titles.sort(java.util.Comparator.comparing(t -> t.name == null ? t.id : t.name));
        String selected = TitleManager.selected(player.getUUID());

        List<GuiElementBuilder> entries = new ArrayList<>();
        for (TitleConfig.TitleDef def : titles) {
            if (def == null || def.id == null) continue;
            entries.add(entry(player, requestedPage, def.id, def.description, TitleConfig.isAccountBound(def.id), owned.contains(def.id), def.id.equals(selected), subTitles.contains(def.id)));
        }

        for (String ownedId : owned) {
            boolean known = false;
            for (TitleConfig.TitleDef def : titles) {
                if (def != null && def.id != null && def.id.equals(ownedId)) { known = true; break; }
            }
            if (known || ownedId == null || ownedId.isBlank()) continue;
            entries.add(entry(player, requestedPage, ownedId, "World First trophy title.", false, true, ownedId.equals(selected), subTitles.contains(ownedId)));
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) TITLE_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        gui.setTitle(Component.literal("Titles §7(" + (page + 1) + "/" + totalPages + ")"));

        gui.setSlot(0, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§7Hide Shown Title"))
                .addLoreLine(Component.literal("§7This only hides your displayed chat title."))
                .addLoreLine(Component.literal("§7Hidden sub titles stay equipped."))
                .setCallback((i,c,t) -> { TitleManager.select(player, "none"); open(player, page); }));

        gui.setSlot(8, new GuiElementBuilder(Items.NETHER_STAR)
                .hideDefaultTooltip()
                .setName(Component.literal("§dHidden Sub Titles §7(" + subTitles.size() + "/3)"))
                .addLoreLine(Component.literal("§7Right-click an unlocked title to equip it here."))
                .addLoreLine(Component.literal("§7Sub titles do not show in chat."))
                .addLoreLine(Component.literal("§7Each sub title gives §f50%§7 of its buffs."))
                .addLoreLine(Component.literal("§7Right-click a current sub title to unequip it.")));

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
                .addLoreLine(Component.literal("§7Showing " + Math.min(entries.size(), start + 1) + "-" + Math.min(entries.size(), start + TITLE_SLOTS.length) + " of " + entries.size() + " titles."))
                .addLoreLine(Component.literal("§eLeft Click: §7shown title"))
                .addLoreLine(Component.literal("§dRight Click: §7hidden sub title")));

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

    private static GuiElementBuilder entry(ServerPlayer player, int page, String titleId, String description, boolean accountBound, boolean unlocked, boolean active, boolean subTitle) {
        GuiElementBuilder b = new GuiElementBuilder(active ? Items.NAME_TAG : subTitle ? Items.AMETHYST_SHARD : unlocked ? Items.PAPER : Items.GRAY_DYE)
                .hideDefaultTooltip()
                .setName(Component.literal((active ? "§aShown §r" : subTitle ? "§dSub Title §r" : unlocked ? "§e" : "§7") + TitleManager.displayFor(player.getUUID(), titleId).replace('&','§')))
                .addLoreLine(Component.literal("§7Objective: §f" + (description == null ? "Unknown" : description)))
                .addLoreLine(Component.literal("§7Scope: §f" + (accountBound ? "Account" : "Profile")))
                .addLoreLine(Component.literal("§7Passive: §a" + TitleConfig.buffText(TitleConfig.get(titleId))))
                .addLoreLine(Component.literal(active ? "§aCurrently shown in chat." : subTitle ? "§dEquipped as hidden sub title. §7(50% buffs)" : unlocked ? "§eUnlocked" : "§8Locked"));
        if (unlocked) {
            b.addLoreLine(Component.literal("§eLeft Click: §7select as shown title"));
            b.addLoreLine(Component.literal("§dRight Click: §7equip/unequip hidden sub title"));
            if (active) b.addLoreLine(Component.literal("§cShown title cannot also be a sub title."));
            b.setCallback((i,c,t) -> {
                if (isRightClick(c)) TitleManager.toggleSubTitle(player, titleId);
                else TitleManager.select(player, titleId);
                open(player, page);
            });
        }
        return b;
    }

    private static boolean isRightClick(Object clickType) {
        String text = String.valueOf(clickType).toLowerCase(Locale.ROOT);
        return text.contains("right") || text.equals("1");
    }
}
