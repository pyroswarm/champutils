package com.champutils.guild;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;
import com.champutils.territory.TerritoryRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GuildRepository {

    public enum Role {
        LEADER,
        OFFICER,
        VETERAN,
        MEMBER,
        RECRUIT;

        @Override
        public String toString() {
            String lower = name().toLowerCase();
            return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        }

        public static Role fromDatabase(String raw) {
            if (raw == null || raw.isBlank()) return RECRUIT;
            String value = raw.trim().toUpperCase();
            if (value.equals("OWNER")) return LEADER;
            try { return Role.valueOf(value); }
            catch (Exception ignored) { return RECRUIT; }
        }

        public int power() {
            return switch (this) {
                case LEADER -> 5;
                case OFFICER -> 4;
                case VETERAN -> 3;
                case MEMBER -> 2;
                case RECRUIT -> 1;
            };
        }
    }

    public static final class GuildSnapshot {
        public UUID id;
        public String name;
        public String tag;
        public String description;
        public UUID ownerUuid;
        public int level;
        public long xp;
        public Role role;
        public int memberCount;
    }

    public static final class GuildXpResult {
        public boolean success;
        public long oldXp;
        public long newXp;
        public int oldLevel;
        public int newLevel;
        public boolean leveledUp;
    }

    @FunctionalInterface
    public interface XpCallback {
        void done(GuildXpResult result);
    }

    public static final class MemberSnapshot {
        public UUID playerUuid;
        public String playerName;
        public Role role;
    }

    private static final Map<UUID, GuildSnapshot> PLAYER_CACHE = new ConcurrentHashMap<>();

    private GuildRepository() {
    }

    private static void guildDebug(String action, String message) {
        System.out.println("[ChampUtils][GuildDebug][" + action + "] " + message);
    }

    private static void guildDebugError(String action, Throwable error, String context) {
        String detail = error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
        System.err.println("[ChampUtils][GuildDebug][" + action + "] FAILED: " + context + " | " + detail);
        error.printStackTrace(System.err);
    }

    public static void createGuild(UUID ownerUuid, String ownerName, String name, String tag, Callback callback) {
        if (ownerUuid == null || ownerName == null || name == null || name.isBlank()) {
            guildDebug("create", "Rejected before DB: invalid request ownerUuid=" + ownerUuid + ", ownerName=" + ownerName + ", name=" + name);
            callback.done(false, "Invalid guild create request.");
            return;
        }

        String cleanName = cleanName(name);
        String cleanTag = cleanTag(tag);
        UUID guildId = UUID.randomUUID();

        DatabaseManager.executeAsync("create guild " + cleanName, connection -> {
            try {
                connection.setAutoCommit(false);
                ensureGuildAccountSchema(connection);
                ensureGuildCreateCooldownTable(connection);

                guildDebug("create", "DB start owner=" + ownerUuid + " name='" + cleanName + "' tag='" + cleanTag + "'");

                long remainingMs = guildCreateCooldownRemainingMillis(connection, ownerUuid);
                if (remainingMs > 0L) {
                    guildDebug("create", "Rejected: owner=" + ownerUuid + " still has cooldown remainingMs=" + remainingMs);
                    connection.rollback();
                    callback.done(false, "You must wait " + formatDuration(remainingMs) + " before creating another guild.");
                    return;
                }

                try (PreparedStatement existing = connection.prepareStatement(
                        "select guild_id from guild_members where player_uuid = ?"
                )) {
                    existing.setObject(1, ownerUuid);
                    try (ResultSet rs = existing.executeQuery()) {
                        if (rs.next()) {
                            guildDebug("create", "Rejected: owner=" + ownerUuid + " is already in guild=" + rs.getObject("guild_id"));
                            connection.rollback();
                            callback.done(false, "You are already in a guild.");
                            return;
                        }
                    }
                }

                try (PreparedStatement insertGuild = connection.prepareStatement(
                        "insert into guilds (id, name, tag, description, owner_uuid, owner_player_uuid, level, xp, created_at, updated_at) " +
                                "values (?, ?, ?, '', ?, ?, 1, 0, now(), now())"
                )) {
                    insertGuild.setObject(1, guildId);
                    insertGuild.setString(2, cleanName);
                    if (cleanTag == null) {
                        insertGuild.setNull(3, Types.VARCHAR);
                    } else {
                        insertGuild.setString(3, cleanTag);
                    }
                    insertGuild.setObject(4, ownerUuid);
                    insertGuild.setObject(5, ownerUuid);
                    insertGuild.executeUpdate();
                }

                try (PreparedStatement insertMember = connection.prepareStatement(
                        "insert into guild_members (guild_id, player_uuid, player_name, role, joined_at) values (?, ?, ?, 'LEADER', now())"
                )) {
                    insertMember.setObject(1, guildId);
                    insertMember.setObject(2, ownerUuid);
                    insertMember.setString(3, ownerName);
                    insertMember.executeUpdate();
                }

                upsertPlayer(connection, ownerUuid, ownerName);

                connection.commit();
                guildDebug("create", "SUCCESS guildId=" + guildId + " owner=" + ownerUuid + " name='" + cleanName + "' tag='" + cleanTag + "'");
                GuildSnapshot snapshot = new GuildSnapshot();
                snapshot.id = guildId;
                snapshot.name = cleanName;
                snapshot.tag = cleanTag;
                snapshot.description = "";
                snapshot.ownerUuid = ownerUuid;
                snapshot.level = 1;
                snapshot.xp = 0;
                snapshot.role = Role.LEADER;
                snapshot.memberCount = 1;
                PLAYER_CACHE.put(ownerUuid, snapshot);
                try {
                    com.champutils.territory.TerritoryRepository.ensureGuildTerritory(
                            guildId,
                            cleanName,
                            null,
                            (territorySuccess, territoryMessage) -> {}
                    );
                }
                catch (Exception ignored) {
                    // Guild creation should never fail because territory allocation can be retried with /gterritory create.
                }
                callback.done(true, "Created guild " + cleanName + ". A guild territory will be assigned automatically.");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                String detail = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                guildDebugError("create", e, "owner=" + ownerUuid + " name='" + cleanName + "' tag='" + cleanTag + "'");
                callback.done(false, "Failed to create guild. " + detail);
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static void loadForPlayer(UUID uuid, String username) {
        if (uuid == null || !DatabaseManager.isEnabled()) {
            return;
        }

        DatabaseManager.executeAsync("load guild for " + uuid, connection -> {
            ensureGuildAccountSchema(connection);
            loadForPlayerSync(connection, uuid);
            upsertPlayer(connection, uuid, username == null || username.isBlank() ? uuid.toString() : username);
        });
    }

    public static GuildSnapshot cachedGuild(UUID uuid) {
        return uuid == null ? null : PLAYER_CACHE.get(uuid);
    }

    public static void invite(UUID inviterUuid, String inviterName, UUID targetUuid, String targetName, Callback callback) {
        GuildSnapshot inviterGuild = cachedGuild(inviterUuid);
        if (inviterGuild == null) {
            guildDebug("invite", "Rejected before DB: inviter=" + inviterUuid + " has no cached guild. Try /guild debugreload or relog if this is wrong.");
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (!canInvite(inviterGuild.role)) {
            guildDebug("invite", "Rejected before DB: inviter=" + inviterUuid + " role=" + inviterGuild.role + " cannot invite.");
            callback.done(false, "Only guild leaders, officers, veterans, and members can invite players.");
            return;
        }
        if (targetUuid == null || targetName == null || targetName.isBlank()) {
            guildDebug("invite", "Rejected before DB: invalid target targetUuid=" + targetUuid + ", targetName=" + targetName);
            callback.done(false, "Invalid invite target.");
            return;
        }
        if (inviterUuid.equals(targetUuid)) {
            guildDebug("invite", "Rejected before DB: inviter attempted self invite uuid=" + inviterUuid);
            callback.done(false, "You cannot invite yourself.");
            return;
        }

        DatabaseManager.executeAsync("guild invite " + targetUuid, connection -> {
            try {
                guildDebug("invite", "DB start guild=" + inviterGuild.id + " inviter=" + inviterUuid + " target=" + targetUuid + " targetName='" + targetName + "'");
                ensureGuildAccountSchema(connection);
                ensureGuildInviteTable(connection);
                guildDebug("invite", describeGuildInviteColumns(connection));
                connection.setAutoCommit(false);

                try (PreparedStatement existingMember = connection.prepareStatement(
                        "select guild_id from guild_members where player_uuid = ?"
                )) {
                    existingMember.setObject(1, targetUuid);
                    try (ResultSet rs = existingMember.executeQuery()) {
                        if (rs.next()) {
                            guildDebug("invite", "Rejected: target=" + targetUuid + " is already in guild=" + rs.getObject("guild_id"));
                            connection.rollback();
                            callback.done(false, targetName + " is already in a guild.");
                            return;
                        }
                    }
                }

                upsertPlayer(connection, targetUuid, targetName);

                insertGuildInvite(connection, inviterGuild.id, targetUuid, targetName, inviterUuid);

                connection.commit();
                guildDebug("invite", "SUCCESS guild=" + inviterGuild.id + " inviter=" + inviterUuid + " target=" + targetUuid);
                callback.done(true, "Invited " + targetName + " to " + inviterGuild.name + ".");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                guildDebugError("invite", e, "guild=" + inviterGuild.id + " inviter=" + inviterUuid + " target=" + targetUuid + " targetName='" + targetName + "'");
                callback.done(false, "Failed to send guild invite. " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Inserts guild invites while satisfying old live beta schemas that still have
     * NOT NULL legacy columns. The canonical columns are invited_uuid and
     * invited_by_uuid, but we mirror values into any legacy columns that still
     * exist so invites keep working even before manual DB cleanup.
     */
    private static void insertGuildInvite(java.sql.Connection connection, UUID guildId, UUID targetUuid, String targetName, UUID inviterUuid) throws Exception {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        columns.add("guild_id"); values.add(guildId);
        columns.add("invited_uuid"); values.add(targetUuid);
        columns.add("invited_name"); values.add(targetName == null ? "" : targetName);
        columns.add("invited_by_uuid"); values.add(inviterUuid);
        columns.add("expires_at");
        columns.add("created_at");

        if (columnExists(connection, "guild_invites", "invited_player_uuid")) {
            columns.add("invited_player_uuid"); values.add(targetUuid);
        }
        // Do NOT mirror account/player UUIDs into invited_profile_id. On older beta schemas
        // this column references player_profiles(id), so writing a player UUID here violates
        // guild_invites_invited_profile_id_fkey. The canonical invite target is invited_uuid.
        // Leave invited_profile_id null when it exists.
        if (columnExists(connection, "guild_invites", "invited_profile_id") && isColumnNotNullable(connection, "guild_invites", "invited_profile_id")) {
            UUID targetProfileId = findActiveProfileIdForPlayer(connection, targetUuid);
            if (targetProfileId != null) {
                columns.add("invited_profile_id"); values.add(targetProfileId);
            }
        }
        if (columnExists(connection, "guild_invites", "inviter_player_uuid")) {
            columns.add("inviter_player_uuid"); values.add(inviterUuid);
        }
        if (columnExists(connection, "guild_invites", "inviter_uuid")) {
            columns.add("inviter_uuid"); values.add(inviterUuid);
        }
        if (columnExists(connection, "guild_invites", "invite_message")) {
            columns.add("invite_message"); values.add("");
        }

        StringBuilder placeholders = new StringBuilder();
        for (String column : columns) {
            if (placeholders.length() > 0) placeholders.append(", ");
            if ("expires_at".equals(column)) {
                placeholders.append("now() + interval '7 days'");
            } else if ("created_at".equals(column)) {
                placeholders.append("now()");
            } else {
                placeholders.append("?");
            }
        }

        List<String> deletePredicates = new ArrayList<>();
        deletePredicates.add("invited_uuid = ?");
        if (columnExists(connection, "guild_invites", "invited_player_uuid")) deletePredicates.add("invited_player_uuid = ?");
        // invited_profile_id is legacy/profile-scoped; account-based guild invites use invited_uuid.
        // Do not compare it to the player UUID.
        String deleteSql = "delete from guild_invites where guild_id = ? and (" + String.join(" or ", deletePredicates) + ")";
        try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
            delete.setObject(1, guildId);
            for (int i = 0; i < deletePredicates.size(); i++) {
                delete.setObject(i + 2, targetUuid);
            }
            delete.executeUpdate();
        }

        String sql = "insert into guild_invites (" + String.join(", ", columns) + ") values (" + placeholders + ")";
        guildDebug("invite", "Insert SQL=" + sql + " values=" + values);

        try (PreparedStatement invite = connection.prepareStatement(sql)) {
            int index = 1;
            for (Object value : values) {
                invite.setObject(index++, value);
            }
            invite.executeUpdate();
        }
    }


    private static boolean isColumnNotNullable(java.sql.Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select is_nullable from information_schema.columns where table_schema = current_schema() and table_name = ? and column_name = ?"
        )) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && "NO".equalsIgnoreCase(rs.getString("is_nullable"));
            }
        }
    }

    private static UUID findActiveProfileIdForPlayer(java.sql.Connection connection, UUID playerUuid) {
        String[] sqls = new String[] {
                "select id from player_profiles where player_uuid = ? order by last_used_at desc nulls last, created_at desc nulls last limit 1",
                "select id from player_profiles where player_id = ? order by created_at desc nulls last limit 1",
                "select id from player_profiles where owner_uuid = ? order by created_at desc nulls last limit 1"
        };
        for (String sql : sqls) {
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, playerUuid);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        Object value = rs.getObject(1);
                        if (value instanceof UUID) return (UUID) value;
                    }
                }
            } catch (Exception ignored) {
                // Live beta schemas have varied profile owner column names. Try the next known shape.
            }
        }
        return null;
    }

    public static void acceptInvite(UUID playerUuid, String playerName, Callback callback) {
        if (cachedGuild(playerUuid) != null) {
            guildDebug("accept", "Rejected before DB: player=" + playerUuid + " already has cached guild=" + cachedGuild(playerUuid).id);
            callback.done(false, "You are already in a guild.");
            return;
        }

        DatabaseManager.executeAsync("guild accept " + playerUuid, connection -> {
            try {
                guildDebug("accept", "DB start player=" + playerUuid + " name='" + playerName + "'");
                ensureGuildAccountSchema(connection);
                ensureGuildInviteTable(connection);
                guildDebug("accept", describeGuildInviteColumns(connection));
                connection.setAutoCommit(false);

                UUID guildId;
                String guildName;
                String guildTag;
                try (PreparedStatement invite = connection.prepareStatement(
                        "select gi.guild_id, g.name, g.tag from guild_invites gi join guilds g on g.id = gi.guild_id " +
                                "where gi.invited_uuid = ? and gi.expires_at > now() order by gi.created_at desc limit 1"
                )) {
                    invite.setObject(1, playerUuid);
                    try (ResultSet rs = invite.executeQuery()) {
                        if (!rs.next()) {
                            guildDebug("accept", "Rejected: no active invite found for player=" + playerUuid);
                            connection.rollback();
                            callback.done(false, "You do not have any active guild invites.");
                            return;
                        }
                        guildId = (UUID) rs.getObject("guild_id");
                        guildName = rs.getString("name");
                        guildTag = rs.getString("tag");
                    }
                }

                try (PreparedStatement existingMember = connection.prepareStatement(
                        "select guild_id from guild_members where player_uuid = ?"
                )) {
                    existingMember.setObject(1, playerUuid);
                    try (ResultSet rs = existingMember.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            callback.done(false, "You are already in a guild.");
                            return;
                        }
                    }
                }

                upsertPlayer(connection, playerUuid, playerName);

                try (PreparedStatement insertMember = connection.prepareStatement(
                        "insert into guild_members (guild_id, player_uuid, player_name, role, joined_at) values (?, ?, ?, 'RECRUIT', now())"
                )) {
                    insertMember.setObject(1, guildId);
                    insertMember.setObject(2, playerUuid);
                    insertMember.setString(3, playerName);
                    insertMember.executeUpdate();
                }

                try (PreparedStatement deleteInvites = connection.prepareStatement(
                        "delete from guild_invites where invited_uuid = ?"
                )) {
                    deleteInvites.setObject(1, playerUuid);
                    deleteInvites.executeUpdate();
                }

                connection.commit();
                guildDebug("accept", "SUCCESS player=" + playerUuid + " joined guild=" + guildId + " name='" + guildName + "'");
                loadForPlayerSync(connection, playerUuid);
                callback.done(true, "Joined " + guildName + (guildTag == null ? "" : " [" + guildTag + "]") + ".");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                guildDebugError("accept", e, "player=" + playerUuid + " name='" + playerName + "'");
                callback.done(false, "Failed to accept guild invite. " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static void denyInvites(UUID playerUuid, Callback callback) {
        DatabaseManager.executeAsync("guild deny " + playerUuid, connection -> {
            try {
                guildDebug("deny", "DB start player=" + playerUuid);
                ensureGuildInviteTable(connection);
                guildDebug("deny", describeGuildInviteColumns(connection));
                try (PreparedStatement statement = connection.prepareStatement(
                        "delete from guild_invites where invited_uuid = ?"
                )) {
                    statement.setObject(1, playerUuid);
                    int removed = statement.executeUpdate();
                    guildDebug("deny", "SUCCESS player=" + playerUuid + " removedInvites=" + removed);
                    callback.done(true, removed > 0 ? "Denied your active guild invite(s)." : "You do not have any active guild invites.");
                }
            }
            catch (Exception e) {
                guildDebugError("deny", e, "player=" + playerUuid);
                callback.done(false, "Failed to deny guild invite. " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                throw e;
            }
        });
    }

    public static void leave(UUID playerUuid, String playerName, Callback callback) {
        GuildSnapshot guild = cachedGuild(playerUuid);
        if (guild == null) {
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (guild.role == Role.LEADER) {
            callback.done(false, "Guild leaders cannot leave yet. Promote another leader later when /guild transfer is added, or ask for disband next.");
            return;
        }

        DatabaseManager.executeAsync("guild leave " + playerUuid, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from guild_members where player_uuid = ?"
            )) {
                statement.setObject(1, playerUuid);
                statement.executeUpdate();
                PLAYER_CACHE.remove(playerUuid);
                callback.done(true, "You left " + guild.name + ".");
            }
        });
    }

    public static void kick(UUID actorUuid, UUID targetUuid, String targetName, Callback callback) {
        GuildSnapshot actorGuild = cachedGuild(actorUuid);
        if (actorGuild == null) {
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (!canKick(actorGuild.role)) {
            callback.done(false, "Only guild leaders and officers can kick members.");
            return;
        }
        if (actorUuid.equals(targetUuid)) {
            callback.done(false, "Use /guild leave instead.");
            return;
        }

        DatabaseManager.executeAsync("guild kick " + targetUuid, connection -> {
            Role targetRole = findMemberRole(connection, actorGuild.id, targetUuid);
            if (targetRole == null) {
                callback.done(false, targetName + " is not in your guild.");
                return;
            }
            if (targetRole == Role.LEADER || actorGuild.role.power() <= targetRole.power()) {
                callback.done(false, "You cannot kick that guild member.");
                return;
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from guild_members where guild_id = ? and player_uuid = ?"
            )) {
                statement.setObject(1, actorGuild.id);
                statement.setObject(2, targetUuid);
                statement.executeUpdate();
                PLAYER_CACHE.remove(targetUuid);
                callback.done(true, "Kicked " + targetName + " from the guild.");
            }
        });
    }

    public static void promote(UUID actorUuid, UUID targetUuid, String targetName, Callback callback) {
        updateRole(actorUuid, targetUuid, targetName, true, callback);
    }

    public static void demote(UUID actorUuid, UUID targetUuid, String targetName, Callback callback) {
        updateRole(actorUuid, targetUuid, targetName, false, callback);
    }

    public static void transferOwnership(UUID actorUuid, UUID expectedGuildId, UUID targetUuid, String targetName, Callback callback) {
        GuildSnapshot actorGuild = cachedGuild(actorUuid);
        if (actorGuild == null) {
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (actorGuild.role != Role.LEADER) {
            callback.done(false, "Only guild owners can transfer guild ownership.");
            return;
        }
        if (expectedGuildId == null || !actorGuild.id.equals(expectedGuildId)) {
            callback.done(false, "That guild transfer is no longer valid.");
            return;
        }
        if (targetUuid == null || actorUuid.equals(targetUuid)) {
            callback.done(false, "Invalid guild transfer target.");
            return;
        }

        DatabaseManager.executeAsync("guild transfer " + targetUuid, connection -> {
            try {
                connection.setAutoCommit(false);

                Role targetRole = findMemberRole(connection, actorGuild.id, targetUuid);
                if (targetRole == null) {
                    connection.rollback();
                    callback.done(false, targetName + " is not in your guild.");
                    return;
                }
                if (targetRole == Role.LEADER) {
                    connection.rollback();
                    callback.done(false, targetName + " is already the guild owner.");
                    return;
                }

                try (PreparedStatement guildUpdate = connection.prepareStatement(
                        "update guilds set owner_uuid = ?, owner_player_uuid = ?, updated_at = now() where id = ? and owner_uuid = ?"
                )) {
                    guildUpdate.setObject(1, targetUuid);
                    guildUpdate.setObject(2, targetUuid);
                    guildUpdate.setObject(3, actorGuild.id);
                    guildUpdate.setObject(4, actorUuid);
                    if (guildUpdate.executeUpdate() <= 0) {
                        connection.rollback();
                        callback.done(false, "Guild ownership changed before this transfer could complete.");
                        return;
                    }
                }

                try (PreparedStatement oldOwner = connection.prepareStatement(
                        "update guild_members set role = 'OFFICER' where guild_id = ? and player_uuid = ?"
                )) {
                    oldOwner.setObject(1, actorGuild.id);
                    oldOwner.setObject(2, actorUuid);
                    oldOwner.executeUpdate();
                }

                try (PreparedStatement newOwner = connection.prepareStatement(
                        "update guild_members set role = 'LEADER' where guild_id = ? and player_uuid = ?"
                )) {
                    newOwner.setObject(1, actorGuild.id);
                    newOwner.setObject(2, targetUuid);
                    newOwner.executeUpdate();
                }

                connection.commit();
                refreshCachedGuildMembers(connection, actorGuild.id);
                callback.done(true, "Transferred guild ownership to " + targetName + ".");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                callback.done(false, "Failed to transfer guild ownership.");
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static void disbandGuild(UUID actorUuid, UUID expectedGuildId, String guildName, Callback callback) {
        GuildSnapshot actorGuild = cachedGuild(actorUuid);
        if (actorGuild == null) {
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (actorGuild.role != Role.LEADER) {
            callback.done(false, "Only guild owners can disband a guild.");
            return;
        }
        if (expectedGuildId == null || !actorGuild.id.equals(expectedGuildId)) {
            callback.done(false, "That guild disband confirmation is no longer valid.");
            return;
        }

        DatabaseManager.executeAsync("guild disband " + actorGuild.id, connection -> {
            List<UUID> memberIds = new ArrayList<>();
            try {
                connection.setAutoCommit(false);
                ensureGuildAccountSchema(connection);
                ensureGuildCreateCooldownTable(connection);

                try (PreparedStatement members = connection.prepareStatement(
                        "select player_uuid from guild_members where guild_id = ?"
                )) {
                    members.setObject(1, actorGuild.id);
                    try (ResultSet rs = members.executeQuery()) {
                        while (rs.next()) {
                            memberIds.add((UUID) rs.getObject("player_uuid"));
                        }
                    }
                }

                try (PreparedStatement territory = connection.prepareStatement(
                        "update territories set generation_state = 'DELETING', is_public = false, allow_visitors = false, updated_at = now() where owner_type = 'GUILD' and owner_id = ?"
                )) {
                    territory.setString(1, actorGuild.id.toString());
                    territory.executeUpdate();
                }

                try (PreparedStatement deleteMembers = connection.prepareStatement(
                        "delete from guild_members where guild_id = ?"
                )) {
                    deleteMembers.setObject(1, actorGuild.id);
                    deleteMembers.executeUpdate();
                }

                try (PreparedStatement deleteInvites = connection.prepareStatement(
                        "delete from guild_invites where guild_id = ?"
                )) {
                    deleteInvites.setObject(1, actorGuild.id);
                    deleteInvites.executeUpdate();
                }

                try (PreparedStatement cooldown = connection.prepareStatement(
                        "insert into guild_create_cooldowns (player_uuid, disbanded_at) values (?, now()) " +
                                "on conflict (player_uuid) do update set disbanded_at = excluded.disbanded_at"
                )) {
                    cooldown.setObject(1, actorUuid);
                    cooldown.executeUpdate();
                }

                try (PreparedStatement deleteGuild = connection.prepareStatement(
                        "delete from guilds where id = ? and owner_uuid = ?"
                )) {
                    deleteGuild.setObject(1, actorGuild.id);
                    deleteGuild.setObject(2, actorUuid);
                    if (deleteGuild.executeUpdate() <= 0) {
                        connection.rollback();
                        callback.done(false, "Guild ownership changed before this disband could complete.");
                        return;
                    }
                }

                connection.commit();
                for (UUID memberId : memberIds) {
                    PLAYER_CACHE.remove(memberId);
                }
                try {
                    TerritoryRepository.removeCachedForOwner(TerritoryRepository.OwnerType.GUILD, actorGuild.id.toString());
                }
                catch (Exception ignored) {
                }
                callback.done(true, "Disbanded " + (guildName == null || guildName.isBlank() ? actorGuild.name : guildName) + ".");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                callback.done(false, "Failed to disband guild.");
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    private static void updateRole(UUID actorUuid, UUID targetUuid, String targetName, boolean promote, Callback callback) {
        GuildSnapshot actorGuild = cachedGuild(actorUuid);
        if (actorGuild == null) {
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (actorGuild.role != Role.LEADER && actorGuild.role != Role.OFFICER) {
            callback.done(false, "Only guild leaders and officers can promote or demote members.");
            return;
        }
        if (actorUuid.equals(targetUuid)) {
            callback.done(false, "You cannot change your own guild role.");
            return;
        }

        DatabaseManager.executeAsync((promote ? "guild promote " : "guild demote ") + targetUuid, connection -> {
            Role targetRole = findMemberRole(connection, actorGuild.id, targetUuid);
            if (targetRole == null) {
                callback.done(false, targetName + " is not in your guild.");
                return;
            }

            if (actorGuild.role.power() <= targetRole.power()) {
                callback.done(false, "You cannot change that guild member's role.");
                return;
            }

            Role newRole;
            if (promote) {
                newRole = switch (targetRole) {
                    case RECRUIT -> Role.MEMBER;
                    case MEMBER -> Role.VETERAN;
                    case VETERAN -> Role.OFFICER;
                    case OFFICER -> null;
                    case LEADER -> null;
                };
                if (newRole == null) {
                    callback.done(false, targetName + " cannot be promoted further. Leader transfer will be added separately.");
                    return;
                }
                if (actorGuild.role != Role.LEADER && newRole.power() >= actorGuild.role.power()) {
                    callback.done(false, "Officers can only promote players below officer rank.");
                    return;
                }
            } else {
                newRole = switch (targetRole) {
                    case LEADER -> Role.OFFICER;
                    case OFFICER -> Role.VETERAN;
                    case VETERAN -> Role.MEMBER;
                    case MEMBER -> Role.RECRUIT;
                    case RECRUIT -> null;
                };
                if (newRole == null) {
                    callback.done(false, targetName + " cannot be demoted further.");
                    return;
                }
                if (actorGuild.role != Role.LEADER && targetRole.power() >= actorGuild.role.power()) {
                    callback.done(false, "Officers can only demote players below officer rank.");
                    return;
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    "update guild_members set role = ? where guild_id = ? and player_uuid = ?"
            )) {
                statement.setString(1, newRole.name());
                statement.setObject(2, actorGuild.id);
                statement.setObject(3, targetUuid);
                statement.executeUpdate();
            }
            loadForPlayerSync(connection, targetUuid);
            callback.done(true, (promote ? "Promoted " : "Demoted ") + targetName + " to " + newRole.name() + ".");
        });
    }

    public static void addXp(UUID guildId, long amount) {
        addXp(guildId, null, null, "manual_or_system", amount, null);
    }

    public static void addXp(UUID guildId, UUID playerUuid, String playerName, String source, long amount, XpCallback callback) {
        if (guildId == null || amount <= 0) {
            if (callback != null) callback.done(new GuildXpResult());
            return;
        }

        String cleanSource = source == null || source.isBlank() ? "manual_or_system" : source.trim();

        DatabaseManager.executeAsync("add guild xp " + guildId, connection -> {
            GuildXpResult result = new GuildXpResult();
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement select = connection.prepareStatement(
                        "select xp, level from guilds where id = ? for update"
                )) {
                    select.setObject(1, guildId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            if (callback != null) callback.done(result);
                            return;
                        }
                        result.oldXp = rs.getLong("xp");
                        result.oldLevel = Math.max(1, rs.getInt("level"));
                    }
                }

                result.newXp = Math.max(0L, result.oldXp + amount);
                result.newLevel = GuildConfig.levelForXp(result.newXp);
                result.leveledUp = result.newLevel > result.oldLevel;
                result.success = true;

                try (PreparedStatement log = connection.prepareStatement(
                        "insert into guild_xp_log (guild_id, player_uuid, source, amount, created_at) values (?, ?, ?, ?, now())"
                )) {
                    log.setObject(1, guildId);
                    if (playerUuid == null) log.setNull(2, Types.OTHER); else log.setObject(2, playerUuid);
                    log.setString(3, cleanSource);
                    log.setLong(4, amount);
                    log.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "update guilds set xp = ?, level = ?, updated_at = now() where id = ?"
                )) {
                    statement.setLong(1, result.newXp);
                    statement.setInt(2, result.newLevel);
                    statement.setObject(3, guildId);
                    statement.executeUpdate();
                }

                connection.commit();
                refreshCachedGuildMembers(connection, guildId);
                if (callback != null) callback.done(result);
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                if (callback != null) callback.done(result);
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static List<MemberSnapshot> cachedOnlineMembers(Iterable<? extends net.minecraft.server.level.ServerPlayer> onlinePlayers, UUID guildId) {
        List<MemberSnapshot> members = new ArrayList<>();
        if (guildId == null || onlinePlayers == null) {
            return members;
        }
        for (net.minecraft.server.level.ServerPlayer player : onlinePlayers) {
            GuildSnapshot snapshot = cachedGuild(player.getUUID());
            if (snapshot != null && guildId.equals(snapshot.id)) {
                MemberSnapshot member = new MemberSnapshot();
                member.playerUuid = player.getUUID();
                member.playerName = player.getGameProfile().getName();
                member.role = snapshot.role;
                members.add(member);
            }
        }
        return members;
    }


    private static boolean columnExists(java.sql.Connection connection, String table, String column) throws Exception {
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

    private static void executeQuietly(java.sql.Connection connection, String sql) {
        java.sql.Savepoint savepoint = null;
        try {
            if (!connection.getAutoCommit()) {
                savepoint = connection.setSavepoint();
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.executeUpdate();
            }
        } catch (Exception ignored) {
            if (savepoint != null) {
                try { connection.rollback(savepoint); } catch (Exception ignoredRollback) {}
            }
        } finally {
            if (savepoint != null) {
                try { connection.releaseSavepoint(savepoint); } catch (Exception ignoredRelease) {}
            }
        }
    }

    /**
     * Repairs older profile-migration guild schemas before any guild query runs.
     * Guild membership is account-based, so all current guild logic uses player_uuid.
     */
    private static void ensureGuildAccountSchema(java.sql.Connection connection) throws Exception {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists players (" +
                            "uuid uuid primary key, " +
                            "username text not null, " +
                            "playtime_seconds bigint not null default 0, " +
                            "first_seen timestamptz not null default now(), " +
                            "last_seen timestamptz not null default now(), " +
                            "last_server_id text" +
                            ")"
            );
            statement.executeUpdate("alter table players add column if not exists last_server_id text");

            statement.executeUpdate(
                    "create table if not exists guilds (" +
                            "id uuid primary key, " +
                            "name text not null unique, " +
                            "tag text unique, " +
                            "description text not null default '', " +
                            "owner_uuid uuid not null, " +
                            "level integer not null default 1, " +
                            "xp bigint not null default 0, " +
                            "created_at timestamptz not null default now(), " +
                            "updated_at timestamptz not null default now()" +
                            ")"
            );

            statement.executeUpdate("alter table guilds add column if not exists owner_uuid uuid");
            statement.executeUpdate("alter table guilds add column if not exists owner_profile_id uuid");
            statement.executeUpdate("alter table guilds add column if not exists owner_player_uuid uuid");
            statement.executeUpdate("alter table guilds add column if not exists description text not null default ''");
            statement.executeUpdate("alter table guilds add column if not exists level integer not null default 1");
            statement.executeUpdate("alter table guilds add column if not exists xp bigint not null default 0");
            statement.executeUpdate("alter table guilds add column if not exists created_at timestamptz not null default now()");
            statement.executeUpdate("alter table guilds add column if not exists updated_at timestamptz not null default now()");
            statement.executeUpdate("alter table guilds alter column owner_profile_id drop not null");
            statement.executeUpdate("alter table guilds alter column owner_player_uuid drop not null");
            // Guilds are account-based, not profile-based. Older profile-era rows may have stored
            // owner_profile_id/profile_id values; translate those through player_profiles.player_uuid
            // before falling back to raw UUIDs.
            statement.executeUpdate("do $$ begin if to_regclass('public.player_profiles') is not null then execute 'update guilds g set owner_uuid = p.player_uuid, owner_player_uuid = p.player_uuid from player_profiles p where g.owner_profile_id = p.id and (g.owner_uuid is null or g.owner_uuid = g.owner_profile_id or g.owner_player_uuid is null or g.owner_player_uuid = g.owner_profile_id)'; end if; end $$");
            statement.executeUpdate("update guilds set owner_uuid = coalesce(owner_uuid, owner_player_uuid, owner_profile_id) where owner_uuid is null");
            statement.executeUpdate("update guilds set owner_player_uuid = coalesce(owner_player_uuid, owner_uuid) where owner_player_uuid is null");

            statement.executeUpdate(
                    "create table if not exists guild_members (" +
                            "guild_id uuid not null references guilds(id) on delete cascade, " +
                            "player_uuid uuid not null, " +
                            "player_name text not null, " +
                            "role text not null, " +
                            "joined_at timestamptz not null default now(), " +
                            "primary key (guild_id, player_uuid), " +
                            "unique (player_uuid)" +
                            ")"
            );

            statement.executeUpdate("alter table guild_members add column if not exists player_uuid uuid");
            statement.executeUpdate("alter table guild_members add column if not exists profile_id uuid");
            statement.executeUpdate("alter table guild_members add column if not exists player_name text not null default ''");
            statement.executeUpdate("alter table guild_members add column if not exists role text not null default 'RECRUIT'");
            statement.executeUpdate("alter table guild_members add column if not exists joined_at timestamptz not null default now()");
            statement.executeUpdate("alter table guild_members alter column profile_id drop not null");
            statement.executeUpdate("do $$ begin if to_regclass('public.player_profiles') is not null then execute 'update guild_members gm set player_uuid = p.player_uuid from player_profiles p where gm.profile_id = p.id and (gm.player_uuid is null or gm.player_uuid = gm.profile_id)'; end if; end $$");
            statement.executeUpdate("update guild_members set player_uuid = profile_id where player_uuid is null");
            statement.executeUpdate("delete from guild_members where player_uuid is null");
            statement.executeUpdate("update guild_members set role = 'LEADER' where upper(role) = 'OWNER'");
            statement.executeUpdate("update guild_members set role = 'RECRUIT' where role is null or trim(role) = ''");
            statement.executeUpdate("alter table guild_members alter column player_uuid set not null");

            statement.executeUpdate("create unique index if not exists guild_members_player_uuid_unique on guild_members (player_uuid)");

            // Final compatibility pass for databases created from early beta SQL. Some installs
            // still had guild_members.profile_id but no player_uuid, which made /guild create fail
            // on the first membership lookup. Keep legacy columns nullable so account-based guilds
            // can write player_uuid without being blocked by old profile constraints.
            if (!columnExists(connection, "guild_members", "player_uuid")) {
                statement.executeUpdate("alter table guild_members add column player_uuid uuid");
            }
            if (columnExists(connection, "guild_members", "profile_id")) {
                executeQuietly(connection, "update guild_members set player_uuid = profile_id where player_uuid is null");
                executeQuietly(connection, "alter table guild_members alter column profile_id drop not null");
            }
            executeQuietly(connection, "alter table guild_members alter column player_uuid set not null");

            validateRequiredColumns(connection, "guilds", "id", "name", "owner_uuid", "owner_player_uuid", "level", "xp", "created_at", "updated_at");
            validateRequiredColumns(connection, "guild_members", "guild_id", "player_uuid", "player_name", "role", "joined_at");
        }
    }

    private static void validateRequiredColumns(java.sql.Connection connection, String table, String... columns) throws Exception {
        List<String> missing = new ArrayList<>();
        for (String column : columns) {
            if (!columnExists(connection, table, column)) {
                missing.add(column);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Database table " + table + " is missing required columns " + missing + ". The running jar may be pointed at the wrong database/schema or an old migration may not have run.");
        }
    }

    private static String describeGuildInviteColumns(java.sql.Connection connection) throws Exception {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "select column_name, is_nullable from information_schema.columns where table_schema = current_schema() and table_name = 'guild_invites' order by ordinal_position"
        ); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                columns.add(rs.getString("column_name") + " nullable=" + rs.getString("is_nullable"));
            }
        }
        return "guild_invites columns=" + columns;
    }

    private static void ensureGuildInviteTable(java.sql.Connection connection) throws Exception {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists guild_invites (" +
                            "guild_id uuid not null references guilds(id) on delete cascade, " +
                            "invited_uuid uuid not null, " +
                            "invited_name text not null default '', " +
                            "invited_by_uuid uuid not null, " +
                            "expires_at timestamptz not null default (now() + interval '7 days'), " +
                            "created_at timestamptz not null default now(), " +
                            "primary key (guild_id, invited_uuid)" +
                            ")"
            );
            statement.executeUpdate("alter table guild_invites add column if not exists invited_uuid uuid");
            statement.executeUpdate("alter table guild_invites add column if not exists invited_name text not null default ''");
            statement.executeUpdate("alter table guild_invites add column if not exists invited_by_uuid uuid");
            statement.executeUpdate("alter table guild_invites add column if not exists expires_at timestamptz not null default (now() + interval '7 days')");
            statement.executeUpdate("alter table guild_invites add column if not exists created_at timestamptz not null default now()");

            // Invite schema changed during beta. Runtime code now uses invited_uuid/invited_by_uuid,
            // while some live databases still have legacy NOT NULL columns such as
            // invited_player_uuid, invited_profile_id, or inviter_player_uuid. If those columns
            // stay NOT NULL, inserts that correctly populate invited_uuid still fail with a null
            // legacy column. Mirror values both ways, then make legacy columns nullable so old
            // rows remain readable without blocking new account-based invites.
            if (columnExists(connection, "guild_invites", "invited_player_uuid")) {
                executeQuietly(connection, "update guild_invites set invited_uuid = invited_player_uuid where invited_uuid is null and invited_player_uuid is not null");
                executeQuietly(connection, "update guild_invites set invited_player_uuid = invited_uuid where invited_player_uuid is null and invited_uuid is not null");
                executeQuietly(connection, "alter table guild_invites alter column invited_player_uuid drop not null");
            }
            if (columnExists(connection, "guild_invites", "invited_profile_id")) {
                // invited_profile_id belongs to the old profile-based schema and may have an FK to player_profiles(id).
                // Keep it nullable and never use it as the account UUID source for new invites.
                executeQuietly(connection, "alter table guild_invites alter column invited_profile_id drop not null");
            }
            if (columnExists(connection, "guild_invites", "inviter_player_uuid")) {
                executeQuietly(connection, "update guild_invites set invited_by_uuid = inviter_player_uuid where invited_by_uuid is null and inviter_player_uuid is not null");
                executeQuietly(connection, "update guild_invites set inviter_player_uuid = invited_by_uuid where inviter_player_uuid is null and invited_by_uuid is not null");
                executeQuietly(connection, "alter table guild_invites alter column inviter_player_uuid drop not null");
            }
            if (columnExists(connection, "guild_invites", "inviter_uuid")) {
                executeQuietly(connection, "update guild_invites set invited_by_uuid = inviter_uuid where invited_by_uuid is null and inviter_uuid is not null");
                executeQuietly(connection, "alter table guild_invites alter column inviter_uuid drop not null");
            }
            if (columnExists(connection, "guild_invites", "invite_message")) {
                executeQuietly(connection, "alter table guild_invites alter column invite_message drop not null");
            }

            statement.executeUpdate("delete from guild_invites where invited_uuid is null");
            validateRequiredColumns(connection, "guild_invites", "guild_id", "invited_uuid", "invited_name", "invited_by_uuid", "expires_at", "created_at");
            guildDebug("schema", describeGuildInviteColumns(connection));
        }
    }


    private static void ensureGuildCreateCooldownTable(java.sql.Connection connection) throws Exception {
        try (java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists guild_create_cooldowns (" +
                            "player_uuid uuid primary key, " +
                            "disbanded_at timestamptz not null default now()" +
                            ")"
            );

            // Existing beta databases may already have this table with only profile_id.
            // Guilds are account/player scoped, so repair the table before any cooldown query runs.
            // Do NOT use website/auth profiles here; game guild identity is guild_members.player_uuid.
            statement.executeUpdate("alter table guild_create_cooldowns add column if not exists player_uuid uuid");
            statement.executeUpdate("alter table guild_create_cooldowns add column if not exists disbanded_at timestamptz not null default now()");

            if (columnExists(connection, "guild_create_cooldowns", "profile_id")) {
                // Old beta schema used profile_id as the primary key. Rebuild the tiny cooldown table
                // into the account/player-scoped shape instead of trying to alter primary-key columns.
                statement.executeUpdate("create table if not exists guild_create_cooldowns_v2 (player_uuid uuid primary key, disbanded_at timestamptz not null default now())");
                executeQuietly(connection,
                        "insert into guild_create_cooldowns_v2(player_uuid, disbanded_at) " +
                                "select gm.player_uuid, max(c.disbanded_at) " +
                                "from guild_create_cooldowns c " +
                                "join guild_members gm on gm.profile_id = c.profile_id " +
                                "where gm.player_uuid is not null " +
                                "group by gm.player_uuid " +
                                "on conflict (player_uuid) do update set disbanded_at = greatest(guild_create_cooldowns_v2.disbanded_at, excluded.disbanded_at)");
                executeQuietly(connection, "insert into guild_create_cooldowns_v2(player_uuid, disbanded_at) select player_uuid, max(disbanded_at) from guild_create_cooldowns where player_uuid is not null group by player_uuid on conflict (player_uuid) do update set disbanded_at = greatest(guild_create_cooldowns_v2.disbanded_at, excluded.disbanded_at)");
                statement.executeUpdate("drop table guild_create_cooldowns");
                statement.executeUpdate("alter table guild_create_cooldowns_v2 rename to guild_create_cooldowns");
            }
            if (columnExists(connection, "guild_create_cooldowns", "owner_uuid")) {
                executeQuietly(connection, "update guild_create_cooldowns set player_uuid = owner_uuid where player_uuid is null");
                // Legacy column may be constrained; do not mutate it during runtime repair.
            }
            if (columnExists(connection, "guild_create_cooldowns", "owner_player_uuid")) {
                executeQuietly(connection, "update guild_create_cooldowns set player_uuid = owner_player_uuid where player_uuid is null");
                // Legacy column may be constrained; do not mutate it during runtime repair.
            }

            statement.executeUpdate("delete from guild_create_cooldowns where player_uuid is null");
            statement.executeUpdate(
                    "delete from guild_create_cooldowns a using guild_create_cooldowns b " +
                            "where a.ctid < b.ctid and a.player_uuid = b.player_uuid"
            );
            statement.executeUpdate("create unique index if not exists guild_create_cooldowns_player_uuid_unique on guild_create_cooldowns (player_uuid)");
            statement.executeUpdate("alter table guild_create_cooldowns alter column player_uuid set not null");
            validateRequiredColumns(connection, "guild_create_cooldowns", "player_uuid", "disbanded_at");
        }
    }

    private static long guildCreateCooldownRemainingMillis(java.sql.Connection connection, UUID playerUuid) throws Exception {
        int cooldownMinutes = GuildConfig.GUILD_CREATION == null ? 30 : Math.max(0, GuildConfig.GUILD_CREATION.disbandCreateCooldownMinutes);
        if (cooldownMinutes <= 0 || playerUuid == null) {
            return 0L;
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "select disbanded_at from guild_create_cooldowns where player_uuid = ?"
        )) {
            statement.setObject(1, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return 0L;
                }
                Timestamp timestamp = rs.getTimestamp("disbanded_at");
                if (timestamp == null) {
                    return 0L;
                }
                Instant allowedAt = timestamp.toInstant().plus(Duration.ofMinutes(cooldownMinutes));
                long remaining = Duration.between(Instant.now(), allowedAt).toMillis();
                if (remaining <= 0L) {
                    try (PreparedStatement cleanup = connection.prepareStatement(
                            "delete from guild_create_cooldowns where player_uuid = ?"
                    )) {
                        cleanup.setObject(1, playerUuid);
                        cleanup.executeUpdate();
                    }
                    return 0L;
                }
                return remaining;
            }
        }
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(1L, (millis + 999L) / 1000L);
        long minutes = seconds / 60L;
        long remainderSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return seconds + " second" + (seconds == 1L ? "" : "s");
        }
        if (remainderSeconds == 0L) {
            return minutes + " minute" + (minutes == 1L ? "" : "s");
        }
        return minutes + " minute" + (minutes == 1L ? "" : "s") + " " + remainderSeconds + " second" + (remainderSeconds == 1L ? "" : "s");
    }

    public static String cleanName(String input) {
        String value = input == null ? "" : input.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        if (value.length() > 20) {
            value = value.substring(0, 20);
        }
        return value;
    }

    public static String cleanTag(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String value = input.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (value.length() > 5) {
            value = value.substring(0, 5);
        }
        return value.isBlank() ? null : value;
    }

    public static boolean canInvite(Role role) {
        return role == Role.LEADER || role == Role.OFFICER || role == Role.VETERAN || role == Role.MEMBER;
    }

    public static boolean canKick(Role role) {
        return role == Role.LEADER || role == Role.OFFICER;
    }

    public static boolean canManageGuildTerritory(Role role) {
        return role == Role.LEADER || role == Role.OFFICER;
    }

    public static boolean canBuildInGuildTerritory(Role role) {
        return role == Role.LEADER || role == Role.OFFICER || role == Role.VETERAN;
    }

    public static boolean canUseGuildTerritory(Role role) {
        return role != null;
    }

    public static boolean canAccessNormalGuildStorage(Role role) {
        return role != null;
    }

    public static boolean canAccessVeteranGuildStorage(Role role) {
        return role == Role.LEADER || role == Role.OFFICER || role == Role.VETERAN;
    }

    public static boolean canUseDangerousGuildActions(Role role) {
        return role == Role.LEADER;
    }

    private static Role findMemberRole(java.sql.Connection connection, UUID guildId, UUID playerUuid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select role from guild_members where guild_id = ? and player_uuid = ?"
        )) {
            statement.setObject(1, guildId);
            statement.setObject(2, playerUuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return Role.fromDatabase(rs.getString("role"));
            }
        }
    }

    private static void loadForPlayerSync(java.sql.Connection connection, UUID uuid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "select g.id, g.name, g.tag, g.description, g.owner_uuid, g.level, g.xp, gm.role, " +
                        "(select count(*) from guild_members gm2 where gm2.guild_id = g.id) as member_count " +
                        "from guild_members gm join guilds g on g.id = gm.guild_id where gm.player_uuid = ?"
        )) {
            statement.setObject(1, uuid);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    PLAYER_CACHE.remove(uuid);
                    return;
                }

                GuildSnapshot snapshot = new GuildSnapshot();
                snapshot.id = (UUID) rs.getObject("id");
                snapshot.name = rs.getString("name");
                snapshot.tag = rs.getString("tag");
                snapshot.description = rs.getString("description");
                snapshot.ownerUuid = (UUID) rs.getObject("owner_uuid");
                snapshot.level = Math.max(1, rs.getInt("level"));
                snapshot.xp = Math.max(0L, rs.getLong("xp"));
                snapshot.role = Role.fromDatabase(rs.getString("role"));
                snapshot.memberCount = Math.max(1, rs.getInt("member_count"));
                PLAYER_CACHE.put(uuid, snapshot);
            }
        }
    }


    private static void refreshCachedGuildMembers(java.sql.Connection connection, UUID guildId) throws Exception {
        if (guildId == null) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "select player_uuid from guild_members where guild_id = ?"
        )) {
            statement.setObject(1, guildId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    loadForPlayerSync(connection, (UUID) rs.getObject("player_uuid"));
                }
            }
        }
    }
    private static void upsertPlayer(java.sql.Connection connection, UUID uuid, String username) throws Exception {
        try (PreparedStatement player = connection.prepareStatement(
                "insert into players (uuid, username, last_seen, last_server_id) values (?, ?, now(), ?) " +
                        "on conflict (uuid) do update set username = excluded.username, last_seen = now(), last_server_id = excluded.last_server_id"
        )) {
            player.setObject(1, uuid);
            player.setString(2, username == null || username.isBlank() ? uuid.toString() : username);
            player.setString(3, NetworkServerConfig.serverId());
            player.executeUpdate();
        }
    }

    @FunctionalInterface
    public interface Callback {
        void done(boolean success, String message);
    }
}
