package com.champutils.profession.passives;

import com.champutils.profession.*;
import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.actives.MiningBlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Random;

public class GemFinderPassive implements ProfessionPassive {
    private static final Random RANDOM = new Random();
    private static final List<String> STONES = List.of(
            "cobblemon:fire_stone", "cobblemon:water_stone", "cobblemon:thunder_stone", "cobblemon:leaf_stone",
            "cobblemon:moon_stone", "cobblemon:sun_stone", "cobblemon:dawn_stone", "cobblemon:dusk_stone",
            "cobblemon:shiny_stone", "cobblemon:ice_stone"
    );
    private static final List<String> FOSSILS = List.of(
            "cobblemon:armor_fossil", "cobblemon:claw_fossil", "cobblemon:cover_fossil", "cobblemon:dome_fossil",
            "cobblemon:helix_fossil", "cobblemon:jaw_fossil", "cobblemon:old_amber_fossil", "cobblemon:plume_fossil",
            "cobblemon:root_fossil", "cobblemon:sail_fossil", "cobblemon:skull_fossil",
            "cobblemon:fossilized_bird", "cobblemon:fossilized_dino",
            "cobblemon:fossilized_drake", "cobblemon:fossilized_fish"
    );

    @Override
    public void apply(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos pos, String blockId) {
        if (player == null || stack == null || stack.isEmpty() || level == null || pos == null) return;
        if (ProfessionBlockTracker.isPlayerPlaced(level, pos)) return;
        boolean shovel = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("_shovel")
                || ProfessionToolMetadata.getToolId(stack).toLowerCase(java.util.Locale.ROOT).contains("shovel");
        if (!shovel && !isStoneMiningBlock(blockId)) {
            return;
        }
        String stat = shovel ? "fossilFinderChance" : "stoneFinderChance";
        double chance = ProfessionToolUtil.getStat(stack, stat);
        if (chance <= 0.0D) return;
        // Tool stats are stored as literal percentages. For example, 0.10 means 0.10%,
        // not 10%. Do not rescale sub-1 values.
        chance *= ActiveEffectManager.getMiningPassiveChanceMultiplier(player, stack);
        if (RANDOM.nextDouble() >= Math.min(100.0D, chance) / 100.0D) return;
        String itemId = shovel ? FOSSILS.get(RANDOM.nextInt(FOSSILS.size())) : STONES.get(RANDOM.nextInt(STONES.size()));
        Item item = item(itemId);
        if (item == Items.AIR) return;
        ItemStack reward = new ItemStack(item, 1);
        ProfessionBackpackManager.giveOrDrop(player, reward, true);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal((shovel ? "§6Fossil Finder" : "§bStone Finder") + ": §fFound something!"), true);
        }
    }

    private static boolean isStoneMiningBlock(String blockId) {
        if (blockId == null || blockId.isBlank()) return false;
        String id = blockId.toLowerCase(java.util.Locale.ROOT);
        if (id.endsWith("_ore") || id.equals("minecraft:ancient_debris")) return true;
        return id.equals("minecraft:stone")
                || id.equals("minecraft:deepslate")
                || id.equals("minecraft:cobbled_deepslate")
                || id.equals("minecraft:cobblestone")
                || id.equals("minecraft:netherrack")
                || id.equals("minecraft:blackstone")
                || id.equals("minecraft:basalt")
                || id.equals("minecraft:smooth_basalt")
                || id.equals("minecraft:tuff")
                || id.equals("minecraft:calcite")
                || id.equals("minecraft:dripstone_block")
                || id.equals("minecraft:end_stone")
                || id.contains("granite") || id.contains("diorite") || id.contains("andesite")
                || id.contains("sandstone") || id.contains("terracotta");
    }

    private static Item item(String itemId) {
        try {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }
}
