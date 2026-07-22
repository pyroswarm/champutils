package com.champutils.survival;

import com.champutils.database.SharedJsonStateRepository;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;
import com.champutils.menu.ConfirmationMenu;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HostileToggleManager {
    private static final Map<UUID, Boolean> DISABLED = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING_CONFIRM = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/champutils/hostile_toggle.json");
    private static final String STATE_KEY = "hostile_toggle";
    private static final long COOLDOWN_MS = 24L * 60L * 60L * 1000L;
    private static final double RADIUS_SQ = 96.0D * 96.0D;
    private static int tickCounter = 0;
    private static boolean registered = false;
    private HostileToggleManager() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        load();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("togglehostile")
                        .executes(ctx -> requestToggle(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> toggle(ctx.getSource().getPlayerOrException()))))
        );
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> removeIfBlocked(entity, world));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter % 5 != 0 || DISABLED.isEmpty()) return;
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    if (!Boolean.TRUE.equals(DISABLED.get(player.getUUID()))) continue;
                    net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(96.0D);
                    for (Mob mob : level.getEntitiesOfClass(Mob.class, box)) removeIfBlocked(mob, level);
                }
            }
        });
    }

    private static void removeIfBlocked(net.minecraft.world.entity.Entity entity, net.minecraft.server.level.ServerLevel world) {
        if (!(entity instanceof Mob)) return;
        // Trial Chamber spawners are encounter mechanics, not ambient hostile spawning.
        // Trial-spawned mobs do not expose a stable public spawn-reason API in 1.21.1,
        // so identify them at entity load by the nearby trial spawner that created them,
        // then permanently tag them before hostile-protection cleanup runs.
        if (entity.getTags().contains("champutils_allow_hostile_toggle")) return;
        try { if (entity.getType().getCategory() != MobCategory.MONSTER) return; } catch (Throwable ignored) { return; }
        // Cache the proximity decision on the entity so the five-tick cleanup loop never
        // performs a block-volume scan repeatedly for the same ordinary hostile mob.
        if (!entity.getTags().contains("champutils_trial_spawner_checked")) {
            entity.addTag("champutils_trial_spawner_checked");
            if (isNearTrialSpawner(entity, world)) {
                entity.addTag("champutils_allow_hostile_toggle");
                return;
            }
        }
        for (ServerPlayer player : world.players()) {
            if (!Boolean.TRUE.equals(DISABLED.get(player.getUUID()))) continue;
            if (player.distanceToSqr(entity) <= RADIUS_SQ) {
                entity.discard();
                return;
            }
        }
    }

    /**
     * Vanilla trial spawners create mobs within a small radius of the block. Scanning an
     * 8x6x8 box only when an untagged hostile entity loads/enters cleanup keeps this cheap
     * while avoiding exemptions for ordinary hostile mobs elsewhere in the world.
     */
    private static boolean isNearTrialSpawner(net.minecraft.world.entity.Entity entity, ServerLevel world) {
        BlockPos origin = entity.blockPosition();
        final int horizontalRadius = 8;
        final int verticalRadius = 6;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int x = -horizontalRadius; x <= horizontalRadius; x++) {
                for (int z = -horizontalRadius; z <= horizontalRadius; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (world.getBlockState(cursor).is(Blocks.TRIAL_SPAWNER)) return true;
                }
            }
        }
        return false;
    }

    private static int requestToggle(ServerPlayer player) {
        long wait = cooldownRemaining(player);
        if (wait > 0 && !player.hasPermissions(4)) {
            long hours = Math.max(1L, (wait + 3599999L) / 3600000L);
            player.sendSystemMessage(Component.literal("You can toggle hostile protection again in " + hours + " hour(s). Need 24 hours between toggles.").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean currentlyDisabled = Boolean.TRUE.equals(DISABLED.get(player.getUUID()));
        ConfirmationMenu.open(
                player,
                "Confirm Hostile Toggle",
                currentlyDisabled ? Items.ZOMBIE_HEAD : Items.TORCH,
                currentlyDisabled ? "§eEnable Hostile Mobs" : "§eDisable Hostile Mobs",
                new String[]{
                        currentlyDisabled
                                ? "§7Hostile mob spawning near you will be turned back ON."
                                : "§7Hostile mob spawning near you will be turned OFF.",
                        "§7Cobblemon spawns are not affected.",
                        "§cThis uses your 24 hour hostile toggle."
                },
                () -> toggle(player),
                () -> player.sendSystemMessage(Component.literal("§eHostile toggle cancelled."))
        );
        return 1;
    }

    private static int toggle(ServerPlayer player) {
        PENDING_CONFIRM.remove(player.getUUID());
        long now = System.currentTimeMillis();
        long wait = cooldownRemaining(player);
        if (wait > 0 && !player.hasPermissions(4)) {
            long hours = Math.max(1L, (wait + 3599999L) / 3600000L);
            player.sendSystemMessage(Component.literal("You can toggle hostile protection again in " + hours + " hour(s).").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean next = !Boolean.TRUE.equals(DISABLED.get(player.getUUID()));
        DISABLED.put(player.getUUID(), next);
        LAST_TOGGLE.put(player.getUUID(), now);
        savePlayer(player.getUUID());
        save();
        player.sendSystemMessage(Component.literal(next
                ? "Hostile mob spawning near you is now disabled. Cobblemon spawns are not affected."
                : "Hostile mob spawning near you is now enabled again.").withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return 1;
    }
    private static long cooldownRemaining(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long last = LAST_TOGGLE.getOrDefault(player.getUUID(), 0L);
        return COOLDOWN_MS - (now - last);
    }

    public static synchronized void preload(UUID playerId) {
        if (playerId == null) return;
        PlayerState fallback = new PlayerState();
        fallback.disabled = Boolean.TRUE.equals(DISABLED.get(playerId));
        fallback.lastToggleMillis = LAST_TOGGLE.getOrDefault(playerId, 0L);
        PlayerState shared = SharedJsonStateRepository.loadPlayer(playerId, STATE_KEY, PlayerState.class, fallback);
        PlayerState selected = shared == null ? fallback : shared;
        if (selected.disabled) DISABLED.put(playerId, true); else DISABLED.remove(playerId);
        LAST_TOGGLE.put(playerId, Math.max(0L, selected.lastToggleMillis));
        save();
    }

    private static void savePlayer(UUID playerId) {
        if (playerId == null) return;
        PlayerState state = new PlayerState();
        state.disabled = Boolean.TRUE.equals(DISABLED.get(playerId));
        state.lastToggleMillis = LAST_TOGGLE.getOrDefault(playerId, 0L);
        SharedJsonStateRepository.savePlayer(playerId, STATE_KEY, state);
    }

    private static synchronized void load() {
        try {
            if (!FILE.exists()) return;
            try (FileReader reader = new FileReader(FILE)) {
                Data data = GSON.fromJson(reader, Data.class);
                DISABLED.clear(); LAST_TOGGLE.clear();
                if (data != null) {
                    if (data.disabled != null) for (String id : data.disabled) try { DISABLED.put(UUID.fromString(id), true); } catch (Exception ignored) {}
                    if (data.lastToggleMillis != null) for (Map.Entry<String, Long> e : data.lastToggleMillis.entrySet()) try { LAST_TOGGLE.put(UUID.fromString(e.getKey()), e.getValue()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) { System.err.println("[ChampUtils] Failed to load hostile toggle data"); e.printStackTrace(); }
    }

    private static synchronized void save() {
        try {
            File parent = FILE.getParentFile(); if (parent != null && !parent.exists()) parent.mkdirs();
            Data data = new Data();
            for (UUID id : DISABLED.keySet()) if (Boolean.TRUE.equals(DISABLED.get(id))) data.disabled.add(id.toString());
            for (Map.Entry<UUID, Long> e : LAST_TOGGLE.entrySet()) data.lastToggleMillis.put(e.getKey().toString(), e.getValue());
            try (FileWriter writer = new FileWriter(FILE)) { GSON.toJson(data, writer); }
        } catch (Exception e) { System.err.println("[ChampUtils] Failed to save hostile toggle data"); e.printStackTrace(); }
    }

    private static final class PlayerState { boolean disabled; long lastToggleMillis; }
    private static final class Data { java.util.List<String> disabled = new java.util.ArrayList<>(); Map<String, Long> lastToggleMillis = new java.util.HashMap<>(); }

}
