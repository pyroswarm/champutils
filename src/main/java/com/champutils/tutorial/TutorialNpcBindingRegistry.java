package com.champutils.tutorial;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TutorialNpcBindingRegistry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/tutorial_npc_bindings.json");
    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();

    private TutorialNpcBindingRegistry() {}

    public static final class Binding {
        public String npcUuid;
        public String world;

        public Binding() {}

        public Binding(UUID npcUuid, ResourceLocation world) {
            this.npcUuid = npcUuid == null ? "" : npcUuid.toString();
            this.world = world == null ? "minecraft:overworld" : world.toString();
        }

        public UUID uuid() {
            try { return UUID.fromString(npcUuid); }
            catch (Exception e) { return null; }
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
                    data.bindings.forEach((id, binding) -> {
                        String normalized = normalize(id);
                        if (TutorialManager.isValidNpcId(normalized) && binding != null && binding.uuid() != null) {
                            BINDINGS.put(normalized, binding);
                        }
                    });
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

    public static void bind(String npcId, NPCEntity npc) {
        if (npc == null) return;
        String normalized = normalize(npcId);
        if (!TutorialManager.isValidNpcId(normalized)) return;
        BINDINGS.put(normalized, new Binding(npc.getUUID(), npc.level().dimension().location()));
        save();
    }

    public static boolean unbind(String npcId) {
        String normalized = normalize(npcId);
        boolean removed = BINDINGS.remove(normalized) != null;
        if (removed) save();
        return removed;
    }

    public static String getTutorialId(Entity entity) {
        if (entity == null) return null;
        UUID entityUuid = entity.getUUID();
        String world = entity.level().dimension().location().toString();
        for (Map.Entry<String, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            UUID uuid = binding == null ? null : binding.uuid();
            if (uuid == null || !uuid.equals(entityUuid)) continue;
            if (binding.world == null || binding.world.isBlank() || binding.world.equals(world)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static Map<String, Binding> snapshot() {
        return Map.copyOf(BINDINGS);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
