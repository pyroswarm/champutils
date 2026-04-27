package com.champutils.menu;

import com.champutils.rank.SeasonArchiveManager;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

public class SeasonTop100Menu {

    public static void open(
            ServerPlayer player,
            int season
    ){

        var top=
                SeasonArchiveManager
                        .getTop100Snapshot(
                                season
                        );

        SimpleGui gui=
                new SimpleGui(
                        MenuType.GENERIC_9x6,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Season "+season+" Top 100"
                )
        );

        int limit=
                Math.min(
                        45,
                        top.size()
                );

        for(
                int i=0;
                i<limit;
                i++
        ){

            var e=
                    top.get(i);

            gui.setSlot(
                    i,
                    new GuiElementBuilder(
                            Items.PLAYER_HEAD
                    )
                            .setName(
                                    Component.literal(
                                            "§6#"
                                                    +(i+1)
                                                    +" "
                                                    +e.player
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§eRP: "
                                                    +e.rp
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§7"
                                                    +e.rank
                                    )
                            )
            );
        }


        MenuUtil.addBackButton(
                gui,
                49,
                ()->SeasonHistoryMenu.open(player)
        );

        gui.open();
    }
}