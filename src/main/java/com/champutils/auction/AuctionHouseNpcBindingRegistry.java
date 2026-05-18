package com.champutils.auction;

import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.UUID;

public final class AuctionHouseNpcBindingRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/auction_npc_binding.json");
    private static Binding binding = null;

    private AuctionHouseNpcBindingRegistry() {}

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
        Binding binding;
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            binding = null;
            if (!FILE.exists()) {
                save();
                return;
            }
            try (FileReader reader = new FileReader(FILE)) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
                binding = data == null ? null : data.binding;
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
            data.binding = binding;
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void bind(NPCEntity npc) {
        if (npc == null) return;
        binding = new Binding(npc.getUUID(), npc.level().dimension().location());
        save();
    }

    public static void unbind() {
        binding = null;
        save();
    }

    public static boolean isBoundNpc(Entity entity) {
        if (entity == null || binding == null) return false;
        UUID uuid = binding.uuid();
        if (uuid == null || !uuid.equals(entity.getUUID())) return false;
        String world = entity.level().dimension().location().toString();
        return binding.world == null || binding.world.isBlank() || binding.world.equals(world);
    }

    public static Binding getBinding() {
        return binding;
    }
}
