-- Cobble Champs beta-critical booster/profile/moderation support
-- Safe to run multiple times.

create table if not exists profile_player_stats (
    profile_id uuid primary key references player_profiles(id) on delete cascade,
    playtime_seconds bigint not null default 0,
    money numeric(18,2) not null default 0,
    battling_xp bigint not null default 0,
    battling_level integer not null default 1,
    total_level integer not null default 1,
    metadata jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

alter table profile_player_stats add column if not exists playtime_seconds bigint not null default 0;
create index if not exists idx_profile_player_stats_playtime on profile_player_stats(playtime_seconds);

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

alter table moderation_actions add column if not exists profile_id uuid null;
alter table moderation_actions add column if not exists account_uuid uuid null;
alter table moderation_actions add column if not exists moderator_uuid uuid null;
alter table moderation_actions add column if not exists moderator_name text not null default 'Console';
alter table moderation_actions add column if not exists target_name text not null default '';
alter table moderation_actions add column if not exists action_type text not null default 'WARN';
alter table moderation_actions add column if not exists reason text not null default '';
alter table moderation_actions add column if not exists issued_at timestamptz not null default now();
alter table moderation_actions add column if not exists expires_at timestamptz null;
alter table moderation_actions add column if not exists revoked_at timestamptz null;
alter table moderation_actions add column if not exists revoked_by uuid null;
alter table moderation_actions add column if not exists server_name text not null default '';
alter table moderation_actions add column if not exists active boolean not null default true;
alter table moderation_actions add column if not exists metadata jsonb not null default '{}'::jsonb;

create index if not exists idx_moderation_actions_account on moderation_actions(account_uuid);
create index if not exists idx_moderation_actions_profile on moderation_actions(profile_id);
create index if not exists idx_moderation_actions_target_lower on moderation_actions(lower(target_name));
create index if not exists idx_moderation_actions_active_type on moderation_actions(action_type, active, expires_at);
create index if not exists idx_moderation_actions_issued on moderation_actions(issued_at desc);
