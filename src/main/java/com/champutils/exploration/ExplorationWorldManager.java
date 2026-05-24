package com.champutils.exploration;

import com.champutils.teleport.SafeTeleportManager;
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
import java.util.Collections;

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


    public static RtpTarget pickRtpTarget(MinecraftServer server) {
        return pickRtpTarget(server, "overworld");
    }

    public static RtpTarget pickRtpTarget(MinecraftServer server, String type) {
        if (server == null || !ExplorationWorldConfig.get().enabled) return null;
        bootstrapState();

        String wantedType = normalizeType(type);
        List<Entry> candidates = new ArrayList<>();
        for (Entry entry : state.worlds) {
            String entryType = normalizeType(entry.worldType);
            if (wantedType.equals(entryType) && isSafeForRtp(server, entry)) {
                candidates.add(entry);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        Collections.shuffle(candidates);
        Entry picked = candidates.get(0);
        ServerLevel level = getLevel(server, picked.worldName);
        if (level == null) {
            return null;
        }
        return new RtpTarget(picked, level);
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "overworld";
        String clean = type.trim().toLowerCase(Locale.ROOT);
        if (clean.equals("exploration") || clean.equals("overworld") || clean.equals("world")) return "overworld";
        if (clean.equals("nether")) return "nether";
        if (clean.equals("end") || clean.equals("the_end")) return "end";
        return clean;
    }

    public static boolean isSafeForRtp(MinecraftServer server, Entry entry) {
        if (server == null || entry == null) return false;
        if (!"READY".equalsIgnoreCase(entry.status)) return false;
        if (state.wipeInProgressWorld != null && state.wipeInProgressWorld.equalsIgnoreCase(entry.worldName)) return false;
        if (getLevel(server, entry.worldName) == null) return false;

        long avoidMs = Math.max(0L, ExplorationWorldConfig.get().rtpAvoidWipeMinutes) * 60L * 1000L;
        long now = System.currentTimeMillis();
        return entry.nextWipeAtMillis <= 0L || entry.nextWipeAtMillis - now > avoidMs;
    }

    public static boolean isOverworldGameplayLevel(ServerLevel level) {
        if (level == null) return false;
        String dimensionId = level.dimension().location().toString();
        if ("minecraft:overworld".equalsIgnoreCase(dimensionId)) return true;

        Entry entry = find(level);
        return entry != null && "overworld".equalsIgnoreCase(normalizeType(entry.worldType));
    }

    public static boolean isExplorationLevel(ServerLevel level) {
        return find(level) != null;
    }

    public static ServerLevel pickOverworldGameplayLevel(MinecraftServer server) {
        if (server == null) return null;
        bootstrapState();

        List<ServerLevel> candidates = new ArrayList<>();
        if (server.overworld() != null) candidates.add(server.overworld());

        for (Entry entry : state.worlds) {
            if (!"overworld".equalsIgnoreCase(normalizeType(entry.worldType))) continue;
            if (!isSafeForRtp(server, entry)) continue;
            ServerLevel level = getLevel(server, entry.worldName);
            if (level != null) candidates.add(level);
        }

        if (candidates.isEmpty()) return server.overworld();
        Collections.shuffle(candidates);
        return candidates.get(0);
    }

    public static boolean teleport(ServerPlayer player, int index) {
        bootstrapState();
        if (index < 1 || index > state.worlds.size()) return false;
        Entry entry = state.worlds.get(index - 1);
        if (!player.hasPermissions(4) && !isSafeForRtp(player.server, entry)) {
            player.sendSystemMessage(Component.literal("That exploration world is not safe to enter right now. Status: " + entry.status + ". It may be generating, wiping, unloaded, or too close to its next wipe.").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        ServerLevel level = getLevel(player.server, entry.worldName);
        if (level == null) {
            player.sendSystemMessage(Component.literal("That exploration world is not loaded yet: " + entry.worldName).withStyle(ChatFormatting.RED));
            return false;
        }
        SafeTeleportManager.teleport(player, level, 0.5D, ExplorationWorldConfig.get().spawnY, 0.5D, player.getYRot(), player.getXRot());
        return true;
    }

    public static boolean teleport(ServerPlayer player, String type, int localIndex) {
        bootstrapState();
        if (type == null) return false;
        for (Entry entry : state.worlds) {
            if (entry.localIndex == localIndex && type.equalsIgnoreCase(entry.worldType)) {
                return teleport(player, entry.index);
            }
        }
        return false;
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
                    SafeTeleportManager.teleportNoBack(player, server.overworld(), server.overworld().getSharedSpawnPos().getX() + 0.5D, server.overworld().getSharedSpawnPos().getY(), server.overworld().getSharedSpawnPos().getZ() + 0.5D, player.getYRot(), player.getXRot());
                    player.sendSystemMessage(Component.literal("Exploration world is wiping. You were moved to spawn.").withStyle(ChatFormatting.YELLOW));
                }
            }
        }

        boolean commandsOk = true;
        if (ExplorationWorldConfig.get().runWorldCommands) {
            for (String command : ExplorationWorldConfig.get().deleteCommands) commandsOk &= run(server, apply(command, entry));
            for (String command : ExplorationWorldConfig.get().createCommands) commandsOk &= run(server, apply(command, entry));
            for (String command : ExplorationWorldConfig.get().chunkyPregenerationCommands) commandsOk &= run(server, apply(command, entry));
        }

        entry.status = commandsOk ? (ExplorationWorldConfig.get().requirePregenerationBeforeEntry ? "GENERATING" : "READY") : "PENDING";
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
            SafeTeleportManager.teleportNoBack(player, player.serverLevel(), x + 0.5D, player.getY(), z + 0.5D, player.getYRot(), player.getXRot());
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
        List<Entry> rebuilt = new ArrayList<>();
        int globalIndex = 1;
        globalIndex = ensureGroup(rebuilt, globalIndex, cfg.worldCount, cfg.worldPrefix, "overworld", now, cfg);
        globalIndex = ensureGroup(rebuilt, globalIndex, cfg.netherWorldCount, cfg.netherWorldPrefix, "nether", now, cfg);
        ensureGroup(rebuilt, globalIndex, cfg.endWorldCount, cfg.endWorldPrefix, "end", now, cfg);
        state.worlds = rebuilt;
    }

    private static int ensureGroup(List<Entry> rebuilt, int globalIndex, int count, String prefix, String type, long now, ExplorationWorldConfig.Data cfg) {
        for (int local = 1; local <= count; local++) {
            String worldName = prefix + "_" + local;
            Entry entry = findExisting(worldName);
            if (entry == null) {
                entry = new Entry();
                entry.worldName = worldName;
                entry.status = cfg.requirePregenerationBeforeEntry ? "PENDING" : "READY";
                entry.lastWipeAtMillis = 0L;
                entry.nextWipeAtMillis = now + ((long) (globalIndex - 1) * cfg.staggerHours * 60L * 60L * 1000L);
            }
            entry.index = globalIndex;
            entry.localIndex = local;
            entry.worldType = type;
            rebuilt.add(entry);
            globalIndex++;
        }
        return globalIndex;
    }

    private static Entry findExisting(String worldName) {
        if (worldName == null || state.worlds == null) return null;
        for (Entry entry : state.worlds) {
            if (entry != null && worldName.equalsIgnoreCase(entry.worldName)) return entry;
        }
        return null;
    }

    private static ServerLevel getLevel(MinecraftServer server, String worldName) {
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        } catch (Exception ignored) { return null; }
    }

    private static String apply(String command, Entry entry) {
        if (command == null) return "";
        String world = entry.worldName == null ? "" : entry.worldName;
        String worldId = world.contains(":") ? world.substring(world.indexOf(':') + 1) : world;
        return command
                .replace("{world}", world)
                .replace("{world_id}", worldId)
                .replace("{index}", Integer.toString(entry.index))
                .replace("{border_radius}", Integer.toString(ExplorationWorldConfig.get().borderRadius))
                .replace("{status}", entry.status == null ? "" : entry.status.toLowerCase(Locale.ROOT))
                .replace("{type}", entry.worldType == null ? "overworld" : entry.worldType.toLowerCase(Locale.ROOT))
                .replace("{local_index}", Integer.toString(entry.localIndex));
    }

    private static boolean run(MinecraftServer server, String command) {
        if (server == null || command == null || command.isBlank()) return true;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), clean);
            System.out.println("[ChampUtils] Ran exploration world command: /" + clean);
            return true;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Exploration world command failed: /" + clean);
            e.printStackTrace();
            return false;
        }
    }

    public static final class RtpTarget {
        public final Entry entry;
        public final ServerLevel level;

        private RtpTarget(Entry entry, ServerLevel level) {
            this.entry = entry;
            this.level = level;
        }
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
        public String worldType = "overworld";
        public int localIndex;
    }
}
