package com.champutils.auction;

import com.champutils.economy.EconomyManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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

        player.sendSystemMessage(Component.literal("Confirm auction listing:").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Item: " + action.itemSnapshot.getHoverName().getString()).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Price: " + EconomyManager.format(price)).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Type /ah confirm to list it, or /ah cancel to stop.").withStyle(ChatFormatting.GREEN));
    }

    public static void setPokemonListing(ServerPlayer player, int slotIndex, String pokemonName, long price) {
        PendingAction action = new PendingAction();
        action.type = Type.POKEMON_LISTING;
        action.price = price;
        action.partySlotIndex = slotIndex;
        action.pokemonName = pokemonName;
        PENDING.put(player.getUUID(), action);

        player.sendSystemMessage(Component.literal("Confirm auction listing:").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("Pokémon: " + pokemonName + " in party slot " + (slotIndex + 1)).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Price: " + EconomyManager.format(price)).withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Type /ah confirm to list it, or /ah cancel to stop.").withStyle(ChatFormatting.GREEN));
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
    }
}
