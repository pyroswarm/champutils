package com.champutils.breeding;

import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.afk.AntiAfkManager;
import com.champutils.profile.CobblemonProfileStorageBridge;
import com.champutils.profile.PlayerProfileManager;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.party.PartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BreedingManager {
    private static final Map<UUID, MovementState> MOVEMENT = new ConcurrentHashMap<>();
    private static final Set<UUID> CREATING_EGG = ConcurrentHashMap.newKeySet();
    private static long ticks;

    private BreedingManager() {}

    public static void initialize() {
        BreedingConfig.load();
        BreedingRepository.ensureSchemaAsync();
        BreedingTradeGuard.register();
        BreedingOriginLanguageTracker.register();
    }

    public static void handleJoin(ServerPlayer player) {
        resetMovement(player);
        reconcileHatchedEggAudit(player);
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) return;
        MOVEMENT.remove(player.getUUID());
        CREATING_EGG.remove(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        BreedingConfig.Values config = BreedingConfig.get();
        if (!config.enabled || server == null) return;
        ticks++;
        if (ticks % config.stepSampleIntervalTicks != 0L) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                tickPlayer(player, config);
            } catch (Throwable error) {
                System.err.println("[ChampUtils][Breeding] Egg step tick failed for " + player.getGameProfile().getName());
                error.printStackTrace();
                resetMovement(player);
            }
        }
    }

    public static void startBreeding(ServerPlayer player, int firstSlot, int secondSlot) {
        BreedingConfig.Values config = BreedingConfig.get();
        if (player == null) return;
        if (!config.enabled) {
            player.sendSystemMessage(Component.literal("Pokémon breeding is disabled.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!com.champutils.database.DatabaseManager.isEnabled()) {
            player.sendSystemMessage(Component.literal("Breeding is temporarily unavailable. Please try again shortly.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!PlayerProfileManager.hasActiveProfile(player)) {
            player.sendSystemMessage(Component.literal("Select a profile before breeding Pokémon.").withStyle(ChatFormatting.RED));
            return;
        }
        if (firstSlot < 0 || firstSlot > 5 || secondSlot < 0 || secondSlot > 5) {
            player.sendSystemMessage(Component.literal("Party slots must be between 1 and 6.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!CREATING_EGG.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("An Egg is already being prepared for you.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        PartyStore party = party(player);
        Pokemon parentA = party == null ? null : party.get(firstSlot);
        Pokemon parentB = party == null ? null : party.get(secondSlot);
        PokemonBreedingRules.Compatibility compatibility = PokemonBreedingRules.compatibility(parentA, parentB);
        if (!compatibility.compatible()) {
            CREATING_EGG.remove(player.getUUID());
            player.sendSystemMessage(Component.literal(compatibility.reason()).withStyle(ChatFormatting.RED));
            return;
        }
        if (!AuctionPokemonSerializer.hasOpenPartySlot(player)) {
            CREATING_EGG.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("You need an empty party slot to receive the Egg.").withStyle(ChatFormatting.RED));
            return;
        }
        if (countPartyEggs(player) >= config.maxEggsInParty) {
            CREATING_EGG.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("You already have the maximum number of Eggs in your party.").withStyle(ChatFormatting.RED));
            return;
        }

        final PokemonBreedingRules.Result result;
        final Pokemon egg;
        try {
            result = PokemonBreedingRules.createEgg(player, parentA, parentB);
            egg = BreedingEggData.createPlaceholder(player, result.hatchling(), parentA, parentB, result.requiredSteps(), result.mysteryEgg(), result.rarity());
        } catch (Throwable error) {
            CREATING_EGG.remove(player.getUUID());
            player.sendSystemMessage(Component.literal("The Egg could not be created. Your Pokémon and cooldown were not changed.").withStyle(ChatFormatting.RED));
            error.printStackTrace();
            return;
        }

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        UUID parentAUuid = parentA.getUuid();
        UUID parentBUuid = parentB.getUuid();
        String parentASpecies = speciesId(parentA);
        String parentBSpecies = speciesId(parentB);
        String offspringSpecies = speciesId(result.hatchling());
        player.sendSystemMessage(Component.literal("Preparing your Egg...").withStyle(ChatFormatting.GRAY));

        BreedingRepository.reserveAndRegister(
                profileId,
                egg.getUuid(),
                parentAUuid,
                parentBUuid,
                parentASpecies,
                parentBSpecies,
                offspringSpecies,
                result.requiredSteps(),
                config.breedingCooldownSeconds
        ).whenComplete((reservation, error) -> {
            MinecraftServer callbackServer = player.server;
            if (callbackServer == null) {
                CREATING_EGG.remove(player.getUUID());
                if (reservation != null && reservation.accepted()) {
                    BreedingRepository.markLostAndRefundAsync(profileId, egg.getUuid());
                }
                return;
            }
            callbackServer.execute(() -> {
            CREATING_EGG.remove(player.getUUID());
            if (error != null) {
                player.sendSystemMessage(Component.literal("Breeding is temporarily unavailable. No Egg was created.").withStyle(ChatFormatting.RED));
                error.printStackTrace();
                return;
            }
            if (reservation == null || !reservation.accepted()) {
                String wait = reservation == null ? "later" : formatCooldown(reservation.nextEggAt());
                player.sendSystemMessage(Component.literal("The nursery needs more time. Try again " + wait + ".").withStyle(ChatFormatting.YELLOW));
                return;
            }

            if (player.hasDisconnected() || !profileId.equals(PlayerProfileManager.activeProfileId(player))) {
                BreedingRepository.markLostAndRefundAsync(profileId, egg.getUuid());
                return;
            }
            PartyStore currentParty = party(player);
            Pokemon currentA = currentParty == null ? null : currentParty.get(firstSlot);
            Pokemon currentB = currentParty == null ? null : currentParty.get(secondSlot);
            boolean parentsStillValid = currentA != null && currentB != null
                    && parentAUuid.equals(currentA.getUuid())
                    && parentBUuid.equals(currentB.getUuid());
            if (config.requireParentsInPartyUntilEggCreated && !parentsStillValid) {
                BreedingRepository.markLostAndRefundAsync(profileId, egg.getUuid());
                player.sendSystemMessage(Component.literal("Breeding canceled because one of the selected party slots changed.").withStyle(ChatFormatting.RED));
                return;
            }
            if (!AuctionPokemonSerializer.hasOpenPartySlot(player) || countPartyEggs(player) >= config.maxEggsInParty) {
                BreedingRepository.markLostAndRefundAsync(profileId, egg.getUuid());
                player.sendSystemMessage(Component.literal("Breeding canceled because your party no longer has room for the Egg.").withStyle(ChatFormatting.RED));
                return;
            }
            if (!AuctionPokemonSerializer.addToFirstOpenPartySlot(player, egg)) {
                BreedingRepository.markLostAndRefundAsync(profileId, egg.getUuid());
                player.sendSystemMessage(Component.literal("The Egg could not be delivered. Your cooldown was refunded.").withStyle(ChatFormatting.RED));
                return;
            }

            CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player);
            player.playNotifySound(SoundEvents.CHICKEN_EGG, SoundSource.PLAYERS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.literal("You received a Pokémon Egg!").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal("Keep it in your party and walk to hatch it. Use /breeding to inspect progress.").withStyle(ChatFormatting.GRAY));
            });
        });
    }

    public static int countPartyEggs(ServerPlayer player) {
        int count = 0;
        PartyStore party = party(player);
        if (party == null) return 0;
        for (int i = 0; i < party.size(); i++) {
            if (BreedingEggData.isEgg(party.get(i))) count++;
        }
        return count;
    }

    public static List<EggView> eggs(ServerPlayer player) {
        List<EggView> result = new ArrayList<>();
        PartyStore party = party(player);
        if (party == null) return result;
        for (int i = 0; i < party.size(); i++) {
            Pokemon pokemon = party.get(i);
            if (BreedingEggData.isEgg(pokemon)) result.add(new EggView(i, pokemon));
        }
        return result;
    }

    public record EggView(int slot, Pokemon egg) {}

    private static void tickPlayer(ServerPlayer player, BreedingConfig.Values config) {
        if (player == null || player.hasDisconnected() || !PlayerProfileManager.hasActiveProfile(player)) {
            handleDisconnect(player);
            return;
        }
        MovementState previous = MOVEMENT.get(player.getUUID());
        Vec3 current = player.position();
        String dimension = player.level().dimension().location().toString();
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (previous == null || !dimension.equals(previous.dimension) || !profileId.equals(previous.profileId)) {
            MOVEMENT.put(player.getUUID(), new MovementState(current.x, current.z, 0.0D, dimension, profileId));
            return;
        }

        double dx = current.x - previous.x;
        double dz = current.z - previous.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double remainder = previous.remainder;
        boolean validMovement = !player.isSpectator()
                && !player.getAbilities().flying
                && !player.isFallFlying()
                && distance > 0.0D
                && distance <= config.maximumBlocksPerSample;
        int walkedSteps = 0;
        if (validMovement) {
            double total = remainder + distance;
            walkedSteps = (int) Math.floor(total);
            remainder = total - walkedSteps;
        } else if (distance > config.maximumBlocksPerSample) {
            remainder = 0.0D;
        }
        MOVEMENT.put(player.getUUID(), new MovementState(current.x, current.z, remainder, dimension, profileId));
        if (walkedSteps <= 0) return;
        // Egg steps require recent genuine input and are blocked for tight/repeating movement loops.
        if (!AntiAfkManager.canProgressEggHatching(player)) return;

        PartyStore party = party(player);
        if (party == null) return;
        int effectiveSteps = hasHatchBoost(party, config) ? walkedSteps * 2 : walkedSteps;
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon egg = party.get(slot);
            if (!BreedingEggData.isEgg(egg)) continue;
            UUID eggProfile = BreedingEggData.profileId(egg);
            if (eggProfile != null && !profileId.equals(eggProfile)) continue;

            BreedingEggData.addSteps(egg, effectiveSteps);
            if (BreedingEggData.shouldPersist(egg, config.persistEverySteps)) {
                BreedingEggData.markPersisted(egg);
                egg.onChange(null);
            }
            if (BreedingEggData.remainingSteps(egg) <= 0) {
                UUID auditProfileId = BreedingEggData.auditProfileId(egg);
                hatch(player, party, slot, egg, auditProfileId == null ? profileId : auditProfileId);
                // Modern games process Eggs in party order and stop after the first hatch.
                break;
            }
        }
    }

    private static boolean hasHatchBoost(PartyStore party, BreedingConfig.Values config) {
        for (int i = 0; i < party.size(); i++) {
            Pokemon pokemon = party.get(i);
            if (pokemon == null || BreedingEggData.isEgg(pokemon)) continue;
            try {
                String ability = normalize(pokemon.getAbility().getName());
                if (config.flameBodyAbilities.stream().map(BreedingManager::normalize).anyMatch(ability::equals)) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void hatch(ServerPlayer player, PartyStore party, int slot, Pokemon egg, UUID profileId) {
        UUID eggUuid = BreedingEggData.eggUuid(egg);
        if (eggUuid == null) return;
        try {
            Pokemon hatchling = BreedingEggData.loadHatchling(player, egg);
            hatchling.setOriginalTrainer(player.getUUID());
            hatchling.setOriginalTrainerName(player.getGameProfile().getName());
            hatchling.setTradeable(true);
            BreedingEggData.markHatchOrigin(hatchling, eggUuid, profileId);
            hatchling.heal();

            if (!AuctionPokemonSerializer.replacePartySlot(player, slot, hatchling)) {
                egg.onChange(null);
                player.sendSystemMessage(Component.literal("Your Egg is ready, but it could not hatch in that party slot. Move it to another slot or reconnect.").withStyle(ChatFormatting.RED));
                return;
            }

            BreedingRepository.markHatchedAsync(profileId, eggUuid);
            CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player);
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.25F);
            player.sendSystemMessage(Component.literal("Oh?").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            player.sendSystemMessage(Component.literal(hatchling.getDisplayName(true).getString() + " hatched from the Egg!")
                    .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            BreedingProfessionService.rewardHatch(player, hatchling);
            com.champutils.worldfirst.WorldFirstManager.award(player, "first_breeding_hatch");
            com.champutils.cosmetic.TitleManager.unlock(player, "breeding_initiate");
            if (profileId != null && profileId.equals(com.champutils.profile.PlayerProfileManager.activeProfileId(player))) {
                com.champutils.dex.TrueCaughtDexManager.markTrueCaught(player, hatchling);
            }
            BreedingEventBridge.postHatch(player, hatchling);
        } catch (Throwable error) {
            player.sendSystemMessage(Component.literal("The Egg could not hatch, so it has stayed in your party.").withStyle(ChatFormatting.RED));
            error.printStackTrace();
        }
    }


    private static void reconcileHatchedEggAudit(ServerPlayer player) {
        if (player == null || !com.champutils.database.DatabaseManager.isEnabled()) return;
        PartyStore party = party(player);
        if (party == null) return;
        for (int slot = 0; slot < party.size(); slot++) {
            Pokemon pokemon = party.get(slot);
            if (pokemon == null || BreedingEggData.isEgg(pokemon)) continue;
            UUID eggUuid = BreedingEggData.hatchOriginEggUuid(pokemon);
            UUID profileId = BreedingEggData.hatchOriginProfileId(pokemon);
            if (eggUuid != null && profileId != null) {
                BreedingRepository.markHatchedAsync(profileId, eggUuid);
            }
        }
    }

    private static PartyStore party(ServerPlayer player) {
        try { return Cobblemon.INSTANCE.getStorage().getParty(player); }
        catch (Throwable ignored) { return null; }
    }

    private static void resetMovement(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = PlayerProfileManager.activeProfileIdOrNull(player.getUUID());
        Vec3 position = player.position();
        MOVEMENT.put(player.getUUID(), new MovementState(
                position.x,
                position.z,
                0.0D,
                player.level().dimension().location().toString(),
                profileId
        ));
    }

    private static String formatCooldown(Instant next) {
        if (next == null) return "later";
        long seconds = Math.max(1L, Duration.between(Instant.now(), next).getSeconds());
        if (seconds < 60L) return "in " + seconds + " second(s)";
        long minutes = (seconds + 59L) / 60L;
        return "in " + minutes + " minute(s)";
    }

    private static String speciesId(Pokemon pokemon) {
        try { return pokemon.getSpecies().getResourceIdentifier().toString(); }
        catch (Throwable ignored) { return "unknown"; }
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record MovementState(double x, double z, double remainder, String dimension, UUID profileId) {}
}
