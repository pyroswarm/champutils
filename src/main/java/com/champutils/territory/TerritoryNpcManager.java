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
        AABB search = new AABB(territory.minX, pos.y - 64, territory.minZ, territory.maxX, pos.y + 64, territory.maxZ);
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
            AABB search = new AABB(territory.minX, pos.y - 64, territory.minZ, territory.maxX, pos.y + 64, territory.maxZ);
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
                try { return TerritoryRepository.get(UUID.fromString(tag.substring(TAG_PREFIX.length()))); }
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

    public static boolean canUseNpc(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        if (territory.ownerType == TerritoryRepository.OwnerType.PLAYER) return TerritoryRepository.canManage(player, territory);
        return TerritoryRepository.isOwnerOrGuildMember(player, territory);
    }

    private static void configureNpc(NPCEntity npc, Vec3 pos, TerritoryRepository.Territory territory) {
        if (npc == null || territory == null) return;
        String displayName = territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward";
        npc.moveTo(pos.x, pos.y, pos.z, 180.0F, 0.0F);
        npc.setYHeadRot(180.0F);
        npc.setYBodyRot(180.0F);
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
