package com.champutils.breeding;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

public final class PokemonBreedabilityManager {
    private static int ticks;
    private static int pcTicks;
    private PokemonBreedabilityManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks < 100) return;
            ticks = 0;
            pcTicks++;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                        PokemonBreedability.auditIvChanges(pokemon);
                    }
                    if ((pcTicks % 12) == 0) {
                        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getPC(player)) {
                            PokemonBreedability.auditIvChanges(pokemon);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        });
    }
}
