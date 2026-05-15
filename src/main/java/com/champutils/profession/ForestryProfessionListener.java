package com.champutils.profession;

import com.champutils.profession.actives.ActiveEffectManager;

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

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            String blockId = getBlockId(state.getBlock());
            Integer xp = ProfessionConfig.SETTINGS.forestryXp.get(blockId);
            if (xp == null || xp <= 0) return true;
            if (ProfessionBlockTracker.isPlayerPlaced(serverPlayer.serverLevel(), pos)) {
                ProfessionBlockTracker.remove(serverPlayer.serverLevel(), pos);
                return true;
            }

            ItemStack tool = serverPlayer.getMainHandItem();
            ProfessionManager.addXp(serverPlayer, ProfessionType.FORESTRY, xp);
            rollXpSurge(serverPlayer, tool, xp);
            ProfessionLootManager.rollReward(serverPlayer, ProfessionType.FORESTRY);
            ProfessionWeaponFragmentDropManager.rollReward(serverPlayer, ProfessionType.FORESTRY);
            rollDropMultiplier(serverPlayer, state, tool);
            rollRewardPassive(serverPlayer, tool, "sapFinderChance", "forestry_sap_finder");
            rollRewardPassive(serverPlayer, tool, "seedFinderChance", "forestry_seed_finder");

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "timber_burst", tool)) {
                breakConnectedLogs(serverPlayer, pos, state, getIntStat(tool, "maxTimberBlocks", 32));
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

    private static void rollDropMultiplier(ServerPlayer player, BlockState state, ItemStack tool) {
        int multiplier = 1;
        if (roll(player, tool, "tripleChopChance")) multiplier = 3;
        else if (roll(player, tool, "doubleChopChance")) multiplier = 2;
        if (multiplier <= 1) return;
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return;
        ItemStack reward = new ItemStack(item, multiplier - 1);
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§a" + multiplier + "x Chop!"), true);
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private static void rollXpSurge(ServerPlayer player, ItemStack tool, int baseXp) {
        if (!roll(player, tool, "forestryXpSurgeChance") && !roll(player, tool, "xpSurgeChance")) return;
        int bonus = Math.max(1, baseXp);
        ProfessionManager.addXp(player, ProfessionType.FORESTRY, bonus);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aForestry XP Surge! +" + bonus), true);
        }
    }

    private static void rollRewardPassive(ServerPlayer player, ItemStack tool, String stat, String table) {
        if (!roll(player, tool, stat)) return;

        if ("forestry_sap_finder".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§6Sap Finder!", "§fFound ");
            return;
        }

        if ("forestry_seed_finder".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§aSeed Finder!", "§fFound ");
            return;
        }

        ProfessionRewardPassiveConfig.giveRolled(player, table);
    }

    private static boolean roll(ServerPlayer player, ItemStack tool, String stat) {
        double chance = ProfessionToolUtil.getStat(tool, stat);
        if (ActiveEffectManager.hasTimedEffect(player, "lumberjack_focus", tool)) {
            chance *= 1.0D + (ProfessionToolUtil.getStat(tool, "lumberjackFocusBoost") / 100.0D);
        }
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
        Block sapling = saplingFor(oldState.getBlock());
        if (sapling == Blocks.AIR) return;
        player.serverLevel().setBlock(pos, sapling.defaultBlockState(), 3);
    }

    private static Block saplingFor(Block block) {
        String id = getBlockId(block);
        return switch (id) {
            case "minecraft:oak_log" -> Blocks.OAK_SAPLING;
            case "minecraft:spruce_log" -> Blocks.SPRUCE_SAPLING;
            case "minecraft:birch_log" -> Blocks.BIRCH_SAPLING;
            case "minecraft:jungle_log" -> Blocks.JUNGLE_SAPLING;
            case "minecraft:acacia_log" -> Blocks.ACACIA_SAPLING;
            case "minecraft:dark_oak_log" -> Blocks.DARK_OAK_SAPLING;
            case "minecraft:mangrove_log" -> Blocks.MANGROVE_PROPAGULE;
            case "minecraft:cherry_log" -> Blocks.CHERRY_SAPLING;
            default -> Blocks.AIR;
        };
    }

    private static Iterable<BlockPos> neighbors(BlockPos pos) {
        return java.util.List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west());
    }

    private static int getIntStat(ItemStack stack, String stat, int fallback) {
        double value = ProfessionToolUtil.getStat(stack, stat);
        return value <= 0 ? fallback : (int) Math.round(value);
    }

    private static String getBlockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
