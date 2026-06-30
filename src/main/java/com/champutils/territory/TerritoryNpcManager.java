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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TerritoryNpcManager {
    public static final String TAG_PREFIX = "champutils_territory_manager_";
    public static final String PERSONAL_TAG = "champutils_personal_territory_manager";
    public static final String GUILD_TAG = "champutils_guild_territory_manager";
    public static final String STEWARD_TAG = "champutils_territory_steward";
    private static final String CONFIGURED_TAG_PREFIX = "champutils_territory_manager_configured_";
    private static final String SKIN_APPLIED_TAG = "champutils_territory_manager_skin_pivilee";

    private static final Set<UUID> SPAWNED_THIS_RUNTIME = new HashSet<>();
    private static final Map<UUID, Long> LAST_WIDE_SCAN_TICK = new HashMap<>();
    private static int repairCursor = 0;
    private static final int REPAIR_INTERVAL_TICKS = 20 * 10;
    private static final int MAX_REPAIRS_PER_PASS = 1;
    private static final long WIDE_SCAN_INTERVAL_TICKS = 20L * 60L * 5L;

    private TerritoryNpcManager() {}

    /**
     * Periodically repairs steward links, but only when the steward chunk is loaded.
     * This keeps old/broken steward NPCs from becoming unusable while avoiding the old duplicate-on-restart bug.
     */
    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % REPAIR_INTERVAL_TICKS != 0) return;
        List<TerritoryRepository.Territory> territories = new ArrayList<>(TerritoryRepository.allCached());
        if (territories.isEmpty()) return;
        int processed = 0;
        int checked = 0;
        while (processed < MAX_REPAIRS_PER_PASS && checked < territories.size()) {
            if (repairCursor >= territories.size()) repairCursor = 0;
            TerritoryRepository.Territory territory = territories.get(repairCursor++);
            checked++;
            if (territory == null || territory.id == null || !territory.isReady() || TerritoryRepository.isDeleting(territory)) continue;
            // Only one lightweight repair is attempted per pass. Older versions reconfigured
            // several NPCs every second, which repeatedly requested player skins and caused
            // 250ms+ server-thread spikes on larger territory lists.
            moveOrCreateLoadedSteward(server, territory, false);
            processed++;
        }
    }

    public static void spawnOnceWhenReady(MinecraftServer server, TerritoryRepository.Territory territory) {
        if (server == null || territory == null || territory.id == null || !territory.isReady() || TerritoryRepository.isDeleting(territory)) return;
        moveOrCreateLoadedSteward(server, territory, true);
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
            moveOrCreateLoadedSteward(player.server, territory, true);
            if (callback != null) callback.done(true, "Territory steward moved here.");
        }));
    }

    private static void moveOrCreateLoadedSteward(MinecraftServer server, TerritoryRepository.Territory territory, boolean forceWideScan) {
        if (server == null || territory == null || territory.id == null) return;
        ServerLevel level = level(server, territory.worldName);
        if (level == null) return;
        Vec3 pos = npcPosition(territory);
        if (!level.hasChunkAt(BlockPos.containing(pos))) return;
        String uniqueTag = TAG_PREFIX + territory.id;
        AABB localSearch = new AABB(pos.x - 8.0D, pos.y - 8.0D, pos.z - 8.0D, pos.x + 8.0D, pos.y + 8.0D, pos.z + 8.0D);
        NPCEntity kept = keepOneAndRemoveDuplicates(level.getEntities((Entity) null, localSearch, e -> isStewardCandidate(e, territory)), territory);
        if (kept != null) {
            configureNpc(kept, pos, territory);
            markStewardSpawned(territory);
            return;
        }

        long now = server.getTickCount();
        long lastWideScan = LAST_WIDE_SCAN_TICK.getOrDefault(territory.id, Long.MIN_VALUE);
        // Periodic repair should never scan an entire territory when SQL already says the steward exists.
        // Full scans are reserved for explicit repair or unspawned territories to keep this manager cheap with hundreds of territories.
        boolean allowWideScan = forceWideScan || (!territory.stewardNpcSpawned && (lastWideScan == Long.MIN_VALUE || now - lastWideScan >= WIDE_SCAN_INTERVAL_TICKS));
        if (!allowWideScan) return;
        LAST_WIDE_SCAN_TICK.put(territory.id, now);

        AABB search = new AABB(territory.minX, level.getMinBuildHeight(), territory.minZ, territory.maxX, level.getMaxBuildHeight(), territory.maxZ);
        kept = keepOneAndRemoveDuplicates(level.getEntities((Entity) null, search, e -> isStewardCandidate(e, territory)), territory);
        if (kept != null) {
            configureNpc(kept, pos, territory);
            markStewardSpawned(territory);
            return;
        }
        SPAWNED_THIS_RUNTIME.add(territory.id);
        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, territory.stewardNpcYaw == null ? 180.0F : territory.stewardNpcYaw,
                territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward", "Pivilee");
        if (npc == null) return;
        repairTags(npc, territory);
        configureNpc(npc, pos, territory);
        markStewardSpawned(territory);
    }

    private static NPCEntity keepOneAndRemoveDuplicates(List<Entity> existing, TerritoryRepository.Territory territory) {
        NPCEntity kept = null;
        for (Entity entity : existing) {
            if (kept == null && entity instanceof NPCEntity npc) {
                kept = npc;
                repairTags(npc, territory);
            } else {
                entity.discard();
            }
        }
        return kept;
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
            moveOrCreateLoadedSteward(server, territory, true);
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
            repairTags(entity, territory);
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
        if (npc == null || territory == null || territory.id == null) return;
        String displayName = territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward";
        float yaw = territory.stewardNpcYaw == null ? 180.0F : territory.stewardNpcYaw;
        float pitch = territory.stewardNpcPitch == null ? 0.0F : territory.stewardNpcPitch;

        // Fast path: the steward is already repaired/configured. Do not re-apply skins,
        // protections, or metadata every repair tick. Re-requesting a player skin here was
        // the expensive part of TerritoryNpcManager and caused large main-thread stalls.
        boolean alreadyConfigured = npc.getTags().contains(CONFIGURED_TAG_PREFIX + territory.id);
        boolean wrongName = npc.getCustomName() == null || !displayName.equals(npc.getCustomName().getString());
        boolean wrongPosition = npc.position().distanceToSqr(pos) > 0.25D;
        if (alreadyConfigured && !wrongName && !wrongPosition) {
            repairTags(npc, territory);
            return;
        }

        if (wrongPosition || Math.abs(npc.getYRot() - yaw) > 0.5F || Math.abs(npc.getXRot() - pitch) > 0.5F) {
            npc.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
        }

        if (wrongName) {
            npc.setCustomName(Component.literal(displayName));
            npc.setCustomNameVisible(true);
        }

        if (!npc.getTags().contains(SKIN_APPLIED_TAG)) {
            ChampTrainerSpawner.applyTrainerSkin(npc, "Pivilee");
            npc.addTag(SKIN_APPLIED_TAG);
        }

        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setInvulnerable(Boolean.TRUE); } catch (Exception ignored) {}
        try { npc.setMovable(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(Boolean.FALSE); } catch (Exception ignored) {}
        repairTags(npc, territory);
        npc.addTag(CONFIGURED_TAG_PREFIX + territory.id);
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
        try { npc.setDeltaMovement(Vec3.ZERO); } catch (Exception ignored) {}
    }

    private static boolean isStewardCandidate(Entity entity, TerritoryRepository.Territory territory) {
        if (!(entity instanceof NPCEntity)) return false;
        String uniqueTag = TAG_PREFIX + territory.id;
        if (entity.getTags().contains(uniqueTag)) return true;
        if (entity.getTags().contains(STEWARD_TAG) && entity.position().distanceToSqr(npcPosition(territory)) <= 100.0D) return true;
        String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString();
        String expected = territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward";
        return expected.equalsIgnoreCase(name);
    }

    private static void repairTags(Entity entity, TerritoryRepository.Territory territory) {
        if (entity == null || territory == null || territory.id == null) return;
        entity.addTag(TAG_PREFIX + territory.id);
        entity.addTag(STEWARD_TAG);
        if (territory.ownerType == TerritoryRepository.OwnerType.GUILD) {
            entity.removeTag(PERSONAL_TAG);
            entity.addTag(GUILD_TAG);
        } else {
            entity.removeTag(GUILD_TAG);
            entity.addTag(PERSONAL_TAG);
        }
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
