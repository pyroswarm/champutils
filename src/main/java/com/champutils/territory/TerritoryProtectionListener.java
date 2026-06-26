package com.champutils.territory;

import com.champutils.profile.IslanderDebugManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DropperBlock;

public final class TerritoryProtectionListener {

    private TerritoryProtectionListener() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return true;
            TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, pos);
            if (territory == null || TerritoryRepository.canBuild(serverPlayer, territory)) return true;
            IslanderDebugManager.log(serverPlayer, "protect.break", territory, "DENY", "canBuild_false");
            deny(serverPlayer, "You cannot break blocks in " + territory.ownerName + "'s territory.");
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos targetPos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(targetPos);
            ItemStack stack = serverPlayer.getItemInHand(hand);
            BlockPos placedPos = targetPos.relative(hitResult.getDirection());

            TerritoryRepository.Territory targetTerritory = TerritoryRepository.findAt(level, targetPos);
            TerritoryRepository.Territory placedTerritory = TerritoryRepository.findAt(level, placedPos);
            TerritoryRepository.Territory territory = placedTerritory != null ? placedTerritory : targetTerritory;

            if (territory == null) return InteractionResult.PASS;

            if (!TerritoryRepository.canEnter(serverPlayer, territory)) {
                IslanderDebugManager.log(serverPlayer, "protect.useBlock", territory, "DENY", "canEnter_false");
                deny(serverPlayer, "You cannot interact in " + territory.ownerName + "'s territory.");
                return InteractionResult.FAIL;
            }

            if ((stack.getItem() instanceof BucketItem || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) && !TerritoryRepository.canBuild(serverPlayer, territory)) {
                IslanderDebugManager.log(serverPlayer, "protect.useBlock", territory, "DENY", "fluid_or_fire_canBuild_false");
                deny(serverPlayer, "You cannot place fluids or fire in " + territory.ownerName + "'s territory.");
                return InteractionResult.FAIL;
            }

            if (stack.getItem() instanceof BlockItem && !TerritoryRepository.canBuild(serverPlayer, territory)) {
                IslanderDebugManager.log(serverPlayer, "protect.useBlock", territory, "DENY", "place_block_canBuild_false");
                deny(serverPlayer, "You cannot place blocks in " + territory.ownerName + "'s territory.");
                return InteractionResult.FAIL;
            }

            if (isContainer(level, targetPos, state) && !TerritoryRepository.canOpenContainers(serverPlayer, territory)) {
                IslanderDebugManager.log(serverPlayer, "protect.useBlock", territory, "DENY", "canOpenContainers_false");
                deny(serverPlayer, "You cannot open containers in " + territory.ownerName + "'s territory.");
                return InteractionResult.FAIL;
            }

            if (isRedstoneOrDoor(state) && !TerritoryRepository.canUseRedstone(serverPlayer, territory)) {
                IslanderDebugManager.log(serverPlayer, "protect.useBlock", territory, "DENY", "canUseRedstone_false");
                deny(serverPlayer, "You cannot use switches, doors, or redstone in " + territory.ownerName + "'s territory.");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, entity.blockPosition());
            if (territory == null || TerritoryRepository.canInteractEntities(serverPlayer, territory)) return InteractionResult.PASS;
            IslanderDebugManager.log(serverPlayer, "protect.useEntity", territory, "DENY", "canInteractEntities_false");
            deny(serverPlayer, "You cannot interact with entities in " + territory.ownerName + "'s territory.");
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            TerritoryRepository.Territory territory = TerritoryRepository.findAt(level, entity.blockPosition());
            if (territory == null || TerritoryRepository.canInteractEntities(serverPlayer, territory)) return InteractionResult.PASS;
            IslanderDebugManager.log(serverPlayer, "protect.attackEntity", territory, "DENY", "canInteractEntities_false");
            deny(serverPlayer, "You cannot attack entities in " + territory.ownerName + "'s territory.");
            return InteractionResult.FAIL;
        });
    }

    private static boolean isContainer(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity instanceof net.minecraft.world.Container) return true;
        return state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof BarrelBlock
                || state.getBlock() instanceof ShulkerBoxBlock
                || state.getBlock() instanceof HopperBlock
                || state.getBlock() instanceof DispenserBlock
                || state.getBlock() instanceof DropperBlock;
    }

    private static boolean isRedstoneOrDoor(BlockState state) {
        return state.getBlock() instanceof ButtonBlock
                || state.getBlock() instanceof LeverBlock
                || state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
