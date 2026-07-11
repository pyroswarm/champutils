package com.champutils.teleport;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.teleport.SafeTeleportManager;
import com.champutils.survival.SurvivalWorldManager;
import com.champutils.survival.SurvivalWorldConfig;
import com.champutils.profile.ProfileLobbyLockManager;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.worldborder.ChampWorldBorderConfig;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RandomTeleportCommand {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> LAST_USE_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, SearchTask> ACTIVE_SEARCHES = new ConcurrentHashMap<>();
    private static final Map<UUID, Runnable> AFTER_SUCCESS_CALLBACKS = new ConcurrentHashMap<>();

    private static final int ATTEMPTS_PER_TICK = 1;
    private static final int BIOME_ATTEMPTS_PER_TICK = 1;
    // RTP is allowed to generate/check a small number of chunks again.
    // The previous zero budget made RTP skip every unloaded candidate and fail in fresh worlds.
    // Keep this intentionally tiny: at most one async chunk request globally, with a per-search cap.
    private static final int GLOBAL_CHUNK_GENERATION_BUDGET_PER_TICK = 1;
    private static final int MAX_GENERATED_CHUNKS_PER_SEARCH = 12;
    private static final int MAX_GENERATED_CHUNKS_PER_BIOME_SEARCH = 24;
    private static final int RTP_CHUNK_GENERATION_COOLDOWN_TICKS = 40;
    private static final int BIOME_CHUNK_GENERATION_COOLDOWN_TICKS = 80;
    private static final int MAX_ACTIVE_RTP_SEARCHES = 1;
    private static final int BORDER_PADDING = 32;
    private static final int NETHER_MAX_SAFE_Y = 119;
    private static final int MAX_RTP_SEARCH_ATTEMPTS = 9600;
    private static final int MAX_RTP_SEARCH_TICKS = 1200;
    private static final int MAX_BIOME_RTP_SEARCH_ATTEMPTS = 20000;
    private static final int MAX_BIOME_RTP_SEARCH_TICKS = 2400;
    private static final int MAX_CACHED_SAFE_POSITIONS_PER_WORLD = 256;
    private static final Map<String, ConcurrentLinkedQueue<BlockPos>> SAFE_POSITION_CACHE = new ConcurrentHashMap<>();

    private RandomTeleportCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("rtp")
                    .executes(ctx -> rtpSurvival(ctx.getSource(), "overworld", null))
                    .then(literal("overworld")
                            .executes(ctx -> rtpSurvival(ctx.getSource(), "overworld", null)))
                    .then(literal("nether")
                            .executes(ctx -> rtpSurvival(ctx.getSource(), "nether", null)))
                    .then(literal("end")
                            .executes(ctx -> rtpSurvival(ctx.getSource(), "end", null))));

            dispatcher.register(literal("rtpcooldown")
                    .requires(source -> source.hasPermission(4))
                    .executes(ctx -> showCooldown(ctx.getSource()))
                    .then(argument("seconds", IntegerArgumentType.integer(0))
                            .executes(ctx -> setCooldown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));

            dispatcher.register(literal("rtpdimension")
                    .requires(source -> source.hasPermission(4))
                    .then(literal("list")
                            .executes(ctx -> listBlocked(ctx.getSource())))
                    .then(literal("block")
                            .then(argument("dimension", StringArgumentType.greedyString())
                                    .executes(ctx -> blockDimension(ctx.getSource(), StringArgumentType.getString(ctx, "dimension")))))
                    .then(literal("unblock")
                            .then(argument("dimension", StringArgumentType.greedyString())
                                    .executes(ctx -> unblockDimension(ctx.getSource(), StringArgumentType.getString(ctx, "dimension")))))
                    .then(literal("fallback")
                            .then(argument("dimension", StringArgumentType.greedyString())
                                    .executes(ctx -> setFallback(ctx.getSource(), StringArgumentType.getString(ctx, "dimension"))))));


            dispatcher.register(literal("rtpworlds")
                    .requires(source -> source.hasPermission(4))
                    .then(literal("list")
                            .executes(ctx -> listRtpWorlds(ctx.getSource())))
                    .then(literal("unlock")
                            .then(argument("password", StringArgumentType.word())
                                    .then(argument("world", StringArgumentType.greedyString())
                                            .executes(ctx -> setRtpWorldActive(ctx.getSource(), StringArgumentType.getString(ctx, "password"), StringArgumentType.getString(ctx, "world"), true)))))
                    .then(literal("lock")
                            .then(argument("password", StringArgumentType.word())
                                    .then(argument("world", StringArgumentType.greedyString())
                                            .executes(ctx -> setRtpWorldActive(ctx.getSource(), StringArgumentType.getString(ctx, "password"), StringArgumentType.getString(ctx, "world"), false))))));
        });
    }



    public static boolean requestRtp(ServerPlayer player, String survivalType, Runnable afterSuccess) {
        if (player == null) return false;
        UUID playerId = player.getUUID();
        if (afterSuccess != null) AFTER_SUCCESS_CALLBACKS.put(playerId, afterSuccess);
        int result = rtpSurvival(player.createCommandSourceStack(), survivalType == null ? "overworld" : survivalType, null);
        if (result <= 0) AFTER_SUCCESS_CALLBACKS.remove(playerId);
        return result > 0;
    }

    private static void runAfterSuccess(ServerPlayer player) {
        if (player == null) return;
        AdventureGuideManager.increment(player, "rtp", 1);
        Runnable callback = AFTER_SUCCESS_CALLBACKS.remove(player.getUUID());
        if (callback == null) return;
        try {
            callback.run();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private static int rtpUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Use /rtp, /rtp nether, or /rtp end. Biome-specific RTP is disabled to protect server performance.").withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestBiomes(CommandSourceStack source, com.mojang.brigadier.suggestion.SuggestionsBuilder builder, String survivalType) {
        String input = normalizeBiomeSearch(builder.getRemaining());
        List<String> names = new ArrayList<>();
        source.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
                .filter(id -> biomeAllowedForRtpType(id, survivalType))
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .forEach(id -> {
                    String display = biomeDisplayName(id);
                    String normalizedDisplay = normalizeBiomeSearch(display);
                    String normalizedPath = normalizeBiomeSearch(id.getPath());
                    String normalizedFull = normalizeBiomeSearch(id.toString());
                    if (input.isBlank() || normalizedDisplay.contains(input) || normalizedPath.contains(input) || normalizedFull.contains(input)) {
                        names.add(display);
                    }
                });
        return SharedSuggestionProvider.suggest(names.stream().distinct().limit(80), builder);
    }

    private static boolean biomeAllowedForRtpType(ResourceLocation id, String survivalType) {
        if (id == null || isRtpBiomeBlacklisted(id)) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String type = survivalType == null ? "overworld" : survivalType.toLowerCase(Locale.ROOT);

        // Be permissive for custom datapack/modded dimensions. The old filter only allowed a few
        // vanilla Nether biomes and blocked End biome RTP entirely, which made RTP fail in many realms.
        if ("nether".equals(type)) {
            return path.contains("nether")
                    || path.contains("crimson")
                    || path.contains("warped")
                    || path.contains("soul_sand")
                    || path.contains("basalt")
                    || path.equals("nether_wastes");
        }
        if ("end".equals(type)) {
            return path.contains("end") || path.equals("the_end") || path.equals("small_end_islands");
        }
        return !path.contains("nether")
                && !path.contains("end")
                && !path.equals("crimson_forest")
                && !path.equals("warped_forest")
                && !path.equals("soul_sand_valley")
                && !path.equals("basalt_deltas")
                && !path.equals("nether_wastes");
    }

    private static boolean isRtpBiomeBlacklisted(ResourceLocation id) {
        if (id == null) return true;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        return path.equals("deep_dark")
                || path.equals("the_void")
                || path.equals("lush_caves")
                || path.equals("dripstone_caves");
    }

    private static int listRtpWorlds(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Survival RTP Worlds").withStyle(ChatFormatting.GOLD), false);
        MinecraftServer server = source.getServer();
        for (SurvivalWorldManager.Entry entry : SurvivalWorldManager.entries()) {
            ServerLevel level = TeleportConfig.resolveLevel(server, entry.worldName);
            int players = level == null ? -1 : level.players().size();
            int cap = SurvivalWorldConfig.get().maxRtpPlayersPerWorld;
            boolean capped = level != null && SurvivalWorldManager.isAtOrOverRtpCap(level);
            ChatFormatting color = !entry.activeForRtp ? ChatFormatting.DARK_GRAY : capped ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            String countText = players < 0 ? "unloaded" : players + "/" + cap + " RTP cap";
            source.sendSuccess(() -> Component.literal((entry.activeForRtp ? "ACTIVE " : "LOCKED ") + "[" + entry.worldType + " " + entry.localIndex + "] " + entry.worldName + " - " + entry.status + " - " + countText + (capped ? " (RTP skip)" : "")).withStyle(color), false);
        }

        source.sendSuccess(() -> Component.literal("Use /rtpworlds unlock <password> <world> or /rtpworlds lock <password> <world>. Default password is CHANGE_ME and will not work until changed in config/champutils/teleport_config.json.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int setRtpWorldActive(CommandSourceStack source, String password, String worldName, boolean active) {
        if (!TeleportConfig.isCorrectRtpWorldUnlockPassword(password)) {
            source.sendFailure(Component.literal("Invalid RTP world unlock password. Set rtpWorldUnlockPassword in config/champutils/teleport_config.json first; CHANGE_ME is intentionally disabled."));
            return 0;
        }

        String normalized = normalizeMultiworldName(worldName);
        boolean changed = SurvivalWorldManager.setActive(normalized, active);

        if (!changed) {
            source.sendFailure(Component.literal("Unknown RTP world: " + worldName));
            return 0;
        }

        String action = active ? "unlocked for RTP" : "locked from RTP";
        source.sendSuccess(() -> Component.literal(normalized + " is now " + action + ".").withStyle(active ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
        return 1;
    }

    private static String normalizeMultiworldName(String worldName) {
        if (worldName == null) return "";
        String clean = worldName.trim();
        if (clean.isBlank()) return clean;
        return clean.contains(":") ? clean : "multiworld:" + clean;
    }

    private static int rtpSurvival(CommandSourceStack source, String survivalType, String biomeName) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only players can use /rtp."));
            return 0;
        }

        if (ProfileLobbyLockManager.isLocked(player) && !ProfileLobbyLockManager.hasBypass(player)) {
            player.sendSystemMessage(Component.literal("Select a profile before using RTP.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (!player.hasPermissions(4) && AdventureGuideManager.isLockedUntilTalk(player)) {
            AdventureGuideManager.denyUntilTalk(player);
            return 0;
        }

        if (PlayerProfileManager.isIslander(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("Islander profiles cannot use RTP. Islanders are limited to spawn and Islander worlds.").withStyle(ChatFormatting.RED));
            return 0;
        }

        UUID playerId = player.getUUID();

        if (ACTIVE_SEARCHES.containsKey(playerId)) {
            player.sendSystemMessage(Component.literal("RTP is already searching for a safe location...").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (ACTIVE_SEARCHES.size() >= MAX_ACTIVE_RTP_SEARCHES && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("RTP is busy right now. Try again in a few seconds.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        int cooldown = TeleportConfig.getRtpCooldownSeconds();
        long now = System.currentTimeMillis();
        long last = LAST_USE_MS.getOrDefault(playerId, 0L);
        long waitMs = (cooldown * 1000L) - (now - last);

        if (!player.hasPermissions(4) && waitMs > 0) {
            long waitSeconds = Math.max(1L, (waitMs + 999L) / 1000L);
            player.sendSystemMessage(Component.literal("You can use /rtp again in " + waitSeconds + "s.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel startLevel = player.serverLevel();
        String currentDimension = startLevel.dimension().location().toString();
        String normalizedType = SurvivalWorldManager.normalizeType(survivalType);
        if (biomeName != null && !biomeName.trim().isBlank()) {
            player.sendSystemMessage(Component.literal("Biome-specific RTP is disabled for now because it is too laggy. Use /rtp " + normalizedType + " for a normal random teleport.").withStyle(ChatFormatting.RED));
            return 0;
        }
        ResourceKey<Biome> desiredBiome = null;
        if (biomeName != null && desiredBiome == null) {
            return 0;
        }
        if (desiredBiome != null && isRtpBiomeBlacklisted(desiredBiome.location())) {
            player.sendSystemMessage(Component.literal("That biome is disabled for RTP because it is too slow or unreliable to search safely. Try /rtp " + normalizedType + " without a biome filter.").withStyle(ChatFormatting.RED));
            return 0;
        }
        if (desiredBiome != null && !biomeAllowedForRtpType(desiredBiome.location(), normalizedType)) {
            sendBiomeNotAllowedMessage(player, desiredBiome.location(), normalizedType);
            return 0;
        }

        ServerLevel targetLevel = resolvePrimaryRtpWorld(player.server, normalizedType);
        if (targetLevel == null) {
            String expectedWorld = primaryRtpWorldName(normalizedType);
            player.sendSystemMessage(Component.literal("RTP world " + expectedWorld + " is not loaded on this server. Please contact staff.").withStyle(ChatFormatting.RED));
            return 0;
        }

        SearchBounds bounds = SearchBounds.from(targetLevel);
        if (bounds == null) {
            player.sendSystemMessage(Component.literal("RTP could not read a valid world border.").withStyle(ChatFormatting.RED));
            return 0;
        }

        player.sendSystemMessage(Component.literal("Looking for a RTP location...").withStyle(ChatFormatting.YELLOW));

        // Do not run loaded-chunk heightmap/block validation inside the command tick.
        // Even "loaded only" probing can stall when many dimensions are saving or chunk
        // tickets are busy. The existing SearchTask spreads all candidate checks across
        // later server ticks and throttles chunk generation globally.
        SearchTask task = new SearchTask(playerId, targetLevel, bounds, normalizedType, desiredBiome, MAX_RTP_SEARCH_ATTEMPTS);
        ACTIVE_SEARCHES.put(playerId, task);
        return 1;
    }


    private static ServerLevel resolvePrimaryRtpWorld(MinecraftServer server, String normalizedType) {
        return TeleportConfig.resolveLevel(server, primaryRtpWorldName(normalizedType));
    }

    private static String primaryRtpWorldName(String normalizedType) {
        SurvivalWorldConfig.Data config = SurvivalWorldConfig.get();
        String type = SurvivalWorldManager.normalizeType(normalizedType);
        String prefix = switch (type) {
            case "nether" -> config.netherPrefix;
            case "end" -> config.endPrefix;
            default -> config.overworldPrefix;
        };
        int index = switch (type) {
            case "nether" -> Math.max(1, config.netherStartIndex);
            case "end" -> Math.max(1, config.endStartIndex);
            default -> Math.max(1, config.overworldStartIndex);
        };
        return prefix + "_" + index;
    }

    private static BlockPos findSimpleRtpPosition(ServerPlayer player, ServerLevel level, SearchBounds bounds, String worldType) {
        if (player == null || level == null || bounds == null) return null;
        BlockPos origin = player.blockPosition();
        final int minDistance = 2000;
        int maxDistance = maxDistanceInsideBounds(origin, bounds);
        if (maxDistance < minDistance) return null;

        SearchTask simpleTask = new SearchTask(player.getUUID(), level, bounds, worldType, null, 1);
        WorldBorder border = level.getWorldBorder();
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            int distance = minDistance + RANDOM.nextInt(Math.max(1, maxDistance - minDistance + 1));
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * distance);
            if (!bounds.contains(x, z)) continue;
            long dx = (long) x - origin.getX();
            long dz = (long) z - origin.getZ();
            long distSq = dx * dx + dz * dz;
            if (distSq < (long) minDistance * minDistance) continue;

            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
            try {
                if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                    continue;
                }
            } catch (Throwable ignored) {
                continue;
            }

            BlockPos safe = validateCandidate(simpleTask, level, border, x, z);
            if (safe != null) return safe;
        }
        return null;
    }

    private static int maxDistanceInsideBounds(BlockPos origin, SearchBounds bounds) {
        double max = 0.0D;
        int[][] corners = new int[][] {
                {bounds.minX, bounds.minZ},
                {bounds.minX, bounds.maxZ},
                {bounds.maxX, bounds.minZ},
                {bounds.maxX, bounds.maxZ}
        };
        for (int[] corner : corners) {
            double dx = corner[0] - origin.getX();
            double dz = corner[1] - origin.getZ();
            max = Math.max(max, Math.sqrt(dx * dx + dz * dz));
        }
        return (int) Math.floor(max);
    }

    public static void tick(MinecraftServer server) {
        if (server == null || ACTIVE_SEARCHES.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, SearchTask>> iterator = ACTIVE_SEARCHES.entrySet().iterator();
        TickBudget budget = new TickBudget(GLOBAL_CHUNK_GENERATION_BUDGET_PER_TICK);

        while (iterator.hasNext()) {
            Map.Entry<UUID, SearchTask> entry = iterator.next();
            SearchTask task = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                ACTIVE_SEARCHES.remove(entry.getKey());
                AFTER_SUCCESS_CALLBACKS.remove(entry.getKey());
                continue;
            }

            if (task.tick(player, budget)) {
                ACTIVE_SEARCHES.remove(entry.getKey());
            }
        }
    }
    private static BlockPos pollCachedSafePosition(SearchTask task) {
        // RTP should be truly random per request, not pulled from a previously discovered location.
        // Keeping this method as a no-op avoids repeat/cache-biased teleports while preserving the rest
        // of the async search flow.
        return null;
        /*
        if (task == null || task.desiredBiome != null) return null;
        ConcurrentLinkedQueue<BlockPos> queue = SAFE_POSITION_CACHE.get(cacheKey(task.level, task.worldType));
        if (queue == null) return null;
        WorldBorder border = task.level.getWorldBorder();
        for (int i = 0; i < 16; i++) {
            BlockPos pos = queue.poll();
            if (pos == null) return null;
            if (!isInsideRtpBorder(border, pos.getX(), pos.getZ())) continue;
            ChunkPos chunkPos = new ChunkPos(pos);
            if (!task.level.hasChunk(chunkPos.x, chunkPos.z)) continue;
            if ("nether".equalsIgnoreCase(task.worldType)) {
                if (findNetherSafePosition(task, task.level, pos.getX(), pos.getZ()) != null) return pos;
            } else {
                BlockPos safe = findSurfaceSafePosition(task, task.level, border, pos.getX(), pos.getZ());
                if (safe != null) return safe;
            }
        }
        return null;
        */
    }

    private static String cacheKey(ServerLevel level, String worldType) {
        return level.dimension().location().toString().toLowerCase(java.util.Locale.ROOT) + "|" + (worldType == null ? "" : worldType.toLowerCase(java.util.Locale.ROOT));
    }

    private static void offerCachedSafePosition(SearchTask task, BlockPos pos) {
        // Do not cache RTP destinations; cached locations make future RTPs feel non-random/repeated.
        if (true) return;
        /*
        if (task == null || pos == null || task.desiredBiome != null) return;
        ConcurrentLinkedQueue<BlockPos> queue = SAFE_POSITION_CACHE.computeIfAbsent(cacheKey(task.level, task.worldType), ignored -> new ConcurrentLinkedQueue<>());
        if (queue.size() < MAX_CACHED_SAFE_POSITIONS_PER_WORLD) {
            queue.offer(pos.immutable());
        }
        */
    }

    private static BlockPos findSafePosition(SearchTask task, TickBudget budget) {
        ServerLevel level = task.level;
        SearchBounds bounds = task.bounds;
        WorldBorder border = level.getWorldBorder();

        BlockPos completedAsyncCandidate = task.consumeCompletedAsyncChunkCandidate();
        if (completedAsyncCandidate != null) {
            BlockPos feet = validateCandidate(task, level, border, completedAsyncCandidate.getX(), completedAsyncCandidate.getZ());
            if (feet != null) {
                offerCachedSafePosition(task, feet);
                return feet;
            }
        }

        // If a chunk generation request is still running, do absolutely no more RTP work this tick.
        // This is intentionally slow: chunkgen is the lag source, so one search waits for its one pending chunk.
        if (task.hasPendingAsyncChunk()) {
            return null;
        }

        int attemptsThisTick = task.desiredBiome == null ? ATTEMPTS_PER_TICK : BIOME_ATTEMPTS_PER_TICK;
        for (int attempt = 0; attempt < attemptsThisTick; attempt++) {
            task.attempts++;

            int x = randomBetween(bounds.minX(task.attempts), bounds.maxX(task.attempts));
            int z = randomBetween(bounds.minZ(task.attempts), bounds.maxZ(task.attempts));

            if (!task.bounds.contains(x, z)) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
            try {
                if (!level.hasChunk(chunkPos.x, chunkPos.z)) {
                    if (task.canGenerateAnotherChunk() && task.canGenerateChunkThisTick() && budget.tryUseGeneratedChunk()) {
                        task.requestAsyncChunk(chunkPos, x, z);
                    } else {
                        task.skippedUnloadedChunks++;
                    }
                    continue;
                }
            } catch (Exception ignored) {
                continue;
            }

            BlockPos feet = validateCandidate(task, level, border, x, z);

            if (feet == null) continue;
            offerCachedSafePosition(task, feet);
            return feet;
        }

        return null;
    }

    private static BlockPos validateCandidate(SearchTask task, ServerLevel level, WorldBorder border, int x, int z) {
        return "nether".equalsIgnoreCase(task.worldType)
                ? findNetherSafePosition(task, level, x, z)
                : findSurfaceSafePosition(task, level, border, x, z);
    }


    private static BlockPos findSurfaceSafePosition(SearchTask task, ServerLevel level, WorldBorder border, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos ground = feet.below();
        BlockPos head = feet.above();

        if (!task.bounds.contains(feet.getX(), feet.getZ())) return null;
        if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) return null;
        if (!matchesRequestedBiome(task, level, feet)) return null;
        if (task.desiredBiome == null && (isOceanBiome(level, feet) || isOceanBiome(level, ground))) return null;
        if (!hasRoomForPlayer(level, feet, head)) return null;
        if (!hasSafeLanding(task, level, feet, ground)) return null;
        return feet;
    }

    private static BlockPos findNetherSafePosition(SearchTask task, ServerLevel level, int x, int z) {
        int minY = Math.max(level.getMinBuildHeight() + 2, 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 3, NETHER_MAX_SAFE_Y);

        for (int y = maxY; y >= minY; y -= 2) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.above();
            BlockPos ground = feet.below();

            if (!task.bounds.contains(feet.getX(), feet.getZ())) continue;
            if (!matchesRequestedBiome(task, level, feet)) continue;
            if (!hasRoomForPlayer(level, feet, head)) continue;
            if (!hasSafeLanding(task, level, feet, ground)) continue;
            if (level.getFluidState(feet).is(FluidTags.LAVA) || level.getFluidState(head).is(FluidTags.LAVA)) continue;
            return feet;
        }

        return null;
    }

    private static boolean hasRoomForPlayer(ServerLevel level, BlockPos feet, BlockPos head) {
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        return !feetState.blocksMotion() && !headState.blocksMotion();
    }

    private static boolean hasSafeLanding(SearchTask task, ServerLevel level, BlockPos feet, BlockPos ground) {
        BlockState groundState = level.getBlockState(ground);
        FluidState feetFluid = level.getFluidState(feet);
        FluidState groundFluid = level.getFluidState(ground);

        if (feetFluid.is(FluidTags.LAVA) || groundFluid.is(FluidTags.LAVA)) {
            return false;
        }

        boolean waterLanding = feetFluid.is(FluidTags.WATER) || groundFluid.is(FluidTags.WATER) || groundState.is(Blocks.WATER);
        if (waterLanding) {
            // Water is acceptable for RTP now. This trades strict landing quality for much higher
            // success rates and avoids long biome/surface searches. Lava is still blocked above.
            return !"nether".equalsIgnoreCase(task.worldType);
        }

        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(feet.above());

        if (groundState.is(Blocks.BEDROCK)
                || groundState.is(Blocks.LAVA)
                || groundState.is(Blocks.MAGMA_BLOCK)
                || groundState.is(Blocks.CACTUS)
                || groundState.is(Blocks.CAMPFIRE)
                || groundState.is(Blocks.SOUL_CAMPFIRE)
                || groundState.is(Blocks.FIRE)
                || groundState.is(Blocks.SOUL_FIRE)
                || feetState.is(Blocks.FIRE)
                || feetState.is(Blocks.SOUL_FIRE)
                || feetState.is(Blocks.CAMPFIRE)
                || feetState.is(Blocks.SOUL_CAMPFIRE)
                || headState.is(Blocks.FIRE)
                || headState.is(Blocks.SOUL_FIRE)
                || headState.is(Blocks.CAMPFIRE)
                || headState.is(Blocks.SOUL_CAMPFIRE)) {
            return false;
        }

        // Require real ground directly under the player. This prevents RTP from choosing
        // an air column or a spot where the player immediately falls to death.
        return !groundState.isAir() && groundState.blocksMotion();
    }

    private static boolean dimensionMatchesType(ServerLevel level, String normalizedType) {
        if (level == null) return false;
        String dim = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        String path = level.dimension().location().getPath().toLowerCase(Locale.ROOT);
        if ("nether".equalsIgnoreCase(normalizedType)) return dim.contains("nether");
        if ("end".equalsIgnoreCase(normalizedType)) return dim.contains("end");
        return !path.contains("nether") && !path.contains("end");
    }

    private static boolean isInsideRtpBorder(WorldBorder border, int x, int z) {
        double minX = border.getMinX() + BORDER_PADDING;
        double maxX = border.getMaxX() - BORDER_PADDING;
        double minZ = border.getMinZ() + BORDER_PADDING;
        double maxZ = border.getMaxZ() - BORDER_PADDING;
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ
                ;
    }

    private static boolean isOceanBiome(ServerLevel level, BlockPos pos) {
        return level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    private static boolean matchesRequestedBiome(SearchTask task, ServerLevel level, BlockPos pos) {
        return task.desiredBiome == null || level.getBiome(pos).is(task.desiredBiome);
    }

    private static boolean isBiomeKeyRegistered(ServerLevel level, ResourceKey<Biome> biomeKey) {
        if (level == null || biomeKey == null) {
            return false;
        }
        try {
            return level.registryAccess().registryOrThrow(Registries.BIOME).containsKey(biomeKey.location());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ResourceKey<Biome> resolveBiome(ServerPlayer player, String biomeName) {
        if (biomeName == null || biomeName.trim().isBlank()) {
            return null;
        }

        String input = normalizeBiomeSearch(biomeName);
        var registry = player.server.registryAccess().registryOrThrow(Registries.BIOME);
        ResourceKey<Biome> best = null;
        String bestDisplay = null;

        for (ResourceLocation id : registry.keySet().stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList()) {
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
            String display = biomeDisplayName(id);
            String normalizedDisplay = normalizeBiomeSearch(display);
            String normalizedPath = normalizeBiomeSearch(id.getPath());
            String normalizedFull = normalizeBiomeSearch(id.toString());

            if (normalizedFull.equals(input) || normalizedPath.equals(input) || normalizedDisplay.equals(input)) {
                return key;
            }
            if (best == null && (normalizedDisplay.contains(input) || normalizedPath.contains(input) || normalizedFull.contains(input))) {
                best = key;
                bestDisplay = display;
            }
        }

        if (best != null) {
            return best;
        }

        player.sendSystemMessage(Component.literal("Unknown biome: " + biomeName + ". Example: Forest, Plains, Cherry Grove").withStyle(ChatFormatting.RED));
        return null;
    }


    private static void sendBiomeNotAllowedMessage(ServerPlayer player, ResourceLocation biomeId, String survivalType) {
        String display = biomeDisplayName(biomeId);
        if ("end".equalsIgnoreCase(survivalType)) {
            player.sendSystemMessage(Component.literal("The End RTP does not allow biome selection. Use /rtp end.").withStyle(ChatFormatting.RED));
            return;
        }

        if ("nether".equalsIgnoreCase(survivalType)) {
            player.sendSystemMessage(Component.literal(display + " is not a Nether biome. Use a Nether biome like Crimson Forest, Warped Forest, Soul Sand Valley, Basalt Deltas, or Nether Wastes.").withStyle(ChatFormatting.RED));
            return;
        }

        player.sendSystemMessage(Component.literal(display + " is not an Overworld RTP biome. Use an Overworld biome like Forest, Plains, Taiga, or Cherry Grove.").withStyle(ChatFormatting.RED));
    }

    private static String normalizeBiomeSearch(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(java.util.Locale.ROOT).replace("minecraft:", "").replaceAll("[^a-z0-9]", "");
    }

    private static String biomeDisplayName(ResourceLocation id) {
        if (id == null) return "";
        String path = id.getPath().replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder();
        for (String part : path.split(" ")) {
            if (part.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.length() > 1 ? part.substring(1) : "");
        }
        return out.length() == 0 ? id.toString() : out.toString();
    }

    private static int randomBetween(int min, int max) {
        return min + RANDOM.nextInt(Math.max(1, max - min + 1));
    }

    private static int showCooldown(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("RTP cooldown: " + TeleportConfig.getRtpCooldownSeconds() + "s").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int setCooldown(CommandSourceStack source, int seconds) {
        TeleportConfig.setRtpCooldownSeconds(seconds);
        source.sendSuccess(() -> Component.literal("Set RTP cooldown to " + seconds + "s.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int listBlocked(CommandSourceStack source) {
        String blocked = TeleportConfig.blockedRtpDimensions().isEmpty()
                ? "none"
                : String.join(", ", TeleportConfig.blockedRtpDimensions());

        source.sendSuccess(() -> Component.literal("Blocked RTP dimensions: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(blocked).withStyle(ChatFormatting.AQUA)), false);
        source.sendSuccess(() -> Component.literal("Fallback: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(TeleportConfig.getRtpFallbackDimension()).withStyle(ChatFormatting.AQUA)), false);
        return 1;
    }

    private static int blockDimension(CommandSourceStack source, String dimension) {
        String normalized = TeleportConfig.normalizeDimension(dimension);
        TeleportConfig.blockRtpDimension(normalized);
        source.sendSuccess(() -> Component.literal("Blocked RTP in dimension: " + normalized).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int unblockDimension(CommandSourceStack source, String dimension) {
        String normalized = TeleportConfig.normalizeDimension(dimension);
        boolean removed = TeleportConfig.unblockRtpDimension(normalized);
        if (removed) {
            source.sendSuccess(() -> Component.literal("Unblocked RTP in dimension: " + normalized).withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("That dimension was not blocked: " + normalized).withStyle(ChatFormatting.RED));
        }
        return removed ? 1 : 0;
    }

    private static int setFallback(CommandSourceStack source, String dimension) {
        String normalized = TeleportConfig.normalizeDimension(dimension);
        TeleportConfig.setRtpFallbackDimension(normalized);
        source.sendSuccess(() -> Component.literal("Set RTP fallback dimension to: " + normalized).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static boolean isSpawnHubDimension(String dimension) {
        if (dimension == null) return false;

        String normalized = dimension.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("multiworld:spawn1")
                || normalized.equals("minecraft:spawn1")
                || normalized.equals("spawn1")
                || normalized.endsWith(":spawn1")
                || normalized.contains("spawn1");
    }

    private static final class SearchTask {
        private final UUID playerId;
        private final ServerLevel level;
        private final SearchBounds bounds;
        private final String worldType;
        private final ResourceKey<Biome> desiredBiome;
        private final int maxAttempts;
        private int attempts = 0;
        private int ticks = 0;
        private int generatedChunksThisTick = 0;
        private int generatedChunksTotal = 0;
        private int skippedUnloadedChunks = 0;
        private int lastGeneratedChunkTick;
        private CompletableFuture<?> pendingChunkFuture = null;
        private int pendingCandidateX = 0;
        private int pendingCandidateZ = 0;

        private SearchTask(UUID playerId, ServerLevel level, SearchBounds bounds, String worldType, ResourceKey<Biome> desiredBiome, int maxAttempts) {
            this.playerId = playerId;
            this.level = level;
            this.bounds = bounds;
            this.worldType = worldType;
            this.desiredBiome = desiredBiome;
            this.maxAttempts = Math.max(1, maxAttempts);
            this.lastGeneratedChunkTick = -(desiredBiome == null ? RTP_CHUNK_GENERATION_COOLDOWN_TICKS : BIOME_CHUNK_GENERATION_COOLDOWN_TICKS);
        }

        private boolean tick(ServerPlayer player, TickBudget budget) {
            ticks++;
            generatedChunksThisTick = 0;

            BlockPos target = findSafePosition(this, budget);
            if (target == null) {
                int maxTicks = desiredBiome == null ? MAX_RTP_SEARCH_TICKS : MAX_BIOME_RTP_SEARCH_TICKS;
                if (attempts >= maxAttempts || ticks >= maxTicks) {
                    String biomeText = desiredBiome == null ? "" : " in " + desiredBiome.location();
                    player.sendSystemMessage(Component.literal("RTP could not find a safe location" + biomeText + " after checking " + attempts + " spots within the slow anti-lag search cap.").withStyle(ChatFormatting.RED));
                    player.sendSystemMessage(Component.literal("Skipped unloaded chunks: " + skippedUnloadedChunks + ". Generated chunks for this search: " + generatedChunksTotal + ". Try again or increase pregenerated survival area for very rare biomes.").withStyle(ChatFormatting.GRAY));
                    AFTER_SUCCESS_CALLBACKS.remove(player.getUUID());
                    return true;
                }
                if (ticks % 100 == 0) {
                    if (desiredBiome == null) {
                        // Keep RTP chat simple for players; the initial search message is enough.
                    } else {
                        // Keep RTP chat simple for players; the initial search message is enough.
                    }
                }
                return false;
            }

            LAST_USE_MS.put(player.getUUID(), System.currentTimeMillis());
            SafeTeleportManager.teleport(player, level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, player.getYRot(), player.getXRot());
            runAfterSuccess(player);
            return true;
        }


        private boolean hasPendingAsyncChunk() {
            return pendingChunkFuture != null && !pendingChunkFuture.isDone();
        }

        private void requestAsyncChunk(ChunkPos chunkPos, int candidateX, int candidateZ) {
            pendingCandidateX = candidateX;
            pendingCandidateZ = candidateZ;
            pendingChunkFuture = level.getChunkSource().getChunkFuture(chunkPos.x, chunkPos.z, ChunkStatus.FULL, true);
            generatedChunksTotal++;
            generatedChunksThisTick++;
            lastGeneratedChunkTick = ticks;
        }

        private BlockPos consumeCompletedAsyncChunkCandidate() {
            CompletableFuture<?> future = pendingChunkFuture;
            if (future == null || !future.isDone()) {
                return null;
            }

            int x = pendingCandidateX;
            int z = pendingCandidateZ;
            pendingChunkFuture = null;

            if (future.isCompletedExceptionally()) {
                return null;
            }

            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
            return level.hasChunk(chunkPos.x, chunkPos.z) ? new BlockPos(x, level.getMinBuildHeight(), z) : null;
        }

        private boolean canGenerateAnotherChunk() {
            int limit = desiredBiome == null ? MAX_GENERATED_CHUNKS_PER_SEARCH : MAX_GENERATED_CHUNKS_PER_BIOME_SEARCH;
            return generatedChunksTotal < limit;
        }

        private boolean canGenerateChunkThisTick() {
            int cooldown = desiredBiome == null ? RTP_CHUNK_GENERATION_COOLDOWN_TICKS : BIOME_CHUNK_GENERATION_COOLDOWN_TICKS;
            return generatedChunksThisTick <= 0
                    && ticks - lastGeneratedChunkTick >= cooldown;
        }
    }

    private static final class TickBudget {
        private int generatedChunksRemaining;

        private TickBudget(int generatedChunksRemaining) {
            this.generatedChunksRemaining = Math.max(0, generatedChunksRemaining);
        }

        private boolean tryUseGeneratedChunk() {
            if (generatedChunksRemaining <= 0) {
                return false;
            }
            generatedChunksRemaining--;
            return true;
        }
    }

    private static final class SearchBounds {
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;
        private SearchBounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        private int minX(int attempts) { return minX; }
        private int maxX(int attempts) { return maxX; }
        private int minZ(int attempts) { return minZ; }
        private int maxZ(int attempts) { return maxZ; }
        private boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

        private static SearchBounds from(ServerLevel level) {
            if (level == null) return null;

            String dimension = level.dimension().location().toString();

            // Survival Multiworld Nether/End worlds have sometimes kept a stale/default
            // vanilla world-border state, which made RTP collapse to the 10k/10k edge.
            // For configured survival RTP worlds, always use the ChampUtils survival
            // radius centered at 0,0 instead of trusting the level's mutable border.
            if (SurvivalWorldManager.find(level) != null) {
                int radius = Math.max(64, com.champutils.survival.SurvivalWorldConfig.get().borderRadius);
                return padded(-radius, radius, -radius, radius);
            }

            ChampWorldBorderConfig.BorderEntry configured = ChampWorldBorderConfig.get(dimension);
            if (configured != null) {
                int rawMinX = (int) Math.ceil(configured.centerX - configured.radius);
                int rawMaxX = (int) Math.floor(configured.centerX + configured.radius);
                int rawMinZ = (int) Math.ceil(configured.centerZ - configured.radius);
                int rawMaxZ = (int) Math.floor(configured.centerZ + configured.radius);
                SearchBounds bounds = padded(rawMinX, rawMaxX, rawMinZ, rawMaxZ);
                if (bounds != null) return bounds;
            }

            WorldBorder border = level.getWorldBorder();
            return padded(
                    (int) Math.ceil(border.getMinX()),
                    (int) Math.floor(border.getMaxX()),
                    (int) Math.ceil(border.getMinZ()),
                    (int) Math.floor(border.getMaxZ())
            );
        }

        private static SearchBounds padded(int rawMinX, int rawMaxX, int rawMinZ, int rawMaxZ) {
            int borderMinX = rawMinX + BORDER_PADDING;
            int borderMaxX = rawMaxX - BORDER_PADDING;
            int borderMinZ = rawMinZ + BORDER_PADDING;
            int borderMaxZ = rawMaxZ - BORDER_PADDING;

            if (borderMinX >= borderMaxX || borderMinZ >= borderMaxZ) {
                return null;
            }

            return new SearchBounds(borderMinX, borderMaxX, borderMinZ, borderMaxZ);
        }
    }
}
