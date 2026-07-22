package com.champutils.guild;

import com.champutils.teleport.SafeTeleportManager;
import com.champutils.crate.CrateCreditManager;
import com.champutils.database.BossAttemptDatabaseRepository;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.permissions.LuckPermsHook;
import com.champutils.trainer.ChampTrainerSpawner;
import com.champutils.time.DailyResetManager;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor;
import com.cobblemon.mod.common.entity.npc.NPCBattleActor;
import com.champutils.territory.TerritoryRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildBossManager {
    private static final String BOSS_ADMIN_PERMISSION = "champutils.worldboss.admin";
    private static final String GUILD_BOSS_MONITOR_PERMISSION = "champutils.guildboss.monitor";
    private static final Map<UUID, ActiveGuildBoss> ACTIVE_GUILD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_GUILD_RESET_ELIGIBLE_AT = new ConcurrentHashMap<>();
    private static final Map<UUID, RewardDrop> GUILD_REWARDS = new ConcurrentHashMap<>();
    private static final Map<UUID, RewardDrop> WORLD_REWARDS = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private static final String WORLD_BOSS_ENTITY_TAG = "champutils_world_boss";
    private static final String GUILD_BOSS_ENTITY_TAG = "champutils_guild_boss";
    private static final String PENDING_BOSS_TRANSFER_KEY = "pending_boss_transfer";
    private static final long PENDING_BOSS_TRANSFER_TTL_MS = 120_000L;
    private static boolean startupBossCleanupDone = false;

    /** World boss target cadence: about once every 12 hours. */
    private static final int TARGET_WORLD_BOSS_AVERAGE_MINUTES = 720;
    /** If a configured spawn world is not loaded yet, retry soon instead of skipping a full cycle. */
    private static final long WORLD_BOSS_RETRY_DELAY_MILLIS = 5L * 60L * 1000L;
    /** Short grace window after a click before Cobblemon has fully attached battle ids to the NPC. */
    private static final long BOSS_BATTLE_START_GRACE_MILLIS = 15L * 1000L;

    private static ActiveWorldBoss activeWorldBoss = null;
    private static long nextWorldBossAtMillis = 0L;

    private GuildBossManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!startupBossCleanupDone) {
            startupBossCleanupDone = true;
            cleanupBossesFromPreviousServerSession(server);
        }
        if (server.getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();

        // Guild bosses live on whichever backend owns that guild territory, so every survival
        // backend must tick its local guild bosses. The global world boss remains single-hosted.
        if (isWorldBossHost()) {
            cleanupOrphanedWorldBossNpcs(server, now);
            long currentResetKey = DailyResetManager.currentResetKeyMillis();
            BossAttemptDatabaseRepository.pruneBeforeResetAsync(currentResetKey);
            normalizeWorldBossCadence();
            if (nextWorldBossAtMillis <= 0L) initializeNextWorldBossSchedule(now);
            if (activeWorldBoss == null && BossConfig.DATA.worldBoss.enabled && now >= nextWorldBossAtMillis) {
                spawnWorldBoss(server);
            }
            if (activeWorldBoss != null && now >= activeWorldBoss.despawnAtMillis) finishWorldBoss(server, activeWorldBoss);
        }

        for (ActiveGuildBoss boss : new ArrayList<>(ACTIVE_GUILD.values())) {
            if (now >= boss.despawnAtMillis) finishGuildBoss(server, boss);
        }
    }

    public static void spawnBoss(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) guild = GuildRepository.loadForPlayerBlocking(player.getUUID(), player.getGameProfile().getName());
        if (guild == null) { msg(player, "You are not in a guild.", ChatFormatting.RED); return; }
        if (!GuildRepository.canManageGuildTerritory(guild.role)) { msg(player, "Only guild leaders and officers can spawn the daily guild boss.", ChatFormatting.RED); return; }
        TerritoryRepository.Territory territory = TerritoryRepository.cachedGuildForPlayer(player);
        if (territory == null || !territory.isReady()) { msg(player, "Your guild territory is not ready yet.", ChatFormatting.RED); return; }
        if (!isCurrentServer(territory.serverId)) {
            routeBossAction(player, territory.serverId, "GUILD_SPAWN");
            return;
        }
        long now = System.currentTimeMillis();
        long nextEligibleReset = NEXT_GUILD_RESET_ELIGIBLE_AT.getOrDefault(guild.id, 0L);
        long currentReset = DailyResetManager.currentResetKeyMillis();
        if (nextEligibleReset > currentReset) {
            long remaining = Math.max(1L, DailyResetManager.nextResetMillis(now) - now);
            msg(player, "Your guild boss resets at " + DailyResetManager.formatResetTime() + ". Try again in " + formatDuration(remaining) + ".", ChatFormatting.RED);
            return;
        }
        if (ACTIVE_GUILD.containsKey(guild.id)) { msg(player, "Your guild already has an active boss.", ChatFormatting.RED); return; }

        BossConfig.WorldBossTheme theme = chooseTheme(BossConfig.DATA.guildBoss.themes);
        List<BossConfig.BossPokemon> team = chooseTeam(theme.pool, Math.max(1, Math.min(6, BossConfig.DATA.guildBoss.partySize)));
        if (team.isEmpty()) team.add(choose(BossConfig.DATA.guildBoss.pool));
        if (team.isEmpty()) { msg(player, "No guild boss Pokémon are configured.", ChatFormatting.RED); return; }
        ServerLevel level = level(player.server, territory.worldName);
        if (level == null) {
            msg(player, "Your guild territory world is not loaded yet. Try again in a moment.", ChatFormatting.RED);
            return;
        }
        double x = territory.spawnX;
        double y = Math.max(territory.spawnY, level.getMinBuildHeight() + 2);
        double z = territory.spawnZ;
        float yaw = territory.spawnYaw;
        String displayName = guildBossDisplayName(theme);
        NPCEntity npc = spawnBossTrainer(level, team, BossConfig.DATA.guildBoss, x, y, z, yaw, displayName, "swordtap");
        if (npc != null) {
            tagBossNpc(npc, GUILD_BOSS_ENTITY_TAG);
        }
        if (npc == null) {
            msg(player, "Could not spawn the guild boss trainer. Check bosses.json and console.", ChatFormatting.RED);
            return;
        }
        ActiveGuildBoss boss = new ActiveGuildBoss();
        boss.id = UUID.randomUUID();
        boss.guildId = guild.id;
        boss.guildName = guild.name;
        boss.species = team.get(0).species;
        boss.theme = theme.type;
        boss.displayName = displayName;
        boss.team = team;
        boss.territoryId = territory.id;
        boss.dimension = level.dimension().location().toString();
        boss.x = x; boss.y = y; boss.z = z;
        boss.despawnAtMillis = now + BossConfig.DATA.guildBoss.aliveMinutes * 60_000L;
        boss.npcUuid = npc.getUUID();
        ACTIVE_GUILD.put(guild.id, boss);
        NEXT_GUILD_RESET_ELIGIBLE_AT.put(guild.id, DailyResetManager.nextResetMillis(boss.despawnAtMillis));
        broadcastGuild(player.server, guild.id, displayName + " appeared on your guild island! Theme: " + theme.type + ". Only your guild can fight it, and you have " + BossConfig.DATA.guildBoss.aliveMinutes + " minutes to defeat it.", ChatFormatting.LIGHT_PURPLE);
    }


    public static ActiveGuildBossView getActiveGuildBossByNpc(UUID npcUuid) {
        if (npcUuid == null) return null;
        for (ActiveGuildBoss boss : ACTIVE_GUILD.values()) {
            if (npcUuid.equals(boss.npcUuid)) return new ActiveGuildBossView(boss.guildId, boss.npcUuid);
        }
        return null;
    }

    public static boolean isActiveGuildBossNpc(UUID npcUuid) {
        return getActiveGuildBossByNpc(npcUuid) != null;
    }

    public static boolean isActiveWorldBossNpc(UUID npcUuid) {
        if (npcUuid == null || activeWorldBoss == null) return false;
        for (BossSpawn spawn : activeWorldBoss.spawns) {
            if (spawn != null && npcUuid.equals(spawn.npcUuid)) return true;
        }
        return false;
    }

    public static boolean prepareGuildBossBattle(ServerPlayer player, NPCEntity npc) {
        if (player == null || npc == null) return false;
        ActiveGuildBoss boss = null;
        for (ActiveGuildBoss candidate : ACTIVE_GUILD.values()) {
            if (npc.getUUID().equals(candidate.npcUuid)) { boss = candidate; break; }
        }
        if (boss == null) return false;
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) guild = GuildRepository.loadForPlayerBlocking(player.getUUID(), player.getGameProfile().getName());
        if (guild == null || !boss.guildId.equals(guild.id)) {
            msg(player, "Only members of this guild can fight this boss.", ChatFormatting.RED);
            return false;
        }
        UUID playerUuid = player.getUUID();
        if (boss.attemptedPlayers.contains(playerUuid) || boss.defeatedPlayers.contains(playerUuid) || BossAttemptDatabaseRepository.hasAttempt("guild", boss.id, playerUuid)) {
            msg(player, "You already challenged this guild boss. Each player only gets one chance, even if they forfeit.", ChatFormatting.YELLOW);
            return false;
        }
        boolean applied = GuildBossPartyBuilder.applyBossTeam(npc, boss.team, BossConfig.DATA.guildBoss);
        if (!applied) {
            msg(player, "This guild boss could not prepare its battle team. Tell staff to check console.", ChatFormatting.RED);
            return false;
        }
        try { npc.setCustomName(Component.literal(boss.displayName == null ? "Guild Boss" : boss.displayName).withStyle(ChatFormatting.LIGHT_PURPLE)); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        boss.battlingPlayers.add(playerUuid);
        boss.battleGraceUntilMillis = System.currentTimeMillis() + BOSS_BATTLE_START_GRACE_MILLIS;
        return true;
    }

    public static boolean prepareWorldBossBattle(ServerPlayer player, NPCEntity npc) {
        if (player == null || npc == null) return false;
        ActiveWorldBoss boss = activeWorldBoss;
        if (boss == null || !isActiveWorldBossNpc(npc.getUUID())) return false;
        UUID playerUuid = player.getUUID();
        if (boss.attemptedPlayers.contains(playerUuid) || boss.defeatedPlayers.contains(playerUuid) || BossAttemptDatabaseRepository.hasAttempt("world", boss.id, playerUuid)) {
            msg(player, "You already challenged this world boss. Each player only gets one chance, even if they change spawn worlds or forfeit.", ChatFormatting.YELLOW);
            return false;
        }
        boolean applied = GuildBossPartyBuilder.applyBossTeam(npc, boss.team, BossConfig.DATA.worldBoss);
        if (!applied) {
            msg(player, "This world boss could not prepare its battle team. Tell staff to check console.", ChatFormatting.RED);
            return false;
        }
        try { npc.setCustomName(Component.literal(boss.displayName).withStyle(ChatFormatting.LIGHT_PURPLE)); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        boss.battlingPlayers.add(playerUuid);
        boss.battleGraceUntilMillis = System.currentTimeMillis() + BOSS_BATTLE_START_GRACE_MILLIS;
        return true;
    }

    public static boolean forceSpawnWorldBoss(MinecraftServer server) {
        if (server == null || !isWorldBossHost()) return false;
        if (activeWorldBoss != null) return false;
        return spawnWorldBoss(server);
    }

    public static boolean hasActiveWorldBoss() {
        return activeWorldBoss != null;
    }

    /**
     * Staff emergency cleanup for stale world boss state/NPCs. This intentionally ignores
     * battle grace and active timers because it is an admin-only recovery command.
     */
    public static int forceClearWorldBoss(MinecraftServer server) {
        if (server == null || !isWorldBossHost()) return 0;
        int removed = 0;
        ActiveWorldBoss boss = activeWorldBoss;
        activeWorldBoss = null;

        if (boss != null && boss.spawns != null) {
            for (BossSpawn spawn : boss.spawns) {
                if (spawn == null || spawn.npcUuid == null) continue;
                removed += removeNpc(server, spawn.dimension, spawn.npcUuid) ? 1 : 0;
            }
        }

        for (ServerLevel level : server.getAllLevels()) {
            removed += removeMatchingBossNpcs(level, true);
        }

        scheduleNextWorldBoss(System.currentTimeMillis());
        return removed;
    }

    public static boolean teleportToWorldBoss(ServerPlayer player) {
        if (player == null) return false;
        String host = worldBossHost();
        if (!isCurrentServer(host)) {
            return routeBossAction(player, host, "WORLD_TP");
        }
        ActiveWorldBoss boss = activeWorldBoss;
        if (boss == null || boss.spawns == null || boss.spawns.isEmpty()) {
            msg(player, "There is no active world boss right now.", ChatFormatting.RED);
            return false;
        }
        BossSpawn chosen = null;
        String playerDimension = player.serverLevel().dimension().location().toString();
        for (BossSpawn spawn : boss.spawns) {
            if (spawn != null && playerDimension.equals(spawn.dimension)) {
                chosen = spawn;
                break;
            }
        }
        if (chosen == null) chosen = boss.spawns.stream().filter(Objects::nonNull).findFirst().orElse(null);
        if (chosen == null) {
            msg(player, "The active world boss has no valid spawn location.", ChatFormatting.RED);
            return false;
        }
        ServerLevel targetLevel = level(player.server, chosen.dimension);
        if (targetLevel == null) {
            msg(player, "The world boss dimension is not loaded right now.", ChatFormatting.RED);
            return false;
        }
        SafeTeleportManager.teleport(player, targetLevel, chosen.x + 0.5D, chosen.y, chosen.z + 0.5D, player.getYRot(), player.getXRot());
        msg(player, "Teleported you to the active world boss.", ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    public static String formatLastWorldBossSpawnAgo() {
        long last = BossConfig.DATA.worldBoss.lastSpawnAtMillis;
        if (last <= 0L) return "Never";
        long elapsed = Math.max(0L, System.currentTimeMillis() - last);
        return formatDuration(elapsed) + " ago";
    }

    public static int getGuildBossCooldownMinutes() {
        return Math.max(1, BossConfig.DATA.guildBoss.cooldownMinutes);
    }

    public static void setGuildBossCooldownMinutes(int minutes) {
        BossConfig.DATA.guildBoss.cooldownMinutes = Math.max(1, minutes);
        BossConfig.save();
    }

    public static void claimRewards(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) { msg(player, "You are not in a guild.", ChatFormatting.RED); return; }
        TerritoryRepository.Territory territory = TerritoryRepository.cachedGuildForPlayer(player);
        if (territory != null && !isCurrentServer(territory.serverId)) {
            routeBossAction(player, territory.serverId, "GUILD_CLAIM");
            return;
        }
        RewardDrop drop = GUILD_REWARDS.get(guild.id);
        claim(player, drop, "guild boss");
    }

    public static void claimWorldRewards(ServerPlayer player) {
        String host = worldBossHost();
        if (!isCurrentServer(host)) {
            routeBossAction(player, host, "WORLD_CLAIM");
            return;
        }
        RewardDrop newest = WORLD_REWARDS.values().stream().max(Comparator.comparingLong(d -> d.createdAtMillis)).orElse(null);
        claim(player, newest, "world boss");
    }

    public static void recordBossVictory(ServerPlayer winner) {
        recordBossVictory(winner, null);
    }

    public static void recordBossVictory(ServerPlayer winner, UUID defeatedNpcUuid) {
        if (winner == null || defeatedNpcUuid == null) return;
        recordBossBattleEnded(winner, defeatedNpcUuid);
        boolean counted = false;
        if (isActiveGuildBossNpc(defeatedNpcUuid)) {
            recordGuildVictory(winner);
            counted = true;
        }
        if (isActiveWorldBossNpc(defeatedNpcUuid)) {
            recordWorldVictory(winner);
            counted = true;
        }
        if (counted) com.champutils.cosmetic.TitleRegistry.handleBoss(winner);
    }

    public static void recordBossBattleEnded(ServerPlayer player, UUID bossNpcUuid) {
        if (player == null || bossNpcUuid == null) return;
        ActiveWorldBoss worldBoss = activeWorldBoss;
        if (worldBoss != null) {
            for (BossSpawn spawn : worldBoss.spawns) {
                if (spawn != null && bossNpcUuid.equals(spawn.npcUuid)) {
                    worldBoss.battlingPlayers.remove(player.getUUID());
                    return;
                }
            }
        }
        for (ActiveGuildBoss boss : ACTIVE_GUILD.values()) {
            if (boss != null && bossNpcUuid.equals(boss.npcUuid)) {
                boss.battlingPlayers.remove(player.getUUID());
                return;
            }
        }
    }

    private static void recordGuildVictory(ServerPlayer winner) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(winner.getUUID());
        if (guild == null) guild = GuildRepository.loadForPlayerBlocking(winner.getUUID(), winner.getGameProfile().getName());
        if (guild == null) return;
        ActiveGuildBoss boss = ACTIVE_GUILD.get(guild.id);
        if (boss == null || !isNear(winner, boss.dimension, boss.x, boss.y, boss.z, BossConfig.DATA.guildBoss.countRadiusBlocks)) return;
        if (boss.defeatedPlayers.add(winner.getUUID())) {
            broadcastGuild(winner.server, guild.id, winner.getGameProfile().getName() + " defeated the guild boss!", ChatFormatting.GREEN);
        }
    }

    private static void recordWorldVictory(ServerPlayer winner) {
        ActiveWorldBoss boss = activeWorldBoss;
        if (boss == null) return;
        boolean nearAny = false;
        for (BossSpawn spawn : boss.spawns) {
            if (isNear(winner, spawn.dimension, spawn.x, spawn.y, spawn.z, BossConfig.DATA.worldBoss.countRadiusBlocks)) { nearAny = true; break; }
        }
        if (!nearAny) return;
        if (boss.defeatedPlayers.add(winner.getUUID())) {
            broadcastAll(winner.server, winner.getGameProfile().getName() + " defeated the world boss! Total clears: " + boss.defeatedPlayers.size(), ChatFormatting.GREEN);
        } else {
            msg(winner, "You already cleared this world boss.", ChatFormatting.YELLOW);
        }
    }

    private static boolean spawnWorldBoss(MinecraftServer server) {
        BossConfig.WorldBossSettings settings = BossConfig.DATA.worldBoss;
        List<BossConfig.BossPokemon> pool = worldBossPool(settings);
        List<BossConfig.BossPokemon> team = chooseTeam(pool, Math.max(1, Math.min(6, settings.partySize)));
        if (team.isEmpty()) {
            scheduleWorldBossRetry(System.currentTimeMillis(), "no configured world boss Pokémon pool");
            return false;
        }
        ActiveWorldBoss boss = new ActiveWorldBoss();
        boss.id = UUID.randomUUID();
        boss.species = team.get(0).species;
        boss.theme = "Mixed";
        boss.displayName = worldBossDisplayName(null);
        boss.team = team;
        boss.despawnAtMillis = System.currentTimeMillis() + settings.aliveMinutes * 60_000L;

        BossConfig.SpawnLocation location = settings.spawnLocation;
        for (String dimension : settings.spawnDimensions) {
            ServerLevel level = level(server, dimension);
            if (level == null) {
                System.err.println("[ChampUtils] World boss skipped unloaded/missing dimension: " + dimension);
                continue;
            }
            NPCEntity npc = spawnBossTrainer(level, team, settings, location.x, location.y, location.z, settings.yaw, boss.displayName, "minpapa210");
            if (npc != null) {
                tagBossNpc(npc, WORLD_BOSS_ENTITY_TAG);
                boss.spawns.add(new BossSpawn(dimension, location.x, location.y, location.z, npc.getUUID()));
            }
        }

        if (boss.spawns.isEmpty()) {
            scheduleWorldBossRetry(System.currentTimeMillis(), "no configured world boss spawn dimensions were loaded");
            return false;
        }
        activeWorldBoss = boss;
        BossConfig.DATA.worldBoss.lastSpawnAtMillis = System.currentTimeMillis();
        BossConfig.save();
        broadcastAll(server, boss.displayName + " has appeared at spawn! Defeat it once within " + settings.aliveMinutes + " minutes to qualify for rewards.", ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    private static void finishGuildBoss(MinecraftServer server, ActiveGuildBoss boss) {
        if (shouldDelayGuildBossDespawn(server, boss)) {
            boss.despawnAtMillis = System.currentTimeMillis() + 30_000L;
            return;
        }
        ACTIVE_GUILD.remove(boss.guildId);
        removeBossNpc(server, boss);
        int clears = boss.defeatedPlayers.size();
        if (clears <= 0) { broadcastGuild(server, boss.guildId, "The guild boss escaped. No rewards were earned.", ChatFormatting.RED); return; }
        RewardDrop drop = makeReward(boss.guildId, clears, BossConfig.DATA.guildBoss.rewardTiers);
        GUILD_REWARDS.put(boss.guildId, drop);
        broadcastGuild(server, boss.guildId, "Guild boss rewards are ready! Each guild member can claim " + drop.credits + " " + drop.crateId + " crate credit(s).", ChatFormatting.GOLD);
    }

    private static void finishWorldBoss(MinecraftServer server, ActiveWorldBoss boss) {
        if (shouldDelayWorldBossDespawn(server, boss)) {
            boss.despawnAtMillis = System.currentTimeMillis() + 30_000L;
            return;
        }
        if (activeWorldBoss == boss) activeWorldBoss = null;
        removeWorldBossNpcs(server, boss);
        int clears = boss.defeatedPlayers.size();
        if (clears <= 0) {
            broadcastAll(server, "The world boss escaped. No rewards were earned.", ChatFormatting.RED);
            scheduleNextWorldBoss(System.currentTimeMillis());
            return;
        }
        RewardDrop drop = makeReward(boss.id, clears, BossConfig.DATA.worldBoss.rewardTiers);
        WORLD_REWARDS.put(boss.id, drop);
        broadcastAll(server, "World boss rewards are ready! Use /worldboss claim to claim your " + drop.crateId + " key credit.", ChatFormatting.GOLD);
        scheduleNextWorldBoss(System.currentTimeMillis());
    }


    private static boolean shouldDelayGuildBossDespawn(MinecraftServer server, ActiveGuildBoss boss) {
        if (boss == null) return false;
        long now = System.currentTimeMillis();
        if (boss.battleGraceUntilMillis > now && boss.battlingPlayers != null && !boss.battlingPlayers.isEmpty()) {
            return true;
        }
        if (isBossNpcActuallyInBattle(server, boss.dimension, boss.npcUuid)) {
            return true;
        }
        if (boss.battlingPlayers != null && !boss.battlingPlayers.isEmpty()) {
            boss.battlingPlayers.clear();
        }
        return false;
    }

    private static boolean isBossNpcActuallyInBattle(MinecraftServer server, String dimension, UUID npcUuid) {
        if (server == null || dimension == null || npcUuid == null) return false;
        ServerLevel level = level(server, dimension);
        if (level == null) return false;
        Entity entity = level.getEntity(npcUuid);
        if (entity instanceof NPCEntity npc) {
            try { return npc.isInBattle(); } catch (Throwable ignored) { return false; }
        }
        return false;
    }

    private static boolean shouldDelayWorldBossDespawn(MinecraftServer server, ActiveWorldBoss boss) {
        if (boss == null) return false;
        if (boss.spawns != null) {
            for (BossSpawn spawn : boss.spawns) {
                if (spawn != null && isWorldBossNpcInPlayerBattle(server, spawn.dimension, spawn.npcUuid)) {
                    return true;
                }
            }
        }
        if (boss.battlingPlayers != null && !boss.battlingPlayers.isEmpty()) {
            boss.battlingPlayers.clear();
        }
        return false;
    }

    private static boolean isWorldBossNpcInPlayerBattle(MinecraftServer server, String dimension, UUID npcUuid) {
        if (server == null || dimension == null || npcUuid == null) return false;
        ServerLevel level = level(server, dimension);
        if (level == null) return false;
        Entity entity = level.getEntity(npcUuid);
        if (!(entity instanceof NPCEntity npc)) return false;
        try {
            for (UUID battleId : npc.getBattleIds()) {
                var battle = BattleRegistry.INSTANCE.getBattle(battleId);
                if (battle == null) continue;
                boolean hasThisNpc = false;
                boolean hasPlayer = false;
                for (Object actor : battle.getActors()) {
                    if (actor instanceof NPCBattleActor npcActor && npcActor.getEntity() != null && npcUuid.equals(npcActor.getEntity().getUUID())) {
                        hasThisNpc = true;
                    } else if (actor instanceof PlayerBattleActor) {
                        hasPlayer = true;
                    }
                }
                if (hasThisNpc && hasPlayer) return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    public static void commitBossBattleStart(ServerPlayer player, UUID npcUuid) {
        if (player == null || npcUuid == null) return;
        UUID playerUuid = player.getUUID();
        ActiveWorldBoss worldBoss = activeWorldBoss;
        if (worldBoss != null) {
            for (BossSpawn spawn : worldBoss.spawns) {
                if (spawn != null && npcUuid.equals(spawn.npcUuid)) {
                    if (worldBoss.attemptedPlayers.add(playerUuid)) {
                        BossAttemptDatabaseRepository.recordAttempt("world", worldBoss.id, playerUuid, player.getGameProfile().getName());
                        com.champutils.adventureguide.AdventureGuideManager.increment(player, "boss_event", 1);
                    }
                    return;
                }
            }
        }
        for (ActiveGuildBoss boss : ACTIVE_GUILD.values()) {
            if (boss != null && npcUuid.equals(boss.npcUuid)) {
                if (boss.attemptedPlayers.add(playerUuid)) {
                    BossAttemptDatabaseRepository.recordAttempt("guild", boss.id, playerUuid, player.getGameProfile().getName());
                    com.champutils.adventureguide.AdventureGuideManager.increment(player, "boss_event", 1);
                }
                return;
            }
        }
    }

    public static void releaseBossBattleStart(ServerPlayer player, UUID npcUuid) {
        if (player == null || npcUuid == null) return;
        UUID playerUuid = player.getUUID();
        ActiveWorldBoss worldBoss = activeWorldBoss;
        if (worldBoss != null) {
            for (BossSpawn spawn : worldBoss.spawns) {
                if (spawn != null && npcUuid.equals(spawn.npcUuid)) {
                    worldBoss.battlingPlayers.remove(playerUuid);
                    return;
                }
            }
        }
        for (ActiveGuildBoss boss : ACTIVE_GUILD.values()) {
            if (boss != null && npcUuid.equals(boss.npcUuid)) {
                boss.battlingPlayers.remove(playerUuid);
                return;
            }
        }
    }

    private static RewardDrop makeReward(UUID id, int clears, List<BossConfig.RewardTier> tiers) {
        BossConfig.RewardTier best = null;
        for (BossConfig.RewardTier tier : tiers) if (clears >= tier.minDefeats && (best == null || tier.minDefeats > best.minDefeats)) best = tier;
        if (best == null) best = new BossConfig.RewardTier(1, "d", 1);
        RewardDrop drop = new RewardDrop();
        drop.id = id;
        drop.crateId = best.crateId;
        drop.credits = best.crateCredits;
        drop.createdAtMillis = System.currentTimeMillis();
        drop.expiresAtMillis = drop.createdAtMillis + 24L * 60L * 60L * 1000L;
        drop.claimed = ConcurrentHashMap.newKeySet();
        return drop;
    }

    private static void claim(ServerPlayer player, RewardDrop drop, String label) {
        if (drop == null || System.currentTimeMillis() > drop.expiresAtMillis) { msg(player, "There are no " + label + " rewards to claim.", ChatFormatting.RED); return; }
        if (!drop.claimed.add(player.getUUID())) { msg(player, "You already claimed this " + label + " reward.", ChatFormatting.RED); return; }
        CrateCreditManager.addCredits(player, drop.crateId, drop.credits);
        msg(player, "Claimed your " + label + " reward!", ChatFormatting.GREEN);
    }



    private static void tagBossNpc(NPCEntity npc, String tag) {
        if (npc == null || tag == null || tag.isBlank()) return;
        try { npc.addTag(tag); } catch (Exception ignored) {}
    }

    private static void cleanupBossesFromPreviousServerSession(MinecraftServer server) {
        // Runtime timers do not survive a server restart. Any saved world/guild boss NPC from a
        // previous JVM session is stale by definition, so remove it before new bosses can spawn.
        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            removed += removeMatchingBossNpcs(level, true);
        }
        activeWorldBoss = null;
        ACTIVE_GUILD.clear();
        if (removed > 0) {
            System.out.println("[ChampUtils] Removed " + removed + " stale boss NPC(s) from a previous server session.");
        }
    }

    private static void cleanupOrphanedWorldBossNpcs(MinecraftServer server, long now) {
        // Defense in depth: remove stale tagged NPCs only when there is no active world boss record.
        // If the active boss is expired, finishWorldBoss() decides whether it can despawn or must stay
        // alive because a player is still battling it.
        if (activeWorldBoss != null) {
            if (now >= activeWorldBoss.despawnAtMillis) finishWorldBoss(server, activeWorldBoss);
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            removeMatchingBossNpcs(level, false);
        }
    }

    private static int removeMatchingBossNpcs(ServerLevel level, boolean includeConfiguredSpawnFallback) {
        if (level == null) return 0;
        int removed = 0;
        List<Entity> toRemove = new ArrayList<>();
        try {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof NPCEntity npc)) continue;
                if (isKnownBossNpc(npc) || (includeConfiguredSpawnFallback && looksLikeConfiguredWorldBossNpc(level, npc))) {
                    toRemove.add(entity);
                }
            }
        } catch (Exception ignored) {
            return 0;
        }
        for (Entity entity : toRemove) {
            try {
                entity.remove(Entity.RemovalReason.DISCARDED);
                removed++;
            } catch (Exception ignored) {}
        }
        return removed;
    }

    public static boolean isBossNpcEntity(Entity entity) {
        try {
            return entity != null && (entity.getTags().contains(WORLD_BOSS_ENTITY_TAG) || entity.getTags().contains(GUILD_BOSS_ENTITY_TAG));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isKnownBossNpc(NPCEntity npc) {
        return isBossNpcEntity(npc);
    }

    private static boolean looksLikeConfiguredWorldBossNpc(ServerLevel level, NPCEntity npc) {
        // Compatibility cleanup for bosses spawned before boss entity tags existed.
        BossConfig.WorldBossSettings settings = BossConfig.DATA.worldBoss;
        if (settings == null || settings.spawnLocation == null || settings.spawnDimensions == null) return false;
        String dimension = level.dimension().location().toString();
        if (!settings.spawnDimensions.contains(dimension)) return false;
        BossConfig.SpawnLocation loc = settings.spawnLocation;
        double dx = npc.getX() - loc.x, dy = npc.getY() - loc.y, dz = npc.getZ() - loc.z;
        if (dx * dx + dy * dy + dz * dz > 16.0D * 16.0D) return false;
        String name = npc.getCustomName() == null ? "" : npc.getCustomName().getString();
        if (name.isBlank()) return false;
        if (settings.themes != null) {
            for (BossConfig.WorldBossTheme theme : settings.themes) {
                if (theme != null && theme.displayName != null && name.equals(theme.displayName)) return true;
            }
        }
        return name.toLowerCase(Locale.ROOT).contains("world boss") || name.toLowerCase(Locale.ROOT).contains("titan");
    }

    private static NPCEntity spawnBossTrainer(ServerLevel level, List<BossConfig.BossPokemon> team, BossConfig.BossSettings settings, double x, double y, double z, float yaw, String name, String skinUsername) {
        try {
            NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, new Vec3(x, y, z), yaw, name, skinUsername);
            if (npc == null) return null;
            try { npc.setNoAi(false); } catch (Exception ignored) {}
            try { npc.setMovable(false); } catch (Exception ignored) {}
            try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
            try {
                npc.moveTo(x, y, z, yaw, 0.0F);
                npc.setYHeadRot(yaw);
                npc.setYBodyRot(yaw);
            } catch (Exception ignored) {}
            if (!GuildBossPartyBuilder.applyBossTeam(npc, team, settings)) {
                try { npc.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
                return null;
            }
            return npc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static NPCEntity spawnBossTrainer(ServerLevel level, BossConfig.BossPokemon pokemon, BossConfig.BossSettings settings, double x, double y, double z, float yaw, String name, String skinUsername) {
        try {
            NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, new Vec3(x, y, z), yaw, name, skinUsername);
            if (npc == null) return null;
            try { npc.setNoAi(false); } catch (Exception ignored) {}
            try { npc.setMovable(false); } catch (Exception ignored) {}
            try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
            try {
                npc.moveTo(x, y, z, yaw, 0.0F);
                npc.setYHeadRot(yaw);
                npc.setYBodyRot(yaw);
            } catch (Exception ignored) {}
            if (!GuildBossPartyBuilder.applyBossPokemon(npc, pokemon, settings)) {
                try { npc.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
                return null;
            }
            return npc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void removeBossNpc(MinecraftServer server, ActiveGuildBoss boss) {
        if (server == null || boss == null || boss.npcUuid == null) return;
        removeNpc(server, boss.dimension, boss.npcUuid);
    }

    private static void removeWorldBossNpcs(MinecraftServer server, ActiveWorldBoss boss) {
        if (server == null || boss == null) return;
        for (BossSpawn spawn : boss.spawns) {
            if (spawn != null && spawn.npcUuid != null) removeNpc(server, spawn.dimension, spawn.npcUuid);
        }
    }

    private static boolean removeNpc(MinecraftServer server, String dimension, UUID npcUuid) {
        ServerLevel level = level(server, dimension);
        if (level == null) return false;
        Entity entity = level.getEntity(npcUuid);
        if (entity != null) {
            try {
                entity.remove(Entity.RemovalReason.DISCARDED);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private static boolean spawnPokemon(MinecraftServer server, ServerLevel level, BossConfig.BossPokemon pokemon, BossConfig.BossSettings settings, double x, double y, double z) {
        try {
            CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, buildPokespawnCommand(pokemon, settings, x, y, z));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String buildPokespawnCommand(BossConfig.BossPokemon pokemon, BossConfig.BossSettings settings, double x, double y, double z) {
        StringBuilder cmd = new StringBuilder("pokespawn ").append(pokemon.species)
                .append(" lvl=").append(settings.level)
                .append(" x=").append(x).append(" y=").append(y).append(" z=").append(z)
                .append(" scale_modifier=").append(settings.scaleModifier)
                .append(" shiny=false ai=false")
                .append(" iv_hp=31 iv_attack=31 iv_defence=31 iv_special_attack=31 iv_special_defence=31 iv_speed=31")
                .append(" ev_hp=252 ev_attack=252 ev_defence=252 ev_special_attack=252 ev_special_defence=252 ev_speed=252");
        if (!pokemon.nature.isBlank()) cmd.append(" nature=").append(pokemon.nature);
        if (!pokemon.ability.isBlank()) cmd.append(" ability=").append(pokemon.ability);
        if (!pokemon.heldItem.isBlank()) cmd.append(" held_item=").append(pokemon.heldItem);
        for (int i = 0; i < pokemon.moves.size() && i < 4; i++) cmd.append(" move").append(i + 1).append("=").append(pokemon.moves.get(i));
        if (!pokemon.extraProperties.isBlank()) cmd.append(' ').append(pokemon.extraProperties.trim());
        return cmd.toString();
    }


    private static List<BossConfig.BossPokemon> worldBossPool(BossConfig.WorldBossSettings settings) {
        List<BossConfig.BossPokemon> pool = new ArrayList<>();
        if (settings != null && settings.pool != null) {
            for (BossConfig.BossPokemon pokemon : settings.pool) if (pokemon != null) pool.add(pokemon);
        }
        if (!pool.isEmpty()) return pool;

        // Legacy fallback: older configs stored world boss Pokémon inside typed themes.
        // Flatten those theme pools into one shared pool instead of selecting a typed team.
        if (settings != null && settings.themes != null) {
            for (BossConfig.WorldBossTheme theme : settings.themes) {
                if (theme == null || theme.pool == null) continue;
                for (BossConfig.BossPokemon pokemon : theme.pool) if (pokemon != null) pool.add(pokemon);
            }
        }
        return pool;
    }

    private static BossConfig.WorldBossTheme chooseTheme(List<BossConfig.WorldBossTheme> themes) {
        if (themes == null || themes.isEmpty()) return new BossConfig.WorldBossTheme("Titan", "Mixed", "Mixed Boss Titan", BossConfig.DATA.worldBoss.pool);
        return themes.get(RANDOM.nextInt(themes.size()));
    }

    private static List<BossConfig.BossPokemon> chooseTeam(List<BossConfig.BossPokemon> pool, int count) {
        List<BossConfig.BossPokemon> clean = new ArrayList<>();
        if (pool != null) {
            for (BossConfig.BossPokemon p : pool) if (p != null) clean.add(p);
        }
        if (clean.isEmpty()) return clean;

        List<BossConfig.BossPokemon> team = new ArrayList<>();

        // Structured boss teams always open with utility pressure, then damage, then a bulky anchor.
        addRolePick(team, clean, "lead/setup");
        if (count > 1) addRolePick(team, clean, "sweeper");
        if (count > 2) addRolePick(team, clean, "anchor");

        Collections.shuffle(clean, RANDOM);
        for (BossConfig.BossPokemon pokemon : clean) {
            if (team.size() >= count) break;
            if (!team.contains(pokemon)) team.add(pokemon);
        }
        return team;
    }

    private static void addRolePick(List<BossConfig.BossPokemon> team, List<BossConfig.BossPokemon> pool, String role) {
        List<BossConfig.BossPokemon> matches = new ArrayList<>();
        for (BossConfig.BossPokemon pokemon : pool) {
            if (pokemon == null || team.contains(pokemon)) continue;
            String pokemonRole = pokemon.role == null ? "sweeper" : pokemon.role.trim().toLowerCase().replace('_', '-');
            if (pokemonRole.equals("lead") || pokemonRole.equals("setup") || pokemonRole.equals("lead-setup")) pokemonRole = "lead/setup";
            if (pokemonRole.equals(role)) matches.add(pokemon);
        }
        if (!matches.isEmpty()) team.add(choose(matches));
    }

    private static BossConfig.BossPokemon choose(List<BossConfig.BossPokemon> pool) {
        int total = 0;
        for (BossConfig.BossPokemon p : pool) total += Math.max(1, p.weight);
        int roll = RANDOM.nextInt(Math.max(1, total));
        for (BossConfig.BossPokemon p : pool) {
            roll -= Math.max(1, p.weight);
            if (roll < 0) return p;
        }
        return pool.get(0);
    }

    private static String worldBossDisplayName(BossConfig.WorldBossTheme theme) {
        // World bosses are no longer typed/theme named. Keep one neutral public name while the
        // battle team is built from the whole configured pool.
        return "World Boss Titan";
    }

    private static String guildBossDisplayName(BossConfig.WorldBossTheme theme) {
        if (theme == null) return "Guild Boss";
        String type = theme.type == null || theme.type.isBlank() ? "Mixed" : theme.type;
        String name = theme.name == null || theme.name.isBlank() ? "Titan" : theme.name;
        return type + " Guild Boss " + name;
    }

    public static String getGuildBossResetInfo() {
        return DailyResetManager.formatResetTime();
    }

    private static void normalizeWorldBossCadence() {
        BossConfig.WorldBossSettings worldBoss = BossConfig.DATA.worldBoss;
        if (worldBoss == null) return;

        // Earlier test configs used very short world boss timers. Migrate those forward so existing
        // servers actually settle on the requested roughly-12-hour cadence without manual JSON edits.
        if (worldBoss.averageMinutesUntilNextBoss <= 0 || worldBoss.averageMinutesUntilNextBoss < 60) {
            worldBoss.averageMinutesUntilNextBoss = TARGET_WORLD_BOSS_AVERAGE_MINUTES;
            BossConfig.save();
        }
    }

    private static void initializeNextWorldBossSchedule(long now) {
        BossConfig.WorldBossSettings worldBoss = BossConfig.DATA.worldBoss;
        int avg = Math.max(1, worldBoss.averageMinutesUntilNextBoss);
        long interval = avg * 60_000L;

        // Timers are in memory, so rebuild them after a restart from the persisted last spawn time.
        // If the server was offline past the due time, start a fresh randomized cycle instead of
        // spawning immediately on every reboot.
        long lastSpawn = worldBoss.lastSpawnAtMillis;
        if (lastSpawn > 0L) {
            long dueAt = lastSpawn + interval;
            if (now >= dueAt) {
                // Do not spawn a world boss immediately just because the server was offline past
                // the due time. Reboots should not create a new boss every time the saved timer
                // is old; start a fresh randomized cycle instead.
                scheduleNextWorldBoss(now);
            } else {
                nextWorldBossAtMillis = dueAt;
            }
            return;
        }

        scheduleNextWorldBoss(now);
    }

    private static void scheduleNextWorldBoss(long fromMillis) {
        int avg = Math.max(1, BossConfig.DATA.worldBoss.averageMinutesUntilNextBoss);
        double factor = 0.75D + RANDOM.nextDouble() * 0.5D;
        nextWorldBossAtMillis = fromMillis + Math.max(60_000L, (long)(avg * factor * 60_000D));
    }

    private static void scheduleWorldBossRetry(long fromMillis, String reason) {
        nextWorldBossAtMillis = fromMillis + WORLD_BOSS_RETRY_DELAY_MILLIS;
        System.err.println("[ChampUtils] World boss auto spawn failed (" + reason + "). Retrying in 5 minutes.");
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
            return server.getLevel(key);
        } catch (Exception ignored) { return null; }
    }

    private static boolean isNear(ServerPlayer player, String dimension, double x, double y, double z, int radius) {
        if (player == null || !player.serverLevel().dimension().location().toString().equals(dimension)) return false;
        double dx = player.getX() - x, dy = player.getY() - y, dz = player.getZ() - z;
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    private static void broadcastGuild(MinecraftServer server, UUID guildId, String text, ChatFormatting color) {
        if (guildId == null) return;
        Component message = Component.literal(text).withStyle(color);
        if (server != null) {
            for (GuildRepository.MemberSnapshot member : GuildRepository.cachedOnlineMembers(server.getPlayerList().getPlayers(), guildId)) {
                if (member == null || member.playerUuid == null) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(member.playerUuid);
                if (player != null) player.sendSystemMessage(message);
            }
        }
        NetworkEventManager.publishGuildNotice(guildId, legacyColor(color) + text);
    }

    private static boolean isBossNotificationAdmin(ServerPlayer player) {
        if (player == null) return false;
        if (player.hasPermissions(4)) return true;
        return LuckPermsHook.hasPermission(player, BOSS_ADMIN_PERMISSION) || LuckPermsHook.hasPermission(player, GUILD_BOSS_MONITOR_PERMISSION);
    }

    private static void broadcastAll(MinecraftServer server, String text, ChatFormatting color) {
        if (server != null) for (ServerPlayer p : server.getPlayerList().getPlayers()) msg(p, text, color);
        NetworkEventManager.publishBroadcastText(legacyColor(color) + text);
    }

    public static void handleProfileReady(ServerPlayer player) {
        if (player == null) return;
        SharedJsonStateRepository.loadPlayerAsync(player.getUUID(), PENDING_BOSS_TRANSFER_KEY, PendingBossTransfer.class, null)
                .thenAccept(pending -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player) || pending == null) return;
                    if (pending.expiresAtMillis < System.currentTimeMillis()) { clearPendingBossTransfer(player.getUUID()); return; }
                    if (pending.targetServerId == null || !pending.targetServerId.equalsIgnoreCase(NetworkServerConfig.serverId())) return;
                    clearPendingBossTransfer(player.getUUID());
                    switch (pending.action == null ? "" : pending.action) {
                        case "GUILD_SPAWN" -> spawnBoss(player);
                        case "GUILD_CLAIM" -> claimRewards(player);
                        case "WORLD_TP" -> teleportToWorldBoss(player);
                        case "WORLD_CLAIM" -> claimWorldRewards(player);
                        default -> { }
                    }
                }));
    }

    private static boolean routeBossAction(ServerPlayer player, String targetServerId, String action) {
        if (player == null || targetServerId == null || targetServerId.isBlank()) return false;
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
        if (active == null) return false;
        PendingBossTransfer pending = new PendingBossTransfer();
        pending.targetServerId = targetServerId.trim();
        pending.action = action;
        pending.expiresAtMillis = System.currentTimeMillis() + PENDING_BOSS_TRANSFER_TTL_MS;
        msg(player, "Sending you to the server hosting that boss activity.", ChatFormatting.YELLOW);
        SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_BOSS_TRANSFER_KEY, pending).whenComplete((ignored, error) -> player.server.execute(() -> {
            if (!SafeTeleportManager.isLive(player)) return;
            if (error != null) { msg(player, "Could not prepare the boss transfer.", ChatFormatting.RED); return; }
            ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, pending.targetServerId, message -> {
                if (message != null && message.startsWith("Could not")) {
                    clearPendingBossTransfer(player.getUUID());
                    msg(player, message, ChatFormatting.RED);
                }
            });
        }));
        return true;
    }

    private static void clearPendingBossTransfer(UUID playerUuid) {
        PendingBossTransfer cleared = new PendingBossTransfer();
        SharedJsonStateRepository.savePlayerAsync(playerUuid, PENDING_BOSS_TRANSFER_KEY, cleared);
    }

    private static String worldBossHost() {
        String configured = NetworkServerConfig.get().worldBossServerId;
        if (configured == null || configured.isBlank()) configured = NetworkServerConfig.get().survivalServerId;
        return configured == null ? NetworkServerConfig.serverId() : configured.trim();
    }

    private static boolean isWorldBossHost() { return isCurrentServer(worldBossHost()); }
    public static boolean isWorldBossHostServer() { return isWorldBossHost(); }
    public static String worldBossHostServerId() { return worldBossHost(); }
    private static boolean isCurrentServer(String serverId) { return serverId == null || serverId.isBlank() || serverId.equalsIgnoreCase(NetworkServerConfig.serverId()); }

    private static String legacyColor(ChatFormatting color) {
        if (color == ChatFormatting.RED) return "§c";
        if (color == ChatFormatting.GREEN) return "§a";
        if (color == ChatFormatting.GOLD) return "§6";
        if (color == ChatFormatting.LIGHT_PURPLE) return "§d";
        if (color == ChatFormatting.YELLOW) return "§e";
        return "§f";
    }

    private static void msg(ServerPlayer p, String text, ChatFormatting color) { if (p != null) p.sendSystemMessage(Component.literal(text).withStyle(color)); }
    private static String pretty(String s) { return s == null ? "Boss" : s.replace('_',' '); }
    private static String formatDuration(long ms) {
        long minutes = Math.max(1L, ms / 60_000L);
        long hours = minutes / 60L;
        long mins = minutes % 60L;
        return hours > 0 ? hours + "h " + mins + "m" : mins + "m";
    }

    public record ActiveGuildBossView(UUID guildId, UUID npcUuid) {}
    private static final class ActiveGuildBoss { UUID id; UUID guildId; UUID territoryId; UUID npcUuid; String guildName; String species; String theme; String displayName; List<BossConfig.BossPokemon> team = new ArrayList<>(); String dimension; double x; double y; double z; long despawnAtMillis; long battleGraceUntilMillis; Set<UUID> attemptedPlayers = ConcurrentHashMap.newKeySet(); Set<UUID> defeatedPlayers = ConcurrentHashMap.newKeySet(); Set<UUID> battlingPlayers = ConcurrentHashMap.newKeySet(); }
    private static final class ActiveWorldBoss { UUID id; String species; String theme; String displayName; List<BossConfig.BossPokemon> team = new ArrayList<>(); long despawnAtMillis; long battleGraceUntilMillis; List<BossSpawn> spawns = new ArrayList<>(); Set<UUID> attemptedPlayers = ConcurrentHashMap.newKeySet(); Set<UUID> defeatedPlayers = ConcurrentHashMap.newKeySet(); Set<UUID> battlingPlayers = ConcurrentHashMap.newKeySet(); }
    private static final class BossSpawn { String dimension; double x; double y; double z; UUID npcUuid; BossSpawn(String dimension, double x, double y, double z, UUID npcUuid) { this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.npcUuid = npcUuid; } }
    private static final class RewardDrop { UUID id; String crateId; int credits; long createdAtMillis; long expiresAtMillis; Set<UUID> claimed; }
    public static final class PendingBossTransfer { public String targetServerId = ""; public String action = ""; public long expiresAtMillis = 0L; }
}
