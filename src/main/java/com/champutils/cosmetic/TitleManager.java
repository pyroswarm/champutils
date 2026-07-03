package com.champutils.cosmetic;

import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class TitleManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SELECTED_FILE = new File("config/champutils/title_selections.json");
    private static final int MAX_SUB_TITLES = 3;

    private static State state = new State();

    /** SQL ownership cache keyed by active profile id. Keeps chat/menu/title rendering off the database. */
    private static final Map<UUID, Set<String>> sqlUnlockedCache = new ConcurrentHashMap<>();
    private static final Set<UUID> sqlLoadedProfiles = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> sqlLoadingProfiles = ConcurrentHashMap.newKeySet();

    /** SQL account-bound ownership cache keyed by real Minecraft account UUID. */
    private static final Map<UUID, Set<String>> sqlAccountUnlockedCache = new ConcurrentHashMap<>();
    private static final Set<UUID> sqlLoadedAccounts = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> sqlLoadingAccounts = ConcurrentHashMap.newKeySet();

    /** Equipped main title and hidden sub titles are profile scoped. */
    private static final Map<String, String> selectedByProfile = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> subtitlesByProfile = new ConcurrentHashMap<>();

    private TitleManager() {}

    public static synchronized void load() {
        TitleConfig.load();
        com.champutils.buff.BuffManager.registerProvider(new com.champutils.buff.BuffProvider() {
            @Override public String id() { return "active_title"; }
            @Override public int priority() { return 45; }
            @Override public double getBuff(com.champutils.buff.BuffContext context, com.champutils.buff.BuffType type) {
                if (context == null || context.player == null || type == null) return 0.0D;
                return TitleConfig.activeBuff(context.player, type);
            }
        });
        // BuffManager already feeds profession XP through ProfessionXpBoostManager. Keeping the legacy
        // profession source registered double-counted title XP, especially once sub titles were added.
        com.champutils.profession.ProfessionXpBoostManager.unregisterSource("active_title");

        state = new State();
        sqlUnlockedCache.clear();
        sqlLoadedProfiles.clear();
        sqlLoadingProfiles.clear();
        sqlAccountUnlockedCache.clear();
        sqlLoadedAccounts.clear();
        sqlLoadingAccounts.clear();
        subtitlesByProfile.clear();
        TitleDatabaseRepository.ensureSchemaAsync();
        loadSelections();
    }

    public static synchronized void save() {
        saveSelections();
    }

    public static boolean unlock(ServerPlayer player, String id) {
        return unlock(player, id, null);
    }

    public static boolean unlock(ServerPlayer player, String id, String ignoredDisplay) {
        if (player == null || id == null || id.isBlank()) return false;
        String normalizedId = id.trim();
        String display = TitleConfig.display(normalizedId);
        if (display == null || display.isBlank()) display = ignoredDisplay;
        if (display == null || display.isBlank()) display = "&7[" + normalizedId + "]";

        UUID profileId = PlayerProfileManager.activeProfileId(player.getUUID());
        UUID accountUuid = player.getUUID();
        boolean accountBound = TitleConfig.isAccountBound(normalizedId);
        boolean changed;

        if (com.champutils.database.DatabaseManager.isEnabled()) {
            Set<String> owned = accountBound ? cachedSqlAccountTitles(accountUuid) : cachedSqlTitles(profileId);
            changed = owned.add(normalizedId);
            if (changed) {
                if (accountBound) TitleDatabaseRepository.unlockAccountAsync(accountUuid, normalizedId);
                else TitleDatabaseRepository.unlockAsync(profileId, normalizedId);
                if (selectedForProfile(profileId).isBlank()) {
                    selectedByProfile.put(profileId.toString(), normalizedId);
                    TitleDatabaseRepository.selectAsync(profileId, normalizedId);
                    saveSelections();
                }
            }
        } else {
            PlayerTitles data = dataForKey(accountBound ? accountUuid.toString() : profileId.toString());
            changed = data.unlocked.add(normalizedId);
            if (changed && selectedLocal(profileId).isBlank()) {
                dataForKey(profileId.toString()).selected = normalizedId;
            }
        }

        if (!changed) return false;
        com.champutils.chat.ChatTagResolver.invalidate(player);
        Component title = com.champutils.chat.ChatTagResolver.legacy(display);
        com.champutils.profession.ProfessionNotificationSettings.sendBroadcast(
                player.server,
                Component.literal("[Title] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" unlocked ").withStyle(ChatFormatting.GRAY))
                        .append(title)
                        .append(Component.literal("!").withStyle(ChatFormatting.GRAY))
        );
        return true;
    }

    public static Set<String> unlocked(UUID uuid) {
        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        TreeSet<String> out = new TreeSet<>();
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            out.addAll(cachedSqlTitles(profileId));
            out.addAll(cachedSqlAccountTitles(uuid));
            return out;
        }
        out.addAll(dataForKey(profileId.toString()).unlocked);
        out.addAll(dataForKey(uuid.toString()).unlocked);
        return out;
    }

    public static String selected(UUID uuid) {
        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            String selected = selectedForProfile(profileId);
            return unlocked(uuid).contains(selected) ? selected : "";
        }
        String selected = selectedLocal(profileId);
        return unlocked(uuid).contains(selected) ? selected : "";
    }

    public static Set<String> subtitles(UUID uuid) {
        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        if (profileId == null) return new LinkedHashSet<>();
        String selected = selected(uuid);
        Set<String> owned = unlocked(uuid);
        Set<String> raw = subtitleSetForProfile(profileId);
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        for (String titleId : raw) {
            if (titleId == null || titleId.isBlank()) continue;
            if (titleId.equals(selected)) continue;
            if (!owned.contains(titleId)) continue;
            clean.add(titleId);
            if (clean.size() >= MAX_SUB_TITLES) break;
        }
        if (!clean.equals(raw)) {
            raw.clear();
            raw.addAll(clean);
            saveSubtitles(profileId, raw);
        }
        return clean;
    }

    public static boolean isSubTitle(UUID uuid, String id) {
        return id != null && subtitles(uuid).contains(id.trim());
    }

    public static void toggleSubTitle(ServerPlayer player, String id) {
        if (player == null || id == null || id.isBlank()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player.getUUID());
        String normalizedId = id.trim();
        Set<String> owned = unlocked(player.getUUID());
        if (!owned.contains(normalizedId)) {
            player.sendSystemMessage(Component.literal("You have not unlocked that title.").withStyle(ChatFormatting.RED));
            return;
        }
        String selected = selected(player.getUUID());
        if (normalizedId.equals(selected)) {
            player.sendSystemMessage(Component.literal("Your shown title cannot also be a hidden sub title.").withStyle(ChatFormatting.RED));
            return;
        }

        Set<String> subtitles = subtitleSetForProfile(profileId);
        if (subtitles.remove(normalizedId)) {
            saveSubtitles(profileId, subtitles);
            player.sendSystemMessage(Component.literal("Removed hidden sub title: ").withStyle(ChatFormatting.GRAY).append(com.champutils.chat.ChatTagResolver.legacy(displayFor(player.getUUID(), normalizedId))));
            return;
        }
        subtitles.removeIf(titleId -> titleId == null || titleId.isBlank() || titleId.equals(selected) || !owned.contains(titleId));
        if (subtitles.size() >= MAX_SUB_TITLES) {
            player.sendSystemMessage(Component.literal("You already have 3/3 hidden sub titles. Right-click one of your current sub titles to unequip it first.").withStyle(ChatFormatting.RED));
            return;
        }
        subtitles.add(normalizedId);
        saveSubtitles(profileId, subtitles);
        player.sendSystemMessage(Component.literal("Equipped hidden sub title: ").withStyle(ChatFormatting.GREEN).append(com.champutils.chat.ChatTagResolver.legacy(displayFor(player.getUUID(), normalizedId))).append(Component.literal(" §7(50% buff power)")));
    }

    public static void select(ServerPlayer player, String id) {
        UUID profileId = PlayerProfileManager.activeProfileId(player.getUUID());
        Set<String> unlocked = unlocked(player.getUUID());
        if (id == null || id.equalsIgnoreCase("none")) {
            if (com.champutils.database.DatabaseManager.isEnabled()) {
                selectedByProfile.put(profileId.toString(), "");
                TitleDatabaseRepository.selectAsync(profileId, "");
                saveSelections();
            } else {
                dataForKey(profileId.toString()).selected = "";
            }
            com.champutils.chat.ChatTagResolver.invalidate(player);
            player.sendSystemMessage(Component.literal("Title hidden.").withStyle(ChatFormatting.GRAY));
            return;
        }

        String normalizedId = id.trim();
        if (!unlocked.contains(normalizedId)) {
            player.sendSystemMessage(Component.literal("You have not unlocked that title.").withStyle(ChatFormatting.RED));
            return;
        }

        Set<String> subtitles = subtitleSetForProfile(profileId);
        boolean removedFromSubtitles = subtitles.remove(normalizedId);
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            selectedByProfile.put(profileId.toString(), normalizedId);
            TitleDatabaseRepository.selectAsync(profileId, normalizedId);
            saveSelections();
        } else {
            dataForKey(profileId.toString()).selected = normalizedId;
        }
        if (removedFromSubtitles) saveSubtitles(profileId, subtitles);
        com.champutils.chat.ChatTagResolver.invalidate(player);
        player.sendSystemMessage(Component.literal("Selected title: ").withStyle(ChatFormatting.GREEN).append(com.champutils.chat.ChatTagResolver.legacy(displayFor(player.getUUID(), normalizedId))));
    }

    public static String displayFor(String id) {
        return displayFor(null, id);
    }

    public static String displayFor(UUID uuid, String id) {
        if (id == null || id.isBlank()) return "";
        String wf = com.champutils.worldfirst.WorldFirstManager.titleDisplay(id);
        if (wf != null) return wf;
        String config = TitleConfig.display(id);
        if (config != null && !config.isBlank()) return config;
        String builtIn = TitleRegistry.defaultDisplay(id);
        if (builtIn != null) return builtIn;
        return "&7[" + id + "]";
    }

    public static void preloadAsync(UUID profileId) {
        if (profileId == null || !com.champutils.database.DatabaseManager.isEnabled()) return;
        if (sqlLoadedProfiles.contains(profileId) || !sqlLoadingProfiles.add(profileId)) return;
        TitleDatabaseRepository.loadSnapshotAsync(profileId).thenAccept(snapshot -> {
            Set<String> existing = sqlUnlockedCache.computeIfAbsent(profileId, ignored -> ConcurrentHashMap.newKeySet());
            // Merge instead of replacing so a title earned while the async preload was in-flight
            // cannot disappear from the live cache before its queued SQL write completes.
            existing.addAll(snapshot.unlocked());
            selectedByProfile.putIfAbsent(profileId.toString(), snapshot.selected() == null ? "" : snapshot.selected());
            if (snapshot.subtitles() != null) {
                Set<String> subtitles = subtitleSetForProfile(profileId);
                subtitles.addAll(snapshot.subtitles());
            }
            sqlLoadedProfiles.add(profileId);
            sqlLoadingProfiles.remove(profileId);
        }).exceptionally(error -> {
            sqlLoadingProfiles.remove(profileId);
            return null;
        });
    }

    private static Set<String> cachedSqlTitles(UUID profileId) {
        if (profileId == null) return new TreeSet<>();
        Set<String> existing = sqlUnlockedCache.computeIfAbsent(profileId, ignored -> ConcurrentHashMap.newKeySet());
        preloadAsync(profileId);
        return existing;
    }

    public static void preloadAccountAsync(UUID accountUuid) {
        if (accountUuid == null || !com.champutils.database.DatabaseManager.isEnabled()) return;
        if (sqlLoadedAccounts.contains(accountUuid) || !sqlLoadingAccounts.add(accountUuid)) return;
        TitleDatabaseRepository.loadAccountTitlesAsync(accountUuid).thenAccept(snapshot -> {
            Set<String> existing = sqlAccountUnlockedCache.computeIfAbsent(accountUuid, ignored -> ConcurrentHashMap.newKeySet());
            existing.addAll(snapshot);
            sqlLoadedAccounts.add(accountUuid);
            sqlLoadingAccounts.remove(accountUuid);
        }).exceptionally(error -> {
            sqlLoadingAccounts.remove(accountUuid);
            return null;
        });
    }

    private static Set<String> cachedSqlAccountTitles(UUID accountUuid) {
        if (accountUuid == null) return new TreeSet<>();
        Set<String> existing = sqlAccountUnlockedCache.computeIfAbsent(accountUuid, ignored -> ConcurrentHashMap.newKeySet());
        preloadAccountAsync(accountUuid);
        return existing;
    }

    private static String selectedForProfile(UUID profileId) {
        if (profileId == null) return "";
        preloadAsync(profileId);
        return selectedByProfile.getOrDefault(profileId.toString(), "");
    }

    private static Set<String> subtitleSetForProfile(UUID profileId) {
        if (profileId == null) return new LinkedHashSet<>();
        if (com.champutils.database.DatabaseManager.isEnabled()) preloadAsync(profileId);
        return subtitlesByProfile.computeIfAbsent(profileId.toString(), ignored -> ConcurrentHashMap.newKeySet());
    }

    private static void saveSubtitles(UUID profileId, Set<String> subtitles) {
        if (profileId == null) return;
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            TitleDatabaseRepository.saveSubtitlesAsync(profileId, subtitles);
        } else {
            dataForKey(profileId.toString()).subtitles.clear();
            dataForKey(profileId.toString()).subtitles.addAll(subtitles);
        }
        saveSelections();
    }

    private static PlayerTitles dataForKey(String key) {
        return state.players.computeIfAbsent(key, k -> new PlayerTitles());
    }

    private static String selectedLocal(UUID profileId) {
        return dataForKey(profileId.toString()).selected == null ? "" : dataForKey(profileId.toString()).selected;
    }

    private static synchronized void loadSelections() {
        selectedByProfile.clear();
        subtitlesByProfile.clear();
        try {
            SELECTED_FILE.getParentFile().mkdirs();
            if (!SELECTED_FILE.exists()) return;
            try (FileReader reader = new FileReader(SELECTED_FILE)) {
                SelectionState loaded = GSON.fromJson(reader, SelectionState.class);
                if (loaded != null && loaded.selectedByProfile != null) {
                    selectedByProfile.putAll(loaded.selectedByProfile);
                }
                if (loaded != null && loaded.subtitlesByProfile != null) {
                    for (Map.Entry<String, Set<String>> entry : loaded.subtitlesByProfile.entrySet()) {
                        Set<String> clean = subtitlesByProfile.computeIfAbsent(entry.getKey(), ignored -> ConcurrentHashMap.newKeySet());
                        if (entry.getValue() == null) continue;
                        for (String titleId : entry.getValue()) {
                            if (titleId != null && !titleId.isBlank() && clean.size() < MAX_SUB_TITLES) clean.add(titleId.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load title selections.");
            e.printStackTrace();
        }
    }

    private static synchronized void saveSelections() {
        try {
            SELECTED_FILE.getParentFile().mkdirs();
            SelectionState out = new SelectionState();
            out.selectedByProfile.putAll(selectedByProfile);
            for (Map.Entry<String, Set<String>> entry : subtitlesByProfile.entrySet()) {
                out.subtitlesByProfile.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            try (FileWriter writer = new FileWriter(SELECTED_FILE)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save title selections.");
            e.printStackTrace();
        }
    }

    private static final class State { Map<String, PlayerTitles> players = new ConcurrentHashMap<>(); }
    private static final class PlayerTitles { Set<String> unlocked = new TreeSet<>(); String selected = ""; Set<String> subtitles = new LinkedHashSet<>(); }
    private static final class SelectionState { Map<String, String> selectedByProfile = new TreeMap<>(); Map<String, Set<String>> subtitlesByProfile = new TreeMap<>(); }
}
