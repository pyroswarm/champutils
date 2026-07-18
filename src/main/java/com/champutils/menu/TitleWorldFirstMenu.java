package com.champutils.menu;

import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class TitleWorldFirstMenu {
    private TitleWorldFirstMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Titles & World Firsts"));
        MenuUtil.fillBorders(gui, 11, 13, 15, 22);

        MenuUtil.addOpenButton(
                gui,
                11,
                Items.NAME_TAG,
                "§dMy Titles",
                () -> com.champutils.cosmetic.TitleMenu.open(player),
                "§7Equip your visible title or",
                "§7manage subtitle buffs."
        );

        MenuUtil.addOpenButton(
                gui,
                13,
                Items.NETHER_STAR,
                "§6World First Trophies",
                () -> com.champutils.worldfirst.WorldFirstMenu.open(player),
                "§7See claimed server-firsts and",
                "§7the trophy titles they unlocked."
        );

        MenuUtil.addInfoCard(
                gui,
                15,
                Items.BOOK,
                "§eHow They Connect",
                "§7World Firsts unlock special titles.",
                "§7Once unlocked, those titles appear",
                "§7inside the normal title menu."
        );

        MenuUtil.addBackButton(gui, 22, () -> MainMenu.open(player));
        gui.open();
    }
}
