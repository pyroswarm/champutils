package com.champutils.database;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerProfileManager;
import com.champutils.debug.ChampDebugManager;

import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
                "create table if not exists profession_xp_bonus_bank (" +
                        "player_uuid uuid not null references players(uuid) on delete cascade, " +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, " +
                        "profession text not null, stored_bonus numeric(10,4) not null default 0, " +
                        "updated_at timestamptz not null default now(), " +
                        "primary key(player_uuid, profile_id, profession))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create index if not exists idx_profession_xp_bonus_bank_profile on profession_xp_bonus_bank(profile_id)"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement ensure = connection.prepareStatement(
                "create table if not exists profile_essence (" +
                        "profile_id uuid not null references player_profiles(id) on delete cascade, essence_id text not null, amount integer not null default 0, " +
                        "updated_at timestamptz not null default now(), primary key(profile_id, essence_id))"
        )) { ensure.executeUpdate(); }

        try (PreparedStatement migrate = connection.prepareStatement(
                "do $$ begin " +
                        "if to_regclass('public.profile_fragments') is not null then " +
	                        "insert into profile_essence (profile_id, essence_id, amount, updated_at) " +
	                        "select profile_id, essence_id, max(amount), max(updated_at) from (" +
	                        "select profile_id, " +
	                        "case upper(replace(replace(fragment_id, '-', '_'), ' ', '_')) " +
	                        "when 'COMMON' then 'F' when 'F_RANK' then 'F' when 'F' then 'F' " +
	                        "when 'UNCOMMON' then 'E' when 'E_RANK' then 'E' when 'E' then 'E' " +
	                        "when 'RARE' then 'D' when 'D_RANK' then 'D' when 'D' then 'D' " +
	                        "when 'EPIC' then 'C' when 'C_RANK' then 'C' when 'C' then 'C' " +
	                        "when 'B_RANK' then 'B' when 'B' then 'B' " +
	                        "when 'LEGENDARY' then 'A' when 'A_RANK' then 'A' when 'A' then 'A' " +
	                        "when 'MYTHIC' then 'S' when 'MYTHICAL' then 'S' when 'S_RANK' then 'S' when 'S' then 'S' " +
	                        "else upper(fragment_id) end as essence_id, " +
	                        "amount, updated_at from profile_fragments where amount > 0) migrated " +
	                        "group by profile_id, essence_id " +
	                        "on conflict (profile_id, essence_id) do update set amount = greatest(profile_essence.amount, excluded.amount), updated_at = now(); " +
	                        "end if; end $$"
        )) { migrate.executeUpdate(); }

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

            UUID playerUuid = ownerPlayerUuid == null ? lookupPlayerUuid(connection, profileId) : ownerPlayerUuid;
            if (playerUuid != null) {
                try (PreparedStatement bonusStatement = connection.prepareStatement(
                        "insert into profession_xp_bonus_bank (player_uuid, profile_id, profession, stored_bonus, updated_at) values (?, ?, ?, ?, now()) " +
                                "on conflict (player_uuid, profile_id, profession) do update set stored_bonus = excluded.stored_bonus, updated_at = now()"
                )) {
                    if (data.xpBonusBank != null) {
                        for (var entry : data.xpBonusBank.entrySet()) {
                            String profession = normalizeDataKey(entry.getKey()).toUpperCase(Locale.ROOT);
                            Double stored = entry.getValue();
                            if (profession.isBlank() || stored == null || !Double.isFinite(stored) || stored <= 0.0D) continue;
                            bonusStatement.setObject(1, playerUuid);
                            bonusStatement.setObject(2, profileId);
                            bonusStatement.setString(3, profession);
                            bonusStatement.setBigDecimal(4, java.math.BigDecimal.valueOf(Math.min(0.9999D, Math.max(0.0D, stored))).setScale(4, java.math.RoundingMode.HALF_UP));
                            bonusStatement.addBatch();
                        }
                    }
                    bonusStatement.executeBatch();
                }

                try (PreparedStatement cleanup = connection.prepareStatement(
                        "delete from profession_xp_bonus_bank where profile_id = ? and stored_bonus <= 0"
                )) {
                    cleanup.setObject(1, profileId);
                    cleanup.executeUpdate();
                }
            }

            try (PreparedStatement fragmentStatement = connection.prepareStatement(
                    "insert into profile_essence (profile_id, essence_id, amount, updated_at) values (?, ?, ?, now()) " +
                            "on conflict (profile_id, essence_id) do update set amount = excluded.amount, updated_at = now()"
            )) {
	                Map<String, Integer> fragments = new LinkedHashMap<>();
	                if (data.fragments != null) {
	                    for (var entry : data.fragments.entrySet()) {
	                        String key = normalizeEssenceKey(entry.getKey());
	                        if (key.isBlank()) continue;
	                        fragments.merge(key, Math.max(0, entry.getValue()), Math::max);
	                    }
	                }
	                for (var entry : fragments.entrySet()) {
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
	                    Map<String, Integer> chunks = new LinkedHashMap<>();
	                    for (var entry : data.chunks.entrySet()) {
	                        String key = normalizeDataKey(entry.getKey());
	                        if (key.isBlank()) continue;
	                        chunks.merge(key, Math.max(0, entry.getValue()), Math::max);
	                    }
	                    for (var entry : chunks.entrySet()) {
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
	                    Map<String, Long> backpack = new LinkedHashMap<>();
	                    for (var entry : data.backpack.entrySet()) {
	                        String key = normalizeDataKey(entry.getKey());
	                        if (key.isBlank()) continue;
	                        backpack.merge(key, Math.max(0L, entry.getValue()), Math::max);
	                    }
	                    for (var entry : backpack.entrySet()) {
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
	                    Map<String, ProfessionDataManager.ProfessionData.SubLevelData> sublevels = new LinkedHashMap<>();
	                    for (var entry : data.sublevels.entrySet()) {
	                        String key = normalizeDataKey(entry.getKey());
	                        ProfessionDataManager.ProfessionData.SubLevelData sublevel = entry.getValue();
	                        if (key == null || key.isBlank() || sublevel == null) continue;
	                        sublevels.merge(key, sublevel, ProfessionDatabaseRepository::higherSublevel);
	                    }
	                    for (var entry : sublevels.entrySet()) {
	                        String key = entry.getKey();
	                        ProfessionDataManager.ProfessionData.SubLevelData sublevel = entry.getValue();
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
                            "(case upper(excluded.rarity) when 'F' then 1 when 'E' then 2 when 'D' then 3 when 'C' then 4 when 'B' then 5 when 'A' then 6 when 'S' then 7 else 0 end) >= " +
                            "(case upper(profile_trinket_pouches.rarity) when 'F' then 1 when 'E' then 2 when 'D' then 3 when 'C' then 4 when 'B' then 5 when 'A' then 6 when 'S' then 7 else 0 end) " +
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

    private static UUID lookupPlayerUuid(java.sql.Connection connection, UUID profileId) throws Exception {
        if (profileId == null) return null;
        try (PreparedStatement statement = connection.prepareStatement("select player_uuid from player_profiles where id = ? and deleted_at is null")) {
            statement.setObject(1, profileId);
            try (var rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                Object value = rs.getObject(1);
                if (value instanceof UUID uuid) return uuid;
                return value == null ? null : UUID.fromString(value.toString());
            }
        }
    }

    private static String normalizeEssenceKey(String value) {
        String key = normalizeDataKey(value).replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        return switch (key) {
            case "COMMON", "F_RANK", "F" -> "F";
            case "UNCOMMON", "E_RANK", "E" -> "E";
            case "RARE", "D_RANK", "D" -> "D";
            case "EPIC", "C_RANK", "C" -> "C";
            case "B_RANK", "B" -> "B";
            case "LEGENDARY", "A_RANK", "A" -> "A";
            case "MYTHIC", "MYTHICAL", "S_RANK", "S" -> "S";
            default -> key;
        };
    }

    private static String normalizeDataKey(String value) {
        return value == null ? "" : value.trim();
    }

    private static ProfessionDataManager.ProfessionData.SubLevelData higherSublevel(
            ProfessionDataManager.ProfessionData.SubLevelData first,
            ProfessionDataManager.ProfessionData.SubLevelData second
    ) {
        if (first == null) return second;
        if (second == null) return first;
        if (second.level > first.level) return second;
        if (second.level == first.level && second.xp > first.xp) return second;
        return first;
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
