package com.champutils.menu;

import com.champutils.rank.SeasonArchiveManager;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;

import net.minecraft.world.item.Items;

public class SeasonHistoryMenu {

    public static void open(
            ServerPlayer player
    ){

        var history=
                SeasonArchiveManager.getHistory(
                        player.getName()
                                .getString()
                );

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Season History"
                )
        );

        int slot=10;

        for(
                var season :
                history
        ){

            if(slot>=44){
                break;
            }

            gui.setSlot(
                    slot++,
                    new GuiElementBuilder(
                            Items.WRITABLE_BOOK
                    )
                            .setName(
                                    Component.literal(
                                            "§6Season "
                                                    +season.season
                                                    +" "
                                                    +season.seasonName
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§7Final RP: "
                                                    +season.finalRp
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§7Peak RP: "
                                                    +season.peakRp
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§eClick for details"
                                    )
                            )
                            .setCallback(
                                    (i,c,t)->
                                            SeasonDetailMenu.open(
                                                    player,
                                                    season
                                            )
                            )
            );
        }


        MenuUtil.addBackButton(
                gui,
                49,
                ()->SeasonMenu.open(player)
        );

        gui.open();
    }
}