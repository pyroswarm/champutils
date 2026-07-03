package com.champutils.afk;

import com.champutils.battle.BattleStateManager;
import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.profile.ProfileLoadingStateManager;
import com.champutils.profile.PlayerProfileManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class AntiAfkManager {
    private static final Map<UUID, PlayerActivity> ACTIVITY = new HashMap<>();
    private static int tickCounter = 0;

    private AntiAfkManager() {}

    public static void load() {
        AntiAfkConfig.load();
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, params) -> {
            markRealActivity(player, "chat");
            return true;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) markRealActivity(serverPlayer, "use_block");
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer) markSoftActivity(serverPlayer, "use_item");
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) markRealActivity(serverPlayer, "use_entity");
            return InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer) markRealActivity(serverPlayer, "attack_block");
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) markRealActivity(serverPlayer, "attack_entity");
            return InteractionResult.PASS;
        });
    }

    public static void handleJoin(ServerPlayer player) {
        PlayerActivity state = state(player);
        state.lastRealActivityTick = now(player);
        state.lastSoftActivityTick = state.lastRealActivityTick;
        state.lastMeaningfulMoveTick = state.lastRealActivityTick;
        state.lastPosition = player.position();
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player != null) ACTIVITY.remove(player.getUUID());
    }

    public static void markRealActivity(ServerPlayer player, String reason) {
        if (player == null) return;
        PlayerActivity state = state(player);
        int now = now(player);
        state.lastRealActivityTick = now;
        state.lastSoftActivityTick = now;
        state.warned = false;
        state.lastReason = reason;
    }

    public static void markSoftActivity(ServerPlayer player, String reason) {
        if (player == null) return;
        PlayerActivity state = state(player);
        state.lastSoftActivityTick = now(player);
        state.lastReason = reason;
    }

    public static void recordContainerClick(ServerPlayer player) {
        // Inventory/menu clicks are useful while a real menu is open, but repeated identical clicks should not beat AFK alone forever.
        if (player == null) return;
        if (player.containerMenu != player.inventoryMenu) {
            markRealActivity(player, "container_menu");
        } else {
            markSoftActivity(player, "inventory_click");
        }
    }

    public static void recordSwing(ServerPlayer player) {
        markSoftActivity(player, "swing");
    }

    public static void recordMove(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
        if (player == null) return;
        PlayerActivity state = state(player);
        int now = now(player);
        Vec3 current = new Vec3(x, y, z);
        Vec3 previous = state.lastPosition;
        state.lastPosition = current;
        state.lastSoftActivityTick = now;

        if (previous == null) {
            state.lastMeaningfulMoveTick = now;
            state.moveSamples.addLast(new MoveSample(now, current));
            return;
        }

        double distance = previous.distanceTo(current);
        boolean meaningfulDistance = distance >= 0.18D;
        boolean meaningfulLook = Math.abs(yaw - state.lastYaw) >= 18.0F || Math.abs(pitch - state.lastPitch) >= 18.0F;
        state.lastYaw = yaw;
        state.lastPitch = pitch;

        if (!meaningfulDistance && !meaningfulLook) return;

        state.moveSamples.addLast(new MoveSample(now, current));
        trimMoveSamples(state, now);

        if (isTinyLoop(state)) {
            state.suspiciousLoopTicks += 20;
            return;
        }

        state.suspiciousLoopTicks = Math.max(0, state.suspiciousLoopTicks - 20);
        double traveled = totalTravelDistance(state);
        if (traveled >= AntiAfkConfig.get().minMeaningfulMoveBlocks) {
            state.lastMeaningfulMoveTick = now;
            state.lastRealActivityTick = now;
            state.warned = false;
            state.lastReason = "movement";
        }
    }

    public static boolean isProtectedFromNormalAfk(ServerPlayer player) {
        if (player == null) return true;
        if (!AntiAfkConfig.get().kickDuringProfileLoading && ProfileLoadingStateManager.isLoading(player)) return true;
        if (PlayerProfileManager.isInMainMenu(player)) return true;
        if (BattleStateManager.isInBattle(player)) return true;
        if (player.containerMenu != player.inventoryMenu) return true;
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !AntiAfkConfig.get().enabled) return;
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, PlayerActivity>> iterator = ACTIVITY.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerActivity> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || player.hasDisconnected()) {
                iterator.remove();
                continue;
            }

            PlayerActivity state = entry.getValue();
            trimMoveSamples(state, now);

            if (isProtectedFromNormalAfk(player)) {
                state.lastRealActivityTick = now;
                state.warned = false;
                continue;
            }

            int idleSeconds = Math.max(0, (now - state.lastRealActivityTick) / 20);
            AntiAfkConfig config = AntiAfkConfig.get();

            if (!state.warned && idleSeconds >= config.warnAfterSeconds) {
                state.warned = true;
                player.sendSystemMessage(Component.literal("§eYou look AFK. Do something real soon or you will be kicked."));
            }

            if (idleSeconds >= config.kickAfterSeconds) {
                player.connection.disconnect(Component.literal("Kicked for being AFK."));
            }
        }
    }

    private static PlayerActivity state(ServerPlayer player) {
        return ACTIVITY.computeIfAbsent(player.getUUID(), ignored -> new PlayerActivity(now(player), player.position()));
    }

    private static int now(ServerPlayer player) {
        return player.getServer() == null ? 0 : player.getServer().getTickCount();
    }

    private static void trimMoveSamples(PlayerActivity state, int now) {
        int oldest = now - (AntiAfkConfig.get().repeatedPatternWindowSeconds * 20);
        while (!state.moveSamples.isEmpty() && state.moveSamples.peekFirst().tick < oldest) {
            state.moveSamples.removeFirst();
        }
    }

    private static boolean isTinyLoop(PlayerActivity state) {
        if (state.moveSamples.size() < 6) return false;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (MoveSample sample : state.moveSamples) {
            minX = Math.min(minX, sample.pos.x); minY = Math.min(minY, sample.pos.y); minZ = Math.min(minZ, sample.pos.z);
            maxX = Math.max(maxX, sample.pos.x); maxY = Math.max(maxY, sample.pos.y); maxZ = Math.max(maxZ, sample.pos.z);
        }
        double radius = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        return radius <= AntiAfkConfig.get().maxTinyLoopRadiusBlocks;
    }

    private static double totalTravelDistance(PlayerActivity state) {
        double total = 0.0D;
        MoveSample previous = null;
        for (MoveSample sample : state.moveSamples) {
            if (previous != null) total += previous.pos.distanceTo(sample.pos);
            previous = sample;
        }
        return total;
    }

    private static final class PlayerActivity {
        int lastRealActivityTick;
        int lastSoftActivityTick;
        int lastMeaningfulMoveTick;
        boolean warned;
        String lastReason = "join";
        Vec3 lastPosition;
        float lastYaw;
        float lastPitch;
        int suspiciousLoopTicks;
        final ArrayDeque<MoveSample> moveSamples = new ArrayDeque<>();

        PlayerActivity(int now, Vec3 position) {
            this.lastRealActivityTick = now;
            this.lastSoftActivityTick = now;
            this.lastMeaningfulMoveTick = now;
            this.lastPosition = position;
            this.moveSamples.addLast(new MoveSample(now, position));
        }
    }

    private record MoveSample(int tick, Vec3 pos) {}
}
