package com.champutils.menu;

import com.champutils.hunt.PokemonHuntManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.profile.NuzlockeManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class GraveyardMenu {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault());
    private GraveyardMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("§8Nuzlocke Graveyard"));
        MenuUtil.fillBordersForced(gui, 45, 46, 47, 48, 50, 51, 52, 53);

        boolean unlocked = NuzlockeManager.canAccessGraveyard(player);
        List<NuzlockeManager.GraveyardEntry> entries = NuzlockeManager.graveyardEntries(player, true);
        int[] slots = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};

        for (int i = 0; i < Math.min(slots.length, entries.size()); i++) {
            NuzlockeManager.GraveyardEntry entry = entries.get(i);
            boolean claimed = entry.claimedAt() != null;
            var icon = PokemonIconUtil.createPokemonIcon(entry.species(), false, "minecraft:skeleton_skull", claimed);
            GuiElementBuilder b = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal((claimed ? "§7" : "§c") + pretty(entry.species())))
                    .addLoreLine(Component.literal("§7Status: " + (claimed ? "§aClaimed" : "§cIn Graveyard")))
                    .addLoreLine(Component.literal("§7Reason: §f" + prettyReason(entry.reason())))
                    .addLoreLine(Component.literal("§7Entered: §f" + DATE.format(entry.enteredAt())))
                    .addLoreLine(Component.literal("§8UUID: " + entry.pokemonUuid()));
            if (claimed) b.addLoreLine(Component.literal("§7Claimed: §f" + DATE.format(entry.claimedAt())));
            gui.setSlot(slots[i], b);
        }

        if (entries.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.BONE)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§aNo deaths recorded"))
                    .addLoreLine(Component.literal("§7Your Nuzlocke Graveyard is empty.")));
        }

        GuiElementBuilder claim = new GuiElementBuilder(unlocked ? Items.CHEST : Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal(unlocked ? "§aClaim Graveyard Pokémon" : "§cGraveyard Locked"))
                .addLoreLine(Component.literal(unlocked ? "§7Click to run §e/graveyard claim§7." : "§7Unlocks after Nuzlocke completion or conversion to Normal."));
        if (unlocked) {
            claim.setCallback((index, clickType, actionType, g) -> {
                String result = NuzlockeManager.claimGraveyard(player);
                player.sendSystemMessage(Component.literal(result).withStyle(result.contains("Restored") ? ChatFormatting.GREEN : ChatFormatting.RED));
                open(player);
            });
        }
        gui.setSlot(49, claim);
        gui.open();
    }

    private static String pretty(String species) {
        if (species == null || species.isBlank()) return "Unknown Pokémon";
        try { return PokemonHuntManager.prettySpecies(species); } catch (Throwable ignored) { return species; }
    }

    private static String prettyReason(String reason) {
        if (reason == null || reason.isBlank()) return "Unknown";
        return switch (reason) {
            case "battle_faint" -> "Fainted in battle";
            case "duplicate_species_catch" -> "Duplicate species catch";
            default -> reason.replace('_', ' ');
        };
    }
}
