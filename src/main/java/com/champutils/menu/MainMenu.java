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
                "§cBattles",
                () -> BattleMenu.open(player),
                "§7Ranked, casual, spectate,",
                "§7or leave your current queue."
        );

        MenuUtil.addOpenButton(
                gui,
                2,
                CobblemonItems.POKEDEX_RED,
                "§dProfile",
                () -> ProfileMenu.open(player),
                "§7Your trainer card, progress,",
                "§7badges, season, and progression."
        );

        MenuUtil.addOpenButton(
                gui,
                4,
                Items.NETHER_STAR,
                "§6Leaderboards",
                () -> LeaderboardMenu.open(player),
                "§7View top trainers, profiles,",
                "§7and profession rankings."
        );

        MenuUtil.addOpenButton(
                gui,
                6,
                Items.COMPASS,
                "§aTerritories",
                () -> com.champutils.territory.TerritoryMenus.openHub(player),
                "§7Manage your land, visit public",
                "§7territories, or browse guild lands."
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
                "§7Monthly 20-day login rewards.",
                "§7Play 30 minutes after reset."
        );

        MenuUtil.addOpenButton(
                gui,
                20,
                Items.NAME_TAG,
                "§dTitles & World Firsts",
                () -> com.champutils.menu.TitleWorldFirstMenu.open(player),
                "§7Equip titles and view server-first",
                "§7achievement trophy titles."
        );

        MenuUtil.addOpenButton(
                gui,
                22,
                Items.DIAMOND_PICKAXE,
                "§aProfessions",
                () -> com.champutils.menu.ProfessionsMenu.open(player),
                "§7Track levels, sublevels,",
                "§7passives, and chunk rewards."
        );

        MenuUtil.addOpenButton(
                gui,
                24,
                Items.EMERALD,
                "§aBoosters",
                () -> com.champutils.cashshop.CashShopMenu.open(player),
                "§7Activate server-wide boosts",
                "§7from your booster credits."
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
