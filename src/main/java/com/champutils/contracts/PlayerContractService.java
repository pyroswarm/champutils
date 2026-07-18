package com.champutils.contracts;

import com.champutils.auction.AuctionItemSerializer;
import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.breeding.PokemonBreedability;
import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.network.NetworkEventManager;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.wiki.PokemonWikiIndex;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerContractService {
    private static final Map<UUID, PendingPokemonContract> PENDING_POKEMON_CONTRACTS = new ConcurrentHashMap<>();
    private static final java.util.Set<UUID> CONTRACT_MUTATIONS_IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private PlayerContractService() {}

    public static boolean consumeChatInput(ServerPlayer player, String rawMessage) {
        if (player == null) return false;
        PendingPokemonContract pending = PENDING_POKEMON_CONTRACTS.get(player.getUUID());
        if (pending == null) return false;
        String input = rawMessage == null ? "" : rawMessage.trim();
        if (input.equalsIgnoreCase("cancel")) {
            PENDING_POKEMON_CONTRACTS.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Pokémon contract creation cancelled.").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        if (input.isBlank()) {
            player.sendSystemMessage(Component.literal("Type a value, or type cancel.").withStyle(ChatFormatting.RED));
            return true;
        }

        if (pending.step == PendingStep.SPECIES) {
            if (!PokemonWikiIndex.knowsSpecies(input)) {
                player.sendSystemMessage(Component.literal("I could not find that Pokémon. Try the species name again, or type cancel.").withStyle(ChatFormatting.RED));
                return true;
            }
            pending.species = PokemonWikiIndex.displayName(input);
            pending.speciesKey = cleanSpecies(input);
            java.util.List<String> genders = PokemonWikiIndex.genderOptions(pending.speciesKey);
            if (genders.isEmpty()) {
                pending.gender = "";
                pending.step = PendingStep.ABILITY;
                PlayerContractMenu.openPokemonAbilityMenu(player, pending.species, PokemonWikiIndex.abilityNames(pending.speciesKey));
            } else {
                pending.step = PendingStep.GENDER;
                PlayerContractMenu.openPokemonGenderMenu(player, pending.species, genders);
            }
            return true;
        }

        if (pending.step == PendingStep.REWARD) {
            long rewardCents = parseRewardCents(input);
            if (rewardCents <= 0L) {
                player.sendSystemMessage(Component.literal("Type the Credits reward as a number, like 500 or 1250.50. Type cancel to stop.").withStyle(ChatFormatting.RED));
                return true;
            }
            PENDING_POKEMON_CONTRACTS.remove(player.getUUID());
            createPokemonContractFromCriteria(player, pending, rewardCents);
            return true;
        }

        player.sendSystemMessage(Component.literal("Use the open contract menu, or type cancel to stop.").withStyle(ChatFormatting.YELLOW));
        return true;
    }

    public static void beginPokemonContract(ServerPlayer player) {
        if (player == null) return;
        PENDING_POKEMON_CONTRACTS.put(player.getUUID(), new PendingPokemonContract());
        player.closeContainer();
        player.sendSystemMessage(Component.literal("Type the Pokémon species for the contract in chat. Example: Gible. Type cancel to stop.").withStyle(ChatFormatting.AQUA));
    }

    public static void cancelPending(ServerPlayer player) {
        if (player == null) return;
        PENDING_POKEMON_CONTRACTS.remove(player.getUUID());
    }

    public static void selectPokemonGender(ServerPlayer player, String gender) {
        PendingPokemonContract pending = pending(player);
        if (pending == null || pending.step != PendingStep.GENDER) return;

        String selected = normalizeGender(gender);
        java.util.List<String> legal = PokemonWikiIndex.genderOptions(pending.speciesKey);
        if (!selected.isBlank() && !selected.equals("any") && legal.stream().noneMatch(value -> normalizeGender(value).equals(selected))) {
            player.sendSystemMessage(Component.literal("That gender is not available for this Pokémon.").withStyle(ChatFormatting.RED));
            return;
        }

        pending.gender = selected.equals("any") ? "" : selected;
        pending.step = PendingStep.ABILITY;
        PlayerContractMenu.openPokemonAbilityMenu(player, pending.species, PokemonWikiIndex.abilityNames(pending.speciesKey));
    }

    public static void selectPokemonAbility(ServerPlayer player, String ability) {
        PendingPokemonContract pending = pending(player);
        if (pending == null || pending.step != PendingStep.ABILITY) return;

        if (!any(ability)) {
            String selected = cleanToken(ability);
            boolean legal = PokemonWikiIndex.abilityNames(pending.speciesKey).stream()
                    .map(PlayerContractService::cleanToken)
                    .anyMatch(selected::equals);
            if (!legal) {
                player.sendSystemMessage(Component.literal("That ability is not available for this Pokémon.").withStyle(ChatFormatting.RED));
                return;
            }
        }

        pending.ability = any(ability) ? "" : pretty(ability);
        pending.step = PendingStep.NATURE;
        PlayerContractMenu.openPokemonNatureMenu(player, pending.species);
    }

    public static void selectPokemonNature(ServerPlayer player, String nature) {
        PendingPokemonContract pending = pending(player);
        if (pending == null || pending.step != PendingStep.NATURE) return;
        pending.nature = any(nature) ? "" : pretty(nature);
        if (PokemonWikiIndex.isBreedableSpecies(pending.speciesKey)) {
            pending.step = PendingStep.BREEDABLE;
            PlayerContractMenu.openPokemonBreedableMenu(player, pending.species);
        } else {
            pending.breedable = null;
            moveToReward(player, pending);
        }
    }

    public static void selectPokemonBreedable(ServerPlayer player, String value) {
        PendingPokemonContract pending = pending(player);
        if (pending == null || pending.step != PendingStep.BREEDABLE) return;
        String selected = value == null ? "any" : value.trim().toLowerCase(Locale.ROOT);
        pending.breedable = switch (selected) {
            case "yes", "true" -> Boolean.TRUE;
            case "no", "false" -> Boolean.FALSE;
            default -> null;
        };
        moveToReward(player, pending);
    }

    private static void moveToReward(ServerPlayer player, PendingPokemonContract pending) {
        pending.step = PendingStep.REWARD;
        player.closeContainer();
        player.sendSystemMessage(Component.literal("Type the Credits reward for this contract. Example: 1000. Type cancel to stop.").withStyle(ChatFormatting.GOLD));
    }

    public static void createItemContractFromHand(ServerPlayer player, int rewardCredits) {
        if (player == null) return;
        ItemStack held = player.getMainHandItem();
        if (held == null || held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item stack you want to request, then pick a reward.").withStyle(ChatFormatting.RED));
            return;
        }
        long rewardCents = EconomyManager.wholeCreditsToCents(Math.max(1, rewardCredits));

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

        JsonObject criteria = new JsonObject();
        criteria.addProperty("species", speciesId(pokemon));
        criteria.addProperty("min_level", pokemon.getLevel());
        criteria.addProperty("max_level", 100);
        if (pokemon.getShiny()) criteria.addProperty("shiny", true);
        criteria.addProperty("breedable", PokemonBreedability.isBreedable(pokemon));
        String title = "Deliver " + pokemon.getDisplayName(true).getString();
        createContract(player, "POKEMON", title, rewardCents, criteria);
    }

    private static void createPokemonContractFromCriteria(ServerPlayer player, PendingPokemonContract pending, long rewardCents) {
        if (player == null || pending == null || pending.speciesKey == null || pending.speciesKey.isBlank()) return;
        JsonObject criteria = new JsonObject();
        criteria.addProperty("species", pending.speciesKey);
        if (pending.gender != null && !pending.gender.isBlank()) criteria.addProperty("gender", normalizeGender(pending.gender));
        if (pending.nature != null && !pending.nature.isBlank()) criteria.addProperty("nature", cleanToken(pending.nature));
        if (pending.ability != null && !pending.ability.isBlank()) criteria.addProperty("ability", cleanToken(pending.ability));
        if (pending.breedable != null) criteria.addProperty("breedable", pending.breedable);

        StringBuilder title = new StringBuilder("Deliver ");
        if (pending.gender != null && !pending.gender.isBlank()) title.append(pretty(pending.gender)).append(" ");
        if (pending.nature != null && !pending.nature.isBlank()) title.append(pending.nature).append(" ");
        if (pending.ability != null && !pending.ability.isBlank()) title.append(pending.ability).append(" ");
        title.append(pending.species == null || pending.species.isBlank() ? PokemonWikiIndex.displayName(pending.speciesKey) : pending.species);
        createContract(player, "POKEMON", title.toString(), rewardCents, criteria);
    }

    private static void createContract(ServerPlayer player, String type, String title, long rewardCents, JsonObject criteria) {
        if (player == null || rewardCents <= 0L) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) {
            player.sendSystemMessage(Component.literal("Select a profile first.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!CONTRACT_MUTATIONS_IN_FLIGHT.add(profileId)) {
            player.sendSystemMessage(Component.literal("Another contract change is already processing.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        String ownerName = player.getGameProfile().getName();
        String safeType = type == null ? "ITEM" : type.toUpperCase(Locale.ROOT);
        String safeTitle = title == null || title.isBlank() ? "Player Contract" : title;
        UUID requestId = UUID.randomUUID();
        UUID chargeId = EconomyManager.operationId("contract-create-charge", profileId, requestId);
        UUID refundId = EconomyManager.operationId("contract-create-refund", profileId, requestId);

        player.sendSystemMessage(Component.literal("Creating contract...").withStyle(ChatFormatting.YELLOW));
        EconomyManager.withdrawAsync(chargeId, profileId, ownerName, rewardCents,
                "create_player_contract:" + safeType.toLowerCase(Locale.ROOT)).thenCompose(withdrawn -> {
            if (!withdrawn.success) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        ContractCreateResult.fail(withdrawn.error == null ? "You do not have enough Credits." : withdrawn.error));
            }
            return DatabaseManager.supplyAsync("create paid player contract", connection -> {
                UUID contractId = PlayerContractRepository.createContract(profileId, ownerName, safeType, safeTitle, rewardCents, criteria);
                return contractId == null ? ContractCreateResult.fail("Contract insert returned no id.") : ContractCreateResult.success(contractId);
            }).handle((created, error) -> {
                if (error != null || created == null || created.contractId() == null) {
                    return EconomyManager.depositAsync(refundId, profileId, ownerName, rewardCents,
                            "refund_failed_player_contract:" + requestId).thenApply(refund ->
                            ContractCreateResult.fail(refund.success
                                    ? "Could not create that contract. Your Credits were refunded."
                                    : "Contract creation failed and the automatic refund failed. Give staff request ID " + requestId + "."));
                }
                return java.util.concurrent.CompletableFuture.completedFuture(created);
            }).thenCompose(future -> future);
        }).whenComplete((result, error) -> player.server.execute(() -> {
            CONTRACT_MUTATIONS_IN_FLIGHT.remove(profileId);
            if (error != null || result == null || result.contractId() == null) {
                String message = result != null && result.error() != null && !result.error().isBlank()
                        ? result.error() : "Could not create that contract.";
                player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }
            player.sendSystemMessage(Component.literal("Contract created. The reward is held until another player completes it.").withStyle(ChatFormatting.GREEN));
            announcePlayerContractCreated(player, safeType, safeTitle, rewardCents);
        }));
    }

    /**
     * Only contracts authored by a player are announced. Adventure Guide contracts remain
     * private in QuestManager, and completions/cancellations do not call this path.
     */
    private static void announcePlayerContractCreated(ServerPlayer creator, String type, String title, long rewardCents) {
        if (creator == null || creator.server == null) return;
        String kind = "POKEMON".equalsIgnoreCase(type) ? "Pokémon" : "item";
        String cleanTitle = title == null || title.isBlank() ? "Player Contract" : title.trim();
        if (cleanTitle.length() > 96) cleanTitle = cleanTitle.substring(0, 96) + "...";

        Component localMessage = Component.literal("[Contract] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(creator.getName().getString()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" posted a player " + kind + " contract: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(cleanTitle).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" • Reward: " + EconomyManager.format(rewardCents) + " • Open the Adventure Guide contract board to view.").withStyle(ChatFormatting.GRAY));

        for (ServerPlayer recipient : creator.server.getPlayerList().getPlayers()) {
            if (ProfessionNotificationSettings.areBroadcastMessagesEnabled(recipient)) {
                recipient.sendSystemMessage(localMessage);
            }
        }

        NetworkEventManager.publishBroadcastText(
                "[Contract] " + creator.getName().getString()
                        + " posted a player " + kind + " contract: " + cleanTitle
                        + " • Reward: " + EconomyManager.format(rewardCents)
                        + " • Open the Adventure Guide contract board to view."
        );
    }

    public static void cancelContract(ServerPlayer player, UUID contractId) {
        if (player == null || contractId == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        if (!CONTRACT_MUTATIONS_IN_FLIGHT.add(profileId)) {
            player.sendSystemMessage(Component.literal("Another contract change is already processing.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        String ownerName = player.getGameProfile().getName();
        player.sendSystemMessage(Component.literal("Cancelling contract...").withStyle(ChatFormatting.YELLOW));

        DatabaseManager.supplyAsync("cancel player contract", connection ->
                PlayerContractRepository.cancelActiveContract(contractId, profileId)).thenCompose(cancelled -> {
            if (cancelled == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(
                        ContractCancelResult.fail("That contract is no longer active, or it is not yours."));
            }
            UUID refundId = EconomyManager.operationId("contract-cancel-refund", contractId, profileId);
            return EconomyManager.depositAsync(refundId, profileId, ownerName, cancelled.rewardCents,
                    "cancel_player_contract:" + contractId).thenApply(refund -> refund.success
                    ? ContractCancelResult.success(cancelled.title, cancelled.rewardCents)
                    : ContractCancelResult.fail("Contract was cancelled, but the refund failed. Ask staff to review contract " + contractId + "."));
        }).whenComplete((result, error) -> player.server.execute(() -> {
            CONTRACT_MUTATIONS_IN_FLIGHT.remove(profileId);
            if (error != null || result == null || !result.success()) {
                String message = result != null && result.error() != null && !result.error().isBlank()
                        ? result.error() : "Could not cancel that contract.";
                player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }
            player.sendSystemMessage(Component.literal("Cancelled contract and refunded " + EconomyManager.format(result.rewardCents()) + ".").withStyle(ChatFormatting.GREEN));
            PlayerContractMenu.open(player);
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
        EconomyManager.depositAsync(EconomyManager.operationId("contract-completion-reward", contract.id, PlayerProfileManager.activeProfileId(player)), player, contract.rewardCents, "Player contract " + contract.id).thenAccept(result ->
                player.server.execute(() -> {
                    if (result.success) player.sendSystemMessage(Component.literal("Earned " + EconomyManager.format(contract.rewardCents) + ".").withStyle(ChatFormatting.GOLD));
                }));
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
        Boolean breedable = bool(criteria, "breedable");
        if (breedable != null && breedable != PokemonBreedability.isBreedable(pokemon)) return false;
        Integer minLevel = integer(criteria, "min_level", "minLevel");
        if (minLevel != null && pokemon.getLevel() < minLevel) return false;
        Integer maxLevel = integer(criteria, "max_level", "maxLevel");
        if (maxLevel != null && pokemon.getLevel() > maxLevel) return false;
        String gender = string(criteria, "gender");
        if (gender != null && !gender.isBlank() && !containsClean(String.valueOf(pokemon.getGender()), gender)) return false;
        String nature = string(criteria, "nature");
        if (nature != null && !nature.isBlank() && !containsClean(readNature(pokemon), nature)) return false;
        String ability = string(criteria, "ability");
        if (ability != null && !ability.isBlank() && !containsClean(readAbility(pokemon), ability)) return false;
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
        return cleaned.replace("-", "_").replace(" ", "_").replaceAll("[^a-z0-9_]", "");
    }

    private static String readNature(Pokemon pokemon) {
        try {
            Object nature = pokemon.getNature();
            return nature == null ? "" : nature.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readAbility(Pokemon pokemon) {
        try {
            Object ability = pokemon.getAbility();
            if (ability == null) return "";
            try {
                Object name = ability.getClass().getMethod("getName").invoke(ability);
                if (name != null) return name.toString();
            } catch (Throwable ignored) {}
            return ability.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalizeGender(String value) {
        if (value == null) return "";
        String normalized = cleanToken(value);
        if (normalized.contains("female")) return "female";
        if (normalized.contains("male")) return "male";
        if (normalized.contains("genderless") || normalized.contains("none") || normalized.contains("unknown")) return "genderless";
        if (normalized.equals("any") || normalized.isBlank()) return "any";
        return normalized;
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

    private static PendingPokemonContract pending(ServerPlayer player) {
        if (player == null) return null;
        PendingPokemonContract pending = PENDING_POKEMON_CONTRACTS.get(player.getUUID());
        if (pending == null) {
            player.sendSystemMessage(Component.literal("That contract setup expired. Start again from Player Contracts.").withStyle(ChatFormatting.RED));
        }
        return pending;
    }

    private static boolean any(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("any") || value.equalsIgnoreCase("any nature") || value.equalsIgnoreCase("any ability");
    }

    private static String pretty(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String cleaned = raw.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder out = new StringBuilder();
        for (String part : cleaned.split("\\s+")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            String lower = part.toLowerCase(Locale.ROOT);
            out.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) out.append(lower.substring(1));
        }
        return out.toString();
    }

    private static String cleanToken(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static long parseRewardCents(String input) {
        if (input == null) return 0L;
        String cleaned = input.toLowerCase(Locale.ROOT)
                .replace("credits", "")
                .replace("credit", "")
                .replace(",", "")
                .trim();
        cleaned = cleaned.replaceAll("[^0-9.]", "");
        if (cleaned.isBlank()) return 0L;
        try {
            return EconomyManager.creditsToCents(Double.parseDouble(cleaned));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private record ContractCreateResult(UUID contractId, String error) {
        static ContractCreateResult success(UUID contractId) { return new ContractCreateResult(contractId, null); }
        static ContractCreateResult fail(String error) { return new ContractCreateResult(null, error); }
    }

    private record ContractCancelResult(boolean success, String title, long rewardCents, String error) {
        static ContractCancelResult success(String title, long rewardCents) { return new ContractCancelResult(true, title, rewardCents, null); }
        static ContractCancelResult fail(String error) { return new ContractCancelResult(false, null, 0L, error); }
    }

    private enum PendingStep {
        SPECIES,
        GENDER,
        NATURE,
        ABILITY,
        BREEDABLE,
        REWARD
    }

    private static final class PendingPokemonContract {
        private PendingStep step = PendingStep.SPECIES;
        private String speciesKey = "";
        private String species = "";
        private String gender = "";
        private String nature = "";
        private String ability = "";
        private Boolean breedable = null;
    }
}
