package com.champutils.profile;

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
    private static final Map<String, UUID> BLOCK_OWNERS = new ConcurrentHashMap<>();
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
            if (!IronmanItemOwnership.isRestricted(serverPlayer)) return InteractionResult.PASS;
            BlockPos placed = hit.getBlockPos().relative(hit.getDirection());
            BLOCK_OWNERS.put(key((ServerLevel) world, placed), PlayerProfileManager.activeProfileId(serverPlayer));
            return InteractionResult.PASS;
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer serverPlayer) || world.isClientSide()) return;
            UUID owner = BLOCK_OWNERS.remove(key((ServerLevel) world, pos));
            if (owner == null) return;
            UUID breaker = PlayerProfileManager.activeProfileId(serverPlayer);
            if (breaker != null && breaker.equals(owner)) return;
            PENDING.add(new PendingBreak((ServerLevel) world, pos.immutable(), owner, 8));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<PendingBreak> it = PENDING.iterator();
            while (it.hasNext()) {
                PendingBreak pending = it.next();
                tagNearbyDrops(pending);
                pending.ticksLeft--;
                if (pending.ticksLeft <= 0) PENDING.remove(pending);
            }
        });
    }

    private static void tagNearbyDrops(PendingBreak pending) {
        AABB box = new AABB(pending.pos).inflate(1.5);
        for (Entity entity : pending.level.getEntities(null, box)) {
            if (entity instanceof ItemEntity itemEntity) {
                itemEntity.addTag(IronmanItemOwnership.DROP_TAG_PREFIX + pending.ownerProfile);
            }
        }
    }

    private static String key(ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        return dim.location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static final class PendingBreak {
        final ServerLevel level;
        final BlockPos pos;
        final UUID ownerProfile;
        int ticksLeft;
        PendingBreak(ServerLevel level, BlockPos pos, UUID ownerProfile, int ticksLeft) {
            this.level = level;
            this.pos = pos;
            this.ownerProfile = ownerProfile;
            this.ticksLeft = ticksLeft;
        }
    }
}
