package com.champutils.profile;

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

    private IslanderProfileManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        tickCounter++;
        if (tickCounter % 40 != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            enforceLocation(player);
        }
    }

    public static boolean canEnterTerritory(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null || player.hasPermissions(4)) return true;

        boolean playerIsIslander = PlayerProfileManager.isIslander(player);
        boolean targetIsIslander = isIslanderTerritory(territory);

        if (playerIsIslander) {
            // Islanders are ONLY allowed in Islander territories. Spawn is handled by dimension rules, not territory trust.
            return targetIsIslander;
        }

        // Non-islanders can never enter Islander territories.
        return !targetIsIslander;
    }

    public static boolean isIslanderTerritory(TerritoryRepository.Territory territory) {
        if (territory == null || territory.ownerType != TerritoryRepository.OwnerType.PLAYER) return false;
        return PlayerProfileManager.modeOfProfileIdBlocking(territory.ownerId) == ProfileGameMode.ISLANDER;
    }

    public static void enforceLocation(ServerPlayer player) {
        if (player == null || player.hasPermissions(4)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        boolean playerIsIslander = PlayerProfileManager.isIslander(player);
        boolean islanderWorld = isIslanderWorld(level);
        boolean islanderMineWorld = IslanderMineManager.isMineWorld(level);
        boolean spawnWorld = isSpawnWorld(level);

        // Absolute world wall:
        // Islanders: spawn + islander_* territory worlds + islander_mine_* shared mine worlds ONLY.
        // Non-islanders: never islander_* territory worlds or islander_mine_* shared mine worlds.
        if (playerIsIslander) {
            if (!spawnWorld && !islanderWorld && !islanderMineWorld) {
                denyAndSendToSpawn(player, "Islanders can only access spawn, Islander territories, and Islander mines.");
                return;
            }
        } else if (islanderWorld || islanderMineWorld) {
            denyAndSendToSpawn(player, "Only Islander profiles can enter Islander worlds.");
            return;
        }

        TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, player.blockPosition());

        if (playerIsIslander) {
            // Spawn is always allowed. Shared mine worlds are allowed only inside the sealed managed mine region.
            // Regular islander_* worlds still require being inside a real Islander territory.
            if (spawnWorld) return;
            if (islanderMineWorld) {
                if (IslanderMineManager.isMineLocation(level, player.blockPosition())) return;
                denyAndSendToSpawn(player, "Stay inside the sealed Islander mine area.");
                return;
            }
            if (territory == null || !isIslanderTerritory(territory)) {
                denyAndSendToSpawn(player, "Islanders can only access Islander territories, Islander mines, and spawn.");
                return;
            }
        } else if (territory == null) {
            return;
        }

        if (territory == null) return;
        if (canEnterTerritory(player, territory) && TerritoryRepository.canEnter(player, territory)) return;

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
