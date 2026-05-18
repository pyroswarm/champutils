package com.champutils.menu;

import com.champutils.profile.ProfileManager;
import com.champutils.badge.BadgeManager;
import com.champutils.rank.RankManager;

import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class ProfileMenu {

    public static void open(
            ServerPlayer player
    ){

        SimpleGui gui=
                MenuUtil.createGui(MenuType.GENERIC_9x5, player);

        gui.setTitle(
                Component.literal(
                        "Trainer Card"
                )
        );

        MenuUtil.fillBorders(
                gui,
                4,
                10,12,14,16,
                19,21,23,25,
                40
        );

        int badgeCount=
                BadgeManager.getBadgeCount(
                        player
                );

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bTrainer Card"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Trainer: §f"+
                                                player.getName().getString()
                                )
                        )
        );

        String currentRank=
                ProfileManager.getCurrentRankName(
                        player
                );

        String peakRank=
                RankManager.getRank(
                        ProfileManager.getPeakRp(
                                player
                        )
                ).name;

        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.WHITE_PLAQUE
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Rank"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eCurrent: §f"+currentRank
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§ePeak: §f"+peakRank
                                )
                        )
        );

        gui.setSlot(
                12,
                new GuiElementBuilder(
                        CobblemonItems.FIRE_GEM
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§cRating"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eRP: §f"+
                                                ProfileManager.getCurrentRp(
                                                        player
                                                )
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§ePeak: §f"+
                                                ProfileManager.getPeakRp(
                                                        player
                                                )
                                )
                        )
        );

        gui.setSlot(
                14,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§bBattle Record"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§aWins: §f"+
                                                ProfileManager.getRankedWins(
                                                        player
                                                )
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§cLosses: §f"+
                                                ProfileManager.getRankedLosses(
                                                        player
                                                )
                                )
                        )
        );

        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BLAZE_POWDER
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Win Streak"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eCurrent: §f"+
                                                ProfileManager.getCurrentStreak(
                                                        player
                                                )
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eBest: §f"+
                                                ProfileManager.getBestStreak(
                                                        player
                                                )
                                )
                        )
        );

        gui.setSlot(
                19,
                new GuiElementBuilder(
                        CobblemonItems.EXPERT_BELT
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§dBadge Case"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Badges: §f"+badgeCount
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to open"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        BadgeMenu.open(
                                                player
                                        )
                        )
        );

        gui.setSlot(
                21,
                new GuiElementBuilder(
                        Items.EXPERIENCE_BOTTLE
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§aProfessions"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7View profession levels"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to open"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        ProfessionMenu.open(
                                                player
                                        )
                        )
        );

        gui.setSlot(
                23,
                new GuiElementBuilder(
                        CobblemonItems.LUCKY_EGG
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Claim Rewards"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Redeem available rewards"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        player.getServer()
                                                .getCommands()
                                                .performPrefixedCommand(
                                                        player.createCommandSourceStack(),
                                                        "claimseasonrewards"
                                                )
                        )
        );

        gui.setSlot(
                25,
                new GuiElementBuilder(
                        Items.COMPASS
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§aPlayer Lookup"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Search another player's trainer card."
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eClick to type a name in chat"
                                )
                        )
                        .setCallback(
                                (i,c,t)-> {
                                    player.closeContainer();
                                    ProfileLookupManager.beginLookup(
                                            player
                                    );
                                }
                        )
        );

        MenuUtil.addBackButton(
                gui,
                40,
                ()->
                        MainMenu.open(
                                player
                        )
        );

        gui.open();
    }
}
