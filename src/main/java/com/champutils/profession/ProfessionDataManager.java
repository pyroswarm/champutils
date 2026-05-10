package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfessionDataManager {

    private static final Gson GSON =
            new GsonBuilder()
                    .setPrettyPrinting()
                    .create();

    public static class ProfessionData {

        public String uuid;
        public String name;

        public Map<String, Integer> levels =
                new HashMap<>();

        public Map<String, Integer> xp =
                new HashMap<>();

        public Map<String, Integer> fragments =
                new HashMap<>();
    }

    private static File professionDir() {
        File dir =
                new File(
                        "config/champutils/professions"
                );

        if (!dir.exists()) {
            dir.mkdirs();
        }

        return dir;
    }

    private static File getFile(UUID uuid) {
        return new File(
                professionDir(),
                uuid.toString() + ".json"
        );
    }

    public static void ensurePlayer(
            UUID uuid,
            String name
    ) {
        File file =
                getFile(uuid);

        if (file.exists()) {
            return;
        }

        ProfessionData data =
                new ProfessionData();

        data.uuid =
                uuid.toString();

        data.name =
                name;

        ensureProfessionDefaults(
                data
        );

        save(
                uuid,
                data
        );
    }

    public static ProfessionData load(
            UUID uuid,
            String name
    ) {
        try {

            ensurePlayer(
                    uuid,
                    name
            );

            try (
                    FileReader r =
                            new FileReader(
                                    getFile(uuid)
                            )
            ) {
                ProfessionData data =
                        GSON.fromJson(
                                r,
                                ProfessionData.class
                        );

                if (data == null) {
                    data =
                            new ProfessionData();
                }

                data.uuid =
                        uuid.toString();

                data.name =
                        name;

                ensureProfessionDefaults(
                        data
                );

                return data;
            }

        } catch (Exception e) {
            e.printStackTrace();

            ProfessionData d =
                    new ProfessionData();

            d.uuid =
                    uuid.toString();

            d.name =
                    name;

            ensureProfessionDefaults(
                    d
            );

            return d;
        }
    }

    public static List<ProfessionData> getAllPlayers() {
        List<ProfessionData> result =
                new ArrayList<>();

        File dir =
                professionDir();

        File[] files =
                dir.listFiles(
                        (d, name) -> name.endsWith(".json")
                );

        if (files == null) {
            return result;
        }

        for (File file : files) {
            try (
                    FileReader r =
                            new FileReader(
                                    file
                            )
            ) {
                ProfessionData data =
                        GSON.fromJson(
                                r,
                                ProfessionData.class
                        );

                if (data == null) {
                    continue;
                }

                if (data.uuid == null || data.uuid.isBlank()) {
                    String fileName =
                            file.getName();

                    data.uuid =
                            fileName.substring(
                                    0,
                                    fileName.length() - 5
                            );
                }

                if (data.name == null || data.name.isBlank()) {
                    data.name =
                            data.uuid;
                }

                ensureProfessionDefaults(
                        data
                );

                result.add(
                        data
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static int getOverallLevel(
            ProfessionData data
    ) {
        ensureProfessionDefaults(
                data
        );

        int total =
                0;

        for (ProfessionType type :
                ProfessionType.values()) {
            total +=
                    data.levels.getOrDefault(
                            type.name(),
                            1
                    );
        }

        return total;
    }

    public static void ensureProfessionDefaults(
            ProfessionData data
    ) {
        if (data.levels == null) {
            data.levels =
                    new HashMap<>();
        }

        if (data.xp == null) {
            data.xp =
                    new HashMap<>();
        }

        if (data.fragments == null) {
            data.fragments =
                    new HashMap<>();
        }

        for (ProfessionType type :
                ProfessionType.values()) {
            data.levels.putIfAbsent(
                    type.name(),
                    1
            );

            data.xp.putIfAbsent(
                    type.name(),
                    0
            );
        }
    }

    public static void save(
            UUID uuid,
            ProfessionData data
    ) {
        ensureProfessionDefaults(
                data
        );

        try (
                FileWriter w =
                        new FileWriter(
                                getFile(uuid)
                        )
        ) {
            GSON.toJson(
                    data,
                    w
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
