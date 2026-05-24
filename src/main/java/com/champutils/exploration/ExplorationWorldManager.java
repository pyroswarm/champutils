package com.champutils.exploration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExplorationWorldManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/exploration_worlds_state.json");
    private static State state = new State();

    private ExplorationWorldManager() {}

    public static void load() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (!FILE.exists()) { bootstrapState(); save(); return; }
            try (FileReader reader = new FileReader(FILE)) {
                State loaded = GSON.fromJson(reader, State.class);
                state = loaded == null ? new State() : loaded;
            }
            bootstrapState();
            save();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load exploration world state.");
            e.printStackTrace();
            state = new State();
            bootstrapState();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(state, writer); }
        catch (Exception e) { System.err.println("[ChampUtils] Failed to save exploration world state."); e.printStackTrace(); }
    }

    public static List<Entry> entries() { bootstrapState(); return state.worlds; }

    public static void tick(MinecraftServer server) {
        if (server == null || !ExplorationWorldConfig.get().enabled) return;
        if (server.getTickCount() % 10 == 0) enforceBorders(server);
        if (server.getTickCount() % 1200 != 0) return;
        bootstrapState();
        long now = System.currentTimeMillis();
        if (state.wipeInProgressWorld != null && !state.wipeInProgressWorld.isBlank()) return;
        for (Entry entry : state.worlds) {
            if ("GENERATING".equalsIgnoreCase(entry.status) || "WIPING".equalsIgnoreCase(entry.status)) return;
        }
        for (Entry entry : state.worlds) {
            if ("PENDING".equalsIgnoreCase(entry.status)) {
                startWipe(server, entry, false);
                return;
            }
        }
        for (Entry entry : state.worlds) {
            if (entry.nextWipeAtMillis <= now) {
                startWipe(server, entry, false);
                break;
            }
        }
    }

    public static boolean teleport(ServerPlayer player, int index) {
        bootstrapState();
        if (index < 1 || index > state.worlds.size()) return false;
        Entry entry = state.worlds.get(index - 1);
        if (!"READY".equalsIgnoreCase(entry.status) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("That exploration world is currently " + entry.status + ". Try another one.").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        ServerLevel level = getLevel(player.server, entry.worldName);
        if (level == null) {
            player.sendSystemMessage(Component.literal("That exploration world is not loaded yet: " + entry.worldName).withStyle(ChatFormatting.RED));
            return false;
        }
        player.teleportTo(level, 0.5D, ExplorationWorldConfig.get().spawnY, 0.5D, player.getYRot(), player.getXRot());
        return true;
    }

    public static void startWipe(MinecraftServer server, Entry entry, boolean forced) {
        if (server == null || entry == null) return;
        if (state.wipeInProgressWorld != null && !state.wipeInProgressWorld.isBlank() && !forced) return;
        state.wipeInProgressWorld = entry.worldName;
        entry.status = "WIPING";
        save();

        ServerLevel oldLevel = getLevel(server, entry.worldName);
        if (oldLevel != null) {
            for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                if (player.serverLevel() == oldLevel) {
                    player.teleportTo(server.overworld(), server.overworld().getSharedSpawnPos().getX() + 0.5D, server.overworld().getSharedSpawnPos().getY(), server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("Exploration world is wiping. You were moved to spawn.").withStyle(ChatFormatting.YELLOW));
                }
            }
        }

        if (ExplorationWorldConfig.get().runWorldCommands) {
            for (String command : ExplorationWorldConfig.get().deleteCommands) run(server, apply(command, entry));
            for (String command : ExplorationWorldConfig.get().createCommands) run(server, apply(command, entry));
            for (String command : ExplorationWorldConfig.get().chunkyPregenerationCommands) run(server, apply(command, entry));
        }

        entry.status = ExplorationWorldConfig.get().requirePregenerationBeforeEntry ? "GENERATING" : "READY";
        entry.lastWipeAtMillis = System.currentTimeMillis();
        entry.nextWipeAtMillis = entry.lastWipeAtMillis + ExplorationWorldConfig.get().wipeIntervalHours * 60L * 60L * 1000L;
        state.wipeInProgressWorld = "";
        save();
    }

    public static boolean markReady(String worldName) {
        bootstrapState();
        for (Entry entry : state.worlds) {
            if (entry.worldName.equalsIgnoreCase(worldName)) {
                entry.status = "READY";
                save();
                return true;
            }
        }
        return false;
    }

    private static void enforceBorders(MinecraftServer server) {
        int radius = ExplorationWorldConfig.get().borderRadius;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = find(player.serverLevel());
            if (entry == null || player.hasPermissions(4)) continue;
            BlockPos pos = player.blockPosition();
            if (Math.abs(pos.getX()) <= radius && Math.abs(pos.getZ()) <= radius) continue;
            int x = Math.max(-radius + 2, Math.min(radius - 2, pos.getX()));
            int z = Math.max(-radius + 2, Math.min(radius - 2, pos.getZ()));
            player.teleportTo(player.serverLevel(), x + 0.5D, player.getY(), z + 0.5D, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("You cannot leave the 5000-block exploration border.").withStyle(ChatFormatting.RED));
        }
    }

    public static Entry find(ServerLevel level) {
        if (level == null) return null;
        String name = level.dimension().location().toString();
        for (Entry entry : entries()) if (entry.worldName.equalsIgnoreCase(name)) return entry;
        return null;
    }

    private static void bootstrapState() {
        ExplorationWorldConfig.Data cfg = ExplorationWorldConfig.get();
        if (state.worlds == null) state.worlds = new ArrayList<>();
        long now = System.currentTimeMillis();
        while (state.worlds.size() < cfg.worldCount) {
            int number = state.worlds.size() + 1;
            Entry entry = new Entry();
            entry.index = number;
            entry.worldName = cfg.worldPrefix + "_" + number;
            entry.status = cfg.requirePregenerationBeforeEntry ? "PENDING" : "READY";
            entry.lastWipeAtMillis = 0L;
            entry.nextWipeAtMillis = now + ((long) (number - 1) * cfg.staggerHours * 60L * 60L * 1000L);
            state.worlds.add(entry);
        }
        if (state.worlds.size() > cfg.worldCount) state.worlds = new ArrayList<>(state.worlds.subList(0, cfg.worldCount));
    }

    private static ServerLevel getLevel(MinecraftServer server, String worldName) {
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        } catch (Exception ignored) { return null; }
    }

    private static String apply(String command, Entry entry) {
        if (command == null) return "";
        return command
                .replace("{world}", entry.worldName)
                .replace("{index}", Integer.toString(entry.index))
                .replace("{border_radius}", Integer.toString(ExplorationWorldConfig.get().borderRadius))
                .replace("{status}", entry.status == null ? "" : entry.status.toLowerCase(Locale.ROOT));
    }

    private static void run(MinecraftServer server, String command) {
        if (command == null || command.isBlank()) return;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), clean);
    }

    public static final class State {
        public String wipeInProgressWorld = "";
        public List<Entry> worlds = new ArrayList<>();
    }

    public static final class Entry {
        public int index;
        public String worldName;
        public String status;
        public long lastWipeAtMillis;
        public long nextWipeAtMillis;
    }
}
