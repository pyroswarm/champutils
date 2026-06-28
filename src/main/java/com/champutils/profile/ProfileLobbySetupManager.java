package com.champutils.profile;

import com.champutils.menu.MenuNpcBindingRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * Lightweight profile-lobby world bootstrap.
 *
 * The profile selection NPC intentionally lives in vanilla minecraft:overworld on the
 * profile_lobby backend. Do not resolve the MultiWorld profile_lobby dimension for this
 * NPC, or startup binding can point at the wrong world and leave the NPC unbound.
 */
public final class ProfileLobbySetupManager {
    private static final String PROFILE_MENU = "profiles";
    private static final int RETRY_TICKS = 20;
    private static final int WATCHDOG_TICKS = 20 * 30;

    private static boolean registered = false;
    private static boolean boundThisRun = false;
    private static int tickCounter = 0;

    private ProfileLobbySetupManager() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            boundThisRun = false;
            tickCounter = 0;
            ensure(server);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ProfileNetworkTransferFlow.isProfileLobbyServer()) return;

            tickCounter++;
            if (!boundThisRun && tickCounter % RETRY_TICKS == 0) {
                ensure(server);
            } else if (boundThisRun && tickCounter % WATCHDOG_TICKS == 0) {
                ensure(server);
            }

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player == null || player.serverLevel() == null) continue;
                // The network profile_lobby backend uses overworld as the lobby world.
                if (player.serverLevel().dimension() != Level.OVERWORLD) continue;
                applyPlayerRules(player);
            }
        });
    }

    public static void ensure(MinecraftServer server) {
        // Manual NPC mode: do not spawn, move, rotate, or auto-repair the profile NPC.
        // Ops should place it with /spawnblanknpc, bind it with /menunpc bind profiles,
        // and manage it with /npcedit or /npcdelete. The binding registry is persisted
        // separately, so startup does not need to touch the NPC entity.
        if (server == null || !ProfileNetworkTransferFlow.isProfileLobbyServer()) return;
        ProfileLobbyManager.applyProfileWorldSpawn(server.overworld());
        boundThisRun = MenuNpcBindingRegistry.getAll().containsKey(PROFILE_MENU);
    }

    public static void applyPlayerRules(ServerPlayer player) {
        if (player == null) return;
        player.setInvisible(true);
        player.setInvulnerable(true);
        try { player.setGameMode(GameType.ADVENTURE); } catch (Exception ignored) {}
        player.clearFire();
        player.resetFallDistance();
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(20.0F);
        if (player.getHealth() < player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

}
