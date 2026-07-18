package com.champutils.exploration;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.champutils.tm.TMManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

public final class ExplorationLootManager {
    private ExplorationLootManager() {}

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return;
        if (!ExplorationLootConfig.get().enabled || !ExplorationLootConfig.get().virtualPerPlayerLoot) return;

        if (!isInstancedLootContainer(level, pos)) {
            return;
        }

        ExplorationLootState.markDiscovered(level, pos);

        if (ExplorationLootState.hasClaimed(player.getUUID(), level, pos)) {
            player.sendSystemMessage(Component.literal("You have already claimed this exploration loot.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        int battlingLevel = Math.max(1, ProfessionManager.getBenefitLevel(player, ProfessionType.BATTLING));
        String rolledRank = rollChestRank(level, pos, battlingLevel, player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits());
        List<ItemStack> rewards = rollRewards(level, pos, rolledRank, player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits());
        if (rewards.isEmpty()) {
            player.sendSystemMessage(Component.literal("This loot table had no valid rewards. Check exploration_loot.json item ids.").withStyle(ChatFormatting.RED));
            return;
        }

        String chestRarity = rolledRank + " Rank";
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal(chestRarity + " Exploration Loot").withStyle(rarityColor(chestRarity)));
        gui.setLockPlayerInventory(true);

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, filler());
        }

        int slot = 10;
        for (ItemStack reward : rewards) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;
            gui.setSlot(slot, new GuiElementBuilder(reward.copy())
                    .addLoreLine(Component.literal("Chest Rarity: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(chestRarity).withStyle(rarityColor(chestRarity))))
                    .addLoreLine(Component.literal("Click Claim All to receive this loot.").withStyle(ChatFormatting.GRAY)));
            slot++;
        }

        gui.setSlot(49, new GuiElementBuilder(Items.EMERALD_BLOCK)
                .setName(Component.literal("Claim All").withStyle(ChatFormatting.GREEN))
                .addLoreLine(Component.literal("Chest Rarity: ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(chestRarity).withStyle(rarityColor(chestRarity))))
                .addLoreLine(Component.literal("One claim per player, per exploration chest.").withStyle(ChatFormatting.GRAY))
                .addLoreLine(Component.literal("Loot is generated separately for every player.").withStyle(ChatFormatting.YELLOW))
                .setCallback((index, type, action) -> {
                    if (ExplorationLootState.hasClaimed(player.getUUID(), level, pos)) {
                        player.sendSystemMessage(Component.literal("You have already claimed this exploration loot.").withStyle(ChatFormatting.YELLOW));
                        gui.close();
                        return;
                    }
                    for (ItemStack stack : rewards) {
                        giveOrDrop(player, stack.copy());
                    }
                    ExplorationLootState.markClaimed(player.getUUID(), level, pos);
                    announceClaim(player, rolledRank);
                    gui.close();
                }));

        gui.open();
    }

    private static List<ItemStack> rollRewards(ServerLevel level, BlockPos pos, String rolledRank, long playerSalt) {
        String tableId = tableId(level, pos);
        ExplorationLootConfig.LootTable table = ExplorationLootConfig.get().tables.get(tableId);
        if (table == null) table = ExplorationLootConfig.get().tables.get("overworld");
        if (table == null) return List.of();

        long seed = 31L * pos.asLong() + 17L * level.getSeed() + playerSalt;
        Random random = new Random(seed);
        int selectedRank = ExplorationLootConfig.rarityRank(rolledRank);
        int rolls = Math.max(6, table.minRolls + random.nextInt(Math.max(1, table.maxRolls - table.minRolls + 1)) + rollBonusForRank(selectedRank));
        List<ItemStack> rewards = new ArrayList<>();

        List<ExplorationLootConfig.LootEntry> valid = validEntries(table, selectedRank);
        if (valid.isEmpty()) return rewards;

        Set<String> alreadyRolled = new HashSet<>();
        for (int i = 0; i < rolls; i++) {
            List<ExplorationLootConfig.LootEntry> pool = valid.stream()
                    .filter(entry -> entry != null && entry.itemId != null && !alreadyRolled.contains(entry.itemId.toLowerCase(Locale.ROOT)))
                    .toList();
            if (pool.isEmpty()) pool = valid;
            ExplorationLootConfig.LootEntry entry = weighted(pool, random);
            if (entry == null) continue;
            ItemStack stack = toStack(entry, random);
            if (!stack.isEmpty()) {
                rewards.add(stack);
                alreadyRolled.add(entry.itemId.toLowerCase(Locale.ROOT));
            }
        }
        return rewards;
    }

    private static List<ExplorationLootConfig.LootEntry> validEntries(ExplorationLootConfig.LootTable table, int selectedRank) {
        List<ExplorationLootConfig.LootEntry> exact = new ArrayList<>();
        List<ExplorationLootConfig.LootEntry> fallback = new ArrayList<>();
        for (ExplorationLootConfig.LootEntry entry : table.items) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank() || entry.weight <= 0) continue;
            String lower = entry.itemId.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("cobblemon:") && !lower.startsWith("champutils:random_tm_")) continue;
            if (isBanned(entry.itemId)) continue;
            if (ExplorationLootConfig.get().skipUnknownItems && !isSpecialReward(entry.itemId) && !isKnownItem(entry.itemId)) continue;
            if (entry.rarityRank() == selectedRank) exact.add(entry);
            if (entry.rarityRank() <= selectedRank) fallback.add(entry);
        }
        return exact.size() >= 6 ? exact : fallback;
    }

    private static String rollChestRank(ServerLevel level, BlockPos pos, int battlingLevel, long playerSalt) {
        long seed = 97L * pos.asLong() + 31L * level.getSeed() + playerSalt;
        Random random = new Random(seed);
        int levelBonus = Math.max(0, Math.min(100, battlingLevel));
        double roll = random.nextDouble();
        double sChance = 0.00001D + (levelBonus * 0.0000049D); // 0.001% -> 0.05%
        double aChance = 0.00020D + (levelBonus * 0.000048D);  // 0.02% -> 0.50%
        double bChance = 0.004D + (levelBonus * 0.00016D);
        double cChance = 0.025D + (levelBonus * 0.00035D);
        double dChance = 0.090D + (levelBonus * 0.00055D);
        double eChance = 0.260D + (levelBonus * 0.00060D);
        if (roll < sChance) return "S";
        roll -= sChance;
        if (roll < aChance) return "A";
        roll -= aChance;
        if (roll < bChance) return "B";
        roll -= bChance;
        if (roll < cChance) return "C";
        roll -= cChance;
        if (roll < dChance) return "D";
        roll -= dChance;
        if (roll < eChance) return "E";
        return "F";
    }

    private static void announceClaim(ServerPlayer player, String rank) {
        Component local = Component.literal("You opened a " + rank + " Rank exploration chest!").withStyle(rarityColor(rank));
        player.sendSystemMessage(local);
        if (("A".equalsIgnoreCase(rank) || "S".equalsIgnoreCase(rank)) && player.getServer() != null) {
            Component global = Component.literal(player.getName().getString() + " discovered an ultra-rare " + rank + " Rank exploration chest!")
                    .withStyle(rarityColor(rank));
            player.getServer().getPlayerList().broadcastSystemMessage(global, false);
        }
    }

    private static boolean isBanned(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
        if (lower.startsWith("genesis:") || lower.startsWith("genesisforms:")) return true;
        for (String banned : ExplorationLootConfig.get().bannedItemContains) {
            if (banned != null && !banned.isBlank() && lower.contains(banned.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static ExplorationLootConfig.LootEntry weighted(List<ExplorationLootConfig.LootEntry> entries, Random random) {
        int total = 0;
        for (ExplorationLootConfig.LootEntry entry : entries) total += Math.max(0, entry.weight);
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (ExplorationLootConfig.LootEntry entry : entries) {
            roll -= Math.max(0, entry.weight);
            if (roll < 0) return entry;
        }
        return entries.get(entries.size() - 1);
    }

    private static boolean isKnownItem(String itemId) {
        try {
            return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemId));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ItemStack toStack(ExplorationLootConfig.LootEntry entry, Random random) {
        try {
            String tmRarity = randomTmRarity(entry.itemId);
            if (tmRarity != null) {
                ItemStack tm = TMManager.createRandomTMStack(tmRarity, 1);
                return tm == null ? ItemStack.EMPTY : tm;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.itemId));
            if (item == Items.AIR) return ItemStack.EMPTY;
            int min = Math.max(1, entry.minAmount);
            int max = Math.max(min, entry.maxAmount);
            int amount = min + random.nextInt(max - min + 1);
            return new ItemStack(item, amount);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean isSpecialReward(String itemId) {
        return randomTmRarity(itemId) != null;
    }

    private static String randomTmRarity(String itemId) {
        if (itemId == null) return null;
        String normalized = itemId.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("champutils:random_tm_")) return null;
        return normalized.substring("champutils:random_tm_".length()).toUpperCase(Locale.ROOT);
    }

    private static String tableId(ServerLevel level, BlockPos pos) {
        String blockId = blockId(level, pos);
        String override = ExplorationLootConfig.get().blockTableOverrides.get(blockId);
        if (override != null && !override.isBlank()) return override.toLowerCase(Locale.ROOT);

        ExplorationWorldManager.Entry entry = ExplorationWorldManager.find(level);
        if (entry != null && entry.worldType != null) return entry.worldType.toLowerCase(Locale.ROOT);
        String id = level.dimension().location().toString().toLowerCase(Locale.ROOT);
        if (id.contains("nether")) return "nether";
        if (id.contains("end")) return "end";
        return "overworld";
    }


    public static boolean isInstancedLootContainer(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        if (!ExplorationLootConfig.get().enabled || !ExplorationLootConfig.get().virtualPerPlayerLoot) return false;

        String id = blockId(level, pos);
        if (id.isBlank()) return false;
        String lower = id.toLowerCase(Locale.ROOT);

        if (!isConfiguredLootContainerId(lower)) return false;

        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof net.minecraft.world.Container)) return false;

        // Cobblemon gilded chests are generated loot containers even when they do not expose
        // the vanilla LootTable tag the same way vanilla chests/barrels do.
        if (matchesConfiguredContains(lower)) return true;

        try {
            var nbt = entity.saveWithFullMetadata(level.registryAccess());
            return nbt != null && nbt.contains("LootTable");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isConfiguredLootContainer(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        String id = blockId(level, pos);
        if (id.isBlank()) return false;
        return isConfiguredLootContainerId(id.toLowerCase(Locale.ROOT));
    }

    private static boolean isConfiguredLootContainerId(String lowerBlockId) {
        if (lowerBlockId == null || lowerBlockId.isBlank()) return false;
        ExplorationLootConfig.Data config = ExplorationLootConfig.get();
        if (config.lootContainerBlockIds != null) {
            for (String configured : config.lootContainerBlockIds) {
                if (configured != null && lowerBlockId.equals(configured.trim().toLowerCase(Locale.ROOT))) return true;
            }
        }
        return matchesConfiguredContains(lowerBlockId);
    }

    private static boolean matchesConfiguredContains(String lowerBlockId) {
        if (lowerBlockId == null || lowerBlockId.isBlank()) return false;
        ExplorationLootConfig.Data config = ExplorationLootConfig.get();
        if (config.lootContainerIdContains == null) return false;
        for (String contains : config.lootContainerIdContains) {
            if (contains != null && !contains.isBlank() && lowerBlockId.contains(contains.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static int chestMaxRarityRank(ServerLevel level, BlockPos pos) {
        String id = blockId(level, pos).toLowerCase(Locale.ROOT);
        if (id.contains("black_gilded_chest")) return ExplorationLootConfig.rarityRank("S");
        if (id.contains("white_gilded_chest")) return ExplorationLootConfig.rarityRank("A");
        if (id.contains("pink_gilded_chest")) return ExplorationLootConfig.rarityRank("B");
        if (id.contains("blue_gilded_chest")) return ExplorationLootConfig.rarityRank("C");
        if (id.contains("green_gilded_chest")) return ExplorationLootConfig.rarityRank("D");
        if (id.contains("yellow_gilded_chest")) return ExplorationLootConfig.rarityRank("E");
        if (id.contains("gilded_chest")) return ExplorationLootConfig.rarityRank("F");

        String table = tableId(level, pos);
        if ("end".equals(table)) return ExplorationLootConfig.rarityRank("B");
        if ("nether".equals(table)) return ExplorationLootConfig.rarityRank("C");
        return ExplorationLootConfig.rarityRank("F");
    }

    private static int rollBonusForRank(int rarityRank) {
        if (rarityRank >= ExplorationLootConfig.rarityRank("S")) return 3;
        if (rarityRank >= ExplorationLootConfig.rarityRank("A")) return 2;
        if (rarityRank >= ExplorationLootConfig.rarityRank("B")) return 2;
        if (rarityRank >= ExplorationLootConfig.rarityRank("C")) return 1;
        if (rarityRank >= ExplorationLootConfig.rarityRank("D")) return 1;
        return 0;
    }

    private static String chestRarity(ServerLevel level, BlockPos pos) {
        String id = blockId(level, pos).toLowerCase(Locale.ROOT);
        if (id.contains("black_gilded_chest")) return "S Rank";
        if (id.contains("white_gilded_chest")) return "A Rank";
        if (id.contains("pink_gilded_chest")) return "B Rank";
        if (id.contains("blue_gilded_chest")) return "C Rank";
        if (id.contains("green_gilded_chest")) return "D Rank";
        if (id.contains("yellow_gilded_chest")) return "E Rank";
        if (id.contains("gilded_chest")) return "F Rank";

        String table = tableId(level, pos);
        if ("nether".equals(table)) return "Nether";
        if ("end".equals(table)) return "End";
        return "F Rank";
    }

    private static ChatFormatting rarityColor(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        if (com.champutils.rarity.RarityScale.isRankLike(rarity)) return com.champutils.rarity.RarityScale.color(rarity);
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "GUILD" -> ChatFormatting.AQUA;
            case "NETHER" -> ChatFormatting.RED;
            case "END" -> ChatFormatting.DARK_AQUA;
            default -> ChatFormatting.WHITE;
        };
    }

    private static String blockId(ServerLevel level, BlockPos pos) {
        try {
            ResourceLocation key = level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location();
            return key.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        boolean inserted = player.getInventory().add(stack);
        if (!inserted || !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    private static GuiElementBuilder filler() {
        return new GuiElementBuilder(Items.BLACK_STAINED_GLASS_PANE).setName(Component.literal(" ")).hideTooltip();
    }
}
