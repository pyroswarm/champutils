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
        open(
                player,
                player.getName()
                        .getString()
        );
    }



    public static void open(
            ServerPlayer viewer,
            String targetName
    ){

        var history=
                SeasonArchiveManager.getHistory(
                        targetName
                );


        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        viewer,
                        false
                );

        gui.setTitle(
                Component.literal(
                        targetName+"'s Season History"
                )
        );


        int slot=10;

        for(
                var season :
                history
        ){

            if(slot==17) slot=19;
            if(slot==26) slot=28;
            if(slot==35) slot=37;

            if(slot>=44){
                break;
            }


            gui.setSlot(
                    slot++,

                    new GuiElementBuilder(
                            Items.WRITABLE_BOOK
                    )
                            .hideDefaultTooltip()

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
                                            "§7Peak Rank: "
                                                    +season.peakRank
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
                                                    viewer,
                                                    season
                                            )
                            )
            );
        }



        if(
                history.isEmpty()
        ){

            gui.setSlot(
                    22,
                    new GuiElementBuilder(
                            Items.BARRIER
                    )
                            .hideDefaultTooltip()
                            .setName(
                                    Component.literal(
                                            "§cNo archived seasons."
                                    )
                            )
            );
        }



/* =========================
CLOSE BUTTON
========================= */

        gui.setSlot(
                49,
                new GuiElementBuilder(
                        Items.BARRIER
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§cClose"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        viewer.closeContainer()
                        )
        );


        gui.open();
    }
}