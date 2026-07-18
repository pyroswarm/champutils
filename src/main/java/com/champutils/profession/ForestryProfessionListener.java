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
import net.minecraft.world.level.block.entity.BlockEntity;
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
            AcceleratedLeafDecayManager.trackLeavesNearRemovedLog(serverPlayer.serverLevel(), pos);
            processForestryRewards(serverPlayer, pos, blockEntity, state, blockId, tool, xp, false);

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "timber_burst", tool)) {
                breakConnectedLogs(serverPlayer, pos, state, timberBurstLimit(serverPlayer, tool));
            }

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "leafstorm", tool)) {
                clearNearbyLeaves(serverPlayer, pos, getIntStat(tool, "leafstormRadius", 5));
            }

            if (hasTreeReplantToggle(serverPlayer, tool)) {
                tryReplantSapling(serverPlayer, pos, state);
            }

            return true;
        });
    }


    private static void processForestryRewards(ServerPlayer player, BlockPos pos, BlockEntity blockEntity, BlockState state, String blockId, ItemStack tool, int baseXp, boolean extraBlock) {
        double rewardMultiplier = extraBlock ? 0.10D : 1.0D;
        // Tool passives must retain their actual rolled chance on every natural block,
        // including blocks destroyed by Timber Burst. Only XP/general loot is reduced.
        double passiveChanceMultiplier = 1.0D;
        int xp = extraBlock ? Math.max(1, (int) Math.ceil(baseXp * rewardMultiplier)) : baseXp;
        ProfessionManager.addXp(player, ProfessionType.FORESTRY, xp);
        ProfessionSubLevelManager.addBlockXp(player, ProfessionType.FORESTRY, blockId, xp);
        com.champutils.quest.QuestManager.recordBlock(player, ProfessionType.FORESTRY, blockId);
        rollXpSurge(player, tool, xp, passiveChanceMultiplier);
        ProfessionLootManager.rollReward(player, ProfessionType.FORESTRY, rewardMultiplier);
                // Profession fragment drops removed; use chunks -> Foreman trades instead.
        rollFortuneMultiplier(player, pos, blockEntity, state, tool, passiveChanceMultiplier);
        rollDropMultiplier(player, state, tool, passiveChanceMultiplier);
        rollApricornFinder(player, tool, passiveChanceMultiplier);
        rollRewardPassive(player, tool, "sapFinderChance", "forestry_sap_finder", passiveChanceMultiplier);
        rollRewardPassive(player, tool, "seedFinderChance", "forestry_seed_finder", passiveChanceMultiplier);
    }

    /**
     * Profession axe fortune is independent from vanilla Fortune. The listener runs before
     * vanilla destroys the log, so award only the extra copies here and let vanilla provide
     * the original drops normally. Using Block#getDrops keeps modded logs and tool-sensitive
     * drops correct instead of assuming every log drops its block item.
     */
    private static void rollFortuneMultiplier(
            ServerPlayer player,
            BlockPos pos,
            BlockEntity blockEntity,
            BlockState state,
            ItemStack tool,
            double chanceMultiplier
    ) {
        if (player == null || pos == null || state == null || state.isAir()) return;
        double chance = ProfessionToolUtil.getStat(tool, "fortuneChance");
        chance *= Math.max(0.0D, chanceMultiplier);
        if (chance <= 0.0D || RANDOM.nextDouble() * 100.0D >= Math.min(100.0D, chance)) return;

        int multiplier = rollFortuneLogMultiplier(player, tool);
        if (multiplier <= 1) return;

        java.util.List<ItemStack> drops = Block.getDrops(
                state,
                player.serverLevel(),
                pos,
                blockEntity,
                player,
                tool
        );
        boolean gaveAnything = false;
        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) continue;
            ItemStack extra = drop.copy();
            extra.setCount(Math.max(1, drop.getCount()) * (multiplier - 1));
            ProfessionBackpackManager.giveOrDrop(player, extra, true);
            gaveAnything = true;
        }

        if (gaveAnything && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§2Fortune Chance: §f" + multiplier + "x logs!"), true);
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private static int rollFortuneLogMultiplier(ServerPlayer player, ItemStack tool) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(tool);
        String rarity = data == null ? "F" : ProfessionFragmentConfig.normalizeRarity(data.rarity);
        int level = Math.max(1, ProfessionManager.getBenefitLevel(player, ProfessionType.FORESTRY));
        int max = switch (rarity) {
            case "S" -> 6;
            case "A" -> 5;
            case "B" -> 4;
            case "D", "C" -> 3;
            default -> 2;
        };
        double highBonus = Math.min(0.25D, level / 400.0D);
        double roll = RANDOM.nextDouble();
        if (max >= 5 && roll < 0.08D + highBonus) return 5;
        if (max >= 4 && roll < 0.18D + highBonus) return 4;
        if (max >= 3 && roll < 0.40D + highBonus) return 3;
        return 2;
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

    private static final java.util.List<String> APRICORN_ITEMS = java.util.List.of(
            "cobblemon:black_apricorn", "cobblemon:blue_apricorn", "cobblemon:green_apricorn",
            "cobblemon:pink_apricorn", "cobblemon:red_apricorn", "cobblemon:white_apricorn",
            "cobblemon:yellow_apricorn"
    );

    private static void rollApricornFinder(ServerPlayer player, ItemStack tool, double chanceMultiplier) {
        if (!roll(player, tool, "apricornFinderChance", chanceMultiplier)) return;
        String itemId = APRICORN_ITEMS.get(RANDOM.nextInt(APRICORN_ITEMS.size()));
        Item item;
        try {
            item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        } catch (Exception ignored) {
            return;
        }
        if (item == null || item == Items.AIR) return;
        ProfessionBackpackManager.giveOrDrop(player, new ItemStack(item, 1), true);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aApricorn Finder: §fFound an apricorn!"), true);
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.65F);
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
                processForestryRewards(player, current, level.getBlockEntity(current), state, currentBlockId, player.getMainHandItem(), forestryXpFor(state, currentBlockId), true);
                MANUALLY_PROCESSED_EXTRA_BLOCKS.add(extraBlockKey(player, current));
                AcceleratedLeafDecayManager.trackLeavesNearRemovedLog(level, current);
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
            if (breakLeafWithToolDrops(player, level, pos.immutable(), state)) {
                cleared++;
            }
        }
        if (cleared > 0 && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aLeafstorm cleared " + cleared + " leaves."), true);
        }
    }

    private static boolean breakLeafWithToolDrops(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) {

        if (player == null || level == null || pos == null || state == null || state.isAir()) {
            return false;
        }

        ItemStack tool = player.getMainHandItem();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        java.util.List<ItemStack> drops =
                Block.getDrops(
                        state,
                        level,
                        pos,
                        blockEntity,
                        player,
                        tool
                );

        double professionFortuneChance =
                ProfessionToolUtil.getStat(
                        tool,
                        "fortuneChance"
                );

        boolean duplicateDrops =
                professionFortuneChance > 0.0D &&
                        RANDOM.nextDouble() * 100.0D < Math.min(100.0D, professionFortuneChance);

        level.setBlock(
                pos,
                Blocks.AIR.defaultBlockState(),
                3
        );

        for (ItemStack drop : drops) {
            if (drop == null || drop.isEmpty()) {
                continue;
            }

            ProfessionBackpackManager.giveOrDrop(
                    player,
                    drop.copy(),
                    true
            );

            if (duplicateDrops) {
                ProfessionBackpackManager.giveOrDrop(
                        player,
                        drop.copy(),
                        true
                );
            }
        }

        return true;
    }

    private static boolean hasTreeReplantToggle(ServerPlayer player, ItemStack tool) {
        return ActiveEffectManager.hasToggle(player, "tree_replant", tool)
                || ActiveEffectManager.hasToggle(player, "forestry_replant", tool)
                || ActiveEffectManager.hasToggle(player, "auto_replant", tool);
    }

    private static void tryReplantSapling(ServerPlayer player, BlockPos pos, BlockState oldState) {
        if (player == null || pos == null || oldState == null || oldState.isAir()) return;

        Block sapling = ForestryBlockUtil.getSaplingForLog(getBlockId(oldState.getBlock()));
        if (sapling == Blocks.AIR) return;

        ServerLevel level = player.serverLevel();
        BlockState replanted = sapling.defaultBlockState();

        // This listener runs from PlayerBlockBreakEvents.BEFORE. Replanting immediately here can
        // cause vanilla's normal block break to remove the sapling right after we place it.
        // Queue the sapling placement for the next server task, after the original log is gone.
        level.getServer().execute(() -> {
            if (!level.getBlockState(pos).isAir()) return;
            if (!replanted.canSurvive(level, pos)) return;
            level.setBlock(pos, replanted, 3 | 16);
            level.blockUpdated(pos, replanted.getBlock());
        });
    }

    private static String extraBlockKey(ServerPlayer player, BlockPos pos) {
        return player.getUUID() + ":" + pos.asLong();
    }

    private static int forestryXpFor(BlockState state, String blockId) {
        if (state != null && state.is(BlockTags.LEAVES)) {
            return 0;
        }
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

    /**
     * Returns every block touching the current block, including edge and corner diagonals.
     * Branching trees such as acacia and large oak frequently connect logs diagonally rather
     * than through a perfectly straight face-adjacent column.
     */
    private static Iterable<BlockPos> neighbors(BlockPos pos) {
        java.util.List<BlockPos> nearby = new java.util.ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    nearby.add(pos.offset(dx, dy, dz));
                }
            }
        }
        return nearby;
    }

    private static int timberBurstLimit(ServerPlayer player, ItemStack tool) {
        int forestryLevel = Math.max(1, ProfessionManager.getBenefitLevel(player, ProfessionType.FORESTRY));
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
