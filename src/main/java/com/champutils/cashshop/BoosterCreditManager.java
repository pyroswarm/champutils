package com.champutils.cashshop;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.permissions.LuckPermsHook;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BoosterCreditManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/booster_credits.json");
    private static final String VIP_PLUS_PERMISSION = "champutils.boosters.daily.vipplus";
    private static final int VIP_PLUS_DAILY_MAX_CLAIMS = 3;
    private static final int VIP_PLUS_EARNED_CREDIT_CAP = 10;
    private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;

    private static State state = new State();
    private static final Set<UUID> SQL_LOADED = new HashSet<>();
    private static final String STATE_KEY = "booster_credits";
    private static boolean registered = false;
    private static int tickCounter = 0;

    private BoosterCreditManager() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                State loaded = GSON.fromJson(reader, State.class);
                state = loaded == null ? new State() : loaded;
                if (state.players == null) state.players = new HashMap<>();
                sanitize();
            }
        } catch (Exception e) {
            e.printStackTrace();
            state = new State();
        }
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> updateVipPlusProgress(handler.player, true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> saveVipPlusSession(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter < 20 * 60) return;
            tickCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                updateVipPlusProgress(player, false);
            }
        });
    }

    public static synchronized int credits(ServerPlayer player) {
        Entry e = entry(player.getUUID());
        return Math.max(0, e.purchasedCredits) + Math.max(0, e.vipPlusCredits);
    }

    public static synchronized int purchasedCredits(ServerPlayer player) {
        return Math.max(0, entry(player.getUUID()).purchasedCredits);
    }

    public static synchronized int vipPlusCredits(ServerPlayer player) {
        return Math.max(0, entry(player.getUUID()).vipPlusCredits);
    }

    public static synchronized void addCredits(UUID uuid, int amount) {
        addPurchasedCredits(uuid, amount);
    }

    public static synchronized void addPurchasedCredits(UUID uuid, int amount) {
        if (uuid == null || amount <= 0) return;
        entry(uuid).purchasedCredits += amount;
        save();
        savePlayer(uuid);
    }

    public static synchronized boolean spend(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return false;
        Entry e = entry(player.getUUID());
        int total = Math.max(0, e.purchasedCredits) + Math.max(0, e.vipPlusCredits);
        if (total < amount) return false;

        int fromPurchased = Math.min(Math.max(0, e.purchasedCredits), amount);
        e.purchasedCredits -= fromPurchased;
        int remaining = amount - fromPurchased;
        if (remaining > 0) e.vipPlusCredits = Math.max(0, e.vipPlusCredits - remaining);
        save();
        savePlayer(player.getUUID());
        return true;
    }

    public static synchronized void updateVipPlusProgress(ServerPlayer player, boolean joining) {
        if (player == null) return;
        if (!LuckPermsHook.hasExactPermissionNode(player, VIP_PLUS_PERMISSION)) return;
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        long now = System.currentTimeMillis();
        Entry e = entry(player.getUUID());

        if (!today.equals(e.vipPlusDate)) {
            e.vipPlusDate = today;
            e.vipPlusClaimsToday = 0;
            e.vipPlusDailyOnlineMillis = 0L;
            e.vipPlusLastSeenMillis = now;
        }

        if (e.vipPlusLastSeenMillis <= 0L || joining) {
            e.vipPlusLastSeenMillis = now;
        } else {
            long elapsed = Math.max(0L, now - e.vipPlusLastSeenMillis);
            e.vipPlusDailyOnlineMillis += Math.min(elapsed, 5L * 60L * 1000L);
            e.vipPlusLastSeenMillis = now;
        }

        boolean changed = grantEligibleVipPlusCredits(player, e);
        if (changed) {
            save();
            savePlayer(player.getUUID());
        }
    }

    public static synchronized void saveVipPlusSession(ServerPlayer player) {
        if (player == null) return;
        Entry e = entry(player.getUUID());
        long now = System.currentTimeMillis();
        if (e.vipPlusLastSeenMillis > 0L) {
            e.vipPlusDailyOnlineMillis += Math.min(Math.max(0L, now - e.vipPlusLastSeenMillis), 5L * 60L * 1000L);
            e.vipPlusLastSeenMillis = 0L;
            save();
            savePlayer(player.getUUID());
        }
    }

    private static boolean grantEligibleVipPlusCredits(ServerPlayer player, Entry e) {
        boolean changed = false;
        while (e.vipPlusClaimsToday < VIP_PLUS_DAILY_MAX_CLAIMS) {
            long requiredOnline = e.vipPlusClaimsToday == 0 ? 0L : e.vipPlusClaimsToday * ONE_HOUR_MILLIS;
            if (e.vipPlusDailyOnlineMillis < requiredOnline) break;
            if (e.vipPlusCredits >= VIP_PLUS_EARNED_CREDIT_CAP) {
                String noticeKey = e.vipPlusDate + ":" + e.vipPlusClaimsToday;
                if (!noticeKey.equals(e.vipPlusCapNoticeKey)) {
                    e.vipPlusCapNoticeKey = noticeKey;
                    changed = true;
                    player.sendSystemMessage(Component.literal("VIP+ booster credits are capped at " + VIP_PLUS_EARNED_CREDIT_CAP + ". Spend one to earn more daily credits.").withStyle(ChatFormatting.YELLOW));
                }
                break;
            }
            e.vipPlusCredits += 1;
            e.vipPlusClaimsToday += 1;
            changed = true;
            player.sendSystemMessage(Component.literal("VIP+ daily reward: +1 earned booster credit (" + e.vipPlusClaimsToday + "/" + VIP_PLUS_DAILY_MAX_CLAIMS + ").").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return changed;
    }

    private static Entry entry(UUID uuid) {
        if (state.players == null) state.players = new HashMap<>();
        Entry fallback = state.players.computeIfAbsent(uuid.toString(), ignored -> new Entry());
        Entry e = fallback;
        if (uuid != null && SQL_LOADED.add(uuid)) {
            e = SharedJsonStateRepository.loadPlayer(uuid, STATE_KEY, Entry.class, fallback);
            state.players.put(uuid.toString(), e);
        }
        migrateLegacy(e);
        return e;
    }

    private static void savePlayer(UUID uuid) {
        if (uuid == null || state.players == null) return;
        Entry entry = state.players.get(uuid.toString());
        if (entry != null) {
            SharedJsonStateRepository.savePlayer(uuid, STATE_KEY, entry);
            NetworkEventManager.publishCacheInvalidation("BOOSTER_CREDITS", uuid);
        }
    }

    public static synchronized void invalidateSharedCache(UUID uuid) {
        if (uuid == null) return;
        SQL_LOADED.remove(uuid);
        if (state.players != null) {
            state.players.remove(uuid.toString());
        }
    }

    private static void sanitize() {
        if (state.players == null) state.players = new HashMap<>();
        for (Entry e : state.players.values()) {
            migrateLegacy(e);
            if (e.purchasedCredits < 0) e.purchasedCredits = 0;
            if (e.vipPlusCredits < 0) e.vipPlusCredits = 0;
            if (e.vipPlusCredits > VIP_PLUS_EARNED_CREDIT_CAP) e.vipPlusCredits = VIP_PLUS_EARNED_CREDIT_CAP;
            if (e.vipPlusClaimsToday < 0) e.vipPlusClaimsToday = 0;
            if (e.vipPlusClaimsToday > VIP_PLUS_DAILY_MAX_CLAIMS) e.vipPlusClaimsToday = VIP_PLUS_DAILY_MAX_CLAIMS;
            if (e.vipPlusDailyOnlineMillis < 0L) e.vipPlusDailyOnlineMillis = 0L;
        }
    }

    private static void migrateLegacy(Entry e) {
        if (e == null) return;
        if (e.credits > 0 && e.purchasedCredits == 0 && e.vipPlusCredits == 0) {
            e.purchasedCredits = e.credits;
        }
        e.credits = 0;
    }

    public static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(state, writer); }
            if (state.players != null) {
                for (String key : state.players.keySet()) {
                    try {
                        savePlayer(UUID.fromString(key));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static final class State { Map<String, Entry> players = new HashMap<>(); }
    private static final class Entry {
        /** Legacy pre-split balance. Migrated into purchasedCredits on load. */
        int credits = 0;
        int purchasedCredits = 0;
        int vipPlusCredits = 0;
        String vipPlusDate = "";
        int vipPlusClaimsToday = 0;
        long vipPlusDailyOnlineMillis = 0L;
        long vipPlusLastSeenMillis = 0L;
        String vipPlusCapNoticeKey = "";
        /** Legacy one-credit-per-day field, intentionally ignored after split. */
        String lastDailyVipPlus = "";
    }
}
