package com.champutils.worldfirst;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class WorldFirstMenu {
    private WorldFirstMenu() {}
    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("World Firsts"));
        int slot = 0;
        for (WorldFirstManager.WorldFirstDef def : WorldFirstManager.definitions()) {
            if (slot >= 54) break;
            if (slot == 45) slot++;
            WorldFirstManager.Claim claim = WorldFirstManager.claim(def.id);
            boolean found = claim != null;
            gui.setSlot(slot++, new GuiElementBuilder(found ? Items.NETHER_STAR : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal(found ? "§6" + def.name : "§8???"))
                    .addLoreLine(Component.literal(found ? "§7First completed by: §b" + claim.playerName : "§7Unknown until someone unlocks it."))
                    .addLoreLine(Component.literal(found ? "§7Unlocked: §f" + claim.claimedAt : "§7Rewards are hidden until discovered."))
                    .addLoreLine(Component.literal(found ? "§7Title: " + def.titleDisplay.replace('&','§') : "§8???")));
        }
        MenuUtil.addBackButton(gui, 45, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }
}
