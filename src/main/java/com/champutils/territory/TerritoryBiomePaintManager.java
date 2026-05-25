package com.champutils.territory;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Paints biome data into VOID-generated territory slots.
 *
 * This gives skyblock territories real biome identity without generating normal terrain. It edits the chunk biome
 * containers only; it does not place terrain, trees, caves, ores, water, structures, or decorations.
 */
public final class TerritoryBiomePaintManager {
    private static final Map<UUID, PaintTask> TASKS = new LinkedHashMap<>();

    private TerritoryBiomePaintManager() {}

    public static boolean requestBiomePaint(ServerLevel level, TerritoryRepository.Territory territory) {
        if (level == null || territory == null || territory.id == null) return true;
        if (!TerritoryConfig.get().paintVoidTerritoryBiomes) return true;

        String desired = TerritoryRepository.cleanBiomePreference(territory.biomePreference);
        if (desired == null || desired.isBlank()) desired = TerritoryConfig.get().defaultVoidTerritoryBiome;
        desired = TerritoryRepository.cleanBiomePreference(desired);
        if (desired == null || desired.isBlank()) desired = "plains";

        Holder<Biome> biome = resolveBiome(level, desired);
        if (biome == null) {
            System.err.println("[ChampUtils] Could not resolve territory biome '" + desired + "'. Using minecraft:plains.");
            biome = resolveBiome(level, "plains");
            if (biome == null) return true;
            desired = "plains";
        }

        PaintTask existing = TASKS.get(territory.id);
        if (existing != null) return existing.done;

        PaintTask task = new PaintTask(copy(territory), biome, desired, level.dimension().location().toString());
        TASKS.put(territory.id, task);
        System.out.println("[ChampUtils] Painting territory " + territory.id + " biome to minecraft:" + desired + " in " + territory.worldName + " bounds. Terrain stays VOID.");
        return false;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || TASKS.isEmpty()) return;
        Iterator<Map.Entry<UUID, PaintTask>> iterator = TASKS.entrySet().iterator();
        if (!iterator.hasNext()) return;

        Map.Entry<UUID, PaintTask> entry = iterator.next();
        PaintTask task = entry.getValue();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(task.dimensionId)));
        if (level == null) return;

        int budget = Math.max(1, TerritoryConfig.get().biomePaintChunksPerTick);
        while (budget-- > 0 && !task.done) {
            task.paintNextChunk(level);
        }

        if (task.done) {
            TerritoryRepository.Territory live = TerritoryRepository.get(task.territory.id);
            if (live != null) live.biomePreference = task.biomePreference;
            iterator.remove();
            System.out.println("[ChampUtils] Finished painting territory " + task.territory.id + " biome to minecraft:" + task.biomePreference + ".");
        }
    }

    private static Holder<Biome> resolveBiome(ServerLevel level, String biomeName) {
        try {
            ResourceLocation id = biomeName.contains(":") ? ResourceLocation.parse(biomeName) : ResourceLocation.fromNamespaceAndPath("minecraft", biomeName);
            ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, id);
            return level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(key).orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TerritoryRepository.Territory copy(TerritoryRepository.Territory source) {
        TerritoryRepository.Territory copy = new TerritoryRepository.Territory();
        copy.id = source.id;
        copy.worldName = source.worldName;
        copy.centerX = source.centerX;
        copy.centerZ = source.centerZ;
        copy.radius = source.radius;
        copy.minX = source.minX;
        copy.maxX = source.maxX;
        copy.minZ = source.minZ;
        copy.maxZ = source.maxZ;
        copy.biomePreference = source.biomePreference;
        return copy;
    }

    private static void setBiomeReflective(LevelChunkSection section, int x, int y, int z, Holder<Biome> biome) {
        try {
            Object container = null;
            Class<?> clazz = section.getClass();
            while (clazz != null && container == null) {
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    if (field.getType().getName().contains("PalettedContainer")) {
                        field.setAccessible(true);
                        Object value = field.get(section);
                        if (value != null && value.getClass().getName().contains("PalettedContainer")) {
                            container = value;
                            break;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }

            if (container == null) {
                throw new IllegalStateException("Could not find biome PalettedContainer on LevelChunkSection");
            }

            for (String methodName : new String[] { "set", "getAndSetUnchecked", "getAndSet" }) {
                for (java.lang.reflect.Method method : container.getClass().getMethods()) {
                    if (!method.getName().equals(methodName)) continue;
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length != 4) continue;
                    if (types[0] != int.class || types[1] != int.class || types[2] != int.class) continue;
                    method.setAccessible(true);
                    method.invoke(container, x, y, z, biome);
                    return;
                }
            }

            for (String methodName : new String[] { "set", "getAndSetUnchecked", "getAndSet" }) {
                for (java.lang.reflect.Method method : container.getClass().getDeclaredMethods()) {
                    if (!method.getName().equals(methodName)) continue;
                    Class<?>[] types = method.getParameterTypes();
                    if (types.length != 4) continue;
                    if (types[0] != int.class || types[1] != int.class || types[2] != int.class) continue;
                    method.setAccessible(true);
                    method.invoke(container, x, y, z, biome);
                    return;
                }
            }

            throw new IllegalStateException("Could not find biome PalettedContainer setter");
        } catch (Exception e) {
            throw new RuntimeException("Failed to paint territory biome", e);
        }
    }

    private static final class PaintTask {
        private final TerritoryRepository.Territory territory;
        private final Holder<Biome> biome;
        private final String biomePreference;
        private final String dimensionId;
        private final int minChunkX;
        private final int maxChunkX;
        private final int minChunkZ;
        private final int maxChunkZ;
        private int chunkX;
        private int chunkZ;
        private boolean done;

        private PaintTask(TerritoryRepository.Territory territory, Holder<Biome> biome, String biomePreference, String dimensionId) {
            this.territory = territory;
            this.biome = biome;
            this.biomePreference = biomePreference;
            this.dimensionId = dimensionId;
            this.minChunkX = Math.floorDiv(territory.minX, 16);
            this.maxChunkX = Math.floorDiv(territory.maxX, 16);
            this.minChunkZ = Math.floorDiv(territory.minZ, 16);
            this.maxChunkZ = Math.floorDiv(territory.maxZ, 16);
            this.chunkX = minChunkX;
            this.chunkZ = minChunkZ;
            this.done = false;
        }

        private void paintNextChunk(ServerLevel level) {
            if (done) return;
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            LevelChunkSection[] sections = chunk.getSections();
            int minSection = level.getMinSection();

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                int sectionY = minSection + sectionIndex;
                int sectionBaseY = sectionY * 16;
                for (int localBiomeY = 0; localBiomeY < 4; localBiomeY++) {
                    int blockY = sectionBaseY + (localBiomeY * 4) + 2;
                    if (blockY < level.getMinBuildHeight() || blockY > level.getMaxBuildHeight()) continue;
                    for (int localBiomeZ = 0; localBiomeZ < 4; localBiomeZ++) {
                        int blockZ = (chunkZ * 16) + (localBiomeZ * 4) + 2;
                        if (blockZ < territory.minZ || blockZ > territory.maxZ) continue;
                        for (int localBiomeX = 0; localBiomeX < 4; localBiomeX++) {
                            int blockX = (chunkX * 16) + (localBiomeX * 4) + 2;
                            if (blockX < territory.minX || blockX > territory.maxX) continue;
                            setBiomeReflective(section, localBiomeX, localBiomeY, localBiomeZ, biome);
                        }
                    }
                }
            }
            chunk.setUnsaved(true);
            advance();
        }

        private void advance() {
            chunkZ++;
            if (chunkZ <= maxChunkZ) return;
            chunkZ = minChunkZ;
            chunkX++;
            if (chunkX <= maxChunkX) return;
            done = true;
        }
    }
}
