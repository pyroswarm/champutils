package com.champutils.profession;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import java.util.HashMap;
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

        for (ProfessionType type :
                ProfessionType.values()) {

            data.levels.put(
                    type.name(),
                    1
            );

            data.xp.put(
                    type.name(),
                    0
            );
        }

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

            return d;
        }
    }

    public static void save(
            UUID uuid,
            ProfessionData data
    ) {
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