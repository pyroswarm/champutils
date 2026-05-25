package com.champutils.guild;

import com.champutils.crate.CrateCreditManager;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildBossManager {
    private static final Map<UUID, ActiveGuildBoss> ACTIVE_GUILD = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_GUILD_SPAWN = new ConcurrentHashMap<>();
    private static final Map<UUID, RewardDrop> GUILD_REWARDS = new ConcurrentHashMap<>();
    private static final Map<UUID, RewardDrop> WORLD_REWARDS = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    private static ActiveWorldBoss activeWorldBoss = null;
    private static long nextWorldBossAtMillis = 0L;

    private GuildBossManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) return;
        long now = System.currentTimeMillis();
        if (nextWorldBossAtMillis <= 0L) scheduleNextWorldBoss(now);
        if (activeWorldBoss == null && BossConfig.DATA.worldBoss.enabled && now >= nextWorldBossAtMillis) spawnWorldBoss(server);

        for (ActiveGuildBoss boss : new ArrayList<>(ACTIVE_GUILD.values())) {
            if (now >= boss.despawnAtMillis) finishGuildBoss(server, boss);
        }
        if (activeWorldBoss != null && now >= activeWorldBoss.despawnAtMillis) finishWorldBoss(server, activeWorldBoss);
    }

    public static void spawnBoss(ServerPlayer player) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
        if (guild == null) { msg(player, "You are not in a guild.", ChatFormatting.RED); return; }
        if (!GuildRepository.canManageGuildTerritory(guild.role)) { msg(player, "Only guild leaders and officers can spawn the daily guild boss.", ChatFormatting.RED); return; }
        TerritoryRepository.Territory territory = TerritoryRepository.cachedGuildForPlayer(player);
        if (territory == null || !territory.isReady()) { msg(player, "Your guild territory is not ready yet.", ChatFormatting.RED); return; }
        long now = System.currentTimeMillis();
        long cooldownMs = Duration.ofMinutes(Math.max(1, BossConfig.DATA.guildBoss.cooldownMinutes)).toMillis();
        long last = LAST_GUILD_SPAWN.getOrDefault(guild.id, 0L);
        long remaining = (last + cooldownMs) - now;
        if (remaining > 0L) {
            msg(player, "Your guild boss is on cooldown for " + formatDuration(remaining) + ".", ChatFormatting.RED);
            return;
        }
        if (ACTIVE_GUILD.containsKey(guild.id)) { msg(player, "Your guild already has an active boss.", ChatFormatting.RED); return; }

        BossConfig.BossPokemon pokemon = choose(BossConfig.DATA.guildBoss.pool);
        ServerLevel level = level(player.server, territory.worldName);
        if (level == null) {
            msg(player, "Your guild territory world is not loaded yet. Try again in a moment.", ChatFormatting.RED);
            return;
        }
        double x = territory.centerX + 0.5D, y = territory.spawnY - 1.0D, z = territory.centerZ + 0.5D;
        NPCEntity npc = spawnBossTrainer(level, pokemon, BossConfig.DATA.guildBoss, x, y, z, 180.0F, "Guild Boss - "+ pretty(pokemon.species), "swordtap");
        if (npc == null) {
            msg(player, "Could not spawn the guild boss trainer. Check bosses.json and console.", ChatFormatting.RED);
            return;
        }
        ActiveGuildBoss boss = new ActiveGuildBoss();
        boss.guildId = guild.id;
        boss.guildName = guild.name;
        boss.species = pokemon.species;
        boss.territoryId = territory.id;
        boss.dimension = level.dimension().location().toString();
        boss.x = x; boss.y = y; boss.z = z;
        boss.despawnAtMillis = now + BossConfig.DATA.guildBoss.aliveMinutes * 60_000L;
        boss.npcUuid = npc.getUUID();
        ACTIVE_GUILD.put(guild.id, boss);
        LAST_GUILD_SPAWN.put(guild.id, now);
        broadcastGuild(player.server, guild.id, "A guild boss trainer appeared on your guild island with " + pretty(pokemon.species) + "! You have " + BossConfig.DATA.guildBoss.aliveMinutes + " minutes to defeat it.", ChatFormatting.LIGHT_PURPLE);
    }


    public static ActiveGuildBossView getActiveGuildBossByNpc(UUID npcUuid) {
        if (npcUuid == null) return null;
        for (ActiveGuildBoss boss : ACTIVE_GUILD.values()) {
            if (npcUuid.equals(boss.npcUuid)) return new ActiveGuildBossView(boss.guildId, boss.npcUuid);
        }
        return null;
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
        if (guild == null || !boss.guildId.equals(guild.id)) {
            msg(player, "Only members of this guild can fight this boss.", ChatFormatting.RED);
            return false;
        }
        if (boss.defeatedPlayers.contains(player.getUUID())) {
            msg(player, "You already defeated this guild boss.", ChatFormatting.YELLOW);
            return false;
        }
        BossConfig.BossPokemon pokemon = new BossConfig.BossPokemon();
        pokemon.species = boss.species;
        for (BossConfig.BossPokemon candidate : BossConfig.DATA.guildBoss.pool) {
            if (candidate != null && candidate.species != null && candidate.species.equalsIgnoreCase(boss.species)) {
                pokemon = candidate;
                break;
            }
        }
        boolean applied = GuildBossPartyBuilder.applyBossPokemon(npc, pokemon, BossConfig.DATA.guildBoss);
        if (!applied) {
            msg(player, "This guild boss could not prepare its battle team. Tell staff to check console.", ChatFormatting.RED);
            return false;
        }
        try { npc.setCustomName(Component.literal("Guild Boss - " + pretty(boss.species)).withStyle(ChatFormatting.LIGHT_PURPLE)); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        return true;
    }

    public static boolean prepareWorldBossBattle(ServerPlayer player, NPCEntity npc) {
        if (player == null || npc == null) return false;
        ActiveWorldBoss boss = activeWorldBoss;
        if (boss == null || !isActiveWorldBossNpc(npc.getUUID())) return false;
        if (boss.defeatedPlayers.contains(player.getUUID())) {
            msg(player, "You already defeated this world boss.", ChatFormatting.YELLOW);
            return false;
        }
        boolean applied = GuildBossPartyBuilder.applyBossTeam(npc, boss.team, BossConfig.DATA.worldBoss);
        if (!applied) {
            msg(player, "This world boss could not prepare its battle team. Tell staff to check console.", ChatFormatting.RED);
            return false;
        }
        try { npc.setCustomName(Component.literal(boss.displayName).withStyle(ChatFormatting.LIGHT_PURPLE)); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        return true;
    }

    public static boolean forceSpawnWorldBoss(MinecraftServer server) {
        if (server == null) return false;
        if (activeWorldBoss != null) return false;
        return spawnWorldBoss(server);
    }

    public static boolean hasActiveWorldBoss() {
        return activeWorldBoss != null;
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
        RewardDrop drop = GUILD_REWARDS.get(guild.id);
        claim(player, drop, "guild boss");
    }

    public static void claimWorldRewards(ServerPlayer player) {
        RewardDrop newest = WORLD_REWARDS.values().stream().max(Comparator.comparingLong(d -> d.createdAtMillis)).orElse(null);
        claim(player, newest, "world boss");
    }

    public static void recordBossVictory(ServerPlayer winner) {
        if (winner == null) return;
        recordGuildVictory(winner);
        recordWorldVictory(winner);
    }

    private static void recordGuildVictory(ServerPlayer winner) {
        GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(winner.getUUID());
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
        BossConfig.WorldBossTheme theme = chooseTheme(settings.themes);
        List<BossConfig.BossPokemon> team = chooseTeam(theme.pool, Math.max(1, Math.min(6, settings.partySize)));
        if (team.isEmpty()) team.add(choose(settings.pool));
        ActiveWorldBoss boss = new ActiveWorldBoss();
        boss.id = UUID.randomUUID();
        boss.species = team.get(0).species;
        boss.theme = theme.type;
        boss.displayName = theme.displayName;
        boss.team = team;
        boss.despawnAtMillis = System.currentTimeMillis() + settings.aliveMinutes * 60_000L;

        BossConfig.SpawnLocation location = settings.spawnLocation;
        for (String dimension : settings.spawnDimensions) {
            ServerLevel level = level(server, dimension);
            if (level == null) {
                System.err.println("[ChampUtils] World boss skipped unloaded/missing dimension: " + dimension);
                continue;
            }
            NPCEntity npc = spawnBossTrainer(level, team, settings, location.x, location.y, location.z, settings.yaw, theme.displayName, "dmitibr");
            if (npc != null) {
                boss.spawns.add(new BossSpawn(dimension, location.x, location.y, location.z, npc.getUUID()));
            }
        }

        if (boss.spawns.isEmpty()) {
            scheduleNextWorldBoss(System.currentTimeMillis());
            return false;
        }
        activeWorldBoss = boss;
        BossConfig.DATA.worldBoss.lastSpawnAtMillis = System.currentTimeMillis();
        BossConfig.save();
        broadcastAll(server, theme.displayName + " has appeared at spawn! Theme: " + theme.type + ". This is a " + theme.type + "-type themed boss, so build a counter team and defeat it once within " + settings.aliveMinutes + " minutes.", ChatFormatting.LIGHT_PURPLE);
        return true;
    }

    private static void finishGuildBoss(MinecraftServer server, ActiveGuildBoss boss) {
        ACTIVE_GUILD.remove(boss.guildId);
        removeBossNpc(server, boss);
        int clears = boss.defeatedPlayers.size();
        if (clears <= 0) { broadcastGuild(server, boss.guildId, "The guild boss escaped. No rewards were earned.", ChatFormatting.RED); return; }
        RewardDrop drop = makeReward(boss.guildId, clears, BossConfig.DATA.guildBoss.rewardTiers);
        GUILD_REWARDS.put(boss.guildId, drop);
        broadcastGuild(server, boss.guildId, "Guild boss rewards are ready! Each guild member can claim " + drop.credits + " " + drop.crateId + " crate credit(s).", ChatFormatting.GOLD);
    }

    private static void finishWorldBoss(MinecraftServer server, ActiveWorldBoss boss) {
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
        broadcastAll(server, "World boss rewards are ready! Each player can claim " + drop.credits + " " + drop.crateId + " crate credit(s).", ChatFormatting.GOLD);
        scheduleNextWorldBoss(System.currentTimeMillis());
    }

    private static RewardDrop makeReward(UUID id, int clears, List<BossConfig.RewardTier> tiers) {
        BossConfig.RewardTier best = null;
        for (BossConfig.RewardTier tier : tiers) if (clears >= tier.minDefeats && (best == null || tier.minDefeats > best.minDefeats)) best = tier;
        if (best == null) best = new BossConfig.RewardTier(1, "rare", 1);
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


    private static NPCEntity spawnBossTrainer(ServerLevel level, List<BossConfig.BossPokemon> team, BossConfig.BossSettings settings, double x, double y, double z, float yaw, String name, String skinUsername) {
        try {
            NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, new Vec3(x, y, z), yaw, name, skinUsername);
            if (npc == null) return null;
            try { npc.setNoAi(true); } catch (Exception ignored) {}
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
            try { npc.setNoAi(true); } catch (Exception ignored) {}
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

    private static void removeNpc(MinecraftServer server, String dimension, UUID npcUuid) {
        ServerLevel level = level(server, dimension);
        if (level == null) return;
        Entity entity = level.getEntity(npcUuid);
        if (entity != null) {
            try { entity.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
        }
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

    private static BossConfig.WorldBossTheme chooseTheme(List<BossConfig.WorldBossTheme> themes) {
        if (themes == null || themes.isEmpty()) return new BossConfig.WorldBossTheme("Titan", "Mixed", "Mixed Boss Titan", BossConfig.DATA.worldBoss.pool);
        return themes.get(RANDOM.nextInt(themes.size()));
    }

    private static List<BossConfig.BossPokemon> chooseTeam(List<BossConfig.BossPokemon> pool, int count) {
        List<BossConfig.BossPokemon> clean = new ArrayList<>();
        if (pool != null) {
            for (BossConfig.BossPokemon p : pool) if (p != null) clean.add(p);
        }
        Collections.shuffle(clean, RANDOM);
        if (clean.size() > count) return new ArrayList<>(clean.subList(0, count));
        return clean;
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

    private static void scheduleNextWorldBoss(long fromMillis) {
        int avg = Math.max(1, BossConfig.DATA.worldBoss.averageMinutesUntilNextBoss);
        double factor = 0.75D + RANDOM.nextDouble() * 0.5D;
        nextWorldBossAtMillis = fromMillis + Math.max(60_000L, (long)(avg * factor * 60_000D));
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
        if (server == null || guildId == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GuildRepository.GuildSnapshot g = GuildRepository.cachedGuild(p.getUUID());
            if (g != null && guildId.equals(g.id)) msg(p, text, color);
        }
    }

    private static void broadcastAll(MinecraftServer server, String text, ChatFormatting color) {
        if (server == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) msg(p, text, color);
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
    private static final class ActiveGuildBoss { UUID guildId; UUID territoryId; UUID npcUuid; String guildName; String species; String dimension; double x; double y; double z; long despawnAtMillis; Set<UUID> defeatedPlayers = ConcurrentHashMap.newKeySet(); }
    private static final class ActiveWorldBoss { UUID id; String species; String theme; String displayName; List<BossConfig.BossPokemon> team = new ArrayList<>(); long despawnAtMillis; List<BossSpawn> spawns = new ArrayList<>(); Set<UUID> defeatedPlayers = ConcurrentHashMap.newKeySet(); }
    private static final class BossSpawn { String dimension; double x; double y; double z; UUID npcUuid; BossSpawn(String dimension, double x, double y, double z, UUID npcUuid) { this.dimension = dimension; this.x = x; this.y = y; this.z = z; this.npcUuid = npcUuid; } }
    private static final class RewardDrop { UUID id; String crateId; int credits; long createdAtMillis; long expiresAtMillis; Set<UUID> claimed; }
}
