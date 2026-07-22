package com.champutils.menu;

import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonRewardManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class SeasonRewardsMenu {
    private SeasonRewardsMenu() {}
    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Season Rewards"));
        MenuUtil.fillBorders(gui, 4,45,46,47,48,49,50,51,52,53);
        gui.setSlot(4, new GuiElementBuilder(Items.NETHER_STAR).hideDefaultTooltip()
                .setName(Component.literal("§6Claim Season Rewards"))
                .addLoreLine(Component.literal("§7Current season: §f" + SeasonManager.CURRENT_NAME))
                .addLoreLine(Component.literal("§7Ends: §f" + SeasonManager.getSeasonEndDisplay())));
        int slot = 10;
        for (var snap : SeasonRewardManager.snapshotsFor(player)) {
            if (slot >= 44) break;
            boolean claimed = SeasonRewardManager.isClaimed(player, snap.season);
            GuiElementBuilder b = new GuiElementBuilder(claimed ? Items.GRAY_DYE : Items.CHEST).hideDefaultTooltip()
                    .setName(Component.literal("§6Season " + snap.season + " §7- §f" + snap.seasonName))
                    .addLoreLine(Component.literal("§7Final RP: §f" + snap.finalRp))
                    .addLoreLine(Component.literal("§7Peak RP: §f" + snap.peakRp))
                    .addLoreLine(Component.literal("§7Ranked games: §f" + snap.rankedGames))
                    .addLoreLine(Component.literal(claimed ? "§aClaimed" : "§eClick to claim"));
            for (var r : snap.rewards) b.addLoreLine(Component.literal("§7• §f" + r.amount + " " + r.type + " " + r.id));
            int season = snap.season;
            if (!claimed) b.setCallback((i,c,a) -> { SeasonRewardManager.claim(player, season); player.server.execute(() -> open(player)); });
            gui.setSlot(slot++, b);
        }
        gui.setSlot(49, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack"))
                .setCallback((i,c,a) -> ProfileMenu.open(player)));
        gui.open();
    }
}
