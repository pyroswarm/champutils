-- ChampUtils / CobbleChamps SQL performance migration
-- Generated from the 2026-07-13 cluster dump.
-- Safe to run once in the Supabase SQL editor against the current database.
-- This intentionally does not include roles, passwords, data, or a full cluster restore.

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('champutils_sql_performance_2026_07_13'));
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

-- Remove redundant indexes. Some are exact duplicates; the remainder are fully
-- covered by a retained primary-key, unique, or equivalent lookup index.
DROP INDEX IF EXISTS public.guild_create_cooldowns_player_uuid_unique;
DROP INDEX IF EXISTS public.guild_members_guild_player_unique;
DROP INDEX IF EXISTS public.idx_guild_members_player;
DROP INDEX IF EXISTS public.idx_guild_members_profile;
DROP INDEX IF EXISTS public.network_events_id_idx;
DROP INDEX IF EXISTS public.player_accounts_minecraft_uuid_unique;
DROP INDEX IF EXISTS public.idx_player_active_profiles_player;
DROP INDEX IF EXISTS public.idx_profiles_player;
DROP INDEX IF EXISTS public.idx_player_profiles_player_name_live;
DROP INDEX IF EXISTS public.profile_badge_unlocks_profile_type_key_uidx;
DROP INDEX IF EXISTS public.idx_profile_badges_profile_badge;
DROP INDEX IF EXISTS public.idx_profile_catch_streaks_profile;
DROP INDEX IF EXISTS public.idx_profile_cobblemon_party_profile;
DROP INDEX IF EXISTS public.idx_profile_cobblemon_pc_profile;
DROP INDEX IF EXISTS public.idx_profile_cobblemon_storage_profile;
DROP INDEX IF EXISTS public.idx_profile_inventory_snapshots_profile;
DROP INDEX IF EXISTS public.idx_profile_loot_claims_location_lookup;
DROP INDEX IF EXISTS public.idx_nuzlocke_graveyard_species;
DROP INDEX IF EXISTS public.idx_profile_nuzlocke_species_profile_species;
DROP INDEX IF EXISTS public.idx_profile_pastures_profile;
DROP INDEX IF EXISTS public.idx_profile_player_nbt_profile;
DROP INDEX IF EXISTS public.idx_profile_ranked_stats_profile_season;
DROP INDEX IF EXISTS public.idx_profile_statistics_profile;
DROP INDEX IF EXISTS public.idx_profile_transfer_audit_profile_created;
DROP INDEX IF EXISTS public.idx_profile_trinket_pouches_profile;
DROP INDEX IF EXISTS public.idx_profile_vanilla_state_profile;
DROP INDEX IF EXISTS public.true_caught_dex_player_uuid_idx;
DROP INDEX IF EXISTS public.idx_wondertrade_pending_claims_player_uuid;
DROP INDEX IF EXISTS public.idx_wondertrade_pending_claims_profile_id_unique;

-- Add missing leading-column indexes for foreign keys. These reduce full-table scans
-- during profile/account deletion cascades and improve the corresponding joins.
CREATE INDEX IF NOT EXISTS idx_account_links_player_uuid_fk ON public.account_links (player_uuid);
CREATE INDEX IF NOT EXISTS idx_auction_listings_buyer_profile_id_fk ON public.auction_listings (buyer_profile_id);
CREATE INDEX IF NOT EXISTS idx_auction_listings_seller_player_uuid_fk ON public.auction_listings (seller_player_uuid);
CREATE INDEX IF NOT EXISTS idx_auction_purchases_listing_id_fk ON public.auction_purchases (listing_id);
CREATE INDEX IF NOT EXISTS idx_chest_shops_owner_player_uuid_fk ON public.chest_shops (owner_player_uuid);
CREATE INDEX IF NOT EXISTS idx_chest_shops_owner_profile_id_fk ON public.chest_shops (owner_profile_id);
CREATE INDEX IF NOT EXISTS idx_guild_invites_invited_by_profile_id_fk ON public.guild_invites (invited_by_profile_id);
CREATE INDEX IF NOT EXISTS idx_guild_invites_invited_player_uuid_fk ON public.guild_invites (invited_player_uuid);
CREATE INDEX IF NOT EXISTS idx_guild_invites_invited_profile_id_fk ON public.guild_invites (invited_profile_id);
CREATE INDEX IF NOT EXISTS idx_guild_player_contract_xp_awards_completer_profile_id_fk ON public.guild_player_contract_xp_awards (completer_profile_id);
CREATE INDEX IF NOT EXISTS idx_guild_player_contracts_completer_profile_id_fk ON public.guild_player_contracts (completer_profile_id);
CREATE INDEX IF NOT EXISTS idx_guild_xp_log_guild_id_fk ON public.guild_xp_log (guild_id);
CREATE INDEX IF NOT EXISTS idx_guild_xp_log_profile_id_fk ON public.guild_xp_log (profile_id);
CREATE INDEX IF NOT EXISTS idx_guilds_owner_player_uuid_fk ON public.guilds (owner_player_uuid);
CREATE INDEX IF NOT EXISTS idx_guilds_owner_profile_id_fk ON public.guilds (owner_profile_id);
CREATE INDEX IF NOT EXISTS idx_moderation_events_player_uuid_fk ON public.moderation_events (player_uuid);
CREATE INDEX IF NOT EXISTS idx_profile_atomic_snapshots_player_uuid_fk ON public.profile_atomic_snapshots (player_uuid);
CREATE INDEX IF NOT EXISTS idx_profile_battle_recovery_events_recovery_id_fk ON public.profile_battle_recovery_events (recovery_id);
CREATE INDEX IF NOT EXISTS idx_profile_battle_recovery_post_battle_generation_fk ON public.profile_battle_recovery (post_battle_generation);
CREATE INDEX IF NOT EXISTS idx_profile_battle_recovery_pre_battle_generation_fk ON public.profile_battle_recovery (pre_battle_generation);
CREATE INDEX IF NOT EXISTS idx_profile_battle_recovery_profile_id_fk ON public.profile_battle_recovery (profile_id);
CREATE INDEX IF NOT EXISTS idx_profile_cobblemon_state_player_uuid_fk ON public.profile_cobblemon_state (player_uuid);
CREATE INDEX IF NOT EXISTS idx_profile_land_claim_members_added_by_profile_id_fk ON public.profile_land_claim_members (added_by_profile_id);
CREATE INDEX IF NOT EXISTS idx_profile_land_claims_player_uuid_fk ON public.profile_land_claims (player_uuid);
CREATE INDEX IF NOT EXISTS idx_profile_save_generations_player_uuid_fk ON public.profile_save_generations (player_uuid);
CREATE INDEX IF NOT EXISTS idx_profile_transfer_audit_logs_save_generation_fk ON public.profile_transfer_audit_logs (save_generation);
CREATE INDEX IF NOT EXISTS idx_profile_transfer_tokens_player_uuid_fk ON public.profile_transfer_tokens (player_uuid);
CREATE INDEX IF NOT EXISTS idx_profile_transfer_tokens_profile_id_fk ON public.profile_transfer_tokens (profile_id);
CREATE INDEX IF NOT EXISTS idx_server_global_buffs_activated_by_profile_id_fk ON public.server_global_buffs (activated_by_profile_id);
CREATE INDEX IF NOT EXISTS idx_territory_delete_cooldowns_owner_guild_id_fk ON public.territory_delete_cooldowns (owner_guild_id);
CREATE INDEX IF NOT EXISTS idx_territory_delete_cooldowns_owner_profile_id_fk ON public.territory_delete_cooldowns (owner_profile_id);
CREATE INDEX IF NOT EXISTS idx_territory_trust_trusted_player_uuid_fk ON public.territory_trust (trusted_player_uuid);
CREATE INDEX IF NOT EXISTS idx_territory_trust_trusted_profile_id_fk ON public.territory_trust (trusted_profile_id);
CREATE INDEX IF NOT EXISTS idx_territory_upvotes_player_uuid_fk ON public.territory_upvotes (player_uuid);
CREATE INDEX IF NOT EXISTS idx_webstore_claims_player_uuid_fk ON public.webstore_claims (player_uuid);
CREATE INDEX IF NOT EXISTS idx_webstore_claims_profile_id_fk ON public.webstore_claims (profile_id);
CREATE INDEX IF NOT EXISTS idx_wondertrade_history_player_uuid_fk ON public.wondertrade_history (player_uuid);
CREATE INDEX IF NOT EXISTS idx_wondertrade_history_profile_id_fk ON public.wondertrade_history (profile_id);
CREATE INDEX IF NOT EXISTS idx_wondertrade_pool_owner_player_uuid_fk ON public.wondertrade_pool (owner_player_uuid);
CREATE INDEX IF NOT EXISTS idx_wondertrade_pool_owner_profile_id_fk ON public.wondertrade_pool (owner_profile_id);
CREATE INDEX IF NOT EXISTS idx_world_first_claims_player_uuid_fk ON public.world_first_claims (player_uuid);
CREATE INDEX IF NOT EXISTS idx_world_firsts_winner_player_uuid_fk ON public.world_firsts (winner_player_uuid);
CREATE INDEX IF NOT EXISTS idx_world_firsts_winner_profile_id_fk ON public.world_firsts (winner_profile_id);

-- Make auth.uid() an init-plan value instead of evaluating it once per candidate row.
ALTER POLICY "Users can delete own pending account links" ON public.account_links
    USING ((website_user_id = (SELECT auth.uid())) AND (verified = false));
ALTER POLICY "Users can insert own account links" ON public.account_links
    WITH CHECK (website_user_id = (SELECT auth.uid()));
ALTER POLICY "Users can read own account links" ON public.account_links
    USING (website_user_id = (SELECT auth.uid()));
ALTER POLICY "Users can read own player account" ON public.player_accounts
    USING (website_user_id = (SELECT auth.uid()));
ALTER POLICY "Users can read own profile" ON public.profiles
    USING (id = (SELECT auth.uid()));
ALTER POLICY "Users can read their own website role" ON public.web_user_roles
    USING ((SELECT auth.uid()) = user_id);
ALTER POLICY "Users can update own account links" ON public.account_links
    USING (website_user_id = (SELECT auth.uid()))
    WITH CHECK (website_user_id = (SELECT auth.uid()));
ALTER POLICY "Users can update own profile" ON public.profiles
    USING (id = (SELECT auth.uid()))
    WITH CHECK (id = (SELECT auth.uid()));
ALTER POLICY "admins can insert site settings" ON public.site_settings
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.profiles
        WHERE profiles.id = (SELECT auth.uid()) AND profiles.role = 'admin'
    ));
ALTER POLICY "admins can update site settings" ON public.site_settings
    USING (EXISTS (
        SELECT 1 FROM public.profiles
        WHERE profiles.id = (SELECT auth.uid()) AND profiles.role = 'admin'
    ))
    WITH CHECK (EXISTS (
        SELECT 1 FROM public.profiles
        WHERE profiles.id = (SELECT auth.uid()) AND profiles.role = 'admin'
    ));
ALTER POLICY players_read_own_cosmetic_settings ON public.account_cosmetic_settings
    USING (EXISTS (
        SELECT 1 FROM public.player_accounts pa
        WHERE pa.minecraft_uuid = account_cosmetic_settings.account_uuid
          AND pa.website_user_id = (SELECT auth.uid())
    ));
ALTER POLICY players_read_own_cosmetic_unlocks ON public.account_cosmetic_unlocks
    USING (EXISTS (
        SELECT 1 FROM public.player_accounts pa
        WHERE pa.minecraft_uuid = account_cosmetic_unlocks.account_uuid
          AND pa.website_user_id = (SELECT auth.uid())
    ));
ALTER POLICY players_read_own_purchase_ledger ON public.account_purchase_ledger
    USING (EXISTS (
        SELECT 1 FROM public.player_accounts pa
        WHERE pa.minecraft_uuid = account_purchase_ledger.account_uuid
          AND pa.website_user_id = (SELECT auth.uid())
    ));
ALTER POLICY players_read_own_vote_balance ON public.account_vote_balances
    USING (EXISTS (
        SELECT 1 FROM public.player_accounts pa
        WHERE pa.minecraft_uuid = account_vote_balances.account_uuid
          AND pa.website_user_id = (SELECT auth.uid())
    ));
ALTER POLICY players_read_own_vote_ledger ON public.account_vote_ledger
    USING (EXISTS (
        SELECT 1 FROM public.player_accounts pa
        WHERE pa.minecraft_uuid = account_vote_ledger.account_uuid
          AND pa.website_user_id = (SELECT auth.uid())
    ));

COMMIT;

-- Refresh planner statistics after index changes. ANALYZE is intentionally outside the
-- transaction so each table can be processed independently.
ANALYZE public.player_profiles;
ANALYZE public.profile_vanilla_state;
ANALYZE public.profile_atomic_snapshots;
ANALYZE public.profile_transfer_tokens;
ANALYZE public.profile_transfer_audit_logs;
ANALYZE public.profile_cobblemon_storage;
ANALYZE public.guild_members;
ANALYZE public.guilds;
ANALYZE public.auction_listings;
ANALYZE public.wondertrade_pending_claims;
ANALYZE public.world_firsts;
