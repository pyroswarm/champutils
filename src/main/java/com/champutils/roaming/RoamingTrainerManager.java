package com.champutils.roaming;

import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattleStateManager;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profile.ProfilePlaytimeManager;
import com.champutils.spawn.SpawnBlockRules;
import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.adventurer.AdventurerRankUtil;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

public final class RoamingTrainerManager {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, RoamingTrainerData> TRAINERS = new ConcurrentHashMap<>();
    private static final String ROAMING_TRAINER_TAG = "champutils_roaming_trainer";
    private static int ticksUntilScan = 20;
    private static int ticksUntilCleanup = 20;
    private static int ticksUntilOrphanCleanup = 600;

    private RoamingTrainerManager() {}

    public static class RoamingTrainerData {
        public UUID npcUuid;
        public UUID ownerPlayerUuid;
        public RoamingTrainerRarity rarity;
        public int targetLevel;
        public long lastNearbyPlayerMillis;
        public long spawnedMillis;
        public boolean rewardsClaimed;
        public String displayName;
        public UUID currentChallengerUuid;
        public long challengeLockMillis;
        public long lastProtectionMillis;
        public double spawnX;
        public double spawnY;
        public double spawnZ;
        public float spawnYaw;
        public long nextWanderMillis;
        public TrainerTier tier = TrainerTier.ROOKIE;
        public String adventureSource = "";
        public int towerFloor = 0;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ticksUntilCleanup--;
        if (ticksUntilCleanup <= 0) {
            ticksUntilCleanup = 20;
            cleanupAndDespawn(server);
        }
        if (!RoamingTrainerConfig.DATA.enabled) return;

        ticksUntilScan--;
        if (ticksUntilScan > 0) return;
        ticksUntilScan = Math.max(5, RoamingTrainerConfig.DATA.scanIntervalSeconds) * 20;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            trySpawnFor(player);
        }
    }

    public static boolean isRoamingTrainer(UUID npcUuid) {
        return npcUuid != null && TRAINERS.containsKey(npcUuid);
    }

    public static RoamingTrainerData get(UUID npcUuid) {
        return npcUuid == null ? null : TRAINERS.get(npcUuid);
    }


    public static boolean tryStartChallenge(ServerPlayer player, NPCEntity npc) {
        if (player == null || npc == null) return false;
        RoamingTrainerData data = TRAINERS.get(npc.getUUID());
        if (data == null) return true;

        UUID playerUuid = player.getUUID();
        UUID challenger = data.currentChallengerUuid;
        if (challenger != null && !challenger.equals(playerUuid)) {
            player.sendSystemMessage(Component.literal("§cThat trainer is already battling another player."));
            return false;
        }

        if (isNpcInBattle(npc) && (challenger == null || !challenger.equals(playerUuid))) {
            player.sendSystemMessage(Component.literal("§cThat trainer is already in battle."));
            return false;
        }

        data.currentChallengerUuid = playerUuid;
        data.challengeLockMillis = System.currentTimeMillis();

        // IMPORTANT: scale the roaming trainer at challenge/battle start time, not spawn time.
        // Players can change their party at a PC after the trainer spawns, so using the spawn-time
        // party would let them bait a low-level trainer and then swap to stronger Pokemon.
        data.targetLevel = playerPartyHighestLevelForRarity(player, data.rarity);
        RoamingTrainerPartyBuilder.apply(npc, data);

        applyRoamingProtections(npc, data);
        return true;
    }

    public static void releaseChallenge(UUID npcUuid, UUID playerUuid) {
        RoamingTrainerData data = TRAINERS.get(npcUuid);
        if (data == null) return;
        if (playerUuid == null || playerUuid.equals(data.currentChallengerUuid)) {
            data.currentChallengerUuid = null;
            data.challengeLockMillis = 0L;
        }
    }

    public static void handleBattleEnded(UUID npcUuid) {
        RoamingTrainerData data = TRAINERS.get(npcUuid);
        if (data == null) return;
        data.currentChallengerUuid = null;
        data.challengeLockMillis = 0L;
    }

    public static int despawnAll(MinecraftServer server) {
        if (server == null) return 0;
        int count = 0;

        // Remove all currently-tracked trainers first.
        for (RoamingTrainerData data : new ArrayList<>(TRAINERS.values())) {
            NPCEntity npc = findNpc(server, data.npcUuid);
            TRAINERS.remove(data.npcUuid);
            cancelBattleForRemovedTrainer(server, data, "The roaming trainer disappeared, so the battle was canceled.");
            if (npc != null) {
                removeNpc(npc);
                count++;
            }
        }

        // Also remove orphaned roaming trainer NPCs that still have our tag but are no longer in memory.
        // This is the part that makes /roamingtrainer despawn all work after /reload or server restart.
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof NPCEntity npc)) continue;
                if (!hasRoamingTrainerTag(npc)) continue;
                if (TRAINERS.containsKey(npc.getUUID())) continue;
                removeNpc(npc);
                count++;
            }
        }

        return count;
    }

    public static int despawnNearby(ServerLevel level, Vec3 center, double radius) {
        if (level == null || center == null) return 0;
        int count = 0;
        for (NPCEntity npc : new ArrayList<>(level.getEntitiesOfClass(NPCEntity.class, box(center, radius)))) {
            if (!isRoamingTrainerEntity(npc)) continue;
            RoamingTrainerData data = TRAINERS.remove(npc.getUUID());
            cancelBattleForRemovedTrainer(level.getServer(), data, "The roaming trainer disappeared, so the battle was canceled.");
            removeNpc(npc);
            count++;
        }
        return count;
    }

    public static boolean spawnManual(ServerPlayer player, RoamingTrainerRarity rarity) {
        if (player == null) return false;
        return spawnNearPlayer(player, rarity == null ? chooseRarity() : rarity, true) != null;
    }

    public static UUID spawnForAdventureGuild(ServerPlayer player, RoamingTrainerRarity rarity, String source, int towerFloor) {
        if (player == null) return null;
        RoamingTrainerData data = spawnNearPlayer(player, rarity == null ? RoamingTrainerRarity.F : rarity, true);
        if (data == null) return null;
        data.adventureSource = source == null ? "" : source;
        data.towerFloor = Math.max(0, towerFloor);
        NPCEntity npc = findNpc(player.getServer(), data.npcUuid);
        if (npc != null) {
            String label;
            if (AdventurerGuildManager.SOURCE_BATTLE_TOWER.equals(data.adventureSource)) {
                label = "Battle Tower Floor " + Math.max(1, data.towerFloor);
            } else if (AdventurerGuildManager.SOURCE_ROAMING_LEAGUE.equals(data.adventureSource)) {
                label = AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(data.rarity));
            } else {
                label = data.displayName;
            }
            data.displayName = label;
            try { npc.setCustomName(Component.literal(label).withStyle(data.rarity.color)); } catch (Exception ignored) {}
            try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        }
        return data.npcUuid;
    }


    public static UUID spawnForAdventureGuildAt(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, RoamingTrainerRarity rarity, String source, int towerFloor) {
        if (player == null || level == null || pos == null) return null;

        RoamingTrainerRarity safeRarity = rarity == null ? RoamingTrainerRarity.F : rarity;
        RoamingTrainerConfig.RaritySettings settings = RoamingTrainerConfig.settings(safeRarity);
        int targetLevel = playerPartyHighestLevelForRarity(player, safeRarity);
        TrainerIdentity identity = chooseIdentity(safeRarity, settings);
        String displayName = identity.displayName;
        String skin = identity.skin;

        ChampTrainerSpawner.SpawnResult result = ChampTrainerSpawner.spawnRoaming(level, pos, yaw, displayName, skin);
        if (!result.success || result.npc == null) return null;

        RoamingTrainerData data = new RoamingTrainerData();
        data.npcUuid = result.npc.getUUID();
        data.ownerPlayerUuid = player.getUUID();
        data.rarity = safeRarity;
        data.tier = tierFor(player);
        data.targetLevel = targetLevel;
        data.lastNearbyPlayerMillis = System.currentTimeMillis();
        data.spawnedMillis = data.lastNearbyPlayerMillis;
        data.displayName = displayName;
        data.spawnX = pos.x;
        data.spawnY = pos.y;
        data.spawnZ = pos.z;
        data.spawnYaw = yaw;
        data.adventureSource = source == null ? "" : source;
        data.towerFloor = Math.max(0, towerFloor);

        if (AdventurerGuildManager.SOURCE_BATTLE_TOWER.equals(data.adventureSource)) {
            data.displayName = "Battle Tower Floor " + Math.max(1, data.towerFloor);
        } else if (AdventurerGuildManager.SOURCE_ROAMING_LEAGUE.equals(data.adventureSource)) {
            data.displayName = AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(data.rarity));
        }

        try { result.npc.setCustomName(Component.literal(data.displayName).withStyle(data.rarity.color)); } catch (Exception ignored) {}
        try { result.npc.setCustomNameVisible(true); } catch (Exception ignored) {}

        TRAINERS.put(data.npcUuid, data);
        scheduleNextWander(data, data.spawnedMillis);
        applyRoamingProtections(result.npc, data);
        RoamingTrainerPartyBuilder.apply(result.npc, data);
        return data.npcUuid;
    }

    public static BattleContextManager.BattleType battleTypeFor(UUID npcUuid) {
        RoamingTrainerData data = get(npcUuid);
        if (data == null) return BattleContextManager.BattleType.NPC;
        if (AdventurerGuildManager.SOURCE_BATTLE_TOWER.equals(data.adventureSource)) return BattleContextManager.BattleType.ADVENTURE_TOWER;
        if (AdventurerGuildManager.SOURCE_ROAMING_LEAGUE.equals(data.adventureSource)) return BattleContextManager.BattleType.ADVENTURE_ROAMING;
        return BattleContextManager.BattleType.NPC;
    }

    public static void handleVictory(ServerPlayer winner, UUID losingNpcUuid) {
        if (winner == null || losingNpcUuid == null) return;
        RoamingTrainerData data = TRAINERS.get(losingNpcUuid);
        if (data == null || data.rewardsClaimed) return;
        data.rewardsClaimed = true;

        if (AdventurerGuildManager.SOURCE_BATTLE_TOWER.equals(data.adventureSource)) {
            AdventurerGuildManager.completeBattleTowerFloor(winner, data);
            NPCEntity npc = findNpc(winner.getServer(), losingNpcUuid);
            if (npc != null) removeNpc(npc);
            TRAINERS.remove(losingNpcUuid);
            return;
        }

        if (AdventurerGuildManager.SOURCE_ROAMING_LEAGUE.equals(data.adventureSource)) {
            AdventurerGuildManager.completeRoamingLeagueTrainer(winner, data);
        }

        RoamingTrainerConfig.RaritySettings settings = RoamingTrainerConfig.settings(data.rarity);
        int fragments = randomBetween(settings.fragmentMin, settings.fragmentMax);
        if (fragments > 0) {
            ItemStack stack = ProfessionFragmentManager.createFragmentStack(data.rarity.name(), fragments);
            if (stack != null && !stack.isEmpty()) {
                boolean added = winner.getInventory().add(stack);
                if (!added) winner.drop(stack, false);
                winner.sendSystemMessage(Component.literal("You received " + fragments + " " + AdventurerRankUtil.displayRank(AdventurerRankUtil.fromRarity(data.rarity)) + " Essence.").withStyle(data.rarity.color));
            }
        }

        runCommands(winner, settings.rewardCommands, data);
        winner.sendSystemMessage(Component.literal("You defeated a " + AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(data.rarity)) + "!").withStyle(data.rarity.color));

        NPCEntity npc = findNpc(winner.getServer(), losingNpcUuid);
        if (npc != null) {
            removeNpc(npc);
        }
        TRAINERS.remove(losingNpcUuid);
    }

    private static void trySpawnFor(ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        String dimensionId = level.dimension().location().toString();
        if (RoamingTrainerConfig.isBlockedDimension(dimensionId) || SpawnBlockRules.isBlockedSpawnLevel(level)) return;
        if (countNearbyRoamingTrainers(level, player.position(), RoamingTrainerConfig.DATA.activePlayerRadius) >= RoamingTrainerConfig.DATA.maxTrainersPerPlayer) return;
        if (countWorldRoamingTrainers(level) >= RoamingTrainerConfig.DATA.maxTrainersPerWorld) return;
        if (RANDOM.nextDouble() > RoamingTrainerConfig.DATA.spawnChancePerScan) return;
        spawnNearPlayer(player, chooseRarityFor(player), false);
    }

    private static RoamingTrainerData spawnNearPlayer(ServerPlayer player, RoamingTrainerRarity rarity, boolean force) {
        ServerLevel level = player.serverLevel();
        String dimensionId = level.dimension().location().toString();
        if (RoamingTrainerConfig.isBlockedDimension(dimensionId) || SpawnBlockRules.isBlockedSpawnLevel(level)) return null;

        Vec3 pos = findSpawnPosition(level, player.position());
        if (pos == null) return null;

        RoamingTrainerConfig.RaritySettings settings = RoamingTrainerConfig.settings(rarity);
        int targetLevel = playerPartyHighestLevelForRarity(player, rarity);
        TrainerIdentity identity = chooseIdentity(rarity, settings);
        String displayName = identity.displayName;
        String skin = identity.skin;
        ChampTrainerSpawner.SpawnResult result = ChampTrainerSpawner.spawnRoaming(level, pos, player.getYRot() + 180.0F, displayName, skin);
        if (!result.success || result.npc == null) return null;

        RoamingTrainerData data = new RoamingTrainerData();
        data.npcUuid = result.npc.getUUID();
        data.ownerPlayerUuid = player.getUUID();
        data.rarity = rarity;
        data.tier = tierFor(player);
        data.targetLevel = targetLevel;
        data.lastNearbyPlayerMillis = System.currentTimeMillis();
        data.spawnedMillis = data.lastNearbyPlayerMillis;
        data.displayName = displayName;
        data.spawnX = pos.x;
        data.spawnY = pos.y;
        data.spawnZ = pos.z;
        data.spawnYaw = player.getYRot() + 180.0F;
        TRAINERS.put(data.npcUuid, data);

        scheduleNextWander(data, data.spawnedMillis);

        applyRoamingProtections(result.npc, data);

        RoamingTrainerPartyBuilder.apply(result.npc, data);

        if (rarity.alertsPlayers()) {
            alertNearbyPlayers(level, result.npc.position(), rarity, displayName);
        }
        return data;
    }

    private static Vec3 findSpawnPosition(ServerLevel level, Vec3 origin) {
        String dimensionId = level.dimension().location().toString();
        boolean islanderWorld = RoamingTrainerConfig.isIslanderDimension(dimensionId);

        int min = islanderWorld
                ? Math.max(4, RoamingTrainerConfig.DATA.islanderSpawnMinDistance)
                : Math.max(8, RoamingTrainerConfig.DATA.spawnMinDistance);
        int max = islanderWorld
                ? Math.max(min, RoamingTrainerConfig.DATA.islanderSpawnMaxDistance)
                : Math.max(min, RoamingTrainerConfig.DATA.spawnMaxDistance);
        int attempts = islanderWorld
                ? Math.max(RoamingTrainerConfig.DATA.maxSpawnAttemptsPerPlayer, RoamingTrainerConfig.DATA.islanderMaxSpawnAttemptsPerPlayer)
                : RoamingTrainerConfig.DATA.maxSpawnAttemptsPerPlayer;

        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double distance = min + RANDOM.nextDouble() * (max - min);
            int x = (int)Math.floor(origin.x + Math.cos(angle) * distance);
            int z = (int)Math.floor(origin.z + Math.sin(angle) * distance);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 2) continue;
            net.minecraft.core.BlockPos candidate = new net.minecraft.core.BlockPos(x, y, z);
            if (SpawnBlockRules.isBlockedSpawnPosition(level, candidate)) continue;
            if (RoamingTrainerConfig.DATA.requireSolidGround) {
                if (level.getBlockState(candidate.below()).isAir()) continue;
                if (!level.getBlockState(candidate).isAir()) continue;
                if (!level.getBlockState(candidate.above()).isAir()) continue;
                if (level.getBlockState(candidate.below()).is(Blocks.WATER)) continue;
                if (level.getBlockState(candidate.below()).is(Blocks.LAVA)) continue;
            }
            return new Vec3(x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    private static void cleanupAndDespawn(MinecraftServer server) {
        ticksUntilOrphanCleanup--;
        if (ticksUntilOrphanCleanup <= 0) {
            ticksUntilOrphanCleanup = 600;
            cleanupOrphanedTaggedTrainers(server);
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, RoamingTrainerData>> iterator = TRAINERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RoamingTrainerData> entry = iterator.next();
            UUID npcUuid = entry.getKey();
            RoamingTrainerData data = entry.getValue();
            NPCEntity npc = findNpc(server, npcUuid);
            if (npc == null || !npc.isAlive()) {
                iterator.remove();
                cancelBattleForRemovedTrainer(server, data, "The roaming trainer was removed, so the battle was canceled.");
                continue;
            }

            if (now - data.lastProtectionMillis > 5_000L) {
                applyRoamingProtections(npc, data);
                data.lastProtectionMillis = now;
            }
            cleanupStaleChallengeLock(npc, data, now);
            updateRoamingMovement(npc, data, now);

            long lifetimeLimit = Math.max(60, RoamingTrainerConfig.DATA.despawnMinutes * 60) * 1000L;
            if (!isNpcInBattle(npc) && data.spawnedMillis > 0L && now - data.spawnedMillis >= lifetimeLimit) {
                iterator.remove();
                cancelBattleForRemovedTrainer(server, data, "The roaming trainer despawned, so the battle was canceled.");
                removeNpc(npc);
                continue;
            }

            boolean playerNearby = hasPlayerNearby((ServerLevel) npc.level(), npc.position(), RoamingTrainerConfig.DATA.activePlayerRadius);
            if (playerNearby) {
                // Keep this updated only for battle protection; lifetime despawn still removes idle trainers.
                data.lastNearbyPlayerMillis = now;
                continue;
            }

            if (RoamingTrainerConfig.DATA.doNotDespawnWhileInBattle && isNpcInBattle(npc)) {
                data.lastNearbyPlayerMillis = now;
                continue;
            }

            long elapsed = now - data.lastNearbyPlayerMillis;
            if (elapsed >= Math.max(30, RoamingTrainerConfig.DATA.noPlayerNearbyDespawnSeconds) * 1000L) {
                iterator.remove();
                cancelBattleForRemovedTrainer(server, data, "The roaming trainer despawned, so the battle was canceled.");
                removeNpc(npc);
            }
        }
    }

    private static void cancelBattleForRemovedTrainer(MinecraftServer server, RoamingTrainerData data, String message) {
        if (server == null || data == null) return;

        data.rewardsClaimed = true;
        UUID playerUuid = data.currentChallengerUuid;
        data.currentChallengerUuid = null;
        data.challengeLockMillis = 0L;

        if (playerUuid == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;

        Object battle = BattleStateManager.getBattle(player);
        if (battle != null) {
            tryStopBattle(battle, player);
        }

        BattleStateManager.clearAll(player);

        if (message != null && !message.isBlank()) {
            player.sendSystemMessage(Component.literal("§e" + message));
        }
    }

    private static boolean tryStopBattle(Object battle, ServerPlayer player) {
        if (battle == null) return false;

        String[] methods = new String[]{
                "forfeit",
                "flee",
                "stop",
                "end",
                "endBattle",
                "close"
        };

        for (String methodName : methods) {
            if (invokeBattleMethod(battle, methodName, player)) return true;
            if (invokeBattleMethod(battle, methodName)) return true;
        }

        return false;
    }

    private static boolean invokeBattleMethod(Object target, String methodName, Object... args) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != args.length) continue;
                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private static void cleanupStaleChallengeLock(NPCEntity npc, RoamingTrainerData data, long now) {
        if (npc == null || data == null || data.currentChallengerUuid == null) return;
        if (isNpcInBattle(npc)) return;
        if (now - data.challengeLockMillis >= 15000L) {
            data.currentChallengerUuid = null;
            data.challengeLockMillis = 0L;
        }
    }

    private static void applyRoamingProtections(NPCEntity npc, RoamingTrainerData data) {
        if (npc == null) return;
        boolean movementEnabled = RoamingTrainerConfig.DATA.movementEnabled;
        try { npc.addTag(ROAMING_TRAINER_TAG); } catch (Exception ignored) {}
        try { npc.setInvulnerable(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
        try { npc.setNoAi(!movementEnabled); } catch (Exception ignored) {}
        try { npc.setMovable(movementEnabled); } catch (Exception ignored) {}
        try { npc.setLeashable(false); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(false); } catch (Exception ignored) {}
        try { npc.setHealth(npc.getMaxHealth()); } catch (Exception ignored) {}
        if (!movementEnabled) {
            try { npc.setDeltaMovement(Vec3.ZERO); } catch (Exception ignored) {}
        }

        if (data != null && !movementEnabled) {
            try {
                Vec3 spawn = new Vec3(data.spawnX, data.spawnY, data.spawnZ);
                if (npc.position().distanceToSqr(spawn) > 0.04D) {
                    npc.teleportTo(data.spawnX, data.spawnY, data.spawnZ);
                    npc.moveTo(data.spawnX, data.spawnY, data.spawnZ, data.spawnYaw, 0.0F);
                }
                npc.setYHeadRot(data.spawnYaw);
                npc.setYBodyRot(data.spawnYaw);
            } catch (Exception ignored) {}
        }
    }

    private static void updateRoamingMovement(NPCEntity npc, RoamingTrainerData data, long now) {
        if (npc == null || data == null || !RoamingTrainerConfig.DATA.movementEnabled) return;
        if (isNpcInBattle(npc)) return;
        if (data.nextWanderMillis <= 0L) scheduleNextWander(data, now);
        if (now < data.nextWanderMillis) return;

        Vec3 target = findWanderPosition((ServerLevel) npc.level(), data);
        if (target != null) {
            try {
                npc.getNavigation().moveTo(target.x, target.y, target.z, RoamingTrainerConfig.DATA.wanderSpeed);
            } catch (Exception ignored) {}
        }
        scheduleNextWander(data, now);
    }

    private static void scheduleNextWander(RoamingTrainerData data, long now) {
        if (data == null) return;
        int min = Math.max(3, RoamingTrainerConfig.DATA.wanderEverySecondsMin);
        int max = Math.max(min, RoamingTrainerConfig.DATA.wanderEverySecondsMax);
        int delay = min + RANDOM.nextInt((max - min) + 1);
        data.nextWanderMillis = now + delay * 1000L;
    }

    private static Vec3 findWanderPosition(ServerLevel level, RoamingTrainerData data) {
        if (level == null || data == null) return null;
        int radius = Math.max(4, RoamingTrainerConfig.DATA.wanderRadiusBlocks);
        for (int i = 0; i < 10; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double distance = 3.0D + RANDOM.nextDouble() * Math.max(1.0D, radius - 3.0D);
            int x = (int)Math.floor(data.spawnX + Math.cos(angle) * distance);
            int z = (int)Math.floor(data.spawnZ + Math.sin(angle) * distance);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 2) continue;
            net.minecraft.core.BlockPos candidate = new net.minecraft.core.BlockPos(x, y, z);
            if (SpawnBlockRules.isBlockedSpawnPosition(level, candidate)) continue;
            net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(x, y, z);
            net.minecraft.core.BlockPos ground = feet.below();
            if (level.getBlockState(ground).isAir()) continue;
            if (!level.getBlockState(feet).isAir()) continue;
            if (!level.getBlockState(feet.above()).isAir()) continue;
            if (level.getBlockState(ground).is(Blocks.WATER)) continue;
            if (level.getBlockState(ground).is(Blocks.LAVA)) continue;
            return new Vec3(x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    private static boolean isNpcInBattle(NPCEntity npc) {
        try { return npc.isInBattle(); } catch (Exception ignored) { return false; }
    }

    private static boolean hasRoamingTrainerTag(NPCEntity npc) {
        try { return npc != null && npc.getTags().contains(ROAMING_TRAINER_TAG); } catch (Exception ignored) { return false; }
    }

    private static boolean isRoamingTrainerEntity(NPCEntity npc) {
        return npc != null && (isRoamingTrainer(npc.getUUID()) || hasRoamingTrainerTag(npc));
    }

    private static void cleanupOrphanedTaggedTrainers(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof NPCEntity npc)) continue;
                if (!hasRoamingTrainerTag(npc)) continue;
                if (TRAINERS.containsKey(npc.getUUID())) continue;
                if (isNpcInBattle(npc)) continue;
                removeNpc(npc);
            }
        }
    }

    public static NPCEntity findTrainerNpc(MinecraftServer server, UUID uuid) {
        return findNpc(server, uuid);
    }

    private static NPCEntity findNpc(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            NPCEntity npc = findNpc(level, uuid);
            if (npc != null) return npc;
        }
        return null;
    }

    private static NPCEntity findNpc(ServerLevel level, UUID uuid) {
        if (level == null || uuid == null) return null;
        Entity entity = level.getEntity(uuid);
        return entity instanceof NPCEntity npc ? npc : null;
    }

    private static int countNearbyRoamingTrainers(ServerLevel level, Vec3 center, double radius) {
        int count = 0;
        for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, box(center, radius))) {
            if (isRoamingTrainerEntity(npc)) count++;
        }
        return count;
    }

    private static int countWorldRoamingTrainers(ServerLevel level) {
        int count = 0;
        if (level == null) return 0;
        for (RoamingTrainerData data : TRAINERS.values()) {
            if (findNpc(level, data.npcUuid) != null) count++;
        }
        return count;
    }

    private static boolean hasPlayerNearby(ServerLevel level, Vec3 center, double radius) {
        return !level.getEntitiesOfClass(ServerPlayer.class, box(center, radius), p -> !p.isSpectator()).isEmpty();
    }

    private static AABB box(Vec3 center, double radius) {
        double r = Math.max(1.0D, radius);
        return new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
    }

    private static void removeNpc(NPCEntity npc) {
        if (npc == null) return;
        try { npc.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
    }

    private static int playerPartyHighestLevelForRarity(ServerPlayer player, RoamingTrainerRarity rarity) {
        int highest = 0;
        try {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                if (pokemon == null) continue;
                highest = Math.max(highest, Math.max(1, pokemon.getLevel()));
            }
        } catch (Exception ignored) {}

        int offset = rarityLevelOffset(rarity);
        // Roaming trainer level scaling is based on spawn rarity, not playtime tier:
        // F +5, E +10, D +15, C +20, B +25, A +30, S +35.
        return clamp(highest <= 0 ? 15 + offset : highest + offset, 1, 100);
    }

    private static int rarityLevelOffset(RoamingTrainerRarity rarity) {
        if (rarity == null) return 5;
        return switch (rarity) {
            case F -> 5;
            case E -> 10;
            case D -> 15;
            case C -> 20;
            case B -> 25;
            case A -> 30;
            case S -> 35;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static TrainerTier tierFor(ServerPlayer player) {
        long hours = ProfilePlaytimeManager.getCachedPlaytimeSeconds(player) / 3600L;
        if (hours < 10L) return TrainerTier.ROOKIE;
        if (hours < 25L) return TrainerTier.VETERAN;
        return TrainerTier.ACE;
    }

    private static RoamingTrainerRarity chooseRarityFor(ServerPlayer player) {
        TrainerTier tier = tierFor(player);
        Map<RoamingTrainerRarity, Double> weights = new LinkedHashMap<>();
        switch (tier) {
            case ROOKIE -> { weights.put(RoamingTrainerRarity.F, 85.0D); weights.put(RoamingTrainerRarity.E, 15.0D); }
            case VETERAN -> { weights.put(RoamingTrainerRarity.F, 45.0D); weights.put(RoamingTrainerRarity.E, 45.0D); weights.put(RoamingTrainerRarity.D, 10.0D); }
            case ACE -> { weights.put(RoamingTrainerRarity.E, 30.0D); weights.put(RoamingTrainerRarity.D, 60.0D); weights.put(RoamingTrainerRarity.C, 10.0D); }
            case CHAMPION -> { weights.put(RoamingTrainerRarity.D, 75.0D); weights.put(RoamingTrainerRarity.C, 25.0D); }
        }
        return rollWeighted(weights);
    }

    private static RoamingTrainerRarity rollWeighted(Map<RoamingTrainerRarity, Double> weights) {
        double total = weights.values().stream().mapToDouble(v -> Math.max(0.0D, v)).sum();
        if (total <= 0.0D) return RoamingTrainerRarity.F;
        double roll = RANDOM.nextDouble() * total;
        for (Map.Entry<RoamingTrainerRarity, Double> entry : weights.entrySet()) {
            roll -= Math.max(0.0D, entry.getValue());
            if (roll <= 0.0D) return entry.getKey();
        }
        return RoamingTrainerRarity.F;
    }

    private static RoamingTrainerRarity chooseRarity() {
        Map<RoamingTrainerRarity, Double> weights = new LinkedHashMap<>();
        double total = 0.0D;
        for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
            double weight = Math.max(0.0D, RoamingTrainerConfig.settings(rarity).weight);
            weights.put(rarity, weight);
            total += weight;
        }
        if (total <= 0.0D) return RoamingTrainerRarity.F;
        double roll = RANDOM.nextDouble() * total;
        for (Map.Entry<RoamingTrainerRarity, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll <= 0.0D) return entry.getKey();
        }
        return RoamingTrainerRarity.F;
    }

    private enum TrainerGender {
        MALE,
        FEMALE
    }

    private static final class TrainerIdentity {
        private final String displayName;
        private final String skin;

        private TrainerIdentity(String displayName, String skin) {
            this.displayName = displayName;
            this.skin = skin;
        }
    }

    private static TrainerIdentity chooseIdentity(RoamingTrainerRarity rarity, RoamingTrainerConfig.RaritySettings settings) {
        TrainerGender gender = chooseGender();
        String skin = chooseSkin(gender);
        String displayName = chooseName(rarity, settings, gender);
        return new TrainerIdentity(displayName, skin);
    }

    private static TrainerGender chooseGender() {
        boolean hasMale = hasUsableSkins(RoamingTrainerConfig.DATA.maleTrainerSkins);
        boolean hasFemale = hasUsableSkins(RoamingTrainerConfig.DATA.femaleTrainerSkins);
        if (hasMale && hasFemale) return RANDOM.nextBoolean() ? TrainerGender.MALE : TrainerGender.FEMALE;
        if (hasMale) return TrainerGender.MALE;
        if (hasFemale) return TrainerGender.FEMALE;
        return RANDOM.nextBoolean() ? TrainerGender.MALE : TrainerGender.FEMALE;
    }

    private static String chooseName(RoamingTrainerRarity rarity, RoamingTrainerConfig.RaritySettings settings, TrainerGender gender) {
        String title = AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(rarity));
        List<String> names = settings.trainerNames;
        if (names != null && !names.isEmpty()) {
            List<String> clean = names.stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!clean.isEmpty()) title = clean.get(RANDOM.nextInt(clean.size()));
        }

        String[] maleFirstNames = {
                "Aiden", "Brock", "Carter", "Drew", "Eli", "Felix", "Flint", "Grant",
                "Kai", "Leo", "Nate", "Orion", "Rowan", "Theo", "Wade", "Zane"
        };
        String[] femaleFirstNames = {
                "Aria", "Callie", "Dawn", "Elena", "Grace", "Harper", "Iris", "Jade",
                "Lana", "Misty", "Nora", "Paige", "Serena", "Talia", "Valerie", "Wren"
        };
        String[] firstNames = gender == TrainerGender.FEMALE ? femaleFirstNames : maleFirstNames;
        String first = firstNames[RANDOM.nextInt(firstNames.length)];

        // Pokemon trainer-style display: "Ace Trainer Kai", "Dragon Tamer Iris", etc.
        if (title.toLowerCase(Locale.ROOT).contains(first.toLowerCase(Locale.ROOT))) return title;
        return title + " " + first;
    }

    private static String chooseSkin(TrainerGender gender) {
        List<String> preferred = gender == TrainerGender.FEMALE
                ? RoamingTrainerConfig.DATA.femaleTrainerSkins
                : RoamingTrainerConfig.DATA.maleTrainerSkins;
        String skin = chooseSkinFrom(preferred);
        if (!skin.isBlank()) return skin;

        // Legacy fallback for configs made before the gendered skin pools existed.
        skin = chooseSkinFrom(RoamingTrainerConfig.DATA.randomTrainerSkins);
        return skin == null ? "" : skin;
    }

    private static boolean hasUsableSkins(List<String> skins) {
        return skins != null && skins.stream()
                .anyMatch(s -> s != null && !s.isBlank() && !RoamingTrainerConfig.isBlockedDefaultSkin(s));
    }

    private static String chooseSkinFrom(List<String> skins) {
        if (skins == null || skins.isEmpty()) return "";
        List<String> clean = skins.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .filter(s -> !RoamingTrainerConfig.isBlockedDefaultSkin(s))
                .distinct()
                .toList();
        if (clean.isEmpty()) return "";
        return clean.get(RANDOM.nextInt(clean.size()));
    }

    private static void alertNearbyPlayers(ServerLevel level, Vec3 pos, RoamingTrainerRarity rarity, String displayName) {
        double radius = Math.max(32.0D, RoamingTrainerConfig.DATA.activePlayerRadius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box(pos, radius), p -> !p.isSpectator())) {
            player.sendSystemMessage(Component.literal("A " + AdventurerRankUtil.trainerLabel(AdventurerRankUtil.fromRarity(rarity)) + " appeared nearby: " + displayName + "!").withStyle(rarity.color));
            try { ProfessionNotificationSettings.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F); } catch (Exception ignored) {}
        }
    }

    private static void runCommands(ServerPlayer player, List<String> commands, RoamingTrainerData data) {
        if (player == null || commands == null || commands.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack();
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw
                    .replace("%player%", player.getName().getString())
                    .replace("%uuid%", player.getUUID().toString())
                    .replace("%rarity%", data.rarity.name().toLowerCase(Locale.ROOT))
                    .replace("%RARITY%", data.rarity.name());
            server.getCommands().performPrefixedCommand(source, cmd);
        }
    }

    private static int randomBetween(int min, int max) {
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return min + RANDOM.nextInt((max - min) + 1);
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "F Rank";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
