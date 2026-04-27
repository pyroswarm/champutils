package com.champutils.matchmaking;

import com.champutils.battle.BattleStateManager;

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
                                        ? "§bCasual Queue §7: §aSearching..."
                                        : "§6Ranked Queue §7: §aClose Search"
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

            if(player==null){
                continue;
            }


            int ticks=
                    TIMES.getOrDefault(
                            id,
                            0
                    );


            // =========================
            // TRUE PAUSE
            // no timer movement while paused
            // =========================

            if(
                    BattleStateManager.isInBattle(
                            player
                    )
            ){

                bar.setColor(
                        BossEvent.BossBarColor.RED
                );

                bar.setName(
                        Component.literal(
                                "§cQueue Paused §7( In Battle )"
                        )
                );

                continue;
            }


            // resume active searching
            bar.setColor(
                    BossEvent.BossBarColor.YELLOW
            );

            ticks++;

            TIMES.put(
                    id,
                    ticks
            );


            float progress=
                    Math.min(
                            1f,
                            ticks/2400f
                    );

            bar.setProgress(
                    progress
            );


            String type=
                    TYPES.getOrDefault(
                            id,
                            "Queue"
                    );


            if(
                    type.equalsIgnoreCase(
                            "casual"
                    )
            ){

                bar.setName(
                        Component.literal(
                                "§bCasual Queue §7: §aSearching..."
                        )
                );

                continue;
            }


            int stage=
                    getStage(
                            ticks
                    );

            STAGES.put(
                    id,
                    stage
            );


            String stageText=
                    switch(stage){

                        case 0 ->
                                "§aClose Search";

                        case 1 ->
                                "§eExpanded Search";

                        default ->
                                "§cWide Search";
                    };


            bar.setName(
                    Component.literal(
                            "§6Ranked Queue §7: "
                                    + stageText
                    )
            );
        }
    }



    private static int getStage(
            int ticks
    ){

        if(ticks<1200)
            return 0;

        if(ticks<2400)
            return 1;

        return 2;
    }
}