package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Random;

public class MiningProfessionListener {

    private static final Random RANDOM =
            new Random();

    public static void register() {

        PlayerBlockBreakEvents.BEFORE.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return true;
                    }

                    Block block =
                            state.getBlock();

                    String blockId =
                            block.builtInRegistryHolder()
                                    .key()
                                    .location()
                                    .toString();

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .miningXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
                        return true;
                    }

                    if (
                            ProfessionBlockTracker.isPlayerPlaced(
                                    serverPlayer.serverLevel(),
                                    pos
                            )
                    ) {
                        ProfessionBlockTracker.remove(
                                serverPlayer.serverLevel(),
                                pos
                        );
                        return true;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.MINING,
                            xp
                    );

                    ProfessionLootManager.rollReward(
                            serverPlayer,
                            ProfessionType.MINING
                    );

                    handleBonusOreDrops(
                            serverPlayer,
                            blockId
                    );

                    return true;
                }
        );
    }

    private static void handleBonusOreDrops(
            ServerPlayer player,
            String blockId
    ) {

        ItemStack stack =
                player.getMainHandItem();

        if (
                !ProfessionToolUtil.hasPassive(
                        stack,
                        "bonus_ore_drops"
                )
        ) {
            return;
        }

        double fortuneBonus =
                ProfessionToolUtil.getStat(
                        stack,
                        "fortuneBonus"
                );

        if (fortuneBonus <= 0) {
            return;
        }

        double chance =
                fortuneBonus / 100D;

        if (
                RANDOM.nextDouble() > chance
        ) {
            return;
        }

        Item item =
                BuiltInRegistries.ITEM.get(
                        ResourceLocation.parse(
                                getBonusDrop(
                                        blockId
                                )
                        )
                );

        if (item == null) {
            return;
        }

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
                        "§bBonus ore drop!"
                ),
                true
        );
    }

    private static String getBonusDrop(
            String blockId
    ) {

        return switch (blockId) {

            case "minecraft:coal_ore",
                 "minecraft:deepslate_coal_ore" ->
                    "minecraft:coal";

            case "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore" ->
                    "minecraft:raw_iron";

            case "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore" ->
                    "minecraft:raw_gold";

            case "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore" ->
                    "minecraft:raw_copper";

            case "minecraft:diamond_ore",
                 "minecraft:deepslate_diamond_ore" ->
                    "minecraft:diamond";

            case "minecraft:emerald_ore",
                 "minecraft:deepslate_emerald_ore" ->
                    "minecraft:emerald";

            case "minecraft:redstone_ore",
                 "minecraft:deepslate_redstone_ore" ->
                    "minecraft:redstone";

            case "minecraft:lapis_ore",
                 "minecraft:deepslate_lapis_ore" ->
                    "minecraft:lapis_lazuli";

            case "minecraft:ancient_debris" ->
                    "minecraft:ancient_debris";

            default ->
                    blockId;
        };
    }
}