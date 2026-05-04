package com.champutils.profession;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

public class ForestryProfessionListener {

    public static void register() {

        PlayerBlockBreakEvents.BEFORE.register(
                (
                        world,
                        player,
                        pos,
                        state,
                        blockEntity
                ) -> {

                    if (!(player instanceof ServerPlayer serverPlayer)) {
                        return true;
                    }

                    Block block =
                            state.getBlock();

                    String blockId =
                            block.builtInRegistryHolder()
                                    .key()
                                    .location()
                                    .toString();

                    /*
                     Ignore anything not configured for forestry immediately
                     */
                    Integer xp =
                            ProfessionConfig
                                    .SETTINGS
                                    .forestryXp
                                    .get(blockId);

                    if (xp == null || xp <= 0) {
                        return true;
                    }

                    if (
                            ProfessionBlockTracker.isPlayerPlaced(
                                    serverPlayer.serverLevel(),
                                    pos
                            )
                    ) {
                        ProfessionBlockTracker.remove(
                                serverPlayer.serverLevel(),
                                pos
                        );
                        return true;
                    }

                    ProfessionManager.addXp(
                            serverPlayer,
                            ProfessionType.FORESTRY,
                            xp
                    );

                    ProfessionLootManager.rollReward(
                            serverPlayer,
                            ProfessionType.FORESTRY
                    );

                    return true;
                }
        );
    }
}