package com.champutils.profession;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.actives.ActiveEffectManager;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class FarmingProfessionListener {

    private static final Random RANDOM = new Random();
    private static final Set<String> MANUALLY_PROCESSED_EXTRA_BLOCKS = new HashSet<>();

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return true;
            ItemStack tool = serverPlayer.getMainHandItem();
            if (!isChampUtilsHoeTool(tool)) return true;
            if (world instanceof ServerLevel serverLevel && isLeafBlock(state)) {
                silkShearLeaf(serverPlayer, serverLevel, pos, state);
                return false;
            }
            if (!isFarmingBlock(state)) return true;
            if (isMatureFarmingBlock(state)) return true;

            return false;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            if (MANUALLY_PROCESSED_EXTRA_BLOCKS.remove(extraBlockKey(serverPlayer, pos))) return;
            if (!isMatureFarmingBlock(state)) return;

            ItemStack tool = serverPlayer.getMainHandItem();
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            int xp = 1;
            processFarmingRewards(serverPlayer, state, blockId, tool, xp, false);

            if (ActiveEffectManager.hasToggle(serverPlayer, "auto_replant", tool)) {
                tryAutoReplant(serverPlayer.serverLevel(), pos, state);
            }

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "harvest_wave", tool)) {
                harvestNearbyCrops(serverPlayer, pos, getIntStat(tool, "harvestWaveRadius", 4));
            }
        });
    }


    private static boolean isLeafBlock(BlockState state) {
        return state != null && !state.isAir() && state.is(BlockTags.LEAVES);
    }

    private static void silkShearLeaf(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player == null || level == null || pos == null || state == null || state.isAir()) return;
        Item item = state.getBlock().asItem();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3 | 16);
        if (item != Items.AIR) {
            Block.popResource(level, pos, new ItemStack(item, 1));
        }
        level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.8F, 1.0F);
    }


    private static void processFarmingRewards(ServerPlayer player, BlockState state, String blockId, ItemStack tool, int baseXp, boolean extraBlock) {
        int xp = extraBlock ? Math.max(1, (int) Math.ceil(baseXp / 2.0D)) : baseXp;
        ProfessionManager.addXp(player, ProfessionType.FARMING, xp);
        ProfessionSubLevelManager.addBlockXp(player, ProfessionType.FARMING, blockId, xp);
        com.champutils.quest.QuestManager.recordBlock(player, ProfessionType.FARMING, blockId);
        // Farming was intentionally nerfed: one mature crop = one base XP.
        ProfessionLootManager.rollReward(player, ProfessionType.FARMING);
                // Profession fragment drops removed; use chunks -> Foreman trades instead.
        rollXpSurge(player, tool, xp);
        rollRewardPassive(player, tool, "seedSaverChance", "farming_seed_saver");
        rollRewardPassive(player, tool, "goldenHarvestChance", "farming_golden_harvest");
        rollRewardPassive(player, tool, "berryFinderChance", "farming_berry_finder");
        rollHarvestMultiplier(player, state.getBlock(), tool);
    }


    private static boolean isChampUtilsHoeTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !ProfessionToolMetadata.isProfessionTool(stack)) return false;
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(stack);
        if (data == null) return false;
        String profession = data.profession == null ? "" : data.profession.trim().toUpperCase(java.util.Locale.ROOT);
        String baseItem = data.baseItem == null ? "" : data.baseItem.toLowerCase(java.util.Locale.ROOT);
        return "FARMING".equals(profession) || baseItem.endsWith("_hoe") || baseItem.contains(":hoe");
    }

    private static boolean isFarmingBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        if (ProfessionConfig.SETTINGS.farmingXp.containsKey(blockId)) return true;
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof CocoaBlock;
    }

    private static void tryAutoReplant(ServerLevel level, BlockPos pos, BlockState oldState) {
        if (level == null || pos == null || oldState == null || oldState.isAir()) return;
        BlockState replanted = getReplantedState(oldState);
        if (replanted == null) return;
        level.getServer().execute(() -> {
            if (!level.getBlockState(pos).isAir()) return;
            if (!replanted.canSurvive(level, pos)) return;
            level.setBlock(pos, replanted, 3 | 16);
            level.blockUpdated(pos, replanted.getBlock());
        });
    }

    private static boolean isMatureFarmingBlock(BlockState state) {
        if (state == null || state.isAir()) return false;

        String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        boolean configured = ProfessionConfig.SETTINGS.farmingXp.containsKey(blockId);

        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }

        if (state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof CocoaBlock || configured) {
            IntegerProperty age = findAgeProperty(state);
            return age != null && state.getValue(age) >= maxAge(age);
        }

        return false;
    }

    private static BlockState getReplantedState(BlockState oldState) {
        if (oldState.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }

        IntegerProperty age = findAgeProperty(oldState);
        if (age != null && oldState.hasProperty(age) && age.getPossibleValues().contains(0)) {
            return oldState.setValue(age, 0);
        }

        return null;
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integerProperty && "age".equals(integerProperty.getName())) {
                return integerProperty;
            }
        }
        return null;
    }

    private static int maxAge(IntegerProperty property) {
        int max = 0;
        for (Integer value : property.getPossibleValues()) {
            if (value > max) max = value;
        }
        return max;
    }

    private static void rollHarvestMultiplier(ServerPlayer player, Block cropBlock, ItemStack tool) {
        double chance = ProfessionToolUtil.getStat(tool, "fortuneChance");
        if (chance <= 0.0D) {
            chance = Math.max(ProfessionToolUtil.getStat(tool, "tripleHarvestChance"), ProfessionToolUtil.getStat(tool, "doubleHarvestChance"));
        }
        if (chance <= 0.0D || RANDOM.nextDouble() * 100.0D >= chance) return;
        int multiplier = rollFortuneHarvestMultiplier(player, tool);
        if (multiplier <= 1) return;
        Item item = cropReward(cropBlock);
        if (item == Items.AIR) return;
        int baseDrops = estimatedBaseCropDrops(cropBlock);
        ItemStack reward = new ItemStack(item, baseDrops * (multiplier - 1));
        ProfessionBackpackManager.giveOrDrop(player, reward, true);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            ProfessionSpecialCelebration.celebrateDropMultiplier(player, multiplier);
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private static int estimatedBaseCropDrops(Block block) {
        if (block == Blocks.POTATOES || block == Blocks.CARROTS || block == Blocks.NETHER_WART || block == Blocks.COCOA) return 3;
        return 1;
    }

    private static int rollFortuneHarvestMultiplier(ServerPlayer player, ItemStack tool) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(tool);
        String rarity = data == null ? "COMMON" : ProfessionFragmentConfig.normalizeRarity(data.rarity);
        int level = Math.max(1, ProfessionManager.getLevel(player, ProfessionType.FARMING));
        int max = switch (rarity) { case "MYTHIC" -> 5; case "LEGENDARY" -> 4; case "RARE", "EPIC" -> 3; default -> 2; };
        double highBonus = Math.min(0.25D, level / 400.0D);
        double r = RANDOM.nextDouble();
        if (max >= 5 && r < 0.08D + highBonus) return 5;
        if (max >= 4 && r < 0.18D + highBonus) return 4;
        if (max >= 3 && r < 0.40D + highBonus) return 3;
        return 2;
    }

    private static void rollXpSurge(ServerPlayer player, ItemStack tool, int baseXp) {
        if (!roll(player, tool, "farmingXpSurgeChance") && !roll(player, tool, "xpSurgeChance")) return;
        int bonus = Math.max(1, baseXp);
        ProfessionManager.addXp(player, ProfessionType.FARMING, bonus);
        ProfessionSubLevelManager.addBlockXp(player, ProfessionType.FARMING, "farming_xp_surge", bonus);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aFarming XP Surge! +" + bonus), true);
        }
    }

    private static void rollRewardPassive(ServerPlayer player, ItemStack tool, String stat, String table) {
        if (!roll(player, tool, stat)) return;

        if ("farming_seed_saver".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§aSeed Saver!", "§fFound ", ProfessionType.FARMING, tool);
            return;
        }

        if ("farming_golden_harvest".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§6Golden Harvest!", "§fFound ", ProfessionType.FARMING, tool);
            return;
        }

        if ("farming_berry_finder".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§dBerry Finder!", "§fFound ", ProfessionType.FARMING, tool);
            return;
        }

        ProfessionRewardPassiveConfig.giveRolled(player, table, null, null, ProfessionType.FARMING, tool);
    }

    private static boolean roll(ServerPlayer player, ItemStack tool, String stat) {
        double chance = ProfessionToolUtil.getStat(tool, stat);
        if (ActiveEffectManager.hasTimedEffect(player, "golden_rain", tool)) {
            chance *= 1.0D + (ProfessionToolUtil.getStat(tool, "goldenRainBoost") / 100.0D);
        }
        return chance > 0.0D && RANDOM.nextDouble() * 100.0D < chance;
    }

    private static void harvestNearbyCrops(ServerPlayer player, BlockPos center, int radius) {
        ServerLevel level = player.serverLevel();
        int harvested = 0;
        int r = Math.max(1, Math.min(radius, 8));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            if (harvested >= 64) return;
            if (pos.equals(center)) continue;
            BlockState state = level.getBlockState(pos);
            if (!isMatureFarmingBlock(state)) continue;
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            if (isCobblemonBerryBush(blockId)) continue;
            int xp = 1;
            processFarmingRewards(player, state, blockId, player.getMainHandItem(), xp, true);
            MANUALLY_PROCESSED_EXTRA_BLOCKS.add(extraBlockKey(player, pos));
            level.destroyBlock(pos.immutable(), true, player);
            if (ActiveEffectManager.hasToggle(player, "auto_replant", player.getMainHandItem()) && !isMelonOrPumpkin(blockId)) {
                tryAutoReplant(level, pos.immutable(), state);
            }
            harvested++;
        }
        if (harvested > 0 && ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aHarvest Wave collected " + harvested + " crops."), true);
        }
    }

    private static boolean isMelonOrPumpkin(String blockId) {
        return "minecraft:melon".equals(blockId) || "minecraft:pumpkin".equals(blockId);
    }

    private static boolean isCobblemonBerryBush(String blockId) {
        if (blockId == null) return false;
        String id = blockId.toLowerCase(java.util.Locale.ROOT);
        return id.startsWith("cobblemon:") && (id.contains("berry") || id.contains("berries"));
    }

    private static String extraBlockKey(ServerPlayer player, BlockPos pos) {
        return player.getUUID() + ":" + pos.asLong();
    }

    private static Item cropReward(Block cropBlock) {
        String id = BuiltInRegistries.BLOCK.getKey(cropBlock).toString();
        return switch (id) {
            case "minecraft:wheat" -> Items.WHEAT;
            case "minecraft:carrots" -> Items.CARROT;
            case "minecraft:potatoes" -> Items.POTATO;
            case "minecraft:beetroots" -> Items.BEETROOT;
            default -> cropBlock.asItem();
        };
    }

    private static int getIntStat(ItemStack stack, String stat, int fallback) {
        double value = ProfessionToolUtil.getStat(stack, stat);
        return value <= 0 ? fallback : (int) Math.round(value);
    }
}
