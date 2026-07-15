package com.champutils.party;

import com.champutils.database.SharedJsonStateRepository;
import com.champutils.network.NetworkEventManager;
import com.champutils.network.NetworkPlayerDirectory;
import com.champutils.profile.ProfileRestrictions;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class PartyManager {

    private static final long INVITE_EXPIRE_MS = 60_000L;
    private static final int DEFAULT_MAX_SIZE = 6;
    private static final long LOAD_COOLDOWN_MS = 5_000L;
    private static final String LEGACY_STATE_KEY = "parties";

    private static final Map<UUID, Party> PARTIES_BY_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingInvite> INVITES_BY_TARGET = new ConcurrentHashMap<>();
    private static final AtomicBoolean LOAD_IN_FLIGHT = new AtomicBoolean(false);
    private static final AtomicBoolean LEGACY_MIGRATION_ATTEMPTED = new AtomicBoolean(false);
    private static volatile long lastLoadMillis = 0L;
    private static int pruneTickCounter = 0;

    private PartyManager() {}

    public static void initialize() {
        PartyRepository.ensureSchemaAsync();
        lastLoadMillis = 0L;
        refreshAsync();
    }

    public static boolean hasParty(UUID playerId) {
        loadSharedIfNeeded();
        return playerId != null && PLAYER_TO_OWNER.containsKey(playerId);
    }

    public static PartySnapshot snapshot(UUID playerId) {
        loadSharedIfNeeded();
        if (playerId == null) return null;
        UUID ownerId = PLAYER_TO_OWNER.get(playerId);
        if (ownerId == null) return null;
        Party party = PARTIES_BY_OWNER.get(ownerId);
        if (party == null) {
            PLAYER_TO_OWNER.remove(playerId);
            return null;
        }
        return party.snapshot();
    }

    public static List<ServerPlayer> onlineMembers(ServerPlayer player) {
        if (player == null || player.server == null) return Collections.emptyList();
        PartySnapshot snapshot = snapshot(player.getUUID());
        if (snapshot == null) return Collections.emptyList();
        List<ServerPlayer> result = new ArrayList<>();
        for (UUID memberId : snapshot.memberIds()) {
            ServerPlayer member = player.server.getPlayerList().getPlayer(memberId);
            if (member != null) result.add(member);
        }
        return result;
    }

    public static int size(UUID playerId) {
        PartySnapshot snapshot = snapshot(playerId);
        return snapshot == null ? 0 : snapshot.memberIds().size();
    }

    public static boolean areInSameParty(UUID first, UUID second) {
        loadSharedIfNeeded();
        UUID firstOwner = first == null ? null : PLAYER_TO_OWNER.get(first);
        UUID secondOwner = second == null ? null : PLAYER_TO_OWNER.get(second);
        return firstOwner != null && firstOwner.equals(secondOwner);
    }

    public static void create(ServerPlayer owner, Consumer<Result> callback) {
        if (owner == null) { complete(owner, callback, Result.fail("Could not create a party.")); return; }
        mutate(owner, PartyRepository.create(owner.getUUID(), owner.getGameProfile().getName()), callback, null);
    }

    public static void invite(ServerPlayer inviter, String targetName, Consumer<Result> callback) {
        if (inviter == null || inviter.server == null || targetName == null || targetName.isBlank()) {
            complete(inviter, callback, Result.fail("Could not send that invite."));
            return;
        }
        if (ProfileRestrictions.blockIronmanTrade(inviter, "player parties")) {
            complete(inviter, callback, Result.silentFail());
            return;
        }
        ServerPlayer local = inviter.server.getPlayerList().getPlayerByName(targetName);
        if (local != null) {
            if (ProfileRestrictions.blockIronmanTrade(local, "player parties")) {
                complete(inviter, callback, Result.fail(local.getGameProfile().getName() + " is on an Ironman profile."));
                return;
            }
            inviteResolved(inviter, local.getUUID(), local.getGameProfile().getName(), callback);
            return;
        }
        NetworkPlayerDirectory.resolveIdentityAsync(targetName).whenComplete((target, error) -> inviter.server.execute(() -> {
            if (error != null || target == null) {
                complete(inviter, callback, Result.fail("Player not found on the network."));
                return;
            }
            if ("IRONMAN".equalsIgnoreCase(target.activeProfileMode())) {
                complete(inviter, callback, Result.fail(target.playerName() + " is on an Ironman profile."));
                return;
            }
            inviteResolved(inviter, target.playerUuid(), target.playerName(), callback);
        }));
    }

    private static void inviteResolved(ServerPlayer inviter, UUID targetId, String targetName, Consumer<Result> callback) {
        if (inviter.getUUID().equals(targetId)) {
            complete(inviter, callback, Result.fail("You cannot invite yourself."));
            return;
        }
        long expiresAt = System.currentTimeMillis() + INVITE_EXPIRE_MS;
        mutate(inviter,
                PartyRepository.invite(inviter.getUUID(), inviter.getGameProfile().getName(), targetId, targetName, expiresAt, DEFAULT_MAX_SIZE),
                callback,
                result -> {
                    if (!result.success()) return;
                    NetworkEventManager.sendPlayerNotice(inviter.server, targetId,
                            "§d" + inviter.getGameProfile().getName() + " invited you to their party. Use /party accept or /party deny.");
                });
    }

    public static void accept(ServerPlayer player, Consumer<Result> callback) {
        if (player == null) { complete(player, callback, Result.fail("Could not accept that invite.")); return; }
        if (ProfileRestrictions.blockIronmanTrade(player, "player parties")) {
            PartyRepository.deny(player.getUUID());
            complete(player, callback, Result.silentFail());
            return;
        }
        mutate(player, PartyRepository.accept(player.getUUID(), player.getGameProfile().getName(), DEFAULT_MAX_SIZE), callback,
                result -> {
                    if (!result.success()) return;
                    Party party = result.ownerId() == null ? null : PARTIES_BY_OWNER.get(result.ownerId());
                    broadcast(player.server, party, player.getGameProfile().getName() + " joined the party.", ChatFormatting.GREEN);
                });
    }

    public static void deny(ServerPlayer player, Consumer<Result> callback) {
        if (player == null) { complete(player, callback, Result.fail("Could not deny that invite.")); return; }
        mutate(player, PartyRepository.deny(player.getUUID()), callback, null);
    }

    public static void leave(ServerPlayer player, Consumer<Result> callback) {
        if (player == null) { complete(player, callback, Result.fail("Could not leave the party.")); return; }
        PartySnapshot before = snapshot(player.getUUID());
        mutate(player, PartyRepository.leave(player.getUUID()), callback, result -> {
            if (!result.success() || before == null) return;
            if (before.isLeader(player.getUUID())) {
                broadcastSnapshot(player.server, before, "The party was disbanded because the leader left.", ChatFormatting.YELLOW);
            } else {
                broadcastSnapshot(player.server, before, player.getGameProfile().getName() + " left the party.", ChatFormatting.YELLOW);
            }
        });
    }

    public static void disband(ServerPlayer leader, Consumer<Result> callback) {
        if (leader == null) { complete(leader, callback, Result.fail("Could not disband the party.")); return; }
        PartySnapshot before = snapshot(leader.getUUID());
        mutate(leader, PartyRepository.disband(leader.getUUID()), callback, result -> {
            if (result.success() && before != null) {
                broadcastSnapshot(leader.server, before, "The party was disbanded.", ChatFormatting.YELLOW);
            }
        });
    }

    public static void kick(ServerPlayer leader, String targetName, Consumer<Result> callback) {
        if (leader == null || targetName == null || targetName.isBlank()) {
            complete(leader, callback, Result.fail("Could not kick that player."));
            return;
        }
        PartySnapshot party = snapshot(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) {
            complete(leader, callback, Result.fail("Only the party leader can kick players."));
            return;
        }
        UUID targetId = party.memberIds().stream()
                .filter(id -> party.nameOf(id).equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (targetId == null) {
            complete(leader, callback, Result.fail(targetName + " is not in your party."));
            return;
        }
        String resolvedName = party.nameOf(targetId);
        mutate(leader, PartyRepository.kick(leader.getUUID(), targetId, resolvedName), callback, result -> {
            if (!result.success()) return;
            NetworkEventManager.sendPlayerNotice(leader.server, targetId, "§cYou were removed from the party.");
            broadcastSnapshot(leader.server, party, resolvedName + " was removed from the party.", ChatFormatting.YELLOW);
        });
    }

    public static void promote(ServerPlayer leader, String targetName, Consumer<Result> callback) {
        if (leader == null || targetName == null || targetName.isBlank()) {
            complete(leader, callback, Result.fail("Could not transfer party leadership."));
            return;
        }
        PartySnapshot party = snapshot(leader.getUUID());
        if (party == null || !party.isLeader(leader.getUUID())) {
            complete(leader, callback, Result.fail("Only the party leader can transfer leadership."));
            return;
        }
        UUID targetId = party.memberIds().stream()
                .filter(id -> party.nameOf(id).equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (targetId == null) {
            complete(leader, callback, Result.fail(targetName + " is not in your party."));
            return;
        }
        String resolvedName = party.nameOf(targetId);
        mutate(leader, PartyRepository.promote(leader.getUUID(), targetId, resolvedName), callback, result -> {
            if (result.success()) broadcastSnapshot(leader.server, party, resolvedName + " is now the party leader.", ChatFormatting.GOLD);
        });
    }

    /**
     * A backend disconnect can be a normal Nova/Eclipse profile transfer. Do not delete a pending
     * invite here; it must survive the disconnect long enough for the destination server to load.
     */
    public static void handleDisconnect(ServerPlayer player) {
        // Expiration is handled by PostgreSQL/tick cleanup.
    }

    public static void tick(MinecraftServer server) {
        if (++pruneTickCounter < 1200) return;
        pruneTickCounter = 0;
        PartyRepository.pruneExpiredAsync();
        loadSharedIfNeeded();
    }

    public static void invalidateSharedCache() {
        lastLoadMillis = 0L;
        refreshAsync();
    }

    private static void mutate(ServerPlayer actor,
                               CompletableFuture<PartyRepository.MutationResult> future,
                               Consumer<Result> callback,
                               Consumer<Result> afterSuccess) {
        future.whenComplete((mutation, error) -> {
            if (error != null || mutation == null) {
                complete(actor, callback, Result.fail("The party service could not complete that request."));
                return;
            }
            refreshAsync().whenComplete((ignored, refreshError) -> {
                Result result = mutation.success()
                        ? Result.ok(mutation.message(), mutation.leaderId())
                        : Result.fail(mutation.message());
                if (mutation.success()) NetworkEventManager.publishCacheInvalidation("PARTY", mutation.partyId() == null ? new UUID(0L, 0L) : mutation.partyId());
                if (actor != null && actor.server != null) {
                    actor.server.execute(() -> {
                        if (afterSuccess != null) afterSuccess.accept(result);
                        if (callback != null) callback.accept(result);
                    });
                } else if (callback != null) {
                    callback.accept(result);
                }
            });
        });
    }

    private static void complete(ServerPlayer actor, Consumer<Result> callback, Result result) {
        if (callback == null) return;
        if (actor != null && actor.server != null) actor.server.execute(() -> callback.accept(result));
        else callback.accept(result);
    }

    private static void loadSharedIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastLoadMillis < LOAD_COOLDOWN_MS) return;
        lastLoadMillis = now;
        refreshAsync();
    }

    private static CompletableFuture<Void> refreshAsync() {
        if (!LOAD_IN_FLIGHT.compareAndSet(false, true)) return CompletableFuture.completedFuture(null);
        PartyRepository.ensureSchemaAsync();
        CompletableFuture<Void> done = new CompletableFuture<>();
        PartyRepository.loadAllAsync().whenComplete((state, error) -> {
            if (error != null) {
                LOAD_IN_FLIGHT.set(false);
                done.completeExceptionally(error);
                return;
            }
            if (state != null && state.parties.isEmpty() && LEGACY_MIGRATION_ATTEMPTED.compareAndSet(false, true)) {
                migrateLegacy().whenComplete((ignored, migrationError) -> {
                    if (migrationError != null) {
                        LOAD_IN_FLIGHT.set(false);
                        done.completeExceptionally(migrationError);
                        return;
                    }
                    PartyRepository.loadAllAsync().whenComplete((migrated, reloadError) -> {
                        if (reloadError == null) applyState(migrated);
                        LOAD_IN_FLIGHT.set(false);
                        if (reloadError == null) done.complete(null); else done.completeExceptionally(reloadError);
                    });
                });
                return;
            }
            applyState(state);
            LOAD_IN_FLIGHT.set(false);
            done.complete(null);
        });
        return done;
    }

    private static CompletableFuture<Void> migrateLegacy() {
        return SharedJsonStateRepository.loadGlobalAsync(LEGACY_STATE_KEY, LegacyState.class, new LegacyState())
                .thenCompose(legacy -> {
                    List<PartyRepository.LegacyPartyRow> parties = new ArrayList<>();
                    List<PartyRepository.LegacyInviteRow> invites = new ArrayList<>();
                    if (legacy != null && legacy.partiesByOwner != null) {
                        for (Map.Entry<UUID, LegacyParty> entry : legacy.partiesByOwner.entrySet()) {
                            LegacyParty source = entry.getValue();
                            if (source == null) continue;
                            UUID leaderId = source.ownerId == null ? entry.getKey() : source.ownerId;
                            List<PartyRepository.MemberRow> members = new ArrayList<>();
                            if (source.members != null) {
                                for (LegacyMember member : source.members) {
                                    if (member != null && member.id != null) members.add(new PartyRepository.MemberRow(member.id, member.name));
                                }
                            }
                            if (members.stream().noneMatch(member -> member.playerId().equals(leaderId))) {
                                members.add(0, new PartyRepository.MemberRow(leaderId, source.ownerName));
                            }
                            parties.add(new PartyRepository.LegacyPartyRow(leaderId, leaderId, source.ownerName, members));
                        }
                    }
                    if (legacy != null && legacy.invitesByTarget != null) {
                        for (Map.Entry<UUID, LegacyInvite> entry : legacy.invitesByTarget.entrySet()) {
                            LegacyInvite invite = entry.getValue();
                            if (invite == null || invite.partyOwnerId == null) continue;
                            invites.add(new PartyRepository.LegacyInviteRow(
                                    entry.getKey(), "", invite.partyOwnerId, invite.partyOwnerId,
                                    invite.inviterId, invite.inviterName, invite.expiresAtMs
                            ));
                        }
                    }
                    return PartyRepository.importLegacyAsync(parties, invites);
                });
    }

    private static void applyState(PartyRepository.LoadedState state) {
        Map<UUID, Party> partiesByOwner = new HashMap<>();
        Map<UUID, UUID> playerToOwner = new HashMap<>();
        Map<UUID, PendingInvite> invitesByTarget = new HashMap<>();
        if (state != null) {
            for (PartyRepository.PartyRow row : state.parties.values()) {
                Party party = new Party(row.partyId(), row.leaderId(), row.leaderName());
                List<PartyRepository.MemberRow> members = state.members.getOrDefault(row.partyId(), List.of());
                for (PartyRepository.MemberRow member : members) {
                    party.members.add(new PartyMember(member.playerId(), member.playerName()));
                    playerToOwner.put(member.playerId(), row.leaderId());
                }
                if (party.members.stream().noneMatch(member -> member.id.equals(row.leaderId()))) {
                    party.members.add(new PartyMember(row.leaderId(), row.leaderName()));
                    playerToOwner.put(row.leaderId(), row.leaderId());
                }
                partiesByOwner.put(row.leaderId(), party);
            }
            for (PartyRepository.InviteRow invite : state.invites.values()) {
                invitesByTarget.put(invite.targetId(), new PendingInvite(
                        invite.partyId(), invite.leaderId(), invite.inviterId(), invite.inviterName(), invite.expiresAtMillis()
                ));
            }
        }
        PARTIES_BY_OWNER.clear();
        PARTIES_BY_OWNER.putAll(partiesByOwner);
        PLAYER_TO_OWNER.clear();
        PLAYER_TO_OWNER.putAll(playerToOwner);
        INVITES_BY_TARGET.clear();
        INVITES_BY_TARGET.putAll(invitesByTarget);
        lastLoadMillis = System.currentTimeMillis();
    }

    private static void broadcast(MinecraftServer server, Party party, String message, ChatFormatting color) {
        if (party != null) broadcastSnapshot(server, party.snapshot(), message, color);
    }

    private static void broadcastSnapshot(MinecraftServer server, PartySnapshot party, String message, ChatFormatting color) {
        if (party == null) return;
        String legacyColor = legacyColor(color);
        for (UUID memberId : party.memberIds()) {
            ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(memberId);
            if (online != null) online.sendSystemMessage(Component.literal(message).withStyle(color));
            else NetworkEventManager.publishPlayerNotice(memberId, legacyColor + message);
        }
    }

    private static String legacyColor(ChatFormatting color) {
        if (color == ChatFormatting.RED) return "§c";
        if (color == ChatFormatting.GREEN) return "§a";
        if (color == ChatFormatting.GOLD) return "§6";
        if (color == ChatFormatting.LIGHT_PURPLE) return "§d";
        return "§e";
    }

    private static final class Party {
        private final UUID partyId;
        private final UUID ownerId;
        private final String ownerName;
        private final LinkedHashSet<PartyMember> members = new LinkedHashSet<>();

        private Party(UUID partyId, UUID ownerId, String ownerName) {
            this.partyId = partyId;
            this.ownerId = ownerId;
            this.ownerName = ownerName == null ? ownerId.toString() : ownerName;
        }

        private PartySnapshot snapshot() {
            Map<UUID, String> names = new HashMap<>();
            List<UUID> ids = new ArrayList<>();
            for (PartyMember member : members) {
                ids.add(member.id);
                names.put(member.id, member.name);
            }
            return new PartySnapshot(partyId, ownerId, ownerName, ids, names, DEFAULT_MAX_SIZE);
        }
    }

    private record PartyMember(UUID id, String name) {}
    private record PendingInvite(UUID partyId, UUID partyOwnerId, UUID inviterId, String inviterName, long expiresAtMs) {}

    public record PartySnapshot(UUID partyId, UUID ownerId, String ownerName, List<UUID> memberIds, Map<UUID, String> memberNames, int maxSize) {
        public boolean isLeader(UUID playerId) { return ownerId.equals(playerId); }
        public String nameOf(UUID playerId) { return memberNames.getOrDefault(playerId, playerId.toString()); }
    }

    public record Result(boolean success, boolean silent, String message, UUID ownerId) {
        public static Result ok(String message, UUID ownerId) { return new Result(true, false, message, ownerId); }
        public static Result fail(String message) { return new Result(false, false, message, null); }
        public static Result silentFail() { return new Result(false, true, "", null); }
    }

    // Gson-compatible legacy snapshot shapes used only for the one-time migration.
    private static final class LegacyState {
        Map<UUID, LegacyParty> partiesByOwner = new HashMap<>();
        Map<UUID, UUID> playerToOwner = new HashMap<>();
        Map<UUID, LegacyInvite> invitesByTarget = new HashMap<>();
    }
    private static final class LegacyParty {
        UUID ownerId;
        String ownerName;
        LinkedHashSet<LegacyMember> members = new LinkedHashSet<>();
    }
    private static final class LegacyMember { UUID id; String name; }
    private static final class LegacyInvite { UUID partyOwnerId; UUID inviterId; String inviterName; long expiresAtMs; }
}
