package com.champutils.menu;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;

import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.elements.GuiElementBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ProfessionMenu {

    public static void open(
            ServerPlayer player
    ) {
        ProfessionDataManager.ProfessionData data =
                ProfessionManager.getData(
                        player
                );

        ProfessionDataManager.ensureProfessionDefaults(
                data
        );

        SimpleGui gui =
                new SimpleGui(
                        MenuType.GENERIC_9x3,
                        player,
                        false
                );

        gui.setTitle(
                Component.literal(
                        "Professions"
                )
        );

        MenuUtil.fillBorders(
                gui,
                4,10,12,14,16,22
        );

        gui.setSlot(
                4,
                new GuiElementBuilder(
                        Items.NETHER_STAR
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§6Overall Level"
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Total: §f" +
                                                ProfessionDataManager.getOverallLevel(
                                                        data
                                                )
                                )
                        )
        );

        setProfessionSlot(
                gui,
                10,
                ProfessionType.BATTLING,
                Items.DIAMOND_SWORD,
                data
        );

        setProfessionSlot(
                gui,
                12,
                ProfessionType.MINING,
                Items.DIAMOND_PICKAXE,
                data
        );

        setProfessionSlot(
                gui,
                14,
                ProfessionType.FORESTRY,
                Items.DIAMOND_AXE,
                data
        );

        setProfessionSlot(
                gui,
                16,
                ProfessionType.FARMING,
                Items.DIAMOND_HOE,
                data
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

    private static void setProfessionSlot(
            SimpleGui gui,
            int slot,
            ProfessionType type,
            Item icon,
            ProfessionDataManager.ProfessionData data
    ) {
        int level =
                data.levels.getOrDefault(
                        type.name(),
                        1
                );

        int xp =
                data.xp.getOrDefault(
                        type.name(),
                        0
                );

        gui.setSlot(
                slot,
                new GuiElementBuilder(
                        icon
                )
                        .hideDefaultTooltip()
                        .setName(
                                Component.literal(
                                        "§b" + format(type)
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7Level: §f" + level
                                )
                        )
                        .addLoreLine(
                                Component.literal(
                                        "§7XP: §f" + xp +
                                                "§7/§f" +
                                                ProfessionManager.xpRequired(
                                                        level
                                                )
                                )
                        )
        );
    }

    private static String format(
            ProfessionType type
    ) {
        String lower =
                type.name().toLowerCase();

        return Character.toUpperCase(
                lower.charAt(0)
        ) + lower.substring(1);
    }
}
