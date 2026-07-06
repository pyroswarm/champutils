package com.champutils.contracts;

import com.champutils.auction.AuctionItemSerializer;
import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.network.NetworkEventManager;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.UUID;

public final class PlayerContractService {
    private PlayerContractService() {}

    public static void createItemContractFromHand(ServerPlayer player, int rewardCredits) {
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item stack you want to request, then pick a reward.").withStyle(ChatFormatting.RED));
            return;
        }
        long rewardCents = EconomyManager.wholeCreditsToCents(Math.max(1, rewardCredits));
        EconomyManager.TransactionResult withdrawn = EconomyManager.withdraw(player, rewardCents, "create_player_item_contract");
        if (!withdrawn.success) {
            player.sendSystemMessage(Component.literal(withdrawn.error == null ? "Not enough Credits." : withdrawn.error).withStyle(ChatFormatting.RED));
            return;
        }

        JsonObject criteria = new JsonObject();
        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        criteria.addProperty("item_id", itemId);
        criteria.addProperty("count", Math.max(1, held.getCount()));
        String title = "Deliver " + held.getCount() + " " + held.getHoverName().getString();
        createContract(player, "ITEM", title, rewardCents, criteria);
    }

    public static void createPokemonContractFromSlot(ServerPlayer player, int partySlotIndex, int rewardCredits) {
        if (player == null) return;
        Pokemon pokemon;
        try {
            pokemon = AuctionPokemonSerializer.getPartyPokemon(player, partySlotIndex);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not read that party slot.").withStyle(ChatFormatting.RED));
            return;
        }
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("That party slot is empty.").withStyle(ChatFormatting.RED));
            return;
        }
        long rewardCents = EconomyManager.wholeCreditsToCents(Math.max(1, rewardCredits));
        EconomyManager.TransactionResult withdrawn = EconomyManager.withdraw(player, rewardCents, "create_player_pokemon_contract");
        if (!withdrawn.success) {
            player.sendSystemMessage(Component.literal(withdrawn.error == null ? "Not enough Credits." : withdrawn.error).withStyle(ChatFormatting.RED));
            return;
        }

        JsonObject criteria = new JsonObject();
        criteria.addProperty("species", speciesId(pokemon));
        criteria.addProperty("min_level", pokemon.getLevel());
        criteria.addProperty("max_level", 100);
        if (pokemon.getShiny()) criteria.addProperty("shiny", true);
        String title = "Deliver " + pokemon.getDisplayName(true).getString();
        createContract(player, "POKEMON", title, rewardCents, criteria);
    }

    private static void createContract(ServerPlayer player, String type, String title, long rewardCents, JsonObject criteria) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        String ownerName = player.getGameProfile().getName();
        DatabaseManager.supplyAsync("create player contract", connection -> PlayerContractRepository.createContract(profileId, ownerName, type, title, rewardCents, criteria))
                .whenComplete((contractId, error) -> player.server.execute(() -> {
                    if (error != null || contractId == null) {
                        EconomyManager.deposit(player, rewardCents, "refund_failed_player_contract");
                        player.sendSystemMessage(Component.literal("Could not create that contract. Your Credits were refunded.").withStyle(ChatFormatting.RED));
                        if (error != null) error.printStackTrace();
                        return;
                    }
                    String line = "§6§l[Player Contracts] §e" + ownerName
                            + " §fcreated a " + ("POKEMON".equalsIgnoreCase(type) ? "Pokémon" : "item")
                            + " contract: §b" + title
                            + " §8| §7Reward: §6" + EconomyManager.format(rewardCents);
                    player.server.getPlayerList().broadcastSystemMessage(Component.literal(line), false);
                    NetworkEventManager.publishBroadcastText(line);
                    player.sendSystemMessage(Component.literal("Contract created. The reward is held until another player completes it.").withStyle(ChatFormatting.GREEN));
                }));
    }

    public static void completeItemContract(ServerPlayer player, UUID contractId) {
        if (player == null || contractId == null) return;
        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the requested item stack, then click the contract again.").withStyle(ChatFormatting.RED));
            return;
        }

        DatabaseManager.supplyAsync("load player item contract", connection -> PlayerContractRepository.fetchActiveContract(contractId))
                .whenComplete((contract, error) -> player.server.execute(() -> {
                    if (error != null || contract == null || !"ITEM".equalsIgnoreCase(contract.type)) {
                        player.sendSystemMessage(Component.literal("That item contract is no longer available.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    if (!matchesItem(contract.criteria, held)) {
                        player.sendSystemMessage(Component.literal("That item does not match this contract.").withStyle(ChatFormatting.RED));
                        return;
                    }

                    int required = requiredCount(contract.criteria, held.getCount());
                    if (held.getCount() < required) {
                        player.sendSystemMessage(Component.literal("You need " + required + " items for this contract.").withStyle(ChatFormatting.RED));
                        return;
                    }

                    ItemStack payment = held.copy();
                    payment.setCount(required);
                    JsonObject payload;
                    try {
                        payload = AuctionItemSerializer.toPayload(player, payment);
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.literal("Could not store that item safely. Nothing was removed.").withStyle(ChatFormatting.RED));
                        e.printStackTrace();
                        return;
                    }

                    held.shrink(required);
                    DatabaseManager.supplyAsync("complete player item contract", connection -> PlayerContractRepository.completeContract(
                            contract.id,
                            PlayerProfileManager.activeProfileId(player),
                            player.getName().getString(),
                            payload
                    )).whenComplete((completed, completeError) -> player.server.execute(() -> {
                        if (completeError != null || !Boolean.TRUE.equals(completed)) {
                            player.getInventory().add(payment.copy());
                            player.sendSystemMessage(Component.literal("Could not complete that contract. Your item was returned.").withStyle(ChatFormatting.RED));
                            if (completeError != null) completeError.printStackTrace();
                            return;
                        }
                        payCompleter(player, contract);
                        player.sendSystemMessage(Component.literal("Contract completed.").withStyle(ChatFormatting.GREEN));
                    }));
                }));
    }

    public static void completePokemonContract(ServerPlayer player, UUID contractId, int partySlotIndex) {
        if (player == null || contractId == null) return;
        Pokemon pokemon;
        try {
            pokemon = AuctionPokemonSerializer.getPartyPokemon(player, partySlotIndex);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not read that party slot.").withStyle(ChatFormatting.RED));
            return;
        }
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("That party slot is empty.").withStyle(ChatFormatting.RED));
            return;
        }

        DatabaseManager.supplyAsync("load player pokemon contract", connection -> PlayerContractRepository.fetchActiveContract(contractId))
                .whenComplete((contract, error) -> player.server.execute(() -> {
                    if (error != null || contract == null || !"POKEMON".equalsIgnoreCase(contract.type)) {
                        player.sendSystemMessage(Component.literal("That Pokémon contract is no longer available.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    Pokemon latest = AuctionPokemonSerializer.getPartyPokemon(player, partySlotIndex);
                    if (latest == null || !latest.getUuid().equals(pokemon.getUuid())) {
                        player.sendSystemMessage(Component.literal("That party slot changed. Try again.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    if (!matchesPokemon(contract.criteria, latest)) {
                        player.sendSystemMessage(Component.literal("That Pokémon does not match this contract.").withStyle(ChatFormatting.RED));
                        return;
                    }

                    JsonObject payload;
                    try {
                        payload = AuctionPokemonSerializer.toPayload(player, latest);
                        AuctionPokemonSerializer.clearPartySlot(player, partySlotIndex);
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.literal("Could not safely store/remove that Pokémon. Nothing was completed.").withStyle(ChatFormatting.RED));
                        e.printStackTrace();
                        return;
                    }

                    DatabaseManager.supplyAsync("complete player pokemon contract", connection -> PlayerContractRepository.completeContract(
                            contract.id,
                            PlayerProfileManager.activeProfileId(player),
                            player.getName().getString(),
                            payload
                    )).whenComplete((completed, completeError) -> player.server.execute(() -> {
                        if (completeError != null || !Boolean.TRUE.equals(completed)) {
                            try {
                                Pokemon restored = AuctionPokemonSerializer.fromPayload(player, payload);
                                AuctionPokemonSerializer.deliverToPartyOrPc(player, restored);
                            } catch (Exception restoreError) {
                                restoreError.printStackTrace();
                            }
                            player.sendSystemMessage(Component.literal("Could not complete that contract. Your Pokémon was returned.").withStyle(ChatFormatting.RED));
                            if (completeError != null) completeError.printStackTrace();
                            return;
                        }
                        payCompleter(player, contract);
                        player.sendSystemMessage(Component.literal("Contract completed.").withStyle(ChatFormatting.GREEN));
                    }));
                }));
    }

    public static void claimNext(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        DatabaseManager.supplyAsync("load completed player contract", connection -> PlayerContractRepository.fetchOldestCompletedForOwner(profileId))
                .whenComplete((contract, error) -> player.server.execute(() -> {
                    if (error != null) {
                        player.sendSystemMessage(Component.literal("Could not check completed contracts. Try again in a moment.").withStyle(ChatFormatting.RED));
                        error.printStackTrace();
                        return;
                    }
                    if (contract == null) {
                        player.sendSystemMessage(Component.literal("You have no completed contracts to claim.").withStyle(ChatFormatting.YELLOW));
                        return;
                    }
                    if ("POKEMON".equalsIgnoreCase(contract.type)) claimPokemon(player, contract, profileId);
                    else claimItem(player, contract, profileId);
                }));
    }

    private static void claimItem(ServerPlayer player, PlayerContractRepository.ContractSummary contract, UUID profileId) {
        ItemStack stack;
        try {
            stack = AuctionItemSerializer.fromPayload(player, contract.completionPayload);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not restore that contract item. Ask staff to review it.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (stack == null || stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("That contract item is missing. Ask staff to review it.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!canFit(player, stack)) {
            player.sendSystemMessage(Component.literal("Make room in your inventory, then click Claim Contract again.").withStyle(ChatFormatting.RED));
            return;
        }
        DatabaseManager.supplyAsync("mark player contract claimed", connection -> PlayerContractRepository.markClaimed(contract.id, profileId))
                .whenComplete((marked, error) -> player.server.execute(() -> {
                    if (error != null || !Boolean.TRUE.equals(marked)) {
                        player.sendSystemMessage(Component.literal("Could not close that contract. Nothing was claimed.").withStyle(ChatFormatting.RED));
                        if (error != null) error.printStackTrace();
                        return;
                    }
                    if (!player.getInventory().add(stack.copy())) {
                        player.sendSystemMessage(Component.literal("Contract closed, but the item could not fit. Contact staff.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    player.sendSystemMessage(Component.literal("Claimed contract item: " + contract.title).withStyle(ChatFormatting.GREEN));
                }));
    }

    private static void claimPokemon(ServerPlayer player, PlayerContractRepository.ContractSummary contract, UUID profileId) {
        Pokemon pokemon;
        try {
            pokemon = AuctionPokemonSerializer.fromPayload(player, contract.completionPayload);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not restore that contract Pokémon. Ask staff to review it.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        DatabaseManager.supplyAsync("mark player pokemon contract claimed", connection -> PlayerContractRepository.markClaimed(contract.id, profileId))
                .whenComplete((marked, error) -> player.server.execute(() -> {
                    if (error != null || !Boolean.TRUE.equals(marked)) {
                        player.sendSystemMessage(Component.literal("Could not close that contract. Nothing was claimed.").withStyle(ChatFormatting.RED));
                        if (error != null) error.printStackTrace();
                        return;
                    }
                    AuctionPokemonSerializer.DeliveryResult delivery = AuctionPokemonSerializer.deliverToPartyOrPc(player, pokemon);
                    if (delivery == AuctionPokemonSerializer.DeliveryResult.FAILED) {
                        player.sendSystemMessage(Component.literal("Contract closed, but the Pokémon could not be delivered. Contact staff.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    String suffix = delivery == AuctionPokemonSerializer.DeliveryResult.PC ? " Your party was full, so it was sent to your PC." : "";
                    player.sendSystemMessage(Component.literal("Claimed contract Pokémon: " + contract.title + "." + suffix).withStyle(ChatFormatting.GREEN));
                }));
    }

    private static void payCompleter(ServerPlayer player, PlayerContractRepository.ContractSummary contract) {
        if (contract == null || contract.rewardCents <= 0L) return;
        EconomyManager.TransactionResult result = EconomyManager.deposit(player, contract.rewardCents, "Player contract " + contract.id);
        if (result.success) {
            player.sendSystemMessage(Component.literal("Earned " + EconomyManager.format(contract.rewardCents) + ".").withStyle(ChatFormatting.GOLD));
        }
    }

    private static boolean matchesItem(JsonObject criteria, ItemStack stack) {
        String requiredId = string(criteria, "item_id", "itemId", "item");
        if (requiredId == null || requiredId.isBlank()) return false;
        String actualId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return requiredId.equalsIgnoreCase(actualId);
    }

    private static int requiredCount(JsonObject criteria, int fallback) {
        for (String key : new String[] {"count", "amount", "quantity"}) {
            try {
                if (criteria != null && criteria.has(key)) return Math.max(1, criteria.get(key).getAsInt());
            } catch (Exception ignored) {}
        }
        return Math.max(1, fallback);
    }

    private static boolean matchesPokemon(JsonObject criteria, Pokemon pokemon) {
        if (criteria == null || pokemon == null) return false;
        String species = string(criteria, "species", "pokemon", "species_id");
        if (species != null && !species.isBlank() && !cleanSpecies(species).equals(cleanSpecies(speciesId(pokemon)))) return false;
        Boolean shiny = bool(criteria, "shiny");
        if (shiny != null && shiny != pokemon.getShiny()) return false;
        Integer minLevel = integer(criteria, "min_level", "minLevel");
        if (minLevel != null && pokemon.getLevel() < minLevel) return false;
        Integer maxLevel = integer(criteria, "max_level", "maxLevel");
        if (maxLevel != null && pokemon.getLevel() > maxLevel) return false;
        String gender = string(criteria, "gender");
        if (gender != null && !gender.isBlank() && !containsClean(String.valueOf(pokemon.getGender()), gender)) return false;
        return true;
    }

    private static String speciesId(Pokemon pokemon) {
        try { return pokemon.getSpecies().getResourceIdentifier().toString(); }
        catch (Exception ignored) { return ""; }
    }

    private static String cleanSpecies(String value) {
        if (value == null) return "";
        String cleaned = value.toLowerCase(Locale.ROOT).trim();
        int colon = cleaned.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < cleaned.length()) cleaned = cleaned.substring(colon + 1);
        return cleaned.replace(" ", "_");
    }

    private static boolean containsClean(String actual, String expected) {
        String a = actual == null ? "" : actual.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        String e = expected == null ? "" : expected.toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
        return !e.isBlank() && a.contains(e);
    }

    private static boolean canFit(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        int remaining = stack.getCount();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack current = player.getInventory().getItem(i);
            if (current == null || current.isEmpty()) return true;
            if (!ItemStack.isSameItemSameComponents(current, stack)) continue;
            remaining -= Math.max(0, current.getMaxStackSize() - current.getCount());
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static String string(JsonObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            try {
                if (object.has(key) && !object.get(key).isJsonNull()) return object.get(key).getAsString();
            } catch (Exception ignored) {}
        }
        return "";
    }

    private static Boolean bool(JsonObject object, String key) {
        try {
            if (object == null || !object.has(key)) return null;
            return object.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Integer integer(JsonObject object, String... keys) {
        for (String key : keys) {
            try {
                if (object != null && object.has(key)) return object.get(key).getAsInt();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
