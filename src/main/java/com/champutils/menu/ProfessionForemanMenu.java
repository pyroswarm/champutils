package com.champutils.menu;

import com.champutils.economy.EconomyManager;
import com.champutils.profession.ProfessionChunkConfig;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionFragmentManager;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class ProfessionForemanMenu {
    private ProfessionForemanMenu() {}

    public static void open(ServerPlayer player) {
        open(player, false);
    }

    private static void open(ServerPlayer player, boolean confirmingSellAll) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Profession Foreman"));
        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43, 49);

        gui.setSlot(4, new GuiElementBuilder(Items.EMERALD).hideDefaultTooltip()
                .setName(Component.literal("§aProfession Foreman"))
                .addLoreLine(Component.literal("§7Sell chunks, trade chunks, or"))
                .addLoreLine(Component.literal("§7exchange backpack materials.")));

        long sellAllPreview = ProfessionChunkManager.sellAllValueCents(player);
        if (confirmingSellAll) {
            gui.setSlot(20, new GuiElementBuilder(Items.GREEN_STAINED_GLASS_PANE).hideDefaultTooltip()
                    .setName(Component.literal("§aConfirm Sell All Chunks"))
                    .addLoreLine(Component.literal("§7This will sell every stored chunk."))
                    .addLoreLine(Component.literal("§7Total: §6" + EconomyManager.format(sellAllPreview)))
                    .addLoreLine(Component.literal("§cThis cannot be undone."))
                    .addLoreLine(Component.literal("§eClick to confirm."))
                    .setCallback((i,c,t) -> {
                        long cents = ProfessionChunkManager.sellAll(player);
                        if (cents <= 0L) player.sendSystemMessage(Component.literal("§cYou do not have any sellable chunks."));
                        else player.sendSystemMessage(Component.literal("§aSold all chunks for §6" + EconomyManager.format(cents) + "§a."));
                        open(player);
                    }));
        } else {
            gui.setSlot(20, new GuiElementBuilder(Items.GOLD_INGOT).hideDefaultTooltip()
                    .setName(Component.literal("§6Sell All Chunks"))
                    .addLoreLine(Component.literal("§7Converts all stored chunks into Credits."))
                    .addLoreLine(Component.literal("§7Total: §6" + EconomyManager.format(sellAllPreview)))
                    .addLoreLine(Component.literal("§eClick to review."))
                    .setCallback((i,c,t) -> ConfirmationMenu.open(
                            player,
                            "Confirm Sell All Chunks",
                            Items.GOLD_INGOT,
                            "§eSell All Chunks",
                            new String[]{
                                    "§7This will sell every stored chunk.",
                                    "§7Total: §6" + EconomyManager.format(sellAllPreview),
                                    "§cThis cannot be undone."
                            },
                            () -> {
                                long cents = ProfessionChunkManager.sellAll(player);
                                if (cents <= 0L) player.sendSystemMessage(Component.literal("§cYou do not have any sellable chunks."));
                                else player.sendSystemMessage(Component.literal("§aSold all chunks for §6" + EconomyManager.format(cents) + "§a."));
                                open(player);
                            },
                            () -> open(player)
                    )));
        }

        gui.setSlot(22, new GuiElementBuilder(Items.AMETHYST_SHARD).hideDefaultTooltip()
                .setName(Component.literal("§dTrade Chunks for Essence"))
                .addLoreLine(Component.literal("§7Use chunk currencies for rank essences."))
                .addLoreLine(Component.literal("§eClick to choose a chunk tier."))
                .setCallback((i,c,t) -> openTrade(player)));

        gui.setSlot(24, new GuiElementBuilder(Items.EXPERIENCE_BOTTLE).hideDefaultTooltip()
                .setName(Component.literal("§dProfession Trade"))
                .addLoreLine(Component.literal("§7Trade backpack materials for rewards."))
                .addLoreLine(Component.literal("§7Default: high-cost Rare Candy trades."))
                .addLoreLine(Component.literal("§eClick to open."))
                .setCallback((i,c,t) -> ProfessionTradeMenu.open(player)));

        gui.setSlot(26, new GuiElementBuilder(Items.CRAFTING_TABLE).hideDefaultTooltip()
                .setName(Component.literal("§6Champ Crafting"))
                .addLoreLine(Component.literal("§7Craft rare Genesis and competitive items."))
                .addLoreLine(Component.literal("§7Uses huge profession backpack costs."))
                .addLoreLine(Component.literal("§eClick to open."))
                .setCallback((i,c,t) -> ChampCraftingMenu.open(player, ProfessionForemanMenu::open)));

        int slot = 28;
        for (String chunk : ProfessionChunkConfig.CONFIG.chunks.keySet()) {
            int amount = ProfessionChunkManager.count(player, chunk);
            gui.setSlot(slot++, new GuiElementBuilder(icon(chunk)).hideDefaultTooltip()
                    .setName(Component.literal(ProfessionChunkManager.formatChunk(chunk)).withStyle(ProfessionChunkManager.color(chunk)))
                    .addLoreLine(Component.literal("§7Stored: §e" + amount))
                    .addLoreLine(Component.literal("§7Sell Value Each: §6" + EconomyManager.format(ProfessionChunkManager.valueCents(chunk))))
                    .addLoreLine(Component.literal("§7Total Value: §6" + EconomyManager.format((long) amount * ProfessionChunkManager.valueCents(chunk)))));
            if (slot == 35) break;
        }

        gui.open();
    }

    private static void openTrade(ServerPlayer player) {
        SimpleGui gui = new SimpleGui(MenuType.GENERIC_9x6, player, false);
        gui.setTitle(Component.literal("Chunk Essence Trades"));
        MenuUtil.fillBorders(gui, 4, 10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43, 49);
        List<String> chunks = new ArrayList<>(ProfessionChunkConfig.CONFIG.chunks.keySet());
        int[] slots = {19,20,21,22,23,24,25};
        for (int idx = 0; idx < chunks.size() && idx < slots.length; idx++) {
            String chunk = chunks.get(idx);
            ProfessionChunkConfig.ChunkData data = ProfessionChunkConfig.CONFIG.chunks.get(chunk);
            int have = ProfessionChunkManager.count(player, chunk);
            int cost = data == null ? 1 : Math.max(1, data.chunksPerFragment);
            int output = data == null ? 1 : Math.max(1, data.fragmentsPerTrade);
            String rarity = data == null ? "F" : data.fragmentRarity;
            int maxTrades = cost <= 0 ? 0 : have / cost;
            gui.setSlot(slots[idx], new GuiElementBuilder(icon(chunk)).hideDefaultTooltip()
                    .setName(Component.literal(ProfessionChunkManager.formatChunk(chunk) + " → " + ProfessionFragmentManager.displayRankName(rarity) + " Essence").withStyle(ProfessionChunkManager.color(chunk)))
                    .addLoreLine(Component.literal("§7Cost: §6" + cost + "x " + ProfessionChunkManager.formatChunk(chunk)))
                    .addLoreLine(Component.literal("§7Output: §a" + output + "x " + ProfessionFragmentManager.displayRankName(rarity) + " Essence"))
                    .addLoreLine(Component.literal("§7You have: §e" + have))
                    .addLoreLine(Component.literal("§7Max right now: §e" + maxTrades + " trades"))
                    .addLoreLine(Component.literal(have >= cost ? "§eLeft Click: trade once" : "§cNot enough chunks"))
                    .addLoreLine(Component.literal(have >= cost ? "§eShift Click: trade up to 64 times" : "§8Shift Click trades your maximum when possible"))
                    .setCallback((i,c,t) -> {
                        int requestedTrades = t == ClickType.QUICK_MOVE ? 64 : 1;
                        ProfessionChunkManager.TradeResult result = ProfessionChunkManager.tradeChunkForFragments(player, chunk, requestedTrades);
                        if (!result.success()) player.sendSystemMessage(Component.literal("§c" + result.error()));
                        else player.sendSystemMessage(Component.literal("§aTraded chunks for §6" + result.fragments() + "x " + ProfessionFragmentManager.displayRankName(result.rarity()) + " Essence§a."));
                        openTrade(player);
                    }));
        }
        MenuUtil.addBackButton(gui, 49, () -> open(player));
        gui.open();
    }

    private static net.minecraft.world.item.Item icon(String chunk) {
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
}
