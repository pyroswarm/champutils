package com.champutils.expeditions;

import com.champutils.breeding.BreedingEggData;
import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.auction.AuctionPokemonSerializer;
import com.champutils.economy.EconomyManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.specialspawn.SpecialWildSpawnConfig;
import com.champutils.tm.TMManager;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ExpeditionManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File DIR = new File("config/champutils/expeditions/profiles");
    private static final long CLAIM_LOCK_TIMEOUT_MILLIS = 120_000L;
    private static final ConcurrentHashMap<UUID, Object> PROFILE_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Save> CACHE = new ConcurrentHashMap<>();
    private static final String STATE_KEY = "expedition_state";

    private ExpeditionManager() {}

    public static void load() {
        if (!DIR.exists()) DIR.mkdirs();
        ExpeditionConfig.load();
    }

    public static void preload(UUID profileId) {
        if (profileId == null) return;
        Save local = loadLocal(profileId);
        Save shared = SharedJsonStateRepository.loadProfile(profileId, STATE_KEY, Save.class, local);
        CACHE.put(profileId, shared == null ? local : shared);
        saveLocal(profileId, CACHE.get(profileId));
    }

    public static Save captureForTransfer(UUID profileId) {
        if (profileId == null) return null;
        Save current = CACHE.get(profileId);
        if (current == null) current = loadLocal(profileId);
        return GSON.fromJson(GSON.toJson(current), Save.class);
    }

    public static void saveBlocking(Connection connection, UUID profileId, Save save) throws Exception {
        if (connection == null || profileId == null || save == null) return;
        SharedJsonStateRepository.ensureSchema(connection);
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into profile_json_state (profile_id, state_key, payload, updated_at, version) values (?, ?, ?, now(), 1) " +
                        "on conflict (profile_id, state_key) do update set payload = excluded.payload, updated_at = now(), version = profile_json_state.version + 1")) {
            ps.setObject(1, profileId);
            ps.setString(2, STATE_KEY);
            ps.setString(3, GSON.toJson(save));
            ps.executeUpdate();
        }
    }

    public static void unload(UUID profileId) {
        if (profileId != null) CACHE.remove(profileId);
    }

    public static boolean hasActive(ServerPlayer player) {
        return loadSave(player).active;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 1200 != 0) return; // once per minute; no per-tick disk spam
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Save save = loadSave(player);
            if (!save.active) continue;
            if (save.startedAt <= 0L) save.startedAt = now;
            long last = save.lastOnlineProgressAt <= 0L ? now : save.lastOnlineProgressAt;
            long delta = Math.max(0L, Math.min(120_000L, now - last));
            if (delta > 0L && now < save.endsAt) {
                // Online players earn one extra millisecond of progress per real millisecond online.
                // That makes active expeditions complete at 2x speed while the player is online.
                save.endsAt = Math.max(now, save.endsAt - delta);
            }
            save.lastOnlineProgressAt = now;
            save(player, save);
            if (save.active && now >= save.endsAt) notifyIfReady(player);
        }
    }

    static String startFromPartySlot(ServerPlayer player, int slotIndex, long endsAt, String expeditionType) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        Object lock = PROFILE_LOCKS.computeIfAbsent(profileId, ignored -> new Object());
        synchronized (lock) {
            Save existing = loadSave(player);
            if (existing.active) {
                throw new IllegalStateException("Player already has an active expedition.");
            }

            Pokemon pokemon = AuctionPokemonSerializer.getAndValidatePartyPokemon(player, slotIndex);
            if (pokemon == null) {
                throw new IllegalStateException("That Pokémon is no longer in that slot.");
            }
            if (BreedingEggData.isEgg(pokemon)) {
                throw new IllegalStateException("Pokémon Eggs cannot be sent on expeditions.");
            }

            UUID pokemonUuid = pokemon.getUuid();
            String displayName = pokemon.getDisplayName(true).getString();
            JsonObject payload = AuctionPokemonSerializer.toPayload(player, pokemon);

            boolean removed = false;
            try {
                AuctionPokemonSerializer.clearPartySlot(player, slotIndex);
                removed = true;

                if (AuctionPokemonSerializer.hasPokemonInPartyOrPc(player, pokemonUuid)) {
                    throw new IllegalStateException("The expedition Pokémon still exists in player storage after removal; refusing to start to prevent duplication.");
                }

                Save save = new Save();
                long now = System.currentTimeMillis();
                save.active = true;
                save.startedAt = now;
                save.baseDurationMillis = Math.max(0L, endsAt - now);
                save.lastOnlineProgressAt = now;
                save.endsAt = endsAt;
                save.level = pokemon.getLevel();
                save.name = displayName;
                save.expeditionType = ExpeditionConfig.normalizeType(expeditionType);
                save.payload = payload.toString();
                save.claimInProgress = false;
                save.claimStartedAt = 0L;
                save.claimCompleted = false;
                save.originalReturned = false;
                save.originalDelivery = "";
                save.baseRewardsGranted = false;
                save.expeditionPokemonRewardGenerated = false;
                save.expeditionPokemonRewardDelivered = false;
                save.expeditionPokemonRewardPayload = "";
                save.expeditionPokemonRewardSpecialKind = "";
                save.chunkRewardsGranted = false;
                save.readyNotified = false;
                save.sourcePokemonUuid = pokemonUuid == null ? "" : pokemonUuid.toString();
                save.sourcePokemonRemoved = true;
                save.sourcePartySlot = slotIndex;
                saveOrThrow(player, save);
                AdventureGuideManager.increment(player, "expedition_start", 1);
                return displayName;
            } catch (Exception e) {
                if (removed && !AuctionPokemonSerializer.hasPokemonInPartyOrPc(player, pokemonUuid)) {
                    AuctionPokemonSerializer.DeliveryResult restored = AuctionPokemonSerializer.deliverToPartyOrPc(player, pokemon);
                    if (restored == AuctionPokemonSerializer.DeliveryResult.FAILED) {
                        player.sendSystemMessage(Component.literal("Expedition start failed after removing the Pokémon, and it could not be returned automatically. Check console immediately.").withStyle(ChatFormatting.RED));
                    }
                }
                throw new IllegalStateException("Could not start expedition safely.", e);
            }
        }
    }

    static void start(ServerPlayer player, int slot, Pokemon pokemon, long endsAt) {
        start(player, slot, pokemon, endsAt, "general");
    }

    static void start(ServerPlayer player, int slot, Pokemon pokemon, long endsAt, String expeditionType) {
        if (BreedingEggData.isEgg(pokemon)) {
            throw new IllegalArgumentException("Pokémon Eggs cannot be sent on expeditions.");
        }
        Save save = loadSave(player);
        long now = System.currentTimeMillis();
        save.active = true;
        save.startedAt = now;
        save.baseDurationMillis = Math.max(0L, endsAt - now);
        save.lastOnlineProgressAt = now;
        save.endsAt = endsAt;
        save.level = pokemon.getLevel();
        save.name = pokemon.getDisplayName(true).getString();
        save.expeditionType = ExpeditionConfig.normalizeType(expeditionType);
        save.payload = AuctionPokemonSerializer.toPayload(player, pokemon).toString();
        save.claimInProgress = false;
        save.claimStartedAt = 0L;
        save.claimCompleted = false;
        save.originalReturned = false;
        save.originalDelivery = "";
        save.baseRewardsGranted = false;
        save.expeditionPokemonRewardGenerated = false;
        save.expeditionPokemonRewardDelivered = false;
        save.expeditionPokemonRewardPayload = "";
        save.expeditionPokemonRewardSpecialKind = "";
        save.chunkRewardsGranted = false;
        save.readyNotified = false;
        save.sourcePokemonUuid = pokemon.getUuid() == null ? "" : pokemon.getUuid().toString();
        save.sourcePokemonRemoved = false;
        save.sourcePartySlot = slot - 1;
        save(player, save);
        AdventureGuideManager.increment(player, "expedition_start", 1);
    }

    public static void notifyIfReady(ServerPlayer player) {
        Save save = loadSave(player);
        if (save.active && System.currentTimeMillis() >= save.endsAt && !save.readyNotified) {
            save.readyNotified = true;
            save(player, save);
            player.sendSystemMessage(Component.literal("Your expedition is done! Use /expeditions claim to get your Pokémon and rewards.").withStyle(ChatFormatting.GOLD));
        }
    }

    public static void status(ServerPlayer player) {
        Save save = loadSave(player);
        if (!save.active) {
            player.sendSystemMessage(Component.literal("No active expedition.").withStyle(ChatFormatting.GRAY));
            return;
        }
        long remainingSeconds = remainingMillis(save) / 1000L;
        String status = remainingSeconds <= 0L ? "ready to claim" : ((remainingSeconds + 59L) / 60L) + "m remaining";
        player.sendSystemMessage(Component.literal(save.name + " expedition: " + status + " (online speed: 2x).").withStyle(ChatFormatting.AQUA));
    }

    public static String activeStatusText(ServerPlayer player) {
        Save save = loadSave(player);
        if (!save.active) return "No active expedition.";
        long remainingSeconds = remainingMillis(save) / 1000L;
        if (remainingSeconds <= 0L) return "Ready to claim";
        long minutes = (remainingSeconds + 59L) / 60L;
        if (minutes < 60L) return minutes + "m remaining · online speed 2x";
        long hours = minutes / 60L;
        long leftover = minutes % 60L;
        return hours + "h " + leftover + "m remaining · online speed 2x";
    }

    private static long remainingMillis(Save save) {
        if (save == null || !save.active) return 0L;
        return Math.max(0L, save.endsAt - System.currentTimeMillis());
    }

    public static void claim(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        Object lock = PROFILE_LOCKS.computeIfAbsent(profileId, ignored -> new Object());
        synchronized (lock) {
            Save save = loadSave(player);
            long now = System.currentTimeMillis();
            if (!save.active) {
                player.sendSystemMessage(Component.literal("No active expedition.").withStyle(ChatFormatting.RED));
                return;
            }
            if (save.claimCompleted) {
                save.active = false;
                save.claimInProgress = false;
                save.claimStartedAt = 0L;
                save(player, save);
                player.sendSystemMessage(Component.literal("That expedition has already been claimed.").withStyle(ChatFormatting.YELLOW));
                return;
            }
            if (remainingMillis(save) > 0L) {
                status(player);
                return;
            }
            if (save.claimInProgress && now - save.claimStartedAt < CLAIM_LOCK_TIMEOUT_MILLIS) {
                player.sendSystemMessage(Component.literal("That expedition claim is already being processed. Please wait a moment.").withStyle(ChatFormatting.YELLOW));
                return;
            }

            player.closeContainer();
            save.claimInProgress = true;
            save.claimStartedAt = now;
            try {
                saveOrThrow(player, save);
            } catch (Exception e) {
                e.printStackTrace();
                player.sendSystemMessage(Component.literal("Could not lock that expedition claim safely. Try again in a moment.").withStyle(ChatFormatting.RED));
                return;
            }

            try {
                int battlingLevel = ProfessionManager.getBenefitLevel(player, ProfessionType.BATTLING);
                List<ItemStack> rewards = ExpeditionConfig.itemStacks(save.level, battlingLevel, save.expeditionType);
                if ("tm".equals(ExpeditionConfig.normalizeType(save.expeditionType))) {
                    rewards = new ArrayList<>(rewards);
                    rewards.addAll(createTmRewards(save.level));
                }

                if (!save.originalReturned) {
                    Pokemon pokemon;
                    JsonObject originalPayload;
                    try {
                        originalPayload = JsonParser.parseString(save.payload).getAsJsonObject();
                        UUID originalUuid = AuctionPokemonSerializer.uuidFromPayload(originalPayload);
                        if (originalUuid != null && AuctionPokemonSerializer.hasPokemonInPartyOrPc(player, originalUuid)) {
                            save.originalReturned = true;
                            save.originalDelivery = "ALREADY_IN_STORAGE";
                            saveOrThrow(player, save);
                        }
                        if (save.originalReturned) {
                            pokemon = null;
                        } else {
                            pokemon = AuctionPokemonSerializer.fromPayload(player, originalPayload);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        unlockClaim(player, save);
                        player.sendSystemMessage(Component.literal("Could not restore that expedition Pokémon. Check console before trying again.").withStyle(ChatFormatting.RED));
                        return;
                    }

                    if (!save.originalReturned) {
                        AuctionPokemonSerializer.DeliveryResult delivered = AuctionPokemonSerializer.deliverToPartyOrPc(player, pokemon);
                        if (delivered == AuctionPokemonSerializer.DeliveryResult.FAILED) {
                            unlockClaim(player, save);
                            player.sendSystemMessage(Component.literal("Could not return Pokémon. Make party or PC space and try again.").withStyle(ChatFormatting.RED));
                            return;
                        }
                        save.originalReturned = true;
                        save.originalDelivery = delivered.name();
                        saveOrThrow(player, save);
                    }
                }

                if (!save.baseRewardsGranted) {
                    if (!canFit(player, rewards)) {
                        unlockClaim(player, save);
                        player.sendSystemMessage(Component.literal("Your Pokémon was returned, but you need inventory space before claiming item rewards.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    long creditReward = ExpeditionConfig.creditReward(save.level, save.expeditionType);
                    save.baseRewardsGranted = true;
                    saveOrThrow(player, save);
                    if (creditReward > 0L) EconomyManager.depositAsync(player, creditReward, "expedition_reward");
                    for (ItemStack stack : rewards) player.getInventory().add(stack.copy());
                }

                if ("pokemon".equals(ExpeditionConfig.normalizeType(save.expeditionType))) {
                    if (!save.expeditionPokemonRewardGenerated) {
                        PokemonRewardResult result = createPokemonReward(battlingLevel, save.level, save.expeditionType);
                        Pokemon found = result == null ? null : result.pokemon;
                        if (found != null) {
                            save.expeditionPokemonRewardPayload = AuctionPokemonSerializer.toPayload(player, found).toString();
                            save.expeditionPokemonRewardSpecialKind = result.specialKind == null ? "" : result.specialKind;
                        } else {
                            save.expeditionPokemonRewardPayload = "";
                            save.expeditionPokemonRewardSpecialKind = "";
                        }
                        save.expeditionPokemonRewardGenerated = true;
                        saveOrThrow(player, save);
                    }

                    if (!save.expeditionPokemonRewardDelivered && save.expeditionPokemonRewardPayload != null && !save.expeditionPokemonRewardPayload.isBlank()) {
                        Pokemon found;
                        JsonObject rewardPayload;
                        String foundDisplayName = "";
                        boolean deliveredNow = false;
                        try {
                            rewardPayload = JsonParser.parseString(save.expeditionPokemonRewardPayload).getAsJsonObject();
                            UUID rewardUuid = AuctionPokemonSerializer.uuidFromPayload(rewardPayload);
                            if (rewardUuid != null && AuctionPokemonSerializer.hasPokemonInPartyOrPc(player, rewardUuid)) {
                                save.expeditionPokemonRewardDelivered = true;
                                saveOrThrow(player, save);
                            }
                            found = save.expeditionPokemonRewardDelivered ? null : AuctionPokemonSerializer.fromPayload(player, rewardPayload);
                        } catch (Exception e) {
                            e.printStackTrace();
                            unlockClaim(player, save);
                            player.sendSystemMessage(Component.literal("Could not restore the expedition discovery. Check console before trying again.").withStyle(ChatFormatting.RED));
                            return;
                        }
                        AuctionPokemonSerializer.DeliveryResult foundDelivery;
                        if (!save.expeditionPokemonRewardDelivered) {
                            foundDelivery = AuctionPokemonSerializer.deliverToPartyOrPc(player, found);
                            if (foundDelivery == AuctionPokemonSerializer.DeliveryResult.FAILED) {
                                unlockClaim(player, save);
                                player.sendSystemMessage(Component.literal("Your expedition found a Pokémon, but your party and PC appear full. Make space and claim again.").withStyle(ChatFormatting.RED));
                                return;
                            }
                            TrueCaughtDexManager.markTrueCaught(player, found);
                            foundDisplayName = found.getDisplayName(true).getString();
                            save.expeditionPokemonRewardDelivered = true;
                            saveOrThrow(player, save);
                            deliveredNow = true;
                            player.sendSystemMessage(Component.literal("Your expedition found a wild " + foundDisplayName + "! It was sent to " + foundDelivery.name() + ".").withStyle(ChatFormatting.AQUA));
                        }
                        if (deliveredNow && save.expeditionPokemonRewardSpecialKind != null && !save.expeditionPokemonRewardSpecialKind.isBlank()) {
                            com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                                    player.server,
                                    Component.literal("§6§lExpedition Discovery! §e" + player.getName().getString() + " found a " + save.expeditionPokemonRewardSpecialKind + " Pokémon: §f" + foundDisplayName + "§e!")
                            );
                        }
                    }
                }

                if (!save.chunkRewardsGranted) {
                    List<ExpeditionConfig.ChunkReward> chunkRewards = ExpeditionConfig.chunkRewards(save.level);
                    save.chunkRewardsGranted = true;
                    saveOrThrow(player, save);
                    if (!chunkRewards.isEmpty()) {
                        StringBuilder chunkSummary = new StringBuilder();
                        for (ExpeditionConfig.ChunkReward reward : chunkRewards) {
                            String chunk = reward.chunk == null ? "" : reward.chunk.trim();
                            if (chunk.isEmpty()) continue;
                            int amount = Math.max(1, reward.amount);
                            ProfessionChunkManager.addChunk(player, chunk, amount, false);
                            if (chunkSummary.length() > 0) chunkSummary.append(", ");
                            chunkSummary.append(amount).append("x ").append(chunk.toLowerCase(java.util.Locale.ROOT));
                        }
                        if (chunkSummary.length() > 0) {
                            player.sendSystemMessage(Component.literal("Expedition chunk rewards: " + chunkSummary).withStyle(ChatFormatting.GOLD));
                        }
                    }
                }

                save.claimCompleted = true;
                saveOrThrow(player, save);
                save.active = false;
                save.claimInProgress = false;
                save.claimStartedAt = 0L;
                saveOrThrow(player, save);
                String delivery = save.originalDelivery == null || save.originalDelivery.isBlank() ? "storage" : save.originalDelivery;
                player.sendSystemMessage(Component.literal("Expedition claimed. Pokémon returned to " + delivery + ".").withStyle(ChatFormatting.GREEN));
            } catch (Exception e) {
                e.printStackTrace();
                Save latest = loadSave(player);
                if (latest.active) unlockClaim(player, latest);
                player.sendSystemMessage(Component.literal("Expedition claim failed safely. Nothing was duplicated; try again or check console.").withStyle(ChatFormatting.RED));
            }
        }
    }


    private static List<ItemStack> createTmRewards(int pokemonLevel) {
        List<ItemStack> rewards = new ArrayList<>();
        int count = ExpeditionConfig.tmRewardCount(pokemonLevel);
        for (int i = 0; i < count; i++) {
            ItemStack tm = TMManager.createRandomTMStack("F", 1);
            if (tm != null && !tm.isEmpty()) rewards.add(tm);
        }
        return rewards;
    }

    private static void unlockClaim(ServerPlayer player, Save save) {
        save.claimInProgress = false;
        save.claimStartedAt = 0L;
        save(player, save);
    }

    private static boolean canFit(ServerPlayer player, List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) return true;
        int emptySlots = 0;
        for (ItemStack slot : player.getInventory().items) {
            if (slot.isEmpty()) emptySlots++;
        }
        int needed = 0;
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            needed += Math.max(1, (int) Math.ceil(stack.getCount() / (double) stack.getMaxStackSize()));
        }
        return emptySlots >= needed;
    }

    private static Save loadSave(ServerPlayer player) {
        UUID profileId = player == null ? null : PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return new Save();
        return CACHE.computeIfAbsent(profileId, id -> {
            Save local = loadLocal(id);
            return SharedJsonStateRepository.loadProfile(id, STATE_KEY, Save.class, local);
        });
    }

    private static Save loadLocal(UUID profileId) {
        File file = file(profileId);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Save save = GSON.fromJson(reader, Save.class);
                if (save != null) return save;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new Save();
    }

    private static void save(ServerPlayer player, Save save) {
        try { saveOrThrow(player, save); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private static void saveOrThrow(ServerPlayer player, Save save) throws Exception {
        UUID profileId = player == null ? null : PlayerProfileManager.activeProfileId(player);
        if (profileId == null) throw new IllegalStateException("No active profile for expedition save.");
        CACHE.put(profileId, save);
        saveLocalOrThrow(profileId, save);
        SharedJsonStateRepository.saveProfile(profileId, STATE_KEY, save);
    }

    private static void saveLocal(UUID profileId, Save save) {
        try { saveLocalOrThrow(profileId, save); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private static void saveLocalOrThrow(UUID profileId, Save save) throws Exception {
        if (profileId == null || save == null) return;
        if (!DIR.exists() && !DIR.mkdirs()) throw new IllegalStateException("Could not create expedition profile directory.");
        File target = file(profileId);
        File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(tmp)) { GSON.toJson(save, writer); }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFailure) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static File file(UUID profileId) {
        if (!DIR.exists()) DIR.mkdirs();
        return new File(DIR, profileId + ".json");
    }

    public static final class Save {
        boolean active;
        long endsAt;
        long startedAt;
        long baseDurationMillis;
        long lastOnlineProgressAt;
        int level;
        String name = "";
        String payload = "";
        String expeditionType = "general";
        boolean claimInProgress;
        long claimStartedAt;
        boolean claimCompleted;
        boolean originalReturned;
        String originalDelivery = "";
        boolean baseRewardsGranted;
        boolean expeditionPokemonRewardGenerated;
        boolean expeditionPokemonRewardDelivered;
        String expeditionPokemonRewardPayload = "";
        String expeditionPokemonRewardSpecialKind = "";
        boolean chunkRewardsGranted;
        boolean readyNotified;
        String sourcePokemonUuid = "";
        boolean sourcePokemonRemoved;
        int sourcePartySlot = -1;
    }

    private static final class PokemonRewardResult {
        final Pokemon pokemon;
        final String specialKind;
        PokemonRewardResult(Pokemon pokemon, String specialKind) {
            this.pokemon = pokemon;
            this.specialKind = specialKind;
        }
    }

    private static PokemonRewardResult createPokemonReward(int battlingLevel, int sentPokemonLevel, String expeditionType) {
        int level = Math.max(1, Math.min(100, battlingLevel));
        double rollPercent = ThreadLocalRandom.current().nextDouble() * 100.0D;
        double legendaryChance = ExpeditionConfig.legendaryPokemonChancePercent(level, sentPokemonLevel, expeditionType);
        double paradoxUbChance = ExpeditionConfig.paradoxUltraBeastChancePercent(level, sentPokemonLevel, expeditionType);
        String species;
        String specialKind = null;
        if (rollPercent < legendaryChance) {
            species = randomFrom(SpecialWildSpawnConfig.DATA.legendarySpawns, SpecialWildSpawnConfig.DATA.mythicalSpawns);
            specialKind = "Legendary/Mythical";
        } else if (rollPercent < legendaryChance + paradoxUbChance) {
            species = randomFrom(SpecialWildSpawnConfig.DATA.paradoxSpawns, SpecialWildSpawnConfig.DATA.ultraBeastSpawns);
            specialKind = "Paradox/Ultra Beast";
        } else if (level >= 75 && rollPercent < legendaryChance + paradoxUbChance + 3.0D) {
            species = randomCommon("dratini", "larvitar", "bagon", "beldum", "gible", "goomy", "dreepy", "frigibax");
        } else if (level >= 45 && rollPercent < legendaryChance + paradoxUbChance + 10.0D) {
            species = randomCommon("eevee", "riolu", "ralts", "rotom", "zorua", "ditto", "togepi", "munchlax");
        } else {
            species = randomCommon("pidgey", "rattata", "magikarp", "shinx", "starly", "bunnelby", "mareep", "wooper", "machop", "gastly");
        }
        if (species == null || species.isBlank()) return null;
        try {
            String clean = species.contains(":") ? species : "cobblemon:" + species;
            int pokemonLevel = Math.max(5, Math.min(70, 5 + level / 2));
            Pokemon rewardPokemon = PokemonProperties.Companion.parse("species=\"" + clean + "\" level=" + pokemonLevel).create();
            return new PokemonRewardResult(rewardPokemon, specialKind);
        } catch (Throwable throwable) {
            return null;
        }
    }

    @SafeVarargs
    private static String randomFrom(List<SpecialWildSpawnConfig.SpawnEntry>... lists) {
        List<String> species = new ArrayList<>();
        if (lists != null) {
            for (List<SpecialWildSpawnConfig.SpawnEntry> list : lists) {
                if (list == null) continue;
                for (SpecialWildSpawnConfig.SpawnEntry entry : list) {
                    if (entry != null && entry.species != null && !entry.species.isBlank()) species.add(entry.species);
                }
            }
        }
        if (species.isEmpty()) return null;
        return species.get(ThreadLocalRandom.current().nextInt(species.size()));
    }

    private static String randomCommon(String... species) {
        if (species == null || species.length == 0) return "magikarp";
        return species[ThreadLocalRandom.current().nextInt(species.length)];
    }
}
