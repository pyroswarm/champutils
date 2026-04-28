package com.champutils.menu;

import com.champutils.rank.LeaderboardManager;
import com.champutils.rank.LeaderboardManager.Entry;
import com.champutils.profile.PlayerDataManager;

import com.mojang.authlib.GameProfile;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LeaderboardMenu {

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
                        "Ranked Leaderboard"
                )
        );


        MenuUtil.fillBorders(
                gui,
                4,
                45,46,47,48,50,51,52,53
        );


/* =========================
HEADER
========================= */

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Top Ranked Players"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Click a player to view profile"
                                )
                        )
        );



        List<Entry> top=
                LeaderboardManager.getTop(
                        36
                );


        int slot=10;

        for(
                int i=0;
                i<top.size();
                i++
        ){

            if(slot==17) slot=19;
            if(slot==26) slot=28;
            if(slot==35) slot=37;


            Entry entry=
                    top.get(i);

            int rankPos=
                    i+1;


            String medal=
                    switch(rankPos){
                        case 1 -> "§6#1 ";
                        case 2 -> "§7#2 ";
                        case 3 -> "§c#3 ";
                        default -> "§e#"+rankPos+" ";
                    };


/* =========================
BUILD SKINNED HEAD
========================= */

            ItemStack head=
                    new ItemStack(
                            Items.PLAYER_HEAD
                    );

            try{

                boolean applied=false;


                /* ---- online player skin ---- */

                ServerPlayer online=
                        player.server
                                .getPlayerList()
                                .getPlayerByName(
                                        entry.playerName
                                );

                if(
                        online!=null
                ){
                    head.set(
                            DataComponents.PROFILE,
                            new ResolvableProfile(
                                    online.getGameProfile()
                            )
                    );

                    applied=true;
                }



                /* ---- offline uuid skin ---- */

                if(!applied){

                    for(
                            var p :
                            PlayerDataManager.getAllPlayers()
                    ){

                        if(
                                p.name.equalsIgnoreCase(
                                        entry.playerName
                                )
                        ){

                            try{

                                UUID uuid=
                                        UUID.fromString(
                                                p.uuid
                                        );

                                GameProfile offlineProfile=
                                        new GameProfile(
                                                uuid,
                                                p.name
                                        );

                                head.set(
                                        DataComponents.PROFILE,
                                        new ResolvableProfile(
                                                offlineProfile
                                        )
                                );

                                applied=true;

                            }catch(Exception ignored){}

                            break;
                        }
                    }
                }



                /* ---- name cache fallback ---- */

                if(!applied){

                    Optional<GameProfile> cached=
                            player.server
                                    .getProfileCache()
                                    .get(
                                            entry.playerName
                                    );

                    if(
                            cached.isPresent()
                    ){

                        head.set(
                                DataComponents.PROFILE,
                                new ResolvableProfile(
                                        cached.get()
                                )
                        );
                    }
                }

            }catch(Exception ignored){}



/* =========================
ENTRY BUTTON
========================= */

            GuiElementBuilder headButton=
                    new GuiElementBuilder(
                            head
                    )

                            .setName(
                                    Component.literal(
                                            medal
                                                    +"§f"
                                                    +entry.playerName
                                    )
                            )

                            .addLoreLine(
                                    Component.literal(
                                            "§6RP: §f"
                                                    +entry.rp
                                    )
                            )

                            .addLoreLine(
                                    Component.literal(
                                            "§7Click to inspect profile"
                                    )
                            )

                            .setCallback(
                                    (index,click,type)->
                                            PlayerProfileMenu.open(
                                                    player,
                                                    entry.playerName
                                            )
                            );


            gui.setSlot(
                    slot++,
                    headButton
            );
        }



/* =========================
BACK
========================= */

        gui.setSlot(
                49,
                new GuiElementBuilder(
                        Items.ARROW
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§cBack"
                                )
                        )
                        .setCallback(
                                (i,c,t)->
                                        MainMenu.open(
                                                player
                                        )
                        )
        );


        gui.open();
    }
}