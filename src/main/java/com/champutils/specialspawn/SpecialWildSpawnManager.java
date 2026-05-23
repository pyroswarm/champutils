package com.champutils.specialspawn;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public final class SpecialWildSpawnManager {
    private static final Random RANDOM = new Random();
    private static int ticksUntilCheck = 200;
    private static final Set<UUID> tracked = new HashSet<>();

    private SpecialWildSpawnManager() {}

    public static void tick(MinecraftServer server) {
        if (!SpecialWildSpawnConfig.DATA.enabled) return;
        ticksUntilCheck--;
        if (ticksUntilCheck > 0) return;
        ticksUntilCheck = Math.max(20, SpecialWildSpawnConfig.DATA.checkIntervalTicks);
        cleanupTracked(server);
        if (tracked.size() >= Math.max(1, SpecialWildSpawnConfig.DATA.maxAliveSpecialWildPokemon)) return;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || isDisabledDimension(p.serverLevel()));
        if (players.isEmpty()) return;
        Collections.shuffle(players, RANDOM);

        for (ServerPlayer player : players) {
            if (tryRollFor(player, "legendary", SpecialWildSpawnConfig.DATA.legendaryChancePerCheck, SpecialWildSpawnConfig.DATA.legendarySpawns, SpecialWildSpawnConfig.DATA.levelRangeLegendary)) return;
            if (tryRollFor(player, "paradox", SpecialWildSpawnConfig.DATA.paradoxChancePerCheck, SpecialWildSpawnConfig.DATA.paradoxSpawns, SpecialWildSpawnConfig.DATA.levelRangeParadox)) return;
            if (tryRollFor(player, "ultra beast", SpecialWildSpawnConfig.DATA.ultraBeastChancePerCheck, SpecialWildSpawnConfig.DATA.ultraBeastSpawns, SpecialWildSpawnConfig.DATA.levelRangeUltraBeast)) return;
        }
    }

    public static void cleanupTracked(MinecraftServer server) {
        tracked.removeIf(id -> {
            for (ServerLevel level : server.getAllLevels()) {
                Entity e = level.getEntity(id);
                if (e != null && e.isAlive()) return false;
            }
            return true;
        });
    }

    private static boolean tryRollFor(ServerPlayer player, String type, double chance, List<SpecialWildSpawnConfig.SpawnEntry> entries, String levelRange) {
        if (entries == null || entries.isEmpty()) return false;
        if (RANDOM.nextDouble() >= Math.max(0.0D, Math.min(1.0D, chance))) return false;

        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition());
            if (pos == null) continue;
            List<SpecialWildSpawnConfig.SpawnEntry> valid = matchingEntries(level, pos, entries);
            if (valid.isEmpty()) continue;
            SpecialWildSpawnConfig.SpawnEntry picked = pickWeighted(valid);
            if (picked == null) continue;
            int pokemonLevel = pickLevel(levelRange);
            boolean spawned = spawnViaCobblemonCommand(player.getServer(), level, pos, picked.species, pokemonLevel);
            if (spawned) {
                announce(player.getServer(), type, picked.species, level, pos);
                return true;
            }
        }
        return false;
    }

    private static BlockPos randomSpawnPos(ServerLevel level, BlockPos origin) {
        int min = Math.max(8, SpecialWildSpawnConfig.DATA.minDistanceFromPlayer);
        int max = Math.max(min + 1, SpecialWildSpawnConfig.DATA.maxDistanceFromPlayer);
        double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
        int dist = min + RANDOM.nextInt(Math.max(1, max - min));
        int x = origin.getX() + (int)Math.round(Math.cos(angle) * dist);
        int z = origin.getZ() + (int)Math.round(Math.sin(angle) * dist);
        BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, origin.getY(), z));
        if (!level.hasChunk(new ChunkPos(top).x, new ChunkPos(top).z)) return null;
        if (!level.getWorldBorder().isWithinBounds(top)) return null;
        if (!level.getBlockState(top.below()).isSolid()) return null;
        if (!level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir()) return null;
        return top;
    }

    private static List<SpecialWildSpawnConfig.SpawnEntry> matchingEntries(ServerLevel level, BlockPos pos, List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        List<SpecialWildSpawnConfig.SpawnEntry> valid = new ArrayList<>();
        String time = timeName(level);
        for (SpecialWildSpawnConfig.SpawnEntry entry : entries) {
            if (entry == null || entry.species == null || entry.species.isBlank()) continue;
            if (entry.times != null && !entry.times.isEmpty() && entry.times.stream().noneMatch(t -> t != null && t.equalsIgnoreCase(time))) continue;
            if (entry.biomes != null && !entry.biomes.isEmpty() && !matchesAnyBiome(level, pos, entry.biomes)) continue;
            valid.add(entry);
        }
        return valid;
    }

    private static boolean matchesAnyBiome(ServerLevel level, BlockPos pos, List<String> biomes) {
        var holder = level.getBiome(pos);
        ResourceLocation biomeId = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(holder.value());
        String current = biomeId == null ? "" : biomeId.toString();
        for (String raw : biomes) {
            if (raw == null || raw.isBlank()) continue;
            String b = raw.trim();
            try {
                if (b.startsWith("#")) {
                    ResourceLocation tagId = ResourceLocation.parse(b.substring(1));
                    TagKey<Biome> tag = TagKey.create(Registries.BIOME, tagId);
                    if (holder.is(tag)) return true;
                } else {
                    if (!b.contains(":")) b = "minecraft:" + b;
                    if (current.equalsIgnoreCase(b)) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static String timeName(ServerLevel level) {
        long t = level.getDayTime() % 24000L;
        if (t >= 23000 || t < 1000) return "dawn";
        if (t >= 1000 && t < 12000) return "day";
        if (t >= 12000 && t < 14000) return "dusk";
        return "night";
    }

    private static SpecialWildSpawnConfig.SpawnEntry pickWeighted(List<SpecialWildSpawnConfig.SpawnEntry> entries) {
        double total = 0.0D;
        for (SpecialWildSpawnConfig.SpawnEntry e : entries) total += Math.max(0.01D, e.weight);
        double roll = RANDOM.nextDouble() * total;
        for (SpecialWildSpawnConfig.SpawnEntry e : entries) {
            roll -= Math.max(0.01D, e.weight);
            if (roll <= 0) return e;
        }
        return entries.isEmpty() ? null : entries.get(0);
    }

    private static int pickLevel(String range) {
        try {
            String[] p = (range == null ? "50-70" : range).replace(" ", "").split("-");
            int min = Integer.parseInt(p[0]);
            int max = p.length > 1 ? Integer.parseInt(p[1]) : min;
            if (max < min) { int tmp = min; min = max; max = tmp; }
            return Math.max(1, Math.min(100, min + RANDOM.nextInt(max - min + 1)));
        } catch (Exception ignored) { return 60; }
    }

    private static boolean spawnViaCobblemonCommand(MinecraftServer server, ServerLevel level, BlockPos pos, String species, int pokemonLevel) {
        try {
            String clean = sanitize(species);
            CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))
                    .withRotation(Vec2.ZERO)
                    .withPermission(4)
                    .withSuppressedOutput();
            String command = "spawnpokemonat " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + clean + " lvl=" + pokemonLevel;
            server.getCommands().performPrefixedCommand(source, command);
            return true;
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to spawn special wild Pokémon: " + species);
            e.printStackTrace();
            return false;
        }
    }

    private static void announce(MinecraftServer server, String type, String species, ServerLevel level, BlockPos pos) {
        boolean broadcast = type.equals("legendary") ? SpecialWildSpawnConfig.DATA.broadcastLegendarySpawns : SpecialWildSpawnConfig.DATA.broadcastParadoxAndUltraBeastSpawns;
        if (!broadcast) return;
        String name = pretty(species);
        String biome = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value()).toString();
        Component msg = Component.literal("§6A wild " + name + " has appeared! §7(" + biome + ")");
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    private static boolean isDisabledDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        for (String d : SpecialWildSpawnConfig.DATA.disabledDimensions) if (id.equalsIgnoreCase(d)) return true;
        return false;
    }

    private static String sanitize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int c = v.lastIndexOf(':');
        if (c >= 0 && c + 1 < v.length()) v = v.substring(c + 1);
        return v.replace(' ', '_').replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String pretty(String raw) {
        String[] parts = sanitize(raw).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.length() > 1 ? p.substring(1) : "");
        }
        return sb.toString();
    }
}
