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

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Battles"));

        MenuUtil.fillBorders(gui, 4, 10, 13, 16, 22);

        MenuUtil.addInfoCard(
                gui,
                4,
                CobblemonItems.POKE_BALL,
                "§cBattle Queues",
                "§7Choose a queue or leave your current queue."
        );

        gui.setSlot(
                10,
                new GuiElementBuilder(CobblemonItems.ULTRA_BALL)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§cRanked Queue"))
                        .addLoreLine(Component.literal("§7Queue for competitive RP battles."))
                        .addLoreLine(Component.literal("§eClick to join"))
                        .setCallback((i, c, t) -> MatchmakingManager.joinQueue(player, "ranked"))
        );

        gui.setSlot(
                13,
                new GuiElementBuilder(CobblemonItems.GREAT_BALL)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§aCasual Queue"))
                        .addLoreLine(Component.literal("§7Queue for practice battles."))
                        .addLoreLine(Component.literal("§eClick to join"))
                        .setCallback((i, c, t) -> MatchmakingManager.joinQueue(player, "casual"))
        );

        gui.setSlot(
                16,
                new GuiElementBuilder(Items.BARRIER)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§cLeave Queue"))
                        .addLoreLine(Component.literal("§7Leave your current matchmaking queue."))
                        .addLoreLine(Component.literal("§eClick to leave"))
                        .setCallback((i, c, t) -> MatchmakingManager.leaveQueue(player))
        );

        MenuUtil.addBackButton(gui, 22, () -> MainMenu.open(player));
        gui.open();
    }
}
