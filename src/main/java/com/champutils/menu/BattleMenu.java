package com.champutils.menu;

import com.champutils.matchmaking.MatchmakingManager;
import com.cobblemon.mod.common.CobblemonItems;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public class BattleMenu {

    public static void open(
            ServerPlayer player
    ){

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Battle Queues"
                )
        );

        MenuUtil.fillBorders(
                gui,
                10,13,16,22
        );

        gui.setSlot(
                10,
                new GuiElementBuilder(
                        CobblemonItems.ULTRA_BALL
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§cRanked Queue"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        MatchmakingManager.joinQueue(
                                                player,"ranked"
                                        )
                        )
        );

        gui.setSlot(
                13,
                new GuiElementBuilder(
                        CobblemonItems.GREAT_BALL
                )
                        .hideDefaultTooltip()
                        .setName(Component.literal(
                                "§aCasual Queue"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        MatchmakingManager.joinQueue(
                                                player,"casual"
                                        )
                        )
        );

        gui.setSlot(
                16,
                new GuiElementBuilder(
                        Items.BARRIER
                )
                        .setName(Component.literal(
                                "§cLeave Queue"
                        ))
                        .setCallback(
                                (i,c,t)->
                                        MatchmakingManager.leaveQueue(
                                                player
                                        )
                        )
        );

        MenuUtil.addBackButton(
                gui,
                22,
                ()->MainMenu.open(player)
        );

        gui.open();
    }
}