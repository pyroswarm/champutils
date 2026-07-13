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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import com.champutils.profession.ProfessionChunkConfig;
import com.champutils.profession.ProfessionChunkManager;
import com.champutils.profession.ProfessionToolConfig;
import com.champutils.breeding.BreedingConfig;

import java.util.List;
import java.util.Map;

public final class ProfessionsMenu {

    private ProfessionsMenu() {}

    public static void open(ServerPlayer player) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x3, player);
        gui.setTitle(Component.literal("Professions"));
        MenuUtil.fillBorders(gui, 10, 12, 14, 16, 22, 26, 4);

        gui.setSlot(4, new GuiElementBuilder(Items.BOOK)
                .hideDefaultTooltip()
                .setName(Component.literal("§6Profession Details"))
                .addLoreLine(Component.literal("§7View rewards, passives,"))
                .addLoreLine(Component.literal("§7chunk odds, and tool scaling."))
                .addLoreLine(Component.literal("§eClick to open details"))
                .setCallback((i, c, t) -> openDetails(player, null)));

        setProfessionButton(gui, player, 10, ProfessionType.MINING, Items.DIAMOND_PICKAXE, "§bMining");
        setProfessionButton(gui, player, 12, ProfessionType.FORESTRY, Items.DIAMOND_AXE, "§aForestry");
        setProfessionButton(gui, player, 14, ProfessionType.FARMING, Items.DIAMOND_HOE, "§eFarming");
        setProfessionButton(gui, player, 16, ProfessionType.BATTLING, Items.DIAMOND_SWORD, "§cBattling");
        setProfessionButton(gui, player, 22, ProfessionType.BREEDING, Items.EGG, "§dBreeding");

        MenuUtil.addBackButton(gui, 26, () -> MainMenu.open(player));
        gui.open();
    }

    private static void setProfessionButton(SimpleGui gui, ServerPlayer player, int slot, ProfessionType profession, Item icon, String name) {
        int level = ProfessionManager.getLevel(player, profession);
        int xp = ProfessionManager.getXp(player, profession);
        int next = ProfessionManager.xpRequired(level);
        if (profession == ProfessionType.BREEDING) {
            BreedingConfig.Values cfg = BreedingConfig.get();
            GuiElementBuilder breeding = new GuiElementBuilder(icon)
                    .hideDefaultTooltip()
                    .setName(Component.literal(name + " §7Lv. " + level))
                    .addLoreLine(Component.literal("§7XP: §f" + xp + "§7/§f" + next))
                    .addLoreLine(Component.literal("§7Earn XP by hatching Eggs."))
                    .addLoreLine(Component.literal("§7Lv. 100 extra perfect IV: §a" + pct(cfg.breedingLevel100ExtraPerfectIvChancePercent)))
                    .addLoreLine(Component.literal("§7Lv. 100 Hidden Ability bonus: §d+" + pct(cfg.breedingLevel100HiddenAbilityBonusPercent)))
                    .addLoreLine(Component.literal("§7Lv. 100 shiny bonus: §e+" + pct(cfg.breedingLevel100ShinyRelativeBonusPercent) + " relative"))
                    .addLoreLine(Component.literal("§7Every hatch awards a chunk."))
                    .addLoreLine(Component.literal("§eClick for details"))
                    .setCallback((i, c, t) -> openDetails(player, profession));
            gui.setSlot(slot, breeding);
            return;
        }
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
                .addLoreLine(Component.literal("§7Mastered sublevels: §6" + mastered + " §8(+10% sublevel XP each)"))
                .addLoreLine(Component.literal("§7Chunk rolls: §f" + chunkRollSummary(profession)));

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
                        .addLoreLine(Component.literal("§8Each level gives +" + pct(ProfessionSubLevelManager.CHUNK_FIND_BONUS_PER_SUBLEVEL * 100.0D) + " chunk find chance."))
                        .addLoreLine(Component.literal("§8Every 10 levels gives +" + pct(ProfessionSubLevelManager.RARITY_BONUS_PER_TEN_LEVELS * 100.0D) + " rarity bias."));
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
        String raw = rawId(key);
        Item direct = item(raw);
        if (direct != Items.AIR) return direct;
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("birch")) return Items.BIRCH_LOG;
        if (lower.contains("spruce")) return Items.SPRUCE_LOG;
        if (lower.contains("jungle")) return Items.JUNGLE_LOG;
        if (lower.contains("acacia")) return Items.ACACIA_LOG;
        if (lower.contains("dark_oak")) return Items.DARK_OAK_LOG;
        if (lower.contains("mangrove")) return Items.MANGROVE_LOG;
        if (lower.contains("cherry")) return Items.CHERRY_LOG;
        if (lower.contains("crimson")) return Items.CRIMSON_STEM;
        if (lower.contains("warped")) return Items.WARPED_STEM;
        if (lower.contains("wheat")) return Items.WHEAT;
        if (lower.contains("carrot")) return Items.CARROT;
        if (lower.contains("potato")) return Items.POTATO;
        if (lower.contains("beetroot")) return Items.BEETROOT;
        if (lower.contains("copper")) return Items.RAW_COPPER;
        if (lower.contains("iron")) return Items.RAW_IRON;
        if (lower.contains("gold")) return Items.RAW_GOLD;
        if (lower.contains("diamond")) return Items.DIAMOND;
        if (lower.contains("emerald")) return Items.EMERALD;
        if (lower.contains("netherite") || lower.contains("ancient_debris")) return Items.ANCIENT_DEBRIS;
        String category = ProfessionSubLevelManager.categoryName(key);
        if ("Crop".equals(category)) return Items.WHEAT;
        if ("Wood".equals(category)) return Items.OAK_LOG;
        if ("Ore".equals(category)) return Items.RAW_IRON;
        if ("Type Slayer".equals(category)) return Items.DIAMOND_SWORD;
        return Items.PAPER;
    }

    private static String rawId(String key) {
        if (key == null) return "";
        String[] parts = key.split(":", 3);
        return parts.length == 3 ? parts[2] : key;
    }

    private static Item item(String id) {
        if (id == null || id.isBlank()) return Items.AIR;
        try {
            ResourceLocation rl = id.contains(":") ? ResourceLocation.parse(id) : ResourceLocation.fromNamespaceAndPath("minecraft", id);
            return BuiltInRegistries.ITEM.get(rl);
        } catch (Throwable ignored) {
            return Items.AIR;
        }
    }

    public static void openDetails(ServerPlayer player, ProfessionType profession) {
        SimpleGui gui = MenuUtil.createGui(MenuType.GENERIC_9x6, player);
        gui.setTitle(Component.literal(profession == null ? "Profession Details" : prettyProfession(profession) + " Details"));
        MenuUtil.fillBorders(gui, 10,11,12,13,14,15,16, 28,29,30,31,32,33,34, 49);

        if (profession == null) {
            setDetailButton(gui, player, 10, ProfessionType.MINING, Items.DIAMOND_PICKAXE, "§bMining Details");
            setDetailButton(gui, player, 12, ProfessionType.FORESTRY, Items.DIAMOND_AXE, "§aForestry Details");
            setDetailButton(gui, player, 14, ProfessionType.FARMING, Items.DIAMOND_HOE, "§eFarming Details");
            setDetailButton(gui, player, 16, ProfessionType.BATTLING, Items.DIAMOND_SWORD, "§cBattling Details");
            setDetailButton(gui, player, 30, ProfessionType.BREEDING, Items.EGG, "§dBreeding Details");
            MenuUtil.addInfoCard(gui, 32, Items.BOOK, "§6What matters",
                    "§7Overall profession level controls",
                    "§7base chunk odds and major unlocks.",
                    "§7Sublevels level faster and add",
                    "§7extra chunk find/rarity bonuses.");
        } else if (profession == ProfessionType.BREEDING) {
            int level = ProfessionManager.getLevel(player, profession);
            int xp = ProfessionManager.getXp(player, profession);
            BreedingConfig.Values cfg = BreedingConfig.get();
            MenuUtil.addInfoCard(gui, 10, Items.EGG, "§dBreeding Progress",
                    "§7Level: §f" + level,
                    "§7XP: §f" + xp + "§7/§f" + ProfessionManager.xpRequired(level),
                    "§7XP comes from hatching Eggs.",
                    "§7Rarer hatchlings give more XP.");
            MenuUtil.addInfoCard(gui, 12, Items.DIAMOND, "§bEgg Quality",
                    "§7Ditto + Ditto can hatch a",
                    "§7random eligible base-stage Pokémon.",
                    "§7Higher levels improve rare-pool odds.",
                    "§7Lv. 100 extra perfect IV: §a" + pct(cfg.breedingLevel100ExtraPerfectIvChancePercent));
            MenuUtil.addInfoCard(gui, 14, Items.ENCHANTED_BOOK, "§5Rare Traits",
                    "§7Lv. 100 Hidden Ability bonus: §d+" + pct(cfg.breedingLevel100HiddenAbilityBonusPercent),
                    "§7Lv. 100 shiny bonus: §e+" + pct(cfg.breedingLevel100ShinyRelativeBonusPercent) + " relative",
                    "§8Bonuses scale gradually and",
                    "§8soft-cap at level 100.");
            MenuUtil.addInfoCard(gui, 16, Items.NETHERITE_SCRAP, "§6Hatch Rewards",
                    "§7Every Egg gives one digital chunk.",
                    "§7Chunk rarity improves with level.",
                    "§7At level 100, Netherite is 2%.",
                    "§7Use §f/chunks §7to view rewards.");
        } else {
            int level = ProfessionManager.getLevel(player, profession);
            int xp = ProfessionManager.getXp(player, profession);
            double findBonus = ProfessionSubLevelManager.chunkFindChanceBonus(player, profession) * 100.0D;
            double rarityBonus = ProfessionSubLevelManager.chunkRarityChanceBonus(player, profession) * 100.0D;
            MenuUtil.addInfoCard(gui, 10, iconForProfession(profession), "§eCurrent Progress",
                    "§7Level: §f" + level,
                    "§7XP: §f" + xp + "§7/§f" + ProfessionManager.xpRequired(level),
                    "§7Sublevel find bonus: §a+" + pct(findBonus),
                    "§7Sublevel rarity bonus: §d+" + pct(rarityBonus));
            MenuUtil.addInfoCard(gui, 12, Items.AMETHYST_SHARD, "§dChunk Rolls",
                    chunkRollLines(profession));
            MenuUtil.addInfoCard(gui, 14, Items.EXPERIENCE_BOTTLE, "§aLevel Scaling",
                    "§7Overall levels now matter more.",
                    "§7Each profession level adds +0.5%",
                    "§7to chunk roll odds, up to +50%",
                    "§7before sublevel/trinket bonuses.",
                    "§7Sublevels are intentionally faster",
                    "§7than main profession levels.");
            MenuUtil.addInfoCard(gui, 16, Items.NETHER_STAR, "§6Popup Rules",
                    "§7Only one title popup plays at once.",
                    "§7If two passives trigger together,",
                    "§7the extra notice moves to chat",
                    "§7and only one sound is played.");
            MenuUtil.addInfoCard(gui, 30, Items.BOOK, "§bTool Speed",
                    "§7Tool speed is fixed server-side",
                    "§7and scales cleanly by rank.",
                    "§7Better tools should feel",
                    "§7noticeably better to use.");
            gui.setSlot(32, new GuiElementBuilder(Items.CHEST)
                    .hideDefaultTooltip()
                    .setName(Component.literal("§eView Sublevels"))
                    .addLoreLine(Component.literal("§7Open all discovered sublevels."))
                    .addLoreLine(Component.literal("§eClick to open"))
                    .setCallback((i, c, t) -> openSublevels(player, profession, 0)));
        }

        MenuUtil.addBackButton(gui, 49, () -> open(player));
        gui.open();
    }

    private static void setDetailButton(SimpleGui gui, ServerPlayer player, int slot, ProfessionType profession, Item icon, String name) {
        gui.setSlot(slot, new GuiElementBuilder(icon).hideDefaultTooltip()
                .setName(Component.literal(name))
                .addLoreLine(Component.literal("§7" + chunkRollSummary(profession)))
                .addLoreLine(Component.literal("§eClick for details"))
                .setCallback((i, c, t) -> openDetails(player, profession)));
    }

    private static Item iconForProfession(ProfessionType profession) {
        return switch (profession) {
            case MINING -> Items.DIAMOND_PICKAXE;
            case FORESTRY -> Items.DIAMOND_AXE;
            case FARMING -> Items.DIAMOND_HOE;
            case BATTLING -> Items.DIAMOND_SWORD;
            case BREEDING -> Items.EGG;
            default -> Items.BOOK;
        };
    }

    private static String chunkRollSummary(ProfessionType profession) {
        if (profession == ProfessionType.BREEDING) return "1 guaranteed chunk per hatch";
        ProfessionChunkConfig.ActivityData activity = ProfessionChunkConfig.CONFIG.activities.get(profession.name());
        if (activity == null || activity.rolls == null || activity.rolls.isEmpty()) return "No chunk rewards yet.";
        return "Base x" + String.format(java.util.Locale.US, "%.2f", activity.activityMultiplier) + " · " + activity.rolls.size() + " rarities";
    }

    private static String[] chunkRollLines(ProfessionType profession) {
        if (profession == ProfessionType.BREEDING) return new String[]{"§7One guaranteed chunk per hatch.", "§7Rarity scales with Breeding level.", "§7Level 100 Netherite chance: §d2.00%"};
        ProfessionChunkConfig.ActivityData activity = ProfessionChunkConfig.CONFIG.activities.get(profession.name());
        if (activity == null || activity.rolls == null || activity.rolls.isEmpty()) return new String[]{"§7No chunk rewards yet."};
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§7Activity multiplier: §f" + String.format(java.util.Locale.US, "%.2fx", activity.activityMultiplier));
        for (var entry : activity.rolls.entrySet()) {
            ProfessionChunkConfig.RollData roll = entry.getValue();
            if (roll == null) continue;
            lines.add("§7" + ProfessionChunkManager.formatChunk(entry.getKey()) + ": §f" + pct(roll.baseChancePercent) + " §8+" + pct(roll.chancePerLevelPercent) + "/lvl, cap " + pct(roll.maxChancePercent));
            if (lines.size() >= 8) break;
        }
        return lines.toArray(new String[0]);
    }

    private static String prettyProfession(ProfessionType profession) {
        String lower = profession.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String pct(double value) {
        return String.format(java.util.Locale.US, "%.2f%%", value);
    }
}
