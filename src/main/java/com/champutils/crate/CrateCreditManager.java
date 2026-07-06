package com.champutils.crate;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CrateCreditManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/crate_credits.json");
    private static final Map<UUID, Map<String, Integer>> CREDITS = new HashMap<>();
    private static final Set<UUID> SQL_LOADED = new HashSet<>();
    private static final String STATE_KEY = "crate_credits";
    private static boolean loaded = false;

    private CrateCreditManager() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        CREDITS.clear();
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                Type type = new TypeToken<Map<String, Map<String, Integer>>>() {}.getType();
                Map<String, Map<String, Integer>> raw = GSON.fromJson(reader, type);
                if (raw == null) return;
                for (Map.Entry<String, Map<String, Integer>> entry : raw.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        Map<String, Integer> balances = new LinkedHashMap<>();
                        if (entry.getValue() != null) {
                            for (Map.Entry<String, Integer> b : entry.getValue().entrySet()) {
                                int amount = Math.max(0, b.getValue() == null ? 0 : b.getValue());
                                if (amount > 0) balances.put(normalize(b.getKey()), amount);
                            }
                        }
                        CREDITS.put(uuid, balances);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load crate credits.");
            e.printStackTrace();
        }
    }

    public static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
            for (Map.Entry<UUID, Map<String, Integer>> entry : CREDITS.entrySet()) {
                out.put(entry.getKey().toString(), new LinkedHashMap<>(entry.getValue()));
            }
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(out, writer); }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save crate credits.");
            e.printStackTrace();
        }
    }

    public static int getCredits(ServerPlayer player, String crateId) {
        if (player == null) return 0;
        load();
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        ensureSqlLoaded(profileId);
        return CREDITS.getOrDefault(profileId, Map.of()).getOrDefault(normalize(crateId), 0);
    }

    public static void addCredits(ServerPlayer player, String crateId, int amount) {
        if (player == null || amount <= 0) return;
        load();
        String id = normalize(crateId);
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        ensureSqlLoaded(profileId);
        CREDITS.computeIfAbsent(profileId, k -> new LinkedHashMap<>());
        Map<String, Integer> balances = CREDITS.get(profileId);
        balances.put(id, balances.getOrDefault(id, 0) + amount);
        save();
        saveProfile(profileId);
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        String name = crate == null ? id : crate.displayName;
        player.sendSystemMessage(Component.literal("+" + amount + " " + name + " credit" + (amount == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GOLD));
    }

    public static void setCredits(ServerPlayer player, String crateId, int amount) {
        if (player == null) return;
        load();
        String id = normalize(crateId);
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        ensureSqlLoaded(profileId);
        Map<String, Integer> balances = CREDITS.computeIfAbsent(profileId, k -> new LinkedHashMap<>());
        int safeAmount = Math.max(0, amount);
        if (safeAmount == 0) balances.remove(id); else balances.put(id, safeAmount);
        save();
        saveProfile(profileId);
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        String name = crate == null ? id : crate.displayName;
        player.sendSystemMessage(Component.literal("Set " + name + " credits to " + safeAmount + ".").withStyle(ChatFormatting.GOLD));
    }

    public static void removeCredits(ServerPlayer player, String crateId, int amount) {
        if (player == null || amount <= 0) return;
        load();
        String id = normalize(crateId);
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        ensureSqlLoaded(profileId);
        Map<String, Integer> balances = CREDITS.computeIfAbsent(profileId, k -> new LinkedHashMap<>());
        int current = balances.getOrDefault(id, 0);
        int next = Math.max(0, current - amount);
        if (next == 0) balances.remove(id); else balances.put(id, next);
        save();
        saveProfile(profileId);
        CrateConfig.CrateDefinition crate = CrateConfig.getCrate(id);
        String name = crate == null ? id : crate.displayName;
        player.sendSystemMessage(Component.literal("-" + Math.min(amount, current) + " " + name + " credit" + (Math.min(amount, current) == 1 ? "" : "s") + ".").withStyle(ChatFormatting.RED));
    }

    public static boolean spendCredit(ServerPlayer player, String crateId) {
        if (player == null) return false;
        load();
        String id = normalize(crateId);
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        ensureSqlLoaded(profileId);
        Map<String, Integer> balances = CREDITS.computeIfAbsent(profileId, k -> new LinkedHashMap<>());
        int current = balances.getOrDefault(id, 0);
        if (current <= 0) return false;
        if (current == 1) balances.remove(id); else balances.put(id, current - 1);
        save();
        saveProfile(profileId);
        return true;
    }

    private static synchronized void ensureSqlLoaded(UUID profileId) {
        if (profileId == null || !SQL_LOADED.add(profileId)) return;
        Map<String, Integer> fallback = CREDITS.getOrDefault(profileId, new LinkedHashMap<>());
        ProfileCredits loaded = SharedJsonStateRepository.loadProfile(profileId, STATE_KEY, ProfileCredits.class, new ProfileCredits(fallback));
        CREDITS.put(profileId, clean(loaded == null ? fallback : loaded.credits));
    }

    private static void saveProfile(UUID profileId) {
        if (profileId == null) return;
        SharedJsonStateRepository.saveProfile(profileId, STATE_KEY, new ProfileCredits(CREDITS.getOrDefault(profileId, Map.of())));
        NetworkEventManager.publishCacheInvalidation("CRATE_CREDITS", profileId);
    }

    public static synchronized void invalidateSharedCache(UUID profileId) {
        if (profileId == null) return;
        SQL_LOADED.remove(profileId);
        CREDITS.remove(profileId);
    }

    private static Map<String, Integer> clean(Map<String, Integer> raw) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            int amount = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (amount > 0) out.put(normalize(entry.getKey()), amount);
        }
        return out;
    }

    private static final class ProfileCredits {
        Map<String, Integer> credits = new LinkedHashMap<>();

        ProfileCredits() {
        }

        ProfileCredits(Map<String, Integer> credits) {
            this.credits = clean(credits);
        }
    }

    public static String normalize(String id) {
        if (id == null || id.isBlank()) return "";
        String value = id.trim().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (value) {
            case "f", "f_rank", "common" -> "f";
            case "e", "e_rank", "uncommon" -> "e";
            case "d", "d_rank", "rare" -> "d";
            case "c", "c_rank", "epic" -> "c";
            case "b", "b_rank" -> "b";
            case "a", "a_rank", "legendary" -> "a";
            case "s", "s_rank", "mythic" -> "s";
            default -> value;
        };
    }
}
