package com.champutils.profile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary hard lock used while a selected profile is still hydrating.
 * Players can be connected to survival before the async SQL/Cobblemon profile load has
 * attached the correct inventories, party, permissions and location. During that small
 * window they must not move, interact, run gameplay commands, fight, or touch blocks.
 */
public final class ProfileLoadingStateManager {
    private static final Map<UUID, LoadingState> LOADING = new ConcurrentHashMap<>();
    private static final long FAILSAFE_TTL_MS = 60_000L;
    private static final String LOADING_DIMENSION = ProfileLobbyManager.PROFILE_LOBBY_DIMENSION;
    private static final double LOADING_X = ProfileLobbyManager.LOBBY_X;
    private static final double LOADING_Y = ProfileLobbyManager.LOBBY_Y;
    private static final double LOADING_Z = ProfileLobbyManager.LOBBY_Z;
    private static final float LOADING_YAW = ProfileLobbyManager.LOBBY_YAW;
    private static final float LOADING_PITCH = ProfileLobbyManager.LOBBY_PITCH;
    private static boolean registered = false;

    private ProfileLoadingStateManager() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(ProfileLoadingStateManager::tick);
    }

    public static void begin(ServerPlayer player, String profileName) {
        begin(player, profileName, false, true);
    }

    /**
     * Full quarantine variant used for cross-server profile transfer.
     * It moves the player to the isolated profile_lobby dimension and clears visible live state
     * so no inventory/party/profile-dependent systems can act on stale data while SQL/Cobblemon
     * hydration is still in progress.
     */
    public static void beginBlank(ServerPlayer player, String profileName) {
        begin(player, profileName, true, true);
    }

    public static void beginBlankSilent(ServerPlayer player, String profileName) {
        begin(player, profileName, true, false);
    }

    private static void begin(ServerPlayer player, String profileName, boolean blankLiveState, boolean showInitialTitle) {
        if (player == null) return;
        String clean = profileName == null || profileName.isBlank() ? "Profile" : profileName.trim();
        // Dedicated profile_lobby backend may use the isolated lobby world as the lock target.
        // SURVIVAL must not bounce a joining player into multiworld:profile_lobby just to wait for
        // hydration. That extra cross-dimension teleport forces chunk/dimension work on the survival
        // tick, then the final saved-location teleport does it again. Freeze the player exactly where
        // Velocity placed them, blank stale state, and only do the final saved-location teleport after
        // the profile is fully hydrated and the destination chunk is preloaded.
        boolean localSurvivalQuarantine = blankLiveState && ProfileNetworkTransferFlow.isSurvivalServer();
        LockTarget target = blankLiveState && !localSurvivalQuarantine ? loadingTarget(player) : currentTarget(player);
        LOADING.compute(player.getUUID(), (uuid, existing) -> {
            long started = existing == null ? System.currentTimeMillis() : existing.startedAtMillis;
            LockTarget effectiveTarget = existing == null || blankLiveState ? target : new LockTarget(existing.dimension, existing.x, existing.y, existing.z, existing.yaw, existing.pitch);
            return new LoadingState(clean, started, effectiveTarget.dimension, effectiveTarget.x, effectiveTarget.y, effectiveTarget.z, effectiveTarget.yaw, effectiveTarget.pitch);
        });
        if (blankLiveState) {
            blankLiveState(player);
            if (!localSurvivalQuarantine) {
                teleportToLoadingTarget(player);
            }
        }
        if (showInitialTitle) apply(player, clean, true);
    }

    public static void end(ServerPlayer player) {
        if (player == null) return;
        LoadingState state = LOADING.remove(player.getUUID());
        if (state != null) {
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.resetFallDistance();
        }
        // If the selected profile is active, fully undo quarantine effects immediately.
        // The old code only removed the map entry, leaving Adventure/invulnerable/invisible state
        // behind until another system happened to clean it up.
        try {
            if (PlayerProfileManager.hasActiveProfile(player)) {
                ProfileLobbyManager.applyNormalPlayerState(player);
                if (player.server != null) {
                    player.server.execute(() -> {
                        if (!player.hasDisconnected() && PlayerProfileManager.hasActiveProfile(player) && !isLoading(player)) {
                            ProfileLobbyManager.applyNormalPlayerState(player);
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
    }


    public static boolean isLoading(ServerPlayer player) {
        if (player == null) return false;
        LoadingState state = LOADING.get(player.getUUID());
        if (state == null) return false;
        if (System.currentTimeMillis() - state.startedAtMillis > FAILSAFE_TTL_MS) {
            LOADING.remove(player.getUUID(), state);
            return false;
        }
        return true;
    }

    public static boolean isAllowedCommand(String command) {
        if (command == null) return false;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        clean = clean.trim().toLowerCase(java.util.Locale.ROOT);
        return clean.equals("login") || clean.startsWith("login ");
    }

    public static void deny(ServerPlayer player) {
        if (player == null) return;
        LoadingState state = LOADING.get(player.getUUID());
        String name = state == null ? "Profile" : state.profileName;
        player.sendSystemMessage(Component.literal("Loading " + name + " Profile... Please wait.").withStyle(ChatFormatting.YELLOW));
    }

    private static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LoadingState state = LOADING.get(player.getUUID());
            if (state == null) continue;
            if (now - state.startedAtMillis > FAILSAFE_TTL_MS) {
                LOADING.remove(player.getUUID(), state);
                continue;
            }
            apply(player, state.profileName, false);
            ServerLevel level = resolveLevel(server, state.dimension);
            if (level == null) level = player.serverLevel();
            boolean wrongDimension = player.serverLevel() == null || !player.serverLevel().dimension().location().toString().equals(state.dimension);
            if (wrongDimension || player.distanceToSqr(state.x, state.y, state.z) > 0.04D) {
                player.teleportTo(level, state.x, state.y, state.z, state.yaw, state.pitch);
            }
        }
    }

    private static void apply(ServerPlayer player, String profileName, boolean showTitle) {
        if (player == null) return;
        try { player.setGameMode(GameType.ADVENTURE); } catch (Throwable ignored) {}
        player.setInvulnerable(true);
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.resetFallDistance();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        player.clearFire();
        if (showTitle && player.connection != null) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 60, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§eLoading " + profileName + " Profile...")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7Please wait")));
        }
    }

    private static LockTarget currentTarget(ServerPlayer player) {
        String dimension = player.serverLevel() == null ? LOADING_DIMENSION : player.serverLevel().dimension().location().toString();
        return new LockTarget(dimension, player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    private static LockTarget loadingTarget(ServerPlayer player) {
        String dimension = LOADING_DIMENSION;
        if (player != null && player.server != null) {
            ServerLevel level = resolveLevel(player.server, LOADING_DIMENSION);
            if (level != null) dimension = level.dimension().location().toString();
        }
        return new LockTarget(dimension, LOADING_X, LOADING_Y, LOADING_Z, LOADING_YAW, LOADING_PITCH);
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
            ServerLevel level = server.getLevel(key);
            if (level != null) return level;
        } catch (Throwable ignored) {}
        return null;
    }

    private static void teleportToLoadingTarget(ServerPlayer player) {
        if (player == null || player.server == null) return;
        LoadingState state = LOADING.get(player.getUUID());
        String dimension = state == null ? LOADING_DIMENSION : state.dimension;
        ServerLevel level = resolveLevel(player.server, dimension);
        if (level == null) {
            player.sendSystemMessage(Component.literal("Profile loading world is missing: " + dimension + ". Staying locked at your current position instead of falling back to overworld.").withStyle(ChatFormatting.RED));
            return;
        }
        player.teleportTo(level, LOADING_X, LOADING_Y, LOADING_Z, LOADING_YAW, LOADING_PITCH);
    }

    private static void blankLiveState(ServerPlayer player) {
        if (player == null) return;
        try { PlayerProfileManager.unload(player.getUUID()); } catch (Throwable ignored) {}
        try { VanillaProfileStateManager.clearLiveForMenu(player); } catch (Throwable ignored) {}
        try { CobblemonProfileStateManager.clearLive(player); } catch (Throwable ignored) {}
    }

    private record LockTarget(String dimension, double x, double y, double z, float yaw, float pitch) {}
    private record LoadingState(String profileName, long startedAtMillis, String dimension, double x, double y, double z, float yaw, float pitch) {}
}
