package com.champutils.profession.passives;

import com.champutils.profession.*;
import com.champutils.profession.actives.ActiveEffectManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;

public class ForestryDropMultiplierPassive implements ProfessionPassive {
    private static final Random RANDOM = new Random();

    @Override
    public void apply(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos pos, String blockId) {
        if (ProfessionBlockTracker.isPlayerPlaced(level, pos)) return;
        double chance = ProfessionToolUtil.getStat(stack, "fortuneChance");
        if (chance <= 0.0D) chance = ProfessionToolUtil.getStat(stack, "bonusLogs");
        if (chance <= 0.0D) return;
        chance *= ActiveEffectManager.getForestryPassiveChanceMultiplier(player, stack);
        if (RANDOM.nextDouble() >= Math.min(100.0D, chance) / 100.0D) return;
        int multiplier = rollMultiplier(player, stack);
        if (multiplier <= 1) return;
        Item item;
        try { item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(blockId)); } catch (Exception ignored) { return; }
        if (item == null || item == Items.AIR) return;
        ItemStack reward = new ItemStack(item, multiplier - 1);
        ProfessionBackpackManager.giveOrDrop(player, reward, true);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§2Fortune Chance: §f" + multiplier + "x logs!"), true);
            ProfessionNotificationSettings.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.45F, 1.4F);
        }
    }

    private int rollMultiplier(ServerPlayer player, ItemStack stack) {
        ProfessionToolConfig.ToolData data = ProfessionToolUtil.getToolData(stack);
        String rarity = data == null ? "COMMON" : ProfessionFragmentConfig.normalizeRarity(data.rarity);
        int level = Math.max(1, ProfessionManager.getBenefitLevel(player, ProfessionType.FORESTRY));
        int max = switch (rarity) { case "MYTHIC" -> 5; case "LEGENDARY" -> 4; case "RARE", "EPIC" -> 3; default -> 2; };
        double highBonus = Math.min(0.25D, level / 400.0D);
        double r = RANDOM.nextDouble();
        if (max >= 5 && r < 0.08D + highBonus) return 5;
        if (max >= 4 && r < 0.18D + highBonus) return 4;
        if (max >= 3 && r < 0.40D + highBonus) return 3;
        return 2;
    }
}
