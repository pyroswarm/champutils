package com.champutils.territory;

import com.champutils.guild.GuildRepository;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Console/player-command bridge for Multiworld territory worlds.
 *
 * Territories intentionally do NOT use Chunky. Chunky is useful for shared exploration worlds, but it is a bad fit
 * for packed per-player/per-guild territory slots because there is no reliable completion hook here and it can leave
 * territories stuck in GENERATING. This manager only ensures the packed Multiworld dimension exists/loads, then marks
 * the territory READY.
 */
public final class TerritoryWorldGenerationManager {
    private static final Set<String> WORLD_REQUESTED_THIS_RUNTIME = new HashSet<>();
    private static final Set<UUID> TERRITORY_REQUESTED_THIS_RUNTIME = new HashSet<>();
    private static final Set<UUID> READY_NOTIFIED_THIS_RUNTIME = new HashSet<>();
    private static final Map<UUID, UUID> READY_INITIATORS = new ConcurrentHashMap<>();

    private TerritoryWorldGenerationManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null || !TerritoryConfig.get().enabled || !TerritoryConfig.get().runGenerationCommands) return;
        if (server.getTickCount() % 200 != 0) return;

        ServerPlayer fallbackPlayer = firstOnlinePlayer(server);
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory == null || territory.id == null || territory.isReady() || TerritoryRepository.isDeleting(territory)) continue;
            requestGeneration(server, fallbackPlayer, territory);
        }
    }

    public static void requestGeneration(MinecraftServer server, TerritoryRepository.Territory territory) {
        requestGeneration(server, null, territory);
    }

    public static void requestGeneration(MinecraftServer server, ServerPlayer initiator, TerritoryRepository.Territory territory) {
        if (territory == null || territory.id == null) return;
        if (initiator != null) READY_INITIATORS.putIfAbsent(territory.id, initiator.getUUID());
        boolean deleting = TerritoryRepository.isDeleting(territory);

        if (server == null && initiator != null) {
            server = initiator.server;
        }
        if (server == null) {
            // No server reference means we cannot call Multiworld commands. Leave it pending for the periodic tick.
            if (!deleting) {
                territory.generationState = "PENDING";
                TerritoryRepository.save(territory, (success, message) -> {});
            }
            return;
        }

        MinecraftServer finalServer = server;
        if (!finalServer.isSameThread()) {
            finalServer.execute(() -> requestGeneration(finalServer, initiator, territory));
            return;
        }

        if (!TERRITORY_REQUESTED_THIS_RUNTIME.add(territory.id) && territory.isReady()) return;

        boolean worldLoaded = isWorldLoaded(finalServer, territory.worldName);
        boolean commandsOk = true;

        if (!worldLoaded && TerritoryConfig.get().runGenerationCommands) {
            ServerPlayer commandPlayer = commandPlayer(finalServer, initiator);
            if (commandPlayer == null) {
                if (!deleting) {
                    territory.generationState = "PENDING";
                    TerritoryRepository.save(territory, (success, message) -> {});
                }
                System.out.println("[ChampUtils] Territory " + territory.id + " is waiting for an online player so Multiworld 1.13.1 can create/load " + territory.worldName + ".");
                return;
            }

            String worldKey = normalizedWorldKey(territory);
            System.out.println("[ChampUtils] Requesting Multiworld territory world " + territory.worldName + " as " + commandPlayer.getGameProfile().getName() + ".");
            // Only one create/load command batch should be sent per packed territory world per runtime. If another
            // player gets a free slot in an existing world, we do not re-create the world.
            if (WORLD_REQUESTED_THIS_RUNTIME.add(worldKey)) {
                for (String command : TerritoryConfig.get().worldCreateCommands) {
                    commandsOk &= run(finalServer, commandPlayer, apply(command, territory));
                }
            } else {
                for (String command : TerritoryConfig.get().worldCreateCommands) {
                    if (command != null && command.toLowerCase(Locale.ROOT).contains(" load ")) {
                        commandsOk &= run(finalServer, commandPlayer, apply(command, territory));
                    }
                }
            }
        }

        ServerLevel loadedLevel = getLoadedLevel(finalServer, territory.worldName);
        if (loadedLevel != null) {
            if (deleting) return;
            if (TerritoryConfig.get().skyblockTerritoryWorlds) {
                // Biome painting is intentionally disabled. It was reflection-heavy and could leave
                // territories stuck in GENERATING. Skyblock territories now only prepare the starter island.
                boolean prepared = TerritorySkyblockIslandManager.requestStarterAreaPreparation(loadedLevel, territory);
                if (!prepared) {
                    System.out.println("[ChampUtils] Territory " + territory.id + " is GENERATING in " + territory.worldName + " slot " + territory.slotIndex + ". Waiting for skyblock starter island.");
                    return;
                }
            } else {
                alignCenterToBiome(loadedLevel, territory);
            }

            territory.generationState = "READY";
            TerritoryRepository.save(territory, (success, message) -> {});
            TerritoryNpcManager.spawnOnceWhenReady(finalServer, territory);
            notifyTerritoryReady(finalServer, territory);
            System.out.println("[ChampUtils] Territory " + territory.id + " is READY in " + territory.worldName + " slot " + territory.slotIndex + (TerritoryConfig.get().skyblockTerritoryWorlds ? " as a skyblock territory. Biome painting is disabled." : ". Chunky was not used."));
        } else {
            if (!deleting) {
                territory.generationState = "PENDING";
                TerritoryRepository.save(territory, (success, message) -> {});
            }
            if (commandsOk) {
                System.out.println("[ChampUtils] Multiworld command was sent for " + territory.worldName + ", but the dimension is not loaded yet. Territory remains PENDING and will retry automatically.");
            } else {
                System.err.println("[ChampUtils] Failed to request/load Multiworld territory world " + territory.worldName + " for territory " + territory.id + ". Territory remains PENDING.");
            }
        }
    }

    public static void notifyTerritoryReady(MinecraftServer server, TerritoryRepository.Territory territory) {
        if (server == null || territory == null || territory.id == null) return;
        if (!READY_NOTIFIED_THIS_RUNTIME.add(territory.id)) return;

        UUID initiatorId = READY_INITIATORS.remove(territory.id);
        ServerPlayer initiator = initiatorId == null ? null : server.getPlayerList().getPlayer(initiatorId);
        if (territory.ownerType == TerritoryRepository.OwnerType.GUILD && territory.ownerId != null) {
            for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(online.getUUID());
                if (guild != null && territory.ownerId.equals(guild.id.toString())) {
                    online.sendSystemMessage(Component.literal("Your guild territory is ready! Use /gterritory home to teleport there.").withStyle(ChatFormatting.GREEN));
                }
            }
            return;
        }

        if (initiator != null) {
            initiator.sendSystemMessage(Component.literal("Your territory is ready! Use /territory home to teleport there.").withStyle(ChatFormatting.GREEN));
            return;
        }

        if (territory.ownerType == TerritoryRepository.OwnerType.PLAYER && territory.ownerId != null) {
            try {
                ServerPlayer owner = server.getPlayerList().getPlayer(UUID.fromString(territory.ownerId));
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("Your territory is ready! Use /territory home to teleport there.").withStyle(ChatFormatting.GREEN));
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isWorldLoaded(MinecraftServer server, String worldName) {
        return getLoadedLevel(server, worldName) != null;
    }

    private static ServerLevel getLoadedLevel(MinecraftServer server, String worldName) {
        if (server == null || worldName == null || worldName.isBlank()) return null;
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, id);
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void alignCenterToBiome(ServerLevel level, TerritoryRepository.Territory territory) {
        String desired = TerritoryRepository.cleanBiomePreference(territory.biomePreference);
        if (desired == null || desired.isBlank()) return;

        BlockPos start = new BlockPos(territory.centerX, 64, territory.centerZ);
        BlockPos match = findNearestBiomeCenter(level, start, desired);
        if (match == null) {
            System.out.println("[ChampUtils] Could not find requested biome '" + desired + "' near territory slot " + territory.slotIndex + ". Keeping original center.");
            return;
        }

        double y = TerritoryTeleportUtil.safeY(level, match.getX() + 0.5D, 80.0D, match.getZ() + 0.5D);
        territory.centerX = match.getX();
        territory.centerZ = match.getZ();
        territory.minX = territory.centerX - territory.radius;
        territory.maxX = territory.centerX + territory.radius;
        territory.minZ = territory.centerZ - territory.radius;
        territory.maxZ = territory.centerZ + territory.radius;
        territory.spawnX = territory.centerX + 0.5D;
        territory.spawnY = y;
        territory.spawnZ = territory.centerZ + 0.5D;
        territory.biomePreference = desired;
    }

    private static BlockPos findNearestBiomeCenter(ServerLevel level, BlockPos start, String desired) {
        if (isBiome(level, start, desired)) return start;
        final int maxRadius = Math.max(512, (TerritoryConfig.get().centerSpacing / 2) - TerritoryConfig.get().defaultRadius - 128);
        final int step = 128;
        BlockPos best = null;
        long bestDistance = Long.MAX_VALUE;
        for (int radius = step; radius <= maxRadius; radius += step) {
            for (int dx = -radius; dx <= radius; dx += step) {
                best = checkBiomeCandidate(level, start, desired, best, bestDistance, dx, -radius);
                if (best != null) bestDistance = distanceSquared(best, start);
                best = checkBiomeCandidate(level, start, desired, best, bestDistance, dx, radius);
                if (best != null) bestDistance = distanceSquared(best, start);
            }
            for (int dz = -radius + step; dz <= radius - step; dz += step) {
                best = checkBiomeCandidate(level, start, desired, best, bestDistance, -radius, dz);
                if (best != null) bestDistance = distanceSquared(best, start);
                best = checkBiomeCandidate(level, start, desired, best, bestDistance, radius, dz);
                if (best != null) bestDistance = distanceSquared(best, start);
            }
            if (best != null) return refineBiomeCenter(level, best, desired);
        }
        return null;
    }

    private static BlockPos checkBiomeCandidate(ServerLevel level, BlockPos start, String desired, BlockPos best, long bestDistance, int dx, int dz) {
        BlockPos candidate = new BlockPos(start.getX() + dx, 64, start.getZ() + dz);
        if (!isBiome(level, candidate, desired)) return best;
        long distance = distanceSquared(candidate, start);
        return distance < bestDistance ? candidate : best;
    }

    private static BlockPos refineBiomeCenter(ServerLevel level, BlockPos coarse, String desired) {
        BlockPos best = coarse;
        for (int step : new int[] {64, 32, 16}) {
            for (int dx = -step; dx <= step; dx += step) {
                for (int dz = -step; dz <= step; dz += step) {
                    BlockPos candidate = new BlockPos(best.getX() + dx, 64, best.getZ() + dz);
                    if (isBiome(level, candidate, desired)) best = candidate;
                }
            }
        }
        return best;
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isBiome(ServerLevel level, BlockPos pos, String desired) {
        try {
            ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value());
            if (biomeId == null) return false;
            return biomeId.getPath().equalsIgnoreCase(desired) || biomeId.toString().equalsIgnoreCase(desired);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizedWorldKey(TerritoryRepository.Territory territory) {
        return (territory.worldKey == null || territory.worldKey.isBlank() ? territory.worldName : territory.worldKey).toLowerCase(Locale.ROOT);
    }

    private static String apply(String command, TerritoryRepository.Territory territory) {
        if (command == null) return "";
        String world = territory.worldName == null ? "" : territory.worldName;
        String worldKey = territory.worldKey == null || territory.worldKey.isBlank() ? world : territory.worldKey;
        String worldId = worldKey.contains(":") ? worldKey.substring(worldKey.indexOf(':') + 1) : worldKey;
        return command
                .replace("{world}", world)
                .replace("{world_key}", worldKey)
                .replace("{world_id}", worldId)
                .replace("{center_x}", Integer.toString(territory.centerX))
                .replace("{center_z}", Integer.toString(territory.centerZ))
                .replace("{radius}", Integer.toString(territory.radius))
                .replace("{diameter}", Integer.toString(territory.radius * 2))
                .replace("{slot}", Integer.toString(territory.slotIndex))
                .replace("{owner_type}", territory.ownerType.name().toLowerCase(Locale.ROOT))
                .replace("{owner_name}", territory.ownerName == null ? "" : territory.ownerName);
    }

    private static ServerPlayer firstOnlinePlayer(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null || server.getPlayerList().getPlayers().isEmpty()) return null;
        return server.getPlayerList().getPlayers().get(0);
    }

    private static ServerPlayer commandPlayer(MinecraftServer server, ServerPlayer initiator) {
        if (initiator != null && initiator.server == server && !initiator.hasDisconnected()) return initiator;
        return firstOnlinePlayer(server);
    }

    private static String sanitizeMultiworldCommand(String clean) {
        if (clean == null) return "";
        // Multiworld commands take the plain world id. Minecraft stores/loads it as multiworld:<id> later.
        return clean
                .replace("mw create multiworld:", "mw create ")
                .replace("multiworld:territories_", "territories_")
                .replace("multiworld:guild_territories_", "guild_territories_")
                .replace("mw load multiworld:", "mw load ");
    }

    private static boolean run(MinecraftServer server, ServerPlayer initiator, String command) {
        if (server == null || command == null || command.isBlank()) return true;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        clean = sanitizeMultiworldCommand(clean);
        if (initiator == null || initiator.server != server || initiator.hasDisconnected()) {
            System.err.println("[ChampUtils] Cannot run territory world command without an online player source for Multiworld 1.13.1: /" + clean);
            return false;
        }
        try {
            CommandSourceStack source = initiator.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, clean);
            System.out.println("[ChampUtils] Ran territory world command as " + initiator.getGameProfile().getName() + ": " + clean);
            return true;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Territory world command failed: /" + clean);
            e.printStackTrace();
            return false;
        }
    }
}
