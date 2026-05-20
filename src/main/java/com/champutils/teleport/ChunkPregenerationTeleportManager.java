package com.champutils.teleport;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChunkPregenerationTeleportManager {

    private static final Map<UUID, Task> TASKS = new HashMap<>();

    private ChunkPregenerationTeleportManager() {
    }

    public static void start(ServerPlayer player, ServerLevel level, List<ChunkPos> chunks, int delayTicks, int radiusChunks) {
        TASKS.put(player.getUUID(), new Task(player.getUUID(), level, chunks, Math.max(1, delayTicks), radiusChunks));
    }

    public static boolean stop(UUID playerId) {
        return TASKS.remove(playerId) != null;
    }

    public static Task get(UUID playerId) {
        return TASKS.get(playerId);
    }

    public static void tick() {
        TASKS.values().removeIf(Task::tick);
    }

    public static final class Task {
        private final UUID playerId;
        private final ServerLevel level;
        private final List<ChunkPos> chunks;
        private final int delayTicks;
        private final int radiusChunks;
        private int index = 0;
        private int ticksUntilNext = 1;

        private Task(UUID playerId, ServerLevel level, List<ChunkPos> chunks, int delayTicks, int radiusChunks) {
            this.playerId = playerId;
            this.level = level;
            this.chunks = chunks;
            this.delayTicks = delayTicks;
            this.radiusChunks = radiusChunks;
        }

        private boolean tick() {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) return true;

            if (index >= chunks.size()) {
                player.sendSystemMessage(Component.literal("Pregeneration teleport pass complete. Loaded " + chunks.size() + " chunks.").withStyle(ChatFormatting.GREEN));
                return true;
            }

            ticksUntilNext--;
            if (ticksUntilNext > 0) return false;
            ticksUntilNext = delayTicks;

            ChunkPos chunk = chunks.get(index++);
            int x = chunk.getMiddleBlockX();
            int z = chunk.getMiddleBlockZ();

            level.getChunk(chunk.x, chunk.z);

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y + 1, z);

            player.teleportTo(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), player.getXRot());

            if (index == 1 || index % 100 == 0 || index >= chunks.size()) {
                player.sendSystemMessage(Component.literal("Pregeneration progress: " + index + "/" + chunks.size() + " chunks.").withStyle(ChatFormatting.YELLOW));
            }

            return false;
        }

        public int getDone() {
            return index;
        }

        public int getTotal() {
            return chunks.size();
        }

        public int getRadiusChunks() {
            return radiusChunks;
        }
    }
}
