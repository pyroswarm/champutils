package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class MainMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x1, player);
        gui.setTitle(Component.literal("Cobble Champs"));

        MenuUtil.addOpenButton(
                gui,
                0,
                CobblemonItems.POKE_BALL,
                "§cPvP Queues",
                () -> BattleMenu.open(player),
                "§7Ranked, casual, and leave queue."
        );

        MenuUtil.addOpenButton(
                gui,
                2,
                CobblemonItems.POKEDEX_RED,
                "§dProfile",
                () -> ProfileMenu.open(player),
                "§7Your trainer card, progress,",
                "§7badges, professions, and lookup."
        );

        MenuUtil.addOpenButton(
                gui,
                4,
                Items.NETHER_STAR,
                "§6Leaderboards",
                () -> LeaderboardMenu.open(player),
                "§7View RP and profession rankings."
        );

        MenuUtil.addOpenButton(
                gui,
                6,
                Items.REDSTONE,
                "§aSettings",
                () -> SettingsMenu.open(player),
                "§7Toggle personal options."
        );

        MenuUtil.addOpenButton(
                gui,
                8,
                Items.BOOK,
                "§bHelp",
                () -> HelpMenu.open(player),
                "§7Guides will be added here later."
        );

        gui.open();
    }
}
