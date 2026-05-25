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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Paints biome data into VOID-generated territory slots.
 *
 * This gives skyblock territories real biome identity without generating normal terrain. It edits the chunk biome
 * containers only; it does not place terrain, trees, caves, ores, water, structures, or decorations.
 *
 * IMPORTANT:
 * Minecraft/Fabric production runtime names can change enough that biome container reflection is not guaranteed.
 * Biome painting is nice-to-have, not worth crashing the server. If reflection fails, the task now fails open:
 * the territory can still become READY and the selected biome preference remains saved for future systems.
 */
public final class TerritoryBiomePaintManager {
    private static final Map<UUID, PaintTask> TASKS = new LinkedHashMap<>();
    private static boolean loggedReflectionFailure = false;

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
            try {
                task.paintNextChunk(level);
            } catch (Throwable throwable) {
                // Absolute safety net: optional biome painting must never crash the server tick loop.
                task.failed = true;
                task.done = true;
                logReflectionFailure(task, throwable);
            }
        }

        if (task.done) {
            TerritoryRepository.Territory live = TerritoryRepository.get(task.territory.id);
            if (live != null) live.biomePreference = task.biomePreference;
            iterator.remove();

            if (task.failed) {
                System.err.println("[ChampUtils] Skipped biome painting for territory " + task.territory.id + ". Territory creation will continue safely.");
            } else {
                System.out.println("[ChampUtils] Finished painting territory " + task.territory.id + " biome to minecraft:" + task.biomePreference + ".");
            }
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

    private static boolean setBiomeReflective(LevelChunkSection section, int x, int y, int z, Holder<Biome> biome) {
        try {
            Object container = findBiomeContainer(section);
            if (container == null) return false;

            Method setter = findSetter(container);
            if (setter == null) return false;

            setter.setAccessible(true);
            setter.invoke(container, x, y, z, biome);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static Object findBiomeContainer(LevelChunkSection section) {
        // Prefer public/protected accessors first. Dev mappings commonly expose getBiomes().
        for (String methodName : new String[] { "getBiomes", "method_38292" }) {
            try {
                Method method = section.getClass().getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(section);
                if (looksLikeBiomeContainer(value)) return value;
            } catch (Throwable ignored) {
            }

            try {
                Method method = section.getClass().getDeclaredMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(section);
                if (looksLikeBiomeContainer(value)) return value;
            } catch (Throwable ignored) {
            }
        }

        // Then scan fields. Production runtime names can be obfuscated, so do not rely on type names.
        Class<?> clazz = section.getClass();
        while (clazz != null) {
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(section);
                    if (looksLikeBiomeContainer(value)) return value;
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static boolean looksLikeBiomeContainer(Object value) {
        if (value == null) return false;

        Method setter = findSetter(value);
        if (setter == null) return false;

        // We cannot reliably inspect generic type at runtime, so accept the container if it has a 3D setter.
        // LevelChunkSection only has one such container we can safely write Holder<Biome> into on mapped dev jars.
        return true;
    }

    private static Method findSetter(Object container) {
        if (container == null) return null;

        for (String methodName : new String[] {
                "set",
                "getAndSetUnchecked",
                "getAndSet",
                "method_12227",
                "method_12228"
        }) {
            Method method = findFourArgIntSetter(container.getClass(), methodName, true);
            if (method != null) return method;

            method = findFourArgIntSetter(container.getClass(), methodName, false);
            if (method != null) return method;
        }

        return null;
    }

    private static Method findFourArgIntSetter(Class<?> clazz, String name, boolean publicOnly) {
        Method[] methods = publicOnly ? clazz.getMethods() : clazz.getDeclaredMethods();

        for (Method method : methods) {
            if (!method.getName().equals(name)) continue;
            Class<?>[] types = method.getParameterTypes();
            if (types.length != 4) continue;
            if (types[0] != int.class || types[1] != int.class || types[2] != int.class) continue;
            return method;
        }

        return null;
    }

    private static void logReflectionFailure(PaintTask task, Throwable throwable) {
        if (loggedReflectionFailure) return;
        loggedReflectionFailure = true;
        System.err.println("[ChampUtils] Territory biome painting is not compatible with this runtime mapping. This is non-fatal.");
        System.err.println("[ChampUtils] Selected biome preference will still be saved, and territory creation will continue.");
        if (throwable != null) {
            throwable.printStackTrace();
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
        private boolean failed;

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
            this.failed = false;
        }

        private void paintNextChunk(ServerLevel level) {
            if (done) return;
            LevelChunk chunk = level.getChunk(chunkX, chunkZ);
            LevelChunkSection[] sections = chunk.getSections();
            int minSection = level.getMinSection();
            boolean paintedAny = false;

            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null) continue;

                int sectionY = minSection + sectionIndex;
                int sectionBaseY = sectionY * 16;
                for (int localBiomeY = 0; localBiomeY < 4; localBiomeY++) {
                    int blockY = sectionBaseY + (localBiomeY * 4) + 2;
                    if (blockY < level.getMinBuildHeight() || blockY >= level.getMaxBuildHeight()) continue;
                    for (int localBiomeZ = 0; localBiomeZ < 4; localBiomeZ++) {
                        int blockZ = (chunkZ * 16) + (localBiomeZ * 4) + 2;
                        if (blockZ < territory.minZ || blockZ > territory.maxZ) continue;
                        for (int localBiomeX = 0; localBiomeX < 4; localBiomeX++) {
                            int blockX = (chunkX * 16) + (localBiomeX * 4) + 2;
                            if (blockX < territory.minX || blockX > territory.maxX) continue;

                            if (!setBiomeReflective(section, localBiomeX, localBiomeY, localBiomeZ, biome)) {
                                failed = true;
                                done = true;
                                logReflectionFailure(this, null);
                                return;
                            }

                            paintedAny = true;
                        }
                    }
                }
            }

            if (paintedAny) chunk.setUnsaved(true);
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
