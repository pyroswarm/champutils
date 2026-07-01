package com.champutils.profession.passives;

import com.champutils.profession.*;
import com.champutils.profession.actives.ActiveEffectManager;
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

public class ForestryRewardFinderPassive implements ProfessionPassive {
    private static final Random RANDOM = new Random();
    private static final List<String> APRICORNS = List.of(
            "cobblemon:black_apricorn", "cobblemon:blue_apricorn", "cobblemon:green_apricorn",
            "cobblemon:pink_apricorn", "cobblemon:red_apricorn", "cobblemon:white_apricorn", "cobblemon:yellow_apricorn"
    );

    @Override
    public void apply(ServerPlayer player, ItemStack stack, ServerLevel level, BlockPos pos, String blockId) {
        if (player == null || stack == null || stack.isEmpty() || level == null || pos == null) return;
        if (ProfessionBlockTracker.isPlayerPlaced(level, pos)) return;
        double chance = ProfessionToolUtil.getStat(stack, "apricornFinderChance");
        if (chance <= 0.0D) return;
        chance *= ActiveEffectManager.getForestryPassiveChanceMultiplier(player, stack);
        if (RANDOM.nextDouble() >= Math.min(100.0D, chance) / 100.0D) return;
        String itemId = APRICORNS.get(RANDOM.nextInt(APRICORNS.size()));
        Item item = item(itemId);
        if (item == Items.AIR) return;
        ItemStack reward = new ItemStack(item, 1);
        ProfessionBackpackManager.giveOrDrop(player, reward, true);
        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            player.displayClientMessage(Component.literal("§aApricorn Finder: §fFound an apricorn!"), true);
        }
    }

    private static Item item(String itemId) {
        try {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        } catch (Exception ignored) {
            return Items.AIR;
        }
    }
}
