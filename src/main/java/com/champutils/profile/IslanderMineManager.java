package com.champutils.profile;

import com.champutils.teleport.SafeTeleportManager;
import com.champutils.time.DailyResetManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;

public final class IslanderMineManager {
    private static final Random RANDOM = new Random();
    private static final Queue<MineTask> TASKS = new ArrayDeque<>();
    private static long nextAutoResetAtMs = 0L;
    private static long lastDailyResetKeyMs = Long.MIN_VALUE;

    private IslanderMineManager() {}

    public static void load() {
        IslanderMineConfig.load();
        scheduleNextAutoReset(System.currentTimeMillis());
        lastDailyResetKeyMs = DailyResetManager.currentResetKeyMillis();
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !IslanderMineConfig.get().enabled) return;
        long now = System.currentTimeMillis();
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();

        if (isDailyMode(cfg)) {
            long currentKey = DailyResetManager.currentResetKeyMillis();
            if (lastDailyResetKeyMs == Long.MIN_VALUE) lastDailyResetKeyMs = currentKey;
            if (currentKey != lastDailyResetKeyMs) {
                queueResetForLoadedIslanderWorlds(server);
                lastDailyResetKeyMs = currentKey;
            }
            nextAutoResetAtMs = DailyResetManager.nextResetMillis(now);
        } else if (now >= nextAutoResetAtMs) {
            queueResetForLoadedIslanderWorlds(server);
            scheduleNextAutoReset(now);
        }

        processTasks(server);
    }

    public static boolean teleportToMine(ServerPlayer player) {
        if (player == null || player.server == null) return false;
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        if (!cfg.enabled) {
            player.sendSystemMessage(Component.literal("Islander mines are currently disabled.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!PlayerProfileManager.isIslander(player) && !player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("Only Islander profiles can use Islander mines.").withStyle(ChatFormatting.RED));
            return false;
        }

        ServerLevel level = resolveMineLevel(player);
        if (level == null) {
            player.sendSystemMessage(Component.literal("No loaded Islander world was found. Visit your Islander territory first, then try /island mine.").withStyle(ChatFormatting.YELLOW));
            return false;
        }

        ensureMineQueued(level, false);
        double x = cfg.centerX + 0.5D;
        double y = cfg.centerY + 2.0D;
        double z = cfg.centerZ + 0.5D;
        SafeTeleportManager.teleportUncheckedNoBack(player, level, x, y, z, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        player.sendSystemMessage(Component.literal("Entered the Islander mine. Ores regenerate on the Islander mine reset timer.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public static void queueManualReset(MinecraftServer server, ServerPlayer actor) {
        queueManualReset(server, actor, null);
    }

    public static void queueManualReset(MinecraftServer server, ServerPlayer actor, String targetLabel) {
        if (server == null) return;
        if (!IslanderMineConfig.get().allowManualReset && actor != null) {
            actor.sendSystemMessage(Component.literal("Manual Islander mine resets are disabled in config.").withStyle(ChatFormatting.RED));
            return;
        }
        int queued = queueResetForLoadedIslanderWorlds(server);
        if (actor != null) {
            String target = (targetLabel == null || targetLabel.isBlank()) ? "all loaded Islander mine areas" : targetLabel;
            actor.sendSystemMessage(Component.literal("Queued Islander mine reset for " + target + " (" + queued + " loaded Islander world(s)).").withStyle(ChatFormatting.GREEN));
        }
    }

    public static boolean isMineLocation(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !IslanderProfileManager.isIslanderWorld(level)) return false;
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        int r = cfg.radius + 6;
        int minY = cfg.centerY - 3;
        int maxY = cfg.centerY + cfg.height + 6;
        return Math.abs(pos.getX() - cfg.centerX) <= r
                && Math.abs(pos.getZ() - cfg.centerZ) <= r
                && pos.getY() >= minY
                && pos.getY() <= maxY;
    }


    private static boolean isDailyMode(IslanderMineConfig.Data cfg) {
        return cfg == null || cfg.resetMode == null || cfg.resetMode.equalsIgnoreCase("DAILY_2AM");
    }

    private static void scheduleNextAutoReset(long now) {
        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        if (isDailyMode(cfg)) {
            nextAutoResetAtMs = DailyResetManager.nextResetMillis(now);
        } else {
            int minutes = Math.max(1, cfg.debugResetMinutes);
            nextAutoResetAtMs = now + minutes * 60L * 1000L;
        }
    }

    private static ServerLevel resolveMineLevel(ServerPlayer player) {
        if (player.serverLevel() != null && IslanderProfileManager.isIslanderWorld(player.serverLevel())) return player.serverLevel();
        for (ServerLevel level : player.server.getAllLevels()) {
            if (IslanderProfileManager.isIslanderWorld(level)) return level;
        }
        return null;
    }

    private static int queueResetForLoadedIslanderWorlds(MinecraftServer server) {
        int queued = 0;
        for (ServerLevel level : server.getAllLevels()) {
            if (!IslanderProfileManager.isIslanderWorld(level)) continue;
            ensureMineQueued(level, true);
            queued++;
        }
        return queued;
    }

    private static void ensureMineQueued(ServerLevel level, boolean forceReset) {
        if (level == null) return;
        String dimension = level.dimension().location().toString();
        for (MineTask task : TASKS) {
            if (task.dimension.equals(dimension)) return;
        }
        TASKS.add(new MineTask(dimension, forceReset));
    }

    private static void processTasks(MinecraftServer server) {
        MineTask task = TASKS.peek();
        if (task == null) return;
        ServerLevel level = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().location().toString().equals(task.dimension)) {
                level = candidate;
                break;
            }
        }
        if (level == null) {
            TASKS.poll();
            return;
        }

        IslanderMineConfig.Data cfg = IslanderMineConfig.get();
        int budget = cfg.blocksPerTick;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (budget-- > 0 && !task.done(cfg)) {
            pos.set(cfg.centerX + task.dx, cfg.centerY + task.dy, cfg.centerZ + task.dz);
            placeMineBlock(level, pos, cfg, task);
            task.advance(cfg);
        }

        if (task.done(cfg)) {
            buildEntrance(level, cfg);
            TASKS.poll();
            System.out.println("[ChampUtils] Islander mine ready in " + task.dimension + ".");
        }
    }

    private static void placeMineBlock(ServerLevel level, BlockPos pos, IslanderMineConfig.Data cfg, MineTask task) {
        int dx = task.dx;
        int dy = task.dy;
        int dz = task.dz;
        int r = cfg.radius;
        int floor = 0;
        int ceiling = cfg.height;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean shell = horizontal >= r - 1 || dy <= floor || dy >= ceiling;
        boolean walkway = Math.abs(dx) <= 3 && Math.abs(dz) <= 3 && dy >= 1 && dy <= 5;
        boolean mainCavern = horizontal < r - 3 && dy > floor && dy < ceiling;

        if (walkway || (mainCavern && RANDOM.nextDouble() < 0.72D)) {
            setClean(level, pos, Blocks.AIR.defaultBlockState());
            return;
        }

        if (shell || mainCavern || task.forceReset) {
            BlockState state = chooseStoneOrOre(cfg);
            setClean(level, pos, state);
        }
    }

    private static void buildEntrance(ServerLevel level, IslanderMineConfig.Data cfg) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 1; dy <= 5; dy++) {
                    setClean(level, new BlockPos(cfg.centerX + dx, cfg.centerY + dy, cfg.centerZ + dz), Blocks.AIR.defaultBlockState());
                }
                setClean(level, new BlockPos(cfg.centerX + dx, cfg.centerY, cfg.centerZ + dz), Blocks.STONE.defaultBlockState());
            }
        }
        setClean(level, new BlockPos(cfg.centerX, cfg.centerY + 1, cfg.centerZ), Blocks.TORCH.defaultBlockState());
    }

    private static BlockState chooseStoneOrOre(IslanderMineConfig.Data cfg) {
        int totalOreWeight = cfg.ores.values().stream().mapToInt(Integer::intValue).sum();
        int stoneWeight = Math.max(120, totalOreWeight * 5);
        int roll = RANDOM.nextInt(stoneWeight + totalOreWeight);
        if (roll < stoneWeight) return RANDOM.nextBoolean() ? Blocks.STONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();

        int oreRoll = roll - stoneWeight;
        for (var entry : cfg.ores.entrySet()) {
            oreRoll -= entry.getValue();
            if (oreRoll < 0) {
                Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(entry.getKey().toLowerCase(Locale.ROOT)));
                if (block != Blocks.AIR) return block.defaultBlockState();
            }
        }
        return Blocks.STONE.defaultBlockState();
    }

    private static void setClean(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
        level.setBlock(pos, state, 18);
        if (level.getBlockEntity(pos) != null) level.removeBlockEntity(pos);
    }

    private static final class MineTask {
        final String dimension;
        final boolean forceReset;
        int dx;
        int dy;
        int dz;

        MineTask(String dimension, boolean forceReset) {
            this.dimension = dimension;
            this.forceReset = forceReset;
            IslanderMineConfig.Data cfg = IslanderMineConfig.get();
            this.dx = -cfg.radius;
            this.dy = 0;
            this.dz = -cfg.radius;
        }

        boolean done(IslanderMineConfig.Data cfg) {
            return dy > cfg.height;
        }

        void advance(IslanderMineConfig.Data cfg) {
            dz++;
            if (dz > cfg.radius) {
                dz = -cfg.radius;
                dx++;
            }
            if (dx > cfg.radius) {
                dx = -cfg.radius;
                dy++;
            }
        }
    }
}
