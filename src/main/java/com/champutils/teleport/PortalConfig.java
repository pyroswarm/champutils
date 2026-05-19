package com.champutils.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PortalConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/portals.json");

    private static final Map<String, PortalDefinition> PORTALS = new LinkedHashMap<>();

    private PortalConfig() {}

    public static class Root {
        public Map<String, PortalDefinition> portals = new LinkedHashMap<>();
    }

    public static class PortalDefinition {
        public String id;
        public String dimension;
        public int minX;
        public int minY;
        public int minZ;
        public int maxX;
        public int maxY;
        public int maxZ;
        public String command;
        public boolean hasPos1;
        public boolean hasPos2;

        public boolean hasBothCorners() {
            return hasPos1 && hasPos2 && dimension != null && !dimension.isBlank();
        }

        public boolean hasCommand() {
            return command != null && !command.isBlank();
        }

        public boolean contains(ServerPlayer player) {
            if (player == null || dimension == null) return false;
            ServerLevel level = player.serverLevel();
            ResourceLocation current = level.dimension().location();
            if (!current.toString().equalsIgnoreCase(dimension)) return false;

            BlockPos pos = player.blockPosition();
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            PORTALS.clear();

            if (!FILE.exists()) {
                save();
                return;
            }

            Root root;
            try (FileReader reader = new FileReader(FILE)) {
                root = GSON.fromJson(reader, Root.class);
            }

            if (root == null || root.portals == null) {
                save();
                return;
            }

            for (Map.Entry<String, PortalDefinition> entry : root.portals.entrySet()) {
                String id = normalizeId(entry.getKey());
                PortalDefinition portal = entry.getValue();
                if (id == null || portal == null) continue;
                portal.id = id;
                if (portal.dimension != null) portal.dimension = portal.dimension.toLowerCase();
                normalizeCorners(portal);
                PORTALS.put(id, portal);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            Root root = new Root();
            root.portals = new LinkedHashMap<>(PORTALS);

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static PortalDefinition getOrCreate(String rawId) {
        String id = normalizeId(rawId);
        if (id == null) return null;
        return PORTALS.computeIfAbsent(id, key -> {
            PortalDefinition portal = new PortalDefinition();
            portal.id = key;
            return portal;
        });
    }

    public static PortalDefinition get(String rawId) {
        String id = normalizeId(rawId);
        if (id == null) return null;
        return PORTALS.get(id);
    }

    public static boolean remove(String rawId) {
        String id = normalizeId(rawId);
        return id != null && PORTALS.remove(id) != null;
    }

    public static Collection<PortalDefinition> all() {
        return PORTALS.values();
    }

    public static void setCorner(ServerPlayer player, String rawId, boolean firstCorner) {
        PortalDefinition portal = getOrCreate(rawId);
        if (portal == null) return;

        BlockPos pos = player.blockPosition();
        portal.dimension = player.serverLevel().dimension().location().toString().toLowerCase();

        if (firstCorner) {
            portal.hasPos1 = true;
            portal.minX = pos.getX();
            portal.minY = pos.getY();
            portal.minZ = pos.getZ();
            if (portal.maxX == 0 && portal.maxY == 0 && portal.maxZ == 0) {
                portal.maxX = pos.getX();
                portal.maxY = pos.getY();
                portal.maxZ = pos.getZ();
            }
        } else {
            portal.hasPos2 = true;
            portal.maxX = pos.getX();
            portal.maxY = pos.getY();
            portal.maxZ = pos.getZ();
        }

        normalizeCorners(portal);
        save();
    }

    public static void setCommand(String rawId, String command) {
        PortalDefinition portal = getOrCreate(rawId);
        if (portal == null) return;
        portal.command = command == null ? "" : command.trim();
        save();
    }

    private static void normalizeCorners(PortalDefinition portal) {
        int minX = Math.min(portal.minX, portal.maxX);
        int minY = Math.min(portal.minY, portal.maxY);
        int minZ = Math.min(portal.minZ, portal.maxZ);
        int maxX = Math.max(portal.minX, portal.maxX);
        int maxY = Math.max(portal.minY, portal.maxY);
        int maxZ = Math.max(portal.minZ, portal.maxZ);

        portal.minX = minX;
        portal.minY = minY;
        portal.minZ = minZ;
        portal.maxX = maxX;
        portal.maxY = maxY;
        portal.maxZ = maxZ;
    }

    public static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim().toLowerCase().replace(' ', '_');
    }
}
