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
import com.champutils.dailylogin.*;
import com.champutils.cosmetic.*;
import com.champutils.worldfirst.*;
import com.champutils.cashshop.*;
import com.champutils.worldborder.*;
import com.champutils.gamerule.*;
import com.champutils.tm.*;
import com.champutils.claims.*;

/*
 =========================
 Fabric imports
 =========================
*/
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
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

    private static final long TICK_MANAGER_WARN_NANOS = Long.getLong("champutils.tickManagerWarnMs", 5L) * 1_000_000L;

    private static void timedTick(String name, Runnable task) {
        long start = System.nanoTime();
        try {
            task.run();
        } catch (RuntimeException | Error throwable) {
            System.err.println("[ChampUtils][TickTiming] " + name + " failed: " + throwable.getMessage());
            throw throwable;
        } finally {
            long elapsed = System.nanoTime() - start;
            if (elapsed >= TICK_MANAGER_WARN_NANOS) {
                System.out.println("[ChampUtils][TickTiming] " + name + " took " + (elapsed / 1_000_000.0D) + " ms");
            }
        }
    }

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
        TitleManager.load();
        WorldFirstManager.load();
        PlayerProfileManager.ensureSchemaAsync();
        VanillaProfileStateManager.ensureSchemaAsync();
        CobblemonProfileStorageBridge.ensureSchemaAsync();
        MonotypeStarterManager.ensureSchemaAsync();
        NuzlockeManager.ensureSchemaAsync();
        ChatPreferenceManager.ensureSchemaAsync();
        ProfileLobbyLockManager.register();
        MonotypeStarterManager.register();
        NuzlockeManager.register();
        IronmanItemOwnership.register();
        IronmanBlockOwnership.register();
        IronmanTradeBlocker.register();
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
        ModerationActionRepository.ensureSchemaAsync();
        ItemBindRegistry.load();
        ExplorationWorldConfig.load();
        ExplorationLootConfig.load();
        ExplorationLootState.load();
        ExplorationWorldManager.load();
        SurvivalWorldConfig.load();
        SurvivalWorldManager.load();
        HomeCommand.load();
        BoosterCreditManager.load();
        CrateConfig.load();
        CrateCreditManager.load();
        CrateKeyCraftingConfig.load();
        DailyLoginManager.load();
        ChampWorldBorderConfig.load();
        IslanderSpawningManager.load();
        IslanderMineManager.load();
        GlobalGameruleConfig.load();
        LandClaimConfig.load();
        LandClaimRepository.ensureSchemaAsync();
        LandClaimRepository.refreshAll();

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
        TMManager.registerTMs();
        ProfessionFragmentUseListener.register();
        EmblemUseListener.register();
        TMUseListener.register();
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
                    CobblemonProfileStorageBridge.registerSqlFactory(server);

                    LeaderboardManager.refreshNow(server);
                    ServerStatusDatabaseRepository.sync(server);
                    ChampWorldBorderManager.applyAll(server);
                    GlobalGameruleManager.applyAll(server);
                    IslanderSpawningManager.handleServerStarted(server);
                    RankedFormatDatabaseRepository.syncCurrentFormats();
                    NetworkReadySchemaManager.ensureAsync();
                    PlayerProfileManager.ensureSchemaAsync();
                    VanillaProfileStateManager.ensureSchemaAsync();
                    CobblemonProfileStorageBridge.ensureSchemaAsync();
                    MonotypeStarterManager.ensureSchemaAsync();
                    NuzlockeManager.ensureSchemaAsync();
                    ChatPreferenceManager.ensureSchemaAsync();
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
         WORLD LOAD
         =========================
         */
        ServerWorldEvents.LOAD.register(
                (server, level) -> GlobalGameruleManager.applyToLevel(server, level)
        );

        /*
         =========================
         SERVER STOP
         =========================
         */
        ServerLifecycleEvents.SERVER_STOPPING.register(
                server -> {

                    ForceSaveRestartCommand.forceSave(server);
                    FirstJoinKitManager.save();
                    ChestShopRegistry.save();
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

                    PlayerProfileManager.handleJoin(player);
                    if (PlayerProfileManager.isInMainMenu(player)) {
                        return;
                    }
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

                    ChatPreferenceManager.preloadOnJoin(
                            player
                    );

                    ModerationManager.handleJoin(
                            player
                    );

                    DailyLoginManager.handleJoin(
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

                    com.champutils.profile.ProfilePlaytimeManager.flushPlayerBlockingBestEffort(handler.player);
                    com.champutils.profile.ProfilePlaytimeManager.clearSession(handler.player);
                    PlayerProfileManager.saveActiveLocation(handler.player);
                    VanillaProfileStateManager.save(handler.player);
                    CobblemonProfileStorageBridge.forceSaveActiveProfileStores(handler.player);
                    ChatPreferenceManager.saveAsync(handler.player.getUUID(), ChatPreferenceManager.get(handler.player.getUUID()));

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

                    DailyLoginManager.handleDisconnect(
                            handler.player
                    );

                    PlayerProfileManager.unload(
                            handler.player.getUUID()
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
        com.champutils.rank.SeasonRewardManager.registerCommand();
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
        TradeSimCommand.register();
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
        QueueCommand.register();
        ItemBindCommand.register();
        OpenCratesCommand.register();
        ExplorationWorldCommand.register();
        TpaCommand.register();
        BackCommand.register();
        GuildCommand.register();
        WorldBossCommand.register();
        com.champutils.guild.BossDamageProtectionListener.register();
        TerritoryCommand.register();
        ChatCommand.register();
        PrivateMessageCommand.register();
        TitleCommand.register();
        WorldFirstCommand.register();
        CashShopCommand.register();
        BoosterCommand.register();
        PartyCommand.register();
        AutoModCommand.register();
        DailyLoginCommand.register();
        ChampWorldBorderCommand.register();
        GlobalGameruleCommand.register();
        ForceSaveRestartCommand.register();
        ProfileCommand.register();
        IslanderMineCommand.register();
        GraveyardCommand.register();
        ClearWildPokemonCommand.register();
        com.champutils.antilag.CatchAttemptProtectionListener.register();
        TMCommand.register();
        LandClaimCommand.register();

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
        ForbiddenNaturalPokemonSpawnGuard.register();
        TradeEvolutionTrueDexListener.register();
        ChestShopInteractionListener.register();
        TerritoryProtectionListener.register();
        LandClaimProtectionListener.register();
        DeathBackListener.register();
        com.champutils.badge.BadgeUnlockManager.init();
        com.champutils.protection.SpawnRealmProtectionListener.register();
        TerritoryNpcInteractionListener.register();
        VanillaPortalBlocker.register();
        XrayDetectionManager.register();
        CashShopBoostItemManager.register();
        BoosterCreditManager.register();
        IslanderSpawningManager.register();
        IslanderMineProtectionListener.register();

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

                    timedTick("ShopPokemonCrateOpeningGui", () -> ShopPokemonCrateOpeningGui.tick(server));
                    timedTick("OpenCratesMenu", () -> OpenCratesMenu.tick(server));
                    timedTick("NotificationManager", () -> NotificationManager.tick(server));
                    timedTick("PokemonHuntManager", () -> PokemonHuntManager.tick(server));
                    timedTick("QuestManager", () -> QuestManager.tick(server));
                    timedTick("RandomTeleportCommand", () -> RandomTeleportCommand.tick(server));
                    timedTick("PortalManager", () -> PortalManager.tick(server));
                    timedTick("RoamingTrainerManager", () -> RoamingTrainerManager.tick(server));
                    timedTick("SpecialWildSpawnManager", () -> SpecialWildSpawnManager.tick(server));
                    timedTick("NaturalSpecialSpawnBlocker", () -> NaturalSpecialSpawnBlocker.tick(server));
                    timedTick("MegaBossManager", () -> MegaBossManager.tick(server));
                    timedTick("ChestShopDisplayManager", () -> ChestShopDisplayManager.tick(server));
                    timedTick("BattleStuckCleanupManager", () -> BattleStuckCleanupManager.tick(server));
                    timedTick("TerritoryBorderManager", () -> TerritoryBorderManager.tick(server));
                    timedTick("TerritoryPhysicalBorderManager", () -> TerritoryPhysicalBorderManager.tick(server));
                    timedTick("TerritoryBorderDisplayManager", () -> TerritoryBorderDisplayManager.tick(server));
                    timedTick("TerritoryWorldGenerationManager", () -> TerritoryWorldGenerationManager.tick(server));
                    timedTick("TerritorySkyblockIslandManager", () -> TerritorySkyblockIslandManager.tick(server));
                    timedTick("TerritoryRegionWipeManager", () -> TerritoryRegionWipeManager.tick(server));
                    timedTick("TerritoryNpcManager", () -> TerritoryNpcManager.tick(server));
                    timedTick("GuildBossManager", () -> GuildBossManager.tick(server));
                    timedTick("ExplorationWorldManager", () -> ExplorationWorldManager.tick(server));
                    timedTick("SurvivalWorldManager", () -> SurvivalWorldManager.tick(server));
                    timedTick("VanillaPortalBlocker", () -> VanillaPortalBlocker.tick(server));
                    timedTick("PartyManager", () -> PartyManager.tick(server));
                    timedTick("AntiLagManager", () -> AntiLagManager.tick(server));
                    timedTick("OversizedChunkEntityGuard", () -> com.champutils.antilag.OversizedChunkEntityGuard.tick(server));
                    timedTick("ModerationManager", () -> ModerationManager.tick(server));
                    timedTick("DailyLoginManager", () -> DailyLoginManager.tick(server));
                    timedTick("ChampWorldBorderManager", () -> ChampWorldBorderManager.tick(server));
                    timedTick("IslanderProfileManager", () -> IslanderProfileManager.tick(server));
                    timedTick("IslanderMineManager", () -> IslanderMineManager.tick(server));
                    timedTick("LandClaimProtectionListener", () -> LandClaimProtectionListener.tick(server));

                    /*
                     Leaderboard refresh
                     */
                    if (
                            server.getTickCount() > 0 &&
                                    server.getTickCount() % 1200 == 0
                    ) {
                        LeaderboardManager.refresh(
                                server
                        );
                    }

                    /*
                     Light autosave: small snapshots only.
                     */
                    if (
                            server.getTickCount() > 0 &&
                                    server.getTickCount() % 1200 == 0
                    ) {
                        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                            PlayerProfileManager.saveActiveLocationAsync(onlinePlayer);
                        }
                        ProfessionManager.saveAll();
                        QuestManager.saveAll();
                        PlaytimeManager.addOnlineMinute(server);
                        com.champutils.profile.ProfilePlaytimeManager.flushAsync();
                        ServerStatusDatabaseRepository.sync(server);
                    }

                    /*
                     Heavy profile autosave: full NBT/Cobblemon snapshots are more expensive, so throttle them.
                     */
                    if (
                            server.getTickCount() > 0 &&
                                    server.getTickCount() % 6000 == 0
                    ) {
                        for (ServerPlayer onlinePlayer : server.getPlayerList().getPlayers()) {
                            VanillaProfileStateManager.saveAsync(onlinePlayer);
                            CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(onlinePlayer);
                        }
                        TrueCaughtDexManager.save();
                        CatchStreakManager.save();
                        PokemonOriginManager.save();
                    }

                    /*
                     Territory DB refresh: expensive, so do it every 10 minutes instead of every minute.
                     */
                    if (server.getTickCount() > 0 && server.getTickCount() % 12000 == 0) {
                        TerritoryRepository.refreshAll();
                        LandClaimRepository.refreshAll();
                    }

                    /*
                     Scoreboard sidebar + ranked action bar
                     */
                    if (
                            server.getTickCount() % 20 == 0
                    ) {
                        timedTick("PlayerSidebarManager", () -> com.champutils.scoreboard.PlayerSidebarManager.tick(server));

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
                    timedTick("ActiveEffectManager", () -> ActiveEffectManager.tick(server));

                    /*
                     Matchmaking systems
                     */
                    timedTick("MatchmakingManager", MatchmakingManager::tick);
                    timedTick("QueueBossBarManager", QueueBossBarManager::tick);

                    timedTick("TeamPreviewManager", () -> TeamPreviewManager.tick(
                            server.getPlayerList()
                                    .getPlayers()
                    ));

                    /*
                     World event systems
                     */
                    timedTick("WorldEventManager", () -> WorldEventManager.tick(server));
                    timedTick("ChampTrainerProtectionManager", () -> ChampTrainerProtectionManager.tick(server));

                    /*
                     Season systems
                     */
                    timedTick("SeasonManager", () -> SeasonManager.tick(server));
                }
        );

        System.out.println(
                "[ChampUtils] Loaded successfully."
        );
    }
}