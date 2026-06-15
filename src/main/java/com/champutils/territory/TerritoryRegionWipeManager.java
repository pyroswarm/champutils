package com.champutils.territory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TerritoryRegionWipeManager {
    private static final Deque<WipeTask> QUEUE = new ArrayDeque<>();
    private static final Set<UUID> QUEUED_TERRITORIES = new HashSet<>();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final int MAX_WORLD_LOAD_ATTEMPTS_BEFORE_DATABASE_DELETE = 18;

    private TerritoryRegionWipeManager() {}

    public static void enqueueDelete(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return;

        if (QUEUED_TERRITORIES.contains(territory.id) || TerritoryRepository.isDeleting(territory)) {
            player.sendSystemMessage(Component.literal("That territory is already being deleted.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        if (!evacuateRequester(player, territory)) {
            player.sendSystemMessage(Component.literal("Could not move you out of the territory safely, so deletion was cancelled.").withStyle(ChatFormatting.RED));
            return;
        }

        TerritoryRepository.Territory wipeCopy = copyOf(territory);
        player.sendSystemMessage(Component.literal("Territory deletion started. You were moved out of the territory. You may create a new territory immediately; the old slot will be wiped in the background.").withStyle(ChatFormatting.GREEN));

        TerritoryRepository.beginDelete(territory, (success, message) -> player.server.execute(() -> {
            if (!success) {
                ServerPlayer requester = player.server.getPlayerList().getPlayer(player.getUUID());
                if (requester != null) requester.sendSystemMessage(Component.literal("Could not delete territory. Please try again.").withStyle(ChatFormatting.RED));
                System.err.println("[ChampUtils] Failed to mark territory " + territory.id + " as DELETING: " + message);
                return;
            }
            enqueueWipe(wipeCopy, player.getUUID());
        }));
    }

    public static void enqueueDeleteForDeletedProfile(MinecraftServer server, UUID profileId, UUID requesterId) {
        if (server == null || profileId == null) return;

        TerritoryRepository.Territory territory = TerritoryRepository.cachedForOwner(TerritoryRepository.OwnerType.PLAYER, profileId.toString());
        if (territory == null) return;

        if (QUEUED_TERRITORIES.contains(territory.id) || TerritoryRepository.isDeleting(territory)) {
            return;
        }

        TerritoryRepository.Territory wipeCopy = copyOf(territory);
        TerritoryRepository.beginDelete(territory, (success, message) -> server.execute(() -> {
            if (!success) {
                System.err.println("[ChampUtils] Failed to mark profile territory " + territory.id + " as DELETING during profile deletion: " + message);
                return;
            }
            enqueueWipe(wipeCopy, requesterId);
        }));
    }


    private static boolean evacuateRequester(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || player.server == null) return false;

        // Do not rely on /spawn. Some servers do not have that command, permissions can block it,
        // and command failure used to prevent /territory delete confirm from doing anything.
        try {
            ServerLevel overworld = player.server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D, player.getYRot(), player.getXRot());
            return true;
        } catch (Exception directTeleportFailed) {
            try {
                player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "spawn");
                return true;
            } catch (Exception commandFailed) {
                System.err.println("[ChampUtils] Failed to evacuate player before deleting territory " + (territory == null ? "unknown" : territory.id) + ".");
                commandFailed.printStackTrace();
                return false;
            }
        }
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        resumeDeletingTerritories();
        if (QUEUE.isEmpty()) return;

        WipeTask task = QUEUE.peekFirst();
        if (task == null) return;

        ServerLevel level = TerritoryTeleportUtil.resolveLevel(server, task.territory.worldName);
        if (level == null) {
            task.worldLoadAttempts++;
            if (server.getTickCount() % 200 == 0) {
                System.err.println("[ChampUtils] Territory deletion is waiting for world " + task.territory.worldName + " to load for territory " + task.territory.id + " (attempt " + task.worldLoadAttempts + "/" + MAX_WORLD_LOAD_ATTEMPTS_BEFORE_DATABASE_DELETE + ").");
                TerritoryWorldGenerationManager.requestGeneration(server, task.territory);
            }
            if (task.worldLoadAttempts >= MAX_WORLD_LOAD_ATTEMPTS_BEFORE_DATABASE_DELETE) {
                QUEUE.removeFirst();
                System.err.println("[ChampUtils] Territory world " + task.territory.worldName + " could not be loaded for deletion. Removing territory records anyway so players are not stuck in DELETING forever. If that world folder exists later, delete/clean that slot manually.");
                TerritoryRepository.finishDelete(task.territory, (success, message) -> server.execute(() -> {
                    QUEUED_TERRITORIES.remove(task.territory.id);
                    if (!success) {
                        System.err.println("[ChampUtils] Failed to finish database deletion for territory " + task.territory.id + ": " + message);
                        return;
                    }
                    notifyDeletionFinished(server, task, "Territory deletion finished. The old world was not loaded, so saved territory records were removed.");
                }));
            }
            return;
        }
        task.worldLoadAttempts = 0;

        TerritoryConfig.Data config = TerritoryConfig.get();
        // Keep territory wipes extremely conservative. A previous version allowed huge
        // per-tick budgets and used ServerLevel#getChunk, which can synchronously load
        // chunk columns on the server thread. If storage is slow, that can trip the
        // watchdog. Clamp here as a final safety net even if an old config still has
        // aggressive values.
        int blockBudget = Math.max(512, Math.min(config.territoryWipeBlocksPerTick, 8192));
        int chunkBudget = Math.max(1, Math.min(config.territoryWipeChunksPerTick, 2));
        long maxNanos = Math.max(1L, Math.min(config.territoryWipeMaxMillisecondsPerTick, 2)) * 1_000_000L;
        long deadline = System.nanoTime() + maxNanos;

        if (!task.entitiesCleared) {
            clearEntities(level, task.territory);
            task.entitiesCleared = true;
        }

        task.step(level, blockBudget, chunkBudget, deadline);

        if (task.done()) {
            QUEUE.removeFirst();
            TerritoryRepository.finishDelete(task.territory, (success, message) -> server.execute(() -> {
                QUEUED_TERRITORIES.remove(task.territory.id);
                if (!success) {
                    System.err.println("[ChampUtils] Failed to finish deleting territory " + task.territory.id + ": " + message);
                    return;
                }
                notifyDeletionFinished(server, task, "Territory deletion finished. A new territory can be created now.");
            }));
        }
    }

    private static void notifyDeletionFinished(MinecraftServer server, WipeTask task, String message) {
        if (server == null || task == null || message == null) return;
        Set<UUID> notified = new HashSet<>();

        if (task.requesterId != null) {
            ServerPlayer requester = server.getPlayerList().getPlayer(task.requesterId);
            if (requester != null) {
                requester.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
                notified.add(requester.getUUID());
            }
        }

        TerritoryRepository.Territory territory = task.territory;
        if (territory != null && territory.ownerType == TerritoryRepository.OwnerType.PLAYER && territory.ownerId != null) {
            try {
                UUID ownerId = UUID.fromString(territory.ownerId);
                if (!notified.contains(ownerId)) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
                    if (owner != null) owner.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GREEN));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void enqueueWipe(TerritoryRepository.Territory territory, UUID requesterId) {
        if (territory == null || territory.id == null || QUEUED_TERRITORIES.contains(territory.id)) return;
        WipeTask task = new WipeTask(copyOf(territory), requesterId);
        QUEUE.addLast(task);
        QUEUED_TERRITORIES.add(territory.id);
        System.out.println("[ChampUtils] Queued full safe territory wipe for " + territory.id + " in " + territory.worldName + " slot " + territory.slotIndex + ".");
    }

    private static void resumeDeletingTerritories() {
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory == null || territory.id == null) continue;
            if (!TerritoryRepository.isDeleting(territory)) continue;
            enqueueWipe(territory, null);
        }
    }

    private static void clearEntities(ServerLevel level, TerritoryRepository.Territory territory) {
        AABB box = new AABB(
                territory.minX, level.getMinBuildHeight(), territory.minZ,
                territory.maxX + 1.0D, level.getMaxBuildHeight(), territory.maxZ + 1.0D
        );
        for (Entity entity : level.getEntities(null, box)) {
            if (entity instanceof ServerPlayer) continue;
            entity.discard();
        }
    }

    private static TerritoryRepository.Territory copyOf(TerritoryRepository.Territory source) {
        TerritoryRepository.Territory copy = new TerritoryRepository.Territory();
        copy.id = source.id;
        copy.ownerType = source.ownerType;
        copy.ownerId = source.ownerId;
        copy.ownerName = source.ownerName;
        copy.displayName = source.displayName;
        copy.serverId = source.serverId;
        copy.worldName = source.worldName;
        copy.worldKey = source.worldKey;
        copy.slotIndex = source.slotIndex;
        copy.generationState = source.generationState;
        copy.centerX = source.centerX;
        copy.centerZ = source.centerZ;
        copy.radius = source.radius;
        copy.minX = Math.min(source.minX, source.maxX);
        copy.maxX = Math.max(source.minX, source.maxX);
        copy.minZ = Math.min(source.minZ, source.maxZ);
        copy.maxZ = Math.max(source.minZ, source.maxZ);
        copy.spawnX = source.spawnX;
        copy.spawnY = source.spawnY;
        copy.spawnZ = source.spawnZ;
        copy.spawnYaw = source.spawnYaw;
        copy.spawnPitch = source.spawnPitch;
        copy.level = source.level;
        copy.biomePreference = source.biomePreference;
        copy.isPublic = source.isPublic;
        copy.allowVisitors = source.allowVisitors;
        copy.visitorsCanBuild = source.visitorsCanBuild;
        copy.visitorsCanOpenContainers = source.visitorsCanOpenContainers;
        copy.visitorsCanInteractEntities = source.visitorsCanInteractEntities;
        copy.visitorsCanUseRedstone = source.visitorsCanUseRedstone;
        copy.lockBorder = source.lockBorder;
        return copy;
    }

    private static final class WipeTask {
        private final TerritoryRepository.Territory territory;
        private final UUID requesterId;
        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private int chunkX;
        private int chunkZ;
        private int sectionIndex;
        private boolean entitiesCleared;
        private int worldLoadAttempts;

        private WipeTask(TerritoryRepository.Territory territory, UUID requesterId) {
            this.territory = territory;
            this.requesterId = requesterId;
            this.minChunkX = Math.floorDiv(territory.minX, 16);
            this.maxChunkX = Math.floorDiv(territory.maxX, 16);
            this.minChunkZ = Math.floorDiv(territory.minZ, 16);
            this.maxChunkZ = Math.floorDiv(territory.maxZ, 16);
            this.chunkX = minChunkX;
            this.chunkZ = minChunkZ;
            this.sectionIndex = 0;
        }

        private boolean done() {
            return chunkX > maxChunkX;
        }

        private void step(ServerLevel level, int blockBudget, int chunkBudget, long deadlineNanos) {
            int blocksExamined = 0;
            int chunksTouched = 0;
            int lastChunkX = Integer.MIN_VALUE;
            int lastChunkZ = Integer.MIN_VALUE;

            while (!done() && blocksExamined < blockBudget && chunksTouched < chunkBudget && System.nanoTime() < deadlineNanos) {
                if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                    chunksTouched++;
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                }

                // Do NOT call level.getChunk(chunkX, chunkZ) here. That method may
                // synchronously load/generate chunks and block the main server thread,
                // which is exactly what caused the watchdog crash during territory
                // deletion. Only wipe chunks that are already loaded; unloaded chunks
                // are skipped instead of force-loaded.
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    advanceChunk();
                    continue;
                }

                LevelChunkSection[] sections = chunk.getSections();
                if (sectionIndex >= sections.length) {
                    advanceChunk();
                    continue;
                }

                LevelChunkSection section = sections[sectionIndex];
                int sectionY = level.getMinSection() + sectionIndex;
                int minY = sectionY << 4;
                int maxY = minY + 15;

                if (maxY < level.getMinBuildHeight() || minY >= level.getMaxBuildHeight() || section.hasOnlyAir()) {
                    sectionIndex++;
                    continue;
                }

                blocksExamined += wipeSection(level, minY, maxY);
                sectionIndex++;
            }
        }

        private int wipeSection(ServerLevel level, int sectionMinY, int sectionMaxY) {
            int chunkMinX = chunkX << 4;
            int chunkMinZ = chunkZ << 4;
            int minX = Math.max(territory.minX, chunkMinX);
            int maxX = Math.min(territory.maxX, chunkMinX + 15);
            int minZ = Math.max(territory.minZ, chunkMinZ);
            int maxZ = Math.min(territory.maxZ, chunkMinZ + 15);
            int minY = Math.max(level.getMinBuildHeight(), sectionMinY);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, sectionMaxY);
            int examined = 0;

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        pos.set(x, y, z);
                        if (!level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, AIR, 2);
                        }
                        examined++;
                    }
                }
            }
            return Math.max(1, examined);
        }

        private void advanceChunk() {
            sectionIndex = 0;
            chunkZ++;
            if (chunkZ <= maxChunkZ) return;
            chunkZ = minChunkZ;
            chunkX++;
        }
    }
}
