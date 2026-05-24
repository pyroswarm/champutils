package com.champutils.teleport;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VanillaPortalBlocker {
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();

    private VanillaPortalBlocker() {}

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 5 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.hasPermissions(4)) continue;
            BlockPos pos = player.blockPosition();
            if (isPortal(player.serverLevel().getBlockState(pos)) || isPortal(player.serverLevel().getBlockState(pos.above()))) {
                player.teleportTo(player.serverLevel(), pos.getX() + 0.5D, pos.getY(), pos.getZ() + 1.5D, player.getYRot(), player.getXRot());
                long now = System.currentTimeMillis();
                long last = LAST_MESSAGE.getOrDefault(player.getUUID(), 0L);
                if (now - last > 3000L) {
                    LAST_MESSAGE.put(player.getUUID(), now);
                    player.sendSystemMessage(Component.literal("Regular Nether/End portals are disabled. Use exploration teleport commands instead.").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private static boolean isPortal(BlockState state) {
        return state != null && (state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL));
    }
}
