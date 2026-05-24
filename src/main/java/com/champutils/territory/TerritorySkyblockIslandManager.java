package com.champutils.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Prepares skyblock-style territory slots while keeping the underlying world NORMAL generated.
 *
 * The world is intentionally not created with a VOID generator anymore. NORMAL generation keeps real biome data
 * for Cobblemon, weather, grass/water colors, and biome checks. This manager only clears a configurable starter
 * radius around the territory center, then builds the starter island.
 */
public final class TerritorySkyblockIslandManager {
    private static final Deque<PrepareTask> PREPARE_QUEUE = new ArrayDeque<>();
    private static final Set<UUID> PREPARING = new HashSet<>();
    private static final Set<UUID> PREPARED_THIS_RUNTIME = new HashSet<>();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private TerritorySkyblockIslandManager() {}

    /**
     * Returns true once the slot has been cleared and the starter island has been built.
     */
    public static boolean requestStarterAreaPreparation(ServerLevel level, TerritoryRepository.Territory territory) {
        if (level == null || territory == null || territory.id == null) return false;
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        if (!cfg.skyblockTerritoryWorlds) return true;

        if (!cfg.clearSkyblockStarterArea) {
            ensureStarterIsland(level, territory);
            PREPARED_THIS_RUNTIME.add(territory.id);
            return true;
        }

        if (PREPARED_THIS_RUNTIME.contains(territory.id)) {
            ensureStarterIsland(level, territory);
            return true;
        }

        if (PREPARING.add(territory.id)) {
            territory.generationState = "GENERATING";
            TerritoryRepository.save(territory, (success, message) -> {});
            PREPARE_QUEUE.addLast(new PrepareTask(copyOf(territory), level.getMinBuildHeight(), level.getMaxBuildHeight()));
            System.out.println("[ChampUtils] Queued skyblock territory preparation for " + territory.id + " in " + territory.worldName + ". Biomes stay NORMAL; clearing starter area only.");
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || PREPARE_QUEUE.isEmpty()) return;

        PrepareTask task = PREPARE_QUEUE.peekFirst();
        if (task == null) return;

        ServerLevel level = TerritoryTeleportUtil.resolveLevel(server, task.territory.worldName);
        if (level == null) return;

        int budget = Math.max(1024, TerritoryConfig.get().skyblockPrepareBlocksPerTick);
        int used = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (used < budget && !task.done()) {
            pos.set(task.x, task.y, task.z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, AIR, 2);
            }
            task.advance();
            used++;
        }

        if (task.done()) {
            PREPARE_QUEUE.removeFirst();
            PREPARING.remove(task.territory.id);
            PREPARED_THIS_RUNTIME.add(task.territory.id);

            TerritoryRepository.Territory live = findCached(task.territory.id);
            if (live == null) live = task.territory;
            ensureStarterIsland(level, live);
            live.generationState = "READY";
            TerritoryRepository.save(live, (success, message) -> {});
            System.out.println("[ChampUtils] Skyblock territory " + live.id + " prepared with NORMAL biome data and starter island in " + live.worldName + ".");
        }
    }

    public static void ensureStarterIsland(ServerLevel level, TerritoryRepository.Territory territory) {
        if (level == null || territory == null) return;
        TerritoryConfig.Data cfg = TerritoryConfig.get();
        if (!cfg.skyblockTerritoryWorlds || !cfg.createSkyblockStarterIsland) return;

        int centerX = territory.centerX;
        int centerZ = territory.centerZ;
        int baseY = Math.max(level.getMinBuildHeight() + 8, Math.min(level.getMaxBuildHeight() - 16, cfg.defaultSpawnY));
        int radius = Math.max(3, cfg.skyblockIslandRadius);

        clearStarterVolume(level, centerX, baseY, centerZ, radius + 8);
        buildIsland(level, centerX, baseY, centerZ, radius);
        buildTree(level, centerX + 4, baseY + 1, centerZ + 3);
        buildSpawnPad(level, centerX, baseY + 1, centerZ);

        territory.spawnX = centerX + 0.5D;
        territory.spawnY = baseY + 2.0D;
        territory.spawnZ = centerZ + 0.5D;
    }

    private static void clearStarterVolume(ServerLevel level, int cx, int baseY, int cz, int radius) {
        int minY = Math.max(level.getMinBuildHeight(), baseY - 6);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, baseY + 20);
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void buildIsland(ServerLevel level, int cx, int baseY, int cz, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 0.25D) continue;

                int x = cx + dx;
                int z = cz + dz;
                int edgeDrop = (int) Math.max(0, distance - (radius - 3));
                int topY = baseY - Math.min(2, edgeDrop);

                level.setBlock(new BlockPos(x, topY, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(new BlockPos(x, topY - 1, z), Blocks.DIRT.defaultBlockState(), 3);
                if (distance < radius - 2) level.setBlock(new BlockPos(x, topY - 2, z), Blocks.DIRT.defaultBlockState(), 3);
                if (distance < radius - 4) level.setBlock(new BlockPos(x, topY - 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
    }

    private static void buildSpawnPad(ServerLevel level, int cx, int y, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y + 1, cz + dz), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.setBlock(new BlockPos(cx, y - 1, cz), Blocks.STONE_BRICKS.defaultBlockState(), 3);
    }

    private static void buildTree(ServerLevel level, int x, int y, int z) {
        for (int i = 0; i < 5; i++) {
            level.setBlock(new BlockPos(x, y + i, z), Blocks.OAK_LOG.defaultBlockState(), 3);
        }

        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dy = 3; dy <= 6; dy++) {
            int radius = dy >= 5 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && dy >= 5) continue;
                    BlockPos pos = new BlockPos(x + dx, y + dy, z + dz);
                    if (level.getBlockState(pos).isAir()) level.setBlock(pos, leaves, 3);
                }
            }
        }
    }

    private static TerritoryRepository.Territory findCached(UUID id) {
        if (id == null) return null;
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory != null && id.equals(territory.id)) return territory;
        }
        return null;
    }

    private static TerritoryRepository.Territory copyOf(TerritoryRepository.Territory source) {
        TerritoryRepository.Territory copy = new TerritoryRepository.Territory();
        copy.id = source.id;
        copy.ownerType = source.ownerType;
        copy.ownerId = source.ownerId;
        copy.ownerName = source.ownerName;
        copy.serverId = source.serverId;
        copy.worldName = source.worldName;
        copy.worldKey = source.worldKey;
        copy.slotIndex = source.slotIndex;
        copy.generationState = source.generationState;
        copy.centerX = source.centerX;
        copy.centerZ = source.centerZ;
        copy.radius = source.radius;
        copy.minX = source.minX;
        copy.maxX = source.maxX;
        copy.minZ = source.minZ;
        copy.maxZ = source.maxZ;
        copy.spawnX = source.spawnX;
        copy.spawnY = source.spawnY;
        copy.spawnZ = source.spawnZ;
        copy.spawnYaw = source.spawnYaw;
        copy.spawnPitch = source.spawnPitch;
        copy.level = source.level;
        copy.biomePreference = source.biomePreference;
        copy.lockBorder = source.lockBorder;
        return copy;
    }

    private static final class PrepareTask {
        private final TerritoryRepository.Territory territory;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private final int minY;
        private final int maxY;
        private int x;
        private int y;
        private int z;

        private PrepareTask(TerritoryRepository.Territory territory, int levelMinY, int levelMaxY) {
            TerritoryConfig.Data cfg = TerritoryConfig.get();
            this.territory = territory;
            int clearRadius = Math.max(cfg.skyblockIslandRadius + 8, Math.min(cfg.skyblockInitialClearRadius, territory.radius));
            this.minX = territory.centerX - clearRadius;
            this.maxX = territory.centerX + clearRadius;
            this.minZ = territory.centerZ - clearRadius;
            this.maxZ = territory.centerZ + clearRadius;
            this.minY = Math.max(levelMinY, cfg.skyblockClearMinY);
            this.maxY = Math.min(levelMaxY - 1, cfg.skyblockClearMaxY);
            this.x = minX;
            this.z = minZ;
            this.y = minY;
        }

        private boolean done() {
            return x > maxX || y > maxY;
        }

        private void advance() {
            y++;
            if (y <= maxY) return;
            y = minY;
            z++;
            if (z <= maxZ) return;
            z = minZ;
            x++;
        }
    }
}
