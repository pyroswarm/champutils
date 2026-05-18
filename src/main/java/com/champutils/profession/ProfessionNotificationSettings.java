package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ProfessionNotificationSettings {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final Map<String, PlayerSettings> SETTINGS =
            new HashMap<>();

    private static boolean loaded =
            false;

    private ProfessionNotificationSettings() {
    }

    public static void load() {
        loaded = true;
        SETTINGS.clear();

        File file = getFile();

        if (!file.exists()) {
            File legacyFile = getLegacyFile();
            if (legacyFile.exists()) {
                file = legacyFile;
            } else {
                save();
                return;
            }
        }

        try (FileReader reader = new FileReader(file)) {
            SaveData data = GSON.fromJson(reader, SaveData.class);

            if (data != null && data.players != null) {
                SETTINGS.putAll(data.players);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File dir = getDir();

            if (!dir.exists()) {
                dir.mkdirs();
            }

            SaveData data = new SaveData();
            data.players.putAll(SETTINGS);

            try (FileWriter writer = new FileWriter(getFile())) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean areProfessionPopupsEnabled(ServerPlayer player) {
        return getSettings(player).professionPopups;
    }

    public static void setProfessionPopupsEnabled(ServerPlayer player, boolean enabled) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return;
        settings.professionPopups = enabled;
        save();
    }

    public static boolean toggleProfessionPopups(ServerPlayer player) {
        boolean enabled = !areProfessionPopupsEnabled(player);
        setProfessionPopupsEnabled(player, enabled);
        return enabled;
    }

    public static boolean areSoundEffectsEnabled(ServerPlayer player) {
        return getSettings(player).soundEffects;
    }

    public static boolean toggleSoundEffects(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.soundEffects = !settings.soundEffects;
        save();
        return settings.soundEffects;
    }

    public static boolean areBroadcastMessagesEnabled(ServerPlayer player) {
        return getSettings(player).broadcastMessages;
    }

    public static boolean toggleBroadcastMessages(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.broadcastMessages = !settings.broadcastMessages;
        save();
        return settings.broadcastMessages;
    }

    public static boolean areQueueNotificationsEnabled(ServerPlayer player) {
        return getSettings(player).queueNotifications;
    }

    public static boolean toggleQueueNotifications(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.queueNotifications = !settings.queueNotifications;
        save();
        return settings.queueNotifications;
    }

    public static boolean areDungeonNotificationsEnabled(ServerPlayer player) {
        return getSettings(player).dungeonNotifications;
    }

    public static boolean toggleDungeonNotifications(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.dungeonNotifications = !settings.dungeonNotifications;
        save();
        return settings.dungeonNotifications;
    }

    public static void playSound(
            ServerPlayer player,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
    ) {
        if (player == null || sound == null) return;
        if (!areSoundEffectsEnabled(player)) return;
        player.playNotifySound(sound, source, volume, pitch);
    }

    public static void sendBroadcast(MinecraftServer server, Component message) {
        if (server == null || message == null) return;
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (areBroadcastMessagesEnabled(target)) {
                target.sendSystemMessage(message);
            }
        }
    }

    public static void playBroadcastSound(
            MinecraftServer server,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
    ) {
        if (server == null || sound == null) return;
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            playSound(target, sound, source, volume, pitch);
        }
    }

    private static PlayerSettings getSettings(ServerPlayer player) {
        if (player == null) {
            return new PlayerSettings();
        }

        ensureLoaded();

        PlayerSettings settings = SETTINGS.get(key(player.getUUID()));
        if (settings == null) {
            return new PlayerSettings();
        }

        settings.normalizeDefaults();
        return settings;
    }

    private static PlayerSettings getOrCreateSettings(ServerPlayer player) {
        if (player == null) return null;

        ensureLoaded();

        String key = key(player.getUUID());
        PlayerSettings settings = SETTINGS.computeIfAbsent(key, ignored -> new PlayerSettings());
        settings.uuid = player.getUUID().toString();
        settings.name = player.getName().getString();
        settings.normalizeDefaults();
        return settings;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static String key(UUID uuid) {
        return uuid.toString();
    }

    private static File getDir() {
        return new File("config/champutils/player_options");
    }

    private static File getFile() {
        return new File(getDir(), "player_options.json");
    }

    private static File getLegacyFile() {
        return new File(getDir(), "profession_notifications.json");
    }

    private static class SaveData {
        Map<String, PlayerSettings> players = new HashMap<>();
    }

    private static class PlayerSettings {
        String uuid;
        String name;
        Boolean professionPopups = true;
        Boolean soundEffects = true;
        Boolean broadcastMessages = true;
        Boolean queueNotifications = true;
        Boolean dungeonNotifications = true;

        void normalizeDefaults() {
            if (professionPopups == null) professionPopups = true;
            if (soundEffects == null) soundEffects = true;
            if (broadcastMessages == null) broadcastMessages = true;
            if (queueNotifications == null) queueNotifications = true;
            if (dungeonNotifications == null) dungeonNotifications = true;
        }
    }
}
