package com.champutils.territory;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class TerritoryMenus {
    private TerritoryMenus() {}

    public static void open(ServerPlayer player, TerritoryRepository.OwnerType type) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal(type == TerritoryRepository.OwnerType.GUILD ? "Public Guild Territories" : "Public Player Territories"));
        fill(gui);

        List<TerritoryRepository.Territory> territories = TerritoryRepository.publicCached(type);
        int slot = 0;
        for (TerritoryRepository.Territory territory : territories) {
            if (slot >= 45) break;
            List<Component> lore = new ArrayList<>();
            lore.add(Component.literal("Owner: " + territory.ownerName).withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("World: " + territory.worldName + " | Slot: " + territory.slotIndex).withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Status: " + (territory.generationState == null ? "READY" : territory.generationState)).withStyle(territory.isReady() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            lore.add(Component.literal("Biome: " + (territory.biomePreference == null ? "Any" : territory.biomePreference)).withStyle(ChatFormatting.GRAY));
            lore.add(Component.literal("Visitors: " + (territory.allowVisitors ? "Allowed" : "Listed Only")).withStyle(territory.allowVisitors ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            lore.add(Component.literal("Click to visit.").withStyle(ChatFormatting.AQUA));

            gui.setSlot(slot++, new GuiElementBuilder(type == TerritoryRepository.OwnerType.GUILD ? Items.BELL : Items.GRASS_BLOCK)
                    .setName(Component.literal(territory.ownerName).withStyle(ChatFormatting.GOLD))
                    .setLore(lore)
                    .setCallback((index, clickType, actionType) -> {
                        if (!territory.isReady() && !player.hasPermissions(4)) {
                            player.sendSystemMessage(Component.literal("That territory world is still being created or loaded. Try again shortly.").withStyle(ChatFormatting.YELLOW));
                            return;
                        }
                        if (!TerritoryRepository.canEnter(player, territory)) {
                            player.sendSystemMessage(Component.literal("You cannot visit that territory.").withStyle(ChatFormatting.RED));
                            return;
                        }
                        gui.close();
                        if (!TerritoryTeleportUtil.teleportHome(player, territory)) {
                            player.sendSystemMessage(Component.literal("That territory world is not loaded: " + territory.worldName).withStyle(ChatFormatting.RED));
                        }
                    }));
        }

        if (territories.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                    .setName(Component.literal("No public territories yet").withStyle(ChatFormatting.RED))
                    .setLore(List.of(Component.literal("Players can enable this with /territory set public true.").withStyle(ChatFormatting.GRAY))));
        }

        gui.open();
    }

    private static void fill(SimpleGui gui) {
        GuiElementBuilder filler = new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" "));
        for (int i = 45; i < 54; i++) {
            gui.setSlot(i, filler);
        }
    }
}
