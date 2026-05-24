package com.champutils.guild;

import com.champutils.database.DatabaseManager;
import com.champutils.network.NetworkServerConfig;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
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

    public static void createGuild(UUID ownerUuid, String ownerName, String name, String tag, Callback callback) {
        if (ownerUuid == null || ownerName == null || name == null || name.isBlank()) {
            callback.done(false, "Invalid guild create request.");
            return;
        }

        String cleanName = cleanName(name);
        String cleanTag = cleanTag(tag);
        UUID guildId = UUID.randomUUID();

        DatabaseManager.executeAsync("create guild " + cleanName, connection -> {
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement existing = connection.prepareStatement(
                        "select guild_id from guild_members where player_uuid = ?"
                )) {
                    existing.setObject(1, ownerUuid);
                    try (ResultSet rs = existing.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            callback.done(false, "You are already in a guild.");
                            return;
                        }
                    }
                }

                try (PreparedStatement insertGuild = connection.prepareStatement(
                        "insert into guilds (id, name, tag, description, owner_uuid, level, xp, created_at, updated_at) " +
                                "values (?, ?, ?, '', ?, 1, 0, now(), now())"
                )) {
                    insertGuild.setObject(1, guildId);
                    insertGuild.setString(2, cleanName);
                    if (cleanTag == null) {
                        insertGuild.setNull(3, Types.VARCHAR);
                    } else {
                        insertGuild.setString(3, cleanTag);
                    }
                    insertGuild.setObject(4, ownerUuid);
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
                callback.done(false, "Failed to create guild. The name or tag may already be taken.");
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
            callback.done(false, "You are not in a guild.");
            return;
        }
        if (!canInvite(inviterGuild.role)) {
            callback.done(false, "Only guild leaders, officers, veterans, and members can invite players.");
            return;
        }
        if (targetUuid == null || targetName == null || targetName.isBlank()) {
            callback.done(false, "Invalid invite target.");
            return;
        }
        if (inviterUuid.equals(targetUuid)) {
            callback.done(false, "You cannot invite yourself.");
            return;
        }

        DatabaseManager.executeAsync("guild invite " + targetUuid, connection -> {
            try {
                connection.setAutoCommit(false);

                try (PreparedStatement existingMember = connection.prepareStatement(
                        "select guild_id from guild_members where player_uuid = ?"
                )) {
                    existingMember.setObject(1, targetUuid);
                    try (ResultSet rs = existingMember.executeQuery()) {
                        if (rs.next()) {
                            connection.rollback();
                            callback.done(false, targetName + " is already in a guild.");
                            return;
                        }
                    }
                }

                upsertPlayer(connection, targetUuid, targetName);

                try (PreparedStatement invite = connection.prepareStatement(
                        "insert into guild_invites (guild_id, invited_uuid, invited_name, invited_by_uuid, expires_at, created_at) " +
                                "values (?, ?, ?, ?, now() + interval '7 days', now()) " +
                                "on conflict (guild_id, invited_uuid) do update set invited_name = excluded.invited_name, invited_by_uuid = excluded.invited_by_uuid, expires_at = excluded.expires_at, created_at = now()"
                )) {
                    invite.setObject(1, inviterGuild.id);
                    invite.setObject(2, targetUuid);
                    invite.setString(3, targetName);
                    invite.setObject(4, inviterUuid);
                    invite.executeUpdate();
                }

                connection.commit();
                callback.done(true, "Invited " + targetName + " to " + inviterGuild.name + ".");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                callback.done(false, "Failed to send guild invite.");
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static void acceptInvite(UUID playerUuid, String playerName, Callback callback) {
        if (cachedGuild(playerUuid) != null) {
            callback.done(false, "You are already in a guild.");
            return;
        }

        DatabaseManager.executeAsync("guild accept " + playerUuid, connection -> {
            try {
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
                loadForPlayerSync(connection, playerUuid);
                callback.done(true, "Joined " + guildName + (guildTag == null ? "" : " [" + guildTag + "]") + ".");
            }
            catch (Exception e) {
                try { connection.rollback(); } catch (Exception ignored) {}
                callback.done(false, "Failed to accept guild invite.");
                throw e;
            }
            finally {
                try { connection.setAutoCommit(true); } catch (Exception ignored) {}
            }
        });
    }

    public static void denyInvites(UUID playerUuid, Callback callback) {
        DatabaseManager.executeAsync("guild deny " + playerUuid, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "delete from guild_invites where invited_uuid = ?"
            )) {
                statement.setObject(1, playerUuid);
                int removed = statement.executeUpdate();
                callback.done(true, removed > 0 ? "Denied your active guild invite(s)." : "You do not have any active guild invites.");
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
