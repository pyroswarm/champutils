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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class ExplorationLootManager {
    private ExplorationLootManager() {}

    public static void open(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return;
        if (!ExplorationLootConfig.get().enabled || !ExplorationLootConfig.get().virtualPerPlayerLoot) return;

        ExplorationLootState.markDiscovered(level, pos);

        if (!isInstancedLootContainer(level, pos)) {
            return;
        }

        if (ExplorationLootState.hasClaimed(player.getUUID(), level, pos)) {
            player.sendSystemMessage(Component.literal("You have already claimed this exploration loot.").withStyle(ChatFormatting.YELLOW));
            return;
        }

        List<ItemStack> rewards = rollRewards(level, pos, player.getUUID().getMostSignificantBits() ^ player.getUUID().getLeastSignificantBits());
        if (rewards.isEmpty()) {
            player.sendSystemMessage(Component.literal("This loot table had no valid rewards. Check exploration_loot.json item ids.").withStyle(ChatFormatting.RED));
            return;
        }

        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Exploration Loot"));
        gui.setLockPlayerInventory(true);

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setSlot(i, filler());
        }

        int slot = 10;
        for (ItemStack reward : rewards) {
            if (slot >= 44) break;
            if (slot % 9 == 8) slot += 2;
            gui.setSlot(slot, new GuiElementBuilder(reward.copy())
                    .addLoreLine(Component.literal("Click Claim All to receive this loot.").withStyle(ChatFormatting.GRAY)));
            slot++;
        }

        gui.setSlot(49, new GuiElementBuilder(Items.EMERALD_BLOCK)
                .setName(Component.literal("Claim All").withStyle(ChatFormatting.GREEN))
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

        for (int i = 0; i < rolls; i++) {
            ExplorationLootConfig.LootEntry entry = weighted(valid, random);
            if (entry == null) continue;
            ItemStack stack = toStack(entry, random);
            if (!stack.isEmpty()) rewards.add(stack);
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
            if (ExplorationLootConfig.get().skipUnknownItems && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(entry.itemId))) continue;
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

    private static ItemStack toStack(ExplorationLootConfig.LootEntry entry, Random random) {
        try {
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

        for (String exact : ExplorationLootConfig.get().lootContainerBlockIds) {
            if (exact != null && id.equalsIgnoreCase(exact.trim())) return true;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (String contains : ExplorationLootConfig.get().lootContainerIdContains) {
            if (contains != null && !contains.isBlank() && lower.contains(contains.toLowerCase(Locale.ROOT))) return true;
        }

        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof net.minecraft.world.Container
                && (lower.contains("chest") || lower.contains("barrel") || lower.contains("loot"));
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
