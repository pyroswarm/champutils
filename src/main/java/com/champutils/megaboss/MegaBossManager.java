package com.champutils.megaboss;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MegaBossManager {
    public static final String BOSS_TAG = "champutils_mega_boss";
    public static final String BOSS_RARITY_PREFIX = "champutils_mega_boss_rarity_";
    public static final String BOSS_STONE_PREFIX = "champutils_mega_boss_stone_";
    private static final String EXPIRES_PREFIX = "champutils_mega_boss_expires_";
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> TRACKED = new ConcurrentHashMap<>();
    private static int ticksUntilCheck = 1200;
    private static int ticksUntilCleanup = 200;

    private MegaBossManager() {}

    public static void tick(MinecraftServer server) {
        if (!MegaBossConfig.DATA.enabled) return;
        ticksUntilCleanup--;
        if (ticksUntilCleanup <= 0) {
            ticksUntilCleanup = 200;
            cleanup(server);
        }
        ticksUntilCheck--;
        if (ticksUntilCheck > 0) return;
        ticksUntilCheck = Math.max(20, MegaBossConfig.DATA.checkIntervalTicks);
        cleanup(server);
        int globalSafetyCap = Math.max(1, MegaBossConfig.DATA.maxAliveBosses);
        if (TRACKED.size() >= globalSafetyCap) return;

        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.removeIf(p -> p == null || p.isSpectator() || isDisabledDimension(p.serverLevel()));
        if (players.isEmpty()) return;

        Collections.shuffle(players, RANDOM);
        int spawnedThisCheck = 0;
        int maxSpawnedThisCheck = Math.max(1, MegaBossConfig.DATA.maxSpawnedPlayersPerCheck);
        for (ServerPlayer player : players) {
            if (TRACKED.size() >= globalSafetyCap) return;
            if (spawnedThisCheck >= maxSpawnedThisCheck) return;
            if (countMegaBossesNear(player.serverLevel(), player.blockPosition(), nearbyBossRadius()) >= Math.max(1, MegaBossConfig.DATA.maxAliveMegaBossesPerNearbyPlayer)) continue;

            if (RANDOM.nextDouble() > MegaBossConfig.DATA.spawnChancePerPlayerCheck) continue;
            MegaBossConfig.BossEntry boss = pickBoss();
            if (boss == null) continue;
            if (trySpawnFor(player, boss)) spawnedThisCheck++;
        }
    }

    public static boolean isMegaBoss(Entity entity) {
        return entity != null && entity.getTags().contains(BOSS_TAG);
    }

    public static String rarity(Entity entity) {
        if (entity == null) return "RARE";
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(BOSS_RARITY_PREFIX)) return tag.substring(BOSS_RARITY_PREFIX.length());
        }
        return "RARE";
    }

    public static String megaStone(Entity entity) {
        if (entity == null) return "";
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(BOSS_STONE_PREFIX)) return tag.substring(BOSS_STONE_PREFIX.length());
        }
        return "";
    }

    public static void discardBoss(Entity entity) {
        if (entity == null) return;
        TRACKED.remove(entity.getUUID());
        entity.discard();
    }

    private static boolean trySpawnFor(ServerPlayer player, MegaBossConfig.BossEntry boss) {
        ServerLevel level = player.serverLevel();
        if (countMegaBossesNear(level, player.blockPosition(), nearbyBossRadius()) >= Math.max(1, MegaBossConfig.DATA.maxAliveMegaBossesPerNearbyPlayer)) return false;
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition());
            if (pos == null) continue;
            int pokemonLevel = playerPartyHighestLevelPlusFive(player);
            Entity entity = spawnViaCommand(player.getServer(), level, pos, boss, pokemonLevel);
            if (entity == null) entity = spawnDirectly(level, pos, boss, pokemonLevel);
            if (entity == null) continue;
            if (countMegaBossesNear(level, pos, nearbyBossRadius()) > Math.max(0, MegaBossConfig.DATA.maxAliveMegaBossesPerNearbyPlayer - 1)) {
                entity.discard();
                continue;
            }
            markBoss(entity, boss, pokemonLevel);
            announce(player, boss, level, pos, pokemonLevel);
            return true;
        }
        return false;
    }

    private static MegaBossConfig.BossEntry pickBoss() {
        String rarity = pickRarity();
        List<MegaBossConfig.BossEntry> valid = new ArrayList<>();
        for (MegaBossConfig.BossEntry entry : MegaBossConfig.DATA.bosses) {
            if (entry == null || !entry.enabled || entry.species == null || entry.species.isBlank()) continue;
            if (rarity.equalsIgnoreCase(entry.rarity)) valid.add(entry);
        }
        if (valid.isEmpty()) {
            for (MegaBossConfig.BossEntry entry : MegaBossConfig.DATA.bosses) {
                if (entry != null && entry.enabled && entry.species != null && !entry.species.isBlank()) valid.add(entry);
            }
        }
        return valid.isEmpty() ? null : valid.get(RANDOM.nextInt(valid.size()));
    }

    private static String pickRarity() {
        MegaBossConfig.RarityWeights w = MegaBossConfig.DATA.rarityWeights;
        int common = Math.max(0, w.COMMON), uncommon = Math.max(0, w.UNCOMMON), rare = Math.max(0, w.RARE), epic = Math.max(0, w.EPIC), legendary = Math.max(0, w.LEGENDARY), mythic = Math.max(0, w.MYTHIC);
        int total = common + uncommon + rare + epic + legendary + mythic;
        if (total <= 0) return "RARE";
        int roll = RANDOM.nextInt(total);
        if ((roll -= common) < 0) return "COMMON";
        if ((roll -= uncommon) < 0) return "UNCOMMON";
        if ((roll -= rare) < 0) return "RARE";
        if ((roll -= epic) < 0) return "EPIC";
        if ((roll -= legendary) < 0) return "LEGENDARY";
        return "MYTHIC";
    }

    private static Entity spawnViaCommand(MinecraftServer server, ServerLevel level, BlockPos pos, MegaBossConfig.BossEntry boss, int pokemonLevel) {
        try {
            Set<UUID> before = new HashSet<>();
            for (Entity entity : level.getEntities(null, new AABB(pos).inflate(32.0D))) before.add(entity.getUUID());
            CommandSourceStack source = server.createCommandSourceStack()
                    .withLevel(level)
                    .withPosition(new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D))
                    .withRotation(Vec2.ZERO)
                    .withPermission(4)
                    .withSuppressedOutput();
            server.getCommands().performPrefixedCommand(source, buildPokespawnCommand(boss, pokemonLevel, pos));
            for (Entity entity : level.getEntities(null, new AABB(pos).inflate(32.0D))) {
                if (!before.contains(entity.getUUID())) return entity;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String buildPokespawnCommand(MegaBossConfig.BossEntry boss, int level, BlockPos pos) {
        StringBuilder cmd = new StringBuilder("pokespawn ").append(sanitize(boss.species))
                .append(" lvl=").append(level)
                .append(" x=").append(pos.getX() + 0.5D).append(" y=").append(pos.getY()).append(" z=").append(pos.getZ() + 0.5D)
                .append(" scale_modifier=").append(MegaBossConfig.DATA.scaleModifier)
                .append(" shiny=false ai=false")
                .append(" iv_hp=31 iv_attack=31 iv_defence=31 iv_special_attack=31 iv_special_defence=31 iv_speed=31")
                .append(" ev_hp=252 ev_attack=252 ev_defence=252 ev_special_attack=252 ev_special_defence=252 ev_speed=252");
        if (boss.ability != null && !boss.ability.isBlank()) cmd.append(" ability=").append(cleanToken(boss.ability));
        if (boss.nature != null && !boss.nature.isBlank()) cmd.append(" nature=").append(cleanToken(boss.nature));
        if (boss.moves != null) {
            for (int i = 0; i < boss.moves.size() && i < 4; i++) {
                String move = cleanToken(boss.moves.get(i));
                if (!move.isBlank()) cmd.append(" move").append(i + 1).append("=").append(move);
            }
        }
        if (boss.extraProperties != null && !boss.extraProperties.isBlank()) cmd.append(' ').append(boss.extraProperties.trim());
        return cmd.toString();
    }

    private static Entity spawnDirectly(ServerLevel level, BlockPos pos, MegaBossConfig.BossEntry boss, int pokemonLevel) {
        try {
            StringBuilder properties = new StringBuilder("species=\"cobblemon:").append(sanitize(boss.species)).append("\" level=").append(pokemonLevel);
            if (boss.extraProperties != null && !boss.extraProperties.isBlank()) properties.append(' ').append(boss.extraProperties.trim());
            if (boss.ability != null && !boss.ability.isBlank()) properties.append(" ability=").append(cleanToken(boss.ability));
            if (boss.nature != null && !boss.nature.isBlank()) properties.append(" nature=").append(cleanToken(boss.nature));
            Class<?> propertiesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propertiesClass.getField("Companion").get(null);
            Object parsed = companion.getClass().getMethod("parse", String.class).invoke(companion, properties.toString());
            Object pokemon = parsed.getClass().getMethod("create").invoke(parsed);
            Object spawned = invokePokemonSendOut(pokemon, level, new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
            return spawned instanceof Entity e ? e : null;
        } catch (Exception ignored) { return null; }
    }

    private static Object invokePokemonSendOut(Object pokemon, ServerLevel level, Vec3 spawnVec) throws Exception {
        for (Method method : pokemon.getClass().getMethods()) {
            if (!method.getName().equals("sendOut")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length < 2) continue;
            if (!params[0].isAssignableFrom(level.getClass()) || !params[1].isAssignableFrom(spawnVec.getClass())) continue;
            Object[] args = new Object[params.length];
            args[0] = level;
            args[1] = spawnVec;
            for (int i = 2; i < params.length; i++) args[i] = defaultArg(params[i]);
            return method.invoke(pokemon, args);
        }
        return null;
    }

    private static Object defaultArg(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if ("toString".equals(method.getName())) return "ChampUtilsMegaBossCallback";
                try { return Class.forName("kotlin.Unit").getField("INSTANCE").get(null); } catch (Exception ignored) { return null; }
            });
        }
        return null;
    }

    private static void markBoss(Entity entity, MegaBossConfig.BossEntry boss, int pokemonLevel) {
        entity.addTag(BOSS_TAG);
        entity.addTag(BOSS_RARITY_PREFIX + normalizeRarity(boss.rarity));
        String stone = boss.megaStoneItem == null || boss.megaStoneItem.isBlank() ? defaultMegaStoneItem(boss) : boss.megaStoneItem.trim();
        entity.addTag(BOSS_STONE_PREFIX + stone);
        long expiresAt = System.currentTimeMillis() + Math.max(1L, MegaBossConfig.DATA.despawnMinutes) * 60_000L;
        entity.addTag(EXPIRES_PREFIX + expiresAt);
        entity.setCustomName(Component.literal(formatNameTag(boss, pokemonLevel)));
        entity.setCustomNameVisible(true);
        if (entity instanceof Mob mob) mob.setPersistenceRequired();
        TRACKED.put(entity.getUUID(), expiresAt);
    }

    private static int nearbyBossRadius() {
        return Math.max(32, MegaBossConfig.DATA.nearbyPlayerBossRadius);
    }

    private static int countMegaBossesNear(ServerLevel level, BlockPos origin, int radius) {
        int count = 0;
        AABB box = new AABB(origin).inflate(radius);
        for (Entity entity : level.getEntities(null, box)) {
            if (isMegaBoss(entity) && entity.isAlive()) count++;
        }
        return count;
    }

    private static String formatNameTag(MegaBossConfig.BossEntry boss, int level) {
        String format = MegaBossConfig.DATA.nameTagFormat;
        if (format == null || format.isBlank()) format = "§5§lMega Boss §8| §d{species} §7[{rarity}] §fLv.{level}";
        return format
                .replace("{species}", pretty(boss.species))
                .replace("{rarity}", normalizeRarity(boss.rarity))
                .replace("{level}", Integer.toString(level));
    }

    public static String defaultMegaStoneItem(MegaBossConfig.BossEntry boss) {
        String species = sanitize(boss == null ? "" : boss.species);
        String extra = boss == null || boss.extraProperties == null ? "" : boss.extraProperties.toLowerCase(Locale.ROOT);
        if ("charizard".equals(species) && extra.contains("mega_x")) return "cobblemon:charizardite_x";
        if ("charizard".equals(species) && extra.contains("mega_y")) return "cobblemon:charizardite_y";
        if ("mewtwo".equals(species) && extra.contains("mega_x")) return "cobblemon:mewtwonite_x";
        if ("mewtwo".equals(species) && extra.contains("mega_y")) return "cobblemon:mewtwonite_y";
        return "cobblemon:" + species + "ite";
    }

    private static void cleanup(MinecraftServer server) {
        recover(server);
        long now = System.currentTimeMillis();
        TRACKED.entrySet().removeIf(entry -> {
            Entity entity = findEntity(server, entry.getKey());
            if (entity == null || !entity.isAlive()) return true;
            if (now >= entry.getValue()) { entity.discard(); return true; }
            return false;
        });
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!isMegaBoss(entity)) continue;
                long expires = expiresAt(entity);
                if (expires > 0L && now >= expires) entity.discard();
            }
        }
    }

    private static void recover(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!isMegaBoss(entity)) continue;
                long expires = expiresAt(entity);
                if (expires > 0L) TRACKED.put(entity.getUUID(), expires);
            }
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static long expiresAt(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(EXPIRES_PREFIX)) {
                try { return Long.parseLong(tag.substring(EXPIRES_PREFIX.length())); } catch (Exception ignored) { return 0L; }
            }
        }
        return 0L;
    }

    private static BlockPos randomSpawnPos(ServerLevel level, BlockPos origin) {
        int min = Math.max(8, MegaBossConfig.DATA.minDistanceFromPlayer);
        int max = Math.max(min + 1, MegaBossConfig.DATA.maxDistanceFromPlayer);
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

    public static int playerPartyHighestLevelPlusFive(ServerPlayer player) {
        int highest = 0;
        try {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                if (pokemon == null) continue;
                highest = Math.max(highest, Math.max(1, pokemon.getLevel()));
            }
        } catch (Exception ignored) {}

        // Match roaming trainer scaling: boss level is exactly +5 above the challenger's highest party Pokemon.
        // If the party cannot be read, fall back to 15 instead of failing the spawn.
        if (highest <= 0) return 15;
        return Math.max(1, Math.min(100, highest + Math.max(0, MegaBossConfig.DATA.levelsAbovePlayerHighest)));
    }

    private static boolean isDisabledDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        for (String d : MegaBossConfig.DATA.disabledDimensions) if (id.equalsIgnoreCase(d)) return true;
        return false;
    }

    private static void announce(ServerPlayer player, MegaBossConfig.BossEntry boss, ServerLevel level, BlockPos pos, int pokemonLevel) {
        if (!MegaBossConfig.DATA.broadcastSpawns || player == null) return;
        String biome = level.registryAccess().registryOrThrow(Registries.BIOME).getKey(level.getBiome(pos).value()).toString();
        player.sendSystemMessage(Component.literal("§5§lMega Boss Spawned! §d" + pretty(boss.species) + " §7[" + normalizeRarity(boss.rarity) + "] §fappeared near you in §e" + biome + " §7Lv." + pokemonLevel + " §eX:" + pos.getX() + " Y:" + pos.getY() + " Z:" + pos.getZ() + " §cCannot be caught."));
    }

    private static String normalizeRarity(String rarity) {
        if (rarity == null || rarity.isBlank()) return "RARE";
        return rarity.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static String sanitize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        int c = v.lastIndexOf(':');
        if (c >= 0 && c + 1 < v.length()) v = v.substring(c + 1);
        return v.replace(' ', '_').replace('-', '_').replaceAll("[^a-z0-9_]", "");
    }

    private static String cleanToken(String raw) {
        return sanitize(raw).replace("_", "");
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
