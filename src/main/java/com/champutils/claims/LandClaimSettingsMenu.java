package com.champutils.claims;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class LandClaimSettingsMenu {
    private LandClaimSettingsMenu() {}

    public static void open(ServerPlayer player) {
        LandClaimRepository.Claim claim = LandClaimRepository.findAt(player.serverLevel(), player.blockPosition());
        if (claim == null) {
            player.sendSystemMessage(Component.literal("You must stand inside your claim to open claim settings.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!LandClaimRepository.isOwner(player, claim)) {
            player.sendSystemMessage(Component.literal("You can only open settings inside your own profile's claim.").withStyle(ChatFormatting.RED));
            return;
        }

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x4, player, false);
        gui.setTitle(Component.literal("Land Claim Settings"));
        fill(gui);
        setToggle(gui, 10, Items.OAK_DOOR, "Allow Visitors", "Lets other players enter/use non-protected space.", claim.allowVisitors, claim, "allowVisitors", player);
        setToggle(gui, 12, Items.GRASS_BLOCK, "Visitors Can Build", "Allows block breaking and placing.", claim.visitorsCanBuild, claim, "visitorsCanBuild", player);
        setToggle(gui, 14, Items.CHEST, "Visitors Can Open Containers", "Allows chests, barrels, shulkers, hoppers, dispensers and droppers.", claim.visitorsCanOpenContainers, claim, "visitorsCanOpenContainers", player);
        setToggle(gui, 16, Items.LEAD, "Visitors Can Interact Entities", "Allows entity interaction and attacks.", claim.visitorsCanInteractEntities, claim, "visitorsCanInteractEntities", player);
        setToggle(gui, 21, Items.REDSTONE, "Visitors Can Use Redstone", "Allows buttons, levers, hoppers, dispensers and droppers.", claim.visitorsCanUseRedstone, claim, "visitorsCanUseRedstone", player);
        setToggle(gui, 23, Items.OAK_TRAPDOOR, "Visitors Can Use Doors", "Allows doors, trapdoors, and fence gates without granting other redstone access.", claim.visitorsCanUseDoors, claim, "visitorsCanUseDoors", player);
        setToggle(gui, 29, Items.SNOWBALL, "Visitors Can Catch Pokémon", "Allows other players to catch wild Pokémon while inside this claim.", claim.visitorsCanCatchPokemon, claim, "visitorsCanCatchPokemon", player);
        setToggle(gui, 31, Items.GRASS_BLOCK, "Pokémon Spawning", "Controls whether new wild Pokémon may spawn inside this claim.", claim.pokemonSpawningEnabled, claim, "pokemonSpawningEnabled", player);
        gui.open();
    }

    private static void setToggle(SimpleGui gui, int slot, Item icon, String name, String lore, boolean enabled, LandClaimRepository.Claim claim, String setting, ServerPlayer player) {
        gui.setSlot(slot, new GuiElementBuilder(enabled ? Items.LIME_DYE : icon)
                .setName(Component.literal((enabled ? "§a" : "§c") + name + ": " + (enabled ? "ON" : "OFF")))
                .addLoreLine(Component.literal("§7" + lore))
                .addLoreLine(Component.literal("§eClick to toggle."))
                .setCallback((index, clickType, actionType) -> {
                    LandClaimRepository.updateSetting(player, claim, setting, !enabled);
                    open(player);
                }));
    }

    private static void fill(SimpleGui gui) {
        GuiElementBuilder filler = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE).hideTooltip().setName(Component.literal(" "));
        for (int i = 0; i < gui.getSize(); i++) gui.setSlot(i, filler);
    }
}
