package com.champutils.profession;

import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.actives.MiningBlockUtil;
import com.champutils.profession.passives.PassiveRegistry;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MiningProfessionListener {

    private static final Set<UUID> BREAKING_EXTRA_BLOCKS =
            new HashSet<>();

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

                        if (!isBreakingExtraBlock(
                                serverPlayer
                        )) {
                            handleExcavationActive(
                                    serverPlayer,
                                    pos,
                                    state
                            );
                        }

                        return true;
                    }

                    if (
                            !isBreakingExtraBlock(
                                    serverPlayer
                            ) &&
                                    handleAutoSmeltActive(
                                            serverPlayer,
                                            pos,
                                            state,
                                            blockId
                                    )
                    ) {
                        return false;
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
                        if (!isBreakingExtraBlock(
                                serverPlayer
                        )) {
                            handleExcavationActive(
                                    serverPlayer,
                                    pos,
                                    state
                            );
                        }

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

                    PassiveRegistry.applyMiningPassives(
                            serverPlayer,
                            serverPlayer.serverLevel(),
                            pos,
                            blockId
                    );

                    if (!isBreakingExtraBlock(
                            serverPlayer
                    )) {
                        handleExcavationActive(
                                serverPlayer,
                                pos,
                                state
                        );
                    }

                    return true;
                }
        );
    }

    private static boolean handleAutoSmeltActive(
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            String blockId
    ) {

        if (!ActiveEffectManager.hasAutoSmelt(
                player
        )) {
            return false;
        }

        SmeltDrop smeltDrop =
                getSmeltDrop(
                        blockId
                );

        if (smeltDrop == null) {
            return false;
        }

        if (!MiningBlockUtil.isPickaxeBlock(
                player.serverLevel(),
                pos,
                state
        )) {
            return false;
        }

        handleAscendedToolTracker(
                player,
                blockId
        );

        Integer xp =
                ProfessionConfig
                        .SETTINGS
                        .miningXp
                        .get(blockId);

        if (xp != null && xp > 0) {
            ProfessionManager.addXp(
                    player,
                    ProfessionType.MINING,
                    xp
            );

            ProfessionLootManager.rollReward(
                    player,
                    ProfessionType.MINING
            );

            PassiveRegistry.applyMiningPassives(
                    player,
                    player.serverLevel(),
                    pos,
                    blockId
            );
        }

        player.serverLevel().destroyBlock(
                pos,
                false,
                player
        );

        giveOrDrop(
                player,
                new ItemStack(
                        smeltDrop.item,
                        smeltDrop.amount
                )
        );

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§6Auto-Smelt: §fOre smelted."
                ),
                true
        );

        handleExcavationActive(
                player,
                pos,
                state
        );

        return true;
    }

    private static void giveOrDrop(
            ServerPlayer player,
            ItemStack stack
    ) {

        if (!player.getInventory().add(
                stack
        )) {
            player.drop(
                    stack,
                    false
            );
        }
    }

    private static SmeltDrop getSmeltDrop(
            String blockId
    ) {

        return switch (blockId) {

            case "minecraft:iron_ore",
                 "minecraft:deepslate_iron_ore" ->
                    new SmeltDrop(
                            Items.IRON_INGOT,
                            1
                    );

            case "minecraft:gold_ore",
                 "minecraft:deepslate_gold_ore",
                 "minecraft:nether_gold_ore" ->
                    new SmeltDrop(
                            Items.GOLD_INGOT,
                            1
                    );

            case "minecraft:copper_ore",
                 "minecraft:deepslate_copper_ore" ->
                    new SmeltDrop(
                            Items.COPPER_INGOT,
                            3
                    );

            case "minecraft:ancient_debris" ->
                    new SmeltDrop(
                            Items.NETHERITE_SCRAP,
                            1
                    );

            default ->
                    null;
        };
    }

    private record SmeltDrop(
            Item item,
            int amount
    ) {
    }

    private static void handleExcavationActive(
            ServerPlayer player,
            BlockPos center,
            BlockState centerState
    ) {

        if (!ActiveEffectManager.hasExcavation(
                player
        )) {
            return;
        }

        ServerLevel level =
                player.serverLevel();

        if (!MiningBlockUtil.isPickaxeBlock(
                level,
                center,
                centerState
        )) {
            return;
        }

        ItemStack tool =
                player.getMainHandItem();

        if (
                tool == null ||
                        tool.isEmpty() ||
                        !ProfessionToolMetadata.isProfessionTool(
                                tool
                        ) ||
                        !ProfessionToolMetadata.isIdentified(
                                tool
                        ) ||
                        ProfessionToolMetadata.isBroken(
                                tool
                        )
        ) {
            return;
        }

        BREAKING_EXTRA_BLOCKS.add(
                player.getUUID()
        );

        try {
            breakExcavationArea(
                    player,
                    center
            );
        }
        finally {
            BREAKING_EXTRA_BLOCKS.remove(
                    player.getUUID()
            );
        }
    }

    private static void breakExcavationArea(
            ServerPlayer player,
            BlockPos center
    ) {

        ServerLevel level =
                player.serverLevel();

        Direction direction =
                getMiningPlaneDirection(
                        player
                );

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {

                if (a == 0 && b == 0) {
                    continue;
                }

                BlockPos target =
                        offsetForPlane(
                                center,
                                direction,
                                a,
                                b
                        );

                BlockState targetState =
                        level.getBlockState(
                                target
                        );

                if (!MiningBlockUtil.isPickaxeBlock(
                        level,
                        target,
                        targetState
                )) {
                    continue;
                }


                String targetBlockId =
                        targetState.getBlock()
                                .builtInRegistryHolder()
                                .key()
                                .location()
                                .toString();

                /*
                 * ServerLevel.destroyBlock does not reliably flow back through
                 * the player's normal block-break callback in every mapping/version.
                 * Apply mining passives explicitly here so Excavation's extra
                 * 3x3 blocks can trigger Double/Triple/Quadruple/Quintuple Drop.
                 * PassiveRegistry still enforces natural-only blocks.
                 */
                PassiveRegistry.applyMiningPassives(
                        player,
                        level,
                        target,
                        targetBlockId
                );

                level.destroyBlock(
                        target,
                        true,
                        player
                );
            }
        }
    }

    private static Direction getMiningPlaneDirection(
            ServerPlayer player
    ) {

        float pitch =
                player.getXRot();

        if (pitch > 55.0F || pitch < -55.0F) {
            return Direction.UP;
        }

        return player.getDirection();
    }

    private static BlockPos offsetForPlane(
            BlockPos center,
            Direction direction,
            int a,
            int b
    ) {

        return switch (direction) {
            case EAST, WEST ->
                    center.offset(
                            0,
                            a,
                            b
                    );

            case NORTH, SOUTH ->
                    center.offset(
                            a,
                            b,
                            0
                    );

            case UP, DOWN ->
                    center.offset(
                            a,
                            0,
                            b
                    );
        };
    }

    private static boolean isBreakingExtraBlock(
            ServerPlayer player
    ) {

        return BREAKING_EXTRA_BLOCKS.contains(
                player.getUUID()
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
}
