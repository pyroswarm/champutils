package com.champutils.wondertrade;

import com.champutils.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WonderTradeRepository {

    private static final Gson GSON = new Gson();
    private static volatile boolean schemaReady = false;
    private static volatile Boolean legacyPokemonDataColumn = null;
    private static volatile Boolean legacyLevelColumn = null;

    private WonderTradeRepository() {}

    public static void ensureSchema() throws Exception {
        ensureSchema(DatabaseManager.getConnection());
    }

    public static void ensureSchema(Connection connection) throws Exception {
        if (schemaReady) {
            return;
        }
        synchronized (WonderTradeRepository.class) {
            if (schemaReady) {
                return;
            }
        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists wondertrade_pool (" +
                        "id uuid primary key default gen_random_uuid()," +
                        "owner_uuid text not null default '00000000-0000-0000-0000-000000000000'," +
                        "owner_username text not null default 'WonderTrade'," +
                        "source text not null default 'PLAYER'," +
                        "species text not null default 'unknown'," +
                        "display_name text not null default 'unknown'," +
                        "pokemon_level integer not null default 1," +
                        "shiny boolean not null default false," +
                        "legendary boolean not null default false," +
                        "payload jsonb not null default '{}'::jsonb," +
                        "created_at timestamptz not null default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        // Migration safety: CREATE TABLE IF NOT EXISTS does not add columns to older tables.
        // These ALTER statements make the Wondertrade schema self-healing if an older SQL snippet was used.
        addColumnIfMissing(connection, "wondertrade_pool", "owner_uuid", "text not null default '00000000-0000-0000-0000-000000000000'");
        addColumnIfMissing(connection, "wondertrade_pool", "owner_username", "text not null default 'WonderTrade'");
        addColumnIfMissing(connection, "wondertrade_pool", "source", "text not null default 'PLAYER'");
        addColumnIfMissing(connection, "wondertrade_pool", "species", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_pool", "display_name", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_pool", "pokemon_level", "integer not null default 1");
        addColumnIfMissing(connection, "wondertrade_pool", "shiny", "boolean not null default false");
        addColumnIfMissing(connection, "wondertrade_pool", "a", "boolean not null default false");
        addColumnIfMissing(connection, "wondertrade_pool", "payload", "jsonb not null default '{}'::jsonb");
        addColumnIfMissing(connection, "wondertrade_pool", "created_at", "timestamptz not null default now()");

        repairLegacyWonderTradePoolColumns(connection);

        try (PreparedStatement statement = connection.prepareStatement(
                "create index if not exists idx_wondertrade_pool_created_at on wondertrade_pool(created_at)"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create index if not exists idx_wondertrade_pool_flags on wondertrade_pool(shiny, legendary)"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists wondertrade_history (" +
                        "id uuid primary key default gen_random_uuid()," +
                        "player_uuid uuid not null," +
                        "player_username text not null," +
                        "sent_species text not null," +
                        "sent_display_name text not null," +
                        "sent_shiny boolean not null default false," +
                        "sent_legendary boolean not null default false," +
                        "received_species text not null," +
                        "received_display_name text not null," +
                        "received_shiny boolean not null default false," +
                        "received_legendary boolean not null default false," +
                        "traded_at timestamptz not null default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        addColumnIfMissing(connection, "wondertrade_history", "player_uuid", "uuid not null default '00000000-0000-0000-0000-000000000000'");
        convertPlayerUuidColumnToUuid(connection, "wondertrade_history");
        addColumnIfMissing(connection, "wondertrade_history", "player_username", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_history", "sent_species", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_history", "sent_display_name", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_history", "sent_shiny", "boolean not null default false");
        addColumnIfMissing(connection, "wondertrade_history", "sent_legendary", "boolean not null default false");
        addColumnIfMissing(connection, "wondertrade_history", "received_species", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_history", "received_display_name", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_history", "received_shiny", "boolean not null default false");
        addColumnIfMissing(connection, "wondertrade_history", "received_legendary", "boolean not null default false");
        addColumnIfMissing(connection, "wondertrade_history", "traded_at", "timestamptz not null default now()");

        repairLegacyWonderTradeHistoryColumns(connection);

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists wondertrade_settings (" +
                        "setting_key text primary key," +
                        "setting_value text not null," +
                        "updated_at timestamptz not null default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wondertrade_settings(setting_key, setting_value) values ('cooldown_minutes', '60') " +
                        "on conflict (setting_key) do nothing"
        )) {
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists wondertrade_cooldowns (" +
                        "profile_id uuid primary key," +
                        "player_uuid uuid not null," +
                        "last_trade_at timestamptz not null default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        addColumnIfMissing(connection, "wondertrade_cooldowns", "profile_id", "uuid");
        addColumnIfMissing(connection, "wondertrade_cooldowns", "player_uuid", "uuid not null default '00000000-0000-0000-0000-000000000000'");
        convertPlayerUuidColumnToUuid(connection, "wondertrade_cooldowns");
        addColumnIfMissing(connection, "wondertrade_cooldowns", "last_trade_at", "timestamptz not null default now()");
        repairLegacyCooldownColumns(connection);
        // Old backups used a player_uuid UNIQUE index. Cooldowns are profile-scoped now, so one account
        // can safely have cooldown rows for multiple profiles without breaking WonderTrade.
        executeQuietly(connection, "drop index if exists idx_wondertrade_cooldowns_player_uuid_unique");
        validateRequiredColumns(connection, "wondertrade_cooldowns", "profile_id", "player_uuid", "last_trade_at");
        executeQuietly(connection, "create index if not exists idx_wondertrade_cooldowns_player_uuid on wondertrade_cooldowns(player_uuid)");

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists wondertrade_pending_claims (" +
                        "profile_id uuid primary key," +
                        "player_uuid uuid not null," +
                        "player_username text not null default 'unknown'," +
                        "claim_type text not null default 'RECEIVED'," +
                        "payload jsonb not null default '{}'::jsonb," +
                        "display_name text not null default 'unknown'," +
                        "created_at timestamptz not null default now()," +
                        "updated_at timestamptz not null default now()" +
                        ")"
        )) {
            statement.executeUpdate();
        }

        addColumnIfMissing(connection, "wondertrade_pending_claims", "profile_id", "uuid");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "player_uuid", "uuid not null default '00000000-0000-0000-0000-000000000000'");
        convertPlayerUuidColumnToUuid(connection, "wondertrade_pending_claims");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "player_username", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "claim_type", "text not null default 'RECEIVED'");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "payload", "jsonb not null default '{}'::jsonb");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "display_name", "text not null default 'unknown'");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "created_at", "timestamptz not null default now()");
        addColumnIfMissing(connection, "wondertrade_pending_claims", "updated_at", "timestamptz not null default now()");
        repairLegacyPendingClaimColumns(connection);
        validateRequiredColumns(connection, "wondertrade_pending_claims", "profile_id", "player_uuid", "player_username", "claim_type", "payload", "display_name", "created_at", "updated_at");
        executeQuietly(connection, "create unique index if not exists idx_wondertrade_pending_claims_profile_id_unique on wondertrade_pending_claims(profile_id)");
        executeQuietly(connection, "create index if not exists idx_wondertrade_pending_claims_player_uuid on wondertrade_pending_claims(player_uuid)");

            legacyPokemonDataColumn = columnExists(connection, "wondertrade_pool", "pokemon_data");
            legacyLevelColumn = columnExists(connection, "wondertrade_pool", "level");
            schemaReady = true;
        }
    }

    private static void addColumnIfMissing(Connection connection, String table, String column, String definition) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from information_schema.columns where table_schema = current_schema() and table_name = ? and column_name = ?"
        )) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        }

        String safeTable = table.replaceAll("[^A-Za-z0-9_]", "");
        String safeColumn = column.replaceAll("[^A-Za-z0-9_]", "");
        try (PreparedStatement statement = connection.prepareStatement(
                "alter table " + safeTable + " add column " + safeColumn + " " + definition
        )) {
            statement.executeUpdate();
        }
    }


    private static void repairLegacyWonderTradePoolColumns(Connection connection) {
        // First Wondertrade SQL draft used pokemon_data text not null with no default.
        // New code uses payload jsonb. This makes older tables compatible instead of failing inserts.
        try {
            if (columnExists(connection, "wondertrade_pool", "pokemon_data")) {
                executeQuietly(connection, "update wondertrade_pool set pokemon_data = '{}' where pokemon_data is null");
                executeQuietly(connection, "alter table wondertrade_pool alter column pokemon_data set default '{}'");
            }
            if (columnExists(connection, "wondertrade_pool", "level")) {
                executeQuietly(connection, "update wondertrade_pool set level = coalesce(level, pokemon_level, 1) where level is null");
                executeQuietly(connection, "alter table wondertrade_pool alter column level set default 1");
            }
            if (columnExists(connection, "wondertrade_pool", "added_by_uuid")) {
                executeQuietly(connection, "alter table wondertrade_pool alter column added_by_uuid drop not null");
            }
            if (columnExists(connection, "wondertrade_pool", "added_by_name")) {
                executeQuietly(connection, "alter table wondertrade_pool alter column added_by_name drop not null");
            }
        } catch (Exception ignored) {
        }
    }

    private static void repairLegacyWonderTradeHistoryColumns(Connection connection) {
        // The first SQL snippet used player_name/deposited_* columns with NOT NULL constraints.
        // New code writes player_username/sent_* columns. This keeps both schema versions safe.
        try {
            if (columnExists(connection, "wondertrade_history", "player_name")) {
                executeQuietly(connection, "update wondertrade_history set player_name = coalesce(player_name, player_username, player_uuid, 'unknown') where player_name is null");
                executeQuietly(connection, "alter table wondertrade_history alter column player_name set default 'unknown'");
            }
            if (columnExists(connection, "wondertrade_history", "deposited_species")) {
                executeQuietly(connection, "update wondertrade_history set deposited_species = coalesce(deposited_species, sent_species, 'unknown') where deposited_species is null");
                executeQuietly(connection, "alter table wondertrade_history alter column deposited_species set default 'unknown'");
            }
            if (columnExists(connection, "wondertrade_history", "deposited_shiny")) {
                executeQuietly(connection, "update wondertrade_history set deposited_shiny = coalesce(deposited_shiny, sent_shiny, false) where deposited_shiny is null");
                executeQuietly(connection, "alter table wondertrade_history alter column deposited_shiny set default false");
            }
            if (columnExists(connection, "wondertrade_history", "deposited_legendary")) {
                executeQuietly(connection, "update wondertrade_history set deposited_legendary = coalesce(deposited_legendary, sent_legendary, false) where deposited_legendary is null");
                executeQuietly(connection, "alter table wondertrade_history alter column deposited_legendary set default false");
            }
            if (columnExists(connection, "wondertrade_history", "received_species")) {
                executeQuietly(connection, "update wondertrade_history set received_species = coalesce(received_species, 'unknown') where received_species is null");
                executeQuietly(connection, "alter table wondertrade_history alter column received_species set default 'unknown'");
            }
            if (columnExists(connection, "wondertrade_history", "received_shiny")) {
                executeQuietly(connection, "update wondertrade_history set received_shiny = coalesce(received_shiny, false) where received_shiny is null");
                executeQuietly(connection, "alter table wondertrade_history alter column received_shiny set default false");
            }
            if (columnExists(connection, "wondertrade_history", "received_legendary")) {
                executeQuietly(connection, "update wondertrade_history set received_legendary = coalesce(received_legendary, false) where received_legendary is null");
                executeQuietly(connection, "alter table wondertrade_history alter column received_legendary set default false");
            }
            if (columnExists(connection, "wondertrade_history", "created_at")) {
                executeQuietly(connection, "alter table wondertrade_history alter column created_at set default now()");
            }
        } catch (Exception ignored) {
        }
    }

    private static void repairLegacyCooldownColumns(Connection connection) {
        // WonderTrade cooldowns are profile-scoped in the live schema. profile_id is the primary key
        // and must never be nullable. player_uuid is retained for audit/debug and optional account lookup.
        try {
            if (columnExists(connection, "wondertrade_cooldowns", "profile_id")) {
                executeQuietly(connection, "delete from wondertrade_cooldowns where profile_id is null");
                executeQuietly(connection, "alter table wondertrade_cooldowns alter column profile_id set not null");
            }
            if (columnExists(connection, "wondertrade_cooldowns", "player_uuid")) {
                executeQuietly(connection, "update wondertrade_cooldowns set player_uuid = '00000000-0000-0000-0000-000000000000' where player_uuid is null");
                executeQuietly(connection, "alter table wondertrade_cooldowns alter column player_uuid set not null");
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] WonderTrade cooldown schema repair failed: " + e.getMessage());
        }
    }

    private static void repairLegacyPendingClaimColumns(Connection connection) {
        // WonderTrade is profile-scoped. profile_id is the ownership key; player_uuid is retained
        // only for audit/display and account-level cooldown checks. Never make profile_id nullable.
        try {
            if (columnExists(connection, "wondertrade_pending_claims", "profile_id")) {
                executeQuietly(connection, "update wondertrade_pending_claims set profile_id = nullif(player_uuid::text, 'unknown')::uuid where profile_id is null and player_uuid::text ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'");
                executeQuietly(connection, "delete from wondertrade_pending_claims where profile_id is null");
                executeQuietly(connection, "alter table wondertrade_pending_claims alter column profile_id set not null");
            }
            if (columnExists(connection, "wondertrade_pending_claims", "player_uuid")) {
                executeQuietly(connection, "update wondertrade_pending_claims set player_uuid = '00000000-0000-0000-0000-000000000000' where player_uuid is null");
                executeQuietly(connection, "alter table wondertrade_pending_claims alter column player_uuid set not null");
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils] WonderTrade pending-claim schema repair failed: " + e.getMessage());
        }
    }

    private static void convertPlayerUuidColumnToUuid(Connection connection, String table) {
        // Older beta schemas created player_uuid as text. Current code uses UUID consistently.
        executeQuietly(connection, "alter table " + table + " alter column player_uuid drop default");
        executeQuietly(connection, "update " + table + " set player_uuid = '00000000-0000-0000-0000-000000000000' where player_uuid is null or trim(player_uuid::text) = '' or player_uuid::text = 'unknown'");
        executeQuietly(connection, "alter table " + table + " alter column player_uuid type uuid using player_uuid::uuid");
        executeQuietly(connection, "alter table " + table + " alter column player_uuid set default '00000000-0000-0000-0000-000000000000'::uuid");
        executeQuietly(connection, "alter table " + table + " alter column player_uuid set not null");
    }

    private static void validateRequiredColumns(Connection connection, String table, String... columns) throws Exception {
        List<String> missing = new ArrayList<>();
        for (String column : columns) {
            if (!columnExists(connection, table, column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Database table " + table + " is missing required columns " + missing + ". Check DB/schema and deployed jar.");
        }
    }


    private static boolean columnExists(Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from information_schema.columns where table_schema = current_schema() and table_name = ? and column_name = ?"
        )) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeQuietly(Connection connection, String sql) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (Exception ignored) {
        }
    }

    public static int poolSize() throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from wondertrade_pool")) {
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static int shinyCount() throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from wondertrade_pool where shiny = true")) {
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static int legendaryCount() throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        try (PreparedStatement statement = connection.prepareStatement("select count(*) from wondertrade_pool where legendary = true")) {
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public static void insertSeed(JsonObject payload, String species, String displayName, int level, boolean shiny) throws Exception {
        insert(UUID.fromString("00000000-0000-0000-0000-000000000000"), "WonderTrade", "SERVER_SEED", payload, species, displayName, level, shiny, false);
    }

    public static WonderTradeEntry exchange(UUID profileId, UUID playerUuid, String playerUsername, JsonObject offeredPayload) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);

        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            int poolCount = 0;
            try (PreparedStatement statement = connection.prepareStatement("select count(*) from wondertrade_pool")) {
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        poolCount = rs.getInt(1);
                    }
                }
            }

            WonderTradeEntry received = null;
            if (poolCount > 0) {
                int offset = java.util.concurrent.ThreadLocalRandom.current().nextInt(poolCount);
                try (PreparedStatement statement = connection.prepareStatement(
                        "select id, owner_uuid, owner_username, species, display_name, pokemon_level, shiny, legendary, payload::text as payload " +
                                "from wondertrade_pool order by created_at offset ? limit 1 for update skip locked"
                )) {
                    statement.setInt(1, offset);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) {
                            received = read(rs);
                        }
                    }
                }

                // If the random row was locked by another trade, fall back to the first available row.
                if (received == null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "select id, owner_uuid, owner_username, species, display_name, pokemon_level, shiny, legendary, payload::text as payload " +
                                    "from wondertrade_pool order by created_at limit 1 for update skip locked"
                    )) {
                        try (ResultSet rs = statement.executeQuery()) {
                            if (rs.next()) {
                                received = read(rs);
                            }
                        }
                    }
                }
            }

            if (received == null) {
                connection.rollback();
                return null;
            }

            try (PreparedStatement statement = connection.prepareStatement("delete from wondertrade_pool where id = ?")) {
                statement.setObject(1, received.id);
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }

            String offeredSpecies = safeString(offeredPayload, "species", "unknown");
            String offeredName = safeString(offeredPayload, "displayName", offeredSpecies);
            int offeredLevel = safeInt(offeredPayload, "level", 1);
            boolean offeredShiny = safeBoolean(offeredPayload, "shiny", false);
            boolean offeredLegendary = WonderTradePokemonUtil.isLegendarySpecies(offeredSpecies);

            insertPlayerPokemon(connection, playerUuid, playerUsername, offeredPayload, offeredSpecies, offeredName, offeredLevel, offeredShiny, offeredLegendary);

            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into wondertrade_history " +
                            "(player_uuid, player_username, sent_species, sent_display_name, sent_shiny, sent_legendary, received_species, received_display_name, received_shiny, received_legendary, traded_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())"
            )) {
                statement.setObject(1, playerUuid);
                statement.setString(2, playerUsername == null ? playerUuid.toString() : playerUsername);
                statement.setString(3, offeredSpecies);
                statement.setString(4, offeredName);
                statement.setBoolean(5, offeredShiny);
                statement.setBoolean(6, offeredLegendary);
                statement.setString(7, received.species);
                statement.setString(8, received.displayName);
                statement.setBoolean(9, received.shiny);
                statement.setBoolean(10, received.legendary);
                statement.executeUpdate();
            }

            savePendingClaim(connection, profileId, playerUuid, playerUsername, "RECEIVED", received.payload, received.displayName);
            markCooldown(connection, profileId, playerUuid);

            connection.commit();
            return received;
        }
        catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        }
        finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    private static void insert(UUID ownerUuid, String ownerUsername, String source, JsonObject payload, String species, String displayName, int level, boolean shiny, boolean legendary) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        insertIntoPool(connection, ownerUuid, ownerUsername, source, payload, species, displayName, level, shiny, legendary);
    }

    private static void insertPlayerPokemon(Connection connection, UUID ownerUuid, String ownerUsername, JsonObject payload, String species, String displayName, int level, boolean shiny, boolean legendary) throws Exception {
        insertIntoPool(connection, ownerUuid, ownerUsername, "PLAYER", payload, species, displayName, level, shiny, legendary);
    }

    private static void insertIntoPool(Connection connection, UUID ownerUuid, String ownerUsername, String source, JsonObject payload, String species, String displayName, int level, boolean shiny, boolean legendary) throws Exception {
        boolean hasLegacyPokemonData = legacyPokemonDataColumn != null ? legacyPokemonDataColumn : columnExists(connection, "wondertrade_pool", "pokemon_data");
        boolean hasLegacyLevel = legacyLevelColumn != null ? legacyLevelColumn : columnExists(connection, "wondertrade_pool", "level");

        String payloadText = GSON.toJson(payload == null ? new JsonObject() : payload);

        String columns = "(owner_uuid, owner_username, source, species, display_name, pokemon_level, shiny, legendary, payload";
        String values = "values (?, ?, ?, ?, ?, ?, ?, ?, ?";
        if (hasLegacyPokemonData) {
            columns += ", pokemon_data";
            values += ", ?";
        }
        if (hasLegacyLevel) {
            columns += ", level";
            values += ", ?";
        }
        columns += ", created_at) ";
        values += ", now())";

        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wondertrade_pool " + columns + " " + values
        )) {
            int index = 1;
            statement.setString(index++, ownerUuid.toString());
            statement.setString(index++, ownerUsername == null ? ownerUuid.toString() : ownerUsername);
            statement.setString(index++, source == null ? "PLAYER" : source);
            statement.setString(index++, species == null ? "unknown" : species);
            statement.setString(index++, displayName == null ? species : displayName);
            statement.setInt(index++, Math.max(1, level));
            statement.setBoolean(index++, shiny);
            statement.setBoolean(index++, legendary);
            statement.setObject(index++, jsonb(payload));
            if (hasLegacyPokemonData) {
                statement.setString(index++, payloadText);
            }
            if (hasLegacyLevel) {
                statement.setInt(index++, Math.max(1, level));
            }
            statement.executeUpdate();
        }
    }

    private static WonderTradeEntry read(ResultSet rs) throws Exception {
        String payloadText = rs.getString("payload");
        JsonObject payload = GSON.fromJson(payloadText, JsonObject.class);
        return new WonderTradeEntry(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("owner_username"),
                rs.getString("species"),
                rs.getString("display_name"),
                rs.getBoolean("shiny"),
                rs.getBoolean("a"),
                rs.getInt("pokemon_level"),
                payload
        );
    }

    private static PGobject jsonb(JsonObject object) throws Exception {
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue(GSON.toJson(object == null ? new JsonObject() : object));
        return pg;
    }

    private static String safeString(JsonObject object, String key, String fallback) {
        try {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsString();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static int safeInt(JsonObject object, String key, int fallback) {
        try {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsInt();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static boolean safeBoolean(JsonObject object, String key, boolean fallback) {
        try {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsBoolean();
            }
        } catch (Exception ignored) {}
        return fallback;
    }



    public static TradeGate getTradeGate(UUID profileId, UUID playerUuid) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);

        if (profileId == null) {
            throw new IllegalArgumentException("WonderTrade requires an active profile_id.");
        }
        boolean pending;
        try (PreparedStatement statement = connection.prepareStatement(
                "select 1 from wondertrade_pending_claims where profile_id = ? limit 1"
        )) {
            statement.setObject(1, profileId);
            try (ResultSet rs = statement.executeQuery()) {
                pending = rs.next();
            }
        }
        if (pending) {
            return new TradeGate(true, 0L);
        }

        int cooldownMinutes = getCooldownMinutes(connection);
        if (cooldownMinutes <= 0) {
            return new TradeGate(false, 0L);
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "select last_trade_at from wondertrade_cooldowns where profile_id = ?"
        )) {
            statement.setObject(1, profileId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return new TradeGate(false, 0L);
                Instant last = rs.getTimestamp(1).toInstant();
                long elapsed = Duration.between(last, Instant.now()).getSeconds();
                long cooldown = cooldownMinutes * 60L;
                return new TradeGate(false, Math.max(0L, cooldown - elapsed));
            }
        }
    }

    public static StatusSnapshot getStatusSnapshot() throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        int pool = 0;
        int shinies = 0;
        int legendaries = 0;
        try (PreparedStatement statement = connection.prepareStatement(
                "select count(*)::int as pool_size, " +
                        "count(*) filter (where shiny)::int as shiny_count, " +
                        "count(*) filter (where legendary)::int as legendary_count " +
                        "from wondertrade_pool"
        )) {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    pool = rs.getInt("pool_size");
                    shinies = rs.getInt("shiny_count");
                    legendaries = rs.getInt("legendary_count");
                }
            }
        }
        return new StatusSnapshot(pool, shinies, legendaries, getCooldownMinutes(connection));
    }

    public static int getCooldownMinutes() throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        return getCooldownMinutes(connection);
    }

    private static int getCooldownMinutes(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select setting_value from wondertrade_settings where setting_key = 'cooldown_minutes'"
        )) {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Math.max(0, Integer.parseInt(rs.getString(1)));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return 60;
    }

    public static void setCooldownMinutes(int minutes) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wondertrade_settings(setting_key, setting_value, updated_at) values ('cooldown_minutes', ?, now()) " +
                        "on conflict (setting_key) do update set setting_value = excluded.setting_value, updated_at = now()"
        )) {
            statement.setString(1, String.valueOf(Math.max(0, minutes)));
            statement.executeUpdate();
        }
    }

    public static long getCooldownRemainingSeconds(UUID profileId) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        if (profileId == null) {
            throw new IllegalArgumentException("WonderTrade cooldown lookup requires a non-null profile_id.");
        }
        int cooldownMinutes = getCooldownMinutes();
        if (cooldownMinutes <= 0) return 0;

        try (PreparedStatement statement = connection.prepareStatement(
                "select last_trade_at from wondertrade_cooldowns where profile_id = ?"
        )) {
            statement.setObject(1, profileId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return 0;
                Instant last = rs.getTimestamp(1).toInstant();
                long elapsed = Duration.between(last, Instant.now()).getSeconds();
                long cooldown = cooldownMinutes * 60L;
                return Math.max(0, cooldown - elapsed);
            }
        }
    }

    public static void markCooldown(UUID profileId, UUID playerUuid) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        markCooldown(connection, profileId, playerUuid);
    }

    private static void markCooldown(Connection connection, UUID profileId, UUID playerUuid) throws Exception {
        if (profileId == null) {
            throw new IllegalArgumentException("WonderTrade cooldowns require a non-null profile_id.");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("WonderTrade cooldowns require a non-null player_uuid.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wondertrade_cooldowns(profile_id, player_uuid, last_trade_at) values (?, ?, now()) " +
                        "on conflict (profile_id) do update set player_uuid = excluded.player_uuid, last_trade_at = now()"
        )) {
            statement.setObject(1, profileId);
            statement.setObject(2, playerUuid);
            statement.executeUpdate();
        }
    }

    public static void savePendingClaim(UUID profileId, UUID playerUuid, String playerUsername, String claimType, JsonObject payload, String displayName) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        savePendingClaim(connection, profileId, playerUuid, playerUsername, claimType, payload, displayName);
    }

    private static void savePendingClaim(Connection connection, UUID profileId, UUID playerUuid, String playerUsername, String claimType, JsonObject payload, String displayName) throws Exception {
        if (profileId == null) {
            throw new IllegalArgumentException("WonderTrade pending claims require a non-null profile_id.");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("WonderTrade pending claims require a non-null player_uuid.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "insert into wondertrade_pending_claims(profile_id, player_uuid, player_username, claim_type, payload, display_name, created_at, updated_at) " +
                        "values (?, ?, ?, ?, ?, ?, now(), now()) " +
                        "on conflict (profile_id) do update set player_uuid = excluded.player_uuid, player_username = excluded.player_username, claim_type = excluded.claim_type, payload = excluded.payload, display_name = excluded.display_name, updated_at = now()"
        )) {
            statement.setObject(1, profileId);
            statement.setObject(2, playerUuid);
            statement.setString(3, playerUsername == null ? playerUuid.toString() : playerUsername);
            statement.setString(4, claimType == null ? "RECEIVED" : claimType);
            statement.setObject(5, jsonb(payload));
            statement.setString(6, displayName == null ? "unknown" : displayName);
            statement.executeUpdate();
        }
    }

    public static PendingClaim getPendingClaim(UUID profileId) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        if (profileId == null) {
            throw new IllegalArgumentException("WonderTrade requires an active profile_id.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "select claim_type, payload::text as payload, display_name from wondertrade_pending_claims where profile_id = ?"
        )) {
            statement.setObject(1, profileId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                JsonObject payload = GSON.fromJson(rs.getString("payload"), JsonObject.class);
                return new PendingClaim(rs.getString("claim_type"), payload, rs.getString("display_name"));
            }
        }
    }

    public static boolean hasPendingClaim(UUID profileId) throws Exception {
        return getPendingClaim(profileId) != null;
    }

    public static void deletePendingClaim(UUID profileId) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        if (profileId == null) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from wondertrade_pending_claims where profile_id = ?"
        )) {
            statement.setObject(1, profileId);
            statement.executeUpdate();
        }
    }

    public record TradeGate(boolean hasPendingClaim, long cooldownRemainingSeconds) {}

    public record StatusSnapshot(int poolSize, int shinyCount, int legendaryCount, int cooldownMinutes) {}

    public record PendingClaim(String claimType, JsonObject payload, String displayName) {}

}
