package com.champutils.megaboss;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;
import com.champutils.profile.IslanderMineManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.territory.TerritoryRepository;

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
    private static final Stat[] PERMANENT_STATS = new Stat[] {
            Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED
    };
    // Cobblemon enforces the vanilla 510 total EV cap. Giving 252 to all six stats
    // creates a 1512-EV Pokémon; when a battle ends or is fled from, Cobblemon's EV
    // reward sync can try to coerce into a negative range and crash the server.
    // 85 x 6 = 510 keeps mega bosses as strong as legally possible without corrupting EV state.
    private static final int BOSS_BALANCED_EV = 85;
    private static final String BOSS_EV_PROPERTIES =
            " ev_hp=85 ev_attack=85 ev_defence=85 ev_special_attack=85 ev_special_defence=85 ev_speed=85";
    public static final String BOSS_TAG = "champutils_mega_boss";
    public static final String BOSS_RARITY_PREFIX = "champutils_mega_boss_rarity_";
    public static final String BOSS_STONE_PREFIX = "champutils_mega_boss_stone_";
    private static final String EXPIRES_PREFIX = "champutils_mega_boss_expires_";
    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> TRACKED = new ConcurrentHashMap<>();
    private static int ticksUntilCheck = 1200;
    private static int ticksUntilCleanup = 200;

    private static void debug(String message) {
        if (MegaBossConfig.DATA.debugSpawning) {
            System.out.println("[ChampUtils][MegaBossDebug] " + message);
        }
    }

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
        if (TRACKED.size() >= globalSafetyCap) {
            debug("skip check: global cap reached tracked=" + TRACKED.size() + "/" + globalSafetyCap);
            return;
        }

        List<ServerPlayer> allPlayers = new ArrayList<>(server.getPlayerList().getPlayers());
        int totalPlayers = allPlayers.size();
        List<ServerPlayer> players = new ArrayList<>(allPlayers);
        players.removeIf(p -> p == null || p.isSpectator() || isDisabledDimension(p.serverLevel()));
        if (players.isEmpty()) {
            debug("skip check: no eligible players total=" + totalPlayers + " tracked=" + TRACKED.size());
            return;
        }

        Collections.shuffle(players, RANDOM);
        int spawnedThisCheck = 0;
        int nearbyCapSkips = 0;
        int chanceSkips = 0;
        int noBossSkips = 0;
        int failedSpawns = 0;
        int maxSpawnedThisCheck = Math.max(1, MegaBossConfig.DATA.maxSpawnedPlayersPerCheck);
        for (ServerPlayer player : players) {
            if (TRACKED.size() >= globalSafetyCap) break;
            if (spawnedThisCheck >= maxSpawnedThisCheck) break;
            if (countMegaBossesNear(player.serverLevel(), player.blockPosition(), nearbyBossRadius()) >= Math.max(1, MegaBossConfig.DATA.maxAliveMegaBossesPerNearbyPlayer)) {
                nearbyCapSkips++;
                continue;
            }

            double roll = RANDOM.nextDouble();
            if (roll > MegaBossConfig.DATA.spawnChancePerPlayerCheck) {
                chanceSkips++;
                continue;
            }
            MegaBossConfig.BossEntry boss = pickBoss();
            if (boss == null) {
                noBossSkips++;
                continue;
            }
            if (trySpawnFor(player, boss)) spawnedThisCheck++;
            else failedSpawns++;
        }
        debug("check complete totalPlayers=" + totalPlayers + " eligible=" + players.size() + " spawned=" + spawnedThisCheck + " tracked=" + TRACKED.size() + "/" + globalSafetyCap + " chance=" + MegaBossConfig.DATA.spawnChancePerPlayerCheck + " nearbyCapSkips=" + nearbyCapSkips + " chanceSkips=" + chanceSkips + " noBossSkips=" + noBossSkips + " failedSpawns=" + failedSpawns);
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
        List<String> stones = megaStones(entity);
        return stones.isEmpty() ? "" : stones.get(0);
    }

    public static List<String> megaStones(Entity entity) {
        List<String> stones = new ArrayList<>();
        if (entity == null) return stones;
        for (String tag : entity.getTags()) {
            if (tag == null || !tag.startsWith(BOSS_STONE_PREFIX)) continue;
            String value = tag.substring(BOSS_STONE_PREFIX.length()).trim();
            if (!value.isBlank() && !stones.contains(value)) stones.add(value);
        }
        return stones;
    }

    public static void discardBoss(Entity entity) {
        if (entity == null) return;
        TRACKED.remove(entity.getUUID());
        entity.discard();
    }

    private static boolean trySpawnFor(ServerPlayer player, MegaBossConfig.BossEntry boss) {
        ServerLevel level = player.serverLevel();
        if (isDisabledDimension(level)) {
            debug("spawn fail player=" + player.getGameProfile().getName() + " reason=disabled_dimension dimension=" + level.dimension().location());
            return false;
        }
        if (countMegaBossesNear(level, player.blockPosition(), nearbyBossRadius()) >= Math.max(1, MegaBossConfig.DATA.maxAliveMegaBossesPerNearbyPlayer)) {
            debug("spawn fail player=" + player.getGameProfile().getName() + " reason=nearby_cap");
            return false;
        }
        int nullPositions = 0;
        int mineWorldSkips = 0;
        int entitySpawnFailures = 0;
        int postSpawnCapSkips = 0;
        int territorySkips = 0;
        for (int attempt = 0; attempt < 20; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition());
            if (pos == null) { nullPositions++; continue; }
            if (isNetherRoofPosition(level, pos)) { nullPositions++; continue; }
            if (TerritoryRepository.findAt(level, pos) != null && !isIslanderDimension(level)) { territorySkips++; continue; }
            int pokemonLevel = playerPartyHighestLevelForRarity(player, boss.rarity);
            Entity entity = spawnViaCommand(player.getServer(), level, pos, boss, pokemonLevel);
            if (entity == null) entity = spawnDirectly(level, pos, boss, pokemonLevel);
            if (entity == null) { entitySpawnFailures++; continue; }
            if (countMegaBossesNear(level, pos, nearbyBossRadius()) > Math.max(0, MegaBossConfig.DATA.maxAliveMegaBossesPerNearbyPlayer - 1)) {
                entity.discard();
                postSpawnCapSkips++;
                continue;
            }
            markBoss(entity, boss, pokemonLevel);
            try { entity.setInvulnerable(true); } catch (Throwable ignored) {}
            announce(player, boss, level, pos, pokemonLevel);
            debug("spawn success player=" + player.getGameProfile().getName() + " boss=" + sanitize(boss.species) + " rarity=" + normalizeRarity(boss.rarity) + " level=" + pokemonLevel + " dimension=" + level.dimension().location() + " pos=" + pos.getX() + "," + pos.getY() + "," + pos.getZ());
            return true;
        }
        debug("spawn fail player=" + player.getGameProfile().getName() + " boss=" + sanitize(boss.species) + " rarity=" + normalizeRarity(boss.rarity) + " reason=no_valid_attempt attempts=20 nullPositions=" + nullPositions + " mineWorldSkips=" + mineWorldSkips + " territorySkips=" + territorySkips + " entitySpawnFailures=" + entitySpawnFailures + " postSpawnCapSkips=" + postSpawnCapSkips + " dimension=" + level.dimension().location());
        return false;
    }

    public static ForceSpawnResult forceSpawn(ServerPlayer player, String rarity) {
        if (player == null) return ForceSpawnResult.fail("Player not found.");
        if (!MegaBossConfig.DATA.enabled) return ForceSpawnResult.fail("Mega bosses are disabled in config.");
        ServerLevel level = player.serverLevel();
        if (isDisabledDimension(level)) return ForceSpawnResult.fail("Mega bosses are disabled in this dimension.");

        String normalized = normalizeRarity(rarity);
        MegaBossConfig.BossEntry boss = pickBoss(normalized, false);
        if (boss == null) return ForceSpawnResult.fail("No enabled mega bosses found for rarity " + normalized + ".");

        // Test command intentionally bypasses the natural nearby/global spawn caps, but it still uses
        // the normal spawn-position safety checks, battle stats, tags, expiry, and reward metadata.
        for (int attempt = 0; attempt < 30; attempt++) {
            BlockPos pos = randomSpawnPos(level, player.blockPosition());
            if (pos == null || isNetherRoofPosition(level, pos) || (TerritoryRepository.findAt(level, pos) != null && !isIslanderDimension(level))) continue;
            int pokemonLevel = playerPartyHighestLevelForRarity(player, boss.rarity);
            Entity entity = spawnViaCommand(player.getServer(), level, pos, boss, pokemonLevel);
            if (entity == null) entity = spawnDirectly(level, pos, boss, pokemonLevel);
            if (entity == null) continue;
            markBoss(entity, boss, pokemonLevel);
            try { entity.setInvulnerable(true); } catch (Throwable ignored) {}
            announce(player, boss, level, pos, pokemonLevel);
            return ForceSpawnResult.success(boss, pos, pokemonLevel);
        }
        return ForceSpawnResult.fail("Could not find a valid spawn position near you.");
    }

    public static List<String> validRarities() {
        return List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    }

    private static MegaBossConfig.BossEntry pickBoss() {
        List<MegaBossConfig.BossEntry> valid = new ArrayList<>();
        for (MegaBossConfig.BossEntry entry : MegaBossConfig.DATA.bosses) {
            if (entry != null && entry.enabled && entry.species != null && !entry.species.isBlank()) valid.add(entry);
        }
        return valid.isEmpty() ? null : valid.get(RANDOM.nextInt(valid.size()));
    }

    private static MegaBossConfig.BossEntry pickBoss(String rarity, boolean fallbackToAnyRarity) {
        String normalized = normalizeRarity(rarity);
        List<MegaBossConfig.BossEntry> valid = new ArrayList<>();
        for (MegaBossConfig.BossEntry entry : MegaBossConfig.DATA.bosses) {
            if (entry == null || !entry.enabled || entry.species == null || entry.species.isBlank()) continue;
            if (normalized.equalsIgnoreCase(entry.rarity)) valid.add(entry);
        }
        if (valid.isEmpty() && fallbackToAnyRarity) {
            for (MegaBossConfig.BossEntry entry : MegaBossConfig.DATA.bosses) {
                if (entry != null && entry.enabled && entry.species != null && !entry.species.isBlank()) valid.add(entry);
            }
        }
        return valid.isEmpty() ? null : valid.get(RANDOM.nextInt(valid.size()));
    }

    private static String pickRarity() {
        // Deprecated natural-spawn helper. Natural megabosses are now selected uniformly
        // from the enabled boss list so rarity no longer changes spawn rarity.
        // This method remains only for compatibility with any older internal calls.
        List<String> rarities = validRarities();
        return rarities.get(RANDOM.nextInt(rarities.size()));
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

    private static String sanitizeExtraProperties(String extraProperties) {
        if (extraProperties == null || extraProperties.isBlank()) return "";
        // Do not allow config-supplied EV tokens to override the safe legal spread above.
        // This protects old configs that still contain ev_hp=252 ... ev_speed=252.
        return extraProperties.trim()
                .replaceAll("(?i)(^|\\s)ev_(hp|attack|atk|defence|defense|def|special_attack|specialattack|spa|special_defence|special_defense|specialdefence|specialdefense|spd|speed|spe)=[^\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String buildPokespawnCommand(MegaBossConfig.BossEntry boss, int level, BlockPos pos) {
        StringBuilder cmd = new StringBuilder("pokespawn ").append(sanitize(boss.species))
                .append(" lvl=").append(level)
                .append(" x=").append(pos.getX() + 0.5D).append(" y=").append(pos.getY()).append(" z=").append(pos.getZ() + 0.5D)
                .append(" scale_modifier=").append(MegaBossConfig.DATA.scaleModifier)
                .append(" shiny=false ai=true")
                .append(" iv_hp=31 iv_attack=31 iv_defence=31 iv_special_attack=31 iv_special_defence=31 iv_speed=31")
                .append(BOSS_EV_PROPERTIES);
        if (boss.ability != null && !boss.ability.isBlank()) cmd.append(" ability=").append(cleanToken(boss.ability));
        if (boss.nature != null && !boss.nature.isBlank()) cmd.append(" nature=").append(cleanToken(boss.nature));
        if (boss.moves != null) {
            for (int i = 0; i < boss.moves.size() && i < 4; i++) {
                String move = cleanToken(boss.moves.get(i));
                if (!move.isBlank()) cmd.append(" move").append(i + 1).append("=").append(move);
            }
        }
        String safeExtra = sanitizeExtraProperties(boss.extraProperties);
        if (!safeExtra.isBlank()) cmd.append(' ').append(safeExtra);
        return cmd.toString();
    }

    private static Entity spawnDirectly(ServerLevel level, BlockPos pos, MegaBossConfig.BossEntry boss, int pokemonLevel) {
        try {
            StringBuilder properties = new StringBuilder("species=\"cobblemon:").append(sanitize(boss.species)).append("\" level=").append(pokemonLevel);
            String safeExtra = sanitizeExtraProperties(boss.extraProperties);
            if (!safeExtra.isBlank()) properties.append(' ').append(safeExtra);
            properties.append(" iv_hp=31 iv_attack=31 iv_defence=31 iv_special_attack=31 iv_special_defence=31 iv_speed=31");
            properties.append(BOSS_EV_PROPERTIES);
            if (boss.ability != null && !boss.ability.isBlank()) properties.append(" ability=").append(cleanToken(boss.ability));
            if (boss.nature != null && !boss.nature.isBlank()) properties.append(" nature=").append(cleanToken(boss.nature));
            Class<?> propertiesClass = Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonProperties");
            Object companion = propertiesClass.getField("Companion").get(null);
            Object parsed = companion.getClass().getMethod("parse", String.class).invoke(companion, properties.toString());
            Object pokemon = parsed.getClass().getMethod("create").invoke(parsed);
            if (pokemon instanceof Pokemon p) maximizePokemon(p, pokemonLevel);
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
        for (String stone : megaStoneItems(boss)) {
            String normalizedStone = normalizeGenesisItemId(stone);
            if (normalizedStone != null && !normalizedStone.isBlank()) entity.addTag(BOSS_STONE_PREFIX + normalizedStone);
        }
        long expiresAt = System.currentTimeMillis() + Math.max(1L, MegaBossConfig.DATA.despawnMinutes) * 60_000L;
        entity.addTag(EXPIRES_PREFIX + expiresAt);
        entity.setCustomName(Component.literal(formatNameTag(boss, pokemonLevel)));
        entity.setCustomNameVisible(true);
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
            mob.setPersistenceRequired();
        }
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

    public static List<String> megaStoneItems(MegaBossConfig.BossEntry boss) {
        List<String> configured = new ArrayList<>();
        if (boss != null && boss.megaStoneItems != null) {
            for (String item : boss.megaStoneItems) {
                String normalized = normalizeGenesisItemId(item);
                if (normalized != null && !normalized.isBlank() && !configured.contains(normalized)) configured.add(normalized);
            }
        }
        if (boss != null && boss.megaStoneItem != null && !boss.megaStoneItem.isBlank()) {
            for (String item : boss.megaStoneItem.split("[,;]")) {
                String normalized = normalizeGenesisItemId(item);
                if (normalized != null && !normalized.isBlank() && !configured.contains(normalized)) configured.add(normalized);
            }
        }
        if (!configured.isEmpty()) return configured;
        return defaultMegaStoneItems(boss);
    }

    public static String defaultMegaStoneItem(MegaBossConfig.BossEntry boss) {
        List<String> stones = defaultMegaStoneItems(boss);
        return stones.isEmpty() ? "" : stones.get(0);
    }

    public static List<String> defaultMegaStoneItems(MegaBossConfig.BossEntry boss) {
        String species = sanitize(boss == null ? "" : boss.species);
        String extra = boss == null || boss.extraProperties == null ? "" : boss.extraProperties.toLowerCase(Locale.ROOT);
        if ("charizard".equals(species) && extra.contains("mega_x")) return List.of("genesisforms:charizardite-x");
        if ("charizard".equals(species) && extra.contains("mega_y")) return List.of("genesisforms:charizardite-y");
        if ("mewtwo".equals(species) && extra.contains("mega_x")) return List.of("genesisforms:mewtwonite-x");
        if ("mewtwo".equals(species) && extra.contains("mega_y")) return List.of("genesisforms:mewtwonite-y");
        if ("charizard".equals(species)) return List.of("genesisforms:charizardite-x", "genesisforms:charizardite-y");
        if ("mewtwo".equals(species)) return List.of("genesisforms:mewtwonite-x", "genesisforms:mewtwonite-y");
        List<String> genesisConfigured = genesisMegaStoneOverride(species);
        if (!genesisConfigured.isEmpty()) return genesisConfigured;
        if (species.isBlank()) return List.of();
        return List.of("genesisforms:" + species + "ite");
    }

    private static List<String> genesisMegaStoneOverride(String species) {
        if (species == null || species.isBlank()) return List.of();
        return switch (species) {
            case "abomasnow" -> List.of("genesisforms:abomasite");
            case "audino" -> List.of("genesisforms:audinite");
            case "banette" -> List.of("genesisforms:banettite");
            case "glalie" -> List.of("genesisforms:glalitite");
            case "houndoom" -> List.of("genesisforms:houndoominite");
            case "manectric" -> List.of("genesisforms:manectite");
            case "sharpedo" -> List.of("genesisforms:sharpedonite");
            case "altaria" -> List.of("genesisforms:altarianite");
            case "blastoise" -> List.of("genesisforms:blastoisinite");
            case "sableye" -> List.of("genesisforms:sablenite");
            case "slowbro" -> List.of("genesisforms:slowbronite");
            case "alakazam" -> List.of("genesisforms:alakazite");
            case "gallade" -> List.of("genesisforms:galladite");
            case "heracross" -> List.of("genesisforms:heracronite");
            case "lopunny" -> List.of("genesisforms:lopunnite");
            case "lucario" -> List.of("genesisforms:lucarionite", "genesisforms:lucarionite-z");
            case "mawile" -> List.of("genesisforms:mawilite");
            case "sceptile" -> List.of("genesisforms:sceptilite");
            case "diancie" -> List.of("genesisforms:diancite");
            case "salamence" -> List.of("genesisforms:salamencite");
            default -> List.of();
        };
    }

    public static String normalizeGenesisItemId(String itemId) {
        if (itemId == null) return "";
        String id = itemId.trim().toLowerCase(Locale.ROOT);
        if (id.isBlank()) return "";
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        path = normalizeGenesisItemPath(path);
        if (isGenesisFormsItemPath(path)) return "genesisforms:" + path;
        return id;
    }

    private static String normalizeGenesisItemPath(String path) {
        if (path == null) return "";
        return path.trim().toLowerCase(Locale.ROOT)
                .replace("charizardite_x", "charizardite-x")
                .replace("charizardite_y", "charizardite-y")
                .replace("mewtwonite_x", "mewtwonite-x")
                .replace("mewtwonite_y", "mewtwonite-y");
    }

    private static boolean isGenesisFormsItemPath(String path) {
        if (path == null || path.isBlank()) return false;
        if (path.equals("tera_orb") || path.equals("mega_bracelet") || path.equals("mega_ring") || path.equals("mega_charm") || path.equals("mega_cuff") || path.equals("mega_anklet") || path.equals("keystone") || path.equals("key_stone")) return true;
        if (path.equals("adamant_crystal") || path.equals("lustrous_globe") || path.equals("griseous_core")) return true;
        if (path.endsWith("ite") || path.endsWith("ite-x") || path.endsWith("ite-y") || path.endsWith("nite-x") || path.endsWith("nite-y") || path.endsWith("ite_z") || path.endsWith("ite-z")) return true;
        return false;
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
        if (isNetherRoofPosition(level, top)) return null;
        if (!level.getWorldBorder().isWithinBounds(top) || !level.getWorldBorder().isWithinBounds(top.above())) return null;
        if (!level.getBlockState(top.below()).isSolid()) return null;
        if (!level.getBlockState(top).isAir() || !level.getBlockState(top.above()).isAir()) return null;
        return top;
    }

    public static int playerPartyHighestLevelForRarity(ServerPlayer player, String rarity) {
        int highest = 0;
        try {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                if (pokemon == null) continue;
                highest = Math.max(highest, Math.max(1, pokemon.getLevel()));
            }
        } catch (Exception ignored) {}

        int offset = rarityLevelOffset(rarity);
        // Mega bosses scale by spawn rarity: common +5, uncommon +10, rare +15,
        // epic +20, legendary +25, mythic +30, capped at level 100.
        if (highest <= 0) return Math.max(1, Math.min(100, 15 + offset));
        return Math.max(1, Math.min(100, highest + offset));
    }

    public static int playerPartyHighestLevelPlusTen(ServerPlayer player) {
        return playerPartyHighestLevelForRarity(player, "RARE");
    }

    private static int rarityLevelOffset(String rarity) {
        String normalized = normalizeRarity(rarity);
        return switch (normalized) {
            case "COMMON" -> 5;
            case "UNCOMMON" -> 10;
            case "RARE" -> 15;
            case "EPIC" -> 20;
            case "LEGENDARY" -> 25;
            case "MYTHIC" -> 30;
            default -> 5;
        };
    }

    public static void maximizePokemon(Pokemon pokemon, int level) {
        if (pokemon == null) return;
        try { pokemon.setLevel(Math.max(1, Math.min(100, level))); } catch (Throwable ignored) {}
        try {
            for (Stat stat : PERMANENT_STATS) pokemon.getIvs().set(stat, 31);
            pokemon.getIvs().update();
        } catch (Throwable ignored) {}
        try {
            Object evs = pokemon.getEvs();
            java.lang.reflect.Field statsField = evs.getClass().getSuperclass().getDeclaredField("stats");
            statsField.setAccessible(true);
            Object value = statsField.get(evs);
            if (value instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<Stat, Integer> map = (Map<Stat, Integer>) rawMap;
                for (Stat stat : PERMANENT_STATS) map.put(stat, BOSS_BALANCED_EV);
            } else {
                for (Stat stat : PERMANENT_STATS) pokemon.getEvs().set(stat, BOSS_BALANCED_EV);
            }
            pokemon.getEvs().update();
        } catch (Throwable ignored) {
            try { for (Stat stat : PERMANENT_STATS) pokemon.getEvs().set(stat, BOSS_BALANCED_EV); } catch (Throwable ignoredToo) {}
        }
        try { pokemon.setCurrentHealth(pokemon.getMaxHealth()); } catch (Throwable ignored) {}
    }

    private static boolean isProtectedSpawnArea(ServerLevel level, BlockPos pos) {
        try { if (com.champutils.territory.TerritoryRepository.findAt(level, pos) != null) return true; } catch (Throwable ignored) {}
        try { if (com.champutils.claims.LandClaimRepository.findAt(level, pos) != null) return true; } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isNetherRoofPosition(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        return (id.equals("minecraft:the_nether") || id.endsWith(":the_nether") || id.equals("the_nether")) && pos.getY() >= 127;
    }

    private static boolean isDisabledDimension(ServerLevel level) {
        String id = level.dimension().location().toString();
        String lower = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (lower.equals("spawn1") || lower.endsWith(":spawn1") || lower.contains("spawn1")) return true;
        for (String d : MegaBossConfig.DATA.disabledDimensions) if (id.equalsIgnoreCase(d)) return true;
        return false;
    }

    private static boolean isIslanderDimension(ServerLevel level) {
        if (level == null) return false;
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        return id.equals("islander") ||
                id.endsWith(":islander") ||
                id.startsWith("islander_") ||
                id.contains(":islander_") ||
                id.contains("/islander_") ||
                IslanderMineManager.isMineWorld(level);
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
    public static final class ForceSpawnResult {
        public final boolean success;
        public final String message;
        public final MegaBossConfig.BossEntry boss;
        public final BlockPos pos;
        public final int level;

        private ForceSpawnResult(boolean success, String message, MegaBossConfig.BossEntry boss, BlockPos pos, int level) {
            this.success = success;
            this.message = message;
            this.boss = boss;
            this.pos = pos;
            this.level = level;
        }

        public static ForceSpawnResult success(MegaBossConfig.BossEntry boss, BlockPos pos, int level) {
            String name = boss == null ? "Mega Boss" : pretty(boss.species);
            String rarity = boss == null ? "UNKNOWN" : normalizeRarity(boss.rarity);
            return new ForceSpawnResult(true, "Spawned " + rarity + " " + name + " Lv." + level + ".", boss, pos, level);
        }

        public static ForceSpawnResult fail(String message) {
            return new ForceSpawnResult(false, message, null, null, 0);
        }
    }

}
