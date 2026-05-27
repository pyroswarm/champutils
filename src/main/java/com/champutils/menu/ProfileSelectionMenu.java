package com.champutils.menu;

import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileGameMode;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ProfileSelectionMenu {
    private ProfileSelectionMenu() {}

    public static void open(ServerPlayer player) {
        if (player == null) return;
        String finalized = PlayerProfileManager.finalizePendingDeletesBlocking(player);
        if (!finalized.isBlank()) player.sendSystemMessage(Component.literal(finalized).withStyle(ChatFormatting.GRAY));

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Select Profile"));

        List<PlayerProfileManager.ProfileRecord> profiles = PlayerProfileManager.listBlocking(player);
        PlayerProfileManager.ProfileLimit limit = PlayerProfileManager.limitBlocking(player);

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < profiles.size() && i < slots.length; i++) {
            var profile = profiles.get(i);
            GuiElementBuilder item = new GuiElementBuilder(icon(profile.gameMode()))
                    .hideDefaultTooltip()
                    .setName(Component.literal((profile.active() ? "★ " : "") + profile.profileName()).withStyle(profile.active() ? ChatFormatting.GREEN : ChatFormatting.AQUA))
                    .addLoreLine(Component.literal("Mode: " + profile.gameMode().displayName() + PlayerProfileManager.modeSuffix(profile)).withStyle(ChatFormatting.GRAY));
            if (profile.pendingDelete()) {
                item.addLoreLine(Component.literal("Pending deletion").withStyle(ChatFormatting.RED));
                if (profile.deleteAvailableAt() != null) {
                    item.addLoreLine(Component.literal("Frees at: " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(profile.deleteAvailableAt())).withStyle(ChatFormatting.DARK_RED));
                }
            } else if (profile.active()) {
                item.addLoreLine(Component.literal("Currently loaded").withStyle(ChatFormatting.GREEN));
            } else {
                item.addLoreLine(Component.literal("Click to load this profile").withStyle(ChatFormatting.YELLOW));
            }
            gui.setSlot(slots[i], item.setCallback((index, clickType, action, gui1) -> {
                if (profile.pendingDelete()) return;
                String result = PlayerProfileManager.switchBlocking(player, profile.profileName());
                player.sendSystemMessage(Component.literal(result).withStyle(result.startsWith("Loaded") ? ChatFormatting.GREEN : ChatFormatting.RED));
                gui.close();
            }));
        }

        gui.setSlot(22, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("Create a Profile").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Slots: " + profiles.size() + " / " + limit.maxProfiles()).withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Use /profiles create <name> <normal|ironman|monotype> [type]").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> {
                    player.sendSystemMessage(Component.literal("Create profiles with: /profiles create <name> <normal|ironman|monotype> [type]").withStyle(ChatFormatting.YELLOW));
                    gui.close();
                }));

        gui.setSlot(26, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("Delete Profiles").withStyle(ChatFormatting.RED))
                .addLoreLine(Component.literal(limit.instantDelete() ? "VIP instant deletion active." : "Normal deletion has a 24 hour cooldown.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Use /profiles delete <name>").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, clickType, action, gui1) -> {
                    player.sendSystemMessage(Component.literal("Delete profiles with: /profiles delete <name>").withStyle(ChatFormatting.YELLOW));
                    gui.close();
                }));

        MenuUtil.fillBordersForced(gui, slots[0], slots[1], slots[2], slots[3], slots[4], slots[5], 22, 26);
        gui.open();
    }

    private static net.minecraft.world.item.Item icon(ProfileGameMode mode) {
        return switch (mode) {
            case IRONMAN -> Items.IRON_INGOT;
            case MONOTYPE -> Items.BLAZE_POWDER;
            case NORMAL -> Items.GRASS_BLOCK;
        };
    }
}
