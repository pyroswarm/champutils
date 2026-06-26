package com.champutils.profile;

import com.champutils.database.DatabaseManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Stores vanilla ServerPlayer NBT per SQL profile_id.
 *
 * This is intentionally conservative: it snapshots the active in-memory player before
 * a profile switch/disconnect and restores the selected profile after login/switch.
 * Cobblemon-specific storage still needs its own adapter/mixin pass, but this prevents
 * vanilla inventory/ender/stat NBT from being shared by the Mojang account UUID.
 */
public final class VanillaProfileStateManager {
    private VanillaProfileStateManager() {}

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure profile vanilla state table", connection -> ensureSchema(connection));
    }

    private static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists profile_vanilla_state (" +
                    "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                    "player_uuid uuid references players(uuid) on delete cascade, " +
                    "vanilla_snbt text not null default '{}', " +
                    "updated_at timestamptz not null default now())");
            statement.executeUpdate("alter table profile_vanilla_state add column if not exists player_uuid uuid");
            statement.executeUpdate("alter table profile_vanilla_state add column if not exists vanilla_snbt text not null default '{}'");
            statement.executeUpdate("alter table profile_vanilla_state add column if not exists updated_at timestamptz not null default now()");
            statement.executeUpdate("create index if not exists idx_profile_vanilla_state_player_uuid on profile_vanilla_state(player_uuid)");
            statement.executeUpdate("create index if not exists idx_profile_vanilla_state_updated_at on profile_vanilla_state(updated_at)");
            ProfileAtomicSnapshotManager.ensureSchema(connection);
        }
    }


    public static String snapshotSnbt(ServerPlayer player) {
        if (player == null) return null;
        try {
            CompoundTag tag = new CompoundTag();
            player.saveWithoutId(tag);
            scrubAccountOnlyFields(tag, player);
            return tag.toString();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to snapshot vanilla profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
            return null;
        }
    }

    public static void saveSnapshotAsync(UUID profileId, UUID playerUuid, String playerName, String snbt) {
        if (profileId == null || playerUuid == null || snbt == null || snbt.isBlank() || !DatabaseManager.isEnabled()) return;
        PlayerProfileManager.cacheVanillaState(profileId, snbt);
        ProfileAtomicSnapshotManager.saveVanillaCoalesced(profileId, playerUuid, playerName, snbt, "checkpoint");
    }

    public static String loadSnbt(Connection connection, UUID profileId) throws Exception {
        if (connection == null || profileId == null) return null;
        try (var ps = connection.prepareStatement("select vanilla_snbt from profile_vanilla_state where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return ProfileAtomicSnapshotManager.latestCompletedVanilla(connection, profileId);
                String active = rs.getString("vanilla_snbt");
                if (active != null && !active.isBlank()) return active;
                return ProfileAtomicSnapshotManager.latestCompletedVanilla(connection, profileId);
            }
        }
    }

    public static void applySnbt(ServerPlayer player, String snbt) {
        if (player == null) return;
        try {
            if (snbt == null || snbt.isBlank() || snbt.equals("{}")) {
                clearLiveForMenu(player);
                return;
            }
            CompoundTag tag = TagParser.parseTag(snbt);
            scrubAccountOnlyFields(tag, player);
            player.load(tag);
            player.inventoryMenu.broadcastChanges();
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to apply vanilla profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Profile load failed. Staff should check console/database logs.").withStyle(ChatFormatting.RED));
        }
    }

    public static void save(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            CompoundTag tag = new CompoundTag();
            player.saveWithoutId(tag);
            scrubAccountOnlyFields(tag, player);
            String snbt = tag.toString();
            PlayerProfileManager.cacheVanillaState(profileId, snbt);
            ProfileAtomicSnapshotManager.saveVanillaBlocking(connection, profileId, player.getUUID(), snbt, "blocking-save");
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to save vanilla profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Profile save failed. Tell staff before switching profiles again.").withStyle(ChatFormatting.RED));
        }
    }

    public static void saveAsync(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;

        UUID playerUuid = player.getUUID();
        String playerName = player.getGameProfile().getName();
        CompoundTag tag = new CompoundTag();
        player.saveWithoutId(tag);
        scrubAccountOnlyFields(tag, player);
        String snbt = tag.toString();
        PlayerProfileManager.cacheVanillaState(profileId, snbt);

        ProfileAtomicSnapshotManager.saveVanillaCoalesced(profileId, playerUuid, playerName, snbt, "async-save");
    }

    public static void load(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null || profileId.equals(player.getUUID())) return;
        try {
            Connection connection = DatabaseManager.getConnection();
            try (var ps = connection.prepareStatement("select vanilla_snbt from profile_vanilla_state where profile_id = ?")) {
                ps.setObject(1, profileId);
                try (ResultSet rs = ps.executeQuery()) {
                    String snbt;
                    if (!rs.next()) {
                        snbt = ProfileAtomicSnapshotManager.latestCompletedVanilla(connection, profileId);
                    } else {
                        snbt = rs.getString("vanilla_snbt");
                        if (snbt == null || snbt.isBlank()) snbt = ProfileAtomicSnapshotManager.latestCompletedVanilla(connection, profileId);
                    }
                    if (snbt == null || snbt.isBlank() || snbt.equals("{}")) {
                        clearLiveForMenu(player);
                        return;
                    }
                    CompoundTag tag = TagParser.parseTag(snbt);
                    scrubAccountOnlyFields(tag, player);
                    player.load(tag);
                    player.inventoryMenu.broadcastChanges();
                }
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] Failed to load vanilla profile state for " + player.getGameProfile().getName());
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Profile load failed. Staff should check console/database logs.").withStyle(ChatFormatting.RED));
        }
    }

    public static void saveThenLoad(ServerPlayer player, UUID targetProfileId) {
        save(player);
        load(player);
    }

    public static void clearLiveForMenu(ServerPlayer player) {
        player.getInventory().clearContent();
        player.getEnderChestInventory().clearContent();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.inventoryMenu.broadcastChanges();
    }

    private static void scrubAccountOnlyFields(CompoundTag tag, ServerPlayer player) {
        // Keep the real Mojang account identity account-wide. The DB row is profile-specific,
        // but vanilla should never deserialize a fake UUID/name into the live connection.
        tag.remove("UUID");
        tag.remove("uuid");
        tag.remove("Name");
        tag.remove("name");
        tag.remove("BukkitValues");
    }
}
