package com.champutils.profession;

import com.champutils.profession.actives.ActiveEffectManager;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class FarmingProfessionListener {

    private static final Random RANDOM = new Random();

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            if (!(state.getBlock() instanceof CropBlock crop)) return;
            if (!crop.isMaxAge(state)) return;

            ItemStack tool = serverPlayer.getMainHandItem();
            int xp = ProfessionConfig.SETTINGS.farmingXp.getOrDefault("default", 10);
            ProfessionManager.addXp(serverPlayer, ProfessionType.FARMING, xp);
            rollXpSurge(serverPlayer, tool, xp);
            ProfessionLootManager.rollReward(serverPlayer, ProfessionType.FARMING);
            rollHarvestMultiplier(serverPlayer, state.getBlock(), tool);
            rollRewardPassive(serverPlayer, tool, "seedSaverChance", "farming_seed_saver");
            rollRewardPassive(serverPlayer, tool, "goldenHarvestChance", "farming_golden_harvest");

            if (ActiveEffectManager.hasToggle(serverPlayer, "auto_replant", tool)) {
                serverPlayer.serverLevel().setBlock(pos, crop.getStateForAge(0), 3);
            }

            if (ActiveEffectManager.hasTimedEffect(serverPlayer, "harvest_wave", tool)) {
                harvestNearbyCrops(serverPlayer, pos, getIntStat(tool, "harvestWaveRadius", 4));
            }
        });
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
        player.displayClientMessage(Component.literal("§a" + multiplier + "x Harvest!"), true);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
    }

    private static void rollXpSurge(ServerPlayer player, ItemStack tool, int baseXp) {
        if (!roll(player, tool, "farmingXpSurgeChance") && !roll(player, tool, "xpSurgeChance")) return;
        int bonus = Math.max(1, baseXp);
        ProfessionManager.addXp(player, ProfessionType.FARMING, bonus);
        player.displayClientMessage(Component.literal("§aFarming XP Surge! +" + bonus), true);
    }

    private static void rollRewardPassive(ServerPlayer player, ItemStack tool, String stat, String table) {
        if (!roll(player, tool, stat)) return;

        if ("farming_seed_saver".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§aSeed Saver!", "§fFound ");
            return;
        }

        if ("farming_golden_harvest".equals(table)) {
            ProfessionRewardPassiveConfig.giveRolled(player, table, "§6Golden Harvest!", "§fFound ");
            return;
        }

        ProfessionRewardPassiveConfig.giveRolled(player, table);
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
            if (!(state.getBlock() instanceof CropBlock crop)) continue;
            if (!crop.isMaxAge(state)) continue;
            level.destroyBlock(pos.immutable(), true, player);
            if (ActiveEffectManager.hasToggle(player, "auto_replant", player.getMainHandItem())) {
                level.setBlock(pos.immutable(), crop.getStateForAge(0), 3);
            }
            harvested++;
        }
        if (harvested > 0) player.displayClientMessage(Component.literal("§aHarvest Wave collected " + harvested + " crops."), true);
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
