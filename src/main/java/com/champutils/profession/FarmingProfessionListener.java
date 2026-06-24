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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
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
            int xp = ProfessionConfig.SETTINGS.farmingXp.getOrDefault(
                    blockId,
                    ProfessionConfig.SETTINGS.farmingXp.getOrDefault("default", 10)
            );
            processFarmingRewards(serverPlayer, state, blockId, tool, xp, false);

            if (ActiveEffectManager.hasToggle(serverPlayer, "auto_replant", tool)) {
                tryAutoReplant(serverPlayer.serverLevel(), pos, state);
            }

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "harvest_wave", tool)) {
                harvestNearbyCrops(serverPlayer, pos, getIntStat(tool, "harvestWaveRadius", 4));
            }
        });
    }



    private static void processFarmingRewards(ServerPlayer player, BlockState state, String blockId, ItemStack tool, int baseXp, boolean extraBlock) {
        int xp = extraBlock ? Math.max(1, (int) Math.ceil(baseXp / 2.0D)) : baseXp;
        ProfessionManager.addXp(player, ProfessionType.FARMING, xp);
        com.champutils.quest.QuestManager.recordBlock(player, ProfessionType.FARMING, blockId);
        rollXpSurge(player, tool, xp);
        ProfessionLootManager.rollReward(player, ProfessionType.FARMING);
        ProfessionWeaponFragmentDropManager.rollReward(player, ProfessionType.FARMING);
        rollHarvestMultiplier(player, state.getBlock(), tool);
        rollRewardPassive(player, tool, "seedSaverChance", "farming_seed_saver");
        rollRewardPassive(player, tool, "goldenHarvestChance", "farming_golden_harvest");
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
        int multiplier = 1;
        if (roll(player, tool, "tripleHarvestChance")) multiplier = 3;
        else if (roll(player, tool, "doubleHarvestChance")) multiplier = 2;
        if (multiplier <= 1) return;
        Item item = cropReward(cropBlock);
        if (item == Items.AIR) return;
        ItemStack reward = new ItemStack(item, multiplier - 1);
        if (!player.getInventory().add(reward)) player.drop(reward, false);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§a" + multiplier + "x Harvest!"), true);
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private static void rollXpSurge(ServerPlayer player, ItemStack tool, int baseXp) {
        if (!roll(player, tool, "farmingXpSurgeChance") && !roll(player, tool, "xpSurgeChance")) return;
        int bonus = Math.max(1, baseXp);
        ProfessionManager.addXp(player, ProfessionType.FARMING, bonus);
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
            int xp = ProfessionConfig.SETTINGS.farmingXp.getOrDefault(
                    blockId,
                    ProfessionConfig.SETTINGS.farmingXp.getOrDefault("default", 10)
            );
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
