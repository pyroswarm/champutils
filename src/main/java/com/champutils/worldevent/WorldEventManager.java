package com.champutils.worldevent;

import com.champutils.database.WorldEventStatsDatabaseRepository;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.trainer.ChampTrainerProtectionManager;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Random overworld world events backed by ChampUtils native trainer NPCs.
 * Events choose a safe ground-level position, spawn a protected trainer NPC,
 * announce a teleport button, and despawn after the configured duration.
 */
public final class WorldEventManager {

    private static final Random RANDOM = new Random();
    private static final Map<String, ActiveEvent> ACTIVE_EVENTS = new HashMap<>();
    private static long nextCheckTick = -1L;
    private static String lastStartFailure = "";
    private static String lastRandomEventId = null;

    private WorldEventManager() {}

    public static class ActiveEvent {
        public String eventId;
        public String displayName;
        public String bossName;
        public UUID npcUuid;
        public ResourceLocation world;
        public BlockPos pos;
        public long expireTick;
        public WorldEventConfig.EventDefinition definition;
        public WorldEventConfig.TeamDefinition team;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || !WorldEventConfig.ENABLED) return;
        long now = server.getTickCount();

        expireOldEvents(server, now);

        if (nextCheckTick < 0L) {
            nextCheckTick = now + minutesToTicks(WorldEventConfig.CHECK_INTERVAL_MINUTES);
            return;
        }

        if (now < nextCheckTick) return;
        nextCheckTick = now + minutesToTicks(WorldEventConfig.CHECK_INTERVAL_MINUTES);

        if (ACTIVE_EVENTS.size() >= WorldEventConfig.MAX_ACTIVE_EVENTS) return;
        if (RANDOM.nextDouble() > WorldEventConfig.EVENT_CHANCE) return;

        startRandom(server, false);
    }

    public static boolean startRandom(MinecraftServer server, boolean force) {
        lastStartFailure = "";
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, WorldEventConfig.EventDefinition> entry : WorldEventConfig.EVENTS.entrySet()) {
            if (entry.getValue() != null && entry.getValue().enabled) ids.add(entry.getKey());
        }
        if (ids.isEmpty()) {
            lastStartFailure = "No enabled world events are configured.";
            return false;
        }

        String finalFailure = "";
        while (!ids.isEmpty()) {
            String id = pickWeightedRandomEvent(ids);
            ids.remove(id);

            if (start(server, id, force)) {
                lastRandomEventId = id;
                return true;
            }

            String reason = getLastStartFailure();
            if (reason != null && !reason.isBlank()) {
                finalFailure = id + ": " + reason;
            }
        }

        lastStartFailure = finalFailure.isBlank() ? "No random world event could start." : finalFailure;
        return false;
    }

    public static boolean start(MinecraftServer server, String eventId, boolean force) {
        lastStartFailure = "";
        if (server == null || eventId == null || eventId.isBlank()) { lastStartFailure = "Server or event id was invalid."; return false; }
        if (!force && ACTIVE_EVENTS.size() >= WorldEventConfig.MAX_ACTIVE_EVENTS) { lastStartFailure = "Maximum active world events reached."; return false; }
        if (ACTIVE_EVENTS.containsKey(eventId)) { lastStartFailure = "That event is already active."; return false; }

        WorldEventConfig.EventDefinition event = WorldEventConfig.EVENTS.get(eventId);
        if (event == null || !event.enabled) { lastStartFailure = "Event is missing or disabled in world_events.json."; return false; }

        ServerLevel level = getEventLevel(server, event);
        if (level == null) { lastStartFailure = "Could not find the configured event world/dimension."; return false; }

        BlockPos spawnPos = WorldEventSpawnFinder.find(level, event);
        if (spawnPos == null) {
            lastStartFailure = "No safe ground spawn was found. Check spawnRadiusMin/spawnRadiusMax, ocean-heavy maps, world border, and Flan claims.";
            System.out.println("[ChampUtils] Could not find safe world event spawn for " + eventId + ". " + lastStartFailure);
            return false;
        }

        WorldEventConfig.TeamDefinition team = selectTeam(event);
        if (team == null) { lastStartFailure = "No team is configured for this world event."; return false; }

        float yaw = RANDOM.nextFloat() * 360.0F;
        Vec3 precisePos = Vec3.atBottomCenterOf(spawnPos);
        ChampTrainerSpawner.SpawnResult spawn = ChampTrainerSpawner.spawn(level, precisePos, yaw, eventId);
        if (spawn == null || !spawn.success || spawn.npc == null) {
            lastStartFailure = "Trainer NPC spawn failed: " + (spawn == null ? "null result" : spawn.message);
            System.out.println("[ChampUtils] Failed to spawn world event trainer " + eventId + ": " + (spawn == null ? "null result" : spawn.message));
            return false;
        }
        NPCEntity npc = spawn.npc;

        boolean applied = WorldEventBossPartyBuilder.applyTeam(npc, team);
        if (!applied) {
            removeNpc(server, npc.getUUID());
            WorldEventBindingRegistry.unbind(eventId);
            ChampTrainerProtectionManager.untrack(npc.getUUID());
            lastStartFailure = "NPC spawned, but applying the configured Pokémon team failed.";
            return false;
        }

        try { npc.setCustomName(Component.literal(event.bossName == null || event.bossName.isBlank() ? event.displayName : event.bossName)); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}

        ActiveEvent active = new ActiveEvent();
        active.eventId = eventId;
        active.displayName = event.displayName;
        active.bossName = event.bossName;
        active.npcUuid = npc.getUUID();
        active.world = level.dimension().location();
        active.pos = npc.blockPosition();
        active.expireTick = server.getTickCount() + minutesToTicks(event.despawnMinutes);
        active.definition = event;
        active.team = team;
        ACTIVE_EVENTS.put(eventId, active);

        announceStart(server, active);
        return true;
    }

    private static String pickWeightedRandomEvent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;

        int total = 0;
        Map<String, Integer> effectiveWeights = new HashMap<>();
        boolean hasNonRepeatCandidate = ids.stream().anyMatch(id -> !id.equals(lastRandomEventId));

        for (String id : ids) {
            WorldEventConfig.EventDefinition event = WorldEventConfig.EVENTS.get(id);
            int baseWeight = event == null ? 1 : Math.max(1, event.weight);
            int effectiveWeight = baseWeight;

            if (hasNonRepeatCandidate && id.equals(lastRandomEventId)) {
                effectiveWeight = (int) Math.floor(baseWeight * WorldEventConfig.REPEAT_EVENT_WEIGHT_MULTIPLIER);
                effectiveWeight = Math.max(0, effectiveWeight);
            }

            effectiveWeights.put(id, effectiveWeight);
            total += effectiveWeight;
        }

        if (total <= 0) {
            List<String> fallback = new ArrayList<>();
            for (String id : ids) {
                if (!id.equals(lastRandomEventId)) fallback.add(id);
            }
            if (fallback.isEmpty()) fallback.addAll(ids);
            return fallback.get(RANDOM.nextInt(fallback.size()));
        }

        int roll = RANDOM.nextInt(total);
        for (String id : ids) {
            roll -= effectiveWeights.getOrDefault(id, 0);
            if (roll < 0) return id;
        }
        return ids.get(RANDOM.nextInt(ids.size()));
    }

    public static String getLastStartFailure() {
        return lastStartFailure == null ? "" : lastStartFailure;
    }

    public static boolean stop(MinecraftServer server, String eventId, boolean announce) {
        if (server == null || eventId == null) return false;
        ActiveEvent active = ACTIVE_EVENTS.remove(eventId);
        if (active == null) return false;

        despawnActiveNpc(server, active);

        if (announce) {
            broadcast(server, Component.literal("§cThe world event §6" + active.displayName + " §chas ended."));
        }
        return true;
    }

    public static void stopAll(MinecraftServer server) {
        for (String id : new ArrayList<>(ACTIVE_EVENTS.keySet())) stop(server, id, false);
    }

    public static ActiveEvent getByNpc(UUID npcUuid) {
        if (npcUuid == null) return null;
        for (ActiveEvent active : ACTIVE_EVENTS.values()) {
            if (npcUuid.equals(active.npcUuid)) return active;
        }
        return null;
    }

    public static List<ActiveEvent> getActiveEvents() {
        return new ArrayList<>(ACTIVE_EVENTS.values());
    }

    public static boolean teleport(ServerPlayer player, String eventId) {
        if (player == null || player.getServer() == null) return false;
        ActiveEvent active = ACTIVE_EVENTS.get(eventId);
        if (active == null) return false;

        Entity npc = findNpcByUuid(player.getServer(), active.npcUuid);
        if (npc != null) {
            active.world = npc.level().dimension().location();
            active.pos = npc.blockPosition();
        }

        ServerLevel level = getLevel(player.getServer(), active.world);
        if (level == null) return false;

        int yOffset = active.definition == null ? 1 : active.definition.teleportYOffset;
        try {
            player.teleportTo(level, active.pos.getX() + 0.5D, active.pos.getY() + yOffset, active.pos.getZ() + 0.5D, player.getYRot(), player.getXRot());
        } catch (Exception e) {
            return false;
        }
        player.sendSystemMessage(Component.literal("§aTeleported to §6" + active.displayName + "§a."));
        return true;
    }

    public static void handleBossDefeated(MinecraftServer server, ActiveEvent active, ServerPlayer winner) {
        if (winner == null) return;
        List<ServerPlayer> winners = new ArrayList<>();
        winners.add(winner);
        handleBossDefeated(server, active, winners);
    }

    public static void handleBossDefeated(MinecraftServer server, ActiveEvent active, List<ServerPlayer> winners) {
        if (server == null || active == null || winners == null || winners.isEmpty()) return;
        ACTIVE_EVENTS.remove(active.eventId);

        for (ServerPlayer winner : winners) {
            if (winner == null) {
                continue;
            }

            int rareDrops = rewardWinner(winner, active);

            WorldEventStatsDatabaseRepository.recordCompletion(
                    winner.getUUID(),
                    winner.getName().getString(),
                    active.eventId,
                    rareDrops
            );
        }

        String winnerNames = winners.get(0).getName().getString();
        if (winners.size() > 1) winnerNames += " and allies";

        broadcast(server, Component.literal("§6" + winnerNames + " §ahas defeated §c" + active.bossName + " §ain §e" + active.displayName + "§a!"));
        despawnActiveNpc(server, active);
    }

    private static int rewardWinner(ServerPlayer player, ActiveEvent active) {
        WorldEventConfig.RewardTable rewards = active.definition == null ? null : active.definition.rewards;
        if (rewards == null) return 0;

        int min = Math.max(1, Math.min(rewards.minFragments, rewards.maxFragments));
        int max = Math.max(min, Math.max(rewards.minFragments, rewards.maxFragments));
        int rolls = min + RANDOM.nextInt((max - min) + 1);

        Map<String, Integer> awarded = new HashMap<>();
        int rareDrops = 0;

        for (int i = 0; i < rolls; i++) {
            String rarity = rollFragmentRarity(rewards.fragmentWeights);
            if (rarity == null) continue;
            String normalized = ProfessionFragmentConfig.normalizeRarity(rarity);
            ProfessionManager.addFragments(player, normalized, 1);
            awarded.put(normalized, awarded.getOrDefault(normalized, 0) + 1);

            if (isRareWorldEventDrop(normalized)) {
                rareDrops++;
            }
        }

        if (!awarded.isEmpty()) {
            player.sendSystemMessage(Component.literal("§dWorld Event Rewards:"));
            for (Map.Entry<String, Integer> entry : awarded.entrySet()) {
                player.sendSystemMessage(Component.literal("§7- §6" + entry.getValue() + "x §f" + ProfessionFragmentManager.formatWords(entry.getKey()) + " Fragment"));
            }
        }

        return rareDrops;
    }

    private static boolean isRareWorldEventDrop(String rarity) {
        if (rarity == null) {
            return false;
        }

        String normalized = rarity.trim().toUpperCase();

        return normalized.equals("LEGENDARY") || normalized.equals("MYTHIC");
    }

    private static String rollFragmentRarity(Map<String, Integer> weights) {
        if (weights == null || weights.isEmpty()) return "EPIC";
        int total = 0;
        for (int weight : weights.values()) total += Math.max(0, weight);
        if (total <= 0) return "EPIC";
        int roll = RANDOM.nextInt(total);
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            roll -= Math.max(0, entry.getValue());
            if (roll < 0) return entry.getKey();
        }
        return "EPIC";
    }

    private static void expireOldEvents(MinecraftServer server, long now) {
        for (ActiveEvent active : new ArrayList<>(ACTIVE_EVENTS.values())) {
            if (now >= active.expireTick) {
                ACTIVE_EVENTS.remove(active.eventId);
                despawnActiveNpc(server, active);
                broadcast(server, Component.literal("§cThe world event §6" + active.displayName + " §chas disappeared."));
            }
        }
    }

    private static void despawnActiveNpc(MinecraftServer server, ActiveEvent active) {
        if (server == null || active == null) return;
        if (active.npcUuid != null) {
            ChampTrainerProtectionManager.untrack(active.npcUuid);
            removeNpc(server, active.npcUuid);
        }
        WorldEventBindingRegistry.unbind(active.eventId);
    }

    private static void removeNpc(MinecraftServer server, UUID uuid) {
        Entity entity = findNpcByUuid(server, uuid);
        if (entity == null) return;
        try { entity.discard(); } catch (Exception ignored) {}
        try { entity.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
    }

    private static Entity findNpcByUuid(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static WorldEventConfig.TeamDefinition selectTeam(WorldEventConfig.EventDefinition event) {
        if (event.teams == null || event.teams.isEmpty()) return null;
        int total = 0;
        for (WorldEventConfig.TeamDefinition team : event.teams) total += Math.max(1, team.weight);
        int roll = RANDOM.nextInt(Math.max(1, total));
        for (WorldEventConfig.TeamDefinition team : event.teams) {
            roll -= Math.max(1, team.weight);
            if (roll < 0) return team;
        }
        return event.teams.get(0);
    }

    private static ServerLevel getEventLevel(MinecraftServer server, WorldEventConfig.EventDefinition event) {
        if (server == null) return null;
        if (WorldEventConfig.OVERWORLD_ONLY) return server.overworld();
        ResourceLocation id = ResourceLocation.parse(event.world == null || event.world.isBlank() ? "minecraft:overworld" : event.world);
        return getLevel(server, id);
    }

    private static ServerLevel getLevel(MinecraftServer server, ResourceLocation worldId) {
        if (server == null || worldId == null) return null;
        ResourceKey<net.minecraft.world.level.Level> key = ResourceKey.create(Registries.DIMENSION, worldId);
        return server.getLevel(key);
    }

    private static void announceStart(MinecraftServer server, ActiveEvent active) {
        broadcast(server, Component.literal("§6⚠ World Event Started: §c" + active.displayName));
        broadcast(server, Component.literal("§7Boss: §f" + active.bossName + " §8| §7Location: §e" + active.pos.getX() + ", " + active.pos.getY() + ", " + active.pos.getZ()));

        if (WorldEventConfig.ANNOUNCE_TELEPORT_BUTTON) {
            Component button = Component.literal("§a[Click to Teleport]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/worldevent teleport " + active.eventId))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Teleport to " + active.displayName)))
                    );
            broadcast(server, Component.literal("§7Travel: ").append(button));
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.45F, 1.3F);
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        if (server == null || message == null) return;
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static long minutesToTicks(int minutes) {
        return Math.max(1L, minutes) * 60L * 20L;
    }
}
