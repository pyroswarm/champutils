package com.champutils.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class DungeonNativeCrateRegistry {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/dungeon_native_crates.json");
    private static final Map<String, CrateBinding> CRATES = new LinkedHashMap<>();
    private static final String HOLOGRAM_TAG = "champutils_dungeon_crate_hologram";

    private DungeonNativeCrateRegistry() {
    }

    public enum CrateType {
        NORMAL,
        POKEMON;

        public static CrateType parse(String text) {
            if (text == null) return NORMAL;
            String normalized = text.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals("POKEMON") || normalized.equals("POKEMONCRATE") || normalized.equals("POKE")) {
                return POKEMON;
            }
            return NORMAL;
        }

        public String display() {
            return this == POKEMON ? "Pokemon" : "Loot";
        }
    }

    public static final class CrateBinding {
        public String rarity = DungeonRarity.COMMON.name();
        public String type = CrateType.NORMAL.name();
        public String world = "multiworld:spawn";
        public int x;
        public int y;
        public int z;
        public String name = "";

        public CrateBinding() {
        }

        public CrateBinding(DungeonRarity rarity, CrateType type, Level level, BlockPos pos) {
            this.rarity = rarity.name();
            this.type = type.name();
            this.world = level.dimension().location().toString();
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
            this.name = defaultName(rarity, type);
        }

        public DungeonRarity rarity() {
            return DungeonRarity.parse(rarity);
        }

        public CrateType type() {
            return CrateType.parse(type);
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }
    }

    private static final class SaveData {
        Map<String, CrateBinding> crates = new LinkedHashMap<>();
    }

    public static void load() {
        try {
            File dir = FILE.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            CRATES.clear();
            if (!FILE.exists()) {
                save();
                return;
            }

            try (FileReader reader = new FileReader(FILE)) {
                SaveData data = GSON.fromJson(reader, SaveData.class);
                if (data != null && data.crates != null) {
                    CRATES.putAll(data.crates);
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
            data.crates.putAll(CRATES);
            try (FileWriter writer = new FileWriter(FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CrateBinding bind(DungeonRarity rarity, CrateType type, ServerLevel level, BlockPos pos) {
        if (rarity == null) rarity = DungeonRarity.COMMON;
        if (type == null) type = CrateType.NORMAL;
        if (level == null || pos == null) return null;

        CrateBinding binding = new CrateBinding(rarity, type, level, pos);
        CRATES.put(key(level.dimension().location(), pos), binding);
        save();
        spawnHologram(level, binding);
        return binding;
    }

    public static boolean unbind(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        CrateBinding removed = CRATES.remove(key(level.dimension().location(), pos));
        if (removed != null) {
            removeHolograms(level, pos);
            save();
            return true;
        }
        return false;
    }

    public static CrateBinding getAt(Level level, BlockPos pos) {
        if (level == null || pos == null) return null;
        return CRATES.get(key(level.dimension().location(), pos));
    }

    public static Map<String, CrateBinding> getAll() {
        return new LinkedHashMap<>(CRATES);
    }

    public static int respawnAllHolograms(MinecraftServer server) {
        if (server == null) return 0;
        int count = 0;
        for (CrateBinding binding : CRATES.values()) {
            ServerLevel level = getLevel(server, binding.world);
            if (level == null) continue;
            spawnHologram(level, binding);
            count++;
        }
        return count;
    }

    private static void spawnHologram(ServerLevel level, CrateBinding binding) {
        if (level == null || binding == null) return;
        BlockPos pos = binding.pos();
        removeHolograms(level, pos);

        ArmorStand stand = new ArmorStand(level, pos.getX() + 0.5D, pos.getY() - 1.15D, pos.getZ() + 0.5D);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setSilent(true);
        stand.setInvulnerable(true);
        // Marker is private in the 1.21.1 mappings used by this project, so keep the stand invisible/no-gravity instead.
        stand.setCustomName(Component.literal(binding.name == null || binding.name.isBlank() ? defaultName(binding.rarity(), binding.type()) : binding.name)
                .withStyle(binding.type() == CrateType.POKEMON ? ChatFormatting.LIGHT_PURPLE : colorFor(binding.rarity()), ChatFormatting.BOLD));
        stand.setCustomNameVisible(true);
        stand.addTag(HOLOGRAM_TAG);
        stand.addTag("champutils_crate_" + key(level.dimension().location(), pos).replace(':', '_').replace(',', '_'));
        level.addFreshEntity(stand);
    }

    private static void removeHolograms(ServerLevel level, BlockPos pos) {
        AABB box = new AABB(
                pos.getX() - 1.0D,
                pos.getY(),
                pos.getZ() - 1.0D,
                pos.getX() + 2.0D,
                pos.getY() + 3.0D,
                pos.getZ() + 2.0D
        );
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box, entity -> entity.getTags().contains(HOLOGRAM_TAG))) {
            stand.discard();
        }
    }

    private static ServerLevel getLevel(MinecraftServer server, String world) {
        try {
            return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(world)));
        } catch (Exception e) {
            return null;
        }
    }

    private static String key(ResourceLocation world, BlockPos pos) {
        return world + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String defaultName(DungeonRarity rarity, CrateType type) {
        String base = nice(rarity == null ? "COMMON" : rarity.name());
        return type == CrateType.POKEMON ? base + " Pokemon Crate" : base + " Loot Crate";
    }

    private static ChatFormatting colorFor(DungeonRarity rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        switch (rarity) {
            case UNCOMMON: return ChatFormatting.GREEN;
            case RARE: return ChatFormatting.BLUE;
            case EPIC: return ChatFormatting.DARK_PURPLE;
            case LEGENDARY: return ChatFormatting.GOLD;
            case MYTHIC: return ChatFormatting.LIGHT_PURPLE;
            case COMMON:
            default: return ChatFormatting.WHITE;
        }
    }

    private static String nice(String raw) {
        if (raw == null || raw.isBlank()) return "Common";
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = lower.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
