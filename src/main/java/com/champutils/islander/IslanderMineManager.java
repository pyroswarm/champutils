package com.champutils.islander;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Drop-in manager for private Islander profile mines.
 * Wire this to your existing profile service, SQL connection provider, shared reset service, and Territory Steward menu.
 */
public final class IslanderMineManager {
    private static final String ISLANDER_WORLD = "multiworld:islander"; // change only if your islander dimension key differs
    private static final int MINE_Y = -32;
    private static final int MINE_RADIUS = 22;
    private static final int MINE_HEIGHT = 18;
    private static final int SPACING = 512;
    private static final ZoneId RESET_ZONE = ZoneId.systemDefault();

    private IslanderMineManager() {}

    public static void enterMine(ServerPlayer player, IslanderProfile profile, Connection sql) {
        if (profile == null || !profile.isIslander()) {
            player.sendSystemMessage(Component.literal("Only Islander profiles can use private mines."));
            return;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel level = getLevel(server, ISLANDER_WORLD);
        if (level == null) {
            player.sendSystemMessage(Component.literal("The Islander world is not loaded."));
            return;
        }

        try {
            MineRecord mine = getOrCreateMine(sql, profile.id());
            String resetKey = currentResetKey();
            if (!resetKey.equals(mine.lastRegenResetKey())) {
                regenerateMine(level, mine.center());
                updateLastReset(sql, profile.id(), resetKey);
            }

            BlockPos spawn = mine.center().offset(0, 2, 0);
            makeSafePad(level, spawn);
            player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.sendSystemMessage(Component.literal("Entering your private Islander mine."));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Could not open your Islander mine. Check server logs."));
            e.printStackTrace();
        }
    }

    public static boolean canEnterMine(ServerPlayer player, IslanderProfile activeProfile, UUID mineOwnerProfileId) {
        return activeProfile != null && activeProfile.isIslander() && activeProfile.id().equals(mineOwnerProfileId);
    }

    public static void regenerateMine(ServerLevel level, BlockPos center) {
        Random random = new Random(center.asLong() ^ 0xC0BB1EL);
        int r = MINE_RADIUS;

        for (int x = -r; x <= r; x++) {
            for (int y = -2; y <= MINE_HEIGHT; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    double dist = Math.sqrt((x * x) + (z * z));

                    if (dist > r || y == -2 || y == MINE_HEIGHT || dist > r - 1) {
                        level.setBlock(pos, Blocks.DEEPSLATE.defaultBlockState(), 3);
                    } else {
                        BlockState state = randomOre(random, y);
                        level.setBlock(pos, state, 3);
                    }
                }
            }
        }

        makeSafePad(level, center.offset(0, 2, 0));
    }

    private static void makeSafePad(ServerLevel level, BlockPos spawn) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(spawn.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                level.setBlock(spawn.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(spawn.offset(x, 1, z), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(spawn.offset(x, 2, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static BlockState randomOre(Random random, int localY) {
        int roll = random.nextInt(10_000);
        if (roll < 18) return Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState();
        if (roll < 38) return Blocks.DEEPSLATE_EMERALD_ORE.defaultBlockState();
        if (roll < 90) return Blocks.DEEPSLATE_LAPIS_ORE.defaultBlockState();
        if (roll < 170) return Blocks.DEEPSLATE_REDSTONE_ORE.defaultBlockState();
        if (roll < 270) return Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState();
        if (roll < 520) return Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
        if (roll < 820) return Blocks.DEEPSLATE_COPPER_ORE.defaultBlockState();
        if (roll < 1220) return Blocks.DEEPSLATE_COAL_ORE.defaultBlockState();
        // Add Cobblemon stone ores here after confirming exact block registry IDs in your installed build.
        return localY < 5 ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static MineRecord getOrCreateMine(Connection sql, UUID profileId) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement("SELECT world_key, center_x, center_y, center_z, last_regen_reset_key FROM public.islander_mines WHERE profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new MineRecord(profileId, rs.getString(1), new BlockPos(rs.getInt(2), rs.getInt(3), rs.getInt(4)), rs.getString(5));
                }
            }
        }

        BlockPos center = deterministicCenter(profileId);
        try (PreparedStatement ps = sql.prepareStatement("INSERT INTO public.islander_mines(profile_id, world_key, center_x, center_y, center_z, last_regen_reset_key) VALUES (?, ?, ?, ?, ?, NULL)")) {
            ps.setObject(1, profileId);
            ps.setString(2, ISLANDER_WORLD);
            ps.setInt(3, center.getX());
            ps.setInt(4, center.getY());
            ps.setInt(5, center.getZ());
            ps.executeUpdate();
        }
        return new MineRecord(profileId, ISLANDER_WORLD, center, null);
    }

    private static BlockPos deterministicCenter(UUID profileId) {
        byte[] bytes = profileId.toString().getBytes(StandardCharsets.UTF_8);
        int hash = Arrays.hashCode(bytes);
        int gridX = Math.floorMod(hash, 10_000) - 5_000;
        int gridZ = Math.floorMod(hash / 10_000, 10_000) - 5_000;
        return new BlockPos(gridX * SPACING, MINE_Y, gridZ * SPACING);
    }

    private static void updateLastReset(Connection sql, UUID profileId, String resetKey) throws SQLException {
        try (PreparedStatement ps = sql.prepareStatement("UPDATE public.islander_mines SET last_regen_reset_key = ?, updated_at = now() WHERE profile_id = ?")) {
            ps.setString(1, resetKey);
            ps.setObject(2, profileId);
            ps.executeUpdate();
        }
    }

    private static String currentResetKey() {
        // Replace this with your shared 2 AM reset service if already implemented.
        return LocalDate.now(RESET_ZONE).toString();
    }

    private static ServerLevel getLevel(MinecraftServer server, String key) {
        ResourceKey<Level> levelKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(key));
        return server.getLevel(levelKey);
    }

    public record MineRecord(UUID profileId, String worldKey, BlockPos center, String lastRegenResetKey) {}

    public interface IslanderProfile {
        UUID id();
        boolean isIslander();
    }
}
