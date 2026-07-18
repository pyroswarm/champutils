package com.champutils.breeding;

import com.champutils.matchmaking.PokemonIconUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

public final class BreedingMenu {
    private static final int[] PARTY_GUI_SLOTS = {19, 20, 21, 23, 24, 25};

    private BreedingMenu() {}

    public static void open(ServerPlayer player) {
        open(player, -1);
    }

    private static void open(ServerPlayer player, int firstParentSlot) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x5, player, false);
        gui.setTitle(Component.literal(firstParentSlot < 0 ? "Pokémon Nursery" : "Choose Second Parent"));
        fill(gui);

        gui.setSlot(4, new GuiElementBuilder(Items.TURTLE_EGG)
                .hideDefaultTooltip()
                .setName(Component.literal("§d§lPokémon Breeding"))
                .addLoreLine(Component.literal("§7Choose two compatible party Pokémon."))
                .addLoreLine(Component.literal("§7The Egg occupies a real party slot."))
                .addLoreLine(Component.literal("§7Travel while it is in your party to hatch it."))
                .addLoreLine(Component.literal("§7Walking and mounted travel both count.")));

        PartyStore party = party(player);
        for (int i = 0; i < PARTY_GUI_SLOTS.length; i++) {
            Pokemon pokemon = party == null ? null : party.get(i);
            int guiSlot = PARTY_GUI_SLOTS[i];
            int partySlot = i;
            if (pokemon == null) {
                gui.setSlot(guiSlot, new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§7Empty Party Slot " + (i + 1))));
                continue;
            }
            if (BreedingEggData.isEgg(pokemon)) {
                GuiElementBuilder eggButton = new GuiElementBuilder(Items.TURTLE_EGG)
                        .hideDefaultTooltip()
                        .setName(Component.literal("§eEgg §7(Slot " + (i + 1) + ")"))
                        .addLoreLine(Component.literal("§7Progress: §d" + BreedingEggData.progressPercent(pokemon) + "%"))
                        .addLoreLine(Component.literal("§7Steps: §f" + BreedingEggData.steps(pokemon) + "§7/§f" + BreedingEggData.requiredSteps(pokemon)))
                        .addLoreLine(Component.literal("§7Remaining: §f" + BreedingEggData.remainingSteps(pokemon)))
                        .addLoreLine(Component.literal("§f" + BreedingEggData.hatchStage(pokemon)));
                if (BreedingConfig.get().revealOffspringTypes) {
                    eggButton.addLoreLine(Component.literal("§7Egg type: §b" + prettyTypes(BreedingEggData.offspringTypes(pokemon))));
                }
                if (BreedingConfig.get().revealOffspringSpecies) {
                    eggButton.addLoreLine(Component.literal("§7Will hatch: §f" + prettySpecies(BreedingEggData.offspringSpecies(pokemon))));
                }
                eggButton.addLoreLine(Component.literal("§8Eggs cannot battle, be sent out, traded,"));
                eggButton.addLoreLine(Component.literal("§8auctioned, or wonder traded."));
                gui.setSlot(guiSlot, eggButton);
                continue;
            }

            ItemStack icon = PokemonIconUtil.getIcon(pokemon, i + 1, false);
            GuiElementBuilder button = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal((firstParentSlot == i ? "§a§l✓ " : "§e") + pokemon.getDisplayName(true).getString()))
                    .addLoreLine(Component.literal("§7Party Slot: §f" + (i + 1)))
                    .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§7Gender: §f" + pretty(pokemon.getGender().name())))
                    .addLoreLine(Component.literal("§7Egg Groups: §f" + eggGroups(pokemon)));
            if (firstParentSlot < 0) {
                button.addLoreLine(Component.literal("§eClick to choose the first parent."))
                        .setCallback((slot, click, action) -> open(player, partySlot));
            } else if (firstParentSlot == i) {
                button.addLoreLine(Component.literal("§aSelected as first parent."))
                        .addLoreLine(Component.literal("§eClick to clear selection."))
                        .setCallback((slot, click, action) -> open(player, -1));
            } else {
                Pokemon first = party == null ? null : party.get(firstParentSlot);
                PokemonBreedingRules.Compatibility compatible = PokemonBreedingRules.compatibility(first, pokemon);
                button.addLoreLine(Component.literal((compatible.compatible() ? "§a" : "§c") + compatible.reason()));
                if (compatible.compatible()) {
                    button.addLoreLine(Component.literal("§eClick to review this pairing."))
                            .setCallback((slot, click, action) -> confirm(player, firstParentSlot, partySlot));
                }
            }
            gui.setSlot(guiSlot, button);
        }

        gui.setSlot(40, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§bBreeding Rules"))
                .addLoreLine(Component.literal("§7• Opposite genders sharing an Egg Group"))
                .addLoreLine(Component.literal("§7• Ditto can pair with most non-Ditto Pokémon"))
                .addLoreLine(Component.literal("§7• Destiny Knot: 5 inherited IVs total instead of 3"))
                .addLoreLine(Component.literal("§7• Power item: forces its stat and uses one inherited slot"))
                .addLoreLine(Component.literal("§7• Everstone: passes the holder's original Nature"))
                .addLoreLine(Component.literal("§7• Mints and Hyper Training do not pass to Eggs"))
                .addLoreLine(Component.literal("§7• Ability Capsule/Patch changes the inheritable slot"))
                .addLoreLine(Component.literal("§7• Egg Moves can pass from either parent's current moves"))
                .addLoreLine(Component.literal("§7• Flame Body/Magma Armor/Steam Engine halve steps")));
        gui.open();
    }

    private static void confirm(ServerPlayer player, int firstSlot, int secondSlot) {
        PartyStore party = party(player);
        Pokemon first = party == null ? null : party.get(firstSlot);
        Pokemon second = party == null ? null : party.get(secondSlot);
        PokemonBreedingRules.Compatibility compatible = PokemonBreedingRules.compatibility(first, second);
        if (!compatible.compatible()) {
            open(player, firstSlot);
            return;
        }

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setTitle(Component.literal("Confirm Breeding"));
        fill(gui);
        gui.setSlot(10, parentButton(first, firstSlot));
        gui.setSlot(16, parentButton(second, secondSlot));
        gui.setSlot(13, new GuiElementBuilder(Items.TURTLE_EGG)
                .hideDefaultTooltip()
                .setName(Component.literal("§d§lCompatible Pair"))
                .addLoreLine(Component.literal("§a" + compatible.reason()))
                .addLoreLine(Component.literal("§7An Egg will be placed in the first open party slot."))
                .addLoreLine(Component.literal("§7Parents are not removed or locked."))
                .addLoreLine(Component.literal("§7Cooldown: §f" + formatCooldown(BreedingConfig.get().breedingCooldownSeconds))));
        gui.setSlot(11, new GuiElementBuilder(Items.GREEN_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§a§lCreate Egg"))
                .addLoreLine(Component.literal("§7Create the Egg in your first open party slot."))
                .setCallback((slot, click, action) -> {
                    player.closeContainer();
                    BreedingManager.startBreeding(player, firstSlot, secondSlot);
                }));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_STAINED_GLASS_PANE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cBack"))
                .setCallback((slot, click, action) -> open(player, firstSlot)));
        gui.open();
    }

    private static GuiElementBuilder parentButton(Pokemon pokemon, int slot) {
        ItemStack icon = PokemonIconUtil.getIcon(pokemon, slot + 1, false);
        return new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal("§e" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Party Slot: §f" + (slot + 1)))
                .addLoreLine(Component.literal("§7Gender: §f" + pretty(pokemon.getGender().name())))
                .addLoreLine(Component.literal("§7Breedable: §f" + (PokemonBreedability.isBreedable(pokemon) ? "Yes" : "No")))
                .addLoreLine(Component.literal(PokemonBreedability.isBreedable(pokemon) ? "§8Eligible as a parent." : "§cPermanently cannot breed."))
                .addLoreLine(Component.literal("§7Egg Groups: §f" + eggGroups(pokemon)));
    }

    private static String formatCooldown(int seconds) {
        if (seconds <= 0) return "Disabled";
        if (seconds % 60 == 0) {
            int minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }
        return seconds + " seconds";
    }

    private static void fill(SimpleGui gui) {
        GuiElementBuilder pane = new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).hideDefaultTooltip().setName(Component.empty());
        for (int i = 0; i < gui.getSize(); i++) gui.setSlot(i, pane);
    }

    private static PartyStore party(ServerPlayer player) {
        try { return Cobblemon.INSTANCE.getStorage().getParty(player); }
        catch (Throwable ignored) { return null; }
    }

    private static String eggGroups(Pokemon pokemon) {
        StringBuilder value = new StringBuilder();
        try {
            for (var group : pokemon.getForm().getEggGroups()) {
                if (value.length() > 0) value.append(", ");
                value.append(pretty(group.name()));
            }
        } catch (Throwable ignored) {
        }
        return value.length() == 0 ? "None" : value.toString();
    }

    private static String prettySpecies(String id) {
        if (id == null || id.isBlank()) return "Unknown";
        int colon = id.indexOf(':');
        return pretty(colon >= 0 ? id.substring(colon + 1) : id);
    }

    private static String prettyTypes(String types) {
        if (types == null || types.isBlank()) return "Unknown";
        String[] split = types.split("/");
        StringBuilder result = new StringBuilder();
        for (String type : split) {
            if (result.length() > 0) result.append(" / ");
            result.append(pretty(type));
        }
        return result.toString();
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String cleaned = value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        StringBuilder result = new StringBuilder();
        for (String word : cleaned.split("\\s+")) {
            if (word.isBlank()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
