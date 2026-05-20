package com.champutils.teleport;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RandomTeleportCommand {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> LAST_USE_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, SearchTask> ACTIVE_SEARCHES = new HashMap<>();

    private static final int ATTEMPTS_PER_TICK = 8;
    private static final int MIN_RTP_DISTANCE_BLOCKS = 1000;
    private static final int BORDER_PADDING = 32;

    private RandomTeleportCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("rtp")
                    .executes(ctx -> rtp(ctx.getSource())));

            dispatcher.register(literal("rtpcooldown")
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> showCooldown(ctx.getSource()))
                    .then(argument("seconds", IntegerArgumentType.integer(0))
                            .executes(ctx -> setCooldown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));

            dispatcher.register(literal("rtpdimension")
                    .requires(source -> source.hasPermission(4))
                    .then(literal("list")
                            .executes(ctx -> listBlocked(ctx.getSource())))
                    .then(literal("block")
                            .then(argument("dimension", StringArgumentType.greedyString())
                                    .executes(ctx -> blockDimension(ctx.getSource(), StringArgumentType.getString(ctx, "dimension")))))
                    .then(literal("unblock")
                            .then(argument("dimension", StringArgumentType.greedyString())
                                    .executes(ctx -> unblockDimension(ctx.getSource(), StringArgumentType.getString(ctx, "dimension")))))
                    .then(literal("fallback")
                            .then(argument("dimension", StringArgumentType.greedyString())
                                    .executes(ctx -> setFallback(ctx.getSource(), StringArgumentType.getString(ctx, "dimension"))))));
        });
    }

    private static int rtp(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /rtp."));
            return 0;
        }

        UUID playerId = player.getUUID();

        if (ACTIVE_SEARCHES.containsKey(playerId)) {
            player.sendSystemMessage(Component.literal("RTP is already searching for a safe location...").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        int cooldown = TeleportConfig.getRtpCooldownSeconds();
        long now = System.currentTimeMillis();
        long last = LAST_USE_MS.getOrDefault(playerId, 0L);
        long waitMs = (cooldown * 1000L) - (now - last);

        if (!player.hasPermissions(4) && waitMs > 0) {
            long waitSeconds = Math.max(1L, (waitMs + 999L) / 1000L);
            player.sendSystemMessage(Component.literal("You can use /rtp again in " + waitSeconds + "s.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel targetLevel = player.serverLevel();
        String currentDimension = targetLevel.dimension().location().toString();

        if (TeleportConfig.isRtpBlocked(currentDimension) || isSpawnHubDimension(currentDimension)) {
            targetLevel = TeleportConfig.resolveLevel(player.server, TeleportConfig.getRtpFallbackDimension());
            if (targetLevel == null) {
                player.sendSystemMessage(Component.literal("RTP fallback dimension is missing or not loaded.").withStyle(ChatFormatting.RED));
                return 0;
            }
        }

        SearchBounds bounds = SearchBounds.from(targetLevel);
        if (bounds == null) {
            player.sendSystemMessage(Component.literal("RTP could not read a valid world border.").withStyle(ChatFormatting.RED));
            return 0;
        }

        // Cooldown starts as soon as the search starts, not after a successful teleport.
        LAST_USE_MS.put(playerId, now);
        ACTIVE_SEARCHES.put(playerId, new SearchTask(playerId, targetLevel, bounds, player.getX(), player.getZ()));

        player.sendSystemMessage(Component.literal("Searching for a random safe RTP location at least " + MIN_RTP_DISTANCE_BLOCKS + " blocks away...").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Range: X " + bounds.minX + " to " + bounds.maxX + ", Z " + bounds.minZ + " to " + bounds.maxZ + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE_SEARCHES.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, SearchTask>> iterator = ACTIVE_SEARCHES.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, SearchTask> entry = iterator.next();
            SearchTask task = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                iterator.remove();
                continue;
            }

            if (task.tick(player)) {
                iterator.remove();
            }
        }
    }

    private static BlockPos findSafePosition(SearchTask task) {
        ServerLevel level = task.level;
        SearchBounds bounds = task.bounds;
        WorldBorder border = level.getWorldBorder();

        for (int attempt = 0; attempt < ATTEMPTS_PER_TICK; attempt++) {
            task.attempts++;

            int x = randomBetween(bounds.minX, bounds.maxX);
            int z = randomBetween(bounds.minZ, bounds.maxZ);

            if (!isFarEnoughFromStart(task, x, z)) {
                continue;
            }

            // Force this candidate chunk to load/generate before checking height and blocks.
            // Without this, RTP can endlessly reject unloaded terrain.
            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
            try {
                level.getChunk(chunkPos.x, chunkPos.z);
            } catch (Exception ignored) {
                continue;
            }

            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos feet = new BlockPos(x, y, z);
            BlockPos ground = feet.below();
            BlockPos head = feet.above();

            if (!border.isWithinBounds(feet)) continue;
            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) continue;
            if (!hasRoomForPlayer(level, feet, head)) continue;
            if (!hasSafeLanding(level, feet, ground)) continue;

            return feet;
        }

        return null;
    }

    private static boolean hasRoomForPlayer(ServerLevel level, BlockPos feet, BlockPos head) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        // Air, water, grass, flowers, snow layers, and other non-solid blocks are fine.
        return !feetState.blocksMotion() && !headState.blocksMotion();
    }

    private static boolean hasSafeLanding(ServerLevel level, BlockPos feet, BlockPos ground) {
        BlockState groundState = level.getBlockState(ground);
        FluidState feetFluid = level.getFluidState(feet);
        FluidState groundFluid = level.getFluidState(ground);

        if (feetFluid.is(FluidTags.WATER) || groundFluid.is(FluidTags.WATER)) {
            return true;
        }

        if (feetFluid.is(FluidTags.LAVA) || groundFluid.is(FluidTags.LAVA)) {
            return false;
        }

        if (groundState.is(Blocks.LAVA)
                || groundState.is(Blocks.MAGMA_BLOCK)
                || groundState.is(Blocks.CACTUS)
                || groundState.is(Blocks.CAMPFIRE)
                || groundState.is(Blocks.SOUL_CAMPFIRE)
                || groundState.is(Blocks.FIRE)
                || groundState.is(Blocks.SOUL_FIRE)) {
            return false;
        }

        // Normal solid blocks, leaves, snow-covered terrain, slabs, paths, etc.
        // If it is not air/liquid/danger and has collision, it is good enough for RTP.
        return !groundState.isAir();
    }


    private static boolean isFarEnoughFromStart(SearchTask task, int x, int z) {
        double dx = x - task.startX;
        double dz = z - task.startZ;
        return (dx * dx) + (dz * dz) >= (double) MIN_RTP_DISTANCE_BLOCKS * (double) MIN_RTP_DISTANCE_BLOCKS;
    }

    private static int randomBetween(int min, int max) {
        return min + RANDOM.nextInt(Math.max(1, max - min + 1));
    }

    private static int showCooldown(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("RTP cooldown: " + TeleportConfig.getRtpCooldownSeconds() + "s").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, int seconds) {
        TeleportConfig.setRtpCooldownSeconds(seconds);
        source.sendSuccess(() -> Component.literal("Set RTP cooldown to " + seconds + "s.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listBlocked(CommandSourceStack source) {
        String blocked = TeleportConfig.blockedRtpDimensions().isEmpty()
                ? "none"
                : String.join(", ", TeleportConfig.blockedRtpDimensions());

        source.sendSuccess(() -> Component.literal("Blocked RTP dimensions: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(blocked).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal("Fallback: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(TeleportConfig.getRtpFallbackDimension()).withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private static int blockDimension(CommandSourceStack source, String dimension) {
        String normalized = TeleportConfig.normalizeDimension(dimension);
        TeleportConfig.blockRtpDimension(normalized);
        source.sendSuccess(() -> Component.literal("Blocked RTP in dimension: " + normalized).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int unblockDimension(CommandSourceStack source, String dimension) {
        String normalized = TeleportConfig.normalizeDimension(dimension);
        boolean removed = TeleportConfig.unblockRtpDimension(normalized);
        if (removed) {
            source.sendSuccess(() -> Component.literal("Unblocked RTP in dimension: " + normalized).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("That dimension was not blocked: " + normalized).withStyle(ChatFormatting.RED));
        }
        return removed ? 1 : 0;
    }

    private static int setFallback(CommandSourceStack source, String dimension) {
        String normalized = TeleportConfig.normalizeDimension(dimension);
        TeleportConfig.setRtpFallbackDimension(normalized);
        source.sendSuccess(() -> Component.literal("Set RTP fallback dimension to: " + normalized).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static boolean isSpawnHubDimension(String dimension) {
        if (dimension == null) return false;

        String normalized = dimension.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("multiworld:spawn1")
                || normalized.equals("minecraft:spawn1")
                || normalized.endsWith(":spawn1");
    }

    private static final class SearchTask {
        private final UUID playerId;
        private final ServerLevel level;
        private final SearchBounds bounds;
        private final double startX;
        private final double startZ;
        private int attempts = 0;
        private int ticks = 0;

        private SearchTask(UUID playerId, ServerLevel level, SearchBounds bounds, double startX, double startZ) {
            this.playerId = playerId;
            this.level = level;
            this.bounds = bounds;
            this.startX = startX;
            this.startZ = startZ;
        }

        private boolean tick(ServerPlayer player) {
            ticks++;

            BlockPos target = findSafePosition(this);
            if (target == null) {
                if (ticks % 100 == 0) {
                    player.sendSystemMessage(Component.literal("Still searching/generating RTP chunks... checked " + attempts + " spots.").withStyle(ChatFormatting.GRAY));
                }
                return false;
            }

            player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Teleported to a random safe location after checking " + attempts + " spots.").withStyle(ChatFormatting.GREEN));
            return true;
        }
    }

    private static final class SearchBounds {
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private SearchBounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        private static SearchBounds from(ServerLevel level) {
            WorldBorder border = level.getWorldBorder();

            int minX = (int) Math.ceil(border.getMinX()) + BORDER_PADDING;
            int maxX = (int) Math.floor(border.getMaxX()) - BORDER_PADDING;
            int minZ = (int) Math.ceil(border.getMinZ()) + BORDER_PADDING;
            int maxZ = (int) Math.floor(border.getMaxZ()) - BORDER_PADDING;

            if (minX >= maxX || minZ >= maxZ) {
                return null;
            }

            return new SearchBounds(minX, maxX, minZ, maxZ);
        }
    }
}
