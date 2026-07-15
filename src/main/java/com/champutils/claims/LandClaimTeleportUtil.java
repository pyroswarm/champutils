package com.champutils.claims;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.teleport.SafeTeleportManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.UUID;

/** Profile-aware cross-server travel for numbered land claims. */
public final class LandClaimTeleportUtil {
    private static final String PENDING_KEY = "pending_land_claim_transfer";
    private static final long TTL_MS = 120_000L;

    private LandClaimTeleportUtil() {}

    public static boolean teleport(ServerPlayer player, LandClaimRepository.Claim claim, int displayNumber) {
        if (player == null || claim == null) return false;
        String targetServer = claim.serverId == null ? "" : claim.serverId.trim();
        if (!targetServer.isBlank() && !targetServer.equalsIgnoreCase(NetworkServerConfig.serverId())) {
            PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
            if (active == null) return false;
            PendingClaimTransfer pending = new PendingClaimTransfer();
            pending.claimId = claim.id.toString();
            pending.targetServerId = targetServer;
            pending.displayNumber = displayNumber;
            pending.expiresAtMillis = System.currentTimeMillis() + TTL_MS;
            player.sendSystemMessage(Component.literal("Sending you to " + displayServer(targetServer) + " for claim #" + displayNumber + ".").withStyle(ChatFormatting.YELLOW));
            SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_KEY, pending).whenComplete((ignored, error) -> player.server.execute(() -> {
                if (!SafeTeleportManager.isLive(player)) return;
                if (error != null) {
                    player.sendSystemMessage(Component.literal("Could not prepare the cross-server claim transfer.").withStyle(ChatFormatting.RED));
                    return;
                }
                ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, targetServer, message -> {
                    if (message != null && message.startsWith("Could not")) {
                        clear(player.getUUID());
                        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                    }
                });
            }));
            return true;
        }
        return teleportLocal(player, claim, displayNumber);
    }

    public static void handleProfileReady(ServerPlayer player) {
        if (player == null) return;
        SharedJsonStateRepository.loadPlayerAsync(player.getUUID(), PENDING_KEY, PendingClaimTransfer.class, null)
                .thenAccept(pending -> player.server.execute(() -> consume(player, pending)));
    }

    private static void consume(ServerPlayer player, PendingClaimTransfer pending) {
        if (!SafeTeleportManager.isLive(player) || pending == null) return;
        if (pending.expiresAtMillis < System.currentTimeMillis()) {
            clear(player.getUUID());
            return;
        }
        if (pending.targetServerId == null || !pending.targetServerId.equalsIgnoreCase(NetworkServerConfig.serverId())) return;
        UUID claimId;
        try { claimId = UUID.fromString(pending.claimId); }
        catch (Exception ignored) { clear(player.getUUID()); return; }

        LandClaimRepository.Claim claim = LandClaimRepository.findById(claimId);
        UUID activeProfile = PlayerProfileManager.activeProfileId(player);
        if (claim == null || activeProfile == null || !activeProfile.equals(claim.profileId)) {
            clear(player.getUUID());
            player.sendSystemMessage(Component.literal("That claim is no longer available to this profile.").withStyle(ChatFormatting.RED));
            return;
        }
        clear(player.getUUID());
        if (!teleportLocal(player, claim, pending.displayNumber)) {
            player.sendSystemMessage(Component.literal("Could not find a safe location inside that claim.").withStyle(ChatFormatting.RED));
        }
    }

    private static boolean teleportLocal(ServerPlayer player, LandClaimRepository.Claim claim, int displayNumber) {
        ServerLevel level = null;
        for (ServerLevel candidate : player.server.getAllLevels()) {
            if (candidate.dimension().location().toString().equalsIgnoreCase(claim.worldName)) {
                level = candidate;
                break;
            }
        }
        if (level == null) return false;
        BlockPos safe = findSafeClaimTeleport(level, claim);
        if (safe == null) return false;
        boolean moved = SafeTeleportManager.teleport(player, level, safe.getX() + 0.5D, safe.getY(), safe.getZ() + 0.5D, player.getYRot(), player.getXRot());
        if (moved) player.sendSystemMessage(Component.literal("Teleported to claim #" + displayNumber + ".").withStyle(ChatFormatting.GREEN));
        return moved;
    }

    private static BlockPos findSafeClaimTeleport(ServerLevel level, LandClaimRepository.Claim claim) {
        int centerX = claim.minX + ((claim.maxX - claim.minX) / 2);
        int centerZ = claim.minZ + ((claim.maxZ - claim.minZ) / 2);
        int radius = Math.max(1, Math.min(24, Math.max(claim.maxX - claim.minX, claim.maxZ - claim.minZ) / 2));
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (x < claim.minX || x > claim.maxX || z < claim.minZ || z > claim.maxZ) continue;
                    level.getChunk(x >> 4, z >> 4);
                    BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, level.getMinBuildHeight(), z));
                    BlockPos feet = new BlockPos(surface.getX(), Math.max(level.getMinBuildHeight() + 1, surface.getY() + 1), surface.getZ());
                    if (isSafe(level, feet)) return feet;
                    BlockPos atSurface = new BlockPos(surface.getX(), Math.max(level.getMinBuildHeight() + 1, surface.getY()), surface.getZ());
                    if (isSafe(level, atSurface)) return atSurface;
                }
            }
        }
        return null;
    }

    private static boolean isSafe(ServerLevel level, BlockPos feet) {
        if (feet == null || feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 2) return false;
        BlockState floor = level.getBlockState(feet.below());
        BlockState body = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (floor.isAir() || !floor.getFluidState().isEmpty() || floor.is(Blocks.CACTUS) || floor.is(Blocks.MAGMA_BLOCK) || floor.is(Blocks.CAMPFIRE) || floor.is(Blocks.SOUL_CAMPFIRE) || floor.is(Blocks.FIRE) || floor.is(Blocks.SOUL_FIRE)) return false;
        if (!body.getCollisionShape(level, feet).isEmpty() || !head.getCollisionShape(level, feet.above()).isEmpty()) return false;
        return !body.getFluidState().is(FluidTags.WATER) && !body.getFluidState().is(FluidTags.LAVA)
                && !head.getFluidState().is(FluidTags.WATER) && !head.getFluidState().is(FluidTags.LAVA);
    }

    private static void clear(UUID playerUuid) {
        PendingClaimTransfer cleared = new PendingClaimTransfer();
        SharedJsonStateRepository.savePlayerAsync(playerUuid, PENDING_KEY, cleared);
    }

    private static String displayServer(String value) {
        if (value == null || value.isBlank()) return "the claim server";
        String clean = value.trim();
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    public static final class PendingClaimTransfer {
        public String claimId = "";
        public String targetServerId = "";
        public int displayNumber = 1;
        public long expiresAtMillis = 0L;
    }
}
