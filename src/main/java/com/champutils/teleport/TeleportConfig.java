package com.champutils.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

public final class TeleportConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/locations.json");

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
                data = new Data();
                data.rtpBlockedDimensions.add("spawn1");
                data.rtpFallbackDimension = "minecraft:overworld";
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }

            if (data.warps == null) data.warps = new HashMap<>();
            if (data.rtpBlockedDimensions == null) data.rtpBlockedDimensions = new HashSet<>();
            if (data.rtpFallbackDimension == null || data.rtpFallbackDimension.isBlank()) data.rtpFallbackDimension = "minecraft:overworld";
            if (data.rtpCooldownSeconds < 0) data.rtpCooldownSeconds = 0;
            if (data.portals == null) data.portals = new HashMap<>();
        }
        catch (Exception exception) {
            exception.printStackTrace();
            data = new Data();
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
        }
        catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public static TeleportLocation capture(ServerPlayer player) {
        return new TeleportLocation(
                player.level().dimension().location().toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    public static boolean teleport(ServerPlayer player, TeleportLocation location) {
        ServerLevel level = resolveLevel(player.server, location.dimension);
        if (level == null) {
            return false;
        }

        player.teleportTo(level, location.x, location.y, location.z, location.yaw, location.pitch);
        return true;
    }

    public static ServerLevel resolveLevel(MinecraftServer server, String dimension) {
        String fixed = normalizeDimension(dimension);
        ResourceLocation id = ResourceLocation.parse(fixed);
        ResourceKey<Level> key = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id);
        return server.getLevel(key);
    }

    public static String normalizeDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "minecraft:overworld";
        }
        String trimmed = dimension.trim();
        return trimmed.contains(":") ? trimmed : trimmed;
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

    public static TeleportLocation getWarp(String name) {
        return data.warps.get(name.toLowerCase());
    }

    public static void setWarp(String name, TeleportLocation location) {
        data.warps.put(name.toLowerCase(), location);
        save();
    }

    public static boolean deleteWarp(String name) {
        boolean removed = data.warps.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public static int getRtpCooldownSeconds() {
        return data.rtpCooldownSeconds;
    }

    public static void setRtpCooldownSeconds(int seconds) {
        data.rtpCooldownSeconds = Math.max(0, seconds);
        save();
    }

    public static Set<String> blockedRtpDimensions() {
        return data.rtpBlockedDimensions;
    }

    public static boolean isRtpBlocked(String dimension) {
        for (String blocked : data.rtpBlockedDimensions) {
            if (matchesDimension(dimension, blocked)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesDimension(String actual, String configured) {
        if (actual == null || configured == null) return false;
        String a = actual.trim();
        String c = configured.trim();
        if (a.equals(c)) return true;
        if (!c.contains(":")) {
            return a.endsWith(":" + c) || a.equals("minecraft:" + c);
        }
        return false;
    }

    public static void blockRtpDimension(String dimension) {
        data.rtpBlockedDimensions.add(dimension);
        save();
    }

    public static boolean unblockRtpDimension(String dimension) {
        boolean removed = data.rtpBlockedDimensions.remove(dimension);
        if (removed) save();
        return removed;
    }

    public static String getRtpFallbackDimension() {
        return data.rtpFallbackDimension;
    }

    public static void setRtpFallbackDimension(String dimension) {
        data.rtpFallbackDimension = dimension;
        save();
    }

    public static Map<String, PortalRegion> portals() {
        return data.portals;
    }

    public static PortalRegion getPortal(String id) {
        return data.portals.get(id.toLowerCase());
    }

    public static PortalRegion getOrCreatePortal(String id) {
        return data.portals.computeIfAbsent(id.toLowerCase(), key -> new PortalRegion());
    }

    public static boolean deletePortal(String id) {
        boolean removed = data.portals.remove(id.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    public static final class Data {
        public TeleportLocation spawn;
        public Map<String, TeleportLocation> warps = new HashMap<>();
        public int rtpCooldownSeconds = 300;
        public Set<String> rtpBlockedDimensions = new HashSet<>(java.util.List.of("multiworld:spawn1", "minecraft:spawn1"));
        public String rtpFallbackDimension = "minecraft:overworld";
        public Map<String, PortalRegion> portals = new HashMap<>();
    }

    public static Set<String> warpNames() {
        return new LinkedHashSet<>(data.warps.keySet());
    }

}
