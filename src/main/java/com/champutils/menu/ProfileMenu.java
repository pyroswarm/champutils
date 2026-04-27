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
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Trainer Card"
                )
        );


        MenuUtil.fillBorders(
                gui,
                4,
                10,12,14,16,
                29,33,
                49
        );


        int badgeCount=
                BadgeManager.getBadgeCount(
                        player
                );


/* =========================
HEADER
========================= */

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
                                        "§7Trainer: §f"
                                                +player.getName()
                                                .getString()
                                )
                        )
        );


/* =========================
RANK
========================= */

        String currentRank=
                ProfileManager.getCurrentRankName(
                        player
                );


        // FIXED:
        // derive peak rank from peak RP,
        // not stale stored rank index
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
                                        "§eCurrent: §f"
                                                +currentRank
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eHighest: §f"
                                                +peakRank
                                )
                        )
        );


/* =========================
RATING
========================= */

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
                                        "§eRP: §f"
                                                +ProfileManager.getCurrentRp(
                                                player
                                        )
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§ePeak: §f"
                                                +ProfileManager.getPeakRp(
                                                player
                                        )
                                )
                        )
        );


/* =========================
RECORD
========================= */

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
                                        "§aWins: §f"
                                                +ProfileManager.getRankedWins(
                                                player
                                        )
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§cLosses: §f"
                                                +ProfileManager.getRankedLosses(
                                                player
                                        )
                                )
                        )
        );


/* =========================
STREAK
========================= */

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
                                        "§eCurrent: §f"
                                                +ProfileManager.getCurrentStreak(
                                                player
                                        )
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§eBest: §f"
                                                +ProfileManager.getBestStreak(
                                                player
                                        )
                                )
                        )
        );


/* =========================
BADGE CASE
========================= */

        gui.setSlot(
                29,
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
                                        "§7Click to open badge case"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        BadgeMenu.open(
                                                player
                                        )
                        )
        );


/* =========================
CLAIM REWARDS
========================= */

        gui.setSlot(
                33,
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
                                        "§7Redeem seasonal rewards"
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


/* =========================
BACK
========================= */

        MenuUtil.addBackButton(
                gui,
                49,
                ()->
                        MainMenu.open(
                                player
                        )
        );


        gui.open();
    }

}