package com.champutils.party;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyManager {

    private static final long INVITE_EXPIRE_MS = 60_000L;
    private static final int DEFAULT_MAX_SIZE = 6;

    private static final Map<UUID, Party> PARTIES_BY_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_OWNER = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingInvite> INVITES_BY_TARGET = new ConcurrentHashMap<>();

    private PartyManager() {
    }

    public static boolean hasParty(UUID playerId) {
        return PLAYER_TO_OWNER.containsKey(playerId);
    }

    public static PartySnapshot snapshot(UUID playerId) {
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
        UUID firstOwner = PLAYER_TO_OWNER.get(first);
        UUID secondOwner = PLAYER_TO_OWNER.get(second);
        return firstOwner != null && firstOwner.equals(secondOwner);
    }

    public static Result create(ServerPlayer owner) {
        if (owner == null) return Result.fail("Could not create a party.");
        if (hasParty(owner.getUUID())) return Result.fail("You are already in a party.");

        Party party = new Party(owner.getUUID(), owner.getGameProfile().getName());
        PARTIES_BY_OWNER.put(owner.getUUID(), party);
        PLAYER_TO_OWNER.put(owner.getUUID(), owner.getUUID());
        INVITES_BY_TARGET.remove(owner.getUUID());
        return Result.ok("Party created. Use /party invite <player> to invite someone.");
    }

    public static Result invite(ServerPlayer inviter, ServerPlayer target) {
        if (inviter == null || target == null) return Result.fail("Could not send that invite.");
        if (inviter.getUUID().equals(target.getUUID())) return Result.fail("You cannot invite yourself.");

        UUID ownerId = PLAYER_TO_OWNER.get(inviter.getUUID());
        Party party = ownerId == null ? null : PARTIES_BY_OWNER.get(ownerId);
        if (party == null) return Result.fail("Create a party first with /party create.");
        if (!party.ownerId.equals(inviter.getUUID())) return Result.fail("Only the party leader can invite players.");
        if (hasParty(target.getUUID())) return Result.fail(target.getGameProfile().getName() + " is already in a party.");
        if (party.members.size() >= DEFAULT_MAX_SIZE) return Result.fail("Your party is full.");

        PendingInvite invite = new PendingInvite(party.ownerId, inviter.getUUID(), inviter.getGameProfile().getName(), System.currentTimeMillis() + INVITE_EXPIRE_MS);
        INVITES_BY_TARGET.put(target.getUUID(), invite);
        target.sendSystemMessage(Component.literal(inviter.getGameProfile().getName() + " invited you to their party. Use /party accept or /party deny.").withStyle(ChatFormatting.LIGHT_PURPLE));
        return Result.ok("Invited " + target.getGameProfile().getName() + " to your party.");
    }

    public static Result accept(ServerPlayer player) {
        if (player == null) return Result.fail("Could not accept that invite.");
        if (hasParty(player.getUUID())) {
            INVITES_BY_TARGET.remove(player.getUUID());
            return Result.fail("You are already in a party.");
        }

        PendingInvite invite = INVITES_BY_TARGET.remove(player.getUUID());
        if (invite == null || invite.isExpired()) return Result.fail("You do not have a pending party invite.");

        Party party = PARTIES_BY_OWNER.get(invite.partyOwnerId);
        if (party == null) return Result.fail("That party no longer exists.");
        if (party.members.size() >= DEFAULT_MAX_SIZE) return Result.fail("That party is full.");

        party.members.add(new PartyMember(player.getUUID(), player.getGameProfile().getName()));
        PLAYER_TO_OWNER.put(player.getUUID(), party.ownerId);
        broadcast(player.server, party, player.getGameProfile().getName() + " joined the party.", ChatFormatting.GREEN);
        return Result.ok("You joined the party.");
    }

    public static Result deny(ServerPlayer player) {
        if (player == null) return Result.fail("Could not deny that invite.");
        PendingInvite invite = INVITES_BY_TARGET.remove(player.getUUID());
        if (invite == null) return Result.fail("You do not have a pending party invite.");
        return Result.ok("Party invite denied.");
    }

    public static Result leave(ServerPlayer player) {
        if (player == null) return Result.fail("Could not leave the party.");
        UUID ownerId = PLAYER_TO_OWNER.get(player.getUUID());
        if (ownerId == null) return Result.fail("You are not in a party.");
        Party party = PARTIES_BY_OWNER.get(ownerId);
        if (party == null) {
            PLAYER_TO_OWNER.remove(player.getUUID());
            return Result.fail("You are not in a party.");
        }

        if (party.ownerId.equals(player.getUUID())) {
            disbandParty(player.server, party, "The party was disbanded because the leader left.");
            return Result.ok("Party disbanded.");
        }

        party.members.removeIf(member -> member.id.equals(player.getUUID()));
        PLAYER_TO_OWNER.remove(player.getUUID());
        broadcast(player.server, party, player.getGameProfile().getName() + " left the party.", ChatFormatting.YELLOW);
        player.sendSystemMessage(Component.literal("You left the party.").withStyle(ChatFormatting.YELLOW));
        return Result.silentOk();
    }

    public static Result disband(ServerPlayer player) {
        if (player == null) return Result.fail("Could not disband the party.");
        Party party = ownedParty(player);
        if (party == null) return Result.fail("Only the party leader can disband the party.");
        disbandParty(player.server, party, "The party was disbanded.");
        return Result.ok("Party disbanded.");
    }

    public static Result kick(ServerPlayer leader, ServerPlayer target) {
        if (leader == null || target == null) return Result.fail("Could not kick that player.");
        Party party = ownedParty(leader);
        if (party == null) return Result.fail("Only the party leader can kick players.");
        if (leader.getUUID().equals(target.getUUID())) return Result.fail("Use /party disband if you want to close the party.");
        if (!PLAYER_TO_OWNER.getOrDefault(target.getUUID(), new UUID(0L, 0L)).equals(party.ownerId)) return Result.fail(target.getGameProfile().getName() + " is not in your party.");

        party.members.removeIf(member -> member.id.equals(target.getUUID()));
        PLAYER_TO_OWNER.remove(target.getUUID());
        target.sendSystemMessage(Component.literal("You were removed from the party.").withStyle(ChatFormatting.RED));
        broadcast(leader.server, party, target.getGameProfile().getName() + " was removed from the party.", ChatFormatting.YELLOW);
        return Result.ok("Removed " + target.getGameProfile().getName() + " from the party.");
    }

    public static Result promote(ServerPlayer leader, ServerPlayer target) {
        if (leader == null || target == null) return Result.fail("Could not transfer party leadership.");
        Party party = ownedParty(leader);
        if (party == null) return Result.fail("Only the party leader can transfer leadership.");
        if (leader.getUUID().equals(target.getUUID())) return Result.fail("You are already the party leader.");
        if (!PLAYER_TO_OWNER.getOrDefault(target.getUUID(), new UUID(0L, 0L)).equals(party.ownerId)) return Result.fail(target.getGameProfile().getName() + " is not in your party.");

        PARTIES_BY_OWNER.remove(party.ownerId);
        party.ownerId = target.getUUID();
        party.ownerName = target.getGameProfile().getName();
        PARTIES_BY_OWNER.put(party.ownerId, party);
        for (PartyMember member : party.members) PLAYER_TO_OWNER.put(member.id, party.ownerId);
        broadcast(leader.server, party, target.getGameProfile().getName() + " is now the party leader.", ChatFormatting.GOLD);
        return Result.ok("Transferred party leadership to " + target.getGameProfile().getName() + ".");
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) return;
        leave(player);
        INVITES_BY_TARGET.remove(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        INVITES_BY_TARGET.entrySet().removeIf(entry -> entry.getValue().expiresAtMs <= now);

        Set<UUID> emptyOwners = new HashSet<>();
        for (Party party : PARTIES_BY_OWNER.values()) {
            if (party.members.isEmpty()) emptyOwners.add(party.ownerId);
        }
        for (UUID ownerId : emptyOwners) PARTIES_BY_OWNER.remove(ownerId);
    }

    private static Party ownedParty(ServerPlayer player) {
        UUID ownerId = PLAYER_TO_OWNER.get(player.getUUID());
        if (ownerId == null || !ownerId.equals(player.getUUID())) return null;
        return PARTIES_BY_OWNER.get(ownerId);
    }

    private static void disbandParty(MinecraftServer server, Party party, String message) {
        for (PartyMember member : new ArrayList<>(party.members)) PLAYER_TO_OWNER.remove(member.id);
        PARTIES_BY_OWNER.remove(party.ownerId);
        if (server != null) {
            for (PartyMember member : party.members) {
                ServerPlayer online = server.getPlayerList().getPlayer(member.id);
                if (online != null) online.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static void broadcast(MinecraftServer server, Party party, String message, ChatFormatting color) {
        if (server == null || party == null) return;
        for (PartyMember member : party.members) {
            ServerPlayer online = server.getPlayerList().getPlayer(member.id);
            if (online != null) online.sendSystemMessage(Component.literal(message).withStyle(color));
        }
    }

    private static final class Party {
        private UUID ownerId;
        private String ownerName;
        private final LinkedHashSet<PartyMember> members = new LinkedHashSet<>();

        private Party(UUID ownerId, String ownerName) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.members.add(new PartyMember(ownerId, ownerName));
        }

        private PartySnapshot snapshot() {
            Map<UUID, String> names = new HashMap<>();
            List<UUID> ids = new ArrayList<>();
            for (PartyMember member : members) {
                ids.add(member.id);
                names.put(member.id, member.name);
            }
            return new PartySnapshot(ownerId, ownerName, ids, names, DEFAULT_MAX_SIZE);
        }
    }

    private record PartyMember(UUID id, String name) {
    }

    private record PendingInvite(UUID partyOwnerId, UUID inviterId, String inviterName, long expiresAtMs) {
        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMs;
        }
    }

    public record PartySnapshot(UUID ownerId, String ownerName, List<UUID> memberIds, Map<UUID, String> memberNames, int maxSize) {
        public boolean isLeader(UUID playerId) {
            return ownerId.equals(playerId);
        }

        public String nameOf(UUID playerId) {
            return memberNames.getOrDefault(playerId, playerId.toString());
        }
    }

    public record Result(boolean success, boolean silent, String message) {
        public static Result ok(String message) {
            return new Result(true, false, message);
        }

        public static Result silentOk() {
            return new Result(true, true, "");
        }

        public static Result fail(String message) {
            return new Result(false, false, message);
        }
    }
}
