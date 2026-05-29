package com.champutils.profile;

import com.cobblemon.mod.common.api.spawning.spawner.PlayerSpawnerFactory;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class IslanderSpawningManager {
    private static boolean registered = false;

    private IslanderSpawningManager() {}

    public static void load() {
        IslanderSpawningConfig.load();
        IslanderSpawnInfluence.clearCache();
    }

    public static void register() {
        if (registered) return;
        registered = true;
        PlayerSpawnerFactory.INSTANCE.getInfluenceBuilders().add(new Function1<ServerPlayer, com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence>() {
            @Override
            public com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence invoke(ServerPlayer player) {
                return IslanderSpawningConfig.CONFIG.enabled ? new IslanderSpawnInfluence(player) : null;
            }
        });
        System.out.println("[ChampUtils] Islander Cobblemon spawn override registered.");
    }

    public static void handleServerStarted(MinecraftServer server) {
        IslanderSpawnInfluence.clearCache();
    }
}
