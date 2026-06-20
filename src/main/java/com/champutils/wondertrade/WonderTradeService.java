package com.champutils.wondertrade;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.champutils.dex.PokemonOriginManager;
import com.champutils.profile.ProfileRestrictions;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WonderTradeService {

    private static final Set<UUID> TRADING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService DB_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "ChampUtils-WonderTrade-DB");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile WonderTradeRepository.StatusSnapshot cachedStatus;
    private static volatile long cachedStatusAtMillis = 0L;
    private static final long STATUS_CACHE_MILLIS = 10_000L;

    private WonderTradeService() {}

    public static void trade(ServerPlayer player, int slotNumber) {
        if (player == null) return;
        if (ProfileRestrictions.blockIronmanTrade(player, "wondertrade")) return;
        if (!DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Wondertrade requires the database to be enabled.").withStyle(ChatFormatting.RED));
            return;
        }

        int slotIndex = slotNumber - 1;
        if (slotIndex < 0 || slotIndex > 5) {
            player.sendSystemMessage(Component.literal("Use a party slot from 1-6.").withStyle(ChatFormatting.RED));
            return;
        }

        UUID playerUuid = player.getUUID();
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) {
            player.sendSystemMessage(Component.literal("You must load a profile before using Wondertrade.").withStyle(ChatFormatting.RED));
            return;
        }
        String playerName = player.getName().getString();
        MinecraftServer server = player.server;

        if (!TRADING.add(playerUuid)) {
            player.sendSystemMessage(Component.literal("You already have a Wondertrade in progress.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("Checking Wondertrade...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.supplyAsync(() -> {
            try {
                WonderTradeRepository.TradeGate gate = WonderTradeRepository.getTradeGate(profileId, playerUuid);
                if (gate.hasPendingClaim()) {
                    return TradeCheck.pendingClaim();
                }
                if (gate.cooldownRemainingSeconds() > 0) {
                    return TradeCheck.cooldown(gate.cooldownRemainingSeconds());
                }
                return TradeCheck.ok();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((check, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);
            if (onlinePlayer == null) {
                TRADING.remove(playerUuid);
                deletePendingAsync(profileId);
                cachedStatus = null;
                cachedStatusAtMillis = 0L;
                return;
            }

            if (error != null) {
                TRADING.remove(playerUuid);
                onlinePlayer.sendSystemMessage(Component.literal("Could not check Wondertrade safety data. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }

            if (!check.allowed) {
                TRADING.remove(playerUuid);
                onlinePlayer.sendSystemMessage(check.message);
                return;
            }

            prepareTradeAfterCheck(onlinePlayer, profileId, slotIndex, slotNumber);
        }));
    }

    private static void prepareTradeAfterCheck(ServerPlayer player, UUID profileId, int slotIndex, int slotNumber) {
        UUID playerUuid = player.getUUID();
        String playerName = player.getName().getString();
        MinecraftServer server = player.server;

        Pokemon offered;
        try {
            offered = AuctionPokemonSerializer.getPartyPokemon(player, slotIndex);
        } catch (Exception e) {
            TRADING.remove(playerUuid);
            player.sendSystemMessage(Component.literal("Could not read that party slot. Check console.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        if (offered == null) {
            TRADING.remove(playerUuid);
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
            TRADING.remove(playerUuid);
            player.sendSystemMessage(Component.literal("Could not safely save that Pokémon for Wondertrade.").withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return;
        }

        player.sendSystemMessage(Component.literal("Preparing " + offeredName + " for Wondertrade...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.runAsync(() -> {
            try {
                // Crash/disconnect safety: this is saved before the party slot is cleared.
                WonderTradeRepository.savePendingClaim(profileId, playerUuid, playerName, "OFFERED", offeredPayload, offeredName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((ignored, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);
            if (onlinePlayer == null) {
                TRADING.remove(playerUuid);
                deletePendingAsync(profileId);
                return;
            }

            if (error != null) {
                TRADING.remove(playerUuid);
                onlinePlayer.sendSystemMessage(Component.literal("Could not create Wondertrade safety record. Nothing was traded.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }

            try {
                AuctionPokemonSerializer.clearPartySlot(onlinePlayer, slotIndex);
            } catch (Exception e) {
                TRADING.remove(playerUuid);
                deletePendingAsync(profileId);
                onlinePlayer.sendSystemMessage(Component.literal("Could not remove that Pokémon from your party. Nothing was traded.").withStyle(ChatFormatting.RED));
                e.printStackTrace();
                return;
            }

            onlinePlayer.sendSystemMessage(Component.literal("Sending " + offeredName + " into Wondertrade...").withStyle(ChatFormatting.GRAY));
            finishTradeAsync(server, profileId, playerUuid, playerName, offeredPayload, offeredName, offeredShiny, offeredLegendary);
        }));
    }

    private static void finishTradeAsync(MinecraftServer server, UUID profileId, UUID playerUuid, String playerName, JsonObject offeredPayload, String offeredName, boolean offeredShiny, boolean offeredLegendary) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return WonderTradeRepository.exchange(profileId, playerUuid, playerName, offeredPayload);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((receivedEntry, error) -> server.execute(() -> {
            TRADING.remove(playerUuid);
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);

            if (error != null || receivedEntry == null) {
                if (onlinePlayer != null) {
                    restoreOffered(onlinePlayer, offeredPayload);
                    deletePendingAsync(profileId);
                    onlinePlayer.sendSystemMessage(Component.literal("Wondertrade failed. Your Pokémon was returned.").withStyle(ChatFormatting.RED));
                } else {
                    saveOfferedPendingAsync(profileId, playerUuid, playerName, offeredPayload, offeredName);
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
                PokemonOriginManager.markOrigin(received, PokemonOriginManager.ORIGIN_WONDERTRADE);
                boolean added = AuctionPokemonSerializer.addToFirstOpenPartySlot(onlinePlayer, received);
                if (!added) {
                    onlinePlayer.sendSystemMessage(Component.literal("Wondertrade completed, but your party was full. Free a party slot, then use the Wonder Trade NPC claim button.").withStyle(ChatFormatting.YELLOW));
                    return;
                }
                deletePendingAsync(profileId);
            } catch (Exception e) {
                onlinePlayer.sendSystemMessage(Component.literal("Wondertrade completed, but claim safety triggered. Free a party slot, then use the Wonder Trade NPC claim button.").withStyle(ChatFormatting.YELLOW));
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
        if (ProfileRestrictions.blockIronmanTrade(player, "wondertrade")) return;
        if (!DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Wondertrade database is disabled.").withStyle(ChatFormatting.RED));
            return;
        }

        UUID playerUuid = player.getUUID();
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) {
            player.sendSystemMessage(Component.literal("You must load a profile before claiming Wondertrade Pokémon.").withStyle(ChatFormatting.RED));
            return;
        }
        MinecraftServer server = player.server;
        player.sendSystemMessage(Component.literal("Checking pending Wondertrade claim...").withStyle(ChatFormatting.GRAY));

        CompletableFuture.supplyAsync(() -> {
            try {
                return WonderTradeRepository.getPendingClaim(profileId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((claim, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);
            if (onlinePlayer == null) return;

            if (error != null) {
                onlinePlayer.sendSystemMessage(Component.literal("Could not claim your pending Wondertrade Pokémon. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }

            if (claim == null) {
                onlinePlayer.sendSystemMessage(Component.literal("You do not have a pending Wondertrade Pokémon.").withStyle(ChatFormatting.GRAY));
                return;
            }

            try {
                Pokemon pokemon = AuctionPokemonSerializer.fromPayload(onlinePlayer, claim.payload());
                if (!"OFFERED".equalsIgnoreCase(claim.claimType())) {
                    PokemonOriginManager.markOrigin(pokemon, PokemonOriginManager.ORIGIN_WONDERTRADE);
                }
                if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(onlinePlayer, pokemon)) {
                    onlinePlayer.sendSystemMessage(Component.literal("Your party is full. Free a slot and run /wondertrade claim again.").withStyle(ChatFormatting.RED));
                    return;
                }
            } catch (Exception e) {
                onlinePlayer.sendSystemMessage(Component.literal("Could not restore your pending Wondertrade Pokémon. Check console.").withStyle(ChatFormatting.RED));
                e.printStackTrace();
                return;
            }

            deletePendingAsync(profileId);
            String label = "OFFERED".equalsIgnoreCase(claim.claimType()) ? "returned" : "claimed";
            onlinePlayer.sendSystemMessage(Component.literal("Wondertrade Pokémon " + label + ": " + claim.displayName()).withStyle(ChatFormatting.GREEN));
        }));
    }

    public static void sendCooldown(ServerPlayer player) {
        if (player == null) return;
        UUID playerUuid = player.getUUID();
        MinecraftServer server = player.server;
        CompletableFuture.supplyAsync(() -> {
            try {
                int minutes = WonderTradeRepository.getCooldownMinutes();
                long remaining = WonderTradeRepository.getCooldownRemainingSeconds(playerUuid);
                return new CooldownInfo(minutes, remaining);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((info, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);
            if (onlinePlayer == null) return;
            if (error != null) {
                onlinePlayer.sendSystemMessage(Component.literal("Could not read Wondertrade cooldown. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            onlinePlayer.sendSystemMessage(Component.literal("Wondertrade cooldown: " + info.minutes + " minutes. Remaining: " + formatRemaining(info.remainingSeconds) + ".").withStyle(ChatFormatting.AQUA));
        }));
    }

    public static void setCooldown(ServerPlayer player, int minutes) {
        if (player == null) return;
        UUID playerUuid = player.getUUID();
        MinecraftServer server = player.server;
        CompletableFuture.runAsync(() -> {
            try {
                WonderTradeRepository.setCooldownMinutes(minutes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((ignored, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);
            if (onlinePlayer == null) return;
            if (error != null) {
                onlinePlayer.sendSystemMessage(Component.literal("Could not update Wondertrade cooldown. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            cachedStatus = null;
            cachedStatusAtMillis = 0L;
            onlinePlayer.sendSystemMessage(Component.literal("Wondertrade cooldown set to " + minutes + " minutes.").withStyle(ChatFormatting.GREEN));
        }));
    }

    public static void sendStatus(ServerPlayer player) {
        if (player == null) return;
        if (ProfileRestrictions.blockIronmanTrade(player, "wondertrade")) return;
        if (!DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Wondertrade database is disabled.").withStyle(ChatFormatting.RED));
            return;
        }

        UUID playerUuid = player.getUUID();
        MinecraftServer server = player.server;
        CompletableFuture.supplyAsync(() -> {
            try {
                long now = System.currentTimeMillis();
                WonderTradeRepository.StatusSnapshot snapshot = cachedStatus;
                if (snapshot != null && now - cachedStatusAtMillis <= STATUS_CACHE_MILLIS) {
                    return snapshot;
                }
                snapshot = WonderTradeRepository.getStatusSnapshot();
                cachedStatus = snapshot;
                cachedStatusAtMillis = now;
                return snapshot;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).whenComplete((stats, error) -> server.execute(() -> {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerUuid);
            if (onlinePlayer == null) return;
            if (error != null) {
                onlinePlayer.sendSystemMessage(Component.literal("Could not read Wondertrade status. Check console.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            onlinePlayer.sendSystemMessage(Component.literal("Wondertrade Pool: " + stats.poolSize() + " Pokémon | Shinies: " + stats.shinyCount() + " | Legendaries: " + stats.legendaryCount() + " | Cooldown: " + stats.cooldownMinutes() + "m").withStyle(ChatFormatting.AQUA));
        }));
    }

    private static void restoreOffered(ServerPlayer player, JsonObject offeredPayload) {
        try {
            Pokemon restored = AuctionPokemonSerializer.fromPayload(player, offeredPayload);
            if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, restored)) {
                saveOfferedPendingAsync(PlayerProfileManager.activeProfileId(player), player.getUUID(), player.getName().getString(), offeredPayload, "Returned Pokémon");
                player.sendSystemMessage(Component.literal("Your party is full. Free a slot and use /wondertrade claim to recover your Pokémon.").withStyle(ChatFormatting.RED));
            }
        } catch (Exception restoreError) {
            player.sendSystemMessage(Component.literal("CRITICAL: Wondertrade failed and Pokémon restore failed. Contact an admin immediately.").withStyle(ChatFormatting.RED));
            restoreError.printStackTrace();
        }
    }

    private static void deletePendingAsync(UUID profileId) {
        CompletableFuture.runAsync(() -> {
            try {
                WonderTradeRepository.deletePendingClaim(profileId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).exceptionally(error -> {
            error.printStackTrace();
            return null;
        });
    }

    private static void saveOfferedPendingAsync(UUID profileId, UUID playerUuid, String playerName, JsonObject offeredPayload, String offeredName) {
        if (profileId == null || playerUuid == null) return;
        CompletableFuture.runAsync(() -> {
            try {
                WonderTradeRepository.savePendingClaim(profileId, playerUuid, playerName, "OFFERED", offeredPayload, offeredName);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, DB_EXECUTOR).exceptionally(error -> {
            error.printStackTrace();
            return null;
        });
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

    private record TradeCheck(boolean allowed, Component message) {
        static TradeCheck ok() {
            return new TradeCheck(true, Component.empty());
        }

        static TradeCheck pendingClaim() {
            return new TradeCheck(false, Component.literal("You have a pending Wondertrade Pokémon. Use the Wonder Trade NPC claim button before trading again.").withStyle(ChatFormatting.YELLOW));
        }

        static TradeCheck cooldown(long remainingSeconds) {
            return new TradeCheck(false, Component.literal("Wondertrade is on cooldown for " + formatRemaining(remainingSeconds) + ".").withStyle(ChatFormatting.RED));
        }
    }

    private record CooldownInfo(int minutes, long remainingSeconds) {}
}
