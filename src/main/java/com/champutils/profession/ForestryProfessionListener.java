package com.champutils.profession;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.actives.ForestryBlockUtil;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ForestryProfessionListener {

    private static final Random RANDOM = new Random();
    private static final Set<String> MANUALLY_PROCESSED_EXTRA_BLOCKS = new HashSet<>();

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            if (MANUALLY_PROCESSED_EXTRA_BLOCKS.remove(extraBlockKey(serverPlayer, pos))) return true;
            String blockId = getBlockId(state.getBlock());
            int xp = forestryXpFor(state, blockId);
            if (xp <= 0) return true;
            if (ProfessionBlockTracker.isPlayerPlaced(serverPlayer.serverLevel(), pos)) {
                ProfessionBlockTracker.removeAfterCurrentTick(serverPlayer.serverLevel(), pos);
                return true;
            }

            ItemStack tool = serverPlayer.getMainHandItem();
            processForestryRewards(serverPlayer, state, blockId, tool, xp, false);

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "timber_burst", tool)) {
                breakConnectedLogs(serverPlayer, pos, state, timberBurstLimit(serverPlayer, tool));
            }

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "leafstorm", tool)) {
                clearNearbyLeaves(serverPlayer, pos, getIntStat(tool, "leafstormRadius", 5));
            }

            if (ActiveEffectManager.hasToggle(serverPlayer, "tree_replant", tool)) {
                tryReplantSapling(serverPlayer, pos, state);
            }

            return true;
        });
    }


    private static void processForestryRewards(ServerPlayer player, BlockState state, String blockId, ItemStack tool, int baseXp, boolean extraBlock) {
        double extraMultiplier = extraBlock ? 0.10D : 1.0D;
        int xp = extraBlock ? Math.max(1, (int) Math.ceil(baseXp * extraMultiplier)) : baseXp;
        ProfessionManager.addXp(player, ProfessionType.FORESTRY, xp);
        ProfessionSubLevelManager.addBlockXp(player, ProfessionType.FORESTRY, blockId, xp);
        com.champutils.quest.QuestManager.recordBlock(player, ProfessionType.FORESTRY, blockId);
        rollXpSurge(player, tool, xp, extraMultiplier);
        ProfessionLootManager.rollReward(player, ProfessionType.FORESTRY, extraMultiplier);
                // Profession fragment drops removed; use chunks -> Foreman trades instead.
        rollDropMultiplier(player, state, tool, extraMultiplier);
        rollRewardPassive(player, tool, "sapFinderChance", "forestry_sap_finder", extraMultiplier);
        rollRewardPassive(player, tool, "seedFinderChance", "forestry_seed_finder", extraMultiplier);
    }

    private static void rollDropMultiplier(ServerPlayer player, BlockState state, ItemStack tool, double chanceMultiplier) {
        int multiplier = 1;
        if (roll(player, tool, "tripleChopChance", chanceMultiplier)) multiplier = 3;
        else if (roll(player, tool, "doubleChopChance", chanceMultiplier)) multiplier = 2;
        if (multiplier <= 1) return;
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return;
        ItemStack reward = new ItemStack(item, multiplier - 1);
        ProfessionBackpackManager.giveOrDrop(player, reward, true);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            ProfessionSpecialCelebration.celebrateDropMultiplier(player, multiplier);
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private static void rollXpSurge(ServerPlayer player, ItemStack tool, int baseXp, double chanceMultiplier) {
        if (!roll(player, tool, "forestryXpSurgeChance", chanceMultiplier) && !roll(player, tool, "xpSurgeChance", chanceMultiplier)) return;
        int bonus = Math.max(1, baseXp);
        ProfessionManager.addXp(player, ProfessionType.FORESTRY, bonus);
        ProfessionSubLevelManager.addBlockXp(player, ProfessionType.FORESTRY, "forestry_xp_surge", bonus);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aForestry XP Surge! +" + bonus), true);
        }
    }

    private static void rollRewardPassive(ServerPlayer player, ItemStack tool, String stat, String table, double chanceMultiplier) {
        if (!roll(player, tool, stat, chanceMultiplier)) return;

        if ("forestry_sap_finder".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§6Sap Finder!", "§fFound ", ProfessionType.FORESTRY, tool);
            return;
        }

        if ("forestry_seed_finder".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§aSeed Finder!", "§fFound ", ProfessionType.FORESTRY, tool);
            return;
        }

        ProfessionRewardPassiveConfig.giveRolled(player, table, null, null, ProfessionType.FORESTRY, tool);
    }

    private static boolean roll(ServerPlayer player, ItemStack tool, String stat, double chanceMultiplier) {
        double chance = ProfessionToolUtil.getStat(tool, stat);
        if (ActiveEffectManager.hasTimedEffect(player, "lumberjack_focus", tool)) {
            chance *= 1.0D + (ProfessionToolUtil.getStat(tool, "lumberjackFocusBoost") / 100.0D);
        }
        chance *= Math.max(0.0D, chanceMultiplier);
        return chance > 0.0D && RANDOM.nextDouble() * 100.0D < chance;
    }

    private static void breakConnectedLogs(ServerPlayer player, BlockPos start, BlockState original, int maxBlocks) {
        ServerLevel level = player.serverLevel();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        int broken = 0;
        while (!queue.isEmpty() && broken < Math.max(1, maxBlocks)) {
            BlockPos current = queue.poll();
            if (!visited.add(current)) continue;
            if (!current.equals(start)) {
                BlockState state = level.getBlockState(current);
                if (state.getBlock() != original.getBlock()) continue;
                if (ProfessionBlockTracker.isPlayerPlaced(level, current)) continue;
                String currentBlockId = getBlockId(state.getBlock());
                processForestryRewards(player, state, currentBlockId, player.getMainHandItem(), forestryXpFor(state, currentBlockId), true);
                MANUALLY_PROCESSED_EXTRA_BLOCKS.add(extraBlockKey(player, current));
                if (level.destroyBlock(current, true, player)) broken++;
            }
            for (BlockPos next : neighbors(current)) queue.add(next);
        }
    }

    private static void clearNearbyLeaves(ServerPlayer player, BlockPos center, int radius) {
        ServerLevel level = player.serverLevel();
        int cleared = 0;
        int r = Math.max(1, Math.min(radius, 8));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (cleared >= 80) return;
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES)) continue;
            level.destroyBlock(pos.immutable(), true, player);
            cleared++;
        }
        if (cleared > 0 && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aLeafstorm cleared " + cleared + " leaves."), true);
        }
    }

    private static void tryReplantSapling(ServerPlayer player, BlockPos pos, BlockState oldState) {
        Block sapling = ForestryBlockUtil.getSaplingForLog(getBlockId(oldState.getBlock()));
        if (sapling == Blocks.AIR) return;
        player.serverLevel().setBlock(pos, sapling.defaultBlockState(), 3);
    }

    private static String extraBlockKey(ServerPlayer player, BlockPos pos) {
        return player.getUUID() + ":" + pos.asLong();
    }

    private static int forestryXpFor(BlockState state, String blockId) {
        Integer configured = ProfessionConfig.SETTINGS.forestryXp.get(blockId);
        if (configured != null && configured > 0) return configured;
        if (state != null && (state.is(BlockTags.LOGS) || looksLikeLog(blockId))) {
            return 5;
        }
        return 0;
    }

    private static boolean looksLikeLog(String blockId) {
        if (blockId == null) return false;
        String id = blockId.toLowerCase(java.util.Locale.ROOT);
        return id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae")
                || id.contains("saccharine") || id.contains("apricorn_log") || id.contains("apricorn_wood");
    }

    private static Iterable<BlockPos> neighbors(BlockPos pos) {
        return java.util.List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west());
    }

    private static int timberBurstLimit(ServerPlayer player, ItemStack tool) {
        int forestryLevel = Math.max(1, ProfessionManager.getLevel(player, ProfessionType.FORESTRY));
        int scaledLimit = 6 + Math.max(0, forestryLevel / 2);
        int configuredCap = getIntStat(tool, "maxTimberBlocks", scaledLimit);
        return Math.max(1, Math.min(configuredCap, scaledLimit));
    }

    private static int getIntStat(ItemStack stack, String stat, int fallback) {
        double value = ProfessionToolUtil.getStat(stack, stat);
        return value <= 0 ? fallback : (int) Math.round(value);
    }

    private static String getBlockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
