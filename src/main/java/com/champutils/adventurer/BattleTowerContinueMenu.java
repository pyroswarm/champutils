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

    public static void open(ServerPlayer player, int clearedFloor, boolean healed) {
        if (player == null) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal(healed ? "Team Restored" : "Floor " + clearedFloor + " Cleared"));
        gui.setSlot(4, new GuiElementBuilder(healed ? Items.GOLDEN_APPLE : Items.DIAMOND_SWORD)
                .hideDefaultTooltip().setName(Component.literal(healed ? "§6Team Fully Healed" : "§dFloor " + clearedFloor + " Cleared"))
                .addLoreLine(Component.literal(healed ? "§7Your party has been healed after five floors." : "§7Choose whether to continue your climb.")));
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
