package com.champutils.claims;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public final class LandClaimProtectionListener {
    private LandClaimProtectionListener() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return true;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, pos);
            if (claim == null || LandClaimRepository.canBuild(serverPlayer, claim)) return true;
            deny(serverPlayer, "You cannot break blocks in " + claim.ownerName + "'s claim.");
            return false;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos targetPos = hitResult.getBlockPos();
            BlockState state = level.getBlockState(targetPos);
            ItemStack stack = serverPlayer.getItemInHand(hand);
            BlockPos placedPos = targetPos.relative(hitResult.getDirection());

            LandClaimRepository.Claim targetClaim = LandClaimRepository.findAt(level, targetPos);
            LandClaimRepository.Claim placedClaim = LandClaimRepository.findAt(level, placedPos);
            LandClaimRepository.Claim claim = placedClaim != null ? placedClaim : targetClaim;
            if (claim == null) return InteractionResult.PASS;

            if (!LandClaimRepository.canEnter(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot interact in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (isDangerousTransportItem(stack) && !LandClaimRepository.canBuild(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot place item-transfer blocks or hopper minecarts into " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (stack.getItem() instanceof BlockItem && !LandClaimRepository.canBuild(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot place blocks in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (isContainer(level, targetPos, state) && !LandClaimRepository.canOpenContainers(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot open containers in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            if (isRedstoneOrDoor(state) && !LandClaimRepository.canUseRedstone(serverPlayer, claim)) {
                deny(serverPlayer, "You cannot use switches, doors, gates, hoppers, or redstone in " + claim.ownerName + "'s claim.");
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
            if (claim == null || LandClaimRepository.canInteractEntities(serverPlayer, claim)) return InteractionResult.PASS;
            deny(serverPlayer, "You cannot interact with entities in " + claim.ownerName + "'s claim.");
            return InteractionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClientSide() || !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            LandClaimRepository.Claim claim = LandClaimRepository.findAt(level, entity.blockPosition());
            if (claim == null || LandClaimRepository.canInteractEntities(serverPlayer, claim)) return InteractionResult.PASS;
            deny(serverPlayer, "You cannot attack entities in " + claim.ownerName + "'s claim.");
            return InteractionResult.FAIL;
        });

        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(world instanceof ServerLevel level)) return;
            if (isHopperMinecart(entity) && LandClaimRepository.findAt(level, entity.blockPosition()) != null) {
                entity.discard();
            }
        });
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 20 != 0) return;
        for (ServerLevel level : server.getAllLevels()) {
            String worldName = level.dimension().location().toString();
            for (LandClaimRepository.Claim claim : LandClaimRepository.allCached()) {
                if (claim == null || !claim.worldName.equalsIgnoreCase(worldName)) continue;
                AABB box = new AABB(claim.minX, level.getMinBuildHeight(), claim.minZ, claim.maxX + 1.0D, level.getMaxBuildHeight(), claim.maxZ + 1.0D);
                for (Entity entity : level.getEntities((Entity) null, box, LandClaimProtectionListener::isHopperMinecart)) {
                    entity.discard();
                }
            }
        }
    }

    private static boolean isDangerousTransportItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(Items.HOPPER_MINECART) || stack.is(Items.HOPPER) || stack.is(Items.DISPENSER) || stack.is(Items.DROPPER);
    }

    private static boolean isHopperMinecart(Entity entity) {
        return entity instanceof MinecartHopper || entity.getType() == EntityType.HOPPER_MINECART;
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
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof HopperBlock
                || state.getBlock() instanceof DispenserBlock
                || state.getBlock() instanceof DropperBlock;
    }

    private static void deny(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.RED));
    }
}
