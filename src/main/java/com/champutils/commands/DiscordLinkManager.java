package com.champutils.commands;

import com.champutils.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Network-wide Minecraft/Discord account linking backed by PostgreSQL. */
public final class DiscordLinkManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/discord_links.json");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static Root DATA = new Root();
    private static boolean loaded;

    private DiscordLinkManager() {}

    public static synchronized void initialize() {
        loadLocal();
        if (!DatabaseManager.isEnabled()) return;
        Root legacy = DATA;
        DatabaseManager.executeAsync("ensure and migrate Discord links", connection -> {
            ensureSchema(connection);
            for (PendingLink pending : legacy.pendingLinks.values()) {
                if (pending == null || pending.minecraftUuid == null || pending.code == null) continue;
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into discord_pending_links (minecraft_uuid, minecraft_name, code, created_at) values (?::uuid, ?, ?, ?::timestamptz) " +
                                "on conflict (minecraft_uuid) do nothing")) {
                    ps.setString(1, pending.minecraftUuid);
                    ps.setString(2, safe(pending.minecraftName));
                    ps.setString(3, pending.code);
                    ps.setString(4, validInstant(pending.createdAt));
                    ps.executeUpdate();
                }
            }
            for (LinkedAccount linked : legacy.linkedAccounts.values()) {
                if (linked == null || linked.minecraftUuid == null || linked.discordId == null) continue;
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into discord_linked_accounts (minecraft_uuid, minecraft_name, discord_id, discord_name, linked_at) values (?::uuid, ?, ?, ?, ?::timestamptz) " +
                                "on conflict (minecraft_uuid) do update set minecraft_name = excluded.minecraft_name, discord_id = excluded.discord_id, discord_name = excluded.discord_name, linked_at = excluded.linked_at")) {
                    ps.setString(1, linked.minecraftUuid);
                    ps.setString(2, safe(linked.minecraftName));
                    ps.setString(3, linked.discordId);
                    ps.setString(4, safe(linked.discordName));
                    ps.setString(5, validInstant(linked.linkedAt));
                    ps.executeUpdate();
                }
            }
        });
    }

    public static CompletableFuture<String> createCodeAsync(UUID minecraftUuid, String minecraftName) {
        if (minecraftUuid == null) return CompletableFuture.completedFuture("");
        String code = randomCode();
        PendingLink pending = new PendingLink();
        pending.minecraftUuid = minecraftUuid.toString();
        pending.minecraftName = safe(minecraftName);
        pending.code = code;
        pending.createdAt = Instant.now().toString();
        synchronized (DiscordLinkManager.class) {
            loadLocal();
            DATA.pendingLinks.put(minecraftUuid.toString(), pending);
            saveLocal();
        }
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(code);
        return DatabaseManager.supplyAsync("create Discord link code", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "insert into discord_pending_links (minecraft_uuid, minecraft_name, code, created_at) values (?, ?, ?, now()) " +
                            "on conflict (minecraft_uuid) do update set minecraft_name = excluded.minecraft_name, code = excluded.code, created_at = now()")) {
                ps.setObject(1, minecraftUuid);
                ps.setString(2, safe(minecraftName));
                ps.setString(3, code);
                ps.executeUpdate();
            }
            return code;
        });
    }

    public static CompletableFuture<LinkedAccount> linkedAsync(UUID minecraftUuid) {
        if (minecraftUuid == null) return CompletableFuture.completedFuture(null);
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(linkedLocal(minecraftUuid));
        return DatabaseManager.supplyAsync("load Discord link", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "select minecraft_uuid::text, minecraft_name, discord_id, discord_name, linked_at::text from discord_linked_accounts where minecraft_uuid = ?")) {
                ps.setObject(1, minecraftUuid);
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? readLinked(rs) : null; }
            }
        });
    }

    public static CompletableFuture<LinkedAccount> linkedByDiscordIdAsync(String discordId) {
        if (discordId == null || discordId.isBlank()) return CompletableFuture.completedFuture(null);
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(linkedByDiscordIdLocal(discordId));
        return DatabaseManager.supplyAsync("load Discord link by Discord id", connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "select minecraft_uuid::text, minecraft_name, discord_id, discord_name, linked_at::text from discord_linked_accounts where discord_id = ?")) {
                ps.setString(1, discordId.trim());
                try (ResultSet rs = ps.executeQuery()) { return rs.next() ? readLinked(rs) : null; }
            }
        });
    }

    public static CompletableFuture<VerifyResult> verifyAsync(UUID minecraftUuid, String code, String discordId, String discordName) {
        if (minecraftUuid == null || code == null || discordId == null || discordId.isBlank()) return CompletableFuture.completedFuture(VerifyResult.INVALID);
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(verifyLocal(minecraftUuid, code, discordId, discordName));
        return DatabaseManager.supplyAsync("verify Discord link", connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                String pendingName;
                String pendingCode;
                try (PreparedStatement ps = connection.prepareStatement(
                        "select minecraft_name, code from discord_pending_links where minecraft_uuid = ? for update")) {
                    ps.setObject(1, minecraftUuid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { connection.rollback(); return VerifyResult.NO_PENDING_CODE; }
                        pendingName = rs.getString(1);
                        pendingCode = rs.getString(2);
                    }
                }
                if (pendingCode == null || !pendingCode.equalsIgnoreCase(code.trim())) {
                    connection.rollback();
                    return VerifyResult.BAD_CODE;
                }
                try (PreparedStatement conflict = connection.prepareStatement(
                        "select minecraft_uuid from discord_linked_accounts where discord_id = ? and minecraft_uuid <> ?")) {
                    conflict.setString(1, discordId.trim());
                    conflict.setObject(2, minecraftUuid);
                    try (ResultSet rs = conflict.executeQuery()) {
                        if (rs.next()) { connection.rollback(); return VerifyResult.DISCORD_ALREADY_LINKED; }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into discord_linked_accounts (minecraft_uuid, minecraft_name, discord_id, discord_name, linked_at) values (?, ?, ?, ?, now()) " +
                                "on conflict (minecraft_uuid) do update set minecraft_name = excluded.minecraft_name, discord_id = excluded.discord_id, discord_name = excluded.discord_name, linked_at = now()")) {
                    ps.setObject(1, minecraftUuid);
                    ps.setString(2, safe(pendingName));
                    ps.setString(3, discordId.trim());
                    ps.setString(4, discordName == null || discordName.isBlank() ? discordId.trim() : discordName.trim());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("delete from discord_pending_links where minecraft_uuid = ?")) {
                    ps.setObject(1, minecraftUuid);
                    ps.executeUpdate();
                }
                connection.commit();
                return VerifyResult.SUCCESS;
            } catch (Throwable error) {
                try { connection.rollback(); } catch (Exception ignored) {}
                if (error instanceof Exception exception) throw exception;
                throw new RuntimeException(error);
            } finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static CompletableFuture<Boolean> unlinkAsync(UUID minecraftUuid) {
        if (minecraftUuid == null) return CompletableFuture.completedFuture(false);
        synchronized (DiscordLinkManager.class) {
            loadLocal();
            DATA.pendingLinks.remove(minecraftUuid.toString());
            DATA.linkedAccounts.remove(minecraftUuid.toString());
            saveLocal();
        }
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(true);
        return DatabaseManager.supplyAsync("unlink Discord account", connection -> {
            ensureSchema(connection);
            int removed;
            try (PreparedStatement ps = connection.prepareStatement("delete from discord_linked_accounts where minecraft_uuid = ?")) {
                ps.setObject(1, minecraftUuid);
                removed = ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("delete from discord_pending_links where minecraft_uuid = ?")) {
                ps.setObject(1, minecraftUuid);
                ps.executeUpdate();
            }
            return removed > 0;
        });
    }

    private static void ensureSchema(java.sql.Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists discord_pending_links (minecraft_uuid uuid primary key, minecraft_name text not null default '', code text not null, created_at timestamptz not null default now())");
            statement.executeUpdate("create table if not exists discord_linked_accounts (minecraft_uuid uuid primary key, minecraft_name text not null default '', discord_id text not null unique, discord_name text not null default '', linked_at timestamptz not null default now())");
            statement.executeUpdate("create index if not exists discord_pending_links_created_idx on discord_pending_links (created_at)");
        }
    }

    private static LinkedAccount readLinked(ResultSet rs) throws Exception {
        LinkedAccount linked = new LinkedAccount();
        linked.minecraftUuid = rs.getString(1);
        linked.minecraftName = rs.getString(2);
        linked.discordId = rs.getString(3);
        linked.discordName = rs.getString(4);
        linked.linkedAt = rs.getString(5);
        return linked;
    }

    private static synchronized LinkedAccount linkedLocal(UUID minecraftUuid) {
        loadLocal();
        return DATA.linkedAccounts.get(minecraftUuid.toString());
    }

    private static synchronized LinkedAccount linkedByDiscordIdLocal(String discordId) {
        loadLocal();
        for (LinkedAccount linked : DATA.linkedAccounts.values()) if (linked != null && discordId.equals(linked.discordId)) return linked;
        return null;
    }

    private static synchronized VerifyResult verifyLocal(UUID minecraftUuid, String code, String discordId, String discordName) {
        loadLocal();
        PendingLink pending = DATA.pendingLinks.get(minecraftUuid.toString());
        if (pending == null) return VerifyResult.NO_PENDING_CODE;
        if (!pending.code.equalsIgnoreCase(code.trim())) return VerifyResult.BAD_CODE;
        LinkedAccount conflict = linkedByDiscordIdLocal(discordId);
        if (conflict != null && !minecraftUuid.toString().equals(conflict.minecraftUuid)) return VerifyResult.DISCORD_ALREADY_LINKED;
        LinkedAccount linked = new LinkedAccount();
        linked.minecraftUuid = minecraftUuid.toString();
        linked.minecraftName = pending.minecraftName;
        linked.discordId = discordId.trim();
        linked.discordName = discordName == null || discordName.isBlank() ? discordId.trim() : discordName.trim();
        linked.linkedAt = Instant.now().toString();
        DATA.pendingLinks.remove(minecraftUuid.toString());
        DATA.linkedAccounts.put(minecraftUuid.toString(), linked);
        saveLocal();
        return VerifyResult.SUCCESS;
    }

    private static String randomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) builder.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return builder.toString().toUpperCase(Locale.ROOT);
    }

    private static synchronized void loadLocal() {
        if (loaded) return;
        loaded = true;
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (FILE.exists()) {
                try (FileReader reader = new FileReader(FILE)) {
                    Root loadedRoot = GSON.fromJson(reader, Root.class);
                    DATA = loadedRoot == null ? new Root() : loadedRoot;
                }
            }
            if (DATA.pendingLinks == null) DATA.pendingLinks = new LinkedHashMap<>();
            if (DATA.linkedAccounts == null) DATA.linkedAccounts = new LinkedHashMap<>();
            saveLocal();
        } catch (Exception e) {
            e.printStackTrace();
            DATA = new Root();
        }
    }

    private static synchronized void saveLocal() {
        try {
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(DATA, writer); }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String validInstant(String value) {
        try { return Instant.parse(value).toString(); } catch (Exception ignored) { return Instant.now().toString(); }
    }

    public enum VerifyResult { SUCCESS, INVALID, NO_PENDING_CODE, BAD_CODE, DISCORD_ALREADY_LINKED }

    public static final class Root {
        public Map<String, PendingLink> pendingLinks = new LinkedHashMap<>();
        public Map<String, LinkedAccount> linkedAccounts = new LinkedHashMap<>();
    }
    public static final class PendingLink {
        public String minecraftUuid;
        public String minecraftName;
        public String code;
        public String createdAt;
    }
    public static final class LinkedAccount {
        public String minecraftUuid;
        public String minecraftName;
        public String discordId;
        public String discordName;
        public String linkedAt;
    }
}
