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

        // Force the player out first through the server's normal spawn command path.
        try {
            player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "spawn");
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Tried to run /spawn before deletion, but the command failed. Territory was not deleted.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal("Territory deletion started. You were sent to spawn.").withStyle(ChatFormatting.GREEN));

        TerritoryRepository.beginDelete(territory, (success, message) -> player.server.execute(() -> {
            if (!success) {
                ServerPlayer requester = player.server.getPlayerList().getPlayer(player.getUUID());
                if (requester != null) requester.sendSystemMessage(Component.literal("Could not delete territory. Please try again.").withStyle(ChatFormatting.RED));
                System.err.println("[ChampUtils] Failed to mark territory " + territory.id + " as DELETING: " + message);
                return;
            }
            enqueueWipe(copyOf(territory), player.getUUID());
        }));
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

        // Wipe by loaded chunk columns instead of single blocks. The old per-block scan was far too slow for
        // 1000-block territory borders and could leave territories stuck in DELETING for hours/days. getChunk()
        // intentionally loads the chunk even when no player is nearby, so deletion keeps progressing offline.
        int budget = Math.max(4096, TerritoryConfig.get().territoryWipeBlocksPerTick);
        int used = 0;

        if (!task.entitiesCleared) {
            clearEntities(level, task.territory);
            task.entitiesCleared = true;
        }

        while (used < budget && !task.done(level)) {
            used += task.wipeCurrentChunk(level);
            task.advanceChunk();
        }

        if (task.done(level)) {
            QUEUE.removeFirst();
            TerritoryRepository.finishDelete(task.territory, (success, message) -> server.execute(() -> {
                QUEUED_TERRITORIES.remove(task.territory.id);
                if (!success) {
                    System.err.println("[ChampUtils] Failed to finish deleting territory " + task.territory.id + ": " + message);
                    return;
                }
                notifyDeletionFinished(server, task, "Territory deletion finished. You can create another territory after the cooldown ends.");
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
        System.out.println("[ChampUtils] Queued territory wipe for " + territory.id + " in " + territory.worldName + " slot " + territory.slotIndex + ".");
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
        }

        private boolean done(ServerLevel level) {
            return chunkX > maxChunkX;
        }

        private int wipeCurrentChunk(ServerLevel level) {
            // Force-load the chunk so deletion does not depend on players keeping the area loaded.
            level.getChunk(chunkX, chunkZ);

            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            int minX = Math.max(territory.minX, chunkX << 4);
            int maxX = Math.min(territory.maxX, (chunkX << 4) + 15);
            int minZ = Math.max(territory.minZ, chunkZ << 4);
            int maxZ = Math.min(territory.maxZ, (chunkZ << 4) + 15);
            int examined = 0;

            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
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
            chunkZ++;
            if (chunkZ <= maxChunkZ) return;
            chunkZ = minChunkZ;
            chunkX++;
        }
    }
}