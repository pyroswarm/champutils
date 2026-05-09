package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.server.level.ServerPlayer;

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

        File file =
                getFile();

        if (!file.exists()) {
            save();
            return;
        }

        try (
                FileReader reader =
                        new FileReader(
                                file
                        )
        ) {
            SaveData data =
                    GSON.fromJson(
                            reader,
                            SaveData.class
                    );

            if (
                    data != null &&
                            data.players != null
            ) {
                SETTINGS.putAll(
                        data.players
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File dir =
                    getDir();

            if (!dir.exists()) {
                dir.mkdirs();
            }

            SaveData data =
                    new SaveData();

            data.players.putAll(
                    SETTINGS
            );

            try (
                    FileWriter writer =
                            new FileWriter(
                                    getFile()
                            )
            ) {
                GSON.toJson(
                        data,
                        writer
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean areProfessionPopupsEnabled(
            ServerPlayer player
    ) {

        if (player == null) {
            return true;
        }

        ensureLoaded();

        PlayerSettings settings =
                SETTINGS.get(
                        key(
                                player.getUUID()
                        )
                );

        if (settings == null) {
            return true;
        }

        return settings.professionPopups;
    }

    public static void setProfessionPopupsEnabled(
            ServerPlayer player,
            boolean enabled
    ) {

        if (player == null) {
            return;
        }

        ensureLoaded();

        String key =
                key(
                        player.getUUID()
                );

        PlayerSettings settings =
                SETTINGS.computeIfAbsent(
                        key,
                        ignored -> new PlayerSettings()
                );

        settings.uuid =
                player.getUUID()
                        .toString();

        settings.name =
                player.getName()
                        .getString();

        settings.professionPopups =
                enabled;

        save();
    }

    public static boolean toggleProfessionPopups(
            ServerPlayer player
    ) {

        boolean enabled =
                !areProfessionPopupsEnabled(
                        player
                );

        setProfessionPopupsEnabled(
                player,
                enabled
        );

        return enabled;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
        }
    }

    private static String key(
            UUID uuid
    ) {
        return uuid.toString();
    }

    private static File getDir() {
        return new File(
                "config/champutils/player_options"
        );
    }

    private static File getFile() {
        return new File(
                getDir(),
                "profession_notifications.json"
        );
    }

    private static class SaveData {
        Map<String, PlayerSettings> players =
                new HashMap<>();
    }

    private static class PlayerSettings {
        String uuid;
        String name;
        boolean professionPopups =
                true;
    }
}
