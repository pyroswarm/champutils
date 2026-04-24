package com.champutils.menu;

import com.champutils.rank.LeaderboardManager;

import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.item.PokeBallItem;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;

import java.util.List;

public class LeaderboardMenu {

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
                        "Top Ladder"
                )
        );


        List<LeaderboardManager.Entry> top=
                LeaderboardManager.getTop(
                        25
                );


        for(
                int i=0;
                i<top.size();
                i++
        ){

            LeaderboardManager.Entry entry=
                    top.get(i);

            PokeBallItem icon;

            if(i==0){

                icon=
                        CobblemonItems.MASTER_BALL;

            }else if(i==1){

                icon=
                        CobblemonItems.ULTRA_BALL;

            }else if(i<=9){

                icon=
                        CobblemonItems.GREAT_BALL;

            }else{

                icon=
                        CobblemonItems.POKE_BALL;
            }


            gui.setSlot(
                    i,

                    new GuiElementBuilder(
                            icon
                    )
                            .hideDefaultTooltip()
                            .setName(
                                    Component.literal(
                                            "§6#"
                                                    +(i+1)
                                                    +" §f"
                                                    +entry.playerName
                                    )
                            )

                            .addLoreLine(
                                    Component.literal(
                                            "§eRP: §f"
                                                    +entry.rp
                                    )
                            )
            );
        }


        MenuUtil.addBackButton(
                gui,
                49,
                ()->SeasonMenu.open(
                        player
                )
        );


        gui.open();
    }

}