package com.champutils.profession;

import com.champutils.profile.PlayerProfileManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProfessionManager {

    private static final Map<UUID, ProfessionDataManager.ProfessionData> CACHE =
            new ConcurrentHashMap<>();

    private static final Set<UUID> DIRTY_PLAYERS =
            ConcurrentHashMap.newKeySet();

    /**
     * Monotonically increasing per-profile save generation.
     *
     * This prevents profession rollback when an async save starts with an older
     * snapshot, the player earns more XP while that save is running, and the
     * old save finishes after the new change. The dirty flag is only cleared if
     * the generation saved is still the latest generation.
     */
    private static final Map<UUID, Long> DIRTY_GENERATIONS =
            new ConcurrentHashMap<>();

    private static final Map<UUID, UUID> PROFILE_OWNER_CACHE =
            new ConcurrentHashMap<>();

    private static final ExecutorService SAVE_EXECUTOR =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "ChampUtils-ProfessionSave");
                thread.setDaemon(true);
                return thread;
            });

    private static final AtomicBoolean SAVE_ALL_RUNNING =
            new AtomicBoolean(false);

    /**
     * Set when a profile is dirtied while the save worker is already running.
     * The worker will schedule one follow-up pass after the current pass exits.
     */
    private static final AtomicBoolean SAVE_RERUN_REQUESTED =
            new AtomicBoolean(false);

    public static ProfessionDataManager.ProfessionData getData(
            ServerPlayer player
    ) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) {
            ProfessionDataManager.ProfessionData transientData = new ProfessionDataManager.ProfessionData();
            transientData.uuid = player == null ? null : player.getUUID().toString();
            transientData.name = player == null ? "No Profile" : player.getName().getString();
            ProfessionDataManager.ensureProfessionDefaults(transientData);
            return transientData;
        }

        UUID uuid = PlayerProfileManager.activeProfileId(player);
        PROFILE_OWNER_CACHE.put(uuid, player.getUUID());

        return CACHE.computeIfAbsent(uuid, ignored ->
                ProfessionDataManager.load(
                        uuid,
                        player.getName().getString()
                )
        );
    }

    private static boolean requiresProfessionTool(ProfessionType profession) {
        return profession == ProfessionType.MINING || profession == ProfessionType.FORESTRY || profession == ProfessionType.FARMING;
    }

    private static boolean hasUsableProfessionTool(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null) return false;
        ItemStack main = player.getMainHandItem();
        if (ProfessionToolUtil.isUsableProfessionTool(player, main, profession)) return true;
        ItemStack off = player.getOffhandItem();
        return ProfessionToolUtil.isUsableProfessionTool(player, off, profession);
    }

    public static boolean canEarnProfessionXp(ServerPlayer player, ProfessionType profession) {
        if (player == null || profession == null || !PlayerProfileManager.hasActiveProfile(player)) return false;
        return !requiresProfessionTool(profession) || hasUsableProfessionTool(player, profession);
    }

    public static void addXp(
            ServerPlayer player,
            ProfessionType profession,
            int amount
    ) {
        if (player == null || profession == null || amount <= 0) {
            return;
        }

        if (!canEarnProfessionXp(player, profession)) {
            return;
        }

        ProfessionBackpackManager.markProfessionAction(
                player,
                profession
        );

        amount = ProfessionXpBoostManager.applyBoosts(
                player,
                profession,
                amount
        );

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
                Math.max(1, data.levels.getOrDefault(
                        key,
                        1
                ));

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

            com.champutils.worldfirst.WorldFirstManager.handleProfessionLevel(
                    player,
                    profession,
                    currentLevel
            );

            com.champutils.cosmetic.TitleRegistry.handleProfessionLevel(
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
                PlayerProfileManager.activeProfileId(player)
        );
    }

    public static int xpRequired(
            int level
    ) {
        int safeLevel = Math.max(1, level);
        if (safeLevel < 50) {
            return 100 + (safeLevel * 25);
        }
        double baseAtFifty = 100.0D + (50.0D * 25.0D);
        double scaled = baseAtFifty * Math.pow(1.115D, safeLevel - 49);
        return Math.max(1, (int) Math.min(Integer.MAX_VALUE / 4, Math.round(scaled)));
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

    public static int getBenefitLevel(
            ServerPlayer player,
            ProfessionType profession
    ) {
        return Math.min(100, Math.max(1, getLevel(player, profession)));
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




    public static int getChunks(
            ServerPlayer player,
            String chunkKey
    ) {
        if (player == null || chunkKey == null || chunkKey.isBlank()) {
            return 0;
        }

        String normalizedChunkKey = ProfessionChunkManager.normalizeChunk(chunkKey);
        ProfessionDataManager.ProfessionData data = getData(player);
        if (data.chunks == null) {
            data.chunks = new HashMap<>();
        }

        return Math.max(0, data.chunks.getOrDefault(normalizedChunkKey, 0));
    }

    public static void addChunks(
            ServerPlayer player,
            String chunkKey,
            int amount
    ) {
        if (player == null || chunkKey == null || chunkKey.isBlank() || amount <= 0) {
            return;
        }

        String normalizedChunkKey = ProfessionChunkManager.normalizeChunk(chunkKey);
        ProfessionDataManager.ProfessionData data = getData(player);
        if (data.chunks == null) {
            data.chunks = new HashMap<>();
        }

        int current = Math.max(0, data.chunks.getOrDefault(normalizedChunkKey, 0));
        data.chunks.put(normalizedChunkKey, current + amount);

        markDirty(PlayerProfileManager.activeProfileId(player));
    }

    public static int removeChunks(
            ServerPlayer player,
            String chunkKey,
            int amount
    ) {
        if (player == null || chunkKey == null || chunkKey.isBlank() || amount <= 0) {
            return 0;
        }

        String normalizedChunkKey = ProfessionChunkManager.normalizeChunk(chunkKey);
        ProfessionDataManager.ProfessionData data = getData(player);
        if (data.chunks == null) {
            data.chunks = new HashMap<>();
        }

        int current = Math.max(0, data.chunks.getOrDefault(normalizedChunkKey, 0));
        int removed = Math.min(current, amount);
        if (removed <= 0) {
            return 0;
        }

        int remaining = current - removed;
        if (remaining <= 0) {
            data.chunks.remove(normalizedChunkKey);
        } else {
            data.chunks.put(normalizedChunkKey, remaining);
        }

        markDirty(PlayerProfileManager.activeProfileId(player));
        return removed;
    }

    public static Map<String, Integer> getChunkBalances(
            ServerPlayer player
    ) {
        ProfessionDataManager.ProfessionData data = getData(player);
        if (data.chunks == null) {
            data.chunks = new HashMap<>();
        }
        return new HashMap<>(data.chunks);
    }

    public static Map<String, Integer> getFragmentBalances(
            ServerPlayer player
    ) {
        ProfessionDataManager.ProfessionData data = getData(player);
        if (data.fragments == null) {
            data.fragments = new HashMap<>();
        }
        return new HashMap<>(data.fragments);
    }

    public static int getFragments(
            ServerPlayer player,
            String fragmentKey
    ) {
        if (player == null || fragmentKey == null || fragmentKey.isBlank()) {
            return 0;
        }

        String normalizedFragmentKey = ProfessionWeaponFragmentConfig.normalizeRarity(fragmentKey);

        return getData(player)
                .fragments
                .getOrDefault(
                        normalizedFragmentKey,
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

        String normalizedFragmentKey = ProfessionWeaponFragmentConfig.normalizeRarity(fragmentKey);

        ProfessionDataManager.ProfessionData data =
                getData(player);

        int current =
                data.fragments.getOrDefault(
                        normalizedFragmentKey,
                        0
                );

        data.fragments.put(
                normalizedFragmentKey,
                current + amount
        );

        markDirty(
                PlayerProfileManager.activeProfileId(player)
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

        String normalizedFragmentKey = ProfessionWeaponFragmentConfig.normalizeRarity(fragmentKey);

        ProfessionDataManager.ProfessionData data =
                getData(player);

        int current =
                data.fragments.getOrDefault(
                        normalizedFragmentKey,
                        0
                );

        if (current < amount) {
            return false;
        }

        data.fragments.put(
                normalizedFragmentKey,
                current - amount
        );

        markDirty(
                PlayerProfileManager.activeProfileId(player)
        );

        return true;
    }

    private static void markDirty(
            UUID uuid
    ) {
        if (uuid != null) {
            DIRTY_PLAYERS.add(uuid);
            DIRTY_GENERATIONS.merge(uuid, 1L, Long::sum);
            if (SAVE_ALL_RUNNING.get()) {
                SAVE_RERUN_REQUESTED.set(true);
            }
        }
    }

    public static void markDirtyProfile(
            UUID uuid
    ) {
        if (uuid != null) {
            markDirty(uuid);
        }
    }

    public static void savePlayer(
            ServerPlayer player
    ) {
        if (player == null) {
            return;
        }
        // Hot profession actions can call savePlayer many times in a short burst.
        // Never force a database write on the server thread from those paths; coalesce
        // the write through the profession save worker instead.
        if (!PlayerProfileManager.hasActiveProfile(player)) {
            return;
        }
        UUID uuid = PlayerProfileManager.activeProfileId(player);
        if (uuid == null || !DIRTY_PLAYERS.contains(uuid)) {
            return;
        }
        saveAllAsync();
    }

    public static void savePlayerNow(
            ServerPlayer player
    ) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) {
            return;
        }
        saveProfileNow(PlayerProfileManager.activeProfileId(player));
    }

    private static void saveProfileNow(UUID uuid) {
        if (uuid == null || !DIRTY_PLAYERS.contains(uuid)) {
            return;
        }

        Long generationAtSaveStart =
                DIRTY_GENERATIONS.get(uuid);

        if (generationAtSaveStart == null) {
            DIRTY_PLAYERS.remove(uuid);
            return;
        }

        ProfessionDataManager.ProfessionData data =
                CACHE.get(uuid);

        if (data == null) {
            DIRTY_PLAYERS.remove(uuid);
            DIRTY_GENERATIONS.remove(uuid);
            return;
        }

        ProfessionDataManager.ProfessionData snapshot =
                ProfessionDataManager.copyOf(data);

        if (ProfessionDataManager.save(
                uuid,
                PROFILE_OWNER_CACHE.get(uuid),
                snapshot
        )) {
            Long latestGeneration =
                    DIRTY_GENERATIONS.get(uuid);

            if (Objects.equals(latestGeneration, generationAtSaveStart)) {
                DIRTY_PLAYERS.remove(uuid);
                DIRTY_GENERATIONS.remove(uuid, generationAtSaveStart);
            }
        }
    }

    public static void saveAll() {
        for (UUID uuid : new ArrayList<>(DIRTY_PLAYERS)) {
            saveProfileNow(uuid);
        }
    }

    public static void saveAllAsync() {
        if (!SAVE_ALL_RUNNING.compareAndSet(false, true)) {
            SAVE_RERUN_REQUESTED.set(true);
            return;
        }
        SAVE_EXECUTOR.execute(() -> {
            try {
                saveAll();
            } finally {
                SAVE_ALL_RUNNING.set(false);
                if (SAVE_RERUN_REQUESTED.getAndSet(false) && !DIRTY_PLAYERS.isEmpty()) {
                    saveAllAsync();
                }
            }
        });
    }

    public static void invalidateSharedCache(UUID profileId) {
        if (profileId == null || DIRTY_PLAYERS.contains(profileId)) {
            return;
        }
        CACHE.remove(profileId);
        PROFILE_OWNER_CACHE.remove(profileId);
    }

    public static void unloadPlayer(
            ServerPlayer player
    ) {
        savePlayerNow(player);

        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) {
            return;
        }

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (!DIRTY_PLAYERS.contains(profileId)) {
            CACHE.remove(
                    profileId
            );
            PROFILE_OWNER_CACHE.remove(profileId);
        }

        ProfessionXpBoostManager.clearFractionBank(player);
    }
}
