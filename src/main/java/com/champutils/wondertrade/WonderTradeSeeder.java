package com.champutils.wondertrade;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.database.DatabaseManager;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WonderTradeSeeder {

    private static final int TARGET_POOL_SIZE = 100;
    private static final double INJECT_SHINY_CHANCE = 0.01D;
    private static final AtomicBoolean SEEDING = new AtomicBoolean(false);
    private static final AtomicBoolean SEEDED_THIS_RUN = new AtomicBoolean(false);

    private WonderTradeSeeder() {}

    public static void handleJoin(ServerPlayer player) {
        seedIfNeeded(player, false);
    }

    public static void seedIfNeeded(ServerPlayer player, boolean notify) {
        seedToTarget(player, TARGET_POOL_SIZE, true, notify);
    }

    public static void seedToTarget(ServerPlayer player, int targetPoolSize, boolean guaranteeOneShinyIfNoneExists, boolean notify) {
        if (player == null || !DatabaseManager.isEnabled()) {
            if (notify && player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cWondertrade requires SQL/database to be enabled."));
            }
            return;
        }

        int safeTarget = Math.max(1, Math.min(10000, targetPoolSize));
        if (!SEEDING.compareAndSet(false, true)) {
            if (notify) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eWondertrade seeding is already running."));
            }
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                WonderTradeRepository.ensureSchema();
                int size = WonderTradeRepository.poolSize();
                int shinyCount = WonderTradeRepository.shinyCount();
                int toCreate = Math.max(0, safeTarget - size);
                if (toCreate <= 0) return 0;

                Random random = new Random();
                int shinyIndex = guaranteeOneShinyIfNoneExists && shinyCount <= 0 ? random.nextInt(toCreate) : -1;
                return createSeedPokemon(player, toCreate, shinyIndex, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((created, error) -> player.server.execute(() -> {
            SEEDING.set(false);
            if (error != null) {
                if (notify) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cWondertrade seed failed. Check console."));
                error.printStackTrace();
                return;
            }
            if (created != null && created > 0) {
                SEEDED_THIS_RUN.set(true);
                System.out.println("[ChampUtils] Wondertrade seeded " + created + " Pokémon. Pool target: " + safeTarget + ".");
                if (notify) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aWondertrade seeded " + created + " Pokémon. Target pool: " + safeTarget + "."));
            } else if (notify) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aWondertrade pool is already at or above " + safeTarget + " Pokémon."));
            }
        }));
    }

    public static void inject(ServerPlayer player, int amount, boolean notify) {
        if (player == null || !DatabaseManager.isEnabled()) {
            if (notify && player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cWondertrade requires SQL/database to be enabled."));
            }
            return;
        }

        int safeAmount = Math.max(1, Math.min(1000, amount));
        if (!SEEDING.compareAndSet(false, true)) {
            if (notify) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eWondertrade seeding/injection is already running."));
            }
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                WonderTradeRepository.ensureSchema();
                return createSeedPokemon(player, safeAmount, -1, true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((created, error) -> player.server.execute(() -> {
            SEEDING.set(false);
            if (error != null) {
                if (notify) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cWondertrade injection failed. Check console."));
                error.printStackTrace();
                return;
            }
            SEEDED_THIS_RUN.set(true);
            System.out.println("[ChampUtils] Wondertrade injected " + created + " Pokémon into the pool.");
            if (notify) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aWondertrade injected " + created + " Pokémon into the pool."));
            }
        }));
    }

    private static int createSeedPokemon(ServerPlayer player, int amount, int guaranteedShinyIndex, boolean randomShinyChance) throws Exception {
        Random random = new Random();
        int created = 0;
        for (int i = 0; i < amount; i++) {
            boolean shiny = i == guaranteedShinyIndex || (randomShinyChance && random.nextDouble() < INJECT_SHINY_CHANCE);
            Pokemon pokemon = WonderTradePokemonUtil.createRandomSeedPokemon(shiny);
            JsonObject payload = AuctionPokemonSerializer.toPayload(player, pokemon);
            String species = payload.has("species") ? payload.get("species").getAsString() : "unknown";
            String displayName = payload.has("displayName") ? payload.get("displayName").getAsString() : species;
            int level = payload.has("level") ? payload.get("level").getAsInt() : Math.max(1, pokemon.getLevel());
            WonderTradeRepository.insertSeed(payload, species, displayName, level, shiny);
            created++;
        }
        return created;
    }

    public static boolean seededThisRun() {
        return SEEDED_THIS_RUN.get();
    }
}
