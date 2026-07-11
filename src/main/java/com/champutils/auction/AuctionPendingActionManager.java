package com.champutils.auction;

import com.champutils.economy.EconomyManager;
import com.champutils.menu.ConfirmationMenu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuctionPendingActionManager {

    private static final Map<UUID, PendingAction> PENDING = new ConcurrentHashMap<>();

    private AuctionPendingActionManager() {}

    public static void setItemListing(ServerPlayer player, ItemStack stackSnapshot, long price) {
        PendingAction action = new PendingAction();
        action.type = Type.ITEM_LISTING;
        action.price = price;
        action.itemSnapshot = stackSnapshot == null ? ItemStack.EMPTY : stackSnapshot.copy();
        PENDING.put(player.getUUID(), action);

        ConfirmationMenu.open(
                player,
                "Confirm Auction Listing",
                action.itemSnapshot.isEmpty() ? Items.CHEST : action.itemSnapshot.getItem(),
                "§eList Item Auction",
                new String[]{
                        "§7Item: §f" + action.itemSnapshot.getHoverName().getString(),
                        "§7Price: §6" + EconomyManager.format(price),
                        "§cThe item is removed when the listing is created."
                },
                () -> AuctionHouseService.confirmPending(player),
                () -> AuctionHouseService.cancelPending(player)
        );
    }

    public static void setPokemonListing(ServerPlayer player, int slotIndex, String pokemonName, UUID pokemonUuid, long price) {
        PendingAction action = new PendingAction();
        action.type = Type.POKEMON_LISTING;
        action.price = price;
        action.partySlotIndex = slotIndex;
        action.pokemonName = pokemonName;
        action.pokemonUuid = pokemonUuid;
        PENDING.put(player.getUUID(), action);

        ConfirmationMenu.open(
                player,
                "Confirm Auction Listing",
                Items.PAPER,
                "§eList Pokémon Auction",
                new String[]{
                        "§7Pokémon: §f" + pokemonName,
                        "§7Party Slot: §f" + (slotIndex + 1),
                        "§7Price: §6" + EconomyManager.format(price),
                        "§cThe Pokémon is removed when the listing is created."
                },
                () -> AuctionHouseService.confirmPending(player),
                () -> AuctionHouseService.cancelPending(player)
        );
    }

    public static PendingAction get(ServerPlayer player) {
        return player == null ? null : PENDING.get(player.getUUID());
    }

    public static PendingAction remove(ServerPlayer player) {
        return player == null ? null : PENDING.remove(player.getUUID());
    }

    public static boolean has(ServerPlayer player) {
        return player != null && PENDING.containsKey(player.getUUID());
    }

    public enum Type {
        ITEM_LISTING,
        POKEMON_LISTING
    }

    public static final class PendingAction {
        public Type type;
        public long price;
        public ItemStack itemSnapshot = ItemStack.EMPTY;
        public int partySlotIndex = -1;
        public String pokemonName = "Pokémon";
        public UUID pokemonUuid;
    }
}
