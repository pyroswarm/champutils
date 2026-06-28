package com.champutils.rank;

import com.champutils.database.DatabaseManager;
import com.champutils.profile.PlayerProfileManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankedTokenManager {
    private static final Map<UUID, Long> CACHE = new ConcurrentHashMap<>();
    private RankedTokenManager() {}

    public static void register() {
        RankedTokenConfig.load();
        ensureSchemaAsync();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> loadAsync(handler.player));
    }

    public static long cachedBalance(ServerPlayer player) { return player == null ? 0L : CACHE.getOrDefault(PlayerProfileManager.activeProfileId(player), 0L); }

    public static void loadAsync(ServerPlayer player) {
        if (player == null || !DatabaseManager.isEnabled()) return;
        UUID profile = PlayerProfileManager.activeProfileId(player);
        DatabaseManager.executeAsync("load ranked tokens " + profile, connection -> {
            try (var ps = connection.prepareStatement("select tokens from ranked_token_balances where profile_uuid = ?")) {
                ps.setObject(1, profile);
                try (var rs = ps.executeQuery()) { CACHE.put(profile, rs.next() ? rs.getLong(1) : 0L); }
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
            int cooldownHours = Math.max(0, RankedTokenConfig.CONFIG.sameOpponentCooldownHours);
            try (var daily = connection.prepareStatement("select coalesce(sum(tokens),0) from ranked_token_ledger where winner_profile_uuid = ? and rewarded_at >= now() - interval '24 hours'")) {
                daily.setObject(1, winnerProfile);
                try (var rs = daily.executeQuery()) { if (rs.next() && rs.getInt(1) >= cap) return; }
            }
            if (cooldownHours > 0) {
                try (var same = connection.prepareStatement("select 1 from ranked_token_ledger where winner_profile_uuid = ? and opponent_profile_uuid = ? and rewarded_at >= now() - (? * interval '1 hour') limit 1")) {
                    same.setObject(1, winnerProfile); same.setObject(2, loserProfile); same.setInt(3, cooldownHours);
                    try (var rs = same.executeQuery()) { if (rs.next()) return; }
                }
            }
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

    public static boolean spend(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) return false;
        UUID profile = PlayerProfileManager.activeProfileId(player);
        long current = CACHE.getOrDefault(profile, 0L);
        if (current < amount) return false;
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
