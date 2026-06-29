package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class MainMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
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
                "§7badges, professions, and progression."
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
                Items.COMPASS,
                "§aTerritories",
                () -> com.champutils.territory.TerritoryMenus.openHub(player),
                "§7Personal territories, guild territories,",
                "§7and public territory browsing."
        );

        MenuUtil.addOpenButton(
                gui,
                8,
                Items.REDSTONE,
                "§aSettings",
                () -> SettingsMenu.open(player),
                "§7Toggle personal options."
        );

        MenuUtil.addOpenButton(
                gui,
                18,
                Items.CLOCK,
                "§eDaily Login",
                () -> com.champutils.dailylogin.DailyLoginMenu.open(player),
                "§7Monthly 20-day reward track.",
                "§7Stay online 30 minutes after reset."
        );

        MenuUtil.addOpenButton(
                gui,
                20,
                Items.NAME_TAG,
                "§dTitles",
                () -> com.champutils.cosmetic.TitleMenu.open(player),
                "§7Select unlocked title cosmetics."
        );

        MenuUtil.addOpenButton(
                gui,
                22,
                Items.NETHER_STAR,
                "§6World Firsts",
                () -> com.champutils.worldfirst.WorldFirstMenu.open(player),
                "§7Server-first achievements.",
                "§7Locked entries show as ???."
        );

        MenuUtil.addOpenButton(
                gui,
                24,
                Items.EMERALD,
                "§aServer Boosters",
                () -> com.champutils.cashshop.CashShopMenu.open(player),
                "§7Cash shop consumables that",
                "§7benefit the full server."
        );

        MenuUtil.addOpenButton(
                gui,
                26,
                Items.ENDER_CHEST,
                "§dTrinket Pouch",
                () -> com.champutils.profession.ProfessionTrinketManager.openDigitalPouch(player),
                "§7Open your digital trinket storage.",
                "§7Also available with /tpouch."
        );

        gui.open();
    }
}
