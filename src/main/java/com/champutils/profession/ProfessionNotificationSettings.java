package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.champutils.network.NetworkEventManager;
import com.champutils.database.SharedJsonStateRepository;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfessionNotificationSettings {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    private static final Map<String, PlayerSettings> SETTINGS =
            new HashMap<>();

    private static boolean loaded =
            false;
    private static final String STATE_KEY = "player_notification_preferences";
    private static final AtomicLong STACKING_SOUND_NONCE = new AtomicLong();
    private static final Map<UUID, ActionBarNotice> LAST_PROFESSION_ACTION_BAR = new ConcurrentHashMap<>();
    private static final long ACTION_BAR_OVERRIDE_WINDOW_MS = 2500L;

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

    /** Reloads this account's settings from shared SQL when it lands on a backend. */
    public static synchronized void preload(UUID playerId, String playerName) {
        if (playerId == null) return;
        ensureLoaded();
        String key = key(playerId);
        PlayerSettings fallback = SETTINGS.getOrDefault(key, new PlayerSettings());
        PlayerSettings shared = SharedJsonStateRepository.loadPlayer(playerId, STATE_KEY, PlayerSettings.class, fallback);
        PlayerSettings selected = shared == null ? fallback : shared;
        selected.uuid = playerId.toString();
        if (playerName != null && !playerName.isBlank()) selected.name = playerName;
        selected.normalizeDefaults();
        SETTINGS.put(key, selected);
        save();
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
        persist(player, settings);
    }

    public static boolean toggleProfessionPopups(ServerPlayer player) {
        boolean enabled = !areProfessionPopupsEnabled(player);
        setProfessionPopupsEnabled(player, enabled);
        return enabled;
    }


    public static boolean areProfessionOverflowMessagesEnabled(ServerPlayer player) {
        return getSettings(player).professionOverflowMessages;
    }

    public static boolean toggleProfessionOverflowMessages(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.professionOverflowMessages = !settings.professionOverflowMessages;
        persist(player, settings);
        return settings.professionOverflowMessages;
    }

    /** Called by the ServerPlayer mixin before a profession action-bar message is sent. */
    public static void handleProfessionActionBar(ServerPlayer player, Component incoming) {
        if (player == null || incoming == null || !areProfessionOverflowMessagesEnabled(player)) return;
        long now = System.currentTimeMillis();
        ActionBarNotice previous = LAST_PROFESSION_ACTION_BAR.put(player.getUUID(), new ActionBarNotice(incoming.copy(), now));
        if (previous != null && now - previous.sentAt <= ACTION_BAR_OVERRIDE_WINDOW_MS) {
            player.sendSystemMessage(previous.message);
        }
    }

    private record ActionBarNotice(Component message, long sentAt) {}

    public static boolean areSoundEffectsEnabled(ServerPlayer player) {
        return getSettings(player).soundEffects;
    }

    public static boolean toggleSoundEffects(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.soundEffects = !settings.soundEffects;
        persist(player, settings);
        return settings.soundEffects;
    }

    public static boolean areBroadcastMessagesEnabled(ServerPlayer player) {
        return getSettings(player).broadcastMessages;
    }

    public static boolean toggleBroadcastMessages(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.broadcastMessages = !settings.broadcastMessages;
        persist(player, settings);
        return settings.broadcastMessages;
    }


    public static boolean areTrinketMessagesEnabled(ServerPlayer player) {
        return getSettings(player).trinketMessages;
    }

    public static boolean toggleTrinketMessages(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.trinketMessages = !settings.trinketMessages;
        persist(player, settings);
        return settings.trinketMessages;
    }
    public static boolean areQueueNotificationsEnabled(ServerPlayer player) {
        return getSettings(player).queueNotifications;
    }

    public static boolean isRepairConfirmationEnabled(ServerPlayer player) {
        return getSettings(player).repairConfirmation;
    }

    public static boolean toggleRepairConfirmation(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.repairConfirmation = !settings.repairConfirmation;
        persist(player, settings);
        return settings.repairConfirmation;
    }

    public static boolean isAutoRepairEnabled(ServerPlayer player) {
        return getSettings(player).autoRepair;
    }

    public static boolean toggleAutoRepair(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return false;
        settings.autoRepair = !settings.autoRepair;
        persist(player, settings);
        return settings.autoRepair;
    }

    public static boolean toggleQueueNotifications(ServerPlayer player) {
        PlayerSettings settings = getOrCreateSettings(player);
        if (settings == null) return true;
        settings.queueNotifications = !settings.queueNotifications;
        persist(player, settings);
        return settings.queueNotifications;
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

        // Minecraft's client may coalesce identical sounds started on the same tick. Give every
        // profession notification its own imperceptibly different pitch so rapid rewards layer
        // instead of replacing one another. The offset stays within +/-0.02 pitch.
        long nonce = STACKING_SOUND_NONCE.getAndIncrement();
        float offset = ((nonce % 4001L) - 2000L) * 0.00001F;
        float uniquePitch = Math.max(0.5F, Math.min(2.0F, pitch + offset));
        player.playNotifySound(sound, source, volume, uniquePitch);
    }

    /**
     * Plays a notification with a tiny unique pitch offset so rapid identical profession dings
     * are separate client sound instances instead of replacing/coalescing one another.
     */
    public static void playStackingSound(
            ServerPlayer player,
            SoundEvent sound,
            SoundSource source,
            float volume,
            float pitch
    ) {
        playSound(player, sound, source, volume, pitch);
    }

    public static void sendBroadcast(MinecraftServer server, Component message) {
        if (server == null || message == null) return;
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (areBroadcastMessagesEnabled(target)) {
                target.sendSystemMessage(message);
            }
        }
        NetworkEventManager.publishBroadcast(message);
    }


    public static void sendQueueNotification(MinecraftServer server, Component message) {
        if (server == null || message == null) return;
        for (ServerPlayer target : server.getPlayerList().getPlayers()) {
            if (areQueueNotificationsEnabled(target)) {
                target.sendSystemMessage(message);
            }
        }
        NetworkEventManager.publishQueueBroadcast(message);
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

    private static void persist(ServerPlayer player, PlayerSettings settings) {
        if (player == null || settings == null) return;
        SharedJsonStateRepository.savePlayer(player.getUUID(), STATE_KEY, settings);
        save();
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
        Boolean professionOverflowMessages = true;
        Boolean soundEffects = true;
        Boolean broadcastMessages = true;
        Boolean queueNotifications = true;
        Boolean repairConfirmation = true;
        Boolean autoRepair = false;
        Boolean trinketMessages = true;

        void normalizeDefaults() {
            if (professionPopups == null) professionPopups = true;
            if (professionOverflowMessages == null) professionOverflowMessages = true;
            if (soundEffects == null) soundEffects = true;
            if (broadcastMessages == null) broadcastMessages = true;
            if (queueNotifications == null) queueNotifications = true;
            if (repairConfirmation == null) repairConfirmation = true;
            if (autoRepair == null) autoRepair = false;
            if (trinketMessages == null) trinketMessages = true;
        }
    }
}
