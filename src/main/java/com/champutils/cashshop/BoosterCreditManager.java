package com.champutils.cashshop;

import com.champutils.permissions.LuckPermsHook;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import java.util.Map;
import java.util.UUID;

public final class BoosterCreditManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/booster_credits.json");
    private static State state = new State();
    private static boolean registered = false;

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
            }
        } catch (Exception e) {
            e.printStackTrace();
            state = new State();
        }
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> grantDailyIfEligible(handler.player));
    }

    public static synchronized int credits(ServerPlayer player) {
        return entry(player.getUUID()).credits;
    }

    public static synchronized void addCredits(UUID uuid, int amount) {
        if (amount <= 0) return;
        entry(uuid).credits += amount;
        save();
    }

    public static synchronized boolean spend(ServerPlayer player, int amount) {
        Entry e = entry(player.getUUID());
        if (amount <= 0 || e.credits < amount) return false;
        e.credits -= amount;
        save();
        return true;
    }

    public static synchronized void grantDailyIfEligible(ServerPlayer player) {
        if (player == null) return;
        if (!LuckPermsHook.hasExactPermissionNode(player, "champutils.boosters.daily.vipplus")) return;
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        Entry e = entry(player.getUUID());
        if (today.equals(e.lastDailyVipPlus)) return;
        e.lastDailyVipPlus = today;
        e.credits += 1;
        save();
        player.sendSystemMessage(Component.literal("VIP+ daily reward: +1 server booster credit.").withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static Entry entry(UUID uuid) {
        if (state.players == null) state.players = new HashMap<>();
        return state.players.computeIfAbsent(uuid.toString(), ignored -> new Entry());
    }

    public static synchronized void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(state, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static final class State { Map<String, Entry> players = new HashMap<>(); }
    private static final class Entry { int credits = 0; String lastDailyVipPlus = ""; }
}
