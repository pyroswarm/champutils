package com.champutils.dungeon;

import com.champutils.matchmaking.MatchmakingManager;
import com.champutils.battle.BattlePrepManager;
import com.champutils.database.DungeonProgressDatabaseRepository;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DungeonManager {

    private static final Map<UUID, DungeonSession> ACTIVE_SESSIONS = new HashMap<>();
    private static final Map<UUID, UUID> TRAINER_TO_PLAYER = new HashMap<>();

    private DungeonManager() {}

    public static boolean isInDungeon(ServerPlayer player) {
        return player != null && ACTIVE_SESSIONS.containsKey(player.getUUID());
    }

    public static DungeonSession getSession(ServerPlayer player) {
        if (player == null) return null;
        return ACTIVE_SESSIONS.get(player.getUUID());
    }

    public static DungeonSession getSessionByTrainer(UUID trainerUuid) {
        UUID playerId = TRAINER_TO_PLAYER.get(trainerUuid);
        if (playerId == null) return null;
        return ACTIVE_SESSIONS.get(playerId);
    }

    public static int startDungeon(ServerPlayer player, String dungeonId) {
        if (player == null) return 0;

        if (isInDungeon(player)) {
            player.sendSystemMessage(Component.literal("You are already inside a dungeon. Use /dungeon forfeit to leave.").withStyle(ChatFormatting.RED));
            return 0;
        }

        DungeonConfig.DungeonData data = DungeonConfig.DUNGEONS.get(dungeonId);
        if (data == null) {
            player.sendSystemMessage(Component.literal("Unknown dungeon: " + dungeonId).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (data.soloOnly && player.getServer() != null) {
            for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
                DungeonSession session = ACTIVE_SESSIONS.get(other.getUUID());
                if (session != null && dungeonId.equals(session.dungeonId) && !other.getUUID().equals(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("That dungeon is currently occupied. Dungeons are solo only.").withStyle(ChatFormatting.RED));
                    return 0;
                }
            }
        }

        DungeonRarity rarity = DungeonRarity.parse(data.rarity);

        if (!DungeonLimitManager.canStartDungeon(player, rarity)) {
            return 0;
        }

        if (!DungeonKeyManager.hasKey(player, data.keyId)) {
            player.sendSystemMessage(Component.literal("You need a " + data.keyId + " to enter this dungeon.").withStyle(ChatFormatting.RED));
            return 0;
        }

        MinecraftServer server = player.getServer();
        if (server == null) return 0;

        ServerLevel destination = getLevel(server, data.world);
        if (destination == null) {
            player.sendSystemMessage(Component.literal("Dungeon world not found: " + normalizeWorldId(data.world) + ". Make sure the multiworld is loaded/created.").withStyle(ChatFormatting.RED));
            return 0;
        }

        MatchmakingManager.leaveQueue(player);

        if (!DungeonKeyManager.consumeKey(player, data.keyId)) {
            player.sendSystemMessage(Component.literal("Failed to consume dungeon key.").withStyle(ChatFormatting.RED));
            return 0;
        }

        BattlePrepManager.healParty(player);

        DungeonSession session = new DungeonSession(
                player.getUUID(),
                player.getName().getString(),
                dungeonId,
                data.displayName == null || data.displayName.isBlank() ? dungeonId : data.displayName,
                rarity,
                destination.dimension(),
                data.x,
                data.y,
                data.z,
                player.serverLevel().dimension(),
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                player.getXRot()
        );

        DungeonTeamLockManager.saveSnapshot(player, session);
        ACTIVE_SESSIONS.put(player.getUUID(), session);

        systemTeleport(player, destination, data.x, data.y, data.z, data.yaw, data.pitch);
        player.playNotifySound(SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8F, 1.2F);

        player.sendSystemMessage(Component.literal("Entered " + session.displayName + ".").withStyle(rarity.getColor(), ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Your party was healed and locked. Changing party members forfeits the dungeon.").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Commands are locked inside dungeons. Use /dungeon forfeit to leave.").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Wave 1 begins now. Defeat every trainer to clear the dungeon.").withStyle(ChatFormatting.YELLOW));

        server.execute(() -> startNextTrainer(player));
        return 1;
    }

    public static int forfeitDungeon(ServerPlayer player) {
        if (player == null) return 0;

        DungeonSession session = ACTIVE_SESSIONS.remove(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("You are not inside a dungeon.").withStyle(ChatFormatting.RED));
            return 0;
        }

        DungeonTeamLockManager.clear(player);
        cleanupTrainerEntity(player.getServer(), session);
        teleportBack(player, session);
        player.sendSystemMessage(Component.literal("You forfeited " + session.displayName + ". Your key was not refunded.").withStyle(ChatFormatting.RED));
        return 1;
    }

    public static void handleDisconnect(ServerPlayer player) {
        if (player == null) return;
        DungeonSession session = ACTIVE_SESSIONS.remove(player.getUUID());
        if (session != null) {
            DungeonTeamLockManager.clear(player);
            cleanupTrainerEntity(player.getServer(), session);
        }
    }

    public static void handleJoinCleanup(ServerPlayer player) {
        if (player == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // Sessions are intentionally not persisted across restarts/disconnects.
        // If a player logs back in inside a dungeon instance world without an active session,
        // move them safely to spawn so they cannot continue or farm rewards.
        if (isDungeonInstanceWorld(player)) {
            ServerLevel overworld = server.overworld();
            systemTeleport(
                    player,
                    overworld,
                    overworld.getSharedSpawnPos().getX() + 0.5D,
                    overworld.getSharedSpawnPos().getY(),
                    overworld.getSharedSpawnPos().getZ() + 0.5D,
                    0.0F,
                    0.0F
            );
            player.sendSystemMessage(Component.literal("Your previous dungeon session was closed safely. You were returned to spawn.").withStyle(ChatFormatting.YELLOW));
        }
    }

    public static void handleServerStopping(MinecraftServer server) {
        if (server == null || ACTIVE_SESSIONS.isEmpty()) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DungeonSession session = ACTIVE_SESSIONS.get(player.getUUID());
            if (session == null) continue;
            DungeonTeamLockManager.clear(player);
            cleanupTrainerEntity(server, session);
            teleportBack(player, session);
            player.sendSystemMessage(Component.literal("Your dungeon was closed because the server is stopping. Your key was not refunded.").withStyle(ChatFormatting.YELLOW));
        }

        ACTIVE_SESSIONS.clear();
        TRAINER_TO_PLAYER.clear();
    }

    public static void handleTrainerVictory(ServerPlayer player, UUID defeatedTrainerUuid) {
        if (player == null || defeatedTrainerUuid == null) return;
        DungeonSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!defeatedTrainerUuid.equals(session.activeTrainerUuid)) return;

        cleanupTrainerEntity(player.getServer(), session);
        session.currentTrainerIndex++;

        DungeonConfig.DungeonData dungeon = DungeonConfig.DUNGEONS.get(session.dungeonId);
        int max = dungeon == null ? 0 : Math.max(1, dungeon.trainerCount);

        if (session.currentTrainerIndex >= max) {
            completeDungeon(player, session);
            return;
        }

        player.sendSystemMessage(Component.literal("Trainer defeated. Next wave: " + (session.currentTrainerIndex + 1) + "/" + max).withStyle(ChatFormatting.GREEN));
        ServerPlayer p = player;
        player.getServer().execute(() -> startNextTrainer(p));
    }

    public static void handlePlayerLost(ServerPlayer player, UUID winningTrainerUuid) {
        if (player == null || winningTrainerUuid == null) return;
        DungeonSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;
        if (!winningTrainerUuid.equals(session.activeTrainerUuid)) return;

        ACTIVE_SESSIONS.remove(player.getUUID());
        DungeonTeamLockManager.clear(player);
        cleanupTrainerEntity(player.getServer(), session);
        teleportBack(player, session);
        player.sendSystemMessage(Component.literal("You were defeated and removed from " + session.displayName + ".").withStyle(ChatFormatting.RED));
    }

    private static void completeDungeon(ServerPlayer player, DungeonSession session) {
        ACTIVE_SESSIONS.remove(player.getUUID());
        DungeonTeamLockManager.clear(player);
        session.completed = true;
        player.sendSystemMessage(Component.literal("Dungeon cleared: " + session.displayName + "!").withStyle(session.rarity.getColor(), ChatFormatting.BOLD));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        DungeonLimitManager.recordDungeonClear(player, session.rarity);
        DungeonProgressDatabaseRepository.recordClear(
                player.getUUID(),
                player.getName().getString(),
                session.dungeonId,
                session.rarity
        );
        DungeonRewardManager.grantCompletionRewards(player, session);
        teleportBack(player, session);
    }

    public static boolean startNextTrainer(ServerPlayer player) {
        DungeonSession session = getSession(player);
        if (session == null) return false;

        if (!DungeonTeamLockManager.matchesSnapshot(player, session)) {
            autoForfeitForTeamChange(player, session);
            return false;
        }

        DungeonConfig.DungeonData dungeon = DungeonConfig.DUNGEONS.get(session.dungeonId);
        DungeonTrainerConfig.DungeonTrainerData trainerData = DungeonTrainerConfig.TRAINERS.get(session.dungeonId);

        if (dungeon == null) {
            player.sendSystemMessage(Component.literal("Dungeon config disappeared. Forfeiting safely.").withStyle(ChatFormatting.RED));
            forfeitDungeon(player);
            return false;
        }

        if (trainerData == null || trainerData.waves == null || trainerData.waves.isEmpty()) {
            player.sendSystemMessage(Component.literal("No dungeon_trainers.json waves configured for " + session.dungeonId + ".").withStyle(ChatFormatting.RED));
            forfeitDungeon(player);
            return false;
        }

        int waveIndex = Math.min(session.currentTrainerIndex, trainerData.waves.size() - 1);
        DungeonTrainerConfig.TrainerWave wave = trainerData.waves.get(waveIndex);
        ServerLevel level = player.getServer().getLevel(session.dungeonWorld);
        if (level == null) return false;

        cleanupTrainerEntity(player.getServer(), session);

        Vec3 pos = new Vec3(session.dungeonX + 2.0D, session.dungeonY, session.dungeonZ + 2.0D);
        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, 180.0F, wave.trainerName, wave.spawnSkin);
        if (npc == null) {
            player.sendSystemMessage(Component.literal("Failed to spawn dungeon trainer.").withStyle(ChatFormatting.RED));
            return false;
        }

        boolean teamOk = DungeonNpcPartyBuilder.applyDungeonTeam(npc, wave, session.rarity.getPokemonLevel());
        if (!teamOk) {
            try { npc.discard(); } catch (Exception ignored) {}
            player.sendSystemMessage(Component.literal("Failed to build dungeon trainer team.").withStyle(ChatFormatting.RED));
            return false;
        }

        session.activeTrainerUuid = npc.getUUID();
        TRAINER_TO_PLAYER.put(npc.getUUID(), player.getUUID());

        player.sendSystemMessage(Component.literal("Battle " + (session.currentTrainerIndex + 1) + "/" + Math.max(1, dungeon.trainerCount) + ": " + wave.trainerName).withStyle(ChatFormatting.YELLOW));
        player.getServer().execute(() -> startBattleReflective(player, npc));
        return true;
    }


    private static void autoForfeitForTeamChange(ServerPlayer player, DungeonSession session) {
        if (player == null || session == null) return;
        ACTIVE_SESSIONS.remove(player.getUUID());
        DungeonTeamLockManager.clear(player);
        cleanupTrainerEntity(player.getServer(), session);
        teleportBack(player, session);
        player.sendSystemMessage(Component.literal("Your dungeon party changed, so the dungeon was forfeited.").withStyle(ChatFormatting.RED));
    }

    public static void cleanupTrainerEntity(MinecraftServer server, DungeonSession session) {
        if (server == null || session == null || session.activeTrainerUuid == null) return;
        UUID uuid = session.activeTrainerUuid;
        TRAINER_TO_PLAYER.remove(uuid);
        for (ServerLevel level : server.getAllLevels()) {
            NPCEntity npc = level.getEntity(uuid) instanceof NPCEntity n ? n : null;
            if (npc != null) {
                try { npc.discard(); } catch (Exception ignored) {}
                break;
            }
        }
        session.activeTrainerUuid = null;
    }

    private static void startBattleReflective(ServerPlayer player, NPCEntity npc) {
        try {
            Class<?> builder = Class.forName("com.cobblemon.mod.common.battles.BattleBuilder");
            Object instance = null;
            try { instance = builder.getField("INSTANCE").get(null); } catch (Exception ignored) {}

            for (Method method : builder.getMethods()) {
                if (!method.getName().equals("pvn")) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length >= 2 && params[0].isAssignableFrom(ServerPlayer.class) && params[1].isAssignableFrom(NPCEntity.class)) {
                    Object[] args = new Object[params.length];
                    args[0] = player;
                    args[1] = npc;
                    for (int i = 2; i < args.length; i++) args[i] = defaultValue(params[i]);
                    method.invoke(instance, args);
                    return;
                }
            }

            player.sendSystemMessage(Component.literal("Could not find Cobblemon BattleBuilder.pvn method. Right-click the trainer to begin.").withStyle(ChatFormatting.RED));
        } catch (Exception e) {
            e.printStackTrace();
            player.sendSystemMessage(Component.literal("Failed to auto-start dungeon battle. Right-click the trainer to begin.").withStyle(ChatFormatting.RED));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return (char) 0;
        return null;
    }

    public static boolean isAllowedDungeonCommand(ServerPlayer player, String command) {
        if (!isInDungeon(player)) return true;
        String normalized = normalizeCommand(command);
        return normalized.equals("dungeon forfeit")
                || normalized.equals("dungeon status")
                || normalized.equals("spawn");
    }

    public static boolean isSpawnCommand(String command) {
        return normalizeCommand(command).equals("spawn");
    }

    private static String normalizeCommand(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase();
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        return normalized;
    }

    public static void sendLimitsFromMenu(ServerPlayer player) {
        if (player == null) return;
        DungeonLimitManager.sendLimits(player);
    }

    public static void sendStatus(ServerPlayer player) {
        DungeonSession session = getSession(player);
        if (session == null) {
            player.sendSystemMessage(Component.literal("You are not inside a dungeon.").withStyle(ChatFormatting.GRAY));
            return;
        }
        DungeonConfig.DungeonData dungeon = DungeonConfig.DUNGEONS.get(session.dungeonId);
        int max = dungeon == null ? 0 : Math.max(1, dungeon.trainerCount);
        player.sendSystemMessage(Component.literal("Dungeon: " + session.displayName).withStyle(session.rarity.getColor(), ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal("Tier: " + session.rarity.name() + " | Pokemon Level: " + session.rarity.getPokemonLevel()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Battle: " + (session.currentTrainerIndex + 1) + "/" + max).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Use /dungeon forfeit to leave.").withStyle(ChatFormatting.RED));
    }

    private static void teleportBack(ServerPlayer player, DungeonSession session) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        cleanupTrainerEntity(server, session);
        ServerLevel level = server.getLevel(session.returnWorld);
        if (level == null) level = server.overworld();
        systemTeleport(player, level, session.returnX, session.returnY, session.returnZ, session.returnYaw, session.returnPitch);
        player.playNotifySound(SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.0F);
    }

    public static void tickTeleportGuard(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            DungeonSession session = ACTIVE_SESSIONS.get(player.getUUID());
            if (session == null) {
                continue;
            }

            // Radius protection was removed now that dungeons live in the dedicated instances world.
            // Active dungeon players are only required to remain in their assigned dungeon dimension.
            // Command locking prevents normal teleport commands, and /spawn intentionally forfeits.
            if (!player.serverLevel().dimension().equals(session.dungeonWorld)) {
                ACTIVE_SESSIONS.remove(player.getUUID());
                DungeonTeamLockManager.clear(player);
                cleanupTrainerEntity(server, session);
                player.sendSystemMessage(Component.literal("You left the dungeon instance, so the dungeon was forfeited.").withStyle(ChatFormatting.RED));
            }
        }
    }

    private static boolean isDungeonInstanceWorld(ServerPlayer player) {
        if (player == null) return false;
        for (DungeonConfig.DungeonData data : DungeonConfig.DUNGEONS.values()) {
            if (data == null || data.world == null || data.world.isBlank()) continue;
            try {
                ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(normalizeWorldId(data.world)));
                if (key.equals(player.serverLevel().dimension())) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static void systemTeleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        player.teleportTo(level, x, y, z, yaw, pitch);
    }

    private static String normalizeWorldId(String id) {
        if (id == null || id.isBlank()) return DungeonConfig.DEFAULT_DUNGEON_WORLD;
        String trimmed = id.trim();
        if (trimmed.equalsIgnoreCase("instances")) return DungeonConfig.DEFAULT_DUNGEON_WORLD;
        if (trimmed.equalsIgnoreCase("overworld")) return "minecraft:overworld";
        if (!trimmed.contains(":")) return "minecraft:" + trimmed;
        return trimmed;
    }

    private static ServerLevel getLevel(MinecraftServer server, String id) {
        if (server == null) return null;
        String normalized = normalizeWorldId(id);
        if (normalized.equalsIgnoreCase("minecraft:overworld")) return server.overworld();
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(normalized));
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }
}
