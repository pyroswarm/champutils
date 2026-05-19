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
                MenuUtil.createGui(MenuType.GENERIC_9x3, player);

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


/* =========================
 KANTO GYMS
========================= */

        addBadge(
                gui,
                0,
                BadgeType.CASCADE,
                "Misty",
                "/pc unlocked",
                earned
        );

        addBadge(
                gui,
                1,
                BadgeType.MARSH,
                "Sabrina",
                "Progression Badge",
                earned
        );

        addBadge(
                gui,
                2,
                BadgeType.EARTH,
                "Giovanni",
                "Progression Badge",
                earned
        );

        addBadge(
                gui,
                3,
                BadgeType.BOULDER,
                "Brock",
                "Progression Badge",
                earned
        );

        addBadge(
                gui,
                4,
                BadgeType.THUNDER,
                "Lt. Surge",
                "/pokeheal unlocked",
                earned
        );

        addBadge(
                gui,
                5,
                BadgeType.RAINBOW,
                "Erika",
                "Midgame Unlock",
                earned
        );

        addBadge(
                gui,
                6,
                BadgeType.SOUL,
                "Koga",
                "Late Unlock",
                earned
        );

        addBadge(
                gui,
                7,
                BadgeType.VOLCANO,
                "Blaine",
                "Elite Four Access",
                earned
        );


/* =========================
 ELITE FOUR + CHAMPION
========================= */

        addBadge(
                gui,
                9,
                BadgeType.LORELEI,
                "Lorelei",
                "Elite Four I",
                earned
        );

        addBadge(
                gui,
                10,
                BadgeType.BRUNO,
                "Bruno",
                "Elite Four II",
                earned
        );

        addBadge(
                gui,
                11,
                BadgeType.AGATHA,
                "Agatha",
                "Elite Four III",
                earned
        );

        addBadge(
                gui,
                12,
                BadgeType.LANCE,
                "Lance",
                "Elite Four IV",
                earned
        );

        addBadge(
                gui,
                13,
                BadgeType.CHAMPION,
                "Champion",
                "Hall of Fame",
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


/* =========================
 ELITE FOUR
========================= */

            case LORELEI ->
                    Items.BLUE_ICE;

            case BRUNO ->
                    Items.IRON_BLOCK;

            case AGATHA ->
                    Items.SOUL_LANTERN;

            case LANCE ->
                    Items.DRAGON_HEAD;


/* =========================
 CHAMPION
========================= */

            case CHAMPION ->
                    Items.NETHER_STAR;
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