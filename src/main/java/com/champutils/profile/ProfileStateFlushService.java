package com.champutils.profile;

import com.champutils.battle.BattleStateManager;
import com.champutils.chat.ChatPreferenceManager;
import com.champutils.database.DatabaseManager;
import com.champutils.economy.EconomyManager;
import com.champutils.megaboss.MegaBossBattleListener;
import com.champutils.profession.ProfessionManager;
import com.champutils.quest.QuestManager;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ProfileStateFlushService {
    private ProfileStateFlushService() {
    }

    public record LocationSnapshot(
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {}

    public record TransferFlushSnapshot(
            UUID profileId,
            UUID playerUuid,
            String playerName,
            String vanillaSnbt,
            ProfileCobblemonSqlStoreFactory.StoreSnapshot cobblemonSnapshot,
            LocationSnapshot locationSnapshot,
            com.champutils.expeditions.ExpeditionManager.Save expeditionSnapshot
    ) {
        public boolean hasActiveProfile() {
            return profileId != null && playerUuid != null;
        }
    }

    /**
     * Captures the live profile state on the server thread before a backend/proxy transfer.
     * The returned object is immutable SNBT/NBT text and primitives, so it can safely be
     * committed on a database worker before a transfer token is issued.
     */
    public static TransferFlushSnapshot captureBeforeTransfer(ServerPlayer player, String reason) {
        if (player == null || !PlayerProfileManager.hasActiveProfile(player)) {
            return new TransferFlushSnapshot(null, null, null, null, null, null, null);
        }

        UUID profileId = PlayerProfileManager.activeProfileId(player);
        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();
        if (profileId == null || profileId.equals(playerUuid)) {
            return new TransferFlushSnapshot(null, playerUuid, playerName, null, null, null, null);
        }

        try {
            ProfilePlaytimeManager.recordCurrentSession(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to checkpoint playtime for " + playerName + " during " + reason);
            e.printStackTrace();
        }

        String vanillaSnbt = null;
        try {
            vanillaSnbt = VanillaProfileStateManager.snapshotSnbt(player);
            if (vanillaSnbt != null && !vanillaSnbt.isBlank()) {
                PlayerProfileManager.cacheVanillaState(profileId, vanillaSnbt);
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to snapshot vanilla profile state for " + playerName + " during " + reason);
            e.printStackTrace();
        }

        ProfileCobblemonSqlStoreFactory.StoreSnapshot cobblemonSnapshot = null;
        try {
            cobblemonSnapshot = CobblemonProfileStorageBridge.snapshotActiveProfileStores(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to snapshot Cobblemon profile stores for " + playerName + " during " + reason);
            e.printStackTrace();
        }

        LocationSnapshot locationSnapshot = captureLocationSnapshot(player);
        com.champutils.expeditions.ExpeditionManager.Save expeditionSnapshot = null;
        try {
            expeditionSnapshot = com.champutils.expeditions.ExpeditionManager.captureForTransfer(profileId);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to snapshot expedition state for " + playerName + " during " + reason);
            e.printStackTrace();
        }

        return new TransferFlushSnapshot(profileId, playerUuid, playerName, vanillaSnbt, cobblemonSnapshot, locationSnapshot, expeditionSnapshot);
    }

    /**
     * Commits the exact server-thread snapshot captured by captureBeforeTransfer. This must run
     * before issuing a transfer token or proxy moving the player to another backend.
     */
    public static void commitTransferSnapshot(Connection connection, TransferFlushSnapshot snapshot, String reason) throws Exception {
        if (connection == null || snapshot == null || !snapshot.hasActiveProfile()) return;
        String saveReason = reason == null || reason.isBlank() ? "profile-transfer" : reason;

        if (snapshot.locationSnapshot() != null) {
            saveLocationSnapshot(connection, snapshot.profileId(), snapshot.locationSnapshot());
        }

        if (snapshot.vanillaSnbt() != null && !snapshot.vanillaSnbt().isBlank()) {
            ProfileAtomicSnapshotManager.saveVanillaBlocking(
                    connection,
                    snapshot.profileId(),
                    snapshot.playerUuid(),
                    snapshot.vanillaSnbt(),
                    saveReason
            );
        }

        if (snapshot.cobblemonSnapshot() != null && snapshot.cobblemonSnapshot().hasAnyPayload()) {
            CobblemonProfileStorageBridge.saveSnapshotBlocking(
                    connection,
                    snapshot.profileId(),
                    snapshot.cobblemonSnapshot(),
                    saveReason
            );
        }

        if (snapshot.expeditionSnapshot() != null) {
            com.champutils.expeditions.ExpeditionManager.saveBlocking(
                    connection,
                    snapshot.profileId(),
                    snapshot.expeditionSnapshot()
            );
        }
    }

    /**
     * Queues the non-snapshot profile systems before a transfer. All SQL-backed managers invoked
     * here enqueue their writes; callers that require the atomic vanilla/Cobblemon snapshot to be
     * committed before proxy handoff should commit the captured TransferFlushSnapshot in their
     * own DatabaseManager future and wait for that future's completion callback.
     */
    public static boolean queueAncillaryStateBeforeTransfer(ServerPlayer player, String reason) {
        if (player == null) return false;

        try {
            PlayerProfileManager.saveActiveLocationAsync(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to queue location checkpoint before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            ProfessionManager.savePlayerNow(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save professions before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
            return false;
        }

        try {
            QuestManager.savePlayer(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save quest state before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            com.champutils.adventureguide.AdventureGuideManager.saveAll();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save adventure guide state before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            com.champutils.adventurer.AdventurerGuildManager.savePlayer(player);
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save adventurer guild state before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            ChatPreferenceManager.saveAsync(player.getUUID(), ChatPreferenceManager.get(player.getUUID()));
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to queue chat preferences before transfer for " + player.getGameProfile().getName());
            e.printStackTrace();
        }

        try {
            EconomyManager.save();
        } catch (Exception ignored) {
        }
        return true;
    }

    public static boolean flushBeforeTransfer(ServerPlayer player, String reason, long timeout, TimeUnit unit) {
        TransferFlushSnapshot snapshot = captureBeforeTransfer(player, reason == null ? "profile_transfer" : reason);
        if (!snapshot.hasActiveProfile()) return true;
        if (!queueAncillaryStateBeforeTransfer(player, reason)) return false;

        DatabaseManager.executeCoalescedAsync(
                "profile-hard-transfer-snapshot:" + snapshot.profileId(),
                "hard profile transfer snapshot",
                connection -> commitTransferSnapshot(connection, snapshot, reason)
        );

        if (Thread.currentThread().getName() != null && Thread.currentThread().getName().equalsIgnoreCase("Server thread")) {
            return true;
        }
        return DatabaseManager.flushSubmittedTasks(timeout, unit);
    }

    private static LocationSnapshot captureLocationSnapshot(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled() || !PlayerProfileManager.hasActiveProfile(player)) return null;
        if (BattleStateManager.isInBattle(player) || BattleStateManager.hasTrackedState(player) || MegaBossBattleListener.isPlayerInMegaBossBattle(player)) {
            return null;
        }

        String dimension = player.serverLevel().dimension().location().toString();
        if (ProfileLobbyManager.PROFILE_LOBBY_DIMENSION.equals(dimension) || PlayerProfileManager.isInMainMenu(player)) {
            return null;
        }

        return new LocationSnapshot(
                dimension,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );
    }

    private static void saveLocationSnapshot(Connection connection, UUID profileId, LocationSnapshot snapshot) throws Exception {
        if (connection == null || profileId == null || snapshot == null || snapshot.dimension() == null || snapshot.dimension().isBlank()) return;
        try (var ps = connection.prepareStatement(
                "update player_profiles set last_dimension = ?, last_x = ?, last_y = ?, last_z = ?, last_yaw = ?, last_pitch = ?, last_survival_server_id = ?, last_used_at = now() where id = ?")) {
            ps.setString(1, snapshot.dimension());
            ps.setDouble(2, snapshot.x());
            ps.setDouble(3, snapshot.y());
            ps.setDouble(4, snapshot.z());
            ps.setFloat(5, snapshot.yaw());
            ps.setFloat(6, snapshot.pitch());
            ps.setString(7, com.champutils.network.NetworkServerConfig.serverId());
            ps.setObject(8, profileId);
            ps.executeUpdate();
        }
    }
}
