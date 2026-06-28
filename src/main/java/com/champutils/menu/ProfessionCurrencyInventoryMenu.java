package com.champutils.menu;

import com.champutils.profession.ProfessionChunkConfig;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ProfessionCurrencyInventoryMenu {
    private ProfessionCurrencyInventoryMenu() {}

    public static void openChunks(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Chunk Inventory"));

        gui.setSlot(4, new GuiElementBuilder(Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Chunk Inventory"))
                .addLoreLine(Component.literal("§7Digital chunks are saved per profile."))
                .addLoreLine(Component.literal("§7Hover each icon to view your balance.")));

        int[] slots = {10, 11, 12, 13, 14, 15};
        int index = 0;
        for (String chunk : ProfessionChunkConfig.CONFIG.chunks.keySet()) {
            if (index >= slots.length) break;
            ProfessionChunkConfig.ChunkData data = ProfessionChunkConfig.CONFIG.chunks.get(chunk);
            int amount = ProfessionChunkManager.count(player, chunk);
            double sellCredits = data == null ? 0.0D : Math.max(0.0D, data.sellCredits);
            String fragment = data == null ? "COMMON" : ProfessionFragmentConfig.normalizeRarity(data.fragmentRarity);
            int chunksPer = data == null ? 1 : Math.max(1, data.chunksPerFragment);
            int fragmentsPer = data == null ? 1 : Math.max(1, data.fragmentsPerTrade);

            gui.setSlot(slots[index++], new GuiElementBuilder(iconForChunk(chunk))
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + (data == null ? ProfessionChunkManager.formatChunk(chunk) : data.displayName)))
                    .addLoreLine(Component.literal("§7Balance: §6" + amount))
                    .addLoreLine(Component.literal("§7Sell Value: §a" + sellCredits + " Credits each"))
                    .addLoreLine(Component.literal("§7Foreman Trade: §b" + chunksPer + " chunk" + (chunksPer == 1 ? "" : "s") + " → " + fragmentsPer + " " + ProfessionFragmentManager.formatWords(fragment) + " Fragment" + (fragmentsPer == 1 ? "" : "s")))
                    .addLoreLine(Component.literal("§8Stored on your active profile.")));
        }

        gui.setSlot(22, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aOpen Profession Foreman"))
                .addLoreLine(Component.literal("§7Sell chunks or trade them for fragments."))
                .addLoreLine(Component.literal("§eClick to open"))
                .setCallback((i, c, t) -> ProfessionForemanMenu.open(player)));

        gui.open();
    }

    public static void openFragments(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Fragment Inventory"));

        gui.setSlot(4, new GuiElementBuilder(Items.AMETHYST_SHARD)
                .hideDefaultTooltip()
                .setName(Component.literal("§dFragment Inventory"))
                .addLoreLine(Component.literal("§7Digital fragments are saved per profile."))
                .addLoreLine(Component.literal("§7Hover each icon to view your balance.")));

        int[] slots = {10, 11, 12, 13, 14, 15};
        int index = 0;
        for (String fragment : ProfessionFragmentConfig.FRAGMENTS.keySet()) {
            if (index >= slots.length) break;
            String normalized = ProfessionFragmentConfig.normalizeRarity(fragment);
            ProfessionFragmentConfig.FragmentData data = ProfessionFragmentConfig.FRAGMENTS.get(normalized);
            int amount = ProfessionFragmentManager.countFragments(player, normalized);
            Item icon = fragmentIcon(normalized);
            String display = data == null || data.displayName == null || data.displayName.isBlank()
                    ? ProfessionFragmentManager.formatWords(normalized) + " Fragment"
                    : data.displayName;

            gui.setSlot(slots[index++], new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§d" + display))
                    .addLoreLine(Component.literal("§7Balance: §6" + amount))
                    .addLoreLine(Component.literal("§7Used for tool crafting, upgrades,"))
                    .addLoreLine(Component.literal("§7crate crafting, and other recipes."))
                    .addLoreLine(Component.literal("§8Stored on your active profile.")));
        }

        gui.setSlot(22, new GuiElementBuilder(Items.CRAFTING_TABLE)
                .hideDefaultTooltip()
                .setName(Component.literal("§aOpen Fragment Crafting"))
                .addLoreLine(Component.literal("§7Craft, upgrade, downgrade, or withdraw fragments."))
                .addLoreLine(Component.literal("§eClick to open"))
                .setCallback((i, c, t) -> FragmentCraftingMenu.open(player)));

        gui.open();
    }

    private static Item iconForChunk(String chunk) {
        return switch (ProfessionChunkManager.normalizeChunk(chunk)) {
            case "COPPER" -> Items.COPPER_INGOT;
            case "IRON" -> Items.IRON_INGOT;
            case "GOLD" -> Items.GOLD_INGOT;
            case "DIAMOND" -> Items.DIAMOND;
            case "NETHERITE" -> Items.NETHERITE_INGOT;
            default -> Items.COBBLESTONE;
        };
    }

    private static Item fragmentIcon(String fragment) {
        return switch (ProfessionFragmentConfig.normalizeRarity(fragment)) {
            case "UNCOMMON" -> Items.COPPER_INGOT;
            case "RARE" -> Items.IRON_INGOT;
            case "EPIC" -> Items.GOLD_INGOT;
            case "LEGENDARY" -> Items.DIAMOND;
            case "MYTHIC" -> Items.NETHERITE_INGOT;
            default -> Items.AMETHYST_SHARD;
        };
    }
}
