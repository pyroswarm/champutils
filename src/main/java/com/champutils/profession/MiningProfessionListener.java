package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

                    handleAscendedToolTracker(
                            serverPlayer,
                            blockId
                    );

                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .miningXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
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

                    handleMiningToolPassives(
                            serverPlayer,
                            blockId
                    );

                    return true;
                }
        );
    }

    private static void handleAscendedToolTracker(
            ServerPlayer player,
            String blockId
    ) {

        ItemStack stack =
                player.getMainHandItem();

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return;
        }

        if (
                !ProfessionToolMetadata.isProfessionTool(
                        stack
                ) ||
                        !ProfessionToolMetadata.isIdentified(
                                stack
                        ) ||
                        !ProfessionToolMetadata.isAscended(
                                stack
                        )
        ) {
            return;
        }

        String selectedTracker =
                ProfessionToolMetadata.getSelectedTracker(
                        stack
                );

        if (
                selectedTracker == null ||
                        selectedTracker.isBlank()
        ) {
            return;
        }

        if (
                !doesBlockMatchTracker(
                        selectedTracker,
                        blockId
                )
        ) {
            return;
        }

        ProfessionToolMetadata.incrementTracker(
                stack,
                selectedTracker,
                1L
        );

        ProfessionToolManager.refreshToolStack(
                stack
        );
    }

    private static boolean doesBlockMatchTracker(
            String trackerId,
            String blockId
    ) {

        return switch (trackerId) {

            case "stone_mined" ->
                    isStoneTrackedBlock(
                            blockId
                    );

            case "coal_mined" ->
                    blockId.equals("minecraft:coal_ore") ||
                            blockId.equals("minecraft:deepslate_coal_ore");

            case "copper_mined" ->
                    blockId.equals("minecraft:copper_ore") ||
                            blockId.equals("minecraft:deepslate_copper_ore");

            case "iron_mined" ->
                    blockId.equals("minecraft:iron_ore") ||
                            blockId.equals("minecraft:deepslate_iron_ore");

            case "gold_mined" ->
                    blockId.equals("minecraft:gold_ore") ||
                            blockId.equals("minecraft:deepslate_gold_ore") ||
                            blockId.equals("minecraft:nether_gold_ore");

            case "redstone_mined" ->
                    blockId.equals("minecraft:redstone_ore") ||
                            blockId.equals("minecraft:deepslate_redstone_ore");

            case "lapis_mined" ->
                    blockId.equals("minecraft:lapis_ore") ||
                            blockId.equals("minecraft:deepslate_lapis_ore");

            case "emerald_mined" ->
                    blockId.equals("minecraft:emerald_ore") ||
                            blockId.equals("minecraft:deepslate_emerald_ore");

            case "diamonds_mined" ->
                    blockId.equals("minecraft:diamond_ore") ||
                            blockId.equals("minecraft:deepslate_diamond_ore");

            case "ancient_debris_mined" ->
                    blockId.equals("minecraft:ancient_debris");

            default ->
                    false;
        };
    }

    private static boolean isStoneTrackedBlock(
            String blockId
    ) {

        return switch (blockId) {
            case "minecraft:stone",
                 "minecraft:deepslate",
                 "minecraft:granite",
                 "minecraft:diorite",
                 "minecraft:andesite",
                 "minecraft:tuff",
                 "minecraft:calcite",
                 "minecraft:dripstone_block",
                 "minecraft:blackstone",
                 "minecraft:basalt",
                 "minecraft:smooth_basalt" ->
                    true;

            default ->
                    false;
        };
    }

    private static void handleMiningToolPassives(
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

        String command =
                "give " +
                        player.getName().getString() +
                        " " +
                        getBonusDrop(
                                blockId
                        ) +
                        " 1";

        player.getServer()
                .getCommands()
                .performPrefixedCommand(
                        player.getServer()
                                .createCommandSourceStack(),
                        command
                );

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
