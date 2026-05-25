package com.champutils.dailylogin;

import com.champutils.menu.MainMenu;
import com.champutils.menu.MenuUtil;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class DailyLoginMenu {
    private static final int[] DAY_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19
    };

    private DailyLoginMenu() {}

    public static void open(ServerPlayer player) {
        DailyLoginData.PlayerState state = DailyLoginManager.getState(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Daily Login Rewards"));

        int required = DailyLoginManager.requiredMinutes();
        int minutes = Math.min(required, Math.max(0, state.minutesThisReset));
        boolean qualifiedToday = state.lastQualifiedResetKey == state.activeResetKey;

        gui.setSlot(40, new GuiElementBuilder(Items.CLOCK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bToday's Login Progress"))
                .addLoreLine(Component.literal("§7Online today: §f" + minutes + "§7/§f" + required + " minutes"))
                .addLoreLine(Component.literal("§7Today: " + (qualifiedToday ? "§aComplete" : "§eIn Progress")))
                .addLoreLine(Component.literal("§7Track: §f" + state.trackProgress + "§7/§f" + DailyLoginConfig.DATA.track.size()))
                .addLoreLine(Component.literal("§7Reset: §f" + com.champutils.time.DailyResetManager.formatResetTime()))
                .addLoreLine(Component.literal("§7Month: §f" + DailyLoginManager.currentMonthKey())));

        for (int i = 0; i < DailyLoginConfig.DATA.track.size() && i < DAY_SLOTS.length; i++) {
            DailyLoginConfig.RewardDay reward = DailyLoginConfig.DATA.track.get(i);
            int day = i + 1;
            boolean unlocked = state.trackProgress >= day;
            boolean claimed = state.claimedDays.contains(day);
            ChatFormatting color = claimed ? ChatFormatting.GREEN : unlocked ? ChatFormatting.GOLD : ChatFormatting.RED;
            String status = claimed ? "Claimed" : unlocked ? "Ready to Claim" : "Locked";

            GuiElementBuilder button = new GuiElementBuilder(claimed ? Items.LIME_DYE : unlocked ? Items.CHEST : Items.GRAY_DYE)
                    .hideDefaultTooltip()
                    .setName(Component.literal(color + "Day " + day + " - " + reward.displayName))
                    .addLoreLine(Component.literal("§7Status: " + color + status));

            if (reward.description != null) {
                for (String line : reward.description) button.addLoreLine(Component.literal(line));
            }
            if (reward.commands != null && !reward.commands.isEmpty()) {
                button.addLoreLine(Component.literal("§8Rewards:"));
                for (String command : reward.commands) button.addLoreLine(Component.literal("§8- " + prettyCommand(command)));
            }
            if (unlocked && !claimed) {
                button.addLoreLine(Component.literal("§eClick to claim"));
                int claimDay = day;
                button.setCallback((index, click, action) -> {
                    DailyLoginManager.claim(player, claimDay);
                    open(player);
                });
            }
            gui.setSlot(DAY_SLOTS[i], button);
        }

        MenuUtil.addBackButton(gui, 45, () -> MainMenu.open(player));
        gui.setSlot(49, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§eHow It Works"))
                .addLoreLine(Component.literal("§7Stay online for §f" + required + " minutes§7."))
                .addLoreLine(Component.literal("§7You can earn §fone reward day§7 per reset."))
                .addLoreLine(Component.literal("§7The reset uses the shared §f2 AM local server time§7 system."))
                .addLoreLine(Component.literal("§7This monthly track is §f20 days§7 long.")));
        gui.open();
    }

    private static String prettyCommand(String command) {
        if (command == null) return "";
        return command.replace("%player%", "you").replace("give you ", "").replace("dailycredits you ", "Credits: ");
    }
}
