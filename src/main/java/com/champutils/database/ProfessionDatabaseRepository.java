package com.champutils.database;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionType;
import com.champutils.profile.PlayerProfileManager;

import java.sql.PreparedStatement;
import java.util.UUID;

public final class ProfessionDatabaseRepository {

    private ProfessionDatabaseRepository() {}

    public static void sync(ProfessionDataManager.ProfessionData data) {
        if (data == null || data.uuid == null || data.uuid.isBlank()) return;

        DatabaseManager.executeAsync("sync profile professions " + data.uuid, connection -> {
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

            UUID profileId = UUID.fromString(data.uuid);
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
        });
    }

    public static void saveAsync(UUID uuid, ProfessionDataManager.ProfessionData data) {
        if (data == null) return;
        data.uuid = PlayerProfileManager.activeProfileId(uuid).toString();
        sync(data);
    }

    public static void touchPlayer(UUID uuid, String name) {
        PlayerDatabaseRepository.touchPlayer(uuid, name);
    }
}
