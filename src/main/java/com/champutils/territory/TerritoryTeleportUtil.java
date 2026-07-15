package com.champutils.territory;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkServerConfig;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfileNetworkTransferFlow;
import com.champutils.teleport.SafeTeleportManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

public final class TerritoryTeleportUtil {
    private static final String PENDING_TERRITORY_KEY = "pending_territory_transfer";
    private static final long PENDING_TRANSFER_TTL_MS = 120_000L;

    private TerritoryTeleportUtil() {}

    public static boolean teleportHome(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null || !territory.isReady()) return false;

        String targetServerId = territory.serverId == null ? "" : territory.serverId.trim();
        String currentServerId = NetworkServerConfig.serverId();
        if (!targetServerId.isBlank() && !targetServerId.equalsIgnoreCase(currentServerId)) {
            return routeToTerritoryServer(player, territory, targetServerId);
        }

        return teleportHomeLocal(player, territory);
    }

    private static boolean teleportHomeLocal(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null || !territory.isReady()) return false;
        ServerLevel level = resolveLevel(player.server, territory.worldName);
        if (level == null) return false;

        ensureDefaultSpawnAnchor(level, territory);
        SafeSpot spot = findSafeSpot(level, territory.spawnX, territory.spawnY, territory.spawnZ);
        return SafeTeleportManager.teleport(player, level, spot.x, spot.y, spot.z, territory.spawnYaw, territory.spawnPitch);
    }

    private static boolean routeToTerritoryServer(ServerPlayer player, TerritoryRepository.Territory territory, String targetServerId) {
        PlayerProfileManager.ProfileRecord active = PlayerProfileManager.active(player);
        if (active == null || territory.id == null) return false;

        PendingTerritoryTransfer pending = new PendingTerritoryTransfer();
        pending.territoryId = territory.id.toString();
        pending.targetServerId = targetServerId;
        pending.expiresAtMillis = System.currentTimeMillis() + PENDING_TRANSFER_TTL_MS;

        player.sendSystemMessage(Component.literal("Sending you to " + displayServer(targetServerId) + " for " + territory.publicName() + ".").withStyle(ChatFormatting.YELLOW));
        SharedJsonStateRepository.savePlayerAsync(player.getUUID(), PENDING_TERRITORY_KEY, pending)
                .whenComplete((ignored, error) -> player.server.execute(() -> {
                    if (!SafeTeleportManager.isLive(player)) return;
                    if (error != null) {
                        player.sendSystemMessage(Component.literal("Could not prepare the cross-server territory transfer. Try again shortly.").withStyle(ChatFormatting.RED));
                        return;
                    }
                    ProfileNetworkTransferFlow.issueTransferFromLobby(player, active, targetServerId, message -> {
                        if (message != null && message.startsWith("Could not")) {
                            clearPending(player.getUUID());
                            player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
                        }
                    });
                }));
        return true;
    }

    public static void handleProfileReady(ServerPlayer player) {
        if (player == null) return;
        SharedJsonStateRepository
                .loadPlayerAsync(player.getUUID(), PENDING_TERRITORY_KEY, PendingTerritoryTransfer.class, null)
                .thenAccept(pending -> player.server.execute(() -> consumePendingTransfer(player, pending)));
    }

    private static void consumePendingTransfer(ServerPlayer player, PendingTerritoryTransfer pending) {
        if (!SafeTeleportManager.isLive(player) || pending == null) return;
        if (pending.expiresAtMillis < System.currentTimeMillis()) {
            clearPending(player.getUUID());
            return;
        }
        if (pending.targetServerId == null || !pending.targetServerId.equalsIgnoreCase(NetworkServerConfig.serverId())) return;

        TerritoryRepository.Territory territory = findCachedTerritory(pending.territoryId);
        if (territory == null || !territory.isReady()) {
            player.sendSystemMessage(Component.literal("That territory is not ready on this server yet. Try again shortly.").withStyle(ChatFormatting.RED));
            clearPending(player.getUUID());
            return;
        }
        if (!TerritoryRepository.canEnter(player, territory)) {
            player.sendSystemMessage(Component.literal("You can no longer enter that territory.").withStyle(ChatFormatting.RED));
            clearPending(player.getUUID());
            return;
        }

        clearPending(player.getUUID());
        if (!teleportHomeLocal(player, territory)) {
            player.sendSystemMessage(Component.literal("Could not reach that territory home. Try again shortly.").withStyle(ChatFormatting.RED));
        }
    }

    private static TerritoryRepository.Territory findCachedTerritory(String id) {
        if (id == null || id.isBlank()) return null;
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory != null && territory.id != null && territory.id.toString().equalsIgnoreCase(id)) return territory;
        }
        return null;
    }

    private static void clearPending(java.util.UUID playerUuid) {
        PendingTerritoryTransfer cleared = new PendingTerritoryTransfer();
        cleared.expiresAtMillis = 0L;
        SharedJsonStateRepository.savePlayerAsync(playerUuid, PENDING_TERRITORY_KEY, cleared);
    }

    private static String displayServer(String serverId) {
        if (serverId == null || serverId.isBlank()) return "the territory server";
        String clean = serverId.trim();
        return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    public static final class PendingTerritoryTransfer {
        public String territoryId = "";
        public String targetServerId = "";
        public long expiresAtMillis = 0L;
    }

    public static boolean teleportInside(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null || !territory.isReady()) return false;
        ServerLevel level = resolveLevel(player.server, territory.worldName);
        if (level == null) return false;

        double minX = Math.min(territory.minX + 2.5D, territory.maxX - 0.5D);
        double maxX = Math.max(territory.maxX - 2.5D, territory.minX + 0.5D);
        double minZ = Math.min(territory.minZ + 2.5D, territory.maxZ - 0.5D);
        double maxZ = Math.max(territory.maxZ - 2.5D, territory.minZ + 0.5D);

        double x = Math.max(minX, Math.min(maxX, player.getX()));
        double z = Math.max(minZ, Math.min(maxZ, player.getZ()));
        SafeSpot spot = findSafeSpot(level, x, player.getY(), z);
        return SafeTeleportManager.teleport(player, level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
    }


    /**
     * Keeps every territory from becoming a softlock. The original/default home column gets
     * an unbreakable bedrock anchor under the stored spawn point. If the owner builds over it,
     * findSafeSpot will naturally place them on the highest safe block above this column.
     */
    public static void ensureDefaultSpawnAnchor(ServerLevel level, TerritoryRepository.Territory territory) {
        if (level == null || territory == null) return;
        int x = (int) Math.floor(territory.spawnX);
        int z = (int) Math.floor(territory.spawnZ);
        int y = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(territory.spawnY) - 2));
        forceChunk(level, x, z);
        BlockPos anchor = new BlockPos(x, y, z);
        if (!level.getBlockState(anchor).is(Blocks.BEDROCK)) {
            if (level.getBlockEntity(anchor) != null) level.removeBlockEntity(anchor);
            level.setBlock(anchor, Blocks.BEDROCK.defaultBlockState(), 3);
            if (level.getBlockEntity(anchor) != null) level.removeBlockEntity(anchor);
        }
    }

    public static ServerLevel resolveLevel(MinecraftServer server, String worldName) {
        if (server == null || worldName == null || worldName.isBlank()) return null;
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static double safeY(ServerLevel level, double x, double preferredY, double z) {
        return findSafeSpot(level, x, preferredY, z).y;
    }

    public static SafeSpot findSafeSpot(ServerLevel level, double x, double preferredY, double z) {
        if (level == null) return new SafeSpot(x, preferredY, z);

        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);

        // Force the destination chunk to exist before asking the heightmap. Without this, a brand-new Multiworld
        // dimension can report a bogus min-height result and /territory home may drop players at Y -63.
        forceChunk(level, ix, iz);

        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        BlockPos exactOrIndoorSpot = safeStandingPosNear(level, ix, iz, (int) Math.floor(preferredY), minY + 1, maxY - 2, 5);
        if (exactOrIndoorSpot != null) {
            return centered(exactOrIndoorSpot, x, z);
        }

        BlockPos preferredSpot = safeStandingPosAtOrBelow(level, ix, iz, Math.min(maxY - 2, (int) Math.ceil(preferredY) + 8), Math.max(minY + 1, (int) Math.floor(preferredY) - 8));
        if (preferredSpot != null) {
            return centered(preferredSpot, x, z);
        }

        int heightmapY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ix, iz);
        BlockPos heightmapSpot = safeStandingPosAtOrBelow(level, ix, iz, Math.min(maxY - 2, heightmapY + 4), minY + 1);
        if (heightmapSpot != null) {
            return centered(heightmapSpot, x, z);
        }

        BlockPos fullScanSpot = safeStandingPosAtOrBelow(level, ix, iz, maxY - 2, minY + 1);
        if (fullScanSpot != null) {
            return centered(fullScanSpot, x, z);
        }

        // Last resort: never use the void/min-build-height for home teleports.
        return new SafeSpot(ix + 0.5D, Math.max(64.0D, minY + 4.0D), iz + 0.5D);
    }

    private static BlockPos safeStandingPosNear(ServerLevel level, int x, int z, int preferredY, int minY, int maxY, int radius) {
        int start = Math.max(minY, Math.min(maxY, preferredY - 1));
        for (int offset = 0; offset <= radius; offset++) {
            int down = start - offset;
            if (down >= minY) {
                BlockPos candidate = safeStandingPosAtOrBelow(level, x, z, down, down);
                if (candidate != null) return candidate;
            }
            int up = start + offset;
            if (up <= maxY) {
                BlockPos candidate = safeStandingPosAtOrBelow(level, x, z, up, up);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private static void forceChunk(ServerLevel level, int blockX, int blockZ) {
        try {
            ChunkAccess ignored = level.getChunk(blockX >> 4, blockZ >> 4);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to force-load territory destination chunk at " + blockX + ", " + blockZ);
            e.printStackTrace();
        }
    }

    private static BlockPos safeStandingPosAtOrBelow(ServerLevel level, int x, int z, int fromY, int minY) {
        BlockPos.MutableBlockPos ground = new BlockPos.MutableBlockPos(x, fromY, z);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos(x, fromY + 1, z);
        BlockPos.MutableBlockPos head = new BlockPos.MutableBlockPos(x, fromY + 2, z);

        for (int y = fromY; y >= minY; y--) {
            ground.set(x, y, z);
            feet.set(x, y + 1, z);
            head.set(x, y + 2, z);

            BlockState groundState = level.getBlockState(ground);
            BlockState feetState = level.getBlockState(feet);
            BlockState headState = level.getBlockState(head);

            if (isGoodGround(groundState) && isOpen(feetState) && isOpen(headState)) {
                return feet.immutable();
            }
        }
        return null;
    }

    private static boolean isGoodGround(BlockState state) {
        return state != null
                && !state.isAir()
                && state.getFluidState().isEmpty()
                && state.blocksMotion();
    }

    private static boolean isOpen(BlockState state) {
        return state == null || (!state.blocksMotion() && state.getFluidState().isEmpty());
    }

    private static SafeSpot centered(BlockPos feet, double requestedX, double requestedZ) {
        return new SafeSpot(Math.floor(requestedX) + 0.5D, feet.getY(), Math.floor(requestedZ) + 0.5D);
    }

    public record SafeSpot(double x, double y, double z) {}
}
