package com.champutils.cosmetic;

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
    private static final File FILE = new File("config/champutils/player_titles.json");
    private static State state = new State();

    private TitleManager() {}

    public static void load() {
        try {
            if (!FILE.exists()) { save(); return; }
            try (FileReader r = new FileReader(FILE)) {
                State loaded = GSON.fromJson(r, State.class);
                state = loaded == null ? new State() : loaded;
                if (state.players == null) state.players = new ConcurrentHashMap<>();
            }
        } catch (Exception e) { state = new State(); e.printStackTrace(); }
    }

    public static void save() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter w = new FileWriter(FILE)) { GSON.toJson(state, w); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static boolean unlock(ServerPlayer player, String id, String display) {
        if (player == null || id == null || id.isBlank()) return false;
        PlayerTitles data = data(player.getUUID());
        if (!data.unlocked.add(id)) return false;
        if (data.selected == null || data.selected.isBlank()) data.selected = id;
        save();
        player.sendSystemMessage(Component.literal("Unlocked title: ").withStyle(ChatFormatting.GOLD).append(com.champutils.chat.ChatTagResolver.legacy(display)));
        return true;
    }

    public static Set<String> unlocked(UUID uuid) { return new TreeSet<>(data(uuid).unlocked); }
    public static String selected(UUID uuid) { return data(uuid).selected; }
    public static void select(ServerPlayer player, String id) {
        PlayerTitles data = data(player.getUUID());
        if (id == null || id.equalsIgnoreCase("none")) {
            data.selected = "";
            save();
            player.sendSystemMessage(Component.literal("Title hidden.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!data.unlocked.contains(id)) {
            player.sendSystemMessage(Component.literal("You have not unlocked that title.").withStyle(ChatFormatting.RED));
            return;
        }
        data.selected = id;
        save();
        player.sendSystemMessage(Component.literal("Selected title: ").withStyle(ChatFormatting.GREEN).append(com.champutils.chat.ChatTagResolver.legacy(displayFor(id))));
    }

    public static String displayFor(String id) {
        if (id == null || id.isBlank()) return "";
        String wf = com.champutils.worldfirst.WorldFirstManager.titleDisplay(id);
        if (wf != null) return wf;
        return "&7[" + id + "]";
    }

    private static PlayerTitles data(UUID uuid) {
        return state.players.computeIfAbsent(uuid.toString(), k -> new PlayerTitles());
    }

    private static final class State { Map<String, PlayerTitles> players = new ConcurrentHashMap<>(); }
    private static final class PlayerTitles { Set<String> unlocked = new TreeSet<>(); String selected = ""; }
}
