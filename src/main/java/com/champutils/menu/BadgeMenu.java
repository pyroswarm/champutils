package com.champutils.menu;

import com.champutils.badge.BadgeManager;
import com.champutils.badge.BadgeType;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Set;

public class BadgeMenu {

    public static void open(
            ServerPlayer player
    ){

        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Badge Case"
                )
        );


        Set<BadgeType> earned =
                BadgeManager.getBadges(
                        player
                );


        fillBackground(
                gui
        );


        addBadge(
                gui,
                10,
                BadgeType.BOULDER,
                "Brock",
                "/pc unlocked",
                earned
        );

        addBadge(
                gui,
                11,
                BadgeType.CASCADE,
                "Misty",
                "Progression Badge",
                earned
        );

        addBadge(
                gui,
                12,
                BadgeType.THUNDER,
                "/pokeheal unlocked",
                "Lt. Surge",
                earned
        );

        addBadge(
                gui,
                13,
                BadgeType.RAINBOW,
                "Erika",
                "Progression Badge",
                earned
        );

        addBadge(
                gui,
                14,
                BadgeType.SOUL,
                "Koga",
                "Midgame Unlock",
                earned
        );

        addBadge(
                gui,
                15,
                BadgeType.MARSH,
                "Sabrina",
                "Midgame Unlock",
                earned
        );

        addBadge(
                gui,
                16,
                BadgeType.VOLCANO,
                "Blaine",
                "Late Unlock",
                earned
        );

        addBadge(
                gui,
                17,
                BadgeType.EARTH,
                "Giovanni",
                "Champion Access",
                earned
        );


        MenuUtil.addBackButton(
                gui,
                22,
                () -> ProfileMenu.open(
                        player
                )
        );


        gui.open();
    }



    private static void addBadge(
            SimpleGui gui,
            int slot,
            BadgeType badge,
            String leader,
            String reward,
            Set<BadgeType> earned
    ){

        boolean unlocked =
                earned.contains(
                        badge
                );


        GuiElementBuilder item;


        if(
                unlocked
        ){

            item =
                    new GuiElementBuilder(
                            getBadgeItem(
                                    badge
                            )
                    )
                            .setName(
                                    Component.literal(
                                            "§6"
                                                    + badge.getDisplayName()
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§7Leader: "
                                                    + leader
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§aEarned"
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§eReward: "
                                                    + reward
                                    )
                            );

        }
        else{

            item =
                    new GuiElementBuilder(
                            Items.GRAY_STAINED_GLASS_PANE
                    )
                            .setName(
                                    Component.literal(
                                            "§8??? Badge"
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§7Defeat "
                                                    + leader
                                    )
                            )
                            .addLoreLine(
                                    Component.literal(
                                            "§cNot earned"
                                    )
                            );
        }


        gui.setSlot(
                slot,
                item
        );

    }



    private static Item getBadgeItem(
            BadgeType badge
    ){

        return switch(
                badge
                ){

            case BOULDER ->
                    Items.STONE;

            case CASCADE ->
                    Items.WATER_BUCKET;

            case THUNDER ->
                    Items.LIGHTNING_ROD;

            case RAINBOW ->
                    Items.SUNFLOWER;

            case SOUL ->
                    Items.POISONOUS_POTATO;

            case MARSH ->
                    Items.ENDER_EYE;

            case VOLCANO ->
                    Items.BLAZE_POWDER;

            case EARTH ->
                    Items.GRASS_BLOCK;

        };

    }



    private static void fillBackground(
            SimpleGui gui
    ){

        for(
                int i=0;
                i<27;
                i++
        ){

            gui.setSlot(
                    i,
                    new GuiElementBuilder(
                            Items.GRAY_STAINED_GLASS_PANE
                    ).setName(
                            Component.literal(
                                    " "
                            )
                    )
            );

        }

    }

}