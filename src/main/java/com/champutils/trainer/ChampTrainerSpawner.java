package com.champutils.trainer;

import com.champutils.badge.BadgeType;
import com.champutils.gym.GymConfig;
import com.champutils.gym.GymNpcPartyBuilder;
import com.champutils.gym.GymRegistry;
import com.champutils.worldevent.WorldEventBindingRegistry;
import com.champutils.worldevent.WorldEventConfig;

import com.cobblemon.mod.common.api.npc.NPCClass;
import com.cobblemon.mod.common.api.npc.NPCClasses;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.entity.npc.NPCPlayerModelType;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.net.URI;
import java.util.Locale;
import java.util.Optional;

public final class ChampTrainerSpawner {

    private ChampTrainerSpawner() {}

    public enum TrainerKind {
        GYM,
        WORLD_EVENT
    }

    public static class SpawnResult {
        public final boolean success;
        public final String message;
        public final NPCEntity npc;
        public final TrainerKind kind;

        private SpawnResult(boolean success, String message, NPCEntity npc, TrainerKind kind) {
            this.success = success;
            this.message = message;
            this.npc = npc;
            this.kind = kind;
        }

        public static SpawnResult fail(String message) {
            return new SpawnResult(false, message, null, null);
        }

        public static SpawnResult ok(String message, NPCEntity npc, TrainerKind kind) {
            return new SpawnResult(true, message, npc, kind);
        }
    }

    public static SpawnResult spawn(ServerLevel level, Vec3 pos, float yaw, String trainerId) {
        if (level == null) return SpawnResult.fail("No level was provided.");
        if (trainerId == null || trainerId.isBlank()) return SpawnResult.fail("Trainer id cannot be blank.");

        String id = trainerId.trim();
        BadgeType badge = resolveBadge(id);
        if (badge != null && GymConfig.hasGym(badge)) {
            return spawnGym(level, pos, yaw, id, badge);
        }

        WorldEventConfig.EventDefinition event = WorldEventConfig.EVENTS.get(id);
        if (event != null) {
            return spawnWorldEvent(level, pos, yaw, id, event);
        }

        return SpawnResult.fail("Unknown trainer id: " + trainerId + " (not found in gyms.json or world_events.json)");
    }

    private static SpawnResult spawnGym(ServerLevel level, Vec3 pos, float yaw, String trainerId, BadgeType badge) {
        GymConfig.GymDefinition gym = GymConfig.getGym(badge);
        String name = firstNonBlank(gym.spawnName, gym.leaderName, badge.getDisplayName());
        // Skin is optional and must be explicit. Do NOT fall back to leaderName/displayName,
        // because that can create an unwanted player texture layered over the base NPC model.
        String skin = firstNonBlank(gym.spawnSkin, gym.skin, gym.playerSkin, gym.skinPlayer, gym.texture);

        NPCEntity npc = createProtectedNpc(level, pos, yaw, name, skin);
        if (npc == null) return SpawnResult.fail("Could not create NPC for " + trainerId + ".");

        GymRegistry.bindGym(npc.getUUID(), badge);
        GymNpcPartyBuilder.applyGymTeam(npc, badge);
        ChampTrainerProtectionManager.track(npc, trainerId, TrainerKind.GYM, pos, yaw);

        return SpawnResult.ok("Spawned and auto-bound gym trainer " + trainerId + " -> " + badge.name(), npc, TrainerKind.GYM);
    }

    private static SpawnResult spawnWorldEvent(ServerLevel level, Vec3 pos, float yaw, String eventId, WorldEventConfig.EventDefinition event) {
        String name = firstNonBlank(event.spawnName, event.bossName, event.displayName, eventId);
        // Skin is optional and must be explicit. Do NOT fall back to bossName/displayName,
        // because that can create an unwanted player texture layered over the base NPC model.
        String skin = firstNonBlank(event.spawnSkin, event.skin, event.playerSkin, event.skinPlayer, event.texture);

        NPCEntity npc = createProtectedNpc(level, pos, yaw, name, skin);
        if (npc == null) return SpawnResult.fail("Could not create NPC for " + eventId + ".");

        WorldEventBindingRegistry.bind(eventId, npc);
        ChampTrainerProtectionManager.track(npc, eventId, TrainerKind.WORLD_EVENT, pos, yaw);

        return SpawnResult.ok("Spawned and auto-bound world event trainer " + eventId, npc, TrainerKind.WORLD_EVENT);
    }

    public static NPCEntity createProtectedNpc(ServerLevel level, Vec3 pos, float yaw, String displayName, String skin) {
        try {
            NPCEntity npc = new NPCEntity(level);

            // Match the known-working CobblemonNPCs flow: use the Cobblemon NPC class by NAME,
            // not by identifier. Identifier lookup can fall through to the wrong custom class,
            // which is what produced the small "cursed goblin" model.
            NPCClass npcClass = Optional
                    .ofNullable(NPCClasses.INSTANCE.getByName("standard"))
                    .orElse(NPCClasses.INSTANCE.random());
            npc.setNpc(npcClass);

            npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            npc.setCustomName(Component.literal(displayName == null || displayName.isBlank() ? "Trainer" : displayName));
            npc.setCustomNameVisible(true);

            applyTrainerProtections(npc);

            try { npc.initialize(1); } catch (Exception e) { e.printStackTrace(); }
            npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            npc.setCustomName(Component.literal(displayName == null || displayName.isBlank() ? "Trainer" : displayName));
            npc.setCustomNameVisible(true);

            boolean added = level.addFreshEntity(npc);
            if (!added) {
                return null;
            }

            applyTrainerProtections(npc);
            npc.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
            npc.setYHeadRot(yaw);
            npc.setYBodyRot(yaw);
            applyTrainerSkin(npc, skin);

            return npc;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void applyTrainerProtections(NPCEntity npc) {
        if (npc == null) return;
        try { npc.setInvulnerable(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setMovable(false); } catch (Exception ignored) { setNullableBooleanProperty(npc, "setMovable", "setIsMovable", false); }
        try { npc.setLeashable(false); } catch (Exception ignored) { setNullableBooleanProperty(npc, "setLeashable", "setIsLeashable", false); }
        try { npc.setAllowProjectileHits(false); } catch (Exception ignored) { setNullableBooleanProperty(npc, "setAllowProjectileHits", null, false); }
        try { npc.setHealth(npc.getMaxHealth()); } catch (Exception ignored) {}
        try { npc.setDeltaMovement(Vec3.ZERO); } catch (Exception ignored) {}
    }

    private static void applyUsableNpcClass(NPCEntity npc, boolean preferPlayerModel) {
        if (npc == null) return;

        Object npcClass = null;

        if (preferPlayerModel) {
            // Cobblemon class ids can vary slightly between builds/resource packs. Try the
            // known player/human/trainer-style ids first, then fall back to standard.
            npcClass = findNpcClass("cobblemon:player");
            if (npcClass == null) npcClass = findNpcClass("player");
            if (npcClass == null) npcClass = findNpcClass("cobblemon:npc_player");
            if (npcClass == null) npcClass = findNpcClass("npc_player");
            if (npcClass == null) npcClass = findNpcClass("cobblemon:human");
            if (npcClass == null) npcClass = findNpcClass("human");
            if (npcClass == null) npcClass = findNpcClass("cobblemon:trainer");
            if (npcClass == null) npcClass = findNpcClass("trainer");
        }

        if (npcClass == null) npcClass = findNpcClass("cobblemon:standard");
        if (npcClass == null) npcClass = findNpcClass("standard");
        if (npcClass == null) npcClass = firstAvailableNpcClass();
        if (npcClass == null) return;

        setNpcClass(npc, npcClass);
    }

    private static void setNpcClass(NPCEntity npc, Object npcClass) {
        if (npc == null || npcClass == null) return;
        try {
            npc.getClass().getMethod("setNpc", npcClass.getClass()).invoke(npc, npcClass);
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Field field = npc.getClass().getDeclaredField("npc");
                field.setAccessible(true);
                field.set(npc, npcClass);
            } catch (Exception ignoredAgain) {}
        }
    }

    private static void applyTrainerSkin(NPCEntity npc, String skin) {
        if (npc == null || skin == null || skin.isBlank()) return;

        String trimmed = skin.trim();

        // 1) Local skin files for offline/custom skins.
        // Supported config values:
        //   "spawnSkin": "Rick"                         -> config/champutils/skins/Rick.png
        //   "spawnSkin": "Rick.png"                     -> config/champutils/skins/Rick.png
        //   "spawnSkin": "config/champutils/skins/Rick.png"
        File local = resolveSkinFile(trimmed);
        if (local != null && local.exists() && local.isFile()) {
            try {
                npc.loadTexture(local.toURI(), NPCPlayerModelType.DEFAULT);
                try { npc.updateAspects(); } catch (Exception ignored) {}
                return;
            } catch (Exception e) {
                System.out.println("[ChampUtils] Failed to load local trainer skin file: " + local.getPath());
                e.printStackTrace();
            }
        }

        // 2) Direct URL support.
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                npc.loadTexture(URI.create(trimmed), NPCPlayerModelType.DEFAULT);
                try { npc.updateAspects(); } catch (Exception ignored) {}
                return;
            } catch (Exception e) {
                System.out.println("[ChampUtils] Failed to load trainer skin URL: " + trimmed);
                e.printStackTrace();
            }
        }

        // 3) Mojang username fallback. This is async inside Cobblemon, so the NPC may
        // briefly show the default player model before the texture arrives.
        try {
            npc.loadTextureFromGameProfileName(trimmed);
        } catch (Exception e) {
            System.out.println("[ChampUtils] Failed to request trainer skin profile: " + trimmed);
            e.printStackTrace();
        }
    }

    private static File resolveSkinFile(String skin) {
        if (skin == null || skin.isBlank()) return null;

        File direct = new File(skin);
        if (direct.exists()) return direct;

        File dir = new File("config/champutils/skins");
        File exact = new File(dir, skin);
        if (exact.exists()) return exact;

        if (!skin.toLowerCase(Locale.ROOT).endsWith(".png")) {
            File png = new File(dir, skin + ".png");
            if (png.exists()) return png;
        }

        return null;
    }

    private static Object findNpcClass(String id) {
        try {
            Class<?> npcClassesClass = Class.forName("com.cobblemon.mod.common.api.npc.NPCClasses");
            Object instance = kotlinObjectInstance(npcClassesClass);
            Class<?> resourceLocationClass = Class.forName("net.minecraft.resources.ResourceLocation");
            Object resourceLocation = resourceLocationClass.getMethod("parse", String.class).invoke(null, id);

            for (java.lang.reflect.Method method : npcClassesClass.getMethods()) {
                if (!method.getName().equals("getByIdentifier")) continue;
                if (method.getParameterCount() != 1) continue;
                Object result = method.invoke(instance, resourceLocation);
                if (result != null) return result;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Object firstAvailableNpcClass() {
        try {
            Class<?> npcClassesClass = Class.forName("com.cobblemon.mod.common.api.npc.NPCClasses");
            Object instance = kotlinObjectInstance(npcClassesClass);

            Object classes = null;
            try {
                classes = npcClassesClass.getMethod("getClasses").invoke(instance);
            } catch (Exception ignored) {}

            if (classes == null) {
                try {
                    java.lang.reflect.Field field = npcClassesClass.getDeclaredField("classes");
                    field.setAccessible(true);
                    classes = field.get(instance);
                } catch (Exception ignored) {}
            }

            if (classes instanceof Iterable<?> iterable) {
                for (Object candidate : iterable) {
                    if (candidate != null) return candidate;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Object kotlinObjectInstance(Class<?> clazz) {
        try {
            java.lang.reflect.Field field = clazz.getField("INSTANCE");
            return field.get(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static BadgeType resolveBadge(String id) {
        if (id == null) return null;
        String key = id.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "gym1", "brock", "boulder" -> BadgeType.BOULDER;
            case "gym2", "misty", "cascade" -> BadgeType.CASCADE;
            case "gym3", "lt_surge", "ltsurge", "surge", "thunder" -> BadgeType.THUNDER;
            case "gym4", "erika", "rainbow" -> BadgeType.RAINBOW;
            case "gym5", "koga", "soul" -> BadgeType.SOUL;
            case "gym6", "sabrina", "marsh" -> BadgeType.MARSH;
            case "gym7", "blaine", "volcano" -> BadgeType.VOLCANO;
            case "gym8", "giovanni", "earth" -> BadgeType.EARTH;
            case "elite4-1", "elite4_1", "lorelei" -> BadgeType.LORELEI;
            case "elite4-2", "elite4_2", "bruno" -> BadgeType.BRUNO;
            case "elite4-3", "elite4_3", "agatha" -> BadgeType.AGATHA;
            case "elite4-4", "elite4_4", "lance" -> BadgeType.LANCE;
            case "champion", "blue" -> BadgeType.CHAMPION;
            default -> BadgeType.fromString(id);
        };
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

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
