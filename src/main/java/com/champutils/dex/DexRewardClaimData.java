package com.champutils.dex;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.profile.PlayerProfileManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class DexRewardClaimData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, Set<Integer>>>() {}.getType();
    private static final Map<String, Set<Integer>> CLAIMS = new LinkedHashMap<>();
    private static final String LEGACY_STATE_KEY = "dex_reward_claims";
    private static final String PROFILE_STATE_KEY = "dex_reward_claims_v2";

    private DexRewardClaimData() {}

    public static synchronized void load() {
        CLAIMS.clear();
        try {
            File file = file();
            if (file.exists()) {
                try (FileReader reader = new FileReader(file)) {
                    Map<String, Set<Integer>> loaded = GSON.fromJson(reader, TYPE);
                    if (loaded != null) CLAIMS.putAll(loaded);
                }
            }
            ClaimRoot shared = SharedJsonStateRepository.loadGlobal(LEGACY_STATE_KEY, ClaimRoot.class, new ClaimRoot(CLAIMS));
            if (shared != null && shared.claims != null) {
                for (Map.Entry<String, Set<Integer>> entry : shared.claims.entrySet()) {
                    CLAIMS.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>()).addAll(entry.getValue());
                }
            }
            saveLocalMirror();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void preload(UUID profileId) {
        if (profileId == null) return;
        Set<Integer> legacy;
        synchronized (DexRewardClaimData.class) {
            legacy = new HashSet<>(CLAIMS.getOrDefault(profileId.toString(), Set.of()));
        }
        try {
            Set<Integer> merged = SharedJsonStateRepository.mutateProfileAsync(
                    profileId,
                    PROFILE_STATE_KEY,
                    ClaimState.class,
                    new ClaimState(legacy),
                    state -> {
                        if (state.claims == null) state.claims = new HashSet<>();
                        state.claims.addAll(legacy);
                        return new HashSet<>(state.claims);
                    }
            ).get(5, TimeUnit.SECONDS);
            synchronized (DexRewardClaimData.class) {
                CLAIMS.put(profileId.toString(), merged == null ? new HashSet<>() : new HashSet<>(merged));
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to preload dex reward claims for profile " + profileId + ".");
            e.printStackTrace();
        }
    }

    public static synchronized void save() {
        saveLocalMirror();
    }

    private static void saveLocalMirror() {
        try {
            File file = file();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(CLAIMS, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized boolean hasClaimed(UUID playerUuid, int percent) {
        UUID profileId = PlayerProfileManager.activeProfileId(playerUuid);
        if (profileId == null) return false;
        Set<Integer> claimed = CLAIMS.get(profileId.toString());
        return claimed != null && claimed.contains(percent);
    }

    public static synchronized boolean hasAnyClaimedForProfile(UUID profileId) {
        if (profileId == null) return false;
        Set<Integer> claimed = CLAIMS.get(profileId.toString());
        return claimed != null && !claimed.isEmpty();
    }

    public static synchronized boolean hasClaimedForProfile(UUID profileId, int percent) {
        if (profileId == null) return false;
        Set<Integer> claimed = CLAIMS.get(profileId.toString());
        return claimed != null && claimed.contains(percent);
    }

    /** Atomically reserves a reward tier so two backends can never grant it twice. */
    public static CompletableFuture<Boolean> markClaimedAsync(UUID profileId, int percent) {
        if (profileId == null) return CompletableFuture.completedFuture(false);
        Set<Integer> fallback;
        synchronized (DexRewardClaimData.class) {
            fallback = new HashSet<>(CLAIMS.getOrDefault(profileId.toString(), Set.of()));
        }
        return SharedJsonStateRepository.mutateProfileAsync(
                profileId,
                PROFILE_STATE_KEY,
                ClaimState.class,
                new ClaimState(fallback),
                state -> {
                    if (state.claims == null) state.claims = new HashSet<>();
                    boolean added = state.claims.add(percent);
                    synchronized (DexRewardClaimData.class) {
                        CLAIMS.put(profileId.toString(), new HashSet<>(state.claims));
                        saveLocalMirror();
                    }
                    return added;
                }
        );
    }

    public static synchronized void invalidateSharedCache(UUID profileId) {
        if (profileId != null) CLAIMS.remove(profileId.toString());
    }

    private static File file() {
        return new File("config/champutils/dex_reward_claims.json");
    }

    private static final class ClaimState {
        Set<Integer> claims = new HashSet<>();
        ClaimState() {}
        ClaimState(Set<Integer> claims) { if (claims != null) this.claims.addAll(claims); }
    }

    private static final class ClaimRoot {
        Map<String, Set<Integer>> claims = new LinkedHashMap<>();
        ClaimRoot() {}
        ClaimRoot(Map<String, Set<Integer>> claims) { if (claims != null) this.claims.putAll(claims); }
    }
}
