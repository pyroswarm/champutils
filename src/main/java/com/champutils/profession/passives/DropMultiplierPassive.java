package com.champutils.profession.passives;

import com.champutils.profession.*;
import com.champutils.profession.actives.ActiveEffectManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;

import java.util.Random;

public class DropMultiplierPassive implements ProfessionPassive {
    private static final Random RANDOM = new Random();

    @Override
    public void apply(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos pos, String blockId) {
        if (ProfessionBlockTracker.isPlayerPlaced(level, pos)) return;
        String bonusDrop = getBonusDrop(player, blockId);
        if (bonusDrop == null || bonusDrop.isBlank()) return;

        double chance = ProfessionToolUtil.getStat(stack, "fortuneChance");
        if (chance <= 0.0D) chance = ProfessionToolUtil.getStat(stack, "fortuneBonus");
        if (chance <= 0.0D) return;
        chance *= ActiveEffectManager.getMiningPassiveChanceMultiplier(player, stack);
        if (RANDOM.nextDouble() >= Math.min(100.0D, chance) / 100.0D) return;

        int multiplier = rollMultiplier(player, stack, ProfessionType.MINING);
        if (multiplier <= 1) return;
        int extraAmount = estimateBaseDropCount(blockId) * (multiplier - 1);
        if (extraAmount <= 0) return;

        Item item;
        try { item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(bonusDrop)); } catch (Exception ignored) { return; }
        if (item == null || item == Items.AIR) return;
        ProfessionBackpackManager.giveOrDrop(player, new ItemStack(item, extraAmount), true);

        ProfessionSpecialCelebration.celebrateDropMultiplier(player, multiplier);
    }

    private int rollMultiplier(ServerPlayer player, ItemStack stack, ProfessionType profession) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(stack);
        String rarity = data == null ? "COMMON" : ProfessionFragmentConfig.normalizeRarity(data.rarity);
        int level = Math.max(1, ProfessionManager.getLevel(player, profession));
        int max = switch (rarity) {
            case "MYTHIC" -> 5;
            case "LEGENDARY" -> 4;
            case "RARE", "EPIC" -> 3;
            default -> 2;
        };
        double highBonus = Math.min(0.25D, level / 400.0D);
        double r = RANDOM.nextDouble();
        if (max >= 5 && r < 0.08D + highBonus) return 5;
        if (max >= 4 && r < 0.18D + highBonus) return 4;
        if (max >= 3 && r < 0.40D + highBonus) return 3;
        return 2;
    }

    private int estimateBaseDropCount(String blockId) {
        return switch (blockId) {
            case "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore" -> 4 + RANDOM.nextInt(6);
            case "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> 2 + RANDOM.nextInt(4);
            case "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore" -> 4 + RANDOM.nextInt(2);
            case "minecraft:nether_gold_ore" -> 2 + RANDOM.nextInt(5);
            case "minecraft:nether_quartz_ore", "minecraft:quartz_ore" -> 1;
            default -> 1;
        };
    }

    private String getBonusDrop(ServerPlayer player, String blockId) {
        if (ActiveEffectManager.hasAutoSmelt(player, player.getMainHandItem())) {
            String smeltedDrop = getSmeltedBonusDrop(blockId);
            if (smeltedDrop != null) return smeltedDrop;
        }
        return switch (blockId) {
            case "minecraft:coal_ore", "minecraft:deepslate_coal_ore" -> "minecraft:coal";
            case "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> "minecraft:raw_iron";
            case "minecraft:gold_ore", "minecraft:deepslate_gold_ore" -> "minecraft:raw_gold";
            case "minecraft:nether_gold_ore" -> "minecraft:gold_nugget";
            case "minecraft:nether_quartz_ore", "minecraft:quartz_ore" -> "minecraft:quartz";
            case "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> "minecraft:raw_copper";
            case "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore" -> "minecraft:diamond";
            case "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore" -> "minecraft:emerald";
            case "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore" -> "minecraft:redstone";
            case "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore" -> "minecraft:lapis_lazuli";
            default -> null;
        };
    }

    private String getSmeltedBonusDrop(String blockId) {
        return switch (blockId) {
            case "minecraft:iron_ore", "minecraft:deepslate_iron_ore" -> "minecraft:iron_ingot";
            case "minecraft:gold_ore", "minecraft:deepslate_gold_ore" -> "minecraft:gold_ingot";
            case "minecraft:nether_gold_ore" -> "minecraft:gold_nugget";
            case "minecraft:copper_ore", "minecraft:deepslate_copper_ore" -> "minecraft:copper_ingot";
            default -> null;
        };
    }
}
