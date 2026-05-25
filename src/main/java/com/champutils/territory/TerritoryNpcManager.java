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

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % 200 != 0) return;
        for (TerritoryRepository.Territory territory : TerritoryRepository.allCached()) {
            ensureNpc(server, territory);
        }
    }

    public static void ensureNpc(MinecraftServer server, TerritoryRepository.Territory territory) {
        if (server == null || territory == null || territory.id == null || !territory.isReady() || TerritoryRepository.isDeleting(territory)) return;
        ServerLevel level = level(server, territory.worldName);
        if (level == null) return;
        String uniqueTag = TAG_PREFIX + territory.id;
        Vec3 pos = npcPosition(territory);
        AABB search = new AABB(pos.x - 32, pos.y - 16, pos.z - 32, pos.x + 32, pos.y + 16, pos.z + 32);
        for (Entity entity : level.getEntities((Entity) null, search, e -> e.getTags().contains(uniqueTag))) {
            if (entity instanceof NPCEntity) return;
            entity.discard();
        }
        if (!SPAWNED_THIS_RUNTIME.add(territory.id)) return;

        NPCEntity npc = ChampTrainerSpawner.createProtectedNpc(level, pos, 180.0F,
                territory.ownerType == TerritoryRepository.OwnerType.GUILD ? "Guild Steward" : "Territory Steward", "");
        if (npc == null) return;
        npc.addTag(uniqueTag);
        npc.addTag(territory.ownerType == TerritoryRepository.OwnerType.GUILD ? GUILD_TAG : PERSONAL_TAG);
        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setInvulnerable(Boolean.TRUE); } catch (Exception ignored) {}
        try { npc.setMovable(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(Boolean.FALSE); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
    }

    public static TerritoryRepository.Territory territoryFor(Entity entity) {
        if (entity == null) return null;
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(TAG_PREFIX)) {
                try { return TerritoryRepository.get(UUID.fromString(tag.substring(TAG_PREFIX.length()))); }
                catch (Exception ignored) { return null; }
            }
        }
        return null;
    }

    public static boolean canUseNpc(ServerPlayer player, TerritoryRepository.Territory territory) {
        if (player == null || territory == null) return false;
        if (territory.ownerType == TerritoryRepository.OwnerType.PLAYER) return TerritoryRepository.canManage(player, territory);
        return TerritoryRepository.isOwnerOrGuildMember(player, territory);
    }

    private static Vec3 npcPosition(TerritoryRepository.Territory territory) {
        double x = territory.spawnX + 3.5D;
        double y = territory.spawnY;
        double z = territory.spawnZ + 2.5D;
        return new Vec3(x, y, z);
    }

    private static ServerLevel level(MinecraftServer server, String worldName) {
        try {
            ResourceLocation id = ResourceLocation.parse(worldName);
            return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, id));
        } catch (Exception ignored) { return null; }
    }
}
