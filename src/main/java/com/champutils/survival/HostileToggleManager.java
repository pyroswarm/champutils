package com.champutils.survival;

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
        try { if (entity.getType().getCategory() != MobCategory.MONSTER) return; } catch (Throwable ignored) { return; }
        for (ServerPlayer player : world.players()) {
            if (!Boolean.TRUE.equals(DISABLED.get(player.getUUID()))) continue;
            if (player.distanceToSqr(entity) <= RADIUS_SQ) {
                entity.discard();
                return;
            }
        }
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

    private static final class Data { java.util.List<String> disabled = new java.util.ArrayList<>(); Map<String, Long> lastToggleMillis = new java.util.HashMap<>(); }

}
