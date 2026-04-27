package com.champutils;

import com.champutils.commands.*;

import com.champutils.rank.LeaderboardManager;
import com.champutils.rank.ActionBarManager;
import com.champutils.rank.SeasonManager;
import com.champutils.rank.SeasonArchiveManager;
import com.champutils.gym.GymBattleStartHandler;
import com.champutils.gym.GymCommand;
import com.champutils.gym.GymBattleHandler;
import com.champutils.gym.GymRegistry;
import com.champutils.gym.GymConfig;

import com.champutils.config.Config;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.matchmaking.QueueBossBarManager;
import com.champutils.matchmaking.TeamPreviewManager;

import com.champutils.battle.CobblemonBattleHandler;
import com.champutils.battle.CobblemonBattleStartHandler;
import com.champutils.battle.BattleItemUseListener;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.FileWriter;

public class ChampUtilsMod implements ModInitializer {

    @Override
    public void onInitialize() {

        File configDir =
                new File(
                        "config/champutils"
                );

        if(!configDir.exists()){
            configDir.mkdirs();
        }


        File configFile =
                new File(
                        configDir,
                        "rules.json"
                );


        if(!configFile.exists()){

            try(
                    FileWriter writer =
                            new FileWriter(
                                    configFile
                            )
            ){
                writer.write("{}");
            }
            catch(Exception ignored){}
        }



/* =========================
 LOAD MAIN CONFIG
========================= */

        Config.load(
                configFile
        );


/* =========================
 LOAD GYM CONFIGS
========================= */

        GymConfig.load();

        GymRegistry.load();



/* =========================
 LOAD SEASON STATE
========================= */

        SeasonManager.loadState();



/* =========================
 INITIAL LEADERBOARD BUILD
========================= */

        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {

                    LeaderboardManager.refresh(
                            server
                    );

                    System.out.println(
                            "[ChampUtils] Leaderboard loaded."
                    );
                }
        );



/* =========================
 PLAYER FILE CREATION
========================= */

        ServerPlayConnectionEvents.JOIN.register(
                (handler,sender,server)->{

                    String playerName =
                            handler.player
                                    .getName()
                                    .getString();

                    SeasonArchiveManager
                            .ensurePlayerFile(
                                    playerName
                            );

                }
        );



/* =========================
 COMMANDS
========================= */

        MenuCommand.register();

        SeasonCommand.register();

        LeaderboardCommand.register();

        GymCommand.register();

        EVTrainingCommand.register();

        EliteFourCommand.register();



/* =========================
 BATTLE HOOKS
========================= */

        CobblemonBattleHandler.register();

        CobblemonBattleStartHandler.register();

        BattleItemUseListener.register();

        GymBattleHandler.register();

        GymBattleStartHandler.register();



/* =========================
 SERVER TICKS
========================= */

        ServerTickEvents.END_SERVER_TICK.register(
                server -> {

                    if(
                            server.getTickCount() > 0 &&
                                    server.getTickCount() % 600 == 0
                    ){

                        LeaderboardManager.refresh(
                                server
                        );
                    }



                    if(
                            server.getTickCount() % 20 == 0
                    ){

                        for(
                                ServerPlayer player :
                                server.getPlayerList()
                                        .getPlayers()
                        ){

                            ActionBarManager.update(
                                    player
                            );
                        }

                    }


                    MatchmakingManager.tick();

                    QueueBossBarManager.tick();

                    TeamPreviewManager.tick(
                            server.getPlayerList()
                                    .getPlayers()
                    );

                }
        );



        System.out.println(
                "[ChampUtils] Gym system loaded."
        );

    }

}