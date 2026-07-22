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
        gui.setTitle(Component.literal("Boosters"));
        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aBooster Credits: §f" + BoosterCreditManager.credits(player)))
                .addLoreLine(Component.literal("§7Purchased: §f" + BoosterCreditManager.purchasedCredits(player)))
                .addLoreLine(Component.literal("§7VIP+ Earned: §d" + BoosterCreditManager.vipPlusCredits(player) + "§7/§d10"))
                .addLoreLine(Component.literal("§7VIP+ earns up to 3 per day:"))
                .addLoreLine(Component.literal("§7join, +1h played, +2h played.")));
        int[] boosterSlots = {9, 10, 11, 12, 13, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26};
        int boosterIndex = 0;
        for (CashShopBoostItemManager.Def def : CashShopBoostItemManager.defs()) {
            if (boosterIndex >= boosterSlots.length) break;
            int slot = boosterSlots[boosterIndex++];
            gui.setSlot(slot, new GuiElementBuilder(Items.NETHER_STAR).hideDefaultTooltip()
                    .setName(Component.literal(def.name))
                    .addLoreLine(Component.literal("§7" + def.lore))
                    .addLoreLine(Component.literal("§7Duration: §f15 minutes"))
                    .addLoreLine(Component.literal("§7Cost: §f1 Booster Credit"))
                    .addLoreLine(Component.literal("§7Spends purchased credits first,"))
                    .addLoreLine(Component.literal("§7then VIP+ earned credits."))
                    .addLoreLine(Component.literal("§eClick to activate for the whole server"))
                    .setCallback((i, c, t) -> {
                        if (BoosterCreditManager.credits(player) < 1) {
                            player.sendSystemMessage(Component.literal("You need 1 booster credit to activate this.").withStyle(net.minecraft.ChatFormatting.RED));
                            return;
                        }
                        CashShopBoostItemManager.activateFromCredit(player, def.id)
                                .whenComplete((activated, error) -> player.server.execute(() -> {
                                    if (error != null || !Boolean.TRUE.equals(activated)) return;
                                    if (!BoosterCreditManager.spend(player, 1)) {
                                        CashShopBoostItemManager.deactivateAdmin(def.id);
                                        player.sendSystemMessage(Component.literal("Your booster credit balance changed before activation. The boost was cancelled.").withStyle(net.minecraft.ChatFormatting.RED));
                                        return;
                                    }
                                    open(player);
                                }));
                    }));
        }
        MenuUtil.addBackButton(gui, 18, () -> com.champutils.menu.MainMenu.open(player));
        gui.open();
    }
}
