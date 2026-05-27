package com.champutils.profile;

import com.champutils.menu.ProfileSelectionMenu;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hard locks the Wynncraft-style profile lobby state.
 *
 * A player in this state has no active ChampUtils profile and should not be able
 * to affect the real world, use items, battle, open shops, run gameplay
 * commands, or keep drifting away from the selector area.
 */
public final class ProfileLobbyLockManager {
    private static final double LOBBY_X = 0.5D;
    private static final double LOBBY_Y = 128.0D;
    private static final double LOBBY_Z = 0.5D;
    private static final double MAX_DISTANCE_SQUARED = 36.0D;
    private static final int REOPEN_MENU_EVERY_TICKS = 80;
    private static final long DENY_COOLDOWN_MS = 1500L;

    private static final Map<UUID, Long> LAST_DENY = new ConcurrentHashMap<>();
    private static int tickCounter = 0;
    private static boolean registered = false;

    private ProfileLobbyLockManager() {}

    public static void register() {
        if (registered) return;
        registered = true;

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer && isLocked(serverPlayer)) {
                deny(serverPlayer);
                return false;
            }
            return true;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && isLocked(serverPlayer)) {
                deny(serverPlayer);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && isLocked(serverPlayer)) {
                deny(serverPlayer);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && isLocked(serverPlayer)) {
                ProfileSelectionMenu.open(serverPlayer);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && isLocked(serverPlayer)) {
                deny(serverPlayer);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer serverPlayer && isLocked(serverPlayer)) {
                ProfileSelectionMenu.open(serverPlayer);
                return InteractionResultHolder.fail(serverPlayer.getItemInHand(hand));
            }
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        });

        ServerTickEvents.END_SERVER_TICK.register(ProfileLobbyLockManager::tick);
    }

    public static boolean isLocked(ServerPlayer player) {
        return player != null && PlayerProfileManager.isInMainMenu(player);
    }

    public static boolean isAllowedCommand(String command) {
        if (command == null) return false;
        String clean = command.startsWith("/") ? command.substring(1) : command;
        clean = clean.trim().toLowerCase();
        return clean.equals("profiles")
                || clean.startsWith("profiles ")
                || clean.equals("profilemode")
                || clean.startsWith("profilemode ");
    }

    public static void deny(ServerPlayer player) {
        if (player == null) return;
        long now = System.currentTimeMillis();
        long last = LAST_DENY.getOrDefault(player.getUUID(), 0L);
        if (now - last >= DENY_COOLDOWN_MS) {
            LAST_DENY.put(player.getUUID(), now);
            player.sendSystemMessage(Component.literal("Select a profile before playing. Use /profiles.").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isLocked(player)) continue;

            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.resetFallDistance();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            player.clearFire();

            double dx = player.getX() - LOBBY_X;
            double dy = player.getY() - LOBBY_Y;
            double dz = player.getZ() - LOBBY_Z;
            if ((dx * dx + dy * dy + dz * dz) > MAX_DISTANCE_SQUARED) {
                ProfileMainMenuManager.teleportToMenu(player);
            }

            if (tickCounter % REOPEN_MENU_EVERY_TICKS == 0 && player.containerMenu == player.inventoryMenu) {
                ProfileSelectionMenu.open(player);
            }
        }
    }
}
