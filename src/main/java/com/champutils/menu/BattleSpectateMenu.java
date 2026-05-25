package com.champutils.menu;

import com.champutils.commands.BattleSpectateCommand;
import com.champutils.matchmaking.MatchmakingManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;

public class BattleSpectateMenu {

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Spectate Battles"));

        MenuUtil.fillBorders(gui, 4, 10, 13, 16, 22, 28, 29, 30, 31, 32, 33, 34, 49);

        MenuUtil.addInfoCard(
                gui,
                4,
                Items.SPYGLASS,
                "§bBattle Spectate",
                "§7Watch active PvP battles without joining the fight."
        );

        gui.setSlot(
                10,
                new GuiElementBuilder(Items.ENDER_EYE)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§dRandom PvP Battle"))
                        .addLoreLine(Component.literal("§7Spectate a random active ranked/casual battle."))
                        .addLoreLine(Component.literal("§eClick to spectate"))
                        .setCallback((i, c, t) -> BattleSpectateCommand.spectateRandom(player.createCommandSourceStack()))
        );

        ServerPlayer high = BattleSpectateCommand.getHighestMmrTarget(player.getServer(), player);
        GuiElementBuilder highButton = new GuiElementBuilder(Items.NETHER_STAR)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Highest MMR Battle"));
        if (high == null) {
            highButton.addLoreLine(Component.literal("§7No ranked/high-MMR PvP battle is active."));
        } else {
            highButton.addLoreLine(Component.literal("§7" + BattleSpectateCommand.getBattleLabel(high)));
            highButton.addLoreLine(Component.literal("§7Top visible RP: §e" + BattleSpectateCommand.getRp(high)));
            highButton.addLoreLine(Component.literal("§eClick to spectate"));
            highButton.setCallback((i, c, t) -> BattleSpectateCommand.spectateTarget(player, high));
        }
        gui.setSlot(13, highButton);

        gui.setSlot(
                16,
                new GuiElementBuilder(Items.COMPASS)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§aRefresh List"))
                        .addLoreLine(Component.literal("§7Reload currently spectatable PvP battles."))
                        .addLoreLine(Component.literal("§eClick to refresh"))
                        .setCallback((i, c, t) -> open(player))
        );

        List<ServerPlayer> targets = BattleSpectateCommand.getSpectatablePlayers(player.getServer(), player);
        int[] slots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        if (targets.isEmpty()) {
            gui.setSlot(
                    31,
                    new GuiElementBuilder(Items.GRAY_DYE)
                            .hideDefaultTooltip()
                            .setName(Component.literal("§7No active PvP battles"))
                            .addLoreLine(Component.literal("§7Ranked and casual battles will show here."))
            );
        } else {
            int count = Math.min(slots.length, targets.size());
            for (int i = 0; i < count; i++) {
                ServerPlayer target = targets.get(i);
                ServerPlayer opponent = MatchmakingManager.getOpponent(target);
                String opponentName = opponent == null ? "Unknown" : opponent.getName().getString();
                boolean ranked = MatchmakingManager.isRankedMatch(target);

                gui.setSlot(
                        slots[i],
                        new GuiElementBuilder(ranked ? Items.DIAMOND_SWORD : Items.IRON_SWORD)
                                .hideDefaultTooltip()
                                .setName(Component.literal((ranked ? "§c" : "§a") + target.getName().getString() + " vs " + opponentName))
                                .addLoreLine(Component.literal("§7Type: " + (ranked ? "§cRanked" : "§aCasual")))
                                .addLoreLine(Component.literal("§7" + target.getName().getString() + " RP: §e" + BattleSpectateCommand.getRp(target)))
                                .addLoreLine(Component.literal(opponent == null ? "§7Opponent RP: §e?" : "§7" + opponentName + " RP: §e" + BattleSpectateCommand.getRp(opponent)))
                                .addLoreLine(Component.literal("§eClick to spectate"))
                                .setCallback((slot, clickType, actionType) -> BattleSpectateCommand.spectateTarget(player, target))
                );
            }
        }

        MenuUtil.addBackButton(gui, 49, () -> BattleMenu.open(player));
        gui.open();
    }
}
