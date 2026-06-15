package com.champutils.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class TeleportConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/teleport_config.json");

    private static Data data = new Data();

    private TeleportConfig() {
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!FILE.exists()) {
                data.rtpBlockedDimensions.add("multiworld:spawn1");
                data.rtpBlockedDimensions.add("minecraft:spawn1");
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }

            if (data.warps == null) data.warps = new HashMap<>();
            if (data.rtpBlockedDimensions == null) data.rtpBlockedDimensions = new HashSet<>();
            if (data.portals == null) data.portals = new HashMap<>();
            if (data.rtpFallbackDimension == null || data.rtpFallbackDimension.isBlank()) {
                data.rtpFallbackDimension = "minecraft:overworld";
            }

            data.rtpBlockedDimensions.add("multiworld:spawn1");
            data.rtpBlockedDimensions.add("minecraft:spawn1");

            save();
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
            data.rtpBlockedDimensions.add("multiworld:spawn1");
            data.rtpBlockedDimensions.add("minecraft:spawn1");
            save();
        }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static TeleportLocation capture(ServerPlayer player) {
        return new TeleportLocation(
                player.serverLevel().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    public static boolean teleport(ServerPlayer player, TeleportLocation location) {
        if (player == null || location == null) {
            return false;
        }

        ServerLevel level = resolveLevel(player.server, location.dimension);
        if (level == null) {
            return false;
        }

        return SafeTeleportManager.teleport(player, level, location.x, location.y, location.z, location.yaw, location.pitch);
    }

    public static TeleportLocation getSpawn() {
        return data.spawn;
    }

    public static void setSpawn(TeleportLocation spawn) {
        data.spawn = spawn;
        save();
    }

    public static Map<String, TeleportLocation> warps() {
        return data.warps;
    }

    public static Set<String> warpNames() {
        return new LinkedHashSet<>(data.warps.keySet());
    }

    public static TeleportLocation getWarp(String name) {
        return data.warps.get(clean(name));
    }

    public static void setWarp(String name, TeleportLocation location) {
        data.warps.put(clean(name), location);
        save();
    }

    public static boolean deleteWarp(String name) {
        boolean removed = data.warps.remove(clean(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static int getRtpCooldownSeconds() {
        return Math.max(0, data.rtpCooldownSeconds);
    }

    public static String getRtpWorldUnlockPassword() {
        if (data.rtpWorldUnlockPassword == null || data.rtpWorldUnlockPassword.isBlank()) {
            data.rtpWorldUnlockPassword = "CHANGE_ME";
            save();
        }
        return data.rtpWorldUnlockPassword;
    }

    public static boolean isCorrectRtpWorldUnlockPassword(String password) {
        String configured = getRtpWorldUnlockPassword();
        return password != null && !configured.equals("CHANGE_ME") && configured.equals(password);
    }

    public static void setRtpCooldownSeconds(int seconds) {
        data.rtpCooldownSeconds = Math.max(0, seconds);
        save();
    }

    public static Set<String> blockedRtpDimensions() {
        return data.rtpBlockedDimensions;
    }

    public static boolean isRtpBlocked(String dimension) {
        return data.rtpBlockedDimensions.contains(normalizeDimension(dimension));
    }

    public static void blockRtpDimension(String dimension) {
        data.rtpBlockedDimensions.add(normalizeDimension(dimension));
        save();
    }

    public static boolean unblockRtpDimension(String dimension) {
        boolean removed = data.rtpBlockedDimensions.remove(normalizeDimension(dimension));
        if (removed) {
            save();
        }
        return removed;
    }

    public static String getRtpFallbackDimension() {
        return normalizeDimension(data.rtpFallbackDimension);
    }

    public static void setRtpFallbackDimension(String dimension) {
        data.rtpFallbackDimension = normalizeDimension(dimension);
        save();
    }

    public static Map<String, PortalRegion> portals() {
        return data.portals;
    }

    public static PortalRegion getPortal(String id) {
        return data.portals.get(clean(id));
    }

    public static PortalRegion getOrCreatePortal(String id) {
        return data.portals.computeIfAbsent(clean(id), key -> new PortalRegion());
    }

    public static boolean deletePortal(String id) {
        boolean removed = data.portals.remove(clean(id)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        if (server == null) {
            return null;
        }

        for (String candidate : dimensionCandidates(dimension)) {
            try {
                ResourceKey<Level> key = ResourceKey.create(
                        Registries.DIMENSION,
                        ResourceLocation.parse(candidate)
                );

                ServerLevel level = server.getLevel(key);
                if (level != null) {
                    return level;
                }
            } catch (Exception ignored) {
            }
        }

        String requestedPath = pathOnly(dimension);
        for (ServerLevel level : server.getAllLevels()) {
            String loaded = level.dimension().location().toString();
            if (loaded.equalsIgnoreCase(dimension)
                    || loaded.equalsIgnoreCase(normalizeDimension(dimension))
                    || pathOnly(loaded).equalsIgnoreCase(requestedPath)) {
                return level;
            }
        }

        return null;
    }

    private static List<String> dimensionCandidates(String dimension) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String normalized = normalizeDimension(dimension);
        if (dimension != null && !dimension.isBlank()) {
            candidates.add(dimension.trim());
        }
        candidates.add(normalized);

        String path = pathOnly(normalized);
        if (!path.isBlank()) {
            candidates.add("multiworld:" + path);
            candidates.add("minecraft:" + path);
            candidates.add(path);
        }

        return new ArrayList<>(candidates);
    }

    private static String pathOnly(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "overworld";
        }
        String trimmed = dimension.trim();
        int colon = trimmed.indexOf(':');
        return colon >= 0 ? trimmed.substring(colon + 1) : trimmed;
    }

    public static String normalizeDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "minecraft:overworld";
        }

        String trimmed = dimension.trim();

        if (trimmed.equalsIgnoreCase("overworld")) {
            return "minecraft:overworld";
        }

        if (trimmed.equalsIgnoreCase("nether")) {
            return "minecraft:the_nether";
        }

        if (trimmed.equalsIgnoreCase("end")) {
            return "minecraft:the_end";
        }

        if (trimmed.equalsIgnoreCase("spawn1")) {
            return "multiworld:spawn1";
        }

        if (!trimmed.contains(":")) {
            return "minecraft:" + trimmed;
        }

        return trimmed;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static final class Data {
        TeleportLocation spawn;
        Map<String, TeleportLocation> warps = new HashMap<>();
        int rtpCooldownSeconds = 300;
        Set<String> rtpBlockedDimensions = new HashSet<>();
        String rtpFallbackDimension = "minecraft:overworld";
        Map<String, PortalRegion> portals = new HashMap<>();
        String rtpWorldUnlockPassword = "CHANGE_ME";
    }
}
