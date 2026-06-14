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
        List<String> titles = new ArrayList<>(TitleManager.unlocked(player.getUUID()));
        String selected = TitleManager.selected(player.getUUID());
        gui.setSlot(0, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§7Hide Title")).setCallback((i,c,t) -> { TitleManager.select(player, "none"); open(player); }));
        int slot = 9;
        for (String id : titles) {
            if (slot >= 54) break;
            if (slot == 45) slot++;
            boolean active = id.equals(selected);
            gui.setSlot(slot++, new GuiElementBuilder(active ? Items.NAME_TAG : Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal((active ? "§aSelected §r" : "") + TitleManager.displayFor(id).replace('&','§')))
                    .addLoreLine(Component.literal("§eClick to select"))
                    .setCallback((i,c,t) -> { TitleManager.select(player, id); open(player); }));
        }
        MenuUtil.addBackButton(gui, 45, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }
}
