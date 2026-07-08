package com.champutils.network;

import com.champutils.database.DatabaseManager;
import com.champutils.guild.GuildRepository;
import com.champutils.profession.ProfessionNotificationSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NetworkEventManager {
    public static final String TYPE_GLOBAL_CHAT = "GLOBAL_CHAT";
    public static final String TYPE_GUILD_CHAT = "GUILD_CHAT";
    public static final String TYPE_PARTY_CHAT = "PARTY_CHAT";
    public static final String TYPE_BROADCAST = "BROADCAST";
    public static final String TYPE_QUEUE_BROADCAST = "QUEUE_BROADCAST";
    public static final String TYPE_CACHE_INVALIDATE = "CACHE_INVALIDATE";
    public static final String TYPE_PRIVATE_MESSAGE = "PRIVATE_MESSAGE";
    public static final String TYPE_TPA_REQUEST = "TPA_REQUEST";
    public static final String TYPE_TPA_ACCEPT = "TPA_ACCEPT";
    public static final String TYPE_TPA_DENY = "TPA_DENY";

    private static volatile long lastSeenEventId = -1L;
    private static volatile boolean pollInFlight = false;
    private static int tickCounter = 0;

    private NetworkEventManager() {
    }

    public static void ensureSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "create table if not exists network_events (" +
                            "id bigserial primary key, " +
                            "event_type text not null, " +
                            "scope text not null default 'GLOBAL', " +
                            "origin_server_id text not null, " +
                            "origin_player_uuid uuid, " +
                            "origin_player_name text not null default '', " +
                            "message text not null default '', " +
                            "payload jsonb not null default '{}'::jsonb, " +
                            "created_at timestamptz not null default now(), " +
                            "expires_at timestamptz not null default (now() + interval '10 minutes')" +
                            ")"
            );
            statement.executeUpdate("create index if not exists network_events_id_idx on network_events (id)");
            statement.executeUpdate("create index if not exists network_events_expires_idx on network_events (expires_at)");
            statement.executeUpdate("create index if not exists network_events_type_scope_idx on network_events (event_type, scope, id)");
        }
    }

    public static void publishGlobalChat(ServerPlayer sender, String formattedMessage) {
        publishChat(sender, TYPE_GLOBAL_CHAT, "GLOBAL", formattedMessage);
    }

    public static void publishGuildChat(ServerPlayer sender, UUID guildId, String formattedMessage) {
        if (guildId == null) {
            return;
        }
        publishChat(sender, TYPE_GUILD_CHAT, "GUILD:" + guildId, formattedMessage);
    }

    public static void publishPartyChat(ServerPlayer sender, UUID partyOwnerId, String formattedMessage) {
        if (partyOwnerId == null) {
            return;
        }
        publishChat(sender, TYPE_PARTY_CHAT, "PARTY:" + partyOwnerId, formattedMessage);
    }

    public static void publishBroadcast(Component message) {
        publishSystem(TYPE_BROADCAST, "GLOBAL", componentText(message));
    }

    public static void publishBroadcastText(String message) {
        publishSystem(TYPE_BROADCAST, "GLOBAL", message);
    }

    public static void publishQueueBroadcast(Component message) {
        publishSystem(TYPE_QUEUE_BROADCAST, "GLOBAL", componentText(message));
    }

    public static void publishPrivateMessage(ServerPlayer sender, String target, String message) {
        if (sender == null || target == null || target.isBlank() || message == null || message.isBlank()) return;
        String scope = target.contains("-") ? "PM_UUID:" + target.trim() : "PM_NAME:" + target.trim().toLowerCase(Locale.ROOT);
        publish(TYPE_PRIVATE_MESSAGE, scope, sender.getUUID(), sender.getGameProfile().getName(), message);
    }

    public static void publishTpaRequest(ServerPlayer requester, UUID targetUuid) {
        if (requester == null || targetUuid == null) return;
        String message = requester.getUUID() + "\t" + requester.getGameProfile().getName() + "\t" + NetworkServerConfig.serverId();
        publish(TYPE_TPA_REQUEST, "TPA_TARGET:" + targetUuid, requester.getUUID(), requester.getGameProfile().getName(), message);
    }

    public static void publishTpaAccept(ServerPlayer target, UUID requesterUuid) {
        if (target == null || requesterUuid == null) return;
        String message = target.getUUID()
                + "\t" + target.getGameProfile().getName()
                + "\t" + NetworkServerConfig.serverId()
                + "\t" + target.serverLevel().dimension().location()
                + "\t" + target.getX()
                + "\t" + target.getY()
                + "\t" + target.getZ()
                + "\t" + target.getYRot()
                + "\t" + target.getXRot();
        publish(TYPE_TPA_ACCEPT, "TPA_REQUESTER:" + requesterUuid, target.getUUID(), target.getGameProfile().getName(), message);
    }

    public static void publishTpaDeny(ServerPlayer target, UUID requesterUuid) {
        if (target == null || requesterUuid == null) return;
        publish(TYPE_TPA_DENY, "TPA_REQUESTER:" + requesterUuid, target.getUUID(), target.getGameProfile().getName(), target.getGameProfile().getName());
    }

    public static void publishCacheInvalidation(String stateKey, UUID ownerId) {
        if (stateKey == null || stateKey.isBlank() || ownerId == null) {
            return;
        }
        publish(TYPE_CACHE_INVALIDATE, stateKey.trim().toUpperCase(Locale.ROOT), null, "", ownerId.toString());
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !NetworkServerConfig.get().enableNetworkEventBus || !DatabaseManager.isEnabled()) {
            return;
        }
        tickCounter++;
        int intervalTicks = Math.max(20, NetworkServerConfig.get().networkEventPollSeconds * 20);
        if (tickCounter < intervalTicks || pollInFlight) {
            return;
        }
        tickCounter = 0;
        pollInFlight = true;

        DatabaseManager.supplyAsync("poll network events", connection -> {
            if (lastSeenEventId < 0L) {
                lastSeenEventId = currentMaxId(connection);
                return List.<EventRecord>of();
            }
            List<EventRecord> events = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "select id, event_type, scope, origin_server_id, origin_player_uuid::text, origin_player_name, message " +
                            "from network_events where id > ? and expires_at > now() order by id asc limit 100"
            )) {
                statement.setLong(1, lastSeenEventId);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        events.add(new EventRecord(
                                rs.getLong(1),
                                rs.getString(2),
                                rs.getString(3),
                                rs.getString(4),
                                rs.getString(5),
                                rs.getString(6),
                                rs.getString(7)
                        ));
                    }
                }
            }
            return events;
        }).whenComplete((events, error) -> server.execute(() -> {
            pollInFlight = false;
            if (error != null) {
                error.printStackTrace();
                return;
            }
            if (events == null || events.isEmpty()) {
                return;
            }
            for (EventRecord event : events) {
                lastSeenEventId = Math.max(lastSeenEventId, event.id);
                if (NetworkServerConfig.serverId().equalsIgnoreCase(nullToEmpty(event.originServerId))) {
                    continue;
                }
                deliver(server, event);
            }
        }));
    }

    private static void publishChat(ServerPlayer sender, String type, String scope, String message) {
        if (sender == null || message == null || message.isBlank()) {
            return;
        }
        publish(type, scope, sender.getUUID(), sender.getGameProfile().getName(), message);
    }

    private static void publishSystem(String type, String scope, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        publish(type, scope, null, "", message);
    }

    private static void publish(String type, String scope, UUID playerId, String playerName, String message) {
        if (!NetworkServerConfig.get().enableNetworkEventBus || !DatabaseManager.isEnabled()) {
            return;
        }
        String safeType = normalize(type);
        String safeScope = scope == null || scope.isBlank() ? "GLOBAL" : scope.trim();
        String safeServer = NetworkServerConfig.serverId();
        String safeName = playerName == null ? "" : playerName;
        String safeMessage = message.length() > 500 ? message.substring(0, 500) : message;

        DatabaseManager.executeAsync("publish network event " + safeType, connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into network_events (event_type, scope, origin_server_id, origin_player_uuid, origin_player_name, message) " +
                            "values (?, ?, ?, ?::uuid, ?, ?)"
            )) {
                statement.setString(1, safeType);
                statement.setString(2, safeScope);
                statement.setString(3, safeServer);
                statement.setString(4, playerId == null ? null : playerId.toString());
                statement.setString(5, safeName);
                statement.setString(6, safeMessage);
                statement.executeUpdate();
            }
        });
    }

    private static long currentMaxId(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select coalesce(max(id), 0) from network_events")) {
            return rs.next() ? Math.max(0L, rs.getLong(1)) : 0L;
        }
    }

    private static void deliver(MinecraftServer server, EventRecord event) {
        String type = normalize(event.type);
        if (TYPE_CACHE_INVALIDATE.equals(type)) {
            handleCacheInvalidation(event);
            return;
        }
        if (TYPE_PRIVATE_MESSAGE.equals(type)) {
            deliverPrivateMessage(server, event);
            return;
        }
        if (TYPE_TPA_REQUEST.equals(type)) {
            com.champutils.commands.TpaCommand.handleNetworkRequest(server, event.scope, event.message);
            return;
        }
        if (TYPE_TPA_ACCEPT.equals(type)) {
            com.champutils.commands.TpaCommand.handleNetworkAccept(server, event.scope, event.message);
            return;
        }
        if (TYPE_TPA_DENY.equals(type)) {
            com.champutils.commands.TpaCommand.handleNetworkDeny(server, event.scope, event.message);
            return;
        }
        Component message = com.champutils.chat.ChatTagResolver.legacy(nullToEmpty(event.message));

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!canReceive(player, event)) {
                continue;
            }
            player.sendSystemMessage(message);
        }
    }

    private static void deliverPrivateMessage(MinecraftServer server, EventRecord event) {
        UUID senderId = null;
        try {
            senderId = UUID.fromString(nullToEmpty(event.originPlayerUuid));
        } catch (Exception ignored) {
        }
        String senderName = nullToEmpty(event.originPlayerName).isBlank() ? "Player" : event.originPlayerName;
        String scope = nullToEmpty(event.scope);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean matches = false;
            if (scope.toUpperCase(Locale.ROOT).startsWith("PM_UUID:")) {
                try {
                    matches = player.getUUID().equals(UUID.fromString(scope.substring("PM_UUID:".length())));
                } catch (Exception ignored) {
                }
            }
            else if (scope.toUpperCase(Locale.ROOT).startsWith("PM_NAME:")) {
                matches = player.getGameProfile().getName().equalsIgnoreCase(scope.substring("PM_NAME:".length()));
            }
            if (!matches) continue;
            if (senderId != null) {
                com.champutils.commands.PrivateMessageCommand.rememberReply(player.getUUID(), senderId);
            }
            player.sendSystemMessage(Component.literal("§d§l[PM] §d" + senderName + " → you: §d" + nullToEmpty(event.message)));
        }
    }

    private static void handleCacheInvalidation(EventRecord event) {
        try {
            UUID ownerId = UUID.fromString(nullToEmpty(event.message));
            String scope = normalize(event.scope);
            if ("ECONOMY".equals(scope)) {
                com.champutils.economy.EconomyManager.invalidateSharedCache(ownerId);
            }
            else if ("CRATE_CREDITS".equals(scope)) {
                com.champutils.crate.CrateCreditManager.invalidateSharedCache(ownerId);
            }
            else if ("BOOSTER_CREDITS".equals(scope)) {
                com.champutils.cashshop.BoosterCreditManager.invalidateSharedCache(ownerId);
            }
            else if ("PROFESSIONS".equals(scope)) {
                com.champutils.profession.ProfessionManager.invalidateSharedCache(ownerId);
            }
            else if ("PLAYER_DATA".equals(scope)) {
                com.champutils.profile.PlayerDataManager.invalidateSharedCache(ownerId);
            }
            else if ("TRUE_CAUGHT_DEX".equals(scope)) {
                com.champutils.dex.TrueCaughtDexManager.invalidateSharedCache(ownerId);
            }
            else if ("CATCH_STREAKS".equals(scope)) {
                com.champutils.dex.CatchStreakManager.invalidateSharedCache(ownerId);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean canReceive(ServerPlayer player, EventRecord event) {
        if (player == null) {
            return false;
        }
        String type = normalize(event.type);
        if (TYPE_BROADCAST.equals(type) && !ProfessionNotificationSettings.areBroadcastMessagesEnabled(player)) {
            return false;
        }
        if (TYPE_QUEUE_BROADCAST.equals(type) && !ProfessionNotificationSettings.areQueueNotificationsEnabled(player)) {
            return false;
        }
        if (TYPE_GUILD_CHAT.equals(type)) {
            UUID guildId = guildIdFromScope(event.scope);
            GuildRepository.GuildSnapshot guild = GuildRepository.cachedGuild(player.getUUID());
            return guildId != null && guild != null && guildId.equals(guild.id);
        }
        if (TYPE_PARTY_CHAT.equals(type)) {
            UUID ownerId = partyOwnerIdFromScope(event.scope);
            com.champutils.party.PartyManager.PartySnapshot party = com.champutils.party.PartyManager.snapshot(player.getUUID());
            return ownerId != null && party != null && ownerId.equals(party.ownerId());
        }
        return true;
    }

    private static ChatFormatting styleFor(String type) {
        if (TYPE_QUEUE_BROADCAST.equals(type)) return ChatFormatting.LIGHT_PURPLE;
        if (TYPE_BROADCAST.equals(type)) return ChatFormatting.GOLD;
        return ChatFormatting.WHITE;
    }

    private static UUID guildIdFromScope(String scope) {
        String value = nullToEmpty(scope);
        if (!value.toUpperCase(Locale.ROOT).startsWith("GUILD:")) {
            return null;
        }
        try {
            return UUID.fromString(value.substring("GUILD:".length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static UUID partyOwnerIdFromScope(String scope) {
        String value = nullToEmpty(scope);
        if (!value.toUpperCase(Locale.ROOT).startsWith("PARTY:")) {
            return null;
        }
        try {
            return UUID.fromString(value.substring("PARTY:".length()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String componentText(Component component) {
        return component == null ? "" : component.getString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record EventRecord(long id, String type, String scope, String originServerId, String originPlayerUuid, String originPlayerName, String message) {
    }
}
