package com.champutils.database;

public final class NetworkReadySchemaManager {

    private static boolean ensured = false;

    private NetworkReadySchemaManager() {
    }

    public static void ensureAsync() {
        if (!DatabaseManager.isEnabled()) {
            return;
        }

        DatabaseManager.executeAsync("ensure network-ready schemas", connection -> {
            if (ensured) {
                return;
            }

            try (java.sql.Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "create table if not exists players (" +
                                "uuid uuid primary key, " +
                                "username text not null, " +
                                "playtime_seconds bigint not null default 0, " +
                                "first_seen timestamptz not null default now(), " +
                                "last_seen timestamptz not null default now(), " +
                                "last_server_id text" +
                                ")"
                );

                statement.executeUpdate("alter table players add column if not exists playtime_seconds bigint not null default 0");
                statement.executeUpdate("alter table players add column if not exists first_seen timestamptz not null default now()");
                statement.executeUpdate("alter table players add column if not exists last_seen timestamptz not null default now()");
                statement.executeUpdate("alter table players add column if not exists last_server_id text");

                statement.executeUpdate(
                        "create table if not exists server_nodes (" +
                                "server_id text primary key, " +
                                "server_role text not null, " +
                                "online_players integer not null default 0, " +
                                "max_players integer not null default 0, " +
                                "motd text not null default '', " +
                                "last_heartbeat timestamptz not null default now(), " +
                                "metadata jsonb not null default '{}'::jsonb" +
                                ")"
                );


                statement.executeUpdate(
                        "create table if not exists server_status (" +
                                "id text primary key, " +
                                "online_players integer not null default 0, " +
                                "max_players integer not null default 0, " +
                                "motd text not null default '', " +
                                "last_heartbeat timestamptz not null default now()" +
                                ")"
                );
                statement.executeUpdate("alter table server_status add column if not exists online_players integer not null default 0");
                statement.executeUpdate("alter table server_status add column if not exists max_players integer not null default 0");
                statement.executeUpdate("alter table server_status add column if not exists motd text not null default ''");
                statement.executeUpdate("alter table server_status add column if not exists last_heartbeat timestamptz not null default now()");

                statement.executeUpdate(
                        "create table if not exists guilds (" +
                                "id uuid primary key, " +
                                "name text not null unique, " +
                                "tag text unique, " +
                                "description text not null default '', " +
                                "owner_uuid uuid not null, " +
                                "level integer not null default 1, " +
                                "xp bigint not null default 0, " +
                                "created_at timestamptz not null default now(), " +
                                "updated_at timestamptz not null default now()" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists guild_members (" +
                                "guild_id uuid not null references guilds(id) on delete cascade, " +
                                "player_uuid uuid not null, " +
                                "player_name text not null, " +
                                "role text not null, " +
                                "joined_at timestamptz not null default now(), " +
                                "primary key (guild_id, player_uuid), " +
                                "unique (player_uuid)" +
                                ")"
                );


                statement.executeUpdate("update guild_members set role = 'LEADER' where upper(role) = 'OWNER'");
                statement.executeUpdate("update guild_members set role = 'RECRUIT' where role is null or trim(role) = ''");

                statement.executeUpdate(
                        "create table if not exists guild_invites (" +
                                "guild_id uuid not null references guilds(id) on delete cascade, " +
                                "invited_uuid uuid not null, " +
                                "invited_name text not null, " +
                                "invited_by_uuid uuid not null, " +
                                "expires_at timestamptz not null, " +
                                "created_at timestamptz not null default now(), " +
                                "primary key (guild_id, invited_uuid)" +
                                ")"
                );


                statement.executeUpdate(
                        "create table if not exists guild_create_cooldowns (" +
                                "player_uuid uuid primary key, " +
                                "disbanded_at timestamptz not null default now()" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists guild_xp_log (" +
                                "id bigserial primary key, " +
                                "guild_id uuid not null references guilds(id) on delete cascade, " +
                                "player_uuid uuid, " +
                                "source text not null, " +
                                "amount bigint not null, " +
                                "created_at timestamptz not null default now()" +
                                ")"
                );

                statement.executeUpdate("alter table guild_xp_log add column if not exists player_uuid uuid");
                statement.executeUpdate("alter table guild_xp_log add column if not exists source text not null default 'manual_or_system'");
                statement.executeUpdate("alter table guild_xp_log add column if not exists amount bigint not null default 0");

                statement.executeUpdate(
                        "create table if not exists guild_buff_unlocks (" +
                                "guild_id uuid not null references guilds(id) on delete cascade, " +
                                "buff_id text not null, " +
                                "unlocked_at timestamptz not null default now(), " +
                                "primary key (guild_id, buff_id)" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists territories (" +
                                "id uuid primary key, " +
                                "owner_type text not null, " +
                                "owner_id text not null, " +
                                "owner_name text not null default '', " +
                                "display_name text, " +
                                "server_id text not null, " +
                                "world_name text not null, " +
                                "min_x integer not null, " +
                                "max_x integer not null, " +
                                "min_z integer not null, " +
                                "max_z integer not null, " +
                                "spawn_x double precision not null, " +
                                "spawn_y double precision not null, " +
                                "spawn_z double precision not null, " +
                                "spawn_yaw real not null default 0, " +
                                "spawn_pitch real not null default 0, " +
                                "level integer not null default 1, " +
                                "created_at timestamptz not null default now(), " +
                                "updated_at timestamptz not null default now(), " +
                                "unique (owner_type, owner_id)" +
                                ")"
                );

                statement.executeUpdate("alter table territories add column if not exists owner_id text");
                statement.executeUpdate("alter table territories add column if not exists owner_profile_id uuid");
                statement.executeUpdate("alter table territories add column if not exists owner_guild_id uuid");
                statement.executeUpdate("update territories set owner_id = coalesce(owner_id, owner_profile_id::text, owner_guild_id::text, '') where owner_id is null");
                statement.executeUpdate("delete from territories where owner_id is null or trim(owner_id) = ''");
                statement.executeUpdate("alter table territories add column if not exists world_key text");
                statement.executeUpdate("alter table territories add column if not exists display_name text");
                statement.executeUpdate("update territories set display_name = owner_name where display_name is null or trim(display_name) = ''");
                statement.executeUpdate("alter table territories add column if not exists slot_index integer not null default 0");
                statement.executeUpdate("alter table territories add column if not exists generation_state text not null default 'READY'");
                statement.executeUpdate("alter table territories add column if not exists deleted_at timestamptz");
                statement.executeUpdate("alter table territories add column if not exists center_x integer");
                statement.executeUpdate("alter table territories add column if not exists center_z integer");
                statement.executeUpdate("alter table territories add column if not exists radius integer");
                statement.executeUpdate("alter table territories add column if not exists biome_preference text");
                statement.executeUpdate("alter table territories add column if not exists is_public boolean not null default false");
                statement.executeUpdate("alter table territories add column if not exists allow_visitors boolean not null default false");
                statement.executeUpdate("alter table territories add column if not exists visitors_can_build boolean not null default false");
                statement.executeUpdate("alter table territories add column if not exists visitors_can_open_containers boolean not null default false");
                statement.executeUpdate("alter table territories add column if not exists visitors_can_interact_entities boolean not null default false");
                statement.executeUpdate("alter table territories add column if not exists visitors_can_use_redstone boolean not null default false");
                statement.executeUpdate("alter table territories add column if not exists lock_border boolean not null default true");
                statement.executeUpdate("alter table territories add column if not exists steward_npc_spawned boolean not null default true");
                statement.executeUpdate("update territories set world_key = world_name where world_key is null or trim(world_key) = ''");
                statement.executeUpdate("update territories set generation_state = 'READY' where generation_state is null or trim(generation_state) = ''");
                statement.executeUpdate("update territories set center_x = ((min_x + max_x) / 2) where center_x is null");
                statement.executeUpdate("update territories set center_z = ((min_z + max_z) / 2) where center_z is null");
                statement.executeUpdate("update territories set radius = greatest(((max_x - min_x) / 2), ((max_z - min_z) / 2)) where radius is null");

                statement.executeUpdate(
                        "create table if not exists territory_trust (" +
                                "territory_id uuid not null references territories(id) on delete cascade, " +
                                "player_uuid uuid not null, " +
                                "player_name text not null, " +
                                "trust_level text not null default 'TRUSTED', " +
                                "created_at timestamptz not null default now(), " +
                                "primary key (territory_id, player_uuid)" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists territory_delete_cooldowns (" +
                                "owner_type text not null, " +
                                "owner_id text not null, " +
                                "deleted_at timestamptz not null default now(), " +
                                "primary key (owner_type, owner_id)" +
                                ")"
                );
                // Existing servers may already have this table from an older build without deleted_at.
                // CREATE TABLE IF NOT EXISTS will not repair that, so keep these as explicit migrations.
                statement.executeUpdate("alter table guilds add column if not exists owner_uuid uuid");
                statement.executeUpdate("alter table guilds add column if not exists owner_profile_id uuid");
                statement.executeUpdate("alter table guilds add column if not exists owner_player_uuid uuid");
                statement.executeUpdate("alter table guild_members add column if not exists player_uuid uuid");
                statement.executeUpdate("alter table guild_members add column if not exists profile_id uuid");

                // Guilds are account-based, not profile-based. Some builds during the SQL profile migration
                // created/altered guild columns as NOT NULL profile columns, which makes normal guild creation
                // fail even when the guild name and tag are available. Repair those schemas here.
                statement.executeUpdate("alter table guilds alter column owner_profile_id drop not null");
                statement.executeUpdate("alter table guilds alter column owner_player_uuid drop not null");
                statement.executeUpdate("alter table guild_members alter column profile_id drop not null");
                statement.executeUpdate("update guilds set owner_uuid = coalesce(owner_uuid, owner_player_uuid) where owner_uuid is null");
                statement.executeUpdate("update guilds set owner_player_uuid = coalesce(owner_player_uuid, owner_uuid) where owner_player_uuid is null");
                statement.executeUpdate("update guild_members set player_uuid = profile_id where player_uuid is null");
                statement.executeUpdate("delete from guild_members where player_uuid is null");
                statement.executeUpdate("alter table guilds alter column owner_uuid set not null");
                statement.executeUpdate("alter table guild_members alter column player_uuid set not null");

                statement.executeUpdate("alter table territory_delete_cooldowns add column if not exists deleted_at timestamptz not null default now()");
                statement.executeUpdate("alter table territory_delete_cooldowns add column if not exists owner_type text");
                statement.executeUpdate("alter table territory_delete_cooldowns add column if not exists owner_id text");
                statement.executeUpdate("delete from territory_delete_cooldowns where owner_type is null or owner_id is null");
                statement.executeUpdate(
                        "delete from territory_delete_cooldowns a using territory_delete_cooldowns b " +
                                "where a.ctid < b.ctid and a.owner_type = b.owner_type and a.owner_id = b.owner_id"
                );
                statement.executeUpdate("alter table territory_delete_cooldowns alter column owner_type set not null");
                statement.executeUpdate("alter table territory_delete_cooldowns alter column owner_id set not null");
                statement.executeUpdate("create unique index if not exists territory_delete_cooldowns_owner_unique on territory_delete_cooldowns (owner_type, owner_id)");

                // Repair territory schemas from older ChampUtils builds. Older DBs may still have an
                // owner_type CHECK constraint that only allowed the pre-profile values, which rejects
                // the current PLAYER/GUILD values and prevents personal territories from being created.
                statement.executeUpdate(
                        "do $$ " +
                                "declare constraint_name text; " +
                                "begin " +
                                "for constraint_name in " +
                                "select c.conname " +
                                "from pg_constraint c " +
                                "join pg_class t on t.oid = c.conrelid " +
                                "join pg_namespace n on n.oid = t.relnamespace " +
                                "where t.relname = 'territories' " +
                                "and n.nspname = current_schema() " +
                                "and c.contype = 'c' " +
                                "and pg_get_constraintdef(c.oid) ilike '%owner_type%' " +
                                "loop " +
                                "execute format('alter table territories drop constraint if exists %I', constraint_name); " +
                                "end loop; " +
                                "end $$"
                );
                statement.executeUpdate("update territories set owner_type = upper(owner_type) where owner_type is not null");
                statement.executeUpdate("update territories set owner_type = 'PLAYER' where owner_type in ('PERSONAL', 'PROFILE', 'USER')");
                statement.executeUpdate("update territories set owner_type = 'GUILD' where owner_type in ('CLAN')");
                statement.executeUpdate("delete from territories where owner_type not in ('PLAYER', 'GUILD')");
                statement.executeUpdate("alter table territories add constraint territories_owner_type_check check (owner_type in ('PLAYER', 'GUILD'))");
                statement.executeUpdate("create unique index if not exists territories_owner_unique on territories (owner_type, owner_id)");

                // Repair territory_trust from older DBs. CREATE TABLE IF NOT EXISTS does not add
                // missing columns to existing tables, so refreshAll() can fail with: column player_uuid does not exist.
                statement.executeUpdate("alter table territory_trust add column if not exists player_uuid uuid");
                statement.executeUpdate("alter table territory_trust add column if not exists player_name text not null default ''");
                statement.executeUpdate("alter table territory_trust add column if not exists trust_level text not null default 'TRUSTED'");
                statement.executeUpdate("alter table territory_trust add column if not exists created_at timestamptz not null default now()");
                statement.executeUpdate(
                        "do $$ " +
                                "begin " +
                                "if exists (select 1 from information_schema.columns where table_schema = current_schema() and table_name = 'territory_trust' and column_name = 'trusted_player_uuid') then " +
                                "execute 'update territory_trust set player_uuid = trusted_player_uuid where player_uuid is null'; " +
                                "end if; " +
                                "if exists (select 1 from information_schema.columns where table_schema = current_schema() and table_name = 'territory_trust' and column_name = 'player_id') then " +
                                "execute 'update territory_trust set player_uuid = player_id where player_uuid is null'; " +
                                "end if; " +
                                "if exists (select 1 from information_schema.columns where table_schema = current_schema() and table_name = 'territory_trust' and column_name = 'trusted_uuid') then " +
                                "execute 'update territory_trust set player_uuid = trusted_uuid where player_uuid is null'; " +
                                "end if; " +
                                "end $$"
                );
                statement.executeUpdate("delete from territory_trust where territory_id is null or player_uuid is null");
                statement.executeUpdate("alter table territory_trust alter column player_uuid set not null");
                statement.executeUpdate("create unique index if not exists territory_trust_territory_player_unique on territory_trust (territory_id, player_uuid)");

                statement.executeUpdate(
                        "create table if not exists player_homes (" +
                                "player_uuid uuid primary key, " +
                                "server_id text not null, " +
                                "world_name text not null, " +
                                "x double precision not null, " +
                                "y double precision not null, " +
                                "z double precision not null, " +
                                "yaw real not null default 0, " +
                                "pitch real not null default 0, " +
                                "updated_at timestamptz not null default now()" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists player_inventories (" +
                                "player_uuid uuid primary key, " +
                                "server_id text not null, " +
                                "inventory_nbt text not null, " +
                                "ender_chest_nbt text not null default '', " +
                                "saved_at timestamptz not null default now(), " +
                                "locked_until timestamptz" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists crate_credits (" +
                                "player_uuid uuid not null, " +
                                "crate_id text not null, " +
                                "credits integer not null default 0, " +
                                "updated_at timestamptz not null default now(), " +
                                "primary key (player_uuid, crate_id)" +
                                ")"
                );

                statement.executeUpdate(
                        "create table if not exists true_caught_dex (" +
                                "player_uuid uuid not null, " +
                                "species_id text not null, " +
                                "caught_at timestamptz not null default now(), " +
                                "primary key (player_uuid, species_id)" +
                                ")"
                );



                statement.executeUpdate(
                        "create table if not exists boss_attempts (" +
                                "boss_type text not null, " +
                                "boss_id uuid not null, " +
                                "player_uuid uuid not null, " +
                                "reset_key_millis bigint not null default 0, " +
                                "player_name text not null default '', " +
                                "attempted_at timestamptz not null default now(), " +
                                "primary key (boss_type, boss_id, player_uuid, reset_key_millis)" +
                                ")"
                );
                statement.executeUpdate("alter table boss_attempts add column if not exists reset_key_millis bigint not null default 0");
                statement.executeUpdate("alter table boss_attempts add column if not exists player_name text not null default ''");
                statement.executeUpdate("alter table boss_attempts add column if not exists attempted_at timestamptz not null default now()");
                statement.executeUpdate("create index if not exists boss_attempts_player_index on boss_attempts (player_uuid)");
                statement.executeUpdate("create index if not exists boss_attempts_reset_index on boss_attempts (reset_key_millis)");

                com.champutils.cosmetic.TitleDatabaseRepository.ensureSchema(connection);
                com.champutils.worldfirst.WorldFirstDatabaseRepository.ensureSchema(connection);

                statement.executeUpdate(
                        "create table if not exists player_settings (" +
                                "player_uuid uuid not null, " +
                                "setting_key text not null, " +
                                "setting_value text not null, " +
                                "updated_at timestamptz not null default now(), " +
                                "primary key (player_uuid, setting_key)" +
                                ")"
                );
            }

            ensured = true;
            System.out.println("[ChampUtils] Network-ready database schemas are ready.");
        });
    }
}
