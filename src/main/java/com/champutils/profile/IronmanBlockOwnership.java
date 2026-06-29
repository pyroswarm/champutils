package com.champutils.profile;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class IronmanBlockOwnership {
    private static final Map<String, BlockOwner> BLOCK_OWNERS = new ConcurrentHashMap<>();
    private static final java.util.List<PendingBreak> PENDING = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean registered = false;

    private IronmanBlockOwnership() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) return InteractionResult.PASS;
            ItemStack stack = serverPlayer.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem)) return InteractionResult.PASS;
            BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
            BLOCK_OWNERS.put(key((ServerLevel) world, placed), new BlockOwner(
                    PlayerProfileManager.activeProfileId(serverPlayer),
                    serverPlayer.getUUID(),
                    PlayerProfileManager.gameMode(serverPlayer)
            ));
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) return;
            BlockOwner placedOwner = BLOCK_OWNERS.remove(key((ServerLevel) world, pos));
            BlockOwner dropOwner = placedOwner != null
                    ? placedOwner
                    : new BlockOwner(PlayerProfileManager.activeProfileId(serverPlayer), serverPlayer.getUUID(), PlayerProfileManager.gameMode(serverPlayer));
            PENDING.add(new PendingBreak((ServerLevel) world, pos.immutable(), dropOwner, 8, 1.5D));
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            Entity attacker = damageSource.getEntity();
            if (!(attacker instanceof ServerPlayer serverPlayer) || !(entity.level() instanceof ServerLevel level)) return;
            BlockOwner dropOwner = new BlockOwner(
                    PlayerProfileManager.activeProfileId(serverPlayer),
                    serverPlayer.getUUID(),
                    PlayerProfileManager.gameMode(serverPlayer)
            );
            PENDING.add(new PendingBreak(level, entity.blockPosition().immutable(), dropOwner, 12, 2.5D));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<PendingBreak> it = PENDING.iterator();
            while (it.hasNext()) {
                PendingBreak pending = it.next();
                markNearbyDrops(pending);
                pending.ticksLeft--;
                if (pending.ticksLeft <= 0) PENDING.remove(pending);
            }
        });
    }

    private static void markNearbyDrops(PendingBreak pending) {
        AABB box = new AABB(pending.pos).inflate(pending.radius);
        for (Entity entity : pending.level.getEntities(null, box)) {
            if (entity instanceof ItemEntity itemEntity && itemEntity.tickCount < 40) {
                IronmanItemOwnership.markEntityOwnerIfUnowned(
                        itemEntity,
                        pending.owner.ownerProfile,
                        pending.owner.ownerPlayer,
                        pending.owner.mode
                );
            }
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        return dim.location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static final class BlockOwner {
        final UUID ownerProfile;
        final UUID ownerPlayer;
        final ProfileGameMode mode;

        private BlockOwner(UUID ownerProfile, UUID ownerPlayer, ProfileGameMode mode) {
            this.ownerProfile = ownerProfile;
            this.ownerPlayer = ownerPlayer;
            this.mode = mode == null ? ProfileGameMode.NORMAL : mode;
        }
    }

    private static final class PendingBreak {
        final ServerLevel level;
        final BlockPos pos;
        final BlockOwner owner;
        final double radius;
        int ticksLeft;
        PendingBreak(ServerLevel level, BlockPos pos, BlockOwner owner, int ticksLeft, double radius) {
            this.level = level;
            this.pos = pos;
            this.owner = owner;
            this.ticksLeft = ticksLeft;
            this.radius = radius;
        }
    }
}
