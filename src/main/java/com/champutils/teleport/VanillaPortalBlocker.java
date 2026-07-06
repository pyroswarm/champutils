package com.champutils.teleport;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaPortalBlocker {
    private static final int PORTAL_SCAN_INTERVAL_TICKS = 100;
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PORTAL_EJECT = new ConcurrentHashMap<>();
    private static boolean registered = false;

    private VanillaPortalBlocker() {}

    public static void register() {
        if (registered) return;
        registered = true;

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            BlockPos clickedPos = hitResult.getBlockPos();
            BlockState clickedState = world.getBlockState(clickedPos);
            ItemStack heldStack = player.getItemInHand(hand);

            if (isPortal(clickedState)) {
                sendBlockedMessage(serverPlayer);
                return InteractionResult.FAIL;
            }

            if (clickedState.is(Blocks.END_PORTAL_FRAME)) {
                sendBlockedMessage(serverPlayer);
                return InteractionResult.FAIL;
            }

            if (isPortalIgnitionItem(heldStack) && isNearObsidianFrame(world.getBlockState(clickedPos), world.getBlockState(clickedPos.relative(hitResult.getDirection())))) {
                sendBlockedMessage(serverPlayer);
                return InteractionResult.FAIL;
            }

            return InteractionResult.PASS;
        });
    }

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % PORTAL_SCAN_INTERVAL_TICKS != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) continue;

            ServerLevel level = player.serverLevel();
            BlockPos playerPos = player.blockPosition();

            if (isPortal(level.getBlockState(playerPos)) || isPortal(level.getBlockState(playerPos.above()))) {
                long now = System.currentTimeMillis();
                long lastEject = LAST_PORTAL_EJECT.getOrDefault(player.getUUID(), 0L);
                if (now - lastEject <= 2500L) continue;
                LAST_PORTAL_EJECT.put(player.getUUID(), now);
                SafeTeleportManager.teleportUncheckedNoBack(
                        player,
                        level,
                        playerPos.getX() + 0.5D,
                        playerPos.getY(),
                        playerPos.getZ() + 1.5D,
                        player.getYRot(),
                        player.getXRot()
                );
                sendBlockedMessage(player);
            }
        }
    }

    private static boolean isPortalIgnitionItem(ItemStack stack) {
        return stack != null && (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE));
    }

    private static boolean isNearObsidianFrame(BlockState clickedState, BlockState targetState) {
        return clickedState != null && targetState != null && (clickedState.is(Blocks.OBSIDIAN) || targetState.is(Blocks.OBSIDIAN));
    }

    private static boolean isPortal(BlockState state) {
        return state != null && (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL));
    }

    private static void sendBlockedMessage(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long last = LAST_MESSAGE.getOrDefault(player.getUUID(), 0L);
        if (now - last <= 3000L) return;

        LAST_MESSAGE.put(player.getUUID(), now);
        player.sendSystemMessage(
                Component.literal("Nether and End portals are disabled. Use server teleport systems instead.")
                        .withStyle(ChatFormatting.RED)
        );
    }
}
