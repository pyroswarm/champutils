package com.champutils.profession.actives;

import com.champutils.profession.ProfessionNotificationSettings;

import com.champutils.profession.ProfessionBlockTracker;
import com.champutils.profession.ProfessionNotificationSettings;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.profession.ProfessionToolUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TreasureSenseAbility implements ProfessionActiveAbility {

    private static final int DEFAULT_RADIUS = 72;
    private static final int MAX_RESULTS = 3;

    @Override
    public String id() {
        return "treasure_sense";
    }

    @Override
    public boolean use(ServerPlayer player, ItemStack stack) {
        BlockPos origin = player.blockPosition();
        int radius = getRadius(stack);

        Set<BlockPos> candidates = new HashSet<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);

                    if (ProfessionBlockTracker.isPlayerPlaced(player.serverLevel(), pos)) {
                        continue;
                    }

                    Block block = player.serverLevel().getBlockState(pos).getBlock();
                    String blockId = block.builtInRegistryHolder().key().location().toString();

                    if (!isTreasureBlock(blockId)) {
                        continue;
                    }

                    candidates.add(pos.immutable());
                }
            }
        }

        List<FoundVein> veins = buildVeins(player, origin, candidates);
        veins.sort(Comparator.comparingDouble(vein -> vein.distance));

        if (veins.isEmpty()) {
            player.sendSystemMessage(Component.literal("§7Treasure Sense found no rare natural veins or ancient debris within §e" + radius + " blocks§7."));
            if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
                player.displayClientMessage(Component.literal("§7No rare treasure veins detected nearby."), true);
            }
            ProfessionNotificationSettings.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.65F, 0.75F);
            return true;
        }

        int shown = Math.min(MAX_RESULTS, veins.size());
        player.sendSystemMessage(Component.literal("§dTreasure Sense: §7nearest §e" + shown + " §7rare target" + (shown == 1 ? "" : "s") + " within §e" + radius + " blocks§7:"));

        for (int i = 0; i < shown; i++) {
            FoundVein vein = veins.get(i);
            String treasureName = formatBlockName(vein.blockId);
            int blocksAway = Math.max(1, (int) Math.round(vein.distance));
            String direction = getDirectionText(origin, vein.nearestPos);
            String sizeText = vein.count <= 1 ? "single block" : vein.count + " block vein";

            player.sendSystemMessage(Component.literal(
                    "§d#" + (i + 1) + " §f" + treasureName + " §7(" + sizeText + ") §e" + blocksAway + " blocks §7" + direction + "§7."
            ));
        }

        if (ProfessionNotificationSettings.areProfessionPopupsEnabled(player)) {
            FoundVein first = veins.get(0);
            String treasureName = formatBlockName(first.blockId);
            int blocksAway = Math.max(1, (int) Math.round(first.distance));
            player.displayClientMessage(Component.literal("§dNearest treasure: " + treasureName + " " + blocksAway + " blocks away"), true);
        }

        ProfessionNotificationSettings.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F, 1.85F);
        return true;
    }

    private List<FoundVein> buildVeins(ServerPlayer player, BlockPos origin, Set<BlockPos> candidates) {
        List<FoundVein> veins = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();

        for (BlockPos start : candidates) {
            if (visited.contains(start)) {
                continue;
            }

            Block startBlock = player.serverLevel().getBlockState(start).getBlock();
            String blockId = startBlock.builtInRegistryHolder().key().location().toString();

            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            int count = 0;
            BlockPos nearestPos = start;
            double nearestDistance = Math.sqrt(origin.distSqr(start));

            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                count++;

                double distance = Math.sqrt(origin.distSqr(current));
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPos = current;
                }

                for (BlockPos neighbor : getNeighbors(current)) {
                    BlockPos immutableNeighbor = neighbor.immutable();
                    if (!candidates.contains(immutableNeighbor) || visited.contains(immutableNeighbor)) {
                        continue;
                    }

                    Block neighborBlock = player.serverLevel().getBlockState(immutableNeighbor).getBlock();
                    String neighborId = neighborBlock.builtInRegistryHolder().key().location().toString();
                    if (!blockId.equals(neighborId)) {
                        continue;
                    }

                    visited.add(immutableNeighbor);
                    queue.add(immutableNeighbor);
                }
            }

            veins.add(new FoundVein(blockId, nearestPos, nearestDistance, count));
        }

        return veins;
    }

    private List<BlockPos> getNeighbors(BlockPos pos) {
        List<BlockPos> neighbors = new ArrayList<>();
        neighbors.add(pos.offset(1, 0, 0));
        neighbors.add(pos.offset(-1, 0, 0));
        neighbors.add(pos.offset(0, 1, 0));
        neighbors.add(pos.offset(0, -1, 0));
        neighbors.add(pos.offset(0, 0, 1));
        neighbors.add(pos.offset(0, 0, -1));
        return neighbors;
    }

    private int getRadius(ItemStack stack) {
        ProfessionToolConfig.ToolData toolData = ProfessionToolUtil.getToolData(stack);
        if (toolData != null && toolData.treasureSenseRadius > 0) {
            return Math.max(16, toolData.treasureSenseRadius);
        }

        double rolledRadius = ProfessionToolUtil.getStat(stack, "treasureSenseRadius");
        if (rolledRadius <= 0.0D) {
            return DEFAULT_RADIUS;
        }
        return Math.max(16, (int) Math.round(rolledRadius));
    }

    private boolean isTreasureBlock(String blockId) {
        return switch (blockId) {
            case "minecraft:diamond_ore",
                 "minecraft:deepslate_diamond_ore",
                 "minecraft:emerald_ore",
                 "minecraft:deepslate_emerald_ore",
                 "minecraft:ancient_debris",
                 "cobblemon:dawn_stone_ore",
                 "cobblemon:deepslate_dawn_stone_ore",
                 "cobblemon:dusk_stone_ore",
                 "cobblemon:deepslate_dusk_stone_ore",
                 "cobblemon:fire_stone_ore",
                 "cobblemon:deepslate_fire_stone_ore",
                 "cobblemon:nether_fire_stone_ore",
                 "cobblemon:ice_stone_ore",
                 "cobblemon:deepslate_ice_stone_ore",
                 "cobblemon:leaf_stone_ore",
                 "cobblemon:deepslate_leaf_stone_ore",
                 "cobblemon:moon_stone_ore",
                 "cobblemon:deepslate_moon_stone_ore",
                 "cobblemon:dripstone_moon_stone_ore",
                 "cobblemon:shiny_stone_ore",
                 "cobblemon:deepslate_shiny_stone_ore",
                 "cobblemon:sun_stone_ore",
                 "cobblemon:deepslate_sun_stone_ore",
                 "cobblemon:terracotta_sun_stone_ore",
                 "cobblemon:thunder_stone_ore",
                 "cobblemon:deepslate_thunder_stone_ore",
                 "cobblemon:water_stone_ore",
                 "cobblemon:deepslate_water_stone_ore" -> true;
            default -> false;
        };
    }

    private String getDirectionText(BlockPos origin, BlockPos target) {
        int dx = target.getX() - origin.getX();
        int dy = target.getY() - origin.getY();
        int dz = target.getZ() - origin.getZ();

        String vertical = "";
        if (dy > 2) {
            vertical = "upward ";
        } else if (dy < -2) {
            vertical = "downward ";
        }

        String horizontal;
        if (Math.abs(dx) >= Math.abs(dz) * 2) {
            horizontal = dx > 0 ? "east" : "west";
        } else if (Math.abs(dz) >= Math.abs(dx) * 2) {
            horizontal = dz > 0 ? "south" : "north";
        } else {
            String northSouth = dz > 0 ? "south" : "north";
            String eastWest = dx > 0 ? "east" : "west";
            horizontal = northSouth + "-" + eastWest;
        }
        return vertical + horizontal;
    }

    private String formatBlockName(String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return "Unknown Treasure";
        }
        String name = blockId.contains(":") ? blockId.substring(blockId.indexOf(":") + 1) : blockId;
        return formatWords(name);
    }

    private String formatWords(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] parts = value.replace("_", " ").replace("-", " ").trim().toLowerCase().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
            builder.append(" ");
        }
        return builder.toString().trim();
    }

    private static class FoundVein {
        String blockId;
        BlockPos nearestPos;
        double distance;
        int count;

        FoundVein(String blockId, BlockPos nearestPos, double distance, int count) {
            this.blockId = blockId;
            this.nearestPos = nearestPos;
            this.distance = distance;
            this.count = count;
        }
    }
}
