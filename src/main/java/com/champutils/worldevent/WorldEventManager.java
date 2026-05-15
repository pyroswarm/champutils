package com.champutils.worldevent;

import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.profession.ProfessionManager;
import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * World events now use pre-existing bound NPCs instead of spawning/despawning NPCs.
 */
public final class WorldEventManager {

    private static final Random RANDOM = new Random();
    private static final Map<String, ActiveEvent> ACTIVE_EVENTS = new HashMap<>();
    private static long nextCheckTick = -1L;

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
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, WorldEventConfig.EventDefinition> entry : WorldEventConfig.EVENTS.entrySet()) {
            if (entry.getValue() != null && entry.getValue().enabled) ids.add(entry.getKey());
        }
        if (ids.isEmpty()) return false;

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.remove(RANDOM.nextInt(ids.size()));
            if (start(server, id, force)) return true;
        }

        return false;
    }

    public static boolean start(MinecraftServer server, String eventId, boolean force) {
        if (server == null || eventId == null || eventId.isBlank()) return false;
        if (!force && ACTIVE_EVENTS.size() >= WorldEventConfig.MAX_ACTIVE_EVENTS) return false;
        if (ACTIVE_EVENTS.containsKey(eventId)) return false;

        WorldEventConfig.EventDefinition event = WorldEventConfig.EVENTS.get(eventId);
        if (event == null || !event.enabled) return false;

        Entity bound = WorldEventBindingRegistry.findBoundNpc(server, eventId);
        if (!(bound instanceof NPCEntity npc)) {
            return false;
        }

        WorldEventConfig.TeamDefinition team = selectTeam(event);
        if (team == null) return false;

        boolean applied = WorldEventBossPartyBuilder.applyTeam(npc, team);
        if (!applied) return false;

        try { npc.setCustomName(Component.literal(event.bossName == null || event.bossName.isBlank() ? event.displayName : event.bossName)); } catch (Exception ignored) {}
        try { npc.setCustomNameVisible(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}

        ActiveEvent active = new ActiveEvent();
        active.eventId = eventId;
        active.displayName = event.displayName;
        active.bossName = event.bossName;
        active.npcUuid = npc.getUUID();
        active.world = npc.level().dimension().location();
        active.pos = npc.blockPosition();
        active.expireTick = server.getTickCount() + minutesToTicks(event.despawnMinutes);
        active.definition = event;
        active.team = team;
        ACTIVE_EVENTS.put(eventId, active);

        announceStart(server, active);
        return true;
    }

    public static boolean stop(MinecraftServer server, String eventId, boolean announce) {
        if (server == null || eventId == null) return false;
        ActiveEvent active = ACTIVE_EVENTS.remove(eventId);
        if (active == null) return false;
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

        Entity npc = WorldEventBindingRegistry.findBoundNpc(player.getServer(), eventId);
        if (npc != null) {
            active.world = npc.level().dimension().location();
            active.pos = npc.blockPosition();
        }

        ServerLevel level = getLevel(player.getServer(), active.world);
        if (level == null) return false;

        int yOffset = active.definition == null ? 1 : active.definition.teleportYOffset;
        try {
            String playerName = player.getGameProfile().getName();
            String command = "execute in " + active.world + " run tp " + playerName + " "
                    + (active.pos.getX() + 0.5D) + " "
                    + (active.pos.getY() + yOffset) + " "
                    + (active.pos.getZ() + 0.5D);
            var source = player.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(4);
            player.getServer().getCommands().performPrefixedCommand(source, "/" + command);
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
            if (winner != null) rewardWinner(winner, active);
        }

        String winnerNames = winners.get(0).getName().getString();
        if (winners.size() > 1) winnerNames += " and allies";

        broadcast(server, Component.literal("§6" + winnerNames + " §ahas defeated §c" + active.bossName + " §ain §e" + active.displayName + "§a!"));
    }

    private static void rewardWinner(ServerPlayer player, ActiveEvent active) {
        WorldEventConfig.RewardTable rewards = active.definition == null ? null : active.definition.rewards;
        if (rewards == null) return;

        int min = Math.max(1, Math.min(rewards.minFragments, rewards.maxFragments));
        int max = Math.max(min, Math.max(rewards.minFragments, rewards.maxFragments));
        int rolls = min + RANDOM.nextInt((max - min) + 1);

        Map<String, Integer> awarded = new HashMap<>();
        for (int i = 0; i < rolls; i++) {
            String rarity = rollFragmentRarity(rewards.fragmentWeights);
            if (rarity == null) continue;
            String normalized = ProfessionFragmentConfig.normalizeRarity(rarity);
            ProfessionManager.addFragments(player, normalized, 1);
            awarded.put(normalized, awarded.getOrDefault(normalized, 0) + 1);
        }

        if (!awarded.isEmpty()) {
            player.sendSystemMessage(Component.literal("§dWorld Event Rewards:"));
            for (Map.Entry<String, Integer> entry : awarded.entrySet()) {
                player.sendSystemMessage(Component.literal("§7- §6" + entry.getValue() + "x §f" + ProfessionFragmentManager.formatWords(entry.getKey()) + " Fragment"));
            }
        }
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
                broadcast(server, Component.literal("§cThe world event §6" + active.displayName + " §chas disappeared."));
            }
        }
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

    private static ServerLevel getLevel(MinecraftServer server, ResourceLocation worldId) {
        if (server == null || worldId == null) return null;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, worldId);
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
            player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.45F, 1.3F);
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
