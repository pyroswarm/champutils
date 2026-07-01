package com.champutils.menu;

import com.champutils.profession.ProfessionDataManager;
import com.champutils.profession.ProfessionManager;
import com.champutils.profession.ProfessionSubLevelManager;
import com.champutils.profession.ProfessionType;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;

public final class ProfessionsMenu {

    private ProfessionsMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Professions"));
        MenuUtil.fillBorders(gui, 10, 12, 14, 16, 22, 4);

        MenuUtil.addInfoCard(
                gui,
                4,
                Items.BOOK,
                "§6Profession Overview",
                "§7Click a profession to see levels, XP,",
                "§7chunk odds, rarity bonuses, and sublevels.",
                "§8Sublevels are crop, wood, ore, and type masteries."
        );

        setProfessionButton(gui, player, 10, ProfessionType.MINING, Items.DIAMOND_PICKAXE, "§bMining");
        setProfessionButton(gui, player, 12, ProfessionType.FORESTRY, Items.DIAMOND_AXE, "§aForestry");
        setProfessionButton(gui, player, 14, ProfessionType.FARMING, Items.DIAMOND_HOE, "§eFarming");
        setProfessionButton(gui, player, 16, ProfessionType.BATTLING, Items.DIAMOND_SWORD, "§cBattling");

        MenuUtil.addBackButton(gui, 22, () -> MainMenu.open(player));
        gui.open();
    }

    private static void setProfessionButton(SimpleGui gui, ServerPlayer player, int slot, ProfessionType profession, Item icon, String name) {
        int level = ProfessionManager.getLevel(player, profession);
        int xp = ProfessionManager.getXp(player, profession);
        int next = ProfessionManager.xpRequired(level);
        double chunkFind = ProfessionSubLevelManager.chunkFindChanceBonus(player, profession) * 100.0D;
        double rarity = ProfessionSubLevelManager.chunkRarityChanceBonus(player, profession) * 100.0D;
        int mastered = ProfessionSubLevelManager.countMasteredSublevels(ProfessionManager.getData(player), profession);
        Map<String, Integer> counts = ProfessionSubLevelManager.sublevelCountsByCategory(player, profession);

        GuiElementBuilder builder = new GuiElementBuilder(icon)
                .hideDefaultTooltip()
                .setName(Component.literal(name + " §7Lv. " + level))
                .addLoreLine(Component.literal("§7XP: §f" + xp + "§7/§f" + next))
                .addLoreLine(Component.literal("§7Chunk chance bonus: §a+" + pct(chunkFind)))
                .addLoreLine(Component.literal("§7Chunk rarity bonus: §d+" + pct(rarity)))
                .addLoreLine(Component.literal("§7Mastered sublevels: §6" + mastered + " §8(+10% sublevel XP each)"));

        if (counts.isEmpty()) {
            builder.addLoreLine(Component.literal("§8No sublevels discovered yet."));
        } else {
            for (var entry : counts.entrySet()) {
                builder.addLoreLine(Component.literal("§8" + entry.getKey() + ": " + entry.getValue()));
            }
        }

        builder.addLoreLine(Component.literal("§eClick to view sublevels"));
        builder.setCallback((i, c, t) -> openSublevels(player, profession, 0));
        gui.setSlot(slot, builder);
    }

    public static void openSublevels(ServerPlayer player, ProfessionType profession, int page) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(prettyProfession(profession) + " Sublevels"));
        MenuUtil.fillBorders(gui, 45, 49, 53);

        List<Map.Entry<String, ProfessionDataManager.ProfessionData.SubLevelData>> entries = ProfessionSubLevelManager.sublevels(player, profession);
        int safePage = Math.max(0, page);
        int start = safePage * 45;
        int end = Math.min(entries.size(), start + 45);

        if (entries.isEmpty()) {
            MenuUtil.addInfoCard(gui, 22, Items.PAPER, "§7No sublevels yet", "§7Use this profession to discover its", "§7crop, wood, ore, or type sublevels.");
        } else {
            for (int idx = start; idx < end; idx++) {
                Map.Entry<String, ProfessionDataManager.ProfessionData.SubLevelData> entry = entries.get(idx);
                ProfessionDataManager.ProfessionData.SubLevelData data = entry.getValue();
                int slot = idx - start;
                int level = Math.max(1, Math.min(100, data.level));
                int next = ProfessionSubLevelManager.xpRequired(level);
                GuiElementBuilder builder = new GuiElementBuilder(iconFor(entry.getKey()))
                        .hideDefaultTooltip()
                        .setName(Component.literal("§e" + ProfessionSubLevelManager.displayName(entry.getKey()) + " §7Lv. " + level))
                        .addLoreLine(Component.literal("§7Type: §f" + ProfessionSubLevelManager.categoryName(entry.getKey())))
                        .addLoreLine(Component.literal(level >= 100 ? "§6Mastered" : "§7XP: §f" + data.xp + "§7/§f" + next))
                        .addLoreLine(Component.literal("§7Actions: §f" + data.actions))
                        .addLoreLine(Component.literal("§8Each level gives +0.025% chunk find chance."))
                        .addLoreLine(Component.literal("§8Every 10 levels gives +0.25% rarity bias."));
                gui.setSlot(slot, builder);
            }
        }

        if (safePage > 0) {
            gui.setSlot(45, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§ePrevious Page")).setCallback((i, c, t) -> openSublevels(player, profession, safePage - 1)));
        }
        MenuUtil.addBackButton(gui, 49, () -> open(player));
        if (end < entries.size()) {
            gui.setSlot(53, new GuiElementBuilder(Items.ARROW).hideDefaultTooltip().setName(Component.literal("§eNext Page")).setCallback((i, c, t) -> openSublevels(player, profession, safePage + 1)));
        }
        gui.open();
    }

    private static Item iconFor(String key) {
        String category = ProfessionSubLevelManager.categoryName(key);
        if ("Crop".equals(category)) return Items.WHEAT;
        if ("Wood".equals(category)) return Items.OAK_LOG;
        if ("Ore".equals(category)) return Items.RAW_IRON;
        if ("Type Slayer".equals(category)) return Items.DIAMOND_SWORD;
        return Items.PAPER;
    }

    private static String prettyProfession(ProfessionType profession) {
        String lower = profession.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String pct(double value) {
        return String.format(java.util.Locale.US, "%.2f%%", value);
    }
}
