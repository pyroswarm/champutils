package com.champutils.rank;

import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class RankedTokenManager {
    private static final Map<UUID, Long> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicLong> REVISIONS = new ConcurrentHashMap<>();
    private RankedTokenManager() {}

    public static void register() {
        RankedTokenConfig.load();
        ensureSchemaAsync();
        RankedMatchRewardManager.ensureSchemaAsync();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> loadAsync(handler.player));
    }

    public static long cachedBalance(ServerPlayer player) { return player == null ? 0L : CACHE.getOrDefault(PlayerProfileManager.activeProfileId(player), 0L); }

    private static long revision(UUID profile) {
        return REVISIONS.computeIfAbsent(profile, ignored -> new AtomicLong()).get();
    }

    private static long markMutation(UUID profile) {
        return REVISIONS.computeIfAbsent(profile, ignored -> new AtomicLong()).incrementAndGet();
    }

    public static void loadAsync(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profile = PlayerProfileManager.activeProfileId(player);
        long expectedRevision = revision(profile);
        DatabaseManager.executeAsync("load ranked tokens " + profile, connection -> {
            long loaded = 0L;
            try (var ps = connection.prepareStatement("select tokens from ranked_token_balances where profile_uuid = ?")) {
                ps.setObject(1, profile);
                try (var rs = ps.executeQuery()) { if (rs.next()) loaded = Math.max(0L, rs.getLong(1)); }
            }
            // Never allow a delayed read to overwrite a newer grant/spend, and never apply
            // a result to a player who changed profiles while the query was running.
            if (revision(profile) == expectedRevision && profile.equals(PlayerProfileManager.activeProfileId(player))) {
                CACHE.put(profile, loaded);
            }
        });
    }

    public static void awardRankedVictory(ServerPlayer winner, ServerPlayer loser) {
        if (winner == null || loser == null) return;
        UUID winnerProfile = PlayerProfileManager.activeProfileId(winner);
        UUID loserProfile = PlayerProfileManager.activeProfileId(loser);
        int amount = Math.max(1, RankedTokenConfig.CONFIG.tokensPerRankedWin);
        if (!DatabaseManager.isEnabled()) {
            CACHE.merge(winnerProfile, (long) amount, Long::sum);
            winner.sendSystemMessage(Component.literal("§d+" + amount + " Ranked Token" + (amount == 1 ? "" : "s") + "§7."));
            return;
        }
        DatabaseManager.executeAsync("award ranked token " + winnerProfile, connection -> {
            int cap = Math.max(1, RankedTokenConfig.CONFIG.dailyTokenCap);
            try (var daily = connection.prepareStatement("select coalesce(sum(tokens),0) from ranked_token_ledger where winner_profile_uuid = ? and rewarded_at >= now() - interval '24 hours'")) {
                daily.setObject(1, winnerProfile);
                try (var rs = daily.executeQuery()) { if (rs.next() && rs.getInt(1) >= cap) return; }
            }
            // Every legitimate ranked victory is token-eligible, including consecutive
            // matches against the same opponent. The daily token cap remains the only
            // repeat-award limit here; immediate fake forfeits are filtered upstream.
            try (var up = connection.prepareStatement("insert into ranked_token_balances(profile_uuid,tokens,updated_at) values(?,?,now()) on conflict(profile_uuid) do update set tokens = ranked_token_balances.tokens + excluded.tokens, updated_at = now() returning tokens")) {
                up.setObject(1, winnerProfile); up.setLong(2, amount);
                try (var rs = up.executeQuery()) { if (rs.next()) CACHE.put(winnerProfile, rs.getLong(1)); }
            }
            try (var led = connection.prepareStatement("insert into ranked_token_ledger(winner_profile_uuid, opponent_profile_uuid, tokens, rewarded_at) values(?,?,?,now())")) {
                led.setObject(1, winnerProfile); led.setObject(2, loserProfile); led.setInt(3, amount); led.executeUpdate();
            }
            if (winner.getServer() != null) winner.getServer().execute(() -> winner.sendSystemMessage(Component.literal("§d+" + amount + " Ranked Token" + (amount == 1 ? "" : "s") + "§7.")));
        });
    }

    public static void grant(ServerPlayer player, int amount, String reason) {
        if (player == null || amount <= 0) return;
        UUID profile = PlayerProfileManager.activeProfileId(player);
        long mutationRevision = markMutation(profile);
        CACHE.merge(profile, (long) amount, Long::sum);
        if (DatabaseManager.isEnabled()) DatabaseManager.executeAsync("grant ranked tokens " + profile, connection -> {
            try (var up = connection.prepareStatement("insert into ranked_token_balances(profile_uuid,tokens,updated_at) values(?,?,now()) on conflict(profile_uuid) do update set tokens = ranked_token_balances.tokens + excluded.tokens, updated_at = now() returning tokens")) {
                up.setObject(1, profile);
                up.setLong(2, amount);
                try (var rs = up.executeQuery()) {
                    if (rs.next() && revision(profile) == mutationRevision) CACHE.put(profile, Math.max(0L, rs.getLong(1)));
                }
            }
        });
        player.sendSystemMessage(Component.literal("§d+" + amount + " Ranked Token" + (amount == 1 ? "" : "s") + (reason == null || reason.isBlank() ? "" : " §7(" + reason + ")") + "§7."));
    }

    public static void take(ServerPlayer player, int amount, String reason) {
        if (player == null || amount <= 0) return;
        UUID profile = PlayerProfileManager.activeProfileId(player);
        long mutationRevision = markMutation(profile);
        CACHE.compute(profile, (ignored, current) -> Math.max(0L, (current == null ? 0L : current) - amount));
        if (DatabaseManager.isEnabled()) DatabaseManager.executeAsync("take ranked tokens " + profile, connection -> {
            try (var up = connection.prepareStatement("insert into ranked_token_balances(profile_uuid,tokens,updated_at) values(?,0,now()) on conflict(profile_uuid) do update set tokens = greatest(0, ranked_token_balances.tokens - ?), updated_at = now() returning tokens")) {
                up.setObject(1, profile);
                up.setLong(2, amount);
                try (var rs = up.executeQuery()) {
                    if (rs.next() && revision(profile) == mutationRevision) CACHE.put(profile, Math.max(0L, rs.getLong(1)));
                }
            }
        });
        player.sendSystemMessage(Component.literal("§c-" + amount + " Ranked Token" + (amount == 1 ? "" : "s") + (reason == null || reason.isBlank() ? "" : " §7(" + reason + ")") + "§7."));
    }

    public static boolean spend(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return false;
        UUID profile = PlayerProfileManager.activeProfileId(player);
        long current = CACHE.getOrDefault(profile, 0L);
        if (current < amount) return false;
        markMutation(profile);
        CACHE.put(profile, current - amount);
        if (DatabaseManager.isEnabled()) DatabaseManager.executeAsync("spend ranked tokens " + profile, connection -> {
            try (var ps = connection.prepareStatement("update ranked_token_balances set tokens = greatest(0, tokens - ?), updated_at = now() where profile_uuid = ?")) { ps.setInt(1, amount); ps.setObject(2, profile); ps.executeUpdate(); }
        });
        return true;
    }

    public static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ranked token schema", connection -> {
            try (var st = connection.createStatement()) {
                st.executeUpdate("create table if not exists ranked_token_balances (profile_uuid uuid primary key, tokens bigint not null default 0, updated_at timestamptz not null default now())");
                st.executeUpdate("create table if not exists ranked_token_ledger (id bigserial primary key, winner_profile_uuid uuid not null, opponent_profile_uuid uuid, tokens integer not null, rewarded_at timestamptz not null default now())");
                st.executeUpdate("create index if not exists idx_ranked_token_ledger_daily on ranked_token_ledger(winner_profile_uuid, rewarded_at desc)");
                st.executeUpdate("create index if not exists idx_ranked_token_ledger_same_opponent on ranked_token_ledger(winner_profile_uuid, opponent_profile_uuid, rewarded_at desc)");
            }
        });
    }
}
