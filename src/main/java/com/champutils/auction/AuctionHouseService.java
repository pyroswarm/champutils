package com.champutils.auction;

import com.champutils.economy.EconomyManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import com.champutils.notifications.NotificationRepository;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionHouseService {

    private static final Set<UUID> CLAIMING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> LISTING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> CANCELING = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> BUYING = ConcurrentHashMap.newKeySet();

    private AuctionHouseService() {}

    public static void beginHeldItemListing(ServerPlayer player, long price) {
        if (player == null) return;
        if (!validatePrice(player, price)) return;
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held == null || held.isEmpty()) {
            player.sendSystemMessage(Component.literal("Hold the item stack you want to list, then try again.").withStyle(ChatFormatting.RED));
            return;
        }
        AuctionPendingActionManager.setItemListing(player, held.copy(), price);
    }

    public static void beginPokemonListing(ServerPlayer player, int playerSlotNumber, long price) {
        if (player == null) return;
        if (!validatePrice(player, price)) return;
        int slotIndex = playerSlotNumber - 1;
        if (slotIndex < 0 || slotIndex > 5) {
            player.sendSystemMessage(Component.literal("Party slot must be 1-6.").withStyle(ChatFormatting.RED));
            return;
        }
        Pokemon pokemon;
        try {
            pokemon = AuctionPokemonSerializer.getPartyPokemon(player, slotIndex);
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not read your party slot. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (pokemon == null) {
            player.sendSystemMessage(Component.literal("There is no Pokémon in party slot " + playerSlotNumber + ".").withStyle(ChatFormatting.RED));
            return;
        }
        AuctionPendingActionManager.setPokemonListing(player, slotIndex, pokemon.getDisplayName(true).getString(), price);
    }

    public static void confirmPending(ServerPlayer player) {
        AuctionPendingActionManager.PendingAction action = AuctionPendingActionManager.get(player);
        if (action == null) {
            player.sendSystemMessage(Component.literal("You do not have an auction action waiting for confirmation.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (action.type == AuctionPendingActionManager.Type.ITEM_LISTING) {
            confirmItemListing(player, action);
        } else if (action.type == AuctionPendingActionManager.Type.POKEMON_LISTING) {
            confirmPokemonListing(player, action);
        }
    }

    public static void cancelPending(ServerPlayer player) {
        AuctionPendingActionManager.PendingAction removed = AuctionPendingActionManager.remove(player);
        if (removed == null) {
            player.sendSystemMessage(Component.literal("You do not have a pending auction action.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        player.sendSystemMessage(Component.literal("Auction action canceled.").withStyle(ChatFormatting.YELLOW));
    }

    private static void confirmItemListing(ServerPlayer player, AuctionPendingActionManager.PendingAction action) {
        if (!LISTING.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have an auction listing in progress.").withStyle(ChatFormatting.RED));
            return;
        }

        AuctionHouseConfig config = AuctionHouseConfig.get();
        int maxSlots = config.safeMaxActiveListingsPerPlayer();
        ItemStack currentHeld = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (currentHeld == null || currentHeld.isEmpty() || !ItemStack.isSameItemSameComponents(currentHeld, action.itemSnapshot) || currentHeld.getCount() < action.itemSnapshot.getCount()) {
            LISTING.remove(player.getUUID());
            AuctionPendingActionManager.remove(player);
            player.sendSystemMessage(Component.literal("Listing canceled because the held item changed before confirmation.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            int activeListings = AuctionHouseRepository.countActiveListings(player.getUUID());
            if (activeListings >= maxSlots) {
                LISTING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("You have reached your auction slot limit: " + activeListings + "/" + maxSlots + ".").withStyle(ChatFormatting.RED));
                return;
            }
        } catch (Exception e) {
            LISTING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not verify your auction slot limit. Try again later.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        ItemStack listedStack = action.itemSnapshot.copy();
        currentHeld.shrink(listedStack.getCount());
        if (currentHeld.isEmpty()) player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        AuctionPendingActionManager.remove(player);
        player.sendSystemMessage(Component.literal("Creating auction listing...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject payload = AuctionItemSerializer.toPayload(player, listedStack);
                return AuctionHouseRepository.createItemListing(player.getUUID(), player.getName().getString(), listedStack.getHoverName().getString(), "Listed in-game by " + player.getName().getString(), action.price, listedStack.getCount(), payload, config.safeListingDurationDays());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((listingId, error) -> player.server.execute(() -> {
            LISTING.remove(player.getUUID());
            if (error != null) {
                giveOrDrop(player, listedStack);
                player.sendSystemMessage(Component.literal("Auction listing failed. Your item was returned.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            player.sendSystemMessage(Component.literal("Listed " + listedStack.getHoverName().getString() + " for " + EconomyManager.format(action.price) + ".").withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("Listing ID: " + listingId).withStyle(ChatFormatting.DARK_GRAY));
        }));
    }

    private static void confirmPokemonListing(ServerPlayer player, AuctionPendingActionManager.PendingAction action) {
        if (!LISTING.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have an auction listing in progress.").withStyle(ChatFormatting.RED));
            return;
        }

        AuctionHouseConfig config = AuctionHouseConfig.get();
        int maxSlots = config.safeMaxActiveListingsPerPlayer();

        Pokemon pokemon;
        try {
            pokemon = AuctionPokemonSerializer.getPartyPokemon(player, action.partySlotIndex);
        } catch (Exception e) {
            LISTING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not read your Pokémon before listing. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        if (pokemon == null || !pokemon.getDisplayName(true).getString().equals(action.pokemonName)) {
            LISTING.remove(player.getUUID());
            AuctionPendingActionManager.remove(player);
            player.sendSystemMessage(Component.literal("Listing canceled because the Pokémon slot changed before confirmation.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            int activeListings = AuctionHouseRepository.countActiveListings(player.getUUID());
            if (activeListings >= maxSlots) {
                LISTING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("You have reached your auction slot limit: " + activeListings + "/" + maxSlots + ".").withStyle(ChatFormatting.RED));
                return;
            }
        } catch (Exception e) {
            LISTING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not verify your auction slot limit. Try again later.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        JsonObject payload;
        String title = pokemon.getDisplayName(true).getString();
        try {
            payload = AuctionPokemonSerializer.toPayload(player, pokemon);
            AuctionPokemonSerializer.clearPartySlot(player, action.partySlotIndex);
        } catch (Exception e) {
            LISTING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not safely serialize/remove that Pokémon. Nothing was listed.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        AuctionPendingActionManager.remove(player);
        player.sendSystemMessage(Component.literal("Creating Pokémon auction listing...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.supplyAsync(() -> {
            try {
                return AuctionHouseRepository.createPokemonListing(player.getUUID(), player.getName().getString(), title, "Pokémon listed in-game by " + player.getName().getString(), action.price, payload, config.safeListingDurationDays());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((listingId, error) -> player.server.execute(() -> {
            LISTING.remove(player.getUUID());
            if (error != null) {
                try {
                    Pokemon restored = AuctionPokemonSerializer.fromPayload(player, payload);
                    if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, restored)) {
                        player.sendSystemMessage(Component.literal("Listing failed and your party is full. Contact an admin before relogging.").withStyle(ChatFormatting.RED));
                    } else {
                        player.sendSystemMessage(Component.literal("Listing failed. Your Pokémon was returned to your party.").withStyle(ChatFormatting.RED));
                    }
                } catch (Exception restoreError) {
                    player.sendSystemMessage(Component.literal("Listing failed and Pokémon restore failed. Contact an admin immediately.").withStyle(ChatFormatting.RED));
                    restoreError.printStackTrace();
                }
                error.printStackTrace();
                return;
            }
            player.sendSystemMessage(Component.literal("Listed " + title + " for " + EconomyManager.format(action.price) + ".").withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("Listing ID: " + listingId).withStyle(ChatFormatting.DARK_GRAY));
        }));
    }




    public static void buyListing(ServerPlayer player, UUID listingId) {
        if (player == null || listingId == null) return;
        if (!BUYING.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have an auction purchase in progress.").withStyle(ChatFormatting.RED));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.fetchActiveListing(listingId); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((listing, loadError) -> player.server.execute(() -> {
            if (loadError != null) {
                BUYING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Could not load that auction listing. Check console.").withStyle(ChatFormatting.RED));
                loadError.printStackTrace();
                return;
            }
            if (listing == null) {
                BUYING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("That auction is no longer active.").withStyle(ChatFormatting.YELLOW));
                return;
            }
            if (player.getUUID().equals(listing.sellerUuid)) {
                BUYING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("You cannot buy your own auction. Use cancel instead.").withStyle(ChatFormatting.RED));
                return;
            }
            if (!EconomyManager.canAfford(player, listing.price)) {
                BUYING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("You cannot afford this listing. Price: " + EconomyManager.format(listing.price) + ".").withStyle(ChatFormatting.RED));
                return;
            }

            if ("POKEMON".equalsIgnoreCase(listing.kind)) {
                buyPokemonListing(player, listing);
            } else {
                buyItemListing(player, listing);
            }
        }));
    }

    private static void buyItemListing(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing) {
        ItemStack stack;
        try { stack = AuctionItemSerializer.fromPayload(player, listing.payload); }
        catch (Exception e) {
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not read that item listing. Nothing was purchased.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (stack == null || stack.isEmpty()) {
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That item listing has an empty payload. Nothing was purchased.").withStyle(ChatFormatting.RED));
            return;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your inventory is full. Clear one empty slot, then buy again.").withStyle(ChatFormatting.RED));
            return;
        }

        finishPurchase(player, listing, () -> giveOrDrop(player, stack), "Purchased auction item: " + listing.title + ".");
    }

    private static void buyPokemonListing(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing) {
        Pokemon pokemon;
        try { pokemon = AuctionPokemonSerializer.fromPayload(player, listing.payload); }
        catch (Exception e) {
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not read that Pokémon listing. Nothing was purchased.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (!AuctionPokemonSerializer.hasOpenPartySlot(player)) {
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your party is full. Clear one party slot, then buy again.").withStyle(ChatFormatting.RED));
            return;
        }

        finishPurchase(player, listing, () -> {
            if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, pokemon)) {
                player.sendSystemMessage(Component.literal("Purchase completed, but your party filled before delivery. Contact an admin immediately.").withStyle(ChatFormatting.RED));
            }
        }, "Purchased auction Pokémon: " + listing.title + ".");
    }

    private static void finishPurchase(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing, Runnable deliver, String successMessage) {
        EconomyManager.TransactionResult withdraw = EconomyManager.withdraw(player, listing.price, "Auction purchase " + listing.id);
        if (!withdraw.success) {
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(withdraw.error == null ? "You cannot afford this listing." : withdraw.error).withStyle(ChatFormatting.RED));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.purchaseActiveListingClaimed(listing.id, player.getUUID(), player.getName().getString()); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((record, error) -> player.server.execute(() -> {
            if (error != null || record == null || record.listing == null) {
                BUYING.remove(player.getUUID());
                EconomyManager.deposit(player, listing.price, "Auction purchase refund " + listing.id);
                player.sendSystemMessage(Component.literal("Purchase failed because the listing may have sold, expired, or been canceled. Your Credits were refunded.").withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }

            EconomyManager.deposit(record.listing.sellerUuid, record.listing.sellerUsername, record.listing.price, "Auction sale " + record.listing.id);
            try {
                NotificationRepository.create(record.listing.sellerUuid, record.listing.sellerUsername, "AUCTION_SOLD", "Auction Sold", player.getName().getString() + " bought " + record.listing.title + " for " + EconomyManager.format(record.listing.price) + ".");
            } catch (Exception notificationError) {
                notificationError.printStackTrace();
            }

            deliver.run();
            BUYING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.literal("Paid " + EconomyManager.format(record.listing.price) + " to " + record.listing.sellerUsername + ".").withStyle(ChatFormatting.GRAY));
        }));
    }

    public static void cancelListing(ServerPlayer player, UUID listingId) {
        if (player == null || listingId == null) return;
        if (!CANCELING.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have an auction cancel in progress.").withStyle(ChatFormatting.RED));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.fetchSellerActiveListing(player.getUUID(), listingId); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((listing, error) -> player.server.execute(() -> {
            if (error != null) {
                CANCELING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Could not load that auction listing. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            if (listing == null) {
                CANCELING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("No active listing found with that ID owned by you.").withStyle(ChatFormatting.YELLOW));
                return;
            }

            if ("POKEMON".equalsIgnoreCase(listing.kind)) {
                cancelPokemonListing(player, listing);
            } else {
                cancelItemListing(player, listing);
            }
        }));
    }

    private static void cancelItemListing(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing) {
        ItemStack stack;
        try { stack = AuctionItemSerializer.fromPayload(player, listing.payload); }
        catch (Exception e) {
            CANCELING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not restore that item listing. Nothing was canceled.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (stack == null || stack.isEmpty()) {
            CANCELING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("That listing has an empty item payload. Nothing was canceled.").withStyle(ChatFormatting.RED));
            return;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            CANCELING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your inventory is full. Clear one empty slot, then cancel again.").withStyle(ChatFormatting.RED));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.cancelActiveListing(player.getUUID(), listing.id); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((cancelled, error) -> player.server.execute(() -> {
            CANCELING.remove(player.getUUID());
            if (error != null || !Boolean.TRUE.equals(cancelled)) {
                player.sendSystemMessage(Component.literal("Could not cancel listing. It may have already sold or expired.").withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }
            giveOrDrop(player, stack);
            player.sendSystemMessage(Component.literal("Canceled auction and returned item: " + listing.title).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static void cancelPokemonListing(ServerPlayer player, AuctionHouseRepository.AuctionListingSummary listing) {
        Pokemon pokemon;
        try { pokemon = AuctionPokemonSerializer.fromPayload(player, listing.payload); }
        catch (Exception e) {
            CANCELING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not restore that Pokémon listing. Nothing was canceled.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (!AuctionPokemonSerializer.hasOpenPartySlot(player)) {
            CANCELING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your party is full. Clear one party slot, then cancel again.").withStyle(ChatFormatting.RED));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.cancelActiveListing(player.getUUID(), listing.id); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((cancelled, error) -> player.server.execute(() -> {
            CANCELING.remove(player.getUUID());
            if (error != null || !Boolean.TRUE.equals(cancelled)) {
                player.sendSystemMessage(Component.literal("Could not cancel listing. It may have already sold or expired.").withStyle(ChatFormatting.RED));
                if (error != null) error.printStackTrace();
                return;
            }
            if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, pokemon)) {
                player.sendSystemMessage(Component.literal("Listing canceled, but your party filled before return. Contact an admin immediately.").withStyle(ChatFormatting.RED));
                return;
            }
            player.sendSystemMessage(Component.literal("Canceled auction and returned Pokémon: " + listing.title).withStyle(ChatFormatting.GREEN));
        }));
    }

    public static void claimNext(ServerPlayer player) {
        if (player == null) return;
        if (!CLAIMING.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have a claim in progress.").withStyle(ChatFormatting.RED));
            return;
        }
        player.sendSystemMessage(Component.literal("Checking for pending auction purchases...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.fetchOldestPendingPurchase(player.getUUID()); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((purchase, error) -> player.server.execute(() -> {
            if (error != null) {
                CLAIMING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("Could not check auction claims. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            if (purchase == null) {
                CLAIMING.remove(player.getUUID());
                player.sendSystemMessage(Component.literal("You have no pending auction purchases.").withStyle(ChatFormatting.YELLOW));
                return;
            }
            if ("POKEMON".equalsIgnoreCase(purchase.kind)) {
                claimPokemon(player, purchase);
            } else {
                claimItem(player, purchase);
            }
        }));
    }

    private static void claimItem(ServerPlayer player, AuctionHouseRepository.PendingPurchase purchase) {
        ItemStack stack;
        try { stack = AuctionItemSerializer.fromPayload(player, purchase.payload); }
        catch (Exception e) {
            CLAIMING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not restore purchased item. Ask an admin to review this purchase.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (stack == null || stack.isEmpty()) {
            CLAIMING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Purchased item payload was empty. Ask an admin to review this purchase.").withStyle(ChatFormatting.RED));
            return;
        }
        int freeSlot = player.getInventory().getFreeSlot();
        if (freeSlot < 0) {
            CLAIMING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your inventory is full. Clear one empty slot, then run /ah claim again.").withStyle(ChatFormatting.RED));
            return;
        }
        player.getInventory().setItem(freeSlot, stack.copy());
        markClaimed(player, purchase.id, "Claimed auction purchase: " + purchase.title + ".");
    }

    private static void claimPokemon(ServerPlayer player, AuctionHouseRepository.PendingPurchase purchase) {
        Pokemon pokemon;
        try { pokemon = AuctionPokemonSerializer.fromPayload(player, purchase.payload); }
        catch (Exception e) {
            CLAIMING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not restore purchased Pokémon. Ask an admin to review this purchase.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }
        if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, pokemon)) {
            CLAIMING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Your party is full. Clear one party slot, then run /ah claim again.").withStyle(ChatFormatting.RED));
            return;
        }
        markClaimed(player, purchase.id, "Claimed auction Pokémon: " + purchase.title + ".");
    }

    private static void markClaimed(ServerPlayer player, UUID purchaseId, String successMessage) {
        CompletableFuture.supplyAsync(() -> {
            try { return AuctionHouseRepository.markPurchaseClaimed(purchaseId); }
            catch (Exception e) { throw new RuntimeException(e); }
        }).whenComplete((marked, markError) -> player.server.execute(() -> {
            CLAIMING.remove(player.getUUID());
            if (markError != null || !Boolean.TRUE.equals(marked)) {
                player.sendSystemMessage(Component.literal("Reward delivered, but claim status could not update. Contact an admin before claiming again.").withStyle(ChatFormatting.RED));
                if (markError != null) markError.printStackTrace();
                return;
            }
            player.sendSystemMessage(Component.literal(successMessage).withStyle(ChatFormatting.GREEN));
        }));
    }

    private static boolean validatePrice(ServerPlayer player, long price) {
        AuctionHouseConfig config = AuctionHouseConfig.get();
        long maxListingPrice = config.safeMaxListingPrice();
        if (price <= 0L || price > maxListingPrice) {
            player.sendSystemMessage(Component.literal("Price must be between 1 and " + maxListingPrice + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return;
        boolean added = player.getInventory().add(stack.copy());
        if (!added) player.drop(stack.copy(), false);
    }
}
