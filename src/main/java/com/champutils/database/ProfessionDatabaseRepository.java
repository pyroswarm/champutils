package com.champutils.database;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.debug.ChampDebugManager;

import java.sql.PreparedStatement;
import java.util.UUID;

import com.google.gson.Gson;

public final class ProfessionDatabaseRepository {

    private static volatile boolean schemaEnsured = false;
    private static final Gson GSON = new Gson();

    private ProfessionDatabaseRepository() {}

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        if (schemaEnsured) return;
        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_professions (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, profession text not null, " +
                        "level integer not null default 1, xp bigint not null default 0, data jsonb not null default '{}'::jsonb, " +
                        "updated_at timestamptz not null default now(), primary key(profile_id, profession))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_fragments (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, fragment_id text not null, amount integer not null default 0, " +
                        "updated_at timestamptz not null default now(), primary key(profile_id, fragment_id))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_chunks (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, chunk_id text not null, amount integer not null default 0, " +
                        "updated_at timestamptz not null default now(), primary key(profile_id, chunk_id))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_profession_backpack (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, item_id text not null, amount bigint not null default 0, " +
                        "updated_at timestamptz not null default now(), primary key(profile_id, item_id))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_profession_sublevels (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, profession text not null, " +
                        "sub_key text not null, level integer not null default 1, xp bigint not null default 0, actions bigint not null default 0, " +
                        "updated_at timestamptz not null default now(), primary key(profile_id, profession, sub_key))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create index if not exists idx_profile_profession_sublevels_profile on profile_profession_sublevels(profile_id, profession)"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_trinket_pouches (" +
                        "profile_id uuid primary key references player_profiles(id) on delete cascade, " +
                        "rarity text not null default '', slots integer not null default 0, " +
                        "items jsonb not null default '[]'::jsonb, updated_at timestamptz not null default now())"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create index if not exists idx_profile_trinket_pouches_updated on profile_trinket_pouches(updated_at)"
        )) { ensure.executeUpdate(); }
        schemaEnsured = true;
    }

    public static void sync(ProfessionDataManager.ProfessionData data) {
        sync(null, data);
    }

    public static void sync(UUID ownerPlayerUuid, ProfessionDataManager.ProfessionData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) return;

        DatabaseManager.executeCoalescedAsync("profession:" + data.uuid, "sync profile professions " + data.uuid, connection -> {
            ensureSchema(connection);

            UUID profileId = UUID.fromString(data.uuid);
            if (!profileRowExists(connection, profileId)) {
                ChampDebugManager.log(ChampDebugManager.Category.DATABASE,
                        "[ChampUtils DB] Skipped profession SQL sync for unknown profile id " + profileId +
                                ". This is expected for legacy player-UUID profession files and prevents ghost /profiles entries.");
                return;
            }
            try (PreparedStatement professionStatement = connection.prepareStatement(
                    "insert into profile_professions (profile_id, profession, level, xp, updated_at) values (?, ?, ?, ?, now()) " +
                            "on conflict (profile_id, profession) do update set level = excluded.level, xp = excluded.xp, updated_at = now()"
            )) {
                for (ProfessionType type : ProfessionType.values()) {
                    String key = type.name();
                    professionStatement.setObject(1, profileId);
                    professionStatement.setString(2, key);
                    professionStatement.setInt(3, Math.max(1, data.levels.getOrDefault(key, 1)));
                    professionStatement.setLong(4, Math.max(0, data.xp.getOrDefault(key, 0)));
                    professionStatement.addBatch();
                }
                professionStatement.executeBatch();
            }

            try (PreparedStatement fragmentStatement = connection.prepareStatement(
                    "insert into profile_fragments (profile_id, fragment_id, amount, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (profile_id, fragment_id) do update set amount = excluded.amount, updated_at = now()"
            )) {
                for (var entry : data.fragments.entrySet()) {
                    fragmentStatement.setObject(1, profileId);
                    fragmentStatement.setString(2, entry.getKey());
                    fragmentStatement.setInt(3, Math.max(0, entry.getValue()));
                    fragmentStatement.addBatch();
                }
                fragmentStatement.executeBatch();
            }

            try (PreparedStatement chunkStatement = connection.prepareStatement(
                    "insert into profile_chunks (profile_id, chunk_id, amount, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (profile_id, chunk_id) do update set amount = excluded.amount, updated_at = now()"
            )) {
                if (data.chunks != null) {
                    for (var entry : data.chunks.entrySet()) {
                        chunkStatement.setObject(1, profileId);
                        chunkStatement.setString(2, entry.getKey());
                        chunkStatement.setInt(3, Math.max(0, entry.getValue()));
                        chunkStatement.addBatch();
                    }
                }
                chunkStatement.executeBatch();
            }

            try (PreparedStatement backpackStatement = connection.prepareStatement(
                    "insert into profile_profession_backpack (profile_id, item_id, amount, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (profile_id, item_id) do update set amount = excluded.amount, updated_at = now()"
            )) {
                if (data.backpack != null) {
                    for (var entry : data.backpack.entrySet()) {
                        long amount = Math.max(0L, entry.getValue());
                        backpackStatement.setObject(1, profileId);
                        backpackStatement.setString(2, entry.getKey());
                        backpackStatement.setLong(3, amount);
                        backpackStatement.addBatch();
                    }
                }
                backpackStatement.executeBatch();
            }

            try (PreparedStatement sublevelStatement = connection.prepareStatement(
                    "insert into profile_profession_sublevels (profile_id, profession, sub_key, level, xp, actions, updated_at) values (?, ?, ?, ?, ?, ?, now()) " +
                            "on conflict (profile_id, profession, sub_key) do update set level = excluded.level, xp = excluded.xp, actions = excluded.actions, updated_at = now()"
            )) {
                if (data.sublevels != null) {
                    for (var entry : data.sublevels.entrySet()) {
                        String key = entry.getKey();
                        ProfessionDataManager.ProfessionData.SubLevelData sublevel = entry.getValue();
                        if (key == null || key.isBlank() || sublevel == null) continue;
                        String profession = key.contains(":") ? key.substring(0, key.indexOf(':')) : "UNKNOWN";
                        sublevelStatement.setObject(1, profileId);
                        sublevelStatement.setString(2, profession);
                        sublevelStatement.setString(3, key);
                        sublevelStatement.setInt(4, Math.max(1, Math.min(100, sublevel.level)));
                        sublevelStatement.setLong(5, Math.max(0, sublevel.xp));
                        sublevelStatement.setLong(6, Math.max(0L, sublevel.actions));
                        sublevelStatement.addBatch();
                    }
                }
                sublevelStatement.executeBatch();
            }

            try (PreparedStatement pouchStatement = connection.prepareStatement(
                    "insert into profile_trinket_pouches (profile_id, rarity, slots, items, updated_at) values (?, ?, ?, ?::jsonb, now()) " +
                            "on conflict (profile_id) do update set " +
                            "rarity = case when " +
                            "(case upper(excluded.rarity) when 'COMMON' then 1 when 'UNCOMMON' then 2 when 'RARE' then 3 when 'EPIC' then 4 when 'LEGENDARY' then 5 when 'MYTHIC' then 6 else 0 end) >= " +
                            "(case upper(profile_trinket_pouches.rarity) when 'COMMON' then 1 when 'UNCOMMON' then 2 when 'RARE' then 3 when 'EPIC' then 4 when 'LEGENDARY' then 5 when 'MYTHIC' then 6 else 0 end) " +
                            "then excluded.rarity else profile_trinket_pouches.rarity end, " +
                            "slots = greatest(profile_trinket_pouches.slots, excluded.slots), " +
                            "items = excluded.items, updated_at = now()"
            )) {
                pouchStatement.setObject(1, profileId);
                pouchStatement.setString(2, data.trinketPouchRarity == null ? "" : data.trinketPouchRarity);
                pouchStatement.setInt(3, Math.max(0, data.trinketPouchSlots));
                pouchStatement.setString(4, GSON.toJson(data.trinketPouchItems == null ? java.util.List.of() : data.trinketPouchItems));
                pouchStatement.executeUpdate();
            }
        });
    }

    private static boolean profileRowExists(java.sql.Connection connection, UUID profileId) throws Exception {
        if (profileId == null) return false;
        try (PreparedStatement exists = connection.prepareStatement("select 1 from player_profiles where id = ? and deleted_at is null")) {
            exists.setObject(1, profileId);
            try (var rs = exists.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void saveAsync(UUID uuid, ProfessionDataManager.ProfessionData data) {
        if (data == null) return;
        UUID activeProfileId = PlayerProfileManager.activeProfileIdOrNull(uuid);
        if (activeProfileId == null) {
            return;
        }
        data.uuid = activeProfileId.toString();
        sync(uuid, data);
    }

    public static void touchPlayer(UUID uuid, String name) {
        PlayerDatabaseRepository.touchPlayer(uuid, name);
    }
}
