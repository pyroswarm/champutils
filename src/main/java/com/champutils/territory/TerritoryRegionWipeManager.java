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
            TerritoryWorldGenerationManager.requestGeneration(server, task.territory);
            return;
        }

        int budget = Math.max(256, TerritoryConfig.get().territoryWipeBlocksPerTick);
        int used = 0;

        if (!task.entitiesCleared) {
            clearEntities(level, task.territory);
            task.entitiesCleared = true;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (used < budget && !task.done(level)) {
            pos.set(task.x, task.y, task.z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, AIR, 3);
            }
            task.advance(level);
            used++;
        }

        if (task.done(level)) {
            QUEUE.removeFirst();
            TerritoryRepository.finishDelete(task.territory, (success, message) -> server.execute(() -> {
                QUEUED_TERRITORIES.remove(task.territory.id);
                if (!success) {
                    System.err.println("[ChampUtils] Failed to finish deleting territory " + task.territory.id + ": " + message);
                    return;
                }
                if (task.requesterId != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(task.requesterId);
                    if (player != null) player.sendSystemMessage(Component.literal("Territory deleted.").withStyle(ChatFormatting.GREEN));
                }
            }));
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
        private int x;
        private int y;
        private int z;
        private boolean entitiesCleared;

        private WipeTask(TerritoryRepository.Territory territory, UUID requesterId) {
            this.territory = territory;
            this.requesterId = requesterId;
            this.x = territory.minX;
            this.z = territory.minZ;
            this.y = Integer.MIN_VALUE;
        }

        private boolean done(ServerLevel level) {
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            if (y == Integer.MIN_VALUE) y = minY;
            return x > territory.maxX || y > maxY;
        }

        private void advance(ServerLevel level) {
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight() - 1;
            y++;
            if (y <= maxY) return;

            y = minY;
            z++;
            if (z <= territory.maxZ) return;

            z = territory.minZ;
            x++;
        }
    }
}
