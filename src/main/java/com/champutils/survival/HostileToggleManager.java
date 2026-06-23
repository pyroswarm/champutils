package com.champutils.survival;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HostileToggleManager {
    private static final Map<UUID, Boolean> DISABLED = new ConcurrentHashMap<>();
    private static final Set<UUID> PENDING_CONFIRM = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_TOGGLE = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 24L * 60L * 60L * 1000L;
    private static final double RADIUS_SQ = 96.0D * 96.0D;
    private static int tickCounter = 0;
    private static boolean registered = false;
    private HostileToggleManager() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("togglehostile")
                        .executes(ctx -> requestToggle(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("confirm")
                                .executes(ctx -> toggle(ctx.getSource().getPlayerOrException()))))
        );
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> removeIfBlocked(entity, world));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter % 20 != 0 || DISABLED.isEmpty()) return;
            for (ServerLevel level : server.getAllLevels()) {
                for (Mob mob : level.getEntitiesOfClass(Mob.class, level.getWorldBorder().getCollisionShape().bounds())) {
                    removeIfBlocked(mob, level);
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
        boolean currentlyDisabled = Boolean.TRUE.equals(DISABLED.get(player.getUUID()));
        player.sendSystemMessage(Component.literal(currentlyDisabled
                ? "Run /togglehostile confirm to turn hostile mob spawning near you back ON. This uses your 24 hour toggle."
                : "Run /togglehostile confirm to turn hostile mob spawning near you OFF. Cobblemon spawns will still work. This uses your 24 hour toggle.").withStyle(ChatFormatting.GOLD));
        PENDING_CONFIRM.add(player.getUUID());
        return 1;
    }

    private static int toggle(ServerPlayer player) {
        if (!PENDING_CONFIRM.remove(player.getUUID())) {
            return requestToggle(player);
        }
        long now = System.currentTimeMillis();
        long last = LAST_TOGGLE.getOrDefault(player.getUUID(), 0L);
        long wait = COOLDOWN_MS - (now - last);
        if (wait > 0 && !player.hasPermissions(4)) {
            long hours = Math.max(1L, (wait + 3599999L) / 3600000L);
            player.sendSystemMessage(Component.literal("You can toggle hostile protection again in " + hours + " hour(s).").withStyle(ChatFormatting.RED));
            return 0;
        }
        boolean next = !Boolean.TRUE.equals(DISABLED.get(player.getUUID()));
        DISABLED.put(player.getUUID(), next);
        LAST_TOGGLE.put(player.getUUID(), now);
        player.sendSystemMessage(Component.literal(next
                ? "Hostile mob spawning near you is now disabled. Cobblemon spawns are not affected."
                : "Hostile mob spawning near you is now enabled again.").withStyle(next ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        return 1;
    }
}
