package com.champutils.trainer;

import com.champutils.gym.GymRegistry;
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
        public long lastProtectMillis;

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

        if (server.getTickCount() % 100 == 0) {
            discoverBoundNpcs(server);
        }

        long now = System.currentTimeMillis();
        for (UUID uuid : new java.util.ArrayList<>(ANCHORS.keySet())) {
            Anchor anchor = ANCHORS.get(uuid);
            if (anchor == null || now - anchor.lastProtectMillis < 5_000L) continue;
            NPCEntity npc = findNpc(server, uuid);
            if (npc != null) {
                protect(npc, anchor);
                anchor.lastProtectMillis = now;
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
    }

    private static NPCEntity findNpc(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof NPCEntity npc) return npc;
        }
        return null;
    }

    private static void protect(NPCEntity npc, Anchor anchor) {
        if (npc == null || anchor == null) return;

        try { npc.setMovable(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setInvulnerable(Boolean.TRUE); } catch (Exception ignored) {}
        try { npc.setLeashable(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(Boolean.FALSE); } catch (Exception ignored) {}
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
