package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionChunkConfig;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionFragmentConfig;
import com.champutils.profession.ProfessionFragmentManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class ProfessionCurrencyInventoryMenu {
    private static final java.util.Map<java.util.UUID, PendingChunkSale> PENDING_CHUNK_SALES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long PENDING_CONFIRM_MS = 10_000L;

    private ProfessionCurrencyInventoryMenu() {}

    public static void openChunks(ServerPlayer player) {
        // Opening the chunk menu fresh should never inherit an old bulk-sell confirmation.
        // Confirmations are only shown after the player shift-clicks a chunk in the currently-open menu.
        PENDING_CHUNK_SALES.remove(player.getUUID());
        openChunks(player, null);
    }

    private static void openChunks(ServerPlayer player, String confirmingChunk) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Chunk Inventory"));

        gui.setSlot(4, new GuiElementBuilder(Items.CHEST)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Chunk Inventory"))
                .addLoreLine(Component.literal("§7Digital chunks are saved per profile."))
                .addLoreLine(Component.literal("§7Hover each icon to view your balance.")));

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = 0;
        for (String chunk : ProfessionChunkConfig.CONFIG.chunks.keySet()) {
            if (index >= slots.length) break;
            ProfessionChunkConfig.ChunkData data = ProfessionChunkConfig.CONFIG.chunks.get(chunk);
            int amount = ProfessionChunkManager.count(player, chunk);
            double sellCredits = data == null ? 0.0D : Math.max(0.0D, data.sellCredits);
            String fragment = data == null ? "F" : ProfessionFragmentConfig.normalizeRarity(data.fragmentRarity);
            int chunksPer = data == null ? 1 : Math.max(1, data.chunksPerFragment);
            int fragmentsPer = data == null ? 1 : Math.max(1, data.fragmentsPerTrade);

            boolean confirming = ProfessionChunkManager.normalizeChunk(chunk).equals(ProfessionChunkManager.normalizeChunk(confirmingChunk));
            GuiElementBuilder builder = new GuiElementBuilder(iconForChunk(chunk))
                    .hideDefaultTooltip()
                    .setName(Component.literal("§e" + (data == null ? ProfessionChunkManager.formatChunk(chunk) : data.displayName)))
                    .addLoreLine(Component.literal("§7Balance: §6" + amount))
                    .addLoreLine(Component.literal("§7Sell Value: §a" + sellCredits + " Credits each"))
                    .addLoreLine(Component.literal("§7Foreman Trade: §b" + chunksPer + " chunk" + (chunksPer == 1 ? "" : "s") + " → " + fragmentsPer + " " + ProfessionFragmentManager.displayRankName(fragment) + " Essence" + (fragmentsPer == 1 ? "" : "s")))
                    .addLoreLine(Component.literal(amount > 0 ? "§eLeft Click: sell 1 chunk." : "§8No chunks to sell."));
            if (confirming) {
                builder.addLoreLine(Component.literal("§cConfirm in the UI to continue."))
                        .addLoreLine(Component.literal("§7This will sell up to a full stack of this chunk."));
            } else {
                builder.addLoreLine(Component.literal(amount > 0 ? "§eShift Click: review selling up to 64." : "§8Shift Click sells up to 64 when you have chunks."));
            }
            builder.addLoreLine(Component.literal("§8Stored on your active profile."))
                    .setCallback((slot, click, type) -> {
                        if (type == ClickType.QUICK_MOVE) {
                            handleShiftChunkSale(player, chunk);
                        } else {
                            sellChunks(player, chunk, 1);
                            openChunks(player);
                        }
                    });
            gui.setSlot(slots[index++], builder);
        }

        gui.setSlot(22, new GuiElementBuilder(Items.EMERALD)
                .hideDefaultTooltip()
                .setName(Component.literal("§aOpen Profession Foreman"))
                .addLoreLine(Component.literal("§7Sell chunks or trade them for essence."))
                .addLoreLine(Component.literal("§eClick to open"))
                .setCallback((i, c, t) -> ProfessionForemanMenu.open(player)));

        gui.open();
    }

    private static void handleShiftChunkSale(ServerPlayer player, String chunk) {
        String normalized = ProfessionChunkManager.normalizeChunk(chunk);
        int amount = Math.min(64, ProfessionChunkManager.count(player, normalized));
        if (amount <= 0) {
            player.sendSystemMessage(Component.literal("§cYou do not have any " + ProfessionChunkManager.formatChunk(normalized) + " to sell."));
            openChunks(player);
            return;
        }
        long value = (long) amount * ProfessionChunkManager.valueCents(normalized);
        ConfirmationMenu.open(
                player,
                "Confirm Chunk Sale",
                iconForChunk(normalized),
                "§eSell " + ProfessionChunkManager.formatChunk(normalized),
                new String[]{
                        "§7Amount: §f" + amount,
                        "§7Total: §6" + EconomyManager.format(value),
                        "§cThis cannot be undone."
                },
                () -> {
                    sellChunks(player, normalized, 64);
                    openChunks(player);
                },
                () -> openChunks(player)
        );
    }

    private static void sellChunks(ServerPlayer player, String chunk, int amount) {
        ProfessionChunkManager.SellResult result = ProfessionChunkManager.sell(player, chunk, amount);
        if (!result.success()) {
            player.sendSystemMessage(Component.literal("§c" + result.error()));
            return;
        }
        player.sendSystemMessage(Component.literal("§aSold §6" + result.sold() + "x " + ProfessionChunkManager.formatChunk(result.chunk()) + " §afor §6" + EconomyManager.format(result.cents()) + "§a."));
    }

    public static void openFragments(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x3, player, false);
        gui.setLockPlayerInventory(true);
        gui.setTitle(Component.literal("Essence Inventory"));

        gui.setSlot(4, new GuiElementBuilder(Items.AMETHYST_SHARD)
                .hideDefaultTooltip()
                .setName(Component.literal("§dEssence Inventory"))
                .addLoreLine(Component.literal("§7Digital essence are saved per profile."))
                .addLoreLine(Component.literal("§7Hover each icon to view your balance.")));

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = 0;
        for (String fragment : ProfessionFragmentConfig.FRAGMENTS.keySet()) {
            if (index >= slots.length) break;
            String normalized = ProfessionFragmentConfig.normalizeRarity(fragment);
            ProfessionFragmentConfig.FragmentData data = ProfessionFragmentConfig.FRAGMENTS.get(normalized);
            int amount = ProfessionFragmentManager.countFragments(player, normalized);
            Item icon = fragmentIcon(normalized);
            String display = data == null || data.displayName == null || data.displayName.isBlank()
                    ? ProfessionFragmentManager.displayRankName(normalized) + " Essence"
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
                .setName(Component.literal("§aOpen Essence Crafting"))
                .addLoreLine(Component.literal("§7Craft, upgrade, downgrade, or withdraw essence."))
                .addLoreLine(Component.literal("§eClick to open"))
                .setCallback((i, c, t) -> FragmentCraftingMenu.open(player)));

        gui.open();
    }

    private static Item iconForChunk(String chunk) {
        return switch (ProfessionChunkManager.normalizeChunk(chunk)) {
            case "COPPER" -> Items.COPPER_INGOT;
            case "IRON" -> Items.IRON_INGOT;
            case "GOLD" -> Items.GOLD_INGOT;
            case "EMERALD" -> Items.EMERALD;
            case "DIAMOND" -> Items.DIAMOND;
            case "NETHERITE" -> Items.NETHERITE_INGOT;
            default -> Items.COBBLESTONE;
        };
    }

    private record PendingChunkSale(String chunk, long expiresAt) {}

    private static Item fragmentIcon(String fragment) {
        return switch (ProfessionFragmentConfig.normalizeRarity(fragment)) {
            case "E" -> Items.COPPER_INGOT;
            case "D" -> Items.IRON_INGOT;
            case "C" -> Items.GOLD_INGOT;
            case "B" -> Items.AMETHYST_SHARD;
            case "A" -> Items.DIAMOND;
            case "S" -> Items.NETHERITE_INGOT;
            default -> Items.AMETHYST_SHARD;
        };
    }
}
