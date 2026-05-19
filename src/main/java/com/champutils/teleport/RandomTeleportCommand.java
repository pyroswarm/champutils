package com.champutils.teleport;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RandomTeleportCommand {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> LAST_USE_MS = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 512;
    private static final int BORDER_PADDING = 32;
    private static final int RTP_DEFAULT_RADIUS = 15000;

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

    private static int rtp(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int cooldown = TeleportConfig.getRtpCooldownSeconds();
        long now = System.currentTimeMillis();
        long last = LAST_USE_MS.getOrDefault(player.getUUID(), 0L);
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

        BlockPos target = findSafePosition(targetLevel);
        if (target == null) {
            player.sendSystemMessage(Component.literal("Could not find a safe RTP location. Try again later or reduce the world border.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.teleportTo(targetLevel, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, player.getYRot(), player.getXRot());
        LAST_USE_MS.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal("Teleported to a random safe location.").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static BlockPos findSafePosition(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();

        // Use the full configured world border.
        // If the border is still vanilla-huge/default, clamp RTP to +/-15,000 so players
        // do not get sent millions of blocks away by accident.
        int borderMinX = (int) Math.ceil(border.getMinX()) + BORDER_PADDING;
        int borderMaxX = (int) Math.floor(border.getMaxX()) - BORDER_PADDING;
        int borderMinZ = (int) Math.ceil(border.getMinZ()) + BORDER_PADDING;
        int borderMaxZ = (int) Math.floor(border.getMaxZ()) - BORDER_PADDING;

        int minX = Math.max(borderMinX, -RTP_DEFAULT_RADIUS);
        int maxX = Math.min(borderMaxX, RTP_DEFAULT_RADIUS);
        int minZ = Math.max(borderMinZ, -RTP_DEFAULT_RADIUS);
        int maxZ = Math.min(borderMaxZ, RTP_DEFAULT_RADIUS);

        if (minX >= maxX || minZ >= maxZ) {
            return null;
        }

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            int x = randomBetween(minX, maxX);
            int z = randomBetween(minZ, maxZ);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            BlockPos feet = new BlockPos(x, y, z);
            BlockPos ground = feet.below();
            BlockPos head = feet.above();

            if (!border.isWithinBounds(feet)) {
                continue;
            }

            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) {
                continue;
            }

            if (!hasRoomForPlayer(level, feet, head)) {
                continue;
            }

            if (!hasSafeLanding(level, feet, ground)) {
                continue;
            }

            if (hasLargeDropNearby(level, feet)) {
                continue;
            }

            return feet;
        }

        return null;
    }

    private static boolean hasRoomForPlayer(ServerLevel level, BlockPos feet, BlockPos head) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);

        // Air, water, snow layers, grass, flowers, etc. are fine as long as they do not block movement.
        return !feetState.blocksMotion() && !headState.blocksMotion();
    }

    private static boolean hasSafeLanding(ServerLevel level, BlockPos feet, BlockPos ground) {
        BlockState groundState = level.getBlockState(ground);
        FluidState feetFluid = level.getFluidState(feet);
        FluidState groundFluid = level.getFluidState(ground);

        // Water landing is allowed. This fixes ocean/river/coast RTP failures.
        if (feetFluid.is(net.minecraft.tags.FluidTags.WATER) || groundFluid.is(net.minecraft.tags.FluidTags.WATER)) {
            return true;
        }

        // Never place players on/in lava.
        if (feetFluid.is(net.minecraft.tags.FluidTags.LAVA) || groundFluid.is(net.minecraft.tags.FluidTags.LAVA)) {
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

        // Snow, slabs, leaves, grass blocks, etc. are okay as long as the block can support the player.
        return groundState.blocksMotion() || groundState.isFaceSturdy(level, ground, net.minecraft.core.Direction.UP);
    }

    private static boolean hasLargeDropNearby(ServerLevel level, BlockPos feet) {
        // Avoid putting players right on the edge of a ravine/cliff/large ditch.
        // This still allows normal hills and shallow terrain changes.
        final int maxDrop = 5;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }

                int sampleX = feet.getX() + dx;
                int sampleZ = feet.getZ() + dz;
                int sampleY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);

                if (feet.getY() - sampleY > maxDrop) {
                    return true;
                }
            }
        }

        return false;
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
        }
        else {
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
        if (dimension == null) {
            return false;
        }

        String normalized = dimension.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("multiworld:spawn1")
                || normalized.equals("minecraft:spawn1")
                || normalized.endsWith(":spawn1");
    }

}
