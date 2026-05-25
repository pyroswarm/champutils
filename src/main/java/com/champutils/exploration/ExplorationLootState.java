package com.champutils.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class ExplorationLootState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/exploration_loot_state.json");
    private static State state = new State();

    private ExplorationLootState() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                State loaded = GSON.fromJson(reader, State.class);
                state = loaded == null ? new State() : loaded.withDefaults();
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load exploration loot state.");
            e.printStackTrace();
            state = new State();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(state.withDefaults(), writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save exploration loot state.");
            e.printStackTrace();
        }
    }

    public static boolean hasClaimed(UUID playerId, ServerLevel level, BlockPos pos) {
        return state.withDefaults().claimedKeys.contains(claimKey(playerId, level, pos));
    }

    public static void markClaimed(UUID playerId, ServerLevel level, BlockPos pos) {
        state.withDefaults().claimedKeys.add(claimKey(playerId, level, pos));
        save();
    }

    public static void markDiscovered(ServerLevel level, BlockPos pos) {
        state.withDefaults().discoveredLootPositions.add(positionKey(level, pos));
        save();
    }

    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        if (!ExplorationLootConfig.get().protectDiscoveredLootStructures) return false;
        int radius = Math.max(0, ExplorationLootConfig.get().discoveredStructureProtectionRadius);
        if (radius <= 0) return false;
        String world = level.dimension().location().toString();
        int radiusSq = radius * radius;
        for (String raw : state.withDefaults().discoveredLootPositions) {
            DiscoveredPos discovered = DiscoveredPos.parse(raw);
            if (discovered == null || !world.equals(discovered.world)) continue;
            int dx = pos.getX() - discovered.x;
            int dy = pos.getY() - discovered.y;
            int dz = pos.getZ() - discovered.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) return true;
        }
        return false;
    }

    public static void clearWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) return;
        String prefix = worldName + "|";
        state.withDefaults().claimedKeys.removeIf(key -> key.contains("|" + worldName + "|"));
        state.discoveredLootPositions.removeIf(key -> key.startsWith(prefix));
        save();
    }

    private static String claimKey(UUID playerId, ServerLevel level, BlockPos pos) {
        return playerId + "|" + positionKey(level, pos);
    }

    private static String positionKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.getX() + "|" + pos.getY() + "|" + pos.getZ();
    }

    private static final class State {
        public Set<String> claimedKeys = new LinkedHashSet<>();
        public Set<String> discoveredLootPositions = new LinkedHashSet<>();

        private State withDefaults() {
            if (claimedKeys == null) claimedKeys = new HashSet<>();
            if (discoveredLootPositions == null) discoveredLootPositions = new HashSet<>();
            return this;
        }
    }

    private static final class DiscoveredPos {
        final String world;
        final int x;
        final int y;
        final int z;

        private DiscoveredPos(String world, int x, int y, int z) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static DiscoveredPos parse(String raw) {
            try {
                String[] parts = raw.split("\\|");
                if (parts.length != 4) return null;
                return new DiscoveredPos(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
