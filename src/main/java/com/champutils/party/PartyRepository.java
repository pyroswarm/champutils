package com.champutils.party;

import com.champutils.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * SQL-authoritative network party storage.
 *
 * Party data used to be saved as one global JSON snapshot. Two backends could both load the
 * same snapshot, make unrelated edits, and whichever save completed last would erase the
 * other backend's change. These normalized tables make every mutation transactional and let
 * PostgreSQL enforce one-party-per-player across the whole network.
 */
final class PartyRepository {
    private PartyRepository() {}

    static void ensureSchemaAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("ensure network party schema", PartyRepository::ensureSchema);
    }

    static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists network_parties (" +
                            "party_id uuid primary key, " +
                            "leader_uuid uuid not null unique, " +
                            "leader_name text not null, " +
                            "created_at timestamptz not null default now(), " +
                            "updated_at timestamptz not null default now()" +
                            ")"
            );
            statement.executeUpdate(
                    "create table if not exists network_party_members (" +
                            "player_uuid uuid primary key, " +
                            "player_name text not null, " +
                            "party_id uuid not null references network_parties(party_id) on delete cascade, " +
                            "joined_at timestamptz not null default now()" +
                            ")"
            );
            statement.executeUpdate(
                    "create table if not exists network_party_invites (" +
                            "target_uuid uuid primary key, " +
                            "target_name text not null default '', " +
                            "party_id uuid not null references network_parties(party_id) on delete cascade, " +
                            "inviter_uuid uuid not null, " +
                            "inviter_name text not null, " +
                            "expires_at timestamptz not null, " +
                            "created_at timestamptz not null default now()" +
                            ")"
            );
            statement.executeUpdate("create index if not exists network_party_members_party_idx on network_party_members (party_id, joined_at)");
            statement.executeUpdate("create index if not exists network_party_invites_party_idx on network_party_invites (party_id)");
            statement.executeUpdate("create index if not exists network_party_invites_expires_idx on network_party_invites (expires_at)");
        }
    }

    static CompletableFuture<Void> importLegacyAsync(List<LegacyPartyRow> parties, List<LegacyInviteRow> invites) {
        if (!DatabaseManager.isEnabled() || parties == null || parties.isEmpty()) return CompletableFuture.completedFuture(null);
        return DatabaseManager.runAsync("migrate legacy network parties", connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                for (LegacyPartyRow party : parties) {
                    if (party == null || party.leaderId() == null) continue;
                    UUID partyId = party.partyId() == null ? party.leaderId() : party.partyId();
                    try (PreparedStatement ps = connection.prepareStatement(
                            "insert into network_parties (party_id, leader_uuid, leader_name, created_at, updated_at) values (?, ?, ?, now(), now()) on conflict do nothing"
                    )) {
                        ps.setObject(1, partyId);
                        ps.setObject(2, party.leaderId());
                        ps.setString(3, safeName(party.leaderName(), party.leaderId()));
                        ps.executeUpdate();
                    }
                    if (party.members() != null) {
                        for (MemberRow member : party.members()) {
                            if (member == null || member.playerId() == null) continue;
                            try (PreparedStatement ps = connection.prepareStatement(
                                    "insert into network_party_members (player_uuid, player_name, party_id, joined_at) values (?, ?, ?, now()) on conflict (player_uuid) do nothing"
                            )) {
                                ps.setObject(1, member.playerId());
                                ps.setString(2, safeName(member.playerName(), member.playerId()));
                                ps.setObject(3, partyId);
                                ps.executeUpdate();
                            }
                        }
                    }
                }
                if (invites != null) {
                    for (LegacyInviteRow invite : invites) {
                        if (invite == null || invite.targetId() == null || invite.partyLeaderId() == null || invite.expiresAtMillis() <= System.currentTimeMillis()) continue;
                        UUID partyId = invite.partyId() == null ? invite.partyLeaderId() : invite.partyId();
                        try (PreparedStatement ps = connection.prepareStatement(
                                "insert into network_party_invites (target_uuid, target_name, party_id, inviter_uuid, inviter_name, expires_at, created_at) " +
                                        "select ?, ?, p.party_id, ?, ?, ?, now() from network_parties p where p.party_id = ? " +
                                        "on conflict (target_uuid) do nothing"
                        )) {
                            ps.setObject(1, invite.targetId());
                            ps.setString(2, safeName(invite.targetName(), invite.targetId()));
                            ps.setObject(3, invite.inviterId() == null ? invite.partyLeaderId() : invite.inviterId());
                            ps.setString(4, safeName(invite.inviterName(), invite.inviterId()));
                            ps.setTimestamp(5, Timestamp.from(Instant.ofEpochMilli(invite.expiresAtMillis())));
                            ps.setObject(6, partyId);
                            ps.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static CompletableFuture<LoadedState> loadAllAsync() {
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(new LoadedState());
        return DatabaseManager.supplyAsync("load network parties", connection -> {
            ensureSchema(connection);
            pruneExpired(connection);
            LoadedState state = new LoadedState();
            try (PreparedStatement ps = connection.prepareStatement(
                    "select party_id::text, leader_uuid::text, leader_name from network_parties order by created_at"
            ); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PartyRow party = new PartyRow(
                            UUID.fromString(rs.getString(1)),
                            UUID.fromString(rs.getString(2)),
                            rs.getString(3)
                    );
                    state.parties.put(party.partyId(), party);
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "select party_id::text, player_uuid::text, player_name from network_party_members order by joined_at"
            ); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID partyId = UUID.fromString(rs.getString(1));
                    state.members.computeIfAbsent(partyId, ignored -> new ArrayList<>()).add(
                            new MemberRow(UUID.fromString(rs.getString(2)), rs.getString(3))
                    );
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "select i.target_uuid::text, i.target_name, i.party_id::text, p.leader_uuid::text, " +
                            "i.inviter_uuid::text, i.inviter_name, i.expires_at " +
                            "from network_party_invites i join network_parties p on p.party_id = i.party_id " +
                            "where i.expires_at > now()"
            ); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp expires = rs.getTimestamp(7);
                    state.invites.put(UUID.fromString(rs.getString(1)), new InviteRow(
                            UUID.fromString(rs.getString(1)),
                            rs.getString(2),
                            UUID.fromString(rs.getString(3)),
                            UUID.fromString(rs.getString(4)),
                            UUID.fromString(rs.getString(5)),
                            rs.getString(6),
                            expires == null ? 0L : expires.toInstant().toEpochMilli()
                    ));
                }
            }
            return state;
        });
    }

    static CompletableFuture<MutationResult> create(UUID ownerId, String ownerName) {
        return mutate("create party " + ownerId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                if (membershipParty(connection, ownerId) != null) {
                    connection.rollback();
                    return MutationResult.fail("You are already in a party.");
                }
                UUID partyId = UUID.randomUUID();
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into network_parties (party_id, leader_uuid, leader_name, created_at, updated_at) values (?, ?, ?, now(), now())"
                )) {
                    ps.setObject(1, partyId);
                    ps.setObject(2, ownerId);
                    ps.setString(3, safeName(ownerName, ownerId));
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into network_party_members (player_uuid, player_name, party_id, joined_at) values (?, ?, ?, now())"
                )) {
                    ps.setObject(1, ownerId);
                    ps.setString(2, safeName(ownerName, ownerId));
                    ps.setObject(3, partyId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("delete from network_party_invites where target_uuid = ?")) {
                    ps.setObject(1, ownerId);
                    ps.executeUpdate();
                }
                connection.commit();
                return MutationResult.ok("Party created. Use /party invite <player> to invite someone.", partyId, ownerId);
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static CompletableFuture<MutationResult> invite(UUID inviterId, String inviterName, UUID targetId, String targetName, long expiresAtMillis, int maxSize) {
        return mutate("invite to party " + targetId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                PartyRow party = leaderPartyForUpdate(connection, inviterId);
                if (party == null) {
                    connection.rollback();
                    return MutationResult.fail("Create a party first with /party create.");
                }
                if (inviterId.equals(targetId)) {
                    connection.rollback();
                    return MutationResult.fail("You cannot invite yourself.");
                }
                if (membershipParty(connection, targetId) != null) {
                    connection.rollback();
                    return MutationResult.fail(safeName(targetName, targetId) + " is already in a party.");
                }
                if (partySize(connection, party.partyId()) >= Math.max(2, maxSize)) {
                    connection.rollback();
                    return MutationResult.fail("Your party is full.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into network_party_invites (target_uuid, target_name, party_id, inviter_uuid, inviter_name, expires_at, created_at) " +
                                "values (?, ?, ?, ?, ?, ?, now()) " +
                                "on conflict (target_uuid) do update set target_name = excluded.target_name, party_id = excluded.party_id, " +
                                "inviter_uuid = excluded.inviter_uuid, inviter_name = excluded.inviter_name, expires_at = excluded.expires_at, created_at = now()"
                )) {
                    ps.setObject(1, targetId);
                    ps.setString(2, safeName(targetName, targetId));
                    ps.setObject(3, party.partyId());
                    ps.setObject(4, inviterId);
                    ps.setString(5, safeName(inviterName, inviterId));
                    ps.setTimestamp(6, Timestamp.from(Instant.ofEpochMilli(expiresAtMillis)));
                    ps.executeUpdate();
                }
                connection.commit();
                return MutationResult.ok("Invited " + safeName(targetName, targetId) + " to your party.", party.partyId(), party.leaderId());
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static CompletableFuture<MutationResult> accept(UUID playerId, String playerName, int maxSize) {
        return mutate("accept party invite " + playerId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                pruneExpired(connection);
                if (membershipParty(connection, playerId) != null) {
                    deleteInvite(connection, playerId);
                    connection.commit();
                    return MutationResult.fail("You are already in a party.");
                }
                InviteWithParty invite = inviteForUpdate(connection, playerId);
                if (invite == null) {
                    connection.rollback();
                    return MutationResult.fail("You do not have a pending party invite.");
                }
                if (partySize(connection, invite.party.partyId()) >= Math.max(2, maxSize)) {
                    deleteInvite(connection, playerId);
                    connection.commit();
                    return MutationResult.fail("That party is full.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "insert into network_party_members (player_uuid, player_name, party_id, joined_at) values (?, ?, ?, now())"
                )) {
                    ps.setObject(1, playerId);
                    ps.setString(2, safeName(playerName, playerId));
                    ps.setObject(3, invite.party.partyId());
                    ps.executeUpdate();
                }
                deleteInvite(connection, playerId);
                try (PreparedStatement ps = connection.prepareStatement("update network_parties set updated_at = now() where party_id = ?")) {
                    ps.setObject(1, invite.party.partyId());
                    ps.executeUpdate();
                }
                connection.commit();
                return MutationResult.ok("You joined the party.", invite.party.partyId(), invite.party.leaderId());
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static CompletableFuture<MutationResult> deny(UUID playerId) {
        return mutate("deny party invite " + playerId, connection -> {
            ensureSchema(connection);
            try (PreparedStatement ps = connection.prepareStatement("delete from network_party_invites where target_uuid = ?")) {
                ps.setObject(1, playerId);
                int removed = ps.executeUpdate();
                return removed > 0 ? MutationResult.ok("Party invite denied.", null, null)
                        : MutationResult.fail("You do not have a pending party invite.");
            }
        });
    }

    static CompletableFuture<MutationResult> leave(UUID playerId) {
        return mutate("leave party " + playerId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                Membership membership = membershipForUpdate(connection, playerId);
                if (membership == null) {
                    connection.rollback();
                    return MutationResult.fail("You are not in a party.");
                }
                if (membership.party.leaderId().equals(playerId)) {
                    try (PreparedStatement ps = connection.prepareStatement("delete from network_parties where party_id = ?")) {
                        ps.setObject(1, membership.party.partyId());
                        ps.executeUpdate();
                    }
                    connection.commit();
                    return MutationResult.ok("Party disbanded.", membership.party.partyId(), playerId);
                }
                try (PreparedStatement ps = connection.prepareStatement("delete from network_party_members where player_uuid = ? and party_id = ?")) {
                    ps.setObject(1, playerId);
                    ps.setObject(2, membership.party.partyId());
                    ps.executeUpdate();
                }
                connection.commit();
                return MutationResult.ok("You left the party.", membership.party.partyId(), membership.party.leaderId());
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static CompletableFuture<MutationResult> disband(UUID leaderId) {
        return mutate("disband party " + leaderId, connection -> {
            ensureSchema(connection);
            PartyRow party = leaderParty(connection, leaderId);
            if (party == null) return MutationResult.fail("Only the party leader can disband the party.");
            try (PreparedStatement ps = connection.prepareStatement("delete from network_parties where party_id = ? and leader_uuid = ?")) {
                ps.setObject(1, party.partyId());
                ps.setObject(2, leaderId);
                int removed = ps.executeUpdate();
                return removed > 0 ? MutationResult.ok("Party disbanded.", party.partyId(), leaderId)
                        : MutationResult.fail("That party no longer exists.");
            }
        });
    }

    static CompletableFuture<MutationResult> kick(UUID leaderId, UUID targetId, String targetName) {
        return mutate("kick party member " + targetId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                PartyRow party = leaderPartyForUpdate(connection, leaderId);
                if (party == null) {
                    connection.rollback();
                    return MutationResult.fail("Only the party leader can kick players.");
                }
                if (leaderId.equals(targetId)) {
                    connection.rollback();
                    return MutationResult.fail("Use /party disband if you want to close the party.");
                }
                UUID targetParty = membershipParty(connection, targetId);
                if (!party.partyId().equals(targetParty)) {
                    connection.rollback();
                    return MutationResult.fail(safeName(targetName, targetId) + " is not in your party.");
                }
                try (PreparedStatement ps = connection.prepareStatement("delete from network_party_members where player_uuid = ? and party_id = ?")) {
                    ps.setObject(1, targetId);
                    ps.setObject(2, party.partyId());
                    ps.executeUpdate();
                }
                connection.commit();
                return MutationResult.ok("Removed " + safeName(targetName, targetId) + " from the party.", party.partyId(), party.leaderId());
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static CompletableFuture<MutationResult> promote(UUID leaderId, UUID targetId, String targetName) {
        return mutate("transfer party leadership " + targetId, connection -> {
            ensureSchema(connection);
            connection.setAutoCommit(false);
            try {
                PartyRow party = leaderPartyForUpdate(connection, leaderId);
                if (party == null) {
                    connection.rollback();
                    return MutationResult.fail("Only the party leader can transfer leadership.");
                }
                if (leaderId.equals(targetId)) {
                    connection.rollback();
                    return MutationResult.fail("You are already the party leader.");
                }
                UUID targetParty = membershipParty(connection, targetId);
                if (!party.partyId().equals(targetParty)) {
                    connection.rollback();
                    return MutationResult.fail(safeName(targetName, targetId) + " is not in your party.");
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "update network_parties set leader_uuid = ?, leader_name = ?, updated_at = now() where party_id = ? and leader_uuid = ?"
                )) {
                    ps.setObject(1, targetId);
                    ps.setString(2, safeName(targetName, targetId));
                    ps.setObject(3, party.partyId());
                    ps.setObject(4, leaderId);
                    if (ps.executeUpdate() <= 0) {
                        connection.rollback();
                        return MutationResult.fail("Party leadership changed before the transfer completed.");
                    }
                }
                connection.commit();
                return MutationResult.ok("Transferred party leadership to " + safeName(targetName, targetId) + ".", party.partyId(), targetId);
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    static void removeInviteAsync(UUID targetId) {
        if (targetId == null || !DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("remove disconnected party invite " + targetId, connection -> {
            ensureSchema(connection);
            deleteInvite(connection, targetId);
        });
    }

    static void pruneExpiredAsync() {
        if (!DatabaseManager.isEnabled()) return;
        DatabaseManager.executeAsync("prune expired party invites", connection -> {
            ensureSchema(connection);
            pruneExpired(connection);
        });
    }

    private static CompletableFuture<MutationResult> mutate(String description, DatabaseManager.SqlSupplier<MutationResult> supplier) {
        if (!DatabaseManager.isEnabled()) return CompletableFuture.completedFuture(MutationResult.fail("Party storage is unavailable."));
        return DatabaseManager.supplyAsync(description, supplier);
    }

    private static PartyRow leaderParty(Connection connection, UUID leaderId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select party_id::text, leader_uuid::text, leader_name from network_parties where leader_uuid = ?"
        )) {
            ps.setObject(1, leaderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PartyRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3));
            }
        }
    }

    private static PartyRow leaderPartyForUpdate(Connection connection, UUID leaderId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select party_id::text, leader_uuid::text, leader_name from network_parties where leader_uuid = ? for update"
        )) {
            ps.setObject(1, leaderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PartyRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3));
            }
        }
    }

    private static UUID membershipParty(Connection connection, UUID playerId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select party_id::text from network_party_members where player_uuid = ?")) {
            ps.setObject(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? UUID.fromString(rs.getString(1)) : null;
            }
        }
    }

    private static Membership membershipForUpdate(Connection connection, UUID playerId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select m.party_id::text, p.leader_uuid::text, p.leader_name from network_party_members m " +
                        "join network_parties p on p.party_id = m.party_id where m.player_uuid = ? for update of p, m"
        )) {
            ps.setObject(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PartyRow party = new PartyRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3));
                return new Membership(playerId, party);
            }
        }
    }

    private static InviteWithParty inviteForUpdate(Connection connection, UUID playerId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "select i.party_id::text, p.leader_uuid::text, p.leader_name from network_party_invites i " +
                        "join network_parties p on p.party_id = i.party_id " +
                        "where i.target_uuid = ? and i.expires_at > now() for update of i, p"
        )) {
            ps.setObject(1, playerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PartyRow party = new PartyRow(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3));
                return new InviteWithParty(party);
            }
        }
    }

    private static int partySize(Connection connection, UUID partyId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("select count(*) from network_party_members where party_id = ?")) {
            ps.setObject(1, partyId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void deleteInvite(Connection connection, UUID targetId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("delete from network_party_invites where target_uuid = ?")) {
            ps.setObject(1, targetId);
            ps.executeUpdate();
        }
    }

    private static void pruneExpired(Connection connection) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("delete from network_party_invites where expires_at <= now()")) {
            ps.executeUpdate();
        }
    }

    private static String safeName(String name, UUID uuid) {
        return name == null || name.isBlank() ? (uuid == null ? "Player" : uuid.toString()) : name.trim();
    }

    static final class LoadedState {
        final Map<UUID, PartyRow> parties = new LinkedHashMap<>();
        final Map<UUID, List<MemberRow>> members = new LinkedHashMap<>();
        final Map<UUID, InviteRow> invites = new LinkedHashMap<>();
    }

    record LegacyPartyRow(UUID partyId, UUID leaderId, String leaderName, List<MemberRow> members) {}
    record LegacyInviteRow(UUID targetId, String targetName, UUID partyId, UUID partyLeaderId, UUID inviterId, String inviterName, long expiresAtMillis) {}
    record PartyRow(UUID partyId, UUID leaderId, String leaderName) {}
    record MemberRow(UUID playerId, String playerName) {}
    record InviteRow(UUID targetId, String targetName, UUID partyId, UUID leaderId, UUID inviterId, String inviterName, long expiresAtMillis) {}
    record MutationResult(boolean success, String message, UUID partyId, UUID leaderId) {
        static MutationResult ok(String message, UUID partyId, UUID leaderId) { return new MutationResult(true, message, partyId, leaderId); }
        static MutationResult fail(String message) { return new MutationResult(false, message, null, null); }
    }
    private record Membership(UUID playerId, PartyRow party) {}
    private record InviteWithParty(PartyRow party) {}
}
