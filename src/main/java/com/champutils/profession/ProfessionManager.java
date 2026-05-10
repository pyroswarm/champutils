package com.champutils.profession;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProfessionManager {

    private static final Map<UUID, ProfessionDataManager.ProfessionData> CACHE =
            new HashMap<>();

    private static final Set<UUID> DIRTY_PLAYERS =
            new HashSet<>();

    public static ProfessionDataManager.ProfessionData getData(
            ServerPlayer player
    ) {
        UUID uuid = player.getUUID();

        if (CACHE.containsKey(uuid)) {
            return CACHE.get(uuid);
        }

        ProfessionDataManager.ProfessionData data =
                ProfessionDataManager.load(
                        uuid,
                        player.getName().getString()
                );

        CACHE.put(uuid, data);

        return data;
    }

    public static void addXp(
            ServerPlayer player,
            ProfessionType profession,
            int amount
    ) {
        if (amount <= 0) {
            return;
        }

        ProfessionDataManager.ProfessionData data =
                getData(player);

        String key =
                profession.name();

        int currentXp =
                data.xp.getOrDefault(
                        key,
                        0
                );

        int currentLevel =
                data.levels.getOrDefault(
                        key,
                        1
                );

        currentXp += amount;

        data.xp.put(
                key,
                currentXp
        );

        ProfessionActionBarManager.sendXpMessage(
                player,
                profession,
                amount
        );

        while (
                currentXp >= xpRequired(currentLevel)
        ) {
            currentXp -=
                    xpRequired(currentLevel);

            currentLevel++;

            data.levels.put(
                    key,
                    currentLevel
            );

            ProfessionActionBarManager.sendLevelUpMessage(
                    player,
                    profession,
                    currentLevel
            );
        }

        data.xp.put(
                key,
                currentXp
        );

        markDirty(
                player.getUUID()
        );
    }

    public static int xpRequired(
            int level
    ) {
        return 100 + (level * 25);
    }

    public static int getLevel(
            ServerPlayer player,
            ProfessionType profession
    ) {
        return getData(player)
                .levels
                .getOrDefault(
                        profession.name(),
                        1
                );
    }

    public static int getXp(
            ServerPlayer player,
            ProfessionType profession
    ) {
        return getData(player)
                .xp
                .getOrDefault(
                        profession.name(),
                        0
                );
    }



    public static int getFragments(
            ServerPlayer player,
            String fragmentKey
    ) {
        if (player == null || fragmentKey == null || fragmentKey.isBlank()) {
            return 0;
        }

        return getData(player)
                .fragments
                .getOrDefault(
                        fragmentKey,
                        0
                );
    }

    public static void addFragments(
            ServerPlayer player,
            String fragmentKey,
            int amount
    ) {
        if (player == null || fragmentKey == null || fragmentKey.isBlank() || amount <= 0) {
            return;
        }

        ProfessionDataManager.ProfessionData data =
                getData(player);

        int current =
                data.fragments.getOrDefault(
                        fragmentKey,
                        0
                );

        data.fragments.put(
                fragmentKey,
                current + amount
        );

        markDirty(
                player.getUUID()
        );
    }

    public static boolean removeFragments(
            ServerPlayer player,
            String fragmentKey,
            int amount
    ) {
        if (player == null || fragmentKey == null || fragmentKey.isBlank() || amount <= 0) {
            return false;
        }

        ProfessionDataManager.ProfessionData data =
                getData(player);

        int current =
                data.fragments.getOrDefault(
                        fragmentKey,
                        0
                );

        if (current < amount) {
            return false;
        }

        data.fragments.put(
                fragmentKey,
                current - amount
        );

        markDirty(
                player.getUUID()
        );

        return true;
    }

    private static void markDirty(
            UUID uuid
    ) {
        DIRTY_PLAYERS.add(uuid);
    }

    public static void savePlayer(
            ServerPlayer player
    ) {
        UUID uuid =
                player.getUUID();

        if (!DIRTY_PLAYERS.contains(uuid)) {
            return;
        }

        ProfessionDataManager.ProfessionData data =
                CACHE.get(uuid);

        if (data == null) {
            return;
        }

        ProfessionDataManager.save(
                uuid,
                data
        );

        DIRTY_PLAYERS.remove(uuid);
    }

    public static void saveAll() {
        for (UUID uuid : DIRTY_PLAYERS) {

            ProfessionDataManager.ProfessionData data =
                    CACHE.get(uuid);

            if (data == null) {
                continue;
            }

            ProfessionDataManager.save(
                    uuid,
                    data
            );
        }

        DIRTY_PLAYERS.clear();
    }

    public static void unloadPlayer(
            ServerPlayer player
    ) {
        savePlayer(player);

        CACHE.remove(
                player.getUUID()
        );
    }
}