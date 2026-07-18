package com.champutils.commands;

import com.champutils.adventureguide.AdventureGuideManager;
import com.champutils.adventurer.AdventurerGuildManager;
import com.champutils.database.DatabaseManager;
import com.champutils.database.ServerStatusDatabaseRepository;
import com.champutils.dex.CatchStreakManager;
import com.champutils.dex.PokemonOriginManager;
import com.champutils.dex.TrueCaughtDexManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profile.CobblemonProfileStorageBridge;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.profile.ProfilePlaytimeManager;
import com.champutils.profile.VanillaProfileStateManager;
import com.champutils.quest.QuestManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight hourly SQL persistence barrier.
 *
 * The tick method only performs a cheap time check. At the hourly boundary it snapshots live
 * player state on the server thread using the same non-blocking save entry points already used by
 * normal autosaves. SQL queue draining happens on the database executor, never on the server thread.
 */
public final class AutoChampSaveManager {
    private static final long INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final AtomicBoolean SAVE_IN_FLIGHT = new AtomicBoolean(false);
    private static long nextSaveAtMillis = System.currentTimeMillis() + INTERVAL_MILLIS;
    private static int tickGate;

    private AutoChampSaveManager() {}

    public static void tick(MinecraftServer server) {
        if (server == null || !DatabaseManager.isEnabled()) return;
        if (++tickGate < 20) return;
        tickGate = 0;

        long now = System.currentTimeMillis();
        if (now < nextSaveAtMillis) return;
        nextSaveAtMillis = now + INTERVAL_MILLIS;
        if (!SAVE_IN_FLIGHT.compareAndSet(false, true)) return;

        long startedAt = now;
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try { PlayerProfileManager.saveActiveLocationAsync(player); } catch (Throwable ignored) {}
                try { VanillaProfileStateManager.saveAsync(player); } catch (Throwable ignored) {}
                try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player); } catch (Throwable ignored) {}
            }
            try { ProfessionManager.saveAllAsync(); } catch (Throwable ignored) {}
            try { QuestManager.saveAll(); } catch (Throwable ignored) {}
            try { AdventurerGuildManager.saveAll(); } catch (Throwable ignored) {}
            try { AdventureGuideManager.saveAll(); } catch (Throwable ignored) {}
            try { ProfilePlaytimeManager.flushAsync(); } catch (Throwable ignored) {}
            try { TrueCaughtDexManager.save(); } catch (Throwable ignored) {}
            try { CatchStreakManager.save(); } catch (Throwable ignored) {}
            try { PokemonOriginManager.save(); } catch (Throwable ignored) {}
            try { ServerStatusDatabaseRepository.sync(server); } catch (Throwable ignored) {}
        } catch (Throwable error) {
            SAVE_IN_FLIGHT.set(false);
            System.err.println("[ChampUtils] Failed to queue hourly SQL save: " + error.getMessage());
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            boolean flushed = DatabaseManager.flushSubmittedTasks(5, TimeUnit.MINUTES);
            if (!flushed) {
                throw new IllegalStateException("Timed out waiting for queued SQL writes.");
            }
        }).whenComplete((ignored, error) -> {
            SAVE_IN_FLIGHT.set(false);
            long elapsed = System.currentTimeMillis() - startedAt;
            if (error == null) {
                System.out.println("[ChampUtils] Hourly SQL save completed in " + elapsed + "ms without blocking the server thread.");
            } else {
                System.err.println("[ChampUtils] Hourly SQL save failed after " + elapsed + "ms: " + error.getMessage());
            }
        });
    }
}
