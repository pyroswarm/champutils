package com.champutils.menu;

import com.champutils.profile.ProfileManager;
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

        SimpleGui gui =
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
                4,10,12,14,16,
                20,24,49
        );

        // Trainer Header
        gui.setSlot(
                4,
                new GuiElementBuilder(
                        CobblemonItems.POKEDEX_RED
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§bTrainer Card"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Trainer: §f"+
                                        player.getName().getString()
                        ))
        );


        // Rank
        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.WHITE_PLAQUE
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§6Rank"
                        ))
                        .addLoreLine(Component.literal(
                                "§eCurrent: §f"+
                                        ProfileManager.getCurrentRankName(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§eHighest: §f"+
                                        ProfileManager.getHighestRankName(player)
                        ))
        );


        // Rating
        gui.setSlot(
                12,
                new GuiElementBuilder(
                        CobblemonItems.FIRE_GEM
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§cRating"
                        ))
                        .addLoreLine(Component.literal(
                                "§eRP: §f"+
                                        ProfileManager.getCurrentRp(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§ePeak: §f"+
                                        ProfileManager.getPeakRp(player)
                        ))
        );


        // Record
        gui.setSlot(
                14,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .setName(Component.literal(
                                "§bBattle Record"
                        ))
                        .addLoreLine(Component.literal(
                                "§aWins: §f"+
                                        ProfileManager.getRankedWins(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§cLosses: §f"+
                                        ProfileManager.getRankedLosses(player)
                        ))
        );


        // Streak
        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BLAZE_POWDER
                )
                        .setName(Component.literal(
                                "§6Win Streak"
                        ))
                        .addLoreLine(Component.literal(
                                "§eCurrent: §f"+
                                        ProfileManager.getCurrentStreak(player)
                        ))
                        .addLoreLine(Component.literal(
                                "§eBest: §f"+
                                        ProfileManager.getBestStreak(player)
                        ))
        );


        // Badge Case
        gui.setSlot(
                20,
                new GuiElementBuilder(
                        CobblemonItems.EXPERT_BELT
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§dBadge Case"
                        ))
                        .addLoreLine(Component.literal(
                                "§70 / 8 Badges"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Coming Soon"
                        ))
        );


        // Rewards
        gui.setSlot(
                24,
                new GuiElementBuilder(
                        CobblemonItems.LUCKY_EGG
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§6Claim Rewards"
                        ))
                        .addLoreLine(Component.literal(
                                "§7Season rewards"
                        ))
                        .setCallback(
                                (i,c,t)->{
                                    player.getServer()
                                            .getCommands()
                                            .performPrefixedCommand(
                                                    player.createCommandSourceStack(),
                                                    "claimseasonrewards"
                                            );
                                }
                        )
        );


        MenuUtil.addBackButton(
                gui,
                49,
                ()->MainMenu.open(player)
        );

        gui.open();
    }

}