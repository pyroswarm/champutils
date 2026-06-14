package com.champutils.profile;

import com.champutils.teleport.SafeTeleportManager;
import com.champutils.time.DailyResetManager;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public final class IslanderMineManager {
    private static final Queue<MineTask> TASKS = new ArrayDeque<>();
    private static final Set<String> READY_DIMENSIONS = new HashSet<>();
    private static long nextAutoResetAtMs = 0L;
    private static long lastDailyResetKeyMs = Long.MIN_VALUE;
    private static long lastAccessCheckAtMs = 0L;

    private IslanderMineManager() {}

    public static void load() {
        IslanderMineConfig.load();
        scheduleNextAutoReset(System.currentTimeMillis());
        lastDailyResetKeyMs = DailyResetManager.currentResetKeyMillis();
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !IslanderMineConfig.get().enabled) return;
        removePokemonFromMineWorlds(server);

        long now = System.currentTimeMillis();
        if (now - lastAccessCheckAtMs >= 5_000L) {
            lastAccessCheckAtMs = now;
            teleportInvalidProfilesOut(server);
        }
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        if (isDailyMode(cfg)) {
            long currentKey = DailyResetManager.currentResetKeyMillis();
            if (lastDailyResetKeyMs == Long.MIN_VALUE) lastDailyResetKeyMs = currentKey;
            if (currentKey != lastDailyResetKeyMs) {
                queueResetForLoadedMineWorlds(server);
                lastDailyResetKeyMs = currentKey;
            }
            nextAutoResetAtMs = DailyResetManager.nextResetMillis(now);
        } else if (now >= nextAutoResetAtMs) {
            queueResetForLoadedMineWorlds(server);
            scheduleNextAutoReset(now);
        }
        processTasks(server);
    }

    public static boolean teleportToMine(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        if (!cfg.enabled) {
            player.sendSystemMessage(Component.literal("Islander mines are currently disabled.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!PlayerProfileManager.isIslander(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("Only Islander profiles can use Islander mines.").withStyle(ChatFormatting.RED));
            return false;
        }

        ServerLevel level = resolveMineLevel(player.server);
        if (level == null) {
            player.sendSystemMessage(Component.literal("No Islander mine world is loaded. Create/load islander_mine_1, islander_mine_2, etc. and try again.").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        ensureMineQueued(level, false);
        SafeTeleportManager.teleportUncheckedNoBack(player, level, cfg.centerX + 0.5D, mineSpawnY(cfg), cfg.centerZ + 0.5D, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.sendSystemMessage(Component.literal("Entered " + level.dimension().location().getPath() + ". This shared mine resets every " + cfg.resetHours + " hours and is capped at " + cfg.maxPlayersPerWorld + " players.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static void queueManualReset(MinecraftServer server, ServerPlayer actor) { queueManualReset(server, actor, null); }

    public static void queueManualReset(MinecraftServer server, ServerPlayer actor, String targetLabel) {
        if (server == null) return;
        if (!IslanderMineConfig.get().allowManualReset && actor != null) {
            actor.sendSystemMessage(Component.literal("Manual Islander mine resets are disabled in config.").withStyle(ChatFormatting.RED));
            return;
        }
        int queued = queueResetForLoadedMineWorlds(server);
        if (actor != null) actor.sendSystemMessage(Component.literal("Queued Islander mine reset for " + queued + " loaded shared mine world(s). Ore positions will be rerolled.").withStyle(ChatFormatting.GREEN));
    }

    public static boolean isMineWorld(ServerLevel level) {
        if (level == null) return false;
        String path = level.dimension().location().getPath().toLowerCase(Locale.ROOT);
        String prefix = IslanderMineConfig.get().worldPrefix == null ? "islander_mine_" : IslanderMineConfig.get().worldPrefix.toLowerCase(Locale.ROOT);
        return path.startsWith(prefix);
    }

    public static boolean isMineLocation(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !isMineWorld(level)) return false;
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        int r = cfg.radius + 3;
        return Math.abs(pos.getX() - cfg.centerX) <= r
                && Math.abs(pos.getZ() - cfg.centerZ) <= r
                && pos.getY() >= cfg.centerY - 2
                && pos.getY() <= cfg.centerY + cfg.height + 2;
    }

    public static boolean isProtectedSpawn(ServerLevel level, BlockPos pos) {
        if (!isMineLocation(level, pos)) return false;
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        int topY = cfg.centerY + cfg.height;
        return Math.abs(pos.getX() - cfg.centerX) <= cfg.protectedSpawnRadius
                && Math.abs(pos.getZ() - cfg.centerZ) <= cfg.protectedSpawnRadius
                && pos.getY() >= topY - cfg.protectedSpawnHeight - 2
                && pos.getY() <= topY + 2;
    }

    public static boolean isBreakProtected(ServerLevel level, BlockPos pos) {
        if (!isMineLocation(level, pos)) return false;
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.BEDROCK);
    }

    private static boolean isDailyMode(IslanderMineConfig.Data cfg) {
        return cfg != null && cfg.resetMode != null && cfg.resetMode.equalsIgnoreCase("DAILY_2AM");
    }

    private static void scheduleNextAutoReset(long now) {
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        if (isDailyMode(cfg)) nextAutoResetAtMs = DailyResetManager.nextResetMillis(now);
        else if (cfg != null && cfg.resetMode != null && cfg.resetMode.equalsIgnoreCase("DEBUG_MINUTES")) nextAutoResetAtMs = now + Math.max(1, cfg.debugResetMinutes) * 60L * 1000L;
        else nextAutoResetAtMs = now + Math.max(1, cfg.resetHours) * 60L * 60L * 1000L;
    }

    private static ServerLevel resolveMineLevel(MinecraftServer server) {
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        ServerLevel bestUnderCap = null;
        ServerLevel bestAnyMine = null;

        for (ServerLevel level : server.getAllLevels()) {
            if (!isMineWorld(level)) continue;

            if (bestAnyMine == null || level.dimension().location().getPath().compareTo(bestAnyMine.dimension().location().getPath()) < 0) {
                bestAnyMine = level;
            }

            if (playerCount(level) >= cfg.maxPlayersPerWorld) continue;
            if (bestUnderCap == null
                    || playerCount(level) < playerCount(bestUnderCap)
                    || (playerCount(level) == playerCount(bestUnderCap)
                    && level.dimension().location().getPath().compareTo(bestUnderCap.dimension().location().getPath()) < 0)) {
                bestUnderCap = level;
            }
        }

        return bestUnderCap != null ? bestUnderCap : bestAnyMine;
    }

    private static int playerCount(ServerLevel level) {
        int count = 0;
        for (ServerPlayer player : level.players()) count++;
        return count;
    }

    private static int queueResetForLoadedMineWorlds(MinecraftServer server) {
        int queued = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (!isMineWorld(level)) continue;
            teleportMinePlayersToSpawn(level);
            ensureMineQueued(level, true);
            queued++;
        }
        return queued;
    }

    private static void ensureMineQueued(ServerLevel level, boolean forceReset) {
        if (level == null) return;
        String dimension = level.dimension().location().toString();
        for (MineTask task : TASKS) if (task.dimension.equals(dimension)) return;
        if (!forceReset && READY_DIMENSIONS.contains(dimension)) return;
        if (forceReset) READY_DIMENSIONS.remove(dimension);
        TASKS.add(new MineTask(dimension, forceReset));
    }

    private static void processTasks(MinecraftServer server) {
        MineTask task = TASKS.peek();
        if (task == null) return;
        ServerLevel level = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().location().toString().equals(task.dimension)) { level = candidate; break; }
        }
        if (level == null) { TASKS.poll(); return; }

        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        int budget = cfg.blocksPerTick;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget-- > 0 && !task.done(cfg)) {
            pos.set(cfg.centerX + task.dx, cfg.centerY + task.dy, cfg.centerZ + task.dz);
            placeMineBlock(level, pos, cfg, task);
            task.advance(cfg);
        }
        if (task.done(cfg)) {
            buildEntrance(level, cfg);
            teleportMinePlayersToSpawn(level);
            READY_DIMENSIONS.add(task.dimension);
            TASKS.poll();
            System.out.println("[ChampUtils] Shared Islander mine ready in " + task.dimension + " with randomized ore pockets.");
        }
    }


    private static void teleportInvalidProfilesOut(MinecraftServer server) {
        if (server == null) return;
        ServerLevel fallback = server.overworld();
        BlockPos spawn = fallback.getSharedSpawnPos();
        for (ServerLevel level : server.getAllLevels()) {
            if (!isMineWorld(level)) continue;
            for (ServerPlayer player : new ArrayList<>(level.players())) {
                if (PlayerProfileManager.isIslander(player) || player.hasPermissions(4)) continue;
                SafeTeleportManager.teleportUncheckedNoBack(
                        player,
                        fallback,
                        spawn.getX() + 0.5D,
                        spawn.getY() + 1.0D,
                        spawn.getZ() + 0.5D,
                        player.getYRot(),
                        player.getXRot()
                );
                player.resetFallDistance();
                player.sendSystemMessage(Component.literal("Only Islander profiles can stay in Islander mines.").withStyle(ChatFormatting.RED));
            }
        }
    }

    private static void teleportMinePlayersToSpawn(ServerLevel level) {
        if (level == null || !isMineWorld(level)) return;
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        double x = cfg.centerX + 0.5D;
        double y = mineSpawnY(cfg);
        double z = cfg.centerZ + 0.5D;
        for (ServerPlayer player : new ArrayList<>(level.players())) {
            SafeTeleportManager.teleportUncheckedNoBack(player, level, x, y, z, player.getYRot(), player.getXRot());
            player.resetFallDistance();
        }
    }

    private static double mineSpawnY(IslanderMineConfig.Data cfg) {
        return cfg.centerY + cfg.height - 7.0D;
    }

    private static void placeMineBlock(ServerLevel level, BlockPos pos, IslanderMineConfig.Data cfg, MineTask task) {
        int dx = task.dx, dy = task.dy, dz = task.dz;
        int r = cfg.radius;
        int topY = cfg.height;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        boolean outerBedrock = horizontal >= r - 1 || dy <= 0 || dy >= topY;
        boolean innerBedrock = horizontal >= r - 2 || dy <= 1;
        boolean spawnRoom = Math.abs(dx) <= cfg.spawnRoomHalfSize && Math.abs(dz) <= cfg.spawnRoomHalfSize && dy >= topY - cfg.spawnRoomHeight - 1 && dy <= topY - 2;

        if (outerBedrock || innerBedrock) { setClean(level, pos, Blocks.BEDROCK.defaultBlockState()); return; }
        if (spawnRoom) { setClean(level, pos, Blocks.AIR.defaultBlockState()); return; }

        BlockState ore = oreAt(task, dx, dy, dz, cfg);
        if (ore != null) { setClean(level, pos, ore); return; }
        setClean(level, pos, baseStoneFor(dy, cfg));
    }

    private static void buildEntrance(ServerLevel level, IslanderMineConfig.Data cfg) {
        int floorY = cfg.centerY + cfg.height - cfg.spawnRoomHeight - 2;
        int topY = cfg.centerY + cfg.height;
        int s = cfg.spawnRoomHalfSize;
        for (int dx = -s; dx <= s; dx++) {
            for (int dz = -s; dz <= s; dz++) {
                for (int y = floorY + 1; y <= topY - 2; y++) setClean(level, new BlockPos(cfg.centerX + dx, y, cfg.centerZ + dz), Blocks.AIR.defaultBlockState());
                setClean(level, new BlockPos(cfg.centerX + dx, floorY, cfg.centerZ + dz), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        setClean(level, new BlockPos(cfg.centerX, floorY + 1, cfg.centerZ), Blocks.TORCH.defaultBlockState());
    }

    private static BlockState baseStoneFor(int localY, IslanderMineConfig.Data cfg) {
        return localY < cfg.height * 0.55D ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static BlockState oreAt(MineTask task, int dx, int dy, int dz, IslanderMineConfig.Data cfg) {
        // Dense mine, sparse pocket starts. This gives strip-mining style pockets instead of random ore confetti.
        long cellSeed = task.seed ^ (((long)(dx >> 2)) * 341873128712L) ^ (((long)(dy >> 2)) * 132897987541L) ^ (((long)(dz >> 2)) * 42317861L);
        Random startRandom = new Random(cellSeed);
        int chance = startRandom.nextInt(10_000);
        int startChance = Math.max(1, Math.min(1000, cfg.orePocketStartChancePer10000));
        if (chance >= startChance) return null;

        IslanderMineConfig.OreRule rule = chooseOreRule(startRandom, cfg, dy);
        if (rule == null) return null;
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(ruleId(cfg, rule).toLowerCase(Locale.ROOT)));
        if (block == Blocks.AIR) return null;

        if (rule.singleOnly) {
            // Single-only ores, especially ancient debris, remain rare even when the global mine density is raised.
            int singleChance = Math.max(1, Math.min(startChance, Math.max(1, rule.weight) * 8));
            return chance < singleChance ? block.defaultBlockState() : null;
        }

        int size = rule.minPocketSize + startRandom.nextInt(Math.max(1, rule.maxPocketSize - rule.minPocketSize + 1));
        int ax = (dx >> 2) * 4 + startRandom.nextInt(4) - 2;
        int ay = (dy >> 2) * 4 + startRandom.nextInt(4) - 2;
        int az = (dz >> 2) * 4 + startRandom.nextInt(4) - 2;
        double blob = Math.sqrt((dx - ax) * (dx - ax) + ((dy - ay) * (dy - ay) * 1.4D) + (dz - az) * (dz - az));
        double maxDistance = Math.max(1.25D, Math.cbrt(size) + startRandom.nextDouble() * 0.75D);
        return blob <= maxDistance ? block.defaultBlockState() : null;
    }

    private static IslanderMineConfig.OreRule chooseOreRule(Random random, IslanderMineConfig.Data cfg, int localY) {
        int total = 0;
        for (IslanderMineConfig.OreRule rule : cfg.ores.values()) {
            if (localY < rule.minLocalY || localY > Math.min(rule.maxLocalY, cfg.height)) continue;
            total += depthAdjustedWeight(rule, localY, cfg.height);
        }
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (IslanderMineConfig.OreRule rule : cfg.ores.values()) {
            if (localY < rule.minLocalY || localY > Math.min(rule.maxLocalY, cfg.height)) continue;
            roll -= depthAdjustedWeight(rule, localY, cfg.height);
            if (roll < 0) return rule;
        }
        return null;
    }

    private static int depthAdjustedWeight(IslanderMineConfig.OreRule rule, int localY, int height) {
        int weight = rule.weight;
        boolean rareDeep = rule.maxPocketSize <= 5 || rule.singleOnly;
        if (rareDeep) {
            double depth = 1.0D - (localY / (double)Math.max(1, height));
            weight = (int)Math.max(1, Math.round(weight * (0.35D + depth * 1.65D)));
        }
        return weight;
    }

    private static String ruleId(IslanderMineConfig.Data cfg, IslanderMineConfig.OreRule rule) {
        for (Map.Entry<String, IslanderMineConfig.OreRule> entry : cfg.ores.entrySet()) if (entry.getValue() == rule) return entry.getKey();
        return "minecraft:stone";
    }

    private static void removePokemonFromMineWorlds(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!isMineWorld(level)) continue;
            for (ServerPlayer player : level.players()) player.resetFallDistance();
            for (Entity entity : level.getAllEntities()) if (entity instanceof PokemonEntity) entity.discard();
        }
    }

    private static void setClean(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
        level.setBlock(pos, state, 18);
        if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
    }

    private static final class MineTask {
        final String dimension;
        final boolean forceReset;
        final long seed;
        int dx, dy, dz;
        MineTask(String dimension, boolean forceReset) {
            this.dimension = dimension;
            this.forceReset = forceReset;
            IslanderMineConfig.Data cfg = IslanderMineConfig.get();
            this.dx = -cfg.radius; this.dy = 0; this.dz = -cfg.radius;
            this.seed = System.nanoTime() ^ dimension.hashCode() ^ UUID.randomUUID().getMostSignificantBits();
        }
        boolean done(IslanderMineConfig.Data cfg) { return dy > cfg.height; }
        void advance(IslanderMineConfig.Data cfg) {
            dz++;
            if (dz > cfg.radius) { dz = -cfg.radius; dx++; }
            if (dx > cfg.radius) { dx = -cfg.radius; dy++; }
        }
    }
}
