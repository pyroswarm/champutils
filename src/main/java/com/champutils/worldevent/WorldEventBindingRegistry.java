package com.champutils.worldevent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores which existing Cobblemon NPC is used for each world event.
 * This replaces world-event NPC spawning completely.
 */
public final class WorldEventBindingRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/world_event_bindings.json");

    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();

    private WorldEventBindingRegistry() {}

    public static final class Binding {
        public String eventId;
        public String npcUuid;
        public String world;

        public Binding() {}

        public Binding(String eventId, UUID npcUuid, ResourceLocation world) {
            this.eventId = eventId;
            this.npcUuid = npcUuid == null ? "" : npcUuid.toString();
            this.world = world == null ? "minecraft:overworld" : world.toString();
        }

        public UUID uuid() {
            try {
                return UUID.fromString(npcUuid);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static final class SaveData {
        Map<String, Binding> bindings = new HashMap<>();
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            if (!FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
                BINDINGS.clear();
                if (data != null && data.bindings != null) {
                    BINDINGS.putAll(data.bindings);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            SaveData data = new SaveData();
            data.bindings.putAll(BINDINGS);

            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void bind(String eventId, Entity npc) {
        if (eventId == null || eventId.isBlank() || npc == null) return;
        BINDINGS.put(
                eventId,
                new Binding(eventId, npc.getUUID(), npc.level().dimension().location())
        );
        save();
    }

    public static boolean unbind(String eventId) {
        if (eventId == null || eventId.isBlank()) return false;
        boolean removed = BINDINGS.remove(eventId) != null;
        if (removed) save();
        return removed;
    }

    public static Binding get(String eventId) {
        return BINDINGS.get(eventId);
    }

    public static Map<String, Binding> getAll() {
        return new LinkedHashMap<>(BINDINGS);
    }


    /**
     * True when this NPC UUID is bound to any world event, active or inactive.
     * Gym systems use this to completely ignore NPCs reserved for world events
     * so gym caps/rewards cannot leak into event bosses.
     */
    public static boolean isBoundNpc(UUID npcUuid) {
        if (npcUuid == null) return false;
        for (Binding binding : BINDINGS.values()) {
            UUID bound = binding == null ? null : binding.uuid();
            if (npcUuid.equals(bound)) return true;
        }
        return false;
    }

    /**
     * Returns the event id this NPC is bound to, or null if it is not bound.
     */
    public static String getEventIdForNpc(UUID npcUuid) {
        if (npcUuid == null) return null;
        for (Map.Entry<String, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            UUID bound = binding == null ? null : binding.uuid();
            if (npcUuid.equals(bound)) return entry.getKey();
        }
        return null;
    }

    public static Entity findBoundNpc(MinecraftServer server, String eventId) {
        Binding binding = get(eventId);
        if (server == null || binding == null) return null;

        UUID uuid = binding.uuid();
        if (uuid == null) return null;

        // Prefer the recorded world, but also scan all worlds as a fallback in case the NPC moved worlds.
        try {
            if (binding.world != null && !binding.world.isBlank()) {
                ServerLevel level = server.getLevel(
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                ResourceLocation.parse(binding.world)
                        )
                );
                if (level != null) {
                    Entity entity = level.getEntity(uuid);
                    if (entity != null) return entity;
                }
            }
        } catch (Exception ignored) {}

        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }

        return null;
    }
}
