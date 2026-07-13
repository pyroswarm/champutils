package com.champutils.antilag;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Protects the server from entity NBT chunks growing until Minecraft starts writing
 * c.x.z.mcc external entity files. Those saves are expensive and show up as constant
 * IO-worker spam/lag. The guard only removes ChampUtils-owned duplicate display entities
 * and reports suspicious chunks; it does not touch normal mobs, NPCs, Pokémon, or items.
 */
public final class OversizedChunkEntityGuard {
    private static final int SCAN_INTERVAL_TICKS = 20 * 30;
    private static final int ENTITY_COUNT_WARN_THRESHOLD = 250;
    private static final int MAX_CHUNKS_TO_LOG_PER_SCAN = 6;

    private static final String CHEST_SHOP_DISPLAY_TAG = "champutils_chestshop_display";
    private static final String FLOATING_TEXT_TAG = "champutils_floating_text";
    private static final String TEXT_ID_PREFIX = "champutils_text_id_";
    private static final String TEXT_LINE_PREFIX = "champutils_text_line_";

    private OversizedChunkEntityGuard() {}

    public static void tick(MinecraftServer server) {
        if (server == null || server.getTickCount() % SCAN_INTERVAL_TICKS != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            try {
                scanLevel(level);
            } catch (Throwable t) {
                System.err.println("[ChampUtils] OversizedChunkEntityGuard skipped a scan after an unexpected error instead of crashing the server: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    private static void scanLevel(ServerLevel level) {
        if (level == null) return;

        String world = level.dimension().location().toString();
        if (!world.contains("territor") && !world.contains("spawn") && !world.contains("world")) return;

        Map<Long, Integer> counts = new HashMap<>();
        Set<String> seenDisplayKeys = new HashSet<>();
        int removed = 0;
        for (Entity entity : level.getAllEntities()) {
            // Some modded entity collections can briefly expose a null entry while an
            // entity is being removed/unloaded. Never let the anti-lag guard crash
            // the entire server tick because of that transient state.
            if (entity == null) continue;

            ChunkPos pos = entity.chunkPosition();
            counts.merge(pos.toLong(), 1, Integer::sum);

            String displayKey = displayDedupeKey(entity);
            if (displayKey != null && !seenDisplayKeys.add(displayKey)) {
                entity.discard();
                removed++;
            }
        }

        if (removed > 0) {
            System.out.println("[ChampUtils] OversizedChunkEntityGuard removed " + removed + " duplicate ChampUtils display entities in " + world + ".");
        }

        int logged = 0;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < ENTITY_COUNT_WARN_THRESHOLD) continue;
            ChunkPos pos = new ChunkPos(entry.getKey());
            System.err.println("[ChampUtils] Entity-heavy chunk detected in " + world + " chunk [" + pos.x + ", " + pos.z + "] with " + entry.getValue() + " entities. If vanilla logs c." + pos.x + "." + pos.z + ".mcc, inspect/remove duplicate displays or stray entities there.");
            if (++logged >= MAX_CHUNKS_TO_LOG_PER_SCAN) break;
        }
    }

    private static boolean isChampUtilsDisplay(Entity entity) {
        return entity != null && (entity.getTags().contains(CHEST_SHOP_DISPLAY_TAG) || entity.getTags().contains(FLOATING_TEXT_TAG));
    }

    private static String displayDedupeKey(Entity entity) {
        if (entity instanceof TextDisplay && entity.getTags().contains(CHEST_SHOP_DISPLAY_TAG)) {
            return entity.level().dimension().location() + ":chestshop@" + entity.blockPosition().asLong();
        }
        if (entity instanceof ArmorStand && entity.getTags().contains(FLOATING_TEXT_TAG)) {
            String textId = textId(entity);
            String lineId = textLineId(entity);
            if (textId == null || lineId == null) return null;
            return entity.level().dimension().location()
                    + ":text@" + textId
                    + "@line@" + lineId
                    + "@" + entity.blockPosition().asLong();
        }
        return null;
    }

    private static String textId(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(TEXT_ID_PREFIX)) return tag.substring(TEXT_ID_PREFIX.length());
        }
        return null;
    }

    private static String textLineId(Entity entity) {
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(TEXT_LINE_PREFIX)) return tag.substring(TEXT_LINE_PREFIX.length());
        }
        return null;
    }
}
