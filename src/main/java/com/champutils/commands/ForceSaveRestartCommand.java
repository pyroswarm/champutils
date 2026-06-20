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

import java.util.concurrent.TimeUnit;

import static net.minecraft.commands.Commands.literal;

/**
 * Admin-only persistence command for safe restarts.
 * /champsave force-saves runtime files, active profiles, playtime, locations, and queues a DB barrier.
 * /champrestart does the same save, then stops the server so the external panel/script can restart it.
 */
public final class ForceSaveRestartCommand {
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
        source.sendSuccess(() -> Component.literal("Force-saving ChampUtils data...").withStyle(ChatFormatting.YELLOW), true);
        forceSave(server);
        source.sendSuccess(() -> Component.literal("ChampUtils force-save complete" + (stopAfterSave ? "; stopping server now." : ".")).withStyle(ChatFormatting.GREEN), true);
        if (stopAfterSave) {
            server.execute(() -> server.halt(false));
        }
        return 1;
    }

    public static void forceSave(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try { ProfilePlaytimeManager.recordCurrentSession(player); } catch (Exception ignored) {}
            try { PlayerProfileManager.saveActiveLocation(player); } catch (Exception ignored) {}
            try { VanillaProfileStateManager.saveAsync(player); } catch (Exception ignored) {}
            try { CobblemonProfileStorageBridge.forceSaveActiveProfileStoresAsync(player); } catch (Exception ignored) {}
        }

        try { ProfilePlaytimeManager.flushBlockingBestEffort(); } catch (Exception ignored) {}
        try { ProfessionManager.saveAll(); } catch (Exception ignored) {}
        try { EconomyManager.save(); } catch (Exception ignored) {}
        try { ProfessionBlockTracker.save(); } catch (Exception ignored) {}
        try { WorldEventBindingRegistry.save(); } catch (Exception ignored) {}
        try { AuctionHouseNpcBindingRegistry.save(); } catch (Exception ignored) {}
        try { MenuNpcBindingRegistry.save(); } catch (Exception ignored) {}
        try { BossConfig.save(); } catch (Exception ignored) {}
        try { ItemBindRegistry.save(); } catch (Exception ignored) {}
        try { ExplorationLootState.save(); } catch (Exception ignored) {}
        try { ExplorationWorldManager.save(); } catch (Exception ignored) {}
        try { SurvivalWorldManager.save(); } catch (Exception ignored) {}
        try { HomeCommand.save(); } catch (Exception ignored) {}
        try { TeleportConfig.save(); } catch (Exception ignored) {}
        try { PortalConfig.save(); } catch (Exception ignored) {}
        try { PokemonHuntManager.save(); } catch (Exception ignored) {}
        try { QuestManager.saveAll(); } catch (Exception ignored) {}
        try { DexRewardClaimData.save(); } catch (Exception ignored) {}
        try { TrueCaughtDexManager.save(); } catch (Exception ignored) {}
        try { CatchStreakManager.save(); } catch (Exception ignored) {}
        try { PokemonOriginManager.save(); } catch (Exception ignored) {}
        try { DailyLoginManager.save(); } catch (Exception ignored) {}
        try { TitleManager.save(); } catch (Exception ignored) {}
        try { WorldFirstManager.save(); } catch (Exception ignored) {}
        try { ShopPokemonCrateOpeningGui.handleServerStopping(server); } catch (Exception ignored) {}
        try { RoamingTrainerManager.despawnAll(server); } catch (Exception ignored) {}
        try { ServerStatusDatabaseRepository.sync(server); } catch (Exception ignored) {}

        if (DatabaseManager.isEnabled()) {
            try {
                DatabaseManager.runAsync("force-save barrier", connection -> {}).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.err.println("[ChampUtils] Database force-save barrier timed out or failed: " + e.getMessage());
            }
        }
    }
}
