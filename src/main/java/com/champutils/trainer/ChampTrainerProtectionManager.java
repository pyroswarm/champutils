package com.champutils.trainer;

import com.champutils.gym.GymRegistry;
import com.champutils.worldevent.WorldEventBindingRegistry;

import com.cobblemon.mod.common.entity.npc.NPCEntity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChampTrainerProtectionManager {

    private static final Map<UUID, Anchor> ANCHORS = new HashMap<>();

    private ChampTrainerProtectionManager() {}

    public static class Anchor {
        public String trainerId;
        public ChampTrainerSpawner.TrainerKind kind;
        public double x;
        public double y;
        public double z;
        public float yaw;

        public Anchor(String trainerId, ChampTrainerSpawner.TrainerKind kind, Vec3 pos, float yaw) {
            this.trainerId = trainerId;
            this.kind = kind;
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.yaw = yaw;
        }
    }

    public static void track(NPCEntity npc, String trainerId, ChampTrainerSpawner.TrainerKind kind, Vec3 pos, float yaw) {
        if (npc == null) return;
        ANCHORS.put(npc.getUUID(), new Anchor(trainerId, kind, pos, yaw));
        protect(npc, ANCHORS.get(npc.getUUID()));
    }

    public static boolean isTracked(UUID uuid) {
        return uuid != null && ANCHORS.containsKey(uuid);
    }

    public static void untrack(UUID uuid) {
        if (uuid == null) return;
        ANCHORS.remove(uuid);
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;

        if (server.getTickCount() % 20 == 0) {
            discoverBoundNpcs(server);
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (UUID uuid : new java.util.ArrayList<>(ANCHORS.keySet())) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof NPCEntity npc) {
                    protect(npc, ANCHORS.get(uuid));
                }
            }
        }
    }

    private static void discoverBoundNpcs(MinecraftServer server) {
        for (UUID uuid : GymRegistry.getAllGyms().keySet()) {
            if (ANCHORS.containsKey(uuid)) continue;
            NPCEntity npc = findNpc(server, uuid);
            if (npc != null) {
                track(npc, "gym", ChampTrainerSpawner.TrainerKind.GYM, npc.position(), npc.getYRot());
            }
        }

        for (WorldEventBindingRegistry.Binding binding : WorldEventBindingRegistry.getAll().values()) {
            if (binding == null) continue;
            UUID uuid = binding.uuid();
            if (uuid == null || ANCHORS.containsKey(uuid)) continue;
            NPCEntity npc = findNpc(server, uuid);
            if (npc != null) {
                track(npc, binding.eventId, ChampTrainerSpawner.TrainerKind.WORLD_EVENT, npc.position(), npc.getYRot());
            }
        }
    }

    private static NPCEntity findNpc(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof NPCEntity npc) return npc;
        }
        return null;
    }

    private static void setNullableBooleanProperty(NPCEntity npc, String methodName, String fallbackMethodName, boolean value) {
        try {
            npc.getClass().getMethod(methodName, Boolean.class).invoke(npc, Boolean.valueOf(value));
            return;
        } catch (Exception ignored) {}

        if (fallbackMethodName != null) {
            try {
                npc.getClass().getMethod(fallbackMethodName, Boolean.class).invoke(npc, Boolean.valueOf(value));
            } catch (Exception ignored) {}
        }
    }

    private static void protect(NPCEntity npc, Anchor anchor) {
        if (npc == null || anchor == null) return;

        setNullableBooleanProperty(npc, "setMovable", "setIsMovable", false);
        setNullableBooleanProperty(npc, "setInvulnerable", "setIsInvulnerable", true);
        setNullableBooleanProperty(npc, "setLeashable", "setIsLeashable", false);
        setNullableBooleanProperty(npc, "setAllowProjectileHits", null, false);
        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
        try { npc.setHealth(npc.getMaxHealth()); } catch (Exception ignored) {}
        try { npc.clearFire(); } catch (Exception ignored) {}
        try { npc.setDeltaMovement(Vec3.ZERO); } catch (Exception ignored) {}
        try { npc.hurtMarked = false; } catch (Exception ignored) {}

        double dx = npc.getX() - anchor.x;
        double dy = npc.getY() - anchor.y;
        double dz = npc.getZ() - anchor.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq > 0.01D) {
            try { npc.teleportTo(anchor.x, anchor.y, anchor.z); } catch (Exception ignored) {}
        }

        try { npc.setYRot(anchor.yaw); } catch (Exception ignored) {}
        try { npc.setXRot(0.0F); } catch (Exception ignored) {}
    }
}
