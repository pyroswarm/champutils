package com.champutils.profession;

import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.fishing.BobberSpawnPokemonEvent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public class FishingProfessionListener {

    public static void register() {

        CobblemonEvents.BOBBER_SPAWN_POKEMON_POST.subscribe(
                FishingProfessionListener::handleBobberSpawnPokemon
        );
    }

    private static void handleBobberSpawnPokemon(
            BobberSpawnPokemonEvent.Post event
    ) {

        if (
                event == null ||
                        event.getBobber() == null
        ) {
            return;
        }

        Entity owner =
                event.getBobber()
                        .getOwner();

        if (!(owner instanceof ServerPlayer player)) {
            return;
        }

        int xp =
                ProfessionConfig
                        .SETTINGS
                        .fishingXp
                        .getOrDefault(
                                "default",
                                10
                        );

        ProfessionManager.addXp(
                player,
                ProfessionType.FISHING,
                xp
        );

        ProfessionLootManager.rollReward(
                player,
                ProfessionType.FISHING
        );
    }
}