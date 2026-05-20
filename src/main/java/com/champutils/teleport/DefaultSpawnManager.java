package com.champutils.teleport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DefaultSpawnManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/default_spawn_players.json");

    private static Data data = new Data();

    private DefaultSpawnManager() {
    }

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            if (!FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                Data loaded = GSON.fromJson(reader, Data.class);
                data = loaded == null ? new Data() : loaded;
            }

            if (data.seenPlayers == null) {
                data.seenPlayers = new HashSet<>();
                save();
            }
        } catch (Exception e) {
            e.printStackTrace();
            data = new Data();
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

    public static void registerRespawnHandler() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) {
                return;
            }

            // Preserve player-specific bed / respawn-anchor spawns.
            // If there is no personal spawn, send them to the configured /spawn.
            if (hasPersonalRespawn(oldPlayer)) {
                return;
            }

            newPlayer.server.execute(() -> teleportToDefaultSpawn(newPlayer, true));
        });
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUUID();
        if (data.seenPlayers.contains(uuid)) {
            return;
        }

        data.seenPlayers.add(uuid);
        save();

        player.server.execute(() -> teleportToDefaultSpawn(player, false));
    }

    private static boolean hasPersonalRespawn(ServerPlayer player) {
        if (player == null) {
            return false;
        }

        try {
            return player.getRespawnPosition() != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void teleportToDefaultSpawn(ServerPlayer player, boolean deathRespawn) {
        if (player == null) {
            return;
        }

        TeleportLocation spawn = TeleportConfig.getSpawn();
        if (spawn == null) {
            return;
        }

        if (TeleportConfig.teleport(player, spawn)) {
            if (deathRespawn) {
                player.sendSystemMessage(Component.literal("You had no bed spawn, so you were returned to server spawn.").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static final class Data {
        Set<UUID> seenPlayers = new HashSet<>();
    }
}
