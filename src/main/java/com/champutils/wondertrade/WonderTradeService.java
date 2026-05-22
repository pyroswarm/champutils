package com.champutils.wondertrade;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class WonderTradeService {

    private static final Set<UUID> TRADING = ConcurrentHashMap.newKeySet();

    private WonderTradeService() {}

    public static void trade(ServerPlayer player, int slotNumber) {
        if (player == null) return;
        if (!DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Wondertrade requires the database to be enabled.").withStyle(ChatFormatting.RED));
            return;
        }

        int slotIndex = slotNumber - 1;
        if (slotIndex < 0 || slotIndex > 5) {
            player.sendSystemMessage(Component.literal("Use a party slot from 1-6.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            if (WonderTradeRepository.hasPendingClaim(player.getUUID())) {
                player.sendSystemMessage(Component.literal("You have a pending Wondertrade Pokémon. Use /wondertrade claim before trading again.").withStyle(ChatFormatting.YELLOW));
                return;
            }

            long remaining = WonderTradeRepository.getCooldownRemainingSeconds(player.getUUID());
            if (remaining > 0) {
                player.sendSystemMessage(Component.literal("Wondertrade is on cooldown for " + formatRemaining(remaining) + ".").withStyle(ChatFormatting.RED));
                return;
            }
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not check Wondertrade safety data. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        if (!TRADING.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You already have a Wondertrade in progress.").withStyle(ChatFormatting.RED));
            return;
        }

        WonderTradeSeeder.seedIfNeeded(player, false);

        Pokemon offered;
        try {
            offered = AuctionPokemonSerializer.getPartyPokemon(player, slotIndex);
        } catch (Exception e) {
            TRADING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not read that party slot. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        if (offered == null) {
            TRADING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("There is no Pokémon in party slot " + slotNumber + ".").withStyle(ChatFormatting.RED));
            return;
        }

        JsonObject offeredPayload;
        String offeredName;
        boolean offeredShiny;
        boolean offeredLegendary;
        try {
            offeredPayload = WonderTradePokemonUtil.toPayload(player, offered);
            offeredName = offered.getDisplayName(true).getString();
            offeredShiny = offered.getShiny();
            offeredLegendary = WonderTradePokemonUtil.isLegendary(offered);
        } catch (Exception e) {
            TRADING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not safely save that Pokémon for Wondertrade.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        try {
            // Crash/disconnect safety: if the server stops after the Pokémon is removed but before the exchange finishes,
            // /wondertrade claim restores the offered Pokémon instead of losing it.
            WonderTradeRepository.savePendingClaim(player.getUUID(), player.getName().getString(), "OFFERED", offeredPayload, offeredName);
        } catch (Exception e) {
            TRADING.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("Could not create Wondertrade safety record. Nothing was traded.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        try {
            AuctionPokemonSerializer.clearPartySlot(player, slotIndex);
        } catch (Exception e) {
            TRADING.remove(player.getUUID());
            try { WonderTradeRepository.deletePendingClaim(player.getUUID()); } catch (Exception ignored) {}
            player.sendSystemMessage(Component.literal("Could not remove that Pokémon from your party. Nothing was traded.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        player.sendSystemMessage(Component.literal("Sending " + offeredName + " into Wondertrade...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.supplyAsync(() -> {
            try {
                return WonderTradeRepository.exchange(player.getUUID(), player.getName().getString(), offeredPayload);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((receivedEntry, error) -> player.server.execute(() -> {
            TRADING.remove(player.getUUID());

            ServerPlayer onlinePlayer = player.server.getPlayerList().getPlayer(player.getUUID());

            if (error != null || receivedEntry == null) {
                if (onlinePlayer != null) {
                    restoreOffered(onlinePlayer, offeredPayload);
                    try { WonderTradeRepository.deletePendingClaim(player.getUUID()); } catch (Exception ignored) {}
                    onlinePlayer.sendSystemMessage(Component.literal("Wondertrade failed. Your Pokémon was returned.").withStyle(ChatFormatting.RED));
                } else {
                    try { WonderTradeRepository.savePendingClaim(player.getUUID(), player.getName().getString(), "OFFERED", offeredPayload, offeredName); } catch (Exception ignored) {}
                }
                if (error != null) error.printStackTrace();
                return;
            }

            // The SQL transaction already converted the pending claim to RECEIVED and marked cooldown.
            // If the player disconnected, they can safely claim the Pokémon later.
            if (onlinePlayer == null) {
                return;
            }

            try {
                Pokemon received = AuctionPokemonSerializer.fromPayload(onlinePlayer, receivedEntry.payload);
                boolean added = AuctionPokemonSerializer.addToFirstOpenPartySlot(onlinePlayer, received);
                if (!added) {
                    onlinePlayer.sendSystemMessage(Component.literal("Wondertrade completed, but your party was full. Use /wondertrade claim after freeing a slot.").withStyle(ChatFormatting.YELLOW));
                    return;
                }
                WonderTradeRepository.deletePendingClaim(onlinePlayer.getUUID());
            } catch (Exception e) {
                onlinePlayer.sendSystemMessage(Component.literal("Wondertrade completed, but claim safety triggered. Free a party slot and use /wondertrade claim.").withStyle(ChatFormatting.YELLOW));
                e.printStackTrace();
                return;
            }

            Component receivedName = Component.literal(receivedEntry.displayName).withStyle(receivedEntry.shiny ? ChatFormatting.GOLD : ChatFormatting.AQUA);
            onlinePlayer.sendSystemMessage(Component.literal("Wondertrade complete! You received ").withStyle(ChatFormatting.GREEN).append(receivedName).append(Component.literal(".").withStyle(ChatFormatting.GREEN)));

            if (offeredShiny || offeredLegendary) {
                announceOffered(onlinePlayer, offeredName, offeredShiny, offeredLegendary);
            }

            if (receivedEntry.shiny || receivedEntry.legendary) {
                announceReceived(onlinePlayer, receivedEntry.displayName, receivedEntry.shiny, receivedEntry.legendary);
            }
        }));
    }

    public static void claimPending(ServerPlayer player) {
        if (player == null) return;
        if (!DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Wondertrade database is disabled.").withStyle(ChatFormatting.RED));
            return;
        }

        try {
            WonderTradeRepository.PendingClaim claim = WonderTradeRepository.getPendingClaim(player.getUUID());
            if (claim == null) {
                player.sendSystemMessage(Component.literal("You do not have a pending Wondertrade Pokémon.").withStyle(ChatFormatting.GRAY));
                return;
            }

            Pokemon pokemon = AuctionPokemonSerializer.fromPayload(player, claim.payload());
            if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, pokemon)) {
                player.sendSystemMessage(Component.literal("Your party is full. Free a slot and run /wondertrade claim again.").withStyle(ChatFormatting.RED));
                return;
            }

            WonderTradeRepository.deletePendingClaim(player.getUUID());
            String label = "OFFERED".equalsIgnoreCase(claim.claimType()) ? "returned" : "claimed";
            player.sendSystemMessage(Component.literal("Wondertrade Pokémon " + label + ": " + claim.displayName()).withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not claim your pending Wondertrade Pokémon. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
        }
    }

    public static void sendCooldown(ServerPlayer player) {
        if (player == null) return;
        try {
            int minutes = WonderTradeRepository.getCooldownMinutes();
            long remaining = WonderTradeRepository.getCooldownRemainingSeconds(player.getUUID());
            player.sendSystemMessage(Component.literal("Wondertrade cooldown: " + minutes + " minutes. Remaining: " + formatRemaining(remaining) + ".").withStyle(ChatFormatting.AQUA));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not read Wondertrade cooldown. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
        }
    }

    public static void setCooldown(ServerPlayer player, int minutes) {
        if (player == null) return;
        try {
            WonderTradeRepository.setCooldownMinutes(minutes);
            player.sendSystemMessage(Component.literal("Wondertrade cooldown set to " + minutes + " minutes.").withStyle(ChatFormatting.GREEN));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not update Wondertrade cooldown. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
        }
    }

    public static void sendStatus(ServerPlayer player) {
        if (player == null) return;
        if (!DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Wondertrade database is disabled.").withStyle(ChatFormatting.RED));
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                WonderTradeRepository.ensureSchema();
                return new int[] {
                        WonderTradeRepository.poolSize(),
                        WonderTradeRepository.shinyCount(),
                        WonderTradeRepository.legendaryCount(),
                        WonderTradeRepository.getCooldownMinutes()
                };
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((stats, error) -> player.server.execute(() -> {
            if (error != null) {
                player.sendSystemMessage(Component.literal("Could not read Wondertrade status. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            player.sendSystemMessage(Component.literal("Wondertrade Pool: " + stats[0] + " Pokémon | Shinies: " + stats[1] + " | Legendaries: " + stats[2] + " | Cooldown: " + stats[3] + "m").withStyle(ChatFormatting.AQUA));
        }));
    }

    private static void restoreOffered(ServerPlayer player, JsonObject offeredPayload) {
        try {
            Pokemon restored = AuctionPokemonSerializer.fromPayload(player, offeredPayload);
            if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, restored)) {
                WonderTradeRepository.savePendingClaim(player.getUUID(), player.getName().getString(), "OFFERED", offeredPayload, "Returned Pokémon");
                player.sendSystemMessage(Component.literal("Your party is full. Free a slot and use /wondertrade claim to recover your Pokémon.").withStyle(ChatFormatting.RED));
            }
        } catch (Exception restoreError) {
            player.sendSystemMessage(Component.literal("CRITICAL: Wondertrade failed and Pokémon restore failed. Contact an admin immediately.").withStyle(ChatFormatting.RED));
            restoreError.printStackTrace();
        }
    }

    private static void announceOffered(ServerPlayer player, String pokemonName, boolean shiny, boolean legendary) {
        String tag = shiny && legendary ? "a shiny legendary" : shiny ? "a shiny" : "a legendary";
        player.server.getPlayerList().broadcastSystemMessage(
                Component.literal("✦ " + player.getName().getString() + " added " + tag + " " + pokemonName + " to Wondertrade!").withStyle(shiny ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE),
                false
        );
    }

    private static void announceReceived(ServerPlayer player, String pokemonName, boolean shiny, boolean legendary) {
        String tag = shiny && legendary ? "a shiny legendary" : shiny ? "a shiny" : "a legendary";
        player.server.getPlayerList().broadcastSystemMessage(
                Component.literal("✦ " + player.getName().getString() + " received " + tag + " " + pokemonName + " from Wondertrade!").withStyle(shiny ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE),
                false
        );
    }

    private static String formatRemaining(long seconds) {
        if (seconds <= 0) return "ready";
        long minutes = seconds / 60;
        long remSeconds = seconds % 60;
        if (minutes <= 0) return remSeconds + "s";
        return minutes + "m " + remSeconds + "s";
    }
}
