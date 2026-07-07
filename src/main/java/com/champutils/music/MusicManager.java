package com.champutils.music;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MusicManager {
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, ForcedTrack> FORCED = new ConcurrentHashMap<>();
    private static long nextCheckTick = 0L;

    private MusicManager() {}

    private static final class State {
        String currentTrack;
        long startedTick;
        boolean disabled;
    }

    private record ForcedTrack(String track, long untilTick) {}

    public static void tick(MinecraftServer server) {
        if (server == null || !MusicConfig.ROOT.enabled) return;
        long tick = server.getTickCount();
        int intervalTicks = Math.max(20, MusicConfig.ROOT.checkIntervalSeconds * 20);
        if (tick < nextCheckTick) return;
        nextCheckTick = tick + intervalTicks;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.hasDisconnected()) continue;
            tickPlayer(player, tick);
        }
    }

    public static void handleJoin(ServerPlayer player) {
        if (player == null) return;
        STATES.remove(player.getUUID());
    }

    public static void handleQuit(ServerPlayer player) {
        if (player == null) return;
        STATES.remove(player.getUUID());
        FORCED.remove(player.getUUID());
    }

    public static boolean toggle(ServerPlayer player) {
        State state = state(player);
        state.disabled = !state.disabled;
        if (state.disabled) {
            stop(player);
            state.currentTrack = null;
        }
        return !state.disabled;
    }

    public static void reload(MinecraftServer server) {
        MusicConfig.load();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                stop(player);
                State state = state(player);
                state.currentTrack = null;
                state.startedTick = 0L;
            }
        }
    }

    public static void forceTrack(ServerPlayer player, String track, int seconds) {
        if (player == null || track == null || track.isBlank()) return;
        long until = player.server == null ? 0L : player.server.getTickCount() + Math.max(1, seconds) * 20L;
        FORCED.put(player.getUUID(), new ForcedTrack(MusicConfig.normalize(track), until));
        play(player, MusicConfig.normalize(track), true);
    }

    public static void clearForced(ServerPlayer player) {
        if (player != null) FORCED.remove(player.getUUID());
    }

    public static void playTest(ServerPlayer player, String track) {
        if (player == null) return;
        play(player, MusicConfig.normalize(track), true);
    }

    private static void tickPlayer(ServerPlayer player, long tick) {
        State state = state(player);
        if (state.disabled) return;

        String desired = desiredTrack(player, tick);
        if (desired == null || desired.isBlank()) desired = MusicConfig.ROOT.defaultTrack;
        MusicConfig.Track track = MusicConfig.getTrack(desired);
        if (track == null) return;

        boolean changed = !desired.equals(state.currentTrack);
        boolean loopDue = state.startedTick <= 0L || tick - state.startedTick >= Math.max(10, track.loopSeconds) * 20L;
        if (changed || loopDue) {
            play(player, desired, changed);
        }
    }

    private static String desiredTrack(ServerPlayer player, long tick) {
        ForcedTrack forced = FORCED.get(player.getUUID());
        if (forced != null) {
            if (forced.untilTick > tick) return forced.track;
            FORCED.remove(player.getUUID());
        }

        String region = regionTrack(player);
        if (region != null) return region;
        return MusicConfig.ROOT.defaultTrack;
    }

    private static String regionTrack(ServerPlayer player) {
        String dimension = player.level().dimension().location().toString();
        int x = player.blockPosition().getX();
        int y = player.blockPosition().getY();
        int z = player.blockPosition().getZ();

        return MusicConfig.ROOT.regions.stream()
                .filter(r -> r != null && r.track != null && r.dimension != null)
                .filter(r -> r.dimension.equalsIgnoreCase(dimension))
                .filter(r -> x >= Math.min(r.minX, r.maxX) && x <= Math.max(r.minX, r.maxX))
                .filter(r -> y >= Math.min(r.minY, r.maxY) && y <= Math.max(r.minY, r.maxY))
                .filter(r -> z >= Math.min(r.minZ, r.maxZ) && z <= Math.max(r.minZ, r.maxZ))
                .max(Comparator.comparingInt(r -> r.priority))
                .map(r -> MusicConfig.normalize(r.track))
                .orElse(null);
    }

    private static void play(ServerPlayer player, String trackKey, boolean stopFirst) {
        MusicConfig.Track track = MusicConfig.getTrack(trackKey);
        if (track == null) return;
        try {
            if (stopFirst) stop(player);
            ResourceLocation soundId = ResourceLocation.parse(track.sound);
            SoundEvent sound = SoundEvent.createVariableRangeEvent(soundId);
            player.connection.send(new ClientboundSoundPacket(
                    Holder.direct(sound),
                    SoundSource.MUSIC,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    track.volume,
                    track.pitch,
                    player.getRandom().nextLong()
            ));
            State state = state(player);
            state.currentTrack = MusicConfig.normalize(trackKey);
            state.startedTick = player.server == null ? 0L : player.server.getTickCount();
            if (MusicConfig.ROOT.debug) {
                System.out.println("[ChampUtils][Music] " + player.getGameProfile().getName() + " -> " + trackKey + " (" + track.sound + ")");
            }
        } catch (Exception e) {
            System.err.println("[ChampUtils][Music] Failed to play track " + trackKey + " for " + player.getGameProfile().getName() + ": " + e.getMessage());
        }
    }

    public static void stop(ServerPlayer player) {
        if (player == null) return;
        try {
            player.connection.send(new ClientboundStopSoundPacket(null, SoundSource.MUSIC));
        } catch (Exception ignored) {
        }
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
    }
}
