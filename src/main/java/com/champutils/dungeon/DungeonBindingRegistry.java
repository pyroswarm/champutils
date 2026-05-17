package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonBindingRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/dungeon_bindings.json");
    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();

    private DungeonBindingRegistry() {}

    public static final class Binding {
        public String dungeonId;
        public String npcUuid;
        public String world;

        public Binding() {}

        public Binding(String dungeonId, UUID npcUuid, ResourceLocation world) {
            this.dungeonId = dungeonId;
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
        Map<String, Binding> bindings = new LinkedHashMap<>();
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            BINDINGS.clear();
            if (!FILE.exists()) {
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
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

    public static void bind(String dungeonId, Entity npc) {
        if (dungeonId == null || dungeonId.isBlank() || npc == null) return;
        BINDINGS.put(dungeonId, new Binding(dungeonId, npc.getUUID(), npc.level().dimension().location()));
        save();
    }

    public static boolean unbind(String dungeonId) {
        if (dungeonId == null || dungeonId.isBlank()) return false;
        boolean removed = BINDINGS.remove(dungeonId) != null;
        if (removed) save();
        return removed;
    }

    public static Binding get(String dungeonId) {
        return BINDINGS.get(dungeonId);
    }

    public static Map<String, Binding> getAll() {
        return new LinkedHashMap<>(BINDINGS);
    }

    public static String getDungeonIdForNpc(UUID npcUuid) {
        if (npcUuid == null) return null;
        for (Map.Entry<String, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            UUID bound = binding == null ? null : binding.uuid();
            if (npcUuid.equals(bound)) return entry.getKey();
        }
        return null;
    }

    public static boolean isBoundNpc(UUID npcUuid) {
        return getDungeonIdForNpc(npcUuid) != null;
    }

    public static Entity findBoundNpc(MinecraftServer server, String dungeonId) {
        Binding binding = get(dungeonId);
        if (server == null || binding == null) return null;
        UUID uuid = binding.uuid();
        if (uuid == null) return null;

        try {
            if (binding.world != null && !binding.world.isBlank()) {
                ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(binding.world)
                ));
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
