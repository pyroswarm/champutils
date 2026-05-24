package com.champutils.territory;

import net.minecraft.server.MinecraftServer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Console-command bridge for Multiworld + Chunky.
 *
 * This intentionally does not hard-depend on either mod's Java API. If the command syntax on the server differs,
 * edit config/champutils/territories.json instead of rebuilding ChampUtils.
 */
public final class TerritoryWorldGenerationManager {
    private static final Set<UUID> REQUESTED_THIS_RUNTIME = new HashSet<>();

    private TerritoryWorldGenerationManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null || !TerritoryConfig.get().enabled || !TerritoryConfig.get().runGenerationCommands) return;
        if (server.getTickCount() % 200 != 0) return;

        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory == null || territory.id == null || territory.isReady()) continue;
            if ("GENERATING".equalsIgnoreCase(territory.generationState)) continue;
            requestGeneration(server, territory);
        }
    }

    public static void requestGeneration(MinecraftServer server, TerritoryRepository.Territory territory) {
        if (server == null || territory == null || territory.id == null) return;
        if (!TerritoryConfig.get().runGenerationCommands) return;
        if (!REQUESTED_THIS_RUNTIME.add(territory.id)) return;

        for (String command : TerritoryConfig.get().worldCreateCommands) {
            run(server, apply(command, territory));
        }
        for (String command : TerritoryConfig.get().chunkyPregenerationCommands) {
            run(server, apply(command, territory));
        }

        territory.generationState = "GENERATING";
        TerritoryRepository.save(territory, (success, message) -> {});
        System.out.println("[ChampUtils] Requested Chunky pregeneration for territory " + territory.id + " in " + territory.worldName + ". Mark ready with /territory admin ready " + territory.id + " after Chunky finishes.");
    }

    private static String apply(String command, TerritoryRepository.Territory territory) {
        if (command == null) return "";
        return command
                .replace("{world}", territory.worldName)
                .replace("{world_key}", territory.worldKey == null ? territory.worldName : territory.worldKey)
                .replace("{center_x}", Integer.toString(territory.centerX))
                .replace("{center_z}", Integer.toString(territory.centerZ))
                .replace("{radius}", Integer.toString(territory.radius))
                .replace("{diameter}", Integer.toString(territory.radius * 2))
                .replace("{slot}", Integer.toString(territory.slotIndex))
                .replace("{owner_type}", territory.ownerType.name().toLowerCase(Locale.ROOT))
                .replace("{owner_name}", territory.ownerName == null ? "" : territory.ownerName);
    }

    private static void run(MinecraftServer server, String command) {
        if (command == null || command.isBlank()) return;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), clean);
    }
}
