package com.champutils.survival;

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
import net.minecraft.world.level.border.WorldBorder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class SurvivalWorldManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/survival_worlds_state.json");
    private static final Set<String> WORLD_CREATE_REQUESTED_THIS_RUNTIME = new HashSet<>();
    private static State state = new State();

    private SurvivalWorldManager() {}

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
            System.err.println("[ChampUtils] Failed to load survival world state.");
            e.printStackTrace();
            state = new State();
            bootstrapState();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(state, writer); }
        catch (Exception e) { System.err.println("[ChampUtils] Failed to save survival world state."); e.printStackTrace(); }
    }

    public static void ensureStartupWorlds(MinecraftServer server) {
        // Intentionally no-op. Configured survival Multiworlds are no longer auto-created/loaded
        // on restart or first player join. Manually create/load worlds when you want them available.
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !SurvivalWorldConfig.get().enabled) return;
        if (server.getTickCount() % 10 == 0) enforceBorders(server);
        // Do not periodically auto-create/load missing survival Multiworlds.
    }

    public static RtpTarget pickRtpTarget(MinecraftServer server, String type) {
        if (server == null || !SurvivalWorldConfig.get().enabled) return null;
        bootstrapState();
        String wantedType = normalizeType(type);
        List<Entry> candidates = new ArrayList<>();
        for (Entry entry : state.worlds) {
            if (!wantedType.equals(normalizeType(entry.worldType))) continue;
            if (!entry.activeForRtp) continue;
            ServerLevel level = getLevel(server, entry.worldName);
            if (level != null) candidates.add(entry);
        }
        if (candidates.isEmpty()) return null;
        Collections.shuffle(candidates);
        Entry picked = candidates.get(0);
        ServerLevel level = getLevel(server, picked.worldName);
        return level == null ? null : new RtpTarget(picked, level);
    }

    public static boolean isSurvivalLevel(ServerLevel level) {
        return find(level) != null;
    }

    public static Entry find(ServerLevel level) {
        if (level == null) return null;
        String name = level.dimension().location().toString();
        for (Entry entry : entries()) if (entry.worldName.equalsIgnoreCase(name)) return entry;
        return null;
    }

    public static List<Entry> entries() {
        bootstrapState();
        return state.worlds;
    }

    public static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "overworld";
        String clean = type.trim().toLowerCase(Locale.ROOT);
        if (clean.equals("survival") || clean.equals("overworld") || clean.equals("world") || clean.equals("normal")) return "overworld";
        if (clean.equals("nether")) return "nether";
        if (clean.equals("end") || clean.equals("the_end")) return "end";
        return clean;
    }

    private static void ensureWorlds(MinecraftServer server, boolean forceCheck) {
        if (server == null || !SurvivalWorldConfig.get().enabled) return;
        bootstrapState();
        if (!server.isSameThread()) {
            server.execute(() -> ensureWorlds(server, forceCheck));
            return;
        }

        boolean changed = false;
        for (Entry entry : state.worlds) {
            if (entry == null || entry.worldName == null || entry.worldName.isBlank()) continue;
            if (!entry.activeForRtp) {
                continue;
            }
            ServerLevel level = getLevel(server, entry.worldName);
            if (level != null) {
                entry.status = "READY";
                applyBorder(level);
                changed = true;
                continue;
            }
            if (!forceCheck && !"PENDING".equalsIgnoreCase(entry.status)) continue;
            if (!SurvivalWorldConfig.get().runWorldCommands) continue;

            String requestKey = entry.worldName.toLowerCase(Locale.ROOT);
            if (!WORLD_CREATE_REQUESTED_THIS_RUNTIME.add(requestKey) && !forceCheck) continue;

            System.out.println("[ChampUtils] Survival world is missing/unloaded. Requesting Multiworld create/load for " + entry.worldName + " (" + entry.worldType + ").");
            boolean commandsOk = true;
            for (String command : createCommandsFor(entry)) commandsOk &= run(server, apply(command, entry));
            ServerLevel loaded = getLevel(server, entry.worldName);
            if (loaded != null) {
                applyBorder(loaded);
                for (String command : SurvivalWorldConfig.get().postCreateCommands) commandsOk &= runInWorld(server, loaded, apply(command, entry));
            } else {
                commandsOk = false;
            }
            entry.status = commandsOk ? "READY" : "PENDING";
            if (!commandsOk) {
                WORLD_CREATE_REQUESTED_THIS_RUNTIME.remove(requestKey);
            }
            changed = true;
        }
        if (changed) save();
    }

    private static List<String> createCommandsFor(Entry entry) {
        String type = normalizeType(entry == null ? null : entry.worldType);
        SurvivalWorldConfig.Data cfg = SurvivalWorldConfig.get();
        if ("nether".equals(type)) return new ArrayList<>(cfg.netherCreateCommands);
        if ("end".equals(type)) return new ArrayList<>(cfg.endCreateCommands);
        return new ArrayList<>(cfg.overworldCreateCommands);
    }

    private static void enforceBorders(MinecraftServer server) {
        int radius = SurvivalWorldConfig.get().borderRadius;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Entry entry = find(player.serverLevel());
            if (entry == null || player.hasPermissions(4)) continue;
            BlockPos pos = player.blockPosition();
            if (Math.abs(pos.getX()) <= radius && Math.abs(pos.getZ()) <= radius) continue;
            int x = Math.max(-radius + 2, Math.min(radius - 2, pos.getX()));
            int z = Math.max(-radius + 2, Math.min(radius - 2, pos.getZ()));
            SafeTeleportManager.teleportNoBack(player, player.serverLevel(), x + 0.5D, player.getY(), z + 0.5D, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("You cannot leave the 10000-block survival border.").withStyle(ChatFormatting.RED));
        }
    }

    private static void applyBorder(ServerLevel level) {
        if (level == null) return;
        int radius = SurvivalWorldConfig.get().borderRadius;
        WorldBorder border = level.getWorldBorder();
        border.setCenter(0.0D, 0.0D);
        border.setSize(radius * 2.0D);
    }

    private static void bootstrapState() {
        SurvivalWorldConfig.Data cfg = SurvivalWorldConfig.get();
        if (state.worlds == null) state.worlds = new ArrayList<>();
        List<Entry> rebuilt = new ArrayList<>();
        int globalIndex = 1;
        globalIndex = ensureGroup(rebuilt, globalIndex, cfg.overworldCount, cfg.overworldPrefix, "overworld");
        globalIndex = ensureGroup(rebuilt, globalIndex, cfg.netherWorldCount, cfg.netherPrefix, "nether");
        ensureGroup(rebuilt, globalIndex, cfg.endWorldCount, cfg.endPrefix, "end");
        state.worlds = rebuilt;
    }

    private static int ensureGroup(List<Entry> rebuilt, int globalIndex, int count, String prefix, String type) {
        for (int local = 1; local <= count; local++) {
            String worldName = prefix + "_" + local;
            Entry entry = findExisting(worldName);
            if (entry == null) {
                entry = new Entry();
                entry.worldName = worldName;
                entry.status = "PENDING";
                entry.activeForRtp = local == 1;
            }
            if (local == 1) {
                entry.activeForRtp = true;
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
        for (Entry entry : state.worlds) if (entry != null && worldName.equalsIgnoreCase(entry.worldName)) return entry;
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
        int radius = SurvivalWorldConfig.get().borderRadius;
        return command
                .replace("{world}", world)
                .replace("{world_id}", worldId)
                .replace("{index}", Integer.toString(entry.index))
                .replace("{local_index}", Integer.toString(entry.localIndex))
                .replace("{type}", entry.worldType == null ? "overworld" : entry.worldType.toLowerCase(Locale.ROOT))
                .replace("{border_radius}", Integer.toString(radius))
                .replace("{border_diameter}", Integer.toString(radius * 2));
    }

    private static String sanitizeMultiworldCommand(String clean) {
        if (clean == null) return "";
        return clean
                .replace("mw create multiworld:", "mw create ")
                .replace("mw load multiworld:", "mw load ")
                .replace("mw unload multiworld:", "mw unload ")
                .replace("mw delete multiworld:", "mw delete ");
    }

    private static boolean needsPlayerCommandSource(String clean) {
        if (clean == null) return false;
        String lower = clean.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("mw ") || lower.startsWith("multiworld ");
    }

    private static boolean run(MinecraftServer server, String command) {
        return runInWorld(server, null, command);
    }

    private static boolean runInWorld(MinecraftServer server, ServerLevel level, String command) {
        if (server == null || command == null || command.isBlank()) return true;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        clean = sanitizeMultiworldCommand(clean);
        try {
            if (needsPlayerCommandSource(clean)) {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    System.out.println("[ChampUtils] Survival world command needs an online player source, so it will retry shortly: /" + clean);
                    return false;
                }
                ServerPlayer player = players.get(0);
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), clean);
            } else if (level != null) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput(), clean);
            } else {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), clean);
            }
            System.out.println("[ChampUtils] Ran survival world command: /" + clean);
            return true;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Survival world command failed: /" + clean);
            e.printStackTrace();
            return false;
        }
    }


    public static boolean setActive(String worldName, boolean active) {
        bootstrapState();
        Entry entry = findExisting(worldName);
        if (entry == null) return false;
        entry.activeForRtp = active;
        if (active && (entry.status == null || entry.status.isBlank() || "LOCKED".equalsIgnoreCase(entry.status))) {
            entry.status = "PENDING";
        }
        save();
        return true;
    }

    public static boolean isActive(String worldName) {
        bootstrapState();
        Entry entry = findExisting(worldName);
        return entry != null && entry.activeForRtp;
    }

    public static final class RtpTarget {
        public final Entry entry;
        public final ServerLevel level;
        private RtpTarget(Entry entry, ServerLevel level) { this.entry = entry; this.level = level; }
    }

    public static final class State { public List<Entry> worlds = new ArrayList<>(); }

    public static final class Entry {
        public int index;
        public String worldName;
        public String status;
        public String worldType = "overworld";
        public int localIndex;
        public boolean activeForRtp;
    }
}
