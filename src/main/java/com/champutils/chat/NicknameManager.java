package com.champutils.chat;

import com.champutils.database.DatabaseManager;
import com.champutils.moderation.ModerationManager;
import com.champutils.permissions.LuckPermsHook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class NicknameManager {
    public static final String USE_PERMISSION = "champutils.nick";
    public static final String COLOR_PERMISSION = "champutils.nick.color";
    public static final String FORMAT_PERMISSION = "champutils.nick.format";
    public static final String BYPASS_PERMISSION = "champutils.nick.bypass";
    public static final String STAFF_PERMISSION = "champutils.nick.staff";

    private static final Pattern PLAIN = Pattern.compile("^[A-Za-z0-9_ ]+$");
    private static final Pattern MINECRAFT_FORMAT = Pattern.compile("(?i)[&§][0-9A-FK-OR]");
    private static final Map<UUID, CachedNickname> CACHE = new ConcurrentHashMap<>();
    private NicknameManager() {}

    public static void initialize() {
        NicknameConfig.load();
        DatabaseManager.runAsync("nickname schema", connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS public.player_nicknames (player_uuid uuid PRIMARY KEY, nickname text NOT NULL, nickname_plain text NOT NULL, changed_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now())");
                statement.execute("ALTER TABLE public.player_nicknames ADD COLUMN IF NOT EXISTS nickname_plain text");
                statement.execute("ALTER TABLE public.player_nicknames ADD COLUMN IF NOT EXISTS changed_at timestamptz NOT NULL DEFAULT now()");
                statement.execute("UPDATE public.player_nicknames SET nickname_plain = lower(regexp_replace(nickname, '[^a-zA-Z0-9_ ]', '', 'g')) WHERE nickname_plain IS NULL OR nickname_plain = ''");
                statement.execute("ALTER TABLE public.player_nicknames ALTER COLUMN nickname_plain SET NOT NULL");
                statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS player_nicknames_plain_unique ON public.player_nicknames (lower(nickname_plain))");
                statement.execute("CREATE TABLE IF NOT EXISTS public.player_nickname_history (id bigserial PRIMARY KEY, player_uuid uuid NOT NULL, old_nickname text, new_nickname text, changed_by uuid, reason text NOT NULL DEFAULT 'PLAYER', changed_at timestamptz NOT NULL DEFAULT now())");
                statement.execute("CREATE INDEX IF NOT EXISTS player_nickname_history_player_idx ON public.player_nickname_history (player_uuid, changed_at DESC)");
            }
        });
    }

    public static boolean canUse(ServerPlayer player) {
        return player != null && NicknameConfig.INSTANCE.enabled && (player.hasPermissions(4) || LuckPermsHook.hasPermission(player, USE_PERMISSION));
    }

    public static String get(ServerPlayer player) {
        if (player == null) return "";
        CachedNickname cached = CACHE.get(player.getUUID());
        return cached == null ? "" : cached.nickname();
    }

    public static String displayName(ServerPlayer player) {
        String nickname = get(player);
        return nickname.isBlank() ? player.getGameProfile().getName() : nickname;
    }

    public static String trueName(ServerPlayer player) {
        return player == null ? "" : player.getGameProfile().getName();
    }

    public static boolean isUsingNickname(ServerPlayer player) { return !get(player).isBlank(); }

    public static MutableComponent displayComponent(ServerPlayer player) {
        MutableComponent name = Component.literal(displayName(player)).withStyle(ChatFormatting.WHITE);
        if (isUsingNickname(player)) {
            name.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("True Name: " + trueName(player)).withStyle(ChatFormatting.GRAY))));
        }
        return name;
    }

    public static void load(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        DatabaseManager.supplyAsync("load nickname", connection -> {
            try (var ps = connection.prepareStatement("SELECT nickname, changed_at FROM public.player_nicknames WHERE player_uuid = ?")) {
                ps.setObject(1, uuid);
                try (var rs = ps.executeQuery()) {
                    return rs.next() ? new CachedNickname(rs.getString("nickname"), rs.getTimestamp("changed_at").toInstant(), Instant.now()) : null;
                }
            }
        }).thenAccept(value -> player.server.execute(() -> {
            if (value == null || value.nickname().isBlank()) CACHE.remove(uuid); else CACHE.put(uuid, value);
            refreshDisplay(player);
        }));
    }

    public static void refreshIfStale(ServerPlayer player) {
        if (player == null) return;
        CachedNickname cached = CACHE.get(player.getUUID());
        long maxAge = Math.max(5, NicknameConfig.INSTANCE.syncSeconds);
        if (cached == null || Duration.between(cached.loadedAt(), Instant.now()).getSeconds() >= maxAge) load(player);
    }

    public static CompletableFuture<String> set(ServerPlayer player, String raw) {
        if (!canUse(player)) return CompletableFuture.completedFuture("VIP or VIP+ is required to use /nick.");
        Validation validation = validate(player, raw);
        if (!validation.ok()) return CompletableFuture.completedFuture(validation.message());
        UUID uuid = player.getUUID();
        String nickname = validation.nickname();
        String plain = validation.plain();
        boolean bypass = player.hasPermissions(4) || LuckPermsHook.hasPermission(player, BYPASS_PERMISSION);

        return DatabaseManager.supplyAsync("save nickname", connection -> {
            connection.setAutoCommit(false);
            try {
                String old = "";
                Instant changedAt = Instant.EPOCH;
                try (var current = connection.prepareStatement("SELECT nickname, changed_at FROM public.player_nicknames WHERE player_uuid = ? FOR UPDATE")) {
                    current.setObject(1, uuid);
                    try (var rs = current.executeQuery()) {
                        if (rs.next()) {
                            old = rs.getString("nickname");
                            changedAt = rs.getTimestamp("changed_at").toInstant();
                        }
                    }
                }
                if (!bypass && NicknameConfig.INSTANCE.cooldownMinutes > 0 && !old.isBlank()) {
                    long remaining = NicknameConfig.INSTANCE.cooldownMinutes - Duration.between(changedAt, Instant.now()).toMinutes();
                    if (remaining > 0) {
                        connection.rollback();
                        return "You can change your nickname again in " + remaining + " minute(s).";
                    }
                }
                if (NicknameConfig.INSTANCE.unique) {
                    try (var unique = connection.prepareStatement("SELECT 1 FROM public.player_nicknames WHERE lower(nickname_plain) = lower(?) AND player_uuid <> ? LIMIT 1")) {
                        unique.setString(1, plain);
                        unique.setObject(2, uuid);
                        try (var rs = unique.executeQuery()) {
                            if (rs.next()) {
                                connection.rollback();
                                return "That nickname is already in use.";
                            }
                        }
                    }
                }
                try (var ps = connection.prepareStatement("INSERT INTO public.player_nicknames (player_uuid, nickname, nickname_plain, changed_at, updated_at) VALUES (?, ?, ?, now(), now()) ON CONFLICT (player_uuid) DO UPDATE SET nickname = excluded.nickname, nickname_plain = excluded.nickname_plain, changed_at = now(), updated_at = now()")) {
                    ps.setObject(1, uuid); ps.setString(2, nickname); ps.setString(3, plain); ps.executeUpdate();
                }
                if (NicknameConfig.INSTANCE.auditHistory) {
                    try (var history = connection.prepareStatement("INSERT INTO public.player_nickname_history (player_uuid, old_nickname, new_nickname, changed_by, reason) VALUES (?, ?, ?, ?, 'PLAYER')")) {
                        history.setObject(1, uuid); history.setString(2, old); history.setString(3, nickname); history.setObject(4, uuid); history.executeUpdate();
                    }
                }
                connection.commit();
                return "Nickname set to " + nickname + ".";
            } catch (Exception error) {
                try { connection.rollback(); } catch (Exception ignored) {}
                throw error;
            } finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        }).thenApply(message -> {
            if (message.startsWith("Nickname set")) {
                CACHE.put(uuid, new CachedNickname(nickname, Instant.now(), Instant.now()));
                player.server.execute(() -> refreshDisplay(player));
            }
            return message;
        });
    }

    public static CompletableFuture<String> clear(ServerPlayer player) {
        if (player == null) return CompletableFuture.completedFuture("Player unavailable.");
        UUID uuid = player.getUUID();
        return DatabaseManager.runAsync("clear nickname", connection -> {
            String old = get(player);
            try (var ps = connection.prepareStatement("DELETE FROM public.player_nicknames WHERE player_uuid = ?")) { ps.setObject(1, uuid); ps.executeUpdate(); }
            if (NicknameConfig.INSTANCE.auditHistory && !old.isBlank()) {
                try (var history = connection.prepareStatement("INSERT INTO public.player_nickname_history (player_uuid, old_nickname, new_nickname, changed_by, reason) VALUES (?, ?, NULL, ?, 'CLEAR')")) {
                    history.setObject(1, uuid); history.setString(2, old); history.setObject(3, uuid); history.executeUpdate();
                }
            }
        }).thenApply(v -> {
            CACHE.remove(uuid);
            player.server.execute(() -> refreshDisplay(player));
            return "Nickname cleared.";
        });
    }

    public static CompletableFuture<String> realNameLookup(String nickname) {
        String plain = normalizePlain(nickname);
        return DatabaseManager.supplyAsync("nickname realname lookup", connection -> {
            try (var ps = connection.prepareStatement("SELECT p.username FROM public.player_nicknames n LEFT JOIN public.players p ON p.uuid = n.player_uuid WHERE lower(n.nickname_plain) = lower(?) LIMIT 1")) {
                ps.setString(1, plain);
                try (var rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
            }
        });
    }

    public static void refreshDisplay(ServerPlayer player) {
        if (player == null) return;
        try {
            player.setCustomName(displayComponent(player));
            player.setCustomNameVisible(true);
        } catch (Throwable ignored) {}
    }

    private static Validation validate(ServerPlayer player, String raw) {
        String nickname = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        String plain = normalizePlain(nickname);
        NicknameConfig config = NicknameConfig.INSTANCE;
        if (plain.length() < config.minLength || plain.length() > config.maxLength) return Validation.fail("Nicknames must be " + config.minLength + "-" + config.maxLength + " characters.");
        if (!config.allowSpaces && plain.contains(" ")) return Validation.fail("Nicknames cannot contain spaces.");
        if (!PLAIN.matcher(plain).matches()) return Validation.fail("Nicknames may only use letters, numbers, underscores" + (config.allowSpaces ? ", or spaces." : "."));
        boolean hasFormatting = MINECRAFT_FORMAT.matcher(nickname).find();
        if (hasFormatting && !(player.hasPermissions(4) || LuckPermsHook.hasPermission(player, COLOR_PERMISSION) || LuckPermsHook.hasPermission(player, FORMAT_PERMISSION))) return Validation.fail("You do not have permission to use nickname formatting.");
        String lower = plain.toLowerCase(Locale.ROOT);
        if (config.reservedNames.stream().anyMatch(value -> value != null && lower.equals(value.trim().toLowerCase(Locale.ROOT)))) return Validation.fail("That nickname is reserved.");
        if (!ModerationManager.isSafeDisplayName(plain)) return Validation.fail("That nickname contains blocked language.");
        return new Validation(true, nickname, plain, "");
    }

    private static String normalizePlain(String value) {
        if (value == null) return "";
        return MINECRAFT_FORMAT.matcher(value).replaceAll("").replaceAll("[^A-Za-z0-9_ ]", "").trim().replaceAll("\\s+", " ");
    }

    private record CachedNickname(String nickname, Instant changedAt, Instant loadedAt) {}
    private record Validation(boolean ok, String nickname, String plain, String message) {
        static Validation fail(String message) { return new Validation(false, "", "", message); }
    }
}
