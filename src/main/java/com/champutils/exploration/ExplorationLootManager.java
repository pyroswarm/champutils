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

        List<ItemStack> rewards = rollRewards(level, pos, player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits());
        if (rewards.isEmpty()) {
            player.sendSystemMessage(Component.literal("This loot table had no valid rewards. Check exploration_loot.json item ids.").withStyle(ChatFormatting.RED));
            return;
        }

        String chestRarity = chestRarity(level, pos);
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
                    player.sendSystemMessage(Component.literal("Claimed exploration loot!").withStyle(ChatFormatting.GREEN));
                    gui.close();
                }));

        gui.open();
    }

    private static List<ItemStack> rollRewards(ServerLevel level, BlockPos pos, long playerSalt) {
        String tableId = tableId(level, pos);
        ExplorationLootConfig.LootTable table = ExplorationLootConfig.get().tables.get(tableId);
        if (table == null) table = ExplorationLootConfig.get().tables.get("overworld");
        if (table == null) return List.of();

        long seed = 31L * pos.asLong() + 17L * level.getSeed() + playerSalt;
        Random random = new Random(seed);
        int rolls = table.minRolls + random.nextInt(Math.max(1, table.maxRolls - table.minRolls + 1));
        List<ItemStack> rewards = new ArrayList<>();

        List<ExplorationLootConfig.LootEntry> valid = validEntries(table);
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

    private static List<ExplorationLootConfig.LootEntry> validEntries(ExplorationLootConfig.LootTable table) {
        int maxRarity = Math.min(ExplorationLootConfig.rarityRank(ExplorationLootConfig.get().maxRarity), ExplorationLootConfig.rarityRank("EPIC"));
        List<ExplorationLootConfig.LootEntry> valid = new ArrayList<>();
        for (ExplorationLootConfig.LootEntry entry : table.items) {
            if (entry == null || entry.itemId == null || entry.itemId.isBlank()) continue;
            if (entry.weight <= 0) continue;
            if (entry.rarityRank() > maxRarity) continue;
            if (isBanned(entry.itemId)) continue;
            if (ExplorationLootConfig.get().skipUnknownItems && !isSpecialReward(entry.itemId) && !isKnownItem(entry.itemId)) continue;
            valid.add(entry);
        }
        return valid;
    }

    private static boolean isBanned(String itemId) {
        String lower = itemId.toLowerCase(Locale.ROOT);
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

    private static String chestRarity(ServerLevel level, BlockPos pos) {
        String id = blockId(level, pos).toLowerCase(Locale.ROOT);
        if (id.contains("black_gilded_chest")) return "Mythic";
        if (id.contains("pink_gilded_chest")) return "Legendary";
        if (id.contains("blue_gilded_chest")) return "Epic";
        if (id.contains("green_gilded_chest")) return "Rare";
        if (id.contains("yellow_gilded_chest")) return "Uncommon";
        if (id.contains("white_gilded_chest")) return "Guild";
        if (id.contains("gilded_chest")) return "Common";

        String table = tableId(level, pos);
        if ("nether".equals(table)) return "Nether";
        if ("end".equals(table)) return "End";
        return "Common";
    }

    private static ChatFormatting rarityColor(String rarity) {
        if (rarity == null) return ChatFormatting.WHITE;
        return switch (rarity.toUpperCase(Locale.ROOT)) {
            case "UNCOMMON" -> ChatFormatting.YELLOW;
            case "RARE" -> ChatFormatting.GREEN;
            case "EPIC" -> ChatFormatting.BLUE;
            case "LEGENDARY" -> ChatFormatting.LIGHT_PURPLE;
            case "MYTHIC" -> ChatFormatting.DARK_PURPLE;
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
