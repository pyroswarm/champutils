package com.champutils.badge;

import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Async SQL persistence for profile-scoped badges and badge-derived unlock snapshots.
 *
 * All public write/read warmup methods queue work on DatabaseManager's async executor so badge awards,
 * permission checks, and command handling never wait on Supabase/Postgres from the Minecraft server thread.
 */
public final class BadgeSqlRepository {

    private static final ConcurrentMap<UUID, Boolean> LOADS_IN_FLIGHT = new ConcurrentHashMap<>();
    private static volatile boolean schemaQueued = false;
    private static volatile boolean schemaEnsured = false;

    private BadgeSqlRepository() {}

    public static void initAsync() {
        ensureSchemaAsync();
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        if (schemaQueued) return;
        schemaQueued = true;
        DatabaseManager.executeAsync("ensure badge sql schema", BadgeSqlRepository::ensureSchema);
    }

    public static void warmPlayerAsync(ServerPlayer player) {
        if (player == null) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        warmProfileAsync(profileId);
    }

    public static void warmProfileAsync(UUID profileId) {
        if (profileId == null || !DatabaseManager.isEnabled()) return;
        ensureSchemaAsync();
        if (LOADS_IN_FLIGHT.putIfAbsent(profileId, Boolean.TRUE) != null) return;

        DatabaseManager.executeAsync("load profile badges", connection -> {
            try {
                Set<BadgeType> badges = loadBadges(connection, profileId);
                BadgeManager.acceptSqlSnapshot(profileId, badges);
            } finally {
                LOADS_IN_FLIGHT.remove(profileId);
            }
        });
    }

    public static void saveBadgeAwardAsync(ServerPlayer player, BadgeType badge) {
        if (player == null || badge == null || !DatabaseManager.isEnabled()) return;
        UUID playerUuid = player.getUUID();
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        String playerName = player.getGameProfile().getName();
        ensureSchemaAsync();

        DatabaseManager.executeAsync("save profile badge award", connection -> {
            ensurePlayer(connection, playerUuid, playerName);
            upsertBadge(connection, profileId, badge);
        });
    }

    public static void saveBadgeSnapshotAsync(UUID profileId, Set<BadgeType> badges) {
        if (profileId == null || badges == null || !DatabaseManager.isEnabled()) return;
        Set<BadgeType> snapshot = new HashSet<>(badges);
        ensureSchemaAsync();

        DatabaseManager.executeCoalescedAsync("badges:" + profileId, "save profile badge snapshot", connection -> {
            for (BadgeType badge : snapshot) {
                upsertBadge(connection, profileId, badge);
            }
        });
    }

    public static void saveUnlockSnapshotAsync(ServerPlayer player, Set<String> commands, Set<String> permissions, Set<String> titles) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profileId = PlayerProfileManager.activeProfileId(player);
        if (profileId == null) return;
        Set<String> commandSnapshot = commands == null ? Set.of() : Set.copyOf(commands);
        Set<String> permissionSnapshot = permissions == null ? Set.of() : Set.copyOf(permissions);
        Set<String> titleSnapshot = titles == null ? Set.of() : Set.copyOf(titles);
        ensureSchemaAsync();

        DatabaseManager.executeCoalescedAsync("badge_unlocks:" + profileId, "save badge unlock snapshot", connection -> {
            replaceUnlocks(connection, profileId, "COMMAND", commandSnapshot);
            replaceUnlocks(connection, profileId, "PERMISSION", permissionSnapshot);
            replaceUnlocks(connection, profileId, "TITLE", titleSnapshot);
        });
    }

    private static synchronized void ensureSchema(Connection connection) throws Exception {
        if (schemaEnsured) return;
        try (Statement statement = connection.createStatement()) {
            // Create minimal tables first. If an older/partial profile_badges table already exists, the ALTERs below repair it.
            statement.executeUpdate("create table if not exists profile_badges (profile_id uuid not null)");
            statement.executeUpdate("alter table profile_badges add column if not exists badge_id text");
            statement.executeUpdate("alter table profile_badges add column if not exists unlocked_at timestamptz not null default now()");
            statement.executeUpdate("alter table profile_badges add column if not exists data jsonb not null default '{}'::jsonb");
            statement.executeUpdate("alter table profile_badges add column if not exists badge text");
            statement.executeUpdate("alter table profile_badges add column if not exists earned_at timestamptz not null default now()");
            statement.executeUpdate("alter table profile_badges add column if not exists metadata jsonb not null default '{}'::jsonb");
            // Migrate older schemas safely. Some beta databases had badge_id, but not name.
            // Never reference optional columns directly unless they exist, or Postgres fails at parse time.
            statement.executeUpdate("do $$ begin " +
                    "if exists (select 1 from information_schema.columns where table_name = 'profile_badges' and column_name = 'badge_id') then " +
                    "execute 'update profile_badges set badge = coalesce(badge, badge_id::text) where badge is null'; " +
                    "end if; end $$");
            statement.executeUpdate("update profile_badges set badge = coalesce(nullif(badge, ''), badge_id) where badge is null or btrim(badge) = ''");
            statement.executeUpdate("update profile_badges set badge_id = badge where badge_id is null or btrim(badge_id) = ''");
            statement.executeUpdate("delete from profile_badges where profile_id is null or badge is null or btrim(badge) = ''");
            statement.executeUpdate("delete from profile_badges a using profile_badges b where a.ctid < b.ctid and a.profile_id = b.profile_id and a.badge = b.badge");
            statement.executeUpdate("delete from profile_badges a using profile_badges b where a.ctid < b.ctid and a.profile_id = b.profile_id and a.badge_id = b.badge_id");
            statement.executeUpdate("alter table profile_badges alter column badge set not null");
            statement.executeUpdate("alter table profile_badges alter column badge_id set not null");
            statement.executeUpdate("do $$ begin " +
                    "if not exists (select 1 from pg_constraint where conname = 'profile_badges_profile_fk') then " +
                    "alter table profile_badges add constraint profile_badges_profile_fk foreign key (profile_id) references player_profiles(id) on delete cascade; " +
                    "end if; end $$");
            statement.executeUpdate("do $$ begin " +
                    "if not exists (select 1 from pg_constraint where conname = 'profile_badges_pkey') then " +
                    "alter table profile_badges add constraint profile_badges_pkey primary key (profile_id, badge); " +
                    "end if; end $$");
            statement.executeUpdate("create unique index if not exists profile_badges_profile_badge_uidx on profile_badges(profile_id, badge)");
            statement.executeUpdate("create index if not exists idx_profile_badges_profile on profile_badges(profile_id)");
            statement.executeUpdate("create index if not exists idx_profile_badges_badge on profile_badges(badge)");

            statement.executeUpdate("create table if not exists profile_badge_unlocks (profile_id uuid not null)");
            statement.executeUpdate("alter table profile_badge_unlocks add column if not exists unlock_type text");
            statement.executeUpdate("alter table profile_badge_unlocks add column if not exists unlock_key text");
            statement.executeUpdate("alter table profile_badge_unlocks add column if not exists source text not null default 'BADGE_CONFIG'");
            statement.executeUpdate("alter table profile_badge_unlocks add column if not exists updated_at timestamptz not null default now()");
            statement.executeUpdate("delete from profile_badge_unlocks where profile_id is null or unlock_type is null or unlock_key is null");
            statement.executeUpdate("delete from profile_badge_unlocks a using profile_badge_unlocks b where a.ctid < b.ctid and a.profile_id = b.profile_id and a.unlock_type = b.unlock_type and a.unlock_key = b.unlock_key");
            statement.executeUpdate("alter table profile_badge_unlocks alter column unlock_type set not null");
            statement.executeUpdate("alter table profile_badge_unlocks alter column unlock_key set not null");
            statement.executeUpdate("do $$ begin " +
                    "if not exists (select 1 from pg_constraint where conname = 'profile_badge_unlocks_profile_fk') then " +
                    "alter table profile_badge_unlocks add constraint profile_badge_unlocks_profile_fk foreign key (profile_id) references player_profiles(id) on delete cascade; " +
                    "end if; end $$");
            statement.executeUpdate("do $$ begin " +
                    "if not exists (select 1 from pg_constraint where conname = 'profile_badge_unlocks_type_check') then " +
                    "alter table profile_badge_unlocks add constraint profile_badge_unlocks_type_check check (unlock_type in ('COMMAND','PERMISSION','TITLE')); " +
                    "end if; end $$");
            statement.executeUpdate("do $$ begin " +
                    "if not exists (select 1 from pg_constraint where conname = 'profile_badge_unlocks_pkey') then " +
                    "alter table profile_badge_unlocks add constraint profile_badge_unlocks_pkey primary key (profile_id, unlock_type, unlock_key); " +
                    "end if; end $$");
            statement.executeUpdate("create unique index if not exists profile_badge_unlocks_profile_type_key_uidx on profile_badge_unlocks(profile_id, unlock_type, unlock_key)");
            statement.executeUpdate("create index if not exists idx_profile_badge_unlocks_profile_type on profile_badge_unlocks(profile_id, unlock_type)");
        }
        schemaEnsured = true;
    }

    private static void ensurePlayer(Connection connection, UUID playerUuid, String playerName) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into players (uuid, username, last_seen) values (?, ?, now()) " +
                        "on conflict (uuid) do update set username = excluded.username, last_seen = now()")) {
            ps.setObject(1, playerUuid);
            ps.setString(2, playerName == null || playerName.isBlank() ? playerUuid.toString() : playerName);
            ps.executeUpdate();
        }
    }

    private static Set<BadgeType> loadBadges(Connection connection, UUID profileId) throws Exception {
        ensureSchema(connection);
        Set<BadgeType> badges = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("select badge from profile_badges where profile_id = ?")) {
            ps.setObject(1, profileId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BadgeType badge = BadgeType.fromString(rs.getString("badge"));
                    if (badge != null) badges.add(badge);
                }
            }
        }
        return badges;
    }

    private static void upsertBadge(Connection connection, UUID profileId, BadgeType badge) throws Exception {
        ensureSchema(connection);
        try (PreparedStatement ps = connection.prepareStatement(
                "insert into profile_badges (profile_id, badge_id, badge, earned_at) " +
                        "select ?, ?, ?, now() where exists (select 1 from player_profiles where id = ? and deleted_at is null) " +
                        "on conflict (profile_id, badge) do nothing")) {
            ps.setObject(1, profileId);
            ps.setString(2, badge.name());
            ps.setString(3, badge.name());
            ps.setObject(4, profileId);
            ps.executeUpdate();
        }
    }

    private static void replaceUnlocks(Connection connection, UUID profileId, String type, Set<String> values) throws Exception {
        ensureSchema(connection);
        try (PreparedStatement delete = connection.prepareStatement(
                "delete from profile_badge_unlocks where profile_id = ? and unlock_type = ?")) {
            delete.setObject(1, profileId);
            delete.setString(2, type);
            delete.executeUpdate();
        }

        if (values.isEmpty()) return;
        try (PreparedStatement insert = connection.prepareStatement(
                "insert into profile_badge_unlocks (profile_id, unlock_type, unlock_key, updated_at) " +
                        "select ?, ?, ?, now() where exists (select 1 from player_profiles where id = ? and deleted_at is null) " +
                        "on conflict (profile_id, unlock_type, unlock_key) do update set updated_at = now()")) {
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                insert.setObject(1, profileId);
                insert.setString(2, type);
                insert.setString(3, value);
                insert.setObject(4, profileId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
