-- Cobble Champs database polish pass - 2026-07-06.
-- Safe intent: tighten public grants, remove duplicate indexes, and keep recovery/snapshot
-- cleanup aligned with the current schema. This does not delete live player state.

create or replace function public.cleanup_profile_recovery_snapshots()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if to_regclass('public.profile_atomic_snapshots') is not null then
    delete from public.profile_atomic_snapshots
    where status = 'PENDING'
      and created_at < now() - interval '1 day';

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

    delete from public.profile_atomic_snapshots
    where status in ('FAILED', 'ABANDONED')
      and created_at < now() - interval '7 days';
  end if;

  if to_regclass('public.profile_save_generations') is not null then
    update public.profile_save_generations
    set state = 'ABORTED',
        aborted_at = coalesce(aborted_at, now()),
        metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('auto_aborted_by', 'db_polish_cleanup')
    where state = 'OPEN'
      and coalesce(heartbeat_at, started_at, opened_at) < now() - interval '1 day';

    with ranked as (
      select id,
             state,
             coalesce(committed_at, completed_at, aborted_at, started_at, opened_at) as effective_at,
             row_number() over (
               partition by profile_id
               order by coalesce(committed_at, completed_at, aborted_at, started_at, opened_at) desc
             ) as rn
      from public.profile_save_generations
      where state <> 'OPEN'
    )
    delete from public.profile_save_generations g
    using ranked r
    where g.id = r.id
      and (r.rn > 20 or r.effective_at < now() - interval '14 days');
  end if;

  if to_regclass('public.profile_battle_recovery') is not null then
    update public.profile_battle_recovery
    set status = 'EXPIRED',
        ended_at = coalesce(ended_at, now()),
        metadata = coalesce(metadata, '{}'::jsonb) || jsonb_build_object('expired_by', 'db_polish_cleanup')
    where status = 'OPEN'
      and coalesce(last_heartbeat, started_at) < now() - interval '6 hours';

    delete from public.profile_battle_recovery
    where status <> 'OPEN'
      and coalesce(ended_at, started_at) < now() - interval '7 days';
  end if;

  if to_regclass('public.profile_battle_recovery_events') is not null then
    delete from public.profile_battle_recovery_events e
    where event_at < now() - interval '7 days'
      and not exists (
        select 1
        from public.profile_battle_recovery r
        where r.id = e.recovery_id
      );
  end if;
end;
$$;

-- Note: do not update cron.job here. Supabase commonly blocks direct cron.job writes
-- from the SQL editor role. The existing job already calls this function by name, so
-- replacing the function above is enough. If the job is missing, recreate it from the
-- Supabase dashboard or run cron.schedule from a privileged SQL session.

-- Remove duplicate indexes that add write overhead without improving lookups.
drop index if exists public.idx_profile_atomic_snapshots_latest_complete;
drop index if exists public.idx_auction_listings_status_created_at;
drop index if exists public.idx_player_active_profiles_player_uuid;
drop index if exists public.idx_professions_profession;

-- Tighten public grants. RLS is enabled, but broad table grants still expand the blast radius.
revoke all on table public.auction_claims from anon, authenticated;
revoke all on table public.auction_purchases from anon, authenticated;
revoke all on table public.profile_atomic_snapshots from anon, authenticated;
revoke all on table public.profile_save_generations from anon, authenticated;
revoke all on table public.profile_battle_recovery from anon, authenticated;
revoke all on table public.profile_battle_recovery_events from anon, authenticated;
revoke all on sequence public.profile_battle_recovery_events_id_seq from anon, authenticated;

revoke all on table public.auction_listings from anon, authenticated;
grant select on table public.auction_listings to anon, authenticated;

grant all on table public.auction_claims to service_role;
grant all on table public.auction_listings to service_role;
grant all on table public.auction_purchases to service_role;
grant all on table public.profile_atomic_snapshots to service_role;
grant all on table public.profile_save_generations to service_role;
grant all on table public.profile_battle_recovery to service_role;
grant all on table public.profile_battle_recovery_events to service_role;
grant all on sequence public.profile_battle_recovery_events_id_seq to service_role;

revoke all on function public.buy_auction_listing(uuid) from anon;
revoke all on function public.buy_auction_listing(uuid, uuid) from anon;
revoke all on function public.create_auction_sale_notification() from anon, authenticated;
grant execute on function public.buy_auction_listing(uuid) to authenticated, service_role;
grant execute on function public.buy_auction_listing(uuid, uuid) to authenticated, service_role;
grant execute on function public.create_auction_sale_notification() to service_role;
