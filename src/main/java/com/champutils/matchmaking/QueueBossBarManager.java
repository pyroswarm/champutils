package com.champutils.matchmaking;

import com.champutils.battle.BattleStateManager;
import com.champutils.teleport.SafeTeleportManager;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class QueueBossBarManager {

    private static final Map<UUID, CustomBossEvent> BARS =
            new HashMap<>();

    private static final Map<UUID, Integer> TIMES =
            new HashMap<>();

    private static final Map<UUID, String> TYPES =
            new HashMap<>();

    private static final Map<UUID, Integer> STAGES =
            new HashMap<>();



    public static void start(
            ServerPlayer player,
            String type
    ) {

        if (!SafeTeleportManager.isLive(player)) return;
        stop(player);

        ResourceLocation id =
                new ResourceLocation(
                        "champutils",
                        "queue_" + player.getUUID()
                );

        CustomBossEvent bar =
                new CustomBossEvent(
                        id,
                        Component.literal(
                                type.equalsIgnoreCase("casual")
                                        ? "§bCasual Queue §7: §aIn Queue"
                                        : "§6Ranked Queue §7: §aIn Queue"
                        )
                );

        bar.setColor(
                BossEvent.BossBarColor.YELLOW
        );

        bar.setOverlay(
                BossEvent.BossBarOverlay.PROGRESS
        );

        bar.addPlayer(player);

        bar.setProgress(0f);

        BARS.put(
                player.getUUID(),
                bar
        );

        TIMES.put(
                player.getUUID(),
                0
        );

        TYPES.put(
                player.getUUID(),
                type
        );

        STAGES.put(
                player.getUUID(),
                0
        );
    }



    public static void stop(
            ServerPlayer player
    ) {

        if (player == null) return;
        CustomBossEvent bar =
                BARS.remove(
                        player.getUUID()
                );

        if(bar!=null){
            bar.removeAllPlayers();
        }

        TIMES.remove(
                player.getUUID()
        );

        TYPES.remove(
                player.getUUID()
        );

        STAGES.remove(
                player.getUUID()
        );
    }



    public static void tick() {

        for(
                UUID id :
                new HashMap<>(BARS).keySet()
        ){

            CustomBossEvent bar =
                    BARS.get(id);

            if(bar==null){
                continue;
            }


            ServerPlayer player =
                    bar.getPlayers()
                            .stream()
                            .findFirst()
                            .orElse(null);

            if(!SafeTeleportManager.isLive(player)){
                bar.removeAllPlayers();
                BARS.remove(id);
                TIMES.remove(id);
                TYPES.remove(id);
                STAGES.remove(id);
                continue;
            }


            int ticks=
                    TIMES.getOrDefault(
                            id,
                            0
                    );


            // Keep the boss bar simple for players.
            // Matchmaking can still pause internally while a player is in battle,
            // but the player-facing text only says they are queued.

            bar.setColor(
                    BossEvent.BossBarColor.YELLOW
            );

            if (
                    !BattleStateManager.isInBattle(
                            player
                    )
            ) {
                ticks++;

                TIMES.put(
                        id,
                        ticks
                );
            }

            float progress=
                    Math.min(
                            1f,
                            ticks/1200f
                    );

            bar.setProgress(
                    progress
            );


            String type=
                    TYPES.getOrDefault(
                            id,
                            "Queue"
                    );


            bar.setName(
                    Component.literal(
                            type.equalsIgnoreCase(
                                    "casual"
                            )
                                    ? "§bCasual Queue §7: §aIn Queue"
                                    : "§6Ranked Queue §7: §aIn Queue"
                    )
            );
        }
    }
}
