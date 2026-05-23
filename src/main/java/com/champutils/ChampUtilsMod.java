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
import com.champutils.database.RankedFormatDatabaseRepository;
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
import com.champutils.economy.EconomyManager;
import com.champutils.economy.SellPriceConfig;
import com.champutils.notifications.NotificationManager;
import com.champutils.auction.*;
import com.champutils.shop.*;
import com.champutils.teleport.*;
import com.champutils.hunt.*;
import com.champutils.quest.*;
import com.champutils.dex.*;
import com.champutils.wondertrade.*;
import com.champutils.emblem.*;
import com.champutils.roaming.*;
import com.champutils.specialspawn.*;
import com.champutils.wiki.*;
import com.champutils.exploration.*;
import com.champutils.crate.*;

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
        com.champutils.scoreboard.ScoreboardPreferenceManager.load();
        SellPriceConfig.load();
        NpcShopConfig.load();
        CrateConfig.load();
        CrateCreditManager.load();
        ChestShopRegistry.load();
        FirstJoinKitManager.load();
        PokemonHuntConfig.load();
        PokemonHuntManager.load();
        QuestManager.load();
        TeleportConfig.load();
        PortalConfig.load();
        DefaultSpawnManager.load();
        DefaultSpawnManager.registerRespawnHandler();
        DexRewardConfig.load();
        DexRewardClaimData.load();
        TrueCaughtDexManager.load();
        PokemonOriginManager.load();
        EmblemConfig.load();
        RoamingTrainerConfig.load();
        SpecialWildSpawnConfig.load();
        ItemBindRegistry.load();

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
        EmblemManager.registerEmblems();
        ProfessionFragmentUseListener.register();
        EmblemUseListener.register();
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
         Battle profession loot config
         */
        BattleProfessionLootConfig.load();

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

        AuctionHouseNpcBindingRegistry.load();
        MenuNpcBindingRegistry.load();

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
                    ServerStatusDatabaseRepository.sync(server);
                    RankedFormatDatabaseRepository.syncCurrentFormats();
                    DatabaseBootstrapSync.syncExistingLocalData();
                    EconomyManager.syncAllToDatabase();
                    PokemonHuntManager.ensureStarted(server);
                    PokemonWikiIndex.reload(server);
                    ChestShopDisplayManager.syncAll(server);

                    if (DatabaseManager.isEnabled()) {
                        try {
                            WonderTradeRepository.ensureSchema();
                        }
                        catch (Exception e) {
                            System.err.println("[ChampUtils] Failed to prepare Wondertrade database schema.");
                            e.printStackTrace();
                        }
                    }

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
                    AuctionHouseNpcBindingRegistry.save();
                    MenuNpcBindingRegistry.save();
                    ItemBindRegistry.save();
                    FirstJoinKitManager.save();
                    ChestShopRegistry.save();
                    TeleportConfig.save();
                    PortalConfig.save();
                    PokemonHuntManager.save();
                    QuestManager.saveAll();
                    DexRewardClaimData.save();
                    TrueCaughtDexManager.save();
                    PokemonOriginManager.save();
                    RoamingTrainerManager.despawnAll(server);
                    ShopPokemonCrateOpeningGui.handleServerStopping(server);
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

                    NotificationManager.handleJoin(
                            player
                    );

                    FirstJoinKitManager.handleJoin(
                            player
                    );

                    DefaultSpawnManager.handleJoin(
                            player
                    );

                    QuestManager.handleJoin(
                            player
                    );

                    WonderTradeSeeder.handleJoin(
                            player
                    );

                    ShopPokemonCrateOpeningGui.handleJoin(
                            player
                    );

                    AuctionHouseService.handleJoin(
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

                    ShopPokemonCrateOpeningGui.handleDisconnect(
                            handler.player
                    );

                    ProfessionManager.unloadPlayer(
                            handler.player
                    );

                    QuestManager.unloadPlayer(
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
        ScoreboardToggleCommand.register();
        ProfessionPopupsCommand.register();
        MenuNpcCommand.register();
        NpcShopCommand.register();
        OpenCratesCommand.register();
        WorldEventCommand.register();
        SpawnTrainerCommand.register();
        BlankNpcCommand.register();
        ArenaCommand.register();
        PokemonHuntCommand.register();
        QuestCommand.register();
        ChestShopCommand.register();
        ServerSellCommand.register();
        DexRewardCommand.register();
        TextCommand.register();
        WonderTradeCommand.register();
        EmblemCommand.register();
        RandomTeleportCommand.register();
        com.champutils.teleport.SpawnWarpCommand.register();
        PortalCommand.register();
        RoamingTrainerCommand.register();
        SpecialWildSpawnCommand.register();
        PokemonWikiCommand.register();
        BattleExitCommand.register();
        ItemBindCommand.register();

        /*
         New custom item test command
         */
        GiveChampItemCommand.register();
        ShowItemCommand.register();
        ItemLockCommand.register();
        XpLockCommand.register();

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
        WorldEventBattleListener.register();
        AuctionHouseBindInteractionListener.register();
        MenuNpcInteractionListener.register();
        ItemBindInteractionListener.register();
        ChampTrainerInteractionListener.register();
        PokemonHuntCatchListener.register();
        TrueCaughtDexListener.register();
        ChestShopInteractionListener.register();

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

                    ShopPokemonCrateOpeningGui.tick(server);
                    NotificationManager.tick(server);
                    PokemonHuntManager.tick(server);
                    QuestManager.tick(server);
                    RandomTeleportCommand.tick(server);
                    PortalManager.tick(server);
                    RoamingTrainerManager.tick(server);
                    SpecialWildSpawnManager.tick(server);
                    ChestShopDisplayManager.tick(server);
                    BattleStuckCleanupManager.tick(server);

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
                        QuestManager.saveAll();
                        TrueCaughtDexManager.save();
                        PokemonOriginManager.save();
                        PlaytimeManager.addOnlineMinute(server);
                        ServerStatusDatabaseRepository.sync(server);
                    }

                    /*
                     Scoreboard sidebar + ranked action bar
                     */
                    if (
                            server.getTickCount() % 20 == 0
                    ) {
                        com.champutils.scoreboard.PlayerSidebarManager.tick(server);

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