package com.champutils.commands;

import com.champutils.auction.AuctionHouseNpcBindingRegistry;
import com.champutils.exploration.ItemBindRegistry;
import com.champutils.shop.ShopPokemonCrateOpeningGui;
import com.champutils.database.DatabaseManager;
import com.champutils.database.ServerStatusDatabaseRepository;
import com.champutils.dex.CatchStreakManager;
import com.champutils.dex.DexRewardClaimData;
import com.champutils.dex.PokemonOriginManager;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.economy.EconomyManager;
import com.champutils.exploration.ExplorationLootState;
import com.champutils.exploration.ExplorationWorldManager;
import com.champutils.guild.BossConfig;
import com.champutils.hunt.PokemonHuntManager;
import com.champutils.menu.MenuNpcBindingRegistry;
import com.champutils.party.PartyManager;
import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionManager;
import com.champutils.profile.CobblemonProfileStorageBridge;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfilePlaytimeManager;
import com.champutils.profile.VanillaProfileStateManager;
import com.champutils.quest.QuestManager;
import com.champutils.roaming.RoamingTrainerManager;
import com.champutils.survival.SurvivalWorldManager;
import com.champutils.survival.HomeCommand;
import com.champutils.teleport.PortalConfig;
import com.champutils.teleport.TeleportConfig;
import com.champutils.worldevent.WorldEventBindingRegistry;
import com.champutils.cosmetic.TitleManager;
import com.champutils.worldfirst.WorldFirstManager;
import com.champutils.dailylogin.DailyLoginManager;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.minecraft.commands.Commands.literal;

/**
 * Admin-only persistence command for safe restarts.
 *
 * Important performance rule:
 * /champsave must never block the Minecraft server thread on SQL or bulk file IO.
 * The command now snapshots player-critical state on the server thread, queues SQL writes,
 * then drains the slower save work on one background worker with tiny pauses between jobs.
 */
public final class ForceSaveRestartCommand {
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ChampUtils-SlowSave");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean SAVE_RUNNING = new AtomicBoolean(false);
    private static final long SAVE_STEP_PAUSE_MILLIS = 75L;

    private ForceSaveRestartCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("champsave")
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                    .executes(ctx -> save(ctx.getSource(), false)));
            dispatcher.register(literal("champrestart")
                    .requires(source -> com.champutils.permissions.PermissionUtil.has(source, "champutils.admin"))
                    .executes(ctx -> save(ctx.getSource(), true)));
        });
    }

    private static int save(CommandSourceStack source, boolean stopAfterSave) {
        MinecraftServer server = source.getServer();
        if (server == null) return 0;

        if (!SAVE_RUNNING.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("A ChampUtils save is already running. Try again after it finishes.").withStyle(ChatFormatting.RED));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("ChampUtils save started in the background. Gameplay should stay smooth.").withStyle(ChatFormatting.YELLOW), true);
        forceSaveAsync(server, source, stopAfterSave);
        return 1;
    }

    /** Backwards-compatible entry point used by scheduled auto-save. Non-blocking by design. */
    public static void forceSave(MinecraftServer server) {
        if (server == null) return;
        if (!SAVE_RUNNING.compareAndSet(false, true)) {
            System.out.println("[ChampUtils] Skipping scheduled /champsave because a save is already running.");
            return;
        }
        forceSaveAsync(server, null, false);
    }

    private static void forceSaveAsync(MinecraftServer server, CommandSourceStack source, boolean stopAfterSave) {
        long startedAt = System.currentTimeMillis();

        // Keep live ServerPlayer/NBT touches on the server thread. These calls only snapshot/cache/queue writes.
        server.execute(() -> {
            try {
                snapshotOnlinePlayers(server);
            } catch (Throwable t) {
                System.err.println("[ChampUtils] Player snapshot phase failed during /champsave.");
                t.printStackTrace();
            }

            SAVE_EXECUTOR.execute(() -> {
                try {
                    runSlowSavePipeline(server, stopAfterSave);
                    long elapsed = System.currentTimeMillis() - startedAt;
                    System.out.println("[ChampUtils] ChampUtils background save finished in " + elapsed + "ms.");
                    if (source != null && !stopAfterSave) {
                        server.execute(() -> source.sendSuccess(() -> Component.literal("ChampUtils background save complete.").withStyle(ChatFormatting.GREEN), true));
                    }
                    if (stopAfterSave) {
                        server.execute(() -> server.halt(false));
                    }
                } catch (Throwable t) {
                    System.err.println("[ChampUtils] ChampUtils background save failed.");
                    t.printStackTrace();
                    if (source != null) {
                        server.execute(() -> source.sendFailure(Component.literal("ChampUtils save failed. Check console logs before restarting.").withStyle(ChatFormatting.RED)));
                    }
                } finally {
                    SAVE_RUNNING.set(false);
                }
            });
        });
    }

    private static void snapshotOnlinePlayers(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try { ProfilePlaytimeManager.recordCurrentSession(player); } catch (Throwable ignored) {}
            try { PlayerProfileManager.saveActiveLocation(player); } catch (Throwable ignored) {}
            try { VanillaProfileStateManager.saveAsync(player); } catch (Throwable ignored) {}
            try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player); } catch (Throwable ignored) {}
        }
        try { ProfilePlaytimeManager.flushAsync(); } catch (Throwable ignored) {}
    }

    private static void runSlowSavePipeline(MinecraftServer server, boolean stopAfterSave) {
        List<NamedSaveTask> tasks = new ArrayList<>();

        // Local JSON/config/cache saves. Run one-at-a-time off the server thread to avoid one giant tick freeze.
        tasks.add(new NamedSaveTask("professions", ProfessionManager::saveAll));
        tasks.add(new NamedSaveTask("economy", EconomyManager::save));
        tasks.add(new NamedSaveTask("profession block tracker", ProfessionBlockTracker::save));
        tasks.add(new NamedSaveTask("world event bindings", WorldEventBindingRegistry::save));
        tasks.add(new NamedSaveTask("auction NPC bindings", AuctionHouseNpcBindingRegistry::save));
        tasks.add(new NamedSaveTask("menu NPC bindings", MenuNpcBindingRegistry::save));
        tasks.add(new NamedSaveTask("boss config", BossConfig::save));
        tasks.add(new NamedSaveTask("item bindings", ItemBindRegistry::save));
        tasks.add(new NamedSaveTask("exploration loot", ExplorationLootState::save));
        tasks.add(new NamedSaveTask("exploration worlds", ExplorationWorldManager::save));
        tasks.add(new NamedSaveTask("survival worlds", SurvivalWorldManager::save));
        tasks.add(new NamedSaveTask("homes", HomeCommand::save));
        tasks.add(new NamedSaveTask("teleports", TeleportConfig::save));
        tasks.add(new NamedSaveTask("portals", PortalConfig::save));
        tasks.add(new NamedSaveTask("hunts", PokemonHuntManager::save));
        tasks.add(new NamedSaveTask("quests", QuestManager::saveAll));
        tasks.add(new NamedSaveTask("dex rewards", DexRewardClaimData::save));
        tasks.add(new NamedSaveTask("true dex", TrueCaughtDexManager::save));
        tasks.add(new NamedSaveTask("catch streaks", CatchStreakManager::save));
        tasks.add(new NamedSaveTask("pokemon origins", PokemonOriginManager::save));
        tasks.add(new NamedSaveTask("daily login", DailyLoginManager::save));
        tasks.add(new NamedSaveTask("titles", TitleManager::save));
        tasks.add(new NamedSaveTask("world firsts", WorldFirstManager::save));
        tasks.add(new NamedSaveTask("server status", () -> ServerStatusDatabaseRepository.sync(server)));

        for (NamedSaveTask task : tasks) {
            runTask(task);
            pauseBetweenTasks();
        }

        // Only restart cleanup should mutate the world by closing crate GUIs/despawning roaming trainers.
        // Plain /champsave is persistence-only and should not cause entity churn or gameplay changes.
        if (stopAfterSave) {
            runOnServerThreadAndWait(server, () -> {
                try { ShopPokemonCrateOpeningGui.handleServerStopping(server); } catch (Throwable ignored) {}
                try { RoamingTrainerManager.despawnAll(server); } catch (Throwable ignored) {}
            });
        }

        if (DatabaseManager.isEnabled()) {
            try {
                // This is intentionally on ChampUtils-SlowSave, never on the Minecraft server thread.
                DatabaseManager.flushSubmittedTasks(20, TimeUnit.SECONDS);
            } catch (Throwable e) {
                System.err.println("[ChampUtils] Database save barrier timed out or failed: " + e.getMessage());
            }
        }
    }

    private static void runTask(NamedSaveTask task) {
        try {
            task.runnable.run();
        } catch (Throwable t) {
            System.err.println("[ChampUtils] Save task failed: " + task.name);
            t.printStackTrace();
        }
    }

    private static void pauseBetweenTasks() {
        try {
            Thread.sleep(SAVE_STEP_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void runOnServerThreadAndWait(MinecraftServer server, Runnable runnable) {
        if (server == null || runnable == null) return;
        java.util.concurrent.CompletableFuture<Void> done = new java.util.concurrent.CompletableFuture<>();
        server.execute(() -> {
            try {
                runnable.run();
                done.complete(null);
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            done.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Server-thread restart cleanup did not finish cleanly: " + e.getMessage());
        }
    }

    private record NamedSaveTask(String name, Runnable runnable) {}
}
