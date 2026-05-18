package com.champutils;

/*
 =========================
 Internal package imports
 =========================
*/
import com.champutils.battle.*;
import com.champutils.commands.*;
import com.champutils.config.*;
import com.champutils.database.DatabaseManager;
import com.champutils.database.DatabaseBootstrapSync;
import com.champutils.database.ServerStatusDatabaseRepository;
import com.champutils.gym.*;
import com.champutils.matchmaking.*;
import com.champutils.menu.*;
import com.champutils.profession.*;
import com.champutils.profession.actives.ActiveAbilityRegistry;
import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.passives.PassiveRegistry;
import com.champutils.profile.*;
import com.champutils.rank.*;
import com.champutils.worldevent.*;
import com.champutils.trainer.*;
import com.champutils.dungeon.*;
import com.champutils.economy.EconomyManager;
import com.champutils.notifications.NotificationManager;
import com.champutils.auction.*;

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
         DATABASE
         =========================
         */
        DatabaseManager.init();
        EconomyManager.load();

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
        ProfessionFragmentConfig.load();
        ActiveAbilityRegistry.registerDefaults();
        PassiveRegistry.registerDefaults();
        ProfessionFragmentManager.registerFragments();
        ProfessionFragmentUseListener.register();
        ProfessionToolManager.registerTools();
        ProfessionToolRequirementListener.register();
        ProfessionToolActiveAbilityListener.register();
        ProfessionToolStatEffectListener.register();
        ProfessionToolFastMiningListener.register();
        ProfessionToolAnnouncementManager.register();
        ItemRollCommand.register();
        ProfessionSalvageCommand.register();

        /*
         Profession loot config
         */
        ProfessionLootConfig.load();
        ProfessionRewardPassiveConfig.load();

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
         WORLD EVENT CONFIG
         =========================
         */
        WorldEventConfig.load();
        WorldEventBindingRegistry.load();

        /*
         =========================
         DUNGEON CONFIG
         =========================
         */
        DungeonKeyConfig.load();
        DungeonKeyDropConfig.load();
        DungeonConfig.load();
        DungeonBindingRegistry.load();
        DungeonNativeCrateRegistry.load();
        AuctionHouseNpcBindingRegistry.load();
        MenuNpcBindingRegistry.load();
        DungeonTrainerConfig.load();
        DungeonRewardConfig.load();
        DungeonKeyManager.registerKeys();

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
                    DungeonNativeCrateRegistry.respawnAllHolograms(server);
                    ServerStatusDatabaseRepository.sync(server);
                    DatabaseBootstrapSync.syncExistingLocalData();
                    EconomyManager.syncAllToDatabase();

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
                    EconomyManager.save();
                    ProfessionBlockTracker.save();
                    WorldEventBindingRegistry.save();
                    DungeonBindingRegistry.save();
                    DungeonNativeCrateRegistry.save();
                    AuctionHouseNpcBindingRegistry.save();
                    MenuNpcBindingRegistry.save();
                    DungeonManager.handleServerStopping(server);
                    ServerStatusDatabaseRepository.markOffline(server);
                    DatabaseManager.shutdown();

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

                    EconomyManager.ensurePlayer(
                            player
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

                    DungeonManager.handleJoinCleanup(
                            player
                    );

                    NotificationManager.handleJoin(
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

                    DungeonManager.handleDisconnect(
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
        ChampUtilsHelpCommand.register();
        MenuCommand.register();
        SeasonCommand.register();
        LeaderboardCommand.register();
        GymCommand.register();
        EVTrainingCommand.register();
        EliteFourCommand.register();
        RpAdminCommand.register();
        ProfessionAdminCommand.register();
        ChampReloadCommand.register();
        DatabaseTestCommand.register();
        LinkAccountCommand.register();
        EconomyCommand.register();
        AuctionHouseCommand.register();
        NotificationsCommand.register();
        ProfessionPopupsCommand.register();
        MenuNpcCommand.register();
        WorldEventCommand.register();
        SpawnTrainerCommand.register();
        DungeonCommand.register();

        /*
         New custom item test command
         */
        GiveChampItemCommand.register();
        ShowItemCommand.register();
        ItemLockCommand.register();

        /*
         =========================
         BATTLE SYSTEMS
         =========================
         */
        CobblemonBattleHandler.register();
        CobblemonBattleStartHandler.register();
        BattleItemUseListener.register();
        DungeonCommandLock.register();

        GymBattleHandler.register();
        GymBattleStartHandler.register();
        WorldEventBattleListener.register();
        DungeonBattleListener.register();
        DungeonBindInteractionListener.register();
        AuctionHouseBindInteractionListener.register();
        MenuNpcInteractionListener.register();
        DungeonNativeCrateInteractionListener.register();
        DungeonInteractionLock.register();
        ChampTrainerInteractionListener.register();

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

                    DungeonManager.tickTeleportGuard(server);
                    DungeonCrateOpeningGui.tick(server);
                    NotificationManager.tick(server);

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
                        PlaytimeManager.addOnlineMinute(server);
                        ServerStatusDatabaseRepository.sync(server);
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
                     Active profession abilities
                     */
                    ActiveEffectManager.tick(server);

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
                     World event systems
                     */
                    WorldEventManager.tick(server);
                    ChampTrainerProtectionManager.tick(server);

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