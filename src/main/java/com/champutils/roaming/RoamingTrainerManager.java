package com.champutils.roaming;

import com.champutils.battle.BattleContextManager;
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

    public static int despawnAll(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (RoamingTrainerData data : new ArrayList<>(TRAINERS.values())) {
                NPCEntity npc = findNpc(level, data.npcUuid);
                if (npc != null) {
                    removeNpc(npc);
                    count++;
                }
                TRAINERS.remove(data.npcUuid);
            }
        }
        return count;
    }

    public static int despawnNearby(ServerLevel level, Vec3 center, double radius) {
        if (level == null || center == null) return 0;
        int count = 0;
        for (NPCEntity npc : level.getEntitiesOfClass(NPCEntity.class, box(center, radius))) {
            if (!isRoamingTrainer(npc.getUUID())) continue;
            TRAINERS.remove(npc.getUUID());
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
        int targetLevel = playerPartyAverageLevel(player);
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
        TRAINERS.put(data.npcUuid, data);

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
                continue;
            }

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
                removeNpc(npc);
                iterator.remove();
            }
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

    private static int playerPartyAverageLevel(ServerPlayer player) {
        int total = 0;
        int count = 0;
        try {
            for (Pokemon pokemon : PlayerExtensionsKt.party(player)) {
                if (pokemon == null) continue;
                total += Math.max(1, pokemon.getLevel());
                count++;
            }
        } catch (Exception ignored) {}
        return count <= 0 ? 10 : Math.max(1, Math.min(100, Math.round((float) total / (float) count)));
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
        List<String> names = settings.trainerNames;
        if (names != null && !names.isEmpty()) {
            List<String> clean = names.stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!clean.isEmpty()) return clean.get(RANDOM.nextInt(clean.size()));
        }
        return pretty(rarity.name()) + " Trainer";
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
