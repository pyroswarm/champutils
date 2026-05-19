package com.champutils.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class ServerLocationManager {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final File FILE =
            new File(
                    "config/champutils",
                    "locations.json"
            );

    private static ServerLocation spawn =
            new ServerLocation(
                    "minecraft:overworld",
                    0.5D,
                    64.0D,
                    0.5D,
                    0.0F,
                    0.0F
            );

    private static final Map<String, ServerLocation> WARPS =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private ServerLocationManager() {
    }

    public static void load() {

        try {

            File parent =
                    FILE.getParentFile();

            if (
                    parent != null &&
                            !parent.exists()
            ) {
                parent.mkdirs();
            }

            if (
                    !FILE.exists()
            ) {
                save();
                return;
            }

            try (
                    FileReader reader =
                            new FileReader(FILE)
            ) {

                Data data =
                        GSON.fromJson(
                                reader,
                                Data.class
                        );

                if (
                        data == null
                ) {
                    save();
                    return;
                }

                if (
                        data.spawn != null
                ) {
                    spawn =
                            data.spawn;
                }

                WARPS.clear();

                if (
                        data.warps != null
                ) {
                    WARPS.putAll(
                            data.warps
                    );
                }
            }

            System.out.println(
                    "[ChampUtils] Loaded locations.json."
            );
        }
        catch (Exception e) {

            System.out.println(
                    "[ChampUtils] Failed to load locations.json."
            );

            e.printStackTrace();
        }
    }

    public static void save() {

        try {

            File parent =
                    FILE.getParentFile();

            if (
                    parent != null &&
                            !parent.exists()
            ) {
                parent.mkdirs();
            }

            Data data =
                    new Data();

            data.spawn =
                    spawn;

            data.warps =
                    new TreeMap<>(
                            WARPS
                    );

            try (
                    FileWriter writer =
                            new FileWriter(FILE)
            ) {
                GSON.toJson(
                        data,
                        writer
                );
            }
        }
        catch (Exception e) {

            System.out.println(
                    "[ChampUtils] Failed to save locations.json."
            );

            e.printStackTrace();
        }
    }

    public static void setSpawn(
            ServerPlayer player
    ) {

        spawn =
                fromPlayer(
                        player
                );

        save();
    }

    public static boolean teleportToSpawn(
            ServerPlayer player
    ) {

        return teleport(
                player,
                spawn
        );
    }

    public static void setWarp(
            String name,
            ServerPlayer player
    ) {

        WARPS.put(
                cleanName(name),
                fromPlayer(player)
        );

        save();
    }

    public static boolean deleteWarp(
            String name
    ) {

        ServerLocation removed =
                WARPS.remove(
                        cleanName(name)
                );

        if (
                removed != null
        ) {
            save();
            return true;
        }

        return false;
    }

    public static boolean teleportToWarp(
            ServerPlayer player,
            String name
    ) {

        ServerLocation location =
                WARPS.get(
                        cleanName(name)
                );

        if (
                location == null
        ) {
            return false;
        }

        return teleport(
                player,
                location
        );
    }

    public static Map<String, ServerLocation> warps() {

        return Collections.unmodifiableMap(
                WARPS
        );
    }

    public static ServerLocation getSpawn() {

        return spawn;
    }

    public static ServerLocation getWarp(
            String name
    ) {

        return WARPS.get(
                cleanName(name)
        );
    }

    public static ServerLocation fromPlayer(
            ServerPlayer player
    ) {

        return new ServerLocation(
                player.serverLevel()
                        .dimension()
                        .location()
                        .toString(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    public static boolean teleport(
            ServerPlayer player,
            ServerLocation location
    ) {

        if (
                player == null ||
                        location == null
        ) {
            return false;
        }

        ServerLevel level =
                getLevel(
                        player.getServer(),
                        location.world
                );

        if (
                level == null
        ) {
            player.sendSystemMessage(
                    Component.literal(
                            "§cThat saved location uses a world that is not loaded: §f"
                                    + location.world
                    )
            );

            return false;
        }

        player.teleportTo(
                level,
                location.x,
                location.y,
                location.z,
                location.yaw,
                location.pitch
        );

        return true;
    }

    public static ServerLevel getLevel(
            MinecraftServer server,
            String worldId
    ) {

        if (
                server == null
        ) {
            return null;
        }

        String normalized =
                normalizeWorldId(
                        worldId
                );

        try {

            ResourceKey<Level> key =
                    ResourceKey.create(
                            Registries.DIMENSION,
                            ResourceLocation.parse(
                                    normalized
                            )
                    );

            return server.getLevel(
                    key
            );
        }
        catch (Exception ignored) {
            return null;
        }
    }

    public static String normalizeWorldId(
            String worldId
    ) {

        if (
                worldId == null ||
                        worldId.isBlank()
        ) {
            return "minecraft:overworld";
        }

        String trimmed =
                worldId.trim();

        if (
                trimmed.equalsIgnoreCase(
                        "overworld"
                )
        ) {
            return "minecraft:overworld";
        }

        if (
                trimmed.equalsIgnoreCase(
                        "nether"
                )
        ) {
            return "minecraft:the_nether";
        }

        if (
                trimmed.equalsIgnoreCase(
                        "end"
                )
        ) {
            return "minecraft:the_end";
        }

        if (
                !trimmed.contains(":")
        ) {
            return "minecraft:" + trimmed;
        }

        return trimmed;
    }

    private static String cleanName(
            String name
    ) {

        if (
                name == null
        ) {
            return "";
        }

        return name.trim()
                .toLowerCase();
    }

    private static class Data {

        ServerLocation spawn;
        Map<String, ServerLocation> warps;
    }
}
