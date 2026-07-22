package com.champutils.commands;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

/** World-scoped day/night voting with a server-wide cooldown after successful votes. */
public final class WorldTimeVoteCommand {
    private static final long VOTE_DURATION_MS = 60_000L;
    private static final long SUCCESS_COOLDOWN_MS = 30L * 60L * 1000L;
    private static final Map<String, Vote> ACTIVE = new HashMap<>();
    private static long globalCooldownUntilMs;
    private static int tickCounter;

    private WorldTimeVoteCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("voteday")
                    .executes(ctx -> startOrVote(ctx.getSource(), VoteType.DAY, true))
                    .then(literal("yes").executes(ctx -> startOrVote(ctx.getSource(), VoteType.DAY, true)))
                    .then(literal("no").executes(ctx -> startOrVote(ctx.getSource(), VoteType.DAY, false))));
            dispatcher.register(literal("votenight")
                    .executes(ctx -> startOrVote(ctx.getSource(), VoteType.NIGHT, true))
                    .then(literal("yes").executes(ctx -> startOrVote(ctx.getSource(), VoteType.NIGHT, true)))
                    .then(literal("no").executes(ctx -> startOrVote(ctx.getSource(), VoteType.NIGHT, false))));
        });
        ServerTickEvents.END_SERVER_TICK.register(WorldTimeVoteCommand::tick);
    }

    private static int startOrVote(CommandSourceStack source, VoteType requestedType, boolean yes) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Players only."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        String worldKey = level.dimension().location().toString();
        Vote existing = ACTIVE.get(worldKey);
        long now = System.currentTimeMillis();

        if (existing != null && now >= existing.endsAtMs) {
            finish(player.server, worldKey, existing);
            existing = null;
        }
        if (existing != null) {
            if (existing.type != requestedType) {
                player.sendSystemMessage(Component.literal("§cA " + existing.type.label + " vote is already active in this world."));
                return 0;
            }
            castVote(player, existing, yes);
            return 1;
        }

        if (now < globalCooldownUntilMs) {
            long seconds = Math.max(1L, (globalCooldownUntilMs - now + 999L) / 1000L);
            player.sendSystemMessage(Component.literal("§cTime voting is on cooldown for another " + formatDuration(seconds) + "."));
            return 0;
        }

        Set<UUID> eligible = new HashSet<>();
        for (ServerPlayer online : level.players()) eligible.add(online.getUUID());
        if (eligible.isEmpty()) eligible.add(player.getUUID());
        Vote vote = new Vote(requestedType, player.getUUID(), player.getGameProfile().getName(), eligible, now + VOTE_DURATION_MS);
        vote.yes.add(player.getUUID());
        ACTIVE.put(worldKey, vote);
        broadcastPrompt(level, vote);
        evaluateEarly(player.server, worldKey, vote);
        return 1;
    }

    private static void castVote(ServerPlayer player, Vote vote, boolean yes) {
        if (!vote.eligible.contains(player.getUUID())) {
            player.sendSystemMessage(Component.literal("§cYou were not present when this vote started."));
            return;
        }
        vote.yes.remove(player.getUUID());
        vote.no.remove(player.getUUID());
        (yes ? vote.yes : vote.no).add(player.getUUID());
        player.sendSystemMessage(Component.literal(yes ? "§aYou voted yes." : "§cYou voted no."));
        evaluateEarly(player.server, player.serverLevel().dimension().location().toString(), vote);
    }

    private static void evaluateEarly(MinecraftServer server, String worldKey, Vote vote) {
        int required = vote.eligible.size() / 2 + 1;
        if (vote.yes.size() >= required) {
            finish(server, worldKey, vote);
            return;
        }
        int remainingPossible = vote.eligible.size() - vote.no.size();
        if (remainingPossible < required) finish(server, worldKey, vote);
    }

    private static void tick(MinecraftServer server) {
        if (++tickCounter < 20) return;
        tickCounter = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Vote> entry : new HashMap<>(ACTIVE).entrySet()) {
            if (now >= entry.getValue().endsAtMs) finish(server, entry.getKey(), entry.getValue());
        }
    }

    private static void finish(MinecraftServer server, String worldKey, Vote vote) {
        if (!ACTIVE.remove(worldKey, vote)) return;
        ServerLevel level = findLevel(server, worldKey);
        int required = vote.eligible.size() / 2 + 1;
        boolean passed = vote.yes.size() >= required;
        if (passed && level != null) {
            level.setDayTime(vote.type == VoteType.DAY ? 1000L : 13000L);
            globalCooldownUntilMs = System.currentTimeMillis() + SUCCESS_COOLDOWN_MS;
        }
        Component result = Component.literal((passed ? "§a" : "§c") + vote.type.labelTitle + " vote " + (passed ? "passed" : "failed")
                + " §7(" + vote.yes.size() + "/" + vote.eligible.size() + " yes; needed " + required + ").");
        if (level != null) for (ServerPlayer player : level.players()) player.sendSystemMessage(result);
    }

    private static void broadcastPrompt(ServerLevel level, Vote vote) {
        String yesCommand = vote.type == VoteType.DAY ? "/voteday yes" : "/votenight yes";
        String noCommand = vote.type == VoteType.DAY ? "/voteday no" : "/votenight no";
        Component yes = Component.literal("[YES]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, yesCommand)));
        Component no = Component.literal("[NO]").withStyle(style -> style.withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, noCommand)));
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 50, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("§6Time Vote Started")));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§fVote for " + vote.type.label + " within 60 seconds")));
            player.sendSystemMessage(Component.literal("§e" + vote.startedByName + " started a vote for " + vote.type.label + ". Non-votes count as no. ")
                    .append(yes).append(Component.literal(" §7or ")).append(no));
        }
    }

    private static ServerLevel findLevel(MinecraftServer server, String key) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(key)) return level;
        }
        return null;
    }

    private static String formatDuration(long seconds) {
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return minutes > 0 ? minutes + "m " + remainder + "s" : remainder + "s";
    }

    private enum VoteType {
        DAY("day", "Day"), NIGHT("night", "Night");
        final String label;
        final String labelTitle;
        VoteType(String label, String labelTitle) { this.label = label; this.labelTitle = labelTitle; }
    }

    private static final class Vote {
        final VoteType type;
        final UUID startedBy;
        final String startedByName;
        final Set<UUID> eligible;
        final Set<UUID> yes = new HashSet<>();
        final Set<UUID> no = new HashSet<>();
        final long endsAtMs;
        Vote(VoteType type, UUID startedBy, String startedByName, Set<UUID> eligible, long endsAtMs) {
            this.type = type;
            this.startedBy = startedBy;
            this.startedByName = startedByName;
            this.eligible = eligible;
            this.endsAtMs = endsAtMs;
        }
    }
}
