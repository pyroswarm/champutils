package com.champutils.teleport;

import com.champutils.teleport.SafeTeleportManager;
import com.champutils.survival.SurvivalWorldManager;
import com.champutils.worldborder.ChampWorldBorderManager;
import com.champutils.profile.ProfileLobbyLockManager;
import com.champutils.profile.PlayerProfileManager;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RandomTeleportCommand {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, Long> LAST_USE_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, SearchTask> ACTIVE_SEARCHES = new ConcurrentHashMap<>();

    private static final int ATTEMPTS_PER_TICK = 8;
    private static final int GLOBAL_CHUNK_GENERATION_BUDGET_PER_TICK = 1;
    private static final int MAX_GENERATED_CHUNKS_PER_SEARCH = 32;
    private static final int MAX_ACTIVE_RTP_SEARCHES = 1;
    private static final int BORDER_PADDING = 32;
    private static final int FALLBACK_RTP_BORDER_RADIUS = 4999;
    private static final int NETHER_MAX_SAFE_Y = 119;
    private static final int MIN_RTP_DISTANCE_BLOCKS = 250;
    private static final int PREGENERATED_AREA_ATTEMPTS = 40;
    private static final int MAX_RTP_SEARCH_ATTEMPTS = 900;
    private static final int MAX_RTP_SEARCH_TICKS = 600;
    private static final int MAX_BIOME_RTP_SEARCH_ATTEMPTS = 1800;

    private RandomTeleportCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("rtp")
                    .executes(ctx -> rtpSurvival(ctx.getSource(), "overworld", null))
                    .then(literal("overworld")
                            .executes(ctx -> rtpSurvival(ctx.getSource(), "overworld", null))
                            .then(argument("biome", StringArgumentType.greedyString())
                                    .suggests((context, builder) -> suggestBiomes(context.getSource(), builder, "overworld"))
                                    .executes(ctx -> rtpSurvival(ctx.getSource(), "overworld", StringArgumentType.getString(ctx, "biome")))))
                    .then(literal("nether")
                            .executes(ctx -> rtpSurvival(ctx.getSource(), "nether", null))
                            .then(argument("biome", StringArgumentType.greedyString())
                                    .suggests((context, builder) -> suggestBiomes(context.getSource(), builder, "nether"))
                                    .executes(ctx -> rtpSurvival(ctx.getSource(), "nether", StringArgumentType.getString(ctx, "biome")))))
                    .then(literal("end")
                            .executes(ctx -> rtpSurvival(ctx.getSource(), "end", null))
                            .then(argument("biome", StringArgumentType.greedyString())
                                    .executes(ctx -> rtpSurvival(ctx.getSource(), "end", StringArgumentType.getString(ctx, "biome"))))));

            dispatcher.register(literal("rtpcooldown")
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                    .executes(ctx -> showCooldown(ctx.getSource()))
                    .then(argument("seconds", IntegerArgumentType.integer(0))
                            .executes(ctx -> setCooldown(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))));

            dispatcher.register(literal("rtpdimension")
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
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
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
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



    private static int rtpUsage(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Use /rtp for overworld, /rtp nether [biome], or /rtp end. Example: /rtp nether Crimson Forest").withStyle(ChatFormatting.YELLOW), false);
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
        String text = id.toString();
        String path = id.getPath();
        if ("nether".equalsIgnoreCase(survivalType)) {
            return text.startsWith("minecraft:") && (path.contains("nether") || path.equals("crimson_forest") || path.equals("warped_forest") || path.equals("soul_sand_valley") || path.equals("basalt_deltas"));
        }
        if ("end".equalsIgnoreCase(survivalType)) {
            return false;
        }
        return !path.contains("nether") && !path.contains("end") && !path.equals("crimson_forest") && !path.equals("warped_forest") && !path.equals("soul_sand_valley") && !path.equals("basalt_deltas");
    }

    private static int listRtpWorlds(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Survival RTP Worlds").withStyle(ChatFormatting.GOLD), false);
        for (SurvivalWorldManager.Entry entry : SurvivalWorldManager.entries()) {
            ChatFormatting color = entry.activeForRtp ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
            source.sendSuccess(() -> Component.literal((entry.activeForRtp ? "ACTIVE " : "LOCKED ") + "[" + entry.worldType + " " + entry.localIndex + "] " + entry.worldName + " - " + entry.status).withStyle(color), false);
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

        if (PlayerProfileManager.isIslander(player) && !com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) {
            player.sendSystemMessage(Component.literal("Islander profiles cannot use RTP. Islanders are limited to spawn and Islander worlds.").withStyle(ChatFormatting.RED));
            return 0;
        }

        UUID playerId = player.getUUID();

        if (ACTIVE_SEARCHES.containsKey(playerId)) {
            player.sendSystemMessage(Component.literal("RTP is already searching for a safe location...").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        if (ACTIVE_SEARCHES.size() >= MAX_ACTIVE_RTP_SEARCHES && !com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin")) {
            player.sendSystemMessage(Component.literal("RTP is busy right now. Try again in a few seconds.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        int cooldown = TeleportConfig.getRtpCooldownSeconds();
        long now = System.currentTimeMillis();
        long last = LAST_USE_MS.getOrDefault(playerId, 0L);
        long waitMs = (cooldown * 1000L) - (now - last);

        if (!com.champutils.permissions.LuckPermsHook.hasPermission(player, "champutils.admin") && waitMs > 0) {
            long waitSeconds = Math.max(1L, (waitMs + 999L) / 1000L);
            player.sendSystemMessage(Component.literal("You can use /rtp again in " + waitSeconds + "s.").withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel startLevel = player.serverLevel();
        String currentDimension = startLevel.dimension().location().toString();
        String normalizedType = SurvivalWorldManager.normalizeType(survivalType);
        ResourceKey<Biome> desiredBiome = resolveBiome(player, biomeName);
        if (biomeName != null && desiredBiome == null) {
            return 0;
        }
        if (desiredBiome != null && !biomeAllowedForRtpType(desiredBiome.location(), normalizedType)) {
            sendBiomeNotAllowedMessage(player, desiredBiome.location(), normalizedType);
            return 0;
        }

        SurvivalWorldManager.RtpTarget survivalTarget = SurvivalWorldManager.pickRtpTarget(player.server, normalizedType);
        if (survivalTarget == null || survivalTarget.level == null || survivalTarget.entry == null) {
            player.sendSystemMessage(Component.literal("No " + normalizedType + " survival world is currently loaded for RTP.").withStyle(ChatFormatting.RED));
            player.sendSystemMessage(Component.literal("ChampUtils will create/load the permanent survival worlds automatically when Multiworld is available.").withStyle(ChatFormatting.GRAY));
            return 0;
        }

        ServerLevel targetLevel = survivalTarget.level;
        double startXForDistance = 0.0D;
        double startZForDistance = 0.0D;

        if (targetLevel == startLevel
                && !isSpawnHubDimension(currentDimension)
                && !TeleportConfig.isRtpBlocked(currentDimension)) {
            startXForDistance = player.getX();
            startZForDistance = player.getZ();
        }

        SearchBounds bounds = SearchBounds.from(targetLevel);
        if (bounds == null) {
            player.sendSystemMessage(Component.literal("RTP could not read a valid world border.").withStyle(ChatFormatting.RED));
            return 0;
        }

        int maxAttempts = desiredBiome == null ? MAX_RTP_SEARCH_ATTEMPTS : MAX_BIOME_RTP_SEARCH_ATTEMPTS;
        if (desiredBiome != null && !isBiomeKeyRegistered(targetLevel, desiredBiome)) {
            player.sendSystemMessage(Component.literal("That biome is not registered in the target survival world, so RTP will not search for it.").withStyle(ChatFormatting.RED));
            return 0;
        }

        LAST_USE_MS.put(playerId, now);
        ACTIVE_SEARCHES.put(playerId, new SearchTask(playerId, targetLevel, bounds, startXForDistance, startZForDistance, normalizedType, desiredBiome, maxAttempts));

        String biomeText = desiredBiome == null ? "" : " in biome " + desiredBiome.location();
        player.sendSystemMessage(Component.literal("Searching for a fast random " + normalizedType + " survival RTP location" + biomeText + "...").withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("Target survival world: " + survivalTarget.entry.worldName).withStyle(ChatFormatting.GRAY));
        return 1;
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
                continue;
            }

            if (task.tick(player, budget)) {
                ACTIVE_SEARCHES.remove(entry.getKey());
            }
        }
    }

    private static BlockPos findSafePosition(SearchTask task, TickBudget budget) {
        ServerLevel level = task.level;
        SearchBounds bounds = task.bounds;
        WorldBorder border = level.getWorldBorder();

        for (int attempt = 0; attempt < ATTEMPTS_PER_TICK; attempt++) {
            task.attempts++;

            int x = randomBetween(bounds.minX(task.attempts), bounds.maxX(task.attempts));
            int z = randomBetween(bounds.minZ(task.attempts), bounds.maxZ(task.attempts));

            if (!isInsideRtpBorder(border, x, z)) {
                continue;
            }

            if (!isFarEnoughFromStart(task, x, z)) {
                continue;
            }

            ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
            try {
                boolean loaded = level.hasChunk(chunkPos.x, chunkPos.z);
                if (!loaded) {
                    // First prefer already-loaded or pregenerated chunks. After a short warmup, allow
                    // a very small global chunk-generation budget so RTP completes reliably without
                    // causing large main-thread spikes.
                    if (task.attempts <= PREGENERATED_AREA_ATTEMPTS || !task.canGenerateAnotherChunk() || !budget.tryUseGeneratedChunk()) {
                        task.skippedUnloadedChunks++;
                        continue;
                    }
                    level.getChunk(chunkPos.x, chunkPos.z);
                    task.generatedChunksThisTick++;
                    task.generatedChunksTotal++;
                }
            } catch (Exception ignored) {
                continue;
            }

            BlockPos feet = "nether".equalsIgnoreCase(task.worldType)
                    ? findNetherSafePosition(task, level, x, z)
                    : findSurfaceSafePosition(task, level, border, x, z);

            if (feet == null) continue;
            return feet;
        }

        return null;
    }


    private static BlockPos findSurfaceSafePosition(SearchTask task, ServerLevel level, WorldBorder border, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos feet = new BlockPos(x, y, z);
        BlockPos ground = feet.below();
        BlockPos head = feet.above();

        if (!isInsideRtpBorder(border, feet.getX(), feet.getZ())) return null;
        if (!border.isWithinBounds(feet)) return null;
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

            if (!level.getWorldBorder().isWithinBounds(feet)) continue;
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

        if (groundState.is(Blocks.BEDROCK)
                || groundState.is(Blocks.LAVA)
                || groundState.is(Blocks.MAGMA_BLOCK)
                || groundState.is(Blocks.CACTUS)
                || groundState.is(Blocks.CAMPFIRE)
                || groundState.is(Blocks.SOUL_CAMPFIRE)
                || groundState.is(Blocks.FIRE)
                || groundState.is(Blocks.SOUL_FIRE)) {
            return false;
        }

        return !groundState.isAir();
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
        return task.desiredBiome == null || level.getBiome(pos).is(task.desiredBiome) || task.attempts > (task.maxAttempts * 2 / 3);
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

    private static boolean isFarEnoughFromStart(SearchTask task, int x, int z) {
        double dx = x - task.startX;
        double dz = z - task.startZ;
        return (dx * dx) + (dz * dz) >= (double) MIN_RTP_DISTANCE_BLOCKS * (double) MIN_RTP_DISTANCE_BLOCKS;
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
        private final double startX;
        private final double startZ;
        private final String worldType;
        private final ResourceKey<Biome> desiredBiome;
        private final int maxAttempts;
        private int attempts = 0;
        private int ticks = 0;
        private int generatedChunksThisTick = 0;
        private int generatedChunksTotal = 0;
        private int skippedUnloadedChunks = 0;

        private SearchTask(UUID playerId, ServerLevel level, SearchBounds bounds, double startX, double startZ, String worldType, ResourceKey<Biome> desiredBiome, int maxAttempts) {
            this.playerId = playerId;
            this.level = level;
            this.bounds = bounds;
            this.startX = startX;
            this.startZ = startZ;
            this.worldType = worldType;
            this.desiredBiome = desiredBiome;
            this.maxAttempts = Math.max(1, maxAttempts);
        }

        private boolean tick(ServerPlayer player, TickBudget budget) {
            ticks++;
            generatedChunksThisTick = 0;

            BlockPos target = findSafePosition(this, budget);
            if (target == null) {
                if (attempts >= maxAttempts || ticks >= MAX_RTP_SEARCH_TICKS) {
                    String biomeText = desiredBiome == null ? "" : " in " + desiredBiome.location();
                    player.sendSystemMessage(Component.literal("RTP could not find a safe location" + biomeText + " after checking " + attempts + " spots. Search stopped safely so the server will not keep generating chunks forever.").withStyle(ChatFormatting.RED));
                    player.sendSystemMessage(Component.literal("Generated chunks this search: " + generatedChunksTotal + ". Skipped unloaded chunks: " + skippedUnloadedChunks + ". Try a broader biome, /rtp " + worldType + ", or unlock/pregen another survival world.").withStyle(ChatFormatting.GRAY));
                    return true;
                }
                if (ticks % 80 == 0) {
                    player.sendSystemMessage(Component.literal("Still searching RTP safely... checked " + attempts + " spots, generated " + generatedChunksTotal + " chunks.").withStyle(ChatFormatting.GRAY));
                }
                return false;
            }

            SafeTeleportManager.teleport(player, level, target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, player.getYRot(), player.getXRot());
            String biomeText = desiredBiome == null ? "" : " in " + desiredBiome.location();
            player.sendSystemMessage(Component.literal("Teleported to a random safe location" + biomeText + " after checking " + attempts + " spots.").withStyle(ChatFormatting.GREEN));
            return true;
        }

        private boolean canGenerateAnotherChunk() {
            return generatedChunksTotal < MAX_GENERATED_CHUNKS_PER_SEARCH;
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
        private final int preferredMinX;
        private final int preferredMaxX;
        private final int preferredMinZ;
        private final int preferredMaxZ;

        private SearchBounds(int minX, int maxX, int minZ, int maxZ) {
            this(minX, maxX, minZ, maxZ, minX, maxX, minZ, maxZ);
        }

        private SearchBounds(int minX, int maxX, int minZ, int maxZ, int preferredMinX, int preferredMaxX, int preferredMinZ, int preferredMaxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.preferredMinX = preferredMinX;
            this.preferredMaxX = preferredMaxX;
            this.preferredMinZ = preferredMinZ;
            this.preferredMaxZ = preferredMaxZ;
        }

        private int minX(int attempts) { return attempts <= PREGENERATED_AREA_ATTEMPTS ? preferredMinX : minX; }
        private int maxX(int attempts) { return attempts <= PREGENERATED_AREA_ATTEMPTS ? preferredMaxX : maxX; }
        private int minZ(int attempts) { return attempts <= PREGENERATED_AREA_ATTEMPTS ? preferredMinZ : minZ; }
        private int maxZ(int attempts) { return attempts <= PREGENERATED_AREA_ATTEMPTS ? preferredMaxZ : maxZ; }


        private static SearchBounds from(ServerLevel level) {
            WorldBorder border = level.getWorldBorder();

            boolean hasChampBorder = ChampWorldBorderManager.isConfigured(level);
            int configuredRadius = hasChampBorder
                    ? (int) Math.floor(ChampWorldBorderManager.radius(level))
                    : FALLBACK_RTP_BORDER_RADIUS;

            int rawMinX = (int) Math.ceil(border.getMinX());
            int rawMaxX = (int) Math.floor(border.getMaxX());
            int rawMinZ = (int) Math.ceil(border.getMinZ());
            int rawMaxZ = (int) Math.floor(border.getMaxZ());

            int borderMinX = (hasChampBorder ? rawMinX : Math.max(rawMinX, -configuredRadius)) + BORDER_PADDING;
            int borderMaxX = (hasChampBorder ? rawMaxX : Math.min(rawMaxX, configuredRadius)) - BORDER_PADDING;
            int borderMinZ = (hasChampBorder ? rawMinZ : Math.max(rawMinZ, -configuredRadius)) + BORDER_PADDING;
            int borderMaxZ = (hasChampBorder ? rawMaxZ : Math.min(rawMaxZ, configuredRadius)) - BORDER_PADDING;

            if (borderMinX >= borderMaxX || borderMinZ >= borderMaxZ) {
                return null;
            }

            return new SearchBounds(borderMinX, borderMaxX, borderMinZ, borderMaxZ);
        }
    }
}
