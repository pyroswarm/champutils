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
import com.champutils.database.NetworkReadySchemaManager;
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
import com.champutils.survival.*;
import com.champutils.crate.*;
import com.champutils.network.*;
import com.champutils.guild.*;
import com.champutils.territory.*;
import com.champutils.chat.*;
import com.champutils.party.*;
import com.champutils.megaboss.*;
import com.champutils.antilag.*;
import com.champutils.moderation.*;

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
        NetworkServerConfig.load();
        GuildConfig.load();
        GuildBuffConfig.load();
        BossConfig.load();
        ChampBattleAIConfig.load();
        TerritoryConfig.load();
        ChatTagConfig.load();

        /*
         =========================
         DATABASE
         =========================
         */
        DatabaseManager.init();
        NetworkReadySchemaManager.ensureAsync();
        EconomyManager.load();
        com.champutils.scoreboard.ScoreboardPreferenceManager.load();
        SellPriceConfig.load();
        NpcShopConfig.load();
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
        CatchStreakManager.load();
        PokemonOriginManager.load();
        EmblemConfig.load();
        RoamingTrainerConfig.load();
        SpecialWildSpawnConfig.load();
        MegaBossConfig.load();
        AntiLagConfig.load();
        ModerationConfig.load();
        ItemBindRegistry.load();
        ExplorationWorldConfig.load();
        ExplorationLootConfig.load();
        ExplorationLootState.load();
        ExplorationWorldManager.load();
        SurvivalWorldConfig.load();
        SurvivalWorldManager.load();
        HomeCommand.load();
        CrateConfig.load();
        CrateCreditManager.load();

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
        ExplorationProtectionListener.register();
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
                    NetworkReadySchemaManager.ensureAsync();
                    TerritoryRepository.refreshAll();
                    DatabaseBootstrapSync.syncExistingLocalData();
                    EconomyManager.syncAllToDatabase();
                    PokemonHuntManager.ensureStarted(server);
                    PokemonWikiIndex.reload(server);
                    ChestShopDisplayManager.syncAll(server);
                    // Do not auto-create/load configured exploration or survival Multiworlds on server restart.
                    // Existing worlds can still be used once they are manually created/loaded.

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
                    BossConfig.save();
                    ItemBindRegistry.save();
                    ExplorationLootState.save();
                    ExplorationWorldManager.save();
                    SurvivalWorldManager.save();
                    HomeCommand.save();
                    FirstJoinKitManager.save();
                    ChestShopRegistry.save();
                    TeleportConfig.save();
                    PortalConfig.save();
                    PokemonHuntManager.save();
                    QuestManager.saveAll();
                    DexRewardClaimData.save();
                    TrueCaughtDexManager.save();
                    CatchStreakManager.save();
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

                    // Do not auto-create/load configured exploration or survival Multiworlds on player join.
                    // Admins can manually create/load more worlds when needed.

                    GuildRepository.loadForPlayer(
                            player.getUUID(),
                            playerName
                    );

                    ChatPreferenceManager.clear(
                            player.getUUID()
                    );

                    ModerationManager.handleJoin(
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

                    PartyManager.handleDisconnect(
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

                    if (!ModerationManager.allowChat(player, message.signedContent())) {
                        return false;
                    }

                    ServerChatManager.handleChat(
                            player,
                            message.signedContent()
                    );

                    return false;
                }
        );

        /*
         =========================
         COMMANDS
         =========================
         */
        ChampUtilsHelpCommand.register();
        ChampAICommand.register();
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
        NetworkDatabaseCommand.register();
        LinkAccountCommand.register();
        EconomyCommand.register();
        AuctionHouseCommand.register();
        NotificationsCommand.register();
        ScoreboardToggleCommand.register();
        ProfessionPopupsCommand.register();
        MenuNpcCommand.register();
        NpcShopCommand.register();
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
        HomeCommand.register();
        com.champutils.teleport.SpawnWarpCommand.register();
        PortalCommand.register();
        RoamingTrainerCommand.register();
        SpecialWildSpawnCommand.register();
        PokemonWikiCommand.register();
        BattleExitCommand.register();
        BattleSpectateCommand.register();
        ItemBindCommand.register();
        OpenCratesCommand.register();
        ExplorationWorldCommand.register();
        TpaCommand.register();
        BackCommand.register();
        GuildCommand.register();
        WorldBossCommand.register();
        TerritoryCommand.register();
        ChatCommand.register();
        PartyCommand.register();
        AutoModCommand.register();

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
        BattleAIDifficultyManager.register();
        CobblemonBattleStartHandler.register();
        BattleItemUseListener.register();

        GymBattleHandler.register();
        GymBattleStartHandler.register();
        WorldEventBattleListener.register();
        MegaBossBattleListener.register();
        MegaBossCaptureBlocker.register();
        WorldEventAreaProtectionListener.register();
        AuctionHouseBindInteractionListener.register();
        MenuNpcInteractionListener.register();
        ItemBindInteractionListener.register();
        ChampTrainerInteractionListener.register();
        PokemonHuntCatchListener.register();
        TrueCaughtDexListener.register();
        CatchStreakSpawnListener.register();
        TradeEvolutionTrueDexListener.register();
        ChestShopInteractionListener.register();
        TerritoryProtectionListener.register();
        TerritoryNpcInteractionListener.register();
        VanillaPortalBlocker.register();
        XrayDetectionManager.register();

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
                    OpenCratesMenu.tick(server);
                    NotificationManager.tick(server);
                    PokemonHuntManager.tick(server);
                    QuestManager.tick(server);
                    RandomTeleportCommand.tick(server);
                    PortalManager.tick(server);
                    RoamingTrainerManager.tick(server);
                    SpecialWildSpawnManager.tick(server);
                    MegaBossManager.tick(server);
                    ChestShopDisplayManager.tick(server);
                    BattleStuckCleanupManager.tick(server);
                    TerritoryBorderManager.tick(server);
                    TerritoryPhysicalBorderManager.tick(server);
                    TerritoryBorderDisplayManager.tick(server);
                    TerritoryWorldGenerationManager.tick(server);
                    TerritorySkyblockIslandManager.tick(server);
                    TerritoryRegionWipeManager.tick(server);
                    TerritoryNpcManager.tick(server);
                    GuildBossManager.tick(server);
                    ExplorationWorldManager.tick(server);
                    SurvivalWorldManager.tick(server);
                    VanillaPortalBlocker.tick(server);
                    PartyManager.tick(server);
                    AntiLagManager.tick(server);
                    ModerationManager.tick(server);

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
                        CatchStreakManager.save();
                        PokemonOriginManager.save();
                        PlaytimeManager.addOnlineMinute(server);
                        ServerStatusDatabaseRepository.sync(server);
                        TerritoryRepository.refreshAll();
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