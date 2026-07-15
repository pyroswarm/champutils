package com.champutils.cosmetic;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.*;

public final class TitleMenu {
    private static final int[] CONTENT_SLOTS = {
            9,10,11,12,13,14,15,16,17,
            18,19,20,21,22,23,24,25,26,
            27,28,29,30,31,32,33,34,35,
            36,37,38,39,40,41,42,43,44
    };

    private static final List<Category> CATEGORIES = List.of(
            new Category("CATCHING", "Catching & Dex", Items.ENDER_PEARL),
            new Category("BATTLE", "Battling", Items.IRON_SWORD),
            new Category("GYMS", "Gyms & Champion", Items.GOLDEN_HELMET),
            new Category("BATTLE_TOWER", "Battle Tower", Items.OBSERVER),
            new Category("PROFESSIONS", "Professions", Items.DIAMOND_PICKAXE),
            new Category("BREEDING", "Breeding", Items.EGG),
            new Category("PROFILE", "Profiles & Playtime", Items.CLOCK),
            new Category("WORLD_FIRSTS", "World Firsts", Items.NETHER_STAR),
            new Category("GENERAL", "General", Items.BOOK),
            new Category("SPECIAL", "Special", Items.NAME_TAG)
    );

    private TitleMenu() {}

    public static void open(ServerPlayer player) { openCategories(player); }

    private static void openCategories(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Title Categories"));
        Set<String> owned = TitleManager.unlocked(player.getUUID());
        List<TitleConfig.TitleDef> all = visibleTitles(owned);

        int slot = 10;
        for (Category category : CATEGORIES) {
            List<TitleConfig.TitleDef> entries = byCategory(all, category.id());
            if (entries.isEmpty()) continue;
            long unlocked = entries.stream().filter(t -> owned.contains(t.id)).count();
            int target = slot;
            gui.setSlot(target, new GuiElementBuilder(category.icon())
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + category.label()))
                    .addLoreLine(Component.literal("§7Unlocked: §f" + unlocked + "§7/§f" + entries.size()))
                    .addLoreLine(Component.literal("§eClick to browse."))
                    .setCallback((i,c,t) -> openCategory(player, category.id(), 0)));
            slot++;
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
        }
        gui.setSlot(4, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip()
                .setName(Component.literal("§7Hide Shown Title"))
                .setCallback((i,c,t) -> { TitleManager.select(player, "none"); openCategories(player); }));
        MenuUtil.addBackButton(gui, 45, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }

    public static void open(ServerPlayer player, int requestedPage) { openCategories(player); }

    private static void openCategory(ServerPlayer player, String categoryId, int requestedPage) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        Set<String> owned = TitleManager.unlocked(player.getUUID());
        Set<String> subTitles = TitleManager.subtitles(player.getUUID());
        List<TitleConfig.TitleDef> titles = byCategory(visibleTitles(owned), categoryId);
        titles.sort(Comparator.comparing(t -> t.name == null ? t.id : t.name));
        String selected = TitleManager.selected(player.getUUID());

        int totalPages = Math.max(1, (int)Math.ceil(titles.size() / (double)CONTENT_SLOTS.length));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        String label = CATEGORIES.stream().filter(c -> c.id().equals(categoryId)).map(Category::label).findFirst().orElse("Titles");
        gui.setTitle(Component.literal(label + " §7(" + (page + 1) + "/" + totalPages + ")"));

        int start = page * CONTENT_SLOTS.length;
        for (int i=0; i<CONTENT_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= titles.size()) break;
            TitleConfig.TitleDef def = titles.get(idx);
            gui.setSlot(CONTENT_SLOTS[i], entry(player, categoryId, page, def, owned.contains(def.id), def.id.equals(selected), subTitles.contains(def.id)));
        }
        if (page > 0) gui.setSlot(48, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page")).setCallback((i,c,t)->openCategory(player, categoryId, page-1)));
        gui.setSlot(49, new GuiElementBuilder(Items.BOOK).hideDefaultTooltip().setName(Component.literal("§fPage " + (page+1) + "§7/§f" + totalPages))
                .addLoreLine(Component.literal("§7" + titles.size() + " titles in this category.")));
        if (page + 1 < totalPages) gui.setSlot(50, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page")).setCallback((i,c,t)->openCategory(player, categoryId, page+1)));
        MenuUtil.addBackButton(gui, 45, () -> openCategories(player));
        gui.open();
    }

    private static List<TitleConfig.TitleDef> visibleTitles(Set<String> owned) {
        List<TitleConfig.TitleDef> list = new ArrayList<>();
        for (TitleConfig.TitleDef def : TitleConfig.titles()) {
            if (def == null || def.id == null) continue;
            if (TitleConfig.isManualAdminTitle(def) && !owned.contains(def.id)) continue;
            boolean worldFirst = def.unlock != null && "world_first".equalsIgnoreCase(def.unlock.type);
            // World-first rewards are secret in /titles until this profile has actually earned them.
            // The separate /worldfirsts menu remains responsible for showing undiscovered entries as ???.
            if (worldFirst && !owned.contains(def.id)) continue;
            list.add(def);
        }
        return list;
    }

    private static List<TitleConfig.TitleDef> byCategory(List<TitleConfig.TitleDef> all, String category) {
        List<TitleConfig.TitleDef> out = new ArrayList<>();
        for (TitleConfig.TitleDef def : all) {
            String actual = def.category == null || def.category.isBlank() ? "GENERAL" : def.category.toUpperCase(Locale.ROOT);
            boolean worldFirst = def.unlock != null && "world_first".equalsIgnoreCase(def.unlock.type);
            if ("WORLD_FIRSTS".equals(category)) {
                if (worldFirst) out.add(def);
            } else if (!worldFirst && actual.equals(category)) {
                out.add(def);
            }
        }
        return out;
    }

    private static GuiElementBuilder entry(ServerPlayer player, String category, int page, TitleConfig.TitleDef def, boolean unlocked, boolean active, boolean subTitle) {
        GuiElementBuilder b = new GuiElementBuilder(active ? Items.NAME_TAG : subTitle ? Items.AMETHYST_SHARD : unlocked ? Items.PAPER : Items.GRAY_DYE)
                .hideDefaultTooltip()
                .setName(Component.literal((active ? "§aShown §r" : subTitle ? "§dSub Title §r" : unlocked ? "§e" : "§7") + TitleManager.displayFor(player.getUUID(), def.id).replace('&','§')))
                .addLoreLine(Component.literal("§7Objective: §f" + (def.description == null ? "Unknown" : def.description)))
                .addLoreLine(Component.literal("§7Scope: §f" + (TitleConfig.isAccountBound(def.id) ? "Account" : "Profile")))
                .addLoreLine(Component.literal("§7Passive: §a" + TitleConfig.buffText(def)))
                .addLoreLine(Component.literal(active ? "§aCurrently shown in chat." : subTitle ? "§dEquipped as hidden sub title. §7(50% buffs)" : unlocked ? "§eUnlocked" : "§8Locked"));
        if (unlocked) {
            b.addLoreLine(Component.literal("§eLeft Click: §7select shown title"));
            b.addLoreLine(Component.literal("§dRight Click: §7toggle hidden sub title"));
            b.setCallback((i,c,t) -> {
                if (isRightClick(c)) TitleManager.toggleSubTitle(player, def.id); else TitleManager.select(player, def.id);
                openCategory(player, category, page);
            });
        }
        return b;
    }

    private static boolean isRightClick(Object clickType) {
        String text = String.valueOf(clickType).toLowerCase(Locale.ROOT);
        return text.contains("right") || text.equals("1");
    }

    private record Category(String id, String label, Item icon) {}
}
