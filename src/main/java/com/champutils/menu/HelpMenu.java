package com.champutils.menu;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class HelpMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x1, player);
        gui.setTitle(Component.literal("Help"));

        MenuUtil.addOpenButton(
                gui,
                0,
                Items.COMPASS,
                "§bGetting Started",
                () -> openGettingStarted(player),
                "§7Learn the basic server loop.",
                "§7Good first stop for new players."
        );

        MenuUtil.addOpenButton(
                gui,
                1,
                Items.IRON_SWORD,
                "§6Gyms & Unlocks",
                () -> openGyms(player),
                "§7Learn how gym progression works.",
                "§7Badges unlock useful features."
        );

        MenuUtil.addOpenButton(
                gui,
                2,
                CobblemonItems.POKE_BALL,
                "§cRanked PvP",
                () -> openRanked(player),
                "§7Ranked, casual, RP,",
                "§7seasons, and queues."
        );

        MenuUtil.addOpenButton(
                gui,
                3,
                Items.NETHERITE_PICKAXE,
                "§6Professions & Gear",
                () -> openGear(player),
                "§7Professions, custom gear,",
                "§7identify, reroll, repair, salvage."
        );

        MenuUtil.addOpenButton(
                gui,
                4,
                Items.DEEPSLATE_BRICKS,
                "§5Dungeons & Events",
                () -> openDungeons(player),
                "§7Keys, dungeons, world events,",
                "§7and reward flow."
        );

        MenuUtil.addOpenButton(
                gui,
                5,
                Items.EMERALD,
                "§aAuction House",
                () -> openAuctionHouse(player),
                "§7Buying, selling, and",
                "§7auction basics."
        );

        MenuUtil.addOpenButton(
                gui,
                8,
                Items.BARRIER,
                "§cBack",
                () -> MainMenu.open(player),
                "§7Return to the main menu."
        );

        gui.open();
    }

    private static void openGettingStarted(ServerPlayer player) {
        openInfoPage(
                player,
                "Getting Started",
                Items.COMPASS,
                "§bWhat should I do first?",
                new String[] {
                        "§71. Pick your starter and explore spawn.",
                        "§72. Battle gyms to unlock useful features.",
                        "§73. Try professions to earn XP and gear.",
                        "§74. Run dungeons and events for stronger rewards.",
                        "§75. Queue casual or ranked PvP when ready.",
                        "",
                        "§eCore loop:",
                        "§7Battle → earn rewards → improve team/gear → compete.",
                        "",
                        "§7Most major systems can be accessed through",
                        "§7spawn NPCs so the server stays clean."
                }
        );
    }

    private static void openGyms(ServerPlayer player) {
        openInfoPage(
                player,
                "Gyms & Unlocks",
                Items.IRON_SWORD,
                "§6Gyms & Unlocks",
                new String[] {
                        "§7Gyms are one of the main progression paths",
                        "§7on Cobble Champs.",
                        "",
                        "§eWhy beat gyms?",
                        "§7Each badge pushes your account forward.",
                        "§7Gym progression unlocks useful in-game",
                        "§7convenience features over time.",
                        "",
                        "§eExample unlock:",
                        "§7Useful commands such as §f/pc §7can be earned",
                        "§7through badge progression instead of being",
                        "§7available immediately.",
                        "",
                        "§7If you feel stuck, challenge the next gym",
                        "§7and keep building your team."
                }
        );
    }

    private static void openRanked(ServerPlayer player) {
        openInfoPage(
                player,
                "Ranked PvP",
                CobblemonItems.POKE_BALL,
                "§cRanked PvP",
                new String[] {
                        "§7Ranked battles use competitive rules.",
                        "§7Winning gives RP. Losing removes RP.",
                        "§7Casual queue is for practice without RP risk.",
                        "§7Seasons reset active RP but history is saved.",
                        "",
                        "§eTips:",
                        "§7Use casual to test teams before ranked.",
                        "§7Check leaderboards to track top players.",
                        "§7Rules may change for special formats later."
                }
        );
    }

    private static void openGear(ServerPlayer player) {
        openInfoPage(
                player,
                "Professions & Gear",
                Items.NETHERITE_PICKAXE,
                "§6Professions & Gear",
                new String[] {
                        "§7Professions let you progress outside battles.",
                        "§7Mining, Forestry, and Farming give XP.",
                        "§7Custom gear can roll useful stats.",
                        "",
                        "§eGear flow:",
                        "§7Find unidentified gear.",
                        "§7Identify it to reveal stats.",
                        "§7Reroll if you want better rolls.",
                        "§7Repair gear before durability reaches zero.",
                        "§7Salvage unwanted gear into fragments.",
                        "§7Use fragments to craft more gear."
                }
        );
    }

    private static void openDungeons(ServerPlayer player) {
        openInfoPage(
                player,
                "Dungeons & Events",
                Items.DEEPSLATE_BRICKS,
                "§5Dungeons & Events",
                new String[] {
                        "§7Dungeons are repeatable challenge content.",
                        "§7They can reward shards, items, gear, and more.",
                        "§7World events are timed server activities.",
                        "§7Event NPCs and dungeon NPCs are found at spawn.",
                        "",
                        "§eTips:",
                        "§7Bring a ready team before entering hard content.",
                        "§7Watch event notices for active opportunities.",
                        "§7Use rewards to improve gear and battle teams."
                }
        );
    }

    private static void openAuctionHouse(ServerPlayer player) {
        openInfoPage(
                player,
                "Auction House",
                Items.EMERALD,
                "§aAuction House",
                new String[] {
                        "§7The Auction House lets players buy and sell.",
                        "§7You can list supported items and Pokémon.",
                        "§7Use the Auction NPC to browse listings.",
                        "",
                        "§eBasic flow:",
                        "§7List something for a price.",
                        "§7Other players can buy it while listed.",
                        "§7Check prices before listing rare rewards.",
                        "§7Use clear pricing to avoid bad sales."
                }
        );
    }

    private static void openInfoPage(
            ServerPlayer player,
            String title,
            Item icon,
            String header,
            String[] lines
    ) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal(title));

        MenuUtil.addInfoCard(gui, 4, icon, header, lines);
        MenuUtil.addBackButton(gui, 22, () -> HelpMenu.open(player));

        gui.open();
    }
}
