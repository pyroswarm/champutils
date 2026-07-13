package com.champutils.adventurer;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/** Locked confirmation screen used between Battle Tower floors. */
public final class BattleTowerContinueMenu {
    private BattleTowerContinueMenu() {}

    public static void open(ServerPlayer player, int clearedFloor, boolean checkpoint) {
        if (player == null) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal(checkpoint ? "Continue?" : "Floor " + clearedFloor + " Cleared"));
        gui.setSlot(4, new GuiElementBuilder(checkpoint ? Items.GOLD_BLOCK : Items.DIAMOND_SWORD)
                .hideDefaultTooltip().setName(Component.literal(checkpoint ? "§6Checkpoint Cleared" : "§dFloor " + clearedFloor + " Cleared"))
                .addLoreLine(Component.literal(checkpoint ? "§7Your party has been healed." : "§7Choose whether to continue your climb.")));
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_STAINED_GLASS_PANE).hideDefaultTooltip()
                .setName(Component.literal("§aContinue"))
                .addLoreLine(Component.literal("§7Advance to the next floor."))
                .setCallback((slot, click, action) -> { gui.close(); AdventurerGuildManager.continueBattleTower(player); }));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE).hideDefaultTooltip()
                .setName(Component.literal("§cGive Up"))
                .addLoreLine(Component.literal("§7End this run and return to spawn."))
                .setCallback((slot, click, action) -> { gui.close(); AdventurerGuildManager.giveUpBattleTower(player); }));
        gui.setAutoUpdate(false);
        gui.open();
    }
}
