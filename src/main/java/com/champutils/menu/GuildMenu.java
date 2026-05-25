package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.guild.GuildConfig;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

public final class GuildMenu {

    private GuildMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Guilds"));
        MenuUtil.fillBordersForced(gui, 10, 13, 16, 22);

        long createCost = GuildConfig.GUILD_CREATION == null
                ? 10_000L
                : Math.max(0L, GuildConfig.GUILD_CREATION.createCostCredits);
        String formattedCost = EconomyManager.format(createCost);

        gui.setSlot(10, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("Join a Guild").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Accept your latest guild invite.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Use this after another player invites you.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to join if you have an invite.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> {
                    gui.close();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "guild accept");
                }));

        gui.setSlot(13, new GuiElementBuilder(Items.OAK_DOOR)
                .hideDefaultTooltip()
                .setName(Component.literal("Leave Current Guild").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal("Leave the guild you are currently in.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Guild owners should transfer or disband instead.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Click to leave your guild.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> {
                    gui.close();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "guild leave");
                }));

        gui.setSlot(16, new GuiElementBuilder(Items.BELL)
                .hideDefaultTooltip()
                .setName(Component.literal("Create Guild").withStyle(ChatFormatting.GOLD))
                .addLoreLine(Component.literal("Start your own guild and become its owner.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Cost: " + formattedCost + ".").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Use: /guild create <name> [tag]").withStyle(ChatFormatting.WHITE))
                .addLoreLine(Component.literal("Click for the command reminder.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, actionType) -> {
                    gui.close();
                    player.sendSystemMessage(Component.literal("Create a guild with: /guild create <name> [tag]").withStyle(ChatFormatting.GOLD));
                    player.sendSystemMessage(Component.literal("Guild creation costs " + formattedCost + ".").withStyle(ChatFormatting.YELLOW));
                }));

        gui.setSlot(22, new GuiElementBuilder(Items.ARROW)
                .hideDefaultTooltip()
                .setName(Component.literal("Back").withStyle(ChatFormatting.YELLOW))
                .addLoreLine(Component.literal("Return to the main menu.").withStyle(ChatFormatting.GRAY))
                .setCallback((index, clickType, actionType) -> MainMenu.open(player)));

        gui.open();
    }
}
