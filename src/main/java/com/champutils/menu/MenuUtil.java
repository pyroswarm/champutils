package com.champutils.menu;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

public class MenuUtil {

    /*
      Global toggle:
      false = no glass filler anywhere
      true  = restore decorative borders
    */
    private static final boolean ENABLE_FILLERS = false;



    public static void fillBorders(
            SimpleGui gui,
            int... skips
    ){

        // disabled globally
        if(!ENABLE_FILLERS){
            return;
        }


        outer:
        for(
                int i=0;
                i<gui.getSize();
                i++
        ){

            for(
                    int s : skips
            ){
                if(i==s){
                    continue outer;
                }
            }

            gui.setSlot(
                    i,
                    new GuiElementBuilder(
                            Items.GRAY_STAINED_GLASS_PANE
                    )
                            .setName(
                                    Component.literal(
                                            " "
                                    )
                            )
            );
        }
    }



    /*
      Optional future decorative filler
      (if you want specific menus to still use glass)
    */
    public static void fillBordersForced(
            SimpleGui gui,
            int... skips
    ){

        outer:
        for(
                int i=0;
                i<gui.getSize();
                i++
        ){

            for(
                    int s : skips
            ){
                if(i==s){
                    continue outer;
                }
            }

            gui.setSlot(
                    i,
                    new GuiElementBuilder(
                            Items.GRAY_STAINED_GLASS_PANE
                    )
                            .setName(
                                    Component.literal(
                                            " "
                                    )
                            )
            );
        }
    }



    public static void addBackButton(
            SimpleGui gui,
            int slot,
            Runnable action
    ){

        gui.setSlot(
                slot,
                new GuiElementBuilder(
                        Items.ARROW
                )
                        .setName(
                                Component.literal(
                                        "§eBack"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        action.run()
                        )
        );
    }
}