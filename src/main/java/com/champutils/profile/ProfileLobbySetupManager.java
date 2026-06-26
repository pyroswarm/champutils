package com.champutils.profile;

import com.champutils.menu.MenuNpcBindingRegistry;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight profile-lobby world bootstrap.
 * Keeps the dedicated profile_lobby backend deterministic and packet-quiet:
 * fixed spawn, adventure mode, invisible players, and one bound profile NPC.
 */
public final class ProfileLobbySetupManager {
    private static boolean registered = false;
    private static boolean ensuredThisRun = false;

    private ProfileLobbySetupManager() {}

    public static void register() {
        if (registered) return;
        registered = true;

        ServerLifecycleEvents.SERVER_STARTED.register(ProfileLobbySetupManager::ensure);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!ProfileNetworkTransferFlow.isProfileLobbyServer()) return;
            if (!ensuredThisRun) ensure(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player == null || player.serverLevel() == null) continue;
                if (!ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(player.serverLevel().dimension().location().toString())) continue;
                applyPlayerRules(player);
            }
        });
    }

    public static void ensure(MinecraftServer server) {
        if (server == null || !ProfileNetworkTransferFlow.isProfileLobbyServer()) return;
        ServerLevel level = server.getLevel(ProfileLobbyManager.resolveLobbyKey());
        if (level == null) level = server.overworld();
        if (level == null) return;

        ProfileLobbyManager.applyProfileWorldSpawn(level);
        ensureProfileNpc(level);
        ensuredThisRun = true;
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

    private static void ensureProfileNpc(ServerLevel level) {
        MenuNpcBindingRegistry.Binding existing = MenuNpcBindingRegistry.getAll().get("profiles");
        if (existing != null && existing.uuid() != null && level.getEntity(existing.uuid()) instanceof NPCEntity) {
            return;
        }

        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(
                level,
                new Vec3(ProfileLobbyManager.PROFILE_NPC_X, ProfileLobbyManager.PROFILE_NPC_Y, ProfileLobbyManager.PROFILE_NPC_Z),
                270.0F,
                "Select a Profile",
                ""
        );
        if (npc == null) return;

        makeLobbyNpc(npc);
        MenuNpcBindingRegistry.bind("profiles", npc);
        System.out.println("[ChampUtils][ProfileLobby] Spawned and bound Select a Profile NPC at 108 65 0 uuid=" + npc.getUUID());
    }

    private static void makeLobbyNpc(NPCEntity npc) {
        if (npc == null) return;
        try { npc.setCustomName(Component.literal("Select a Profile")); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        try { npc.setBattle(null); } catch (Exception ignored) {}
        try { npc.setParty(null); } catch (Exception ignored) {}
        try { npc.setInteraction(null); } catch (Exception ignored) {}
        try { npc.setSkill(null); } catch (Exception ignored) {}
        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setInvulnerable(Boolean.TRUE); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setMovable(Boolean.FALSE); } catch (Exception ignored) {}
        try { ((net.minecraft.world.entity.Entity) npc).setInvulnerable(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
    }
}
