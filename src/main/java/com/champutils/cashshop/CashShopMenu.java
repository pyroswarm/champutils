package com.champutils.cashshop;

import com.champutils.menu.MenuUtil;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class CashShopMenu {
    private CashShopMenu() {}
    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Server Boosters"));
        int slot = 10;
        for (CashShopBoostItemManager.Def def : CashShopBoostItemManager.defs()) {
            gui.setSlot(slot++, new GuiElementBuilder(Items.NETHER_STAR).hideDefaultTooltip()
                    .setName(Component.literal(def.name))
                    .addLoreLine(Component.literal("§7" + def.lore))
                    .addLoreLine(Component.literal("§7Duration: §f15 minutes"))
                    .addLoreLine(Component.literal("§8Buy this from your real-money store")));
        }
        gui.open();
    }
}
