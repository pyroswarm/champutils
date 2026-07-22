package com.champutils.commands;

import com.champutils.economy.EconomyManager;
import com.champutils.matchmaking.PokemonIconUtil;
import com.champutils.menu.MenuUtil;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;

/** Inventory UI for selecting a party Pokémon, egg move, replacement slot, and purchase confirmation. */
public final class EggMoveMenu {
    private static final int[] PARTY_SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int[] MOVE_GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int[] REPLACE_SLOTS = {10, 12, 14, 16};

    private EggMoveMenu() {}

    public static void open(ServerPlayer player) {
        if (player == null) return;
        PartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Egg Move Tutor"));
        MenuUtil.fillBordersForced(gui, 4, 10, 11, 12, 14, 15, 16, 22);

        gui.setSlot(4, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                .hideDefaultTooltip()
                .setName(Component.literal("§d§lEgg Move Tutor"))
                .addLoreLine(Component.literal("§7Choose a party Pokémon."))
                .addLoreLine(Component.literal("§7Teaching costs credits."))
                .addLoreLine(Component.literal("§7Balance: §6" + EconomyManager.format(EconomyManager.getBalance(player)))));

        for (int i = 0; i < 6; i++) {
            Pokemon pokemon = party == null ? null : party.get(i);
            if (pokemon == null) continue;
            UUID pokemonId = pokemon.getUuid();
            List<String> available = EggMoveCommand.learnableEggMoveIds(pokemon);
            GuiElementBuilder builder = new GuiElementBuilder(PokemonIconUtil.getIcon(pokemon, i + 1, false))
                    .hideDefaultTooltip()
                    .setName(Component.literal((available.isEmpty() ? "§c" : "§a") + EggMoveCommand.displayName(pokemon)))
                    .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                    .addLoreLine(Component.literal("§7Available egg moves: §f" + available.size()));
            if (available.isEmpty()) {
                builder.addLoreLine(Component.literal("§eNo unlearned egg moves available."));
            } else {
                builder.addLoreLine(Component.literal("§eClick to view egg moves."))
                        .setCallback((slot, click, action) -> openMoves(player, pokemonId, 0));
            }
            gui.setSlot(PARTY_SLOTS[i], builder);
        }

        gui.setSlot(22, new GuiElementBuilder(Items.BARRIER)
                .hideDefaultTooltip()
                .setName(Component.literal("§cClose"))
                .setCallback((slot, click, action) -> player.closeContainer()));
        gui.open();
    }

    private static void openMoves(ServerPlayer player, UUID pokemonId, int requestedPage) {
        Pokemon pokemon = EggMoveCommand.findPartyPokemon(player, pokemonId);
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("§cThat Pokémon is no longer in your party."));
            open(player);
            return;
        }
        List<String> moves = EggMoveCommand.learnableEggMoveIds(pokemon);
        if (moves.isEmpty()) {
            player.sendSystemMessage(Component.literal("§eThat Pokémon has no unlearned egg moves available."));
            open(player);
            return;
        }

        int pageSize = MOVE_GRID.length;
        int pages = Math.max(1, (moves.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Egg Moves: " + EggMoveCommand.displayName(pokemon) + " " + (page + 1) + "/" + pages));
        MenuUtil.fillBordersForced(gui, MOVE_GRID);

        int from = page * pageSize;
        int to = Math.min(moves.size(), from + pageSize);
        for (int index = from; index < to; index++) {
            String moveId = moves.get(index);
            long price = EggMoveCommand.priceCents(moveId);
            gui.setSlot(MOVE_GRID[index - from], new GuiElementBuilder(Items.ENCHANTED_BOOK)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§b" + EggMoveCommand.prettyMove(moveId)))
                    .addLoreLine(Component.literal("§6Price: §f" + EconomyManager.format(price)))
                    .addLoreLine(Component.literal("§7Current balance: §f" + EconomyManager.format(EconomyManager.getBalance(player))))
                    .addLoreLine(Component.literal("§eClick to select."))
                    .setCallback((slot, click, action) -> selectMove(player, pokemonId, moveId)));
        }

        if (page > 0) gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page"))
                .setCallback((slot, click, action) -> openMoves(player, pokemonId, page - 1)));
        gui.setSlot(49, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack to Party"))
                .setCallback((slot, click, action) -> open(player)));
        if (page + 1 < pages) gui.setSlot(53, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page"))
                .setCallback((slot, click, action) -> openMoves(player, pokemonId, page + 1)));
        gui.open();
    }

    private static void selectMove(ServerPlayer player, UUID pokemonId, String moveId) {
        Pokemon pokemon = EggMoveCommand.findPartyPokemon(player, pokemonId);
        if (pokemon == null || !EggMoveCommand.learnableEggMoveIds(pokemon).contains(moveId)) {
            player.sendSystemMessage(Component.literal("§cThat egg move is no longer available."));
            open(player);
            return;
        }
        List<String> current = EggMoveCommand.currentMoveIds(pokemon);
        if (current.size() >= 4) openReplacement(player, pokemonId, moveId);
        else openConfirmation(player, pokemonId, moveId, 0, null);
    }

    private static void openReplacement(ServerPlayer player, UUID pokemonId, String moveId) {
        Pokemon pokemon = EggMoveCommand.findPartyPokemon(player, pokemonId);
        if (pokemon == null || !EggMoveCommand.learnableEggMoveIds(pokemon).contains(moveId)) {
            player.sendSystemMessage(Component.literal("§cThat egg move is no longer available."));
            open(player);
            return;
        }
        List<String> current = EggMoveCommand.currentMoveIds(pokemon);
        if (current.size() < 4) {
            openConfirmation(player, pokemonId, moveId, 0, null);
            return;
        }

        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Choose a Move to Replace"));
        MenuUtil.fillBordersForced(gui, 10, 12, 14, 16, 22);
        for (int i = 0; i < 4; i++) {
            int replaceSlot = i + 1;
            String oldMove = current.get(i);
            gui.setSlot(REPLACE_SLOTS[i], new GuiElementBuilder(Items.PAPER)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + EggMoveCommand.prettyMove(oldMove)))
                    .addLoreLine(Component.literal("§7Replace with: §b" + EggMoveCommand.prettyMove(moveId)))
                    .addLoreLine(Component.literal("§cThe forgotten move is not refunded."))
                    .addLoreLine(Component.literal("§eClick to continue."))
                    .setCallback((slot, click, action) -> openConfirmation(player, pokemonId, moveId, replaceSlot, oldMove)));
        }
        gui.setSlot(22, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack"))
                .setCallback((slot, click, action) -> openMoves(player, pokemonId, 0)));
        gui.open();
    }

    private static void openConfirmation(ServerPlayer player, UUID pokemonId, String moveId, int replaceSlot, String oldMove) {
        Pokemon pokemon = EggMoveCommand.findPartyPokemon(player, pokemonId);
        if (pokemon == null || !EggMoveCommand.learnableEggMoveIds(pokemon).contains(moveId)) {
            player.sendSystemMessage(Component.literal("§cThat egg move is no longer available."));
            open(player);
            return;
        }
        long price = EggMoveCommand.priceCents(moveId);
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Confirm Egg Move"));
        MenuUtil.fillBordersForced(gui, 11, 13, 15);
        GuiElementBuilder summary = new GuiElementBuilder(PokemonIconUtil.getIcon(pokemon, 1, false))
                .hideDefaultTooltip()
                .setName(Component.literal("§f" + EggMoveCommand.displayName(pokemon)))
                .addLoreLine(Component.literal("§7Learn: §a" + EggMoveCommand.prettyMove(moveId)));
        if (oldMove != null) summary.addLoreLine(Component.literal("§7Forget: §c" + EggMoveCommand.prettyMove(oldMove)));
        summary.addLoreLine(Component.literal("§6Cost: §f" + EconomyManager.format(price)))
                .addLoreLine(Component.literal("§7Balance: §f" + EconomyManager.format(EconomyManager.getBalance(player))));
        gui.setSlot(13, summary);
        gui.setSlot(11, new GuiElementBuilder(Items.LIME_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("§aConfirm Purchase"))
                .addLoreLine(Component.literal("§7Credits are charged only if teaching succeeds."))
                .setCallback((slot, click, action) -> EggMoveCommand.beginPurchase(player, pokemonId, moveId, replaceSlot)));
        gui.setSlot(15, new GuiElementBuilder(Items.RED_CONCRETE)
                .hideDefaultTooltip()
                .setName(Component.literal("§cCancel"))
                .setCallback((slot, click, action) -> {
                    if (oldMove == null) {
                        openMoves(player, pokemonId, 0);
                    } else {
                        openReplacement(player, pokemonId, moveId);
                    }
                }));
        gui.open();
    }
}
