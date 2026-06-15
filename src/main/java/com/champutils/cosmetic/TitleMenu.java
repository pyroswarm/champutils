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
    private TitleMenu() {}
    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Titles"));
        java.util.Set<String> owned = TitleManager.unlocked(player.getUUID());
        List<TitleConfig.TitleDef> titles = new ArrayList<>(TitleConfig.titles());
        titles.sort(java.util.Comparator.comparing(t -> t.name == null ? t.id : t.name));
        String selected = TitleManager.selected(player.getUUID());
        gui.setSlot(0, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§7Hide Title")).setCallback((i,c,t) -> { TitleManager.select(player, "none"); open(player); }));
        int slot = 9;
        for (TitleConfig.TitleDef def : titles) {
            if (def == null || def.id == null) continue;
            if (slot >= 54) break;
            if (slot == 45) slot++;
            boolean unlocked = owned.contains(def.id);
            boolean active = def.id.equals(selected);
            GuiElementBuilder b = new GuiElementBuilder(active ? Items.NAME_TAG : unlocked ? Items.PAPER : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal((active ? "§aSelected §r" : unlocked ? "§e" : "§7") + TitleManager.displayFor(def.id).replace('&','§')))
                    .addLoreLine(Component.literal("§7Objective: §f" + (def.description == null ? "Unknown" : def.description)))
                    .addLoreLine(Component.literal("§7Passive: §a" + (def.passiveDescription == null ? "No passive bonus." : def.passiveDescription)))
                    .addLoreLine(Component.literal(unlocked ? "§eClick to select" : "§8Locked"));
            if (unlocked) b.setCallback((i,c,t) -> { TitleManager.select(player, def.id); open(player); });
            gui.setSlot(slot++, b);
        }
        MenuUtil.addBackButton(gui, 45, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }
}
