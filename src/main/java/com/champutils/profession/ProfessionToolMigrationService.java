package com.champutils.profession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionToolMigrationService {

    private ProfessionToolMigrationService() {
    }

    public static MigrationReport auditOnlineAndLoaded(MinecraftServer server) {
        MigrationReport report = new MigrationReport();
        if (server == null) return report;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            auditPlayer(player, report);
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity) {
                    auditStack(itemEntity.getItem(), report);
                }
            }
        }

        return report;
    }

    public static MigrationReport migrateOnlineAndLoaded(MinecraftServer server) {
        MigrationReport report = new MigrationReport();
        if (server == null) return report;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            migratePlayer(player, report);
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity) {
                    ItemStack migrated = migrateStack(itemEntity.getItem(), report);
                    if (migrated != itemEntity.getItem()) {
                        itemEntity.setItem(migrated);
                    }
                }
            }
        }

        return report;
    }

    public static MigrationReport migrateHeld(ServerPlayer player) {
        MigrationReport report = new MigrationReport();
        if (player == null) return report;

        ItemStack held = player.getMainHandItem();
        ItemStack migrated = migrateStack(held, report);
        if (migrated != held) {
            player.getInventory().setItem(player.getInventory().selected, migrated);
            player.inventoryMenu.broadcastChanges();
        }
        return report;
    }

    private static void auditPlayer(ServerPlayer player, MigrationReport report) {
        if (player == null) return;
        report.playersScanned++;
        player.getInventory().items.forEach(stack -> auditStack(stack, report));
        player.getInventory().armor.forEach(stack -> auditStack(stack, report));
        player.getInventory().offhand.forEach(stack -> auditStack(stack, report));
        for (int slot = 0; slot < player.getEnderChestInventory().getContainerSize(); slot++) {
            auditStack(player.getEnderChestInventory().getItem(slot), report);
        }
    }

    private static void migratePlayer(ServerPlayer player, MigrationReport report) {
        if (player == null) return;
        report.playersScanned++;
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            ItemStack original = player.getInventory().items.get(slot);
            ItemStack migrated = migrateStack(original, report);
            if (migrated != original) player.getInventory().items.set(slot, migrated);
        }
        for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
            ItemStack original = player.getInventory().armor.get(slot);
            ItemStack migrated = migrateStack(original, report);
            if (migrated != original) player.getInventory().armor.set(slot, migrated);
        }
        for (int slot = 0; slot < player.getInventory().offhand.size(); slot++) {
            ItemStack original = player.getInventory().offhand.get(slot);
            ItemStack migrated = migrateStack(original, report);
            if (migrated != original) player.getInventory().offhand.set(slot, migrated);
        }
        for (int slot = 0; slot < player.getEnderChestInventory().getContainerSize(); slot++) {
            ItemStack original = player.getEnderChestInventory().getItem(slot);
            ItemStack migrated = migrateStack(original, report);
            if (migrated != original) player.getEnderChestInventory().setItem(slot, migrated);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static void auditStack(ItemStack stack, MigrationReport report) {
        report.stacksScanned++;
        String toolId = ProfessionToolUtil.getToolId(stack);
        if (toolId == null || toolId.isBlank()) {
            report.skipped++;
            return;
        }
        int version = ProfessionToolMetadata.getToolVersion(stack);
        report.versionCounts.merge(version, 1, Integer::sum);
        if (version < ProfessionToolMetadata.CURRENT_TOOL_VERSION || !ProfessionToolMetadata.isProfessionTool(stack)) {
            report.needsMigration++;
        }
    }

    private static ItemStack migrateStack(ItemStack original, MigrationReport report) {
        report.stacksScanned++;
        if (original == null || original.isEmpty()) {
            report.skipped++;
            return original;
        }

        String toolId = ProfessionToolUtil.getToolId(original);
        if (toolId == null || toolId.isBlank()) {
            report.skipped++;
            return original;
        }

        ProfessionToolConfig.ToolData toolData = ProfessionToolConfig.TOOLS.get(toolId);
        if (toolData == null) {
            report.invalidTools++;
            return original;
        }

        int version = ProfessionToolMetadata.getToolVersion(original);
        report.versionCounts.merge(version, 1, Integer::sum);

        boolean legacyRootMissing = !ProfessionToolMetadata.isProfessionTool(original);
        if (!legacyRootMissing && version >= ProfessionToolMetadata.CURRENT_TOOL_VERSION) {
            ProfessionToolManager.refreshToolStack(original);
            report.refreshed++;
            return original;
        }

        ItemStack migrated = ProfessionToolManager.createToolWithoutAscendedRoll(toolId, ProfessionToolMetadata.isAscended(original));
        if (migrated.isEmpty()) {
            report.failed++;
            return original;
        }

        copyPersistentProgress(original, migrated, toolData);
        ProfessionToolMetadata.setToolVersion(migrated, ProfessionToolMetadata.CURRENT_TOOL_VERSION);
        ProfessionToolManager.refreshToolStack(migrated);
        report.updated++;
        return migrated;
    }

    private static void copyPersistentProgress(ItemStack from, ItemStack to, ProfessionToolConfig.ToolData toolData) {
        ProfessionToolMetadata.setIdentified(to, true);
        ProfessionToolMetadata.setAscended(to, ProfessionToolMetadata.isAscended(from));
        ProfessionToolMetadata.setRerolls(to, ProfessionToolMetadata.getRerolls(from));

        Map<String, Double> rolledStats = ProfessionToolMetadata.getRolledStats(from);
        if (!rolledStats.isEmpty()) {
            ProfessionToolMetadata.setRolledStats(to, rolledStats);
            ProfessionToolMetadata.setQuality(to, ProfessionToolMetadata.getQuality(from));
        }

        int oldMax = ProfessionToolMetadata.getMaxDurability(from);
        int oldCurrent = ProfessionToolMetadata.getCurrentDurability(from);
        ProfessionToolManager.initializeDurabilityIfNeeded(to, toolData, true);
        int newMax = Math.max(1, ProfessionToolMetadata.getMaxDurability(to));
        if (oldMax > 0 && oldCurrent >= 0) {
            int newCurrent = (int) Math.round(newMax * (oldCurrent / (double) oldMax));
            ProfessionToolMetadata.setCurrentDurability(to, Math.max(0, Math.min(newMax, newCurrent)));
        }

        ProfessionToolMetadata.setLocked(to, ProfessionToolMetadata.isLocked(from));
        ProfessionToolMetadata.setDiscoveryAnnouncementEligible(to, ProfessionToolMetadata.isDiscoveryAnnouncementEligible(from));
        ProfessionToolMetadata.setDiscoveryAnnounced(to, ProfessionToolMetadata.isDiscoveryAnnounced(from));
        ProfessionToolMetadata.setPerfectRollAnnounced(to, ProfessionToolMetadata.isPerfectRollAnnounced(from));

        String selectedTracker = ProfessionToolMetadata.getSelectedTracker(from);
        if (selectedTracker != null && !selectedTracker.isBlank()) {
            ProfessionToolMetadata.setSelectedTracker(to, selectedTracker);
        }
        for (Map.Entry<String, Long> entry : ProfessionToolMetadata.getTrackers(from).entrySet()) {
            ProfessionToolMetadata.setTracker(to, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : ProfessionToolMetadata.getCustomEnchants(from).entrySet()) {
            ProfessionToolMetadata.setCustomEnchantLevel(to, entry.getKey(), entry.getValue());
        }
    }

    public static final class MigrationReport {
        public int playersScanned;
        public int stacksScanned;
        public int skipped;
        public int refreshed;
        public int updated;
        public int needsMigration;
        public int invalidTools;
        public int failed;
        public final Map<Integer, Integer> versionCounts = new LinkedHashMap<>();

        public Component toComponent(String title) {
            return Component.literal(title + "\n" +
                    "Players scanned: " + playersScanned + "\n" +
                    "Stacks scanned: " + stacksScanned + "\n" +
                    "Updated: " + updated + "\n" +
                    "Refreshed current tools: " + refreshed + "\n" +
                    "Needs migration: " + needsMigration + "\n" +
                    "Invalid tools: " + invalidTools + "\n" +
                    "Failed: " + failed + "\n" +
                    "Versions: " + versionCounts);
        }
    }
}
