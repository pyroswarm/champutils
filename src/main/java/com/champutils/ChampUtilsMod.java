package com.champutils;

/*
 =========================
 Internal package imports
 =========================
*/
import com.champutils.battle.*;
import com.champutils.breeding.*;
import com.champutils.commands.*;
import com.champutils.config.*;
import com.champutils.database.DatabaseManager;
import com.champutils.database.DatabaseBootstrapSync;
import com.champutils.database.ServerStatusDatabaseRepository;
import com.champutils.database.RankedFormatDatabaseRepository;
import com.champutils.database.NetworkReadySchemaManager;
import com.champutils.database.DatabaseMaintenanceManager;
import com.champutils.gym.*;
import com.champutils.matchmaking.*;
import com.champutils.menu.*;
import com.champutils.profession.*;
import com.champutils.profession.actives.ActiveAbilityRegistry;
import com.champutils.profession.actives.ActiveEffectManager;
import com.champutils.profession.passives.PassiveRegistry;
import com.champutils.profile.*;
import com.champutils.rank.*;
import com.champutils.trainer.*;
import com.champutils.economy.EconomyManager;
import com.champutils.economy.SellPriceConfig;
import com.champutils.notifications.NotificationManager;
import com.champutils.notifications.NotificationRepository;
import com.champutils.auction.*;
import com.champutils.shop.*;
import com.champutils.genesis.*;
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
import com.champutils.contracts.PlayerContractRepository;
import com.champutils.crafting.ChampCraftingConfig;
import com.champutils.commerce.*;
import com.champutils.debug.ChampDebugManager;
import com.champutils.worldborder.*;
import com.champutils.gamerule.*;
import com.champutils.tm.*;
import com.champutils.claims.*;
import com.champutils.expeditions.*;
import com.champutils.adventurer.*;
import com.champutils.adventureguide.*;
import com.champutils.tutorial.*;
import com.champutils.music.*;
import com.champutils.afk.*;
import com.champutils.riding.InfiniteRideStaminaListener;
import java.util.HashSet;
import java.util.Set;

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
                double elapsedMs = elapsed / 1_000_000.0D;
                String timingMessage = "[ChampUtils][TickTiming] " + name + " took " + elapsedMs + " ms";
                ChampDebugManager.log(ChampDebugManager.Category.PERFORMANCE, timingMessage);
                // A multi-second manager stall can disconnect the whole server. Always surface these
                // severe stalls even when optional performance debugging is disabled.
                if (elapsedMs >= 1000.0D) {
                    System.err.println(timingMessage);
                }
            }
        }
    }


    private static void initializeProfileLobbyOnly() {

        DatabaseManager.init();
        NetworkReadySchemaManager.ensureAsync();
        DatabaseMaintenanceManager.ensureAsync();
        PlayerProfileManager.ensureSchemaAsync();
        PreferredSurvivalServerManager.ensureSchemaAsync();
        ProfileAtomicSnapshotManager.ensureSchemaAsync();
        com.champutils.database.CreditsDatabaseRepository.ensureSchemaAsync();
        MenuNpcBindingRegistry.load();

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            try {
                MenuNpcBindingRegistry.save();
            } catch (Exception ignored) {
            }
            DatabaseManager.flushSubmittedTasks(15, java.util.concurrent.TimeUnit.SECONDS);
            DatabaseManager.shutdown();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            server.execute(() -> {
                if (player.hasDisconnected()) return;
                ProfileLobbyManager.sendToLobby(player);
                ProfileLobbySetupManager.applyPlayerRules(player);
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            PlayerProfileManager.clearActiveForMenu(player);
        });

        ProfileCommand.register();
        StaffServerCommand.register();
        ChampDebugCommand.register();
        MenuNpcCommand.register();
        BlankNpcCommand.register();
        NpcAdminCommand.register();
        MenuNpcInteractionListener.register();
        ProfileLobbySetupManager.register();
        ProfileLoadingStateManager.register();
        SurvivalQueueManager.register();
        LobbyCommandTreePruner.register();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Intentionally empty. PROFILE_LOBBY must not run gameplay packet managers.
        });

    }

    @Override
    public void onInitialize() {

        BreedingResourcePackBridge.registerAssets();

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
        ProxyTransferBridge.register();
        InfiniteRideStaminaListener.register();

        if (ProfileNetworkTransferFlow.isProfileLobbyServer()) {
            initializeProfileLobbyOnly();
            return;
        }

        GuildConfig.load();
        MusicConfig.load();
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
        DatabaseMaintenanceManager.ensureAsync();
        TitleManager.load();
        WorldFirstManager.load();
        PlayerProfileManager.ensureSchemaAsync();
        BreedingManager.initialize();
        PokemonBreedabilityManager.register();
        PreferredSurvivalServerManager.ensureSchemaAsync();
        TutorialManager.ensureSchemaAsync();
        VanillaProfileStateManager.ensureSchemaAsync();
        CobblemonProfileStorageBridge.ensureSchemaAsync();
        ProfileAtomicSnapshotManager.ensureSchemaAsync();
        com.champutils.database.CreditsDatabaseRepository.ensureSchemaAsync();
        AuctionHouseRepository.ensureSchemaAsync();
        WonderTradeRepository.ensureSchemaAsync();
        NotificationRepository.ensureSchemaAsync();
        PlayerContractRepository.ensureSchemaAsync();
        MonotypeStarterManager.ensureSchemaAsync();
        NuzlockeManager.ensureSchemaAsync();
        ChatPreferenceManager.ensureSchemaAsync();
        ProfileLobbyLockManager.register();
        ProfileLoadingStateManager.register();
        SurvivalQueueManager.register();
        PokemonExperienceBuffListener.register();
        MonotypeStarterManager.register();
        NuzlockeManager.register();
        IronmanItemOwnership.register();
        IronmanBlockOwnership.register();
        IronmanTradeBlocker.register();
        EconomyManager.load();
        com.champutils.scoreboard.ScoreboardPreferenceManager.load();
        SellPriceConfig.load();
        NpcShopConfig.load();
        IslanderShopConfig.load();
        MegaShopConfig.load();
        ChestShopRegistry.load();
        FirstJoinKitManager.load();
        PokemonHuntConfig.load();
        PokemonHuntManager.load();
        QuestManager.load();
        AdventurerGuildManager.load();
        AdventureGuideManager.load();
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
        AntiAfkManager.load();
        ModerationConfig.load();
        ModerationActionRepository.ensureSchemaAsync();
        ItemBindRegistry.load();
        ExplorationWorldConfig.load();
        ExplorationLootConfig.load();
        ExplorationLootState.load();
        ExplorationWorldManager.load();
        SurvivalWorldConfig.load();
        SurvivalWorldManager.load();
        SurvivalWhitelistConfig.load();
        HomeCommand.load();
        BoosterCreditManager.load();
        com.champutils.account.AccountUpgradeConfig.load();
        com.champutils.account.AccountUpgradeManager.initialize();
        com.champutils.chat.NicknameManager.initialize();
        AccountCommerceConfig.load();
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
        GymRewardConfig.load();
        GymRewardClaimData.load();
        ExpeditionManager.load();
        /*
         =========================
         PROFESSION CONFIGS
         =========================
         */
        ProfessionConfig.load();
        ProfessionChunkConfig.load();
        ProfessionBackpackConfig.load();
        ChampCraftingConfig.load();

        /*
         Custom tools
         */
        ProfessionToolConfig.load();
        ProfessionFragmentConfig.load();
        RankedTokenManager.register();
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
        VanillaToolRestrictionManager.register();
        VanillaArmorRestrictionManager.register();
        ProfessionToolFastMiningListener.register();
        ProfessionToolAnnouncementManager.register();
        ProfessionToolTooltipUpdater.register();
        ExplorationProtectionListener.register();
        ItemRollCommand.register();
        ProfessionSalvageCommand.register();
        RunningShoeManager.registerItems();
        RunningShoeManager.registerEffects();
        ProfessionGearConfig.load();
        ProfessionGearManager.registerItems();
        ProfessionGearManager.registerEffects();
        ProfessionTrinketConfig.load();
        ProfessionTrinketManager.registerItems();
        ProfessionTrinketManager.registerEffects();
        BattlePokemonDropMultiplierListener.register();
        BattleBondEvolutionListener.register();

        /*
         Profession loot config
         */
        ProfessionLootConfig.load(); // legacy file kept readable; profession item drops are disabled by ProfessionLootManager.
        ProfessionRewardPassiveConfig.load();

        /*
         Battle profession loot config
         */
        BattleProfessionLootConfig.load(); // legacy file kept readable; battle item/money loot disabled by managers.
        BattleBondEvolutionConfig.load();

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
        AuctionHouseNpcBindingRegistry.load();
        TutorialNpcBindingRegistry.load();
        MenuNpcBindingRegistry.load();
        MegaShopConfig.load();

        /*
         =========================
         SEASON STATE
         =========================
         */
        SeasonManager.loadState();
        SeasonArchiveManager.initialize();

        /*
         =========================
         SERVER START
         =========================
         */
        ServerLifecycleEvents.SERVER_STARTED.register(
                server -> {
                    ForceSaveRestartCommand.markServerRunning();
                    ServerLifecycleBridge.setServer(server);
                    CobblemonProfileStorageBridge.registerSqlFactory(server);

                    LeaderboardManager.refreshNow(server);
                    ServerStatusDatabaseRepository.sync(server);
                    ChampWorldBorderManager.applyAll(server);
                    // Reload from disk on every completed server launch, then apply to every
                    // currently loaded world. Newly loaded worlds are also covered by LOAD below.
                    GlobalGameruleConfig.load();
                    GlobalGameruleManager.applyAll(server);
                    IslanderSpawningManager.handleServerStarted(server);
                    RankedFormatDatabaseRepository.syncCurrentFormats();
                    NetworkReadySchemaManager.ensureAsync();
                    PartyManager.initialize();
                    com.champutils.buff.ServerBuffManager.refreshAsync();
                    DatabaseMaintenanceManager.ensureAsync();
                    AccountCommerceRepository.ensureSchemaAsync();
                    BoosterCreditManager.ensureSchemaAsync();
                    TrailCosmeticManager.ensureSchemaAsync();
                    AccountVoteManager.start(server);
                    PlayerProfileManager.ensureSchemaAsync();
                    TutorialManager.ensureSchemaAsync();
                    BattleProfileRecoveryManager.recoverInterruptedGuardsAsync();
                    VanillaProfileStateManager.ensureSchemaAsync();
                    CobblemonProfileStorageBridge.ensureSchemaAsync();
                    ProfileAtomicSnapshotManager.ensureSchemaAsync();
                    com.champutils.database.CreditsDatabaseRepository.ensureSchemaAsync();
                    AuctionHouseRepository.ensureSchemaAsync();
                    WonderTradeRepository.ensureSchemaAsync();
                    NotificationRepository.ensureSchemaAsync();
                    PlayerContractRepository.ensureSchemaAsync();
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
                        WonderTradeRepository.ensureSchemaAsync();
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

                    // Never launch a fresh asynchronous /champsave from SERVER_STOPPING. That
                    // pipeline posts tasks back to the server thread and races DatabaseManager.shutdown().
                    ForceSaveRestartCommand.beginServerStopping();
                    FirstJoinKitManager.save();
                    AdventurerGuildManager.saveAll();
                    AdventureGuideManager.saveAll();
                    ChestShopRegistry.save();
                    ServerStatusDatabaseRepository.markOffline(server);
                    DatabaseManager.flushSubmittedTasks(15, java.util.concurrent.TimeUnit.SECONDS);
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

                    com.champutils.chat.NicknameManager.load(player);

                    String playerName =
                            player.getName()
                                    .getString();

                    SeasonArchiveManager.ensurePlayerFile(
                            playerName
                    );

                    SurvivalWhitelistManager.handleJoin(player);
                    MusicManager.handleJoin(player);
                    if (player.hasDisconnected()) return;

                    PlayerProfileManager.handleJoin(player);
                    if (com.champutils.profile.ProfileNetworkTransferFlow.isProfileLobbyServer()) {
                        return;
                    }
                    if (PlayerProfileManager.isInMainMenu(player)) {
                        return;
                    }
                    if (com.champutils.profile.ProfileNetworkTransferFlow.isSurvivalServer() &&
                            (com.champutils.profile.ProfileLoadingStateManager.isLoading(player) ||
                                    !PlayerProfileManager.hasActiveProfile(player))) {
                        return;
                    }
                    PlayerDataManager.ensurePlayer(
                            player.getUUID(),
                            playerName
                    );

                    EconomyManager.ensurePlayer(
                            player
                    );

                    ExpeditionManager.notifyIfReady(player);

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

                    if (!player.getTags().contains("champutils_seen_before")) {
                        player.addTag("champutils_seen_before");
                        server.getPlayerList().broadcastSystemMessage(
                                net.minecraft.network.chat.Component.literal("§aWelcome §f" + playerName + " §ato Cobble Champs for the first time!"),
                                false
                        );
                    }

                    DefaultSpawnManager.handleJoin(
                            player
                    );

                    QuestManager.handleJoin(
                            player
                    );

                    AdventurerGuildManager.handleJoin(
                            player
                    );

                    AdventureGuideManager.handleJoin(
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

                    TutorialManager.handleJoin(player);
                    AntiAfkManager.handleJoin(player);
                    BreedingManager.handleJoin(player);
                    if ("survival2".equalsIgnoreCase(com.champutils.network.NetworkServerConfig.serverId())) {
                        com.champutils.network.NetworkEventManager.publishServerBroadcast(
                                "main_survival1",
                                "§7[Network] §f" + playerName + " §7joined §eEclipse§7."
                        );
                    }

                }
        );

        /*
         =========================
         PLAYER DISCONNECT
         =========================
         */
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {

                    AntiAfkManager.handleDisconnect(handler.player);
                    BreedingManager.handleDisconnect(handler.player);
                    MusicManager.handleQuit(handler.player);
                    AdventurerGuildManager.handleDisconnect(handler.player);
                    AdventureGuideManager.unloadPlayer(handler.player);
                    PlayerProfileManager.saveAndUnloadForDisconnect(handler.player);

                    MatchmakingManager.leaveQueue(
                            handler.player
                    );

                    TutorialManager.unload(handler.player);
                }
        );

        /*
         =========================
         CHAT LISTENER
         =========================
         */
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                (message, player, params) -> {

                    if (com.champutils.secret.SecretManager.consumeChat(player, message.signedContent())) return false;

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

                    if (com.champutils.contracts.PlayerContractService.consumeChatInput(
                            player,
                            message.signedContent()
                    )) {
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
        CommandBlocker.register();
        AccessCommandWrappers.register();
        ChampUtilsHelpCommand.register();
        ChampAICommand.register();
        ChampDebugCommand.register();
        MenuCommand.register();
        SeasonCommand.register();
        com.champutils.rank.SeasonRewardManager.registerCommand();
        LeaderboardCommand.register();
        GymCommand.register();
        // /evtraining removed intentionally. EV training access can still be reused by other systems.
        EnderChestCommand.register();
        RpAdminCommand.register();
        ProfessionAdminCommand.register();
        ProfessionsCommand.register();
        ProfessionToolsCommand.register();
        BackpackCommand.register();
        ChampCraftingCommand.register();
        ChunksCommand.register();
        ProfessionTradeCommand.register();
        ChampReloadCommand.register();
        DatabaseTestCommand.register();
        NetworkDatabaseCommand.register();
        LinkAccountCommand.register();
        EconomyCommand.register();
        MonotypeStarterCommand.register();
        AuctionHouseCommand.register();
        NotificationsCommand.register();
        ScoreboardToggleCommand.register();
        ProfessionPopupsCommand.register();
        AutoStepCommand.register();
        MenuNpcCommand.register();
        NpcShopCommand.register();
        IslanderShopCommand.register();
        SpawnTrainerCommand.register();
        BlankNpcCommand.register();
        NpcAdminCommand.register();
        ArenaCommand.register();
        PokemonHuntCommand.register();
        QuestCommand.register();
        AdventurerGuildCommand.register();
        ChestShopCommand.register();
        // /serversell removed: economy now uses digital chunks sold through the Profession Foreman.
        DexRewardCommand.register();
        ShinyOddsCommand.register();
        BreedingCommand.register();
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
        com.champutils.chat.NicknameCommand.register();
        PrivateMessageCommand.register();
        RankedShopCommand.register();
        DiscordCommand.register();
        TitleCommand.register();
        TrailCommand.register();
        WorldFirstCommand.register();
        CashShopCommand.register();
        BoosterCommand.register();
        AccountCommerceCommand.register();
        com.champutils.account.AccountUpgradeCommand.register();
        PartyCommand.register();
        AutoModCommand.register();
        DailyLoginCommand.register();
        ChampWorldBorderCommand.register();
        GlobalGameruleCommand.register();
        ForceSaveRestartCommand.register();
        ProfileCommand.register();
        StaffServerCommand.register();
        IslanderMineCommand.register();
        IslanderDebugCommand.register();
        GraveyardCommand.register();
        ClearWildPokemonCommand.register();
        MegaBossCommand.register();
        com.champutils.antilag.CatchAttemptProtectionListener.register();
        com.champutils.buff.CatchChanceGuaranteeListener.register();
        TMCommand.register();
        LandClaimCommand.register();
        GymRewardCommand.register();
        ExpeditionCommand.register();
        WildSpawnCapCommand.register();
        TutorialCommand.register();
        MusicCommand.register();
        MagnetCommand.register();
        com.champutils.survival.HostileToggleManager.register();
        SurvivalWhitelistCommand.register();
        AntiAfkManager.register();

        /*
         New custom item test command
         */
        GiveChampItemCommand.register();
        ShowItemCommand.register();
        ItemLockCommand.register();
        ItemDebugCommand.register();
        XpLockCommand.register();
        NeuterCommand.register();
        BottleCapConfirmationCommand.register();
        // /levelcap removed: gym/progression caps are enforced by battle systems only.
        // LevelCapItemUseGuard disabled with /levelcap removal.

        /*
         =========================
         BATTLE SYSTEMS
         =========================
         */
        CobblemonBattleHandler.register();
        BattleAIDifficultyManager.register();
        CobblemonBattleStartHandler.register();
        BattleItemUseListener.register();
        BattleDamageProtectionListener.register();
        RoamingTrainerDamageProtectionListener.register();
        MusicBattleListener.register();

        GymBattleHandler.register();
        GymBattleStartHandler.register();
        MegaBossBattleListener.register();
        MegaBossCaptureBlocker.register();
        MegaBossDamageProtectionListener.register();
        LandClaimSelectionItemListener.register();
        AuctionHouseBindInteractionListener.register();
        TutorialNpcInteractionListener.register();
        MenuNpcInteractionListener.register();
        ItemBindInteractionListener.register();
        ChampTrainerInteractionListener.register();
        PokemonHuntCatchListener.register();
        TrueCaughtDexListener.register();
        SpecialCatchAnnouncementListener.register();
        com.champutils.cosmetic.QuirkyCatchTitleListener.register();
        CatchStreakSpawnListener.register();
        ForbiddenNaturalPokemonSpawnGuard.register();
        WildGymLevelCapManager.register();
        SpecialSpawnDamageProtectionListener.register();
        TradeEvolutionTrueDexListener.register();
        ChestShopInteractionListener.register();
        // Steward interactions must register before generic territory entity protection so the
        // steward menu is linked deterministically for owners and guild members.
        TerritoryNpcInteractionListener.register();
        TerritoryProtectionListener.register();
        LandClaimProtectionListener.register();
        LandClaimPokemonRulesListener.register();
        DeathBackListener.register();
        com.champutils.badge.BadgeUnlockManager.init();
        com.champutils.secret.SecretManager.register();
        com.champutils.protection.SpawnRealmProtectionListener.register();
        com.champutils.protection.CampfirePotSafetyListener.register();
        com.champutils.protection.SpawnEditCommand.register();
        VanillaPortalBlocker.register();
        XrayDetectionManager.register();
        CashShopBoostItemManager.register();
        BoosterCreditManager.register();
        TrailCosmeticManager.register();
        IslanderSpawningManager.register();
        IslanderMineProtectionListener.register();

        /*
         =========================
         PROFESSION SYSTEMS
         =========================
         */
        MiningProfessionListener.register();
        ForestryProfessionListener.register();
        AcceleratedLeafDecayManager.register();
        FarmingProfessionListener.register();
        ProfessionPlacementListener.register();

        /*
         =========================
         SERVER TICK LOOP
         =========================
         */
        ServerTickEvents.END_SERVER_TICK.register(
                server -> {

                    if (ProfileNetworkTransferFlow.isProfileLobbyServer()) {
                        // PROFILE_LOBBY must be as packet-quiet as possible while players are connecting through Velocity.
                        // Do not run gameplay/sidebar/actionbar/world systems here; players should only see the lobby
                        // world and manually open the selector through /profiles or the bound NPC.
                        return;
                    }

                    timedTick("ShopPokemonCrateOpeningGui", () -> ShopPokemonCrateOpeningGui.tick(server));
                    timedTick("OpenCratesMenu", () -> OpenCratesMenu.tick(server));
                    timedTick("NotificationManager", () -> NotificationManager.tick(server));
                    timedTick("MusicManager", () -> MusicManager.tick(server));
                    timedTick("NetworkEventManager", () -> NetworkEventManager.tick(server));
                    timedTick("NetworkTabListManager", () -> NetworkTabListManager.tick(server));
                    timedTick("PokemonHuntManager", () -> PokemonHuntManager.tick(server));
                    timedTick("QuestManager", () -> QuestManager.tick(server));
                    timedTick("AdventurerGuildManager", () -> AdventurerGuildManager.tick(server));
                    timedTick("AdventureGuideManager", () -> AdventureGuideManager.tick(server));
                    timedTick("TutorialManager", () -> TutorialManager.tick(server));
                    timedTick("RandomTeleportCommand", () -> RandomTeleportCommand.tick(server));
                    timedTick("PortalManager", () -> PortalManager.tick(server));
                    timedTick("RoamingTrainerManager", () -> RoamingTrainerManager.tick(server));
                    timedTick("SpecialWildSpawnManager", () -> SpecialWildSpawnManager.tick(server));
                    timedTick("ExpeditionManager", () -> ExpeditionManager.tick(server));
                    timedTick("ServerBuffManager", () -> com.champutils.buff.ServerBuffManager.tick(server));
                    timedTick("NaturalSpecialSpawnBlocker", () -> NaturalSpecialSpawnBlocker.tick(server));
                    if (NetworkServerConfig.isAuthoritativeGameplayServer()) {
                        timedTick("MegaBossManager", () -> MegaBossManager.tick(server));
                    }
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
                    timedTick("VanillaOverworldGuard", () -> VanillaOverworldGuard.tick(server));
                    timedTick("VanillaPortalBlocker", () -> VanillaPortalBlocker.tick(server));
                    timedTick("PartyManager", () -> PartyManager.tick(server));
                    timedTick("AntiLagManager", () -> AntiLagManager.tick(server));
                    timedTick("OversizedChunkEntityGuard", () -> com.champutils.antilag.OversizedChunkEntityGuard.tick(server));
                    timedTick("ModerationManager", () -> ModerationManager.tick(server));
                    timedTick("RedstoneAutoModManager", () -> RedstoneAutoModManager.tick(server));
                    timedTick("AntiAfkManager", () -> AntiAfkManager.tick(server));
                    timedTick("BreedingManager", () -> BreedingManager.tick(server));
                    timedTick("PvPBattleStallManager", () -> PvPBattleStallManager.tick(server));
                    timedTick("DailyLoginManager", () -> DailyLoginManager.tick(server));
                    timedTick("AutoChampSaveManager", () -> com.champutils.commands.AutoChampSaveManager.tick(server));
                    timedTick("ChampWorldBorderManager", () -> ChampWorldBorderManager.tick(server));
                    timedTick("IslanderProfileManager", () -> IslanderProfileManager.tick(server));
                    timedTick("IslanderMineManager", () -> IslanderMineManager.tick(server));
                    timedTick("LandClaimProtectionListener", () -> LandClaimProtectionListener.tick(server));
                    timedTick("DatabaseMaintenanceManager", () -> DatabaseMaintenanceManager.tick(server));

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
                        ProfessionManager.saveAllAsync();
                        QuestManager.saveAll();
                        AdventurerGuildManager.saveAll();
                        AdventureGuideManager.saveAll();
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
                    timedTick("MatchmakingManager", () -> MatchmakingManager.tick(server));
                    timedTick("QueueBossBarManager", QueueBossBarManager::tick);

                    timedTick("TeamPreviewManager", () -> TeamPreviewManager.tick(
                            server.getPlayerList()
                                    .getPlayers()
                    ));
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
