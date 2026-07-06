package com.champutils.contracts;

import com.champutils.auction.AuctionItemSerializer;
import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.menu.MenuUtil;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.CobblemonItems;
import com.cobblemon.mod.common.pokemon.Pokemon;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PlayerContractMenu {
    private PlayerContractMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x5, player);
        gui.setTitle(Component.literal("Player Contracts"));
        MenuUtil.fillBorders(gui, 4, 19, 20, 21, 23, 24, 25, 40);

        gui.setSlot(4, new GuiElementBuilder(Items.WRITABLE_BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§ePlayer Contracts"))
                .addLoreLine(Component.literal("§7Find jobs for items or Pokémon."))
                .addLoreLine(Component.literal("§7Finish jobs to earn Credits.")));

        gui.setSlot(19, new GuiElementBuilder(Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§aItem Contracts"))
                .addLoreLine(Component.literal("§7Browse item jobs."))
                .addLoreLine(Component.literal("§eClick to view"))
                .setCallback((slot, click, action) -> openBoard(player, "ITEM")));

        gui.setSlot(20, new GuiElementBuilder(CobblemonItems.POKE_BALL)
                .hideDefaultTooltip()
                .setName(Component.literal("§bPokémon Contracts"))
                .addLoreLine(Component.literal("§7Browse Pokémon jobs."))
                .addLoreLine(Component.literal("§eClick to view"))
                .setCallback((slot, click, action) -> openBoard(player, "POKEMON")));

        gui.setSlot(21, new GuiElementBuilder(Items.MAP)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Active Contracts"))
                .addLoreLine(Component.literal("§7View every open player job."))
                .addLoreLine(Component.literal("§eClick to view"))
                .setCallback((slot, click, action) -> openBoard(player, null)));

        gui.setSlot(23, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aClaim Contract"))
                .addLoreLine(Component.literal("§7Claim items or Pokémon from"))
                .addLoreLine(Component.literal("§7contracts other players finished."))
                .addLoreLine(Component.literal("§eClick to claim"))
                .setCallback((slot, click, action) -> PlayerContractService.claimNext(player)));

        gui.setSlot(24, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§eYour Contracts"))
                .addLoreLine(Component.literal("§7See your open and completed jobs."))
                .addLoreLine(Component.literal("§eClick to view"))
                .setCallback((slot, click, action) -> openMine(player)));

        gui.setSlot(25, new GuiElementBuilder(Items.ANVIL)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Create Item Contract"))
                .addLoreLine(Component.literal("§7Hold the requested item stack,"))
                .addLoreLine(Component.literal("§7then choose the reward."))
                .addLoreLine(Component.literal("§eClick to create"))
                .setCallback((slot, click, action) -> openCreateItem(player)));

        gui.setSlot(31, new GuiElementBuilder(CobblemonItems.GREAT_BALL)
                .hideDefaultTooltip()
                .setName(Component.literal("§bCreate Pokémon Contract"))
                .addLoreLine(Component.literal("§7Choose a party Pokémon as"))
                .addLoreLine(Component.literal("§7the request template."))
                .addLoreLine(Component.literal("§eClick to create"))
                .setCallback((slot, click, action) -> openCreatePokemonSlots(player)));

        MenuUtil.addBackButton(gui, 40, () -> com.champutils.adventurer.AdventurerGuildMenu.open(player));
        gui.open();
    }

    private static void openCreateItem(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Create Item Contract"));
        gui.setSlot(4, new GuiElementBuilder(Items.ANVIL).hideDefaultTooltip()
                .setName(Component.literal("§6Item Contract"))
                .addLoreLine(Component.literal("§7Hold the exact stack you want"))
                .addLoreLine(Component.literal("§7another player to deliver.")));
        setRewardButtons(gui, player, amount -> {
            PlayerContractService.createItemContractFromHand(player, amount);
            open(player);
        });
        MenuUtil.addBackButton(gui, 18, () -> open(player));
        gui.open();
    }

    private static void openCreatePokemonSlots(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x1, player);
        gui.setTitle(Component.literal("Choose Template Pokémon"));
        for (int i = 0; i < 6; i++) {
            int slotIndex = i;
            Pokemon pokemon = null;
            try { pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slotIndex); } catch (Exception ignored) {}
            if (pokemon == null) {
                gui.setSlot(i, new GuiElementBuilder(Items.GRAY_DYE).hideDefaultTooltip().setName(Component.literal("§7Empty Slot " + (i + 1))));
                continue;
            }
            gui.setSlot(i, pokemonSlotButton(pokemon, i + 1)
                    .addLoreLine(Component.literal("§eClick to choose reward"))
                    .setCallback((slot, click, action) -> openCreatePokemonReward(player, slotIndex)));
        }
        gui.setSlot(8, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack"))
                .setCallback((slot, click, action) -> open(player)));
        gui.open();
    }

    private static void openCreatePokemonReward(ServerPlayer player, int partySlotIndex) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Pokémon Contract Reward"));
        gui.setSlot(4, new GuiElementBuilder(CobblemonItems.POKE_BALL).hideDefaultTooltip()
                .setName(Component.literal("§bPokémon Contract"))
                .addLoreLine(Component.literal("§7Pick the Credits reward"))
                .addLoreLine(Component.literal("§7for this request.")));
        setRewardButtons(gui, player, amount -> {
            PlayerContractService.createPokemonContractFromSlot(player, partySlotIndex, amount);
            open(player);
        });
        MenuUtil.addBackButton(gui, 18, () -> openCreatePokemonSlots(player));
        gui.open();
    }

    private static void setRewardButtons(SimpleGui gui, ServerPlayer player, java.util.function.IntConsumer callback) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int[] amounts = {100, 250, 500, 1000, 2500, 5000, 10000};
        for (int i = 0; i < amounts.length; i++) {
            int amount = amounts[i];
            gui.setSlot(slots[i], new GuiElementBuilder(Items.EMERALD)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§6" + amount + " Credits"))
                    .addLoreLine(Component.literal("§7Paid now and awarded"))
                    .addLoreLine(Component.literal("§7when the contract is done."))
                    .addLoreLine(Component.literal("§eClick to create"))
                    .setCallback((slot, click, action) -> callback.accept(amount)));
        }
    }

    private static void openBoard(ServerPlayer player, String type) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(type == null ? "Active Contracts" : ("ITEM".equals(type) ? "Item Contracts" : "Pokémon Contracts")));
        gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack"))
                .setCallback((slot, click, action) -> open(player)));
        gui.setSlot(49, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip().setName(Component.literal("§aClaim Contract"))
                .addLoreLine(Component.literal("§7Claim completed jobs."))
                .setCallback((slot, click, action) -> PlayerContractService.claimNext(player)));
        gui.open();

        DatabaseManager.supplyAsync("load active player contracts", connection -> PlayerContractRepository.fetchActive(type, 45))
                .whenComplete((contracts, error) -> player.server.execute(() -> {
                    if (error != null) {
                        player.sendSystemMessage(Component.literal("Could not load player contracts.").withStyle(ChatFormatting.RED));
                        error.printStackTrace();
                        return;
                    }
                    renderBoard(player, gui, contracts == null ? List.of() : contracts);
                }));
    }

    private static void openMine(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal("Your Contracts"));
        gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack"))
                .setCallback((slot, click, action) -> open(player)));
        gui.setSlot(49, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip().setName(Component.literal("§aClaim Contract"))
                .addLoreLine(Component.literal("§7Claim completed jobs."))
                .setCallback((slot, click, action) -> PlayerContractService.claimNext(player)));
        gui.open();

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        DatabaseManager.supplyAsync("load own player contracts", connection -> PlayerContractRepository.fetchOwnerContracts(profileId, 45))
                .whenComplete((contracts, error) -> player.server.execute(() -> {
                    if (error != null) {
                        player.sendSystemMessage(Component.literal("Could not load your contracts.").withStyle(ChatFormatting.RED));
                        error.printStackTrace();
                        return;
                    }
                    renderMine(player, gui, contracts == null ? List.of() : contracts);
                }));
    }

    private static void renderBoard(ServerPlayer player, SimpleGui gui, List<PlayerContractRepository.ContractSummary> contracts) {
        int[] slots = boardSlots();
        if (contracts.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.GRAY_DYE).hideDefaultTooltip()
                    .setName(Component.literal("§7No open contracts"))
                    .addLoreLine(Component.literal("§7Check back later.")));
            return;
        }
        for (int i = 0; i < Math.min(slots.length, contracts.size()); i++) {
            PlayerContractRepository.ContractSummary contract = contracts.get(i);
            gui.setSlot(slots[i], buttonFor(player, contract, true));
        }
    }

    private static void renderMine(ServerPlayer player, SimpleGui gui, List<PlayerContractRepository.ContractSummary> contracts) {
        int[] slots = boardSlots();
        if (contracts.isEmpty()) {
            gui.setSlot(22, new GuiElementBuilder(Items.GRAY_DYE).hideDefaultTooltip()
                    .setName(Component.literal("§7No contracts posted"))
                    .addLoreLine(Component.literal("§7Completed contracts will appear here.")));
            return;
        }
        for (int i = 0; i < Math.min(slots.length, contracts.size()); i++) {
            PlayerContractRepository.ContractSummary contract = contracts.get(i);
            gui.setSlot(slots[i], buttonFor(player, contract, false));
        }
    }

    private static GuiElementBuilder buttonFor(ServerPlayer player, PlayerContractRepository.ContractSummary contract, boolean canComplete) {
        GuiElementBuilder builder = iconFor(player, contract).hideDefaultTooltip()
                .setName(Component.literal(("COMPLETED".equalsIgnoreCase(contract.status) ? "§a" : "§e") + cleanTitle(contract.title)));
        builder.addLoreLine(Component.literal("§7Type: §f" + ("POKEMON".equalsIgnoreCase(contract.type) ? "Pokémon" : "Item")));
        builder.addLoreLine(Component.literal("§7Posted by: §f" + blank(contract.ownerName)));
        builder.addLoreLine(Component.literal("§7Reward: §6" + EconomyManager.format(contract.rewardCents)));
        if ("COMPLETED".equalsIgnoreCase(contract.status)) {
            builder.addLoreLine(Component.literal("§aReady to claim."));
        } else if (canComplete) {
            builder.addLoreLine(Component.literal("§eClick to complete"));
            builder.setCallback((slot, click, action) -> {
                if ("POKEMON".equalsIgnoreCase(contract.type)) openPokemonSlots(player, contract.id);
                else PlayerContractService.completeItemContract(player, contract.id);
            });
        } else {
            builder.addLoreLine(Component.literal("§7Waiting for another player."));
        }
        return builder;
    }

    private static void openPokemonSlots(ServerPlayer player, UUID contractId) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x1, player);
        gui.setTitle(Component.literal("Choose Pokémon"));
        for (int i = 0; i < 6; i++) {
            int slotIndex = i;
            Pokemon pokemon = null;
            try { pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slotIndex); } catch (Exception ignored) {}
            if (pokemon == null) {
                gui.setSlot(i, new GuiElementBuilder(Items.GRAY_DYE).hideDefaultTooltip().setName(Component.literal("§7Empty Slot " + (i + 1))));
                continue;
            }
            gui.setSlot(i, pokemonSlotButton(pokemon, i + 1)
                    .addLoreLine(Component.literal("§eClick to complete contract"))
                    .setCallback((slot, click, action) -> PlayerContractService.completePokemonContract(player, contractId, slotIndex)));
        }
        gui.setSlot(8, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eBack"))
                .setCallback((slot, click, action) -> openBoard(player, "POKEMON")));
        gui.open();
    }

    private static GuiElementBuilder iconFor(ServerPlayer player, PlayerContractRepository.ContractSummary contract) {
        if ("POKEMON".equalsIgnoreCase(contract.type)) {
            return new GuiElementBuilder(CobblemonItems.POKE_BALL);
        }
        try {
            ItemStack stack = AuctionItemSerializer.fromPayload(player, contract.criteria);
            if (stack != null && !stack.isEmpty()) return new GuiElementBuilder(stack.copy()).setLore(new ArrayList<>());
        } catch (Exception ignored) {}
        return new GuiElementBuilder(Items.CHEST);
    }

    private static GuiElementBuilder pokemonSlotButton(Pokemon pokemon, int slotNumber) {
        return new GuiElementBuilder(CobblemonItems.POKE_BALL)
                .hideDefaultTooltip()
                .setName(Component.literal("§b" + pokemon.getDisplayName(true).getString()))
                .addLoreLine(Component.literal("§7Slot: §f" + slotNumber))
                .addLoreLine(Component.literal("§7Level: §f" + pokemon.getLevel()))
                .addLoreLine(Component.literal("§7Shiny: §f" + (pokemon.getShiny() ? "Yes" : "No")));
    }

    private static int[] boardSlots() {
        return new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    }

    private static String cleanTitle(String title) {
        return title == null || title.isBlank() ? "Player Contract" : title;
    }

    private static String blank(String text) {
        return text == null || text.isBlank() ? "Unknown" : text;
    }
}
