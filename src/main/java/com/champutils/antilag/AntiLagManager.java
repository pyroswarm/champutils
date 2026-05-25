package com.champutils.antilag;

import com.champutils.permissions.LuckPermsHook;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

public final class AntiLagManager {
    private static int ticksUntilScan = 20;
    private static int ticksUntilCleanup = 20;
    private static final Map<UUID, ThrowWindow> snowballThrows = new HashMap<>();
    private static final Set<UUID> kickedThisSession = new HashSet<>();

    private static final String[] PROTECTED_TAG_MARKERS = {
            "champutils_mega_boss",
            "champutils_guild_boss",
            "champutils_world_boss",
            "champutils_special_spawn",
            "champutils_roaming_trainer",
            "champutils_npc",
            "boss",
            "special"
    };

    private AntiLagManager() {}

    public static void tick(MinecraftServer server) {
        if (!AntiLagConfig.DATA.enabled) return;

        ticksUntilScan--;
        if (ticksUntilScan <= 0) {
            ticksUntilScan = Math.max(20, AntiLagConfig.DATA.scanIntervalSeconds * 20);
            if (AntiLagConfig.DATA.detectLagMachines) scanForLagMachines(server);
        }

        ticksUntilCleanup--;
        if (ticksUntilCleanup <= 0) {
            ticksUntilCleanup = Math.max(20, AntiLagConfig.DATA.cleanupIntervalMinutes * 60 * 20);
            if (AntiLagConfig.DATA.entityCleanupEnabled) cleanupEntities(server, true);
        }
    }

    public static CleanupResult cleanupEntities(MinecraftServer server, boolean notifyAdmins) {
        CleanupResult result = new CleanupResult();
        int max = Math.max(1, AntiLagConfig.DATA.maxRemovalsPerScan);

        for (ServerLevel level : server.getAllLevels()) {
            if (isDisabled(level)) continue;
            for (Entity entity : level.getAllEntities()) {
                if (result.totalRemoved() >= max) break;
                if (AntiLagConfig.DATA.cleanupDroppedItems && entity instanceof ItemEntity) {
                    entity.discard();
                    result.droppedItems++;
                    continue;
                }
                if (AntiLagConfig.DATA.cleanupWildPokemon && isSafeWildPokemonToWipe(entity)) {
                    entity.discard();
                    result.wildPokemon++;
                }
            }
        }

        if (notifyAdmins && result.totalRemoved() > 0) {
            alertAdmins(server, "§7Entity cleanup removed §e" + result.droppedItems + "§7 dropped item entities and §b" + result.wildPokemon + "§7 natural wild Pokémon.");
        }
        return result;
    }

    private static void scanForLagMachines(MinecraftServer server) {
        expireSnowballWindows();
        int kicks = 0;

        for (ServerLevel level : server.getAllLevels()) {
            if (isDisabled(level)) continue;
            List<Entity> minecarts = new ArrayList<>();
            List<Entity> snowballs = new ArrayList<>();
            List<Entity> generic = new ArrayList<>();

            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof AbstractMinecart) minecarts.add(entity);
                if (entity instanceof Snowball) {
                    snowballs.add(entity);
                    trackSnowballThrow(entity);
                }
                if (!(entity instanceof ServerPlayer) && !(entity instanceof ItemEntity) && !(isPokemonEntity(entity)) && !(isNpcEntity(entity))) {
                    generic.add(entity);
                }
            }

            if (AntiLagConfig.DATA.minecartDetectionEnabled) {
                kicks += detectCluster(server, level, minecarts, AntiLagConfig.DATA.minecartClusterRadiusBlocks, AntiLagConfig.DATA.minecartClusterThreshold, "minecart cluster", kicks);
            }
            if (kicks >= AntiLagConfig.DATA.maxKicksPerScan) return;

            if (AntiLagConfig.DATA.snowballDetectionEnabled) {
                kicks += detectCluster(server, level, snowballs, AntiLagConfig.DATA.snowballClusterRadiusBlocks, AntiLagConfig.DATA.snowballClusterThreshold, "snowball cluster", kicks);
                kicks += detectSnowballThrowSpam(server, kicks);
            }
            if (kicks >= AntiLagConfig.DATA.maxKicksPerScan) return;

            if (AntiLagConfig.DATA.genericEntityClusterDetectionEnabled) {
                kicks += detectCluster(server, level, generic, AntiLagConfig.DATA.genericEntityClusterRadiusBlocks, AntiLagConfig.DATA.genericEntityClusterThreshold, "entity cluster", kicks);
            }
        }
    }

    private static int detectCluster(MinecraftServer server, ServerLevel level, List<Entity> entities, int radius, int threshold, String reason, int currentKicks) {
        if (entities.size() < threshold || currentKicks >= AntiLagConfig.DATA.maxKicksPerScan) return 0;
        double radiusSq = radius * radius;
        Set<UUID> handled = new HashSet<>();
        int kicks = 0;

        for (Entity center : entities) {
            if (handled.contains(center.getUUID())) continue;
            List<Entity> cluster = new ArrayList<>();
            for (Entity other : entities) {
                if (center.distanceToSqr(other) <= radiusSq) cluster.add(other);
            }
            if (cluster.size() < threshold) continue;
            cluster.forEach(e -> handled.add(e.getUUID()));

            ServerPlayer suspect = nearestPlayer(level, center, AntiLagConfig.DATA.minecartPlayerAttributionRadiusBlocks);
            String location = level.dimension().location() + " " + center.blockPosition().toShortString();
            alertAdmins(server, "§c[AntiLag] Possible lag machine: §e" + reason + " §7(" + cluster.size() + " entities) near §f" + location + (suspect == null ? "§7." : "§7. Suspect: §f" + suspect.getGameProfile().getName()));

            if (AntiLagConfig.DATA.removeDetectedLagMachineEntities) removeEntities(cluster);
            if (suspect != null && kickSuspect(suspect, reason + " detected near your location")) kicks++;
            if (currentKicks + kicks >= AntiLagConfig.DATA.maxKicksPerScan) break;
        }
        return kicks;
    }

    private static int detectSnowballThrowSpam(MinecraftServer server, int currentKicks) {
        int kicks = 0;
        int threshold = AntiLagConfig.DATA.snowballThrowThresholdPerWindow;
        for (Map.Entry<UUID, ThrowWindow> entry : new ArrayList<>(snowballThrows.entrySet())) {
            if (entry.getValue().count < threshold) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            alertAdmins(server, "§c[AntiLag] Possible snowball lag machine: §f" + player.getGameProfile().getName() + " §7created §e" + entry.getValue().count + "§7 snowballs in the current window.");
            if (kickSuspect(player, "snowball spam detected")) kicks++;
            snowballThrows.remove(entry.getKey());
            if (currentKicks + kicks >= AntiLagConfig.DATA.maxKicksPerScan) break;
        }
        return kicks;
    }

    private static void trackSnowballThrow(Entity entity) {
        if (!(entity instanceof Projectile projectile)) return;
        Entity owner = projectile.getOwner();
        if (!(owner instanceof ServerPlayer player)) return;
        long now = System.currentTimeMillis();
        long windowMillis = Math.max(1000L, AntiLagConfig.DATA.snowballWindowSeconds * 1000L);
        ThrowWindow window = snowballThrows.computeIfAbsent(player.getUUID(), uuid -> new ThrowWindow(now + windowMillis));
        if (now > window.expiresAt) {
            window.count = 0;
            window.expiresAt = now + windowMillis;
        }
        window.count++;
    }

    private static void expireSnowballWindows() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, ThrowWindow>> iterator = snowballThrows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now > iterator.next().getValue().expiresAt) iterator.remove();
        }
    }

    private static boolean kickSuspect(ServerPlayer player, String reason) {
        if (!AntiLagConfig.DATA.autoKickLagMachineSuspects) return false;
        if (player.hasPermissions(4)) {
            player.sendSystemMessage(Component.literal("[AntiLag] You matched a lag-machine rule, but ops are not auto-kicked: " + reason).withStyle(ChatFormatting.RED));
            return false;
        }
        if (kickedThisSession.contains(player.getUUID())) return false;
        kickedThisSession.add(player.getUUID());
        player.connection.disconnect(Component.literal(AntiLagConfig.DATA.lagMachineKickMessage));
        return true;
    }

    private static void removeEntities(List<Entity> entities) {
        int removed = 0;
        int max = Math.max(1, AntiLagConfig.DATA.maxRemovalsPerScan);
        for (Entity entity : entities) {
            if (removed >= max) break;
            if (entity instanceof ServerPlayer) continue;
            entity.discard();
            removed++;
        }
    }

    private static ServerPlayer nearestPlayer(ServerLevel level, Entity entity, int radius) {
        double radiusSq = radius * radius;
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) continue;
            double distance = player.distanceToSqr(entity);
            if (distance > radiusSq || distance >= bestDistance) continue;
            best = player;
            bestDistance = distance;
        }
        return best;
    }

    private static boolean isSafeWildPokemonToWipe(Entity entity) {
        if (!isPokemonEntity(entity)) return false;
        if (hasProtectedTag(entity)) return false;
        if (AntiLagConfig.DATA.protectPokemonWithCustomName && entity.hasCustomName()) return false;
        if (AntiLagConfig.DATA.protectPokemonWithPersistenceRequired && entity instanceof Mob mob && mob.isPersistenceRequired()) return false;
        if (AntiLagConfig.DATA.protectPokemonInBattle && booleanValue(entity, "isBattling", "isInBattle", "getBattleId", "getBattleIds")) return false;

        Object pokemon = firstValue(entity, "pokemon", "getPokemon");
        if (pokemon != null) {
            if (booleanValue(pokemon, "getShiny", "isShiny") || booleanField(pokemon, "shiny")) return false;
            if (AntiLagConfig.DATA.protectPokemonWithOwnerOrStorage && hasOwnerOrStorage(pokemon)) return false;
            String aspects = String.valueOf(firstValue(pokemon, "aspects", "getAspects")).toLowerCase(Locale.ROOT);
            if (aspects.contains("shiny") || aspects.contains("boss") || aspects.contains("special")) return false;
        }

        return true;
    }

    private static boolean hasOwnerOrStorage(Object pokemon) {
        Object owner = firstValue(pokemon, "ownerUUID", "getOwnerUUID", "owner", "getOwner", "originalTrainer", "getOriginalTrainer", "storeCoordinates", "getStoreCoordinates", "storeCoordinate", "getStoreCoordinate");
        if (owner == null) return false;
        if (owner instanceof Optional<?> optional) return optional.isPresent();
        String value = owner.toString();
        return !value.equalsIgnoreCase("null") && !value.equalsIgnoreCase("Optional.empty") && !value.isBlank();
    }

    private static boolean hasProtectedTag(Entity entity) {
        for (String tag : entity.getTags()) {
            String lower = tag.toLowerCase(Locale.ROOT);
            for (String marker : PROTECTED_TAG_MARKERS) {
                if (lower.contains(marker)) return true;
            }
        }
        return false;
    }

    private static boolean isPokemonEntity(Entity entity) {
        return entity != null && entity.getClass().getName().equals("com.cobblemon.mod.common.entity.pokemon.PokemonEntity");
    }

    private static boolean isNpcEntity(Entity entity) {
        return entity != null && entity.getClass().getName().equals("com.cobblemon.mod.common.entity.npc.NPCEntity");
    }

    private static boolean booleanValue(Object source, String... names) {
        for (String name : names) {
            Object value = firstValue(source, name);
            if (value instanceof Boolean b) return b;
            if (value instanceof Set<?> s && !s.isEmpty()) return true;
            if (value instanceof Iterable<?> iterable) return iterable.iterator().hasNext();
            if (value instanceof UUID) return true;
            if (value instanceof Optional<?> optional) return optional.isPresent();
        }
        return false;
    }

    private static boolean booleanField(Object source, String fieldName) {
        try {
            Field field = findField(source.getClass(), fieldName);
            if (field == null) return false;
            field.setAccessible(true);
            Object value = field.get(source);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object firstValue(Object source, String... names) {
        if (source == null) return null;
        for (String name : names) {
            try {
                if (name.startsWith("get") || name.startsWith("is")) {
                    Method method = source.getClass().getMethod(name);
                    method.setAccessible(true);
                    if (method.getParameterCount() == 0) {
                        Object value = method.invoke(source);
                        if (value != null) return value;
                    }
                } else {
                    Field field = findField(source.getClass(), name);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(source);
                        if (value != null) return value;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (Throwable ignored) { current = current.getSuperclass(); }
        }
        return null;
    }

    private static boolean isDisabled(ServerLevel level) {
        ResourceLocation id = level.dimension().location();
        return AntiLagConfig.DATA.disabledDimensions != null && AntiLagConfig.DATA.disabledDimensions.contains(id.toString());
    }

    private static void alertAdmins(MinecraftServer server, String message) {
        if (!AntiLagConfig.DATA.alertAdmins) return;
        Component component = Component.literal(message);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(4) || LuckPermsHook.hasPermission(player, AntiLagConfig.DATA.adminAlertPermission)) {
                player.sendSystemMessage(component);
            }
        }
        System.out.println(message.replace('§', '&'));
    }

    private static final class ThrowWindow {
        int count = 0;
        long expiresAt;
        ThrowWindow(long expiresAt) { this.expiresAt = expiresAt; }
    }

    public static final class CleanupResult {
        public int droppedItems;
        public int wildPokemon;
        public int totalRemoved() { return droppedItems + wildPokemon; }
    }
}
