package com.champutils.tm;

import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Right-click TM flow: choose a party Pokémon, inspect compatibility, and teach safely. */
public final class TMTeachMenu {
    private static final int[] PARTY_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int[] MOVE_SLOTS = {10, 12, 14, 16};

    private TMTeachMenu() {}

    public static void open(ServerPlayer player) {
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        String moveId = TMManager.getMoveId(held);
        if (moveId == null) {
            player.sendSystemMessage(Component.literal("Hold the TM in your main hand to use it.").withStyle(ChatFormatting.RED));
            return;
        }

        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Teach " + TMManager.prettyMove(moveId)));
        MenuUtil.fillBordersForced(gui, 10, 11, 12, 14, 15, 16, 22);

        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party == null ? null : party.get(i);
            if (pokemon == null) continue;
            UUID pokemonId = pokemon.getUuid();
            boolean knows = TMManager.pokemonAlreadyKnows(pokemon, moveId);
            boolean canLearn = !knows && TMManager.pokemonCanLearnTM(pokemon, moveId);
            List<String> moves = TMManager.pokemonMoveIds(pokemon);

            GuiElementBuilder builder = new GuiElementBuilder(PokemonIconUtil.getIcon(pokemon, i + 1, false))
                    .hideDefaultTooltip()
                    .setName(Component.literal((canLearn ? "§a" : "§c") + pokemon.getDisplayName(true).getString()))
                    .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§7Move: §f" + TMManager.prettyMove(moveId)));

            if (knows) {
                builder.addLoreLine(Component.literal("§eAlready knows this move."));
            } else if (!canLearn) {
                builder.addLoreLine(Component.literal("§cCannot learn this move."));
            } else if (moves.size() >= 4) {
                builder.addLoreLine(Component.literal("§aCan learn this move."))
                        .addLoreLine(Component.literal("§7Moveset is full."))
                        .addLoreLine(Component.literal("§eClick to choose a move to replace."))
                        .setCallback((slot, click, action) -> openReplacement(player, pokemonId, moveId));
            } else {
                builder.addLoreLine(Component.literal("§aCan learn this move."))
                        .addLoreLine(Component.literal("§eClick to teach it."))
                        .setCallback((slot, click, action) -> teach(player, pokemonId, moveId, 0));
            }
            gui.setSlot(PARTY_SLOTS[i], builder);
        }

        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§cClose"))
                .setCallback((slot, click, action) -> player.closeContainer()));
        gui.open();
    }

    private static void openReplacement(ServerPlayer player, UUID pokemonId, String expectedMoveId) {
        Pokemon pokemon = validateTarget(player, pokemonId, expectedMoveId);
        if (pokemon == null) return;
        List<String> moves = TMManager.pokemonMoveIds(pokemon);
        if (moves.size() < 4) {
            open(player);
            return;
        }

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Replace a Move"));
        MenuUtil.fillBordersForced(gui, 10, 12, 14, 16, 22);
        for (int i = 0; i < 4; i++) {
            String oldMove = moves.get(i);
            int replaceSlot = i + 1;
            gui.setSlot(MOVE_SLOTS[i], new GuiElementBuilder(Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + TMManager.prettyMove(oldMove)))
                    .addLoreLine(Component.literal("§7Replace with: §b" + TMManager.prettyMove(expectedMoveId)))
                    .addLoreLine(Component.literal("§cThis cannot be undone."))
                    .addLoreLine(Component.literal("§eClick to continue."))
                    .setCallback((slot, click, action) -> openConfirmation(player, pokemonId, expectedMoveId, replaceSlot, oldMove)));
        }
        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER).hideDefaultTooltip().setName(Component.literal("§cBack"))
                .setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    private static void openConfirmation(ServerPlayer player, UUID pokemonId, String expectedMoveId, int replaceSlot, String oldMove) {
        Pokemon pokemon = validateTarget(player, pokemonId, expectedMoveId);
        if (pokemon == null) return;
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Confirm TM Teaching"));
        MenuUtil.fillBordersForced(gui, 11, 13, 15);
        gui.setSlot(13, new GuiElementBuilder(PokemonIconUtil.getIcon(pokemon, 1, false))
                .hideDefaultTooltip()
                .setName(Component.literal("§f" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Forget: §c" + TMManager.prettyMove(oldMove)))
                .addLoreLine(Component.literal("§7Learn: §a" + TMManager.prettyMove(expectedMoveId)))
                .addLoreLine(Component.literal("§7One TM use will be consumed.")));
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE).hideDefaultTooltip().setName(Component.literal("§aConfirm"))
                .setCallback((slot, click, action) -> teach(player, pokemonId, expectedMoveId, replaceSlot)));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE).hideDefaultTooltip().setName(Component.literal("§cCancel"))
                .setCallback((slot, click, action) -> openReplacement(player, pokemonId, expectedMoveId)));
        gui.open();
    }

    private static Pokemon validateTarget(ServerPlayer player, UUID pokemonId, String expectedMoveId) {
        String heldMove = TMManager.getMoveId(player.getMainHandItem());
        if (heldMove == null || !heldMove.equals(expectedMoveId)) {
            player.sendSystemMessage(Component.literal("You must still be holding the same TM in your main hand.").withStyle(ChatFormatting.RED));
            player.closeContainer();
            return null;
        }
        Pokemon pokemon = TMManager.findPartyPokemon(player, pokemonId);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("That Pokémon is no longer in your party.").withStyle(ChatFormatting.RED));
            player.closeContainer();
            return null;
        }
        if (TMManager.pokemonAlreadyKnows(pokemon, expectedMoveId)) {
            player.sendSystemMessage(Component.literal(pokemon.getDisplayName(true).getString() + " already knows " + TMManager.prettyMove(expectedMoveId) + ".").withStyle(ChatFormatting.YELLOW));
            open(player);
            return null;
        }
        if (!TMManager.pokemonCanLearnTM(pokemon, expectedMoveId)) {
            player.sendSystemMessage(Component.literal(pokemon.getDisplayName(true).getString() + " cannot learn " + TMManager.prettyMove(expectedMoveId) + ".").withStyle(ChatFormatting.RED));
            open(player);
            return null;
        }
        return pokemon;
    }

    private static void teach(ServerPlayer player, UUID pokemonId, String expectedMoveId, int replaceSlot) {
        if (validateTarget(player, pokemonId, expectedMoveId) == null) return;
        TMManager.TeachResult result = TMManager.teachHeldTMByPokemonId(player, pokemonId, replaceSlot);
        player.sendSystemMessage(Component.literal(result.message()).withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (result.success()) player.closeContainer();
        else open(player);
    }
}
