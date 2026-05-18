package com.champutils.menu;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class MenuNpcBindingRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/menu_npc_bindings.json");
    private static final Map<String, Binding> BINDINGS = new LinkedHashMap<>();

    private MenuNpcBindingRegistry() {}

    public static final class Binding {
        public String menu;
        public String npcUuid;
        public String world;

        public Binding() {}

        public Binding(String menu, UUID npcUuid, ResourceLocation world) {
            this.menu = normalize(menu);
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
                    data.bindings.forEach((menu, binding) -> {
                        String normalized = normalize(menu);
                        if (isValidMenu(normalized) && binding != null) {
                            binding.menu = normalized;
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

    public static void bind(String menu, NPCEntity npc) {
        String normalized = normalize(menu);
        if (!isValidMenu(normalized) || npc == null) return;
        BINDINGS.put(normalized, new Binding(normalized, npc.getUUID(), npc.level().dimension().location()));
        save();
    }

    public static boolean unbind(String menu) {
        String normalized = normalize(menu);
        boolean removed = BINDINGS.remove(normalized) != null;
        if (removed) save();
        return removed;
    }

    public static String getBoundMenu(Entity entity) {
        if (entity == null) return null;
        UUID entityUuid = entity.getUUID();
        String world = entity.level().dimension().location().toString();

        for (Map.Entry<String, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            if (binding == null) continue;

            UUID boundUuid = binding.uuid();
            if (boundUuid == null || !boundUuid.equals(entityUuid)) continue;
            if (binding.world != null && !binding.world.isBlank() && !binding.world.equals(world)) continue;

            return entry.getKey();
        }

        return null;
    }

    public static Map<String, Binding> getAll() {
        return Collections.unmodifiableMap(BINDINGS);
    }

    public static boolean isValidMenu(String menu) {
        return switch (normalize(menu)) {
            case "items", "dungeons", "professions", "seasons", "auction" -> true;
            default -> false;
        };
    }

    public static String validMenusText() {
        return "items, dungeons, professions, seasons, auction";
    }

    public static String normalize(String menu) {
        return menu == null ? "" : menu.trim().toLowerCase();
    }
}
