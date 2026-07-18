package com.champutils.adventurer;

import com.champutils.buff.BuffContext;
import com.champutils.buff.BuffManager;
import com.champutils.buff.BuffType;
import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattlePrepManager;
import com.champutils.battle.PluginTrainerBattleStarter;
import com.champutils.economy.EconomyManager;
import com.champutils.crate.CrateCreditManager;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.roaming.RoamingTrainerRarity;
import com.champutils.roaming.RoamingTrainerManager;
import com.champutils.spawn.SpawnBlockRules;
import com.champutils.teleport.RandomTeleportCommand;
import com.champutils.teleport.SafeTeleportManager;
import com.champutils.territory.TerritoryTeleportUtil;
import com.champutils.territory.TerritoryRepository;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AdventurerGuildManager {
    public static final String SOURCE_BATTLE_TOWER = "battle_tower";
    public static final String SOURCE_BATTLE_TOWER_ULTIMATE = "battle_tower_ultimate";
    public static final String SOURCE_ROAMING_LEAGUE = "adventurer_request";
    private static final String PENDING_TOWER_TRANSFER_KEY = "pending_battle_tower_transfer";
    private static final long PENDING_TOWER_TRANSFER_TTL_MS = 120_000L;
    private static final String PENDING_ISLANDER_REQUEST_KEY = "pending_islander_adventurer_request";

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final Map<UUID, AdventurerGuildDataManager.PlayerData> CACHE = new HashMap<>();
    private static final HashSet<UUID> DIRTY = new HashSet<>();
    private static int tickCounter = 0;

    private AdventurerGuildManager() {}

    public static void load() {
        AdventurerGuildConfig.load();
        BattleTowerPoolConfig.load();
        AdventurerGuildDataManager.ensureSchemaAsync();
    }

    public static void handleJoin(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = getData(player);
        refreshPeriods(data);
        if (data.activeTowerFloor > 0) {
            int interruptedFloor = data.activeTowerFloor;
            data.lastTowerEndMillis = System.currentTimeMillis();
            clearActiveTower(data);
            data.ultimateClimbActive = false;
            data.ultimateClimbStartedMillis = 0L;
            data.towerFloor = checkpointForBest(data.bestTowerFloor);
            markDirty(player);
            sendToSpawn(player);
            player.sendSystemMessage(Component.literal("Your Battle Tower run ended because you disconnected during floor " + interruptedFloor + ". Try again in " + secondsLeft(Math.max(0, AdventurerGuildConfig.SETTINGS.battleTowerCooldownSeconds) * 1000L) + ".").withStyle(ChatFormatting.RED));
        }
        notifyRankProgress(player, data);
        savePlayer(player);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !AdventurerGuildConfig.SETTINGS.enabled) return;
        tickCounter++;
        if (tickCounter < 1200) return;
        tickCounter = 0;
        ensureBattleTowerChunksLoaded(server);
        long now = System.currentTimeMillis();
        long activeLimit = Math.max(5, AdventurerGuildConfig.SETTINGS.battleTowerActiveMinutes) * 60_000L;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!PlayerProfileManager.hasActiveProfile(player)) continue;
            AdventurerGuildDataManager.PlayerData data = getData(player);
            refreshPeriods(data);
            if (data.activeTowerFloor > 0 && data.activeTowerStartedMillis > 0L && now - data.activeTowerStartedMillis > activeLimit) {
                data.lastTowerEndMillis = now;
                data.lastTowerEndMillis = System.currentTimeMillis();
                clearActiveTower(data);
                data.ultimateClimbActive = false; data.ultimateClimbStartedMillis = 0L; data.lastTowerEndMillis = System.currentTimeMillis();
                data.towerFloor = checkpointForBest(data.bestTowerFloor);
                sendToSpawn(player);
                markDirty(player);
                player.sendSystemMessage(Component.literal("Your Battle Tower challenge expired. You can restart from floor " + data.towerFloor + ".").withStyle(ChatFormatting.YELLOW));
            }
            savePlayer(player);
        }
    }

    public static void preload(UUID profileId, String playerName) {
        if (profileId == null) return;
        AdventurerGuildDataManager.PlayerData data = AdventurerGuildDataManager.load(profileId, playerName);
        refreshPeriods(data);
        CACHE.put(profileId, data);
    }

    public static AdventurerGuildDataManager.PlayerData getData(ServerPlayer player) {
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) {
            AdventurerGuildDataManager.PlayerData fallback = new AdventurerGuildDataManager.PlayerData();
            fallback.name = player == null ? "" : player.getName().getString();
            refreshPeriods(fallback);
            return fallback;
        }
        AdventurerGuildDataManager.PlayerData data = CACHE.get(profileId);
        if (data != null) {
            refreshPeriods(data);
            return data;
        }
        data = AdventurerGuildDataManager.load(profileId, player.getName().getString());
        refreshPeriods(data);
        CACHE.put(profileId, data);
        return data;
    }

    public static void recordBattleResult(ServerPlayer winner, ServerPlayer loser, BattleContextManager.BattleType battleType) {
        if (!AdventurerGuildConfig.SETTINGS.enabled || winner == null || battleType == null) return;
        if (battleType != BattleContextManager.BattleType.RANKED && battleType != BattleContextManager.BattleType.CASUAL) return;
        recordQueuedPvp(winner, battleType == BattleContextManager.BattleType.RANKED, true);
        if (loser != null) recordQueuedPvp(loser, battleType == BattleContextManager.BattleType.RANKED, false);
    }

    public static void recordBattleLoss(ServerPlayer player, BattleContextManager.BattleType battleType) {
        if (player == null || battleType == null) return;
        if (battleType == BattleContextManager.BattleType.ADVENTURE_TOWER) {
            AdventurerGuildDataManager.PlayerData data = getData(player);
            if (data.activeTowerFloor > 0) {
                int failed = data.activeTowerFloor;
                data.lastTowerEndMillis = System.currentTimeMillis();
                removeActiveTowerPokemon(player, data);
                clearActiveTower(data);
                data.ultimateClimbActive = false; data.ultimateClimbStartedMillis = 0L;
                data.towerFloor = checkpointForBest(data.bestTowerFloor);
                markDirty(player);
                savePlayer(player);
                sendToSpawn(player);
                long cooldown = Math.max(0, AdventurerGuildConfig.SETTINGS.battleTowerCooldownSeconds) * 1000L;
                player.sendSystemMessage(Component.literal("You have failed your climb. Try again in " + secondsLeft(cooldown) + ".").withStyle(ChatFormatting.RED));
            }
        }
    }

    public static boolean startBattleTowerFloor(ServerPlayer player) {
        return startBattleTowerFloor(player, false);
    }

    private static boolean startBattleTowerFloor(ServerPlayer player, boolean ignoreCooldown) {
        if (player == null || !AdventurerGuildConfig.SETTINGS.enabled) return false;
        Boolean routed = routeToBattleTowerHost(player, "floor", ignoreCooldown);
        if (routed != null) return routed;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, AdventurerGuildConfig.SETTINGS.battleTowerCooldownSeconds) * 1000L;
        if (data.activeTowerFloor > 0) {
            player.sendSystemMessage(Component.literal("You already have an active Battle Tower trainer. Defeat it or wait for it to expire.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!ignoreCooldown && cooldown > 0 && now - data.lastTowerEndMillis < cooldown) {
            player.sendSystemMessage(Component.literal("Battle Tower is preparing your next floor. Try again in " + secondsLeft(cooldown - (now - data.lastTowerEndMillis)) + ".").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        int floor = Math.max(1, Math.min(AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor, data.towerFloor));
        if (!isCheckpointFloor(floor) && floor != 1 && floor > data.bestTowerFloor + 1) {
            floor = checkpointForBest(data.bestTowerFloor);
            data.towerFloor = floor;
        }

        AdventurerGuildConfig.BattleTowerFloor floorData = AdventurerGuildConfig.floor(floor);
        if (!floorData.locationSet) {
            player.sendSystemMessage(Component.literal("Battle Tower floor " + floor + " has no arena center yet. Ask an admin to stand at the center and run /adventurer admin settowerfloor " + floor + ".").withStyle(ChatFormatting.RED));
            return false;
        }

        ServerLevel targetLevel = resolveTowerLevel(player, floorData);
        if (targetLevel == null) {
            player.sendSystemMessage(Component.literal("Could not find the configured Battle Tower world for floor " + floor + ".").withStyle(ChatFormatting.RED));
            return false;
        }

        TowerPlacement placement = towerPlacement(floorData);
        player.closeContainer();
        if (!loadTowerChunks(targetLevel, placement)) {
            player.sendSystemMessage(Component.literal("Could not load Battle Tower floor " + floor + ". Check the configured world/location.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!teleportTo(player, targetLevel, placement.playerPos, placement.playerYaw, 0.0F)) {
            player.sendSystemMessage(Component.literal("Could not teleport you to Battle Tower floor " + floor + ". Check the configured world/location.").withStyle(ChatFormatting.RED));
            return false;
        }

        String towerSource = data.ultimateClimbActive ? SOURCE_BATTLE_TOWER_ULTIMATE : SOURCE_BATTLE_TOWER;
        UUID pokemonUuid = BattleTowerTrainerBattle.spawnAndStart(player, targetLevel, placement.npcPos, placement.npcYaw, floor, data.ultimateClimbActive);
        if (pokemonUuid == null) {
            player.sendSystemMessage(Component.literal("Could not create the Battle Tower trainer for floor " + floor + ". Check the configured center and pool.").withStyle(ChatFormatting.RED));
            sendToSpawn(player);
            return false;
        }

        data.activeTowerFloor = floor;
        data.activeTowerNpcUuid = pokemonUuid.toString();
        data.activeTowerStartedMillis = now;
        data.lastTowerStartMillis = now;
        markDirty(player);
        savePlayer(player);
        player.closeContainer();
        showFloorAnnouncement(player, floor);
        player.sendSystemMessage(Component.literal("Battle Tower floor " + floor + " has begun. Party switching, Pokémon storage, and healing are locked.").withStyle(ChatFormatting.GOLD));
        return true;
    }


    public static boolean startUltimateClimb(ServerPlayer player) {
        if (player == null || !AdventurerGuildConfig.SETTINGS.enabled) return false;
        Boolean routed = routeToBattleTowerHost(player, "ultimate", true);
        if (routed != null) return routed;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(1, AdventurerGuildConfig.SETTINGS.ultimateClimbAttemptCooldownHours) * 3_600_000L;
        if (data.activeTowerFloor > 0) { player.sendSystemMessage(Component.literal("Finish your current Battle Tower run first.").withStyle(ChatFormatting.RED)); return false; }
        if (now - data.lastUltimateClimbAttemptMillis < cooldown) {
            player.sendSystemMessage(Component.literal("Ultimate Climb is available again in " + secondsLeft(cooldown - (now - data.lastUltimateClimbAttemptMillis)) + ".").withStyle(ChatFormatting.YELLOW)); return false;
        }
        data.ultimateClimbActive = true;
        data.ultimateClimbStartedMillis = now;
        data.lastUltimateClimbAttemptMillis = now;
        data.towerFloor = 1;
        markDirty(player); savePlayer(player);
        player.sendSystemMessage(Component.literal("Ultimate Climb started. You must clear floors 1-100 in one uninterrupted session.").withStyle(ChatFormatting.LIGHT_PURPLE));
        return startBattleTowerFloor(player, true);
    }

    private static Boolean routeToBattleTowerHost(ServerPlayer player, String action, boolean ignoreCooldown) {
        if (player == null) return false;
        String targetServer = NetworkServerConfig.get().battleTowerServerId;
        if (targetServer == null || targetServer.isBlank()) targetServer = NetworkServerConfig.get().survivalServerId;
        if (targetServer == null || targetServer.isBlank() || targetServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            return null;
        }
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
        if (active == null) {
            player.sendSystemMessage(Component.literal("Select a profile before entering the Battle Tower.").withStyle(ChatFormatting.RED));
            return false;
        }
        PendingTowerTransfer pending = new PendingTowerTransfer();
        pending.targetServerId = targetServer;
        pending.action = action == null ? "floor" : action;
        pending.ignoreCooldown = ignoreCooldown;
        pending.expiresAtMillis = System.currentTimeMillis() + PENDING_TOWER_TRANSFER_TTL_MS;
        String finalTargetServer = targetServer;
        player.closeContainer();
        player.sendSystemMessage(Component.literal("Sending you to the Battle Tower server.").withStyle(ChatFormatting.YELLOW));
        SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_TOWER_TRANSFER_KEY, pending)
                .whenComplete((ignored, error) -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player)) return;
                    if (error != null) {
                        player.sendSystemMessage(Component.literal("Could not prepare the Battle Tower transfer.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, finalTargetServer, message -> {
                        if (message != null && message.startsWith("Could not")) {
                            clearPendingTowerTransfer(player.getUUID());
                            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                        }
                    });
                }));
        return true;
    }

    public static void handleProfileReady(ServerPlayer player) {
        if (player == null) return;
        SharedJsonStateRepository.loadPlayerAsync(player.getUUID(), PENDING_TOWER_TRANSFER_KEY, PendingTowerTransfer.class, null)
                .thenAccept(pending -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player) || pending == null) return;
                    if (pending.expiresAtMillis < System.currentTimeMillis()) {
                        clearPendingTowerTransfer(player.getUUID());
                        return;
                    }
                    if (pending.targetServerId == null || !pending.targetServerId.equalsIgnoreCase(NetworkServerConfig.serverId())) return;
                    clearPendingTowerTransfer(player.getUUID());
                    if ("ultimate".equalsIgnoreCase(pending.action)) {
                        startUltimateClimb(player);
                    } else {
                        startBattleTowerFloor(player, pending.ignoreCooldown);
                    }
                }));

        SharedJsonStateRepository.loadPlayerAsync(player.getUUID(), PENDING_ISLANDER_REQUEST_KEY, PendingIslanderRequest.class, null)
                .thenAccept(pending -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player) || pending == null || pending.expiresAtMillis <= 0L) return;
                    if (pending.expiresAtMillis < System.currentTimeMillis()) {
                        clearPendingIslanderRequest(player.getUUID());
                        return;
                    }
                    if (pending.targetServerId == null || !pending.targetServerId.equalsIgnoreCase(NetworkServerConfig.serverId())) return;
                    clearPendingIslanderRequest(player.getUUID());
                    startRoamingLeague(player, RoamingTrainerRarity.parse(pending.rarity, RoamingTrainerRarity.F), true);
                }));
    }

    private static void clearPendingTowerTransfer(UUID playerUuid) {
        if (playerUuid == null) return;
        SharedJsonStateRepository.savePlayerAsync(playerUuid, PENDING_TOWER_TRANSFER_KEY, new PendingTowerTransfer());
    }

    public static final class PendingTowerTransfer {
        public String targetServerId = "";
        public String action = "floor";
        public boolean ignoreCooldown = false;
        public long expiresAtMillis = 0L;
    }

    private static void clearPendingIslanderRequest(UUID playerUuid) {
        if (playerUuid == null) return;
        SharedJsonStateRepository.savePlayerAsync(playerUuid, PENDING_ISLANDER_REQUEST_KEY, new PendingIslanderRequest());
    }

    public static final class PendingIslanderRequest {
        public String targetServerId = "";
        public String rarity = "F";
        public long expiresAtMillis = 0L;
    }

    public static boolean startRoamingLeague(ServerPlayer player, RoamingTrainerRarity rarity) {
        return startRoamingLeague(player, rarity, false);
    }

    private static boolean startRoamingLeague(ServerPlayer player, RoamingTrainerRarity rarity, boolean locationResolved) {
        if (player == null || !AdventurerGuildConfig.SETTINGS.enabled) return false;
        RoamingTrainerRarity safeRarity = rarity == null ? RoamingTrainerRarity.F : rarity;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        AdventurerGuildConfig.RoamingLeagueEntry entry = AdventurerGuildConfig.roamingEntry(safeRarity);
        if (data.renown < Math.max(0, entry.minRenown)) {
            player.sendSystemMessage(Component.literal("You need " + entry.minRenown + " Adventurer XP to request that Adventurer.").withStyle(ChatFormatting.RED));
            return false;
        }

        if (!locationResolved && PlayerProfileManager.isIslander(player)) {
            return routeIslanderAdventurerRequest(player, safeRarity);
        }

        if (!locationResolved && shouldRtpBeforeAdventurerRequest(player)) {
            player.closeContainer();
            player.sendSystemMessage(Component.literal("Taking you out into the world before your Adventurer arrives...").withStyle(ChatFormatting.YELLOW));
            return RandomTeleportCommand.requestRtp(player, "overworld", () -> startRoamingLeague(player, safeRarity, true));
        }

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, AdventurerGuildConfig.SETTINGS.roamingLeagueCooldownMinutes) * 60_000L;
        if (cooldown > 0 && now - data.lastRoamingLeagueStartMillis < cooldown) {
            player.sendSystemMessage(Component.literal("Adventurer requests are on cooldown for " + secondsLeft(cooldown - (now - data.lastRoamingLeagueStartMillis)) + ".").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        int cost = Math.max(0, entry.creditCost);
        boolean free = data.roamingLeagueDailySpawns < Math.max(0, AdventurerGuildConfig.SETTINGS.roamingLeagueDailyFreeSpawns);
        if (!free && cost > 0) {
            long cents = EconomyManager.wholeCreditsToCents(cost);
            EconomyManager.withdrawAsync(player, cents, "adventurer_roaming_league:" + safeRarity.name().toLowerCase(Locale.ROOT))
                    .thenAccept(result -> player.server.execute(() -> {
                        if (!result.success) {
                            player.sendSystemMessage(Component.literal(result.error == null ? "Not enough Credits." : result.error).withStyle(ChatFormatting.RED));
                            return;
                        }
                        finishRoamingLeagueStart(player, safeRarity, data, now, free, cost);
                    }));
            return true;
        }
        return finishRoamingLeagueStart(player, safeRarity, data, now, free, cost);
    }

    private static boolean finishRoamingLeagueStart(ServerPlayer player, RoamingTrainerRarity safeRarity,
                                                     AdventurerGuildDataManager.PlayerData data, long now,
                                                     boolean free, int cost) {
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 spawnPos = player.position().add(look.x * 2.0D, 0.0D, look.z * 2.0D);
        UUID npcUuid = RoamingTrainerManager.spawnForAdventureGuildAt(
                player,
                player.serverLevel(),
                spawnPos,
                player.getYRot() + 180.0F,
                safeRarity,
                SOURCE_ROAMING_LEAGUE,
                0
        );
        if (npcUuid == null) {
            npcUuid = RoamingTrainerManager.spawnForAdventureGuild(player, safeRarity, SOURCE_ROAMING_LEAGUE, 0);
        }
        if (npcUuid == null) {
            if (!free && cost > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(cost), "adventurer_roaming_league_refund");
            player.sendSystemMessage(Component.literal("Could not summon an Adventurer nearby. Move to a safer open area and try again.").withStyle(ChatFormatting.RED));
            return false;
        }

        NPCEntity npc = RoamingTrainerManager.findTrainerNpc(player.getServer(), npcUuid);
        if (npc == null || !RoamingTrainerManager.tryStartChallenge(player, npc)) {
            RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid);
            if (!free && cost > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(cost), "adventurer_roaming_league_refund");
            player.sendSystemMessage(Component.literal("The Adventurer arrived, but the battle could not start. Please try again.").withStyle(ChatFormatting.RED));
            return false;
        }
        try {
            PluginTrainerBattleStarter.StartResult battle = PluginTrainerBattleStarter.start(
                    player, npc, BattleContextManager.BattleType.ADVENTURE_ROAMING, SOURCE_ROAMING_LEAGUE,
                    BattleFormat.Companion.getGEN_9_SINGLES(), false, false);
            if (!battle.started()) {
                RoamingTrainerManager.releaseChallenge(npcUuid, player.getUUID());
                RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid);
                if (!free && cost > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(cost), "adventurer_roaming_league_refund");
                player.sendSystemMessage(Component.literal("The Adventurer arrived, but the battle could not start. Please try again.").withStyle(ChatFormatting.RED));
                return false;
            }
        } catch (Exception exception) {
            RoamingTrainerManager.releaseChallenge(npcUuid, player.getUUID());
            RoamingTrainerManager.removeTrainerSilently(player.getServer(), npcUuid);
            if (!free && cost > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(cost), "adventurer_roaming_league_refund");
            player.sendSystemMessage(Component.literal("The Adventurer battle failed to start. Please try again.").withStyle(ChatFormatting.RED));
            return false;
        }

        data.lastRoamingLeagueStartMillis = now;
        if (free) data.roamingLeagueDailySpawns++;
        markDirty(player);
        savePlayer(player);
        player.closeContainer();
        AdventureGuideManager.increment(player, "adventurer_request", 1);
        player.sendSystemMessage(Component.literal("A " + AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(safeRarity)) + " has arrived nearby.").withStyle(safeRarity.color));
        return true;
    }

    public static void completeBattleTowerFloor(ServerPlayer player) { completeBattleTowerFloor(player, null); }

    public static void completeBattleTowerFloor(ServerPlayer player, RoamingTrainerManager.RoamingTrainerData trainerData) {
        if (player == null) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        int floor = trainerData != null && trainerData.towerFloor > 0 ? trainerData.towerFloor : data.activeTowerFloor;
        if (floor <= 0) floor = 1;
        int maxFloor = Math.max(1, AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor);
        boolean checkpointReached = isCheckpointFloor(floor) || floor >= maxFloor;

        data.bestTowerFloor = Math.max(data.bestTowerFloor, floor);
        if (checkpointReached) {
            claimTowerTierReward(player, data, floor);
            healParty(player);
            AdventureGuideManager.increment(player, "battle_tower_checkpoint", 1);
            com.champutils.worldfirst.WorldFirstManager.award(player, "first_battle_tower_" + floor);
            com.champutils.cosmetic.TitleManager.unlock(player, "tower_floor_" + floor);
            player.sendSystemMessage(Component.literal("Checkpoint " + floor + " cleared. Your party has been healed.").withStyle(ChatFormatting.GOLD));
        }
        clearActiveTower(data);

        if (floor >= maxFloor) {
            data.towerClears++;
            addRenown(data, Math.max(0, AdventurerGuildConfig.SETTINGS.battleTowerClearBonusRenown));
            addMarks(data, Math.max(0, AdventurerGuildConfig.SETTINGS.battleTowerClearBonusMarks));
            if (AdventurerGuildConfig.SETTINGS.battleTowerClearBonusCredits > 0)
                EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(AdventurerGuildConfig.SETTINGS.battleTowerClearBonusCredits), "adventurer_battle_tower_clear");
            if (data.ultimateClimbActive) awardUltimateClimb(player, data);
            data.ultimateClimbActive = false; data.ultimateClimbStartedMillis = 0L;
            data.towerFloor = 91; data.lastTowerEndMillis = System.currentTimeMillis();
            com.champutils.worldfirst.WorldFirstManager.award(player, "first_battle_tower_100");
            sendToSpawn(player); markDirty(player); savePlayer(player); notifyRankProgress(player, data);
            player.sendSystemMessage(Component.literal("Battle Tower cleared!").withStyle(ChatFormatting.GOLD));
            return;
        }
        data.towerFloor = floor + 1;
        markDirty(player); savePlayer(player); notifyRankProgress(player, data);
        BattleTowerContinueMenu.open(player, floor, checkpointReached);
    }

    private static void claimTowerTierReward(ServerPlayer player, AdventurerGuildDataManager.PlayerData data, int floor) {
        AdventurerGuildConfig.BattleTowerFloor reward = AdventurerGuildConfig.floor(floor);
        String key = "floor_" + floor;
        long now = System.currentTimeMillis();
        long cooldown = Math.max(1, AdventurerGuildConfig.SETTINGS.battleTowerRewardCooldownHours) * 3_600_000L;
        long last = data.towerRewardClaims.getOrDefault(key, 0L);
        if (now - last < cooldown) {
            player.sendSystemMessage(Component.literal("Floor " + floor + " tier reward was already claimed. Available again in " + secondsLeft(cooldown - (now-last)) + ".").withStyle(ChatFormatting.YELLOW));
            return;
        }
        data.towerRewardClaims.put(key, now);
        addRenown(data, Math.max(0,reward.rewardRenown)); addMarks(data, Math.max(0,reward.rewardMarks));
        if (reward.rewardCredits > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(reward.rewardCredits), "adventurer_battle_tower_tier:"+floor);
        if (reward.crateCreditId != null && !reward.crateCreditId.isBlank() && reward.crateCreditAmount > 0)
            CrateCreditManager.addCredits(player, reward.crateCreditId, reward.crateCreditAmount);
        runRewardCommands(player, reward.rewardCommands);
        player.sendSystemMessage(Component.literal("Daily floor " + floor + " reward claimed: " + reward.crateCreditAmount + " " + reward.crateCreditId.toUpperCase(Locale.ROOT) + " Rank Crate Credit(s).").withStyle(ChatFormatting.GREEN));
    }

    private static void awardUltimateClimb(ServerPlayer player, AdventurerGuildDataManager.PlayerData data) {
        data.ultimateClimbClears++;
        CrateCreditManager.addCredits(player, AdventurerGuildConfig.SETTINGS.ultimateClimbCrateCreditId, Math.max(1, AdventurerGuildConfig.SETTINGS.ultimateClimbCrateCredits));
        if (AdventurerGuildConfig.SETTINGS.ultimateClimbBonusCredits > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(AdventurerGuildConfig.SETTINGS.ultimateClimbBonusCredits), "battle_tower_ultimate_clear");
        addRenown(data, AdventurerGuildConfig.SETTINGS.ultimateClimbBonusRenown); addMarks(data, AdventurerGuildConfig.SETTINGS.ultimateClimbBonusMarks);
        com.champutils.cosmetic.TitleManager.unlock(player, "ultimate_tower_conqueror");
        com.champutils.worldfirst.WorldFirstManager.award(player, "first_ultimate_battle_tower_clear");
        player.sendSystemMessage(Component.literal("ULTIMATE CLIMB COMPLETE! You earned an S Rank Crate Credit and the Ultimate Tower Conqueror title.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static void completeRoamingLeagueTrainer(ServerPlayer player, RoamingTrainerManager.RoamingTrainerData trainerData) {
        if (player == null || trainerData == null) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        RoamingTrainerRarity rarity = trainerData.rarity == null ? RoamingTrainerRarity.F : trainerData.rarity;
        AdventurerGuildConfig.RoamingLeagueEntry entry = AdventurerGuildConfig.roamingEntry(rarity);
        addRenown(data, Math.max(0, entry.rewardRenown));
        addMarks(data, Math.max(0, entry.rewardMarks));
        runRewardCommands(player, entry.rewardCommands);
        markDirty(player);
        savePlayer(player);
        player.sendSystemMessage(Component.literal("Adventurer request reward: +" + entry.rewardRenown + " Adventurer XP, +" + entry.rewardMarks + " Adventurer's Marks.").withStyle(rarity.color));
        notifyRankProgress(player, data);
    }

    public static boolean setBattleTowerFloorLocation(ServerPlayer player, int floorNumber) {
        if (player == null) return false;
        int floor = Math.max(1, Math.min(100, floorNumber));
        AdventurerGuildConfig.BattleTowerFloor entry = AdventurerGuildConfig.ensureFloor(floor);
        entry.locationSet = true;
        entry.world = player.serverLevel().dimension().location().toString();
        entry.x = player.getX();
        entry.y = player.getY();
        entry.z = player.getZ();
        entry.yaw = player.getYRot();
        entry.pitch = player.getXRot();
        AdventurerGuildConfig.save();
        forceTowerChunks(player.serverLevel(), towerPlacement(entry));
        player.sendSystemMessage(Component.literal("Set Battle Tower floor " + floor + " center to " + entry.world + " @ "
                + String.format(Locale.US, "%.1f %.1f %.1f", entry.x, entry.y, entry.z) + ". Player and trainer will spawn 7 blocks apart facing each other.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean claimDailyPvp(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = getData(player);
        if (data.dailyPvpClaimed) {
            player.sendSystemMessage(Component.literal("You already claimed today's PvP quest.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (data.dailyPvpWins < Math.max(1, AdventurerGuildConfig.SETTINGS.pvpDailyRequiredWins)) {
            player.sendSystemMessage(Component.literal("Daily PvP quest is not complete yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        data.dailyPvpClaimed = true;
        awardConfigured(player, data, AdventurerGuildConfig.SETTINGS.pvpDailyRewardCredits, AdventurerGuildConfig.SETTINGS.pvpDailyRewardRenown, AdventurerGuildConfig.SETTINGS.pvpDailyRewardMarks, AdventurerGuildConfig.SETTINGS.pvpDailyRewardCommands, "adventurer_daily_pvp");
        player.sendSystemMessage(Component.literal("Daily PvP quest claimed!").withStyle(ChatFormatting.GREEN));
        markDirty(player);
        savePlayer(player);
        return true;
    }

    public static boolean claimWeeklyPvp(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = getData(player);
        if (data.weeklyPvpClaimed) {
            player.sendSystemMessage(Component.literal("You already claimed this week's PvP quest.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (data.weeklyPvpMatches < Math.max(1, AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredMatches)
                || data.weeklyPvpWins < Math.max(0, AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredWins)) {
            player.sendSystemMessage(Component.literal("Weekly PvP quest is not complete yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        data.weeklyPvpClaimed = true;
        awardConfigured(player, data, AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardCredits, AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardRenown, AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardMarks, AdventurerGuildConfig.SETTINGS.pvpWeeklyRewardCommands, "adventurer_weekly_pvp");
        player.sendSystemMessage(Component.literal("Weekly PvP quest claimed!").withStyle(ChatFormatting.GREEN));
        markDirty(player);
        savePlayer(player);
        return true;
    }

    public static boolean claimNextRankReward(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = getData(player);
        AdventurerGuildConfig.RankDefinition bestClaimable = null;
        if (AdventurerGuildConfig.SETTINGS.ranks != null) {
            for (AdventurerGuildConfig.RankDefinition rank : AdventurerGuildConfig.SETTINGS.ranks) {
                if (rank == null || rank.id == null) continue;
                boolean hasReward = rank.rewardCredits > 0 || rank.rewardMarks > 0 || (rank.rewardCommands != null && !rank.rewardCommands.isEmpty());
                if (!hasReward) continue;
                if (data.renown < Math.max(0, rank.renownRequired)) continue;
                if (data.claimedRankRewards.contains(rank.id.toUpperCase(Locale.ROOT))) continue;
                if (bestClaimable == null || rank.renownRequired < bestClaimable.renownRequired) bestClaimable = rank;
            }
        }
        if (bestClaimable == null) {
            player.sendSystemMessage(Component.literal("No Adventurer Rank reward is ready to claim.").withStyle(ChatFormatting.RED));
            return false;
        }
        data.claimedRankRewards.add(bestClaimable.id.toUpperCase(Locale.ROOT));
        awardConfigured(player, data, bestClaimable.rewardCredits, 0, bestClaimable.rewardMarks, bestClaimable.rewardCommands, "adventurer_rank:" + bestClaimable.id);
        player.sendSystemMessage(Component.literal("Claimed " + bestClaimable.displayName + " rank reward.").withStyle(ChatFormatting.GOLD));
        markDirty(player);
        savePlayer(player);
        return true;
    }

    public static String currentRankId(ServerPlayer player) {
        AdventurerGuildDataManager.PlayerData data = getData(player);
        AdventurerGuildConfig.RankDefinition rank = AdventurerGuildConfig.currentRank(data.renown);
        return rank == null || rank.id == null ? "F" : AdventurerRankUtil.normalizeRank(rank.id);
    }

    public static boolean hasRank(ServerPlayer player, String requiredRank) {
        return AdventurerRankUtil.atLeast(currentRankId(player), requiredRank);
    }

    public static boolean spendGuildMarks(ServerPlayer player, long amount) {
        if (player == null) return false;
        long safe = Math.max(0L, amount);
        AdventurerGuildDataManager.PlayerData data = getData(player);
        if (safe <= 0L) return true;
        if (data.guildMarks < safe) return false;
        data.guildMarks -= safe;
        markDirty(player);
        savePlayer(player);
        return true;
    }

    public static void addGuildMarks(ServerPlayer player, long amount) {
        if (player == null || amount <= 0L) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        data.guildMarks = Math.max(0L, data.guildMarks + amount);
        markDirty(player);
        savePlayer(player);
    }


    public static void awardGuildActivity(ServerPlayer player, int renown, int marks, String reason) {
        if (player == null || !AdventurerGuildConfig.SETTINGS.enabled) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        int safeRenown = Math.max(0, renown);
        int baseMarks = Math.max(0, marks);
        double marksBonus = BuffManager.getTotalBuff(
                BuffContext.builder(player, BuffContext.Source.GUILD_ACTIVITY).build(),
                BuffType.ADVENTURER_MARKS
        );
        int bonusMarks = baseMarks <= 0 ? 0 : (int) Math.floor(baseMarks * marksBonus);
        double fractionalMark = baseMarks * marksBonus - bonusMarks;
        if (fractionalMark > 0.0D && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < fractionalMark) bonusMarks++;
        int safeMarks = baseMarks + bonusMarks;
        addRenown(data, safeRenown);
        addMarks(data, safeMarks);
        markDirty(player);
        savePlayer(player);
        notifyRankProgress(player, data);
        if (safeRenown > 0 || safeMarks > 0) {
            player.sendSystemMessage(Component.literal("Adventurer's Guild: +" + safeRenown + " XP, +" + safeMarks + " Adventurer's Marks" + (reason == null || reason.isBlank() ? "." : " from " + reason + ".")).withStyle(ChatFormatting.GOLD));
        }
    }

    public static String dailyKey() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        LocalDate date = now.toLocalDate();
        LocalDateTime reset = date.atTime(AdventurerGuildConfig.SETTINGS.dailyResetHour, AdventurerGuildConfig.SETTINGS.dailyResetMinute);
        if (now.isBefore(reset)) date = date.minusDays(1);
        return date.toString();
    }

    public static String weeklyKey() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        DayOfWeek resetDay = AdventurerGuildConfig.weeklyResetDay();
        LocalDate date = now.toLocalDate();
        while (date.getDayOfWeek() != resetDay) date = date.minusDays(1);
        LocalDateTime reset = date.atTime(AdventurerGuildConfig.SETTINGS.weeklyResetHour, AdventurerGuildConfig.SETTINGS.weeklyResetMinute);
        if (now.isBefore(reset)) date = date.minusWeeks(1);
        WeekFields wf = WeekFields.ISO;
        return date.getYear() + "-W" + String.format("%02d", date.get(wf.weekOfWeekBasedYear()));
    }

    public static boolean isDailyPvpReady(AdventurerGuildDataManager.PlayerData data) {
        return data != null && !data.dailyPvpClaimed && data.dailyPvpWins >= Math.max(1, AdventurerGuildConfig.SETTINGS.pvpDailyRequiredWins);
    }

    public static boolean isWeeklyPvpReady(AdventurerGuildDataManager.PlayerData data) {
        return data != null && !data.weeklyPvpClaimed
                && data.weeklyPvpMatches >= Math.max(1, AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredMatches)
                && data.weeklyPvpWins >= Math.max(0, AdventurerGuildConfig.SETTINGS.pvpWeeklyRequiredWins);
    }

    public static boolean isAttemptingBattleTower(ServerPlayer player) {
        return player != null && getData(player).activeTowerFloor > 0;
    }

    public static boolean selectTowerCheckpoint(ServerPlayer player, int checkpointFloor) {
        if (player == null) return false;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        if (data.activeTowerFloor > 0) {
            player.sendSystemMessage(Component.literal("Finish or fail the current Battle Tower attempt before changing checkpoints.").withStyle(ChatFormatting.RED));
            return false;
        }
        int checkpoint = normalizeCheckpoint(checkpointFloor);
        if (!isCheckpointUnlocked(data, checkpoint)) {
            player.sendSystemMessage(Component.literal("That Battle Tower checkpoint is not unlocked yet.").withStyle(ChatFormatting.RED));
            return false;
        }
        data.towerFloor = checkpoint;
        markDirty(player);
        savePlayer(player);
        player.sendSystemMessage(Component.literal("Battle Tower start floor set to checkpoint " + checkpoint + ".").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static boolean isCheckpointUnlocked(AdventurerGuildDataManager.PlayerData data, int checkpointFloor) {
        int checkpoint = normalizeCheckpoint(checkpointFloor);
        return checkpoint <= 1 || (data != null && data.bestTowerFloor >= checkpoint);
    }

    public static String timeUntilRoamingReady(AdventurerGuildDataManager.PlayerData data) {
        if (data == null) return "Ready";
        long cooldown = Math.max(0, AdventurerGuildConfig.SETTINGS.roamingLeagueCooldownMinutes) * 60_000L;
        long left = cooldown - (System.currentTimeMillis() - data.lastRoamingLeagueStartMillis);
        return left <= 0L ? "Ready" : secondsLeft(left);
    }

    public static String timeUntilTowerReady(AdventurerGuildDataManager.PlayerData data) {
        if (data == null) return "Ready";
        long cooldown = Math.max(0, AdventurerGuildConfig.SETTINGS.battleTowerCooldownSeconds) * 1000L;
        long left = cooldown - (System.currentTimeMillis() - data.lastTowerEndMillis);
        return left <= 0L ? "Ready" : secondsLeft(left);
    }

    public static void savePlayer(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        if (!DIRTY.contains(profileId)) return;
        AdventurerGuildDataManager.PlayerData data = CACHE.get(profileId);
        if (data != null) AdventurerGuildDataManager.save(profileId, data);
        DIRTY.remove(profileId);
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        if (data.activeTowerFloor > 0 || data.ultimateClimbActive) {
            data.ultimateClimbActive = false; data.ultimateClimbStartedMillis = 0L; data.lastTowerEndMillis = System.currentTimeMillis();
            removeActiveTowerPokemon(player, data);
            clearActiveTower(data); data.towerFloor = checkpointForBest(data.bestTowerFloor); markDirty(player);
        }
        unloadPlayer(player);
    }

    public static void unloadPlayer(ServerPlayer player) {
        if (player == null) return;
        savePlayer(player);
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId != null) CACHE.remove(profileId);
    }

    public static void saveAll() {
        for (UUID profileId : new HashSet<>(DIRTY)) {
            AdventurerGuildDataManager.PlayerData data = CACHE.get(profileId);
            if (data != null) AdventurerGuildDataManager.save(profileId, data);
        }
        DIRTY.clear();
    }

    public static void reload() {
        AdventurerGuildConfig.load();
    }

    private static void recordQueuedPvp(ServerPlayer player, boolean ranked, boolean won) {
        AdventurerGuildDataManager.PlayerData data = getData(player);
        data.dailyPvpMatches++;
        data.weeklyPvpMatches++;
        if (won) {
            data.dailyPvpWins++;
            data.weeklyPvpWins++;
            if (ranked) data.weeklyRankedWins++;
        }
        markDirty(player);
        savePlayer(player);
        if (isDailyPvpReady(data) || isWeeklyPvpReady(data)) {
            player.sendSystemMessage(Component.literal("PvP quest ready! Open the Adventurer Board to claim.").withStyle(ChatFormatting.GOLD));
        }
    }

    private static void awardConfigured(ServerPlayer player, AdventurerGuildDataManager.PlayerData data, int credits, int renown, int marks, List<String> commands, String reason) {
        if (credits > 0) EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(credits), reason);
        addRenown(data, Math.max(0, renown));
        addMarks(data, Math.max(0, marks));
        runRewardCommands(player, commands);
        notifyRankProgress(player, data);
    }

    private static void runRewardCommands(ServerPlayer player, List<String> commands) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null || commands == null) return;
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw.replace("%player%", player.getName().getString()).replace("%uuid%", PlayerProfileManager.activeProfileId(player).toString());
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
        }
    }

    private static void refreshPeriods(AdventurerGuildDataManager.PlayerData data) {
        if (data == null) return;
        String daily = dailyKey();
        if (!daily.equals(data.dailyKey)) {
            data.dailyKey = daily;
            data.dailyPvpMatches = 0;
            data.dailyPvpWins = 0;
            data.dailyPvpClaimed = false;
            data.roamingLeagueDailyKey = daily;
            data.roamingLeagueDailySpawns = 0;
        } else if (!daily.equals(data.roamingLeagueDailyKey)) {
            data.roamingLeagueDailyKey = daily;
            data.roamingLeagueDailySpawns = 0;
        }
        String weekly = weeklyKey();
        if (!weekly.equals(data.weeklyKey)) {
            data.weeklyKey = weekly;
            data.weeklyPvpMatches = 0;
            data.weeklyPvpWins = 0;
            data.weeklyRankedWins = 0;
            data.weeklyPvpClaimed = false;
        }
    }

    private static void clearActiveTower(AdventurerGuildDataManager.PlayerData data) {
        if (data == null) return;
        data.activeTowerFloor = 0;
        data.activeTowerNpcUuid = "";
        data.activeTowerStartedMillis = 0L;
    }

    private static void addRenown(AdventurerGuildDataManager.PlayerData data, int amount) {
        if (data == null || amount <= 0) return;
        data.renown = Math.max(0L, data.renown + amount);
    }

    private static void addMarks(AdventurerGuildDataManager.PlayerData data, int amount) {
        if (data == null || amount <= 0) return;
        data.guildMarks = Math.max(0L, data.guildMarks + amount);
    }

    private static void notifyRankProgress(ServerPlayer player, AdventurerGuildDataManager.PlayerData data) {
        if (player == null || data == null) return;
        AdventurerGuildConfig.RankDefinition current = AdventurerGuildConfig.currentRank(data.renown);
        AdventurerGuildConfig.RankDefinition next = AdventurerGuildConfig.nextRank(data.renown);
        if (current != null && current.id != null) {
            String rankId = AdventurerRankUtil.normalizeRank(current.id);
            if (AdventurerRankUtil.rankIndex(rankId) > 0) {
                com.champutils.cosmetic.TitleManager.unlock(player, "adventurer_rank_" + rankId.toLowerCase(Locale.ROOT));
            }
        }
        if (next == null && current != null) {
            player.sendSystemMessage(Component.literal("Adventurer Rank: " + current.displayName + " (max rank reached)").withStyle(ChatFormatting.GOLD));
        }
    }


    private static boolean routeIslanderAdventurerRequest(ServerPlayer player, RoamingTrainerRarity rarity) {
        TerritoryRepository.Territory territory = TerritoryRepository.cachedPersonal(player);
        if (territory == null) {
            player.sendSystemMessage(Component.literal("Create your Islander territory first with /territory create.").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        if (!territory.isReady()) {
            player.sendSystemMessage(Component.literal("Your Islander territory is still being prepared. Try again when it is ready.").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        String targetServer = territory.serverId == null ? "" : territory.serverId.trim();
        if (!targetServer.isBlank() && !targetServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            PendingIslanderRequest pending = new PendingIslanderRequest();
            pending.targetServerId = targetServer;
            pending.rarity = rarity.name();
            pending.expiresAtMillis = System.currentTimeMillis() + 120_000L;
            SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_ISLANDER_REQUEST_KEY, pending)
                    .whenComplete((ignored, error) -> player.server.execute(() -> {
                        if (!SafeTeleportManager.isLive(player)) return;
                        if (error != null || !TerritoryTeleportUtil.teleportHome(player, territory)) {
                            clearPendingIslanderRequest(player.getUUID());
                            player.sendSystemMessage(Component.literal("Could not route the Adventurer request to your territory server.").withStyle(ChatFormatting.RED));
                        }
                    }));
            return true;
        }

        if (!TerritoryTeleportUtil.teleportHome(player, territory)) {
            player.sendSystemMessage(Component.literal("Could not teleport to your Islander territory. Try again shortly.").withStyle(ChatFormatting.RED));
            return false;
        }
        player.closeContainer();
        player.sendSystemMessage(Component.literal("Summoning your Adventurer at your territory...").withStyle(ChatFormatting.YELLOW));
        java.util.concurrent.CompletableFuture.runAsync(() -> {}, java.util.concurrent.CompletableFuture.delayedExecutor(500L, java.util.concurrent.TimeUnit.MILLISECONDS))
                .thenRun(() -> player.server.execute(() -> {
                    if (SafeTeleportManager.isLive(player)) startRoamingLeague(player, rarity, true);
                }));
        return true;
    }

    private static boolean shouldRtpBeforeAdventurerRequest(ServerPlayer player) {
        if (player == null) return false;
        String id = player.serverLevel().dimension().location().toString().toLowerCase(Locale.ROOT);
        return id.equals("multiworld:spawn1")
                || id.equals("minecraft:spawn1")
                || id.equals("spawn1")
                || id.endsWith(":spawn1")
                || id.contains("spawn1");
    }

    private static void awardTowerCheckpoint(ServerPlayer player, AdventurerGuildDataManager.PlayerData data, int fromFloor, int toFloor) {
        if (player == null || data == null) return;
        int start = Math.max(1, fromFloor);
        int end = Math.max(start, toFloor);
        for (int i = start; i <= end; i++) {
            AdventurerGuildConfig.BattleTowerFloor reward = AdventurerGuildConfig.floor(i);
            addRenown(data, Math.max(0, reward.rewardRenown));
            addMarks(data, Math.max(0, reward.rewardMarks));
            if (reward.rewardCredits > 0) {
                EconomyManager.depositAsync(player, EconomyManager.wholeCreditsToCents(reward.rewardCredits), "adventurer_battle_tower_checkpoint:" + i);
            }
            runRewardCommands(player, reward.rewardCommands);
        }
    }

    private static void healParty(ServerPlayer player) {
        try {
            BattlePrepManager.healParty(player);
        } catch (Throwable ignored) {
        }
    }

    private static int normalizeCheckpoint(int floor) {
        int max=Math.max(10,AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor);
        int safe=Math.max(1,Math.min(max,floor));
        return safe <= 1 ? 1 : ((safe-1)/10)*10+1;
    }
    private static int checkpointForBest(int bestFloor) { return bestFloor < 10 ? 1 : Math.min(91,(bestFloor/10)*10+1); }
    private static int previousCheckpointFloor(int floor) { return Math.max(0,((Math.max(1,floor)-1)/10)*10); }
    private static boolean isCheckpointFloor(int floor) { return floor % 10 == 0 || floor >= Math.max(1,AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor); }


    private record TowerPlacement(Vec3 playerPos, float playerYaw, Vec3 npcPos, float npcYaw) {}

    private static TowerPlacement towerPlacement(AdventurerGuildConfig.BattleTowerFloor floor) {
        double radians = Math.toRadians(floor.yaw);
        double dx = -Math.sin(radians);
        double dz = Math.cos(radians);
        Vec3 center = new Vec3(floor.x, floor.y, floor.z);
        Vec3 playerPos = center.add(dx * -7.0D, 0.0D, dz * -7.0D);
        Vec3 npcPos = center.add(dx * 7.0D, 0.0D, dz * 7.0D);
        float playerYaw = floor.yaw;
        float npcYaw = wrapYaw(floor.yaw + 180.0F);
        return new TowerPlacement(playerPos, playerYaw, npcPos, npcYaw);
    }

    private static float wrapYaw(float yaw) {
        float wrapped = yaw % 360.0F;
        if (wrapped > 180.0F) wrapped -= 360.0F;
        if (wrapped < -180.0F) wrapped += 360.0F;
        return wrapped;
    }

    private static ServerLevel resolveTowerLevel(ServerPlayer player, AdventurerGuildConfig.BattleTowerFloor floor) {
        if (player == null || floor == null) return null;
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        String configuredWorld = floor.world == null ? "" : floor.world.trim();
        if (configuredWorld.isBlank()) return player.serverLevel();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equalsIgnoreCase(configuredWorld)) {
                return level;
            }
        }
        return null;
    }

    /**
     * Keeps every configured Battle Tower arena loaded on this physical server. Forced chunks are
     * dimension-specific and persist independently of where players currently are, allowing a climb
     * to begin from spawn, survival, an Islander territory, or any other world on this server.
     */
    private static void ensureBattleTowerChunksLoaded(MinecraftServer server) {
        if (server == null) return;
        Set<String> forced = new HashSet<>();
        int maxFloor = Math.max(1, Math.min(100, AdventurerGuildConfig.SETTINGS.battleTowerMaxFloor));
        for (int floor = 1; floor <= maxFloor; floor++) {
            AdventurerGuildConfig.BattleTowerFloor floorData = AdventurerGuildConfig.floor(floor);
            if (floorData == null || !floorData.locationSet) continue;
            ServerLevel level = resolveTowerLevel(server, floorData);
            if (level == null) continue;
            TowerPlacement placement = towerPlacement(floorData);
            forceTowerChunks(level, placement, forced);
        }
    }

    private static ServerLevel resolveTowerLevel(MinecraftServer server, AdventurerGuildConfig.BattleTowerFloor floor) {
        if (server == null || floor == null || floor.world == null || floor.world.isBlank()) return null;
        String configuredWorld = floor.world.trim();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equalsIgnoreCase(configuredWorld)) return level;
        }
        return null;
    }

    private static boolean loadTowerChunks(ServerLevel level, TowerPlacement placement) {
        if (level == null || placement == null) return false;
        try {
            forceTowerChunks(level, placement);
            loadChunkAt(level, placement.playerPos);
            loadChunkAt(level, placement.npcPos);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void forceTowerChunks(ServerLevel level, TowerPlacement placement) {
        forceTowerChunks(level, placement, new HashSet<>());
    }

    private static void forceTowerChunks(ServerLevel level, TowerPlacement placement, Set<String> dedupe) {
        if (level == null || placement == null) return;
        forceChunkAt(level, placement.playerPos, dedupe);
        forceChunkAt(level, placement.npcPos, dedupe);
    }

    private static void forceChunkAt(ServerLevel level, Vec3 pos, Set<String> dedupe) {
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;
        String key = level.dimension().location() + ":" + chunkX + ":" + chunkZ;
        if (dedupe != null && !dedupe.add(key)) return;
        level.setChunkForced(chunkX, chunkZ, true);
        level.getChunk(chunkX, chunkZ);
    }

    private static void loadChunkAt(ServerLevel level, Vec3 pos) {
        int chunkX = ((int) Math.floor(pos.x)) >> 4;
        int chunkZ = ((int) Math.floor(pos.z)) >> 4;
        level.getChunk(chunkX, chunkZ);
    }

    private static boolean teleportTo(ServerPlayer player, ServerLevel target, Vec3 pos, float yaw, float pitch) {
        if (player == null || target == null || pos == null) return false;
        if (target != player.serverLevel()) {
            return invokeServerLevelTeleport(player, target, pos.x, pos.y, pos.z, yaw, pitch);
        }
        try {
            player.teleportTo(pos.x, pos.y, pos.z);
            player.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean teleportToTowerFloor(ServerPlayer player, AdventurerGuildConfig.BattleTowerFloor floor) {
        ServerLevel target = resolveTowerLevel(player, floor);
        return target != null && teleportTo(player, target, new Vec3(floor.x, floor.y, floor.z), floor.yaw, floor.pitch);
    }

    private static boolean invokeServerLevelTeleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        if (player == null || level == null) return false;
        for (Method method : ServerPlayer.class.getMethods()) {
            if (!method.getName().equals("teleportTo")) continue;
            Class<?>[] types = method.getParameterTypes();
            try {
                if (types.length == 7 && ServerLevel.class.isAssignableFrom(types[0])) {
                    method.invoke(player, level, x, y, z, Set.of(), yaw, pitch);
                    return true;
                }
                if (types.length == 8 && ServerLevel.class.isAssignableFrom(types[0])) {
                    method.invoke(player, level, x, y, z, Set.of(), yaw, pitch, true);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public static boolean isInsideBattleTower(ServerPlayer player) {
        if (player == null) return false;
        String world = player.serverLevel().dimension().location().toString();
        for (AdventurerGuildConfig.BattleTowerFloor floor : AdventurerGuildConfig.SETTINGS.battleTowerFloors) {
            if (floor == null || !floor.locationSet || floor.world == null || !floor.world.equalsIgnoreCase(world)) continue;
            double dx = player.getX() - floor.x;
            double dy = player.getY() - floor.y;
            double dz = player.getZ() - floor.z;
            if (dx * dx + dz * dz <= 144.0D && Math.abs(dy) <= 8.0D) return true;
        }
        return false;
    }

    public static boolean isBattleTowerDestination(ServerLevel level, double x, double y, double z) {
        if (level == null) return false;
        String world = level.dimension().location().toString();
        for (AdventurerGuildConfig.BattleTowerFloor floor : AdventurerGuildConfig.SETTINGS.battleTowerFloors) {
            if (floor == null || !floor.locationSet || floor.world == null || !floor.world.equalsIgnoreCase(world)) continue;
            double dx = x - floor.x, dy = y - floor.y, dz = z - floor.z;
            if (dx * dx + dz * dz <= 144.0D && Math.abs(dy) <= 8.0D) return true;
        }
        return false;
    }

    public static void continueBattleTower(ServerPlayer player) {
        if (player == null) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        if (data.activeTowerFloor > 0) return;
        startBattleTowerFloor(player, true);
    }

    public static void giveUpBattleTower(ServerPlayer player) {
        if (player == null) return;
        AdventurerGuildDataManager.PlayerData data = getData(player);
        data.lastTowerEndMillis = System.currentTimeMillis();
        data.ultimateClimbActive = false; data.ultimateClimbStartedMillis = 0L;
        data.towerFloor = checkpointForBest(data.bestTowerFloor);
        markDirty(player); savePlayer(player); sendToSpawn(player);
        player.sendSystemMessage(Component.literal("Your Battle Tower run has ended.").withStyle(ChatFormatting.YELLOW));
    }

    private static void showFloorAnnouncement(ServerPlayer player, int floor) {
        if (player == null || player.getServer() == null) return;
        String name = player.getName().getString();
        try {
            var source = player.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(4);
            player.getServer().getCommands().performPrefixedCommand(source, "title " + name + " times 5 30 10");
            player.getServer().getCommands().performPrefixedCommand(source, "title " + name + " title {\"text\":\"FLOOR " + floor + "\",\"color\":\"gold\",\"bold\":true}");
            player.getServer().getCommands().performPrefixedCommand(source, "playsound minecraft:block.note_block.pling master " + name + " ~ ~ ~ 1 1");
        } catch (Exception ignored) {}
    }

    private static void removeActiveTowerPokemon(ServerPlayer player, AdventurerGuildDataManager.PlayerData data) {
        if (player == null || data == null || data.activeTowerNpcUuid == null || data.activeTowerNpcUuid.isBlank() || player.getServer() == null) return;
        try {
            UUID id = UUID.fromString(data.activeTowerNpcUuid);
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(id);
                if (entity != null) entity.discard();
            }
        } catch (Exception ignored) {}
    }

    public static void forfeitBattleTower(ServerPlayer player, String reason) {
        if (player == null) return;
        AdventurerGuildDataManager.PlayerData data=getData(player);
        if (data.activeTowerFloor <= 0) return;
        data.lastTowerEndMillis=System.currentTimeMillis();
        removeActiveTowerPokemon(player, data);
        clearActiveTower(data); data.ultimateClimbActive=false; data.ultimateClimbStartedMillis=0L; data.towerFloor=checkpointForBest(data.bestTowerFloor);
        sendToSpawn(player); markDirty(player); savePlayer(player);
        if(reason!=null&&!reason.isBlank()) player.sendSystemMessage(Component.literal(reason).withStyle(ChatFormatting.RED));
    }

    private static void sendToSpawn(ServerPlayer player) {
        if(player==null||player.getServer()==null)return;
        try { player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "spawn"); } catch(Exception ignored) {}
    }

    private static void markDirty(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId != null) DIRTY.add(profileId);
        com.champutils.chat.ChatTagResolver.invalidate(player);
    }

    private static String secondsLeft(long millis) {
        long seconds = Math.max(1L, (long)Math.ceil(millis / 1000.0D));
        long minutes = seconds / 60L;
        long leftover = seconds % 60L;
        if (minutes <= 0) return seconds + "s";
        return leftover <= 0 ? minutes + "m" : minutes + "m " + leftover + "s";
    }

    public static String pretty(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String[] parts = value.toLowerCase(Locale.ROOT).replace('_', ' ').split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
