package com.champutils.roaming;

import com.champutils.battle.BattleContextManager;
import com.champutils.battle.BattleStateManager;
import com.champutils.profession.ProfessionFragmentManager;
import com.champutils.trainer.ChampTrainerSpawner;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.util.PlayerExtensionsKt;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

public final class RoamingTrainerManager {

    private static final Random RANDOM = new Random();
    private static final Map<UUID, RoamingTrainerData> TRAINERS = new ConcurrentHashMap<>();
    private static int ticksUntilScan = 20;

    private RoamingTrainerManager() {}

    public static class RoamingTrainerData {
        public UUID npcUuid;
        public UUID ownerPlayerUuid;
        public RoamingTrainerRarity rarity;
        public int targetLevel;
        public long lastNearbyPlayerMillis;
        public boolean rewardsClaimed;
        public String displayName;
        public UUID currentChallengerUuid;
        public long challengeLockMillis;
        public double spawnX;
        public double spawnY;
        public double spawnZ;
        public float spawnYaw;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        cleanupAndDespawn(server);
        if (!RoamingTrainerConfig.DATA.enabled) return;

        ticksUntilScan--;
        if (ticksUntilScan > 0) return;
        ticksUntilScan = Math.max(5, RoamingTrainerConfig.DATA.scanIntervalSeconds) * 20;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            trySpawnFor(player);
        }
    }

    public static boolean isRoamingTrainer(UUID npcUuid) {
        return npcUuid != null && TRAINERS.containsKey(npcUuid);
    }

    public static RoamingTrainerData get(UUID npcUuid) {
        return npcUuid == null ? null : TRAINERS.get(npcUuid);
    }


    public static boolean tryStartChallenge(ServerPlayer player, NPCEntity npc) {
        if (player == null || npc == null) return false;
        RoamingTrainerData data = TRAINERS.get(npc.getUUID());
        if (data == null) return true;

        UUID playerUuid = player.getUUID();
        UUID challenger = data.currentChallengerUuid;
        if (challenger != null && !challenger.equals(playerUuid)) {
            player.sendSystemMessage(Component.literal("§cThat trainer is already battling another player."));
            return false;
        }

        if (isNpcInBattle(npc) && (challenger == null || !challenger.equals(playerUuid))) {
            player.sendSystemMessage(Component.literal("§cThat trainer is already in battle."));
            return false;
        }

        data.currentChallengerUuid = playerUuid;
        data.challengeLockMillis = System.currentTimeMillis();
        applyRoamingProtections(npc, data);
        return true;
    }

    public static void releaseChallenge(UUID npcUuid, UUID playerUuid) {
        RoamingTrainerData data = TRAINERS.get(npcUuid);
        if (data == null) return;
        if (playerUuid == null || playerUuid.equals(data.currentChallengerUuid)) {
            data.currentChallengerUuid = null;
            data.challengeLockMillis = 0L;
        }
    }

    public static void handleBattleEnded(UUID npcUuid) {
        RoamingTrainerData data = TRAINERS.get(npcUuid);
        if (data == null) return;
        data.currentChallengerUuid = null;
        data.challengeLockMillis = 0L;
    }

    public static int despawnAll(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (RoamingTrainerData data : new ArrayList<>(TRAINERS.values())) {
                NPCEntity npc = findNpc(level, data.npcUuid);
                TRAINERS.remove(data.npcUuid);
                if (npc != null) {
                    cancelBattleForRemovedTrainer(server, data, "The roaming trainer disappeared, so the battle was canceled.");
                    removeNpc(npc);
                    count++;
                }
            }
        }
        return count;
    }

    public static int despawnNearby(ServerLevel level, Vec3 center, double radius) {
        if (level == null || center == null) return 0;
        int count = 0;
        for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, box(center, radius))) {
            if (!isRoamingTrainer(npc.getUUID())) continue;
            RoamingTrainerData data = TRAINERS.remove(npc.getUUID());
            cancelBattleForRemovedTrainer(level.getServer(), data, "The roaming trainer disappeared, so the battle was canceled.");
            removeNpc(npc);
            count++;
        }
        return count;
    }

    public static boolean spawnManual(ServerPlayer player, RoamingTrainerRarity rarity) {
        if (player == null) return false;
        return spawnNearPlayer(player, rarity == null ? chooseRarity() : rarity, true);
    }

    public static void handleVictory(ServerPlayer winner, UUID losingNpcUuid) {
        if (winner == null || losingNpcUuid == null) return;
        RoamingTrainerData data = TRAINERS.get(losingNpcUuid);
        if (data == null || data.rewardsClaimed) return;
        data.rewardsClaimed = true;

        RoamingTrainerConfig.RaritySettings settings = RoamingTrainerConfig.settings(data.rarity);
        int fragments = randomBetween(settings.fragmentMin, settings.fragmentMax);
        if (fragments > 0) {
            ItemStack stack = ProfessionFragmentManager.createFragmentStack(data.rarity.name(), fragments);
            if (stack != null && !stack.isEmpty()) {
                boolean added = winner.getInventory().add(stack);
                if (!added) winner.drop(stack, false);
                winner.sendSystemMessage(Component.literal("You received " + fragments + " " + pretty(data.rarity.name()) + " Fragment(s).").withStyle(data.rarity.color));
            }
        }

        runCommands(winner, settings.rewardCommands, data);
        winner.sendSystemMessage(Component.literal("You defeated a " + pretty(data.rarity.name()) + " trainer!").withStyle(data.rarity.color));

        NPCEntity npc = findNpc(winner.getServer(), losingNpcUuid);
        if (npc != null) {
            removeNpc(npc);
        }
        TRAINERS.remove(losingNpcUuid);
    }

    private static void trySpawnFor(ServerPlayer player) {
        if (player == null || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        String dimensionId = level.dimension().location().toString();
        if (RoamingTrainerConfig.isBlockedDimension(dimensionId)) return;
        if (countNearbyRoamingTrainers(level, player.position(), RoamingTrainerConfig.DATA.activePlayerRadius) >= RoamingTrainerConfig.DATA.maxTrainersPerPlayer) return;
        spawnNearPlayer(player, chooseRarity(), false);
    }

    private static boolean spawnNearPlayer(ServerPlayer player, RoamingTrainerRarity rarity, boolean force) {
        ServerLevel level = player.serverLevel();
        String dimensionId = level.dimension().location().toString();
        if (!force && RoamingTrainerConfig.isBlockedDimension(dimensionId)) return false;

        Vec3 pos = findSpawnPosition(level, player.position());
        if (pos == null) return false;

        RoamingTrainerConfig.RaritySettings settings = RoamingTrainerConfig.settings(rarity);
        int targetLevel = playerPartyHighestLevelPlusFive(player);
        String displayName = chooseName(rarity, settings);
        String skin = chooseSkin();
        ChampTrainerSpawner.SpawnResult result = ChampTrainerSpawner.spawnRoaming(level, pos, player.getYRot() + 180.0F, displayName, skin);
        if (!result.success || result.npc == null) return false;

        RoamingTrainerData data = new RoamingTrainerData();
        data.npcUuid = result.npc.getUUID();
        data.ownerPlayerUuid = player.getUUID();
        data.rarity = rarity;
        data.targetLevel = targetLevel;
        data.lastNearbyPlayerMillis = System.currentTimeMillis();
        data.displayName = displayName;
        data.spawnX = pos.x;
        data.spawnY = pos.y;
        data.spawnZ = pos.z;
        data.spawnYaw = player.getYRot() + 180.0F;
        TRAINERS.put(data.npcUuid, data);

        applyRoamingProtections(result.npc, data);

        RoamingTrainerPartyBuilder.apply(result.npc, data);

        if (rarity.alertsPlayers()) {
            alertNearbyPlayers(level, result.npc.position(), rarity, displayName);
        }
        return true;
    }

    private static Vec3 findSpawnPosition(ServerLevel level, Vec3 origin) {
        int min = Math.max(8, RoamingTrainerConfig.DATA.spawnMinDistance);
        int max = Math.max(min, RoamingTrainerConfig.DATA.spawnMaxDistance);
        for (int i = 0; i < RoamingTrainerConfig.DATA.maxSpawnAttemptsPerPlayer; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double distance = min + RANDOM.nextDouble() * (max - min);
            int x = (int)Math.floor(origin.x + Math.cos(angle) * distance);
            int z = (int)Math.floor(origin.z + Math.sin(angle) * distance);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 2) continue;
            if (RoamingTrainerConfig.DATA.requireSolidGround) {
                if (level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).isAir()) continue;
                if (!level.getBlockState(new net.minecraft.core.BlockPos(x, y, z)).isAir()) continue;
                if (!level.getBlockState(new net.minecraft.core.BlockPos(x, y + 1, z)).isAir()) continue;
                if (level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).is(Blocks.WATER)) continue;
                if (level.getBlockState(new net.minecraft.core.BlockPos(x, y - 1, z)).is(Blocks.LAVA)) continue;
            }
            return new Vec3(x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    private static void cleanupAndDespawn(MinecraftServer server) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, RoamingTrainerData>> iterator = TRAINERS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RoamingTrainerData> entry = iterator.next();
            UUID npcUuid = entry.getKey();
            RoamingTrainerData data = entry.getValue();
            NPCEntity npc = findNpc(server, npcUuid);
            if (npc == null || !npc.isAlive()) {
                iterator.remove();
                cancelBattleForRemovedTrainer(server, data, "The roaming trainer was removed, so the battle was canceled.");
                continue;
            }

            applyRoamingProtections(npc, data);
            cleanupStaleChallengeLock(npc, data, now);

            boolean playerNearby = hasPlayerNearby((ServerLevel) npc.level(), npc.position(), RoamingTrainerConfig.DATA.activePlayerRadius);
            if (playerNearby) {
                data.lastNearbyPlayerMillis = now;
                continue;
            }

            if (RoamingTrainerConfig.DATA.doNotDespawnWhileInBattle && isNpcInBattle(npc)) {
                data.lastNearbyPlayerMillis = now;
                continue;
            }

            long elapsed = now - data.lastNearbyPlayerMillis;
            if (elapsed >= Math.max(30, RoamingTrainerConfig.DATA.despawnAfterNoPlayersSeconds) * 1000L) {
                iterator.remove();
                cancelBattleForRemovedTrainer(server, data, "The roaming trainer despawned, so the battle was canceled.");
                removeNpc(npc);
            }
        }
    }

    private static void cancelBattleForRemovedTrainer(MinecraftServer server, RoamingTrainerData data, String message) {
        if (server == null || data == null) return;

        data.rewardsClaimed = true;
        UUID playerUuid = data.currentChallengerUuid;
        data.currentChallengerUuid = null;
        data.challengeLockMillis = 0L;

        if (playerUuid == null) return;

        ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
        if (player == null) return;

        Object battle = BattleStateManager.getBattle(player);
        if (battle != null) {
            tryStopBattle(battle, player);
        }

        BattleStateManager.clearAll(player);

        if (message != null && !message.isBlank()) {
            player.sendSystemMessage(Component.literal("§e" + message));
        }
    }

    private static boolean tryStopBattle(Object battle, ServerPlayer player) {
        if (battle == null) return false;

        String[] methods = new String[]{
                "forfeit",
                "flee",
                "stop",
                "end",
                "endBattle",
                "close"
        };

        for (String methodName : methods) {
            if (invokeBattleMethod(battle, methodName, player)) return true;
            if (invokeBattleMethod(battle, methodName)) return true;
        }

        return false;
    }

    private static boolean invokeBattleMethod(Object target, String methodName, Object... args) {
        try {
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != args.length) continue;
                method.setAccessible(true);
                method.invoke(target, args);
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private static void cleanupStaleChallengeLock(NPCEntity npc, RoamingTrainerData data, long now) {
        if (npc == null || data == null || data.currentChallengerUuid == null) return;
        if (isNpcInBattle(npc)) return;
        if (now - data.challengeLockMillis >= 15000L) {
            data.currentChallengerUuid = null;
            data.challengeLockMillis = 0L;
        }
    }

    private static void applyRoamingProtections(NPCEntity npc, RoamingTrainerData data) {
        if (npc == null) return;
        try { npc.setInvulnerable(true); } catch (Exception ignored) {}
        try { npc.setPersistenceRequired(); } catch (Exception ignored) {}
        try { npc.setNoAi(true); } catch (Exception ignored) {}
        try { npc.setMovable(false); } catch (Exception ignored) {}
        try { npc.setLeashable(false); } catch (Exception ignored) {}
        try { npc.setAllowProjectileHits(false); } catch (Exception ignored) {}
        try { npc.setHealth(npc.getMaxHealth()); } catch (Exception ignored) {}
        try { npc.setDeltaMovement(Vec3.ZERO); } catch (Exception ignored) {}

        if (data != null) {
            try {
                Vec3 spawn = new Vec3(data.spawnX, data.spawnY, data.spawnZ);
                if (npc.position().distanceToSqr(spawn) > 0.04D) {
                    npc.teleportTo(data.spawnX, data.spawnY, data.spawnZ);
                    npc.moveTo(data.spawnX, data.spawnY, data.spawnZ, data.spawnYaw, 0.0F);
                }
                npc.setYHeadRot(data.spawnYaw);
                npc.setYBodyRot(data.spawnYaw);
            } catch (Exception ignored) {}
        }
    }

    private static boolean isNpcInBattle(NPCEntity npc) {
        try { return npc.isInBattle(); } catch (Exception ignored) { return false; }
    }

    private static NPCEntity findNpc(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            NPCEntity npc = findNpc(level, uuid);
            if (npc != null) return npc;
        }
        return null;
    }

    private static NPCEntity findNpc(ServerLevel level, UUID uuid) {
        if (level == null || uuid == null) return null;
        Entity entity = level.getEntity(uuid);
        return entity instanceof NPCEntity npc ? npc : null;
    }

    private static int countNearbyRoamingTrainers(ServerLevel level, Vec3 center, double radius) {
        int count = 0;
        for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, box(center, radius))) {
            if (isRoamingTrainer(npc.getUUID())) count++;
        }
        return count;
    }

    private static boolean hasPlayerNearby(ServerLevel level, Vec3 center, double radius) {
        return !level.getEntitiesOfClass(ServerPlayer.class, box(center, radius), p -> !p.isSpectator()).isEmpty();
    }

    private static AABB box(Vec3 center, double radius) {
        double r = Math.max(1.0D, radius);
        return new AABB(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
    }

    private static void removeNpc(NPCEntity npc) {
        if (npc == null) return;
        try { npc.remove(Entity.RemovalReason.DISCARDED); } catch (Exception ignored) {}
    }

    private static int playerPartyHighestLevelPlusFive(ServerPlayer player) {
        int highest = 0;
        try {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                if (pokemon == null) continue;
                highest = Math.max(highest, Math.max(1, pokemon.getLevel()));
            }
        } catch (Exception ignored) {}

        // Roaming trainer scaling is intentionally simple and predictable:
        // every trainer Pokemon is exactly five levels above the player's highest party Pokemon.
        // If the player somehow has no readable party, use level 15 instead of failing the spawn.
        if (highest <= 0) return 15;
        return Math.max(1, Math.min(100, highest + 5));
    }

    private static RoamingTrainerRarity chooseRarity() {
        Map<RoamingTrainerRarity, Double> weights = new LinkedHashMap<>();
        double total = 0.0D;
        for (RoamingTrainerRarity rarity : RoamingTrainerRarity.values()) {
            double weight = Math.max(0.0D, RoamingTrainerConfig.settings(rarity).weight);
            weights.put(rarity, weight);
            total += weight;
        }
        if (total <= 0.0D) return RoamingTrainerRarity.COMMON;
        double roll = RANDOM.nextDouble() * total;
        for (Map.Entry<RoamingTrainerRarity, Double> entry : weights.entrySet()) {
            roll -= entry.getValue();
            if (roll <= 0.0D) return entry.getKey();
        }
        return RoamingTrainerRarity.COMMON;
    }

    private static String chooseName(RoamingTrainerRarity rarity, RoamingTrainerConfig.RaritySettings settings) {
        String title = pretty(rarity.name()) + " Trainer";
        List<String> names = settings.trainerNames;
        if (names != null && !names.isEmpty()) {
            List<String> clean = names.stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!clean.isEmpty()) title = clean.get(RANDOM.nextInt(clean.size()));
        }

        String[] firstNames = {
                "Aiden", "Aria", "Blake", "Brock", "Callie", "Carter", "Dawn", "Drew",
                "Elena", "Eli", "Felix", "Flint", "Grace", "Grant", "Harper", "Iris",
                "Jade", "Kai", "Lana", "Leo", "Misty", "Nate", "Nora", "Orion",
                "Paige", "Quinn", "Riley", "Rowan", "Serena", "Sky", "Talia", "Theo",
                "Valerie", "Wade", "Wren", "Zane"
        };
        String first = firstNames[RANDOM.nextInt(firstNames.length)];

        // Pokemon trainer-style display: "Ace Trainer Kai", "Dragon Tamer Iris", etc.
        if (title.toLowerCase(Locale.ROOT).contains(first.toLowerCase(Locale.ROOT))) return title;
        return title + " " + first;
    }


    private static String chooseSkin() {
        List<String> skins = RoamingTrainerConfig.DATA.randomTrainerSkins;
        if (skins == null || skins.isEmpty()) return "";
        List<String> clean = skins.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .filter(s -> !RoamingTrainerConfig.isBlockedDefaultSkin(s))
                .distinct()
                .toList();
        if (clean.isEmpty()) return "";
        return clean.get(RANDOM.nextInt(clean.size()));
    }

    private static void alertNearbyPlayers(ServerLevel level, Vec3 pos, RoamingTrainerRarity rarity, String displayName) {
        double radius = Math.max(32.0D, RoamingTrainerConfig.DATA.activePlayerRadius);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box(pos, radius), p -> !p.isSpectator())) {
            player.sendSystemMessage(Component.literal("A " + pretty(rarity.name()) + " trainer appeared nearby: " + displayName + "!").withStyle(rarity.color));
            try { player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.9F, 1.0F); } catch (Exception ignored) {}
        }
    }

    private static void runCommands(ServerPlayer player, List<String> commands, RoamingTrainerData data) {
        if (player == null || commands == null || commands.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CommandSourceStack source = server.createCommandSourceStack();
        for (String raw : commands) {
            if (raw == null || raw.isBlank()) continue;
            String cmd = raw
                    .replace("%player%", player.getName().getString())
                    .replace("%uuid%", player.getUUID().toString())
                    .replace("%rarity%", data.rarity.name().toLowerCase(Locale.ROOT))
                    .replace("%RARITY%", data.rarity.name());
            server.getCommands().performPrefixedCommand(source, cmd);
        }
    }

    private static int randomBetween(int min, int max) {
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return min + RANDOM.nextInt((max - min) + 1);
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Common";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
