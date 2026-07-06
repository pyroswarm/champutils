package com.champutils.profession;

import com.champutils.database.ProfessionDatabaseRepository;
import com.champutils.database.SharedJsonStateRepository;
import com.champutils.profile.PlayerProfileManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ProfessionDataManager {
    private static final String STATE_KEY = "professions";

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

        /** Digital profession sellables. These are not physical items. */
        public Map<String, Integer> chunks =
                new HashMap<>();

        /** Per-profile profession specializations. Key format: PROFESSION:CATEGORY:ID. */
        public Map<String, SubLevelData> sublevels =
                new HashMap<>();

        /** Fractional profession XP bonuses banked per profession for exact decimal boosts. */
        public Map<String, Double> xpBonusBank =
                new HashMap<>();

        public static class SubLevelData {
            public int level = 1;
            public int xp = 0;
            public long actions = 0L;
        }

        /** Digital profile-bound profession backpack item balances. Key = item id, value = amount. */
        public Map<String, Long> backpack =
                new HashMap<>();

        /** Highest trinket pouch tier unlocked on this profile. Empty means no digital pouch yet. */
        public String trinketPouchRarity =
                "";

        /** Cached slot count for the unlocked digital trinket pouch. */
        public int trinketPouchSlots =
                0;

        /** Stored trinkets as ItemStack SNBT strings. Profile-bound digital storage. */
        public List<String> trinketPouchItems =
                new ArrayList<>();

        public boolean backpackAutopickup =
                ProfessionBackpackConfig.CONFIG.defaultAutopickup;
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

    private static File getProfileFile(UUID profileId) {
        File dir = new File(professionDir(), "profiles");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(
                dir,
                profileId.toString() + ".json"
        );
    }

    private static File getFile(UUID uuid) {
        return getProfileFile(uuid);
    }

    public static void ensurePlayer(
            UUID uuid,
            String name
    ) {
        UUID profileId = PlayerProfileManager.activeProfileIdOrNull(uuid);
        if (profileId == null) {
            return;
        }

        File file =
                getProfileFile(profileId);

        if (file.exists()) {
            ProfessionDatabaseRepository.touchPlayer(
                    uuid,
                    name
            );
            return;
        }

        ProfessionData data =
                new ProfessionData();

        data.uuid =
                profileId.toString();

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

            File file = getProfileFile(uuid);
            ProfessionData data = new ProfessionData();

            if (file.exists()) {
                try (
                        FileReader r =
                                new FileReader(
                                        file
                                )
                ) {
                    ProfessionData local =
                            GSON.fromJson(
                                    r,
                                    ProfessionData.class
                            );
                    if (local != null) {
                        data =
                                local;
                    }
                }
            }

            data =
                    SharedJsonStateRepository.loadProfile(
                            uuid,
                            STATE_KEY,
                            ProfessionData.class,
                            data
                    );

            data.uuid =
                    uuid.toString();

            data.name =
                    name;

            ensureProfessionDefaults(
                    data
            );

            return data;

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

        List<File> filesToRead = new ArrayList<>();
        File[] rootFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (rootFiles != null) {
            filesToRead.addAll(java.util.Arrays.asList(rootFiles));
        }
        File profileDir = new File(dir, "profiles");
        File[] profileFiles = profileDir.listFiles((d, name) -> name.endsWith(".json"));
        if (profileFiles != null) {
            filesToRead.addAll(java.util.Arrays.asList(profileFiles));
        }

        for (File file : filesToRead) {
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
                    new ConcurrentHashMap<>();
        } else if (!(data.levels instanceof ConcurrentHashMap)) {
            data.levels =
                    new ConcurrentHashMap<>(data.levels);
        }

        if (data.xp == null) {
            data.xp =
                    new ConcurrentHashMap<>();
        } else if (!(data.xp instanceof ConcurrentHashMap)) {
            data.xp =
                    new ConcurrentHashMap<>(data.xp);
        }

        if (data.fragments == null) {
            data.fragments =
                    new ConcurrentHashMap<>();
        } else if (!(data.fragments instanceof ConcurrentHashMap)) {
            data.fragments =
                    new ConcurrentHashMap<>(data.fragments);
        }

        if (data.chunks == null) {
            data.chunks =
                    new ConcurrentHashMap<>();
        } else if (!(data.chunks instanceof ConcurrentHashMap)) {
            data.chunks =
                    new ConcurrentHashMap<>(data.chunks);
        }

        if (data.backpack == null) {
            data.backpack =
                    new ConcurrentHashMap<>();
        } else if (!(data.backpack instanceof ConcurrentHashMap)) {
            data.backpack =
                    new ConcurrentHashMap<>(data.backpack);
        }

        if (data.sublevels == null) {
            data.sublevels =
                    new ConcurrentHashMap<>();
        } else if (!(data.sublevels instanceof ConcurrentHashMap)) {
            data.sublevels =
                    new ConcurrentHashMap<>(data.sublevels);
        }

        if (data.xpBonusBank == null) {
            data.xpBonusBank =
                    new ConcurrentHashMap<>();
        } else if (!(data.xpBonusBank instanceof ConcurrentHashMap)) {
            data.xpBonusBank =
                    new ConcurrentHashMap<>(data.xpBonusBank);
        }

        data.xpBonusBank.entrySet().removeIf(entry ->
                entry.getKey() == null ||
                        entry.getKey().isBlank() ||
                        entry.getValue() == null ||
                        !Double.isFinite(entry.getValue()) ||
                        entry.getValue() <= 0.0D
        );

        data.sublevels.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null);
        for (ProfessionData.SubLevelData sublevel : data.sublevels.values()) {
            sublevel.level = Math.max(1, Math.min(100, sublevel.level));
            sublevel.xp = Math.max(0, sublevel.xp);
            sublevel.actions = Math.max(0L, sublevel.actions);
        }

        if (data.trinketPouchRarity == null) {
            data.trinketPouchRarity =
                    "";
        }

        if (data.trinketPouchSlots < 0) {
            data.trinketPouchSlots =
                    0;
        }

        if (data.trinketPouchItems == null) {
            data.trinketPouchItems =
                    Collections.synchronizedList(new ArrayList<>());
        } else if (!(data.trinketPouchItems instanceof java.util.RandomAccess && data.trinketPouchItems.getClass().getName().contains("Synchronized"))) {
            data.trinketPouchItems =
                    Collections.synchronizedList(new ArrayList<>(data.trinketPouchItems));
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


    public static ProfessionData copyOf(
            ProfessionData data
    ) {
        ProfessionData copy =
                new ProfessionData();

        if (data == null) {
            ensureProfessionDefaults(copy);
            return copy;
        }

        ensureProfessionDefaults(data);

        synchronized (data) {
            copy.uuid = data.uuid;
            copy.name = data.name;
            copy.levels = new HashMap<>(data.levels);
            copy.xp = new HashMap<>(data.xp);
            copy.fragments = new HashMap<>(data.fragments);
            copy.chunks = new HashMap<>(data.chunks);
            copy.backpack = new HashMap<>(data.backpack);
            copy.xpBonusBank = new HashMap<>(data.xpBonusBank);
            copy.sublevels = new HashMap<>();
            if (data.sublevels != null) {
                for (Map.Entry<String, ProfessionData.SubLevelData> entry : data.sublevels.entrySet()) {
                    ProfessionData.SubLevelData source = entry.getValue();
                    if (entry.getKey() == null || source == null) {
                        continue;
                    }
                    ProfessionData.SubLevelData target = new ProfessionData.SubLevelData();
                    target.level = source.level;
                    target.xp = source.xp;
                    target.actions = source.actions;
                    copy.sublevels.put(entry.getKey(), target);
                }
            }
            copy.trinketPouchRarity = data.trinketPouchRarity;
            copy.trinketPouchSlots = data.trinketPouchSlots;
            copy.trinketPouchItems = new ArrayList<>();
            if (data.trinketPouchItems != null) {
                synchronized (data.trinketPouchItems) {
                    copy.trinketPouchItems.addAll(data.trinketPouchItems);
                }
            }
            copy.backpackAutopickup = data.backpackAutopickup;
        }

        ensureProfessionDefaults(copy);
        return copy;
    }

    public static boolean save(
            UUID uuid,
            ProfessionData data
    ) {
        return save(uuid, null, data);
    }

    public static boolean save(
            UUID uuid,
            UUID ownerPlayerUuid,
            ProfessionData data
    ) {
        ensureProfessionDefaults(
                data
        );

        UUID profileId = uuid;
        if (data != null && data.uuid != null && !data.uuid.isBlank()) {
            try {
                profileId = UUID.fromString(data.uuid);
            } catch (Exception ignored) {
                profileId = uuid;
            }
        }

        if (profileId == null) {
            return false;
        }

        File targetFile =
                getProfileFile(profileId);

        File tempFile =
                new File(
                        targetFile.getParentFile(),
                        targetFile.getName() + ".tmp"
                );

        try (
                FileWriter w =
                        new FileWriter(
                                tempFile
                        )
        ) {
            GSON.toJson(
                    data,
                    w
            );
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        try {
            try {
                Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            ProfessionDatabaseRepository.sync(
                    ownerPlayerUuid,
                    data
            );

            SharedJsonStateRepository.saveProfile(
                    profileId,
                    STATE_KEY,
                    data
            );

            com.champutils.network.NetworkEventManager.publishCacheInvalidation(
                    "PROFESSIONS",
                    profileId
            );

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
