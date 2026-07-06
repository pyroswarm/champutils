package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import com.champutils.teleport.TeleportConfig;
import com.champutils.teleport.TeleportLocation;
import com.champutils.territory.TerritoryRepository;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hard isolation rules for Islander profiles.
 *
 * Critical rule:
 * - Islander profiles may ONLY be in spawn or islander_* worlds/territories.
 * - They may NOT enter normal exploration/MMO worlds, guild territories, or any other non-Islander area.
 *
 * Non-Islanders may never enter Islander worlds/territories.
 */
public final class IslanderProfileManager {
    private static int tickCounter = 0;
    private static final java.util.Map<String, ProfileGameMode> PROFILE_MODE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> PROFILE_MODE_LOADS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private IslanderProfileManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % 100 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforceLocation(player);
        }
    }


    public static boolean canEnterTerritoryFast(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null || player.hasPermissions(4)) return true;
        if (ProfileLoadingStateManager.isLoading(player)) {
            IslanderDebugManager.log(player, "canEnterFast", territory, "DENY", "profile_loading");
            return false;
        }
        if (!PlayerProfileManager.hasActiveProfile(player)) {
            IslanderDebugManager.log(player, "canEnterFast", territory, "DENY", "no_active_profile");
            return false;
        }

        boolean playerIsIslander = PlayerProfileManager.isIslander(player);
        boolean targetIsIslander = isIslanderTerritory(player, territory);

        if (playerIsIslander) {
            boolean allowed = targetIsIslander;
            IslanderDebugManager.log(player, "canEnterFast", territory, allowed ? "ALLOW" : "DENY", allowed ? "islander_to_islander" : "islander_to_non_islander");
            return allowed;
        }

        boolean allowed = !targetIsIslander;
        IslanderDebugManager.log(player, "canEnterFast", territory, allowed ? "ALLOW" : "DENY", allowed ? "normal_to_normal" : "normal_to_islander");
        return allowed;
    }

    public static boolean canEnterTerritory(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null || player.hasPermissions(4)) return true;
        if (ProfileLoadingStateManager.isLoading(player)) {
            IslanderDebugManager.log(player, "canEnter", territory, "DENY", "profile_loading");
            return false;
        }
        if (!PlayerProfileManager.hasActiveProfile(player)) {
            IslanderDebugManager.log(player, "canEnter", territory, "DENY", "no_active_profile");
            return false;
        }

        boolean playerIsIslander = PlayerProfileManager.isIslander(player);
        boolean targetIsIslander = isIslanderTerritory(player, territory);

        if (playerIsIslander) {
            // Islanders are ONLY allowed in Islander territories. Spawn is handled by dimension rules, not territory trust.
            boolean allowed = targetIsIslander;
            IslanderDebugManager.log(player, "canEnter", territory, allowed ? "ALLOW" : "DENY", allowed ? "islander_to_islander" : "islander_to_non_islander");
            return allowed;
        }

        // Non-islanders can never enter Islander territories.
        boolean allowed = !targetIsIslander;
        IslanderDebugManager.log(player, "canEnter", territory, allowed ? "ALLOW" : "DENY", allowed ? "normal_to_normal" : "normal_to_islander");
        return allowed;
    }

    public static boolean isIslanderTerritory(TerritoryRepository.Territory territory) {
        return isIslanderTerritory(null, territory);
    }

    public static boolean isIslanderTerritory(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (territory == null || territory.ownerType != TerritoryRepository.OwnerType.PLAYER) return false;
        // Hot path: this can be called from territory movement checks. Never hit SQL here.
        // Islander territory worlds are named islander_*; use that as the fast path and cache DB fallback.
        String world = territory.worldName == null ? "" : territory.worldName.toLowerCase(java.util.Locale.ROOT);
        String path = world.contains(":") ? world.substring(world.indexOf(':') + 1) : world;
        if (path.startsWith("islander_")) return true;

        if (player != null) {
            java.util.UUID activeProfile = PlayerProfileManager.activeProfileId(player);
            if (activeProfile != null && territory.ownerId.equalsIgnoreCase(activeProfile.toString())) {
                return PlayerProfileManager.isIslander(player);
            }
        }

        ProfileGameMode cached = PROFILE_MODE_CACHE.get(territory.ownerId);
        if (cached != null) return cached == ProfileGameMode.ISLANDER;
        warmProfileModeAsync(territory.ownerId);
        return false;
    }

    private static void warmProfileModeAsync(String profileId) {
        if (profileId == null || profileId.isBlank() || !PROFILE_MODE_LOADS.add(profileId)) return;
        if (!DatabaseManager.isEnabled()) {
            PROFILE_MODE_LOADS.remove(profileId);
            return;
        }
        DatabaseManager.executeAsync("warm islander territory profile mode " + profileId, connection -> {
            try {
                ProfileGameMode loaded = PlayerProfileManager.modeOfProfileIdBlocking(profileId);
                PROFILE_MODE_CACHE.put(profileId, loaded);
            }
            finally {
                PROFILE_MODE_LOADS.remove(profileId);
            }
        });
    }

    /**
     * Non-blocking profile mode lookup for diagnostics and movement hot paths.
     * Returns null when the mode has not been warmed yet. Callers must not fall
     * back to SQL on the Minecraft server thread.
     */
    public static ProfileGameMode cachedProfileMode(String profileId) {
        if (profileId == null || profileId.isBlank()) return null;
        return PROFILE_MODE_CACHE.get(profileId);
    }

    public static void enforceLocation(ServerPlayer player) {
        if (player == null || player.hasPermissions(4)) return;
        if (ProfileLoadingStateManager.isLoading(player)) {
            IslanderDebugManager.log(player, "enforceLocation", null, "SKIP", "profile_loading");
            return;
        }
        if (!PlayerProfileManager.hasActiveProfile(player)) {
            IslanderDebugManager.log(player, "enforceLocation", null, "SKIP", "no_active_profile");
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) return;

        boolean playerIsIslander = PlayerProfileManager.isIslander(player);
        boolean islanderWorld = isIslanderWorld(level);
        boolean islanderMineWorld = IslanderMineManager.isMineWorld(level);
        boolean spawnWorld = isSpawnWorld(level);
        TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, player.blockPosition());
        if (territory == null && playerIsIslander && islanderWorld) {
            java.util.UUID activeProfile = PlayerProfileManager.activeProfileId(player);
            TerritoryRepository.Territory owned = activeProfile == null ? null : TerritoryRepository.cachedForOwner(TerritoryRepository.OwnerType.PLAYER, activeProfile.toString());
            if (owned != null && owned.containsLoose(level.dimension().location().toString(), player.blockPosition())) {
                territory = owned;
                IslanderDebugManager.log(player, "enforceLocation", territory, "RECOVER", "owner_territory_fallback_world_or_server_mismatch");
            }
        }
        boolean territoryIsIslander = isIslanderTerritory(player, territory);

        // Important: check the claimed territory BEFORE the dimension wall.
        // Islander islands may live in the normal survival/overworld dimension, so a world-only
        // check incorrectly kicks players even when they are standing inside their Islander claim.
        if (playerIsIslander) {
            if (spawnWorld) {
                IslanderDebugManager.log(player, "enforceLocation", territory, "ALLOW", "islander_spawn_world");
                return;
            }

            if (islanderMineWorld) {
                if (IslanderMineManager.isMineLocation(level, player.blockPosition())) {
                    IslanderDebugManager.log(player, "enforceLocation", territory, "ALLOW", "islander_mine_location");
                    return;
                }
                IslanderDebugManager.log(player, "enforceLocation", territory, "DENY", "islander_mine_outside_bounds");
                denyAndSendToSpawn(player, "Stay inside the sealed Islander mine area.");
                return;
            }

            if (territoryIsIslander) {
                if (TerritoryRepository.canEnterFast(player, territory)) {
                    IslanderDebugManager.log(player, "enforceLocation", territory, "ALLOW", "islander_territory_can_enter");
                    return;
                }
                IslanderDebugManager.log(player, "enforceLocation", territory, "DENY", "islander_territory_locked_or_not_owner_trusted");
                denyAndSendToSpawn(player, "That Islander territory is locked.");
                return;
            }

            IslanderDebugManager.log(player, "enforceLocation", territory, "DENY", "islander_not_in_spawn_mine_or_islander_territory");
            denyAndSendToSpawn(player, "Islanders can only access claimed Islander islands, Islander mines, and spawn.");
            return;
        }

        // Non-islanders can never enter Islander worlds, Islander mines, or Islander territories.
        if (islanderWorld || islanderMineWorld || territoryIsIslander) {
            IslanderDebugManager.log(player, "enforceLocation", territory, "DENY", "normal_in_islander_area");
            denyAndSendToSpawn(player, "Only Islander profiles can enter Islander islands and mines.");
            return;
        }

        if (territory == null) {
            IslanderDebugManager.log(player, "enforceLocation", null, "ALLOW", "normal_no_territory");
            return;
        }
        if (TerritoryRepository.canEnterFast(player, territory)) {
            IslanderDebugManager.log(player, "enforceLocation", territory, "ALLOW", "normal_territory_can_enter");
            return;
        }

        IslanderDebugManager.log(player, "enforceLocation", territory, "DENY", "normal_territory_locked");
        denyAndSendToSpawn(player, "That territory is locked by profile type rules.");
    }

    public static boolean isIslanderWorld(ServerLevel level) {
        if (level == null) return false;
        String path = level.dimension().location().getPath().toLowerCase(java.util.Locale.ROOT);
        return path.startsWith("islander_");
    }

    public static boolean isSpawnWorld(ServerLevel level) {
        if (level == null) return false;
        String id = level.dimension().location().toString();
        String path = level.dimension().location().getPath();
        TeleportLocation spawn = TeleportConfig.getSpawn();
        if (spawn != null && spawn.dimension != null && spawn.dimension.equalsIgnoreCase(id)) return true;
        return id.equalsIgnoreCase("multiworld:spawn")
                || id.equalsIgnoreCase("multiworld:spawn1")
                || path.equalsIgnoreCase("spawn")
                || path.equalsIgnoreCase("spawn1");
    }

    private static void denyAndSendToSpawn(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
        TeleportLocation spawn = TeleportConfig.getSpawn();
        if (spawn != null) TeleportConfig.teleport(player, spawn);
    }
}
