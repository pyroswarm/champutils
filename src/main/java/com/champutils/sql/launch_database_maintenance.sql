-- Cobble Champs launch database maintenance / retention.
-- Safe to run manually. It only prunes crash-recovery/snapshot/history rows, not live player values.

alter table if exists public.profile_save_generations add column if not exists started_at timestamptz not null default now();
alter table if exists public.profile_save_generations add column if not exists committed_at timestamptz;
alter table if exists public.profile_atomic_snapshots add column if not exists created_at timestamptz not null default now();
alter table if exists public.profile_atomic_snapshots add column if not exists completed_at timestamptz;
alter table if exists public.profile_battle_recovery add column if not exists started_at timestamptz not null default now();
alter table if exists public.profile_battle_recovery add column if not exists last_heartbeat timestamptz not null default now();
alter table if exists public.profile_battle_recovery add column if not exists ended_at timestamptz;
alter table if exists public.profile_battle_recovery_events add column if not exists event_at timestamptz not null default now();

create index if not exists idx_profile_atomic_snapshots_created on public.profile_atomic_snapshots(profile_id, snapshot_type, created_at desc);
create index if not exists idx_profile_atomic_snapshots_status_created on public.profile_atomic_snapshots(status, created_at);
create index if not exists idx_profile_save_generations_profile_started on public.profile_save_generations(profile_id, started_at desc);
create index if not exists idx_profile_save_generations_state_started on public.profile_save_generations(state, started_at);
create index if not exists idx_profile_battle_recovery_status_started on public.profile_battle_recovery(status, started_at);
create index if not exists idx_profile_battle_recovery_ended on public.profile_battle_recovery(ended_at);
create index if not exists idx_profile_battle_recovery_events_event_at on public.profile_battle_recovery_events(event_at);

-- Dead interrupted saves.
delete from public.profile_atomic_snapshots
where status = 'PENDING'
  and created_at < now() - interval '1 day';

-- Keep only the latest COMPLETE snapshot per profile/type; live state is stored separately.
with ranked as (
  select id,
         row_number() over (
           partition by profile_id, snapshot_type
           order by completed_at desc nulls last, created_at desc
         ) as rn
  from public.profile_atomic_snapshots
  where status = 'COMPLETE'
)
delete from public.profile_atomic_snapshots s
using ranked r
where s.id = r.id
  and r.rn > 1;

-- Save-generation metadata retention.
with ranked as (
  select id,
         state,
         started_at,
         coalesce(committed_at, started_at) as effective_at,
         row_number() over (
           partition by profile_id
           order by coalesce(committed_at, started_at) desc, started_at desc
         ) as rn
  from public.profile_save_generations
  where state <> 'OPEN'
)
delete from public.profile_save_generations g
using ranked r
where g.id = r.id
  and (r.rn > 20 or r.effective_at < now() - interval '14 days');

update public.profile_save_generations
set state = 'ABORTED',
    metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('auto_aborted_by', 'manual_maintenance')
where state = 'OPEN'
  and started_at < now() - interval '1 day';

-- Battle recovery retention.
update public.profile_battle_recovery
set status = 'EXPIRED',
    ended_at = now(),
    metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('expired_by', 'manual_maintenance')
where status = 'OPEN'
  and last_heartbeat < now() - interval '6 hours';

delete from public.profile_battle_recovery
where status <> 'OPEN'
  and coalesce(ended_at, started_at) < now() - interval '7 days';

delete from public.profile_battle_recovery_events e
where event_at < now() - interval '7 days'
  and not exists (
    select 1 from public.profile_battle_recovery r where r.id = e.recovery_id
  );
