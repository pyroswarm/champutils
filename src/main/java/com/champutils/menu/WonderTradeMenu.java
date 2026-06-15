package com.champutils.menu;

import com.champutils.profile.ProfileRestrictions;
import com.champutils.wondertrade.WonderTradeService;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import com.cobblemon.mod.common.CobblemonItems;

public final class WonderTradeMenu {
    private WonderTradeMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Wonder Trade"));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 13, 14, 15, 16, 22);

        gui.setSlot(4, new GuiElementBuilder(Items.ENDER_EYE)
                .hideDefaultTooltip()
                .setName(Component.literal("§dWonder Trade"))
                .addLoreLine(Component.literal("§7Choose a party slot to trade."))
                .addLoreLine(Component.literal("§7Trades, evolutions, and hunt catches do not count.")));

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < slots.length; i++) {
            final int partySlot = i + 1;
            gui.setSlot(slots[i], new GuiElementBuilder(CobblemonItems.POKE_BALL)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eTrade Party Slot " + partySlot))
                    .addLoreLine(Component.literal("§7This will trade the Pokémon in slot " + partySlot + "."))
                    .addLoreLine(Component.literal("§cThis cannot be undone."))
                    .addLoreLine(Component.literal("§eClick to Wonder Trade"))
                    .setCallback((index, click, action) -> {
                        if (ProfileRestrictions.blockIronmanTrade(player, "Wonder Trade")) return;
                        gui.close();
                        WonderTradeService.trade(player, partySlot);
                    }));
        }

        gui.setSlot(16, new GuiElementBuilder(Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§aClaim Pending Trade"))
                .addLoreLine(Component.literal("§7Use this if a trade was interrupted."))
                .addLoreLine(Component.literal("§eClick to claim"))
                .setCallback((index, click, action) -> {
                    if (ProfileRestrictions.blockIronmanTrade(player, "Wonder Trade")) return;
                    WonderTradeService.claimPending(player);
                    open(player);
                }));

        gui.setSlot(22, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bWonder Trade Status"))
                .addLoreLine(Component.literal("§7Click to check cooldown and status."))
                .setCallback((index, click, action) -> {
                    WonderTradeService.sendCooldown(player);
                    WonderTradeService.sendStatus(player);
                }));

        gui.open();
    }
}
