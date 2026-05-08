package com.champutils;

/*
 =========================
 Internal package imports
 =========================
*/
import com.champutils.battle.*;
import com.champutils.commands.*;
import com.champutils.config.*;
import com.champutils.gym.*;
import com.champutils.matchmaking.*;
import com.champutils.menu.*;
import com.champutils.profession.*;
import com.champutils.profile.*;
import com.champutils.rank.*;

/*
 =========================
 Fabric imports
 =========================
*/
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/*
 =========================
 Minecraft imports
 =========================
*/
import net.minecraft.server.level.ServerPlayer;

/*
 =========================
 Java imports
 =========================
*/
import java.io.File;
import java.io.FileWriter;

public class ChampUtilsMod implements ModInitializer {

    @Override
    public void onInitialize() {

        /*
         =========================
         CONFIG DIRECTORY
         =========================
         */
        File configDir =
                new File("config/champutils");

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File configFile =
                new File(
                        configDir,
                        "rules.json"
                );

        if (!configFile.exists()) {
            try (
                    FileWriter writer =
                            new FileWriter(configFile)
            ) {
                writer.write("{}");
            }
            catch (Exception ignored) {
            }
        }

        Config.load(configFile);

        /*
         =========================
         PROFESSION CONFIGS
         =========================
         */
        ProfessionConfig.load();

        /*
         Custom tools
         */
        ProfessionToolConfig.load();
        ProfessionToolManager.registerTools();
        ProfessionToolRequirementListener.register();
        ProfessionToolActiveAbilityListener.register();
        ItemRollCommand.register();

        /*
         Profession loot config
         */
        ProfessionLootConfig.load();

        /*
         Wild battle loot config
         */
        WildBattleLootConfig.load();

        /*
         Anti exploit block tracking
         */
        ProfessionBlockTracker.load();

        /*
         =========================
         GYM CONFIG
         =========================
         */
        GymConfig.load();
        GymRegistry.load();

        /*
         =========================
         SEASON STATE
         =========================
         */
        SeasonManager.loadState();

        /*
         =========================
         SERVER START
         =========================
         */
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    ServerLifecycleBridge.setServer(server);

                    LeaderboardManager.refresh(server);

                    System.out.println(
                            "[ChampUtils] Leaderboard loaded."
                    );
                }
        );

        /*
         =========================
         SERVER STOP
         =========================
         */
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> {

                    ProfessionManager.saveAll();
                    ProfessionBlockTracker.save();

                    System.out.println(
                            "[ChampUtils] Saved profession data."
                    );
                }
        );

        /*
         =========================
         PLAYER JOIN
         =========================
         */
        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> {

                    ServerPlayer player =
                            handler.player;

                    String playerName =
                            player.getName()
                                    .getString();

                    SeasonArchiveManager.ensurePlayerFile(
                            playerName
                    );

                    PlayerDataManager.ensurePlayer(
                            player.getUUID(),
                            playerName
                    );

                    ProfessionDataManager.ensurePlayer(
                            player.getUUID(),
                            playerName
                    );

                    int storedRp =
                            PlayerDataManager.getRp(
                                    player.getUUID(),
                                    playerName
                            );

                    ProfileManager.setElo(
                            player,
                            storedRp
                    );

                    DisconnectForfeitManager.handleJoin(
                            player
                    );
                }
        );

        /*
         =========================
         PLAYER DISCONNECT
         =========================
         */
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {

                    MatchmakingManager.leaveQueue(
                            handler.player
                    );

                    DisconnectForfeitManager.handleDisconnect(
                            handler.player
                    );

                    ProfessionManager.unloadPlayer(
                            handler.player
                    );
                }
        );

        /*
         =========================
         CHAT LISTENER
         =========================
         */
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                (message, player, params) -> {

                    if (
                            ProfileLookupManager.isWaiting(
                                    player
                            )
                    ) {
                        ProfileLookupManager.handleChat(
                                player,
                                message.signedContent()
                        );

                        return false;
                    }

                    return true;
                }
        );

        /*
         =========================
         COMMANDS
         =========================
         */
        MenuCommand.register();
        SeasonCommand.register();
        LeaderboardCommand.register();
        GymCommand.register();
        EVTrainingCommand.register();
        EliteFourCommand.register();
        RpAdminCommand.register();
        ProfessionAdminCommand.register();

        /*
         New custom item test command
         */
        GiveChampItemCommand.register();

        /*
         =========================
         BATTLE SYSTEMS
         =========================
         */
        CobblemonBattleHandler.register();
        CobblemonBattleStartHandler.register();
        BattleItemUseListener.register();

        GymBattleHandler.register();
        GymBattleStartHandler.register();

        /*
         =========================
         PROFESSION SYSTEMS
         =========================
         */
        MiningProfessionListener.register();
        ForestryProfessionListener.register();
        FarmingProfessionListener.register();
        ProfessionPlacementListener.register();

        /*
         =========================
         SERVER TICK LOOP
         =========================
         */
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {

                    /*
                     Leaderboard refresh
                     */
                    if (
                            server.getTickCount() > 0 &&
                                    server.getTickCount() % 600 == 0
                    ) {
                        LeaderboardManager.refresh(
                                server
                        );
                    }

                    /*
                     Profession autosave
                     */
                    if (
                            server.getTickCount() > 0 &&
                                    server.getTickCount() % 1200 == 0
                    ) {
                        ProfessionManager.saveAll();
                    }

                    /*
                     Ranked action bar
                     */
                    if (
                            server.getTickCount() % 20 == 0
                    ) {
                        for (
                                ServerPlayer player :
                                server.getPlayerList()
                                        .getPlayers()
                        ) {
                            ActionBarManager.update(
                                    player
                            );
                        }
                    }

                    /*
                     Matchmaking systems
                     */
                    MatchmakingManager.tick();
                    QueueBossBarManager.tick();

                    TeamPreviewManager.tick(
                            server.getPlayerList()
                                    .getPlayers()
                    );

                    /*
                     Season systems
                     */
                    SeasonManager.tick(server);
                }
        );

        System.out.println(
                "[ChampUtils] Loaded successfully."
        );
    }
}