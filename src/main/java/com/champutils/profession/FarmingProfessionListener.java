package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public class FarmingProfessionListener {

    private static final Random RANDOM = new Random();

    public static void register() {

        PlayerBlockBreakEvents.AFTER.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return;
                    }

                    Block block = state.getBlock();

                    if (!(block instanceof CropBlock crop)) {
                        return;
                    }

                    // IMPORTANT:
                    // No XP, loot, bonus yield, or auto-replant unless the crop was fully grown.
                    if (!crop.isMaxAge(state)) {
                        return;
                    }

                    int xp = ProfessionConfig
                            .SETTINGS
                            .farmingXp
                            .getOrDefault(
                                    "default",
                                    10
                            );

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.FARMING,
                            xp
                    );

                    ProfessionLootManager.rollReward(
                            serverPlayer,
                            ProfessionType.FARMING
                    );

                    handleBonusCropYield(
                            serverPlayer,
                            block
                    );

                    handleAutoReplant(
                            serverPlayer,
                            pos,
                            crop
                    );
                }
        );
    }

    private static void handleBonusCropYield(
            ServerPlayer player,
            Block cropBlock
    ) {

        ItemStack held = player.getMainHandItem();

        double bonusYield =
                ProfessionToolUtil.getStat(
                        held,
                        "bonusCropYield"
                );

        if (bonusYield <= 0) {
            return;
        }

        double chance = bonusYield / 100D;

        if (RANDOM.nextDouble() > chance) {
            return;
        }

        String bonusItemId =
                getCropRewardItem(
                        cropBlock
                );

        if (bonusItemId == null) {
            return;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse(
                                bonusItemId
                        )
                );

        ItemStack reward =
                new ItemStack(
                        item,
                        1
                );

        if (!player.getInventory().add(reward)) {
            player.drop(
                    reward,
                    false
            );
        }

        player.displayClientMessage(
                Component.literal(
                        "§aBonus crop yield!"
                ),
                true
        );
    }

    private static void handleAutoReplant(
            ServerPlayer player,
            BlockPos pos,
            CropBlock crop
    ) {

        ItemStack held = player.getMainHandItem();

        if (
                !ProfessionToolUtil.hasPassive(
                        held,
                        "auto_replant"
                )
        ) {
            return;
        }

        BlockState replanted =
                crop.getStateForAge(
                        0
                );

        player.serverLevel()
                .setBlock(
                        pos,
                        replanted,
                        3
                );

        player.displayClientMessage(
                Component.literal(
                        "§2Auto Replant"
                ),
                true
        );
    }

    private static String getCropRewardItem(
            Block cropBlock
    ) {

        String blockId =
                cropBlock.builtInRegistryHolder()
                        .key()
                        .location()
                        .toString();

        return switch (blockId) {
            case "minecraft:wheat" ->
                    "minecraft:wheat";

            case "minecraft:carrots" ->
                    "minecraft:carrot";

            case "minecraft:potatoes" ->
                    "minecraft:potato";

            case "minecraft:beetroots" ->
                    "minecraft:beetroot";

            default ->
                    null;
        };
    }
}