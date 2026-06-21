package com.champutils.territory;

import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TerritoryNpcManager {
    public static final String TAG_PREFIX = "champutils_territory_manager_";
    public static final String PERSONAL_TAG = "champutils_personal_territory_manager";
    public static final String GUILD_TAG = "champutils_guild_territory_manager";

    private static final Set<UUID> SPAWNED_THIS_RUNTIME = new HashSet<>();

    private TerritoryNpcManager() {}

    /**
     * Intentionally does not spawn/repair NPCs on a timer.
     *
     * Territory steward NPCs are persistent world entities. Creating them from a periodic server tick causes
     * duplicates after restart if the existing NPC is in an unloaded chunk or is otherwise not returned by
     * the nearby entity search yet. NPCs should only be created when the territory creation pipeline confirms
     * the territory has become READY.
     */
    public static void tick(MinecraftServer server) {
        // No-op by design. Keep the hook so older initializers do not need to change.
    }

    public static void spawnOnceWhenReady(MinecraftServer server, TerritoryRepository.Territory territory) {
        if (server == null || territory == null || territory.id == null || !territory.isReady() || TerritoryRepository.isDeleting(territory)) return;
        if (territory.stewardNpcSpawned) return;
        ServerLevel level = level(server, territory.worldName);
        if (level == null) return;
        String uniqueTag = TAG_PREFIX + territory.id;
        Vec3 pos = npcPosition(territory);
        AABB search = new AABB(territory.minX, level.getMinBuildHeight(), territory.minZ, territory.maxX, level.getMaxBuildHeight(), territory.maxZ);
        for (Entity entity : level.getEntities((Entity) null, search, e -> e.getTags().contains(uniqueTag))) {
            if (entity instanceof NPCEntity npc) {
                configureNpc(npc, pos, territory);
                markStewardSpawned(territory);
                return;
            }
            entity.discard();
        }
        if (!SPAWNED_THIS_RUNTIME.add(territory.id)) return;

        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, 180.0F,
                territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward", "Pivilee");
        if (npc == null) return;
        npc.addTag(uniqueTag);
        npc.addTag(territory.ownerType == TerritoryRepository.OwnerType.GUILD ? GUILD_TAG : PERSONAL_TAG);
        configureNpc(npc, pos, territory);
        markStewardSpawned(territory);
    }


    public static void moveStewardHere(ServerPlayer player, TerritoryRepository.Territory territory, Vec3 pos, float yaw, float pitch, TerritoryRepository.Callback callback) {
        if (player == null || territory == null || territory.id == null || pos == null) {
            if (callback != null) callback.done(false, "Invalid territory steward move.");
            return;
        }
        if (!territory.contains(com.champutils.network.NetworkServerConfig.serverId(), player.serverLevel().dimension().location().toString(), BlockPos.containing(pos))) {
            if (callback != null) callback.done(false, "You must stand inside your territory to move the steward.");
            return;
        }

        territory.worldName = player.serverLevel().dimension().location().toString();
        territory.stewardNpcX = pos.x;
        territory.stewardNpcY = pos.y;
        territory.stewardNpcZ = pos.z;
        territory.stewardNpcYaw = yaw;
        territory.stewardNpcPitch = pitch;
        territory.stewardNpcSpawned = true;

        TerritoryRepository.save(territory, (success, message) -> player.server.execute(() -> {
            if (!success) {
                if (callback != null) callback.done(false, "Could not save steward location: " + message);
                return;
            }
            moveOrCreateLoadedSteward(player.server, territory);
            if (callback != null) callback.done(true, "Territory steward moved here.");
        }));
    }

    private static void moveOrCreateLoadedSteward(MinecraftServer server, TerritoryRepository.Territory territory) {
        if (server == null || territory == null || territory.id == null) return;
        ServerLevel level = level(server, territory.worldName);
        if (level == null) return;
        String uniqueTag = TAG_PREFIX + territory.id;
        Vec3 pos = npcPosition(territory);
        AABB search = new AABB(territory.minX, level.getMinBuildHeight(), territory.minZ, territory.maxX, level.getMaxBuildHeight(), territory.maxZ);
        List<Entity> existing = level.getEntities((Entity) null, search, e -> e.getTags().contains(uniqueTag));
        NPCEntity kept = null;
        for (Entity entity : existing) {
            if (kept == null && entity instanceof NPCEntity npc) {
                kept = npc;
            } else {
                entity.discard();
            }
        }
        if (kept != null) {
            configureNpc(kept, pos, territory);
            return;
        }
        SPAWNED_THIS_RUNTIME.remove(territory.id);
        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, territory.stewardNpcYaw == null ? 180.0F : territory.stewardNpcYaw,
                territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward", "Pivilee");
        if (npc == null) return;
        npc.addTag(uniqueTag);
        npc.addTag(territory.ownerType == TerritoryRepository.OwnerType.GUILD ? GUILD_TAG : PERSONAL_TAG);
        configureNpc(npc, pos, territory);
    }

    public static int rebuildAllStewards(MinecraftServer server) {
        if (server == null) return 0;
        int queued = 0;
        SPAWNED_THIS_RUNTIME.clear();
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            if (territory == null || territory.id == null || !territory.isReady() || TerritoryRepository.isDeleting(territory)) continue;
            ServerLevel level = level(server, territory.worldName);
            if (level == null) continue;
            Vec3 pos = npcPosition(territory);
            String uniqueTag = TAG_PREFIX + territory.id;
            String displayName = territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward";
            AABB search = new AABB(territory.minX, level.getMinBuildHeight(), territory.minZ, territory.maxX, level.getMaxBuildHeight(), territory.maxZ);
            for (Entity entity : level.getEntities((Entity) null, search, e ->
                    e instanceof NPCEntity && (e.getTags().contains(uniqueTag) || e.getTags().contains(PERSONAL_TAG) || e.getTags().contains(GUILD_TAG) ||
                            (e.getCustomName() != null && ("Territory Steward".equalsIgnoreCase(e.getCustomName().getString()) || "Guild Steward".equalsIgnoreCase(e.getCustomName().getString())))))) {
                entity.discard();
            }
            territory.stewardNpcSpawned = false;
            spawnOnceWhenReady(server, territory);
            queued++;
        }
        return queued;
    }

    private static void markStewardSpawned(TerritoryRepository.Territory territory) {
        if (territory == null || territory.stewardNpcSpawned) return;
        territory.stewardNpcSpawned = true;
        TerritoryRepository.save(territory, (success, message) -> {});
    }

    public static TerritoryRepository.Territory territoryFor(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(TAG_PREFIX)) {
                try {
                    TerritoryRepository.Territory territory = TerritoryRepository.get(UUID.fromString(tag.substring(TAG_PREFIX.length())));
                    if (territory != null && entity instanceof NPCEntity npc) reconcileLoadedTaggedNpc(npc, territory);
                    return territory;
                }
                catch (Exception ignored) { return null; }
            }
        }

        // Self-heal older/broken stewards that survived as normal NPCs but lost their tags.
        String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
        if (!("Territory Steward".equalsIgnoreCase(name) || "Guild Steward".equalsIgnoreCase(name))) return null;
        String worldName = entity.level().dimension().location().toString();
        BlockPos pos = entity.blockPosition();
        for (TerritoryRepository.Territory territory : TerritoryRepository.cachedInWorld(com.champutils.network.NetworkServerConfig.serverId(), worldName)) {
            if (territory == null || territory.id == null || !territory.isReady()) continue;
            if (pos.getX() < territory.minX || pos.getX() > territory.maxX || pos.getZ() < territory.minZ || pos.getZ() > territory.maxZ) continue;
            entity.addTag(TAG_PREFIX + territory.id);
            entity.addTag(territory.ownerType == TerritoryRepository.OwnerType.GUILD ? GUILD_TAG : PERSONAL_TAG);
            if (entity instanceof NPCEntity npc) configureNpc(npc, npcPosition(territory), territory);
            territory.stewardNpcSpawned = true;
            TerritoryRepository.save(territory, (success, message) -> {});
            return territory;
        }
        return null;
    }


    private static void reconcileLoadedTaggedNpc(NPCEntity npc, TerritoryRepository.Territory territory) {
        if (npc == null || territory == null || territory.id == null) return;
        Vec3 desired = npcPosition(territory);
        if (npc.position().distanceToSqr(desired) <= 4.0D) {
            configureNpc(npc, desired, territory);
            return;
        }
        ServerLevel level = npc.level() instanceof ServerLevel serverLevel ? serverLevel : null;
        if (level == null) return;
        String uniqueTag = TAG_PREFIX + territory.id;
        AABB desiredSearch = new AABB(desired.x - 2.0D, desired.y - 4.0D, desired.z - 2.0D, desired.x + 2.0D, desired.y + 4.0D, desired.z + 2.0D);
        List<Entity> atDesiredLocation = level.getEntities((Entity) null, desiredSearch, e -> e != npc && e instanceof NPCEntity && e.getTags().contains(uniqueTag));
        if (!atDesiredLocation.isEmpty()) {
            npc.discard();
            return;
        }
        configureNpc(npc, desired, territory);
    }

    public static boolean canUseNpc(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        if (territory.ownerType == TerritoryRepository.OwnerType.PLAYER) return TerritoryRepository.canManage(player, territory);
        return TerritoryRepository.isOwnerOrGuildMember(player, territory);
    }

    private static void configureNpc(NPCEntity npc, Vec3 pos, TerritoryRepository.Territory territory) {
        if (npc == null || territory == null) return;
        String displayName = territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward";
        float yaw = territory.stewardNpcYaw == null ? 180.0F : territory.stewardNpcYaw;
        float pitch = territory.stewardNpcPitch == null ? 0.0F : territory.stewardNpcPitch;
        npc.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setCustomName(Component.literal(displayName));
        npc.setCustomNameVisible(true);
        ChampTrainerSpawner.applyTrainerSkin(npc, "Pivilee");
        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setInvulnerable(Boolean.TRUE); } catch (Exception ignored) {}
        try { npc.setMovable(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
        try { npc.setDeltaMovement(Vec3.ZERO); } catch (Exception ignored) {}
    }

    private static Vec3 npcPosition(TerritoryRepository.Territory territory) {
        if (territory.stewardNpcX != null && territory.stewardNpcY != null && territory.stewardNpcZ != null) {
            return new Vec3(territory.stewardNpcX, territory.stewardNpcY, territory.stewardNpcZ);
        }
        double x = territory.centerX + 0.5D;
        double y = territory.spawnY - 1.0D;
        double z = territory.centerZ + 4.5D;
        return new Vec3(x, y, z);
    }

    private static ServerLevel level(MinecraftServer server, String worldName) {
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        } catch (Exception ignored) { return null; }
    }
}
