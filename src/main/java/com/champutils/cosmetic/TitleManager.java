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

    private static State state = new State();

    /** SQL ownership cache keyed by active profile id. Keeps chat/menu paths off the database. */
    private static final Map<UUID, Set<String>> sqlUnlockedCache = new ConcurrentHashMap<>();
    private static final Set<UUID> sqlLoadedProfiles = ConcurrentHashMap.newKeySet();

    /** Equipped title is local/cache-backed so SQL only stores ownership. Keyed by profile id, not account uuid. */
    private static final Map<String, String> selectedByProfile = new ConcurrentHashMap<>();

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
        com.champutils.profession.ProfessionXpBoostManager.registerSource(new com.champutils.profession.ProfessionXpBoostManager.ProfessionXpBoostSource() {
            @Override public String id() { return "active_title"; }
            @Override public int priority() { return 50; }
            @Override public double getBonus(ServerPlayer player, com.champutils.profession.ProfessionType profession) {
                return TitleConfig.activeProfessionXpBonus(player, profession);
            }
        });
        state = new State();
        sqlUnlockedCache.clear();
        sqlLoadedProfiles.clear();
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
        boolean changed;

        if (com.champutils.database.DatabaseManager.isEnabled()) {
            Set<String> owned = cachedSqlTitles(profileId);
            if (owned.contains(normalizedId)) {
                return false;
            }
            changed = TitleDatabaseRepository.unlock(profileId, normalizedId);
            if (changed) {
                owned.add(normalizedId);
                if (selectedForProfile(profileId).isBlank()) {
                    selectedByProfile.put(profileId.toString(), normalizedId);
                    TitleDatabaseRepository.select(profileId, normalizedId);
                    saveSelections();
                }
            }
        } else {
            PlayerTitles data = data(player.getUUID());
            changed = data.unlocked.add(normalizedId);
            if (changed && (data.selected == null || data.selected.isBlank())) data.selected = normalizedId;
        }

        if (!changed) return false;
        com.champutils.chat.ChatTagResolver.invalidate(player);
        Component title = com.champutils.chat.ChatTagResolver.legacy(display);
        player.server.getPlayerList().broadcastSystemMessage(Component.literal("[Title] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" unlocked ").withStyle(ChatFormatting.GRAY))
                .append(title)
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)), false);
        return true;
    }

    public static Set<String> unlocked(UUID uuid) {
        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            return new TreeSet<>(cachedSqlTitles(profileId));
        }
        return new TreeSet<>(data(uuid).unlocked);
    }

    public static String selected(UUID uuid) {
        UUID profileId = PlayerProfileManager.activeProfileId(uuid);
        if (com.champutils.database.DatabaseManager.isEnabled()) {
            String selected = selectedForProfile(profileId);
            return cachedSqlTitles(profileId).contains(selected) ? selected : "";
        }
        return data(uuid).selected;
    }

    public static void select(ServerPlayer player, String id) {
        UUID profileId = PlayerProfileManager.activeProfileId(player.getUUID());
        Set<String> unlocked = unlocked(player.getUUID());
        if (id == null || id.equalsIgnoreCase("none")) {
            if (com.champutils.database.DatabaseManager.isEnabled()) {
                selectedByProfile.put(profileId.toString(), "");
                TitleDatabaseRepository.select(profileId, "");
                saveSelections();
            com.champutils.chat.ChatTagResolver.invalidate(player);
            } else {
                data(player.getUUID()).selected = "";
            }
            player.sendSystemMessage(Component.literal("Title hidden.").withStyle(ChatFormatting.GRAY));
            return;
        }

        String normalizedId = id.trim();
        if (!unlocked.contains(normalizedId)) {
            player.sendSystemMessage(Component.literal("You have not unlocked that title.").withStyle(ChatFormatting.RED));
            return;
        }

        if (com.champutils.database.DatabaseManager.isEnabled()) {
            selectedByProfile.put(profileId.toString(), normalizedId);
            TitleDatabaseRepository.select(profileId, normalizedId);
            saveSelections();
        } else {
            data(player.getUUID()).selected = normalizedId;
        }
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

    private static Set<String> cachedSqlTitles(UUID profileId) {
        if (profileId == null) return new TreeSet<>();
        Set<String> existing = sqlUnlockedCache.computeIfAbsent(profileId, ignored -> ConcurrentHashMap.newKeySet());
        if (sqlLoadedProfiles.add(profileId)) {
            existing.clear();
            existing.addAll(TitleDatabaseRepository.unlocked(profileId));
        }
        return existing;
    }

    private static String selectedForProfile(UUID profileId) {
        if (profileId == null) return "";
        String key = profileId.toString();
        if (!selectedByProfile.containsKey(key) && com.champutils.database.DatabaseManager.isEnabled()) {
            selectedByProfile.put(key, TitleDatabaseRepository.selected(profileId));
        }
        return selectedByProfile.getOrDefault(key, "");
    }

    private static PlayerTitles data(UUID uuid) {
        return state.players.computeIfAbsent(PlayerProfileManager.activeProfileId(uuid).toString(), k -> new PlayerTitles());
    }

    private static synchronized void loadSelections() {
        selectedByProfile.clear();
        try {
            SELECTED_FILE.getParentFile().mkdirs();
            if (!SELECTED_FILE.exists()) return;
            try (FileReader reader = new FileReader(SELECTED_FILE)) {
                SelectionState loaded = GSON.fromJson(reader, SelectionState.class);
                if (loaded != null && loaded.selectedByProfile != null) {
                    selectedByProfile.putAll(loaded.selectedByProfile);
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
            try (FileWriter writer = new FileWriter(SELECTED_FILE)) {
                GSON.toJson(out, writer);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save title selections.");
            e.printStackTrace();
        }
    }

    private static final class State { Map<String, PlayerTitles> players = new ConcurrentHashMap<>(); }
    private static final class PlayerTitles { Set<String> unlocked = new TreeSet<>(); String selected = ""; }
    private static final class SelectionState { Map<String, String> selectedByProfile = new TreeMap<>(); }
}
