package com.champutils.auction;

import com.champutils.database.DatabaseManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

public final class AuctionHouseRepository {

    private static final Gson GSON = new Gson();
    private static final CopyOnWriteArrayList<AuctionListingSummary> ACTIVE_LISTING_CACHE = new CopyOnWriteArrayList<>();
    private static volatile long activeListingCacheMs = 0L;

    private AuctionHouseRepository() {}

    public static UUID createItemListing(UUID sellerUuid, String sellerUsername, String title, String description, long price, int quantity, JsonObject payload, int listingDurationDays) throws Exception {
        return createListing(sellerUuid, sellerUsername, "ITEM", title, description, price, Math.max(1, quantity), payload, listingDurationDays);
    }

    public static UUID createPokemonListing(UUID sellerUuid, String sellerUsername, String title, String description, long price, JsonObject payload, int listingDurationDays) throws Exception {
        return createListing(sellerUuid, sellerUsername, "POKEMON", title, description, price, 1, payload, listingDurationDays);
    }

    private static UUID createListing(UUID sellerUuid, String sellerUsername, String kind, String title, String description, long price, int quantity, JsonObject payload, int listingDurationDays) throws Exception {
        ensureSchema(DatabaseManager.getConnection());

        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "insert into auction_listings " +
                        "(seller_profile_id, seller_player_uuid, seller_name, listing_type, price, " +
                        "seller_uuid, seller_username, listing_kind, title, description, unit_price, quantity, payload, status, created_at, updated_at, expires_at) " +
                        "values (?, (select player_uuid from player_profiles where id = ?), ?, ?, ?, " +
                        "?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now(), now() + (? * interval '1 day')) " +
                        "returning id"
        )) {
            statement.setObject(1, sellerUuid);
            statement.setObject(2, sellerUuid);
            statement.setString(3, safe(sellerUsername, sellerUuid.toString()));
            statement.setString(4, kind);
            statement.setLong(5, price);
            statement.setString(6, sellerUuid.toString());
            statement.setString(7, safe(sellerUsername, sellerUuid.toString()));
            statement.setString(8, kind);
            statement.setString(9, safe(title, "Auction Listing"));
            statement.setString(10, description == null ? "" : description);
            statement.setLong(11, price);
            statement.setInt(12, Math.max(1, quantity));
            statement.setObject(13, jsonb(payload));
            statement.setInt(14, Math.max(1, Math.min(365, listingDurationDays)));

            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    activeListingCacheMs = 0L;
                    ACTIVE_LISTING_CACHE.clear();
                    return UUID.fromString(rs.getString("id"));
                }
            }
        }

        throw new IllegalStateException("Auction listing insert did not return an id.");
    }

    public static int countActiveListings(UUID sellerUuid) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select count(*) as total from auction_listings where seller_uuid = ? and status = 'ACTIVE' and expires_at > now()"
        )) {
            statement.setString(1, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        }
        return 0;
    }

    public static List<AuctionListingSummary> cachedActiveListings(int limit) {
        int capped = Math.max(1, Math.min(54, limit));
        if (ACTIVE_LISTING_CACHE.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(ACTIVE_LISTING_CACHE.subList(0, Math.min(capped, ACTIVE_LISTING_CACHE.size())));
    }

    public static long activeListingCacheAgeMs() {
        long loaded = activeListingCacheMs;
        return loaded <= 0L ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - loaded);
    }

    public static List<AuctionListingSummary> fetchActiveListings(int limit) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        List<AuctionListingSummary> listings = new ArrayList<>();
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, seller_uuid, seller_username, listing_kind, title, unit_price, quantity, created_at, expires_at, payload::text as payload " +
                        "from auction_listings where status = 'ACTIVE' and expires_at > now() order by created_at desc limit ?"
        )) {
            statement.setInt(1, Math.max(1, Math.min(54, limit)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) listings.add(readListingSummary(rs));
            }
        }
        ACTIVE_LISTING_CACHE.clear();
        ACTIVE_LISTING_CACHE.addAll(listings);
        activeListingCacheMs = System.currentTimeMillis();
        return listings;
    }

    public static List<AuctionListingSummary> fetchSellerActiveListings(UUID sellerUuid, int limit) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        List<AuctionListingSummary> listings = new ArrayList<>();
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, seller_uuid, seller_username, listing_kind, title, unit_price, quantity, created_at, expires_at, payload::text as payload " +
                        "from auction_listings where seller_uuid = ? and status = 'ACTIVE' and expires_at > now() order by created_at desc limit ?"
        )) {
            statement.setString(1, sellerUuid.toString());
            statement.setInt(2, Math.max(1, Math.min(54, limit)));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) listings.add(readListingSummary(rs));
            }
        }
        return listings;
    }

    public static AuctionListingSummary fetchSellerActiveListing(UUID sellerUuid, UUID listingId) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, seller_uuid, seller_username, listing_kind, title, unit_price, quantity, created_at, expires_at, payload::text as payload " +
                        "from auction_listings where id = ? and seller_uuid = ? and status = 'ACTIVE' and expires_at > now() limit 1"
        )) {
            statement.setObject(1, listingId);
            statement.setString(2, sellerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return readListingSummary(rs);
            }
        }
        return null;
    }



    public static AuctionListingSummary fetchActiveListing(UUID listingId) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, seller_uuid, seller_username, listing_kind, title, unit_price, quantity, created_at, expires_at, payload::text as payload " +
                        "from auction_listings where id = ? and status = 'ACTIVE' and expires_at > now() limit 1"
        )) {
            statement.setObject(1, listingId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return readListingSummary(rs);
            }
        }
        return null;
    }

    public static PurchaseRecord purchaseActiveListingClaimed(UUID listingId, UUID buyerUuid, String buyerUsername) throws Exception {
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            AuctionListingSummary listing = null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, seller_uuid, seller_username, listing_kind, title, unit_price, quantity, created_at, expires_at, payload::text as payload " +
                            "from auction_listings where id = ? and status = 'ACTIVE' and expires_at > now() for update"
            )) {
                statement.setObject(1, listingId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) listing = readListingSummary(rs);
                }
            }

            if (listing == null) {
                connection.rollback();
                return null;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "update auction_listings set status = 'SOLD', buyer_profile_id = ?, sold_at = now(), updated_at = now() where id = ? and status = 'ACTIVE'"
            )) {
                statement.setObject(1, buyerUuid);
                statement.setObject(2, listing.id);
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return null;
                }
            }

            UUID purchaseId = null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into auction_purchases " +
                            "(listing_id, buyer_uuid, buyer_username, seller_uuid, seller_username, listing_kind, title, quantity, total_price, payload, delivery_status, purchased_at, claimed_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CLAIMED', now(), now()) returning id"
            )) {
                statement.setObject(1, listing.id);
                statement.setString(2, buyerUuid.toString());
                statement.setString(3, safe(buyerUsername, buyerUuid.toString()));
                statement.setString(4, listing.sellerUuid.toString());
                statement.setString(5, safe(listing.sellerUsername, listing.sellerUuid.toString()));
                statement.setString(6, listing.kind);
                statement.setString(7, listing.title);
                statement.setInt(8, listing.quantity);
                statement.setLong(9, listing.price);
                statement.setObject(10, jsonb(listing.payload));
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) purchaseId = UUID.fromString(rs.getString("id"));
                }
            }

            if (purchaseId == null) {
                connection.rollback();
                throw new IllegalStateException("Auction purchase insert did not return an id.");
            }

            connection.commit();
            PurchaseRecord record = new PurchaseRecord();
            record.purchaseId = purchaseId;
            record.listing = listing;
            return record;
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    public static boolean cancelActiveListing(UUID sellerUuid, UUID listingId) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "update auction_listings set status = 'CANCELLED', updated_at = now() where id = ? and seller_uuid = ? and status = 'ACTIVE' and expires_at > now()"
        )) {
            statement.setObject(1, listingId);
            statement.setString(2, sellerUuid.toString());
            return statement.executeUpdate() == 1;
        }
    }

    public static boolean cancelActiveListingToPendingClaim(UUID sellerUuid, AuctionListingSummary listing) throws Exception {
        if (sellerUuid == null || listing == null || listing.id == null) return false;
        Connection connection = DatabaseManager.getConnection();
        ensureSchema(connection);
        boolean previousAutoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "update auction_listings set status = 'CANCELLED', updated_at = now() where id = ? and seller_uuid = ? and status = 'ACTIVE' and expires_at > now()"
            )) {
                statement.setObject(1, listing.id);
                statement.setString(2, sellerUuid.toString());
                if (statement.executeUpdate() != 1) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into auction_purchases " +
                            "(listing_id, buyer_uuid, buyer_username, seller_uuid, seller_username, listing_kind, title, quantity, total_price, payload, delivery_status, purchased_at) " +
                            "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', now())"
            )) {
                statement.setObject(1, listing.id);
                statement.setString(2, sellerUuid.toString());
                statement.setString(3, safe(listing.sellerUsername, sellerUuid.toString()));
                statement.setString(4, sellerUuid.toString());
                statement.setString(5, safe(listing.sellerUsername, sellerUuid.toString()));
                statement.setString(6, listing.kind == null || listing.kind.isBlank() ? "ITEM" : listing.kind);
                statement.setString(7, safe(listing.title, "Canceled Auction Item"));
                statement.setInt(8, Math.max(1, listing.quantity));
                statement.setLong(9, Math.max(1L, listing.price));
                statement.setObject(10, jsonb(listing.payload));
                statement.executeUpdate();
            }
            connection.commit();
            activeListingCacheMs = 0L;
            ACTIVE_LISTING_CACHE.clear();
            return true;
        } catch (Exception e) {
            try { connection.rollback(); } catch (Exception ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(previousAutoCommit); } catch (Exception ignored) {}
        }
    }

    private static AuctionListingSummary readListingSummary(ResultSet rs) throws Exception {
        AuctionListingSummary listing = new AuctionListingSummary();
        listing.id = UUID.fromString(rs.getString("id"));
        listing.sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
        listing.sellerUsername = rs.getString("seller_username");
        listing.kind = rs.getString("listing_kind");
        listing.title = rs.getString("title");
        listing.price = rs.getLong("unit_price");
        listing.quantity = rs.getInt("quantity");
        listing.createdAt = String.valueOf(rs.getObject("created_at"));
        listing.expiresAt = String.valueOf(rs.getObject("expires_at"));
        String payloadText = rs.getString("payload");
        listing.payload = payloadText == null ? new JsonObject() : GSON.fromJson(payloadText, JsonObject.class);
        return listing;
    }

    public static PendingPurchase fetchOldestPendingPurchase(UUID buyerUuid) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select id, listing_kind, title, quantity, total_price, payload::text as payload from auction_purchases " +
                        "where buyer_uuid = ? and delivery_status = 'PENDING' order by purchased_at asc limit 1"
        )) {
            statement.setString(1, buyerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                PendingPurchase purchase = new PendingPurchase();
                purchase.id = UUID.fromString(rs.getString("id"));
                purchase.kind = rs.getString("listing_kind");
                purchase.title = rs.getString("title");
                purchase.quantity = rs.getInt("quantity");
                purchase.totalPrice = rs.getLong("total_price");
                purchase.payload = GSON.fromJson(rs.getString("payload"), JsonObject.class);
                return purchase;
            }
        }
    }

    public static int countPendingPurchases(UUID buyerUuid) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select count(*) as total from auction_purchases where buyer_uuid = ? and delivery_status = 'PENDING'"
        )) {
            statement.setString(1, buyerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        }
        return 0;
    }

    public static int countPendingPokemonPurchases(UUID buyerUuid) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "select count(*) as total from auction_purchases where buyer_uuid = ? and delivery_status = 'PENDING' and upper(listing_kind) = 'POKEMON'"
        )) {
            statement.setString(1, buyerUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) return rs.getInt("total");
            }
        }
        return 0;
    }

    public static boolean markPurchaseClaimed(UUID purchaseId) throws Exception {
        ensureSchema(DatabaseManager.getConnection());
        try (PreparedStatement statement = DatabaseManager.getConnection().prepareStatement(
                "update auction_purchases set delivery_status = 'CLAIMED', claimed_at = now() where id = ? and delivery_status = 'PENDING'"
        )) {
            statement.setObject(1, purchaseId);
            return statement.executeUpdate() == 1;
        }
    }

    private static synchronized void ensureSchema(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists auction_listings (" +
                        "id uuid primary key default gen_random_uuid(), seller_uuid text not null, seller_username text not null, " +
                        "listing_kind text not null, title text not null, description text, unit_price bigint not null check (unit_price > 0), " +
                        "quantity integer not null default 1 check (quantity > 0), payload jsonb not null default '{}'::jsonb, " +
                        "status text not null default 'ACTIVE', created_at timestamp with time zone default now(), " +
                        "updated_at timestamp with time zone default now(), expires_at timestamp with time zone default (now() + interval '7 days'))"
        )) { statement.executeUpdate(); }

        String[] listingColumns = {
                "alter table auction_listings add column if not exists seller_uuid text",
                "alter table auction_listings add column if not exists seller_username text not null default ''",
                "alter table auction_listings add column if not exists listing_kind text not null default 'ITEM'",
                "alter table auction_listings add column if not exists title text not null default 'Auction Listing'",
                "alter table auction_listings add column if not exists description text",
                "alter table auction_listings add column if not exists unit_price bigint not null default 1",
                "alter table auction_listings add column if not exists quantity integer not null default 1",
                "alter table auction_listings add column if not exists payload jsonb not null default '{}'::jsonb",
                "alter table auction_listings add column if not exists status text not null default 'ACTIVE'",
                "alter table auction_listings add column if not exists updated_at timestamptz not null default now()",
                "alter table auction_listings add column if not exists seller_profile_id uuid",
                "alter table auction_listings add column if not exists seller_player_uuid uuid",
                "alter table auction_listings add column if not exists seller_name text not null default ''",
                "alter table auction_listings add column if not exists listing_type text not null default 'ITEM'",
                "alter table auction_listings add column if not exists price numeric not null default 1",
                "alter table auction_listings add column if not exists sold_at timestamptz",
                "alter table auction_listings add column if not exists buyer_profile_id uuid",
                "alter table auction_listings add column if not exists expires_at timestamptz not null default (now() + interval '7 days')"
        };
        for (String sql : listingColumns) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) { statement.executeUpdate(); }
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "create table if not exists auction_purchases (" +
                        "id uuid primary key default gen_random_uuid(), listing_id uuid references auction_listings(id) on delete set null, " +
                        "buyer_uuid text not null, buyer_username text not null, seller_uuid text not null, seller_username text not null, " +
                        "listing_kind text not null, title text not null, quantity integer not null default 1 check (quantity > 0), " +
                        "total_price bigint not null check (total_price > 0), payload jsonb not null default '{}'::jsonb, " +
                        "delivery_status text not null default 'PENDING', purchased_at timestamp with time zone default now(), claimed_at timestamp with time zone)"
        )) { statement.executeUpdate(); }
    }

    private static PGobject jsonb(JsonObject object) throws Exception {
        PGobject pg = new PGobject();
        pg.setType("jsonb");
        pg.setValue(GSON.toJson(object == null ? new JsonObject() : object));
        return pg;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static final class AuctionListingSummary {
        public UUID id;
        public UUID sellerUuid;
        public String sellerUsername;
        public String kind;
        public String title;
        public long price;
        public int quantity;
        public String createdAt;
        public String expiresAt;
        public JsonObject payload;
    }

    public static final class PurchaseRecord {
        public UUID purchaseId;
        public AuctionListingSummary listing;
    }

    public static final class PendingPurchase {
        public UUID id;
        public String kind;
        public String title;
        public int quantity;
        public long totalPrice;
        public JsonObject payload;
    }
}
