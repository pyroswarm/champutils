-- ChampUtils multi-survival shared-state migration
-- Safe to run more than once.

create table if not exists network_events (
    id bigserial primary key,
    event_type text not null,
    scope text not null default 'GLOBAL',
    origin_server_id text not null,
    origin_player_uuid uuid,
    origin_player_name text not null default '',
    message text not null default '',
    payload jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '10 minutes')
);

create index if not exists network_events_id_idx on network_events (id);
create index if not exists network_events_expires_idx on network_events (expires_at);
create index if not exists network_events_type_scope_idx on network_events (event_type, scope, id);

create table if not exists profile_json_state (
    profile_id uuid not null,
    state_key text not null,
    payload text not null default '{}',
    version bigint not null default 0,
    updated_at timestamptz not null default now(),
    primary key (profile_id, state_key)
);

alter table profile_json_state add column if not exists version bigint not null default 0;

create index if not exists profile_json_state_updated_idx
    on profile_json_state (state_key, updated_at desc);

create table if not exists player_json_state (
    player_uuid uuid not null,
    state_key text not null,
    payload text not null default '{}',
    version bigint not null default 0,
    updated_at timestamptz not null default now(),
    primary key (player_uuid, state_key)
);

alter table player_json_state add column if not exists version bigint not null default 0;

create index if not exists player_json_state_updated_idx
    on player_json_state (state_key, updated_at desc);

create table if not exists global_json_state (
    state_key text primary key,
    payload text not null default '{}',
    version bigint not null default 0,
    updated_at timestamptz not null default now()
);

alter table global_json_state add column if not exists version bigint not null default 0;

create table if not exists player_economy (
    uuid text primary key,
    username text not null,
    credits bigint not null default 0,
    lifetime_earned bigint not null default 0,
    lifetime_spent bigint not null default 0,
    updated_at timestamptz not null default now()
);

create table if not exists economy_ledger (
    id uuid primary key,
    transfer_id uuid,
    uuid text,
    username text not null default '',
    type text not null,
    amount bigint not null default 0,
    balance_after bigint not null default 0,
    reason text not null default 'unspecified',
    server_id text not null default '',
    created_at timestamptz not null default now()
);

create index if not exists economy_ledger_uuid_created_idx
    on economy_ledger (uuid, created_at desc);

create table if not exists true_caught_dex (
    player_uuid uuid not null,
    species_id text not null,
    caught_at timestamptz not null default now(),
    primary key (player_uuid, species_id)
);

create index if not exists true_caught_dex_player_idx
    on true_caught_dex (player_uuid);

create table if not exists moderation_actions (
    id uuid primary key,
    profile_id uuid null,
    account_uuid uuid null,
    moderator_uuid uuid null,
    moderator_name text not null default 'Console',
    target_name text not null default '',
    action_type text not null default 'WARN',
    reason text not null default '',
    issued_at timestamptz not null default now(),
    expires_at timestamptz null,
    revoked_at timestamptz null,
    revoked_by uuid null,
    server_name text not null default '',
    active boolean not null default true,
    metadata jsonb not null default '{}'::jsonb
);

create index if not exists idx_moderation_actions_account
    on moderation_actions(account_uuid);

create index if not exists idx_moderation_actions_profile
    on moderation_actions(profile_id);

create index if not exists idx_moderation_actions_target_lower
    on moderation_actions(lower(target_name));

create index if not exists idx_moderation_actions_active_type
    on moderation_actions(action_type, active, expires_at);

create index if not exists idx_moderation_actions_active_account_type
    on moderation_actions(account_uuid, action_type, issued_at desc)
    where active = true and revoked_at is null;

create index if not exists idx_moderation_actions_active_target_type
    on moderation_actions(lower(target_name), action_type, issued_at desc)
    where active = true and revoked_at is null;

create index if not exists idx_moderation_actions_warn_daily_account
    on moderation_actions(account_uuid, issued_at desc)
    where action_type = 'WARN';

create index if not exists idx_moderation_actions_warn_daily_target
    on moderation_actions(lower(target_name), issued_at desc)
    where action_type = 'WARN';

create index if not exists idx_moderation_actions_issued
    on moderation_actions(issued_at desc);

create index if not exists server_nodes_role_heartbeat_idx
    on server_nodes (server_role, last_heartbeat desc);

create index if not exists server_nodes_survival_capacity_idx
    on server_nodes (server_role, online_players, last_heartbeat desc);

create table if not exists global_matchmaking_queue (
    player_uuid uuid primary key,
    profile_id uuid,
    player_name text not null default '',
    queue_type text not null,
    source_server_id text not null,
    priority integer not null default 0,
    rp integer not null default 1000,
    rank_index integer not null default 0,
    team_snapshot jsonb not null default '{}'::jsonb,
    status text not null default 'QUEUED',
    queued_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '10 minutes')
);

alter table global_matchmaking_queue add column if not exists rp integer not null default 1000;
alter table global_matchmaking_queue add column if not exists rank_index integer not null default 0;

create index if not exists global_matchmaking_queue_pick_idx
    on global_matchmaking_queue (queue_type, status, priority desc, queued_at)
    where status = 'QUEUED';

create table if not exists global_matchmaking_sessions (
    id uuid primary key default gen_random_uuid(),
    queue_type text not null,
    player_one_uuid uuid not null,
    player_one_name text not null default '',
    player_two_uuid uuid not null,
    player_two_name text not null default '',
    battle_server_id text not null,
    status text not null default 'PENDING_ACCEPT',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '3 minutes'),
    started_at timestamptz
);

alter table global_matchmaking_sessions add column if not exists player_one_name text not null default '';
alter table global_matchmaking_sessions add column if not exists player_two_name text not null default '';
alter table global_matchmaking_sessions add column if not exists started_at timestamptz;

create index if not exists global_matchmaking_sessions_player_idx
    on global_matchmaking_sessions (player_one_uuid, player_two_uuid, status);

create table if not exists global_matchmaking_acceptances (
    session_id uuid not null references global_matchmaking_sessions(id) on delete cascade,
    player_uuid uuid not null,
    accepted boolean not null default false,
    responded_at timestamptz,
    primary key (session_id, player_uuid)
);

create table if not exists profile_homes (
    profile_id uuid not null,
    home_name text not null,
    server_id text not null,
    world_name text not null,
    x double precision not null,
    y double precision not null,
    z double precision not null,
    yaw real not null default 0,
    pitch real not null default 0,
    updated_at timestamptz not null default now(),
    primary key (profile_id, home_name)
);

create table if not exists profile_reward_claims (
    profile_id uuid not null,
    reward_type text not null,
    reward_key text not null,
    claimed_at timestamptz not null default now(),
    primary key (profile_id, reward_type, reward_key)
);

create table if not exists profile_catch_streaks (
    profile_id uuid not null,
    streak_key text not null,
    streak_count integer not null default 0,
    best_streak integer not null default 0,
    updated_at timestamptz not null default now(),
    primary key (profile_id, streak_key)
);

create table if not exists profile_daily_login_state (
    profile_id uuid primary key,
    streak_days integer not null default 0,
    last_claim_date date,
    updated_at timestamptz not null default now()
);

create table if not exists pokemon_hunt_cycles (
    id uuid primary key default gen_random_uuid(),
    hunt_key text not null unique,
    species_id text not null,
    required_count integer not null default 1,
    reward_payload jsonb not null default '{}'::jsonb,
    starts_at timestamptz not null default now(),
    ends_at timestamptz not null,
    status text not null default 'ACTIVE',
    updated_at timestamptz not null default now()
);

create table if not exists pokemon_hunt_entries (
    cycle_id uuid not null references pokemon_hunt_cycles(id) on delete cascade,
    profile_id uuid not null,
    player_uuid uuid not null,
    player_name text not null default '',
    progress integer not null default 0,
    completed_at timestamptz,
    updated_at timestamptz not null default now(),
    primary key (cycle_id, profile_id)
);

create table if not exists pokemon_hunt_claims (
    cycle_id uuid not null references pokemon_hunt_cycles(id) on delete cascade,
    profile_id uuid not null,
    claimed_at timestamptz not null default now(),
    primary key (cycle_id, profile_id)
);

delete from network_events where expires_at < now() - interval '1 hour';
