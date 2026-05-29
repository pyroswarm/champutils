package com.champutils.profile;

import com.champutils.auction.AuctionHouseService;
import com.champutils.shop.ShopPokemonCrateOpeningGui;
import com.champutils.economy.EconomyManager;
import com.champutils.guild.GuildRepository;
import com.champutils.battle.DisconnectForfeitManager;
import com.champutils.notifications.NotificationManager;
import com.champutils.party.PartyManager;
import com.champutils.profession.ProfessionDataManager;
import com.champutils.quest.QuestManager;
import com.champutils.shop.FirstJoinKitManager;
import com.champutils.wondertrade.WonderTradeSeeder;
import com.champutils.chat.ChatPreferenceManager;
import com.champutils.moderation.ModerationManager;
import com.champutils.dailylogin.DailyLoginManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Initializes systems that are safe to start only after a real profile is selected.
 */
public final class ProfileSessionLoader {
    private ProfileSessionLoader() {}

    public record BackgroundSnapshot(UUID playerUuid, UUID profileId, int storedRp) {}

    private static long time(String operation, Runnable runnable) {
        long start = System.currentTimeMillis();
        try {
            runnable.run();
        } finally {
            System.out.println("[PROFILE-TIMING] " + operation + " took " + (System.currentTimeMillis() - start) + "ms");
        }
        return System.currentTimeMillis() - start;
    }

    /**
     * Legacy entry point. Kept for commands/old paths, but internally split so timing logs show the offender.
     */
    public static void load(ServerPlayer player) {
        if (player == null) return;
        loadCritical(player);
        loadBackground(player.getUUID(), PlayerProfileManager.activeProfileId(player), player.getName().getString());
        applyBackground(player, new BackgroundSnapshot(player.getUUID(), PlayerProfileManager.activeProfileId(player), PlayerDataManager.getRp(player.getUUID(), player.getName().getString())));
        loadDelayedNonCritical(player);
    }

    /**
     * Server-thread only. Keep this as small as possible for smooth profile switching.
     */
    public static void loadCritical(ServerPlayer player) {
        if (player == null) return;
        time("ProfileSessionLoader.loadCritical.DisconnectForfeitManager.handleJoin", () -> DisconnectForfeitManager.handleJoin(player));
        time("ProfileSessionLoader.loadCritical.FirstJoinKitManager.handleJoin", () -> FirstJoinKitManager.handleJoin(player));
        time("ProfileSessionLoader.loadCritical.MonotypeStarterManager.handleProfileLoaded", () -> MonotypeStarterManager.handleProfileLoaded(player));
    }

    /**
     * Database/file/cache work only. Never touch live Minecraft objects here.
     */
    public static BackgroundSnapshot loadBackground(UUID playerUuid, UUID profileId, String playerName) {
        long all = System.currentTimeMillis();
        if (playerUuid == null) return new BackgroundSnapshot(null, profileId, 300);
        String safeName = playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName;

        time("ProfileSessionLoader.loadBackground.PlayerDataManager.ensurePlayer", () -> PlayerDataManager.ensurePlayer(playerUuid, safeName));
        time("ProfileSessionLoader.loadBackground.ProfessionDataManager.ensurePlayer", () -> ProfessionDataManager.ensurePlayer(playerUuid, safeName));
        time("ProfileSessionLoader.loadBackground.GuildRepository.loadForPlayer", () -> GuildRepository.loadForPlayer(playerUuid, safeName));

        final int[] rp = new int[] { 300 };
        time("ProfileSessionLoader.loadBackground.PlayerDataManager.getRp", () -> rp[0] = PlayerDataManager.getRp(playerUuid, safeName));

        System.out.println("[PROFILE-TIMING] ProfileSessionLoader.loadBackground.total took " + (System.currentTimeMillis() - all) + "ms for profile=" + profileId);
        return new BackgroundSnapshot(playerUuid, profileId, rp[0]);
    }

    /**
     * Server-thread apply of async results. Verifies the player is still on the same profile.
     */
    public static void applyBackground(ServerPlayer player, BackgroundSnapshot snapshot) {
        if (player == null || snapshot == null || player.hasDisconnected()) return;
        UUID active = PlayerProfileManager.activeProfileId(player);
        if (active == null || snapshot.profileId() == null || !active.equals(snapshot.profileId())) {
            System.out.println("[PROFILE-TIMING] ProfileSessionLoader.applyBackground skipped stale snapshot active=" + active + " snapshot=" + (snapshot.profileId() == null ? "null" : snapshot.profileId()));
            return;
        }
        time("ProfileSessionLoader.applyBackground.ProfileManager.setElo", () -> ProfileManager.setElo(player, snapshot.storedRp()));
    }

    /**
     * Not needed for the exact activation tick. Running this after critical activation avoids stacking
     * all join systems into the profile-switch tick.
     */
    public static void loadDelayedNonCritical(ServerPlayer player) {
        if (player == null || player.hasDisconnected()) return;
        time("ProfileSessionLoader.delayed.NotificationManager.handleJoin", () -> NotificationManager.handleJoin(player));
        time("ProfileSessionLoader.delayed.QuestManager.handleJoin", () -> QuestManager.handleJoin(player));
        time("ProfileSessionLoader.delayed.WonderTradeSeeder.handleJoin", () -> WonderTradeSeeder.handleJoin(player));
        time("ProfileSessionLoader.delayed.ShopPokemonCrateOpeningGui.handleJoin", () -> ShopPokemonCrateOpeningGui.handleJoin(player));
        time("ProfileSessionLoader.delayed.AuctionHouseService.handleJoin", () -> AuctionHouseService.handleJoin(player));
        time("ProfileSessionLoader.delayed.ChatPreferenceManager.applyCached", () -> ChatPreferenceManager.apply(player, ChatPreferenceManager.getCachedOrDefault(player.getUUID())));
        time("ProfileSessionLoader.delayed.ModerationManager.handleJoin", () -> ModerationManager.handleJoin(player));
        time("ProfileSessionLoader.delayed.DailyLoginManager.handleJoin", () -> DailyLoginManager.handleJoin(player));
        time("ProfileSessionLoader.delayed.EconomyManager.ensurePlayer", () -> EconomyManager.ensurePlayer(player));
    }

    public static void unload(ServerPlayer player) {
        if (player == null) return;
        ChatPreferenceManager.saveAsync(player.getUUID(), ChatPreferenceManager.get(player.getUUID()));
        ShopPokemonCrateOpeningGui.handleDisconnect(player);
        com.champutils.profession.ProfessionManager.unloadPlayer(player);
        QuestManager.unloadPlayer(player);
        PartyManager.handleDisconnect(player);
        DailyLoginManager.handleDisconnect(player);
    }
}
